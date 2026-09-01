package gh.mutalib.sika.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import gh.mutalib.sika.ui.theme.Bg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **The signature move.** Three soft glows drift behind everything on long, unequal loops.
 *
 * Because the balance capsule and the dock are translucent, their tint is never quite the
 * same twice. The glass is not decoration — it is a window onto something moving. This is
 * the one thing in Sika that no other project of Mutalib's does (`house-taste.md`
 * anti-sameness guard), and it is why the glass is worth having at all: a flat dark
 * background gives glass nothing to refract.
 *
 * The loops are 26 s, 34 s and 30 s deliberately — coprime enough that the three never
 * line up into a visible pattern.
 *
 * @param animated false freezes every glow. Wired to the system's animator scale so a
 * phone with animations turned off gets a still image, per docs/ui-guidelines.md.
 */
@Composable
fun Aura(modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = rememberInfiniteTransition(label = "aura")

    @Composable
    fun phase(seconds: Int, label: String): Float =
        if (!animated) 0f else t.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(seconds * 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = label,
        ).value

    val p1 = phase(26, "g1")
    val p2 = phase(34, "g2")
    val p3 = phase(30, "g3")

    // ⚠ Read in composable scope. A Canvas draw block is not one, so a colour read inside
    // it would be frozen at whichever theme was current when the file was written.
    val ground = Bg
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(ground)

        // Each glow travels a small ellipse. The movement is tiny — 40-ish dp — because
        // the effect should read as light shifting, never as objects sliding about.
        fun glow(colour: Color, cx: Float, cy: Float, r: Float, phase: Float, sway: Float) {
            val x = cx + cos(phase) * sway
            val y = cy + sin(phase * 0.8f) * sway * 0.7f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colour, colour.copy(alpha = 0f)),
                    center = Offset(x, y),
                    radius = r,
                ),
                radius = r,
                center = Offset(x, y),
            )
        }

        glow(AuraAqua, w * 0.10f, h * 0.02f, w * 0.78f, p1, w * 0.10f)
        glow(AuraBlue, w * 0.98f, h * 0.34f, w * 0.72f, p2, w * 0.09f)
        glow(AuraAquaFaint, w * 0.42f, h * 0.98f, w * 0.62f, p3, w * 0.08f)
    }
}

/**
 * Aura colours, kept here rather than in the palette file on purpose: these are *light*,
 * not surfaces or text, and nothing may ever set type in them. Grown from the pinned
 * accent so the glow belongs to the same family.
 */
private val AuraAqua = Color(0x8AA8DCE7)
private val AuraBlue = Color(0x7A608CD7)
private val AuraAquaFaint = Color(0x44A8DCE7)
