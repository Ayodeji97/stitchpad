# Integration Guide — how each service is wired in

> **Companion doc:** [third-party-services.md](./third-party-services.md) — *what* each service is and which tier we're on. This doc is *how* each is integrated, with file paths.
> **Last audited:** 2026-06-02. Paths are relative to repo root unless noted.

## Architecture in one paragraph

The KMP client (`composeApp/`) talks to Firebase via the **GitLive KMP SDK** (`dev.gitlive:firebase-*` 2.4.0), wired through **Koin** DI modules in `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/`. Anything needing server secrets or third-party APIs (AI, email, billing reconciliation) goes through **Cloud Functions** (`functions/`, TypeScript, Node 20, `europe-west1`), which the client calls as **callable functions**. Koin is started in `StitchPadApp.kt` (`initKoin`), invoked per platform from `StitchPadApplication.kt` (Android) and `iOSApp.swift` (iOS).

---

## Firebase core (Auth / Firestore / Storage / Functions)

**Client SDK + DI.** The GitLive Firebase singletons are provided in Koin:
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt` — provides `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt` — provides `FirebaseFunctions` region-scoped: `Firebase.functions("europe-west1")`.
- Modules assembled + `startKoin` in `composeApp/.../StitchPadApp.kt` (`initKoin`); platform entry: `composeApp/src/androidMain/.../StitchPadApplication.kt`, `iosApp/iosApp/iOSApp.swift`.
- Config: `composeApp/google-services.json` (Android), `iosApp/iosApp/GoogleService-Info.plist` (iOS) — both gitignored. Android plugins `com.google.gms.google-services` + `com.google.firebase.crashlytics` in `composeApp/build.gradle.kts`.

**Auth** — `feature/auth/data/FirebaseAuthRepository.kt` (signup/login/SSO/delete/reset, email-verification gate via `sendEmailVerification`/`reloadUser`/`isEmailVerified`). DI in `di/AuthModule.kt`.

**Firestore** — repositories read/write typed `@Serializable` DTOs (never `Map<String,Any?>` — iOS crashes). Examples: `core/data/repository/FirebaseUserRepository.kt`, `core/smartinfra/data/quota/FirebaseSmartUsageDocSource.kt`, `core/data/entitlement/UserDocEntitlementsProvider.kt`. Rules: `firestore.rules`.

**Storage** — brand logos / photos, with an offline outbox: `core/offline/OfflineUploadOutbox.kt`, `core/offline/OfflinePhotoStore.kt`, scheduler `core/offline/OfflineUploadScheduler.*.kt` (Android WorkManager / iOS background task).

**Functions (client side)** — `core/smartinfra/data/ai/GitLiveFunctionsCaller.kt` (`httpsCallable("smartDraftMessage")`), `feature/auth/data/GitLiveVerificationEmailSender.kt` (`httpsCallable("sendVerificationEmail")`), `feature/freemium/data/CloudFunctionsFreemiumRepository.kt` (`reconcileCustomerSlots`). All map `FirebaseFunctionsException` codes → typed `AuthError`/domain errors and re-throw `CancellationException`.

**Crashlytics (Android)** — `composeApp/src/androidMain/.../StitchPadApplication.kt` enables collection in non-debug builds; `core/logging/CrashlyticsAntilog.kt` bridges Napier → Crashlytics; `core/logging/AppLogger.kt` is the logging facade.

---

## Cloud Functions backend (`functions/`)

- Entry/exports: `functions/src/index.ts`. Runtime Node 20, `firebase-functions ^6.0.1`, `firebase-admin ^12.7.0`. All `europe-west1`.
- Deploy: `cd functions && npm run deploy` (predeploy runs lint + `tsc`); the `--only` list in `functions/package.json` must include every function. **Gotcha:** a partial deploy once silently omitted a new function — always confirm with `firebase functions:list`.

| Function | Trigger | File | Calls |
|---|---|---|---|
| `onAuthUserDeleted` | `auth.user().onDelete` | `src/index.ts` + `src/cleanup/{firestore,storage}.ts` | Admin Firestore `recursiveDelete` (allowlist: `customers,orders,goals,private`) + Storage `deleteFiles` |
| `smartDraftMessage` | `https.onCall` | `src/smart/draftMessage.ts` | **Vertex AI** (`src/smart/vertexClient.ts`) + Firestore quota txn |
| `reconcileCustomerSlots` | `https.onCall` | `src/freemium/reconcileSlots.ts` | Firestore (customer slot locking by tier/welcome window) |
| `sendVerificationEmail` | `https.onCall` | `src/auth/sendVerificationEmail.ts` | Admin Auth link + **Resend** API |

---

## Vertex AI / Gemini

- Client wrapper: `functions/src/smart/vertexClient.ts` — `new GoogleGenAI({ vertexai: true, project: 'stitchpad-30607', location: 'global' })`, model `gemini-3.1-flash-lite`, `temperature 0.7`, `maxOutputTokens 200`.
- Prompts: `functions/src/smart/promptBuilder.ts`. Quota/coins: `functions/src/smart/freeTierCounter.ts` (Lagos-timezone monthly rollover) + `types.ts`.
- Invoked only inside `smartDraftMessage`, **after** the Firestore quota reservation (so failures don't burn quota). Real Vertex errors are logged and masked to the client as `unavailable`.
- Auth: Application Default Credentials from the function runtime (no key in code).

---

## Resend (verification email)

- Send call: `functions/src/auth/sendVerificationEmail.ts` → `POST https://api.resend.com/emails` with `Authorization: Bearer ${RESEND_API_KEY}`. Secret declared via `.runWith({ secrets: ['RESEND_API_KEY'] })`, read from `process.env`.
- HTML: `functions/src/auth/verificationEmailTemplate.ts` (pure, unit-tested builder; Adire Atelier white/indigo, serif headline, logo from Firebase Storage, copy-paste fallback link).
- Server-side 60s throttle at Firestore `users/{uid}/private/emailThrottle` (released on send failure).
- Client trigger: `FirebaseAuthRepository.sendEmailVerification()` → `GitLiveVerificationEmailSender` → callable. The verify screen (`feature/auth/presentation/verifyemail/`) sends on entry + resend, and the gate routing lives in `navigation/NavGraph.kt` (`needsEmailVerification`).
- Setup secret: `firebase functions:secrets:set RESEND_API_KEY`. Logo PNG lives in Firebase Storage; gitleaks allowlist for its URL is in `.gitleaks.toml`.

