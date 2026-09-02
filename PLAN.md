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

- [x] **12. Cash-out prompt** — done 2026-09-01
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

  - [x] **Device check — passed 2026-09-02**, driven from the laptop by tapping the real
        notification, with the app backgrounded and `stopped=false` (never force-stopped, so
        this is the delivery path a real message would take).

        ```
        cash-out prompt shown for row 2157 (3 quick answers)
        cash-out row 2157 noted from the shade
        id 2157 | label 'Food' | labelSource 'PROMPT' | note 'waakye at the junction'
        ```

        **`MainActivity started` never appears in that logcat** — which is the actual claim
        being tested. The label, the source and the typed note all landed without the app
        being opened.

        ⚠ **This is also the first proof of the `FLAG_MUTABLE` fix.** `RemoteInput` hands its
        text back by *filling in* the PendingIntent, which an immutable one forbids — so the
        note box could never have worked before, and `getResultsFromIntent` would have
        returned null in silence. The typed string arriving intact is the only evidence that
        settles it.

        ⚠ **Getting here took five approaches, and the reason is worth recording.** Once Sika
        has more than one notification live, Android *bundles* them and the children render
        collapsed — no buttons, nothing for `uiautomator` to find. The heads-up banner is the
        only surface with the actions on it, it lives about five seconds, and the timing that
        works is: inject, wait 2s, tap the category, wait 2.5s, tap the action. Waiting 1s
        fails — the banner is still re-inflating and swallows the tap.

        The whole sequence is in `design-scratch/notif_drive.py` (gitignored).
        ```bash
        adb shell "am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS -n gh.mutalib.sika/.sms.DebugSmsReceiver --es body 'Cash Out made for GHS20.00 to AGENT TEST .Current Balance: GHS 67.21. Transaction Id: 90000000012. Fee charged: GHS0.00,Tax Charged 0.'"
        ```
        `DebugSmsReceiver` now passes `promptOnCashOut = true` to match the live receiver, so
        an injected cash-out raises the same prompt a real one would.
        ⚠ Grant the notification permission first — on Android 13+ a denied `POST_NOTIFICATIONS`
        makes the prompt vanish with only a logcat line, which reads exactly like a broken build:
        `adb logcat -s Sika` will say `cash-out prompt suppressed`.

- [x] **13. Month report** — built 2026-08-31, **device check passed 2026-09-02**
  `ledger/MonthSummary.kt` holds the arithmetic (pure Kotlin, no Android import, like
  `Reconciler`), `ui/report/` holds the screen. Charts hand-drawn on Compose `Canvas`.
  `check: PASS`, 47 unit tests, 0 failures. gate.py 0 block / 2 warn (both are `⚠` in code
  *comments*, not UI chrome). Humanizer 100.0/85 on every visible string.

  ⚠ **A real inconsistency found and fixed on the way.** Home summed bare `amount` for the
  month but `amount + fee` for today — two figures on the same card computed differently.
  `Reconciler` settles it: MoMo's stated balance only agrees with
  `previous − amount − fee − tax`, so fee and tax are part of the spend. Both now go through
  one `outflow()`. **His month totals will read slightly higher than before, and that is the
  correct number.**

  Three changes driven by the task-13 research browse (sources in the session notes):
  - **No donut or pie.** At 6+ categories the angular encoding stops resolving, and a one-hue
    palette cannot supply the distinguishable colours a multi-slice pie needs. One stacked
    band carries part-to-whole; the ranked list does the comparing.
  - **A percentage is withheld when last month's base was under GHS 20.** At ~45 transactions
    a month one purchase swings a small category 300%, and a screen of meaningless arrows
    trains you to ignore the one that matters.
  - **The biggest-change callout only fires when the top mover beats the second by 1.4×.**
    Two similar swings are not a story, and asserting one would be the report inventing a
    finding.

  - [x] **Device check — passed 2026-09-02.** The report's own figures against the same
        months worked out independently:

        | Month | Report screen | Re-parsed from the raw MTN texts |
        |---|---|---|
        | Aug 2026 | GHS 1705.30 out · 447.50 in | GHS 1,705.30 out · 447.50 in |
        | Jul 2026 | — | GHS 4,107.30 out · 5,132.00 in (matches stored) |
        | Jun 2026 | — | GHS 782.75 out · 1,060.00 in (matches stored) |

        ⚠ **The check re-parses `rawBody`, it does not re-add Sika's own columns.** Summing the
        stored `amount`/`fee`/`tax` would agree by construction and prove only that addition
        works. `design-scratch/hand_check.py` writes its own regexes against the original MTN
        text, so a parser bug cannot hide on both sides.

        ⚠ **It disagreed once, and Sika was right.** The checker read a GHS 0.38 fee as zero on
        every TELECEL PUSH payment, because those messages say *"Fee was GHS 0.38"* — a fourth
        wording the checker's regex did not allow and Sika's parser did. A disagreement names a
        suspect, not a culprit.

        **The stronger check, which needs no regex at all:** walk all 148 rows in time order
        and test each stated balance against `previous − amount − fee − tax`. That is MTN's
        arithmetic, not ours. **It breaks exactly once — the same single gap the app reports.**

