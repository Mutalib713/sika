"""
Proves the launcher icon still fits its safe circle, and still sits in the middle of it.

⚠ **Run this after any change to `icon_p2.py`.** Both faults it checks for are silent — the
build succeeds, lint passes, every unit test stays green, and the damage only appears on a
phone. That is exactly how a clipped icon shipped on 2026-09-01: the art reached r=52 on a
canvas whose safe circle is r=33, and nothing in the toolchain had an opinion about it.

    python tools/icon/check_icon.py

**What the numbers mean, in ordinary words.** Android does not let an app choose the shape of
its own icon. You supply a 108x108 square, and the *launcher* cuts it to whatever shape that
phone uses — a Pixel cuts a circle, Samsung a rounded-square-ish "squircle", others a rounded
square or a teardrop. So anything you draw near the edge is at the mercy of a shape you cannot
see. The circle that survives every one of those cuts has radius 33 on that canvas. Draw
outside it and some phone, somewhere, slices your artwork.

The second check is not about clipping at all: art can fit perfectly and still sit off to one
side, which reads as a mistake rather than a design. So the drawn bounding box has to be
centred on (54, 54) too.

⚠ **This needs a headless Chromium to rasterise with, and skips if it cannot find one.** It is
a drawing check, not a build gate — `./gradlew check` neither runs nor needs it.
"""
import math
import os
import subprocess
import sys
import tempfile

import icon_p2

SCALE = 10          # 1080px for the 108dp canvas, so one pixel is 0.1dp
SAFE = 33.0         # the 66dp keyline circle every launcher mask respects
CENTRE_SLACK = 1.5  # dp of off-centre that is not worth arguing about

CHROMES = [
    r"C:\Users\USER\AppData\Local\ms-playwright\chromium_headless_shell-1234"
    r"\chrome-headless-shell-win64\chrome-headless-shell.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    "/usr/bin/chromium",
    "/usr/bin/google-chrome",
]


def find_chrome():
    for c in CHROMES:
        if os.path.exists(c):
            return c
    return None


def render(png, size, chrome):
    """
    The foreground only, on a transparent ground, so every drawn pixel is measurable.

    ⚠ Shadows are drawn ink and count. A launcher crops a shadow like anything else, and a
    shadow sliced by the mask edge reads as a smudge — so the alpha threshold below is
    deliberately low rather than "mostly opaque".
    """
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" '
           f'width="{size}" height="{size}">'
           f'{icon_p2.to_svg(icon_p2.foreground(), "c")}</svg>')
    src = os.path.join(tempfile.gettempdir(), "sika_icon_check.html")
    with open(src, "w", encoding="utf-8") as fh:
        fh.write('<!doctype html><meta charset="utf-8">'
                 '<style>html,body{margin:0;padding:0;background:transparent}</style>' + svg)
    subprocess.run(
        [chrome, "--headless", "--disable-gpu", "--hide-scrollbars",
         "--default-background-color=00000000",
         f"--screenshot={png}", f"--window-size={size},{size}",
         "file:///" + src.replace("\\", "/")],
        check=True, capture_output=True)


def measure(png, size):
    from PIL import Image
    px = Image.open(png).convert("RGBA").getchannel("A").load()
    c = size / 2.0
    worst, lo_x, hi_x, lo_y, hi_y = 0.0, size, 0, size, 0
    for y in range(size):
        for x in range(size):
            if px[x, y] > 8:
                worst = max(worst, math.hypot(x - c, y - c))
                lo_x, hi_x = min(lo_x, x), max(hi_x, x)
                lo_y, hi_y = min(lo_y, y), max(hi_y, y)
    return worst / SCALE, ((lo_x + hi_x) / 2 / SCALE, (lo_y + hi_y) / 2 / SCALE)


def main():
    chrome = find_chrome()
    if not chrome:
        print("icon check: SKIPPED — no headless Chromium found.")
        print("Tried:\n  " + "\n  ".join(CHROMES))
        return 0

    size = 108 * SCALE
    png = os.path.join(tempfile.gettempdir(), "sika_icon_check.png")
    render(png, size, chrome)
    worst, mid = measure(png, size)
    off = math.hypot(mid[0] - 54, mid[1] - 54)

    print(f"  furthest drawn point   r = {worst:.1f}   (safe circle {SAFE:.0f})")
    print(f"  centre of the drawing  {mid[0]:.1f}, {mid[1]:.1f}   ({off:.1f} off 54,54)")

    bad = []
    if worst > SAFE:
        bad.append(f"art reaches r={worst:.1f}, {worst - SAFE:.1f}dp outside the safe circle — "
                   f"some launcher will cut it")
    if off > CENTRE_SLACK:
        bad.append(f"art sits {off:.1f}dp off centre")
    if bad:
        print("\nicon check: FAIL")
        for b in bad:
            print("  -", b)
        return 1
    print("\nicon check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
