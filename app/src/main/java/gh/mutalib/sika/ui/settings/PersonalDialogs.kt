package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.onboarding.ChoiceCard
import gh.mutalib.sika.ui.onboarding.TermDatePicker
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * The way back into the two answers first run collected.
 *
 * ⚠ **Without these, a typo in a name is permanent** and a term entered in the wrong year can
 * never be corrected. An onboarding question with no editable home afterwards is a trap, and
 * Mutalib asked for the way back explicitly.
 */
@Composable
fun NameDialog(initial: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.orEmpty()) }
    DialogShell(onDismiss) {
        Text("Your name", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Used for the greeting on Home, and nowhere else.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(14.dp))
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
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSave(name.trim().takeIf { it.isNotEmpty() }) },
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
        Spacer(Modifier.height(8.dp))
        Text(
            "Leave it empty and the greeting just says the time of day.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        Buttons(
            onCancel = onDismiss,
            confirm = "Save",
        ) { onSave(name.trim().takeIf { it.isNotEmpty() }) }
    }
}

@Composable
fun SemesterDialog(
    initialStudent: Boolean,
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onSave: (Boolean, LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var student by remember { mutableStateOf(initialStudent) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var picking by remember { mutableStateOf<Int?>(null) }

    DialogShell(onDismiss) {
        Text("Semester", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "A student gets a semester view in the report, alongside week and month.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(14.dp))
        ChoiceCard("Yes, I am a student", "Show the semester view", student) { student = true }
        Spacer(Modifier.height(9.dp))
        ChoiceCard("No", "Week, month and all time is plenty", !student) { student = false }

        if (student) {
            Spacer(Modifier.height(14.dp))
            DateField("STARTS", start) { picking = 0 }
            Spacer(Modifier.height(9.dp))
            DateField("ENDS", end) { picking = 1 }
            Spacer(Modifier.height(9.dp))
            Text(
                // The fallback is what happens today for everyone, so saying so is honest
                // rather than apologetic.
                "Leave these unset and Sika falls back to guessing from your oldest " +
                    "transaction, which is almost never the start of a term.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Buttons(onCancel = onDismiss, confirm = "Save") {
            onSave(student, if (student) start else null, if (student) end else null)
        }
    }

    picking?.let { which ->
        TermDatePicker(
            title = if (which == 0) "When does it start?" else "When does it end?",
            initial = if (which == 0) start else end,
            onPick = {
                if (which == 0) start = it else end = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(
                1.dp,
                if (date == null) Border else Accent.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(56.dp),
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

@Composable
private fun DialogShell(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border, RoundedCornerShape(22.dp))
                .padding(19.dp),
        ) { content() }
    }
}

@Composable
private fun Buttons(onCancel: () -> Unit, confirm: String, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancel", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent)
                .clickable(onClick = onConfirm)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(confirm, style = MaterialTheme.typography.titleMedium, color = AccentContrast)
        }
    }
}
