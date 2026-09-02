"""
P2 — one description of the icon geometry, rendered two ways.

⚠ **Why this file exists at all.** The sketch and the shipped icon were two separate drawings
of "the same" art, and they were not the same: the sketch used an SVG blur filter for its
shadows, the Android drawable used offset solid shapes instead, because vector drawables have
no `<filter>`. So measuring the sketch measured something that never shipped. Every number
below is emitted into both the SVG grid and the `.xml` the launcher loads, from one list of
shapes, so the thing being measured and the thing being installed cannot drift apart.

⚠ **What P2 changes, and why it is not just "smaller".** The Pixel screenshot showed two
faults, and only one of them was clipping:

  1. the drawing reached r=52 on a canvas whose safe circle is r=33, so a third of it was cut;
  2. the objects that survived merged into one pale smudge, because paper (#F6FBFD) on the
     field (#C4E6EF) is barely a contrast — the only shapes that read at 48px were the dark
     phone and the dark sweep.

Shrinking three pale objects into a smaller circle fixes (1) and makes (2) worse. So P2 drops
to two objects, and the bubble takes over the dark that the phone used to carry.

Sizes on this canvas: 108 total, the middle 72 guaranteed, r=33 the keyline every launcher
mask respects, r≈36 what Mutalib's Pixel actually crops to.
"""

P = dict(field="#E8F6FA", field2="#C4E6EF", sweep="#101422", sweep2="#1D3A46",
         coin="#A8DCE7", coinDeep="#4B7E88", paper="#F6FBFD", ink="#101422")

SHADOW_DY = 2.4       # far enough to read as lift, near enough to stay inside the keyline
SHADOW_ALPHA = 0.16


# ------------------------------------------------------------------ path builders
def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} "
            f"a{r},{r} 0 0 1 -{r},{r} h-{w - 2 * r} a{r},{r} 0 0 1 -{r},-{r} "
            f"v-{h - 2 * r} a{r},{r} 0 0 1 {r},-{r} z")


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1 0 {2 * rx},0 a{rx},{ry} 0 1 0 -{2 * rx},0 z"


def bubble_path(x, y, w, h, r=9, tail=8):
    """A rounded rect with a tail dropping from the bottom-left, as a speech bubble."""
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} "
            f"a{r},{r} 0 0 1 -{r},{r} h-{w - 2 * r - tail - 1} l-{tail},{tail} v-{tail} h-1 "
            f"a{r},{r} 0 0 1 -{r},-{r} v-{h - 2 * r} a{r},{r} 0 0 1 {r},-{r} z")


# ------------------------------------------------------------------ the shapes
class Shape:
    """
    One drawn path. `fill` is a colour, or ('grad', x1, y1, x2, y2, c1, c2).

    `even_odd` switches the fill rule so that a subpath drawn inside another cuts a hole
    instead of filling it. Only the themed icon needs it — see [monochrome].
    """

    def __init__(self, d, fill, alpha=None, even_odd=False):
        self.d, self.fill, self.alpha, self.even_odd = d, fill, alpha, even_odd


# Geometry, tuned against fit_icon.py until the worst drawn pixel sat inside r=33 AND the
# drawn bounding box was centred on (54,54). Fitting alone is not enough: the first pass fit
# at r=32.5 but sat 3.4dp up and to the left, which reads as a mistake even though nothing
# was cut. Both numbers are checked on every run.
BUB = dict(x=31, y=27.6, w=42, h=29, r=9)
COIN = dict(cx=65, cy=62.6, rx=12.0, ry=8.4, step=4.0)


def foreground():
    """Two objects: a dark bubble, and a stack of coins overlapping its tail."""
    s = []
    bub = bubble_path(**BUB)

    # ⚠ The shadow is a real offset shape, not a blur — vector drawables have no filters.
    # It is drawn first so everything else sits on top of it.
    s.append(Shape(bubble_path(BUB["x"], BUB["y"] + SHADOW_DY, BUB["w"], BUB["h"], BUB["r"]),
                   P["ink"], SHADOW_ALPHA))
    s.append(Shape(bub, P["sweep"]))

    # The face inside the bubble: a coin and two bars, so it reads as "money, in a message"
    # rather than as a plain chat icon.
    cx, cy = BUB["x"] + 13, BUB["y"] + BUB["h"] / 2
    s.append(Shape(ellipse(cx, cy, 7.6, 7.6),
                   ("grad", cx - 7.6, cy - 7.6, cx + 7.6, cy + 7.6, P["coin"], P["coinDeep"])))
    s.append(Shape(rrect(BUB["x"] + 24, cy - 6.4, 14, 4.6, 2.3), "#FFFFFF", 0.93))
    s.append(Shape(rrect(BUB["x"] + 24, cy + 1.4, 9, 4.6, 2.3), "#FFFFFF", 0.55))

    # ⚠ Coins are drawn deep-first, light-face-on-top. The earlier version used a gradient
    # from the pale accent, which on a pale field is nearly no edge at all; here the body is
    # the deep teal and the pale accent only ever appears surrounded by it.
    c = COIN
    for i, dy in enumerate((c["step"] * 2, c["step"], 0)):
        y = c["cy"] + dy
        s.append(Shape(ellipse(c["cx"], y + SHADOW_DY * 0.6, c["rx"], c["ry"]),
                       P["ink"], SHADOW_ALPHA))
        s.append(Shape(ellipse(c["cx"], y, c["rx"], c["ry"]), P["coinDeep"]))
        s.append(Shape(ellipse(c["cx"], y - 2.2, c["rx"] * 0.96, c["ry"] * 0.94), P["coin"]))
    return s


