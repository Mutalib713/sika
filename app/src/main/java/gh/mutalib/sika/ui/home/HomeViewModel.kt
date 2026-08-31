package gh.mutalib.sika.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
) {
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SikaDatabase.get(app).transactions()

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
            moneyIn = inMonth.filter { it.direction == Direction.IN }.sumOf { it.amount },
            moneyOut = inMonth.filter { it.direction == Direction.OUT }.sumOf { it.amount },
            gaps = inMonth.count { it.reconciled == Reconciled.GAP },
            firstGap = inMonth.lastOrNull { it.reconciled == Reconciled.GAP },
            unlabelled = inMonth.count { it.label == null },
            days = days,
            total = inMonth.size,
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
        val DAY_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")
    }
}
