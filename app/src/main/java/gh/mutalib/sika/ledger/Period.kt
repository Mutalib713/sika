package gh.mutalib.sika.ledger

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * What stretch of time a report covers — Mutalib's request, 2026-08-31: *"the report side
 * had some ideas so it can be weekly monthly or semester based which the user can switch
 * through modes"*.
 */
enum class PeriodMode {
    /** Monday to Sunday. The scale you can still remember, so the numbers are checkable. */
    WEEK,

    /** The calendar month. What the report did before there was a choice. */
    MONTH,

    /**
     * A stretch he defines himself.
     *
     * ⚠ **KNUST's real semester dates are not written into this app, deliberately.** They
     * are a fact only Mutalib has, and guessing them would put invented dates on a screen
     * whose entire argument is that it does not invent numbers. Until he sets one, a
     * semester runs from the oldest transaction in the ledger to today — which for a new
     * install is close to the truth and, more importantly, is *derived* rather than made up.
     */
    SEMESTER,
    ;

    /**
     * The word the screen uses for one of these, in a sentence.
     *
     * Exists because every string on the report used to say "month" — "97% on last month",
     * "Food went up this month", "every cedi this month is accounted for" — which became
     * untrue the moment the period could be a week or a semester. The period now supplies
     * its own noun rather than each string assuming one.
     */
    val noun: String get() = when (this) {
        WEEK -> "week"
        MONTH -> "month"
        SEMESTER -> "semester"
    }
}

/**
 * A concrete stretch of days, half-open: `start` is included, `endExclusive` is not.
 *
 * Half-open because that is the only way to tile time without gaps or overlaps — the last
 * millisecond of a month is a place bugs hide, and "before the 1st of next month" has no
 * such edge.
 */
data class Period(
    val mode: PeriodMode,
    val start: LocalDate,
    val endExclusive: LocalDate,
) {
    val label: String get() = when (mode) {
        PeriodMode.WEEK -> {
            val last = endExclusive.minusDays(1)
            if (start.month == last.month) {
                "${start.dayOfMonth}–${last.dayOfMonth} ${MONTH_SHORT.format(last)}"
            } else {
                "${DAY_MONTH.format(start)} – ${DAY_MONTH.format(last)}"
            }
        }
        PeriodMode.MONTH -> MONTH_FULL.format(start)
        PeriodMode.SEMESTER -> "Since ${DAY_MONTH.format(start)}"
    }

    /** The same length of time, immediately before this one. What "against last" compares to. */
    fun previous(): Period = when (mode) {
        PeriodMode.WEEK -> Period(mode, start.minusWeeks(1), start)
        PeriodMode.MONTH -> Period(mode, start.minusMonths(1), start)
        // A semester has no natural predecessor, so the comparison is against the same
        // number of days immediately before it.
        PeriodMode.SEMESTER -> {
            val days = ChronoUnit.DAYS.between(start, endExclusive)
            Period(mode, start.minusDays(days), start)
        }
    }

    /** Moved [steps] periods later (negative for earlier). */
    fun shift(steps: Long): Period = when (mode) {
        PeriodMode.WEEK -> Period(mode, start.plusWeeks(steps), endExclusive.plusWeeks(steps))
        PeriodMode.MONTH -> Period(mode, start.plusMonths(steps), endExclusive.plusMonths(steps))
        PeriodMode.SEMESTER -> {
            val days = ChronoUnit.DAYS.between(start, endExclusive)
            Period(mode, start.plusDays(days * steps), endExclusive.plusDays(days * steps))
        }
    }

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    /** True when this period has already begun — there is nothing to show beyond today. */
    fun startsAfter(today: LocalDate): Boolean = start.isAfter(today)

    companion object {
        private val MONTH_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

        /** The week containing [date], Monday first. */
        fun weekOf(date: LocalDate): Period {
            val monday = date.minusDays(((date.dayOfWeek.value + 6) % 7).toLong())
            return Period(PeriodMode.WEEK, monday, monday.plusWeeks(1))
        }

        fun monthOf(date: LocalDate): Period {
            val first = YearMonth.from(date).atDay(1)
            return Period(PeriodMode.MONTH, first, first.plusMonths(1))
        }

        /**
         * A semester running from [from] up to and including today.
         *
         * `endExclusive` is tomorrow rather than today, so today's own transactions are in
         * it — an "up to now" range that silently excluded this morning would be wrong in
         * the way nobody notices until the totals disagree.
         */
        fun semesterFrom(from: LocalDate, today: LocalDate): Period =
            Period(PeriodMode.SEMESTER, from, today.plusDays(1))

        /** The current period for a mode, given where "now" is. */
        fun current(mode: PeriodMode, today: LocalDate, semesterStart: LocalDate?): Period =
            when (mode) {
                PeriodMode.WEEK -> weekOf(today)
                PeriodMode.MONTH -> monthOf(today)
                PeriodMode.SEMESTER ->
                    semesterFrom(semesterStart ?: today.withDayOfMonth(1), today)
            }
    }
}

/** Today, in Accra. Kept here so no screen has to remember which zone the ledger uses. */
fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)
