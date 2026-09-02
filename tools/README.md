# tools/

Things that check Sika on a real device, or make one testable.

They live here rather than in `design-scratch/` because that folder is gitignored: anything in
it exists on one laptop and nowhere else. The icon generator was in that position until the day
it was needed, which is why this folder exists at all.

None of them are part of `./gradlew check`. They need a phone or an emulator, and `check` has
to pass with nothing attached.

---

## Which one do I want?

| I want to… | Use |
|---|---|
| Find out whether a total on screen is actually right | `hand_check.py` |
| Watch an alarm fire, or see a month's report with data in it | `seed_month.py` on an emulator |
| Test answering a cash-out from the notification shade | `notif_drive.py` |
| Change the icon and prove it still fits every launcher mask | `icon/build_launcher_icon.py`, then `icon/check_icon.py` |

Every one takes `--device <serial>` and **refuses to guess** when a phone and an emulator are
both attached — writing test rows into the wrong ledger is the mistake worth making impossible.

---

## `hand_check.py` — is the report telling the truth?

```bash
python tools/hand_check.py 2026-08
```

Pulls the ledger off the phone and answers one question: **is Sika misreading the messages, or
is your memory of the month wrong?** Nothing else in the project can answer it, because
everything else shares the parser.

It does **not** add up Sika's stored columns — that would agree every time, since Sika wrote
them. It re-reads `rawBody`, the original MTN text kept with every row, using regexes written
in that file. Two independent readings of the same messages.

Then `--chain` does something stronger and shares nothing at all: every MoMo message states a
balance, so walking the ledger in time order and checking `previous − amount − fee − tax`
against what MTN itself printed tests Sika against MTN's arithmetic rather than against a
second copy of our own.

⚠ **A break in the chain is not automatically a bug.** A message MTN never sent breaks it too —
that is exactly what the app calls a gap. Compare the number of breaks with the gap count on
the Settings screen; they should be equal.

⚠ **A disagreement names a suspect, not a culprit.** Its first run found GHS 0.38 missing and
**the app was right**: TELECEL PUSH messages say *"Fee was GHS 0.38"*, a fourth wording this
script had not allowed for. Read the message before believing either side.

## `seed_month.py` — give an emulator a month to talk about

```bash
python tools/seed_month.py 2026-09          # 13 transactions across September
python tools/seed_month.py 2026-09 --undo   # take them all back out
```

⚠ **Refuses to run on a real phone without `--force`.** It writes invented transactions. On an
emulator that is the point; on the Pixel it is pollution to be picked out one id at a time.

⚠ **The balances form a real chain.** Reconciliation compares each message's stated balance
against the one before it, so arbitrary balances would arrive pre-broken and every screenshot
afterwards would show manufactured gaps.

It prints the totals it built **from its own numbers**, so they are something to check the
report against rather than a copy of it.

## `notif_drive.py` — answer a cash-out without opening the app

```bash
python tools/notif_drive.py clear                 # get down to one notification first
python tools/notif_drive.py cashout               # inject, pick a category, save
python tools/notif_drive.py note "waakye at KNUST"
python tools/notif_drive.py forget --txid 99000000900
```

`CashOutReplyReceiver` is `exported=false` — correct, and the reason none of this can be done
with a broadcast. The only way in is tapping the real buttons.

⚠ **Grouped notifications hide their buttons.** With more than one Sika notification live,
Android bundles them and the children render collapsed — nothing for `uiautomator` to find.
Run `clear` first.

⚠ **The heads-up banner lasts about five seconds, and the timing matters.** Inject → 2.0s →
tap the category → **2.5s** → tap the action. At 1.0s the banner is still re-inflating and
eats the tap; at 0s the second tap lands before the buttons have moved.

## `icon/` — the launcher icon

`icon_p2.py` holds the geometry and is the **only** description of the mark; the
`ic_launcher_*.xml` files are generated and must never be hand-edited. `build_launcher_icon.py`
writes them, `check_icon.py` proves the art still fits the 66dp circle every launcher mask
respects, and that it is centred.

---

## Getting the emulator to behave

```bash
~/AppData/Local/Android/Sdk/emulator/emulator.exe -avd wird_pixel6pro \
    -no-snapshot-load -gpu swiftshader_indirect
```

⚠ **`-gpu swiftshader_indirect` is not optional if you intend to take screenshots.** With
hardware rendering, `adb exec-out screencap` on this machine returns a fully black image while
the emulator is plainly awake and drawing. Software rendering fixes it; it boots slower.

⚠ **Moving the wall clock does not move `elapsedRealtime`.** `setAndAllowWhileIdle` alarms have
an hour-wide window measured in *real* seconds, so jumping the date forward makes an alarm
overdue (`whenElapsed` goes negative in `dumpsys alarm`) without advancing the window. It fires
seconds-to-minutes later. It is batched, not broken.

⚠ **Two devices attached means `installDebug` cannot choose.** Build with `assembleDebug` and
`adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
