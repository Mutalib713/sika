package gh.mutalib.sika.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ledger.CategorySlice
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
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon
import kotlin.math.abs

/** Which chart is on screen. Mutalib's request, 2026-09-01: switch, do not choose. */
enum class ChartKind { BARS, RING }

/**
 * Screen 3 — the report. PLAN task 13.
 *
 * **Direction:** *a quiet auditor, not a coach.* It states what happened and admits what it
 * does not know. The one emotional beat is the biggest-change sentence, and it earns its
 * weight by being the only sentence.
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
    // Held on the screen rather than in the ViewModel: it changes nothing about the data,
    // only which way you are looking at it, and it should reset to the bars on a fresh open.
    var chart by remember { mutableStateOf(ChartKind.BARS) }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 54.dp, bottom = 150.dp),
        ) {
            item {
                PeriodHeader(
                    label = summary?.period?.label ?: "",
                    // "All time" cannot be stepped through — there is nowhere to go.
                    steppable = mode != PeriodMode.ALL,
                    canStepForward = canStepForward,
                    onStep = onStep,
                )
                Spacer(Modifier.height(13.dp))
                ModeSwitch(mode = mode, onMode = onMode)
                Spacer(Modifier.height(20.dp))
            }

            if (summary == null) {
                item { Text("Adding it up…", style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
                return@LazyColumn
            }
            if (summary.isEmpty) {
                item { EmptyPeriod(mode) }
                return@LazyColumn
            }

            item {
                Headline(summary)
                Spacer(Modifier.height(16.dp))
                ChartCard(summary, chart, mode) { chart = it }
            }

            if (summary.tooLittleLabelledToBreakDown) {
                item { Spacer(Modifier.height(18.dp)); NothingLabelledYet(summary) }
            } else {
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Breakdown",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Total ${summary.moneyOut.asCedis()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
                items(summary.slices, key = { it.label }) { SliceRow(it) }
                item {
                    summary.biggestChange?.let {
                        Spacer(Modifier.height(18.dp))
                        BiggestChange(it, mode)
                    }
                }
            }

            item { Spacer(Modifier.height(22.dp)); HonestyNote(summary, mode) }
        }
    }
}

@Composable
private fun PeriodHeader(
    label: String,
    steppable: Boolean,
    canStepForward: Boolean,
    onStep: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        ThemeToggle()
        if (steppable) {
            Chevron(back = true, enabled = true) { onStep(-1) }
            Chevron(back = false, enabled = canStepForward) { onStep(1) }
        }
    }
}

@Composable
private fun Chevron(back: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            contentDescription = if (back) "Previous period" else "Next period",
            tint = TextMuted,
            modifier = Modifier.size(19.dp).rotate(if (back) 90f else -90f),
        )
    }
}

/**
 * Week / Month / Semester / All.
 *
 * Four options in a segmented row rather than a menu: all four are worth one tap, and a menu
 * would hide which one you are currently looking at.
 */
