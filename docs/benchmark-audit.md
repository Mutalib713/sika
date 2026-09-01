# Benchmark audit and red team — Sika

**PLAN task 18, run 2026-09-01.** Graded against real apps, not against "good for a side
project". Two halves: where Sika stands, then a deliberate attempt to make it lie.

⚠ **Read the research limits first.** They are severe enough to change how much weight the
benchmark half deserves.

---

## Research limits — what could and could not be reached

Being specific about this matters more than the conclusions, because a confident comparison
built on nothing is worse than an admitted gap.

| Source | Result |
|---|---|
| Hacker News (Algolia API) | Reached. **Almost nothing relevant.** One tangential result: *Show HN: TrackMyRupee — a privacy-first, manual expense tracker for India* (2026-01-18, 1 point). Searches for SMS-parsing finance apps and M-Pesa trackers returned no substantive discussion. |
| Stack Overflow (API) | Reached, and **genuinely useful** — see the Play Store finding below. |
| Reddit | **Not reached.** WebSearch is banned from reddit.com; the DuckDuckGo HTML fallback returned no usable Reddit URLs for MoMo/M-Pesa expense tracking. |
| Ghana-specific sources | **Nothing found.** This is the biggest gap: how Ghanaians actually track MoMo spending is the most relevant evidence there is, and I have none of it. |
| App store reviews | Not reached. |

**So: the competitor grading below is reasoning from documented product behaviour and from the
Play Store policy evidence, not from users complaining in public.** Treat the red-team half as
the trustworthy part of this document. It is code, probed directly, with tests attached.

---

## The finding that reframes the whole comparison

