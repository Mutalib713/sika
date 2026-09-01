package gh.mutalib.sika.notify

import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.parser.asCedis
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The monthly summary — **the arithmetic and the words, with no Android in sight.**
 *
 * Everything here is a pure function of a clock reading and a [PeriodSummary], which is what
 * makes the interesting half of PLAN task 14 testable on a laptop with no phone attached. The
 * `AlarmManager` plumbing lives beside it and can only be proved on the device.
 */
object MonthlyReport {

    const val CHANNEL_ID = "monthly_report"
    const val ACTION_FIRE = "gh.mutalib.sika.MONTHLY_REPORT"
    const val NOTIFICATION_ID = 20_260_902

    /**
     * 9am on the 1st.
     *
     * The month just gone is finished, and a summary of it is something to read with the
     * morning rather than at the end of a day it does not describe. (The end-of-day nudge
     * fires at 9pm for the opposite reason: it is about the day you are still in.)
     */
    val AT: LocalTime = LocalTime.of(9, 0)

    /**
     * When the next summary is due.
     *
     * ⚠ **Uses `withDayOfMonth(1)` and `plusMonths`, never "add 30 days".** Months are not a
     * fixed length, and an alarm rebooked by adding days drifts a little every month until it
     * is firing on the 28th — quietly, months after anyone would connect the two.
     *
     * ⚠ **`isAfter`, not `isEqual` or `isBefore`.** Called at exactly 09:00:00 on the 1st —
     * which is precisely when the firing itself reschedules — "the 1st at 9am" is now, not
     * later, and returning it would book an alarm for the present instant and fire forever.
     * The boundary is the whole reason this is a named function with tests rather than three
     * lines inside `schedule`.
     *
     * Africa/Accra is UTC+0 with no daylight saving, so no local time here is ambiguous or
     * skipped. That is a property of the zone, not of this code, so it is stated rather than
     * relied on silently: a zone with DST could land on a 9am that happens twice.
     */
    fun nextFire(now: ZonedDateTime): ZonedDateTime {
        val thisMonth = now.withDayOfMonth(1).with(AT).withSecond(0).withNano(0)
        return if (thisMonth.isAfter(now)) thisMonth else thisMonth.plusMonths(1)
    }

    /**
     * The notification's title: the month, what left, what arrived.
     *
     * @param summary the month being reported on — **the one that just ended**, not the one
     * the alarm fires in.
     */
    fun title(summary: PeriodSummary): String {
        val month = MONTH.format(summary.period.start)
        return "$month: ${summary.moneyOut.asCedis()} out, ${summary.moneyIn.asCedis()} in"
    }

    /**
     * The second line, or null when there is nothing honest to put in it.
     *
     * ⚠ **Returns null rather than reaching for something to say.** The rules it inherits from
     * [PeriodSummary] are the point: `biggestChange` is already null on a first month, when
     * nothing clearly moved most, and when the mover is Uncategorised — which is an absence of
     * information rather than a category. A notification that invents a headline out of noise
     * is the exact failure this app exists to avoid, and it would do it once a month forever.
     *
     * When most of the month is unlabelled it says *that* instead, because "you have not told
     * me what this money was" is true and useful, where a breakdown of the 30% that is
     * labelled would be neither.
     */
    fun detail(summary: PeriodSummary): String? {
        if (summary.isEmpty) return null
        if (summary.tooLittleLabelledToBreakDown) {
            val share = (summary.uncategorisedShare * 100).roundToInt()
            return "$share% of it has no category yet, so there is no breakdown to give you."
        }
        val biggest = summary.biggestChange ?: return null
        val name = biggest.label
        val spent = biggest.amount.asCedis()
        val change = biggest.change?.takeIf { it != 0L }?.let { moved ->
            (if (moved > 0) "up " else "down ") + abs(moved).asCedis()
        }
        return if (change == null) "$name was your biggest at $spent."
        else "$name was your biggest at $spent, $change on the month before."
    }

    /** "August", from the period being reported on. */
    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
}
