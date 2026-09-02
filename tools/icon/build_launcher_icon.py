"""
Writes the launcher icon into the app: three vector drawables and the adaptive-icon wrapper.

⚠ **The geometry is not in this file.** It lives in `icon_p2.py`, which also feeds the proof
grid and the fit measurement. That separation is the whole point: the first version of this
icon had its shapes written out here by hand and *also* sketched separately in SVG, and the
two drawings quietly disagreed — the sketch used a blur filter for its shadows, the drawable
used offset solid shapes, so measuring the sketch measured something that never shipped. One
source now, three outputs, no way to drift.

⚠ **Android vector drawables are not SVG.** No `<filter>`, so every shadow is a real offset
shape at low alpha. Gradients need `<aapt:attr>`, not `<defs>` — a `<defs>` gradient is
silently ignored and the shape renders black. `icon_p2.to_vector` handles both.

⚠ **minSdk is 31, so there are no legacy PNG densities.** Every device that can install Sika
supports adaptive icons, which is why `mipmap-anydpi` is the only folder and there is no
`ic_launcher.png` anywhere in the project.

What the art is, and why it changed on 2026-09-02: Mutalib picked the full object cluster
knowing it overran the safe circle, then saw what his Pixel did with it — the coins cut off
the right edge, the rest merged into a pale smudge. This is that drawing repaired against both
faults: fitted to r=31.4 against a keyline of 33, and darkened so it does not depend on
paper-on-pale to be visible. Run `fit_icon.py` for the numbers, `p2_proof.py` to look at it.
"""
import os

import icon_p2

ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!--
  The adaptive icon. The launcher supplies the mask, which is why there is no shape here:
  a Pixel crops this to a circle, Samsung to a squircle, other skins to a rounded square or a
  teardrop. One file, every shape — and nothing to choose, because the choice is not ours.

  monochrome is Android 13+ themed icons. Without it the launcher falls back to shrinking the
  colour icon into a grey blob, which looks broken beside apps that provide one.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
"""

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                    "app", "src", "main", "res")


def write(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("  ", path)


def main():
    os.makedirs(f"{ROOT}/mipmap-anydpi", exist_ok=True)
    print("wrote:")
    write(f"{ROOT}/drawable/ic_launcher_background.xml",
          icon_p2.to_vector(icon_p2.background()))
    write(f"{ROOT}/drawable/ic_launcher_foreground.xml",
          icon_p2.to_vector(icon_p2.foreground()))
    write(f"{ROOT}/drawable/ic_launcher_monochrome.xml",
          icon_p2.to_vector(icon_p2.monochrome()))
    write(f"{ROOT}/mipmap-anydpi/ic_launcher.xml", ADAPTIVE)
    write(f"{ROOT}/mipmap-anydpi/ic_launcher_round.xml", ADAPTIVE)


if __name__ == "__main__":
    main()
