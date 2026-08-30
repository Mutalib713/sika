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

    /**
     * `Payment for GHSx to y .Current Balance: …` — the commonest outgoing shape in
     * Mutalib's real inbox (×20). Bank transfers, other networks, bills, bundles.
     */
    PAYMENT_FOR,

    /** `Cash In received for GHS x from y. Current Balance GHS …` — a deposit at an agent. */
    CASH_IN,

    /** `You have transferred GHS x to y from your mobile money account …` — cross-network. */
    TRANSFER,

    /** `Y'ello. You have Paid GHS x to Merchant nnn …` — MoMoPay at a till. */
    MERCHANT_PAY,
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

    /**
     * It looks like money — it carries a GHS amount **and** a transaction id — but no known
     * shape matched. **This is what the review queue is for.** Sacred Rule 7.
     *
     * [reason] is written for a human reading that queue, not for a log file.
     */
    data class Unrecognised(val reason: String) : ParseResult

    /**
     * Not a transaction at all: an OTP, a fraud warning, a bundle advert.
     *
     * ⚠ **A distinct case from [Unrecognised], and the distinction was learned from real
     * data.** The first sweep of Mutalib's inbox on 2026-08-30 found 465 messages from
     * MoMo-ish senders, of which only 118 were transactions. Treating the other 347 as
     * "could not read" filed every MTN advert into the review queue as though it might be
     * money, burying the handful of messages that genuinely need a human.
     *
     * A review queue full of adverts is a review queue nobody opens, and then Sacred
     * Rule 7 quietly stops working. These are dropped, not queued.
     */
    data class NotATransaction(val reason: String) : ParseResult
}
