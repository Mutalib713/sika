# Sika — project instructions

**Read `PROFILE.md` first, every session. It is canonical. This file is the operating manual.**

Sika reads MTN MoMo SMS alerts on the phone and turns them into a spending ledger.
Android only. Offline only. One user.

---

## Talk to Mutalib like this

He is a beginner who ships real projects. Never infer his level from his output.

1. Say what a thing is and why it matters, in ordinary words, **first**.
2. Then the full technical version. Depth is fine. Depth arriving first is not.
3. Define every term the first time it appears in a session — `BroadcastReceiver`, `Room`,
   `content provider`, `dedupe`, `minSdk`. None of these are obvious.
4. Say plainly what something **cannot** do, so he is not disappointed later.
5. **End every task with a plain-words walkthrough**: the problem → how it works → the one idea →
   what I got wrong. This is Sacred Rule 11, not a nicety.

## Commands

> The Gradle project itself lands in Phase 3 (walking skeleton). Until then these are the contract,
> not yet runnable.

```bash
./gradlew check              # THE check command: lint + unit tests. Must pass before any install.
./gradlew testDebugUnitTest  # parser golden tests only, fast loop
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # build + push to the Pixel over USB
adb logcat -s Sika           # watch the receiver fire in real time
```

## Deploy

There is no deploy. There is no server, no host, no Play Store.
"Shipping" means `./gradlew installDebug` onto the Pixel 6 Pro over USB.

## Machine gotchas

- Android Studio, SDK and adb all work on this laptop. Use the bundled JBR as `JAVA_HOME`.
- ~34 s incremental builds. No Avast on this machine, so **no** JKS truststore workaround and
  **no** `--max-workers=1`.
- The signing key came from the previous laptop: the first reinstall after any signing change
  needs a manual uninstall on the phone first.
- **No emulator.** SMS behaviour must be verified on the real Pixel with real messages. An emulator
  can fake an SMS, but it cannot reproduce doze, battery optimisation, or MTN's real wording.
- `adb shell am broadcast` can inject a fake SMS for testing without waiting for a real transaction.

## Gotchas specific to this app

- **The space after `GHS` is optional and changes within a single message.** `GHS 5.00` and
  `GHS0.50` appear in the same SMS. Every amount pattern needs `\s*`.
- **A trailing full stop will be swallowed by a greedy number pattern.** `Fee charged: GHS0.50.`
  yields `"0.50."` and crashes conversion. This bit us on the very first run against real data.
- **Only one of the four known shapes carries a timestamp.** Dates come from Android's SMS
  timestamp, never from the body. Sacred Rule 5.
- **The inbox sweep re-reads messages it has already processed, every single time.** Without the
  dedupe on `txId`, one text becomes many rows and totals inflate silently. Sacred Rule 4.
- **`Reference:` is not the category.** In real data it is `-` or `1`. It is a hint that pre-fills
  a label, nothing more.
- MTN sometimes does not send an SMS at all. That is what reconciliation is for.

## Sacred Rules (copied from PROFILE.md — do not reopen without Mutalib's say-so)

1. **No server, ever, in v1.** Nothing leaves the phone. The `INTERNET` permission is deliberately
   absent from the manifest so this is enforced by Android, not by good intentions.
2. **Only MoMo messages are ever read.** Every other SMS is ignored, for any reason, forever.
3. **Reconciliation ships in v1, not later.** A money app that cannot check its own arithmetic does
   not ship.
4. **Dedupe on MTN's transaction ID, always.**
5. **Dates come from Android's SMS timestamp, never from the message body.**
6. **The raw SMS body is stored with every row**, so a parser fix can reprocess history.
7. **Unparsed goes to the review queue.** Never guessed, never silently dropped.
8. **MTN only in v1.**
9. **design-studio runs before any UI work.** Colour comes from the palette picker, pinned by
   Mutalib, and is not "improved" later.
10. **No AI attribution** in any commit, PR, or repo artifact. Ever.
11. **Every task ends with a plain-words walkthrough.**

## Working rules

- Commit **and push** after every meaningful change. Do not wait for a green build — commit
  progress and describe honestly what state it is in.
- Never write `Co-Authored-By: Claude`, "Generated with Claude Code", or any AI attribution
  anywhere that lands in this repo.
- One task per session. Read `PROFILE.md` and `PLAN.md`, do the task, run `./gradlew check` plus
  the task's own verification, show the evidence, tick the checkbox, commit, push.
- A decision `PROFILE.md` does not answer → **stop and ask.** Do not improvise scope.
- Anything in NOT IN V1 stays out, however easy it looks.
