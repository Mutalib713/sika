package gh.mutalib.sika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/**
 * A text box with a light bulb in its right-hand corner. Tap the bulb and a small note drops
 * under the box showing the sort of thing that goes in it. Nothing in the note is pressable —
 * it explains, it does not fill anything in.
 *
 * ⚠ **Examples, never a picker — his instruction, after two wrong attempts.** Chips filled the
 * box in; so did the first version of this popup. Both were pickers wearing different clothes,
 * and both were wrong: *"it shouldnt be clickable to select"*.
 *
 * ⚠ **This replaces two different chip rows that were solving the same problem badly.** The
 * gap note had a scrolling row of *"Used before"* chips above the box; the semester editor had
 * a single *"Suggested"* chip. Mutalib asked three times for a bulb inside the field and got
 * chips each time — his words on 2026-09-03: *"still u are not getting me the suggested
 * button… it should be at the right corner of the text space… and it look like a light bulb.
 * that most apps use"*. He was describing a real convention and the chips were not it.
 *
 * ⚠ **The chips also broke the type, which is the second thing he spotted** — *"used before and
 * the suggested are different fonts"*. He was right, and it was not his eyes: both headings used
 * `labelSmall`, which in `Type.kt` is `LabelStyle` — **11sp, weight 600, +0.14em tracking**, a
 * style built for the ALL-CAPS micro-labels (BALANCE, IN, OUT). Set in sentence case next to a
 * `bodySmall` chip at **12sp, weight 400, no tracking**, it differs in size, weight *and*
 * letter-spacing at once, which is enough to read as a different typeface even though both are
 * IBM Plex Sans. Folding both into one component means there is nothing left to drift.
 *
 * ⚠ **A hand-built [Popup], not `DropdownMenu`.** Material 3's menu takes its background from
 * `surfaceContainer`, which `Theme.kt` never maps to a Sika colour — it would have arrived in
 * Material's default lilac-grey. Everything visible here is drawn from Sika's own tokens.
 *
 * ⚠ **The bulb is not shown at all when there is nothing to suggest.** A lit button that opens
 * an empty list is worse than no button.
 */
@Composable
fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    /** What the bulb shows. Empty means no bulb. */
    examples: List<String>,
    /**
     * The question the popup opens with, above "For example".
     *
     * ⚠ **Mutalib, 2026-09-03: *"it should be like which semester and year are u in then the
     * for example comes"*.** A bare list of examples answers a question the reader has not been
     * asked yet — they have to work backwards from "Year 1, first semester" to what the box
     * wants. Asking first and illustrating second is the order a person actually reads in.
     */
    prompt: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    /** Overrides the accent for a field that lives on a warning-coloured card. */
    accent: Color = Accent,
    onDone: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    // The popup is placed by hand directly under the box, so it needs the box's real height.
    var fieldHeight by remember { mutableIntStateOf(0) }

    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = if (label == null) null else ({ Text(label, color = TextMuted) }),
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            },
            trailingIcon = {
                if (examples.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { open = !open },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_bulb),
                            contentDescription = "Show suggestions",
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(13.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = accent,
                unfocusedBorderColor = accent.copy(alpha = 0.4f),
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                cursorColor = accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { fieldHeight = it.height },
        )

        if (open && examples.isNotEmpty()) {
            Popup(
                // ⚠ TopEnd plus a downward offset of the field's own height, rather than
                // BottomEnd. `alignment` places the popup *inside* the anchor's bounds, so
                // BottomEnd would sit the note on top of the text it is explaining.
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, fieldHeight),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ExamplePopup(prompt = prompt, examples = examples, accent = accent)
            }
        }
    }
}

/**
 * What the bulb opens: the question the box is asking, a couple of examples, and nothing to
 * press.
 *
 * ⚠ **Nothing in here is tappable, and that is the whole specification.** Mutalib, 2026-09-03:
 * *"when u click on it it just give examples… it shouldnt be clickable to select"*. The version
 * before this one filled the box in when you tapped a line, which quietly made it a picker —
 * and a picker is the thing he has now ruled out twice, first as chips and then as this.
 *
 * The cost is real and worth stating: he retypes a note he has written before instead of tapping
 * it. What he gets back is a field that only ever contains what he typed, and a bulb that means
 * *"here is the sort of thing that goes here"* — the same thing it means in every other app.
 */
@Composable
private fun ExamplePopup(prompt: String, examples: List<String>, accent: Color) {
    Column(
        Modifier
            .widthIn(min = 210.dp, max = 300.dp)
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceRaised)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // The question first, in the reading colour, because it is the point of the popup.
        Text(prompt, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(9.dp))
        Text(
            "For example",
            // ⚠ bodySmall, not labelSmall. This is a sentence, and labelSmall is the tracked
            // all-caps style — the exact mismatch that started this rewrite.
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        examples.forEach { example ->
            Spacer(Modifier.height(6.dp))
            Text(example, style = MaterialTheme.typography.bodyMedium, color = accent)
        }
    }
}
