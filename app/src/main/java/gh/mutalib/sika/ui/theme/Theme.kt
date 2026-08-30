package gh.mutalib.sika.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Sika is dark only, and [isSystemInDarkTheme] is deliberately ignored.
 *
 * docs/ui-guidelines.md: #101422 is the ground this palette is built on, and inverting
 * it would need a second set of decisions Mutalib has not made. A half-built light theme
 * looks worse than an honest dark-only one, so the app looks the same either way.
 */
private val SikaColors = darkColorScheme(
    primary = Accent,
    onPrimary = AccentContrast,
    secondary = Accent,
    onSecondary = AccentContrast,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger,
    onError = AccentContrast,
)

@Composable
fun SikaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SikaColors,
        typography = SikaTypography,
        content = content,
    )
}
