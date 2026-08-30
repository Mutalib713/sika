package gh.mutalib.sika.parser

/**
 * **Money is a Long of pesewas. Never a Double.**
 *
 * GHS 10.00 is `1000`. GHS 0.50 is `50`.
 *
 * This corrects PROFILE.md § 8, which originally said `Double`, and it matters more here
 * than in most apps. Sacred Rule 3 has the ledger check its own arithmetic —
 * `previous − amount − fee == new` — and in binary floating point that comparison is not
 * reliably true even when the numbers are right. `0.1 + 0.2` is `0.30000000000000004`.
 *
 * With Doubles the check would need a tolerance, and a tolerance is exactly the thing that
 * lets a real discrepancy hide. With Longs the comparison is exact, so a flagged gap is
 * always a real gap and a clean row is always genuinely clean.
 *
 * 100 pesewas to the cedi, and a Long holds more pesewas than anyone will ever transact.
 */

/** The cedi in pesewas. */
const val PESEWAS_PER_CEDI = 100L

private val MONEY = Regex("""^(\d+)(?:\.(\d{1,2}))?$""")

/**
 * Turns the digits captured from an SMS into pesewas, or null if they are not a number.
 *
 * Handles the shapes MTN actually sends: `10.00`, `20`, `1,000.00`, `0.50`. Returns null
 * for anything else — including the literal `-` that appears where a tax figure should be
 * in shape 1 — because a value the parser cannot read must never become a silent zero.
 */
fun parseMoney(raw: String?): Long? {
    val cleaned = raw?.replace(",", "")?.trim() ?: return null
    val m = MONEY.matchEntire(cleaned) ?: return null

    val cedis = m.groupValues[1].toLongOrNull() ?: return null
    val fraction = m.groupValues[2]

    // "10.5" is fifty pesewas, not five. One digit means tenths.
    val pesewas = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 10
        else -> fraction.toLong()
    }
    return cedis * PESEWAS_PER_CEDI + pesewas
}

/** Renders pesewas the way MoMo writes them, so the app and the SMS agree: `GHS 10.00`. */
fun Long.asCedis(): String {
    val sign = if (this < 0) "-" else ""
    val abs = kotlin.math.abs(this)
    return "%sGHS %d.%02d".format(sign, abs / PESEWAS_PER_CEDI, abs % PESEWAS_PER_CEDI)
}
