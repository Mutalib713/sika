package gh.mutalib.sika.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.createBitmap
import gh.mutalib.sika.TAG
import gh.mutalib.sika.ui.animationsEnabled
import kotlin.math.hypot
import kotlinx.coroutines.launch

/**
 * The theme switch as a circle that opens out of the button you pressed.
 *
 * **What it looks like, in ordinary words.** Tap the moon and the dark theme does not simply
 * replace the screen — it appears as a small circle under your finger and grows until it has
 * covered everything. Tap the sun and the dark circle shrinks back into the button and
 * disappears, leaving the light theme behind it. Mutalib asked for this after seeing it in
 * Telegram (2026-09-02).
 *
 * ### How it actually works, because the obvious way does not
 *
 * The instinct is to draw the *new* theme inside a growing circle on top of the old one. That
 * cannot be done: Compose holds one theme at a time, and drawing the whole app twice — once
 * light, once dark — would mean two copies of every screen, every scroll position and every
 * animation, fighting each other.
 *
 * So the trick is to freeze the past instead of predicting the future:
 *
 *  1. **Photograph the screen** as it is now, with `PixelCopy`. That is the OLD theme.
 *  2. **Lay the photograph over the app**, so nothing appears to change.
 *  3. **Switch the theme underneath it.** The app recomposes in the new colours, completely
 *     hidden behind the photograph.
 *  4. **Cut a hole in the photograph** and grow it from the button. What shows through the
 *     hole is the new theme, already there, waiting.
 *  5. Throw the photograph away.
 *
 * Going back to light is *the same photograph with the opposite cut*: instead of a growing
 * hole, the old dark screen is clipped down to a shrinking circle until there is nothing left
 * of it. One snapshot, one animation, two directions — which is exactly what Mutalib
 * described as "it does the opposite".
 *
 * ### What this cannot do, stated plainly
 *
 *  * **It does not ripple the status bar or the navigation bar icons.** Android draws those
 *    itself, above everything an app can paint. They are held at their old appearance until
 *    the circle finishes and then flip — see [LocalThemeRevealing].
 *  * **It does not survive a dialog.** A dialog lives in its own window, so it is not in the
 *    photograph. Changing the theme from the Appearance dialog closes it first and ripples
 *    from the Settings row instead, which is where you were looking.
 *  * **It is skipped entirely when animations are off.** `ANIMATOR_DURATION_SCALE == 0` is a
 *    person telling the phone they do not want this, and a full-screen wipe is exactly the
 *    kind of motion that setting exists to stop. docs/ui-guidelines.md, "Reduced motion".
 *  * **If the photograph fails, the theme still changes.** `PixelCopy` can refuse — a secure
 *    window, a surface that is not ready. The switch is never held hostage to the effect.
 */
private const val REVEAL_MS = 520

/**
 * Wraps the app, owns the snapshot, and hands down a setter that ripples.
 *
 * @param mode the current preference, owned by the caller so it can be persisted.
 * @param onModeChange applied *behind* the snapshot, once the photograph has been taken.
 */
