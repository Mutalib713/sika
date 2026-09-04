# Sika

An Android app that reads MTN MoMo SMS alerts and turns them into a spending ledger on the phone.

Money in, money out, where it went, and a report at the end of the month.

**Offline. No account, no server, no internet permission.** One user, one phone, MTN only.

---

## Status

**v1.1.0**, in daily use. 155 transactions on the phone it was built for.

- [x] Phase 0: idea interrogated, verdict build
- [x] Phase 1: `PROFILE.md`
- [x] Phase 2: `PLAN.md`
- [x] Phase 3: walking skeleton
- [x] Phase 4: build loop
- [x] Phase 5: harden
- [x] Phase 6: v1.0.0, then v1.1.0

## How it works

MoMo already texts you after every transaction. Android files every text into a shared store and
rings a bell the whole phone can hear. Sika leaves a note asking to be woken by that bell, reads
the message, pulls out the amount, fee, counterparty and balance, and writes one row.

It also sweeps the inbox on launch, so anything the bell missed still lands, and so your whole
existing history appears the day you install it.

Every message carries your balance afterwards, which means the ledger can check its own arithmetic
and tell you when it does not add up, instead of quietly showing a wrong total.

## What it does

**Keeps the ledger.** Four message shapes parsed, deduped on MTN's transaction ID, with the raw
text stored beside every row so a parser fix can reprocess history rather than lose it.

**Checks its own arithmetic.** Every row is tested against the balance MTN printed on the message
before it. When money has moved that no message explains, Sika says so instead of absorbing
it, and you can say what it was and file it under a category. Anything counted that way is marked
*"with no message from MTN"* on every screen that shows it, so a remembered figure never passes
as a measured one.

**Labels.** Categories you choose during setup and edit later; a keyword guess from the message
itself; and a rule it learns the second time you label the same counterparty. Cash-outs are
the one shape where MTN names an agent rather than a purchase, so those ask in the
notification shade, while you still remember.

**Reports.** By week, by month, by semester, or all of it. Semesters are named stretches you
define, and the report steps through them. A summary arrives on the 1st, a week before a semester
ends, and again once it has.

## What it cannot do

Worth knowing before you install it rather than after.

- **MTN only.** Vodafone Cash and AirtelTigo Money are not parsed.
- **No sync and no cloud backup.** The phone is the only place the ledger lives. Backup is an
  export file you keep yourself; losing the phone without one loses the ledger.
- **It only sees what MTN texts you.** Cash spent from your pocket is invisible, and a message
  MTN never sent leaves a hole reconciliation can find but not fill.
- **It asks about cash-outs, not payments.** Payments are labelled in the app or from the
  end-of-day reminder.
- **Android 12 or newer**, and no plans for iOS.
- **Not on the Play Store**, and not going there. Sideloaded only.

## Docs

- [`PROFILE.md`](PROFILE.md): canonical spec. Read first.
- [`CLAUDE.md`](CLAUDE.md): commands, gotchas, Sacred Rules.
- [`docs/`](docs/): app flow, UI guidelines, security checklist.
- [`tools/README.md`](tools/README.md): the device scripts, and which one to reach for when a
  figure looks wrong.

## Build

```bash
./gradlew check         # lint + tests, must pass before installing
./gradlew installDebug  # build and push to the phone over USB
```

Room's DAOs are tested against real SQLite on the phone, which `check` cannot run with no device
attached:

```bash
./gradlew connectedDebugAndroidTest
```

The launcher icon has its own check, because nothing in the Android toolchain has an opinion about
artwork that runs off the edge of its own canvas:

```bash
python tools/icon/build_launcher_icon.py
python tools/icon/check_icon.py
```
