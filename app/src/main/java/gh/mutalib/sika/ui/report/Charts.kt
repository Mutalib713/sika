package gh.mutalib.sika.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.log10
import gh.mutalib.sika.ledger.BucketSpend
import gh.mutalib.sika.ledger.CategorySlice
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.StatMoneyStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor

/**
 * The two charts, both drawn by hand — PLAN task 13, extended 2026-09-01.
 *
 * Mutalib asked to switch between them rather than choose one, which is the right call:
 * they answer different questions and neither can answer the other's. The bars say **when**
 * the money went, the ring says **what** it went on.
 */

/**
 * Spend per bucket, with the same bucket one period earlier drawn faintly behind it.
 *
 * ⚠ **The pale bar is data, not decoration**, and that is the whole point of it. Mutalib
 * sent a close-up of the reference chart on 2026-09-01 and said to trace it rather than
 * describe it. The detail that settles the design is that **the pale bars are different
 * heights from each other** — a plain track would be one constant height, so the pale bar
 * must be carrying a value. With the reference's own "Current" legend, that value is the
 * previous period.
 *
 * So this Wednesday's bar sits in front of last Wednesday's, and the chart answers a
 * question the ring cannot: *is this week worse than the last one, and on which day*.
 *
 * ⚠ It degrades honestly. On "All time" there is no previous period, so nothing pale is
 * drawn — rather than inventing a comparison against nothing.
 */