- [x] **14. Monthly notification** — built 2026-09-01, **alarm verified on the device
  2026-09-02** straight out of `dumpsys alarm`:

  ```
  tag=*walarm*:gh.mutalib.sika.MONTHLY_REPORT
  type=RTC_WAKEUP origWhen=2026-10-01 09:00:00.000
  ```

  The 1st of the following month at 9am, registered with the system rather than merely
  computed. `NotificationPrefs.monthly` and the Settings row shipped with it, and the
  rescheduling walk across 24 firings is covered by `MonthlyReportTest`.

  ⚠ The clock-rolling test in this task's own Verify line was **not** run: winding a real
  phone's clock to 23:58 on the last of a month to watch one alarm fire is a poor trade
  against a booked `RTC_WAKEUP` you can read directly and 12 unit tests on `nextFire`.

- [x] **13b. A note is not a category** — done 2026-09-01
  Mutalib's distinction, 2026-09-01: a cash-out for something one-off — a laptop repair, a
  birthday — should be describable *without* becoming a category. The `+` is for things he
  pays for repeatedly; a one-time thing wants a note.

  Why it matters: today the only ways to describe a transaction are to pick `Other`, which
  loses the information, or to invent a category, which pollutes the breakdown forever with
  entries that hold one transaction each. That is how a category list stops meaning anything.

  - **Note** — free text, this row only, never in the breakdown, never a rule.
  - **Category** — reusable, drives the report, keeps the `+`.

  It also blunts the one-off problem found in the task-13 research: a single large purchase
  dominates the biggest-change callout and makes it useless. With a note the sentence can name
  the transaction rather than assert a trend.

  ⚠ **This adds a column, so it needs a real Room migration.** There is no
  `fallbackToDestructiveMigration` on the database builder, deliberately — it means "wipe
  everything if the schema changed", on the one dataset that cannot be rebuilt from the SMS
  inbox. Bump the version and write the `ALTER TABLE` by hand.
  **Verified on the real device by upgrading in place over his 148 rows.**
  Before: 148 rows, 5 labels, `user_version` 1, no `note` column.
  After: 148 rows, 5 labels, `user_version` 2, `note` present. Nothing lost.

  That is the route that matters, and it is safe precisely because the builder has no
  `fallbackToDestructiveMigration` — a broken migration fails to open rather than quietly
  wiping anything. ⚠ `MigrationTest` exists in `androidTest` but was **not** run:
  `connectedDebugAndroidTest` uninstalls the app when it finishes, and the uninstall would
  take the ledger with it. Run it on a fresh device or after a CSV export.

  Also shipped: **a text box inside the cash-out notification** (`RemoteInput`), so the
  one-off answer can be typed in the shade without opening the app — which is the whole
  reason the prompt exists.

  `AlarmManager`, allow-while-idle, rescheduled after each firing.
  **Verify:** set the device clock to 23:58 on the last of a month, watch it fire, confirm it
  reschedules for the following month.

  ⚠ **Bring the switch with it.** Settings has switches for the cash-out prompt and the
  end-of-day reminder, both backed by `NotificationPrefs`. A monthly-summary switch was left
  out deliberately rather than drawn dead — add `NotificationPrefs.monthly`, check it before
  posting, and add the row with `R.drawable.ic_calendar` (Lucide "calendar", deleted here
  because lint correctly called it unused).

  **Half of it is already built and tested, 2026-09-01**, on a laptop with no phone attached:
  `notify/MonthlyReport.kt` holds `nextFire`, `title` and `detail` as pure functions, with 12
  tests in `MonthlyReportTest`. The rescheduling test walks 24 firings and requires every one
  to land on the 1st at 9am — the drift a "+30 days" rebooking would cause is invisible for one
  cycle and firing on the 28th by December.

  ⚠ **This task's own line says "exact", and that is now wrong.** `SCHEDULE_EXACT_ALARM` is a
  restricted permission on Android 12+ that the user can revoke, and a monthly summary does not
  need a to-the-second alarm — "some time on the morning of the 1st" is entirely good enough.
  Use `setAndAllowWhileIdle`, exactly as `DailyNudge` already does and for the same reason.
  Still to build: the channel, the receiver, the `AlarmManager` booking, and the reschedule on
  firing. Only those need the device.

