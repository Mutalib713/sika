# Sika — PROFILE.md

**Canonical spec. Every session reads this first. Sacred Rules change only with Mutalib's explicit approval.**

Created 2026-08-30 · Phase 1 of the master-prompt pipeline · GO given 2026-08-30

---

## 1. WHAT

Sika is an Android app that reads the SMS alerts MTN MoMo already sends after every transaction,
turns each one into a row in a ledger on the phone, and answers one question: **where did the money go?**

It shows money in, money out, net and current balance for the month; breaks spending down by label;
compares this month against last; and pushes a report notification on the 1st.

Everything happens on the device. No account, no server, no internet.

## 2. WHO

One user: Mutalib. One phone: Pixel 6 Pro. One network: MTN MoMo.

**What he does today:** scrolls the SMS inbox by hand to work out where money went, and uses the
MTN MoMo app to actually transact. The MoMo app already shows a transaction list — what it does not
do is total it, split it by category, or hand over a monthly summary.

**Spending shape (established Phase 0):** almost all direct MoMo payments; cash-outs occasional.
This matters — if most spending had been cash-out-then-cash, the app would have answered nothing.

**His words:** *"it seems i spend alot and i dunno what i spent on that i sepnmt that much"*

## 3. SUCCESS METRIC

**Still running and still correct three months after install, without babysitting.**

Concretely, on 2026-12-01: every MoMo transaction since install is recorded, there are no
unreconciled balance gaps, and fewer than 10% of transactions sit unlabelled.

Downstream signs, which follow automatically if the metric holds and are *not* themselves the metric:
a monthly report contained a genuinely surprising number; he can name his biggest school spending
category; he changed something because of it.

## 4. V1 SCOPE

1. **SMS ingest** — live `BroadcastReceiver` for new messages, inbox sweep on every launch to catch
   anything the broadcast missed, full backfill of the existing inbox on first run.
2. **Parser** — the four confirmed MTN MoMo message shapes, deduplicated on MTN's transaction ID.
   Anything it cannot parse with confidence goes to a **review queue**. Never guessed, never dropped.
3. **Home screen** — this month's in / out / net / current balance, plus recent transactions.
   Tap any transaction to fix its label.
4. **Labels** — auto-applied from learned counterparty rules. Editing a label creates or updates a
   rule, so the same counterparty labels itself next time.
5. **Cash-out prompt** — when a Cash Out lands, a notification asks *"GHS X — what for?"* with a
   one-tap answer, closing the blind spot while the memory is fresh.
6. **Month report** — the four numbers, the breakdown by label, this month vs last.
7. **Monthly notification** on the 1st, delivering the report.
8. **Balance reconciliation** — verify `previous balance − amount − fee = new balance` across the
   ledger and surface any gap visibly.
9. **Settings** — permission state, list of learned rules, CSV export.
10. **Error and empty states** as first-class screens, not afterthoughts.

### v1.1 — recorded now so it is not forgotten, and not built until v1 is solid

Approved 2026-08-30. These are the web app's features, absorbed and reordered. Every one is pure
arithmetic on data v1 already collects, so all of them work offline.

11. **Budgets with 80% alerts** — a monthly limit per label, a notification when you cross 80%.
    Answers the question that comes right after *"where did it go"*: **am I overspending?**
12. **Semester ranges** — the KNUST academic calendar as date ranges, so "this semester" is a real
    period. Fits the original framing: he wants this for when he is *in school*.
13. **Insights** — short sentences generated from arithmetic. Primary path, always available.
14. **Gemini insights (optional second layer)** — open-ended questions only, governed by Sacred
    Rule 12. This is the first and only task that adds the `INTERNET` permission.

Deliberately *not* scheduled: savings goals (fuzzy on MoMo-only data — money you simply did not
spend never appears as saved), debt/IOU tracking (mostly manual entry).