def monochrome():
    """
    Android 13+ themed icons: one flat silhouette the system tints itself.

    ⚠ **Not the colour art with the colour removed, and the first attempt proved why.** A themed
    icon is a single tint, so every light-against-dark relationship inside the drawing vanishes
    at once. Rendered flat, the bubble and the coin stack — which merely *touch* in the colour
    version, where teal-on-dark separates them — fused into one blob with a spur on it. It read
    as nothing at all.

    Two repairs, both of which the colour version does not need because colour does the work:

      * **Holes instead of light shapes.** The coin and the two bars are cut out of the bubble
        with the even-odd fill rule, so the interior still has structure when it is one tint.
      * **A cut gap where the objects meet.** An inflated copy of the top coin is subtracted
        from the bubble, leaving about 2.5dp of air. Touching shapes fuse; separated ones read.
    """
    c = COIN
    cx, cy = BUB["x"] + 13, BUB["y"] + BUB["h"] / 2

    # ⚠ Subpath order does not matter to even-odd, but overlap does: each hole must sit wholly
    # inside the bubble, or it cuts into the empty field and notches the outline instead.
    holes = (ellipse(cx, cy, 7.6, 7.6)
             + rrect(BUB["x"] + 24, cy - 6.4, 14, 4.6, 2.3)
             + rrect(BUB["x"] + 24, cy + 1.4, 9, 4.6, 2.3))
    gap = ellipse(c["cx"], c["cy"], c["rx"] + 2.6, c["ry"] + 2.6)
    s = [Shape(bubble_path(**BUB) + holes + gap, "#FF000000", even_odd=True)]

    # The stack stays a union, so it reads as one solid object rather than three rings: three
    # subpaths under the default fill rule merge instead of cancelling.
    stack = "".join(ellipse(c["cx"], c["cy"] + dy, c["rx"], c["ry"])
                    for dy in (c["step"] * 2, c["step"], 0))
    s.append(Shape(stack, "#FF000000"))
    return s


def background():
    """The field and the diagonal sweep, measured off the reference Mutalib sent."""
    # ⚠ The background fills all 108x108 with square corners on purpose. The launcher supplies
    # the shape; rounding it here would round it twice and leave pale slivers at the mask edge.
    return [
        Shape("M0,0 h108 v108 h-108 z",
              ("grad", 0, 0, 108, 108, P["field"], P["field2"])),
        Shape("M108,20 C70,44 52,80 46,108 L20,108 C26,66 56,30 108,8 Z",
              ("grad", 20, 20, 108, 108, P["sweep2"], P["sweep"]), 0.20),
        Shape("M108,34 C84,52 74,74 70,108 L108,108 Z",
              ("grad", 70, 34, 108, 108, P["sweep2"], P["sweep"]), 0.96),
    ]


# ------------------------------------------------------------------ renderers
def to_svg(shapes, uid="a"):
    out, defs = [], []
    for i, sh in enumerate(shapes):
        op = "" if sh.alpha is None else f' opacity="{sh.alpha}"'
        op += ' fill-rule="evenodd"' if sh.even_odd else ""
        if isinstance(sh.fill, tuple):
            _, x1, y1, x2, y2, c1, c2 = sh.fill
            gid = f"g{uid}{i}"
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                        f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
                        f'<stop offset="0" stop-color="{c1}"/>'
                        f'<stop offset="1" stop-color="{c2}"/></linearGradient>')
            fill = f"url(#{gid})"
        else:
            fill = sh.fill
        out.append(f'<path d="{sh.d}" fill="{fill}"{op}/>')
    return f'<defs>{"".join(defs)}</defs>' + "".join(out)


VEC_HDR = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp"\n'
           '    android:height="108dp"\n'
           '    android:viewportWidth="108"\n'
           '    android:viewportHeight="108">\n')


def to_vector(shapes):
    """
    The same shapes as an Android vector drawable.

    ⚠ Colours here carry an explicit alpha channel (#AARRGGBB) and gradients live inside an
    `<aapt:attr>` block rather than a `<defs>`. Neither is optional: a `<defs>` gradient is
    silently ignored by aapt, which renders the shape black.
    """
    out = VEC_HDR
    for sh in shapes:
        a = "" if sh.alpha is None else f'\n        android:fillAlpha="{sh.alpha}"'
        a += '\n        android:fillType="evenOdd"' if sh.even_odd else ""
        if isinstance(sh.fill, tuple):
            _, x1, y1, x2, y2, c1, c2 = sh.fill
            out += (f'    <path\n        android:pathData="{sh.d}"{a}\n        >\n'
                    f'        <aapt:attr name="android:fillColor">\n'
                    f'            <gradient android:type="linear"\n'
                    f'                android:startX="{x1}" android:startY="{y1}"\n'
                    f'                android:endX="{x2}" android:endY="{y2}">\n'
                    f'                <item android:offset="0" android:color="{argb(c1)}"/>\n'
                    f'                <item android:offset="1" android:color="{argb(c2)}"/>\n'
                    f'            </gradient>\n'
                    f'        </aapt:attr>\n    </path>\n')
        else:
            out += (f'    <path\n        android:pathData="{sh.d}"\n'
                    f'        android:fillColor="{argb(sh.fill)}"{a}/>\n')
    return out + "</vector>\n"


def argb(hexcolour):
    """#RRGGBB -> #FFRRGGBB. Android wants the alpha channel spelled out."""
    return hexcolour if len(hexcolour) == 9 else "#FF" + hexcolour.lstrip("#")
