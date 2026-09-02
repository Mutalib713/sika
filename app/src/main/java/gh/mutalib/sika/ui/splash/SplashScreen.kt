package gh.mutalib.sika.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.animationsEnabled
import gh.mutalib.sika.ui.theme.Bg
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The opening screen: the mark, then the name, then the app.
 *
 * **Why this exists on top of the system splash, and why it is not a second splash.** Android
 * 12 and above shows a splash for every app automatically — Sika had one before anyone asked,
 * it was just the launcher icon on a plain background. `Theme.Sika.Splash` styles that one.
 * This composable is what it hands over to, and the handover is built to be invisible: both
 * draw the same mark, on the same disc, on the same ground, in the same place. The system
 * splash fades out over 180ms onto a picture of itself, so what you see is one screen that
 * starts still and then begins to move.
 *
 * ### The timing, and what each beat is for
 *
 * ```
 *   0ms   the mark, matching the system splash exactly — nothing appears to happen
 *   0ms   ──────────► mark settles 0.94 → 1.00                          (440ms)
 * 170ms   ──────────► "Sika" fades up and rises 12dp                    (380ms)
 * 260ms   ──────────► the line under it fades in                        (320ms)
 * 900ms   hold, or longer if the ledger is still being read
 *   ↓     ──────────► everything fades out, mark drifts to 1.05         (260ms)
 * ```
 *
 * ⚠ **The hold is not a delay for its own sake.** Sika reads the whole SMS inbox on launch —
 * `Gate.Sweeping` — and that work has always happened behind a skeleton screen. The splash now
 * covers it. `minimumHold` is a floor, not a wait: if the sweep takes longer, the splash stays
 * up until it is done rather than dropping the person onto a half-built screen. So the cost is
 * not "the app got a second slower"; on a full inbox it is close to nothing.
 *
 * ⚠ **What this cannot do.** It cannot start before Android's own splash — nothing can. And it
 * cannot be skipped by tapping: at under a second, a skip control is more interruption than the
 * thing it skips.
 *
 * ⚠ **The name is live text, not a picture.** Mutalib has said he may rename the app
 * (2026-09-01). A wordmark drawn as an image would mean redrawing an asset; this is one string.
 */
@Composable
fun SplashScreen(
    /** False while the ledger is still being read, so the splash can cover the wait. */
    appReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animated = remember { animationsEnabled(context) }

    // ⚠ **The one seam that cannot be designed away, so it is dressed instead.** Android picks
    // the system splash's background from `@color/bg` before any Sika code runs, which means
    // it follows the PHONE's light/dark setting — it has no way to know the person overrode
    // the theme inside the app. Caught on the Pixel 2026-09-02 by recording a cold start:
    // his phone is dark, Sika is set to Light, and the opening flashed dark then white.
    //
    // Reading the same resource here gives exactly the colour the system splash used, so this
    // screen can start on it and wash across to the app's real ground over 420ms. A mismatch
    // that cannot be prevented becomes a transition that looks intended.
    val systemGround = remember { Color(ContextCompat.getColor(context, R.color.bg)) }
    val appGround = Bg
    val wash = remember { Animatable(0f) }

    val markScale = remember { Animatable(if (animated) 0.94f else 1f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameRise = remember { Animatable(if (animated) 12f else 0f) }
    val lineAlpha = remember { Animatable(0f) }
    val whole = remember { Animatable(1f) }

    // The entrance. It never ends the splash — that is the exit's job, below, because when to
    // leave depends on the sweep and not on the animation.
    LaunchedEffect(Unit) {
        // ⚠ Reduced motion is not "a shorter animation", it is no animation. A full-screen
        // fade is exactly the kind of movement ANIMATOR_DURATION_SCALE = 0 is asking us not
        // to make. docs/ui-guidelines.md, "Reduced motion is honoured and kills all four".
        if (!animated) {
            nameAlpha.snapTo(1f)
            lineAlpha.snapTo(1f)
            wash.snapTo(1f)
            onFinished()
            return@LaunchedEffect
        }
        launch { wash.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
        launch { markScale.animateTo(1f, tween(440, easing = FastOutSlowInEasing)) }
        launch {
            delay(170)
            launch { nameAlpha.animateTo(1f, tween(380, easing = LinearOutSlowInEasing)) }
            launch { nameRise.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
        }
        launch {
            delay(260)
            lineAlpha.animateTo(1f, tween(320))
        }
    }

    // ⚠ **Keyed on `appReady`, which is what makes the hold a floor rather than a wait.** The
    // effect restarts when the sweep finishes; if the inbox was already read it is true on the
    // first pass and the splash lasts exactly MINIMUM_HOLD. If the sweep is slow, the splash
    // simply stays — nobody lands on a half-built screen, and nobody waits twice.
    LaunchedEffect(appReady) {
        if (!animated || !appReady) return@LaunchedEffect
        delay(MINIMUM_HOLD)
        launch { markScale.animateTo(1.05f, tween(260, easing = FastOutSlowInEasing)) }
        whole.animateTo(0f, tween(260))
        onFinished()
    }

    Box(
        modifier
            .fillMaxSize()
            .alpha(whole.value)
            .background(lerp(systemGround, appGround, wash.value)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ⚠ Drawn on its own pale disc, matching `windowSplashScreenIconBackgroundColor`.
            // Without the disc the dark bubble in the mark would disappear into the dark
            // ground in night mode — the same reason the system splash needs an icon
            // background. The mark is the launcher foreground, so there is one drawing of
            // Sika and not two that can drift apart.
            Box(
                Modifier
                    .size(MARK_DISC)
                    .scale(markScale.value)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F6FA)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null, // the name is right underneath it
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(MARK_DISC),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Sika",
                // headlineSmall rather than headlineMedium: SikaTypography only defines four
                // styles, and asking for one it does not define silently falls back to
                // Material's default face — which is not IBM Plex.
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.W600,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                // ⚠ graphicsLayer rather than an offset modifier: translation is a draw-time
                // property, so the rise costs no measure or layout pass on any frame.
                modifier = Modifier
                    .alpha(nameAlpha.value)
                    .graphicsLayer { translationY = nameRise.value.dp.toPx() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your MoMo, counted",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(lineAlpha.value),
            )
        }
    }
}

/**
 * The floor on how long the splash stays up.
 *
 * Long enough to be read as a deliberate opening rather than a stutter, short enough that it
 * never becomes the thing standing between someone and their money. 900ms is roughly the
 * count-up on the balance card, which is the app's existing sense of pace.
 */
private const val MINIMUM_HOLD = 900L

/**
 * Sized to match Android's own splash icon exactly, so the handover has nothing to give it away.
 *
 * ⚠ **Measured, not chosen.** 132dp was a guess and it was 13% too big: a screen recording of a
 * cold start put the system splash's disc at 62px and this one at 70px in the same frames, so
 * the mark visibly jumped smaller the moment Compose took over. 62/70 × 132 ≈ 117.
 *
 * If the system splash's icon sizing ever changes, re-record and re-measure — the ratio is the
 * only thing that matters here, and it can be read straight off two frames of the same video.
 */
private val MARK_DISC = 117.dp
