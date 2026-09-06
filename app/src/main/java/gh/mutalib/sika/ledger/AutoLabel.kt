package gh.mutalib.sika.ledger

import android.content.Context
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.RuleDao
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TransactionDao
import gh.mutalib.sika.logPrivate

/**
 * Puts a category on new money without being asked — the one place that decides.
 *
 * ## What was wrong before this existed
 *
 * Two mechanisms were supposed to keep the ledger named, and **neither ran where it said
 * it did.** Mutalib reported the symptom on 2026-09-06: transactions kept arriving with no
 * category and nothing said so.
 *
 * 1. **The learn-once rule never applied to new transactions at all.** Ticking *remember
 *    this* wrote a rule and rewrote the rows **already in the table**, and that was the
 *    end of it. Nothing read `rules` when a message arrived, on either route. So a shop
 *    taught to the app in August still landed unlabelled in September, forever, and the
 *    feature whose whole promise is *"tell me once"* was really *"tell me every time"*.
 * 2. **The keyword guess only ran on the live route**, despite a comment in `SmsIngest`
 *    claiming it "runs on both routes". [gh.mutalib.sika.sms.Sweeper] never called
 *    `ingest` — it batches its own inserts for speed — so the first-run backfill of months
 *    of history got no guesses at all, and neither did anything the live receiver missed.
 *
 * Both routes now end in this function, which is the point of it existing: a rule that
 * applies on one path and not the other is worse than no rule, because the ledger's
 * behaviour then depends on whether Android happened to wake the receiver.
 *
 * ## The order, and why
 *
 * A **rule** first, a **keyword** second. A rule is a decision Mutalib made and asked the
 * app to remember; a keyword is the app guessing from English. When both have an opinion
 * the human's wins, and putting the rule first is all it takes — [setLabelIfUnset] will
 * not overwrite what the rule just wrote.
 *
 * ## What it cannot do, plainly
 *
 * - **It never overwrites anything.** Every write goes through `label IS NULL`, so a
 *   hand-set label, a prompt answer and an existing guess all survive untouched.
 * - **It cannot name a cash-out.** A rule is keyed on the counterparty, and a cash-out's
 *   counterparty is the agent, not the purchase — the same agent funds a taxi one day and
 *   lunch the next. That is why the notification prompt exists, and why
 *   [gh.mutalib.sika.notify.CategoryReplyReceiver] deliberately writes no rule.
 * - **It is only as good as what it has been told.** A shop seen for the first time, with
 *   no reference and no word in [Keywords], gets nothing — and then the prompt asks.
 */
object AutoLabel {

    /**
     * SQLite refuses a statement with too many bound parameters, and the first-run backfill
     * hands this every transaction on the phone at once. Chunking is not a performance
     * tweak — it is what stops the sweep throwing on a full inbox.
     */
    private const val CHUNK = 400

    /**
     * The decision, with no database anywhere near it: what should this row be called?
     *
     * Split out from the writing on purpose, and for the same reason [Reconciler] is split
     * from [ReconcilePass] — the part that can be *wrong* is the choosing, and a rule about
     * precedence proved against a real phone is a rule proved slowly and rarely. This is
     * ordinary Kotlin and runs in `./gradlew check`.
     *
     * @param rules counterparty to category, read once by the caller.
     * @return the category and where it came from, or null when nothing has an opinion.
     */
    fun pick(
        rules: Map<String, String>,
        counterparty: String,
        reference: String?,
    ): Pair<String, LabelSource>? {
        // ⚠ **A blank counterparty must never match a rule.** A rule accidentally keyed on
        // "" would claim every nameless row at once — and the review queue is full of rows
        // with no counterparty, because that is what an unparsed message looks like.
        val byRule = counterparty.takeIf { it.isNotBlank() }?.let { rules[it] }
        if (byRule != null) return byRule to LabelSource.AUTO_RULE

        val guess = Keywords.categoryFor(reference, counterparty) ?: return null
        return guess to LabelSource.AUTO_KEYWORD
    }

    /**
     * Labels whatever among [ids] still has no category.
     *
     * @return how many rows got one, for the log line and the tests.
     */
    suspend fun run(context: Context, ids: List<Long>): Int {
        val db = SikaDatabase.get(context)
        return run(db.transactions(), db.rules(), ids)
    }

    /**
     * The same work against daos handed in directly, so the instrumented test can point it
     * at an in-memory database instead of the real ledger on the phone.
     */
    suspend fun run(dao: TransactionDao, ruleDao: RuleDao, ids: List<Long>): Int {
        // -1 is what Room returns for a row the dedupe ignored. Filtering here rather than at
        // each call site means a caller can hand over `insertAll`'s result untouched.
        val fresh = ids.filter { it != -1L }
        if (fresh.isEmpty()) return 0

        val rows = fresh.chunked(CHUNK).flatMap { dao.unlabelledIn(it) }
        if (rows.isEmpty()) return 0

        // ⚠ **One read of the rules table, not one per row.** The sweep can hand this
        // hundreds of rows on a first run, and a lookup each would be hundreds of queries
        // on the launch path — the one place where a delay is visible as a frozen splash.
        val rules = ruleDao.all().associate { it.counterparty to it.label }

        var filled = 0
        for (row in rows) {
            val (label, source) = pick(rules, row.counterparty, row.reference) ?: continue
            if (dao.setLabelIfUnset(row.id, label, source) > 0) {
                filled++
                // ⚠ Debug only: the counterparty is a real shop or person.
                logPrivate { "auto-labelled row ${row.id} '${row.counterparty}' -> '$label' ($source)" }
            }
        }

        if (filled > 0) Log.i(TAG, "auto-label: named $filled of ${rows.size} new rows")
        return filled
    }
}
