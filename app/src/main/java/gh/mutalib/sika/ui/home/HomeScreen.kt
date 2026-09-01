package gh.mutalib.sika.ui.home

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.CategorySlice
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.UNCATEGORISED
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.ThemeToggle
import gh.mutalib.sika.ui.report.BucketBars
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.BalanceStyle
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.RowMoneyStyle
import gh.mutalib.sika.ui.theme.StatMoneyStyle
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal const val OWNER = "Osman"

/**
 * Screen 1 — Home, rebuilt as a dashboard on 2026-09-01.
 *
 * **The shape Mutalib picked (H2):** a filled card carrying the month, then everything else
 * about *this week* — the bars, then the categories under them — then the newest few rows.
 *
 * ⚠ **The card leads with the month and the section below it is the week, on purpose.** The
 * month is the number people quote at themselves; the week is the one you can still do
 * something about. Mixing them in one block would blur which question was being answered, so
 * they are separated by a heading that names the span.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    animated: Boolean,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    val pullState = rememberPullToRefreshState()
    var pickedBar by remember(state.weekSummary?.period) { mutableStateOf<Int?>(null) }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    containerColor = SurfaceRaised,
                    color = Accent,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
            ) {
                item { Greeting(state); Spacer(Modifier.height(16.dp)) }

                if (state.isEmpty) {
                    item { EmptyMonth() }
                    return@LazyColumn
                }

                item {
                    state.monthSummary?.let { HeroCard(it) }
                    Spacer(Modifier.height(11.dp))
                    state.monthSummary?.let { StatPair(it) }
                }

                item { Spacer(Modifier.height(20.dp)); SectionHeading("This week") }

                item {
                    state.weekSummary?.let { week ->
                        WeekChart(week, pickedBar) { pickedBar = it }
                        Spacer(Modifier.height(6.dp))
                        // Categories for the SAME week as the chart above — his instruction.
                        week.slices.take(3).forEach { CategoryRow(it) }
                        if (week.slices.isEmpty()) NothingSpentThisWeek()
                    }
                }

                item {
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        SectionHeading("Recent", Modifier.weight(1f))
                        Text(
                            // ⚠ "View all", never "See all 146". The count changes every time
                            // a message lands, so a label carrying it is stale the moment it
                            // is read — and it makes the control look like it is reporting a
                            // number rather than offering a door.
                            "View all",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onSeeAll)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }

                items(state.recent, key = { it.id }) { row ->
                    TransactionRow(row) { onTransactionClick(row) }
                }
            }
        }
    }
}

@Composable
private fun Greeting(state: HomeState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${greeting()}, $OWNER",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                subtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        ThemeToggle()
    }
}

/**
 * The headline card — a filled block in the accent, from the reference Mutalib sent.
 *
 * ⚠ **The progress row is NOT a budget.** In the reference it is a savings goal with a
 * target and a "$550 left" line. Sika has neither goals nor budgets, both deliberately out
 * of v1, so drawing that bar would mean inventing a target. It shows how much of the month
 * is categorised instead — a true number, and the one that decides whether the report can
 * explain anything. It puts the app's own honesty on the first screen rather than at the
 * bottom of the third.
 */
@Composable
private fun HeroCard(month: PeriodSummary) {
    val labelled = 1f - month.uncategorisedShare
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Accent)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "SPENT IN ${MONTH_FULL.format(month.period.start).uppercase()}",
            style = LabelStyle,
            // Dark ink on the aqua, never white — 1.49:1. docs/ui-guidelines.md.
            color = AccentContrast.copy(alpha = 0.68f),
        )
        Spacer(Modifier.height(3.dp))
        Text(month.moneyOut.asCedis(), style = BalanceStyle, color = AccentContrast)

        month.biggestChange?.change?.let { change ->
            Spacer(Modifier.height(7.dp))
            Text(
                "${if (change > 0) "↑" else "↓"} ${abs(change).asCedis()} on ${month.slices.firstOrNull()?.label ?: "last month"}",
                style = MaterialTheme.typography.bodySmall,
                color = AccentContrast,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(AccentContrast.copy(alpha = 0.13f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(13.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentContrast.copy(alpha = 0.16f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(labelled.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AccentContrast),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${Math.round(labelled * 100)}% categorised",
                style = MaterialTheme.typography.bodySmall,
                color = AccentContrast.copy(alpha = 0.75f),
            )
            if (month.unlabelledCashOut > 0) {
                Text(
                    "${month.unlabelledCashOut.asCedis()} unexplained",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentContrast.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun StatPair(month: PeriodSummary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Stat("RECEIVED", month.moneyIn.asCedis(), Accent, Modifier.weight(1f))
        Stat("LEFT NOW", month.closingBalance?.asCedis() ?: "—", TextPrimary, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, colour: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(label, style = LabelStyle, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = StatMoneyStyle, color = colour, maxLines = 1)
    }
}

@Composable
private fun WeekChart(week: PeriodSummary, picked: Int?, onPick: (Int?) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(15.dp),
    ) {
        val bar = picked?.let { week.buckets.getOrNull(it) }
        if (bar != null) {
            Text(bar.amount.asCedis(), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(bar.label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        } else {
            Text(
                week.moneyOut.asCedis(),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                "spent this week · tap a bar",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(13.dp))
        BucketBars(week.buckets, height = 92.dp, selected = picked, onSelect = onPick)
    }
}

@Composable
private fun CategoryRow(slice: CategorySlice) {
    val colour = categoryColor(slice.label.takeIf { it != UNCATEGORISED })
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(31.dp).clip(RoundedCornerShape(10.dp))
                    .background(colour.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(categoryIcon(slice.label)),
                    contentDescription = null,
                    tint = colour,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(slice.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${Math.round(slice.share * 100)}% of the week",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Text(slice.amount.asCedis(), style = StatMoneyStyle, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.padding(start = 42.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Border),
        ) {
            Box(
                Modifier.fillMaxWidth(slice.share.coerceIn(0f, 1f)).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(colour),
            )
        }
    }
}

@Composable
private fun NothingSpentThisWeek() {
    Text(
        "Nothing spent yet this week.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = TextPrimary,
        modifier = modifier,
    )
}

@Composable
internal fun TransactionRow(row: TransactionEntity, onClick: () -> Unit) {
    val colour = categoryColor(row.label)
    val incoming = row.direction == Direction.IN
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 9.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(31.dp).clip(RoundedCornerShape(10.dp))
                .background(colour.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(categoryIcon(row.label)),
                contentDescription = null,
                tint = colour,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.counterparty.ifBlank { "Unreadable message" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                (row.label ?: "Add category") + " · " +
                    TIME.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.label == null) TextMuted else colour,
            )
        }
        Text(
            (if (incoming) "+" else "−") + row.amount.asCedis(),
            style = RowMoneyStyle,
            color = if (incoming) Accent else TextPrimary,
        )
    }
}

@Composable
private fun EmptyMonth() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing yet this month",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Transactions appear here as MoMo texts arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun subtitle(state: HomeState): String = when {
    state.gaps > 0 -> "${state.gaps} ${if (state.gaps == 1) "transaction doesn't" else "transactions don't"} add up"
    state.unlabelled > 0 -> "${state.unlabelled} still need a category"
    state.total == 1 -> "1 transaction, all accounted for"
    state.total > 0 -> "${state.total} transactions, all accounted for"
    else -> "Nothing recorded this month yet"
}

/** Africa/Accra, so the greeting matches the clock on the wall rather than a server's. */
internal fun greeting(): String = when (java.time.LocalTime.now(ACCRA).hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mma")
private val MONTH_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
