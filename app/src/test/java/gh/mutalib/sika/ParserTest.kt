package gh.mutalib.sika

import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.MomoParser
import gh.mutalib.sika.parser.ParseResult
import gh.mutalib.sika.parser.ParsedTransaction
import gh.mutalib.sika.parser.Shape
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.parser.parseMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The golden suite. It only ever grows.**
 *
 * Every message below is a real MTN MoMo SMS from Mutalib's own inbox, supplied
 * 2026-08-30, with names and numbers replaced by him before they were shared. The wording,
 * spacing and punctuation are untouched — that is the whole value of them.
 *
 * **The rule, from PROFILE.md § 12:** when PLAN task 5 sweeps the real inbox and finds a
 * shape nobody has seen, **the test is written here first** and only then is the parser
 * changed to pass it. A parser fixed without a test breaks the same way again.
 */
class ParserTest {

    // ---------------------------------------------------------------- the four shapes

    private val airtime =
        "Your payment of GHS 10.00 to MTN AIRTIME has been completed at 2026-06-10 21:00:02. " +
            "Your new balance: GHS 90.57. Fee was GHS 0.00 Tax was GHS -. Reference: -. " +
            "Financial Transaction Id: 83077174642. External Transaction Id: 83077174642." +
            "Download the MoMo App for a Faster & Easier Experience Click here: " +
            "https://mtnmymomo.onelink.me/XJOt/MoMo"

    private val cashOut =
        "Cash Out made for GHS20.00 to 000. Current Balance: GHS9.79 " +
            "Financial Transaction Id: 87482945712. Cash-out fee is charged automatically from " +
            "your MTN MoMo wallet. Please do not pay any fees to the Agent. Thank you for using " +
            "MTN MobileMoney. Fee charged: GHS0.50."

    private val received =
        "Payment received for GHS 100.00 from Aaa  Current Balance: GHS 179.29 . " +
            "Available Balance: GHS 179.29. Reference: 1. Transaction ID: 88139850923. " +
            "TRANSACTION FEE: 0.00"

    private val sent =
        "Payment made for GHS 5.00 to bbb Current Balance: GHS 102.41 . " +
            "Available Balance: GHS 102.41. Reference: 1. Transaction ID: 88336392260. " +
            "Fee charged: GHS0.50 Tax charged: 0. Download the MoMo App for a Faster & Easier " +
            "Experience. Click here: https://mtnmymomo.onelink.me/XJOt/MoMo"

    @Test
    fun `shape 1 - airtime purchase`() {
        val t = parsed(airtime)
        assertEquals(Shape.BILL_AIRTIME, t.shape)
        assertEquals(Direction.OUT, t.direction)
        assertEquals(1000L, t.amount)          // GHS 10.00
        assertEquals(0L, t.fee)
        assertEquals(9057L, t.balanceAfter)    // GHS 90.57
        assertEquals("MTN AIRTIME", t.counterparty)
        assertEquals("83077174642", t.txId)
        // `Tax was GHS -.` — a dash is not zero, and must not become zero.
        assertNull(t.tax)
        // `Reference: -.` — same reasoning.
        assertNull(t.reference)
    }

    @Test
    fun `shape 2 - cash out`() {
        val t = parsed(cashOut)
        assertEquals(Shape.CASH_OUT, t.shape)
        assertEquals(Direction.OUT, t.direction)
        // ⚠ No space after GHS in this shape, unlike shape 1.
        assertEquals(2000L, t.amount)
        // ⚠ The fee sits at the very end, after a paragraph of boilerplate, and its
        // trailing full stop is what crashed the first parser written against real data.
        assertEquals(50L, t.fee)
        assertEquals(979L, t.balanceAfter)
        assertEquals("000", t.counterparty)
        assertEquals("87482945712", t.txId)
        assertNull(t.reference)                // this shape has no Reference field at all
    }

