package gh.mutalib.sika

import gh.mutalib.sika.data.Backup
import gh.mutalib.sika.data.Csv
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.Reconciler
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * PLAN task 18, red team half: deliberate attempts to make the ledger lie.
 *
 * Each test says in its name what it is trying to break. A test that passes here is a
 * property being *held*, not a feature being demonstrated — the point of writing them is that
 * the failures would be silent, and silent failures in a ledger are the only kind that matter.
 */
class RedTeamFindingsTest {

    private var nextId = 1L

    /** One row. Defaults are deliberately boring so each test's own numbers stand out. */
    private fun row(
        amount: Long,
        balanceAfter: Long?,
        direction: Direction = Direction.OUT,
        fee: Long = 0L,
        tax: Long? = 0L,
        at: Long = nextId * 1_000L,
    ) = TransactionEntity(
        id = nextId++,
        txId = "tx$nextId",
        occurredAt = at,
        direction = direction,
        shape = Shape.PAYMENT_MADE,
        amount = amount,
        fee = fee,
        tax = tax,
        counterparty = "XXX",
        reference = null,
        balanceAfter = balanceAfter,
        rawBody = "irrelevant",
    )

    // ================================================================ the arithmetic

    @Test
    fun `a missing message is caught, and costs exactly one flag`() {
        // Anchor at 10000, spend 1000 -> should be 9000, but 2000 vanished with no message.
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000),
            row(amount = 1_000, balanceAfter = 7_000),
            row(amount = 1_000, balanceAfter = 6_000),
        )
        val checks = Reconciler.reconcile(rows).associateBy { it.id }

        assertEquals(Reconciled.UNCHECKED, checks.getValue(1L).state)
        assertEquals(Reconciled.GAP, checks.getValue(2L).state)
        assertEquals("the chain must re-anchor, not stay red", Reconciled.OK, checks.getValue(3L).state)
        assertEquals(-2_000L, checks.getValue(2L).difference)
    }

    @Test
    fun `a row that states no balance defers the check rather than swallowing it`() {
        // The attack: hide a missing message behind a row with no balance in its text, hoping
        // the discrepancy is absorbed and never reported.
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000),
            row(amount = 1_000, balanceAfter = null),   // states nothing
            row(amount = 1_000, balanceAfter = 6_000),  // 2000 short of 8000
        )
        val checks = Reconciler.reconcile(rows).associateBy { it.id }

        assertEquals(Reconciled.UNCHECKED, checks.getValue(2L).state)
        assertEquals(
            "the error must surface at the next row that states a balance",
            Reconciled.GAP,
            checks.getValue(3L).state,
        )
        assertEquals(-2_000L, checks.getValue(3L).difference)
    }

    @Test
    fun `money in and money out move the balance in opposite directions`() {
        val rows = listOf(
            row(amount = 0, balanceAfter = 5_000),
            row(amount = 2_000, balanceAfter = 7_000, direction = Direction.IN),
            row(amount = 2_000, balanceAfter = 5_000, direction = Direction.OUT),
        )
        Reconciler.reconcile(rows).drop(1).forEach {
            assertEquals("a sign error would show up here first", Reconciled.OK, it.state)
        }
    }

    @Test
    fun `fee and tax both come out of the balance`() {
        // A fee counted twice, or not at all, is the most likely arithmetic slip in this app.
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000),
            row(amount = 1_000, fee = 50, tax = 25, balanceAfter = 8_925),
        )
        assertEquals(Reconciled.OK, Reconciler.reconcile(rows)[1].state)
    }

    @Test
    fun `a null tax is not treated as a different number from zero`() {
        // `tax = null` means the SMS wrote "-". It must behave as "nothing was taken", which
        // is arithmetically zero even though it is not the same fact.
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000),
            row(amount = 1_000, tax = null, balanceAfter = 9_000),
        )
        assertEquals(Reconciled.OK, Reconciler.reconcile(rows)[1].state)
    }

    @Test
    fun `review-queue rows are excluded from the walk entirely`() {
        // An unparsed row has every money field zero. If it entered the chain it would anchor
        // the running balance at zero and make every later row a gap.
        val unparsed = row(amount = 0, balanceAfter = null).copy(parsedOk = false)
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000),
            unparsed,
            row(amount = 1_000, balanceAfter = 9_000),
        )
        val checks = Reconciler.reconcile(rows)
        assertEquals("the unparsed row must not be checked at all", 2, checks.size)
        assertEquals(Reconciled.OK, checks[1].state)
    }

    @Test
    fun `two messages in the same second keep a stable order`() {
        // Android's SMS timestamp has one-second resolution, so a tie is real. Insertion order
        // is the tiebreak; without it the walk could reorder and invent a gap.
        val rows = listOf(
            row(amount = 0, balanceAfter = 10_000, at = 5_000),
            row(amount = 1_000, balanceAfter = 9_000, at = 9_000),
            row(amount = 1_000, balanceAfter = 8_000, at = 9_000),
        )
        Reconciler.reconcile(rows).drop(1).forEach { assertEquals(Reconciled.OK, it.state) }
        // And the reverse input order must produce the same verdicts, since the walk sorts.
        Reconciler.reconcile(rows.reversed()).forEach {
            assertNotEquals(Reconciled.GAP, it.state)
        }
    }

    // ================================================================ the CSV

    @Test
    fun `an unterminated quote does not silently merge every following row`() {
        // The attack: a corrupt export where one field opens a quote and never closes it.
        // A lenient parser would swallow the rest of the file into one field and restore a
        // ledger of one nonsense row while reporting success.
        val text = "SIKA BACKUP,1\n[transactions]\ntxId\n\"never closed\n1234,more\n"
        val parsed = Backup.read(text)
        // ⚠ It used to swallow the rest of the file into one field and report "1 row could
        // not be read" — on a 144-row file damaged at row 3, that number understates the loss
        // by two orders of magnitude and invites you to shrug. It is fatal now.
        assertEquals(0, parsed.transactions.size)
        assertTrue(
            "a damaged file must be refused whole, not counted as one bad row",
            parsed.fatal?.contains("damaged") == true,
        )
    }

    @Test
    fun `a CSV row with too few columns is reported, not padded`() {
        val text = "SIKA BACKUP,1\n[transactions]\ntxId\n1234,5678\n"
        val parsed = Backup.read(text)
        assertEquals(0, parsed.transactions.size)
        assertEquals(1, parsed.problems.size)
    }

    @Test
    fun `a field containing the section marker cannot fake a new section`() {
        // "[categories]" inside a quoted counterparty must stay data, not become a marker.
        val line = Csv.row(listOf("a", "[categories]", "b"))
        val back = Csv.parse(line).single()
        assertEquals("[categories]", back[1])
    }

    @Test
    fun `an absurd amount is refused rather than stored`() {
        // Long.MAX_VALUE pesewas would overflow the moment anything summed it with a fee.
        // The parser must reject what it cannot represent rather than wrap around.
        val huge = "99999999999999999999"
        val text = "SIKA BACKUP,1\n[transactions]\ntxId\n" +
            "tx1,1000,OUT,PAYMENT_MADE,$huge,0,0,XXX,,100,,NONE,,1,OK,body\n"
        val parsed = Backup.read(text)
        assertEquals("an unrepresentable amount must not become a transaction", 0, parsed.transactions.size)
        assertEquals(1, parsed.problems.size)
    }

    // ================================================================ time

    @Test
    fun `a week period does not lose the last day of a month`() {
        // Tiling time with half-open ranges is where off-by-one days hide, and a lost day is
        // money missing from a total with nothing to show it went anywhere.
        val week = Period.weekOf(LocalDate.of(2026, 8, 31))
        assertTrue(week.contains(LocalDate.of(2026, 8, 31)))
        assertTrue("a week must span the month boundary", week.contains(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `a month period covers its first and last day and nothing else`() {
        val feb = Period.monthOf(LocalDate.of(2026, 2, 14))
        assertTrue(feb.contains(LocalDate.of(2026, 2, 1)))
        assertTrue("a 28-day February must include the 28th", feb.contains(LocalDate.of(2026, 2, 28)))
        assertTrue(!feb.contains(LocalDate.of(2026, 3, 1)))
        assertTrue(!feb.contains(LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun `a leap February is 29 days, not 28`() {
        val feb = Period.monthOf(LocalDate.of(2028, 2, 14))
        assertTrue(feb.contains(LocalDate.of(2028, 2, 29)))
        assertTrue(!feb.contains(LocalDate.of(2028, 3, 1)))
    }

    @Test
    fun `the ALL period has no previous, and says so rather than inventing one`() {
        val all = Period.current(PeriodMode.ALL, LocalDate.of(2026, 9, 1), null, LocalDate.of(2026, 1, 1))
        val before = all.previous()
        assertTrue(
            "comparing all time against a fabricated earlier all time would be meaningless",
            !before.contains(LocalDate.of(2025, 6, 1)),
        )
    }

    // ================================================================ labels

    @Test
    fun `a label source ordering exists so a guess cannot outrank a decision`() {
        // Not arithmetic, but the same class of silent loss: a keyword guess overwriting a
        // hand-set label would erase a decision with no trace.
        assertNotEquals(LabelSource.MANUAL, LabelSource.AUTO_KEYWORD)
        assertNotEquals(LabelSource.PROMPT, LabelSource.AUTO_RULE)
        assertNull("a fresh row must carry no label at all", row(0, null).label)
        assertEquals(LabelSource.NONE, row(0, null).labelSource)
    }
}
