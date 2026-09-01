package gh.mutalib.sika

import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.UNCATEGORISED
import gh.mutalib.sika.ledger.outflow
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The month report's arithmetic — PLAN task 13.
 *
 * Its verification step says the numbers must match arithmetic done by hand rather than
 * merely look plausible, so every expected value below is written out longhand in a comment
 * and the test asserts the total. A test that computes the expectation the same way the
 * code does would agree with a bug.
 */
class PeriodSummaryTest {

    private val accra: ZoneId = ZoneId.of("Africa/Accra")
    private val august = Period.monthOf(java.time.LocalDate.of(2026, 8, 15))

    /**
     * ⚠ The one that caught a real inconsistency. Before task 13, Home summed `amount` for
     * the month and `amount + fee` for today — two figures on the same card computed
     * differently. [Reconciler] settles it: MoMo's own stated balance only agrees with
     * `previous − amount − fee − tax`, so all three are the spend.
     */
    @Test
    fun `outflow counts the fee and the tax, because the balance does`() {
        // GHS 5.00 + 50p fee + 25p tax = GHS 5.75 = 575 pesewas
        val row = out(id = 1, day = 3, amount = 500, fee = 50, tax = 25)
        assertEquals(575L, row.outflow())
    }

    @Test
    fun `a missing tax is not a zero it is simply absent`() {
        // tax = null means MoMo wrote "-". It must not blow up, and must not invent a charge.
        assertEquals(550L, out(id = 1, day = 3, amount = 500, fee = 50, tax = null).outflow())
    }

    @Test
    fun `the four headline numbers, added up by hand`() {
        val rows = listOf(
            // OUT: 10.00 + 0.50 fee            = 1050
            out(id = 1, day = 2, amount = 1_000, fee = 50),
            // OUT: 20.00 + 0.00 fee            = 2000
            out(id = 2, day = 5, amount = 2_000, fee = 0),
            // IN:  100.00                      = 10000
            income(id = 3, day = 6, amount = 10_000, balance = 12_345),
        )
        val s = summarise(rows, august, accra)

        // 1050 + 2000 = 3050
        assertEquals(3_050L, s.moneyOut)
        assertEquals(10_000L, s.moneyIn)
        // 10000 − 3050 = 6950
        assertEquals(6_950L, s.net)
        // The newest stated balance in the month, not one we computed.
        assertEquals(12_345L, s.closingBalance)
        assertEquals(3, s.transactionCount)
    }

    @Test
    fun `rows from other months are excluded`() {
        val rows = listOf(
            out(id = 1, day = 5, amount = 1_000, fee = 0),
            out(id = 2, day = 5, amount = 9_999, fee = 0, month = 7),
            out(id = 3, day = 5, amount = 8_888, fee = 0, month = 9),
        )
        assertEquals(1_000L, summarise(rows, august, accra).moneyOut)
        assertEquals(1, summarise(rows, august, accra).transactionCount)
    }

    /** Review-queue placeholders are all-zero rows that exist to be looked at, not counted. */
    @Test
    fun `unparsed rows never reach a total`() {
        val rows = listOf(
            out(id = 1, day = 5, amount = 1_000, fee = 0),
            out(id = 2, day = 5, amount = 5_000, fee = 0, ok = false),
        )
        val s = summarise(rows, august, accra)
        assertEquals(1_000L, s.moneyOut)
        assertEquals(1, s.transactionCount)
    }

