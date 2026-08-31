package gh.mutalib.sika.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import gh.mutalib.sika.R

/**
 * Type for Sika. docs/ui-guidelines.md is canonical.
 *
 * **One family, IBM Plex Sans, for everything.** Chosen by Mutalib on 2026-08-31 from four
 * options shown side by side, after he said the previous pairing was too stylised — he
 * asked for "just a normal font".
 *
 * It replaces **both** Space Grotesk (money and headings) and Inter (body). One family
 * across a whole app is unusual advice, but it is right here: Sika is a ledger, the type has
 * one job, and a second face was buying variety nobody asked for.
 *
 * Bundled in the APK rather than fetched. Not a preference — Sika has no `INTERNET`
 * permission (Sacred Rule 1), so a downloadable font is impossible. Licensed under the SIL
 * Open Font License 1.1, which permits bundling. **Net saving of about 190 KB**, because the
 * two faces it replaces came to roughly 1 MB.
 *
 * ⚠ **Four static files, not one variable file.** The previous two faces were variable, so
 * one file covered every weight and the weights interpolated exactly. IBM does publish a
 * variable Plex, but neither Google Fonts nor jsDelivr would serve it as a TTF on
 * 2026-08-31 — Google's TTF fallback is four static cuts. The cost is four files instead of
 * one, and weights that snap to the nearest cut rather than interpolating. Swap to the
 * variable file if a reliable TTF source turns up.
 */
private fun plex(weight: Int) = Font(
    when (weight) {
        500 -> R.font.ibm_plex_sans_500
        600 -> R.font.ibm_plex_sans_600
        700 -> R.font.ibm_plex_sans_700
        else -> R.font.ibm_plex_sans_400
    },
    weight = FontWeight(weight),
)

val Plex = FontFamily(plex(400), plex(500), plex(600), plex(700))

/**
 * ⚠ **Every money figure uses tabular figures, and that is the point.**
 *
 * With proportional digits a column of amounts shifts left and right as the values change,
 * because a `1` is narrower than an `8`. A money app whose numbers wobble reads as
 * untrustworthy — the exact opposite of what reconciliation is for.
 *
 * ⚠ **IBM Plex Sans needs no feature to do this: its digits are natively tabular.** Measured
 * on 2026-08-31 with fontTools — all ten digits share one advance width, and the font ships
 * no `tnum` feature at all because it does not need one. That is *stronger* than the old
 * arrangement, not weaker: Space Grotesk and Inter both have **nine** different digit widths
 * and only became tabular because this setting switched the feature on. A font swap that
 * dropped the setting would have silently un-aligned every column.
 *
 * The setting is kept anyway. On Plex it is an inert no-op; it costs nothing and it keeps
 * the guarantee if the family is ever changed again.
 */
const val MoneyFeature = "tnum"

/**
 * The four figures on the capsule — out, in, today, balance.
 *
 * ⚠ **One size for all four, deliberately.** The balance was previously 44sp against an
 * 11sp label and a 19sp in/out pair, which made it read as *the* number and everything
 * beside it as a footnote. Mutalib could not tell which figure was which, and said the
 * balance was not the most relevant one anyway. Equal weight is what makes them comparable.
 */
val CellMoneyStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight.W600,
    fontSize = 26.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = MoneyFeature,
)

/** Kept for any screen that genuinely wants one dominant figure — the report's SPENT. */
val BalanceStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight.W600,
    fontSize = 44.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = MoneyFeature,
)

/** The in/out pair. */
val StatMoneyStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight.W600,
    fontSize = 19.sp,
    fontFeatureSettings = MoneyFeature,
)

/** One transaction's amount. */
val RowMoneyStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight.W500,
    fontSize = 15.sp,
    textAlign = TextAlign.End,
    fontFeatureSettings = MoneyFeature,
)

/** The uppercase micro-labels: BALANCE, IN, OUT, TODAY. */
val LabelStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight.W600,
    fontSize = 11.sp,
    letterSpacing = 0.14.em,
)

val SikaTypography = Typography(
    headlineSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.W600, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.W600, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.W400, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.W400, fontSize = 12.sp),
    labelSmall = LabelStyle,
)
