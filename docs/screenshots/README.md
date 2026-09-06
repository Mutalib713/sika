# Screenshots

Sika shows nothing without MTN MoMo messages, so for most people who open this repo the
screenshots **are** the app. They are the deliverable here, not decoration.

## What is here

Captured 2026-09-06 on the `wird_pixel6pro` emulator (Android 14) in demo mode, so every figure
and every counterparty is fabricated.

| File | Screen | What it carries |
|---|---|---|
| `01-home.png` | Home | Totals, the categorised bar, and the amber reconciliation card asking what GHS 90.00 was. Doubles as the "needs a category" shot. |
| `02-prompt.png` | Notification shade | The category prompt with its three buttons showing. Cropped to the card. |
| `03-confirm.png` | Notification shade | The second step: *"Just this one, or always MELCOM?"* with Just once / Change / Always. |
| `04-report.png` | Report | Period selector, week-by-week against last month, and the category breakdown. |
| `05-settings.png` | Settings | The notification switches, including the renamed prompt row and the tap-to-test reminder. |

Still missing, and worth adding: **a short screen recording of the prompt flow.** The two-step
pick then confirm is a sequence, and no still can show that one tap never writes anything.
`adb exec-out screenrecord --output-format=h264 -` captures one; keep it to a few seconds.

## Two things the emulator taught us

⚠ **Android bundles Sika's notifications when more than one is showing**, and a bundled
notification hides its action buttons. Reaching the category buttons took two expansions: one for
the app group, one for the notification. A heads-up banner still shows them immediately, so this
only bites once the prompt has settled into a busy shade — which is exactly where it lives when
you come back to the phone later.

⚠ **Tapping at the bottom of the screen hits the gesture bar, not the app.** Guessing tab
coordinates from a screenshot sent the emulator to its launcher and silently killed demo mode,
which lives in memory and dies with the process. Get real bounds from
`adb shell uiautomator dump` instead, and prefix the command with `MSYS_NO_PATHCONV=1` or Git Bash
rewrites `/sdcard/ui.xml` into a Windows path on the device.

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
