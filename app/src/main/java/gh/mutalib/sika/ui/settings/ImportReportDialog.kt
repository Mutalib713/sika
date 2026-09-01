package gh.mutalib.sika.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.data.BackupIo
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/**
 * What an import actually did, when part of it could not be read.
 *
 * ⚠ **Leads with what worked, then names what did not.** A restore that skipped three rows out
 * of 144 is a success with a footnote, and opening on the failure would read as a broken
 * import. But the footnote has to be specific: line numbers can be found in the file, where
 * "3 rows could not be read" can only be worried about.
 *
 * ⚠ **Says explicitly that nothing was replaced.** Import only ever adds — see
 * `TransactionDao.restoreLabel` — and the moment worth saying so is the moment someone is
 * looking at a list of things that went wrong and wondering what else it touched.
 */
@Composable
fun ImportReportDialog(report: BackupIo.Import, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border, RoundedCornerShape(20.dp))
                .padding(17.dp),
        ) {
            Text(
                headline(report),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                skipped(report.problems.size) +
                    " Nothing already on this phone was changed or replaced.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Column(
                Modifier
                    .heightIn(max = 190.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
            ) {
                report.problems.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium, color = AccentContrast)
            }
        }
    }
}

private fun headline(r: BackupIo.Import): String {
    val parts = buildList {
        if (r.added > 0) add("${r.added} transactions")
        if (r.labels > 0) add("${r.labels} labels")
        if (r.notes > 0) add("${r.notes} notes")
        if (r.categories > 0) add("${r.categories} categories")
        if (r.rules > 0) add("${r.rules} rules")
    }
    return if (parts.isEmpty()) "Nothing new was restored."
    else "Restored " + parts.joinToString(", ") + "."
}

private fun skipped(n: Int): String =
    if (n == 1) "1 row could not be read and was left out."
    else "$n rows could not be read and were left out."
