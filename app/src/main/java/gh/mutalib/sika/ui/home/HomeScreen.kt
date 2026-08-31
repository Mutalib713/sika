package gh.mutalib.sika.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Screen 1 of docs/screens.md, built to the direction in docs/ui-guidelines.md.
 *
 * Layout is skeleton **B**, chosen by Mutalib from four: a glass capsule carrying the
 * balance and the in/out pair, then a bare list on the field — no cards, because grouping
 * by proximity and hairlines does the work boxes usually get asked for.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    animated: Boolean,
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding clears the floating dock. Content scrolls *behind* it, which
            // is the point of a floating navigation layer.
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 118.dp),
        ) {
            item { MonthHeader(state) }
            item { Spacer(Modifier.height(18.dp)) }
            item { BalanceCapsule(state, animated) }

            if (state.gaps > 0) item { GapNotice(state) }
            if (state.unlabelled > 0) item { UnlabelledNudge(state.unlabelled) }

            if (state.isEmpty) {
                item { EmptyMonth() }
            }

            state.days.forEach { day ->
                item(key = "day-${day.label}") { DayMark(day.label) }
                items(day.rows, key = { it.id }) { row ->
                    TransactionRow(row, onClick = { onTransactionClick(row) })
                    if (row !== day.rows.last()) {
                        HorizontalDivider(color = Border, thickness = 1.dp)
                    }
                }
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
        Text(
            MONTH.format(state.month),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun BalanceCapsule(state: HomeState, animated: Boolean) {
    // The balance counts up once on arrival. 900ms, ease-out — the money is the point of
    // the screen, so it arrives rather than simply being there.
    val target = (state.balance ?: 0L).toFloat()
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animated) 900 else 0),
        label = "balance",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .glass(corner = 30.dp)
            .specularSweep(enabled = animated)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text("BALANCE", style = LabelStyle, color = TextOnGlass)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.balance == null) "—" else shown.toLong().asCedis(),
            style = BalanceStyle,
            color = TextPrimary,
        )
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = TextOnGlass.copy(alpha = 0.16f), thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
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

/**
 * The reconciliation notice. Appears only when the ledger's own arithmetic disagrees with
 * MoMo's stated balance — its absence is the app quietly saying the books are clean.
 */
@Composable
private fun GapNotice(state: HomeState) {
    val gap = state.firstGap
    val text = when {
        gap == null -> "${state.gaps} transactions don't add up"
        state.gaps == 1 -> "One transaction on ${DAY.format(Instant.ofEpochMilli(gap.occurredAt).atZone(ACCRA))} doesn't add up"
        else -> "${state.gaps} transactions don't add up, from ${DAY.format(Instant.ofEpochMilli(gap.occurredAt).atZone(ACCRA))}"
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = Warn,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Warn)
    }
}

@Composable
private fun UnlabelledNudge(count: Int) {
    Text(
        if (count == 1) "1 transaction needs a category" else "$count transactions need a category",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun DayMark(label: String) {
    Text(
        label.uppercase(),
        style = LabelStyle,
        color = TextMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun TransactionRow(row: TransactionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .glass(corner = 13.dp),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.counterparty.ifBlank { "Unreadable message" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(row.label ?: "Add category")
                    append(" · ")
                    append(TIME.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)))
                },
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
        Modifier.fillMaxWidth().padding(top = 64.dp),
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

private val MONTH = DateTimeFormatter.ofPattern("MMMM")
private val DAY = DateTimeFormatter.ofPattern("d MMMM")
private val TIME = DateTimeFormatter.ofPattern("h:mma")
