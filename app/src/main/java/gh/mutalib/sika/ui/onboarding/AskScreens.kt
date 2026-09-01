package gh.mutalib.sika.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * Why Sika needs the messages, said before the system dialog rather than after it.
 *
 * ⚠ **This comes after the tour, not before it.** The system's permission dialog is a yes/no
 * with no argument attached; by this point there is a reason to say yes and a specific claim —
 * only MoMo, nothing leaves the phone — that the manifest can be checked against.
 */
@Composable
fun AskPermission(onAllow: () -> Unit, onRestore: () -> Unit) {
    OnboardingFrame(
        primary = "Allow SMS access", onPrimary = onAllow,
        quiet = "Restoring after a reinstall?", onQuiet = onRestore,
    ) {
        Headline("Sika needs to read your messages")
        Spacer(Modifier.height(20.dp))
        PointRow(
            R.drawable.ic_message,
            "Only messages from MoMo",
            "Every other text on this phone is ignored.",
        )
        PointRow(
            R.drawable.ic_shield,
            "Nothing leaves your phone",
            "Sika has no internet permission at all.",
        )
        PointRow(
            R.drawable.ic_clock,
            "Your whole history, straight away",
            "Months of it, in about a second.",
        )
    }
}

/**
 * ⚠ **Replaces `OWNER = "Osman"`**, a compile-time constant that was in the binary in two
 * places. Skipping is a real answer, not a failure: the greeting then says the time of day
 * with no name after it.
 */
@Composable
fun AskName(initial: String?, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial.orEmpty()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun finish(value: String?) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        onDone(value)
    }

    OnboardingFrame(
        primary = "Continue", onPrimary = { finish(name.trim().takeIf { it.isNotEmpty() }) },
        quiet = "Skip", onQuiet = { finish(null) },
    ) {
        Headline("What should Sika call you?")
        Spacer(Modifier.height(10.dp))
        Sub("Only for the greeting on your Home screen, and it stays on this phone like everything else.")
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = {
                Text("Your name", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            },
            textStyle = MaterialTheme.typography.titleMedium,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                // A name is a name, so the keyboard should start in caps rather than making
                // someone reach for shift on the first character they type in this app.
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { finish(name.trim().takeIf { it.isNotEmpty() }) },
            ),
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
        Spacer(Modifier.height(14.dp))
        Fine("Skip it and the greeting just says the time of day.")
    }
}

/**
 * Are you a student, and if so when does your term run.
 *
 * ⚠ **One screen, with the dates revealed underneath a Yes** — Mutalib's shape, 2026-09-01,
 * and better than the two screens it replaces: the question and its consequence stay visible
 * together, so the dates are plainly *because of* the answer above them.
 *
 * ⚠ **A No removes the semester view entirely**, rather than leaving a segment in the report's
 * switcher that means nothing to whoever is reading it.
 *
 * ⚠ **"I don't know the dates yet" is a real answer.** Someone installing this in the holidays
 * has no term to name, and the fallback — guessing from the oldest transaction — is what
 * happens today anyway.
 */
@Composable
fun AskStudent(
    initialStudent: Boolean,
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDone: (student: Boolean, start: LocalDate?, end: LocalDate?) -> Unit,
) {
    var student by remember { mutableStateOf<Boolean?>(if (initialStudent) true else null) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var picking by remember { mutableStateOf<DateField?>(null) }

    OnboardingFrame(
        primary = "Continue",
        onPrimary = {
            val isStudent = student == true
            onDone(isStudent, if (isStudent) start else null, if (isStudent) end else null)
        },
        quiet = if (student == true) "I don't know the dates yet" else null,
        onQuiet = if (student == true) ({ onDone(true, null, null) }) else null,
    ) {
        Headline("Are you a student?", centred = false)
        Spacer(Modifier.height(10.dp))
        Sub(
            "If you are, Sika adds a semester view to the report, alongside week and month.",
            centred = false,
        )
        Spacer(Modifier.height(22.dp))
        ChoiceCard(
            "Yes", "Show me the semester view",
            chosen = student == true,
        ) { student = true }
        Spacer(Modifier.height(10.dp))
        ChoiceCard(
            "No", "Week, month and all time is plenty",
            chosen = student == false,
        ) { student = false }

        Reveal(student == true) {
            Column {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Accent.copy(alpha = 0.28f)))
                Spacer(Modifier.height(14.dp))
                Text(
                    "WHEN DOES YOURS RUN?",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                )
                Spacer(Modifier.height(8.dp))
                DateRow("STARTS", start) { picking = DateField.START }
                Spacer(Modifier.height(9.dp))
                DateRow("ENDS", end) { picking = DateField.END }
                Spacer(Modifier.height(13.dp))
                Fine(
                    "Sika guesses this from your oldest transaction today, which is almost " +
                        "never right. When the end date passes it will say so rather than " +
                        "quietly reporting on a term that is over.",
                    centred = false,
                )
            }
        }
    }

    picking?.let { field ->
        TermDatePicker(
            title = if (field == DateField.START) "When does it start?" else "When does it end?",
            initial = if (field == DateField.START) start else end,
            onPick = {
                if (field == DateField.START) start = it else end = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

enum class DateField { START, END }

@Composable
private fun DateRow(label: String, date: LocalDate?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, if (date == null) Border else Accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(58.dp),
        )
        Text(
            date?.format(LONG_DATE) ?: "Pick a date",
            style = MaterialTheme.typography.titleMedium,
            color = if (date == null) TextMuted else TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The three questions Sika may ask, with a reason on each.
 *
 * ⚠ **Asked in words before the system dialog.** Android's own prompt is a bare yes/no; a
 * refusal there is permanent on modern Android, so the one chance to make the case comes
 * first.
 */
@Composable
fun AskNotifications(onAllow: () -> Unit, onSkip: () -> Unit) {
    OnboardingFrame(
        primary = "Allow notifications", onPrimary = onAllow,
        quiet = "Not now", onQuiet = onSkip,
    ) {
        Headline("Can Sika ask you things?")
        Spacer(Modifier.height(10.dp))
        Sub("Three questions, and never anything else. Each one can be switched off on its own in Settings afterwards.")
        Spacer(Modifier.height(22.dp))
        PointRow(
            R.drawable.ic_notification,
            "What a cash-out was for",
            "MoMo never says where cash went. This is the only chance to catch it.",
        )
        PointRow(
            R.drawable.ic_clock,
            "A nudge at the end of the day",
            "Only when something still has no category. Silent otherwise.",
        )
        PointRow(
            R.drawable.ic_warning,
            "When a balance does not tally",
            "Money that moved with no message to explain it.",
        )
    }
}
