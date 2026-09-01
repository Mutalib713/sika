package gh.mutalib.sika.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette, in both themes.
 *
 * **The dark values are Mutalib's pinned palette and are canon (Sacred Rule 9).** He chose
 * the four anchors on 2026-08-30 from a reference image; the rest were grown from the accent
 * with `palette.py --seed "#A8DCE7" --dark`. Nothing here "improves" them.
 *
 * **The light values were derived on 2026-08-31, not invented.** Light mode arrived because
 * he said dark-only did not suit him. Every light value keeps the hue of its dark
 * counterpart and moves *lightness only*, as far as the contrast rule forces and no
 * further — computed in `design-scratch/derive_palettes.py` and measured, never eyeballed.
 *
 * ⚠ **The accent could not simply carry over.** `#A8DCE7` on white measures **1.49:1** —
 * invisible. The light accent `#4B7E88` is the same hue (217°) and the same chroma,
 * darkened until it clears 4.5:1.
 *
 * Named by the job each colour does, not by the colour it is — `surfaceRaised` survives a
 * palette change, `lightBlue2` does not. Full measurements in docs/ui-guidelines.md.
 */
data class SikaColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textMuted: Color,
    val textOnGlass: Color,
    val accent: Color,
    /** Text drawn **on** the accent. ⚠ Differs by theme — see [SikaLight]. */
    val accentContrast: Color,
    val border: Color,
    val warn: Color,
    val danger: Color,
    val isDark: Boolean,
)

/** Mutalib's pinned palette. Canon. */
val SikaDark = SikaColors(
    bg = Color(0xFF101422),
    surface = Color(0xFF272B3B),
    surfaceRaised = Color(0xFF323749),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFB4BCCB),
    textOnGlass = Color(0xFFEDF1F7),
    accent = Color(0xFFA8DCE7),
    // ⚠ Never white on this aqua: 1.49:1, effectively invisible.
    accentContrast = Color(0xFF101422),
    border = Color(0xFF333849),
    warn = Color(0xFFFFC857),
    danger = Color(0xFFF2777A),
    isDark = true,
)

/**
 * Derived from the pinned palette, hue preserved, measured against `#FFFFFF` (the strictest
 * light ground) so it also holds on the slightly grey page below.
 *
 * ⚠ **[accentContrast] inverts here, and this is the trap.** On the dark theme the rule is
 * *never white on the accent*. On the light theme the opposite holds: white on `#4B7E88`
 * measures **4.53:1** and passes, while the dark ink measures **3.92:1** and fails. The rule
 * is not "never white on accent" — it is "measure it", and the two themes disagree.
 */
val SikaLight = SikaColors(
    bg = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    text = Color(0xFF141821),          // 17.76:1 on white
    textMuted = Color(0xFF5A6373),     // 6.06:1
    textOnGlass = Color(0xFF141821),
    accent = Color(0xFF4B7E88),        // 4.53:1, hue 217° preserved
    accentContrast = Color(0xFFFFFFFF),
    border = Color(0xFFDDE2EA),
    warn = Color(0xFFBC8D17),          // 3.02:1 as a filled shape
    danger = Color(0xFFC44F55),        // 4.57:1
    isDark = false,
)

val LocalSikaColors = staticCompositionLocalOf { SikaDark }

/*
 * The token accessors.
 *
 * These keep the plain names every screen already uses — `Accent`, `Bg`, `TextMuted` — so
 * adding a whole second theme changed no call sites at all. They read the current palette
 * out of [LocalSikaColors] instead of being fixed constants.
 *
 * ⚠ They are `@Composable`, so they cannot be read from a plain function or a file-level
 * `val`. That is a feature: anywhere the compiler objects is a place a colour was about to
 * be frozen at one theme.
 */
val Bg: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.bg
val Surface: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.surface
val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.surfaceRaised
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.text
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.textMuted
val TextOnGlass: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.textOnGlass
val Accent: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.accent
val AccentContrast: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.accentContrast
val Border: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.border
val Warn: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.warn
val Danger: Color @Composable @ReadOnlyComposable get() = LocalSikaColors.current.danger
