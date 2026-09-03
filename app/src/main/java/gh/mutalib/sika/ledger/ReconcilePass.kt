package gh.mutalib.sika.ledger

import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.warnPrivate
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.parser.asCedis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Runs [Reconciler] over the stored ledger and writes each verdict back.
 *
 * The thin database half, kept apart from the arithmetic so the arithmetic stays testable on
 * the JVM. Runs after every sweep: the check is cheap and Sacred Rule 3 wants it current, not
 * something you remember to trigger.
 */
object ReconcilePass {

    suspend fun run(context: Context): ReconcileReport = withContext(Dispatchers.IO) {
        val dao = SikaDatabase.get(context).transactions()
        val rows = dao.allChronological()
        val byId = rows.associateBy { it.id }

        val checks = Reconciler.reconcile(rows)
        // ⚠ The difference used to be computed here and thrown away. Storing it is what lets
        // an explained gap reach a category total at all — nothing downstream of this runs the
        // reconciler, so an amount that only exists in memory is an amount the report cannot
        // see. Absolute value: a gap has a size, not a direction.
        for (c in checks) dao.setReconciled(c.id, c.state, c.difference?.let { kotlin.math.abs(it) })

        // ⚠ **The window opens at the last row that STATED a balance, not simply the previous
        // row.** A message with no balance in its text cannot anchor anything, so if one sits
        // between the hole and the row that catches it, the previous-row answer names a window
        // that excludes the moment the money actually left — sending someone to look on a day
        // when nothing happened. Found by probing for PLAN task 18.
        val ordered = rows.sortedWith(compareBy({ it.occurredAt }, { it.id }))
        val anchorBefore = mutableMapOf<Long, Long?>()
        var lastStated: Long? = null
        for (r in ordered) {
            anchorBefore[r.id] = lastStated
            if (r.balanceAfter != null) lastStated = r.occurredAt
        }

        val gaps = checks.filter { it.state == Reconciled.GAP }.map { c ->
            val row = byId.getValue(c.id)
            Gap(
                rowId = row.id,
                whenMillis = row.occurredAt,
                // ⚠ **When the hole OPENED, not when it was caught.** The check fires on the
                // message after the missing one, so this row's own date is the far end of the
                // window. Reporting it as the date of the loss sends someone looking on the
                // wrong day - Mutalib's question, 2026-09-01.
                sinceMillis = anchorBefore[row.id],
                counterparty = row.counterparty,
                shape = row.shape.name,
                amount = row.amount,
                expected = c.expected ?: 0L,
                actual = c.actual ?: 0L,
                difference = c.difference ?: 0L,
                explained = row.gapNote,
            )
        }

        ReconcileReport(
            checked = checks.size,
            ok = checks.count { it.state == Reconciled.OK },
            gaps = gaps,
            unchecked = checks.count { it.state == Reconciled.UNCHECKED },
        ).also { r ->
            Log.i(TAG, "reconcile: ${r.ok} ok, ${r.gaps.size} gaps, ${r.unchecked} unchecked, of ${r.checked}")
            // Newest first — a gap from last week matters more than one from June.
            // ⚠ Debug only, and this line was MISSED by the task 17 sweep. It printed a real
            // counterparty — a person's full name — plus their balance, straight into logcat.
            // Caught on the device on 2026-09-01 by reading the app's own output while
            // verifying the migration, which is exactly why a grep is not an audit.
            r.gaps.sortedByDescending { it.whenMillis }.take(12).forEach { g ->
                warnPrivate {
                    "gap ${g.date()} ${g.shape} '${g.counterparty}' amount ${g.amount.asCedis()} " +
                        "expected ${g.expected.asCedis()} actual ${g.actual.asCedis()} " +
                        "diff ${g.difference.asCedis()}"
                }
            }
        }
    }
}

data class Gap(
    /** The transaction that revealed it — the one *after* the hole. */
    val rowId: Long,
    val whenMillis: Long,
    /** The transaction before it, which opens the window. Null when this is the first one. */
    val sinceMillis: Long?,
    val counterparty: String,
    val shape: String,
    val amount: Long,
    val expected: Long,
    val actual: Long,
    /** actual − expected. Positive means the balance is higher than the maths predicted. */
    val difference: Long,
    /** What Mutalib said the missing money was, if he has said. */
    val explained: String? = null,
) {
    fun date(): String = FORMAT.format(Instant.ofEpochMilli(whenMillis).atZone(ACCRA))
}

data class ReconcileReport(
    val checked: Int,
    val ok: Int,
    val gaps: List<Gap>,
    val unchecked: Int,
)

private val ACCRA: ZoneId = ZoneId.of("Africa/Accra")
private val FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm")
