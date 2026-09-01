package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon
import java.time.Instant
import java.time.format.DateTimeFormatter

private val LEARNED = DateTimeFormatter.ofPattern("d MMM")

/**
 * Every shop Sika taught itself, and the way to make it stop.
 *
 * ⚠ **Deleting a rule does not unlabel anything, and the screen says so.** A rule is a
 * standing instruction for messages that have not arrived yet; the labels it already applied
 * are facts about transactions that already happened. Undoing both would mean one tap silently
 * emptying dozens of categories — exactly the kind of quiet destruction the whole app is built
 * to avoid.
 */
@Composable
fun LearnedRulesScreen(
    rules: List<RuleEntity>,
    animated: Boolean,
    onBack: () -> Unit,
    onForget: (RuleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                SubScreenHeader("Learned rules", onBack)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (rules.isEmpty()) {
                        "Nothing yet. Sika writes one of these the first time you tick " +
                            "\"always this\" on a shop."
                    } else {
                        "Sika wrote these itself, the first time you labelled each shop."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(14.dp))
            }

            if (rules.isEmpty()) return@LazyColumn

            item {
                SettingsCard {
                    rules.forEachIndexed { i, rule ->
                        if (i > 0) RowDivider()
                        RuleRow(rule) { onForget(rule) }
                    }
                }
            }

            item {
                Footnote(
                    "Deleting a rule stops the labelling from here on. Transactions it already " +
                        "labelled keep their labels.",
                )
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleEntity, onForget: () -> Unit) {
    val colour = categoryColor(rule.label)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(colour, categoryIcon(rule.label))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rule.counterparty,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle(rule, colour),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleButton(
            icon = R.drawable.ic_trash,
            description = "Stop labelling ${rule.counterparty}",
            tint = TextMuted,
            onClick = onForget,
        )
    }
}

/** The category is coloured inside the line, so the row's colour and its words agree. */
@Composable
private fun subtitle(rule: RuleEntity, colour: androidx.compose.ui.graphics.Color): AnnotatedString {
    val since = LEARNED.format(Instant.ofEpochMilli(rule.createdAt).atZone(ACCRA))
    return buildAnnotatedString {
        append("always ")
        withStyle(SpanStyle(color = colour)) { append(rule.label) }
        append(" · since $since")
    }
}
