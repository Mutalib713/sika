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

  - [x] **Stage 2 — the live test.** Passed 2026-08-30 23:47 on a real airtime purchase, with
        Sika backgrounded and never opened:
        ```
        23:47:03.589 I/Sika: live: recorded BILL_AIRTIME OUT 10p to 'MTN AIRTIME' txId=88451332200
        ```
        Then, on the next launch, the sweep re-read that same message and added **0 new** rows —
        the two routes overlapping harmlessly, which is the whole point of having both.

        ⚠ **Two attempts were wasted by my own test setup**, both now in CLAUDE.md:
        `connectedDebugAndroidTest` had silently uninstalled the app, and `am force-stop` put it in
        Android's *stopped state* where it receives no broadcasts at all. Use `am kill` instead —
        it frees the process without setting the stopped flag.

        ⚠ **And stage 1 was weaker than first reported.** `adb`'s `am broadcast` sets
        `FLAG_INCLUDE_STOPPED_PACKAGES`, so the debug injector reaches a stopped app that a real
        SMS never would. It proves the ledger path, never delivery.

## Milestone 2 — Trust

Sacred Rule 3: this ships before the pretty screens, not after.

- [x] **7. Reconciliation** — done 2026-08-30
  `Reconciler` is pure Kotlin over a plain list, so the arithmetic is tested on the JVM;
  `ReconcilePass` is the thin database half and runs after every sweep rather than on request.
  **Verified over the real 144-row ledger:**
  ```
  reconcile: 142 ok, 1 gaps, 1 unchecked, of 144

  gap  22 Jul 12:56  PAYMENT_MADE '<counterparty>'  amount GHS 6.00
       expected GHS 4595.39   actual GHS 4590.39   diff -GHS 5.00
  ```
  **GHS 5.00 left the wallet on 22 July that MoMo never texted about.** The one unchecked row is
  the oldest, which has nothing before it to check against. 8/8 reconciler tests green, plus
  20/20 parser and 9/9 instrumented.
  ⚠ **A test caught a real flaw in the first algorithm.** It anchored only on the last *stated*
  balance and ignored the amounts of rows that state none, so any such row made the next one look
  wrong by exactly its amount. A false gap is worse than a missed one — it teaches you to ignore
  the warning. Now a running balance carries through every transaction and re-anchors whenever
  MoMo states a figure.

- [x] **8. Review queue** — done 2026-08-31
  Read from the ledger rather than from the current sweep, so a message queued by the live
  receiver last week still appears. Count and contents on the diagnostic screen and in the log.
  **Verified on the Pixel** by injecting a money-shaped message no MTN shape matches
  (`Reversal of GHS 30.00 … Transaction Id: 99900011122 … Current Balance: GHS 60.00`):

  |  | baseline | after injecting | meaning |
  |---|---|---|---|
  | queued for review | 0 | **1** | held, not dropped |
  | transactions | 150 | **150** | not counted as money |
  | rows in ledger | 144 | 145 | it exists |
  | reconcile | 142 ok / 1 gap / 1 unchecked **of 144** | **identical, still of 144** | never entered the chain |

  Ingestion logged `0p to ''` — **no amount guessed, no counterparty invented** — and the queue
  showed the reason worked out on read plus the original text. Test row then cleared; ledger back
  to 144. 11/11 instrumented, 28/28 JVM tests, `check: PASS`.
  ⚠ **Correctness fix made here:** an unparsed row's `counterparty` is now empty rather than the
  failure reason. `counterparty` is the key a learn-once rule attaches to, so a review row could
  have acquired a rule for a sentence of English. The reason is derived by re-parsing `rawBody`
  instead, which also keeps it current — fix the parser and a queued message reports that it is
  now readable.

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

- [x] **11. Labels + learn-once rules** — built 2026-08-31, device check pending
  `TransactionSheet` (screen 2 of docs/screens.md): tap any row for the amount, the category chips
  with a `+` at the end, the learn-once toggle, the receipt, and the original SMS behind a
  disclosure. Wired to both Home and the all-transactions list.
  Ticking **"Always label X this way"** — on by default — writes a rule keyed on the counterparty
  and applies it to every other row from that party. The hand-set row is always `MANUAL`, so a rule
  can never later overwrite a decision made by hand.
  `check: PASS`, gate.py 0 block, humanizer 100.0/85.
  - [x] **Device check passed 2026-08-31.** Tapped a `AKOSUA MENSAH` row, tapped **Food**,
        and **all three** of her rows — 31 Aug, 29 Aug and 28 Aug — relabelled at once. One tap,
        three rows. The learn-once mechanism working on real data, which is the answer to risk #2
        (label rot).
        Two fixes from watching it: the sheet now closes on pick **deliberately** rather than by
        accident, because picking is the job and the result is worth seeing on the list behind it;
        and a local named `remember` that shadowed the composable of the same name was renamed.
        Also learned: **the `Reference` field is not always `-` or `1`** — one real transaction
        carries `Bread`. PROFILE.md § 8's note that it is useless in practice is too strong.

- [ ] **12. Cash-out prompt** — built 2026-08-31, **device check pending (phone not connected)**
  When a `CASH_OUT` row lands, a notification asks *"GHS X — what for?"* with one-tap answers.
  `notify/CashOutPrompt.kt` posts it, `notify/CashOutReplyReceiver.kt` stores the answer with
  `labelSource = PROMPT`.
  `check: PASS`, 31 unit tests, 0 failures.

  Three decisions the spec did not cover, all recorded in `docs/screens.md`:
  - **Android draws at most three action buttons.** A fourth is silently dropped, so the
    `Choose…` escape moved onto the notification body, which opens the transaction sheet.
  - **Only the live receiver prompts, never the sweep** — otherwise first run fires a
    notification for every historic cash-out.
  - **No learn-once rule from a prompt answer** — the counterparty is the agent, not the
    purchase, so a rule there would mislabel every future cash-out from that agent.

  Two bugs caught before the device: `asCedis()` already writes the `GHS` prefix, so the title
  read `GHS GHS 20.00`; and `POST_NOTIFICATIONS` does not exist below API 33, where
  `checkSelfPermission` answers DENIED — which would have silenced the prompt on Android 12
  for a permission it never needed.

  - [ ] **Device check.** ⚠ Not run — no device attached on 2026-08-31. Inject a cash-out
        broadcast, tap an answer from the shade, confirm the label is stored with
        `labelSource = PROMPT` and that the app was never opened.
        ```bash
        adb shell "am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS -n gh.mutalib.sika/.sms.DebugSmsReceiver --es body 'Cash Out made for GHS20.00 to AGENT TEST .Current Balance: GHS 67.21. Transaction Id: 90000000012. Fee charged: GHS0.00,Tax Charged 0.'"
        ```
        `DebugSmsReceiver` now passes `promptOnCashOut = true` to match the live receiver, so
        an injected cash-out raises the same prompt a real one would.
        ⚠ Grant the notification permission first — on Android 13+ a denied `POST_NOTIFICATIONS`
        makes the prompt vanish with only a logcat line, which reads exactly like a broken build:
        `adb logcat -s Sika` will say `cash-out prompt suppressed`.

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