    @Test
    fun `shape 3 - payment received`() {
        val t = parsed(received)
        assertEquals(Shape.PAYMENT_RECEIVED, t.shape)
        assertEquals(Direction.IN, t.direction)
        assertEquals(10_000L, t.amount)        // GHS 100.00
        // ⚠ `TRANSACTION FEE: 0.00` — capitals, and no GHS prefix.
        assertEquals(0L, t.fee)
        assertEquals(17_929L, t.balanceAfter)
        // ⚠ Two spaces between the name and "Current Balance" in the real message.
        assertEquals("Aaa", t.counterparty)
        // ⚠ Labelled `Transaction ID`, not `Financial Transaction Id`.
        assertEquals("88139850923", t.txId)
        assertEquals("1", t.reference)
    }

    @Test
    fun `shape 4 - payment made`() {
        val t = parsed(sent)
        assertEquals(Shape.PAYMENT_MADE, t.shape)
        assertEquals(Direction.OUT, t.direction)
        assertEquals(500L, t.amount)           // GHS 5.00
        // ⚠ `GHS 5.00` and `GHS0.50` in the SAME message. The space is not reliable.
        assertEquals(50L, t.fee)
        assertEquals(10_241L, t.balanceAfter)
        assertEquals("bbb", t.counterparty)
        assertEquals("88336392260", t.txId)
        assertEquals("1", t.reference)
        // `Tax charged: 0.` — here it IS zero, unlike shape 1's dash.
        assertEquals(0L, t.tax)
    }

    // ------------------------- the six shapes the first real sweep found (PLAN task 5b)

    /*
     * Real messages from Mutalib's inbox, captured 2026-08-30. **Business names, phone
     * numbers and his MoMo account number are redacted** — everything that decides parsing
     * (wording, spacing, punctuation, amounts, ids) is exactly as MTN sent it.
     */

    @Test
    fun `shape 5 - Payment for, the commonest outgoing shape`() {
        // ×20 in the real inbox, and none of them were being recorded.
        val t = parsed(
            "Payment for GHS5.00 to Other Networks  .Current Balance: GHS 44.79. " +
                "Transaction Id: 87543030119. Fee charged: GHS0.00,Tax Charged 0." +
                "Download the MoMo App for a Faster & Easier Experience.",
        )
        assertEquals(Shape.PAYMENT_FOR, t.shape)
        assertEquals(Direction.OUT, t.direction)
        assertEquals(500L, t.amount)
        assertEquals(0L, t.fee)
        assertEquals(4479L, t.balanceAfter)
        assertEquals("Other Networks", t.counterparty)
        // ⚠ A THIRD capitalisation of the id label: `Transaction Id`.
        assertEquals("87543030119", t.txId)
        // ⚠ `Tax Charged 0` — no colon at all, unlike `Tax charged: 0` in shape 4.
        assertEquals(0L, t.tax)
    }

    @Test
    fun `shape 5 - a payee whose own name contains a full stop`() {
        // `Bills.INV ..Current Balance` — two dots, and one of them belongs to the payee.
        val t = parsed(
            "Payment for GHS25.00 to Bills.INV ..Current Balance: GHS 149.29. " +
                "Transaction Id: 88216564395. Fee charged: GHS0.00,Tax Charged 0.",
        )
        assertEquals("Bills.INV", t.counterparty)
        assertEquals(2500L, t.amount)
        assertEquals(14_929L, t.balanceAfter)
    }

    @Test
    fun `shape 6 - Cash In, money arriving`() {
        val t = parsed(
            "Cash In received for GHS 100.00 from AGENT NAME REDACTED. " +
                "Current Balance GHS 102.07 Available Balance GHS 102.07. " +
                "Transaction ID: 83681775075. Fee charged: GHS 0. " +
                "Cash in (Deposit) is a free transaction on MTN Mobile Money.",
        )
        assertEquals(Shape.CASH_IN, t.shape)
        assertEquals(Direction.IN, t.direction)
        assertEquals(10_000L, t.amount)
        assertEquals(0L, t.fee)
        // ⚠ `Current Balance GHS 102.07` — NO COLON. This is why the shared balance
        // pattern missed all seven of these.
        assertEquals(10_207L, t.balanceAfter)
        assertEquals("AGENT NAME REDACTED", t.counterparty)
    }

