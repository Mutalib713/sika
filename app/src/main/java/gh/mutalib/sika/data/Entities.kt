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
