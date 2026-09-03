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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.notify.GapAlert
import gh.mutalib.sika.parser.asCedis
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
@Composable
fun GapCard(
    gap: GapDetail,
    onExplain: (String?) -> Unit,
    categories: List<String> = emptyList(),
    onFile: (String?) -> Unit = {},
) {
    var typing by remember(gap.rowId) { mutableStateOf(false) }
    var text by remember(gap.rowId, gap.note) { mutableStateOf(gap.note.orEmpty()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        onExplain(text.trim().takeIf { it.isNotEmpty() })
        typing = false
    }

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

        when {
            // Already answered. Shown back rather than hidden, so the amount has a name on it
            // months later, and tappable so a wrong guess can be corrected.
            gap.note != null && !typing -> {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { typing = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            gap.note,
                            style = MaterialTheme.typography.titleMedium,
                            color = Warn,
                        )
                        Spacer(Modifier.height(2.dp))
                        // ⚠ **Mutalib's own wording, 2026-09-03.** The line used to read
                        // "From memory. No message, so it stays out of your totals." He
                        // rejected "from memory" — it describes where the words came from,
                        // when the thing worth saying is what happened: MTN sent nothing, and
                        // the figure was worked out from the balance rather than typed.
                        //
                        // ⚠ The second sentence changes with the facts. Once the money is
                        // filed under a category it is IN the totals, and a line still saying
                        // it is out would be the app lying about its own arithmetic.
                        Text(
                            if (gap.category != null) {
                                "MTN sent no message. The amount is from your balance, " +
                                    "counted under ${gap.category}."
                            } else {
                                "MTN sent no message. The amount is from your balance."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_pencil),
                        contentDescription = "Change what this was",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            typing -> {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Barber, or a friend I paid cash",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Warn,
                        unfocusedBorderColor = Warn.copy(alpha = 0.4f),
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface,
                        cursorColor = Warn,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GapButton("Save", filled = true) { save() }
                    GapButton("Cancel", filled = false) {
                        text = gap.note.orEmpty()
                        typing = false
                    }
                }
            }

            else -> {
                Spacer(Modifier.height(12.dp))
                GapButton("I know what this was", filled = false) { typing = true }
            }
        }

        // ⚠ **Only offered once the money has a name.** Filing an amount under Food before
        // saying what it was is guessing with extra steps, and the note is the cheap part —
        // it costs nothing to be wrong about. This is the expensive part: it moves money into
        // a total that no message backs, so it waits until there is a reason.
        if (gap.note != null && !typing && categories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (gap.category != null) "Counted under" else "Count it under",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                categories.forEach { name ->
                    val picked = gap.category == name
                    GapChip(name, picked) {
                        // ⚠ Tapping the chosen one un-chooses it. The same tap that put the
                        // money into a total takes it back out, so nothing here is a one-way
                        // door — which is the least an app owes you before it counts a figure
                        // it cannot prove.
                        onFile(if (picked) null else name)
                    }
                }
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

/** Outlined in the warning colour, never filled with it — a warning is not an invitation. */
@Composable
private fun GapButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) Modifier.background(Warn.copy(alpha = 0.2f))
                else Modifier.border(1.dp, Warn.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = Warn,
        )
    }
}
