package gh.mutalib.sika.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
 * Spend per bucket, with a faint full-height track behind every bar.
 *
 * ⚠ **The track is the whole trick**, and it is what Mutalib meant by *"they have a way they
 * do it that is way nicer"*. Without it, a quiet day is a stub floating in space and the
 * chart has no shape until you read the labels. With it, every bucket occupies the same
 * visible slot and each bar reads as *a proportion of something* — so an empty Thursday
 * still looks like a Thursday that was empty, rather than a gap in the data.
 */
@Composable
fun BucketBars(
    buckets: List<BucketSpend>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
) {
    if (buckets.isEmpty()) return
    val peak = buckets.maxOf { it.amount }.coerceAtLeast(1L)
    // The busiest bucket is highlighted, because "when was the worst of it" is the first
    // question anyone asks of this chart.
    val busiest = buckets.indexOfFirst { it.amount == buckets.maxOf { b -> b.amount } }
    val track = Border
    val accent = Accent

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) 2.dp else 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEachIndexed { index, bucket ->
                val on = index == busiest && bucket.amount > 0
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(track.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val frac = bucket.amount.toFloat() / peak
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A bucket with real spending never renders as nothing: a hairline
                            // is still a fact, and zero height would read as no data.
                            .fillMaxHeight(if (bucket.amount > 0) frac.coerceAtLeast(0.04f) else 0f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) accent else accent.copy(alpha = 0.42f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) 2.dp else 7.dp),
        ) {
            buckets.forEachIndexed { index, bucket ->
                Text(
                    // Long spans get every other label, or they overlap into mush.
                    if (buckets.size > 12 && index % 2 == 1) "" else bucket.label,
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
