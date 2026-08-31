package gh.mutalib.sika.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The Home entrance: capsule drops from above, its contents rise from below, then the
 * balance counts up. 1.72 s end to end.
 *
 * **Plays on a cold start only.** Mutalib's choice from two options, 2026-08-31. It is a
 * pleasure the first time and a wait on the fortieth, so a warm return — coming back from
 * the Report tab, or from another app — renders the finished state immediately. The flag
 * lives at file scope rather than in a `remember`, so it survives recomposition and
 * navigation but dies with the process, which is exactly what "cold start" means.
 */
private var entrancePlayed = false

/** Elapsed milliseconds of the entrance. 1720 means finished. */
class EntranceClock(val elapsed: Float) {

    /** 0 → 1 as the capsule drops in from above. */
    val capsuleDrop: Float get() = easeOut(span(0f, 420f))

    /** 0 → 1 as the capsule fades up. Faster than the drop so it is never a ghost sliding. */
    val capsuleFade: Float get() = span(0f, 200f)

    /**
     * 0 → 1 for the content at [index], each 60 ms behind the one above it.
     *
     * Starts at 340 ms — **before the drop finishes at 420 ms, deliberately.** A clean
     * hand-off between the two stages reads as two separate animations; the 80 ms overlap
     * reads as one movement.
     */
    fun content(index: Int): Float {
        val start = 340f + index * 60f
        return easeOut(span(start, start + 420f))
    }

    /** 0 → 1 for the balance count-up. Last, because the money is the point of the screen. */
    val count: Float get() = easeOut(span(820f, 1720f))

    private fun span(from: Float, to: Float) = ((elapsed - from) / (to - from)).coerceIn(0f, 1f)
    private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)

    companion object {
        /** Everything already finished — the warm-return and reduced-motion state. */
        val Finished = EntranceClock(9_999f)
        const val DURATION_MS = 1_720
    }
}

/**
 * Drives [EntranceClock] once per cold start.
 *
 * @param animated false when the phone has animations turned off, in which case the
 * finished state is returned immediately rather than played at zero duration — the same
 * result, without a frame of work.
 */
@Composable
fun rememberEntrance(animated: Boolean): EntranceClock {
    if (!animated || entrancePlayed) return EntranceClock.Finished

    var clock by remember { mutableStateOf(EntranceClock(0f)) }
    LaunchedEffect(Unit) {
        val t = Animatable(0f)
        t.animateTo(
            targetValue = EntranceClock.DURATION_MS.toFloat(),
            // Linear on purpose: each stage applies its own easing, and easing the clock
            // as well would ease everything twice.
            animationSpec = tween(EntranceClock.DURATION_MS, easing = LinearEasing),
        ) { clock = EntranceClock(value) }
        entrancePlayed = true
        clock = EntranceClock.Finished
    }
    return clock
}
