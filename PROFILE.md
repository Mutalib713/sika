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

## 5. NOT IN V1

Explicit exclusions. No session builds these "helpfully."

- Telecel Cash, AirtelTigo Money — MTN only
- iOS or any Apple target — **physically impossible**, iOS cannot read SMS. Never revisit.
- The MTN MoMo API in any form — **no consumer endpoint exists** (verified 2026-08-30 against MTN's
  own portal: the complete API list is Collection, Disbursement, Remittance, Sandbox Provisioning;
  none return a transaction history, all lookups are by a reference ID your own code generated,
  `getBalance()` returns the *merchant* float, and production credentials require MTN KYC approval)
- Statement PDF import — the `*170#` statement is a manual audit in v1, not an app feature
- Budgets, spending limits, savings goals
- Multi-currency — GHS only
- Cloud sync, accounts, login, any server whatsoever
- Play Store publication
- Debt tracking, bill splitting, recurring transactions
- Home screen widgets
- AI / LLM categorisation

## 6. SACRED RULES

Decisions no future session may reopen without Mutalib's explicit say-so.

1. **No server, ever, in v1.** Nothing leaves the phone. No analytics, no crash-reporting SDK.
   Enforced by the OS, not by discipline: the `INTERNET` permission is deliberately absent from the
   manifest, so the app *cannot* phone home even by accident.
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
| Build | Gradle / Android Studio, `minSdk 31`, `targetSdk 36` | Pixel 6 Pro shipped on API 31; no reason to support older |
| Distribution | **Sideloaded APK over adb** | No Play Store, therefore no review, no permissions declaration form, no privacy policy |
| Backend | **None** | Not cheap — absent |

**Cost: GHS 0 per month, permanently. There is no infrastructure to pay for.**

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
| `amount` | Double | GHS |
| `fee` | Double | |
| `tax` | Double? | often literally `-` in the SMS |
| `counterparty` | String | "MTN AIRTIME", an agent number, a person's name |
| `reference` | String? | usually `-` or `1` in practice — a hint, not the category |
| `balanceAfter` | Double? | the reconciliation anchor |
| `label` | String? | null = unlabelled |
| `labelSource` | enum | `AUTO_RULE` / `MANUAL` / `PROMPT` / `NONE` |
| `rawBody` | String | the original SMS, kept so a parser fix can reprocess (Sacred Rule 6) |
| `parsedOk` | Boolean | false = sits in the review queue |
| `reconciled` | enum | `OK` / `GAP` / `UNCHECKED` |

### `rules`

`counterparty` (UNIQUE), `label`, `createdAt`. This is the learn-once table that makes labelling
decay toward zero work.

### Confirmed message shapes

From Mutalib's real inbox, 2026-08-30. A throwaway regex probe parsed **4 of 4**.

1. `Your payment of GHS {amt} to {payee} has been completed at {ts}. Your new balance: GHS {bal}. Fee was GHS {fee} Tax was GHS {tax}. Reference: {ref}. Financial Transaction Id: {id}.`
2. `Cash Out made for GHS{amt} to {agent}. Current Balance: GHS{bal} Financial Transaction Id: {id}. ... Fee charged: GHS{fee}.`
3. `Payment received for GHS {amt} from {sender}  Current Balance: GHS {bal} . Available Balance: GHS {bal}. Reference: {ref}. Transaction ID: {id}. TRANSACTION FEE: {fee}`
4. `Payment made for GHS {amt} to {payee} Current Balance: GHS {bal} . Available Balance: GHS {bal}. Reference: {ref}. Transaction ID: {id}. Fee charged: GHS{fee} Tax charged: {tax}.`

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
| `INTERNET` | **deliberately absent** | makes Sacred Rule 1 an OS guarantee, not a promise |

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
