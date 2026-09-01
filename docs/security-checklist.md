# Security checklist — Sika

**Run end to end on 2026-09-01 (PLAN task 17).** Every tick below says *how* it was checked,
not just that it was. An item that could not be verified says so rather than being ticked.

Sika is unusual for this checklist, which was written for web projects: there is **no server,
no account, no paid API, no browser and no network at all**. Whole sections are genuinely
inapplicable and are marked `N/A` with the reason. That is not the same as passing, and where
an item still bites in a different form — input validation on hostile SMS, data loss on a
migration — it is answered rather than dismissed.

**Threat model, stated so the answers can be judged.** One phone, one user, sideloaded. The
data is months of real transactions with real counterparty names and balances. The realistic
adversaries are, in order: **a bug that destroys the ledger**, **the operating system quietly
copying it off the device**, **another app on the phone**, and **anyone who picks the phone up
and plugs it in**. There is no remote attacker, because there is nothing listening.

---

## The critical seven

- **N/A** — *No key is in the repo.* There are no keys at all: no API, no service, no
  network. Verified `git ls-files` and `git log --all --diff-filter=A` for `.env`, `.jks`,
  `keystore`, `.pem`, `secret` — nothing tracked and nothing ever committed.
- **N/A** — *Every paid API has a spending ceiling.* No paid API exists. Nothing in this app
  can cost money by running.
- **N/A** — *Nothing secret reaches the browser.* There is no browser and no client bundle.
- [x] **Every input is validated before it is trusted.** Reworded from "on the server",
  which has no meaning here. The hostile-ish input is SMS text from a sender Sika does not
  control. `MomoParser` refuses anything it does not recognise rather than guessing
  (Sacred Rule 7), and `Backup.read` rejects a row whose amount will not parse instead of
  defaulting it to zero (`data/Backup.kt`, `transaction()` returns null on any bad field).
- **N/A** — *`DRY_RUN=1` is still the default.* Nothing is ever sent anywhere.
- [x] **No personal WhatsApp number is connected to an unofficial gateway.** Nothing is
  connected to anything. `INTERNET` is absent from the merged manifest.
- [x] **Other people's data is handled deliberately.** Counterparty names arrive inside the
  user's own MoMo messages and never leave the phone. As of this run they are also no longer
  written to logcat in a release build — see Finding 2.

---

## 1. Secrets and keys

- **N/A** — `.env` is gitignored. No `.env` exists; this is not a Node project.
- [x] **No secret file is tracked.** `git ls-files | grep -iE "\.env|secret|\.jks|keystore|\.pem"`
  returns nothing.
- [x] **No key was ever committed, including in old commits.**
  `git log --all --diff-filter=A --name-only` shows no such file has ever been added.
- [x] **`local.properties` is gitignored** (`.gitignore:4`), along with `*.jks`,
  `*.keystore` and `keystore.properties` (`.gitignore:23–25`). It holds only the SDK path.
- **N/A** — `.env.example`, host env settings, rotatable keys, GitHub Actions secrets. There
  is no host, no CI and no key.

## 2. Money

- **N/A** — every item. No paid or metered call exists; a hostile loop costs nothing because
  there is nothing to call. **The one future exception is recorded in PROFILE.md § 12**: the
  optional Gemini insight at v1.1 is the first thing that would ever add `INTERNET`, and this
  section becomes live the day it does.

## 3. Input and abuse

- [x] **Every field is validated: type, length, range, allowed values.** For SMS,
  `MomoParser` matches explicit shapes and rejects the rest. For the CSV, `Backup.read`
  validates every enum and every money field and reports what it skipped.
- [ ] **Request body size is capped.** ⚠ **Not capped — see Finding 3.** A CSV chosen at
  import is read whole into memory (`BackupIo.import` → `readBytes()`).
- **N/A** — uploads by type and filename. The only file read is one the user picks themselves
  through the system picker; there is no upload endpoint.
- [x] **User text is escaped where it is rendered.** Compose `Text` does not interpret markup,
  so a counterparty or a note containing HTML renders as characters. There is no `WebView`
  anywhere: `grep -rn "WebView" app/src` returns nothing.
