"""
Fills an emulator with a plausible month, so alarms and reports have something to say.

    python tools/seed_month.py 2026-09

⚠ **It refuses to run on a real phone unless you force it, and that guard is the point.** This
writes invented transactions into the ledger. On the emulator that is the whole idea; on
Mutalib's Pixel it is pollution he would then have to find and delete one `txId` at a time.
Every seeded row uses a `95…` transaction id so `--undo` can take them all back out.

⚠ **The balances form a real chain, they are not random.** Reconciliation checks
`previous - amount - fee - tax == the balance stated in the message`, so a seed with arbitrary
balances arrives pre-broken and every screenshot afterwards is of an app reporting gaps that
were manufactured by the seeding. Each message here states the balance the one before implies.

Why an emulator at all: it is the only place a clock can be moved. Winding a real phone
forward to watch a monthly alarm fire costs its owner a day of working alarms and login codes.
"""
import argparse
import os
import sys
import time
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb  # noqa: E402

# (day of month, counterparty, amount in cedis, fee in cedis)
SPEND = [
    (2, "MELCOM GHANA", 45.00, 0.00),
    (4, "SHOPRITE ACCRA", 128.50, 0.65),
    (6, "TROTRO CARD", 12.00, 0.00),
    (9, "MTN AIRTIME", 20.00, 0.00),
    (11, "PAPAYE FAST FOOD", 63.00, 0.32),
    (14, "ECG PREPAID", 150.00, 0.75),
    (17, "MTN DATA BUNDLE", 30.00, 0.00),
    (19, "KFC OSU", 88.00, 0.44),
    (22, "UBER TRIP", 41.50, 0.21),
    (25, "MELCOM GHANA", 76.00, 0.38),
    (28, "MTN AIRTIME", 15.00, 0.00),
]
RECEIVED = [(3, "AUNTIE AMA", 400.00), (16, "KWAME MENSAH", 250.00)]

START_BALANCE = 900.00
FIRST_TXID = 95000000001


def seed(device, year, month):
    events = ([(d, "out", who, amt, fee) for d, who, amt, fee in SPEND]
              + [(d, "in", who, amt, 0.0) for d, who, amt in RECEIVED])
    events.sort()

    balance, txid = START_BALANCE, FIRST_TXID - 1
    for day, kind, who, amt, fee in events:
        txid += 1
        at = int(datetime(year, month, day, 10, 30, tzinfo=timezone.utc).timestamp() * 1000)
        if kind == "out":
            balance = round(balance - amt - fee, 2)
            body = (f"Payment for GHS{amt:.2f} to {who}  .Current Balance: GHS {balance:.2f}. "
                    f"Transaction Id: {txid}. Fee charged: GHS{fee:.2f},Tax Charged 0.")
        else:
            balance = round(balance + amt, 2)
            body = (f"Payment received for GHS {amt:.2f} from {who}  Current Balance: "
                    f"GHS {balance:.2f} . Available Balance: GHS {balance:.2f}. "
                    f"Reference: 1. Transaction ID: {txid}. TRANSACTION FEE: 0.00")
        device.broadcast(f"--es body '{body}' --el at {at}")
        time.sleep(0.7)
        print(f"  {day:2}  {kind:3}  {who:20} {amt:8.2f}   balance {balance:.2f}")

    out = sum(a + f for _, _, a, f in SPEND)
    got = sum(a for _, _, a in RECEIVED)
    print(f"\nseeded {len(events)} transactions")
    print(f"  the month should report  GHS {out:,.2f} out, GHS {got:,.2f} in")
    print(f"  closing balance          GHS {balance:,.2f}")
    # ⚠ Plain hyphens in printed text: an em dash mojibakes on a cp1252 Windows console.
    print("\nThose figures are worked out here, not read back from the app - so they are")
    print("something to check the report against rather than a copy of it.")


def undo(device):
    removed = 0
    for txid in range(FIRST_TXID, FIRST_TXID + len(SPEND) + len(RECEIVED)):
        device.broadcast(f"--es forget {txid}")
        time.sleep(0.6)
        removed += 1
    print(f"asked the app to forget {removed} seeded ids "
          f"({FIRST_TXID}…{FIRST_TXID + removed - 1})")
    print("Check `adb logcat -s Sika` for the row counts it actually deleted.")


def main():
    p = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    p.add_argument("month", help="YYYY-MM to seed")
    p.add_argument("--undo", action="store_true", help="remove the rows this tool added")
    p.add_argument("--force", action="store_true",
                   help="allow running against a real phone (it will write invented rows)")
    adb.add_device_arg(p)
    args = p.parse_args()

    device = adb.Device(args.device)
    if not device.is_emulator and not args.force:
        sys.exit(f"{device.serial} is not an emulator, and this writes invented transactions "
                 f"into the ledger.\nStart an emulator, or pass --force if you truly mean it.")

    year, month = (int(x) for x in args.month.split("-"))
    if args.undo:
        undo(device)
    else:
        seed(device, year, month)


if __name__ == "__main__":
    main()
