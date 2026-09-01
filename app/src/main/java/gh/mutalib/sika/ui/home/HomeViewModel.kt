package gh.mutalib.sika.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.sms.Sweeper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Africa/Accra, UTC+0 with no daylight saving — stated rather than assumed. PROFILE.md § 7. */
val ACCRA: ZoneId = ZoneId.of("Africa/Accra")

data class DayGroup(val label: String, val rows: List<TransactionEntity>)

data class HomeState(
    val month: YearMonth = YearMonth.now(ACCRA),
    val loading: Boolean = true,
    /**
     * This month, for the headline card.
     *
     * The month is the figure worth leading with — it is the one people quote at themselves,
     * and it is the span the report defaults to.
     */
    val monthSummary: PeriodSummary? = null,
    /**
     * This week, for the chart and the categories under it.
     *
     * ⚠ Mutalib's instruction, 2026-09-01: *"for the home one the categories should be based
     * on the weekly aspects, which the daily there"*. Everything below the headline card is
     * about this week, so the bars and the categories beneath them describe the same stretch
     * of time. A weekly chart over monthly categories would look coherent and be answering
     * two different questions.
     */
    val weekSummary: PeriodSummary? = null,
    /** The five newest rows, for the short list on Home. */
    val recent: List<TransactionEntity> = emptyList(),
    val gaps: Int = 0,
    /** The oldest unexplained gap, which is the one worth chasing first. */
    val firstGap: TransactionEntity? = null,
    val unlabelled: Int = 0,
    /** Every transaction this month, grouped by day — what the all-transactions screen shows. */
    val days: List<DayGroup> = emptyList(),
    val total: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && total == 0
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SikaDatabase.get(app)
    private val dao = db.transactions()
    private val rules = db.rules()
    private val categoryDao = db.categories()

    /** The category list, live — a new one added from the sheet appears immediately. */
    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Sets a transaction's category, and optionally teaches Sika to do it automatically.
     *
     * @param alsoRemember writes a learn-once rule keyed on the counterparty and applies it
     * to every other row from the same party. This is the mechanism that makes labelling
     * decay toward zero work — risk #2 in PROFILE.md, label rot.
     *
     * The hand-set row is always [LabelSource.MANUAL], so a rule can never later overwrite
     * a decision made by hand.
     */
    fun setCategory(row: TransactionEntity, category: String, alsoRemember: Boolean) {
        viewModelScope.launch {
            dao.setLabel(row.id, category, LabelSource.MANUAL)
            if (alsoRemember && row.counterparty.isNotBlank()) {
                rules.put(RuleEntity(row.counterparty, category, System.currentTimeMillis()))
                val touched = dao.applyRule(row.counterparty, category)
                Log.i(TAG, "rule '" + row.counterparty + "' -> '" + category + "' applied to " + touched + " rows")
            }
        }
    }

    private val _refreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh sweep is running. */
    val refreshing: StateFlow<Boolean> = _refreshing

    /**
     * Re-reads the inbox and re-runs reconciliation, on demand.
     *
     * ⚠ **A minimum visible duration is deliberate.** The sweep finishes in well under a
     * second on 300 messages, and an indicator that vanishes before it is seen reads as
     * "nothing happened" rather than "checked, nothing new".
     */
    fun refresh(context: Context) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val started = System.currentTimeMillis()
            runCatching { Sweeper.sweep(context) }
                .onFailure { Log.e(TAG, "refresh failed", it) }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - elapsed)
            _refreshing.value = false
        }
    }

    /** Adds a category from the sheet. IGNORE on conflict, so a duplicate name is harmless. */
    fun addCategory(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val order = (categoryDao.all().maxOfOrNull { it.sortOrder } ?: 0) + 1
            categoryDao.insert(CategoryEntity(name = clean, sortOrder = order))
        }
    }

    /**
     * The whole ledger, folded into what Home shows.
     *
     * Reading everything and folding in memory rather than querying per period: a few hundred
     * rows is nothing, and it means the month card and the week chart come from one read
     * rather than three. Revisit if the ledger ever reaches thousands.
     */
    val state: StateFlow<HomeState> = dao.observeAll()
        .map { all -> fold(all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun fold(all: List<TransactionEntity>): HomeState {
        val now = today(ACCRA)
        val month = Period.monthOf(now)
        val week = Period.weekOf(now)

        val inMonth = all.filter { month.contains(dateOf(it)) }

        val days = inMonth
            .groupBy { dateOf(it) }
            .toSortedMap(reverseOrder())
            .map { (date, rows) -> DayGroup(dayLabel(date), rows.sortedByDescending { it.occurredAt }) }

        return HomeState(
            month = YearMonth.from(month.start),
            loading = false,
            monthSummary = summarise(all, month, ACCRA),
            weekSummary = summarise(all, week, ACCRA),
            recent = all.sortedByDescending { it.occurredAt }.take(RECENT_ROWS),
            gaps = inMonth.count { it.reconciled == Reconciled.GAP },
            firstGap = inMonth.lastOrNull { it.reconciled == Reconciled.GAP },
            unlabelled = inMonth.count { it.label == null },
            days = days,
            total = inMonth.size,
        )
    }

    private fun dateOf(row: TransactionEntity): LocalDate =
        Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA).toLocalDate()

    private fun dayLabel(date: LocalDate): String {
        val now = today(ACCRA)
        return when (date) {
            now -> "Today"
            now.minusDays(1) -> "Yesterday"
            else -> DAY_FORMAT.format(date).uppercase()
        }
    }

    private companion object {
        const val MIN_VISIBLE_MS = 600L
        /** Five is what fits without Home becoming the list it links to. */
        const val RECENT_ROWS = 5
        val DAY_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")
    }
}
