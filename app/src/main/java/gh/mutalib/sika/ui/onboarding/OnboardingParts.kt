package gh.mutalib.sika.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/**
 * The furniture every first-run screen shares.
 *
 * ⚠ **The step dots sit above the button, not at the top of the screen.** Mutalib's
 * correction, 2026-09-01, and he is right: they belong beside the control that advances them,
 * where they can be read as "three more presses" rather than as decoration.
 *
 * ⚠ **The content scrolls.** Every screen here fits on a Pixel 6 Pro at the default text size
 * and none of them fit at the largest one, and an onboarding screen whose button has been
 * pushed off the bottom is a dead end with no way past it.
 */
@Composable
fun OnboardingFrame(
    step: Int = 0,
    total: Int = 0,
    onSkip: (() -> Unit)? = null,
    primary: String,
    onPrimary: () -> Unit,
    quiet: String? = null,
    onQuiet: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Aura(animated = true)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 18.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(30.dp)) {
                if (onSkip != null) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.bodyMedium
                            .copy(fontWeight = FontWeight.W600),
                        color = TextMuted,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                content = content,
            )

            if (total > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(total) { i ->
                        val on = i + 1 == step
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .width(if (on) 28.dp else 20.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        on -> Accent
                                        i + 1 < step -> Accent.copy(alpha = 0.4f)
                                        else -> Border
                                    },
                                ),
                        )
                    }
                }
            }

            PrimaryButton(primary, onPrimary)
            if (quiet != null && onQuiet != null) {
                Spacer(Modifier.height(4.dp))
                QuietButton(quiet, onQuiet)
            }
        }
    }
}

/** Dark ink on the aqua, never white — 1.49:1. docs/ui-guidelines.md. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = AccentContrast)
    }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Headline(text: String, centred: Boolean = true) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall,
    color = TextPrimary,
    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth(),
)

@Composable
fun Sub(text: String, centred: Boolean = true) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = TextMuted,
    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth(),
)

@Composable
fun Fine(text: String, centred: Boolean = true) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = TextMuted,
    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth(),
)

/** A demo card: a real fragment of the app, shown rather than described. */
@Composable
fun DemoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = content,
    )
}

/** One point in a list, with an icon and a reason. */
@Composable
fun PointRow(icon: Int, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

/** A tappable answer. The chosen one takes the accent border rather than a filled panel. */
@Composable
fun ChoiceCard(label: String, detail: String, chosen: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (chosen) Accent.copy(alpha = 0.10f) else SurfaceRaised)
            .border(1.dp, if (chosen) Accent else Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/**
 * Content that slides in underneath an answer.
 *
 * ⚠ **`expandVertically`, not just a fade.** Mutalib asked for the semester dates to animate
 * in below the Yes rather than take their own page, and the height change is the part that
 * carries the meaning: the screen visibly grows *because of* what you just tapped. A fade
 * alone would look like it had been there all along and you had missed it.
 */
@Composable
fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220, delayMillis = 60)),
        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(120)),
    ) { content() }
}
