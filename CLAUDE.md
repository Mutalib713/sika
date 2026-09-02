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

```bash
./gradlew check              # THE check command: lint + unit tests. Must pass before any install.
./gradlew testDebugUnitTest  # parser golden tests only, fast loop
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # build + push to the Pixel over USB
adb logcat -s Sika           # watch the receiver fire in real time
```

Room's DAOs are tested against **real SQLite on the phone**, which `check` cannot run because it
must work with no device attached:

```bash
./gradlew connectedDebugAndroidTest   # needs the Pixel plugged in
```

The launcher icon has its own check, because nothing in the Android toolchain has an opinion
about artwork that runs off the edge of its own canvas — the build succeeds, lint passes, and
the damage only appears once it is on a phone:

```bash
python tools/icon/build_launcher_icon.py   # regenerate the drawables from tools/icon/icon_p2.py
python tools/icon/check_icon.py            # prove the art fits the safe circle and is centred
```

⚠ **`tools/icon/icon_p2.py` is the only description of the icon. Never hand-edit the generated
`ic_launcher_*.xml`.** The first version of this icon was drawn twice — once as an SVG sketch
and once by hand as vector XML — and the two silently disagreed, so the sketch that was measured
was not the drawing that shipped. Edit the geometry, regenerate, re-run the check.


## Deploy

There is no deploy. There is no server, no host, no Play Store.
"Shipping" means `./gradlew installDebug` onto the Pixel 6 Pro over USB.

## Toolchain — read before touching any build file

**AGP 8.13.2 · Kotlin 2.3.21 · KSP 2.3.11 · Gradle 9.4.1 · JVM target 17.** Every one of those is
pinned for a reason and they only work together. Measured at PLAN task 1 on 2026-08-30.

- ⚠ **This project is on AGP 8. Wird and Thrum are on AGP 9. That is deliberate.** Room needs KSP,
  and KSP does not work with AGP 9 by either route — built-in Kotlin makes KSP refuse, and turning
  it off makes AGP 9's new DSL reject the classic Kotlin plugin. Escaping needs
  `android.builtInKotlin=false` *and* `android.newDsl=false`, both already deprecated and removed
  in AGP 10. **Do not add those flags.** PROFILE.md § 7 has the full errors.
- ⚠ **Do not upgrade Gradle past 9.4.1** while on AGP 8. AGP 8 uses a Gradle internal API removed in
  9.6.0 and the build dies at plugin-apply time. (`gradle-9.5` is not a real version either — the
  releases are `9.5.0` and `9.5.1`.)
- ⚠ **`kotlin { compilerOptions { jvmTarget } }` must match `compileOptions`.** AGP 9's built-in
  Kotlin kept them in step; the classic plugin does not and defaults to the build JDK — the JBR is
  21, so without this the build fails with "Inconsistent JVM-target compatibility".
- Lint's `AndroidGradlePluginVersion` is disabled *only* because the upgrade it asks for is
  impossible. Delete that disable the day KSP supports AGP 9.

## Machine gotchas

- Android Studio, SDK and adb all work on this laptop. Use the bundled JBR as `JAVA_HOME`.
- ⚠ **`local.properties` is gitignored and must be recreated on a new machine.** Two traps, both hit
  on 2026-08-30: PowerShell's `Set-Content -Encoding utf8` writes a **BOM**, which corrupts the
  first key so Gradle reports "SDK location not found"; and a lone backslash is an **escape
  character** in a `.properties` file, so `C:\Users` silently becomes an invalid path. Write it with
  forward slashes and an escaped colon:
  `sdk.dir=C\:/Users/USER/AppData/Local/Android/Sdk`
- ~34 s incremental builds. No Avast on this machine, so **no** JKS truststore workaround and
  **no** `--max-workers=1`.
- The signing key came from the previous laptop: the first reinstall after any signing change
  needs a manual uninstall on the phone first.
- **No emulator.** SMS behaviour must be verified on the real Pixel with real messages. An emulator
  can fake an SMS, but it cannot reproduce doze, battery optimisation, or MTN's real wording.
- ⚠ **`adb shell am broadcast` CANNOT inject a real SMS.** `SMS_RECEIVED` is a protected
  broadcast — `SecurityException: not allowed to send broadcast … from uid=2000`. There is no
  emulator here either, so `adb emu sms send` is unavailable. Use the debug-only injector, which
  runs the identical ingestion path:
  ```bash
  adb shell "am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS -n gh.mutalib.sika/.sms.DebugSmsReceiver --es body 'Payment for GHS1.00 to TEST  .Current Balance: GHS 99.00. Transaction Id: 90000000001. Fee charged: GHS0.00,Tax Charged 0.'"
  ```
  ⚠ **Quote the whole `am` command for the DEVICE's shell.** Passing `--es body "..."` with only
  local quotes lets the words split, and the broadcast silently takes the second word as the
  package (`pkg=for`) instead of failing.
