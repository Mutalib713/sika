package gh.mutalib.sika.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
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
                    buckets.forEach { bucket ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
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
                                if (hasPrevious) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(accent.copy(alpha = 0.20f)),
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(
                                            if (tall > 0) bucket.amount.toFloat() / tall else 0f,
                                        )
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(accent),
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
                        // Long spans get every other label, or they overlap into mush.
                        if (many && index % 2 == 1) "" else bucket.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (index == busiest && bucket.amount > 0) Accent else TextMuted,
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
 * ⚠ **Capped at five slices plus a merged remainder**, and that cap is the reason this is
 * defensible at all. I argued against a ring twice on two grounds: past five or six slices
 * the angles stop resolving, and a single-hue palette cannot supply distinguishable colours.
 * Mutalib's move to the nine measured hues removed the second objection outright, and this
 * cap removes the first. The full list below the chart still carries every category, so the
 * cap hides nothing — it only stops the ring claiming to show what it cannot.
 *
 * Gaps between segments are deliberate: touching arcs of similar lightness read as one
 * shape, and the gap is what makes the boundary a boundary.
 */
@Composable
fun CategoryRing(
    slices: List<CategorySlice>,
    total: Long,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty() || total <= 0) return
    val shown = slices.take(RING_SLICES)
    val rest = slices.drop(RING_SLICES).sumOf { it.amount }
    val parts = shown.map { it.label to it.amount } +
        if (rest > 0) listOf("Other" to rest) else emptyList()
    val colors = parts.map { categoryColor(it.first) }
    val emptyTrack = Border

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(196.dp)) {
            val thickness = 30.dp.toPx()
            val inset = thickness / 2
            val arcSize = Size(size.width - thickness, size.height - thickness)
            val topLeft = Offset(inset, inset)
            // A faint full ring underneath, so the chart still has a shape while the
            // segments are being read — the same idea as the bars' track.
            drawArc(
                color = emptyTrack.copy(alpha = 0.5f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = thickness),
            )
            val gap = 2.2f
            var angle = -90f
            parts.forEachIndexed { index, (_, amount) ->
                val sweep = 360f * (amount.toFloat() / total)
                if (sweep > gap) {
                    drawArc(
                        color = colors[index],
                        startAngle = angle + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = thickness),
                    )
                }
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOTAL SPENT", style = LabelStyle, color = TextMuted)
            Spacer(Modifier.height(3.dp))
            Text(total.asCedis(), style = StatMoneyStyle, color = TextPrimary)
        }
    }
}

/** The ring's legend — the three biggest, named and measured. */
@Composable
fun RingLegend(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        slices.take(3).forEach { slice ->
            Row(
                Modifier.padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(categoryColor(slice.label)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${Math.round(slice.share * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            }
        }
    }
}

private const val RING_SLICES = 5
