package gh.mutalib.sika.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.TAG
import gh.mutalib.sika.logPrivate
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.DemoMode
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.Reconciler
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.sms.Sweeper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Africa/Accra, UTC+0 with no daylight saving — stated rather than assumed. PROFILE.md § 7. */
val ACCRA: ZoneId = ZoneId.of("Africa/Accra")

data class DayGroup(val label: String, val rows: List<TransactionEntity>)

/**
 * Money that moved with no message to explain it.
 *
 * ⚠ **[untilMillis] is when it was CAUGHT, not when it happened.** The arithmetic fails on the
 * message *after* the missing one, so all that is genuinely known is a window. Mutalib asked
 * how Sika could know about a message that never came, and this is the honest shape of the
 * answer: an exact amount, and a range of dates.
 */
data class GapDetail(
    /** The transaction that revealed it. */
    val rowId: Long,
    /** How much the balance moved beyond what the messages account for. Always positive. */
    val amount: Long,
    /** The transaction before it. Null when the very first checkable row is already a gap. */
    val sinceMillis: Long?,
    val untilMillis: Long,
    /** What Mutalib said it was, if he has said. */
    val note: String?,
    /**
     * Which category the amount is counted under, if he has chosen one.
     *
     * ⚠ Null means it stays out of every total, exactly as gaps behaved before 2026-09-03.
     * Choosing one is opt-in, per gap, and reversible by choosing it again.
     */
    val category: String? = null,
)

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
    /**
     * The newest gap, with enough detail to say something useful about it.
     *
     * Newest rather than oldest: a hole from last week is one you might still remember, and
     * remembering is the only way it ever gets a name.
     */
    val gap: GapDetail? = null,
    val unlabelled: Int = 0,
    /** Every transaction this month, grouped by day — what the all-transactions screen shows. */
    val days: List<DayGroup> = emptyList(),
    /** Every transaction on record, grouped by day — what the "All time" filter reads. */
    val allDays: List<DayGroup> = emptyList(),
    val total: Int = 0,
    /**
     * Every transaction on record, not just this month's.
     *
     * ⚠ **This exists because one empty state was doing two jobs and getting one of them
     * wrong.** [isEmpty] means "nothing THIS MONTH", and the screen told both a phone with 148
     * transactions and a brand-new install the same thing: *transactions appear here as MoMo
     * texts arrive*. For the second phone that is advice to wait, and waiting will not help.
     */
    val totalEver: Int = 0,
    /** The newest transaction on record — proof, for a month that has none yet. */
    val lastEver: TransactionEntity? = null,
) {
    /** Nothing this month. There may be plenty of history. */
    val isEmpty: Boolean get() = !loading && total == 0

    /** Nothing at all, ever. A different situation with a different answer. */
    val neverAnything: Boolean get() = !loading && totalEver == 0
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SikaDatabase.get(app)
    private val dao = db.transactions()
    private val rules = db.rules()
    private val categoryDao = db.categories()

    /**
     * The category list, live — a new one added from the sheet appears immediately.
     *
     * ⚠ **Visible only.** A category put away in Settings stops being offered here, which is
     * the entire point of putting one away. The screen that manages them reads `observeAll`
     * instead; wire this one to that by mistake and hiding does nothing at all.
     */
    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeVisible()
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
        // Demo rows are not in the database, so an edit has to land on them instead. See
        // DemoMode.edit for why a sandbox that ignores input is worse than none.
        if (DemoMode.active) {
            DemoMode.edit(row.id) { it.copy(label = category, labelSource = LabelSource.MANUAL) }
            if (alsoRemember && row.counterparty.isNotBlank()) {
                DemoMode.rows.value.orEmpty()
                    .filter { it.counterparty == row.counterparty }
                    .forEach { match ->
                        DemoMode.edit(match.id) { it.copy(label = category, labelSource = LabelSource.AUTO_RULE) }
                    }
            }
            return
        }
        viewModelScope.launch {
            dao.setLabel(row.id, category, LabelSource.MANUAL)
            if (alsoRemember && row.counterparty.isNotBlank()) {
                rules.put(RuleEntity(row.counterparty, category, System.currentTimeMillis()))
                val touched = dao.applyRule(row.counterparty, category)
                logPrivate { "rule '" + row.counterparty + "' -> '" + category + "'" }
                Log.i(TAG, "a learned rule was applied to " + touched + " rows")
            }
        }
    }

    /**
     * Sets or clears a row's note — what this one payment was actually for.
     *
     * Never writes a rule, and never touches the label. A note describes one transaction and
     * generalises to nothing, which is the entire difference between it and a category.
     */
    fun setNote(row: TransactionEntity, note: String?) {
        if (DemoMode.active) {
            DemoMode.edit(row.id) { it.copy(note = note) }
            return
        }
        viewModelScope.launch { dao.setNote(row.id, note) }
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

    /**
     * Records what the money behind a gap actually was.
     *
     * ⚠ **Writes the note and nothing else.** It does not clear the `GAP` flag: MTN still
     * never sent that message, and remembering the purchase does not make it exist. Passing
     * null clears the explanation again.
     */
    fun explainGap(rowId: Long, what: String?) {
        val clean = what?.trim()?.takeIf { it.isNotEmpty() }
        if (DemoMode.active) {
            DemoMode.edit(rowId) { it.copy(gapNote = clean) }
            return
        }
        viewModelScope.launch { dao.setGapNote(rowId, clean) }
    }

    /**
     * Files remembered money under a category, so it reaches that category's total.
     *
     * ⚠ **This is the only path in Sika that puts a figure into a total without a message
     * behind it**, and it is deliberate — Mutalib's decision, 2026-09-03. Passing the category
     * that is already set clears it again, so the choice is always reversible from the same
     * tap that made it.
     *
     * ⚠ It does not touch the GAP flag or the note. The hole is still a hole; this only says
     * where the money went.
     */
    fun fileGap(rowId: Long, category: String?) {
        if (DemoMode.active) {
            DemoMode.edit(rowId) { it.copy(gapCategory = category) }
            return
        }
        viewModelScope.launch { dao.setGapCategory(rowId, category) }
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
    val state: StateFlow<HomeState> = combine(dao.observeAll(), DemoMode.rows) { all, demo ->
        // Demo rows replace the ledger for display only; nothing is ever written.
        fold(demo ?: all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun fold(all: List<TransactionEntity>): HomeState {
        val now = today(ACCRA)
        val month = Period.monthOf(now)
        val week = Period.weekOf(now)

        val inMonth = all.filter { month.contains(dateOf(it)) }

        fun byDay(rows: List<TransactionEntity>) = rows
            .groupBy { dateOf(it) }
            .toSortedMap(reverseOrder())
            .map { (date, day) -> DayGroup(dayLabel(date), day.sortedByDescending { it.occurredAt }) }

        val days = byDay(inMonth)

        // The reconciliation verdicts are already stored on the rows by ReconcilePass; this
        // re-runs the pure arithmetic only to recover the SIZE of each hole, which is not a
        // column. Cheap on a few hundred rows, and it keeps the ledger free of a derived
        // number that could drift out of step with the rows it came from.
        val ordered = all.filter { it.parsedOk }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
        // The window opens at the last row that STATED a balance — see ReconcilePass for why
        // the previous row is the wrong answer.
        val anchorBefore = mutableMapOf<Long, Long?>()
        var lastStated: Long? = null
        for (r in ordered) {
            anchorBefore[r.id] = lastStated
            if (r.balanceAfter != null) lastStated = r.occurredAt
        }
        val newestGap = Reconciler.reconcile(all)
            .filter { it.state == Reconciled.GAP }
            .mapNotNull { check ->
                val row = ordered.firstOrNull { it.id == check.id } ?: return@mapNotNull null
                GapDetail(
                    rowId = row.id,
                    amount = kotlin.math.abs(check.difference ?: 0L),
                    sinceMillis = anchorBefore[row.id],
                    untilMillis = row.occurredAt,
                    note = row.gapNote,
                    category = row.gapCategory,
                )
            }
            .maxByOrNull { it.untilMillis }

        return HomeState(
            month = YearMonth.from(month.start),
            loading = false,
            monthSummary = summarise(all, month, ACCRA),
            weekSummary = summarise(all, week, ACCRA),
            recent = all.sortedByDescending { it.occurredAt }.take(RECENT_ROWS),
            gaps = inMonth.count { it.reconciled == Reconciled.GAP },
            gap = newestGap,
            firstGap = inMonth.lastOrNull { it.reconciled == Reconciled.GAP },
            // ⚠ **Outgoing only.** Mutalib, 2026-09-02: money arriving is *"just money to use
            // for my expenses on campus"* — it has not been spent on anything yet, so asking
            // which category it belongs to is a question with no answer. Counting it here made
            // Home report a backlog of work that could not be done: every payment received
            // added one to "still need a category" and stayed there forever.
            unlabelled = inMonth.count { it.label == null && it.direction == Direction.OUT },
            days = days,
            allDays = byDay(all),
            total = inMonth.size,
            totalEver = all.size,
            lastEver = all.maxByOrNull { it.occurredAt },
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
