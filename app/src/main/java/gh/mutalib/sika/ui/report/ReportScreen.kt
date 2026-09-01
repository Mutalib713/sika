package gh.mutalib.sika.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ledger.CategorySlice
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.UNCATEGORISED
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.ThemeToggle
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.BalanceStyle
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.StatMoneyStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Screen 3 — the month report. PLAN task 13.
 *
 * **Direction, from the task-13 research:** *a quiet auditor, not a coach.* It states what
 * happened and admits what it does not know. The one emotional beat is the biggest-change
 * sentence, and it earns its weight by being the only sentence.
 *
 * Three findings from that research shaped what is on screen:
 *
 * 1. **No donut.** At six-plus categories the angular encoding stops resolving, and a
 *    one-hue palette cannot supply the distinguishable colours a multi-slice pie needs —
 *    you end up direct-labelling it, which is a table drawn in a circle. A single stacked
 *    band carries the part-to-whole cue; the ranked list does the actual comparing.
 * 2. **Rank by cedis, not percent**, and withhold the percentage on a small base. See
 *    [CategorySlice.changePercent].
 * 3. **The honesty note is the closing statement, not a footnote** — and it is not styled
 *    as a warning, because it is a statement of a limit rather than a thing to fix.
 */
@Composable
fun ReportScreen(
    summary: PeriodSummary?,
    mode: PeriodMode,
    canStepForward: Boolean,
    animated: Boolean,
    onStep: (Long) -> Unit,
    onMode: (PeriodMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 150.dp),
        ) {
            item {
                PeriodHeader(
                    label = summary?.period?.label ?: "",
                    canStepForward = canStepForward,
                    onStep = onStep,
                )
                Spacer(Modifier.height(14.dp))
                ModeSwitch(mode = mode, onMode = onMode)
                Spacer(Modifier.height(24.dp))
            }

            if (summary == null) {
                item { Text("Adding it up…", style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
                return@LazyColumn
            }

            if (summary.isEmpty) {
                item { EmptyMonth() }
                return@LazyColumn
            }

            item {
                Headline(summary)
                Spacer(Modifier.height(24.dp))
            }

            // The four numbers above are always true. The breakdown below is only worth
            // drawing once enough is labelled to answer "where did it go".
            if (summary.tooLittleLabelledToBreakDown) {
                item { NothingLabelledYet(summary) }
            } else {
                item {
                    // The part-to-whole cue, in 10dp of height. The list below is its
                    // legend, which is why there is no legend.
                    StackedBand(summary.slices)
                    Spacer(Modifier.height(24.dp))
                    Text("WHERE IT WENT", style = LabelStyle, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                }
                items(summary.slices, key = { it.label }) { slice ->
                    SliceRow(slice, summary.slices.indexOf(slice), summary.period.mode.noun)
                }
                item {
                    summary.biggestChange?.let {
                        Spacer(Modifier.height(20.dp))
                        BiggestChange(it, summary.period.mode.noun)
                    }
                }
            }

            item {
                Spacer(Modifier.height(26.dp))
                HonestyNote(summary)
            }
        }
    }
}

@Composable
private fun PeriodHeader(label: String, canStepForward: Boolean, onStep: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        ThemeToggle()
        Spacer(Modifier.width(2.dp))
        Chevron(back = true, enabled = true) { onStep(-1) }
        Spacer(Modifier.width(6.dp))
        // Disabled rather than hidden: a control that vanishes reads as a glitch, and a
        // greyed one says "there is nothing later", which is the true reason.
        Chevron(back = false, enabled = canStepForward) { onStep(1) }
    }
}

/**
 * Week / Month / Semester — Mutalib's request, 2026-08-31.
 *
 * A segmented row rather than a dropdown: three options, all worth reaching in one tap, and
 * a menu would hide which one you are currently looking at. The selected pill carries the
 * accent with dark text on it, never white (docs/ui-guidelines.md).
 */
