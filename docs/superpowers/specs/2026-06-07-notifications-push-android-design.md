# Notifications — Slice 3 (V1): Android Daily-Summary Push (design)

> **Date:** 2026-06-07
> **Status:** approved design, pre-implementation
> **Slice:** 3 of 3 in the notifications feature (email ✅ → in-app inbox ✅ → **push**)
> **Scope of THIS spec:** Android FCM only. iOS APNs is a deliberate fast-follow (separate spec).
> **Builds on:** Slice 1 (daily email digest, PR #123) + Slice 2 (in-app inbox, PR #126 + #130)

## Context

Slices 1 and 2 shipped a daily morning scan (`runDailyDigest`, ~07:00 Africa/Lagos) that, per
tailor, detects **overdue / due-soon / to-collect** orders into a `DigestModel`, **writes
deduped notification docs** to `users/{uid}/notifications`, then sends a suppress-when-empty
email. The in-app inbox renders those docs. The missing channel is **push** — reaching the
tailor when the app is closed, without them opening email.

This slice adds **one calm daily-summary push per morning**, Android only, reusing the existing
scan/loop/gates. It is the heaviest slice because push is genuinely from-scratch (GitLive's
Firebase KMP SDK has **no messaging wrapper**, and the app has no device-token storage, no
notification permission handling, and no cold-start deep-linking today).

## Locked decisions

- **Cadence & target:** ONE daily summary push per morning (same scan, suppress-when-empty);
  tapping it opens the **in-app inbox** (`NotificationsInboxRoute`). No per-order deep-linking.
- **Platform:** **Android first.** The KMP token layer is `expect`/`actual`, so the iOS `actual`
  slots in later with no rework. iOS APNs (Apple Push key, entitlement, SPM Firebase Messaging,
  `UNUserNotificationCenter` delegate, real-device testing) is OUT of this spec.
- **Sender:** extend the existing daily loop (Approach A) — not a Firestore trigger, not a
  separate scheduler. One scan → three channels (docs + email + push).
- **Permission:** a **contextual pre-prompt bottom sheet** on first dashboard landing → the
  system `POST_NOTIFICATIONS` dialog. Re-nudgeable from Settings.
- **Token storage:** `users/{uid}/notificationTokens/{token}` subcollection (multi-device,
  self-pruning on invalid-token send failures).
- **Opt-out:** a new `dailyPushEnabled` user flag + Settings toggle, **independent** of the
  email toggle. Default ON.
- **Rollout:** push reuses the Slice 1 `STAGING` allowlist (`rollout.ts`) during testing.

## 1. Backend — push send in the daily loop (`functions/`)

### 1a. `DigestIO` seam (extend `functions/src/notifications/types.ts`)

```ts
loadPushTokens(uid: string): Promise<string[]>;            // token strings from the subcollection
sendPush(tokens: string[], payload: PushSummary): Promise<{ invalidTokens: string[] }>;
deletePushTokens(uid: string, tokens: string[]): Promise<void>;
getLastPushDate(uid: string): Promise<string | null>;     // Lagos date key, mirrors email stamp
setLastPushDate(uid: string, dateKey: string): Promise<void>;
```
`DigestRecipient` gains `pushEnabled: boolean` (read from the user doc `dailyPushEnabled`,
**default true** when the field is absent — mirror `dailyDigestEmailEnabled`).

### 1b. Pure summary builder (`functions/src/notifications/pushSummary.ts`, new)

`pushSummary(model: DigestModel): PushSummary` where `PushSummary = { title: string; body: string }`.
- `title` = a fixed app string (e.g. "StitchPad").
- `body` = a human one-liner naming the most urgent item + a "+N more" tail, e.g.
  `"Folake's Asoebi is overdue + 2 more need attention"`; with a single item, no tail.
  Priority order for the lead item: overdue → due-soon → to-collect (most urgent first).
- Pure + fully unit-tested. No side effects, no Firestore. (Mirrors `digestEmailTemplate`.)

### 1c. Loop change (`functions/src/notifications/runDailyDigest.ts`)

Insert the push block **after** `io.writeNotifications(uid, model)` and **before** the email
gates, gated independently:
```
io.writeNotifications(uid, model)                                  // unchanged, always
if (recipient.pushEnabled && io.isAllowed(uid, email)
        && !isEmpty(model) && (await io.getLastPushDate(uid)) !== today) {
    const tokens = await io.loadPushTokens(uid)
    if (tokens.length) {
        const { invalidTokens } = await io.sendPush(tokens, pushSummary(model))
        if (invalidTokens.length) await io.deletePushTokens(uid, invalidTokens)
        await io.setLastPushDate(uid, today)
    }
}
// EMAIL gates unchanged from here
```
Notes: the daily stamp guards scan retries (same idea as the email `lastSentDate`, but its own
key so push/email stay independent). `isEmpty(model)` is the existing suppress-when-empty check.
Push does NOT depend on the email toggle/stamp.

### 1d. Production IO (`functions/src/notifications/dailyDigest.ts`)

Implement the new methods on the production `DigestIO`:
- `loadPushTokens` — read `users/{uid}/notificationTokens` doc ids (the token is the id).
- `sendPush` — `admin.messaging().sendEachForMulticast({ tokens, notification: {title, body},
  android: { notification: { channelId: "daily_reminders" } }, data: { target: "inbox" } })`.
  Collect tokens whose per-message error code is
  `messaging/registration-token-not-registered` into `invalidTokens` (compare the string code;
  do NOT reference a named admin constant — see `feedback_firebase_admin_grpcstatus_typeonly`).
- `deletePushTokens` — batch-delete those token docs.
- `get`/`setLastPushDate` — store under the existing `users/{uid}/private/digestState` doc
  (new field `lastPushDate`), reusing the Lagos date-key helper.

### 1e. Debug callable (`debugSendMyDigest`)

Extend the existing debug callable so it ALSO sends a push to the caller (bypassing the
allowlist + daily stamp, like it already does for email), so the in-app **Debug → Notifications
→ "Send daily digest now"** populates the inbox AND fires a push on the test device.

## 2. Client — device-token registration (KMP)

### 2a. Native token seam (`expect`/`actual`)

`feature/notification/push/PushTokenProvider` (commonMain `expect`, androidMain `actual`):
- `expect suspend fun currentPushToken(): String?`
- Android `actual` wraps native `FirebaseMessaging.getInstance().token` (kotlinx-coroutines
  `await` over the `Task`). (iOS `actual` is added in the fast-follow; for now an iosMain stub
  returning `null` keeps the build green — documented as a placeholder.)

### 2b. Token repository (commonMain, GitLive Firestore)

`PushTokenRepository` + `FirebasePushTokenRepository`:
- `suspend fun registerToken(userId, token, platform="android")` — write
  `users/{uid}/notificationTokens/{token}` = `{ platform, createdAt, updatedAt }` (fire-and-forget,
  trust the write; see `feedback_gitlive_firestore_set_awaits_server_ack` — do NOT block UI on it).
- `suspend fun unregisterToken(userId, token)` — delete on logout/account-delete (this device).
The Firestore WRITE uses GitLive (supported); only token RETRIEVAL is native (2a).

### 2c. Lifecycle

- After a successful login/session-restore, fetch the token (2a) and register it (2b).
- `onNewToken` (the Android service, §3) re-registers on rotation.
- On explicit logout, unregister this device's token. (Account deletion already cascade-cleans
  `users/{uid}` subcollections via `onAuthUserDeleted` — add `notificationTokens` to its
  allowlist, mirroring how `notifications` was added.)

