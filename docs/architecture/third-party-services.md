# Third-Party Services & Integrations

> **Audience:** maintainers. A catalogue of every external service StitchPad depends on, what it does, and which plan/tier we're on.
> **Companion doc:** [integration-guide.md](./integration-guide.md) — *how* each is wired into the code.
> **Last audited:** 2026-06-02.

StitchPad is a Kotlin Multiplatform (Android + iOS) app (`com.danzucker.stitchpad`) with a TypeScript Cloud Functions backend. Firebase project: **`stitchpad-30607`**, region **`europe-west1`**.

---

## At a glance

| Service | Category | What it does | Plan / tier we use | Cost shape |
|---|---|---|---|---|
| **Firebase** (Auth, Firestore, Storage, Functions, Crashlytics) | Backend / BaaS | Auth, database, file storage, server logic, crash reporting | **Blaze** (pay-as-you-go) — required by Cloud Functions | Usage-based; mostly within free quotas today |
| **Google Vertex AI / Gemini** | AI | Generates AI draft WhatsApp messages (`gemini-3.1-flash-lite`) | Pay-per-use (Vertex AI on the Blaze project) | Per-token |
| **Resend** | Transactional email | Sends the branded verification email | **Free tier** (~3k emails/mo, 100/day) | Free now; paid if volume grows |
| **Cloudflare** | DNS + email routing | Authoritative DNS for `getstitchpad.com`; Email Routing forwards `support@` | **Free plan** | Free |
| **Vercel** | Web hosting | Hosts the marketing site (`getstitchpad.com`) | Hobby/free (separate `stitchpad-web` repo) | Free now |
| **Namecheap** | Domain registrar | Registers `getstitchpad.com` (DNS delegated to Cloudflare) | Paid registration | ~$10–12/yr |
| **Paystack** | Payments (Nigeria) | Prepaid subscription upgrades (freemium → Pro/Atelier) | **Live infra (prepaid)** | Per-transaction fee |
| **Termly** | Legal docs | Hosts Privacy Policy + Terms | Free tier | Free |
| **Google Sign-In** | Auth provider | OAuth login (Android + iOS) | Free | Free |
| **Apple Sign-In** | Auth provider | OAuth login (iOS); mandatory App Store peer to Google | Free (needs Apple Developer Program) | — |
| **Apple Developer + App Store Connect / TestFlight** | iOS distribution | Builds, TestFlight, App Store | Apple Developer Program | $99/yr |
| **Google Play Console** | Android distribution | Internal/closed testing, Play Store | Developer account | $25 one-time |
| **GitHub + GitHub Actions** | Source + CI | Repo, PRs, CI (gitleaks, detekt, builds, functions tests) | Free tier | Free |
| **Cursor Bugbot + codex** | PR review bots | Automated code review on PRs (review rotation) | Per existing subscriptions | — |

**Not yet integrated (planned):** Firebase Cloud Messaging / push (FCM + APNs), Paystack Plans/Subscriptions (auto-renew) + renewal reminders. (Paystack prepaid checkout + webhook are live.)

---

## Firebase (Google) — **Blaze plan**

The backbone. The project is on the **Blaze (pay-as-you-go) plan** — required because Cloud Functions and Vertex AI don't run on the free Spark plan. In practice most usage still sits inside the free monthly quotas at current scale.

- **Authentication** *(free)* — email/password, Google, and Apple sign-in. Email verification gate after signup.
- **Cloud Firestore** *(usage-based)* — the database: `users/{uid}` profiles + subcollections (`customers`, `orders`, `styles`, `measurements`, `goals`), AI usage/quota docs, entitlements. `europe-west1`. Security rules in `firestore.rules`.
- **Cloud Storage** *(usage-based)* — brand logos, style/order photos, the email logo asset. ⚠️ No custom `storage.rules` in the repo (default rules) — a hardening to-do.
- **Cloud Functions** *(Blaze-only, usage-based)* — Node 20, `europe-west1`. Four deployed: `onAuthUserDeleted`, `smartDraftMessage`, `reconcileCustomerSlots`, `sendVerificationEmail`.
- **Crashlytics** *(free)* — crash reporting, **Android only** (iOS not wired).
- **Firebase Hosting** — a hosting block exists in `firebase.json` (`web/public`), but the live marketing domain resolves to **Vercel** (see below); Firebase Hosting is not the live host for the apex. Firebase Auth's email-action links do use the default `stitchpad-30607.firebaseapp.com/__/auth/action` handler.
- **Cloud Messaging (FCM)** — **NOT configured.** No `firebase-messaging` dependency, no token registration, no service worker / APNs. Prerequisite for the upcoming push-notification work.

Config files (gitignored): `composeApp/google-services.json` (Android), `iosApp/iosApp/GoogleService-Info.plist` (iOS).

---

## Google Vertex AI / Gemini — pay-per-use

Powers the **Smart "Draft message"** feature (AI-written WhatsApp messages for customers).

- SDK: `@google/genai` `^2.3.0` (backend only), model **`gemini-3.1-flash-lite`**, `vertexai: true`, project `stitchpad-30607`, location `global`.
- Called only from the `smartDraftMessage` Cloud Function; authenticated via the function runtime's Application Default Credentials (no API key).
- **Tier:** pay-per-token on the Blaze project. Cost is gated client- and server-side by a freemium quota (free: 5 drafts/mo + 30-coin welcome bonus; pro: 50/mo; atelier: unlimited).

