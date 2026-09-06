# Screenshots

Sika shows nothing without MTN MoMo messages, so for most people who open this repo the
screenshots **are** the app. They are the deliverable here, not decoration.

## What has to be in them

One per row, in this order. The order matters: it walks someone from "what am I looking at" to
"why is this different from every other expense tracker".

| File | Screen | The one thing it has to show |
|---|---|---|
| `01-home.png` | Home | Real-looking totals (in, out, net, balance) and a list with categories on it. The first shot has to answer "what is this" without a caption. |
| `02-uncategorised.png` | Home | The *"N still need a category"* line. This is the problem the app is actually solving. |
| `03-prompt.png` | Notification shade | The category prompt, expanded, with its three buttons visible. **Not collapsed**, because collapsed hides the buttons and the buttons are the point of the shot. |
| `04-confirm.png` | Notification shade | The second step: *"Just this one, or always MELCOM?"*. Shows that one tap never writes, and that the app offers to learn rather than learning behind your back. |
| `05-report.png` | Report | A category breakdown with a period selector. Where the money went. |
| `06-gap.png` | Home or Report | A reconciliation gap, marked *"with no message from MTN"*. Nothing else in this class of app admits it might be missing something. |
| `07-settings.png` | Settings | The notification switches and SMS access row. Reassures a cautious installer. |

## How to take them without using real money

⚠ **Never screenshot the real ledger.** Every row carries a real person's name, a real amount and
a real balance, and a screenshot in a public README cannot be taken back. Use demo mode, which
holds fabricated rows in memory and never touches the database.

An emulator is the safest surface. No real messages exist on it at all, so there is nothing to
leak even by accident.

```bash
# 1. Boot the emulator (see CLAUDE.md — it opens off-screen on this laptop)
~/AppData/Local/Android/Sdk/emulator/emulator.exe -avd wird_pixel6pro -no-snapshot-load

# 2. Install the debug build. Two devices attached means -s is not optional.
./gradlew assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Turn on demo mode
adb -s emulator-5554 shell "am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS \
  -n gh.mutalib.sika/.sms.DebugSmsReceiver --es demo on"

# 4. Capture. exec-out, never `shell screencap > file` — the Windows PTY
#    rewrites LF to CRLF and corrupts the PNG into something that still
#    half-opens, which is the worst way for this to fail.
adb -s emulator-5554 exec-out screencap -p > docs/screenshots/01-home.png
```

For the notification shots, `adb shell cmd statusbar expand-notifications` opens the shade, and
the debug injector will post a real prompt:

```bash
adb -s emulator-5554 shell "am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS \
  -n gh.mutalib.sika/.sms.DebugSmsReceiver \
  --es body 'Payment for GHS12.50 to MELCOM  .Current Balance: GHS 88.00. Transaction Id: 90000000123. Fee charged: GHS0.00,Tax Charged 0.'"
```

⚠ Quote the whole `am` command for the **device's** shell. Passing `--es body "..."` with only
local quotes lets the words split, and the broadcast silently takes the second word as the
package name instead of failing.

## Then

Drop the files in this folder and replace the *Screenshots* section of the top-level `README.md`
with the images. Keep them under about 300 KB each: a README that takes ten seconds to paint is
a README people scroll past.

A short screen recording is worth more than any single shot for the notification flow, because
the two-step *pick → confirm* is a sequence and a still cannot show it. `adb exec-out screenrecord
--output-format=h264 -` captures one; convert to GIF and keep it under a few seconds.