## 3. Android — receive, permission, routing (`androidMain`)

### 3a. Dependencies + manifest
- Add `com.google.firebase:firebase-messaging` (via the existing Firebase BoM) to `androidMain`.
  (`google-services` plugin + `google-services.json` already present.)
- `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`
  and register the messaging service.

### 3b. Messaging service
`StitchPadMessagingService : FirebaseMessagingService` (androidMain):
- `onNewToken(token)` → register via `PushTokenRepository` (needs the current uid; resolve via
  Koin / a small token-sync entry point. If logged out, no-op).
- `onMessageReceived(msg)` → only needed for the **foreground** case (FCM auto-displays
  `notification` payloads when backgrounded/killed). Post a local notification on the
  `daily_reminders` channel with a `PendingIntent` carrying `target=inbox`.

### 3c. Notification channel
Create a **"Daily reminders"** channel (id `daily_reminders`, importance DEFAULT) on app start
(Android O+), in the Android `Application`/init seam.

### 3d. Permission pre-prompt
- A reusable pre-prompt **bottom sheet** (follows `feedback_notification_patterns`: bottom sheet
  for choices) shown the **first time** the tailor lands on the dashboard, framed around the
  value ("Get a morning reminder of deadlines and money owed"). Confirm → launch the system
  `POST_NOTIFICATIONS` request. Dismiss → remember and don't auto-show again.