**Why this is a separate milestone and not v1:** the success metric is *"still running and still
correct in three months."* Three screens has a real chance of that. Fourteen features does not, and
risk #4 is this becoming project number seven.

## 5. NOT IN V1

Explicit exclusions. No session builds these "helpfully."

- Telecel Cash, AirtelTigo Money — MTN only
- iOS or any Apple target — **physically impossible**, iOS cannot read SMS. Never revisit.
- The MTN MoMo API in any form — **no consumer endpoint exists** (verified 2026-08-30 against MTN's
  own portal: the complete API list is Collection, Disbursement, Remittance, Sandbox Provisioning;
  none return a transaction history, all lookups are by a reference ID your own code generated,
  `getBalance()` returns the *merchant* float, and production credentials require MTN KYC approval)
- Statement PDF import — the `*170#` statement is a manual audit in v1, not an app feature
- Budgets, spending limits, savings goals, semester ranges, insights — **deferred to v1.1, not
  discarded.** See the v1.1 milestone under Section 4.
- **Cloud sync, Supabase, accounts, login, any server the app depends on** — raised as Path 2,
  approved, then dropped the same day when the web app was cut. Sacred Rule 1.
- Multi-currency — GHS only
- **On-device AI models** (Gemma 3 1B via MediaPipe, Gemini Nano) — evaluated and rejected
  2026-08-30. AI is the wrong tool for parsing a *known, fixed* format: it is non-deterministic, it
  cannot be covered by golden tests, it hallucinates digits, a 529 MB model does not fit a
  ten-second broadcast window, and Gemini Nano needs Tensor G3+ which the Pixel 6 Pro is not.
  Possible v2 role: a fallback that guesses at *unrecognised* shapes and routes them to the review
  queue flagged as a guess. Never the primary parser.
- Play Store publication
- Debt tracking, bill splitting, recurring transactions
- Home screen widgets
- AI / LLM categorisation

## 6. SACRED RULES

Decisions no future session may reopen without Mutalib's explicit say-so.

1. **Offline-first, always. The phone is the only place the ledger lives.**
   *Final form, 2026-08-30. Path 2 (sync to Supabase) was approved and then dropped the same day
   when the web app was cut — with no web app there was nothing to feed, and the ledger already
   rebuilds from the SMS inbox. Do not resurrect it without a new reason.*
   No Supabase. No sync. No account, no login, no server the app depends on. No analytics, no
   crash-reporting SDK. The app must work completely with the network off, forever.
   **The one permitted network call** is the optional Gemini insight request (v1.1+), governed by
   Sacred Rule 12. `INTERNET` stays out of the manifest until that task is built — **v1 ships
   without it.**
2. **Only MoMo messages are ever read.** The app filters to the MoMo sender and ignores every other
   SMS, for any reason, forever.
3. **Reconciliation ships in v1, not later.** A money app that cannot check its own arithmetic does
   not ship. Silent wrongness is the failure mode that kills this category.
4. **Dedupe on MTN's transaction ID, always.** The inbox sweep must be safe to run infinitely.
5. **Dates come from Android's SMS timestamp, never from the message body.** Only 1 of the 4
   confirmed shapes carries a timestamp at all.
6. **The raw SMS body is stored with every row.** A parser fix must be able to reprocess history.
7. **Unparsed goes to the review queue.** Never guessed, never silently dropped.
8. **MTN only in v1.**
9. **design-studio runs before any UI work.** Colour comes from the palette picker, pinned by
   Mutalib, and is not "improved" later.
10. **No AI attribution** in any commit, PR, or repo artifact. Ever.
11. **Every task ends with a plain-words walkthrough** of what was built and how it works.
12. **The LLM never sees the ledger.** Any Gemini call sends only an aggregate summary the app
    builds locally — category totals, percentage changes, days remaining. **Never** raw rows, never
    counterparty names or phone numbers, never transaction IDs, never balances. Google's Gemini
    free tier *may use submitted content to improve their models*, so anything sent must be
    harmless if it were. Arithmetic insights are the primary path and must keep working with the
    LLM switched off, out of quota, or unreachable.