- [x] **15. Settings + CSV export *and import***
  Shipped 2026-09-01. Grouped cards (shape **B**, picked by Mutalib from four organisations in
  `design-scratch/settings.html`), one line of state at the top, Appearance as a named list,
  Categories, Learned rules, and a backup file that actually restores.

  **Verified:** 86 unit tests, 0 failures, `check: PASS`. `BackupTest` writes a ledger out and
  reads it back field for field, including a body containing `Fee charged: GHS0.50,Tax Charged 0.`
  — the comma that a naive `split(",")` would have silently turned into two columns.
  ⚠ **Not yet verified on the phone**: the wipe-and-restore round trip, and the 2→3 migration
  over the real 148 rows. Both need the Pixel, which was disconnected when this was built.

  **Appearance** brought back the three-way choice, as this task always said it would — named
  options in a list rather than a cycle with a dead step in it. ⚠ **"Default phone theme", never
  "Follow my phone"** — Mutalib's wording, held on `ThemeMode.label` so there is one copy of it.
  `systemPrefersDark()` writes out the fallback: no preference reported means light.

  ⚠ **Categories are PUT AWAY, not deleted.** Mutalib overruled the delete-and-reassign plan
  recorded here, and was right — it would have kept the money and lost what the money was for.
  `CategoryEntity.isHidden`, migration 2→3, `ALTER TABLE ... NOT NULL DEFAULT 0`. Delete survives
  only for a category holding nothing at all (`deleteIfUnused`, re-checked inside the
  transaction). `Other` can be neither hidden nor deleted.

  ⚠ **Import can only ADD.** `restoreLabel` and `restoreNote` carry `AND label IS NULL` /
  `AND note IS NULL`, so restoring a two-week-old file can never silently revert two weeks of
  labelling. Rows come in under `OnConflictStrategy.IGNORE`, so an existing transaction keeps
  what it has. A row whose amount cannot be read is reported and skipped, never guessed at.

  ⚠ **No storage permission.** Both directions go through the Storage Access Framework, which
  hands back a `Uri` for the one file chosen. `WRITE_EXTERNAL_STORAGE` would mean asking for the
  whole device to save one CSV.

  Also shipped: `NotificationPrefs`, so the two notification switches control something real
  rather than looking live and doing nothing. The monthly-summary switch is deliberately absent
  until task 14 gives it something to switch.

