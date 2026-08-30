package gh.mutalib.sika.parser

// Declared here rather than beside the other patterns: Kotlin initialises top-level
// properties in source order, so anything referencing IGNORE must come after it.
private val IGNORE = RegexOption.IGNORE_CASE

/**
 * Reads an MTN MoMo SMS into a [ParsedTransaction].
 *
 * Pure Kotlin with no Android import anywhere, deliberately: every message shape is then
 * testable on the JVM in milliseconds, with no emulator and no device. That is what makes
 * the golden suite in `ParserTest` cheap enough to grow forever.
 *
 * **Structure: a list of [ShapeMatcher]s, tried in order.** Adding a fifth shape means
 * adding one object to [matchers], not editing a branching chain — which matters because
 * PLAN task 5 sweeps the real inbox and will almost certainly find shapes nobody has seen.
 *
 * The fields that appear in every shape — fee, balance, id, reference, tax — are pulled by
 * shared patterns below rather than being repeated per shape, because MTN is inconsistent
 * about their *labels* but consistent about their presence.
 */
object MomoParser {

    fun parse(body: String): ParseResult {
        // ⚠ **Failures are checked first, before anything else looks at this message.**
        //
        // Nine messages in Mutalib's inbox read `Your payment of GHS 20.00 to TELECEL PUSH
        // has failed` — shape 1's wording exactly, apart from those two words. They carry a
        // real amount and a real transaction id, so every other test in this file would
        // wave them through. No money moved, and counting them would overstate spending
        // with nothing to reveal the error afterwards.
        if (FAILED.containsMatchIn(body)) {
            return ParseResult.NotATransaction("Transaction failed — no money moved.")
        }

        val matcher = matchers.firstOrNull { it.pattern.containsMatchIn(body) }
            ?: return if (looksLikeMoney(body)) {
                ParseResult.Unrecognised("Carries an amount and a transaction ID, but no known shape matched.")
            } else {
                // An OTP, a fraud warning, a bundle advert. Not money, so not the review
                // queue's problem. See ParseResult.NotATransaction for why this case exists.
                ParseResult.NotATransaction("No amount and no transaction ID — not a transaction.")
            }

        val m = matcher.pattern.find(body)!!

        val amount = parseMoney(m.groupValues[1])
            ?: return ParseResult.Unrecognised("Matched ${matcher.shape} but the amount was unreadable.")

        // No id, no row. Dedupe is keyed on it (Sacred Rule 4), so a transaction without
        // one cannot be safely stored at all — the inbox sweep would insert it again on
        // every launch. Better in the review queue than silently multiplying.
        val txId = TX_ID.find(body)?.groupValues?.get(1)
            ?: return ParseResult.Unrecognised("Matched ${matcher.shape} but found no transaction ID.")

        return ParseResult.Parsed(
            ParsedTransaction(
                txId = txId,
                shape = matcher.shape,
                direction = matcher.direction,
                amount = amount,
                // Absent means nothing was charged. All four known shapes state a fee, so
                // this default only ever applies to a shape we have not met yet.
                fee = FEE.find(body)?.groupValues?.get(1)?.let(::parseMoney) ?: 0L,
                // Null, not zero. Shape 1 writes `Tax was GHS -.` and a dash is not a number.
                tax = TAX.find(body)?.groupValues?.get(1)?.let(::parseMoney),
                counterparty = m.groups["who"]?.value?.trim().orEmpty(),
                reference = REFERENCE.find(body)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "-" },
                balanceAfter = findBalance(body),
            ),
        )
    }

    private class ShapeMatcher(
        val shape: Shape,
        val direction: Direction,
        val pattern: Regex,
    )

    /**
     * Tried in order. The four confirmed against Mutalib's real inbox on 2026-08-30.
     *
     * Every one captures the amount as group 1 and the counterparty as `who`.
     */
    private val matchers = listOf(
        ShapeMatcher(
            Shape.BILL_AIRTIME, Direction.OUT,
            Regex("""Your payment of $AMOUNT to (?<who>.+?) has been completed""", IGNORE),
        ),
        ShapeMatcher(
            // `to 000.` — the agent, terminated by the full stop. The amount's own decimal
            // point is already consumed by AMOUNT, so [^.] is safe here.
            Shape.CASH_OUT, Direction.OUT,
            Regex("""Cash Out made for $AMOUNT to (?<who>[^.]+)\.""", IGNORE),
        ),
        ShapeMatcher(
            // Note `\s+`: the real message has *two* spaces after the sender's name.
            Shape.PAYMENT_RECEIVED, Direction.IN,
            Regex("""Payment received for $AMOUNT from (?<who>.+?)\s+Current Balance""", IGNORE),
        ),
        ShapeMatcher(
            Shape.PAYMENT_MADE, Direction.OUT,
            Regex("""Payment made for $AMOUNT to (?<who>.+?)\s+Current Balance""", IGNORE),
        ),

        // ---- the six found by the first real sweep, 2026-08-30 (PLAN task 5b) ----

        ShapeMatcher(
            // The commonest outgoing shape of all — ×20. `\.+` rather than `\.` because the
            // payee's own name can end in one: `Bills.INV ..Current Balance`.
            Shape.PAYMENT_FOR, Direction.OUT,
            Regex("""Payment for $AMOUNT to (?<who>.+?)\s*\.+\s*Current Balance""", IGNORE),
        ),
        ShapeMatcher(
            Shape.CASH_IN, Direction.IN,
            Regex("""Cash In received for $AMOUNT from (?<who>.+?)\.\s*Current Balance""", IGNORE),
        ),
        ShapeMatcher(
            Shape.TRANSFER, Direction.OUT,
            Regex(
                """You have transferred $AMOUNT\s+to (?<who>.+?)\s+from your mobile money account""",
                IGNORE,
            ),
        ),
        ShapeMatcher(
            Shape.MERCHANT_PAY, Direction.OUT,
            Regex(
                """You have Paid $AMOUNT to (?<who>.+?) on your mobile money account""",
                IGNORE,
            ),
        ),
    )
}