---

## Resend — transactional email *(free tier)*

Sends the **verification email** from our own authenticated domain (replaced Firebase Auth's spam-prone default sender).

- Sends from **`StitchPad <noreply@send.getstitchpad.com>`**, reply-to `support@getstitchpad.com`.
- Called from the `sendVerificationEmail` Cloud Function via the Resend REST API (`https://api.resend.com/emails`).
- **Tier:** free (~3,000 emails/month, 100/day). Comfortable for current signup volume; revisit if we grow or add more email types.
- Domain `send.getstitchpad.com` is verified in Resend (DKIM/SPF/MX live via Cloudflare).
- Secret: `RESEND_API_KEY` (Firebase Functions secret).

See [[project-transactional-email]] memory for the full setup.

---

## Cloudflare — DNS + Email Routing *(free)*

Authoritative DNS for `getstitchpad.com` (migrated from Namecheap on 2026-06-02). Nameservers: `kira.ns.cloudflare.com`, `tosana.ns.cloudflare.com`.

- Holds all records: Vercel A/CNAME (DNS-only, not proxied), Resend DKIM/SPF/MX, Firebase verification TXT.
- **Email Routing** (free) forwards `support@getstitchpad.com` → personal inbox (replaced Namecheap free forwarding).
- **Tier:** Free plan. No proxying (the apex points straight to Vercel).

---

## Vercel — marketing site hosting

Hosts the public marketing site at `getstitchpad.com` (apex A → Vercel, `www` → Vercel). Built from the **separate `stitchpad-web` repo** (Astro + Tailwind), not this repo.

- **Tier:** Hobby/free (assumed at current traffic).

---

## Namecheap — domain registrar

Registers `getstitchpad.com`. DNS is now **delegated to Cloudflare** (Custom DNS nameservers). Namecheap's free email forwarding is no longer used (Cloudflare Email Routing took over).

- **Cost:** ~$10–12/yr registration.

---

## Paystack — payments *(prepaid subscriptions)*

The payment processor for Nigeria (freemium upgrades to Pro/Atelier).

- **Model:** **prepaid** — a one-off Paystack `transaction/initialize` grants a fixed Pro/Atelier period; **no auto-renew** (`subscriptionRenews` is always `false`). A daily `expirePrepaidSubscriptions` job downgrades expired users to Free.
- **Wiring:** callable `initializeSubscriptionCheckout` + HTTP `paystackWebhook` (signature-verified) + scheduled `expirePrepaidSubscriptions`, all in `functions/src/billing/paystackBilling.ts`. Client: `UpgradeViewModel` → `CloudFunctionsPaymentRepository`. See the **deployment runbook** in `integration-guide.md`.
- **Secrets:** `PAYSTACK_SECRET_KEY` (API + webhook HMAC); optional `PAYSTACK_CALLBACK_URL`.
- **Tier:** Paystack charges per-transaction fees (no monthly fee); **test mode is free and needs no account activation** (activation only gates live settlement).
- **Not yet:** true Paystack Plans/Subscriptions (auto-renew) and renewal reminders — both deferred (reminders are the next stacked PR).

---

## Termly — legal document hosting *(free)*

Hosts the **Privacy Policy** and **Terms of Service** (linked from the app and the marketing site).

- **Tier:** free.
- See [[reference-legal-resources]] memory for the policy URLs/IDs.

---

## Auth providers — Google & Apple Sign-In *(free)*

- **Google Sign-In** — Android (Credential Manager + GoogleID), iOS (GIDSignIn). Exchanged for a Firebase credential.
- **Apple Sign-In** — iOS only (AuthenticationServices). **Mandatory** App Store peer to Google per Guideline 4.8. Disabled on Android.

---

## Distribution & tooling

- **Apple Developer Program** *($99/yr)* — code signing, TestFlight (`pilot-tailors-ios` external group), App Store. Team `7DUJFVWF7W`.
- **Google Play Console** *($25 one-time)* — internal/closed testing, Play Store. Package `com.danzucker.stitchpad`.
- **GitHub + Actions** *(free)* — repo + CI: `secrets-scan` (gitleaks), `detekt`, `build-android`, `build-ios`, `functions-tests`.
- **Cursor Bugbot + codex review** — automated PR review (the required review rotation).
- **Fastlane** — local release automation (`fastlane android beta` / `ios beta`); CI automation is a later phase.

---

## Planned / not yet integrated

| Item | Why it matters | Status |
|---|---|---|
| **FCM (Android) + APNs (iOS)** push | Needed for **push notifications** (next milestone) | Not configured anywhere |
| **Email notifications** (beyond verification) | Reuse the Resend path for reminders/alerts | Resend wired only for verification today |
| **In-app notifications** | New surface (likely Firestore-backed) | Not built |
| **Paystack Plans/Subscriptions (auto-renew) + renewal reminders** | Auto-renew instead of manual re-pay; remind before prepaid period ends | Prepaid + webhook live; auto-renew & reminders deferred |
| **iOS Crashlytics** | Crash parity with Android | Android only |
| **`storage.rules`** | Lock down Cloud Storage (default rules now) | Hardening to-do |
