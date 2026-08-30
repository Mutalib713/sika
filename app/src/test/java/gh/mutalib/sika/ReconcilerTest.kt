package gh.mutalib.sika

import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.Reconciler
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The arithmetic behind Sacred Rule 3, tested on the JVM against handmade sequences.
 *
 * These matter more than they look. Reconciliation is the thing that turns "the app says
 * you spent GHS 640" into "the app says you spent GHS 640 *and can show its working*". If it
 * is wrong, it is worse than absent: it would either cry wolf on clean rows or stay silent
 * on real holes.
 */
class ReconcilerTest {

    @Test
    fun `a clean chain reconciles, using the real figures from shape 4`() {
        // Balance 107.91, then GHS 5.00 out with a 50p fee, leaving 102.41 — the arithmetic
        // from Mutalib's own "Payment made" message.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 500, fee = 50, balance = 10_791),
                row(id = 2, at = 200, dir = Direction.OUT, amount = 500, fee = 50, balance = 10_241),
            ),
        )
        // The first row has nothing before it, so it can never be checked.
        assertEquals(Reconciled.UNCHECKED, checks[0].state)
        assertEquals(Reconciled.OK, checks[1].state)
    }

    @Test
    fun `money in adds to the balance`() {
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 7_929),
                // GHS 100.00 received, no fee: 79.29 + 100.00 = 179.29
                row(id = 2, at = 200, dir = Direction.IN, amount = 10_000, fee = 0, balance = 17_929),
            ),
        )
        assertEquals(Reconciled.OK, checks[1].state)
    }

    @Test
    fun `fee and tax are both deducted`() {
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                // 100.00 − 10.00 − 0.50 − 0.25 = 89.25
                row(id = 2, at = 200, dir = Direction.OUT, amount = 1000, fee = 50, tax = 25, balance = 8_925),
            ),
        )
        assertEquals(Reconciled.OK, checks[1].state)
    }

    @Test
    fun `a missing message costs exactly one gap, never a cascade`() {
        // ⚠ The property that decides whether this feature is usable at all.
        //
        // MTN does not always send an SMS. If one transaction is missing, the row after the
        // hole cannot be explained — but every row after THAT must still reconcile, or a
        // single missing message on 12 June would paint the whole rest of the year red and
        // the warning would become noise nobody reads.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                // …a GHS 20.00 transaction happened here and MTN sent nothing…
                row(id = 2, at = 200, dir = Direction.OUT, amount = 1000, fee = 0, balance = 7_000),
                row(id = 3, at = 300, dir = Direction.OUT, amount = 1000, fee = 0, balance = 6_000),
                row(id = 4, at = 400, dir = Direction.OUT, amount = 500, fee = 0, balance = 5_500),
            ),
        )
        assertEquals(Reconciled.UNCHECKED, checks[0].state)
        assertEquals("the row after the hole cannot be explained", Reconciled.GAP, checks[1].state)
        assertEquals("but the chain must re-synchronise immediately", Reconciled.OK, checks[2].state)
        assertEquals(Reconciled.OK, checks[3].state)

        // And the gap says how far out it was: expected 90.00, actually 70.00.
        assertEquals(9_000L, checks[1].expected)
        assertEquals(7_000L, checks[1].actual)
        assertEquals(-2_000L, checks[1].difference)
    }

    @Test
    fun `a row with no stated balance is unchecked, not a gap`() {
        // Unknown is not the same as wrong. Flagging it would be crying wolf.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                row(id = 2, at = 200, dir = Direction.OUT, amount = 1000, fee = 0, balance = null),
                row(id = 3, at = 300, dir = Direction.OUT, amount = 1000, fee = 0, balance = 8_000),
            ),
        )
        assertEquals(Reconciled.UNCHECKED, checks[1].state)
        assertNull(checks[1].expected)
        // The anchor stays at the last KNOWN balance, so row 3 is measured from 100.00.
        assertEquals(Reconciled.OK, checks[2].state)
    }

    @Test
    fun `the comparison is exact, with no tolerance`() {
        // One pesewa out is out. A tolerance is exactly where a real discrepancy would hide.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                row(id = 2, at = 200, dir = Direction.OUT, amount = 1000, fee = 0, balance = 8_999),
            ),
        )
        assertEquals(Reconciled.GAP, checks[1].state)
        assertEquals(-1L, checks[1].difference)
    }

    @Test
    fun `review-queue rows are excluded entirely`() {
        // Unparsed rows carry zeroes for every money field. Including them would corrupt the
        // chain with figures that were never real.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                row(id = 2, at = 150, dir = Direction.OUT, amount = 0, fee = 0, balance = null, ok = false),
                row(id = 3, at = 200, dir = Direction.OUT, amount = 1000, fee = 0, balance = 9_000),
            ),
        )
        assertEquals("the unparsed row must not appear at all", 2, checks.size)
        assertEquals(Reconciled.OK, checks[1].state)
    }

    @Test
    fun `rows sharing a timestamp are ordered by insertion`() {
        // Android's SMS timestamp has one-second resolution, so two transactions can tie.
        // Insertion order is the best remaining guess at what happened first.
        val checks = Reconciler.reconcile(
            listOf(
                row(id = 3, at = 100, dir = Direction.OUT, amount = 1000, fee = 0, balance = 8_000),
                row(id = 1, at = 100, dir = Direction.OUT, amount = 0, fee = 0, balance = 10_000),
                row(id = 2, at = 100, dir = Direction.OUT, amount = 1000, fee = 0, balance = 9_000),
            ),
        )
        assertEquals(listOf(1L, 2L, 3L), checks.map { it.id })
        assertEquals(Reconciled.OK, checks[1].state)
        assertEquals(Reconciled.OK, checks[2].state)
    }

    private fun row(
        id: Long,
        at: Long,
        dir: Direction,
        amount: Long,
        fee: Long,
        balance: Long?,
        tax: Long? = null,
        ok: Boolean = true,
    ) = TransactionEntity(
        id = id,
        txId = "tx$id",
        occurredAt = at,
        direction = dir,
        shape = Shape.PAYMENT_MADE,
        amount = amount,
        fee = fee,
        tax = tax,
        counterparty = "test",
        reference = null,
        balanceAfter = balance,
        rawBody = "",
        parsedOk = ok,
    )
}
