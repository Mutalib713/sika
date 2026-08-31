package gh.mutalib.sika.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.glass
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.CellMoneyStyle
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextOnGlass
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Screen 2 of docs/screens.md — tap a row, give it a category.
 *
 * This is the screen that turns 44 rows reading "Add category" into a report worth opening,
 * so the whole design is built around **one tap being enough**: the chips are the first
 * thing under the amount, and the remember-toggle is on by default, because labelling the
 * same counterparty twice is the work Sika exists to remove.
 */
@Composable
fun TransactionSheet(
    row: TransactionEntity,
    categories: List<CategoryEntity>,
    onPick: (category: String, alsoRemember: Boolean) -> Unit,
    onAddCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remember by remember(row.id) { mutableStateOf(true) }
    var adding by remember(row.id) { mutableStateOf(false) }
    var newName by remember(row.id) { mutableStateOf("") }
    var showRaw by remember(row.id) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(SurfaceRaised)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 22.dp),
    ) {
        // Drag handle. The sheet is dismissible by swipe, and a handle is how that is
        // advertised without a word.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(TextMuted.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.height(18.dp))

        val incoming = row.direction == Direction.IN
        Text(
            (if (incoming) "+" else "−") + row.amount.asCedis(),
            style = CellMoneyStyle,
            color = if (incoming) Accent else TextPrimary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            row.counterparty.ifBlank { "Unreadable message" } + " · " +
                WHEN.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        Spacer(Modifier.height(22.dp))

        // ---- categories: the reason this sheet exists ----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { category ->
                Chip(
                    label = category.name,
                    selected = row.label == category.name,
                    onClick = { onPick(category.name, remember) },
                )
            }
            // The `+` lives at the end of the row, so a category can be created from the
            // moment you need it rather than in Settings. Mutalib's instruction, 2026-08-31.
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { adding = true }
                    .glass(corner = 19.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = TextOnGlass)
            }
        }

        if (adding) {
            Spacer(Modifier.height(12.dp))
            NewCategoryField(
                value = newName,
                onValue = { newName = it },
                onDone = {
                    if (newName.isNotBlank()) {
                        onAddCategory(newName)
                        onPick(newName.trim(), remember)
                    }
                    newName = ""
                    adding = false
                },
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---- the learn-once toggle, stated in plain words ----
        if (row.counterparty.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clickable { remember = !remember }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = remember,
                    onCheckedChange = { remember = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Accent,
                        checkmarkColor = AccentContrast,
                        uncheckedColor = TextMuted,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Always label ${row.counterparty} this way",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(14.dp))

        // ---- the receipt ----
        Detail("Fee", row.fee.asCedis())
        row.tax?.let { Detail("Tax", it.asCedis()) }
        row.balanceAfter?.let { Detail("Balance after", it.asCedis()) }
        Detail("Reference", row.reference ?: "—")
        Detail("MoMo transaction ID", row.txId)
        if (row.reconciled == Reconciled.GAP) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = Warn,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "This one doesn't add up against the balance before it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warn,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- the original message ----
        //
        // Kept reachable on purpose. A money app that hides where its numbers came from is
        // asking to be trusted for nothing; this is the receipt behind the receipt.
        Text(
            if (showRaw) "Hide original message" else "Show original message",
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
            modifier = Modifier.clickable { showRaw = !showRaw }.padding(vertical = 6.dp),
        )
        if (showRaw) {
            Spacer(Modifier.height(4.dp))
            Text(
                row.rawBody,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(19.dp))
            .then(
                // Selected chips are filled with the accent and carry dark text — never
                // white, which measures 1.49:1 on this aqua (docs/ui-guidelines.md).
                if (selected) {
                    Modifier.background(Accent)
                } else {
                    Modifier.glass(corner = 19.dp)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) AccentContrast else TextOnGlass,
        )
    }
}

@Composable
private fun NewCategoryField(value: String, onValue: (String) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(corner = 16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "New category",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMuted,
                    )
                }
                inner()
            },
        )
        Text(
            "Add",
            style = MaterialTheme.typography.titleMedium,
            color = if (value.isBlank()) TextMuted else Accent,
            modifier = Modifier.clickable(enabled = value.isNotBlank(), onClick = onDone),
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), style = LabelStyle, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mma")
