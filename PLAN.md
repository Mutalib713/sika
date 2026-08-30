# Sika — PLAN.md

Read [`PROFILE.md`](PROFILE.md) first. It is canonical; this file is the order of work.

**One task per session.** Each ends with something visible, its own verification, a commit and a
push. ⚠ marks a task whose outcome is genuinely unknown before it runs.

**The ordering is the point.** Parser before screens, correctness before polish. When a number looks
wrong you should only ever have one suspect.

---

## Milestone 0 — Skeleton

- [x] **1. Gradle project + `check`** — done 2026-08-30
  Kotlin, Compose, Material 3, Room + KSP, `minSdk 31`, `targetSdk 36`, package `gh.mutalib.sika`.
  `ParserTest.kt` exists with one placeholder so the QA suite is wired from day one.
  ⚠ **Found the hard way: KSP does not work with AGP 9 at all**, so this project runs AGP 8.13.2 /
  Kotlin 2.3.21 / KSP 2.3.11 / Gradle 9.4.1 while Wird and Thrum stay on AGP 9. PROFILE.md § 7 and
  CLAUDE.md carry the reasoning — do not "fix" it by adding the two deprecated AGP flags.
  **Verified:** `.\check.ps1` → `check: PASS`, exit 0. Lint "No issues found", ParserTest 1/1
  passed, `app/build/generated/ksp` present so KSP genuinely ran.

- [x] **2. The app runs on the Pixel** — done 2026-08-30
  The designed "Nothing yet this month" empty state from `docs/screens.md`, on the pinned palette.
  No permissions requested, no parser, no SMS access. `TAG = "Sika"` established as the single
  logcat tag for the whole app.
  **Verified on the real device** (Pixel 6 Pro, `raven`, `1A131FDEE006MD`):
  `check: PASS` → `installDebug` exit 0 → `versionName=0.1` installed →
  `topResumedActivity=gh.mutalib.sika/.MainActivity` →
  `I Sika: MainActivity started — no ledger yet, nothing to show`.
  This is "live on the real host": from here every task ships to a device.

## Milestone 1 — The parser

The risky heart. No UI in this milestone at all.

- [x] **3. ⚠ Parser + golden tests** — done 2026-08-30
  `MomoParser` as a list of shape matchers, pure Kotlin with no Android import so the suite runs on
  the JVM in 0.1s. All four confirmed shapes, every measured landmine covered.
  **Money became `Long` pesewas, not `Double`** — PROFILE.md § 8 corrected. Reconciliation compares
  `prev − amount − fee == new`, and with Doubles that needs a tolerance, which is exactly what hides
  a real discrepancy.
  **Verified:** `check: PASS`, **12/12 tests green**, lint clean.
  **And verified the suite has teeth**, by mutation: replacing the fee pattern with `([\d.,]+)`
  makes `shape 2 - cash out` fail with `expected:<50> but was:<0>`. Two earlier mutation attempts
  changed nothing, which corrected a wrong comment — `\d+` cannot swallow a full stop, only a
  character class *containing* the dot can. The failure mode is the important part: the fee goes
  silently to **zero** rather than crashing.

- [x] **4. Room entities, DAO, dedupe** — done 2026-08-30
  `transactions`, `rules` **and `categories`** (added to PROFILE.md § 8 after this plan was written,
  so building it now avoids a migration one task later). Unique index on `txId`, `rawBody` and
  `balanceAfter` present, enums stored by name, `exportSchema = true` from version 1.
  **Verified on the Pixel** — real SQLite, not a fake: `connectedDebugAndroidTest` **9/9 green**,
  plus `check: PASS` for lint and the 12 parser tests.
  ⚠ **The verification this task originally specified was not sufficient**, and mutation testing
  showed it. Swapping `IGNORE` for `REPLACE` — the obvious wrong choice — leaves the row count at
  exactly one, so *"insert three times, assert one row"* still passes while every label, cash-out
  answer and reconciliation result is silently destroyed on each sweep. The test that caught it was
  `reInsertingASeenTransactionDoesNotDestroyItsLabel`: `expected:<Airtime> but was:<null>`.

