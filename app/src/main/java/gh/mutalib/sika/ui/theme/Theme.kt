package gh.mutalib.sika.ui.theme

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which theme to show.
 *
 * Mutalib's decision, 2026-08-31: **follow the phone by default, but let him override it**,
 * with the control in the top corner rather than buried in Settings.
 */
enum class ThemeMode {
    /** Follow the phone's own light/dark setting. The default. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** The next mode when the corner control is tapped: system → light → dark → system. */
    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}

/**
 * Remembers the choice across launches.
 *
 * `SharedPreferences` rather than DataStore: one enum, read once at startup, and DataStore
 * would add a dependency and a coroutine for a single string. Room already covers everything
 * that needs real durability.
 */
object ThemePreference {
    private const val FILE = "sika_prefs"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY, mode.name) }
    }

    /** Held in one place so the corner control and the theme never disagree. */
    fun state(context: Context): MutableState<ThemeMode> = mutableStateOf(load(context))
}

/**
 * The current mode and the way to change it, so the corner control can live on any screen
 * without every screen's signature growing two parameters for it.
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalSetThemeMode = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

/**
 * ⚠ **Was dark-only until 2026-08-31**, and the old comment here said inverting the ground
 * "would need a second set of decisions Mutalib has not made". He has now made them: he
 * asked for light mode, and the light palette was derived and measured rather than guessed.
 * See [SikaLight].
 */
@Composable
fun SikaTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    onModeChange: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) SikaDark else SikaLight

    // Material's own scheme is kept in step, because Compose components that Sika does not
    // style by hand — the sheet scrim, the pull-to-refresh spinner, ripples — read from it.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.accentContrast,
            secondary = colors.accent, onSecondary = colors.accentContrast,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textMuted,
            outline = colors.border, error = colors.danger, onError = colors.accentContrast,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.accentContrast,
            secondary = colors.accent, onSecondary = colors.accentContrast,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textMuted,
            outline = colors.border, error = colors.danger, onError = colors.accentContrast,
        )
    }

    CompositionLocalProvider(
        LocalSikaColors provides colors,
        LocalThemeMode provides mode,
        LocalSetThemeMode provides onModeChange,
    ) {
        MaterialTheme(colorScheme = material, typography = SikaTypography, content = content)
    }
}
