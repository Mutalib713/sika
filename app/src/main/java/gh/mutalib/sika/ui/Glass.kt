package gh.mutalib.sika.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The glass material.
 *
 * ⚠ **Apple's rule, recorded in docs/ui-guidelines.md: glass belongs to the navigation
 * layer only.** In Sika that is the balance capsule and the bottom dock — never the
 * transaction list, never the background, never stacked on itself.
 *
 * ### Why there is no backdrop blur here, and why that is not a shortcut
 *
 * I told Mutalib real backdrop blur would work on his Pixel because `minSdk` is 31. It
 * would — and on this screen it would achieve nothing.
 *
 * Blur exists to destroy *detail* behind a translucent surface. The capsule's backdrop is
 * [Aura]: three smooth radial gradients with no detail in them at all. Blurring a smooth
 * gradient returns the same smooth gradient. It would cost a GPU pass per frame, on a
 * surface that is already animating, for a result nobody could tell apart.
 *
 * What actually makes this read as glass is the other three things Apple's material does:
 * a translucent fill, a hairline **rim** that catches light, and a brighter **top edge**
 * where the specular highlight would fall.
 *
 * The one place real blur would earn its keep is the dock, because the transaction list
 * scrolls behind it — that *is* detail. [DockFill] handles it with a more opaque fill
 * instead, which obscures the same thing without a per-frame blur or a new dependency.
 * If the dock ever looks wrong over a dense list, that is the moment to reach for
 * `RenderEffect`, and not before.
 */
fun Modifier.glass(
    corner: Dp,
    fill: Color = GlassFill,
    rim: Color = GlassRim,
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .background(
        // The top edge is brighter than the bottom: light falls from above, so the rim
        // nearest the light source catches it. Without this the surface reads as flat
        // plastic rather than glass.
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.35f to Color.Transparent,
        ),
    )
    .border(1.dp, rim, RoundedCornerShape(corner))

/** The capsule: light fill, because the aura behind it is already smooth. */
val GlassFill = Color.White.copy(alpha = 0.11f)

/**
 * The dock sits over scrolling text, so it needs to obscure rather than merely tint.
 * More opaque than the capsule for that reason alone.
 */
val DockFill = Color(0xFF1B2133).copy(alpha = 0.86f)

val GlassRim = Color.White.copy(alpha = 0.14f)

/**
 * The specular sweep: one narrow band of light crossing the surface every 7.5 seconds,
 * travelling for under 3 of them and absent for the rest.
 *
 * The stillness is the design. A highlight that runs continuously is a shimmer, and
 * shimmer reads cheap — the research phrase worth keeping was *motion can feel luxurious
 * without shouting*. Apply to the capsule only; the dock is chrome and should not glint.
 */
@Composable
fun Modifier.specularSweep(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val t = rememberInfiniteTransition(label = "sweep")
    val x by t.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            // Held off-screen for the first 62% of the cycle, then crossing.
            animation = keyframes {
                durationMillis = 7500
                -0.4f at 0
                -0.4f at 4650
                1.4f at 7500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepX",
    )
    return this.drawWithContent {
        drawContent()
        val bandWidth = size.width * 0.28f
        val cx = size.width * x
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.13f),
                    Color.Transparent,
                ),
                start = Offset(cx - bandWidth, 0f),
                end = Offset(cx + bandWidth, size.height),
            ),
        )
    }
}
