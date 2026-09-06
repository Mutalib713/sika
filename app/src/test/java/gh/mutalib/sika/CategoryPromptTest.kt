package gh.mutalib.sika

import gh.mutalib.sika.notify.CategoryPrompt
import gh.mutalib.sika.parser.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure logic behind the category prompt — PLAN task 12, broadened 2026-09-06.
 *
 * Everything else in [CategoryPrompt] talks to Android's NotificationManager and belongs on
 * the phone. These are the parts that can be wrong *arithmetically*, which is exactly the
 * kind of wrong that looks fine in review.
 */
class CategoryPromptTest {

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
        val codes = (0..3).map { CategoryPrompt.requestCode(rowId, it) }
        assertEquals("all four slots must be distinct", 4, codes.toSet().size)
    }

    /** Two different rows must never collide, or answering one dismisses the other. */
    @Test
    fun differentRowsDoNotShareRequestCodes() {
        val a = (0..3).map { CategoryPrompt.requestCode(7L, it) }
        val b = (0..3).map { CategoryPrompt.requestCode(8L, it) }
        assertTrue("row 7 and row 8 must not overlap", a.intersect(b.toSet()).isEmpty())
    }

    /**
     * The notification id is the row id, so answering cancels the right prompt and a
     * re-shown prompt replaces rather than stacks.
     */
    @Test
    fun notificationIdIsTheRowId() {
        assertEquals(42, CategoryPrompt.notificationId(42L))
        assertEquals(1, CategoryPrompt.notificationId(1L))
    }

    // ------------------------------------------------------------------------ the wording

    /**
     * A cash-out and a payment are different questions and must not read alike.
     *
     * ⚠ **The counterparty is deliberately absent from a cash-out's headline.** It is the
     * agent who handed over the notes, so "GHS 20.00 to MTN AGENT" would read as though the
     * agent were the shop — naming the one party the answer is definitely not about.
     */
    @Test
    fun aCashOutSaysWhatHappenedAndAPaymentSaysWhereItWent() {
        assertEquals(
            "GHS 20.00 cashed out",
            CategoryPrompt.headline(2_000L, Shape.CASH_OUT, "MTN AGENT 054"),
        )
        assertEquals(
            "GHS 12.50 to MELCOM",
            CategoryPrompt.headline(1_250L, Shape.MERCHANT_PAY, "MELCOM"),
        )
    }

    /**
     * A message with no recipient in it still has to produce a sentence. "GHS 5.00 to "
     * would look like the app had lost half the notification.
     */
    @Test
    fun aPaymentWithNoRecipientStillReadsAsEnglish() {
        assertEquals(
            "GHS 5.00 spent",
            CategoryPrompt.headline(500L, Shape.PAYMENT_FOR, ""),
        )
    }

    // ---------------------------------------------------------------- learning a shop

    /**
     * ⚠ **A cash-out must never offer "Always", and this is the exclusion that matters.**
     *
     * A learned rule is keyed on the counterparty. For a shop that is the shop; for a
     * cash-out it is the *agent who handed over the notes*, and Mutalib uses the same agent
     * for whatever he happens to need cash for. One tap would file every future cash-out
     * from that agent as Food — silently, and looking for all the world like the app had
     * learned something useful.
     */
    @Test
    fun aCashOutNeverOffersToLearnTheAgent() {
        assertFalse(CategoryPrompt.offersAlways(Shape.CASH_OUT, "MTN AGENT 054"))
    }

    /** A shop repeats, so a rule is exactly the right thing to offer. */
    @Test
    fun aShopPaymentOffersToLearnTheShop() {
        assertTrue(CategoryPrompt.offersAlways(Shape.MERCHANT_PAY, "MELCOM"))
        assertTrue(CategoryPrompt.offersAlways(Shape.PAYMENT_FOR, "ECG PREPAID"))
    }

    /**
     * Nothing to key a rule on. A rule stored under the empty string would claim every
     * unparsed row in the review queue, since a blank counterparty is what an unreadable
     * message looks like.
     */
    @Test
    fun aPaymentWithNoCounterpartyOffersNothingToLearn() {
        assertFalse(CategoryPrompt.offersAlways(Shape.PAYMENT_FOR, ""))
    }

    /**
     * The small line settles what the headline cannot: "GHS 5.00 to MTN" could be airtime,
     * a bundle or a bill, and the point of asking in the shade is that it is answerable
     * without opening anything.
     */
    @Test
    fun theSubTextSaysWhatKindOfTransactionItWas() {
        assertEquals("Airtime or bundle", CategoryPrompt.subText(Shape.BILL_AIRTIME, "MTN"))
        assertEquals("Paid at a till", CategoryPrompt.subText(Shape.MERCHANT_PAY, "MELCOM"))
        // The cash-out is the one shape that names its counterparty here instead.
        assertEquals("MTN AGENT 054", CategoryPrompt.subText(Shape.CASH_OUT, "MTN AGENT 054"))
        assertEquals("MoMo agent", CategoryPrompt.subText(Shape.CASH_OUT, ""))
    }
}
