# Notifications — Slice 3b: iOS APNs Push (design)

> **Date:** 2026-06-09
> **Status:** approved design, pre-implementation
> **Branch:** `feature/notifications-push-ios` (off `main`, which has the Android push slice #136)
> **Builds on:** Slice 3 (Android daily-summary push, #136) — reuses its entire shared layer + backend.

## Context

The Android push slice (#136) shipped the full notifications backbone: a daily scan that
multicasts an FCM push to every registered device token (regardless of platform), plus the
shared Kotlin client layer — `PushTokenRegistrar`, `FirebasePushTokenRepository` (writes
`users/{uid}/notificationTokens/{token}` with a `platform` field), `PushTokenProvider` and
`PushPermissionController` `expect`/`actual` seams (currently **iOS stubs**), the dashboard
permission pre-prompt, and `PendingDeepLinkHolder` + the tap→inbox routing. iOS was a
deliberate fast-follow.

This slice fills in the iOS half: register an APNs/FCM token, request permission, receive
pushes, and route a tap to the inbox — **client + Apple-side ops only. No backend changes**
(the scan already sends to any token via `sendEachForMulticast`).

## Locked decisions

- **Approach A — Swift bridge.** FirebaseMessaging + `UNUserNotificationCenter` live in Swift
  (`AppDelegate` + delegates); a Swift→Kotlin bridge (the existing `iosNative…` idiom used for
  the Google/Apple Sign-In launchers) feeds the FCM token, permission, and tap into the shared
  Kotlin layer. Rejected: Kotlin/Native cinterop to the Firebase iOS SDK (fiddly, off-idiom);
  Swift writing Firestore directly (duplicates the shared repo, breaks DRY).
- **Parity with Android:** daily-summary push → inbox; permission via the **same dashboard
  pre-prompt** (no new UI); reuse the registrar, repo, and deep-link holder unchanged.
- **Testing:** real iPhone (APNs is reliable on device); Daniel handles the Apple ops.

## 1. Project config + Apple ops

- **SPM:** add the **FirebaseMessaging** product to the `iosApp` target (the project already
  links 6 Firebase SPM products from `firebase-ios-sdk` ≥ 12.11.0; add one more).
- **`iosApp/iosApp/iosApp.entitlements`:** add `aps-environment` = `development` (release builds
  resolve to `production` via the APNs key; the key is environment-agnostic).
- **`iosApp/iosApp/Info.plist`:** add `remote-notification` to the `UIBackgroundModes` array
  (currently `["processing"]`) so backgrounded/killed pushes are delivered.
- **Ops (Daniel, on the Apple side):** create an **APNs auth key (.p8)** in the Apple Developer
  portal (Keys → enable Apple Push Notifications service), upload it to **Firebase Console →
  Project settings → Cloud Messaging → Apple app configuration → APNs Authentication Key**
  (with the Key ID + Team ID). One key serves dev + prod. Enable the Push Notifications
  capability on the App ID / in Xcode Signing & Capabilities (adds the entitlement).

## 2. Swift — `iOSApp.swift` (AppDelegate + delegates)

In `application(_:didFinishLaunchingWithOptions:)`, **after** `FirebaseApp.configure()` and
**before** `doInitKoin`:
- `Messaging.messaging().delegate = self`
- `UNUserNotificationCenter.current().delegate = self`
- Assign the bridge: `PlatformModule_iosKt.iosNativePushService = PushServiceIos()` (§3) —
  set before `doInitKoin` so Koin can wire it, exactly like the SSO launchers.

`AppDelegate` conformances:
- **`MessagingDelegate`** — `messaging(_:didReceiveRegistrationToken:)`: store the token in the
  `PushServiceIos` instance (so `currentFcmToken()` can return it) and call Kotlin
  `iosOnFcmTokenReceived(token)`.
- **`UIApplicationDelegate`** — `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`:
  `Messaging.messaging().apnsToken = deviceToken` (explicit APNs↔FCM handshake, robust to
  swizzling being disabled); log `didFailToRegisterForRemoteNotificationsWithError`.
- **`UNUserNotificationCenterDelegate`** —
  - `userNotificationCenter(_:willPresent:withCompletionHandler:)` → `completionHandler([.banner,
    .sound, .list])` so a push shows while the app is foreground (iOS analog of Android's manual
    foreground post).
  - `userNotificationCenter(_:didReceive:withCompletionHandler:)` (tap) → if the notification's
    `userInfo["target"]` is `"inbox"`, call Kotlin `iosOnPushInboxTap()`, then
    `completionHandler()`.

A small Swift `PushServiceIos` type holds the latest FCM token and implements the
`NativePushService` Kotlin interface (§3): `currentFcmToken()` returns the stored token;
`authorizationUndetermined()` checks `UNUserNotificationCenter.current().notificationSettings`
(`.notDetermined`); `requestAuthorization()` calls
`UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])` and,
on grant, `UIApplication.shared.registerForRemoteNotifications()` on the main thread.

## 3. Swift↔Kotlin bridge (`composeApp/src/iosMain/.../di/PlatformModule.ios.kt`)

Mirror the SSO-launcher idiom. **Pull direction** (Kotlin asks Swift): a Kotlin interface Swift
implements, held in a top-level `var` Swift assigns:
```kotlin
interface NativePushService {
    fun currentFcmToken(): String?      // latest FCM token Swift received, or null
    fun authorizationUndetermined(): Boolean  // true when iOS permission not yet asked
    fun requestAuthorization()          // requestAuthorization + (on grant) registerForRemoteNotifications
    fun deleteToken()                   // Messaging.deleteToken — for sign-out invalidation (§4)
}
var iosNativePushService: NativePushService? = null
```
**Push/event direction** (Swift calls Kotlin — top-level funcs like `doInitKoin`, exported to
Swift), in an `iosMain` file (e.g. `feature/notification/push/IosPushBridge.kt`):
```kotlin
fun iosOnFcmTokenReceived(token: String) { /* resolve registrar + uid via Koin, register */ }
fun iosOnPushInboxTap() { /* resolve PendingDeepLinkHolder via Koin, set(INBOX) */ }
```
These reach Koin singletons from non-injected code the **same way the existing iosMain top-level
entry points do** (e.g. however `registerIosOfflineUploadTasks` / `doInitKoin` obtain singletons —
`GlobalContext.get().get()` or the project's equivalent; match it exactly).
`iosOnFcmTokenReceived` launches a coroutine on an app scope: `val uid =
authRepository.getCurrentUser()?.id ?: return; registrar.register(uid, token)`.

## 4. Kotlin — fill in the iOS `actual`s + un-hide the toggle

- **`PushTokenProvider.ios.kt`:** `currentToken()` → `iosNativePushService?.currentFcmToken()`;
  `invalidateToken()` → bridge to delete the FCM token (add a `deleteToken()` to
  `NativePushService` calling `Messaging.messaging().deleteToken { … }`), matching Android's
  sign-out token rotation. If a clean Swift `deleteToken` bridge is awkward in V1, a documented
  no-op is acceptable (the server-side invalid-token pruning + token-ownership follow-up cover
  the residual) — prefer the real `deleteToken`.
- **`IosPushPermissionController.kt`:** `shouldRequest()` →
  `iosNativePushService?.authorizationUndetermined() ?: false`; `requestPermission()` →
  `iosNativePushService?.requestAuthorization(); return true`.
- **Settings toggle:** `SettingsViewModel.buildState` sets `pushReminderSupported = true` on iOS
  now (it was `!Platform.isIos`). Simplest: drop the platform gate so the toggle shows on both
  platforms (push works on both now). Confirm the row renders on iOS.

## 5. Permission flow (reuse Android's, no new UI)

The existing dashboard pre-prompt now fires on iOS: `shouldRequest()` returns true when
undetermined → the same bottom sheet → "Turn on reminders" → `requestPermission()` → the iOS
system dialog → grant → `registerForRemoteNotifications` → APNs token → FCM token →
`iosOnFcmTokenReceived` → registered. The `requestPermission()` Boolean contract (added on
Android) holds: iOS returns `true` (it launches the system dialog), so the pre-prompt marks
asked + closes. The Settings toggle's "request on enable while missing" path works identically.

## 6. Data flow (end to end)

sign in → first dashboard → pre-prompt → grant → APNs registration → `didReceiveRegistrationToken`
→ `iosOnFcmTokenReceived(token)` → `registrar.register(uid, token, "ios")` → doc written. Morning
scan (unchanged) → `sendEachForMulticast` → APNs → device shows the push (system tray when
backgrounded/killed; `willPresent` when foreground). Tap → `iosOnPushInboxTap()` →
`PendingDeepLinkHolder.set(INBOX)` → existing `PushDeepLinkRedirectEffect` + `MainRoot` consumer
route to the inbox.

## 7. Error handling / edge cases

- Token not yet available at dashboard load (APNs handshake is async): the **push path**
  (`iosOnFcmTokenReceived` on receipt) covers it — registration doesn't depend solely on the pull
  path. `currentFcmToken()` may return null early; that's fine.
- Permission denied: no token, no push; the Settings toggle + re-prompt path (Android parity)
  apply. `authorizationUndetermined()` returns false once asked, so the pre-prompt won't re-nag.
- Sign-out: the shared `SignOutUseCase` already unregisters the token doc + invalidates the token
  (now wired to the iOS `invalidateToken` bridge); no iOS-specific change.
- Swizzling: setting `Messaging.apnsToken` explicitly + the manual delegates make us robust
  whether or not FCM method-swizzling is enabled (default on).

## 8. Testing

- **Compile gates:** `:composeApp:compileKotlinIosSimulatorArm64` green; the iOS app builds in
  Xcode with FirebaseMessaging + the entitlement.
- **Manual smoke (real iPhone — APNs needs a device):** sign in → accept the pre-prompt → grant →
  verify a `users/{uid}/notificationTokens/{token}` doc with `platform = "ios"` → Debug →
  "Send digest + push (test)" (seed an actionable order) → receive the push in **foreground,
  background, and killed** states → **tap → lands on the Notifications inbox** → toggle the push
  reminder off (or revoke OS permission) → confirm no push. Confirm the same push also reaches a
  registered Android device (cross-platform multicast).

## Out of scope

- No backend / Cloud Functions changes (the scan already multicasts to all platforms).
- Per-order deep-linking; real-time/event push; configurable send time — unchanged deferrals.
- The pre-rollout follow-ups (verify the live cron, server-side token-ownership cleanup, flip
  `STAGING=false`) remain shared across Android + iOS.

## Review

Per the rotation: **Cursor Bugbot** + **`codex review`** (pre-push). Watch for: the Swift→Kotlin
bridge being set before `doInitKoin`; the APNs↔FCM `apnsToken` handshake; `currentToken()`
null-safety; the toggle correctly showing on iOS; and the `platform="ios"` value on the token doc
matching what the backend expects (it's platform-agnostic, but keep the field accurate).