    @Test
    fun `shape 7 - a FAILED payment moved no money and is never a transaction`() {
        // ⚠ The most dangerous find of the sweep. Nine of these. Shape 1's exact wording
        // apart from `has failed` in place of `has been completed` — and they carry a real
        // amount AND a real transaction id, so the looksLikeMoney test alone would have
        // filed them as reviewable money.
        val r = MomoParser.parse(
            "Your payment of GHS 5.00 to MTN AIRTIME  has failed at 2026-07-15 15:47:43. " +
                "Reference: -. Financial Transaction Id: 85436498944. " +
                "External Transaction Id: 85436498944. TRANSACTION FEE IS 0",
        )
        assertTrue("a failed payment must never be counted, got $r", r is ParseResult.NotATransaction)
    }

    @Test
    fun `shape 8 - an exceeded limit is a failure, not income`() {
        val r = MomoParser.parse(
            "You have exceeded your daily transaction limit. INTEROPERABILITY PULL OVA " +
                "failed to send GHS 990.00 to your account. Go to my wallet to check your " +
                "wallet limit. Financial transaction Id: 86187286967. Transaction Fee is 0.",
        )
        // GHS 990 that never arrived. Counting it would invent income out of nothing.
        assertTrue("expected NotATransaction, got $r", r is ParseResult.NotATransaction)
    }

    @Test
    fun `shape 9 - transferred, with the balance written backwards`() {
        val t = parsed(
            "You have transferred GHS 10.00  to Other Networks   from your mobile money " +
                "account 000000000 at 2026-07-19 09:37:20. Your new balance: 4652.89 GHS. " +
                "Message from sender: airtime:Telecel:0000000000:6. Message to receiver: . " +
                "Financial Transaction Id: 85672169720.",
        )
        assertEquals(Shape.TRANSFER, t.shape)
        assertEquals(Direction.OUT, t.direction)
        assertEquals(1000L, t.amount)
        assertEquals("Other Networks", t.counterparty)
        // ⚠ `4652.89 GHS` — the number comes FIRST here. Every other shape writes
        // `GHS 4652.89`, so the normal pattern reads this as no balance at all.
        assertEquals(465_289L, t.balanceAfter)
    }

    @Test
    fun `shape 10 - MoMoPay at a merchant, amount with no decimals`() {
        val t = parsed(
            "Y'ello. You have Paid GHS 40 to Merchant 104901 on your mobile money account " +
                "at 202608184348. Message from sender: 1. Your new balance: GHS 106.39 . " +
                "Fee was GHS 0.50 . Financial Transaction Id: 84997229749.",
        )
        assertEquals(Shape.MERCHANT_PAY, t.shape)
        assertEquals(Direction.OUT, t.direction)
        // ⚠ `GHS 40` with no decimal part — forty cedis, not forty pesewas.
        assertEquals(4000L, t.amount)
        assertEquals(50L, t.fee)
        assertEquals(10_639L, t.balanceAfter)
        assertEquals("Merchant 104901", t.counterparty)
    }

    // ------------------------------------------------------- the landmines, on their own

    @Test
    fun `trailing full stop is not swallowed into the number`() {
        // The original failure, isolated: "GHS0.50." must be 50 pesewas, not a crash.
        assertEquals(50L, parsed(cashOut).fee)
    }

    @Test
    fun `a dash where a number belongs is null, never zero`() {
        // Sacred Rule 7 in miniature: unreadable is not the same as absent, and neither is
        // the same as zero. A tax of zero and a tax nobody stated are different facts.
        assertNull(parseMoney("-"))
        assertNull(parseMoney(null))
        assertNull(parseMoney(""))
    }

    @Test
    fun `money is pesewas, exactly`() {
        assertEquals(1000L, parseMoney("10.00"))
        assertEquals(2000L, parseMoney("20"))        // no decimal part at all
        assertEquals(1050L, parseMoney("10.5"))      // one digit means tenths, so 50p
        assertEquals(50L, parseMoney("0.50"))
        assertEquals(100_000L, parseMoney("1,000.00")) // thousands separator
        assertNull(parseMoney("0.50."))              // the landmine itself
        assertNull(parseMoney("abc"))
    }

