package gh.mutalib.sika

import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.notify.TermAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The date arithmetic behind the semester alerts, on a laptop with no phone attached.
 *
 * ⚠ **`endExclusiveDay` is the day AFTER the term, and that is the whole risk here.** Every
 * assertion below is written against a term whose *last day* is 30 September, so an off-by-one
 * shows up as a wrong day rather than as a vague "it fired sometime around then". Getting this
 * wrong would announce every semester's ending a day early, forever, and read like a rounding
 * bug in the dates rather than a bug in this file.
 */
class TermAlertTest {

    /** 1 Sep to 30 Sep inclusive. `endExclusive` is 1 October. */
    private val term = TermEntity(
        id = 7,
        name = "First semester, Year 2",
        startDay = LocalDate.of(2026, 9, 1).toEpochDay(),
        endExclusiveDay = LocalDate.of(2026, 10, 1).toEpochDay(),
    )

    private fun on(y: Int, m: Int, d: Int, told: Set<String> = emptySet()) =
        TermAlert.due(listOf(term), LocalDate.of(y, m, d), told)

    @Test fun `says nothing while the end is far away`() {
        assertNull(on(2026, 9, 1))
        assertNull(on(2026, 9, 22))
    }

    @Test fun `warns exactly a week before the last day`() {
        val due = on(2026, 9, 23)!!
        assertEquals(TermAlert.Kind.ENDING_SOON, due.kind)
        assertEquals(7L, due.daysLeft)
    }

    @Test fun `counts down to nothing on the last day itself`() {
        assertEquals(1L, on(2026, 9, 29)!!.daysLeft)
        val lastDay = on(2026, 9, 30)!!
        assertEquals(TermAlert.Kind.ENDING_SOON, lastDay.kind)
        assertEquals(0L, lastDay.daysLeft)
    }

    /** ⚠ 1 October is `endExclusive` — the first morning the term is over, not the last day. */
    @Test fun `reports the ending on the morning after the last day`() {
        assertEquals(TermAlert.Kind.ENDED, on(2026, 10, 1)!!.kind)
    }

    @Test fun `still reports an ending a few days late, so a phone in a drawer catches up`() {
        assertEquals(TermAlert.Kind.ENDED, on(2026, 10, 5)!!.kind)
    }

    /** Otherwise installing Sika would announce the end of every old semester on day one. */
    @Test fun `goes quiet once the ending is stale`() {
        assertNull(on(2026, 10, 20))
    }

    @Test fun `says each thing once`() {
        val warned = on(2026, 9, 25)!!
        assertEquals(TermAlert.Kind.ENDING_SOON, warned.kind)
        assertNull(on(2026, 9, 26, told = setOf(warned.key)))

        // ...and the ending is still said afterwards, because it is a different key.
        val ended = on(2026, 10, 1, told = setOf(warned.key))!!
        assertEquals(TermAlert.Kind.ENDED, ended.kind)
    }

    /** A term that has just ended is also "ending within the week"; the definite one wins. */
    @Test fun `prefers the ending over the warning when both apply`() {
        val next = term.copy(
            id = 8,
            name = "Second semester, Year 2",
            startDay = LocalDate.of(2026, 10, 1).toEpochDay(),
            endExclusiveDay = LocalDate.of(2026, 10, 6).toEpochDay(),
        )
        val due = TermAlert.due(listOf(term, next), LocalDate.of(2026, 10, 1))!!
        assertEquals(TermAlert.Kind.ENDED, due.kind)
        assertEquals(7L, due.term.id)
    }

    @Test fun `an empty list is quiet rather than crashing`() {
        assertNull(TermAlert.due(emptyList(), LocalDate.of(2026, 9, 30)))
    }

    // ---- the words -------------------------------------------------------------------

    /** ⚠ "ends in 1 days" and "ends in 0 days" are the two the plural helper cannot fix. */
    @Test fun `counts in words at the boundaries`() {
        assertEquals(
            "First semester, Year 2 ends today",
            TermAlert.title(on(2026, 9, 30)!!),
        )
        assertEquals(
            "First semester, Year 2 ends tomorrow",
            TermAlert.title(on(2026, 9, 29)!!),
        )
        assertEquals(
            "First semester, Year 2 ends in 7 days",
            TermAlert.title(on(2026, 9, 23)!!),
        )
        assertEquals(
            "First semester, Year 2 has ended",
            TermAlert.title(on(2026, 10, 1)!!),
        )
    }

    /** Quoting GHS 0.00 at someone as if it were a finding is the invented headline. */
    @Test fun `says nothing about money when there was none`() {
        assertNull(TermAlert.detail(on(2026, 9, 30)!!, spent = 0L))
        assertNull(TermAlert.detail(on(2026, 10, 1)!!, spent = 0L))
    }

    @Test fun `quotes the total when there is one`() {
        val line = TermAlert.detail(on(2026, 10, 1)!!, spent = 48_150L)
        assertEquals("You spent GHS 481.50 over it. Tap to look back at the whole semester.", line)
    }
}
