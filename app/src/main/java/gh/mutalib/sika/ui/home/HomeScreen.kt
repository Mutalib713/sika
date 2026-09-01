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
import androidx.compose.ui.graphics.graphicsLayer
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
    onOpenReport: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
    onExplainGap: (Long, String?) -> Unit = { _, _ -> },
) {
    val pullState = rememberPullToRefreshState()
    var pickedBar by remember(state.weekSummary?.period) { mutableStateOf<Int?>(null) }
    // ⚠ Restored 2026-09-01. Both of these were lost when Home was rebuilt as a dashboard —
    // the rewrite replaced the screen wholesale and quietly took the entrance and the
    // refresh skeleton with it. Neither failed loudly, which is why they were only noticed
    // by Mutalib using the app.
    val entrance = rememberEntrance(animated)

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
            // While a refresh is running the screen becomes its own skeleton, so the pull
            // has something to show for itself beyond a spinner.
            if (refreshing) {
                LoadingState(animated = animated, showHeader = false)
                return@PullToRefreshBox
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
            ) {
                item { Greeting(state); Spacer(Modifier.height(16.dp)) }

                if (state.isEmpty) {
                    item { if (state.neverAnything) NothingEverRead() else EmptyMonth(state) }
                    return@LazyColumn
                }

                item {
                    state.monthSummary?.let { month ->
                        // The card drops in from above; its contents rise into it; the money
                        // counts up last, because the money is the point of the screen.
                        Box(
                            Modifier.graphicsLayer {
                                translationY = (1f - entrance.capsuleDrop) * -320f
                                alpha = entrance.capsuleFade
                            },
                        ) {
                            HeroCard(month, entrance)
                        }
                        Spacer(Modifier.height(11.dp))
                        Rising(entrance, 2) { StatPair(month) }
                    }
                }

                // Directly under the money, because it is about the money. A gap used to be
                // one line of grey subtitle under the greeting - Sacred Rule 3 checks the
                // arithmetic on every sweep, and whispering the result is most of the way back
                // to not checking.
                state.gap?.let { gap ->
                    item {
                        Spacer(Modifier.height(11.dp))
                        Rising(entrance, 2) {
                            GapCard(gap, onExplain = { onExplainGap(gap.rowId, it) })
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Rising(entrance, 3) { SectionHeading("This week") }
                }

                item {
                    state.weekSummary?.let { week ->
                        WeekChart(week, pickedBar) { pickedBar = it }
                        Spacer(Modifier.height(6.dp))
                        // Categories for the SAME week as the chart above — his instruction.
                        week.slices.take(3).forEach { CategoryRow(it) }
                        if (week.slices.isEmpty()) NothingSpentThisWeek()
                        // ⚠ A button at the FOOT of the section, not a link beside its
                        // heading — his correction, 2026-09-01. A link by the title competes
                        // with the heading for the same glance; down here it sits where you
                        // arrive having read the section, which is the moment you would want
                        // more of it.
                        Spacer(Modifier.height(14.dp))
                        FullReportButton(onOpenReport)
                    }
                }

                item {
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        SectionHeading("Recent", Modifier.weight(1f))
                        // ⚠ **A chip, not bare text.** As plain accent-coloured words beside
                        // the heading it read as a caption rather than a control — Mutalib
                        // could not see it was tappable. A filled shape and an arrow are what
                        // say "this goes somewhere"; colour alone does not.
                        //
                        // "View all", never "See all 146": the count changes every time a
                        // message lands, so a label carrying it is stale the moment it is
                        // read, and it makes the control look like it is reporting a number
                        // rather than offering a door.
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Accent.copy(alpha = 0.14f))
                                .clickable(onClick = onSeeAll)
                                .padding(start = 13.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "View all",
                                style = MaterialTheme.typography.titleMedium,
                                color = Accent,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painterResource(R.drawable.ic_forward_double),
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }

                items(state.recent, key = { it.id }) { row ->
                    TransactionRow(row) { onTransactionClick(row) }
                }
            }
        }
    }
}

/**
 * One block of the entrance: rises from below into place, [index] slots behind the card.
 *
 * ⚠ Wrapped in a Box with a `graphicsLayer` rather than an offset modifier, because a
 * layer moves pixels that are already drawn while an offset re-measures the layout — and
 * re-measuring a LazyColumn item sixty times a second is how a smooth animation becomes a
 * stutter on a list.
 */
@Composable
private fun Rising(entrance: EntranceClock, index: Int, content: @Composable () -> Unit) {
    val progress = entrance.content(index)
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - progress) * 46f
            alpha = progress
        },
    ) {
        content()
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
private fun HeroCard(month: PeriodSummary, entrance: EntranceClock) {
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
        // ⚠ Counts up from zero, and lands on the real figure. The count is driven by the
        // entrance clock rather than its own animation so it cannot finish before the card
        // it sits on has arrived.
        Text(
            (month.moneyOut * entrance.count.toDouble()).toLong().asCedis(),
            style = BalanceStyle,
            color = AccentContrast,
        )

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
private fun FullReportButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("View full report", style = MaterialTheme.typography.titleMedium, color = Accent)
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
            // ⚠ **A note replaces the category name, it does not follow it.** Mutalib asked
            // how he was meant to see what he had written, and "Other - laptop repair" was
            // the obvious answer. It is the wrong one: "Other" means "not one of the named
            // ones", so it says nothing, and spending half the line on it pushes out the half
            // that says everything. The icon and the colour still mark the row as Other, so
            // nothing is lost by letting the words be the useful ones.
            Text(
                (row.note?.takeIf { it.isNotBlank() } ?: row.label ?: "Add category") + " · " +
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

/**
 * Nothing this month, but there is history.
 *
 * ⚠ **The opposite job to [NothingEverRead], and the two used to share one composable.** Here
 * the app is working and the month is simply quiet, so the useful thing is proof: name the
 * last transaction. "Transactions appear here as texts arrive" is true and says nothing.
 */
@Composable
private fun EmptyMonth(state: HomeState) {
    Column(
        Modifier.fillMaxWidth().padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing yet in " + MONTH_NAME.format(state.month),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        val last = state.lastEver
        Text(
            if (last == null) {
                "Anything new lands here the moment the text arrives."
            } else {
                "Your last transaction was " + last.amount.asCedis() + " to " +
                    last.counterparty.ifBlank { "an unnamed party" } + " on " +
                    DAY_MONTH.format(Instant.ofEpochMilli(last.occurredAt).atZone(ACCRA)) +
                    ". Anything new lands here the moment the text arrives."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

/**
 * No MoMo messages at all, ever.
 *
 * ⚠ **Says what Sika CANNOT do, which is the whole reason this is a separate screen.** It can
 * only read what is still in the phone's SMS inbox — there is no account, no server, no second
 * source. If the texts were deleted before Sika was installed, no button in this app brings
 * them back, and telling someone to wait would be a lie.
 *
 * The `*170#` route is offered because it is the one thing that genuinely helps, and it is
 * stated with its limit attached: MTN emails a PDF, and Sika cannot read a PDF. Mutalib asked
 * for this on 2026-09-01.
 */
@Composable
private fun NothingEverRead() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp, start = 14.dp, end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No MoMo messages on this phone",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Sika read the whole inbox and found nothing from MTN MoMo. If you have used " +
                "MoMo on this number, those texts may have been deleted — Sika can only read " +
                "what is still in the inbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "MTN can email you a statement going back three years: dial *170#, then My " +
                "Wallet, then Statements. That one is for your own eyes — Sika cannot read a " +
                "PDF and will not import it.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Pull down to read the inbox again.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

private val MONTH_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

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