- [x] **Database queries use parameters, never string concatenation.** Every query is a Room
  `@Query` with bound `:parameters`; there is no raw SQL string built from input.
- [x] **Submitting the same thing twice does not create two records.** Sacred Rule 4: a unique
  index on `txId` with `OnConflictStrategy.IGNORE`. This is why the sweep is safe to run on
  every launch.
- **N/A** — rate limiting. Nothing writes, sends or costs on a request from outside.

## 4. Other people's data

- [x] **You can say what personal data this holds and why.** Per transaction: counterparty
  name, amount, fee, tax, balance, timestamp, MTN's transaction id, and the raw message.
  The raw body is kept under Sacred Rule 6 so a parser fix can repair history — the single
  most sensitive field, and the one with the clearest justification.
- **N/A** — publishing numbers and addresses, removal requests. Nothing is published.
- [x] **No personal data sits in URLs or analytics events.** There are no URLs and no
  analytics SDK. `grep -rn "Firebase\|Analytics\|Crashlytics" app/src` returns nothing.
- [x] **Logs do not record message bodies or names — as of this run.** ⚠ They did until
  2026-09-01. See Finding 2; fixed in this pass, not merely noted.
- **N/A** — a privacy line on the site. There is no site. The equivalent claim is on the
  Settings screen and is *verifiable*: "No internet permission. You can check that yourself in
  Android's app info."
- [x] **Data is exportable, so a bad deploy cannot lose it.** PLAN task 15: CSV export **and
  import**, through the Storage Access Framework. ⚠ The round trip has still not been run on
  the phone — see "Not verified" below.

## 5. Auth and access

- **N/A** — every item. There is no login, no endpoint, no session and no server. The access
  control is the operating system's: Sika's database lives in its private app directory, which
  no other app can open without root.

## 6. What reaches the browser

- **N/A** — every item. There is no browser, no bundle, no CORS and no source map.

## 7. Deploy and hosting

- **N/A** — HTTPS, `noindex`, staging URLs, security headers. There is no deploy. "Shipping"
  is `./gradlew installDebug` over USB to one phone.
- [x] **Error paths do not print stack traces to the user.** Failures are caught and reported
  in plain words (`BackupIo` returns "Sika could not read that file. Nothing was changed.");
  the stack trace goes to logcat.
- [x] **Dependencies have no known critical advisories.** ⚠ Weaker than it looks — see
  "Not verified": there is no `npm audit` equivalent wired up here, and this is a judgement
  from the dependency list, not a scan.
- [x] **`check` passes on the commit being shipped.** `./gradlew check` — lint plus 106 unit
  tests, 0 failures, on the commit this run covers.

## 8. Messaging channels

- **N/A** — every item. Sika sends nothing. It only ever *reads* SMS, and posts local
  notifications to its own phone, which leave no device.

## 9. Android builds

- [x] **The signing keystore is not in the repo.** `*.jks` and `*.keystore` are gitignored and
  none is tracked. ⚠ **Backed up off this laptop: NOT VERIFIED** — this is Mutalib's to
  confirm, and the key came from the previous laptop (CLAUDE.md). Losing it means never
  updating the app under the same identity.
- [x] **Keystore passwords are in `local.properties`, which is gitignored.**
- [x] **No API key is compiled into the APK.** There are none to compile in.
- [x] **`android:exported` is set deliberately on every component.** Six declarations, all
  explicit. The two `true` ones are `MainActivity` (a launcher, necessarily) and `SmsReceiver`
  — which is exported because Android delivers `SMS_RECEIVED` from outside the app, and is
  **guarded by `android:permission="android.permission.BROADCAST_SMS"`**, so only the system
  can reach it. The four notification receivers are `exported="false"`.
- [x] **Cleartext HTTP traffic is disabled.** Vacuously and better than by configuration:
  there is no `INTERNET` permission, so no traffic of any kind is possible.