## 7. STACK & ARCHITECTURE

Chosen by Mutalib on 2026-08-30 from a three-option menu (A: native Kotlin/Compose, B: Flutter,
C: Kotlin + XML Views). **He picked A.**

The reasoning that decided it: cross-platform frameworks exist to reach iOS, and iOS physically
cannot read SMS — so Flutter's one real benefit does not apply here, while its costs (an
unmaintained SMS plugin, a second language, a bridge back to Kotlin anyway) all do.

| Piece | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Android's native language; SMS access is a native capability with no wrapper in between |
| UI | **Jetpack Compose + Material 3** | Google's current UI toolkit; matches Wird and pixel-routines, so practice compounds |
| Storage | **Room** (over SQLite) | Official layer; transactions described as Kotlin objects, no hand-written SQL |
| Live SMS | **`BroadcastReceiver`** on `SMS_RECEIVED`, declared in the manifest | App is not running; Android wakes it for a moment per message |
| History | **`ContentResolver`** query against the SMS content provider | Backfill and catch-up sweep |
| Monthly report | **`AlarmManager`** (exact, allow-while-idle), rescheduled each month | Needs a real calendar date; periodic background work is too vague for "the 1st" |
| Notifications | `NotificationManager`, two channels: cash-out prompt, monthly report | |
| Charts | **Hand-drawn on Compose `Canvas`** | One breakdown bar and one comparison. A charting library is more dependency than it is worth |
| Build | **AGP 8.13.2 · Kotlin 2.3.21 · KSP 2.3.11 · Gradle 9.4.1** · `minSdk 31` · `targetSdk 36` · `compileSdk 36` · JVM target 17 | Pixel 6 Pro shipped on API 31. **AGP 8, not 9 — see below.** Measured at PLAN task 1, not guessed |
| Distribution | **Sideloaded APK over adb** | No Play Store, therefore no review, no permissions declaration form, no privacy policy |
| Backend | **None until the sync task** | Local Room database is the source of truth; Supabase is a mirror, added last |

**Cost: GHS 0 per month. There is no infrastructure to pay for.**

#### ⚠ Why AGP 8 when Wird and Thrum are on AGP 9

Found at PLAN task 1 on 2026-08-30. Sika needs Room, Room needs KSP, and KSP cannot be made to work
with AGP 9 — both escape routes are closed, each by the other:

- **AGP 9's built-in Kotlin** → KSP refuses to configure: *"KSP is not compatible with Android
  Gradle Plugin's built-in Kotlin. Please disable by adding `android.builtInKotlin=false` … and
  apply `kotlin("android")` plugin"*
- **So disable it and apply Kotlin the classic way** → AGP 9's new DSL refuses: *"The
  `org.jetbrains.kotlin.android` plugin is not compatible with AGP's 9.0 new DSL"*

Getting through needs `android.builtInKotlin=false` **and** `android.newDsl=false` together — two
flags AGP already marks deprecated and removes in version 10. A foundation resting on two escape
hatches breaks on someone else's release schedule, and § 3 asks this app to still be correct in
three months.

Two consequences that follow, both recorded so nobody "fixes" them:

- **Gradle is pinned at 9.4.1.** AGP 8 cannot run on Gradle 9.6+ — it uses an internal API removed
  in 9.6.0 and Gradle names it. 9.4.1 is under the ceiling and already cached on this machine.
- **Lint's `AndroidGradlePluginVersion` is disabled**, uniquely among the disables, because the
  upgrade it demands is *impossible* rather than merely inconvenient. Leaving it on would fail
  `check` forever on a fix nobody can apply.

**Revisit the day KSP supports AGP 9 — and never by adding the two flags.**

### Implementation decisions

Settled 2026-08-30 so no session re-derives them.

