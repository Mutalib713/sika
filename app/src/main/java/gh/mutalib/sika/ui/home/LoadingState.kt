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
import gh.mutalib.sika.ui.theme.Accent
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
                .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp)),
        ) {
            // The greeting is real rather than skeletal: it is known before any message is
            // read, so faking it as a grey bar would be pretending not to know something.
            if (showHeader) {
                Text(
                    greetingLine(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                "Reading your MoMo messages…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(16.dp))

            // ⚠ The skeleton mirrors the CURRENT Home, not the one this file was written
            // for. It drew the old glass capsule with four cells until 2026-09-01, long
            // after Home became a hero card over a chart — a skeleton that promises a
            // different screen from the one that arrives is worse than none, because the
            // jump from loading to loaded stops being a fill and becomes a replacement.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .alpha(pulse)
                    .background(Accent.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                SkeletonBlock(pulse, Modifier.weight(1f).height(62.dp))
                SkeletonBlock(pulse, Modifier.weight(1f).height(62.dp))
            }
            Spacer(Modifier.height(22.dp))
            Bar(pulse, width = 108.dp, height = 17.dp)
            Spacer(Modifier.height(11.dp))
            SkeletonBlock(pulse, Modifier.fillMaxWidth().height(168.dp))
            Spacer(Modifier.height(16.dp))
            listOf(0.62f, 0.48f, 0.70f).forEach { width -> SkeletonRow(pulse, width) }
        }
    }
}

/** A rounded block standing in for a card. */
@Composable
private fun SkeletonBlock(pulse: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .alpha(pulse)
            .background(TextMuted.copy(alpha = 0.20f)),
    )
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
