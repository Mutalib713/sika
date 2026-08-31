package gh.mutalib.sika.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.glass
import gh.mutalib.sika.ui.specularSweep
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.BalanceStyle
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.RowMoneyStyle
import gh.mutalib.sika.ui.theme.StatMoneyStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextOnGlass
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * How many transactions Home shows before handing off to the full list.
 *
 * Mutalib's call, 2026-08-31: Home showed all 144 and became a wall. Home answers *where
 * am I right now*; the full history is a separate place you go on purpose.
 */
private const val RECENT_COUNT = 5

/**
 * Screen 1 of docs/screens.md, built to the direction in docs/ui-guidelines.md.
 *
 * Layout is skeleton **B**, chosen from four: a glass capsule carrying the balance and the
 * in/out pair, then a short list on the field — no cards, because proximity and hairlines
 * do the work boxes usually get asked for.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    animated: Boolean,
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    val entrance = rememberEntrance(animated)
    val recent = state.days.flatMap { it.rows }.take(RECENT_COUNT)

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding clears the floating dock — content scrolls behind it, which is
            // the point of a floating navigation layer.
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 130.dp),
        ) {
            item { MonthHeader(state) }
            item { Spacer(Modifier.height(18.dp)) }
            item { BalanceCapsule(state, animated, entrance) }

            if (!state.isEmpty) item { TodayLine(state) }

            // The status strips. Both sit flush with the capsule's left edge and share one
            // vertical rhythm, so they read as a set rather than two stray lines.
            if (state.gaps > 0) item { StatusStrip(R.drawable.ic_warning, gapText(state), Warn) }
            if (state.unlabelled > 0) {
                item { StatusStrip(null, unlabelledText(state.unlabelled), TextMuted) }
            }

            if (state.isEmpty) {
                item { EmptyMonth() }
            } else {
                item { SectionHeading("Recent") }
                items(recent, key = { it.id }) { row ->
                    TransactionRow(row, onClick = { onTransactionClick(row) })
                    if (row !== recent.last()) HorizontalDivider(color = Border, thickness = 1.dp)
                }
                item { SeeAllButton(state.total, onSeeAll) }
            }
        }
    }
}

@Composable
private fun MonthHeader(state: HomeState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(MONTH.format(state.month), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun BalanceCapsule(state: HomeState, animated: Boolean, entrance: EntranceClock) {
    val shown = ((state.balance ?: 0L) * entrance.count).toLong()

    Column(
        Modifier
            .fillMaxWidth()
            // Stage 1: the capsule drops in from above and fades up.
            .graphicsLayer {
                translationY = (1f - entrance.capsuleDrop) * -320f
                alpha = entrance.capsuleFade
            }
            .glass(corner = 30.dp)
            .specularSweep(enabled = animated)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        // Stage 2: contents rise from behind the capsule's own bottom edge, staggered. They
        // are clipped by the capsule, so it reads as the card filling rather than text
        // flying across the screen.
        Rising(entrance, 0) { Text("BALANCE", style = LabelStyle, color = TextOnGlass) }
        Spacer(Modifier.height(6.dp))
        Rising(entrance, 1) {
            Text(
                if (state.balance == null) "—" else shown.asCedis(),
                style = BalanceStyle,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(15.dp))
        Rising(entrance, 2) {
            HorizontalDivider(color = TextOnGlass.copy(alpha = 0.16f), thickness = 1.dp)
        }
        Spacer(Modifier.height(14.dp))
        Rising(entrance, 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("IN", style = LabelStyle, color = TextOnGlass)
                    Spacer(Modifier.height(4.dp))
                    Text("+" + state.moneyIn.asCedis().removePrefix("GHS "), style = StatMoneyStyle, color = Accent)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("OUT", style = LabelStyle, color = TextOnGlass)
                    Spacer(Modifier.height(4.dp))
                    Text("−" + state.moneyOut.asCedis().removePrefix("GHS "), style = StatMoneyStyle, color = TextPrimary)
                }
            }
        }
    }
}

/** One line of capsule content, rising into place on its own beat. */
@Composable
private fun Rising(entrance: EntranceClock, index: Int, content: @Composable () -> Unit) {
    val p = entrance.content(index)
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - p) * 46f
            alpha = p
        },
    ) { content() }
}

/**
 * A status line under the capsule: a reconciliation gap, or a count of unlabelled rows.
 *
 * ⚠ 20 dp of air above it and flush with the capsule's left edge — measured fix,
 * 2026-08-31. It previously sat tight under the capsule and looked like a caption that had
 * slipped, rather than a separate statement.
 */
@Composable
private fun StatusStrip(icon: Int?, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/**
 * What has left the wallet today.
 *
 * The single idea taken from the CediSmart dashboard, 2026-08-31. Everything else on that
 * dashboard — monthly income, savings progress, remaining — needs a figure Mutalib would
 * have to type. This one Sika reads.
 *
 * **Fees are included.** A GHS 5 transfer with a 50p fee cost GHS 5.50, and "spent today"
 * that quietly omits fees is the kind of small lie this app exists not to tell.
 */
@Composable
private fun TodayLine(state: HomeState) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("TODAY", style = LabelStyle, color = TextMuted)
        if (state.spentToday == 0L) {
            Text(
                if (state.countToday == 0) "Nothing yet" else "Nothing spent",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        } else {
            Text("−" + state.spentToday.asCedis().removePrefix("GHS "), style = StatMoneyStyle, color = TextPrimary)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = LabelStyle,
        color = TextMuted,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
    )
}

/** Hands off to the full history. Names what it does and how much there is of it. */
@Composable
private fun SeeAllButton(total: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .glass(corner = 16.dp)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "See all $total transactions",
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
        )
    }
}

@Composable
private fun TransactionRow(row: TransactionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).glass(corner = 13.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.counterparty.ifBlank { "Unreadable message" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                (row.label ?: "Add category") + " · " +
                    TIME.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val incoming = row.direction == Direction.IN
            Text(
                (if (incoming) "+" else "−") + row.amount.asCedis().removePrefix("GHS "),
                style = RowMoneyStyle,
                // Colour reinforces; the sign carries the meaning. docs/ui-guidelines.md.
                color = if (incoming) Accent else TextPrimary,
            )
            row.balanceAfter?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it.asCedis().removePrefix("GHS "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyMonth() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp).alpha(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing yet this month",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Transactions appear here as MoMo texts arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun gapText(state: HomeState): String {
    val gap = state.firstGap ?: return "${state.gaps} transactions don't add up"
    val day = DAY.format(Instant.ofEpochMilli(gap.occurredAt).atZone(ACCRA))
    return if (state.gaps == 1) {
        "One transaction on $day doesn't add up"
    } else {
        "${state.gaps} transactions don't add up, from $day"
    }
}

private fun unlabelledText(count: Int) =
    if (count == 1) "1 transaction needs a category" else "$count transactions need a category"

private val MONTH = DateTimeFormatter.ofPattern("MMMM")
private val DAY = DateTimeFormatter.ofPattern("d MMMM")
private val TIME = DateTimeFormatter.ofPattern("h:mma")
