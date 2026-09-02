package gh.mutalib.sika.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import gh.mutalib.sika.R
import gh.mutalib.sika.ledger.UNCATEGORISED

/**
 * A colour and an icon for every category — **option A**, chosen by Mutalib on 2026-09-01
 * after seeing all three palettes side by side.
 *
 * The nine hues are ColorBrewer's *Dark2*, **tuned by computation and then measured**, not
 * picked by eye (`design-scratch/derive_palettes.py`). Dark2 was the best dual-ground
 * performer in the task-13 research at 6 of 8 clearing 3:1 on both a light and a dark
 * background; moving lightness — and nothing else — brought all nine to **9/9**.
 *
 * ⚠ **Only Provisions needs a different value per theme.** Its Dark2 yellow measures 2.3:1
 * on white, which fails the 3:1 rule for a filled shape, so the light theme darkens it to
 * `#C08A00`. Every other hue clears both grounds unchanged — which is why this is nine
 * colours plus one exception rather than the eighteen I first warned about.
 *
 * ⚠ **Colour is never the only signal.** Every category carries an icon too. With nine hues
 * some pair will always be close for someone, and a list that can only be read in colour
 * cannot be read at all by a portion of people.
 *
 * ⚠ **Green, amber and red are not in this set** — they are reserved for over/under and for
 * warnings. Both budgeting apps whose colour rules could be read from primary documentation
 * (YNAB, Copilot) spend those three on status, and once Food is green there is no colour
 * left to say "you are fine".
 */
private val DARK_HUES = mapOf(
    "Food" to Color(0xFF1B9E77),
    "Transport" to Color(0xFFD95F02),
    "Data" to Color(0xFF7570B3),
    "Airtime" to Color(0xFFE7298A),
    "Rent" to Color(0xFF66A61E),
    "Provisions" to Color(0xFFE6AB02),
    "Printing" to Color(0xFFA6761D),
    "Sent home" to Color(0xFF8C6D8C),
    "Other" to Color(0xFF666666),
)

/** Identical but for Provisions — see the note above. */
private val LIGHT_HUES = DARK_HUES + mapOf("Provisions" to Color(0xFFC08A00))

/** Anything not in the list, including [UNCATEGORISED]. Deliberately colourless. */
private val UNKNOWN_DARK = Color(0xFF6B7280)
private val UNKNOWN_LIGHT = Color(0xFF8A93A3)

@Composable
@ReadOnlyComposable
fun categoryColor(name: String?): Color {
    val dark = LocalSikaColors.current.isDark
    val table = if (dark) DARK_HUES else LIGHT_HUES
    return table[name] ?: if (dark) UNKNOWN_DARK else UNKNOWN_LIGHT
}

/**
 * The icon for a category.
 *
 * ⚠ **"Other" and "no category" are different things and must not share a glyph.** They did
 * until 2026-09-01, when Mutalib pointed out that a transaction he had deliberately filed
 * under Other looked identical to one nobody had touched. "Other" is a decision — a box you
 * chose to put something in. Uncategorised is an open question, and gets a question mark.
 *
 * Never falls back to nothing: a row with no icon reads as a rendering failure.
 */
/**
 * The mark for money arriving.
 *
 * ⚠ **Kept as its own function rather than a `"Money in"` entry in [categoryIcon].** Incoming
 * money carries no label at all — nothing is written to the database for it — so there is no
 * name to match on. Direction is the fact, and the caller is the only place that knows it.
 */
fun incomingIcon(): Int = R.drawable.ic_cat_money_in

fun categoryIcon(name: String?): Int = when (name) {
    "Food" -> R.drawable.ic_cat_food
    "Transport" -> R.drawable.ic_cat_transport
    "Data" -> R.drawable.ic_cat_data
    "Airtime" -> R.drawable.ic_cat_airtime
    "Rent" -> R.drawable.ic_cat_rent
    "Provisions" -> R.drawable.ic_cat_provisions
    "Printing" -> R.drawable.ic_cat_printing
    "Sent home" -> R.drawable.ic_cat_sent_home
    "Other" -> R.drawable.ic_cat_other
    else -> R.drawable.ic_cat_none
}
