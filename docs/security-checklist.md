# Security checklist — <PROJECT NAME>

Run this twice: once before the first deploy that anyone else can reach, and
again before launch. Tick items in a commit so the state is visible in the repo.

This sits underneath the hardening floor in the new-project pipeline Phase 5. The
floor is eight items and it is the minimum. This is the detailed sweep.

**A term used throughout:** an *environment variable* (env var) is a secret value
stored by the host (Vercel, GitHub Actions) rather than written in a file that
gets committed. Code reads it at runtime by name. The name is public, the value
is not.

## The critical seven

If time runs out, these are the ones. Each has either cost real money, leaked
real data, or taken down a real account in projects like this one.

- [ ] **No key is in the repo, and none ever was.** Rotating a leaked key is not
      enough if the old value is still in git history, where anyone can read it.
- [ ] **Every paid API has a spending ceiling.** A loop, a bot, or a bug can run
      a free-tier key into a real bill overnight.
- [ ] **Nothing secret reaches the browser.** Anything the browser downloads is
      readable by everyone, forever.
- [ ] **Every input is validated on the server.** Browser-side checks are a
      convenience for honest users and stop nobody else.
- [ ] **`DRY_RUN=1` is still the default** for anything that sends a message or
      moves money, until the moment you deliberately flip it.
- [ ] **No personal WhatsApp number is connected to an unofficial gateway.**
      This is a Sacred Rule. It cost a six-hour account restriction once already,
      with zero messages sent.
- [ ] **Other people's phone numbers and addresses are handled deliberately,**
      not just because they were easy to scrape.

## 1. Secrets and keys

- [ ] `.env` is gitignored.
      ```powershell
      git check-ignore -v .env
      ```
      No output means it is **not** ignored. Fix that before anything else.

- [ ] No `.env` file is tracked.
      ```powershell
      git ls-files | Select-String "env"
      ```
      `.env.example` is fine and should exist. `.env`, `.env.local`, and
      `.env.production` are not.

- [ ] No key was ever committed, including in old commits.
      ```powershell
      git log --all -p -S "AIza" --oneline
      ```
      Repeat for `sk-`, `sk_live`, `ghp_`, `Bearer `, and any provider prefix you
      use. A hit means the key is burned: rotate it at the provider, do not just
      delete the line.

- [ ] `.env.example` names every variable the project needs, with no real values.
- [ ] Every secret lives in the host's env settings (Vercel project settings,
      GitHub repository secrets) and nowhere else.
- [ ] Every key can be rotated without a code change. If a value is hardcoded
      anywhere, it cannot.
- [ ] Keys used by GitHub Actions are alive. A dead secret produces a failing
      workflow every night and an inbox you stop reading, which is how a real
      failure gets missed.

## 2. Money

Free tiers are not free when someone loops them.

- [ ] Every paid or metered API call has a hard ceiling: requests per minute per
      user, and a total per day.
- [ ] You know the answer to: **what does a hostile loop cost me per hour?**
      Write the number here: `<...>`
- [ ] Billing alerts are on at the provider, at a threshold you would actually
      mind paying.
- [ ] AI/LLM endpoints cap input length. A pasted book is a bill.
- [ ] Nothing expensive runs before the user is identified, if there is a login.
- [ ] Payment amounts are calculated server-side. A price that arrives from the
      browser is a price the customer chose.
- [ ] Payment webhooks verify their signature before being trusted, and are
      safe to receive twice.

## 3. Input and abuse

- [ ] Every field is validated on the server: type, length, range, allowed values.
- [ ] Request body size is capped.
- [ ] Uploads are capped by size, checked by type, and never trusted by filename.
- [ ] User text is escaped where it is rendered. If HTML is inserted anywhere,
      it is sanitised first.
      ```powershell
      Get-ChildItem -Recurse -Include *.ts,*.tsx,*.js,*.jsx,*.html | Select-String "dangerouslySetInnerHTML|innerHTML"
      ```
      Every hit needs a reason.
- [ ] Database queries use parameters, never string concatenation with user input.
- [ ] Submitting the same form twice does not create two records.
- [ ] Rate limiting exists on anything that writes, sends, or costs.

## 4. Other people's data

Applies whenever the project holds information about people who are not the user:
scraped listings, contact numbers, reviews, uploaded photos.

- [ ] You can say what personal data this project holds and why each field is
      needed. Fields nobody needs get deleted.
- [ ] Published phone numbers and addresses were already public, and there is a
      route for someone to ask to be removed. A contact link counts.