Stack Overflow has a run of questions about Play Store rejections for `READ_SMS`
([2019](https://stackoverflow.com/questions/54416513/app-release-error-even-after-removing-call-log-and-sms-permission),
score 8; [2020](https://stackoverflow.com/questions/64439006/android-google-playstore-upload-rejection-for-sms-permission-policy-violation-is)).
Google restricted `READ_SMS` and `RECEIVE_SMS` to apps whose *core* function needs them —
default SMS handlers, and a short list of declared exceptions. Financial-transaction SMS is a
permitted use case, but only through an approved declaration; the default outcome for everyone
else is rejection.

**This is why almost no published expense tracker reads your bank or MoMo texts.** It is not
that nobody thought of it. It is a distribution constraint, and Sika sidesteps it entirely by
being sideloaded onto one phone.

Two consequences, and the second is the uncomfortable one:

- Sika's automatic capture is a **real** advantage over most published competitors, not an
  imagined one.
- **It is also a ceiling.** If Sika were ever to be published, this is the wall it hits, and
  the answer would be a declaration and a review, not a code change. Worth knowing before
  anyone suggests putting it on Play.

---

## Where Sika actually stands

Graded against what the best apps in the category do, by behaviour rather than by reputation.

### Automatic capture — **ahead**
Money Manager, Wallet, Spendee and YNAB all rest on manual entry or bank-feed aggregation
(Plaid and equivalents), and no aggregator covers MTN MoMo in Ghana. Sika needs neither: the
message is already on the phone. **Nothing else in reach does this for MoMo.**

### Correctness and trust — **clearly ahead, and this is the real differentiator**
Every competitor asks to be believed. Sika checks itself: each message states a balance, so
the arithmetic is verified against MoMo's own figure on every sweep, and a discrepancy is
flagged as a specific amount over a specific window. In a category where "my totals look
wrong" is a permanent complaint, an app that *detects* its own wrongness is a different kind
of product.

Money is `Long` pesewas throughout. Apps that use floating point for money are wrong in ways
that appear at the third decimal place and never get diagnosed.

### Effort over time — **level**
The learn-once rules mean labelling decays toward zero, which is the right mechanism. The best
apps do the same thing with better matching (fuzzy merchant names, shared community rules).
Sika's is exact-match on the counterparty string, which is simpler and will miss variants of
the same shop. Not behind, not ahead.

### Reporting — **behind**
Sika has one report: a period, a chart, a breakdown, one insight. The category standard adds
budgets with alerts, trends over many months, recurring-payment detection, and net worth.
Budgets are deliberately out of v1 (PROFILE), and that is a defensible scope call — but it is
still less.

### Data ownership — **ahead, and by a wide margin**
No account, no server, no analytics, no `INTERNET` permission — verified against the merged
manifest, debug and release. Full CSV export **and import**. Most competitors hold your
history on their servers and offer export as a courtesy; several make it a paid feature.

### Onboarding — **level, as of today**
Was well behind until this week: no tour, a hardcoded name, and a bare permission dialog.
The four-screen tour plus name/student/notifications closes most of the distance. Still no
app icon at all, which the best apps have before their first screen.

### Visual craft — **level, arguably ahead of the mid-market**
A measured palette, one type family, tabular figures, real empty states, a designed dark and
light mode. Ahead of Money Manager and Wallet; behind Copilot and Monzo, which have design
teams.

### The honest summary
**Sika wins decisively on the two things it chose to compete on — automatic capture and
provable correctness — and loses on breadth.** That is the right trade for one user who wants
to know where his money went. It would be the wrong trade for a product with strangers to
convince, and the gap that would hurt most there is budgets.

---

## Red team

Method: construct sequences and files designed to make the ledger report something false.
Findings marked **PROVEN** have a test in
`app/src/test/java/gh/mutalib/sika/RedTeamFindingsTest.kt`; **ARGUED** means reasoned from
the code without a test.

### 1. A damaged CSV understated its own losses by two orders of magnitude — FIXED, PROVEN

**The attack:** a backup file where one field opens a quote and never closes it.

**What happened:** the parser swallowed every remaining line into that one field, producing a
single unreadable row. Import then reported **"1 row could not be read"** — on a 144-row file
damaged at row 3, that number is wrong by 141. Worse than a crash: a small number invites you
to shrug and carry on believing you restored your ledger.

**Fixed.** `Csv.parseChecked` now reports an unterminated quote, and `Backup.read` treats it as
fatal: *"That file is damaged… Nothing was imported."* Refusing the whole file is the honest
answer when the parser cannot tell where the rows were.

### 2. The gap window could point at the wrong day — FIXED, ARGUED

A gap is caught at the first message *stating a balance* after the hole. The window's start
was taken from the immediately preceding row — but a MoMo message that states no balance
cannot anchor anything, so if one sat in between, the window excluded the moment the money
actually left.

Given that the whole point of the window is to help someone remember what they spent, sending
them to the wrong day defeats it. `sinceMillis` now opens at the last row that stated a
balance. Not test-proven: it needs a row with a null `balanceAfter` inside a database round
trip, which is `androidTest` and needs the phone.

### 3. The reconciliation chain holds under attack — PROVEN, no fix needed

Seven probes, all passing:
- a missing message is caught, costs **exactly one** flag, and the chain re-anchors;
- hiding a hole behind a balance-less message **defers** the check to the next stated balance
  rather than swallowing it;
- money in and money out move the balance in opposite directions;
- fee and tax are each deducted exactly once;
- a null tax behaves as zero without being conflated with it;
- review-queue rows are excluded entirely — if they entered the walk, their all-zero money
  fields would anchor the balance at zero and make every later row a gap;
- two messages in the same second produce stable verdicts in either input order.

That last one matters more than it looks: Android's SMS timestamp has one-second resolution,
so ties are real, not theoretical.

### 4. Absurd amounts are refused, not wrapped — PROVEN, no fix needed
`99999999999999999999` pesewas in a CSV is rejected as unreadable rather than overflowing a
`Long`. The parser refuses what it cannot represent.

### 5. A section marker inside a quoted field cannot forge a section — PROVEN
`"[categories]"` as a counterparty stays data.

### 6. Date arithmetic holds at the boundaries — PROVEN
Weeks spanning a month end, February in a leap year and a common year, and `ALL` having no
previous period to compare against. ⚠ **These pass on Africa/Accra, which is UTC+0 with no
DST — the one place a timezone bug is invisible.** They are correct here; they are not
evidence the code would be correct elsewhere.

### 7. Import reads the whole file into memory — RECORDED, NOT FIXED
Carried over from task 17. Low severity: user-chosen file, and the crash writes nothing.

---

## What could not be checked

- **Anything needing the phone.** The migrations against the real 148 rows, the export-wipe-
  import round trip, the gap alert firing, the cash-out `RemoteInput` fix from task 17, and
  the whole first-run flow.
- **Concurrency.** The SMS receiver, the sweep and the UI can all touch Room at once. Room
  serialises writes, but this was reasoned about, not exercised.
- **The parser against a real corpus of hostile messages.** The golden tests cover the four
  known shapes; nobody has fuzzed it.
- **Everything in the research-limits table**, which is most of the outside world.
