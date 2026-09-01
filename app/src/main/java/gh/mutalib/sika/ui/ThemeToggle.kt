package gh.mutalib.sika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.LocalSetThemeMode
import gh.mutalib.sika.ui.theme.LocalSikaColors
import gh.mutalib.sika.ui.theme.LocalThemeMode
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.ThemeMode

/**
 * The theme control, in the top corner — Mutalib's placement, 2026-08-31.
 *
 * **Three states on one button, cycling system → light → dark → system.** A three-way
 * choice usually wants a menu, but this one earns a single tap: the icon always shows the
 * mode you are *in*, the cycle is short enough to reach any state in two taps, and it costs
 * no screen furniture on a screen whose job is showing money.
 *
 * ⚠ **The icon shows the setting, not the appearance.** On `SYSTEM` it shows the sun-moon
 * glyph even when the phone is currently dark, because "following the phone" is a different
 * fact from "currently dark" — and a control that lies about its own state is worse than no
 * control.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val mode = LocalThemeMode.current
    val onChange = LocalSetThemeMode.current
    val showingDark = LocalSikaColors.current.isDark
    // ⚠ The icon shows what a TAP WILL DO, not what the setting currently is. On a dark
    // screen it is a sun, because tapping brings the light one. An icon naming the present
    // state leaves you working out the consequence yourself, on a control whose whole job is
    // to be obvious.
    val icon = if (showingDark) R.drawable.ic_theme_light else R.drawable.ic_theme_dark
    val description = if (showingDark) "Switch to the light theme" else "Switch to the dark theme"
    Box(
        modifier
            .size(44.dp) // ≥44dp touch target, docs/screens.md
            .clip(RoundedCornerShape(22.dp))
            .clickable { onChange(mode.next(showingDark)) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
