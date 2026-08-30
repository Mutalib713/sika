# Stitch prompts — Sika

Copy-paste prompts for [stitch.withgoogle.com](https://stitch.withgoogle.com). Free with a Google
account, no waitlist.

## Read this before you start

**Stitch output is a picture, not the app.** It exports HTML, Tailwind or JSX — and Sika is Jetpack
Compose in Kotlin. So what comes out is a **visual reference to build from**, never code to paste.
That's fine and it's still worth doing: deciding what a screen looks like is the slow part, and
Stitch is fast at it.

**Spend your generations wisely.** Standard mode (Gemini Flash) gives ~350 a month. Experimental
("Thinking", Gemini 2.5 Pro) gives ~50 and produces noticeably better layouts. Explore in Flash,
then re-run **Home** and **Month report** in Thinking — those two carry the app.

**Paste prompt 0 first**, in a new project. It sets the design system for everything after it. Then
run each screen prompt as its own generation so they land side by side on the canvas.

**When a screen comes back wrong**, don't rewrite the whole prompt. Name the operation: *"make the
balance card shorter and move the In/Out pair above the transaction list"* beats *"make it cleaner."*

---

## 0 · Context — paste this first

```
I'm designing a dark-only Android app called Sika. Set this up as my design system
and keep it for every screen I ask for next.

WHAT THE APP IS
Sika reads the SMS alerts MTN Mobile Money sends after every transaction and turns
them into a spending ledger on the phone. It shows a Ghanaian university student
where their money went. It works completely offline — no account, no login, no
internet. One user, one phone.

WHO IT'S FOR
A 20-year-old university student in Accra, Ghana. Currency is Ghana cedis, written
as "GHS 240.00". They check it on a phone, often in a hurry, often outdoors.

TONE
A calm ledger, not a finance dashboard. Factual, quiet, slightly serious. It tells
you the truth and does not nag. No gradients, no glow, no 3D, no confetti, no
motivational copy, no cartoon mascots, no stock photography of people.

COLOUR — use these exact values, do not substitute
  #101422  app background
  #272B3B  cards, list rows, panels
  #323749  raised sheets and dialogs
  #FFFFFF  primary text, amounts, headings
  #9AA1B5  secondary text: dates, times, captions
  #A8DCE7  accent — primary actions, money coming in, selected state
  #333849  hairline dividers only
  #FFC857  warning
  #F2777A  danger

CRITICAL COLOUR RULE
Text on the #A8DCE7 accent must always be #101422, NEVER white. White on that aqua
is unreadable. Anything filled with the accent gets dark text.

TYPE
One clean grotesque sans throughout. Money amounts must use TABULAR FIGURES so
digits line up vertically down a list. Amounts are the most important thing on
every screen — they should be the largest, heaviest text in their row.

LAYOUT
Mobile portrait, Android, Material 3 spacing. 16dp screen margins. 8dp grid.
Generous vertical breathing room between sections. Cards with 16dp corner radius.
Touch targets at least 48dp. Bottom navigation with exactly three tabs:
Home, Report, Settings.

HOW MONEY IS SHOWN — this matters more than anything else
Direction is carried by a + or − sign, never by colour alone.
  Money out:  plain white text, minus sign      − GHS 640.50
  Money in:   accent aqua text, plus sign       + GHS 500.00
Do not colour outgoing transactions red. Most transactions are outgoing and a
screen full of red reads as an alarm. Reserve #FFC857 and #F2777A strictly for
things that need action.

Confirm you've got this, then wait for my first screen.
```

---

## 1 · Home

```
Design the Home screen for Sika, using the system above.

Top to bottom:
1. A month header reading "August 2026" with left/right chevrons to change month.
2. A balance card filled with the accent #A8DCE7, dark #101422 text on it. One very
   large number: "GHS 179.29". Small label above: "Current balance". Small muted
   line below: "as of 2:14pm today".
3. Two figures side by side, equal weight, on the dark background — labelled IN and
   OUT. IN reads "+ GHS 500.00" in accent aqua. OUT reads "− GHS 640.50" in white.
4. A full-width warning strip in #FFC857 with a warning icon, tappable, reading
   "3 transactions between 8 and 11 Aug don't add up".
5. A muted tappable line: "12 transactions need a label".
6. A transaction list grouped under day headers ("TODAY", "YESTERDAY",
   "MON 26 AUG"). Each row is a card in #272B3B with two lines:
   - line 1: counterparty name in white on the left, amount with sign on the right
   - line 2: a small category chip and the time in #9AA1B5 on the left, and the
     balance after the transaction in #9AA1B5 on the right
   Use these real examples:
     MTN AIRTIME · Airtime · 9:00pm · − GHS 10.00 · GHS 90.57
     Aaa · Sent home · 4:12pm · + GHS 100.00 · GHS 179.29
     Cash Out · no category yet · 11:40am · − GHS 20.00 · GHS 9.79
   The third row has no category — show a dashed outline chip reading "Add category".
7. Bottom navigation: Home (selected), Report, Settings.
```

---

## 2 · Transaction detail sheet

```
Design a bottom sheet for Sika that slides up when a transaction row is tapped.
Background #323749, rounded top corners, a drag handle.

Contents:
- The amount, very large: "− GHS 10.00"
- Below it: "MTN AIRTIME" and "Tue 10 June 2026, 9:00pm" in #9AA1B5
- A horizontal row of category chips: Food, Transport, Data, Airtime, Rent,
  Provisions, Printing, Sent home, Other. "Airtime" is selected — filled with
  #A8DCE7 with dark #101422 text. The others are outlined. At the end of the row,
  a circular "+" chip for creating a new category.
- A checkbox row: "Always label MTN AIRTIME as Airtime"
- A muted details block, label on the left and value on the right:
  Fee GHS 0.00 · Tax — · Balance after GHS 90.57 · Transaction ID 83077174642
- A collapsed expandable row at the bottom: "Show original message"
```

---

## 3 · Month report

```
Design the Month report screen for Sika, using the system above.
This is the screen a monthly notification opens.

1. Month header "August 2026" with chevrons.
2. Four stat figures in a 2x2 grid: IN "+ GHS 500.00", OUT "− GHS 640.50",
   NET "− GHS 140.50", CLOSING BALANCE "GHS 179.29". Labels small and muted above
   each number.
3. A horizontal stacked bar showing the split of spending across categories, using
   tints and shades of the accent #A8DCE7 only — no rainbow of unrelated hues.
4. A category list under it, biggest first. Each row: category name, amount, share
   as a percentage, a thin progress bar, and the change against last month shown as
   an up or down arrow with a percentage. Real examples:
     Food        GHS 240.00   38%   ↑ 39%
     Transport   GHS 120.00   19%   ↓ 8%
     Airtime     GHS  60.00    9%   ↑ 12%
5. A callout card: "Food went up GHS 67 this month. That's your biggest change."
6. A muted honesty note at the bottom in #FFC857 text:
   "GHS 200 was cashed out and never categorised. This report can't explain it."
7. Bottom navigation with Report selected.
```

---

## 4 · First run and permission

```
Design a first-run permission screen for Sika, using the system above.
Single screen, centred, generous space, no illustration of people.

- A short headline: "Sika reads your MoMo messages"
- Three short lines with small icons, stacked:
    Only messages from MoMo. Nothing else is ever read.
    Nothing leaves your phone. Sika has no internet access.
    Your whole history appears straight away.
- A large primary button filled with #A8DCE7 with dark #101422 text:
  "Allow SMS access"
- A quiet text link below it: "What Sika can't see"

Then design a second version of the same screen showing progress after permission
is granted: the headline becomes "Reading your MoMo messages…", a thin accent
progress bar, and three counters: "214 found · 209 recorded · 5 need a look".
```

---

## 5 · Settings

```
Design the Settings screen for Sika, using the system above.
Grouped list rows on #272B3B cards, section labels in #9AA1B5 caps.

DATA
  SMS access — right-hand value "Granted" in accent aqua
  Categories — right-hand value "9"
  Learned rules — right-hand value "14"
  Review queue — right-hand value "5" with a #FFC857 dot

BACKUP
  Export CSV
  Import CSV

ABOUT
  Version 1.0.0
  A muted paragraph, not a row:
  "Sika has no internet permission. Your data cannot leave this phone."

Bottom navigation with Settings selected.
```

---

## 6 · Categories

```
Design the Categories screen for Sika, using the system above.

A reorderable list of category rows on #272B3B cards, each with a drag handle on
the left, the name in white, and a delete icon on the right in #9AA1B5:
Food, Transport, Data, Airtime, Rent, Provisions, Printing, Sent home, Other.

"Other" has no delete icon and shows a small muted label "always kept".

A floating action button in #A8DCE7 with a dark #101422 plus icon, bottom right.

Also design the add-category dialog it opens: a small card on #323749 with the
title "New category", one text field, and Cancel / Add buttons. Add is filled
accent with dark text.
```

---

## 7 · Empty and error states

```
Design four empty states for Sika as a set, using the system above. Each is a
centred short headline, one muted supporting line, and at most one button. No
illustrations of people, no cartoon graphics — a single simple line icon at most.

1. "Nothing yet this month" / "Transactions appear here as MoMo texts arrive."
   Button: "See July instead"
2. "Nothing needs reviewing" / "Every MoMo message so far has been read correctly."
   No button. This is the good state — make it feel calm, not empty.
3. "Sika can't see your messages" / "Without SMS access there's nothing to track."
   Button filled accent: "Open settings"
4. "Not enough history yet" / "Come back next month and Sika can compare."
   No button.
```

---

## After Stitch

The output is a reference. PLAN.md task 10 still applies in full: the design-studio pass, then the
screens rebuilt in Compose, then all the gates —

```bash
python C:/Users/USER/.claude/skills/design-studio/scripts/gate.py <files>
```

```bash
python C:/Users/USER/.claude/skills/humanizer/scripts/gate.py <file> --profile ux-microcopy
```

impeccable's detector does not read `.kt`, so gate 3 does not apply here. Say so; don't fake a pass.
