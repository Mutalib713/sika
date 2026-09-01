package gh.mutalib.sika.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.DemoMode
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.ui.home.ACCRA
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

    init {
        viewModelScope.launch {
            val first = dao.oldestTimestamp()
            if (first != null) {
                val date = Instant.ofEpochMilli(first).atZone(ACCRA).toLocalDate()
                semesterStart.value = date
                oldest.value = date
                // The period was built before the ledger was read, so "All" and "Semester"
                // would otherwise sit on the fallback range until the mode was tapped.
                if (_mode.value == PeriodMode.ALL || _mode.value == PeriodMode.SEMESTER) {
                    _period.value = Period.current(_mode.value, today(ACCRA), date, date)
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
        _period.value = Period.current(mode, today(ACCRA), semesterStart.value, oldest.value)
    }

    fun step(steps: Long) {
        val next = _period.value.shift(steps)
        // Never walk into the future: there is nothing there, and an empty state the user
        // navigated into by accident reads as a bug.
        if (!next.startsAfter(today(ACCRA))) _period.value = next
    }

    val canStepForward: StateFlow<Boolean> = _period
        .map { !it.shift(1).startsAfter(today(ACCRA)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
