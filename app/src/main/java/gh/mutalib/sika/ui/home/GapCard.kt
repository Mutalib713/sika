package gh.mutalib.sika.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.notify.GapAlert
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.SuggestionField
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn

/**
 * Money that moved with no message to explain it.
 *
 * ⚠ **It names a WINDOW, not a day.** The arithmetic fails on the message *after* the missing
 * one, so that date is when the hole was caught, not when it opened. Mutalib's question on
 * 2026-09-01 — how can Sika know about a message that never came — is what exposed the earlier
 * wording as wrong: it said "missing from 28 August", which would send him looking on a day
 * nothing happened.
 *
 * ⚠ **It does not say "nothing to fix".** An earlier draft did, and that was too absolute.
 * There are three causes and they do not share an answer: MTN never sent a message (nothing to
 * do), the message was deleted before Sika read it (nothing to do), or Sika misread one (a
 * parser fix, which repairs history on its own because Sacred Rule 6 keeps every raw body).
 *
 * ⚠ **Explaining a gap does not clear it.** The flag stays, because MTN still sent no message
 * and remembering the purchase does not make one exist. What the note buys is that the card
 * stops asking, and that in six months the amount has a name attached to it.
 */
/** The one category that explains nothing by itself, so it is the one that opens a box. */
private const val OTHER = "Other"

@Composable
fun GapCard(
    gap: GapDetail,
    onExplain: (String?) -> Unit,
    categories: List<String> = emptyList(),
    onFile: (String?) -> Unit = {},
    /**
     * Words he has used to explain a gap before.
     *
     * ⚠ **His own past answers, never a list Sika invented.** Mutalib asked for a suggested
     * button here (2026-09-03), and the honest source for one is what he has already written:
     * "barber" typed once should be a tap the second time. Making up plausible reasons — "a
     * friend", "food" — would be the app guessing at his spending, which is the one thing it
     * exists not to do.
     */
    pastNotes: List<String> = emptyList(),
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Warn.copy(alpha = 0.09f))
            .border(1.dp, Warn.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = Warn,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                gap.amount.asCedis() + " is unaccounted for",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            GapAlert.window(gap.sinceMillis, gap.untilMillis, ACCRA) +
                " your balance dropped " + gap.amount.asCedis() +
                " more than your messages explain. This usually means MTN never sent one.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        // ⚠ **The editor is shared with the history sheet, deliberately.** Once the card
        // leaves Home the only way back to this answer is through the transaction list, and
        // two hand-written copies of the same chips would drift the first time one is touched.
        GapAnswer(
            categories = categories,
            category = gap.category,
            note = gap.note,
            pastNotes = pastNotes,
            onSave = { category, note -> onFile(category); onExplain(note) },
        )
    }
}

/**
 * Choosing what a gap was, in the same shape as labelling a transaction.
 *
 * ⚠ **Mutalib, 2026-09-03: *"it should just be like the transaction one … where u have the food
 * and the other ones and also the other which brings the text box"*.** The card used to ask its
 * own question first — a button reading "I know what this was", then a box, and only then the
 * categories. That made explaining a gap a different act from labelling a payment, with a
 * different vocabulary, for the same job.
 *
 * ⚠ **Nothing is written until Save**, for every category and not only "Other". His words:
 * *"i cant see the save for the others but the other with the text box has save"*. Tapping a
 * chip used to file the money on the spot, so six of the seven categories committed with no
 * confirming press while "Other" waited for one — one card teaching two rules about when a tap
 * counts. `TransactionSheet` has always had a single Save, and this matches it.
 *
 * ⚠ **The consequence, stated rather than buried: explaining now also counts.** Before this a
 * note could be written with no category, so the amount stayed out of every total. Choosing a
 * category *is* the explanation now, and a chosen category reaches its total.
 */
@Composable
fun GapAnswer(
    categories: List<String>,
    category: String?,
    note: String?,
    pastNotes: List<String>,
    onSave: (category: String?, note: String?) -> Unit,
) {
    if (categories.isEmpty()) return

    var selected by remember(category) { mutableStateOf(category) }
    var text by remember(note) { mutableStateOf(note.orEmpty()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // ⚠ A note belongs to "Other" and nowhere else, the rule `TransactionSheet` already
    // follows. Leaving "barber" attached to a gap filed under Food would leave the card
    // stating two different things about the same money.
    val pending = if (selected == OTHER) text.trim().takeIf { it.isNotEmpty() } else null
    val changed = selected != category || pending != note

    fun save() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        onSave(selected, pending)
    }

    Spacer(Modifier.height(14.dp))
    Text("WHAT WAS IT FOR?", style = MaterialTheme.typography.labelSmall, color = TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        categories.forEach { name ->
            val picked = selected == name
            // Tapping the chosen one un-chooses it, so nothing here is a one-way door — the
            // least an app owes you before it counts a figure it cannot prove.
            GapChip(name, picked) { selected = if (picked) null else name }
        }
    }

    // The box, for the one chip that explains nothing by itself.
    if (selected == OTHER) {
        Spacer(Modifier.height(12.dp))
        SuggestionField(
            value = text,
            onValueChange = { text = it },
            prompt = "What was this money for?",
            // ⚠ Capped at three. The bulb is a hint, not a history screen.
            examples = pastNotes.take(3),
            placeholder = "Barber, or a friend I paid cash",
            // Amber throughout; an accent-teal box here would look like a control borrowed
            // from another screen.
            accent = Warn,
            onDone = { save() },
        )
    }

    // ⚠ **One Save, under everything, for every category.** It appears the moment the answer
    // differs from what is stored and goes again once saved, so the card always says whether
    // there is anything outstanding.
    if (changed) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GapButton("Save", filled = true) { save() }
            GapButton("Cancel", filled = false) {
                selected = category
                text = note.orEmpty()
            }
        }
    }
}

/** A chip in the warning colour, so it reads as part of the gap rather than of the ledger. */
@Composable
private fun GapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Warn.copy(alpha = 0.9f) else Warn.copy(alpha = 0.10f))
            .border(
                1.dp,
                if (selected) Warn else Warn.copy(alpha = 0.35f),
                RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            // ⚠ Never white on the warning colour — docs/ui-guidelines.md. The surface is the
            // readable pairing here, the same rule the accent buttons follow.
            color = if (selected) Surface else TextPrimary,
        )
    }
}

/**
 * The card's two actions.
 *
 * ⚠ **Save is filled solid, not tinted. Mutalib, 2026-09-03: *"the save doesnt look like its a
 * button u will click"*.** It was `Warn` at 20% behind `Warn` text — a wash barely darker than
 * the card it sits on, which reads as a label rather than a control. The rest of the app fills
 * its confirming button and outlines its dismissing one, and this now does the same.
 *
 * ⚠ **`Surface` on `Warn`, never white** — docs/ui-guidelines.md. White on the warning amber is
 * 1.9:1 and unreadable; the surface colour is the pairing the selected chip already uses.
 */
@Composable
private fun GapButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (filled) Modifier.background(Warn)
                else Modifier.border(1.dp, Warn.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) Surface else Warn,
        )
    }
}
