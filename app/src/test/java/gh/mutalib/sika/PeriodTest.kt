package gh.mutalib.sika

import gh.mutalib.sika.ledger.BucketUnit
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Week / month / semester — Mutalib's request, 2026-08-31.
 *
 * Boundary arithmetic gets its own tests because an off-by-one day here does not crash: it
 * quietly moves money between two periods, and the totals stay plausible while being wrong.
 */
class PeriodTest {

    // Saturday 15 August 2026.
    private val sat = LocalDate.of(2026, 8, 15)

    @Test
    fun `a week runs Monday to Sunday`() {
        val w = Period.weekOf(sat)
        assertEquals(LocalDate.of(2026, 8, 10), w.start)          // Monday
        assertEquals(LocalDate.of(2026, 8, 17), w.endExclusive)   // the NEXT Monday
        assertTrue(w.contains(LocalDate.of(2026, 8, 16)))          // Sunday, the last day
        assertFalse("the next Monday belongs to the next week", w.contains(w.endExclusive))
    }

    /** ⚠ Monday itself must land in its own week, not the one before. */
    @Test
    fun `Monday belongs to the week it starts`() {
        val monday = LocalDate.of(2026, 8, 10)
        assertEquals(monday, Period.weekOf(monday).start)
    }

    @Test
    fun `Sunday is the end of its week, not the start of the next`() {
        val sunday = LocalDate.of(2026, 8, 16)
        assertEquals(LocalDate.of(2026, 8, 10), Period.weekOf(sunday).start)
    }

    @Test
    fun `a month is the calendar month, half open`() {
        val m = Period.monthOf(sat)
        assertEquals(LocalDate.of(2026, 8, 1), m.start)
        assertEquals(LocalDate.of(2026, 9, 1), m.endExclusive)
        assertTrue(m.contains(LocalDate.of(2026, 8, 31)))
        assertFalse(m.contains(LocalDate.of(2026, 9, 1)))
    }

    /**
     * ⚠ A semester runs *up to and including today*. An "up to now" range that silently
     * excluded this morning's spending would be wrong in the way nobody notices.
     */
    @Test
    fun `a semester includes today`() {
        val s = Period.semesterFrom(LocalDate.of(2026, 5, 12), sat)
        assertTrue(s.contains(sat))
        assertEquals(LocalDate.of(2026, 8, 16), s.endExclusive)
    }

