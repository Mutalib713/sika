package gh.mutalib.sika.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.ThemeMode
import gh.mutalib.sika.ui.theme.systemPrefersDark

/**
 * The three appearances, named.
 *
 * ⚠ **This is the fix for a control that could no-op.** The corner toggle cycled
 * `system → light → dark`, and on a phone already in dark mode the step to `system` changed
 * the setting while changing nothing on screen — so it took two presses to do one thing, which
 * Mutalib hit and reported on 2026-09-01. A three-way choice belongs in a list where each
 * option has a name you can point at. The corner toggle stays, but it now only flips light and
 * dark; coming back to "default phone theme" happens here.
 *
 * ⚠ **"Default phone theme", never "Follow my phone"** — his wording, and the string lives on
 * [ThemeMode.label] so there is exactly one copy of it.
 */
@Composable
fun AppearanceDialog(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val phoneIsDark = systemPrefersDark()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border, RoundedCornerShape(22.dp))
                .padding(horizontal = 19.dp, vertical = 18.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))

            ThemeMode.entries.forEachIndexed { i, mode ->
                if (i > 0) RowDivider()
                Option(
                    icon = when (mode) {
                        ThemeMode.SYSTEM -> R.drawable.ic_theme_auto
                        ThemeMode.LIGHT -> R.drawable.ic_theme_light
                        ThemeMode.DARK -> R.drawable.ic_theme_dark
                    },
                    title = mode.label,
                    // Only the phone-follows option needs explaining, and what it needs to say
                    // is what it is doing right now — otherwise picking it is a guess.
                    subtitle = if (mode == ThemeMode.SYSTEM) {
                        if (phoneIsDark) "Dark right now" else "Light right now"
                    } else {
                        null
                    },
                    selected = mode == current,
                    onClick = { onPick(mode) },
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Spacer(Modifier.height(12.dp))
            Text(
                "If your phone never says which it wants, Sika shows the light one.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun Option(
    icon: Int,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 54.dp)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (selected) Accent else TextMuted,
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
        // A ring that fills, rather than a tick that appears: the empty ring is what tells you
        // the other options are choices too, before you have touched any of them.
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) Accent else Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(Accent))
        }
    }
}