---

## Cloudflare (DNS + Email Routing)

- Not referenced in app code — it's account/DNS config. Nameservers `kira/tosana.ns.cloudflare.com` set at Namecheap (Custom DNS).
- DNS zone holds: apex/`www` → Vercel (DNS-only), `resend._domainkey.send` (DKIM), `send.send` (SPF + MX → `feedback-smtp.eu-west-1.amazonses.com`), Firebase verification TXT, and Email-Routing MX (`route1/2/3.mx.cloudflare.net`).
- Email Routing rule: `support@getstitchpad.com` → personal inbox.

---

## Vercel (marketing site)

- Lives in the **separate `stitchpad-web` repo** (Astro + Tailwind), deployed to Vercel; `getstitchpad.com` apex/`www` point there via Cloudflare DNS. Not part of this repo's build.

---

## Paystack (payments — prepaid subscriptions)

StitchPad bills **prepaid** Pro/Atelier periods — a one-off Paystack transaction grants
a fixed period; there is **no auto-renew** (`subscriptionRenews` is always `false`). A
daily job downgrades users when their period ends.

### Flow

1. **Client** — `feature/freemium/presentation/upgrade/UpgradeViewModel.kt` calls the
   `initializeSubscriptionCheckout` callable via `CloudFunctionsPaymentRepository`
   (`feature/freemium/data/`), passing `tier` + `cadence` (`BillingCadence`). On success it
   emits `OpenExternalBrowser(authorizationUrl)` to open Paystack checkout in the browser.
2. **Server — `initializeSubscriptionCheckout`** (`functions/src/billing/paystackBilling.ts`)
   resolves the price from the server-owned `PRICES_KOBO` map, calls Paystack
   `transaction/initialize`, and writes a `pending` record under
   `users/{uid}/billingTransactions/{reference}`.
3. **Server — `paystackWebhook`** (HTTP, `europe-west1`) verifies the
   `x-paystack-signature` HMAC-SHA512 against `PAYSTACK_SECRET_KEY`, and on `charge.success`
   (with matching amount/tier/cadence/currency) sets `subscriptionTier`,
   `subscriptionStatus: active`, `subscriptionEndsAt`, `subscriptionRenews: false` on
   `users/{uid}`. Idempotent (no-op if already applied); mismatches mark the txn `failed`.
