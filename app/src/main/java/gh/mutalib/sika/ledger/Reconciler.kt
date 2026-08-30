package gh.mutalib.sika.ledger

import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction

/**
 * **The signature move. Sacred Rule 3.**
 *
 * Every MoMo message states the balance afterwards, which means the ledger can check its own
 * arithmetic instead of asking to be trusted. Walk the rows oldest-first and each one has to
 * explain the balance before it:
 *
 * ```
 * money out:  previous − amount − fee − tax  ==  balanceAfter
 * money in:   previous + amount − fee − tax  ==  balanceAfter
 * ```
 *
 * Exact, not approximate, because money is integer pesewas. A tolerance would be the place a
 * real discrepancy hides.
 *
 * **A missing message costs exactly one GAP, not a cascade.** When MTN sends no SMS for a
 * transaction, the row after the hole disagrees with the row before it — but that row's own
 * stated balance then becomes the anchor for the next check, so the chain re-synchronises
 * immediately. One hole, one flag.
 *
 * Pure Kotlin over a plain list, with no Android or database import, so the whole thing is
 * testable on the JVM against handmade sequences.
 */
object Reconciler {

    fun reconcile(rows: List<TransactionEntity>): List<Check> {
        // Oldest first. `id` breaks ties, because two messages can share a second and
        // Android's timestamp has no finer resolution — insertion order is the best
        // remaining guess at what really happened first.
        val ordered = rows
            .filter { it.parsedOk }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))

        val checks = mutableListOf<Check>()

        // A balance carried forward through every transaction, whether or not that
        // transaction stated one. Null until the ledger's first stated balance gives it
        // something to start from.
        var running: Long? = null

        for (row in ordered) {
            val actual = row.balanceAfter

            if (running == null) {
                // The very first row with a balance can never be checked — there is nothing
                // earlier to check it against, and inventing one would fabricate the
                // evidence. It becomes the anchor instead.
                checks += Check(row.id, Reconciled.UNCHECKED, null, actual)
                running = actual
                continue
            }

            // ⚠ **Applied to every row, including ones that state no balance of their own.**
            //
            // An earlier version only compared against the last *stated* balance and forgot
            // the amounts in between, so a transaction with no balance in its text made the
            // next row look wrong by exactly that amount. A false gap is worse than a missed
            // one: it teaches you to ignore the warning.
            val delta = if (row.direction == Direction.IN) row.amount else -row.amount
            val expected = running + delta - row.fee - (row.tax ?: 0L)

            checks += when (actual) {
                // Nothing stated to compare against. Not wrong — unknown. The running figure
                // carries on so later rows are still measured from the right place.
                null -> Check(row.id, Reconciled.UNCHECKED, null, null)
                else -> Check(
                    id = row.id,
                    state = if (expected == actual) Reconciled.OK else Reconciled.GAP,
                    expected = expected,
                    actual = actual,
                )
            }

            // ⚠ Re-anchor on what MoMo actually said, even when the check just failed.
            // Carrying the computed figure forward instead would turn one missing message
            // into every later row being wrong — and a permanently red ledger says nothing.
            running = actual ?: expected
        }
        return checks
    }

    /** One row's verdict. [expected] is null when there was nothing to compare against. */
    data class Check(
        val id: Long,
        val state: Reconciled,
        val expected: Long?,
        val actual: Long?,
    ) {
        /** How far out the arithmetic was, in pesewas. Null unless this is a [Reconciled.GAP]. */
        val difference: Long?
            get() = if (state == Reconciled.GAP && expected != null && actual != null) {
                actual - expected
            } else {
                null
            }
    }
}
