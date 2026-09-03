package gh.mutalib.sika.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.ParsedTransaction
import gh.mutalib.sika.parser.Shape

/** Where a row's label came from. Kept so the app never overwrites a human's decision. */
enum class LabelSource {
    /** Nothing has labelled it yet. */
    NONE,

    /** A learned counterparty rule applied it. Safe to re-apply if the rule changes. */
    AUTO_RULE,

    /** Mutalib set it by hand. **Never overwritten by a rule.** */
    MANUAL,

    /** Answered from the cash-out notification. Also a human decision. */
    PROMPT,

    /**
     * Guessed from a word in the reference or the counterparty — see [gh.mutalib.sika.ledger.Keywords].
     *
     * ⚠ **The weakest source there is, and the only one derived from language rather than
     * from a decision.** It may only ever fill a row that has no label at all: a rule, a
     * hand-set label and a prompt answer all outrank it.
     */
    AUTO_KEYWORD,
}

/** Whether this row's arithmetic agrees with the balance MoMo reported. Sacred Rule 3. */
enum class Reconciled {
    /** Not checked yet — a new row before the pass runs. */
    UNCHECKED,

    /** `previous − amount − fee == balanceAfter`. Exact, because money is integer pesewas. */
    OK,

    /** It does not add up. Something is missing or misread, and the app says so. */
    GAP,
}

/**
 * One MoMo transaction.
 *
 * **[txId] carries a unique index, and that is the whole dedupe story** (Sacred Rule 4).
 * The inbox sweep re-reads every message on every launch, so without this one row would
 * become many and the totals would inflate silently.
 *
 * Every money field is pesewas — see `parser/Money.kt` for why it is not a Double.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["txId"], unique = true),
        // Every screen filters by time, and the reconciliation pass walks in this order.
        Index(value = ["occurredAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** MTN's own id. The dedupe key. */
    val txId: String,

    /**
     * Epoch millis, **from Android's timestamp on the SMS** — Sacred Rule 5. Only one of
     * the four known shapes carries a time in its text, so the message body is never
     * trusted for this.
     */
    val occurredAt: Long,

    val direction: Direction,
    val shape: Shape,

    /** Pesewas, always positive. [direction] carries the sign. */
    val amount: Long,
    val fee: Long,
    /** Null when the SMS wrote `-`. Not the same as zero. */
    val tax: Long?,

    val counterparty: String,
    val reference: String?,
    val balanceAfter: Long?,

    val label: String? = null,
    val labelSource: LabelSource = LabelSource.NONE,

    /**
     * What this particular payment was actually for, in Mutalib's own words.
     *
     * ⚠ **A note is not a category, and keeping them apart is the whole point.** His
     * distinction, 2026-09-01: the `+` is for things he pays for repeatedly; a laptop repair
     * or a birthday is a one-off that deserves a description without becoming a category.
     *
     * Before this the only ways to describe a transaction were to file it under `Other`,
     * which loses the information, or to invent a category, which then sits in the breakdown
     * forever holding a single transaction. Do that a few times and the category list stops
     * meaning anything.
     *
     * So a note belongs to exactly one row. It never appears in the breakdown, never becomes
     * a learn-once rule, and never affects a total.
     */
    val note: String? = null,

    /**
     * What the money that never got a message actually was, in Mutalib's own words.
     *
     * ⚠ **Only ever set on a row flagged [Reconciled.GAP]**, and it explains the *hole before
     * this row*, not this row itself. His instruction, 2026-09-01: *"if the user remembers he
     * can do something about it… I might remember what I did"*. He is right that an app which
     * says "GHS 20.00 is missing" and offers nothing else is only half useful.
     *
     * ⚠ **Writing this does NOT clear the gap, and does not move the money into any total.**
     * The flag is a fact about messages — MTN sent none — and remains true however well you
     * remember the purchase. Clearing it on an explanation would mean the reconciliation
     * check quietly passing on evidence that was typed rather than measured, which is the one
     * thing this app must never do. What the note buys is that Home stops asking, and that
     * six months later the amount has a name attached to it.
     *
     * Whether an explained gap should ALSO become a spendable amount in the category
     * breakdown was left open here until 2026-09-03. It is now [gapCategory]'s job.
     */
    val gapNote: String? = null,

    /**
     * How far the balance moved beyond what the messages account for, in pesewas.
     *
     * ⚠ **Persisted so the breakdown can use it, not because reconciliation needs it.** The
     * reconciler computes this difference every run and used to throw it away, keeping only
     * the GAP flag. Nothing that only reads transactions — `summarise` in particular — could
     * therefore see how *much* was missing, so a gap could never appear in a category total
     * however well it was explained.
     *
     * Always positive, and always about the hole *before* this row.
     */
    val gapAmount: Long? = null,

    /**
     * Which category the remembered money should count toward, if the answer is known.
     *
     * ⚠ **This is the one place Sika lets a figure into a total that no message proves.**
     * Mutalib's decision, 2026-09-03, after being shown the trade: he took "count it and mark
     * it" over leaving the money out, and over counting it silently. The marking is the
     * condition on which it was agreed and is not decoration — see [PeriodSummary.fromBalance]
     * and `CategorySlice.fromBalance`, which carry it all the way to the screen so no total is
     * ever shown as measured when part of it is remembered.
     *
     * ⚠ **Setting this does NOT clear the GAP flag.** MTN still sent no message, and that
     * stays true however confidently the purchase is recalled. Reconciliation keeps reporting
     * it; what changes is only that the amount now has somewhere to go.
     *
     * ⚠ **Null and "no category" mean the same here**, deliberately: an explained gap with no
     * category chosen stays out of every total, exactly as before this column existed.
     */
    val gapCategory: String? = null,

    /**
     * The original SMS, kept verbatim — Sacred Rule 6.
     *
     * This is what makes a parser fix retroactive: correct a pattern, re-run it over
     * stored bodies, and every row it once got wrong is repaired. Without it a fix only
     * helps future messages and the bad history stays bad forever.
     */
    val rawBody: String,

    /** False means it sits in the review queue. Never guessed at — Sacred Rule 7. */
    val parsedOk: Boolean = true,

    val reconciled: Reconciled = Reconciled.UNCHECKED,
)

