package gh.mutalib.sika.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.sms.Sweeper
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.inflow
import gh.mutalib.sika.ledger.outflow
import gh.mutalib.sika.parser.Direction
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
    /** The most recent stated balance in this month. Null when the month has nothing in it. */
    val balance: Long? = null,
    val balanceAt: Long? = null,
    val moneyIn: Long = 0,
    val moneyOut: Long = 0,
    val gaps: Int = 0,
    /** The oldest unexplained gap, which is the one worth chasing first. */
    val firstGap: TransactionEntity? = null,
    val unlabelled: Int = 0,
    val days: List<DayGroup> = emptyList(),
    /** Every transaction in this month, for the "See all" hand-off. */
    val total: Int = 0,
    /**
     * What has left the wallet today. The one idea worth taking from the dashboard
     * comparison on 2026-08-31 — and unlike the rest of that dashboard, Sika can answer it
     * from real messages without asking Mutalib to type anything.
     */
    val spentToday: Long = 0,
    val countToday: Int = 0,
    /**
     * What period the SPENT/RECEIVED figures cover, as it appears on their labels — "AUG"
     * today, "SEM 1" once semester ranges land in v1.1.
     *
     * ⚠ Carried in state rather than derived in the composable so the labels follow the
     * period automatically. A figure whose period you have to infer is one you cannot act
     * on, which is what Mutalib caught when the labels just said OUT and IN.
     */
    val periodLabel: String = "",
) {
    val isEmpty: Boolean get() = !loading && days.isEmpty()
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
     * The live receiver already records messages as they arrive, so this is not how the
     * ledger normally stays current — it is the manual catch-up for the case Android drops
     * a broadcast, and the gesture people reach for by reflex when a screen might be stale.
     *
     * ⚠ **A minimum visible duration is deliberate.** The sweep finishes in well under a
     * second on 300 messages, and an indicator that vanishes before it is seen reads as
     * "nothing happened" rather than "checked, nothing new". 600 ms is long enough to
     * register and short enough not to feel slow.
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
     * The whole ledger as a Flow, folded into one month's view.
     *
     * Reading everything and filtering in memory rather than querying per month: 144 rows
     * is nothing, and it means changing month is instant with no round trip. Revisit if the
     * ledger ever reaches thousands — `observeBetween` already exists for that day.
     */
    val state: StateFlow<HomeState> = dao.observeAll()
        .map { all -> fold(all, YearMonth.now(ACCRA)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun fold(all: List<TransactionEntity>, month: YearMonth): HomeState {
        val inMonth = all.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ACCRA)) == month
        }

        val today = LocalDate.now(ACCRA)
        val todays = inMonth.filter {
            Instant.ofEpochMilli(it.occurredAt).atZone(ACCRA).toLocalDate() == today
        }

        val days = inMonth
            .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(ACCRA).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (date, rows) -> DayGroup(dayLabel(date), rows.sortedByDescending { it.occurredAt }) }

        return HomeState(
            month = month,
            loading = false,
            // The newest row's own stated balance. Not a running total we computed —
            // showing our arithmetic where MoMo's own figure exists would be inventing a
            // number, and this app's whole argument is that it does not do that.
            balance = inMonth.firstOrNull { it.balanceAfter != null }?.balanceAfter,
            balanceAt = inMonth.firstOrNull { it.balanceAfter != null }?.occurredAt,
            // ⚠ Both through `inflow`/`outflow` — see ledger/MonthSummary.kt. Until task 13
            // this line summed bare `amount` while `spentToday` below summed `amount + fee`,
            // so two figures on the same card were computed differently. The fee and tax are
            // money that left the wallet: Reconciler proves it, because MoMo's own stated
            // balance only agrees with `previous − amount − fee − tax`.
            moneyIn = inMonth.filter { it.direction == Direction.IN }.sumOf { it.inflow() },
            moneyOut = inMonth.filter { it.direction == Direction.OUT }.sumOf { it.outflow() },
            gaps = inMonth.count { it.reconciled == Reconciled.GAP },
            firstGap = inMonth.lastOrNull { it.reconciled == Reconciled.GAP },
            unlabelled = inMonth.count { it.label == null },
            days = days,
            total = inMonth.size,
            periodLabel = PERIOD.format(month).uppercase(),
            spentToday = todays.filter { it.direction == Direction.OUT }.sumOf { it.outflow() },
            countToday = todays.size,
        )
    }

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now(ACCRA)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DAY_FORMAT.format(date).uppercase()
        }
    }

    private companion object {
        const val MIN_VISIBLE_MS = 600L
        val DAY_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")
        val PERIOD: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("MMM")
    }
}
