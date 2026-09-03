package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.ui.onboarding.TermDatePicker
import gh.mutalib.sika.ui.theme.Danger
import gh.mutalib.sika.ui.theme.SurfaceRaised
import androidx.compose.foundation.clickable

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.theme.Accent

import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import java.time.format.DateTimeFormatter

private val TERM_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The semesters, named.
 *
 * ⚠ **This replaced a single pair of dates**, on Mutalib's instruction 2026-09-02: *"the
 * semester we can label them lets say first sem year 1 or something"*. Naming implies more
 * than one, and more than one is what turns the report's ‹ › from "the same number of days
 * again" into "the semester before this one" — a question that finally has an exact answer.
 *
 * ⚠ **A semester is a lens, not a container.** Deleting one touches no transaction: the rows
 * keep their dates and simply stop being grouped under that name. That is why there is no
 * confirmation dialog here, unlike putting a category away — nothing is at risk.
 */
@Composable
fun SemestersScreen(
    terms: List<TermEntity>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TermEntity) -> Unit,
    onDelete: (TermEntity) -> Unit,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val now = today(ACCRA)
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                SubScreenHeader("Semesters", onBack)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (terms.isEmpty()) {
                        "Name the stretches you think in, and the report can group your " +
                            "money by them."
                    } else {
                        "The report steps through these with ‹ ›."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(14.dp))
            }

            items(terms, key = { it.id }) { term ->
                TermRow(
                    term = term,
                    // ⚠ "Now" rather than a tick: a semester that has ended is not wrong, and
                    // marking only the live one answers the question someone actually has.
                    current = term.contains(now),
                    onEdit = { onEdit(term) },
                    onDelete = { onDelete(term) },
                )
                Spacer(Modifier.height(9.dp))
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(Surface)
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = Accent)
                    Spacer(Modifier.width(11.dp))
                    Text(
                        "Add a semester",
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TermRow(
    term: TermEntity,
    current: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Surface)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    term.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                if (current) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Now",
                        style = MaterialTheme.typography.labelSmall
                            .copy(fontWeight = FontWeight.W600),
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                // ⚠ `endExclusive` is the day AFTER the term, so the last day shown is one
                // back. Printing the stored date would put every semester one day long than
                // it is, which nobody would notice and everybody would inherit.
                TERM_DATE.format(term.start) + " – " +
                    TERM_DATE.format(term.endExclusive.minusDays(1)),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_trash),
                contentDescription = "Remove ${term.name}",
                tint = TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * Editing one semester: its name, and the two dates.
 *
 * ⚠ **The name is free text with a suggestion already in it, never a picker.** Mutalib asked
 * for "first sem year 1 *or something*", and the "or something" is the load-bearing half —
 * KNUST's numbering is his, and another school's trimesters would make a fixed list nonsense.
 *
 * ⚠ **Both date buttons show the INCLUSIVE last day**, while the row stores an exclusive end.
 * Asking someone to enter "the day after my semester ends" would be correct and unusable.
 */
@Composable
fun TermEditor(
    term: TermEntity,
    /** What the name box shows when it is empty — "Year 1, first semester". */
    suggestion: String,
    onSave: (TermEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    // ⚠ **Nothing is filled in for a new one — Mutalib, 2026-09-03: "dont name and give date
    // automatically just suggest".** A pre-filled name and a pair of invented dates look like
    // answers, and an answer you did not give is one you stop reading. So a new semester opens
    // blank, with the name as a PLACEHOLDER and the dates as "Choose".
    val isNew = term.id == 0L
    var name by remember(term.id) { mutableStateOf(if (isNew) "" else term.name) }
    var start by remember(term.id) {
        mutableStateOf<java.time.LocalDate?>(if (isNew) null else term.start)
    }
    var lastDay by remember(term.id) {
        mutableStateOf<java.time.LocalDate?>(if (isNew) null else term.endExclusive.minusDays(1))
    }
    var picking by remember { mutableStateOf<String?>(null) }
    val ready = start != null && lastDay != null && lastDay!!.isAfter(start)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceRaised)
                .padding(19.dp),
        ) {
            Text("Semester", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name", color = TextMuted) },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextMuted,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // ⚠ **A visible chip, not the text field's placeholder.** The placeholder was the
            // obvious choice and it does not work: Material only draws one while the field is
            // focused and empty, so the suggestion Mutalib asked to see was invisible until he
            // tapped into the box — by which point he is already typing his own. A chip is
            // always on screen, says what it will do, and fills the field in one tap.
            if (name.isBlank()) {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(Accent.copy(alpha = 0.14f))
                            .clickable { name = suggestion }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = Accent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DateButton("Starts", start) { picking = "start" }
            Spacer(Modifier.height(8.dp))
            DateButton("Ends", lastDay) { picking = "end" }

            // ⚠ Said plainly rather than silently corrected. Swapping the dates for him would
            // be guessing which one he mistyped.
            if (start != null && lastDay != null && !lastDay!!.isAfter(start)) {
                Spacer(Modifier.height(9.dp))
                Text(
                    "The end has to come after the start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton(
                    "Save",
                    filled = true,
                    // ⚠ Disabled rather than silently doing nothing. A Save that looks
                    // available and ignores the tap is the worst of both.
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val s0 = start
                        val e0 = lastDay
                        if (s0 != null && e0 != null && e0.isAfter(s0)) {
                            onSave(
                                term.copy(
                                    // An untouched name takes the suggestion. He saw it in the
                                    // box, so accepting it by not typing is a real choice.
                                    name = name.trim().ifBlank { suggestion },
                                    startDay = s0.toEpochDay(),
                                    // Back to exclusive on the way in.
                                    endExclusiveDay = e0.plusDays(1).toEpochDay(),
                                ),
                            )
                            onDismiss()
                        }
                    },
                )
            }
        }
    }

    when (picking) {
        // ⚠ The picker opens on a sensible date when none is set — that is the "suggest" half
        // — without writing anything back until he actually taps a day.
        "start" -> TermDatePicker(
            "When does it start?",
            start ?: java.time.LocalDate.now(),
            { start = it; picking = null },
        ) { picking = null }
        "end" -> TermDatePicker(
            "When does it end?",
            lastDay ?: (start ?: java.time.LocalDate.now()).plusMonths(4),
            { lastDay = it; picking = null },
        ) { picking = null }
    }
}

@Composable
private fun DateButton(label: String, value: java.time.LocalDate?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.weight(1f))
        Text(
            value?.let { TERM_DATE.format(it) } ?: "Choose",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) Accent else TextPrimary,
        )
    }
}
