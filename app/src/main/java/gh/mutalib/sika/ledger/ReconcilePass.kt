package gh.mutalib.sika.ledger

import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
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
        for (c in checks) dao.setReconciled(c.id, c.state)

        val gaps = checks.filter { it.state == Reconciled.GAP }.map { c ->
            val row = byId.getValue(c.id)
            Gap(
                whenMillis = row.occurredAt,
                counterparty = row.counterparty,
                shape = row.shape.name,
                amount = row.amount,
                expected = c.expected ?: 0L,
                actual = c.actual ?: 0L,
                difference = c.difference ?: 0L,
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
            r.gaps.sortedByDescending { it.whenMillis }.take(12).forEach { g ->
                Log.w(
                    TAG,
                    "gap ${g.date()} ${g.shape} '${g.counterparty}' amount ${g.amount.asCedis()} " +
                        "expected ${g.expected.asCedis()} actual ${g.actual.asCedis()} " +
                        "diff ${g.difference.asCedis()}",
                )
            }
        }
    }
}

data class Gap(
    val whenMillis: Long,
    val counterparty: String,
    val shape: String,
    val amount: Long,
    val expected: Long,
    val actual: Long,
    /** actual − expected. Positive means the balance is higher than the maths predicted. */
    val difference: Long,
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