- ⚠⚠ **NEVER `am force-stop` the app before a live SMS test.** A force-stopped app is in
  Android's **stopped state** and receives *no broadcasts at all* until it is launched again
  (`dumpsys package … | grep stopped=`). Force-stopping to prove "the app is closed" is exactly
  what stops the receiver working, and it cost a real transaction on 2026-08-30.
  **Correct setup:** launch the app once, then press HOME. `stopped=false`, app backgrounded,
  Android free to kill the process — which is the real-world state being tested.
  ⚠ And note why the debug injector cannot catch this: `adb`'s `am broadcast` sets
  `FLAG_INCLUDE_STOPPED_PACKAGES`, so it reaches a stopped app that a real SMS never would.
  The injector proves the ledger path; only a real transaction proves delivery.
- ⚠ **`connectedDebugAndroidTest` UNINSTALLS the app when it finishes.** Standard AGP behaviour,
  and it silently undoes an earlier `installDebug`. On 2026-08-30 this cost a live-receiver test:
  the app was gone from the phone when the real transaction arrived, so nothing fired and the
  first diagnosis looked like a permissions problem. **Always run connected tests BEFORE
  `installDebug`, never after** — and re-grant the SMS permissions afterwards, because the
  uninstall takes those with it.
- ⚠ **Never pipe a long-running gradle command into `Select-Object -First N` in PowerShell.**
  `-First` closes the pipeline as soon as it has enough lines, which kills `gradlew` mid-run and
  reports exit 255 on a build that was passing. Use `Select-String` alone, or `Select-Object -Last`.
  This produced two false "test run failed" results on 2026-08-30.

## Gotchas specific to this app

- **The space after `GHS` is optional and changes within a single message.** `GHS 5.00` and
  `GHS0.50` appear in the same SMS. Every amount pattern needs `\s*`.
- ⚠ **Write the decimal point as a literal `\.` followed by digits — never a character class.**
  `[\d.]+` matches the sentence's trailing full stop too, so `Fee charged: GHS0.50.` yields
  `"0.50."`. Be precise about the cause: `\d+` *cannot* do this, because a full stop is not a
  digit — only a class containing the dot can. (The looser explanation was in this file until
  mutation testing at task 3 disproved it.)
  **And the failure is silent in Kotlin.** The Python probe crashed; the app's parser just returns
  a fee of **zero**, which reconciliation then treats as valid arithmetic.
- **Only one of the four known shapes carries a timestamp.** Dates come from Android's SMS
  timestamp, never from the body. Sacred Rule 5.
- **The inbox sweep re-reads messages it has already processed, every single time.** Without the
  dedupe on `txId`, one text becomes many rows and totals inflate silently. Sacred Rule 4.
- **`Reference:` is not the category.** In real data it is `-` or `1`. It is a hint that pre-fills
  a label, nothing more.
- MTN sometimes does not send an SMS at all. That is what reconciliation is for.
- ⚠ **`OnConflictStrategy.IGNORE` on the transaction insert is load-bearing. Never `REPLACE`.**
  Room implements REPLACE as delete-then-insert, so every inbox sweep would wipe the label
  Mutalib set, his cash-out answers and the reconciliation result — silently, on every launch.
  Proved by mutation 2026-08-30: REPLACE makes the label come back `null`.
  ⚠ And note *what did not catch it*: the row-count test still passed, because REPLACE also
  leaves exactly one row. Only `reInsertingASeenTransactionDoesNotDestroyItsLabel` failed.
- ⚠ **No `fallbackToDestructiveMigration()` on the database builder.** It means "wipe everything
  if the schema changed", on the one dataset that cannot be rebuilt from the SMS inbox.

## Sacred Rules (copied from PROFILE.md — do not reopen without Mutalib's say-so)

1. **Offline-first, always. The phone is the only place the ledger lives.** No Supabase, no sync,
   no account, no server the app depends on, no analytics, no crash-reporting SDK. `INTERNET` is
   absent from the manifest in v1, so this is enforced by Android rather than by good intentions.
   The one permitted network call is the optional Gemini insight request at v1.1 — see rule 12.
   *(Sync to Supabase was raised as "Path 2", approved, and dropped the same day when the web app
   was cut. Do not resurrect it without a new reason.)*
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
12. **The LLM never sees the ledger.** Any Gemini call sends only an aggregate summary built
    locally — category totals, percentage changes, days remaining. Never raw rows, counterparty
    names, phone numbers, transaction IDs or balances. Google's free tier may train on submitted
    content. Arithmetic insights are primary and must keep working with the LLM off or unreachable.

## Working rules

- Commit **and push** after every meaningful change. Do not wait for a green build — commit
  progress and describe honestly what state it is in.
- Never write `Co-Authored-By: Claude`, "Generated with Claude Code", or any AI attribution
  anywhere that lands in this repo.
- One task per session. Read `PROFILE.md` and `PLAN.md`, do the task, run `./gradlew check` plus
  the task's own verification, show the evidence, tick the checkbox, commit, push.
- A decision `PROFILE.md` does not answer → **stop and ask.** Do not improvise scope.
- Anything in NOT IN V1 stays out, however easy it looks.
