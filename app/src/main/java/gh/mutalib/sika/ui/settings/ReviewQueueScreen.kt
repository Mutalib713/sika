package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.sms.SmsIngest
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn
import java.time.Instant
import java.time.format.DateTimeFormatter

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mma")

/**
 * Screen 4 — the messages Sika could not read.
 *
 * ⚠ **This screen exists because Settings already had a row pointing at it.** "Needs a look"
 * shipped on 2026-09-01 with a chevron and no destination, an hour after a comment was written
 * in this same codebase saying a control that goes nowhere is worse than no control. Built
 * rather than the chevron removed, because the queue is Sacred Rule 7 made visible: a message
 * the parser refuses is *held*, never guessed at and never dropped.
 *
 * ⚠ **These rows are in no total anywhere**, and the screen says so first. Every money field
 * on them is zero and `parsedOk` is false, so they are excluded from the reports, the charts
 * and the reconciliation walk. Someone who did not know that would reasonably assume their
 * figures already include this money.
 *
 * ⚠ **Nothing here can be edited into a transaction, and that is deliberate.** Typing an
 * amount from a message Sika misread would put a number in the ledger that no MoMo balance
 * backs — the one thing reconciliation cannot check. The repair route is the parser: the raw
 * body is kept verbatim (Sacred Rule 6), so teaching the reader this shape brings the message
 * in on the next sweep, with nothing retyped.
 */
@Composable
fun ReviewQueueScreen(
    queue: List<TransactionEntity>,
    animated: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                SubScreenHeader("Needs a look", onBack)
                Spacer(Modifier.height(12.dp))
            }

            if (queue.isEmpty()) {
                item { NothingToReview() }
                return@LazyColumn
            }

            item {
                Text(
                    "Sika could not read " + count(queue.size) + ", so they are in no total " +
                        "anywhere. Every figure in the app is the sum of what it could read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(14.dp))
            }

            items(queue, key = { it.id }) { row -> QueuedMessage(row) }

            item {
                Footnote(
                    "They are kept exactly as they arrived. If the reader is ever taught to " +
                        "understand them, these come in on their own — nothing has to be " +
                        "typed back.",
                )
            }
        }
    }
}

private fun count(n: Int): String = if (n == 1) "this message" else "these $n messages"

@Composable
private fun QueuedMessage(row: TransactionEntity) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, Warn.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "NOT COUNTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warn,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                WHEN.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            // The message itself, whole. A truncated one cannot be compared against the
            // parser, which is the only thing this screen is really for.
            row.rawBody,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = Warn,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                // Worked out now by re-parsing, never recalled from when it failed. Fix the
                // parser and a stale row starts reporting that it is readable.
                SmsIngest.reasonFor(row),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

/** The good state, and the one that should be normal. */
@Composable
private fun NothingToReview() {
    Column(
        Modifier.fillMaxWidth().padding(top = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Nothing needs reviewing",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sika read every MoMo message on this phone. Every figure in the app is the sum " +
                "of all of them, with nothing left out.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}