| Decision | Choice | Why it matters |
|---|---|---|
| App structure | One Activity, Compose screens, a ViewModel each, one Repository over Room | Matches Wird; nothing exotic |
| Receiver threading | `goAsync()` + coroutine; **never** a Room write on the main thread | Android allows the receiver ~10 s; a main-thread write freezes the phone |
| Parser structure | Each message shape is its own object in a list of matchers | Task 5 *will* find new shapes. Adding one must mean adding a file, not editing a branching chain |
| Room migrations | A migration written from version 1, every schema change | Without it, every update wipes the history that is the whole point by month three |
| Backup | Export **and import** CSV, in the same task | Risk #3: labels are the only irreplaceable data. Export alone lets you *look* at them after a wipe, not recover them |
| Month boundary | Calendar month, Africa/Accra (UTC+0), explicit in code | No DST, so simple — but it must be stated, not assumed |

### Direction (design-studio, set 2026-08-30)

- **Tone:** a calm ledger, not a finance dashboard. Factual, quiet, slightly serious. It tells you
  the truth and does not nag.
- **Type pairing:** one grotesque for everything, but **amounts always in tabular figures** so
  columns line up down a list. A money app whose numbers wobble reads as untrustworthy.
- **Colour world:** aqua `#A8DCE7` on deep navy-ink `#101422`. Dark only.
- **Signature move:** **the reconciliation strip.** The app tells you when its own numbers do not
  add up. Nothing else in this category does that.

### There is no web app

Decided 2026-08-30. Sika App (web / CediSmart) is **not being rebuilt** — its code is gone
(`nonydev27/sika-app` contains only `.next/` build cache and `node_modules`, no source, and the dev
source maps carry no `sourcesContent`, so it is not recoverable). Android only.

The web product's features are not lost, they are **absorbed and reordered** — see v1.1 below. Its
own answer to ingestion was *"paste any MoMo SMS and the AI logs it"*, metered at 50 free parses a
month and ₵5/month for unlimited. The phone does that job automatically, unlimited, offline, with
four regular expressions and no model at all.

Recovered architecture, kept only as a record of what existed: 7 pages (landing, login, signup,
onboarding, dashboard, insights, settings, statement), 3 API routes (`/api/statement`,
`/api/usage`, `/api/ai/cache-status`), 8 components (`ai/ChatWidget`, `dashboard/AppTour`,
`BackgroundAnalysis`, `Sidebar`, `UsageBanner`, `landing/Hero`, `Features`, `Navbar`). Next.js +
TypeScript + the Anthropic SDK.

### Insights engine (v1.1)

**Arithmetic first, always.** Nearly everything a money coach tells a student is subtraction and a
calendar, not intelligence:

- *"You spent GHS 340 on food this month — 28% more than last month."*
- *"You're 82% through your transport budget with 11 days left."*
- *"You cashed out GHS 200 in August and never said what it was for."*
- *"At this rate you run out on the 24th."*

These are more trustworthy than an LLM's version, because arithmetic cannot invent a number.

**Gemini as the optional second layer**, for open-ended questions and phrasing only. Note it is the
**cloud Gemini API**, not on-device: Gemini Nano needs Tensor G3+ and the Pixel 6 Pro is Tensor G1.
Free tier is 1,500 requests/day and 15/minute at no cost and no card — far beyond what one person
needs — but free-tier content **may be used by Google to train their models**, which is exactly why
Sacred Rule 12 exists. The API key ships inside the APK; acceptable *only* because this app is
sideloaded to one phone and never published. If it is ever published, that key must move behind a
proxy or be removed.

## 8. DATA MODEL

Everything lives in one Room database in the app's private folder. No other app can open it.

### `transactions`