- [ ] Removal requests are honoured in the data file, not just the UI.
- [ ] No personal data sits in URLs, query strings, or analytics events.
- [ ] Logs do not record full names, numbers, tokens, or message bodies.
- [ ] There is a privacy line on the site if anything at all is collected.
- [ ] Data is exportable or backed up, so a bad deploy cannot lose it.

## 5. Auth and access

Skip if there is no login, but read item one first.

- [ ] Every endpoint that returns user data checks who is asking. A URL with an
      id in it is not authorisation.
- [ ] Changing an id in the URL returns 403, not someone else's record. Test it
      by hand, with two accounts.
- [ ] Row-level security is on if the database supports it. *Row-level security*
      means the database itself refuses to return rows that do not belong to the
      requester, even if the code forgets to filter.
- [ ] Admin routes are not protected by being unguessable.
- [ ] Sessions expire, and signing out actually invalidates.
- [ ] Password reset and magic links expire and are single use.

## 6. What reaches the browser

- [ ] No secret is in the client bundle.
      ```powershell
      Get-ChildItem -Recurse -Include *.ts,*.tsx,*.js,*.jsx | Select-String "NEXT_PUBLIC_"
      ```
      In Next.js, any variable prefixed `NEXT_PUBLIC_` is compiled into the
      JavaScript the browser downloads. Anything genuinely secret must not carry
      that prefix.

- [ ] Fetch the deployed page and search the served JavaScript for your key
      prefixes. Reading the source of the live site is what an attacker does
      first, so do it before they do.
- [ ] API calls that need a secret go through your own server route, so the key
      stays on the server.
- [ ] CORS is not `*` on anything that writes. *CORS* controls which other
      websites are allowed to call your API from a browser.
- [ ] Source maps are off in production, or you are content for the source to be
      readable.

## 7. Deploy and hosting

- [ ] HTTPS everywhere, with HTTP redirecting to it.
- [ ] `noindex` is still set if this is not launched yet, and removed the moment
      it is.
      ```powershell
      curl.exe -sI https://<domain> | Select-String "x-robots-tag"
      ```
- [ ] Preview and staging URLs are not indexed and do not hold real user data.
- [ ] Error pages do not print stack traces, file paths, or environment values.
- [ ] Security headers are set: `Content-Security-Policy`,
      `X-Content-Type-Options: nosniff`, `Referrer-Policy`.
- [ ] Dependencies have no known critical advisories.
      ```powershell
      npm audit --omit=dev
      ```
- [ ] `npm run check` passes on the commit being deployed.

## 8. Messaging channels

For anything that sends WhatsApp, SMS, or email.

- [ ] **No personal number is linked to an unofficial gateway.** Official APIs
      only. This is not negotiable and is not worth re-testing.
- [ ] `DRY_RUN=1` remains the default; flipping it is a deliberate, separate
      commit.
- [ ] Recipients are on a list you control, and there is an opt-out.
- [ ] Send volume is capped per run, so a loop cannot mass-message.
- [ ] A failed send is logged and retried with a limit, not retried forever.
- [ ] Template content is reviewed before approval, since approved templates are
      awkward to change later.

## 9. Android builds (delete this section for web-only projects)

- [ ] The signing keystore is not in the repo and is backed up somewhere off this
      laptop. Losing it means never updating the app under the same identity.
- [ ] Keystore passwords are in `local.properties` or env, and `local.properties`
      is gitignored.
- [ ] No API key is compiled into the APK. Anything in the APK can be extracted.
- [ ] `android:exported` is set deliberately on every activity, service, and
      receiver. Anything exported can be triggered by any other app.
- [ ] Cleartext HTTP traffic is disabled unless a specific endpoint needs it.
- [ ] The app requests only permissions it uses, and degrades gracefully when
      one is refused.
- [ ] Nothing sensitive is written to external storage or logged with `Log.d`.

## 10. After launch

- [ ] Uptime monitor pings the live URL and the alert reaches your phone.
- [ ] Errors are logged where you will actually look.
- [ ] API spend is checked weekly, in the Phase 7 operate pass.
- [ ] There is a contact route for someone to report a problem.

## Sign-off

| Run | Date | Commit | Items failing | Deployed anyway? |
|---|---|---|---|---|
| Pre-first-deploy | | | | |
| Pre-launch | | | | |

A failing item can ship if you decide it can. Write down which one and why, so
the next session finds a decision rather than an oversight.
