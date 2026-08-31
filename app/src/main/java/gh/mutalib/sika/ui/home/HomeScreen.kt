package gh.mutalib.sika.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.asCedis
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.glass
import gh.mutalib.sika.ui.specularSweep
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.CellMoneyStyle
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.RowMoneyStyle
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextOnGlass
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * How many transactions Home shows before handing off to the full list.
 *
 * Mutalib's call, 2026-08-31: Home showed all 144 and became a wall. Home answers *where
 * am I right now*; the full history is a separate place you go on purpose.
 */
private const val RECENT_COUNT = 5

/**
 * Screen 1 of docs/screens.md, built to the direction in docs/ui-guidelines.md.
 *
 * Layout is skeleton **B**, chosen from four: a glass capsule carrying the balance and the
 * in/out pair, then a short list on the field — no cards, because proximity and hairlines
 * do the work boxes usually get asked for.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    animated: Boolean,
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
) {
    val entrance = rememberEntrance(animated)
    val recent = state.days.flatMap { it.rows }.take(RECENT_COUNT)

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding clears the floating dock — content scrolls behind it, which is
            // the point of a floating navigation layer.
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 130.dp),
        ) {
            item { MonthHeader(state) }
            item { Spacer(Modifier.height(18.dp)) }
            item { BalanceCapsule(state, animated, entrance) }

            // The status strips. Both sit flush with the capsule's left edge and share one
            // vertical rhythm, so they read as a set rather than two stray lines.
            // Only the gap gets a strip. The category count lives in the header subtitle,
            // and saying it twice on one screen made both instances easier to ignore.
            if (state.gaps > 0) item { StatusStrip(R.drawable.ic_warning, gapText(state), Warn) }

            if (state.isEmpty) {
                item { EmptyMonth() }
            } else {
                item { SectionHeading("Recent") }
                items(recent, key = { it.id }) { row ->
                    TransactionRow(row, onClick = { onTransactionClick(row) })
                    if (row !== recent.last()) HorizontalDivider(color = Border, thickness = 1.dp)
                }
                item { SeeAllButton(state.total, onSeeAll) }
            }
        }
    }
}

/**
 * Whose phone this is. Hardcoded on purpose — Sika is a single-user app sideloaded to one
 * device (PROFILE.md § 2), so asking for a name would be a setup step that buys nothing.
 */
private const val OWNER = "Osman"

/**
 * The greeting, and the month.
 *
 * ⚠ **The month chip is here instead of a notification bell, hamburger or brightness
 * toggle.** Mutalib asked for one of those and was unsure which; none of the three has a
 * job in Sika. Its notifications are system notifications, so a bell would open nothing.
 * Settings is already a tab, so a hamburger is a second route to one place. The app is
 * dark-only by design, so a brightness toggle would toggle nothing.
 *
 * Changing month is the one thing genuinely worth reaching for from here, so that is what
 * the corner does.
 */
@Composable
private fun MonthHeader(state: HomeState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "${greeting()}, $OWNER",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(15.dp)).glass(corner = 15.dp)
                .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                MONTH_SHORT.format(state.month),
                style = MaterialTheme.typography.titleMedium,
                color = TextOnGlass,
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = "Change month",
                tint = TextOnGlass,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Africa/Accra, so the greeting matches the clock on the wall rather than a server's. */
