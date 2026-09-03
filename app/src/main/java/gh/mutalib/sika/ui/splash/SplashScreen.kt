package gh.mutalib.sika.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.animationsEnabled
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Bg
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The opening screen: a portal opens, the mark goes through it, then the name.
 *
 * **Why this exists on top of the system splash, and why it is not a second splash.** Android
 * 12 and above shows a splash for every app automatically — Sika had one before anyone asked,
 * it was just the launcher icon on a plain background. `Theme.Sika.Splash` styles that one.
 * This composable is what it hands over to, and the handover is built to be invisible: both
 * draw the same mark, on the same disc, on the same ground, in the same place, at the same
 * size. The system splash fades out over 180ms onto a picture of itself, so what you see is
 * one screen that starts still and then begins to move.
 *
 * ### Variant B, chosen by Mutalib from six on 2026-09-03
 *
 * He sketched it on paper: *"like a oval starts opening and the app logo comes from it. as it
 * comes the oval which is just like portals in scifi movies closes. the app logo when it comes
 * up it bounces after that then the name and desciption under comes"*. Six variants were
 * animated in a browser first and he picked **B**. The generator is
 * `design-scratch/make_splash_variants.py` — ⚠ **gitignored, so it is on his laptop and not
 * in this repo**; it is named here as provenance, not as a path anyone else can open.
 *
 * ⚠ **What separates B from the other five, and why it is the whole point.** In A the mark
 * travels *over* the portal, which reads as a disc sliding across an ellipse. In B the mark is
 * **clipped by the portal plane** — below the plane it does not exist. That occlusion is the
 * entire illusion in every sci-fi portal ever filmed, and it is one clipping box in code.
 *
 * ### The dip, which he did not draw, and why it is here anyway
 *
 * ⚠ **His sketch starts with the mark BELOW the portal. The system splash leaves it at its
 * FINAL position.** Both cannot be true. Starting the mark low means it jumps down the instant
 * Compose takes over — which is exactly the blink that was measured and killed on 2026-09-02.
 *
 * So the mark sinks into the portal as the portal opens, and rises back out. Nothing jumps, the
 * continuity from the system splash survives, and the portal earns itself: something went in and
 * something came out. It costs one beat he did not ask for, and he was told so before it shipped.
 *
 * ### The timing, and what each beat is for
 *
 * ```
 *   0ms   the mark, matching the system splash exactly — nothing appears to happen
 *   0ms   ──────────► ground washes system → app                        (420ms)
 * 100ms   ──────────► the portal opens: slit → full oval                (260ms)
 * 150ms   ──────────► the mark sinks through the plane and vanishes     (240ms)
 * 390ms   ──────────► it rises back out, clipped, and overshoots once   (480ms)
 * 630ms   ──────────► the portal shuts behind it                        (240ms)
 * 720ms   ──────────► "Sika" fades up and rises 12dp                    (300ms)
 * 840ms   ──────────► the line under it fades in                        (280ms)
 * 1120ms  hold, or longer if the ledger is still being read
 *   ↓     ──────────► everything fades out, mark drifts to 1.05         (260ms)
 * ```
 *
 * ⚠ **The hold is not a delay for its own sake.** Sika reads the whole SMS inbox on launch —
 * `Gate.Sweeping` — and that work has always happened behind a skeleton screen. The splash now
 * covers it. `MINIMUM_HOLD` is a floor, not a wait: if the sweep takes longer, the splash stays
 * up until it is done rather than dropping the person onto a half-built screen. So the cost is
 * not "the app got a second slower"; on a full inbox it is close to nothing.
 *
 * ⚠ **What this cannot do.** It cannot start before Android's own splash — nothing can, and on
 * `minSdk 31` that splash always runs. It cannot be skipped by tapping: at roughly a second, a
 * skip control is more interruption than the thing it skips.
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
    // ⚠ Read here, not inside the Canvas. `Accent` is a @Composable getter reading a
    // CompositionLocal, and a DrawScope lambda is not a composable scope — it would not
    // compile, and hoisting it also means the theme is sampled once per recomposition
    // rather than once per frame.
    val portalColour = Accent
    val appGround = Bg
    val wash = remember { Animatable(0f) }

    // How far the mark sits below its resting place, in dp. 0 is home; MARK_TRAVEL is fully
    // under the portal plane and therefore invisible.
    val markDrop = remember { Animatable(0f) }
    val markScale = remember { Animatable(1f) }
    val portalOpen = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameRise = remember { Animatable(if (animated) 12f else 0f) }
    val lineAlpha = remember { Animatable(0f) }
    val whole = remember { Animatable(1f) }

    // The entrance. It never ends the splash — that is the exit's job, below, because when to
    // leave depends on the sweep and not on the animation.
    LaunchedEffect(Unit) {
        // ⚠ Reduced motion is not "a shorter animation", it is no animation. A mark diving
        // through a portal is exactly the kind of movement ANIMATOR_DURATION_SCALE = 0 is
        // asking us not to make, and it is the single biggest movement in the app.
        // docs/ui-guidelines.md, "Reduced motion is honoured and kills all four".
        if (!animated) {
            nameAlpha.snapTo(1f)
            lineAlpha.snapTo(1f)
            wash.snapTo(1f)
            onFinished()
            return@LaunchedEffect
        }
        launch { wash.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
        launch {
            delay(100)
            portalOpen.animateTo(1f, tween(260, easing = ENTER))
            // Shuts behind the mark rather than after it has landed: by 630ms the mark is
            // already clear of the plane, so the portal closing reads as a consequence of the
            // arrival instead of a separate event tacked on the end.
            delay(270)
            portalOpen.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
        launch {
            delay(150)
            // Down fast — an exit should be quicker than an entrance.
            markDrop.animateTo(MARK_TRAVEL, tween(240, easing = FastOutSlowInEasing))
            // ...and back up slowly, with one overshoot. One, not a rubber ball: the bounce is
            // punctuation on an arrival, and a mark that wobbles three times reads as a toy.
            launch { markDrop.animateTo(0f, tween(480, easing = OVERSHOOT)) }
            launch {
                markScale.animateTo(1.045f, tween(300, easing = ENTER))
                markScale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            }
        }
        launch {
            delay(720)
            launch { nameAlpha.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
            launch { nameRise.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        }
        launch {
            delay(840)
            lineAlpha.animateTo(1f, tween(280))
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
            // ⚠ **This Box is exactly MARK_DISC and nothing else, on purpose.** The portal is
            // drawn as an overlay that takes no space in the layout, so the mark's resting
            // position on screen is byte-for-byte what it was before the portal existed — and
            // the measured match with the system splash's icon survives untouched. Putting the
            // portal in the Column's flow would push the mark up and reopen the seam.
            Box(Modifier.size(MARK_DISC), contentAlignment = Alignment.Center) {
                // The clipping window: everything ABOVE the portal plane. The plane is this
                // box's bottom edge, so a mark pushed below it simply stops being drawn. This
                // one modifier is the whole difference between variant B and variant A.
                Box(Modifier.matchParentSize().clipToBounds()) {
                    // ⚠ Drawn on its own pale disc, matching
                    // `windowSplashScreenIconBackgroundColor`. Without the disc the dark bubble
                    // in the mark would disappear into the dark ground in night mode — the same
                    // reason the system splash needs an icon background. The mark is the
                    // launcher foreground, so there is one drawing of Sika and not two that can
                    // drift apart.
                    Box(
                        Modifier
                            .matchParentSize()
                            // ⚠ graphicsLayer, not offset/size: translation and scale are
                            // draw-time properties, so the dive costs no measure or layout pass
                            // on any frame of it.
                            .graphicsLayer {
                                translationY = markDrop.value.dp.toPx()
                                scaleX = markScale.value
                                scaleY = markScale.value
                            }
                            .clip(CircleShape)
                            .background(DISC_GROUND),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null, // the name is right underneath it
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }

                // The portal itself, sitting on the plane — half above the clip line, half
                // below, which is what makes the mark appear to pass through its middle.
                Canvas(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(PORTAL_W, PORTAL_H)
                        .offset(y = PORTAL_H / 2)
                        .graphicsLayer {
                            // Opens as a slit first and widens: a shape that grows evenly in
                            // both directions reads as a balloon, not an aperture.
                            scaleX = 0.06f + 0.94f * portalOpen.value
                            scaleY = 0.12f + 0.88f * portalOpen.value
                            alpha = portalOpen.value.coerceIn(0f, 1f)
                        },
                ) {
                    drawOval(color = portalColour)
                }
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
 * Confident arrival, no elastic. `cubic-bezier(0.16, 1, 0.3, 1)` — the standard deceleration
 * curve for something that has travelled a distance and means to stop.
 */
private val ENTER = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * One overshoot and settle, for the rise out of the portal.
 *
 * ⚠ **Not `spring()`.** A spring's overshoot depends on velocity, so the same code bounces
 * differently depending on how the previous animation ended — and this one always follows a
 * dive, whose end velocity is not something worth being at the mercy of. A fixed curve bounces
 * identically every launch, which is what a signature moment needs.
 *
 * ⚠ **impeccable's `bounce-easing` rule fires on this, and it is overruled on purpose.** The
 * rule says bounce and elastic read as dated, and as a default it is right — which is why
 * nothing else in Sika bounces. Here the bounce is the specification: *"the app logo when it
 * comes up it bounces"*, Mutalib, 2026-09-03. It is held to ONE overshoot of 1.28 and no
 * second crossing, so it punctuates an arrival rather than wobbling like a toy.
 */
private val OVERSHOOT = CubicBezierEasing(0.22f, 1.28f, 0.32f, 1f)

/**
 * How far the mark drops to be fully hidden by the portal plane.
 *
 * Equal to the disc's own height, because the plane is the bottom edge of the disc's box: drop
 * it by its own height and every pixel of it is below the line.
 */
private val MARK_TRAVEL = 117f

/** Wide enough to read as a portal the mark came through, not a shadow it is sitting on. */
private val PORTAL_W = 140.dp
private val PORTAL_H = 30.dp

/** Matches `windowSplashScreenIconBackgroundColor` — the same pale field as the launcher icon. */
private val DISC_GROUND = Color(0xFFE8F6FA)

/**
 * The floor on how long the splash stays up.
 *
 * ⚠ **Raised from 900ms to 1120ms on 2026-09-03, and only because the entrance grew.** It is
 * not a taste change: the last beat of the portal sequence — the description line — finishes at
 * 1120ms, and a floor shorter than the animation would start the exit fade over a line that had
 * only just arrived. The rule is that the floor is the length of the entrance, so if the
 * choreography is ever shortened this comes down with it.
 *
 * Still short enough that it never becomes the thing standing between someone and their money,
 * and on a full inbox it costs nothing at all — the sweep is the longer of the two.
 */
private const val MINIMUM_HOLD = 1120L

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
