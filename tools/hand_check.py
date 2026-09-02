"""
Checks Sika's totals against the raw MTN messages, using arithmetic Sika did not do.

    python tools/hand_check.py 2026-08

⚠ **This is the tool to reach for when a figure looks wrong.** It answers the only question
that matters in that moment: is the app misreading the messages, or is your memory of the
month wrong? Nothing else in the project answers it, because everything else in the project
shares the parser.

### Why it does not simply add up the database

Summing the stored `amount`, `fee` and `tax` columns would agree with the report every time —
Sika put those numbers there. It would prove addition works and nothing else. So this re-reads
`rawBody`, the original MTN text kept with every row (Sacred Rule 6), with regexes written
here. Two independent readings of the same messages; a parser bug cannot hide in both.

### And the check that shares nothing at all

`--chain` walks every row in time order and tests each stated balance against
`previous - amount - fee - tax`. Those balances are **MTN's own arithmetic**, printed in their
own messages. If Sika mis-read an amount anywhere, the chain breaks at that row.

A break is not automatically a bug — a message MTN never sent breaks it too, which is exactly
what the app calls a gap. The number to compare is *how many* breaks against the gap count on
the Settings screen. They should be equal.

⚠ **A disagreement names a suspect, not a culprit.** The first run of this found a GHS 0.38
difference and the app was right: TELECEL PUSH messages say *"Fee was GHS 0.38"*, a fourth
wording this script did not allow and Sika's parser did. Read the message before believing
either side.
"""
import argparse
import datetime
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb  # noqa: E402

# Africa/Accra is UTC+0 with no daylight saving, so no local time here is ambiguous.
ACCRA = datetime.timezone.utc

# ⚠ `GHS\s*` — the space after GHS is optional and changes within a single message.
# ⚠ `\d+\.\d{2}` and never a class containing the dot: `[\d.]+` swallows the sentence's own
#    full stop and turns "GHS0.50." into "0.50.".
AMOUNT = r"GHS\s*(\d+(?:,\d{3})*\.\d{2})"

OUT_WORDS = ("payment for", "cash out made", "payment made", "your payment of",
             "transfer made", "airtime", "bundle", "you have transferred", "you have paid")
IN_WORDS = ("payment received", "you have received", "cash in", "received for")


def money(text):
    return int(round(float(text.replace(",", "")) * 100))


def parse(body):
    """(direction, amount, fee, tax) in pesewas, or None if this is not a transaction."""
    low = body.lower()
    amounts = re.findall(AMOUNT, body, re.I)
    if not amounts:
        return None

    fee = 0
    # ⚠ "was" is in here because of a real miss — see the module docstring.
    m = re.search(r"fee\s*(?:charged|is|was)?\s*[:=]?\s*" + AMOUNT, body, re.I)
    if m:
        fee = money(m.group(1))
    else:
        m = re.search(r"transaction fee\s*[:=]?\s*(\d+(?:\.\d{2})?)", body, re.I)
        if m:
            fee = int(round(float(m.group(1)) * 100))

    tax = 0
    m = re.search(r"tax\s*charged\s*[:=]?\s*(?:GHS\s*)?(\d+(?:\.\d{2})?)", body, re.I)
    if m:
        tax = int(round(float(m.group(1)) * 100))

    direction = None
    if any(w in low for w in IN_WORDS):
        direction = "IN"
    if any(w in low for w in OUT_WORDS):
        direction = "OUT"
    if direction is None:
        return None
    return direction, money(amounts[0]), fee, tax


def ghs(pesewas):
    return f"GHS {pesewas / 100:,.2f}"


def compare(con, month):
    rows = con.execute(
        "select occurredAt, direction, amount, fee, tax, rawBody from transactions",
    ).fetchall()

    mine_out = mine_in = app_out = app_in = 0
    counted = skipped = disagree = 0

    for occ, d, a, f, t, body in rows:
        if datetime.datetime.fromtimestamp(occ / 1000, ACCRA).strftime("%Y-%m") != month:
            continue
        counted += 1
        # ⚠ fee and tax are nullable: a message stating no fee stores NULL, not 0, because
        # "not stated" and "nothing" are different facts.
        a, f, t = a or 0, f or 0, t or 0
        if d == "OUT":
            app_out += a + f + t
        else:
            app_in += a

        got = parse(body or "")
        if got is None:
            skipped += 1
            continue
        md, ma, mf, mt = got
        if md == "OUT":
            mine_out += ma + mf + mt
        else:
            mine_in += ma
        if (md, ma, mf) != (d, a, f):
            disagree += 1
            print(f"  ! app({d},{a},{f},{t})  vs  this script({md},{ma},{mf},{mt})")
            print(f"    {(body or '')[:120]}")

    print(f"\n{month}: {counted} transactions")
    print(f"  re-read from the messages   out {ghs(mine_out):>14}   in {ghs(mine_in):>14}")
    print(f"  as Sika stored it           out {ghs(app_out):>14}   in {ghs(app_in):>14}")
    print(f"  unreadable to this script   {skipped}")
    print(f"  rows in disagreement        {disagree}")
    ok = mine_out == app_out and mine_in == app_in and skipped == 0
    # ⚠ Plain hyphens in anything PRINTED. An em dash comes out as a replacement character on
    # a Windows console under cp1252, which makes a working tool look broken.
    print("\n" + ("MATCH" if ok else "DIFFERENT - read the messages above before believing "
                                     "either side"))
    return ok


def chain(con):
    rows = con.execute(
        "select direction, amount, fee, tax, balanceAfter from transactions "
        "where balanceAfter is not null order by occurredAt, id",
    ).fetchall()
    breaks, prev, first = 0, 0, True
    for d, a, f, t, bal in rows:
        a, f, t = a or 0, f or 0, t or 0
        if first:
            prev, first = bal, False
            continue
        expected = prev - a - f - t if d == "OUT" else prev + a
        if expected != bal:
            breaks += 1
        # ⚠ Re-anchor on what MTN said, so one hole costs one break and not every row after it.
        prev = bal
    print(f"\nbalance chain, {len(rows)} rows that state a balance: {breaks} break(s)")
    print("Compare with the gap count on the Settings screen - they should be equal.")
    print("A break is a message MTN never sent, not necessarily a misreading.")


def main():
    p = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    p.add_argument("month", nargs="?", help="YYYY-MM. Omit to run the chain check only.")
    p.add_argument("--db", help="an already-pulled sika.db, instead of reading the phone")
    adb.add_device_arg(p)
    args = p.parse_args()

    if args.db:
        path = args.db
    else:
        out = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pulled")
        path = adb.Device(args.device).pull_db(out)
        print(f"pulled the ledger to {out}")

    con = sqlite3.connect(path)
    if args.month:
        compare(con, args.month)
    else:
        months = sorted({r[0] for r in con.execute(
            "select strftime('%Y-%m', occurredAt/1000, 'unixepoch') from transactions")})
        print("months in the ledger:", ", ".join(months))
    chain(con)


if __name__ == "__main__":
    main()