private fun greeting(): String = when (java.time.LocalTime.now(ACCRA).hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** One honest line about the state of the ledger, not a slogan. */
private fun subtitle(state: HomeState): String = when {
    state.isEmpty -> "Nothing recorded this month yet"
    state.gaps > 0 -> "${state.total} transactions · ${state.gaps} to check"
    state.unlabelled == state.total -> "${state.total} transactions · none categorised yet"
    state.unlabelled > 0 -> "${state.total} transactions · ${state.unlabelled} need a category"
    else -> "${state.total} transactions, all accounted for"
}

/**
 * Four labelled figures on one glass surface.
 *
 * ⚠ **Restructured 2026-08-31.** It was one enormous balance with a smaller in/out pair,
 * and Mutalib said two things about it: he could not tell which figure was which, and the
 * balance was not the most relevant number anyway. Both fair — a 44sp number with an 11sp
 * label reads as *the* number, and everything beside it reads as a footnote.
 *
 * So every figure now gets the same label treatment and a comparable size, separated by
 * hairlines rather than by shouting. This is **not** the four-card dashboard grid he
 * rejected: it is one glass surface with four cells, so the navigation-layer rule holds and
 * nothing is boxed.
 *
 * Order is deliberate — **Out first.** What left is the question the app exists to answer.
 */
@Composable
private fun BalanceCapsule(state: HomeState, animated: Boolean, entrance: EntranceClock) {
    val balance = ((state.balance ?: 0L) * entrance.count).toLong()

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - entrance.capsuleDrop) * -320f
                alpha = entrance.capsuleFade
            }
            .glass(corner = 30.dp)
            .specularSweep(enabled = animated)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // ⚠ The period is named on the label, not implied by the screen.
        //
        // "OUT" alone does not say out of *what* — this month, this semester, all time?
        // Mutalib caught it 2026-08-31, and it matters because the app exists to answer
        // "what did I spend this month / this semester". A figure whose period you have to
        // infer is a figure you cannot act on.
        //
        // [HomeState.periodLabel] carries the answer, so when semester ranges arrive in
        // v1.1 these labels follow without touching this composable.
        Rising(entrance, 0) {
            Row(Modifier.fillMaxWidth()) {
                Cell("SPENT IN ${state.periodLabel}", "−" + state.moneyOut.plain(), TextPrimary, Modifier.weight(1f))
                Cell("RECEIVED IN ${state.periodLabel}", "+" + state.moneyIn.plain(), Accent, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Rising(entrance, 1) {
            HorizontalDivider(color = TextOnGlass.copy(alpha = 0.16f), thickness = 1.dp)
        }
        Spacer(Modifier.height(16.dp))
        Rising(entrance, 2) {
            Row(Modifier.fillMaxWidth()) {
                Cell(
                    "SPENT TODAY",
                    if (state.spentToday == 0L) "—" else "−" + state.spentToday.plain(),
                    TextPrimary,
                    Modifier.weight(1f),
                )
                Cell(
                    "BALANCE NOW",
                    if (state.balance == null) "—" else balance.plain(),
                    TextPrimary,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** One labelled figure. Same treatment every time, which is what makes them comparable. */
@Composable
private fun Cell(label: String, value: String, colour: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = LabelStyle, color = TextOnGlass)
        Spacer(Modifier.height(5.dp))
        Text(value, style = CellMoneyStyle, color = colour)
    }
}

/** `1663.20` — the currency lives in the capsule's context, not on every figure. */
private fun Long.plain(): String = asCedis().removePrefix("GHS ")

/** One line of capsule content, rising into place on its own beat. */
@Composable
private fun Rising(entrance: EntranceClock, index: Int, content: @Composable () -> Unit) {
    val p = entrance.content(index)
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - p) * 46f
            alpha = p
        },
    ) { content() }
}

/**
 * A status line under the capsule: a reconciliation gap, or a count of unlabelled rows.
 *
 * ⚠ 20 dp of air above it and flush with the capsule's left edge — measured fix,
 * 2026-08-31. It previously sat tight under the capsule and looked like a caption that had
 * slipped, rather than a separate statement.
 */
@Composable
private fun StatusStrip(icon: Int?, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = LabelStyle,
        color = TextMuted,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
    )
}

/** Hands off to the full history. Names what it does and how much there is of it. */
@Composable
private fun SeeAllButton(total: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .glass(corner = 16.dp)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "See all $total transactions",
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
        )
    }
}

@Composable
private fun TransactionRow(row: TransactionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).glass(corner = 13.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.counterparty.ifBlank { "Unreadable message" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                (row.label ?: "Add category") + " · " +
                    TIME.format(Instant.ofEpochMilli(row.occurredAt).atZone(ACCRA)),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val incoming = row.direction == Direction.IN
            Text(
                (if (incoming) "+" else "−") + row.amount.asCedis().removePrefix("GHS "),
                style = RowMoneyStyle,
                // Colour reinforces; the sign carries the meaning. docs/ui-guidelines.md.
                color = if (incoming) Accent else TextPrimary,
            )
            row.balanceAfter?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it.asCedis().removePrefix("GHS "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyMonth() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp).alpha(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing yet this month",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Transactions appear here as MoMo texts arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun gapText(state: HomeState): String {
    val gap = state.firstGap ?: return "${state.gaps} transactions don't add up"
    val day = DAY.format(Instant.ofEpochMilli(gap.occurredAt).atZone(ACCRA))
    return if (state.gaps == 1) {
        "One transaction on $day doesn't add up"
    } else {
        "${state.gaps} transactions don't add up, from $day"
    }
}


private val MONTH = DateTimeFormatter.ofPattern("MMMM")
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")
private val DAY = DateTimeFormatter.ofPattern("d MMMM")
private val TIME = DateTimeFormatter.ofPattern("h:mma")