/**
 * Anything saying the transaction did not happen.
 *
 * `has failed` covers the nine failed payments; `failed to send` covers the
 * exceeded-daily-limit message, where MTN reports money that never arrived. Both carry
 * amounts and transaction ids, so nothing else in the parser would have stopped them.
 */
private val FAILED = Regex("""has failed|failed to send""", IGNORE)

/**
 * The balance MoMo reports afterwards — the anchor the whole reconciliation walk depends on.
 *
 * Two patterns, because MTN writes it both ways round. Almost every shape says
 * `Current Balance: GHS 44.79`, but the transfer message says **`Your new balance: 4652.89
 * GHS`** — number first, currency after. One message in the real inbox, and without the
 * second pattern it would silently store no balance at all, which reconciliation would
 * then read as a gap.
 */
private fun findBalance(body: String): Long? =
    BALANCE.find(body)?.groupValues?.get(1)?.let(::parseMoney)
        ?: BALANCE_REVERSED.find(body)?.groupValues?.get(1)?.let(::parseMoney)


/**
 * The number pattern every money field shares. Three things about it are deliberate:
 *
 * 1. `\s*` after GHS — the space is optional and **changes inside a single message**:
 *    `Payment made for GHS 5.00 … Fee charged: GHS0.50`.
 * 2. `[\d,]*` — amounts carry thousands separators once they pass a thousand cedis.
 * 3. **The fraction is `\.\d{1,2}`, never a character class.**
 *
 * ⚠ Point 3 is the trailing-full-stop landmine, and it is worth being precise about it
 * because the obvious explanation is wrong. `[\d.]+` — a class *containing* the dot — is
 * what swallowed the full stop in `Fee charged: GHS0.50.` and produced `"0.50."` on the
 * first run against real data. Writing the dot as a literal `\.` followed by digits makes
 * that impossible, since a full stop is not a digit.
 *
 * Proved by mutation on 2026-08-30: swapping this for `([\d.,]+)` makes
 * `shape 2 - cash out` fail with **expected:<50> but was:<0>** — and note the failure
 * mode. It does not crash. The fee silently becomes **zero**, which reconciliation would
 * then treat as correct arithmetic. That is precisely the silent wrongness Sacred Rule 3
 * exists to catch.
 */
private const val AMOUNT = """GHS\s*(\d[\d,]*(?:\.\d{1,2})?)"""

/** Three labels for one field: `Fee was GHS 0.00`, `Fee charged: GHS0.50`, `TRANSACTION FEE: 0.00`. */
private val FEE = Regex(
    """(?:Fee was\s+GHS\s*|Fee charged\s*:\s*GHS\s*|TRANSACTION FEE\s*:\s*)(\d[\d,]*(?:\.\d{1,2})?)""",
    IGNORE,
)

/**
 * Two labels — `Your new balance:` and `Current Balance:` — and the colon is **optional**.
 *
 * The Cash In shape writes `Current Balance GHS 102.07` with no colon at all, which is why
 * all seven deposits stored no balance until this was measured.
 */
private val BALANCE = Regex(
    """(?:Your new balance|Current Balance)\s*:?\s*GHS\s*(\d[\d,]*(?:\.\d{1,2})?)""",
    IGNORE,
)

/** The transfer shape only: `Your new balance: 4652.89 GHS`. See [findBalance]. */
private val BALANCE_REVERSED = Regex(
    """(?:Your new balance|Current Balance)\s*:?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*GHS""",
    IGNORE,
)

/** Two labels, differing in capitalisation too: `Financial Transaction Id` and `Transaction ID`. */
private val TX_ID = Regex("""(?:Financial Transaction Id|Transaction ID)\s*:\s*(\d+)""", IGNORE)

/**
 * Captures `-` as well as a number, so [parseMoney] can reject it and yield null — a tax
 * nobody stated is a different fact from a tax of zero.
 *
 * Four spellings in real messages: `Tax was GHS -`, `Tax charged: 0`, `Tax charged: GHS 0`,
 * and `Tax Charged 0` **with no colon whatsoever**.
 */
private val TAX = Regex(
    """(?:Tax was\s+GHS\s*|Tax charged\s*:?\s*GHS\s*|Tax charged\s*:?\s*)(-|\d[\d,]*(?:\.\d{1,2})?)""",
    IGNORE,
)

/**
 * Does this message carry money at all?
 *
 * The test for whether an unmatched message belongs in the review queue. It requires
 * **both** a GHS amount and a transaction id, because MTN's adverts quote amounts freely —
 * *"enjoy an overdraft of up to GHS 1,800"* — so an amount on its own proves nothing. Only
 * a real transaction carries an id.
 */
private fun looksLikeMoney(body: String): Boolean =
    HAS_AMOUNT.containsMatchIn(body) && TX_ID.containsMatchIn(body)

private val HAS_AMOUNT = Regex("""GHS\s*\d""", IGNORE)

/** `Reference: -.` and `Reference: 1.` both appear in real data. */
private val REFERENCE = Regex("""Reference\s*:\s*([^.]*)\.""", IGNORE)
