package gh.mutalib.sika.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon

/**
 * Choosing which categories to keep, during setup.
 *
 * ⚠ **Mutalib asked for this on 2026-09-01 and it was not built until 2026-09-03.** His message
 * had two halves — the minus-to-exclude pattern for Settings, *"or on a onboarding screen we
 * ask the user the ones they want to include and exclude"* — and only the first was
 * implemented. He raised it again when the Categories screen looked unchanged to him, because
 * the half he was looking for is this screen.
 *
 * ⚠ **Everything starts ON, and that is the design, not a default nobody thought about.**
 * Variant A of four, his pick. It is the only one where continuing without touching anything
 * leaves you exactly where a new install already stood — so the screen can improve on the
 * default but never make it worse. The alternative that starts everything off forces a real
 * decision, and punishes a skipped one with a ledger you cannot label.
 *
 * ⚠ **`Other` is never listed.** It is `isProtected` in the seed and absorbs everything
 * unlabelled; a switch that must not be flipped is worse than no switch at all.
 *
 * ⚠ **Nothing is deleted here, ever.** Switching one off writes `isHidden`, the same flag the
 * Settings screen sets — so the choice is reversible from two places and no transaction can
 * ever be orphaned by it. That was his own reasoning about deleting a category in use, and it
 * applies just as well before there are any transactions at all.
 */
@Composable
fun CategoryPickScreen(
    categories: List<CategoryEntity>,
    excluded: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val offered = categories.filterNot { it.isProtected }
    // ⚠ No step dots. Those belong to the tour, which has a known length; the ask screens
    // branch — "are you a student" can add a page — so a "3 of 4" here would be a promise the
    // flow cannot keep.
    OnboardingFrame(
        // ⚠ Step 3 of the questions, not of the tour — see ASK_STEPS in AskScreens.kt. This
        // screen shipped without dashes at all, which is how the gap in the other three came
        // to light.
        step = 3, total = 4,
        primary = "Continue",
        onPrimary = onNext,
        quiet = "Keep them all",
        onQuiet = onSkip,
    ) {
        Headline("Which of these do you spend on?")
        Spacer(Modifier.height(10.dp))
        Sub(
            "All on to start. Switch off anything you never use — you can bring it back in " +
                "Settings, and add your own any time.",
        )
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                // ⚠ **No scroll and no height cap here — OnboardingFrame already scrolls.**
                // This card used to do both, and the result was a list that silently clipped:
                // six rows visible on a Pixel 6 Pro, the rest cut off, and no gesture that
                // could reach them. Two vertical scrolls nested on the same axis do not
                // cooperate — the outer one takes the drag, while the inner `heightIn` quietly
                // trims everything past its cap. Caught on the device 2026-09-03 with ten
                // categories; it looked fine at eight, which is why it shipped.
                .clip(RoundedCornerShape(17.dp))
                .background(Surface)
                .padding(horizontal = 13.dp),
        ) {
            offered.forEachIndexed { i, category ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TextMuted.copy(alpha = 0.13f)),
                    )
                }
                CategoryToggleRow(
                    name = category.name,
                    on = category.name !in excluded,
                    onChange = { onToggle(category.name, it) },
                )
            }
        }
    }
}

@Composable
private fun CategoryToggleRow(name: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colour = categoryColor(name)
    Row(
        Modifier
            .fillMaxWidth()
            // ⚠ The whole row toggles, not just the switch. A 34dp switch is a small target
            // for a thumb on a 6.7in phone, and there is nothing else on this row to hit.
            .clickable { onChange(!on) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colour.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(categoryIcon(name)),
                contentDescription = null,
                tint = colour,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentContrast,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Surface,
                uncheckedTrackColor = TextMuted.copy(alpha = 0.45f),
                uncheckedBorderColor = TextMuted.copy(alpha = 0.45f),
            ),
        )
    }
}
