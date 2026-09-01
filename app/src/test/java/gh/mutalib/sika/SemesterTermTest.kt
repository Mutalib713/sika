package gh.mutalib.sika

import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The semester period, now that a real term can be given instead of guessed.
 *
 * ⚠ **The guess it replaces was wrong in a specific way worth naming.** `SEMESTER` ran from
 * the oldest transaction on record, which is not the start of a term — it is the date this
 * phone first received a MoMo text. On a phone that has had MoMo for two years, "this
 * semester" meant two years.
 */
class SemesterTermTest {

    private val today = LocalDate.of(2026, 9, 1)

    @Test
    fun `a given term start is used`() {
        val term = LocalDate.of(2026, 1, 12)
        val period = Period.current(PeriodMode.SEMESTER, today, semesterStart = term)
        assertEquals(term, period.start)
    }

    @Test
    fun `the period includes today`() {
        // endExclusive is tomorrow, so this morning's transactions are inside it. A range
        // that quietly excluded today would be wrong in the way nobody notices.
        val period = Period.current(PeriodMode.SEMESTER, today, LocalDate.of(2026, 1, 12))
        assertEquals(today.plusDays(1), period.endExclusive)
        assertEquals(true, period.contains(today))
    }

    @Test
    fun `with no term given it falls back rather than refusing`() {
        // "I don't know the dates yet" is a real answer — someone installing in the holidays
        // has no term to name — so the old behaviour has to survive as the fallback.
        val period = Period.current(PeriodMode.SEMESTER, today, semesterStart = null)
        assertEquals(LocalDate.of(2026, 9, 1), period.start)
    }

    @Test
    fun `a term from a previous year still spans correctly`() {
        val term = LocalDate.of(2025, 9, 15)
        val period = Period.current(PeriodMode.SEMESTER, today, term)
        assertEquals(term, period.start)
        assertEquals(true, period.contains(LocalDate.of(2026, 3, 4)))
        assertEquals(false, period.contains(LocalDate.of(2025, 9, 14)))
    }
}
