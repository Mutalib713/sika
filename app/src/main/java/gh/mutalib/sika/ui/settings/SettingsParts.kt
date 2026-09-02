package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.Danger
import gh.mutalib.sika.ui.theme.Surface
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * The pieces every Settings screen is built from.
 *
 * ⚠ **Grouped cards, chosen by Mutalib on 2026-09-01 from four organisations shown side by
 * side** (`design-scratch/settings.html`). He picked B — the shape Home and Report already
 * speak — over Android's own flat list, which he could see would look correct in any app and
 * therefore belonged to none.
 *
 * Rows are ≥56dp, above the 48dp floor in docs/screens.md, because a settings row is a target
 * you hit while holding the phone one-handed and reading the label at the same time.
 */

/** A section heading. Uppercase, muted, small — it labels, it does not compete. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** One card holding a run of rows. */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp),
        content = content,
    )
}

/** The hairline between two rows in a card. Never above the first or below the last. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
}

/**
 * One settings row: an icon, a name, an optional line under it, and something on the right.
 *
 * [trailing] is a slot rather than a set of flags because the three shapes a row's right-hand
 * side can take — a value with a chevron, a switch, a circular button — have nothing in common
 * except position.
 */
@Composable
fun SettingsRow(
    icon: Int,
    title: String,
    // ⚠ First optional parameter, and lint enforces it (`ModifierParameter`). The convention
    // is not fussiness: it is what lets every call site pass a modifier in the same position
    // without reading the signature. All 14 callers name their arguments, so moving it here
    // changed nothing at any of them.
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = tint ?: TextMuted,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

/** A value and a chevron — the shape that means "this opens something". */
@Composable
fun ValueAndChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Spacer(Modifier.width(7.dp))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.size(15.dp),
    )
}

/**
 * ⚠ **Every switch here is decorative until the thing behind it exists.** A control that
 * looks live and changes nothing is worse than no control, so each one is passed a real
 * `checked` and a real `onChange` by its caller, and the two that have no backing setting yet
 * are not drawn at all rather than drawn dead.
 */
@Composable
fun SettingsSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AccentContrast,
            checkedTrackColor = Accent,
            checkedBorderColor = Accent,
            uncheckedThumbColor = TextMuted,
            uncheckedTrackColor = Surface,
            uncheckedBorderColor = Border,
        ),
    )
}

/** The small circular buttons on the Categories screen: put away, bring back, delete. */
@Composable
fun CircleButton(
    icon: Int,
    description: String,
    tint: Color,
    borderColor: Color = Border,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** A coloured tile with a category's icon in it — the same one the transaction list uses. */
@Composable
fun CategoryTile(colour: Color, icon: Int) {
    Box(
        Modifier
            .size(31.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colour.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colour, modifier = Modifier.size(16.dp))
    }
}

/** The footnote under a card. Explains a consequence; never decorates. */
@Composable
fun Footnote(text: String, emphasis: String? = null) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        if (emphasis != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                emphasis,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = TextPrimary,
            )
        }
    }
}

/**
 * What just happened, said once and then gone.
 *
 * ⚠ **Every action on these screens is silent otherwise.** Putting a category away removes a
 * row from a list you may not be looking at; restoring a file changes nothing you can see
 * until you go back to Home. An action with no visible result reads as a broken button, which
 * is how someone ends up pressing it four times.
 *
 * Dismisses itself after a few seconds, and on a tap. Sits above the dock rather than over it.
 */
@Composable
fun SettingsToast(toast: Toast?, onDismiss: () -> Unit) {
    if (toast == null) return
    LaunchedEffect(toast) {
        delay(if (toast.bad) 6_000 else 3_500)
        onDismiss()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceRaised)
            .border(1.dp, if (toast.bad) Danger else Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(
            toast.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (toast.bad) Danger else TextPrimary,
        )
    }
}

/** The header every sub-screen shares: the double chevron back, a title, an optional action. */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_back_double),
                contentDescription = "Back to Settings",
                tint = TextPrimary,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}