- [x] **5. ⚠ Inbox sweep + first-run backfill** — done 2026-08-30
  `ContentResolver` query against the SMS provider, sender `MobileMoney`, dates from Android's
  timestamp. Runs on every launch, not just the first.
  **Verified on the Pixel:** `301 matched · 118 transactions · 144 not transactions · 39
  unrecognised · 152 in ledger`. A second run added **0 new rows** — Sacred Rule 4 holding on real
  data across restarts.
  **Open question 1 is answered, and the answer is yes.** The four known shapes cover only ~75% of
  real transactions. Six more are recorded in PROFILE.md § 8, which re-opens task 3 → **task 5b**.
  Also changed here: the sender filter narrowed from three loose patterns to the one real address
  (465 → 301 messages read, nothing lost), and `ParseResult` gained `NotATransaction` so MTN's OTPs
  and adverts stop flooding the review queue.

- [x] **5b. ⚠ The six shapes the sweep found** — done 2026-08-30
  Golden tests written first from the real bodies, then the parser extended. Four new matchers
  (`Payment for`, `Cash In received`, `You have transferred`, `Y'ello… You have Paid`) plus a
  **failure guard checked before anything else**, because a failed payment carries a real amount
  and a real transaction id and every other test would wave it through.
  Three shared patterns had to change: the balance colon became optional (`Current Balance GHS
  102.07`), a reversed balance pattern was added (`4652.89 GHS`, number first), and the tax label
  gained a no-colon spelling (`Tax Charged 0`).
  **Verified:** `check: PASS` with **20/20 golden tests**, `connectedDebugAndroidTest` 9/9, and a
  re-sweep of the real inbox:

  ```
                transactions   not-transactions   unrecognised
  before 5b            118                144             39
  after  5b            147                154              0
  ```

  The 39 resolved as **29 real transactions + 10 failures** — exactly 39, nothing unaccounted for.
  A second sweep added **0 new rows**. 147 transactions produced 141 rows, so the unique index
  caught **6 genuine duplicates** MTN had sent twice.

- [x] **6. ⚠ Live BroadcastReceiver** — stage 1 done 2026-08-30, stage 2 awaiting a real transaction
  `SmsReceiver` declared in the manifest for `SMS_RECEIVED`, protected by
  `android:permission="android.permission.BROADCAST_SMS"` so only the system can deliver it —
  without that, any app on the phone could fabricate a transaction and Sika would record it as
  real money. Multipart parts are concatenated, because MoMo alerts run past 160 characters and
  reading only `parts[0]` would drop the fee, balance and transaction id off the end while still
  matching a shape. `goAsync()` plus a coroutine keeps the Room write off the main thread's ~10s
  budget.

  ⚠ **The verification this task specified is impossible.** `SMS_RECEIVED` is a *protected
  broadcast* — `adb shell am broadcast` is refused with `SecurityException … from uid=2000` — and
  there is no emulator on this machine. So a **debug-only injector** (`src/debug`, absent from
  release builds entirely) feeds a body through the identical ingestion path.

  **Stage 1 verified, with the app force-stopped:**
  ```
  debug-inject: recorded PAYMENT_FOR OUT 100p to 'TASK SIX TEST' txId=90000000001
  debug-inject: already had PAYMENT_FOR OUT 100p to 'TASK SIX TEST' txId=90000000001
  ```
  The app was closed, Android started it for the broadcast, and the second injection was refused
  by the dedupe. Test row cleared afterwards; ledger rebuilt to 141 rows from real messages only.
  `check: PASS`, 20/20 golden tests, 9/9 instrumented.

  - [ ] **Stage 2 — the live test.** Buy GHS 1 of airtime (or any small transaction) with the app
        closed and `adb logcat -s Sika` running. A `live:` line must appear without the app being
        opened. This is the only step that exercises PDU decoding and Android's real wake-up
        behaviour, and neither can be faked.

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
