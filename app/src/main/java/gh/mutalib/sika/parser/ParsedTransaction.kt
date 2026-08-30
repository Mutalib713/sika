package gh.mutalib.sika.parser

/** Which way the money went. */
enum class Direction { IN, OUT }

/**
 * Which MoMo message shape produced this row.
 *
 * Kept on the row rather than thrown away after parsing: when MTN reword one alert, this
 * says exactly which shape stopped matching and which rows came from it.
 */
enum class Shape {
    /** `Payment made for GHS x to y …` — money sent to a person or a shop. */
    PAYMENT_MADE,

    /** `Payment received for GHS x from y …` — money arriving. */
    PAYMENT_RECEIVED,

    /** `Cash Out made for GHSx to y …` — cash taken at an agent. The blind spot. */
    CASH_OUT,

    /** `Your payment of GHS x to MTN AIRTIME has been completed …` — airtime, bundles, bills. */
    BILL_AIRTIME,
}

/**
 * One MoMo transaction, read out of one SMS. **No date field on purpose** — Sacred Rule 5:
 * the date comes from Android's own timestamp on the message, because only one of the four
 * known shapes carries a time at all. The parser never sees a date and cannot invent one.
 *
 * Every money value is pesewas. See [parseMoney].
 */
data class ParsedTransaction(
    /** MTN's own id. The dedupe key — Sacred Rule 4. */
    val txId: String,
    val shape: Shape,
    val direction: Direction,
    /** Always positive. [direction] carries the sign. */
    val amount: Long,
    /** Zero when MoMo charged nothing. Never null: a missing fee would break reconciliation. */
    val fee: Long,
    /** Null when the message writes `-`, which shape 1 does. Not the same as zero. */
    val tax: Long?,
    /** "MTN AIRTIME", an agent number, a person's name. As written, not cleaned up. */
    val counterparty: String,
    /** Null when absent or literally `-`. A hint for labelling, never the category itself. */
    val reference: String?,
    /** The balance MoMo reports afterwards. The anchor reconciliation walks. */
    val balanceAfter: Long?,
)

/**
 * What the parser returns. A sealed type rather than a nullable row, so that
 * "I could not read this" carries a reason and cannot be mistaken for "nothing happened".
 *
 * Sacred Rule 7: unparsed goes to the review queue. Never guessed, never dropped.
 */
sealed interface ParseResult {
    data class Parsed(val transaction: ParsedTransaction) : ParseResult

    /** [reason] is written for a human reading the review queue, not for a log file. */
    data class Unrecognised(val reason: String) : ParseResult
}