    @Test
    fun `categories are ranked biggest first and shares sum to one`() {
        val rows = listOf(
            out(id = 1, day = 2, amount = 1_000, fee = 0, label = "Transport"),
            out(id = 2, day = 3, amount = 6_000, fee = 0, label = "Food"),
            out(id = 3, day = 4, amount = 3_000, fee = 0, label = "Data"),
        )
        val s = summarise(rows, august, accra)

        assertEquals(listOf("Food", "Data", "Transport"), s.slices.map { it.label })
        // 6000 / 10000 = 0.6
        assertEquals(0.6f, s.slices[0].share, 0.0001f)
        assertEquals(1f, s.slices.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `unlabelled spending is its own slice, not dropped`() {
        val rows = listOf(
            out(id = 1, day = 2, amount = 1_000, fee = 0, label = "Food"),
            out(id = 2, day = 3, amount = 4_000, fee = 0, label = null),
        )
        val s = summarise(rows, august, accra)
        assertEquals(5_000L, s.moneyOut)
        assertEquals(UNCATEGORISED, s.slices[0].label)
        assertEquals(4_000L, s.slices[0].amount)
    }

    @Test
    fun `against last month, per category`() {
        val rows = listOf(
            // July: Food 17300
            out(id = 1, day = 10, amount = 17_300, fee = 0, label = "Food", month = 7),
            // August: Food 24000 → up 6700, which is +38.7% → rounds to 39
            out(id = 2, day = 10, amount = 24_000, fee = 0, label = "Food"),
        )
        val food = summarise(rows, august, accra).slices.first { it.label == "Food" }

        assertEquals(17_300L, food.previousAmount)
        assertEquals(6_700L, food.change)
        assertEquals(39, food.changePercent)
    }

    /**
     * ⚠ "Up from nothing" is not a percentage. Rendering it as 100% or ∞ would be inventing
     * a number, which is the one thing this app must not do.
     */
    @Test
    fun `a category that did not exist last month has no percentage`() {
        val rows = listOf(out(id = 1, day = 10, amount = 5_000, fee = 0, label = "Rent"))
        val rent = summarise(rows, august, accra).slices.first()

        assertNull("absent last month is not zero", rent.previousAmount)
        assertNull(rent.change)
        assertNull(rent.changePercent)
    }

    /**
     * The callout ranks by absolute pesewas, not percent: a category that went GHS 2 → GHS 4
     * is up 100% and means nothing next to Food up GHS 67.
     */
    @Test
    fun `the biggest change is the biggest in money, not in percent`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 200, fee = 0, label = "Printing", month = 7),
            out(id = 2, day = 1, amount = 400, fee = 0, label = "Printing"), // +200, +100%
            out(id = 3, day = 2, amount = 17_300, fee = 0, label = "Food", month = 7),
            out(id = 4, day = 2, amount = 24_000, fee = 0, label = "Food"), // +6700, +39%
        )
        assertEquals("Food", summarise(rows, august, accra).biggestChange?.label)
    }

    /**
     * At ~45 transactions a month one purchase can swing a small category 200–300%. The
     * amount and share are still reported; only the misleading ratio is withheld.
     */
    @Test
    fun `a percentage is withheld when last month's base was too small to mean anything`() {
        val rows = listOf(
            // GHS 3.00 last month — one purchase away from any percentage at all.
            out(id = 1, day = 10, amount = 300, fee = 0, label = "Printing", month = 7),
            // GHS 12.00 this month. Technically +300%, and technically meaningless.
            out(id = 2, day = 10, amount = 1_200, fee = 0, label = "Printing"),
        )
        val printing = summarise(rows, august, accra).slices.first { it.label == "Printing" }

        assertNull("300% off a GHS 3 base is noise, not a finding", printing.changePercent)
        // Nothing is hidden — the money is still reported in full.
        assertEquals(1_200L, printing.amount)
        assertEquals(900L, printing.change)
    }

    /**
     * If two categories moved by similar amounts there is no single story, and asserting
     * one would be the report inventing a finding.
     */
    @Test
    fun `no callout when two categories moved by similar amounts`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 10_000, fee = 0, label = "Food", month = 7),
            out(id = 2, day = 1, amount = 16_000, fee = 0, label = "Food"), // +6000
            out(id = 3, day = 2, amount = 10_000, fee = 0, label = "Data", month = 7),
            out(id = 4, day = 2, amount = 15_500, fee = 0, label = "Data"), // +5500, too close
        )
        assertNull(summarise(rows, august, accra).biggestChange)
    }

    @Test
    fun `a clear standout still gets its sentence`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 10_000, fee = 0, label = "Food", month = 7),
            out(id = 2, day = 1, amount = 26_000, fee = 0, label = "Food"), // +16000
            out(id = 3, day = 2, amount = 10_000, fee = 0, label = "Data", month = 7),
            out(id = 4, day = 2, amount = 11_000, fee = 0, label = "Data"), // +1000
        )
        assertEquals("Food", summarise(rows, august, accra).biggestChange?.label)
    }

    /**
     * ⚠ Caught on the real device on 2026-08-31, where nothing was labelled: the screen
     * announced "Uncategorised went down GHS 2402.00 this month. That's your biggest
     * change." Every figure in it was correct and it said nothing — it only means less
     * money moved. Uncategorised is an absence of information, not a category.
     */
    @Test
    fun `uncategorised is never reported as the biggest change`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 410_730, fee = 0, label = null, month = 7),
            out(id = 2, day = 1, amount = 170_530, fee = 0, label = null), // moved -240200
            out(id = 3, day = 2, amount = 10_000, fee = 0, label = "Food", month = 7),
            out(id = 4, day = 2, amount = 13_000, fee = 0, label = "Food"), // moved +3000
        )
        // Food wins despite being ~80x smaller, because Uncategorised cannot be the story.
        assertEquals("Food", summarise(rows, august, accra).biggestChange?.label)
    }

    @Test
    fun `with nothing labelled there is no story at all`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 410_730, fee = 0, label = null, month = 7),
            out(id = 2, day = 1, amount = 170_530, fee = 0, label = null),
        )
        val s = summarise(rows, august, accra)
        assertNull(s.biggestChange)
        assertTrue("the breakdown must not be drawn", s.tooLittleLabelledToBreakDown)
    }

    @Test
    fun `a well-labelled month does draw its breakdown`() {
        val rows = listOf(
            out(id = 1, day = 1, amount = 9_000, fee = 0, label = "Food"),
            out(id = 2, day = 2, amount = 1_000, fee = 0, label = null), // 10% unlabelled
        )
        assertFalse(summarise(rows, august, accra).tooLittleLabelledToBreakDown)
    }

    @Test
    fun `a first month has nothing to compare against`() {
        val rows = listOf(out(id = 1, day = 5, amount = 1_000, fee = 0, label = "Food"))
        val s = summarise(rows, august, accra)
        assertFalse(s.hasPrevious)
        assertNull(s.biggestChange)
    }

    /** The honesty note: money this report genuinely cannot explain. */
    @Test
    fun `unlabelled cash-outs are totalled separately`() {
        val rows = listOf(
            out(id = 1, day = 2, amount = 20_000, fee = 0, shape = Shape.CASH_OUT, label = null),
            // Labelled, so it IS explained and must not appear in the note.
            out(id = 2, day = 3, amount = 5_000, fee = 0, shape = Shape.CASH_OUT, label = "Food"),
            // Not a cash-out.
            out(id = 3, day = 4, amount = 1_000, fee = 0, label = null),
        )
        assertEquals(20_000L, summarise(rows, august, accra).unlabelledCashOut)
    }

    @Test
    fun `an empty month says so rather than dividing by zero`() {
        val s = summarise(emptyList(), august, accra)
        assertTrue(s.isEmpty)
        assertEquals(0L, s.moneyOut)
        assertEquals(0L, s.net)
        assertNull(s.closingBalance)
        assertTrue(s.slices.isEmpty())
    }

    // ---- helpers ----

    private fun out(
        id: Long,
        day: Int,
        amount: Long,
        fee: Long,
        tax: Long? = null,
        label: String? = null,
        shape: Shape = Shape.PAYMENT_MADE,
        month: Int = 8,
        ok: Boolean = true,
    ) = row(id, day, month, Direction.OUT, amount, fee, tax, label, shape, null, ok)

    private fun income(id: Long, day: Int, amount: Long, balance: Long?, month: Int = 8) =
        row(id, day, month, Direction.IN, amount, 0, null, null, Shape.PAYMENT_RECEIVED, balance, true)

    private fun row(
        id: Long,
        day: Int,
        month: Int,
        dir: Direction,
        amount: Long,
        fee: Long,
        tax: Long?,
        label: String?,
        shape: Shape,
        balance: Long?,
        ok: Boolean,
    ) = TransactionEntity(
        id = id,
        txId = "tx$id",
        // Midday, so no plausible zone shift can move a row into a neighbouring month and
        // make these tests fail for a reason that has nothing to do with the arithmetic.
        occurredAt = LocalDateTime.of(2026, month, day, 12, 0)
            .atZone(accra).toInstant().toEpochMilli(),
        direction = dir,
        shape = shape,
        amount = amount,
        fee = fee,
        tax = tax,
        counterparty = "test",
        reference = null,
        balanceAfter = balance,
        label = label,
        rawBody = "",
        parsedOk = ok,
    )
}
