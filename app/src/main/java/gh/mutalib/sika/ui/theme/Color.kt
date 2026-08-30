package gh.mutalib.sika.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The pinned palette. Mutalib chose these on 2026-08-30 from a reference image he
 * supplied; the four anchors are his exact hexes, the rest were grown from the accent
 * with `palette.py --seed "#A8DCE7" --dark`.
 *
 * **Sacred Rule 9: this is canon. It does not get "improved" later.**
 *
 * Named by the job each colour does, not by the colour it is — `SurfaceRaised` survives
 * a palette change, `LightBlue2` does not. Full contrast measurements and the reasoning
 * are in docs/ui-guidelines.md.
 */

/** App background. His. */
val Bg = Color(0xFF101422)

/** Cards, list rows, panels. His. */
val Surface = Color(0xFF272B3B)

/** Sheets and dialogs, one step above Surface. Derived. */
val SurfaceRaised = Color(0xFF323749)

/** Amounts, headings, body. His. Contrast on Bg is 18.34:1. */
val TextPrimary = Color(0xFFFFFFFF)

/** Dates, counterparty, captions. Derived. 7.11:1 on Bg, so it passes body text. */
val TextMuted = Color(0xFF9AA1B5)

/** Primary action, money coming in, selected state. His. */
val Accent = Color(0xFFA8DCE7)

/**
 * Text placed ON [Accent].
 *
 * ⚠ **Never use [TextPrimary] on [Accent].** White on this aqua measures 1.49:1, which
 * is invisible — far below the 4.5:1 floor. It is the single easiest way to wreck this
 * palette, so the correct colour has its own name to make the mistake harder.
 */
val AccentContrast = Color(0xFF101422)

/**
 * Hairlines between rows. Derived.
 *
 * ⚠ **Decorative only.** At 1.57:1 on [Bg] this cannot carry information. Any border
 * that *means* something — a focused field, a selected chip, a flagged row — uses
 * [TextMuted], [Accent] or [Warn] instead.
 */
val Border = Color(0xFF333849)

/** Reconciliation gaps, 80% of a budget. Derived. 9.13:1 on Surface. */
val Warn = Color(0xFFFFC857)

/** Over budget, destructive actions. Derived. 5.15:1 on Surface. */
val Danger = Color(0xFFF2777A)
