package gh.mutalib.sika.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.DemoMode
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.data.Terms
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.onboarding.OnboardingPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * Screen 3's state — PLAN task 13, extended on 2026-08-31 with week / month / semester.
 *
 * All the arithmetic lives in [summarise], which is pure and JVM-tested. This class only
 * decides *which stretch of time*, and joins that to the ledger.
 */
class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SikaDatabase.get(app).transactions()

    /**
     * Where a semester starts. Null until it is known.
     *
     * ⚠ Defaults to the oldest transaction in the ledger rather than to a guessed academic
     * date. KNUST's real term dates are a fact only Mutalib has, and putting invented dates
     * on this screen would be exactly the thing the report exists not to do.
     */
    private val semesterStart = MutableStateFlow<LocalDate?>(null)

    /** The oldest transaction on record — where "All" begins. */
    private val oldest = MutableStateFlow<LocalDate?>(null)

    private val _mode = MutableStateFlow(PeriodMode.MONTH)
    val mode: StateFlow<PeriodMode> = _mode

    private val _period = MutableStateFlow(
        Period.current(PeriodMode.MONTH, today(ACCRA), null),
    )
    val period: StateFlow<Period> = _period

    /**
     * ⚠ **A term the user typed beats the guess from the ledger.**
     *
     * Until 2026-09-01 SEMESTER ran from the oldest transaction on record, which is only the
     * start of a term by coincidence — it is really the date this phone first got a MoMo text.
     * Onboarding now asks, so the answer is used when there is one and the old guess stays as
     * the fallback for "I don't know the dates yet".
     */
    private val storedTerm: LocalDate? =
        OnboardingPrefs.termStart(app).takeIf { OnboardingPrefs.isStudent(app) }

    init {
        viewModelScope.launch {
            // ⚠ Carries the old single term into the list before anything reads it, so a phone
            // upgrading from v1.0.1 keeps the semester it already had instead of losing it to
            // an empty table.
            val termDao = SikaDatabase.get(app).terms()
            Terms.ensureSeeded(app, termDao)
            terms.value = termDao.all()
            if (_mode.value == PeriodMode.SEMESTER && terms.value.isNotEmpty()) {
                _period.value = semesterPeriod(termIndexFor(today(ACCRA)))
            }
        }
        viewModelScope.launch {
            val first = dao.oldestTimestamp()
            if (first != null) {
                val date = Instant.ofEpochMilli(first).atZone(ACCRA).toLocalDate()
                semesterStart.value = storedTerm ?: date
                oldest.value = date
                // The period was built before the ledger was read, so "All" and "Semester"
                // would otherwise sit on the fallback range until the mode was tapped.
                if (_mode.value == PeriodMode.ALL || _mode.value == PeriodMode.SEMESTER) {
                    _period.value =
                        Period.current(_mode.value, today(ACCRA), semesterStart.value, date)
                }
            }
        }
    }

    /**
     * Reads the whole ledger rather than one period, because the comparison against the
     * previous one needs both. At Mutalib's volume — a few hundred rows — that is nothing,
     * and it makes switching mode instant with no round trip.
     */
    val summary: StateFlow<PeriodSummary?> =
        combine(dao.observeAll(), _period, DemoMode.rows) { all, period, demo ->
            summarise(demo ?: all, period, ACCRA)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Switching mode always lands on the *current* week/month/semester, never a stale offset. */
    fun setMode(mode: PeriodMode) {
        _mode.value = mode
        _period.value =
            if (mode == PeriodMode.SEMESTER) semesterPeriod(termIndexFor(today(ACCRA)))
            else Period.current(mode, today(ACCRA), semesterStart.value, oldest.value)
    }

    /**
     * ⚠ **A semester steps to the NEXT NAMED TERM, not by its own length in days.**
     *
     * The old behaviour shifted by however many days the current stretch happened to be, which
     * was the only thing possible when a semester was one anonymous range. Now that Mutalib
     * names them, "previous semester" has an exact meaning and it is a row in a list — and the
     * two answers disagree badly, because semesters are not equal lengths and the vacation
     * between them belongs to neither.
     *
     * Falls back to the day-shift when no terms are recorded, so someone who has never opened
     * the semester screen still gets a working ‹ ›.
     */
    fun step(steps: Long) {
        val current = _period.value
        if (current.mode == PeriodMode.SEMESTER && terms.value.isNotEmpty()) {
            val index = terms.value.indexOfFirst { it.name == current.name }
                .takeIf { it >= 0 } ?: termIndexFor(today(ACCRA))
            val target = (index + steps).toInt()
            if (target in terms.value.indices) _period.value = semesterPeriod(target)
            return
        }
        val next = current.shift(steps)
        // Never walk into the future: there is nothing there, and an empty state the user
        // navigated into by accident reads as a bug.
        if (!next.startsAfter(today(ACCRA))) _period.value = next
    }

    /** The term list, ordered by start date. Empty until the first semester is named. */
    private val terms = MutableStateFlow<List<TermEntity>>(emptyList())

    private fun termIndexFor(day: LocalDate): Int {
        val list = terms.value
        if (list.isEmpty()) return -1
        val here = Terms.currentOrLast(list, day)
        return list.indexOf(here).coerceAtLeast(0)
    }

    /**
     * A period for the term at [index], or the old open-ended stretch when there is no list.
     *
     * ⚠ The fallback matters: naming semesters is optional, and someone who never does must
     * keep exactly the behaviour they had before this existed.
     */
    private fun semesterPeriod(index: Int): Period {
        val list = terms.value
        val term = list.getOrNull(index)
            ?: return Period.current(
                PeriodMode.SEMESTER, today(ACCRA), semesterStart.value, oldest.value,
            )
        return Period.namedSemester(term.name, term.start, term.endExclusive)
    }

    val canStepForward: StateFlow<Boolean> = combine(_period, terms) { period, list ->
        if (period.mode == PeriodMode.SEMESTER && list.isNotEmpty()) {
            val i = list.indexOfFirst { it.name == period.name }
            i >= 0 && i < list.lastIndex
        } else {
            !period.shift(1).startsAfter(today(ACCRA))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
