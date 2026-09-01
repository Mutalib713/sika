package gh.mutalib.sika

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
}