| Field | Type | Note |
|---|---|---|
| `id` | Long | auto |
| `txId` | String, **UNIQUE** | MTN's `Financial Transaction Id` / `Transaction ID` — the dedupe key |
| `occurredAt` | Long | epoch millis, from Android's SMS timestamp (Sacred Rule 5) |
| `direction` | enum | `IN` / `OUT` |
| `shape` | enum | `PAYMENT_MADE` / `PAYMENT_RECEIVED` / `CASH_OUT` / `BILL_AIRTIME` / `UNKNOWN` |
| `amount` | **Long** | **pesewas**, always positive — GHS 10.00 is `1000`. Corrected from `Double` at task 3 |
| `fee` | **Long** | pesewas. Never null: a missing fee breaks reconciliation |
| `tax` | **Long?** | pesewas. Null when the SMS writes `-`, which is **not** the same as zero |
| `counterparty` | String | "MTN AIRTIME", an agent number, a person's name |
| `reference` | String? | usually `-` or `1` in practice — a hint, not the category |
| `balanceAfter` | **Long?** | pesewas. The reconciliation anchor |
| `label` | String? | null = unlabelled |
| `labelSource` | enum | `AUTO_RULE` / `MANUAL` / `PROMPT` / `NONE` |
| `rawBody` | String | the original SMS, kept so a parser fix can reprocess (Sacred Rule 6) |
| `parsedOk` | Boolean | false = sits in the review queue |
| `reconciled` | enum | `OK` / `GAP` / `UNCHECKED` |

### `rules`

`counterparty` (UNIQUE), `label`, `createdAt`. This is the learn-once table that makes labelling
decay toward zero work.

### `categories`

`id`, `name` (UNIQUE), `sortOrder`, `isDefault`, `isProtected`.

**A table, not a hardcoded list** — decided 2026-08-30. Seeded on first run with nine starters
chosen from Mutalib's own spending and student life in Accra:

> **Food · Transport · Data · Airtime · Rent · Provisions · Printing · Sent home · Other**

- **The user adds more with a `+`**, everywhere a category can be chosen. No trip to Settings.
- **`Other` is protected** — always present, cannot be renamed or deleted, and is where everything
  unclassified lands.
- **Deleting a category reassigns its transactions to `Other`**, never deletes them. Losing a
  category must never lose money.
- The one-tap cash-out prompt shows the **four most-used** categories plus *Choose…*, because a
  notification with nine buttons is a notification nobody taps.

### Confirmed message shapes

From Mutalib's real inbox, 2026-08-30. A throwaway regex probe parsed **4 of 4**.

1. `Your payment of GHS {amt} to {payee} has been completed at {ts}. Your new balance: GHS {bal}. Fee was GHS {fee} Tax was GHS {tax}. Reference: {ref}. Financial Transaction Id: {id}.`
2. `Cash Out made for GHS{amt} to {agent}. Current Balance: GHS{bal} Financial Transaction Id: {id}. ... Fee charged: GHS{fee}.`
3. `Payment received for GHS {amt} from {sender}  Current Balance: GHS {bal} . Available Balance: GHS {bal}. Reference: {ref}. Transaction ID: {id}. TRANSACTION FEE: {fee}`
4. `Payment made for GHS {amt} to {payee} Current Balance: GHS {bal} . Available Balance: GHS {bal}. Reference: {ref}. Transaction ID: {id}. Fee charged: GHS{fee} Tax charged: {tax}.`

### ⚠ Money is a Long of pesewas, never a Double

Corrected at PLAN task 3 on 2026-08-30. This section originally said `Double`, and that was wrong
in a way that would have quietly undermined Sacred Rule 3.

Reconciliation asks whether `previous − amount − fee == new`. In binary floating point that
comparison is not reliably true even when every figure is right — `0.1 + 0.2` is
`0.30000000000000004`. Doubles would force a tolerance into the check, and a tolerance is exactly
what lets a real discrepancy hide inside it.

As integer pesewas the comparison is exact: a flagged gap is always real, and a clean row is
always genuinely clean. GHS 10.00 is `1000`.

### Parser landmines already measured — do not re-learn these

