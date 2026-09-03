package gh.mutalib.sika.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
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
 * ⚠ **Every row gets the minus — the bin is not an alternative to it. Corrected 2026-09-03.**
 * The first build chose *one* button per row: a bin if the category was unused, the minus if it
 * was not. On Mutalib's phone only Food had ever been used, so the screen showed a single minus
 * and six bins, and he read it exactly right — *"the minus side should be for everything"*.
 * The two operations are not two grades of the same action and must not compete for the same
 * slot:
 *   * **put away** — reversible, offered on every row, changes nothing but what the picker
 *     offers. This is the everyday one, so it is the one that is always there.
 *   * **delete** — permanent, and now reached only by long-pressing a row that is *already*
 *     put away. Two deliberate steps stand between a category and being gone, and the second
 *     one happens on a list you had to go and put things into first.
 */
@Composable
fun CategoriesScreen(
    state: SettingsState,
    animated: Boolean,
    onBack: () -> Unit,
    onHide: (CategoryRow, Boolean) -> Unit,
    onDelete: (List<CategoryRow>) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<CategoryRow?>(null) }

    // ⚠ Held as ids rather than rows. `state` is rebuilt from the database on every change, so
    // a stored `CategoryRow` is a snapshot that stops matching the list the moment anything
    // else edits a category. Ids survive that.
    val selected = remember { mutableStateSetOf<Long>() }
    var selecting by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val putAway = state.putAway
    val chosen = putAway.filter { it.category.id in selected }

    // Leaving selection mode when the last selectable row goes away — deleted, or brought
    // back — stops an empty "0 selected" bar hanging over a list with nothing in it.
    fun stopSelecting() {
        selecting = false
        selected.clear()
    }
    // ⚠ In an effect, not inline. Writing `selecting` straight from the composition body is
    // a write during composition, which Compose is entitled to re-run — and a state write in
    // a body that re-runs on that same state is how a recomposition loop starts.
    val deletable = putAway.count { it.canDelete }
    LaunchedEffect(deletable) { if (deletable == 0) stopSelecting() }

    BackHandler(enabled = selecting) { stopSelecting() }

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
                        )
                    }
                }
            }

            if (putAway.isNotEmpty()) {
                item {
                    if (selecting) {
                        SelectionBar(
                            count = chosen.size,
                            onDelete = { if (chosen.isNotEmpty()) confirmingDelete = true },
                            onCancel = { stopSelecting() },
                        )
                    } else {
                        SectionLabel("PUT AWAY · LONG PRESS TO DELETE")
                    }
                    SettingsCard {
                        putAway.forEachIndexed { i, row ->
                            if (i > 0) RowDivider()
                            CategoryListRow(
                                row = row,
                                putAway = true,
                                selecting = selecting,
                                isSelected = row.category.id in selected,
                                onHide = {},
                                onBringBack = { onHide(row, false) },
                                onToggle = {
                                    if (row.category.id in selected) selected -= row.category.id
                                    else selected += row.category.id
                                },
                                onLongPress = {
                                    if (row.canDelete) {
                                        selecting = true
                                        selected += row.category.id
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Footnote(
                    "Putting one away only stops it being offered. Old transactions keep their " +
                        "label and the report still counts the money.",
                    "To delete for good: put it away first, then long press it in the list below.",
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

    if (confirmingDelete) {
        DeleteDialog(
            rows = chosen,
            onConfirm = { onDelete(chosen); confirmingDelete = false; stopSelecting() },
            onDismiss = { confirmingDelete = false },
        )
    }

    if (adding) {
        AddCategoryDialog(
            onAdd = { onAdd(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}

/**
 * Replaces the PUT AWAY label while a selection is running.
 *
 * ⚠ **Takes the label's place rather than sitting above it**, so nothing below moves when
 * selection starts. A list that jumps under your finger the instant you long-press is how you
 * end up selecting the row you did not mean to.
 */
@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == 0) "Choose what to delete" else "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Cancel",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            "Delete",
            style = MaterialTheme.typography.titleMedium,
            // ⚠ Greyed at zero rather than hidden. A button that vanishes and reappears as you
            // tick rows reads as a glitch; one that is plainly not ready reads as an instruction.
            color = if (count == 0) TextMuted else Danger,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CategoryListRow(
    row: CategoryRow,
    putAway: Boolean,
    onHide: () -> Unit,
    onBringBack: () -> Unit,
    selecting: Boolean = false,
    isSelected: Boolean = false,
    onToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val name = row.category.name
    Row(
        // ⚠ Dimmed, not greyed out. A put-away category is still a real thing you can bring
        // back in one tap; drawing it in the disabled colour would say it is broken.
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (putAway && !isSelected) 0.55f else 1f }
            .then(
                if (putAway) {
                    Modifier.combinedClickable(
                        // ⚠ A plain tap does nothing outside selection mode. The row is not a
                        // link to anywhere, and a tap target that sometimes acts and sometimes
                        // does not is worse than one that never does.
                        enabled = selecting || row.canDelete,
                        onClick = { if (selecting && row.canDelete) onToggle() },
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier
                },
            ),
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
                    usageLine(row, putAway, selecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
        when {
            // Selection replaces the row's own button, so there is never a tick box next to a
            // button that does something else.
            selecting -> SelectionMark(selected = isSelected, selectable = row.canDelete)

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

/**
 * The tick box, or a lock where a row cannot be deleted.
 *
 * ⚠ **An undeletable row shows a lock rather than an empty box.** An empty box that refuses to
 * fill in reads as a broken tap; a lock says the row is not eligible, and the line underneath
 * the name says why.
 */
@Composable
private fun SelectionMark(selected: Boolean, selectable: Boolean) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (!selectable) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = "Cannot be deleted",
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier.background(Danger)
                        else Modifier.border(1.5.dp, Border, CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = "Selected",
                        tint = AccentContrast,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

private fun usageLine(row: CategoryRow, putAway: Boolean, selecting: Boolean = false): String = when {
    // During a selection the question is not "how much have I used this" but "can this go",
    // so the one row that cannot answers before being asked.
    selecting && !row.canDelete && row.category.isProtected -> "Always kept"
    selecting && !row.canDelete -> count(row.uses, "transaction") + " " +
        agree(row.uses, "uses", "use") + " it, so it stays"
    row.uses == 0 -> "Never used"
    putAway -> count(row.uses, "transaction") + " " +
        agree(row.uses, "keeps", "keep") + " this label"
    else -> count(row.uses, "transaction")
}

/**
 * ⚠ **Names what is going, and says the one thing that is not obvious**: this is the only
 * action on the screen that cannot be undone. Everything else here is a switch.
 */
@Composable
private fun DeleteDialog(rows: List<CategoryRow>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                "Delete " + count(rows.size, "category", "categories") + "?",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                rows.joinToString(", ") { it.category.name } + ". " +
                    agree(rows.size, "It goes", "They go") + " for good — this is the one " +
                    "thing here you cannot undo. You can add the " +
                    agree(rows.size, "name", "names") + " again later, but " +
                    agree(rows.size, "it starts", "they start") + " empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton(
                    "Delete",
                    filled = true,
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
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
    // ⚠ **Red only for what cannot be undone.** Putting a category away is confirmed with the
    // ordinary accent button because it is a switch; deleting is the one action on this screen
    // with no way back, so it is the only one that gets to look alarming. Colouring both red
    // would teach the red to mean nothing.
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) {
                    Modifier.background(
                        when {
                            !enabled -> Border
                            danger -> Danger
                            else -> Accent
                        },
                    )
                } else {
                    Modifier.border(1.dp, Border, RoundedCornerShape(14.dp))
                },
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
