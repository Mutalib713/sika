package gh.mutalib.sika

import gh.mutalib.sika.notify.CashOutPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure logic behind the cash-out prompt — PLAN task 12.
 *
 * Everything else in [CashOutPrompt] talks to Android's NotificationManager and belongs on
 * the phone. These are the parts that can be wrong *arithmetically*, which is exactly the
 * kind of wrong that looks fine in review.
 */
class CashOutPromptTest {

    /**
     * ⚠ The real bug this exists for: `PendingIntent` matches on requestCode and the Intent,
     * **and does not compare extras**. Three action buttons sharing a request code would
     * therefore all deliver whichever extras were registered first — every button on the
     * notification labelling the row "Food", silently and for every cash-out forever.
     */
    @Test
    fun everyButtonOnOneNotificationGetsItsOwnRequestCode() {
        val rowId = 42L
        // Three category buttons plus the body tap-through, which is slot 3.
        val codes = (0..3).map { CashOutPrompt.requestCode(rowId, it) }
        assertEquals("all four slots must be distinct", 4, codes.toSet().size)
    }

    /** Two different rows must never collide, or answering one dismisses the other. */
    @Test
    fun differentRowsDoNotShareRequestCodes() {
        val a = (0..3).map { CashOutPrompt.requestCode(7L, it) }
        val b = (0..3).map { CashOutPrompt.requestCode(8L, it) }
        assertTrue("row 7 and row 8 must not overlap", a.intersect(b.toSet()).isEmpty())
    }

    /**
     * The notification id is the row id, so answering cancels the right prompt and a
     * re-shown prompt replaces rather than stacks.
     */
    @Test
    fun notificationIdIsTheRowId() {
        assertEquals(42, CashOutPrompt.notificationId(42L))
        assertEquals(1, CashOutPrompt.notificationId(1L))
    }
}