@Composable
private fun ModeSwitch(mode: PeriodMode, onMode: (PeriodMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(Surface),
    ) {
        PeriodMode.entries.forEach { option ->
            val on = option == mode
            Box(
                Modifier
                    .weight(1f)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.background(Accent) else Modifier)
                    .clickable { onMode(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (option) {
                        PeriodMode.WEEK -> "Week"
                        PeriodMode.MONTH -> "Month"
                        PeriodMode.SEMESTER -> "Semester"
                        PeriodMode.ALL -> "All"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) AccentContrast else TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Headline(s: PeriodSummary) {
    Text("TOTAL SPENT", style = LabelStyle, color = TextMuted)
    Spacer(Modifier.height(3.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(s.moneyOut.asCedis(), style = BalanceStyle, color = TextPrimary)
        s.biggestChange?.let { Spacer(Modifier.width(9.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("RECEIVED", s.moneyIn.asCedis(), Accent, Modifier.weight(1f))
        StatBox("LEFT NOW", s.closingBalance?.asCedis() ?: "—", TextPrimary, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(label, style = LabelStyle, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = StatMoneyStyle, color = color, maxLines = 1)
    }
}

/**
 * The chart, and the control that swaps it.
 *
 * The bars answer *when* the money went and the ring answers *what* it went on. Neither can
 * answer the other's question, which is exactly why this is a switch and not a decision.
 */
@Composable
private fun ChartCard(
    s: PeriodSummary,
    chart: ChartKind,
    mode: PeriodMode,
    onChart: (ChartKind) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(15.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (chart) {
                    ChartKind.BARS -> when (mode) {
                        PeriodMode.WEEK -> "Day by day"
                        PeriodMode.MONTH -> "Week by week"
                        PeriodMode.SEMESTER, PeriodMode.ALL -> "Month by month"
                    }
                    ChartKind.RING -> "Where it went"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            ChartToggle(chart, onChart)
        }
        Spacer(Modifier.height(14.dp))
        when (chart) {
            ChartKind.BARS -> BucketBars(s.buckets)
            ChartKind.RING -> {
                CategoryRing(s.slices, s.moneyOut)
                RingLegend(s.slices)
            }
        }
    }
}

@Composable
private fun ChartToggle(chart: ChartKind, onChart: (ChartKind) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(15.dp)).background(Border.copy(alpha = 0.5f)).padding(2.dp),
    ) {
        listOf(
            ChartKind.BARS to R.drawable.ic_chart_bars,
            ChartKind.RING to R.drawable.ic_chart_donut,
        ).forEach { (kind, icon) ->
            val on = kind == chart
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .then(if (on) Modifier.background(Accent) else Modifier)
                    .clickable { onChart(kind) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = if (kind == ChartKind.BARS) "Show bars" else "Show the ring",
                    tint = if (on) AccentContrast else TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One category: icon, name, share, amount, change, and a rail underneath. */
@Composable
private fun SliceRow(slice: CategorySlice) {
    val colour = categoryColor(slice.label.takeIf { it != UNCATEGORISED })
    Column(Modifier.fillMaxWidth().padding(top = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(33.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colour.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(categoryIcon(slice.label)),
                    contentDescription = null,
                    tint = colour,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(slice.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${Math.round(slice.share * 100)}% of total",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(slice.amount.asCedis(), style = StatMoneyStyle, color = TextPrimary)
                // Most rows carry no pill, and that is the design: a percentage only appears
                // where last period's base was big enough to make it mean something.
                slice.changePercent?.let { pct ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        (if (pct >= 0) "+" else "") + "$pct% vs last",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Border.copy(alpha = 0.6f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .padding(start = 44.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(slice.share.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colour),
            )
        }
    }
}

@Composable
private fun BiggestChange(slice: CategorySlice, mode: PeriodMode) {
    val change = slice.change ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Accent.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            "${slice.label} went ${if (change > 0) "up" else "down"} " +
                "${abs(change).asCedis()} this ${mode.noun}.",
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

@Composable
private fun NothingLabelledYet(s: PeriodSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Accent.copy(alpha = 0.10f))
            .padding(16.dp),
    ) {
        Text(
            "This report can't tell you where it went yet.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${Math.round(s.uncategorisedShare * 100)}% has no category. Tap any transaction " +
                "to label it — label one shop once and every payment to it is labelled from then on.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
}

/**
 * **The closing statement, and the thing no competitor has.**
 *
 * Every other money app hides its uncategorised bucket. Sika ends on it. Deliberately not
 * warn-coloured and deliberately not phrased as a task: the moment it reads as a nag it gets
 * dismissed, and the report goes back to claiming a completeness it does not have.
 */
@Composable
private fun HonestyNote(s: PeriodSummary, mode: PeriodMode) {
    HorizontalDivider(color = Border)
    Spacer(Modifier.height(15.dp))
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
            "Every cedi this ${mode.noun} is accounted for.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
    if (!s.hasPrevious && mode != PeriodMode.ALL) {
        Spacer(Modifier.height(9.dp))
        Text(
            "Nothing on record before this ${mode.noun}, so there is nothing to compare it against.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun EmptyPeriod(mode: PeriodMode) {
    Column(
        Modifier.fillMaxWidth().padding(top = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing this ${mode.noun}",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "No MoMo messages arrived in this stretch. Try the arrows, or another mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
