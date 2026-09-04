package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.ui.onboarding.TermDatePicker
import gh.mutalib.sika.ui.theme.Danger
import gh.mutalib.sika.ui.theme.SurfaceRaised
import androidx.compose.foundation.border
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
import gh.mutalib.sika.ui.SuggestionField
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border

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
 * keep their dates and simply stop being grouped under that name.
 *
 * ⚠ **It still asks first, since 2026-09-04.** "Nothing is at risk" was true of the money and
 * false of the work — Mutalib lost all three of his semesters to a bin that sat one unguarded
 * tap away, and a name plus two dates cannot be recovered from anywhere else in the app.
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
    // ⚠ **Deleting asks first. Added 2026-09-04, after Mutalib lost all three of his.**
    // The old reasoning was that a semester is a lens rather than a container, so deleting
    // one risks no transaction and needs no confirmation. That was right about the money and
    // wrong about the work: the name and the two dates are things he sat and typed, they
    // cannot be recovered from anywhere else in the app, and the bin sat one unguarded tap
    // away on a row whose whole body is also tappable.
    var confirming by remember { mutableStateOf<TermEntity?>(null) }

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
                    onDelete = { confirming = term },
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

    confirming?.let { term ->
        RemoveTermDialog(
            term = term,
            onConfirm = { onDelete(term); confirming = null },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * ⚠ **Says what survives, not just "are you sure?".** The thing worth knowing here is the
 * thing people fear and the app does not do: no transaction is touched. Naming that is what
 * makes the dialog worth reading rather than a speed bump to tap through.
 */
@Composable
private fun RemoveTermDialog(term: TermEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                "Remove ${term.name}?",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No transaction is touched — they keep their dates and stop being grouped " +
                    "under this name. You would have to type the name and both dates again.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogButton(
                    "Remove",
                    filled = true,
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
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
    /**
     * What the bulb offers, best guess first. The first entry is also the box's placeholder
     * and the name an untouched field falls back to on save, so this must never be empty.
     *
     * ⚠ **More than one, because the series is knowable and he may be adding out of order.**
     * A student setting up three semesters at once should not have to retype
     * "Year 2, first semester" by hand just because Sika guessed the next one in sequence.
     */
    suggestions: List<String>,
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
    val suggestion = suggestions.first()
    var picking by remember { mutableStateOf<String?>(null) }
    // ⚠ **A name is required. Mutalib, 2026-09-04: saving with the box empty used to take
    // the suggestion, and he was right that it should not.** The suggestion is grey
    // placeholder text, which reads as a hint rather than as a value — so "save without
    // typing" felt like saving nothing and silently produced "First semester, Year 1".
    // A name someone did not choose is worse than being made to choose one, because the
    // whole point of naming semesters was that he calls them what he calls them.
    val named = name.isNotBlank()
    val ready = named && start != null && lastDay != null && lastDay!!.isAfter(start)

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
            // ⚠ **The bulb, not a chip and not the placeholder — third attempt, and his call.**
            // The placeholder failed because Material only draws one while the field is focused
            // AND empty, so the suggestion was invisible until he was already typing over it.
            // The chip that replaced it worked but was not what he kept asking for, and it put
            // a `labelSmall` heading beside `bodySmall` chip text — the font mismatch he
            // spotted. `SuggestionField` carries the whole pattern now.
            SuggestionField(
                value = name,
                onValueChange = { name = it },
                prompt = "Which semester and year are you in?",
                examples = suggestions.take(2),
                placeholder = suggestion,
                label = "Name",
            )

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
                                    // No `ifBlank` fallback any more: `ready` guarantees a
                                    // name, so a default here could only ever mask a bug.
                                    name = name.trim(),
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
            // ⚠ **The border is the fix, not the icons.** Mutalib, 2026-09-03: *"the start and
            // end it doesnt even show that it is a date picker"*. These rows sat directly under
            // an outlined text box wearing nothing but a fill, so they read as panels printing
            // a value rather than controls that open something. Matching the field's outline is
            // what puts them in the same family; the calendar says which kind of control, and
            // the chevron says it goes somewhere.
            .border(1.dp, Border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = if (value == null) Accent else TextMuted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.weight(1f))
        Text(
            value?.let { TERM_DATE.format(it) } ?: "Choose",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) Accent else TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(13.dp),
        )
    }
}