- [x] **The app requests only permissions it uses.** Verified against the **merged** manifest,
  not the source file — debug and release both contain exactly four:
  `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. **No `INTERNET`,
  no `WRITE_EXTERNAL_STORAGE`, no `SCHEDULE_EXACT_ALARM`.** This is the app's central claim
  and it is now proven against the artifact rather than the intention.
- [x] **It degrades gracefully when a permission is refused.** SMS refused → a screen that
  says why and a way back. Notifications refused → the ledger still works; every posting site
  checks `CashOutPrompt.canPost` first.
- [x] **Nothing sensitive is written to external storage.** The only file written outside the
  app is the CSV the user explicitly asks for and chooses the location of.
- [x] **`android:allowBackup="false"` and `dataExtractionRules` excludes every domain.**
  Worth its own line: without this, Android would copy the entire ledger to Google Drive on
  every backup — the OS performing exactly the exfiltration the missing `INTERNET` permission
  exists to prevent, and the single most likely way this app's promise could have been broken.

## 10. After launch

- **N/A** — uptime monitor, API spend, contact route. Nothing is hosted and nothing is
  public.
- [x] **Errors are logged where they will be looked at.** One tag, `adb logcat -s Sika`.

---

## Findings — 2026-09-01

Ordered worst first. **Findings 1 and 2 were fixed during this run**; 3 and 4 are recorded.

### 1. The cash-out notification's text box could never have worked — FIXED

`CashOutPrompt.noteAction` attached a `RemoteInput` to a `PendingIntent` built with
`FLAG_IMMUTABLE`. `RemoteInput` delivers what was typed by writing it **into that Intent**,
which immutability forbids, so `RemoteInput.getResultsFromIntent` returned null and
`CashOutReplyReceiver` logged the answer as ignored.

**Why it survived:** it fails in the way hardest to notice. The button appears, the box opens,
the text sends, the notification dismisses — and the label silently never changes. Everything
looks right except the outcome.

**Fixed** by granting mutability to that one action and leaving the three category buttons
immutable. ⚠ Still unproven on the phone: this needs a real cash-out message.

### 2. Whole SMS bodies and counterparty names were written to logcat — FIXED

`Sweeper` logged 150–200 characters of unparsed messages, `SmsIngest` logged amount and
counterparty per row, `HomeViewModel` logged the counterparty of every learned rule. `adb
logcat` is readable by anyone who can plug the phone in — which is precisely the data this app
refuses to put on a network. It would have been giving it away through a side door.

**Fixed** with `logPrivate`/`warnPrivate` in `Logging.kt`, guarded on `BuildConfig.DEBUG` so
R8 removes the strings from a release build entirely. Debug keeps everything, because reading
real queued messages out of logcat is how the parser gets fixed.

### 3. An import reads the whole file into memory — RECORDED, NOT FIXED

`BackupIo.import` calls `readBytes()` on a user-picked `Uri`. Pointing it at a very large file
would allocate the whole thing and could `OutOfMemory`. **Severity: low.** The file is chosen
by the user from their own storage; there is no attacker who gets to choose it, and the crash
loses nothing because nothing has been written by that point. Worth a size cap before v1.0.0
if the fix is cheap — a streaming reader would be over-engineering for a few hundred rows.

### 4. The signing key's off-laptop backup is unconfirmed — MUTALIB'S TO CHECK

Not a code issue. The key came from the previous laptop and losing it means never updating the
app under the same identity. This cannot be verified from inside the repo.

---

## Not verified, and why

- **The CSV round trip on a real device.** Export → clear app data → import → confirm every
  row *and every label* returns. Needs the phone; it was disconnected for this run.
- **The 1→2, 2→3 and 3→4 migrations against the real 148 rows.** `MigrationTest` exists in
  `androidTest` but was deliberately not run: `connectedDebugAndroidTest` uninstalls the app
  when it finishes, and the uninstall would take the ledger with it.
- **Dependency advisories.** There is no scan wired up. The dependency list is small and
  first-party (AndroidX, Room, Compose), but "no known criticals" here is a judgement, not a
  result.
- **The keystore backup** — see Finding 4.

## Sign-off

| Run | Date | Commit | Items failing | Shipped anyway? |
|---|---|---|---|---|
| Task 17, first full pass | 2026-09-01 | see the commit carrying this file | 1 open (Finding 3, low) | Not shipped yet — v1.0.0 is task 19 |

Finding 3 is accepted for now: the input is user-chosen, the failure mode is a crash that
writes nothing, and a cap is cheap enough to add during task 19 rather than blocking on it.