@Composable
private fun ModeSwitch(mode: PeriodMode, onMode: (PeriodMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Border.copy(alpha = 0.5f)),
    ) {
        PeriodMode.entries.forEach { option ->
            val on = option == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (on) Modifier.background(Accent) else Modifier)
                    .clickable { onMode(option) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (option) {
                        PeriodMode.WEEK -> "Week"
                        PeriodMode.MONTH -> "Month"
                        PeriodMode.SEMESTER -> "Semester"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (on) AccentContrast else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Chevron(back: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp) // ≥44dp touch target, docs/screens.md
            .clip(RoundedCornerShape(22.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            contentDescription = if (back) "Previous period" else "Next period",
            tint = TextMuted,
            // One chevron asset, rotated. Lucide's set is the only source of icons here,
            // and rotating beats shipping a second nearly identical file.
            modifier = Modifier.size(20.dp).rotate(if (back) 90f else -90f),
        )
    }
}

/**
 * The four numbers, with an obvious protagonist.
 *
 * Out is set large because it is the question actually being asked. In, Net and Closing sit
 * at roughly half its size in the same face, so they read as one family rather than four
 * competing headlines.
 */
@Composable
private fun Headline(s: PeriodSummary) {
    Text("SPENT", style = LabelStyle, color = TextMuted)
    Spacer(Modifier.height(4.dp))
    Text(s.moneyOut.asCedis(), style = BalanceStyle, color = TextPrimary)
    Spacer(Modifier.height(20.dp))
    // ⚠ **Stacked, not three across.** Side by side they had a third of the width each, and
    // "−GHS 1257.80" wrapped onto two lines on the real device — a four-figure month is
    // ordinary, so this was going to happen constantly. Stacking gives every number the
    // full width and lines the decimal points up into one vertical rule, which is the
    // cheapest thing that makes a hand-drawn list look engineered.
    Stat("RECEIVED", s.moneyIn.asCedis(), Accent)
    Stat(
        "NET",
        // The sign is what carries direction — never colour. docs/ui-guidelines.md.
        (if (s.net >= 0) "+" else "−") + abs(s.net).asCedis(),
        TextPrimary,
    )
    Stat("CLOSING BALANCE", s.closingBalance?.asCedis() ?: "—", TextPrimary)
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LabelStyle, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = StatMoneyStyle, color = color, maxLines = 1)
    }
}

/**
 * One horizontal band, hand-drawn, showing each category's share of the month.
 *
 * ⚠ **Capped at five segments plus a merged remainder.** Anything under a few percent is a
 * sliver too thin to read, so beyond five the tail is drawn as one block. Everything stays
 * itemised in the list below, where a short bar is still honest.
 *
 * ⚠ **What this cannot show:** absolute size. A band always fills the width, so a GHS 400
 * month and a GHS 1,200 month look identical. The SPENT figure above is the only thing
 * carrying magnitude, which is why it is the biggest thing on the screen.
 */
