package gh.mutalib.sika.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import gh.mutalib.sika.R

/**
 * Type for Sika. docs/ui-guidelines.md is canonical.
 *
 * **Space Grotesk** carries money and headings; **Inter** carries body copy.
 *
 * Both are **bundled in the APK**, not fetched. That is not a preference — Sika has no
 * `INTERNET` permission (Sacred Rule 1), so a downloadable font is impossible. About 1 MB
 * of the app is these two files, and for an app that never touches the network that is a
 * fair trade.
 *
 * Both are *variable* fonts: one file covers every weight, and [FontVariation] dials the
 * `wght` axis. Cheaper than shipping five static files, and the weights interpolate exactly
 * rather than snapping to the nearest cut.
 */

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))
val Inter = FontFamily(inter(400), inter(500), inter(600))

/**
 * ⚠ **Every money figure uses this, and the tabular figures are the point.**
 *
 * With proportional digits a column of amounts shifts left and right as the values change,
 * because a `1` is narrower than an `8`. A money app whose numbers wobble reads as
 * untrustworthy — the exact opposite of what reconciliation is for.
 *
 * Space Grotesk carries real `tnum` figures, which is why it was chosen over prettier faces.
 */
val MoneyFeature = "tnum"

/** GHS 179.29 on the balance capsule. */
val BalanceStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.W600,
    fontSize = 44.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = MoneyFeature,
)

/** The in/out pair. */
val StatMoneyStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.W600,
    fontSize = 19.sp,
    fontFeatureSettings = MoneyFeature,
)

/** One transaction's amount. */
val RowMoneyStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.W500,
    fontSize = 15.sp,
    textAlign = TextAlign.End,
    fontFeatureSettings = MoneyFeature,
)

/** The uppercase micro-labels: BALANCE, IN, OUT, TODAY. */
val LabelStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W600,
    fontSize = 11.sp,
    letterSpacing = 0.14.em,
)

val SikaTypography = Typography(
    headlineSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.W600, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 12.sp),
    labelSmall = LabelStyle,
)
