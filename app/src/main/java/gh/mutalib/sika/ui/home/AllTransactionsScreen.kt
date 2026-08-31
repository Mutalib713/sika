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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.glass
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.RowMoneyStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The full month, grouped by day. Reached from Home's "See all" — a place you go on
 * purpose, which is what lets Home stay short.
 *
 * ⚠ **Structure only.** This screen has not had its own design pass; it reuses Home's row
 * and its glass. Filters, search and month-switching belong here and are not built. Treat
 * it as the destination working, not the destination finished.
 */
@Composable
fun AllTransactionsScreen(
    state: HomeState,
    animated: Boolean,
    onBack: () -> Unit,
    // Compose convention, enforced by lint: modifier is the FIRST optional parameter, so a
    // caller can always pass it positionally without naming the callbacks.
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 130.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onBack)
                            .glass(corner = 16.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Back", style = MaterialTheme.typography.titleMedium, color = Accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${state.total} in ${MONTH.format(state.month)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
            }
            item { Spacer(Modifier.height(10.dp)) }

            state.days.forEach { day ->
                item(key = "day-${day.label}") {
                    Text(
                        day.label.uppercase(),
                        style = LabelStyle,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                    )
                }
                items(day.rows, key = { it.id }) { row ->
                    CompactRow(row) { onTransactionClick(row) }
                    if (row !== day.rows.last()) HorizontalDivider(color = Border, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun CompactRow(row: TransactionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
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
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            val incoming = row.direction == Direction.IN
            Text(
                (if (incoming) "+" else "−") + row.amount.asCedis().removePrefix("GHS "),
                style = RowMoneyStyle,
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

private val MONTH = DateTimeFormatter.ofPattern("MMMM")
private val TIME = DateTimeFormatter.ofPattern("h:mma")