@Composable
fun ThemeRevealHost(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    content: @Composable (setMode: (ThemeMode, Offset?) -> Unit) -> Unit,
) {
    val view = LocalView.current
    val context = view.context
    val animated = remember { animationsEnabled(context) }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var expanding by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }

    val setMode: (ThemeMode, Offset?) -> Unit = { next, from ->
        val currentlyDark = if (mode == ThemeMode.SYSTEM) {
            // Cheap enough to read here, and reading it avoids threading yet another
            // parameter down: what matters is only which way the circle should travel.
            (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            mode == ThemeMode.DARK
        }
        val nextDark = when (next) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        // ⚠ Nothing to reveal when the appearance is not actually changing. Picking
        // "Default phone theme" on a phone that is already light is a real setting change
        // and no visual one, and rippling for it would look like a bug.
        if (!animated || snapshot != null || nextDark == currentlyDark) {
            // ⚠ **Every skip says which one it was. Added 2026-09-04 because the ripple
            // stopped working and there was no way to find out why.** Four separate reasons
            // ended in the same silent `onModeChange`, so a broken effect and a deliberately
            // skipped one were indistinguishable from the outside — and from a log.
            Log.i(
                TAG,
                "theme reveal skipped: " + when {
                    !animated -> "reduced motion is on"
                    snapshot != null -> "one is already running"
                    else -> "the appearance is not changing"
                },
            )
            onModeChange(next)
        } else {
            capture(view) { bitmap ->
                if (bitmap == null) {
                    Log.w(TAG, "theme reveal: the screenshot failed, switching without it")
                    onModeChange(next)
                } else {
                    snapshot = bitmap.asImageBitmap()
                    origin = from ?: Offset(view.width / 2f, view.height / 2f)
                    // Dark arrives by growing; light arrives by the dark shrinking away.
                    expanding = nextDark
                    onModeChange(next)
                    scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
                        snapshot = null
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeRevealing provides (snapshot != null)) {
        Box(Modifier.fillMaxSize()) {
            content(setMode)
            snapshot?.let { image ->
                Canvas(
                    Modifier
                        .fillMaxSize()
                        // ⚠ Swallows touches for the half-second it is up. Without this a tap
                        // lands on whatever is *underneath* the photograph, which is not what
                        // the person can see — including a second tap on the theme button.
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                ) {
                    val full = maxRadius(origin, size)
                    val r = if (expanding) progress.value * full else (1f - progress.value) * full
                    val circle = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                left = origin.x - r, top = origin.y - r,
                                right = origin.x + r, bottom = origin.y + r,
                            ),
                        )
                    }
                    // Difference: keep the old screen everywhere EXCEPT the circle, so the new
                    // theme shows through a hole that grows.
                    // Intersect: keep the old screen only INSIDE the circle, so it shrinks away.
                    clipPath(circle, if (expanding) ClipOp.Difference else ClipOp.Intersect) {
                        drawImage(image)
                    }
                }
            }
        }
    }
}

/**
 * How far the circle has to travel to cover everything: the distance to the furthest corner.
 *
 * ⚠ Not half the diagonal, and not the longest edge. A ripple starting in the top-right
 * corner has to reach the bottom-left one, and any smaller radius leaves a crescent of the
 * old theme in the far corner for the last frame — which is precisely where the eye is
 * looking by then.
 */
private fun maxRadius(origin: Offset, size: Size): Float = maxOf(
    hypot(origin.x, origin.y),
    hypot(size.width - origin.x, origin.y),
    hypot(origin.x, size.height - origin.y),
    hypot(size.width - origin.x, size.height - origin.y),
)

/**
 * Photographs the window.
 *
 * ⚠ **`PixelCopy` rather than `view.draw(canvas)`.** Drawing the view by hand into a software
 * bitmap misses anything the GPU composited — and while Sika has no blur today, `Glass.kt`
 * names `RenderEffect` as where it is heading, at which point a hand-drawn copy would quietly
 * lose the glass and the ripple would flicker. `PixelCopy` asks the compositor for what is
 * actually on screen.
 *
 * It is asynchronous, by a frame or so. That delay is the reason the theme is changed inside
 * the callback rather than before it: switching first would let one frame of the new theme
 * escape before the photograph covers it, which reads as a flash.
 */
private fun capture(view: View, onReady: (Bitmap?) -> Unit) {
    val window = view.context.activityWindow()
    if (window == null || view.width <= 0 || view.height <= 0) {
        onReady(null)
        return
    }
    val bitmap = createBitmap(view.width, view.height)
    val at = IntArray(2).also(view::getLocationInWindow)
    runCatching {
        PixelCopy.request(
            window,
            Rect(at[0], at[1], at[0] + view.width, at[1] + view.height),
            bitmap,
            { result ->
                // The result code is the whole diagnosis: SUCCESS is 0, and everything else
                // names a different reason (ERROR_DESTINATION_INVALID, ERROR_SOURCE_NO_DATA,
                // ERROR_TIMEOUT…). Swallowing it left nothing to go on.
                if (result != PixelCopy.SUCCESS) Log.w(TAG, "PixelCopy refused: result=$result")
                onReady(if (result == PixelCopy.SUCCESS) bitmap else null)
            },
            Handler(Looper.getMainLooper()),
        )
    }.onFailure {
        Log.w(TAG, "PixelCopy threw", it)
        onReady(null)
    }
}

private fun Context.activityWindow(): Window? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}
