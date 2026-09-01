package gh.mutalib.sika.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.ledger.outflow
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.StatMoneyStyle
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor

/** What the list is currently narrowed to. */
private data class Filters(
    val category: String? = null,
    val incomingOnly: Boolean = false,
    val unlabelledOnly: Boolean = false,
)

/**
 * Screen 4 — every transaction this month, with filters.
 *
 * Built from the expenses-list reference Mutalib sent on 2026-09-01: filter chips across the
 * top, groups by day, and **each day carrying its own total** — which none of the references
 * do, and which is the number you actually want when scanning a list this long.
 *
 * ⚠ **Every chip here does something.** A row of controls where some are real and some are
 * decoration teaches you to distrust all of them, so the amount-range chip from the
 * reference is absent rather than present and inert. It arrives when it is wired.
 */
@Composable
fun AllTransactionsScreen(
    state: HomeState,
    animated: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    var filters by remember { mutableStateOf(Filters()) }

    val categories = remember(state.days) {
        state.days.flatMap { it.rows }.mapNotNull { it.label }.distinct().sorted()
    }
    val days = remember(state.days, filters) {
        state.days
            .map { day -> day.copy(rows = day.rows.filter { it.matches(filters) }) }
            // A day whose every row was filtered out must not leave its heading behind.
            .filter { it.rows.isNotEmpty() }
    }
    val shown = days.sumOf { it.rows.size }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_back_double),
                            contentDescription = "Back to Home",
                            tint = TextPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Chip("All", filters == Filters()) { filters = Filters() }
                    Chip("Money in", filters.incomingOnly) {
                        filters = Filters(incomingOnly = !filters.incomingOnly)
                    }
                    Chip("No category", filters.unlabelledOnly) {
                        filters = Filters(unlabelledOnly = !filters.unlabelledOnly)
                    }
                    categories.forEach { name ->
                        Chip(name, filters.category == name) {
                            filters = Filters(category = name.takeIf { filters.category != name })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Summary(shown, days)
            }

            if (days.isEmpty()) {
                item { NothingMatches() }
                return@LazyColumn
            }

            days.forEach { day ->
                item(key = "head-${day.label}") { DayHeading(day) }
                items(day.rows, key = { it.id }) { row ->
                    TransactionRow(row) { onTransactionClick(row) }
                }
            }
        }
    }
}

private fun TransactionEntity.matches(f: Filters): Boolean = when {
    f.incomingOnly -> direction == Direction.IN
    f.unlabelledOnly -> label == null
    f.category != null -> label == f.category
    else -> true
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (on) Accent else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            // Dark ink on the aqua, never white — docs/ui-guidelines.md.
            color = if (on) AccentContrast else TextMuted,
            maxLines = 1,
        )
    }
}

/** What is on screen right now, and what it comes to. */
@Composable
private fun Summary(shown: Int, days: List<DayGroup>) {
    val out = days.flatMap { it.rows }.filter { it.direction == Direction.OUT }.sumOf { it.outflow() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (shown == 1) "1 transaction" else "$shown transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text("−${out.asCedis()}", style = StatMoneyStyle, color = TextPrimary)
    }
}

/**
 * A day, and what left the wallet that day.
 *
 * The day total is the addition none of the references make, and it is the one that turns a
 * scroll into a scan: "Tuesday was GHS 183" is answerable at a glance, where adding six rows
 * in your head is not.
 */
@Composable
private fun DayHeading(day: DayGroup) {
    val out = day.rows.filter { it.direction == Direction.OUT }.sumOf { it.outflow() }
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(day.label, style = LabelStyle, color = TextMuted, modifier = Modifier.weight(1f))
        if (out > 0) {
            Text(
                "−${out.asCedis()}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun NothingMatches() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing matches that",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Try another filter, or tap All.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
