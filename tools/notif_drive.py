"""
Drives Sika's notifications from the laptop, for the one flow that cannot be tested any other
way: answering a cash-out from the shade without opening the app.

    python tools/notif_drive.py show          what Sika has on screen right now
    python tools/notif_drive.py clear         swipe Sika's notifications away
    python tools/notif_drive.py cashout       inject one, tap a category, tap Save
    python tools/notif_drive.py note "waakye" inject one, then type a note into the reply box

⚠ **`CashOutReplyReceiver` is `exported=false`, so adb cannot broadcast to it.** That is
correct and deliberate — it is what stops any other app on the phone writing labels into the
ledger — and it means the only way in is to tap the real buttons.

### The two things that make this hard, both learned the slow way

**Grouped notifications hide their buttons.** Once Sika has more than one notification live,
Android bundles them and renders the children collapsed: title and text only, nothing for
`uiautomator` to find. `clear` exists for that reason — get down to one, then work.

**The heads-up banner is the only surface with the actions on it, and it lives about five
seconds.** The timing that works, measured on a Pixel 6 Pro:

    inject → wait 2.0s → tap the category → wait 2.5s → tap the action

Waiting 1s between the taps fails: the banner is re-inflating after the update and swallows
the tap. Waiting 0s fails differently — the second tap lands before the buttons have moved,
on whatever was in that position a moment earlier.

⚠ **Every injected row is real.** Delete them afterwards, with
`python tools/notif_drive.py forget <txid>` or the `--es forget` broadcast.
"""
import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb  # noqa: E402

# Where the banner's buttons sit on a 1440x3120 screen. Read off a screenshot rather than
# computed, because the heads-up is drawn by SystemUI and is not in the app's layout.
BANNER_ROW_Y = 447
BANNER_X = {"first": 369, "second": 648, "third": 925, "save": 365, "note": 997}


def ascii_(s):
    return s.encode("ascii", "replace").decode()


class Shade:
    def __init__(self, device):
        self.d = device

    def dump(self):
        """The visible node tree. Returns [] rather than raising when the dump fails."""
        self.d.run("shell", "uiautomator", "dump", "/sdcard/sika_ui.xml")
        x = self.d.run("exec-out", "cat", "/sdcard/sika_ui.xml")
        out = []
        for m in re.finditer(
            r'<node[^>]*?text="([^"]*)"[^>]*?resource-id="([^"]*)"[^>]*?'
            r'class="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x
        ):
            l, t, r, b = (int(v) for v in m.groups()[3:])
            out.append(dict(text=m.group(1), rid=m.group(2), cls=m.group(3),
                            box=(l, t, r, b), c=((l + r) // 2, (t + b) // 2)))
        return out

    def find(self, nodes, *needles, rid=None, cls=None):
        for n in nodes:
            if rid and rid not in n["rid"]:
                continue
            if cls and cls not in n["cls"]:
                continue
            if needles and not any(k in n["text"] for k in needles):
                continue
            if needles or rid or cls:
                return n
        return None

    def open(self):
        self.d.shell("cmd statusbar expand-notifications")
        time.sleep(2)

    def to_top(self):
        """⚠ The shade keeps its scroll position between openings — always start at the top."""
        for _ in range(12):
            self.d.run("shell", "input", "swipe", "720", "1500", "720", "2400", "180")

    def scroll_to(self, *needles, passes=16):
        self.to_top()
        time.sleep(1)
        for _ in range(passes):
            nodes = self.dump()
            if self.find(nodes, *needles):
                return nodes
            self.d.run("shell", "input", "swipe", "720", "2300", "720", "1600", "250")
            time.sleep(1)
        return None


SIKA_TEXT = ("Save it?", "What was it for", "unaccounted", "cashed out", "out, GHS")


def cmd_show(d, _args):
    s = Shade(d)
    s.open()
    nodes = s.scroll_to(*SIKA_TEXT)
    if not nodes:
        print("no Sika notification on screen")
        return
    for n in nodes:
        if any(k in n["text"] for k in ("GHS", "Say what", "Save", "Change", "unaccounted")):
            print(f'  {ascii_(n["text"])[:46]!r:50} {n["c"]}')


def cmd_clear(d, _args):
    s = Shade(d)
    s.open()
    for _ in range(12):
        nodes = s.scroll_to(*SIKA_TEXT)
        if not nodes:
            print("shade is clear of Sika")
            return
        n = s.find(nodes, *SIKA_TEXT)
        y = n["c"][1]
        print(f"  dismissing {ascii_(n['text'])[:40]!r}")
        d.run("shell", "input", "swipe", "400", str(y), "1360", str(y), "200")
        time.sleep(1.2)


def inject_cashout(d, txid, amount, balance):
    d.run("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.5)
    body = (f"Cash Out made for GHS{amount} to 000. Current Balance: GHS{balance} "
            f"Financial Transaction Id: {txid}. Cash-out fee is charged automatically "
            f"from your MTN MoMo wallet. Fee charged: GHS0.50.")
    d.broadcast(f"--es body '{body}'")
    print(f"injected {txid} — remember to forget it afterwards")
    time.sleep(2.0)


def tap(d, where):
    d.run("shell", "input", "tap", str(BANNER_X[where]), str(BANNER_ROW_Y))


def cmd_cashout(d, args):
    inject_cashout(d, args.txid, args.amount, args.balance)
    tap(d, "first")     # the first category chip
    time.sleep(2.5)     # ⚠ not 1.0 — the banner is still re-inflating at 1.0
    tap(d, "save")
    time.sleep(3)
    print(d.run("logcat", "-d", "-s", "Sika")[-600:])


def cmd_note(d, args):
    inject_cashout(d, args.txid, args.amount, args.balance)
    tap(d, "first")
    time.sleep(2.5)
    tap(d, "note")
    time.sleep(2)
    s = Shade(d)
    box = s.find(s.dump(), cls="android.widget.EditText")
    if not box:
        print("the reply box did not open - the banner probably timed out, try again")
        return
    d.run("shell", "input", "tap", str(box["c"][0]), str(box["c"][1]))
    time.sleep(1)
    d.run("shell", "input", "text", args.text.replace(" ", "%s"))
    time.sleep(1)
    send = s.find(s.dump(), rid="remote_input_send")
    if send:
        d.run("shell", "input", "tap", str(send["c"][0]), str(send["c"][1]))
    else:
        d.run("shell", "input", "keyevent", "KEYCODE_ENTER")
    time.sleep(3)
    print(d.run("logcat", "-d", "-s", "Sika")[-600:])


def cmd_forget(d, args):
    d.broadcast(f"--es forget {args.txid}")
    time.sleep(2)
    print(d.run("logcat", "-d", "-s", "Sika")[-300:])


def main():
    p = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    p.add_argument("command", choices=["show", "clear", "cashout", "note", "forget"])
    p.add_argument("text", nargs="?", default="agent near campus")
    p.add_argument("--txid", default="99000000900")
    p.add_argument("--amount", default="5.00")
    p.add_argument("--balance", default="0.50")
    adb.add_device_arg(p)
    args = p.parse_args()

    d = adb.Device(args.device)
    {"show": cmd_show, "clear": cmd_clear, "cashout": cmd_cashout,
     "note": cmd_note, "forget": cmd_forget}[args.command](d, args)


if __name__ == "__main__":
    main()