@Composable
private fun StackedBand(slices: List<CategorySlice>) {
    if (slices.isEmpty()) return
    val shown = slices.take(BAND_SEGMENTS)
    val tail = slices.drop(BAND_SEGMENTS).sumOf { it.share.toDouble() }.toFloat()

    // ⚠ Read here, in composable scope. A Canvas draw block is NOT a composable context,
    // so a colour read inside it would have to be a fixed constant — frozen at one theme.
    val accent = Accent
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val gap = 3.dp.toPx()
        val radius = CornerRadius(size.height / 2)
        var x = 0f
        val segments = shown.map { it.label to it.share } +
            if (tail > 0f) listOf("tail" to tail) else emptyList()

        segments.forEachIndexed { index, (label, share) ->
            val width = (size.width * share) - gap
            if (width <= 0f) return@forEachIndexed
            if (label == UNCATEGORISED) {
                // Drawn hollow on purpose: money nobody has categorised is not a category,
                // and giving it a solid fill would let it sit in the band as a peer of Food.
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    topLeft = Offset(x + 1f, 1f),
                    size = Size(width - 2f, size.height - 2f),
                    cornerRadius = radius,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            } else {
                drawRoundRect(
                    color = rampColor(accent, index),
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
            x += width + gap
        }
    }
}

@Composable
private fun SliceRow(slice: CategorySlice, rank: Int, unit: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slice.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(slice.amount.asCedis(), style = StatMoneyStyle, color = TextPrimary)
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The bar and the share say the same thing twice, on purpose: the bar is read
            // at a glance, the number is read when you care about the exact figure.
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Border),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(slice.share.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(rampColor(Accent, rank)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${Math.round(slice.share * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        // Most rows carry no arrow, and that is the design. A percentage is only shown
        // where last month's base was big enough to make it mean something.
        slice.changePercent?.let { percent ->
            Spacer(Modifier.height(5.dp))
            Text(
                (if (percent >= 0) "↑ " else "↓ ") + abs(percent) + "% on the previous $unit" +
                    " (" + (if (percent >= 0) "+" else "−") + abs(slice.change ?: 0).asCedis() + ")",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

/** The one sentence on the screen. Only shown when there genuinely is one story. */
@Composable
private fun BiggestChange(slice: CategorySlice, unit: String) {
    val change = slice.change ?: return
    val direction = if (change > 0) "up" else "down"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Accent.copy(alpha = 0.10f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "${slice.label} went $direction ${abs(change).asCedis()} this $unit.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "That's your biggest change.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
}

/**
 * **The closing statement, and the thing no competitor has.**
 *
 * Every other money app hides its uncategorised bucket. Sika ends on it. Deliberately not
 * `--warn` coloured and deliberately not phrased as a task: the moment it reads as a nag it
 * gets dismissed, and the report goes back to claiming a completeness it does not have.
 *
 * When there is nothing unexplained the block still appears, inverted — which is what makes
 * the numbers above it believable.
 */
@Composable
private fun HonestyNote(s: PeriodSummary) {
    HorizontalDivider(color = Border)
    Spacer(Modifier.height(16.dp))
    if (s.unlabelledCashOut > 0) {
        Text(
            "${s.unlabelledCashOut.asCedis()} was cashed out and never categorised.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "That's money this report can't explain.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    } else {
        Text(
            "Every cedi in this ${s.period.mode.noun} is accounted for.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
    if (!s.hasPrevious) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Nothing before this to compare it against.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

/**
 * Shown when almost nothing is labelled, in place of the breakdown.
 *
 * ⚠ Found on the device on 2026-08-31: with nothing labelled, the screen drew one
 * full-width bar reading "Uncategorised 100%" and a confident callout announcing that
 * Uncategorised was the biggest change. Every number on it was correct and the screen was
 * useless. **A report that looks informative while saying nothing is worse than one that
 * admits it has nothing to say.**
 *
 * This states the limit and points at the one action that lifts it. The four numbers above
 * stay, because those are true regardless of labelling.
 */
@Composable
private fun NothingLabelledYet(s: PeriodSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Accent.copy(alpha = 0.10f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            "This report can't tell you where it went yet.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // "this month" was wrong the moment the period could be a week or a semester.
            "${Math.round(s.uncategorisedShare * 100)}% of this period has no category. " +
                "Tap any transaction on Home to label it — label one shop once and every " +
                "payment to it is labelled from then on.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
}

@Composable
private fun EmptyMonth() {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Nothing in this period",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "No MoMo messages arrived in this stretch. Try the arrows, or a different mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One hue, stepped down in lightness by rank — biggest category at full aqua.
 *
 * That reads as ranked by construction and needs no legend. The steps are kept far apart
 * because adjacent tints of one hue turn to mush on an OLED at low brightness.
 */
private fun rampColor(accent: Color, rank: Int): Color =
    accent.copy(alpha = RAMP.getOrElse(rank) { RAMP.last() })

private val RAMP = listOf(1f, 0.78f, 0.60f, 0.45f, 0.33f, 0.24f)
private const val BAND_SEGMENTS = 5