- A persisted **"already prompted"** flag (local — e.g. a small settings/prefs store; this is
  device/permission UI state, not server state). Below Android 13, permission is implicit — skip
  the system request but still allow the value pre-prompt to no-op gracefully.
- Re-entry: a Settings row (under Preferences, near the push toggle) deep-links to system
  notification settings when permission is denied.
- All permission/runtime-request code is Android-specific (`expect`/`actual` or an androidMain
  controller invoked from the common dashboard via a callback) — keep commonMain free of
  Android permission APIs.

### 3e. Cold-start / tap routing → inbox
- Tapping the push opens `MainActivity` (launcher) with extra `target=inbox`.
- `MainActivity` reads the extra in `onCreate` (cold start) and `onNewIntent` (warm) → publishes
  a `PendingDeepLink` (a small holder, e.g. a `StateFlow` in a shared object or the root state).
- The NavHost, once past `SplashRoute` + auth, consumes a pending `inbox` deep link and
  `navigate(NotificationsInboxRoute)`, then clears it (so it fires once). If unauthenticated when
  the intent arrives, the pending deep link is **retained and consumed once authentication
  completes** (the NavHost resolves auth, then routes to the inbox); if the user never signs in,
  it is naturally dropped on the next cold start.

## 4. Opt-out, rollout, errors, testing

- **Opt-out:** new `dailyPushEnabled` flag through `User`/`UserDto`/`UserMapper`/
  `FirebaseUserRepository` (default true), and a **snapshot-driven** toggle row in
  `feature/settings/presentation/home/` next to the email toggle. System permission is the
  second, independent gate.
- **Rollout:** push reuses `rollout.ts` `STAGING`/allowlist; flipped alongside email at launch.
- **Errors:** invalid tokens pruned on send (§1d); permission-denied is graceful (no crash,
  enable later); token writes fire-and-forget; a logged-out `onNewToken` no-ops.
- **Testing:**
  - **Functions (Jest):** `pushSummary(model)` pure tests (lead-item priority + "+N more" +
    single-item); `runDailyDigest` fake-IO tests proving push fires for
    enabled+allowed+non-empty+has-token and is SKIPPED for disabled / not-allowed / empty /
    no-token / already-pushed-today; invalid-token pruning; the daily stamp set.
  - **Client (commonTest):** `FirebasePushTokenRepository` register/unregister mapping; the
    permission pre-prompt VM logic (show-once, confirm/dismiss); pending-deeplink → inbox
    resolution.
  - **iOS compile gate** still green (the iosMain `PushTokenProvider` stub).
  - **Manual smoke (Daniel, real Android device):** grant via the pre-prompt → seed actionable
    orders → Debug "Send daily digest now" → receive the push in **foreground, background, and
    killed** states → tap → lands on the inbox → confirm opt-out (toggle off) and
    revoked-permission both yield no push.
- **Debug menu:** add **Debug → Notifications → "Send test push to me"** (debug builds only),
  per `feedback_debug_menu_per_feature`.

## Out of scope (this slice)

- **iOS APNs** — the fast-follow slice (Apple Push key, `aps-environment` entitlement, Firebase
  Messaging via SPM, `UNUserNotificationCenter` delegate, real-device testing).
- Per-order deep-linking (tap → specific order); real-time / per-event push; coalescing logic.
- Configurable send time, per-event preferences, rich/image notifications, notification grouping.
- A weekly Smart Grow re-engagement push (different goal — growth, not reliability).

## Review

Per the rotation: **Cursor Bugbot** (auto) + **`codex review`** (pre-push). Watch for:
client/server field-name drift (`dailyPushEnabled`, token doc shape, `data.target`), the
independent push gating in the restructured loop, the firebase-admin error-code comparison
(string, not named constant), invalid-token pruning correctness, and the once-per-day stamp
guarding retries.
