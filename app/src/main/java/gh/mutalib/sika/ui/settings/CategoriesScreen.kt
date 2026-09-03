package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.agree
import gh.mutalib.sika.ui.count
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Danger
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon

/**
 * The categories, in use and put away.
 *
 * ⚠ **Nothing here deletes a category that holds transactions, and that was Mutalib's call.**
 * On 2026-09-01 he said deleting one already in use "can cause problems" and asked for the
 * minus-to-exclude pattern instead. He was right, and the plan he corrected was worse than it
 * looked: it would have moved every affected transaction to `Other`, which keeps the money and
 * throws away the only record of what the money was for.
 *
 * So there are two operations with two different weights:
 *   * **put away** — reversible, changes nothing but what the picker offers;
 *   * **delete** — only offered for a category nothing points at, where there is nothing to lose.
 */
@Composable
fun CategoriesScreen(
    state: SettingsState,
    animated: Boolean,
    onBack: () -> Unit,
    onHide: (CategoryRow, Boolean) -> Unit,
    onDelete: (CategoryRow) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<CategoryRow?>(null) }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                SubScreenHeader("Categories", onBack) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_plus),
                            contentDescription = "Add a category",
                            tint = Accent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            item {
                SectionLabel("IN USE · OFFERED WHEN YOU LABEL")
                SettingsCard {
                    state.inUse.forEachIndexed { i, row ->
                        if (i > 0) RowDivider()
                        CategoryListRow(
                            row = row,
                            putAway = false,
                            onHide = { confirming = row },
                            onBringBack = {},
                            onDelete = { onDelete(row) },
                        )
                    }
                }
            }

            if (state.putAway.isNotEmpty()) {
                item {
                    SectionLabel("PUT AWAY")
                    SettingsCard {
                        state.putAway.forEachIndexed { i, row ->
                            if (i > 0) RowDivider()
                            CategoryListRow(
                                row = row,
                                putAway = true,
                                onHide = {},
                                onBringBack = { onHide(row, false) },
                                onDelete = { onDelete(row) },
                            )
                        }
                    }
                }
            }

            item {
                Footnote(
                    "Putting one away only stops it being offered. Old transactions keep their " +
                        "label and the report still counts the money.",
                    "A category that was never used can be deleted instead.",
                )
            }
        }
    }

    confirming?.let { row ->
        PutAwayDialog(
            row = row,
            onConfirm = { onHide(row, true); confirming = null },
            onDismiss = { confirming = null },
        )
    }

    if (adding) {
        AddCategoryDialog(
            onAdd = { onAdd(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun CategoryListRow(
    row: CategoryRow,
    putAway: Boolean,
    onHide: () -> Unit,
    onBringBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val name = row.category.name
    Row(
        // ⚠ Dimmed, not greyed out. A put-away category is still a real thing you can bring
        // back in one tap; drawing it in the disabled colour would say it is broken.
        Modifier.fillMaxWidth().graphicsLayer { alpha = if (putAway) 0.55f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTile(categoryColor(name), categoryIcon(name))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    usageLine(row, putAway),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
        when {
            // `Other` is where anything that fits nothing else goes. Hide it and a transaction
            // can end up with no honest answer available at all.
            row.category.isProtected -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("Kept", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            row.canDelete -> CircleButton(
                icon = R.drawable.ic_trash,
                description = "Delete $name",
                tint = Danger,
                borderColor = Danger.copy(alpha = 0.4f),
                onClick = onDelete,
            )

            putAway -> CircleButton(
                icon = R.drawable.ic_plus,
                description = "Bring $name back",
                tint = Accent,
                borderColor = Accent.copy(alpha = 0.4f),
                onClick = onBringBack,
            )

            else -> CircleButton(
                icon = R.drawable.ic_minus,
                description = "Put $name away",
                tint = TextMuted,
                onClick = onHide,
            )
        }
    }
}

private fun usageLine(row: CategoryRow, putAway: Boolean): String = when {
    row.uses == 0 -> "Never used"
    putAway -> count(row.uses, "transaction") + " " +
        agree(row.uses, "keeps", "keep") + " this label"
    else -> count(row.uses, "transaction")
}

/**
 * ⚠ **Says what survives before anything happens.** The three things someone would worry
 * about — the label, the report, the money — are each named, because "are you sure?" answers
 * none of them and leaves the person to guess at the consequence.
 */
@Composable
private fun PutAwayDialog(row: CategoryRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = row.category.name
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border, RoundedCornerShape(22.dp))
                .padding(19.dp),
        ) {
            Text(
                "Put $name away?",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (row.uses == 0) {
                    "It stops appearing when you label something. You can bring it back any time."
                } else {
                    "It stops appearing when you label something. Its " +
                        count(row.uses, "transaction") + " " +
                        agree(row.uses, "keeps the label it already has", "keep the label they " +
                            "already have") + ", and the report still counts the money. " +
                        "You can bring it back any time."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton("Put it away", filled = true, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        if (name.isNotBlank()) onAdd(name)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border, RoundedCornerShape(22.dp))
                .padding(19.dp),
        ) {
            Text("New category", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "A name, and that is all. Every category is drawn in the same palette.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = {
                    Text("Barber", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton(
                    "Add it",
                    filled = true,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = { submit() },
                )
            }
        }
    }
}

/** Never white on the accent — 1.49:1, invisible. docs/ui-guidelines.md. */
@Composable
// ⚠ Internal rather than private since 2026-09-03: the semester editor is a second
// dialog in this package and needs the same button. Two copies of a button is how two
// dialogs end up looking subtly different.
internal fun DialogButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) Modifier.background(if (enabled) Accent else Border)
                else Modifier.border(1.dp, Border, RoundedCornerShape(14.dp)),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                filled && enabled -> AccentContrast
                filled -> TextMuted
                else -> TextPrimary
            },
        )
    }
}
