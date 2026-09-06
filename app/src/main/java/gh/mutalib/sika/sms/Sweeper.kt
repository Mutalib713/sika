package gh.mutalib.sika.sms

import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.logPrivate
import gh.mutalib.sika.warnPrivate
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.ReconcilePass
import gh.mutalib.sika.ledger.ReconcileReport
import gh.mutalib.sika.data.toEntity
import gh.mutalib.sika.parser.MomoParser
import gh.mutalib.sika.parser.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads MoMo messages out of the inbox and into the ledger.
 *
 * Runs on every launch, not just the first — that is the catch-up half of the design. The
 * live receiver (task 6) can be skipped by Android under doze or a battery saver, but the
 * message still lands in the inbox, so this picks up anything the broadcast missed.
 *
 * Safe to run infinitely, because the unique index on `txId` and `OnConflictStrategy.IGNORE`
 * mean a message already seen is simply not re-inserted. Sacred Rule 4.
 */
object Sweeper {

    suspend fun sweep(context: Context): SweepReport = withContext(Dispatchers.IO) {
        val db = SikaDatabase.get(context)
        val dao = db.transactions()

        // Seeds a new phone, tops up an existing one. Onboarding calls the same function, so
        // whichever runs first leaves a usable table — see ensureCategories.
        SikaDatabase.ensureCategories(db.categories())

        // Full read every time. Filtering by "since the newest row" would be faster, but
        // it would also permanently skip anything that arrived out of order or was
        // repaired later — and the dedupe makes the full read cheap enough to not care.
        val messages = SmsInbox.readMomo(context.contentResolver)

        val unrecognised = mutableListOf<RawSms>()
        val rows = mutableListOf<TransactionEntity>()
        var notTransactions = 0

        for (sms in messages) {
            when (val result = MomoParser.parse(sms.body)) {
                is ParseResult.Parsed ->
                    rows += result.transaction.toEntity(
                        occurredAt = sms.receivedAt,
                        rawBody = sms.body,
                    )
                is ParseResult.Unrecognised -> {
                    unrecognised += sms
                    // Sacred Rule 7: held, not dropped. It goes in with parsedOk = false so
                    // it surfaces in the review queue rather than vanishing. There is no
                    // txId to dedupe on, so the message body itself is the key.
                    rows += SmsIngest.unparsedRow(sms.body, sms.receivedAt, result.reason)
                }
                // OTPs, fraud warnings, bundle adverts. Counted so the sweep can say how
                // much noise it discarded, but never stored — a review queue full of
                // adverts is one nobody opens.
                is ParseResult.NotATransaction -> notTransactions++
            }
        }

        val insertedIds = dao.insertAll(rows)
        val newlyAdded = insertedIds.count { it != -1L }

        // Sacred Rule 3: the check runs on every sweep rather than on request. It is cheap,
        // and a verification you have to remember to trigger is one that stops happening.
        val reconcile = ReconcilePass.run(context)

        // The stored queue, not just what this sweep happened to miss. A message queued by
        // the live receiver last week has to show up here too, or the count lies.
        val queued = dao.reviewQueue()

        // Which sender each *transaction* actually came from. The point of measuring this
        // is to narrow the address filter: the first sweep matched 465 messages on a
        // deliberately loose pattern, and only 118 of them were money.
        val txSenders = messages
            .filter { MomoParser.parse(it.body) is ParseResult.Parsed }
            .groupingBy { it.address }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        SweepReport(
            senders = txSenders,
            found = messages.size,
            parsed = messages.size - unrecognised.size - notTransactions,
            notTransactions = notTransactions,
            unrecognised = unrecognised.size,
            newlyAdded = newlyAdded,
            oldest = messages.filter { MomoParser.parse(it.body) is ParseResult.Parsed }
                .minOfOrNull { it.receivedAt },
            newest = messages.filter { MomoParser.parse(it.body) is ParseResult.Parsed }
                .maxOfOrNull { it.receivedAt },
            totalInLedger = dao.count(),
            queued = queued.size,
            queuedSamples = queued.take(5).map { SmsIngest.reasonFor(it) to it.rawBody },
            reconcile = reconcile,
        ).also {
            Log.i(TAG, "sweep: ${it.found} matched, ${it.parsed} transactions, " +
                "${it.notTransactions} not transactions, ${it.unrecognised} unrecognised, " +
                "${it.newlyAdded} new, ${it.totalInLedger} in ledger, ${it.queued} queued for review")
            // ⚠ **Debug only: these are raw SMS `address` values.** MTN's are shortcodes,
            // but the field holds whatever sent the message, so a person's number can land
            // here the moment anything unexpected parses as a transaction. The count is the
            // useful part in release; the identities are not.
            Log.i(TAG, "transaction senders: ${it.senders.size} distinct")
            logPrivate { "senders: " + it.senders.joinToString { s -> "${s.first}=${s.second}" } }
            it.queuedSamples.forEach { (reason, body) ->
                // ⚠ Debug only. This is a whole MoMo message: amount, counterparty,
                // balance. It is what the parser gets fixed from, and it must not be in a
                // release build's logcat.
                warnPrivate { "queued: $reason  <<< ${body.take(150)}" }
                Log.w(TAG, "queued a message the parser refused: $reason")
            }
            // Grouped by opening phrase, not listed one by one: 39 unrecognised messages
            // are only a handful of distinct *shapes*, and the shapes are what matter.
            unrecognised
                .groupingBy { it.body.take(38) }
                .eachCount()
                .entries.sortedByDescending { e -> e.value }
                .forEach { (prefix, n) ->
                    val full = unrecognised.first { it.body.startsWith(prefix) }.body
                    // Truncated: full bodies carry counterparty names and account
                    // numbers, and logcat is readable by anyone with the phone.
                    warnPrivate { "shape x$n: ${full.take(200)}" }
                }
        }
    }

}

/**
 * What one sweep found. PLAN task 5's verification is the four numbers in the middle, and
 * [queuedSamples] is the one that decides whether the parser is finished: **every message
 * held for review becomes a golden test before the parser is touched.**
 */
data class SweepReport(
    /** Senders that produced actual transactions, so the address filter can be narrowed. */
    val senders: List<Pair<String, Int>>,
    /** Messages the sender filter matched, before anything looked at their content. */
    val found: Int,
    val parsed: Int,
    /** OTPs, fraud warnings, adverts. Discarded, not queued. */
    val notTransactions: Int,
    val unrecognised: Int,
    val newlyAdded: Int,
    val oldest: Long?,
    val newest: Long?,
    val totalInLedger: Int,
    /** Everything held for review, however it got there. */
    val queued: Int,
    /** reason (worked out now) to raw message body. */
    val queuedSamples: List<Pair<String, String>>,
    val reconcile: ReconcileReport,
)