4. **Client** observes entitlements (`UserDocEntitlementsProvider`) and reacts to the
   server-applied upgrade (`UpgradeEvent.UpgradeDetected`) — no client-side tier writes.
5. **Server — `expirePrepaidSubscriptions`** (daily, Africa/Lagos) downgrades users whose
   `subscriptionEndsAt` has passed back to Free.

### Security model

- All `subscription*` fields and `billingTransactions` are **server-owned**
  (`firestore.rules`): clients read their own transactions but cannot write them, and
  `fieldUnchanged` blocks adding/editing/**deleting** locked fields.
- Amount, tier, and cadence are validated server-side in the webhook; the client never
  asserts what it paid.

### Deployment runbook

Secrets / env (Firebase Functions):

| Name | Required | Purpose |
|---|---|---|
| `PAYSTACK_SECRET_KEY` | yes | Paystack API calls **and** webhook HMAC verification (same key) |
| `PAYSTACK_CALLBACK_URL` | optional | return-to-app URL after checkout |

1. **Set the secret (test first):** `firebase functions:secrets:set PAYSTACK_SECRET_KEY`
   → paste the **test** key (`sk_test_…`). (Live: re-run with `sk_live_…`.)
2. **Deploy:** `cd functions && npm run deploy` (the `--only` list already includes
   `initializeSubscriptionCheckout`, `paystackWebhook`, `expirePrepaidSubscriptions`).
   This also deploys `firestore.indexes.json` only if you run `firebase deploy --only
   firestore` — deploy the index too, or `expirePrepaidSubscriptions` fails with a
   missing-index error.
3. **Register the webhook** in the Paystack dashboard (Settings → API Keys & Webhooks),
   in the **test** section first, then **live**:
   `https://europe-west1-stitchpad-30607.cloudfunctions.net/paystackWebhook`
4. **Smoke test (test mode):** from the app Upgrade screen, pay with a Paystack test card →
   confirm the webhook fires (Functions logs), `users/{uid}` flips to the paid tier, the
   `billingTransactions` record is `paid`, and the Upgrade screen reacts. Replay the webhook
   (idempotent no-op) and tamper the amount (marks `failed`, no upgrade).
5. **Go live:** swap to the `sk_live_…` secret, register the live webhook, confirm the
   settlement bank account is in the operating entity's name, then run one real low-value
   transaction.

> **Test mode needs no Paystack account activation** — full activation/compliance only
> gates live settlement.

---

## Auth providers (Google / Apple Sign-In)

- Abstraction: `feature/auth/data/SsoCredentialProvider.kt`; exchanged to Firebase in `FirebaseAuthRepository` (`GoogleAuthProvider.credential(...)`, `OAuthProvider.credential("apple.com", ...)`).
- **Android:** `feature/auth/data/AndroidSsoCredentialProvider.kt` (Credential Manager + GoogleID; web client id from `BuildConfig`, set in `composeApp/build.gradle.kts`). Apple disabled on Android.
- **iOS:** `iosApp/iosApp/GoogleSignInLauncherIos.swift`, `AppleSignInLauncherIos.swift`, bridged via `feature/auth/data/NativeGoogleSignInLauncher.kt` / `NativeAppleSignInLauncher.kt`; registered in `iOSApp.swift` **before** `doInitKoin`. URL callback handled in `iOSApp.swift`.

---

## CI / review

- `.github/workflows/ci.yml`: `secrets-scan` (gitleaks/gitleaks-action; config `.gitleaks.toml`), `detekt`, `build-android`, `build-ios`, `functions-tests` (Jest). Always run `functions` lint locally before pushing — the lint step gates `functions-tests`.
- PR review rotation: Cursor Bugbot + `codex review` (both required per [[feedback-review-rotation]]).

---

## When we add notifications (next milestone) — where things will plug in

- **Push (FCM/APNs):** add `firebase-messaging` (Android) + APNs (iOS); a `FirebaseMessagingService` (Android) and `UNUserNotificationCenter` delegate (iOS); register device tokens to Firestore (e.g. `users/{uid}/notificationTokens`); send from a new Cloud Function via Admin SDK. None exists today.
- **Email notifications:** reuse the **Resend** path — add new templates alongside `verificationEmailTemplate.ts` and a callable (or triggered) function, same `RESEND_API_KEY`.
- **In-app notifications:** likely a new Firestore collection (`users/{uid}/notifications`) with a snapshot-listener repository + MVI surface, mirroring existing patterns (e.g. `UserDocEntitlementsProvider`).
