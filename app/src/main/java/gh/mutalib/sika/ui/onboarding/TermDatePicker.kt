package gh.mutalib.sika.ui.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material's own date picker, for the semester dates.
 *
 * ⚠ **The picker works in UTC millis and Sika works in Africa/Accra dates**, and the
 * conversion is written out in both directions rather than left to luck. Accra is UTC+0, so a
 * mistake here would be invisible on the one phone this app runs on and a day out everywhere
 * else — the worst kind of bug to leave implicit.
 *
 * A hand-built picker was the alternative and would have been worse: date entry is a solved
 * problem with real accessibility behaviour attached, so the house style reaches into this one
 * through colours instead of replacing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermDatePicker(
    title: String,
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = SurfaceRaised),
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // Back out through UTC, the same way it went in.
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text("Set", style = MaterialTheme.typography.titleMedium, color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, color = TextMuted)
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceRaised,
                selectedDayContainerColor = Accent,
                selectedDayContentColor = AccentContrast,
                todayDateBorderColor = Accent,
            ),
        )
    }
}