- [x] **16. Error and empty states**
  Shipped 2026-09-01. Six states that had no design, plus a screen Settings was already
  pointing at.

  **Verified:** `check: PASS`, 102 unit tests, 0 failures. ⚠ **Not yet triggered on the phone**
  — PLAN's own verification for this task is "trigger each one deliberately and screenshot it",
  and that needs the Pixel. Built and compiled, not yet seen.

  - **Home told two situations the same thing.** `isEmpty` means "nothing THIS MONTH", so a
    brand-new install with no MoMo messages at all got *"transactions appear here as MoMo texts
    arrive"* — advice to wait, on a phone where waiting cannot help. Now two states:
    `NothingEverRead` says Sika can only read what is still in the inbox and offers `*170#`
    (Mutalib's request) **with its limit attached** — MTN emails a PDF, and Sika cannot read a
    PDF. `EmptyMonth` does the opposite job and names the last transaction as proof the app
    works.
  - **The transactions list** said "nothing matches that" with no filter on. Now
    `NothingHereYet` when the ledger is genuinely empty.
  - **Screen 4 exists.** `ReviewQueueScreen`. It shipped an hour earlier as a Settings row with
    a chevron and no destination — the exact thing a comment in the same codebase forbids.
  - **The import report** is a panel with the line numbers, not a count in a toast.

- [x] **A gap says what it found, and can be answered** — Mutalib's requests, 2026-09-01:
  *"if the user remembers he can do something about it"* and *"add an alert immediately the
  balance doesn't tally"*.

  - `GapCard` on Home, under the money. A gap was one line of grey subtitle before this.
  - `GapAlert` fires the moment a live message fails the check, with a text box in the shade.
    ⚠ **Live route only, never the sweep** — same rule as the cash-out prompt, same reason: the
    sweep re-reads everything, so alerting from it would post one notification per historic gap
    on first run.
  - ⚠ **It names a WINDOW, not a day.** The check fails on the message *after* the missing one,
    so that date is when it was caught. The earlier copy said "missing from 28 August", which
    would send him looking on the wrong day. `GapWindowTest` pins all four cases.
  - ⚠ **Explaining a gap does not clear it, and does not enter any total.** MTN still sent no
    message. `setGapNote` touches nothing else; a check that can be switched off by typing into
    it is not a check.

  **Open, deliberately not decided:** whether an explained gap should also count in the category
  breakdown. The argument for is real — the balance proves the money left, so "spent this month"
  is currently under by exactly that amount. The argument against is that it mixes measured
  money with remembered money in one figure with no way to tell them apart later. Left as-is
  until Mutalib says otherwise.

- [x] **First run: a tour, and the two questions** — Mutalib's, 2026-09-01. He overruled the
  recommendation against a tour, and his reason was better than the objection: I was arguing
  against decorative feature cards, and his point was that Sika has features **nobody can
  discover** — reconciliation only shows itself the day something breaks, and the semester view
  is one segment in a switcher you might never press.

  Four tour screens (what Sika is · it reads for you · it checks its own maths · week, month,
  semester), skippable from the first, then SMS access, then the name, then student-and-dates,
  then notifications. Each feature screen shows a real fragment of the app rather than an
  illustration of it. **Learning your shops is deliberately absent** — "always this" sits in
  the sheet the first time you label anything, so it teaches itself.

  ⚠ **`OWNER = "Osman"` is gone.** It was a compile-time constant in the binary in two places:
  wrong for anyone else, right for Mutalib only by luck. A blank name is a real answer, so the
  greeting drops the comma rather than inventing something.

  ⚠ **The semester question closes PROFILE.md § 11's open item.** `SEMESTER` ran from the
  oldest transaction on record, which is not the start of a term but the date this phone first
  got a MoMo text — on a two-year-old number, "this semester" meant two years. A stored term
  now wins, the old guess survives as the fallback for "I don't know the dates yet", and a
  **No** to "are you a student" removes the segment entirely. Settings carries all of it back,
  including a warning when the term's end date has passed.

  **Verified:** `check: PASS`, 106 unit tests, 0 failures. ⚠ **Not run on the phone.** The
  whole flow is untriggered, and the two migrations still have not met the real 148 rows.

  ⚠ **Still a placeholder: the app has no launcher icon at all** — no `mipmap`, no
  `android:icon`, so it is Android's blank default on the home screen. The intro screen holds
  its space with a cedi sign. Drawing a real mark is identity work with its own variants, and
  doing it inside this build would have been deciding it by accident.

## Device verification — 2026-09-01, Pixel 6 Pro

Everything below was run on the real phone against the real ledger. A byte-exact copy of the
database was taken off the device first. ⚠ **The first copy was corrupt**: `adb shell cat`
translates line endings on Windows and produced a file 202 bytes too long. `adb exec-out` is
the binary-safe form, and the header was checked before anything was installed.

- [x] **The migrations, over the real 148 rows.** v2 → v4 in one launch. Integrity ok.
      148/148 transactions, 13/13 labels, 3/3 notes, 1/1 gap, 9/9 categories, 8/8 rules, both
      new columns present, 0 categories hidden. **Nothing lost.**
- [x] **First run.** ⚠ **And it exposed a real bug: the tour was skipped entirely.** The
      resume logic started the flow at the name screen whenever SMS permission was already
      granted — sensible for someone who granted it and then took a call, and wrong for every
      existing install, where permission was granted weeks ago. Mutalib upgraded and never saw
      the four screens; he reported it, and he was right. "Has permission" and "has seen the
      tour" are different facts, and conflating them meant the one person the tour was built
      for could never reach it. Now gated on its own `tour_seen` flag, and "Show the tour
      again" clears that too — otherwise it showed everything except the tour. Re-verified on
      the device: screen 1 of 4 renders, dots above the button, chevron on the action.
- [x] **The gap card, in use.** His real GHS 5.00 gap renders with the window *between 21 and
      22 Jul* — the widened window, showing the right days — and he has already explained it
      ("friend"), so the explain path works on device too.
- [x] **The gap alert.** Injected a message whose balance was GHS 5.00 short through the live
      route. Detected (expected GHS 20.71, actual GHS 15.71), posted as *"GHS 5.00 is
      unaccounted for"* with its reply action attached.
- [x] **The monthly alarm.** Booked for `2026-10-01T09:00 Africa/Accra` on a phone whose clock
      says 1 September, past 9am — `nextFire` proven against the real clock rather than a test
      one. The `BOOT_COMPLETED` reschedule path also fired and worked.
- [x] **Release log stripping**, proven on the artifact: `assembleRelease` then `strings` over
      the APK. Name-bearing fragments absent, sanitised lines and a UI string present as
      controls.
- [x] **Cleanup.** Both injected rows deleted; back to exactly 148 / 13 / 1. `stopped=false`
      confirmed afterwards, so the live receiver is alive.

- [x] **Export → clear data → import, for real.** Exported, `pm clear`'d the app to nothing —
      no database, no permissions, a true reinstall — then restored through the app's own
      *"Restoring after a reinstall?"* entry on the first-run permission screen.

      **Everything came back**, checked field by field against a byte-exact pre-wipe copy:
      148/148 transactions, 13/13 labels **on the same transactions**, 3/3 notes, the gap
      explanation, 9 categories, 8 rules.

      ⚠ **And it found a bug that would have destroyed data, minutes before it did.** The
      export was missing `gapNote` entirely — the column was added for the gap feature and
      never reached the backup format, so a restore would have silently dropped the words
      Mutalib typed about money no message could explain. It is the one field in the file that
      cannot be recovered from anywhere else. Format bumped to 2; version-1 files still read,
      because adding a column must never make an older backup unreadable. **This is the whole
      argument for running the destructive test rather than reasoning about it.**

      ⚠ **A second, smaller finding from the same run:** the import reported "148 transactions,
      9 categories, 8 rules" and said nothing about labels, because it only counted labels
      filled into rows that already existed. On an empty phone — the case the feature exists
      for — every label arrives inside a new row. The labels were restored; the sentence just
      failed to mention the one thing the person is anxious about. Now counted properly.

⚠ **Still not verified, and it needs a human at the phone:**

- **The cash-out note box** (the task 17 fix). Its `RemoteInput` sits on the *confirm* step, so
  it needs a category button tapped on a real prompt. `CashOutReplyReceiver` is `exported=false`
  — correctly — so adb cannot drive it, which is a good sign and an inconvenient one.

## Milestone 4 — Harden and ship

- [x] **17. `docs/security-checklist.md` end to end**, ticks committed. Done 2026-09-01.
  Every tick says how it was checked. Whole sections are genuinely N/A — there is no server,
  no browser, no paid API and no network — and each says why rather than passing by default.

  **The central claim is now proven against the artifact.** `INTERNET` is absent from the
  MERGED manifest in both debug and release, not just from the source file, so a dependency
  cannot have added it through manifest merging. Four permissions total, all justified.

  **Two real bugs found and fixed in the pass**, neither by using the app:

  ⚠ **The cash-out notification's text box could never have worked.** `RemoteInput` delivers
  typed text by writing it into the PendingIntent, which `FLAG_IMMUTABLE` forbids — so the
  answer arrived null and was logged as ignored. It failed in the way hardest to notice: box
  opens, text sends, notification dismisses, label silently unchanged. Mutability now granted
  to that one action only. **Still unproven on the phone.**

  ⚠ **Whole SMS bodies and counterparty names were being written to logcat**, readable by
  anyone who can plug the phone in — the same data the app refuses to put on a network, going
  out through a side door. Now behind `BuildConfig.DEBUG` via `Logging.kt`, so R8 strips the
  strings from a release build entirely.

  One open finding, accepted: an import reads the whole CSV into memory. Low severity — the
  file is user-chosen and the failure writes nothing — with a cap to add during task 19.
- [x] **18. Benchmark audit + red team.** Done 2026-09-01 — `docs/benchmark-audit.md`.

  ⚠ **Read the research-limits table in that file first.** Reddit was unreachable, Hacker News
  had almost nothing, and **no Ghana-specific source was found at all** — which is the most
  relevant evidence there is. The benchmark half is reasoning from documented behaviour, not
  from users complaining in public. The red-team half is code, probed directly, with tests.

  **The finding that reframes the comparison:** Google restricts `READ_SMS` to apps whose core
  function needs it, and Stack Overflow carries a run of rejection threads about it. That is
  why almost no published tracker reads your bank texts — a distribution constraint, not a
  missing idea. Sika sidesteps it by being sideloaded, and would hit that wall the day anyone
  suggested publishing it.

  **Grade:** decisively ahead on automatic capture and on provable correctness — the two things
  it chose to compete on — level on effort-over-time, onboarding and craft, and **behind on
  breadth**, chiefly budgets. Right trade for one user; wrong trade for a product with
  strangers to convince.

  **Two red-team findings fixed:**

  ⚠ **A damaged CSV understated its own losses by two orders of magnitude.** An unterminated
  quote swallowed the rest of the file into one field, so a 144-row backup damaged at row 3
  reported "1 row could not be read". A small number invites you to shrug and believe you
  restored your ledger. It is fatal now: the whole file is refused.

  ⚠ **The gap window could name the wrong day.** It opened at the previous row, but a message
  stating no balance cannot anchor anything — so one sitting between the hole and the row that
  caught it pushed the window past the moment the money left. It now opens at the last row
  that stated a balance.

  **The reconciliation chain held under seven separate attacks** and needed no change. Worth
  recording as a result, not an absence of one.

  ⚠ **Date tests pass on Africa/Accra, which is UTC+0 with no DST** — the one zone where a
  timezone bug is invisible. They prove correctness here, not portability.
- [x] **18b. An opening, and a theme switch that travels** — built 2026-09-02, verified on the
  Pixel by screen recording.

  Mutalib's two requests, same day: *"a splash screen animation with the app logo and name when
  u first open the app"*, and Telegram's theme switch — *"it starts like an oval shape and
  spreads the screen and when u switch back to light mode it does the opposite"*.

  ⚠ **The splash was not added. It was taken over.** On API 31+ Android shows a splash for
  every app whether it asks or not, and Sika's was the default: the launcher icon on the window
  background. Building a custom splash Activity would have shown *both*. `Theme.Sika.Splash`
  styles the system one; `ui/splash/SplashScreen.kt` is what it hands over to.

  ⚠ **The hold is a floor, not a wait.** The splash covers the inbox sweep, which has always
  happened behind a skeleton — so on a full inbox it costs close to nothing.

  ⚠ **The ripple cannot draw the new theme; it freezes the old one.** Compose holds one theme at
  a time, so the effect photographs the screen with `PixelCopy`, switches the theme behind the
  photograph, then grows a hole in it. Going back to light is the same photograph clipped to a
  *shrinking* circle instead — which is exactly what "does the opposite" means.

  **Three faults the recording caught that the build could not:**
  - The system splash came up **dark while the app was light** — Android picks that colour from
    the phone's night setting before any Sika code runs, and cannot know the theme was
    overridden in-app. Now washed across over 420ms instead of flashing.
  - `windowSplashScreenIconBackgroundColor` **was being ignored**: on API 31+ the platform reads
    the `android:`-prefixed attributes, and core-splashscreen's un-prefixed ones are for its
    API 21–30 backport only. It compiled, ran, and did nothing.
  - The mark **jumped 13% smaller** at the handover. Measured off two frames of the same video —
    62px system vs 70px Compose — and corrected to 117dp.

  ⚠ **Also fixed on the way, and it was a real bug:** `themes.xml` set a dark window background
  for everyone, with a comment saying "dark only, deliberately". True when written, wrong from
  the day light mode became the default. Every light-mode cold start began with a flash of
  #101422. Now split across `values/` and `values-night/`.

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