@Composable
fun BucketBars(
    buckets: List<BucketSpend>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 118.dp,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    if (buckets.isEmpty()) return
    // Both series share one scale, or the comparison is a lie: a taller pale bar has to mean
    // more money, not a different axis.
    val tallest = buckets.maxOf { maxOf(it.amount, it.previous) }
    // ⚠ The axis tops out at a ROUND number, not at the tallest bar. Scaling to the data
    // means the top gridline reads "GHS 91.37", which is a number nobody can measure
    // against — and it makes every chart a different scale, so two weeks cannot be compared
    // by eye. A round ceiling is what turns bars into a graph.
    val ceiling = niceCeiling(tallest).coerceAtLeast(1L)
    val hasPrevious = buckets.any { it.previous > 0 }
    val busiest = buckets.indexOfFirst { it.amount == buckets.maxOf { b -> b.amount } }
    val accent = Accent
    val grid = Border
    val many = buckets.size > 12
    val gap = if (many) 2.dp else 6.dp

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(height)) {
            // The value axis. Three labels only — top, middle, nothing at the floor beyond
            // a zero — because a money chart read at a glance needs a sense of scale, not a
            // readable value for every bar. The exact figures live in the list below.
            Column(
                Modifier.fillMaxHeight().width(34.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(ceiling)
                AxisLabel(ceiling / 2)
                AxisLabel(0)
            }
            Spacer(Modifier.width(7.dp))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Gridlines sit behind the bars, at the same three heights as the labels.
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(grid.copy(alpha = if (it == 2) 0.9f else 0.45f)),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    buckets.forEachIndexed { index, bucket ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                // The whole column is the target, not the bar. A quiet day
                                // draws a 4dp stub, and asking anyone to hit that is asking
                                // them to stop trying.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(if (selected == index) null else index) },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // The taller of the two sets the column height; the solid bar is
                            // drawn over the pale one from the baseline up.
                            val tall = maxOf(bucket.amount, bucket.previous)
                            Box(
                                Modifier
                                    .fillMaxWidth(BAR_WIDTH)
                                    .fillMaxHeight(fraction(tall, ceiling)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                // Everything except the chosen bar steps back, rather than
                                // the chosen one lighting up: dimming the rest keeps the
                                // scale readable, while a brighter single bar would change
                                // the only thing the eye uses to compare heights.
                                val dim = selected != null && selected != index
                                if (hasPrevious) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                accent.copy(alpha = if (dim) 0.09f else 0.20f),
                                            ),
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(
                                            if (tall > 0) bucket.amount.toFloat() / tall else 0f,
                                        )
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (dim) accent.copy(alpha = 0.38f) else accent,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(41.dp)) // clears the axis gutter, so labels line up
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                buckets.forEachIndexed { index, bucket ->
                    Text(
                        // A selected bar always keeps its label, even in a long span where
                        // every other one is dropped — otherwise you can tap a bar and be
                        // shown a figure with no idea which day it belongs to.
                        if (many && index % 2 == 1 && index != selected) "" else bucket.label,
                        style = MaterialTheme.typography.bodySmall,
                        // ⚠ Only ONE label is ever accent. The busiest bucket is highlighted
                        // by default, but the moment something is picked that highlight steps
                        // back — two accent labels at once and you cannot tell which one you
                        // chose. Caught on the device: Mon was busiest, Sun was selected, and
                        // both looked chosen.
                        color = when {
                            index == selected -> Accent
                            selected == null && index == busiest && bucket.amount > 0 -> Accent
                            else -> TextMuted
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One rung of the value axis, in whole cedis — pesewas on an axis are noise. */
@Composable
private fun AxisLabel(pesewas: Long) {
    Text(
        (pesewas / 100).toString(),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted.copy(alpha = 0.75f),
        maxLines = 1,
    )
}

/**
 * Rounds up to the next 1, 2 or 5 times a power of ten — 9137 becomes 10000, 3400 becomes
 * 5000, 620 becomes 1000.
 *
 * This is what makes the gridlines land on numbers a person can hold: "100 / 50 / 0" rather
 * than "91.37 / 45.69 / 0". It also keeps the scale stable between neighbouring periods, so
 * two weeks of similar spending draw at the same size and can be compared by eye.
 */
private fun niceCeiling(value: Long): Long {
    if (value <= 0) return 100
    val magnitude = Math.pow(10.0, floor(log10(value.toDouble()))).toLong().coerceAtLeast(1)
    val steps = ceil(value.toDouble() / magnitude)
    val rounded = when {
        steps <= 1 -> 1
        steps <= 2 -> 2
        steps <= 5 -> 5
        else -> 10
    }
    return rounded * magnitude
}

/** A bucket with real money in it never renders as nothing — a hairline is still a fact. */
private fun fraction(value: Long, ceiling: Long): Float =
    if (value <= 0) 0f else (value.toFloat() / ceiling).coerceIn(0.035f, 1f)

/** The bar fills this much of its slot. Taken from the reference, which leaves real air. */
private const val BAR_WIDTH = 0.72f

/**
 * The category ring.
 *
 * ⚠ **Capped at five slices plus a merged remainder**, and that cap is why this is defensible
 * at all. I argued against a ring twice, on two grounds: past five or six slices the angles
 * stop resolving, and a single-hue palette cannot supply distinguishable colours. Moving to
 * the nine measured hues removed the second objection outright and this cap removes the
 * first. The full list below still carries every category, so the cap hides nothing.
 *
 * Gaps between segments are deliberate: touching arcs of similar lightness read as one shape,
 * and the gap is what makes the boundary a boundary.
 *
 * **Tap a slice for its figure**, the same gesture as the bars. A ring is good at "roughly
 * what share" and bad at "exactly how much", so the tap is not a nicety — it supplies the one
 * thing the shape genuinely cannot.
 */
@Composable
fun CategoryRing(
    slices: List<CategorySlice>,
    total: Long,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    if (slices.isEmpty() || total <= 0) return
    val parts = ringParts(slices)
    val colors = parts.map { categoryColor(it.first) }
    val track = Border
    val thickness = 30.dp

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(196.dp)
                .pointerInput(parts, selected) {
                    detectTapGestures { tap ->
                        val hit = sliceAt(tap, size.width.toFloat(), thickness.toPx(), parts, total)
                        // ⚠ **Dismiss first.** Mutalib's rule, 2026-09-01: while a figure is
                        // showing, ANY tap clears it — the same slice, a different slice, or
                        // the middle. Only a tap on a clean ring picks something.
                        //
                        // It costs a tap to move between slices, and that is the trade he
                        // asked for: on a ring the segments are wedges that meet at a point,
                        // so a thumb aiming at one regularly lands on its neighbour. Under
                        // select-on-tap that mis-hit silently swaps the figure for a
                        // different category's and looks like the right answer. Under
                        // dismiss-first the worst a mis-hit does is close the readout.
                        onSelect(if (selected == null) hit else null)
                    }
                },
        ) {
            val t = thickness.toPx()
            val arcSize = Size(size.width - t, size.height - t)
            val topLeft = Offset(t / 2, t / 2)
            drawArc(
                color = track.copy(alpha = 0.5f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = t),
            )
            val gap = 2.2f
            var angle = -90f
            parts.forEachIndexed { index, (_, amount) ->
                val sweep = 360f * (amount.toFloat() / total)
                if (sweep > gap) {
                    // Everything except the chosen slice steps back, matching the bars.
                    val dim = selected != null && selected != index
                    drawArc(
                        color = if (dim) colors[index].copy(alpha = 0.30f) else colors[index],
                        startAngle = angle + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        // The chosen slice is drawn thicker, so it reads as picked even in a
                        // photograph, where opacity alone is hard to judge.
                        style = Stroke(width = if (selected == index) t * 1.16f else t),
                    )
                }
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val pick = selected?.let { parts.getOrNull(it) }
            Text(
                pick?.first?.uppercase() ?: "TOTAL SPENT",
                style = LabelStyle,
                color = if (pick != null) categoryColor(pick.first) else TextMuted,
            )
            Spacer(Modifier.height(3.dp))
            Text((pick?.second ?: total).asCedis(), style = StatMoneyStyle, color = TextPrimary)
            if (pick != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Math.round(pick.second * 100.0 / total)}% of total",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/** The ring's slices: the top five, plus everything else merged into one. */
fun ringParts(slices: List<CategorySlice>): List<Pair<String, Long>> {
    val shown = slices.take(RING_SLICES).map { it.label to it.amount }
    val rest = slices.drop(RING_SLICES).sumOf { it.amount }
    return if (rest > 0) shown + ("Other" to rest) else shown
}

/**
 * Which slice a tap landed on, or null.
 *
 * ⚠ Taps inside the hole and outside the ring return null on purpose. The middle of a donut
 * is the one place a finger lands by accident, and treating that as "you chose the first
 * slice" would be a silent lie about what was picked.
 *
 * Angles are measured from twelve o'clock, because that is where the first segment starts.
 * `atan2` measures from three o'clock, hence the 90 degree shift.
 */
private fun sliceAt(
    tap: Offset,
    widthPx: Float,
    thicknessPx: Float,
    parts: List<Pair<String, Long>>,
    total: Long,
): Int? {
    val centre = widthPx / 2f
    val dx = tap.x - centre
    val dy = tap.y - centre
    val radius = hypot(dx, dy)
    val inner = centre - thicknessPx
    if (radius < inner || radius > centre) return null

    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (degrees < 0f) degrees += 360f

    var walked = 0f
    parts.forEachIndexed { index, (_, amount) ->
        val sweep = 360f * (amount.toFloat() / total)
        if (degrees >= walked && degrees < walked + sweep) return index
        walked += sweep
    }
    return null
}

/** The ring's legend — the three biggest, named and measured. Tappable, like the ring. */
@Composable
fun RingLegend(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val parts = ringParts(slices)
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        parts.take(3).forEachIndexed { index, pair ->
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(9.dp))
                    // Same rule as the ring itself, or the two would disagree about what a
                    // tap means while a figure is on screen.
                    .clickable { onSelect(if (selected == null) index else null) }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(categoryColor(pair.first)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    pair.first,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected == index) TextPrimary else TextMuted,
                )
            }
        }
    }
}

private const val RING_SLICES = 5
