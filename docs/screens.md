# Screens — Sika

The full screen inventory for v1. Written before any UI exists, so the design and the build agree.

Palette and the rules that govern it: [`ui-guidelines.md`](ui-guidelines.md). Scope: PROFILE.md §4.

**Six screens, two notifications, three tabs.** Anything not listed here is not in v1.

---

## The shape of it

```
  ┌─ first run ──────────┐
  │ 0. Permission        │  once, ever
  │    ↓ backfill        │
  └──────────┬───────────┘
             ↓
  ╔══════════╧═══════════════════════════════╗
  ║  bottom nav: Home · Report · Settings    ║
  ╚══╤════════════╤══════════════╤═══════════╝
     ↓            ↓              ↓
  1. Home     3. Report      5. Settings
     │                          │
     ├→ 2. Transaction sheet    ├→ 6. Categories
     └→ 4. Review queue         └→ rules · export · import

  notifications: cash-out prompt · monthly report
```

---

## 0. First run

Shown once. Never again after the permission is granted.

**Three states, one screen.**

**a. The ask.** Plain-language explanation before the system dialog, because a permission request
with no context gets denied. Says what it does *and what it will never do*:

> Sika reads the messages MoMo already sends you and turns them into a spending record.
> It only ever reads messages from MoMo. Nothing leaves your phone — Sika has no internet access at all.

One primary button: **Allow SMS access**. One quiet link: *What Sika can't see*.

**b. Backfilling.** A progress state, not a spinner. Counts climbing:

> Reading your MoMo messages…
> **214 found · 209 recorded · 5 need a look**

**c. The result.** A summary card that earns trust immediately by showing real numbers from *his*
inbox:

> Found **209 transactions**, going back to **12 May 2026**.
> 5 messages I couldn't read — they're in Review.

Primary: **See my money**.

**Denied path:** a real screen, not a toast. Explains that without SMS access the app cannot do
anything at all, and offers a button into system settings.

---

## 1. Home — the one you open

Answers *"where am I right now."*

**Top: month header.** `August 2026` with chevrons. Tapping the label opens a month picker.

**Balance card.** Aqua `--accent`, dark text on it (never white). The single biggest number on the
screen — your current balance, taken from the most recent message. Below it, small and muted:
*as of 2:14pm today*.

**In / Out pair.** Two figures side by side, equal weight — this is the question he actually asked.

```
   IN                    OUT
   + GHS 500.00          − GHS 640.50
```

In is `--accent`. Out is plain `--text`. Sign always present. Tabular figures.

**Reconciliation strip.** *Only appears when there is a gap.* `--warn`, full width, tappable:

> ⚠ 3 transactions between 8 and 11 Aug don't add up.

This is the signature move. When it's absent the app is silently telling you the books are clean.

**Unlabelled nudge.** *Only when count > 0.* Muted, tappable, takes you into a focused
label-them-all flow rather than making you hunt:

> 12 transactions need a label

**Transaction list.** Grouped by day, newest first. Each row:

```
  MTN AIRTIME                          − GHS 10.00
  Airtime · 9:00pm                        GHS 90.57
```

Line 1: counterparty (`--text`) and amount with sign. Line 2: category chip and time (`--text-muted`)
and balance after (`--text-muted`). Unlabelled rows show a dashed *Add category* chip instead.

**Empty state:** no transactions this month — say so plainly and offer the previous month.

---

## 2. Transaction sheet

A bottom sheet, not a page. Opens on tapping any row.

- **Amount**, large, with sign and direction.
- **Counterparty**, date and time.
- **Category chips** — horizontal, the full list, with a **`+` at the end to create a new one
  inline.** No trip to Settings.
- **The learn-once toggle**, shown explicitly so the behaviour is never mysterious:
  > ☑ Always label **MTN AIRTIME** as **Airtime**
- **Details**, muted: fee, tax, balance after, MTN transaction ID.
- **The original message**, collapsed. Expandable. It is there because a money app that hides its
  source is asking to be trusted for no reason.

---

## 3. Month report

Answers *"where did it go, and am I getting worse."* This is what the 1st-of-the-month notification
opens.

- **Month selector**, same control as Home.
- **The four numbers** as the headline: In, Out, Net, Closing balance.
- **Breakdown by category** — a horizontal bar, then a list. Each row: category name, amount, share
  of total, and a bar. Sorted biggest first.
- **Against last month** — each category with its change:
  > Food GHS 240 · **↑ 39%** (+GHS 67)
- **Biggest change callout**, one sentence, plain:
  > Food went up GHS 67 this month. That's your biggest change.
- **Blind spot note**, only if unlabelled cash-outs exist:
  > GHS 200 was cashed out and never categorised. That's money this report can't explain.

Honest by design — the report says what it *doesn't* know.

**Empty state:** first month, nothing to compare against. Say that; don't render an empty chart.

---

## 4. Review queue

Reachable from Home when non-empty, and always from Settings.

Messages the parser could not read with confidence. **Never guessed, never silently dropped**
(Sacred Rule 7). Each entry shows the raw SMS and its arrival date, with two actions: **Ignore**
(not a MoMo transaction) and **Copy message** (so a new shape can be added to the parser).

**Empty state is the good state:** *Nothing needs reviewing.*

---

## 5. Settings

- **SMS access** — granted or denied, with a button into system settings.
- **Categories** → screen 6.
- **Learned rules** — every counterparty → category pair, each deletable. Deleting stops the
  auto-labelling without touching past transactions.
- **Export CSV** / **Import CSV** — the whole ledger including labels. Import restores after a
  reinstall.
- **Review queue** → screen 4, with its count.
- **About** — version, and one line stated as fact because it is verifiable in the manifest:
  > Sika has no internet permission. Your data cannot leave this phone.

---

## 6. Categories

- The nine starters: **Food · Transport · Data · Airtime · Rent · Provisions · Printing · Sent home
  · Other**.
- **`+` to add.** Name, and that's all — no colour picker, no icon picker. Every category renders in
  the same palette.
- Reorder by drag. Order drives the cash-out prompt's four quick options.
- **`Other` is protected** — no rename, no delete.
- Deleting a category moves its transactions to `Other` and says so before it happens. Losing a
  category must never lose money.

---

## Notifications

**Cash-out prompt.** Fires the moment a `CASH_OUT` message lands.

> **GHS 20.00 cashed out**
> What was it for?
> `[ Food ] [ Transport ] [ Data ] [ Choose… ]`

Four most-used categories plus an escape. Answering from the shade never opens the app.

**Monthly report.** 1st of the month.

> **August: GHS 640 out, GHS 500 in**
> Food was your biggest at GHS 240, up 39%.

Taps through to screen 3 for that month.

---

## Rules that apply to every screen

- **Amounts always in tabular figures**, always with a sign, always `GHS` before the number to match
  how MoMo writes it.
- **Never white text on `--accent`** — 1.49:1, invisible.
- **Colour never carries meaning alone.** The sign carries direction; `--warn` and `--danger` are
  reserved for things needing action.
- **Every list has a designed empty state.** No blank screens.
- Touch targets ≥ 48dp.
- Dark only in v1.
