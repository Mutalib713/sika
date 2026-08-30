# Sika — PLAN.md

Read [`PROFILE.md`](PROFILE.md) first. It is canonical; this file is the order of work.

**One task per session.** Each ends with something visible, its own verification, a commit and a
push. ⚠ marks a task whose outcome is genuinely unknown before it runs.

**The ordering is the point.** Parser before screens, correctness before polish. When a number looks
wrong you should only ever have one suspect.

---

## Milestone 0 — Skeleton

- [ ] **1. Gradle project + `./gradlew check`**
  Kotlin, Compose, Material 3, Room, `minSdk 31`, `targetSdk 36`. Package `gh.mutalib.sika`.
  Empty `ParserTest.kt` with one trivial assertion so the QA suite file exists from day one.
  **Verify:** `./gradlew check` exits 0. Paste the output.

- [ ] **2. The app runs on the Pixel**
  One screen, an empty state saying there is nothing yet. No permissions requested. No parser.
  **Verify:** `./gradlew installDebug`, the app opens on the phone, `adb logcat -s Sika` shows it
  start. This is "live on the real host" — from here every task ships to a device.

## Milestone 1 — The parser

The risky heart. No UI in this milestone at all.

- [ ] **3. ⚠ Parser + golden tests**
  The four confirmed shapes from PROFILE.md §8. Write the tests *first*, then make them pass.
  Handle the measured landmines: optional space after `GHS`, three fee labels, two balance labels,
  two ID labels, the trailing full stop, `Tax was GHS -.`
  **Risky because:** these are four samples of a format only MTN controls.
  **Verify:** 4/4 golden tests green. Show the test output.

- [ ] **4. Room entities, DAO, dedupe**
  `transactions` and `rules` exactly as PROFILE.md §8 specifies, including `rawBody` and
  `balanceAfter`. Unique index on `txId`.
  **Verify:** a test that inserts the same `txId` three times and asserts exactly one row survives.

- [ ] **5. ⚠ Inbox sweep + first-run backfill**
  `ContentResolver` query against the SMS provider, filtered to the MoMo sender. Dates from
  Android's timestamp, never the body.
  **Risky because:** this is the first contact with your real inbox. **This task answers open
  question 1** — whether shapes beyond the four exist.
  **Verify:** run on the Pixel and print four numbers: total MoMo messages found, parsed OK,
  `UNKNOWN`, and how far back the oldest message goes. Any `UNKNOWN` becomes a new golden test in
  task 3 *before* the parser is touched.

- [ ] **6. ⚠ Live BroadcastReceiver**
  Manifest-declared, `SMS_RECEIVED`, runtime permission request.
  **Risky because:** background wake-up behaviour is device and battery-state dependent.
  **Verify:** two stages. First `adb shell am broadcast` with a fake MoMo message — logcat shows a
  row written. Then a real transaction: buy GHS 1 of airtime and watch the row appear without
  opening the app.

## Milestone 2 — Trust

Sacred Rule 3: this ships before the pretty screens, not after.

- [ ] **7. Reconciliation**
  Walk the ledger in time order, check `previous balance − amount − fee == new balance`, mark each
  row `OK` / `GAP` / `UNCHECKED`.
  **Verify:** run over the real backfill from task 5. Report how many rows reconcile and where the
  gaps are. **Gaps are expected** — MTN does not always send an SMS. Finding them is a pass, not a
  failure.

- [ ] **8. Review queue**
  Anything `parsedOk = false` is held, visible and countable. Never guessed, never dropped.
  **Verify:** feed the parser a deliberately mangled message; it lands in the queue instead of
  becoming a wrong row.

## Milestone 3 — The screens

Screen inventory: [`docs/screens.md`](docs/screens.md). Stitch prompts for visual exploration:
[`docs/stitch-prompt.md`](docs/stitch-prompt.md) — its output is a reference, not shippable code.

- [x] **9. Palette pinned** — done 2026-08-30
  Mutalib brought his own four hexes from a reference image rather than using the picker:
  `#A8DCE7` `#101422` `#FFFFFF` `#272B3B`. Gap roles grown from `palette.py --seed "#A8DCE7"
  --dark`. Sacred Rule 9: pinned by him, canon, not "improved" later.
  **Verified:** full token set, measured contrast for every information-carrying pair, and the two
  rules that fall out of it, written into `docs/ui-guidelines.md`.

- [ ] **10. design-studio pass, then Home screen**
  Studio tier: house-taste, 2–3 galleries, `/taste` on the best one, direction line. Then build:
  this month's in / out / net / current balance, recent transactions below.
  **Verify:** `gate.py` output pasted, humanizer string gate on every visible string, screenshot on
  the real device. *(impeccable's detector does not read `.kt` — say so, don't fake it.)*

- [ ] **11. Labels + learn-once rules**
  Tap any transaction to set a label. Setting one writes a rule keyed on the counterparty, so the
  next message from `MTN AIRTIME` or `bbb` labels itself.
  **Verify:** label a counterparty once, inject a second message from the same counterparty, confirm
  it arrives already labelled.

- [ ] **12. Cash-out prompt**
  When a `CASH_OUT` row lands, a notification asks *"GHS X — what for?"* with one-tap answers.
  **Verify:** inject a cash-out broadcast, tap an answer from the notification shade, confirm the
  label is stored with `labelSource = PROMPT`.

- [ ] **13. Month report**
  The four numbers, breakdown by label, this month vs last. Charts hand-drawn on Compose `Canvas`.
  **Verify:** screenshot against a hand-checked total from the same month's raw messages. The
  numbers must match arithmetic done by hand, not just look plausible.

- [ ] **14. Monthly notification**
  `AlarmManager`, exact, allow-while-idle, rescheduled after each firing.
  **Verify:** set the device clock to 23:58 on the last of a month, watch it fire, confirm it
  reschedules for the following month.

- [ ] **15. Settings + CSV export *and import***
  Permission state, categories, learned rules, export to CSV **and import back**. Risk #3: your
  labels are the only irreplaceable data, and export alone lets you look at them after a wipe
  rather than recover them.
  **Verify:** export, wipe the app's data, import, confirm every row *and every label* returns.

- [ ] **16. Error and empty states**
  Permission denied, no messages found, nothing this month, reconciliation gap detected.
  **Verify:** trigger each one deliberately and screenshot it.

## Milestone 4 — Harden and ship

- [ ] **17. `docs/security-checklist.md` end to end**, ticks committed.
- [ ] **18. Benchmark audit + red team.** Graded against the best real app in the category, not
      "good for a side project."
- [ ] **19. Tag `v1.0.0`.** Then leave it alone and use it for a month.

---

## Not scheduled — v1.1, after v1 has survived a month

Recorded in PROFILE.md §4. Budgets with 80% alerts · semester ranges · arithmetic insights ·
Gemini as an optional second layer (the only task that ever adds `INTERNET`).

## Not scheduled — Mutalib's own checks

Neither blocks anything; both close open questions in PROFILE.md §11.

- [ ] Dial `*170#` → My Wallet → Statements → Statement Request. Does the PDF cost anything, is it
      password-protected, how far back does it go?
- [ ] 20 minutes with *Expense Tracker: Budget No Ads* — do its totals look right, and what does it
      do with a Cash Out?