    @Test
    fun `pesewas arithmetic is exact where doubles are not`() {
        // Why money is a Long. As Doubles, 0.1 + 0.2 != 0.3 and reconciliation would need
        // a tolerance — which is precisely what lets a real discrepancy hide.
        assertEquals(30L, parseMoney("0.10")!! + parseMoney("0.20")!!)
        assertTrue(0.1 + 0.2 != 0.3)

        // The reconciliation sum itself, on shape 4's real figures:
        // balance before (107.91) − amount (5.00) − fee (0.50) == balance after (102.41)
        assertEquals(10_241L, 10_791L - 500L - 50L)
    }

    @Test
    fun `renders the way MoMo writes it`() {
        assertEquals("GHS 10.00", 1000L.asCedis())
        assertEquals("GHS 0.50", 50L.asCedis())
        assertEquals("GHS 1000.00", 100_000L.asCedis())
    }

    // ------------------------------------------------------------- refusing to guess

    @Test
    fun `an unknown shape that carries money goes to the review queue`() {
        // Amount and transaction id present, but no shape matches. This is the case the
        // review queue exists for — it might be money, so a human must look.
        val r = MomoParser.parse(
            "Reversal of GHS 30.00 completed. Transaction ID: 12345678901. New balance GHS 60.00",
        )
        assertTrue("expected Unrecognised, got $r", r is ParseResult.Unrecognised)
    }

    /**
     * ⚠ **The real messages from the first sweep of Mutalib's inbox, 2026-08-30.**
     *
     * 465 messages matched a MoMo-ish sender and only 118 were transactions. These four are
     * why: MTN sends OTPs, fraud warnings and adverts from the same senders. Treating them
     * as "could not read" buried the review queue under 347 adverts.
     *
     * Note the second one especially — it quotes **GHS 1,800**. An amount alone cannot be
     * the test for whether something is money; it needs a transaction id too.
     */
    @Test
    fun `otps and adverts are not transactions and are never queued`() {
        val notMoney = listOf(
            "<#> FRAUD ALERT: This OTP gives access to your wallet. Anyone asking for this " +
                "code is trying to fraudulently gain access to your funds. DO NOT SHARE IT " +
                "OR YOUR PIN WITH ANYONE. MTN",
            "Dear customer, enjoy an overdraft of up to GHS 1,800 for your MoMo transactions. " +
                "Opt in to MoMo Boost by dialing *170# option 5 > 3 > 4 or using the MoMo App.",
            "Go cashless with MoMoPay. Pay any merchant using ID or QR Code and get 300MB FREE. " +
                "Dial *170#, select MoMoPay or use the MoMoApp",
            "Need more data? Upgrade to a bigger Just4U bundle get more data at a better price.",
        )
        for (body in notMoney) {
            val r = MomoParser.parse(body)
            assertTrue(
                "should be NotATransaction, got $r for: ${body.take(50)}",
                r is ParseResult.NotATransaction,
            )
        }
    }

    @Test
    fun `a matching shape with no transaction id is refused`() {
        // Dedupe is keyed on the id (Sacred Rule 4). Without one the inbox sweep would
        // re-insert this row on every launch, so the review queue is the only safe home.
        val r = MomoParser.parse("Payment made for GHS 5.00 to bbb Current Balance: GHS 102.41 .")
        assertTrue("expected Unrecognised, got $r", r is ParseResult.Unrecognised)
    }

    @Test
    fun `an empty message is not a transaction, not a review item`() {
        // It has no amount and no id, so it is noise rather than unreadable money. This
        // assertion was `Unrecognised` until the first real sweep split the two cases —
        // an empty body has nothing for a human to review.
        assertTrue(MomoParser.parse("") is ParseResult.NotATransaction)
    }

    private fun parsed(body: String): ParsedTransaction {
        val r = MomoParser.parse(body)
        assertTrue("parser refused a known-good message: $r", r is ParseResult.Parsed)
        return (r as ParseResult.Parsed).transaction
    }
}