- The space after `GHS` is optional and **changes within a single message** (`GHS 5.00` … `GHS0.50`)
- Three different fee labels: `Fee was GHS`, `Fee charged:`, `TRANSACTION FEE:`
- Two different balance labels: `Your new balance:`, `Current Balance:`
- Two different ID labels: `Financial Transaction Id`, `Transaction ID`
- Amounts are followed by a full stop that a greedy pattern will swallow: `GHS0.50.` yields
  `"0.50."` and crashes a naive number conversion. This happened on the very first real run.
- `Tax was GHS -.` — a dash where a number should be
- Only shape 1 carries a timestamp; the rest carry none

## 9. INTEGRATIONS & KEYS

**None.** No API keys, no environment variables, no secrets, no `.env`.

`DRY_RUN` does not apply: nothing in this app sends a message or spends money.

The only external surface is Android permissions:

| Permission | When | Why |
|---|---|---|
| `READ_SMS` | runtime, granted once | read the inbox for backfill and catch-up |
| `RECEIVE_SMS` | runtime, granted once | wake on new messages |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | cash-out prompt, monthly report |
| `INTERNET` | **absent in v1** | makes Sacred Rule 1 an OS guarantee rather than a promise. Enters the manifest only at v1.1 task 14 (Gemini insights), and nowhere else |

## 10. CONSTRAINTS

- **Budget GHS 0**, permanently. No paid service may enter this project.
- **Build machine:** Lenovo i7-1165G7, 15.7 GB RAM. Android Studio, SDK and adb all working;
  ~34 s incremental builds. No Avast, so no JKS truststore workaround and no `--max-workers=1`.
- **Test device:** Pixel 6 Pro over USB. No emulator — SMS behaviour must be verified on real
  hardware with real messages.
- **Keystore:** signed with a key from the previous laptop; the first reinstall after any signing
  change needs a manual uninstall first.
- **Single user, single device.** No sync, no multi-device story.
- Ghana mobile-data constraints do not apply — the app never touches the network.

## 11. RISKS & OPEN QUESTIONS

### Risks

Established in Phase 0, with their mitigations already inside v1 scope.

| Risk | Mitigation | Where it lives |
|---|---|---|
| **Silent parser drift** — MTN rewords an alert and totals go quietly wrong | reconciliation + `rawBody` retention + review queue | Sacred Rules 3, 6, 7 |
| **Label rot** — corrections stop, "Other" swells, reports stop answering anything | learn-once rules table | V1 scope 4 |
| **Corrections are the only irreplaceable data** — the ledger rebuilds from the inbox, the labels do not | CSV export | V1 scope 9 |
| **Becoming project #7** alongside Wird, Thrum, pixel-routines, techdey and the portfolio | v1 held to three screens | V1 scope |

### Open questions

| Question | What resolves it |
|---|---|
| Are there MoMo message shapes beyond the four confirmed? | run the parser over the full backfill and count `UNKNOWN` rows |
| Is the `*170#` statement PDF password-protected, and does it cost anything? | request one and look — MTN's help pages return 403 to automated fetches |
| Does the inbox actually reach back three months? | observe at first backfill |
| Does the existing Play Store app already solve this well enough? | 20 minutes: install *Expense Tracker: Budget No Ads*, check its totals and its Cash Out handling |

## 12. VERIFICATION

**Check command:** `./gradlew check` — Android Lint plus unit tests. Must pass before any install
to the phone.

**QA suite:** `app/src/test/java/gh/mutalib/sika/ParserTest.kt` — golden tests. Each known SMS shape
goes in, the exact expected row comes out. Starts with the four confirmed shapes and **only ever
grows**: every new shape found in the wild gets a test *before* the parser is changed to handle it.

**Also required before any install to the phone:**

- reconciliation self-test passes over the backfilled ledger with zero unexplained gaps
- the dedupe test: running the inbox sweep three times in a row produces identical row counts
- `docs/security-checklist.md` worked end to end before the first release build
