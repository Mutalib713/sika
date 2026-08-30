# Sika

An Android app that reads MTN MoMo SMS alerts and turns them into a spending ledger on the phone.

Money in, money out, where it went, and a report on the 1st of each month.

**Offline. No account, no server, no internet permission.** One user, one phone, MTN only.

---

## Status

Phase 1 — spec written. No code yet.

- [x] Phase 0 — idea interrogated, verdict: build
- [x] Phase 1 — `PROFILE.md`
- [x] Phase 2 — `PLAN.md`
- [ ] Phase 3 — walking skeleton
- [ ] Phase 4 — build loop
- [ ] Phase 5 — harden
- [ ] Phase 6 — v1.0.0

## How it works

MoMo already texts you after every transaction. Android files every text into a shared store and
rings a bell the whole phone can hear. Sika leaves a note asking to be woken by that bell, reads
the message, pulls out the amount, fee, counterparty and balance, and writes one row.

It also sweeps the inbox on launch, so anything the bell missed still lands — and so your whole
existing history appears the day you install it.

Every message carries your balance afterwards, which means the ledger can check its own arithmetic
and tell you when it does not add up, instead of quietly showing a wrong total.

## Docs

- [`PROFILE.md`](PROFILE.md) — canonical spec. Read first.
- [`CLAUDE.md`](CLAUDE.md) — commands, gotchas, Sacred Rules.
- [`docs/`](docs/) — app flow, UI guidelines, security checklist.

## Build

```bash
./gradlew check         # lint + tests, must pass before installing
./gradlew installDebug  # build and push to the phone over USB
```

Not on the Play Store, and not going there. Sideloaded only.