    @Test
    fun `the previous period is the same length, immediately before`() {
        val week = Period.weekOf(sat)
        assertEquals(LocalDate.of(2026, 8, 3), week.previous().start)
        assertEquals(week.start, week.previous().endExclusive)

        val month = Period.monthOf(sat)
        assertEquals(LocalDate.of(2026, 7, 1), month.previous().start)
        assertEquals(month.start, month.previous().endExclusive)

        // A semester has no natural predecessor, so it compares against the same number of
        // days immediately before it.
        val sem = Period.semesterFrom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10))
        assertEquals(10L, java.time.temporal.ChronoUnit.DAYS.between(sem.start, sem.endExclusive))
        assertEquals(LocalDate.of(2026, 7, 22), sem.previous().start)
    }

    @Test
    fun `stepping moves whole periods and back again`() {
        val m = Period.monthOf(sat)
        assertEquals(LocalDate.of(2026, 9, 1), m.shift(1).start)
        assertEquals(LocalDate.of(2026, 7, 1), m.shift(-1).start)
        assertEquals(m, m.shift(1).shift(-1))
    }

    /** Nothing exists after today, and an empty screen reached by accident reads as a bug. */
    @Test
    fun `a period starting after today is refused`() {
        val next = Period.monthOf(sat).shift(1)
        assertTrue(next.startsAfter(sat))
        assertFalse(Period.monthOf(sat).startsAfter(sat))
    }

    @Test
    fun `labels say what the period is`() {
        assertEquals("August 2026", Period.monthOf(sat).label)
        assertEquals("10–16 Aug", Period.weekOf(sat).label)
        // A week spanning two months names both.
        assertEquals("31 Aug – 6 Sep", Period.weekOf(LocalDate.of(2026, 9, 2)).label)
        assertEquals("Since 12 May", Period.semesterFrom(LocalDate.of(2026, 5, 12), sat).label)
    }

    @Test
    fun `current picks the right period for each mode`() {
        val start = LocalDate.of(2026, 5, 12)
        assertEquals(PeriodMode.WEEK, Period.current(PeriodMode.WEEK, sat, start).mode)
        assertEquals(LocalDate.of(2026, 8, 1), Period.current(PeriodMode.MONTH, sat, start).start)
        assertEquals(start, Period.current(PeriodMode.SEMESTER, sat, start).start)
        // With no semester start known, it falls back to the start of this month rather
        // than to an invented academic date.
        assertEquals(
            LocalDate.of(2026, 8, 1),
            Period.current(PeriodMode.SEMESTER, sat, null).start,
        )
    }

    // ---- buckets: what one bar of the chart covers ----

    /** A week draws seven bars, one per day, Monday first. */
    @Test
    fun `a week buckets into seven days`() {
        val b = Period.weekOf(sat).buckets()
        assertEquals(7, b.size)
        assertEquals(LocalDate.of(2026, 8, 10), b.first().start)
        assertEquals(LocalDate.of(2026, 8, 16), b.last().start)
        // Every day is covered exactly once, with no gap and no overlap.
        b.zipWithNext().forEach { (a, next) -> assertEquals(a.endExclusive, next.start) }
    }

    /** A month draws weeks. August 2026 has 31 days, so five bars. */
    @Test
    fun `a month buckets into weeks`() {
        val b = Period.monthOf(sat).buckets()
        assertEquals(5, b.size)
        assertEquals(listOf("W1", "W2", "W3", "W4", "W5"), b.map { it.label })
        // ⚠ The last bucket is clipped to the month, not run past its end - otherwise
        // September's first days would be counted in August's chart.
        assertEquals(LocalDate.of(2026, 9, 1), b.last().endExclusive)
    }

    @Test
    fun `a semester buckets into months`() {
        val b = Period.semesterFrom(LocalDate.of(2026, 5, 12), sat).buckets()
        assertEquals(listOf("May", "Jun", "Jul", "Aug"), b.map { it.label })
        assertEquals(LocalDate.of(2026, 6, 1), b[0].endExclusive)
    }

    @Test
    fun `all time runs from the oldest row to today, and cannot be stepped`() {
        val oldest = LocalDate.of(2026, 5, 12)
        val all = Period.current(PeriodMode.ALL, sat, null, oldest)
        assertEquals(oldest, all.start)
        assertTrue(all.contains(sat))
        // Nowhere to step to.
        assertEquals(all, all.shift(1))
        assertEquals(all, all.shift(-1))
    }

    /**
     * ⚠ Nothing precedes everything. An empty previous range means no row matches it, so
     * every "vs last" column disappears rather than comparing against a fiction.
     */
    @Test
    fun `all time has no previous period`() {
        val all = Period.current(PeriodMode.ALL, sat, null, LocalDate.of(2026, 5, 12))
        val prev = all.previous()
        assertEquals(prev.start, prev.endExclusive)
        assertFalse(prev.contains(sat))
        assertFalse(prev.contains(LocalDate.of(2026, 5, 12)))
    }

    @Test
    fun `each mode buckets into the unit below it`() {
        assertEquals(BucketUnit.DAY, PeriodMode.WEEK.bucket)
        assertEquals(BucketUnit.WEEK, PeriodMode.MONTH.bucket)
        assertEquals(BucketUnit.MONTH, PeriodMode.SEMESTER.bucket)
        assertEquals(BucketUnit.MONTH, PeriodMode.ALL.bucket)
    }
}
