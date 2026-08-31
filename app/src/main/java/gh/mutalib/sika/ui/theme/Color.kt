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

/**
 * Dates, counterparty, captions, and every right-aligned secondary value.
 *
 * ⚠ **Raised from `#9AA1B5` on 2026-08-31, and the reason is structural.** Contrast against
 * the flat field was a comfortable 7.11:1 — but the aura's blue glow sits on the **right
 * side** of the screen, and right-aligned muted text lands on it. `Nothing yet` measured
 * **3.94:1** there while `TODAY`, the same colour at the same height on the left, measured
 * 7.11:1.
 *
 * Special-casing that one string would have left every future right-aligned caption exposed
 * to the same trap. This value clears 4.5:1 across the whole field, glow included.
 */
val TextMuted = Color(0xFFB4BCCB)

/**
 * Labels sitting **on glass**, where [TextMuted] is not bright enough.
 *
 * ⚠ **Measured on the device three times, and it needed all three fixes.** On glass,
 * contrast depends on *where the text lands*, because the aura drifts underneath.
 *
 * | fix | BALANCE label |
 * |---|---|
 * | first build | 3.27:1 |
 * | glass top-highlight tightened from a 35% wash to a 5.5% rim | 4.27:1 |
 * | this colour raised from `#D3D9E6` | see below |
 *
 * **Re-measure on the phone if the aura, the glass fill or the highlight changes.** The
 * HTML sketch is not a substitute — it read 4.71:1 for a label the real device rendered
 * at 3.27:1, because the aura is brighter at phone scale.
 */
val TextOnGlass = Color(0xFFEDF1F7)

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
