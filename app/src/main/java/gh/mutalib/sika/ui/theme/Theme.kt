package gh.mutalib.sika.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Which theme to show.
 *
 * Mutalib's decision, 2026-08-31: **follow the phone by default, but let him override it**,
 * with the control in the top corner rather than buried in Settings.
 */
enum class ThemeMode {
    /** Whatever the phone itself is set to. The default. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /**
     * What Settings calls each one.
     *
     * ⚠ **"Default phone theme", not "Follow my phone"** — Mutalib's wording, 2026-09-01, and
     * the reason it lives here rather than in the screen is so there is one place to change it
     * and no chance of two screens naming the same setting differently.
     */
    val label: String
        get() = when (this) {
            SYSTEM -> "Default phone theme"
            LIGHT -> "Light"
            DARK -> "Dark"
        }

    /**
     * What the corner control switches to, given what is currently on screen.
     *
     * ⚠ **Takes the current appearance, not just the current mode, and that is the fix for a
     * real bug.** The old cycle was `SYSTEM → LIGHT → DARK → SYSTEM`, which looks tidy and
     * has a dead step in it: going `DARK → SYSTEM` on a phone that is itself in dark mode
     * changes the setting and changes nothing you can see. Mutalib hit exactly that on
     * 2026-09-01 — "when it's on dark mode I have to press it twice before it goes to light".
     * He was pressing once to reach SYSTEM, seeing no change, and pressing again.
     *
     * **A control that can no-op is broken**, however correct its state machine. This one is
     * defined by what it does to the screen: it always flips light and dark.
     *
     * The cost, stated: the toggle can no longer return to "follow my phone". That is a real
     * loss and it goes in Settings at PLAN task 15, where a three-way choice can be shown as
     * three labelled options rather than guessed at from an icon. SYSTEM remains the default
     * until the control is touched for the first time.
     */
    fun next(showingDark: Boolean): ThemeMode = if (showingDark) LIGHT else DARK
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
 *
 * ⚠ **The setter takes an origin, and it is not decoration.** Mutalib asked for Telegram's
 * theme switch, where the new theme spreads out of the control you touched rather than
 * replacing the screen all at once (2026-09-02). That effect only reads as *caused by the
 * button* if it starts at the button, so the position has to travel with the request — a
 * control that fires a ripple from the middle of the screen looks like a glitch, not an
 * answer. `null` means "no particular place": switch from the centre, or instantly.
 *
 * See [gh.mutalib.sika.ui.theme.ThemeRevealHost] for what receives it.
 */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalSetThemeMode = staticCompositionLocalOf<(ThemeMode, Offset?) -> Unit> { { _, _ -> } }

/**
 * True while the ripple is mid-flight.
 *
 * ⚠ **Read by MainActivity to hold the status-bar icons still.** Android draws the status bar
 * glyphs itself, above everything Sika draws, so flipping them the instant the mode changes
 * turns them dark while three-quarters of the screen is still light. They wait for the circle
 * to finish instead.
 */
val LocalThemeRevealing = staticCompositionLocalOf { false }

/**
 * What the phone itself is asking for, **and light when it is not asking for anything.**
 *
 * Mutalib's rule, 2026-09-01: *"if the user does not have the default phone mode the default
 * should be the light theme"*. Android's config has three states, not two —
 * `UI_MODE_NIGHT_YES`, `NO`, and `UNDEFINED` — and `UNDEFINED` is real: a stripped ROM, a
 * device with no dark setting, or a config that has not resolved yet.
 *
 * ⚠ **This is written out rather than left to `isSystemInDarkTheme()` even though that
 * function already returns false for `UNDEFINED`.** The behaviour was correct by accident;
 * an accident is not a decision, and the next person to touch this cannot tell the difference
 * unless it says so. Nothing about the running app changes.
 */
@Composable
@ReadOnlyComposable
fun systemPrefersDark(): Boolean {
    val night = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return night == Configuration.UI_MODE_NIGHT_YES
}

/**
 * ⚠ **Was dark-only until 2026-08-31**, and the old comment here said inverting the ground
 * "would need a second set of decisions Mutalib has not made". He has now made them: he
 * asked for light mode, and the light palette was derived and measured rather than guessed.
 * See [SikaLight].
 */
@Composable
fun SikaTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    onModeChange: (ThemeMode, Offset?) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemPrefersDark()
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