/**
 * The learn-once table. Label `MTN AIRTIME` as Airtime once and every later message from
 * that counterparty labels itself.
 *
 * This is what stops label rot — risk #2. The correcting work has to shrink toward zero on
 * its own, or the reports quietly stop meaning anything by week five.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    /** The counterparty, exactly as MoMo writes it. Natural key — one rule per party. */
    @PrimaryKey val counterparty: String,
    val label: String,
    val createdAt: Long,
)

/**
 * A spending category. **A table, not a hardcoded list**, so the `+` button can add one
 * from anywhere a category is chosen.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Drag order. Also decides the four quick options on the cash-out notification. */
    val sortOrder: Int,
    /** True for the nine seeded on first run. */
    val isDefault: Boolean = false,
    /** True only for `Other`: it cannot be renamed or deleted, and absorbs deletions. */
    val isProtected: Boolean = false,

    /**
     * Put away: still a real category, just no longer offered when labelling.
     *
     * ⚠ **This exists because deleting was the wrong idea, and Mutalib said so first**
     * (2026-09-01): *"if a user deletes a category when it's already being used it can cause
     * problems"*. He is right, and the plan before this was worse than he realised — it would
     * have moved every transaction to `Other`, which does not lose the money but does lose the
     * only record of what the money was for. Destroying information to tidy a list is a bad
     * trade, and it is not undoable.
     *
     * Hiding costs nothing and reverses in one tap. A hidden category:
     *   * disappears from the picker and from the cash-out notification's quick options,
     *   * keeps every transaction already filed under it, untouched,
     *   * still appears in reports, because the money was still spent.
     *
     * Deleting outright survives only for a category that has never labelled anything — see
     * `CategoryDao.deleteIfUnused`. Nothing can be lost by removing a name nothing points at.
     */
    val isHidden: Boolean = false,
)

/**
 * Room stores enums as their names. Written out rather than stored as ordinals on purpose:
 * an ordinal silently changes meaning the moment someone reorders an enum, which would
 * turn every stored `IN` into `OUT` with no error anywhere.
 */
class Converters {
    @TypeConverter fun directionToString(v: Direction): String = v.name
    @TypeConverter fun stringToDirection(v: String): Direction = Direction.valueOf(v)

    @TypeConverter fun shapeToString(v: Shape): String = v.name
    @TypeConverter fun stringToShape(v: String): Shape = Shape.valueOf(v)

    @TypeConverter fun labelSourceToString(v: LabelSource): String = v.name
    @TypeConverter fun stringToLabelSource(v: String): LabelSource = LabelSource.valueOf(v)

    @TypeConverter fun reconciledToString(v: Reconciled): String = v.name
    @TypeConverter fun stringToReconciled(v: String): Reconciled = Reconciled.valueOf(v)
}

/**
 * Turns a parsed message into a storable row.
 *
 * [occurredAt] and [rawBody] come from the SMS itself rather than the parser, which is the
 * point: the parser never sees a date and cannot invent one, and the untouched body is
 * what makes a future parser fix able to repair old rows.
 */
fun ParsedTransaction.toEntity(occurredAt: Long, rawBody: String) = TransactionEntity(
    txId = txId,
    occurredAt = occurredAt,
    direction = direction,
    shape = shape,
    amount = amount,
    fee = fee,
    tax = tax,
    counterparty = counterparty,
    reference = reference,
    balanceAfter = balanceAfter,
    rawBody = rawBody,
    parsedOk = true,
)
