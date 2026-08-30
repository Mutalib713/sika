package gh.mutalib.sika.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Type for Sika. The system face for now; the display pairing is decided in the
 * design pass at PLAN task 10, not guessed here.
 *
 * The one rule that is already settled and is not a task-10 decision: **amounts use
 * tabular figures.** See [Amount].
 */
val SikaTypography = Typography()

/**
 * The style every money figure uses. **Not optional, and not cosmetic.**
 *
 * `FontFamily.Monospace` is standing in for a proportional face with tabular figures
 * until task 10 picks the real one. What matters is the property, not this particular
 * font: every digit must occupy the same width, so amounts line up vertically down a
 * list and the eye can compare them without reading.
 *
 * With proportional digits a column of amounts wobbles left and right, and a money app
 * whose numbers wobble reads as untrustworthy — which is the exact opposite of what
 * reconciliation is for.
 */
val Amount = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    textAlign = TextAlign.End,
)
