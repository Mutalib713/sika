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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import gh.mutalib.sika.ui.theme.LocalSikaColors
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
@Composable
fun Modifier.glass(
    corner: Dp,
    fill: Color = GlassFill,
    rim: Color = GlassRim,
): Modifier = glassInternal(corner, fill, rim, LocalSikaColors.current.isDark)

private fun Modifier.glassInternal(
    corner: Dp,
    fill: Color,
    rim: Color,
    isDark: Boolean,
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .background(
        // The top edge is brighter than the bottom: light falls from above, so the rim
        // nearest the light source catches it. Without this the surface reads as flat
        // plastic rather than glass.
        //
        // ⚠ **A thin rim, not a wash.** This originally faded over 35% of the height, which
        // lightened the whole upper third — and the BALANCE label sits there. Measured on
        // the device 2026-08-31 at **3.27:1**, below the 4.5 floor, purely because of this
        // gradient. A real specular edge is a few pixels; brighter over a shorter run reads
        // as more glassy, not less, and leaves the text alone.
        Brush.verticalGradient(
            // ⚠ Inverted in light mode. A white specular edge on a near-white panel is
            // invisible; the light that "falls from above" has to be read as a *darker*
            // rim below it instead, or the surface loses its edge entirely.
            0f to if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.55f),
            0.055f to Color.Transparent,
        ),
    )
    .border(1.dp, rim, RoundedCornerShape(corner))

/**
 * The capsule fill.
 *
 * ⚠ **Inverts with the theme, and it has to.** On the navy, glass is a *lightening* of
 * what is behind it — white at 11%. On the light page that same white is invisible, so
 * light-mode glass is a near-opaque white panel that reads as raised instead: the frost
 * comes from the fill, not from a blur this app deliberately does not do.
 */
val GlassFill: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalSikaColors.current.isDark) Color.White.copy(alpha = 0.11f)
        else Color.White.copy(alpha = 0.82f)

/**
 * The dock sits over scrolling text, so it must **obscure**, not merely tint.
 *
 * ⚠ Measured on the device 2026-08-31: at alpha `0.86` a transaction row behind the dock
 * was still plainly readable — "TELECEL PUSH" and "−20.00" collided with the dock's own
 * labels. The arithmetic says why: 14% of white text still lands at roughly `#424852`
 * against a `#1B2133` dock, which the eye separates easily.
 *
 * `0.94` leaves about 6%, which reads as a faint warmth rather than words. Paired with
 * [scrimBehindDock], content is nearly gone before it arrives.
 */
val DockFill: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalSikaColors.current.isDark) Color(0xFF161B29).copy(alpha = 0.94f)
        else Color(0xFFFFFFFF).copy(alpha = 0.96f)

/** The hairline that catches light. White on the navy; a soft shadow line on the page. */
val GlassRim: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalSikaColors.current.isDark) Color.White.copy(alpha = 0.14f)
        else Color(0xFF141821).copy(alpha = 0.10f)

/**
 * A gradient that fades content out before it reaches the dock — the same trick iOS uses
 * behind a tab bar. Sits between the list and the dock.
 *
 * Doing the work here rather than making the dock fully opaque is what lets it stay glass:
 * the aura still shows through, but scrolling text does not.
 */
@Composable
fun Modifier.scrimBehindDock(background: Color): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to background,
        ),
    )
}

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
