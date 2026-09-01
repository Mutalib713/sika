package gh.mutalib.sika.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
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

/** How far back the list reaches. */
private enum class Span(val label: String) {
    THIS_MONTH("This month"),
    EVERYTHING("All time"),
}

/** A size of transaction, in pesewas. */
private enum class Size(val label: String, val min: Long, val max: Long) {
    ANY("Any amount", 0, Long.MAX_VALUE),
    SMALL("Under GHS 20", 0, 2_000),
    MEDIUM("GHS 20 - 100", 2_000, 10_000),
    LARGE("Over GHS 100", 10_000, Long.MAX_VALUE),
}

/** What the list is currently narrowed to. */
private data class Filters(
    val span: Span = Span.THIS_MONTH,
    val category: String? = null,
    val size: Size = Size.ANY,
    val query: String = "",
) {
    val categoryLabel: String get() = category ?: "All categories"

    /**
     * True when nothing is narrowing the list.
     *
     * ⚠ The difference between "nothing matches your filter" and "you have no transactions"
     * is the difference between a problem you can solve in one tap and one you cannot solve at
     * all. The screen showed the first message in both cases until 2026-09-01.
     */
    val isDefault: Boolean
        get() = category == null && size == Size.ANY && query.isBlank()
}

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
    var searching by remember { mutableStateOf(false) }

    // The month-only list was what shipped first, and it quietly hid most of the ledger -
    // two rows on screen out of 148 on record. "All time" reads the whole thing.
    val source = if (filters.span == Span.EVERYTHING) state.allDays else state.days
    val categories = remember(state.allDays) {
        state.allDays.flatMap { it.rows }.mapNotNull { it.label }.distinct().sorted()
    }
    val days = remember(source, filters) {
        source
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
                        modifier = Modifier.weight(1f),
                    )
                    SmallIconButton(R.drawable.ic_search, "Search") { searching = !searching }
                }
                if (searching) {
                    Spacer(Modifier.height(10.dp))
                    SearchField(filters.query) { filters = filters.copy(query = it) }
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MenuChip(
                        label = filters.span.label,
                        on = filters.span != Span.THIS_MONTH,
                        options = Span.entries.map { it.label },
                    ) { i -> filters = filters.copy(span = Span.entries[i]) }

                    MenuChip(
                        label = filters.categoryLabel,
                        on = filters.category != null,
                        options = listOf("All categories") + categories,
                    ) { i -> filters = filters.copy(category = categories.getOrNull(i - 1)) }

                    MenuChip(
                        label = filters.size.label,
                        on = filters.size != Size.ANY,
                        options = Size.entries.map { it.label },
                    ) { i -> filters = filters.copy(size = Size.entries[i]) }
                }
                Spacer(Modifier.height(12.dp))
                Summary(shown, days)
            }

            if (days.isEmpty()) {
                // An empty ledger is not a filter problem, and telling someone to "try another
                // filter" when they have no transactions at all is advice that cannot work.
                item {
                    if (filters.isDefault && state.allDays.isEmpty()) NothingHereYet()
                    else NothingMatches()
                }
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

/** Every active filter must pass. They narrow together rather than replacing each other. */
private fun TransactionEntity.matches(f: Filters): Boolean {
    if (f.category != null && label != f.category) return false
    val size = amount + fee
    if (size < f.size.min || size >= f.size.max) return false
    if (f.query.isNotBlank() &&
        !counterparty.contains(f.query, ignoreCase = true) &&
        !(label ?: "").contains(f.query, ignoreCase = true)
    ) {
        return false
    }
    return true
}

@Composable
private fun SmallIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit) {
    // ⚠ Outlined, with the magnifier inside it. A filled box with grey placeholder text
    // does not read as typeable - the same mistake the note field made, found by Mutalib on
    // 2026-09-01. A placeholder is not an affordance.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, if (value.isEmpty()) Border else Accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = if (value.isEmpty()) TextMuted else Accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Search a shop or a category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
                inner()
            },
        )
    }
}

/**
 * A chip that opens a menu - the shape from the reference.
 *
 * It shows the CURRENT choice rather than the name of the field: "GHS 20 - 100", not
 * "Amount". A chip reading "Amount" makes you open it to find out what it is doing, which
 * is the one thing a row of filters must never require.
 */
@Composable
private fun MenuChip(
    label: String,
    on: Boolean,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(if (on) Accent else Surface)
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                // Dark ink on the aqua, never white - docs/ui-guidelines.md.
                color = if (on) AccentContrast else TextMuted,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = if (on) AccentContrast else TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Surface,
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == label) Accent else TextPrimary,
                        )
                    },
                    onClick = { onPick(index); open = false },
                )
            }
        }
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

/** No transactions at all — nothing to do with the filters. */
@Composable
private fun NothingHereYet() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing here yet",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Every MoMo message Sika reads becomes a row on this screen. None have been read " +
                "yet, so there is nothing to show — this is not a filter hiding them.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
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
