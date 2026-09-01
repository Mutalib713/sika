package gh.mutalib.sika.sms

import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.toEntity
import gh.mutalib.sika.ledger.Keywords
import gh.mutalib.sika.ledger.ReconcilePass
import gh.mutalib.sika.notify.CashOutPrompt
import gh.mutalib.sika.notify.GapAlert
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.MomoParser
import gh.mutalib.sika.parser.ParseResult
import gh.mutalib.sika.parser.Shape

/**
 * One message in, one row (or nothing) out.
 *
 * **Both routes share this classification on purpose.** The live receiver calls [ingest]
 * per message; [Sweeper] batches its inserts for speed but builds rows through the same
 * parser and the same [unparsedRow]. They must not drift: a shape the sweep understands and
 * the receiver does not would mean a transaction that only appears once you open the app,
 * which is the kind of difference nobody notices until the totals disagree.
 */
object SmsIngest {

    /**
     * @param promptOnCashOut ask what a new cash-out was for, via a notification.
     *
     * ⚠ **Only the live route passes true, and that is load-bearing.** The sweep re-reads the
     * whole inbox on every launch, so a backfill would fire one notification per historic
     * cash-out — dozens at once on first run, for money spent months ago that nobody can
     * remember. The prompt is only worth anything at the moment the money leaves.
     *
     * @return true if this message became a new row.
     */
    suspend fun ingest(
        context: Context,
        body: String,
        receivedAt: Long,
        source: String,
        promptOnCashOut: Boolean = false,
    ): Boolean {
        val dao = SikaDatabase.get(context).transactions()

        val row = when (val result = MomoParser.parse(body)) {
            is ParseResult.Parsed -> result.transaction.toEntity(receivedAt, body)
            // Sacred Rule 7: held for review, never guessed at.
            is ParseResult.Unrecognised -> unparsedRow(body, receivedAt, result.reason)
            // OTPs, adverts, failed payments. Not money — dropped, not queued.
            is ParseResult.NotATransaction -> {
                Log.i(TAG, "$source: ignored — ${result.reason}")
                return false
            }
        }

        val id = dao.insert(row)
        val isNew = id != -1L
        Log.i(
            TAG,
            "$source: ${if (isNew) "recorded" else "already had"} ${row.shape} " +
                "${row.direction} ${row.amount}p to '${row.counterparty}' txId=${row.txId}",
        )

        // A guess from the words in the reference, for rows nothing else has claimed.
        // Mutalib's "bread -> Food" question, 2026-08-31. Runs on both routes, unlike the
        // prompt, because a guess costs nothing and interrupts nobody.
        if (isNew) {
            Keywords.categoryFor(row.reference, row.counterparty)?.let { guess ->
                val filled = dao.setLabelIfUnset(id, guess, LabelSource.AUTO_KEYWORD)
                if (filled > 0) Log.i(TAG, "$source: guessed '$guess' from the reference")
            }
        }

        // ⚠ **The arithmetic check runs on the live route too, and alerts if it fails.**
        // Mutalib's request, 2026-09-01: "add an alert immediately the balance doesn't tally".
        // Gated on `promptOnCashOut` — which really means "this message just arrived" — for
        // the same reason the prompt is: the sweep re-reads everything, so alerting from it
        // would post one notification per historic gap on first run.
        if (isNew && promptOnCashOut) {
            val report = ReconcilePass.run(context)
            report.gaps.firstOrNull { it.rowId == id && it.explained == null }?.let { gap ->
                GapAlert.show(
                    context = context,
                    rowId = gap.rowId,
                    difference = kotlin.math.abs(gap.difference),
                    sinceMillis = gap.sinceMillis,
                    untilMillis = gap.whenMillis,
                    zone = ACCRA,
                )
            }
        }

        // Only a genuinely new cash-out is worth asking about. `isNew` is what stops a
        // re-read of the same message asking twice — the dedupe doing double duty.
        if (isNew && promptOnCashOut && row.shape == Shape.CASH_OUT) {
            CashOutPrompt.show(
                context = context,
                rowId = id,
                amount = row.amount,
                counterparty = row.counterparty,
                // Visible only: a category put away in Settings must not come back as a
                // button in the shade, which is the one place it cannot be corrected from.
                categories = SikaDatabase.get(context).categories().visible().map { it.name },
            )
        }
        return isNew
    }

    /**
     * A placeholder row for a message the parser refused, so it appears in the review queue.
     *
     * Every money field is zero and `parsedOk` is false, so it is excluded from every total,
     * every report and the reconciliation walk. It exists to be looked at, not counted.
     *
     * The synthetic id comes from the body, so re-reading the same unreadable message —
     * which the sweep does on every launch — does not pile up duplicates of it either.
     */
    fun unparsedRow(body: String, receivedAt: Long, reason: String) = TransactionEntity(
        txId = "unparsed:$receivedAt:${body.hashCode()}",
        occurredAt = receivedAt,
        direction = Direction.OUT,
        shape = Shape.PAYMENT_MADE,
        amount = 0,
        fee = 0,
        tax = null,
        // ⚠ **Empty, not the failure reason.** An earlier version stored the reason here,
        // which was wrong twice over: `counterparty` is what a learn-once rule is keyed on,
        // so a review row could have acquired a rule for a sentence of English; and the
        // reason would have been frozen at the moment of failure.
        //
        // The reason is derived on read instead, by re-parsing [rawBody] — which Sacred
        // Rule 6 guarantees is kept. That makes it always current: fix the parser and a
        // queued message stops reporting a failure, because it no longer is one.
        counterparty = "",
        reference = null,
        balanceAfter = null,
        rawBody = body,
        parsedOk = false,
    )

    /**
     * Why this queued message could not be read, worked out now rather than recalled from
     * when it failed. [reason] is unused by callers but kept in the signature so the log
     * line at the moment of failure still says something useful.
     */
    fun reasonFor(row: TransactionEntity): String = when (val r = MomoParser.parse(row.rawBody)) {
        is ParseResult.Unrecognised -> r.reason
        is ParseResult.NotATransaction -> r.reason
        // The parser has since been taught this shape. The row is stale, not broken.
        is ParseResult.Parsed -> "Now readable — re-sweep to record it as ${r.transaction.shape}."
    }
}
