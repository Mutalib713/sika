package gh.mutalib.sika.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.ledger.MonthSummary
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ui.home.ACCRA
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/**
 * Screen 3's state — PLAN task 13.
 *
 * All the arithmetic lives in [summarise], which is pure and JVM-tested. This class only
 * decides *which* month, and joins that to the ledger.
 */
class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SikaDatabase.get(app).transactions()

    private val _month = MutableStateFlow(YearMonth.now(ACCRA))
    val month: StateFlow<YearMonth> = _month

    /**
     * Reads the whole ledger rather than one month, because the comparison against the
     * previous month needs both. At Mutalib's volume — a few hundred rows — that is
     * nothing, and it makes changing month instant with no round trip.
     */
    val summary: StateFlow<MonthSummary?> =
        combine(dao.observeAll(), _month) { all, month -> summarise(all, month, ACCRA) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun step(months: Long) {
        val next = _month.value.plusMonths(months)
        // Never walk into the future: there is nothing there, and an empty state that the
        // user navigated into by accident reads as a bug.
        if (next <= YearMonth.now(ACCRA)) _month.value = next
    }

    fun canStepForward(): Boolean = _month.value < YearMonth.now(ACCRA)
}
