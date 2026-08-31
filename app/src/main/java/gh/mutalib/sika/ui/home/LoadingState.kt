package gh.mutalib.sika.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.glass
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/**
 * What you see while the inbox is being read.
 *
 * ⚠ **A skeleton, not a spinner.** A spinner says "wait" and nothing else. This says what is
 * about to arrive — the capsule with its four figures, then a list of transactions — so the
 * screen you are waiting for is already familiar when it lands, and the jump from loading to
 * loaded is a fill rather than a replacement.
 *
 * It matters more here than in most apps because the **first** run is the slow one: Sika is
 * reading 300 messages and reconciling 45 transactions, so this is the screen that forms the
 * first impression of whether the app is doing something real.
 */
@Composable
fun LoadingState(
    animated: Boolean,
    modifier: Modifier = Modifier,
    /** False on pull-to-refresh, where the real header is already on screen above this. */
    showHeader: Boolean = true,
) {
    // A slow breath, not a sweeping shimmer. Shimmer that races across a screen reads as
    // decoration; a pulse at this speed reads as a pulse.
    val pulse = if (!animated) {
        0.55f
    } else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val v by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        v
    }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        Column(
            Modifier
                .fillMaxSize()
                .padding(PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp)),
        ) {
            // The header, real rather than skeletal: the greeting is known before any
            // message is read, so faking it as a grey bar would be pretending not to know
            // something we know.
            if (showHeader) {
                Text(
                    "${greeting()}, $OWNER",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                "Reading your MoMo messages…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))

            // The capsule, in outline. Same corner radius and height as the real one, so it
            // does not jump when the figures arrive.
            Column(
                Modifier.fillMaxWidth().glass(corner = 30.dp).padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    SkeletonCell(pulse, Modifier.weight(1f))
                    SkeletonCell(pulse, Modifier.weight(1f))
                }
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth()) {
                    SkeletonCell(pulse, Modifier.weight(1f))
                    SkeletonCell(pulse, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("RECENT", style = LabelStyle, color = TextMuted.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))

            // Five rows, because five is what Home shows. Widths vary so it reads as a list
            // of different things rather than a stack of identical bars.
            listOf(0.62f, 0.48f, 0.70f, 0.55f, 0.66f).forEach { width ->
                SkeletonRow(pulse, width)
            }
        }
    }
}

@Composable
private fun SkeletonCell(pulse: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Bar(pulse, width = 74.dp, height = 9.dp)
        Spacer(Modifier.height(11.dp))
        Bar(pulse, width = 118.dp, height = 20.dp)
    }
}

@Composable
private fun SkeletonRow(pulse: Float, nameWidth: Float) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .alpha(pulse)
                .background(TextMuted.copy(alpha = 0.22f)),
        )
        Spacer(Modifier.width(13.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Bar(pulse, width = (150 * nameWidth * 1.6f).dp, height = 12.dp)
            Bar(pulse, width = (110 * nameWidth).dp, height = 9.dp)
        }
        Bar(pulse, width = 62.dp, height = 13.dp)
    }
}

@Composable
private fun Bar(pulse: Float, width: Dp, height: Dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .alpha(pulse)
            .background(TextMuted.copy(alpha = 0.24f)),
    )
}
