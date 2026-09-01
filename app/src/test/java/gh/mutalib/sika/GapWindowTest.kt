package gh.mutalib.sika

import gh.mutalib.sika.notify.GapAlert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The window a gap actually happened in.
 *
 * ⚠ **The bug this pins down was in shipped copy, and Mutalib's question found it.** He asked
 * how Sika could know about a message that never came; explaining the answer — the check fails
 * on the message *after* the missing one — made it obvious that "missing from 28 August" names
 * the day it was *caught*, not the day the money moved. Wrong by up to the whole gap between
 * two messages, and it would send someone hunting through the wrong day's memory.
 */
class GapWindowTest {

    private val accra: ZoneId = ZoneId.of("Africa/Accra")

    private fun millis(m: Int, d: Int, h: Int) =
        LocalDateTime.of(2026, m, d, h, 0).atZone(accra).toInstant().toEpochMilli()

    @Test
    fun `two different days give a range`() {
        assertEquals(
            "Between 26 Aug and 28 Aug,",
            GapAlert.window(millis(8, 26, 9), millis(8, 28, 14), accra),
        )
    }

    @Test
    fun `no earlier transaction gives an open start`() {
        // Happens when the very first checkable row is already a gap: there is nothing before
        // it, so the honest phrasing is one-sided rather than an invented start date.
        assertEquals("Before 28 Aug", GapAlert.window(null, millis(8, 28, 14), accra))
    }

    @Test
    fun `two messages on one day do not read as a bug`() {
        // "Between 28 Aug and 28 Aug" is a real window and looks like broken code.
        assertEquals(
            "On 28 Aug,",
            GapAlert.window(millis(8, 28, 9), millis(8, 28, 14), accra),
        )
    }

    @Test
    fun `the window can span months`() {
        assertEquals(
            "Between 30 Aug and 2 Sep,",
            GapAlert.window(millis(8, 30, 20), millis(9, 2, 8), accra),
        )
    }
}
