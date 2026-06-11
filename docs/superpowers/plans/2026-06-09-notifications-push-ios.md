# iOS APNs Push (Notifications Slice 3b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans for the **Kotlin tasks (1–3)**. The **Swift/Xcode/ops tasks (4–7)** are Daniel-driven in Xcode + on a real device — the plan gives complete code/steps, but they can't be subagent-TDD'd (no device/Xcode in the agent sandbox).

**Goal:** Register an APNs/FCM token, request permission, receive pushes, and route a tap to the inbox on iOS — reusing the Android slice's shared layer + backend unchanged.

**Architecture:** Swift owns FirebaseMessaging + `UNUserNotificationCenter`; a Swift→Kotlin bridge (the `iosNative…` idiom used for SSO launchers) feeds the FCM token / permission / tap into the existing Kotlin layer (`PushTokenRegistrar`, `FirebasePushTokenRepository`, `PendingDeepLinkHolder`, the dashboard pre-prompt).

**Tech Stack:** Kotlin Multiplatform (iosMain), Swift (FirebaseMessaging, UNUserNotificationCenter), Firebase iOS SDK via SPM, Koin.

**Spec:** `docs/superpowers/specs/2026-06-09-notifications-push-ios-design.md`
**Branch:** `feature/notifications-push-ios` (off main; spec already committed).

---

## File Structure

**Kotlin (subagent, iOS-compile-gated):**
- Create `composeApp/src/iosMain/.../feature/notification/push/NativePushService.kt` — the Swift-implemented bridge interface.
- Modify `composeApp/src/iosMain/.../di/PlatformModule.ios.kt` — add `var iosNativePushService`.
- Modify `composeApp/src/iosMain/.../feature/notification/push/PushTokenProvider.ios.kt` — real impl via the service.
- Modify `composeApp/src/iosMain/.../feature/notification/push/IosPushPermissionController.kt` — real impl via the service.
- Create `composeApp/src/iosMain/.../feature/notification/push/IosPushBridge.kt` — `iosOnFcmTokenReceived` / `iosOnPushInboxTap`.
- Modify `composeApp/src/commonMain/.../feature/notification/push/PushTokenRegistrar.kt` — tag platform via `Platform.isIos`.
- Modify `composeApp/src/commonMain/.../feature/settings/presentation/home/SettingsViewModel.kt` — un-hide the toggle on iOS.

**Swift / Xcode (Daniel-driven):**
- Xcode: add FirebaseMessaging SPM product; enable Push Notifications capability (adds entitlement); `Info.plist` `remote-notification`.
- Create `iosApp/iosApp/PushServiceIos.swift`.
- Modify `iosApp/iosApp/iOSApp.swift` — AppDelegate delegates + bridge.
- (Ops) APNs .p8 key → Firebase Console.

---

## Task 1: iOS bridge interface + real `actual`s (Kotlin, iosMain)

**Files:**
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/NativePushService.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/PushTokenProvider.ios.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/IosPushPermissionController.kt`

- [ ] **Step 1: Define the bridge interface**

Create `NativePushService.kt`:
```kotlin
package com.danzucker.stitchpad.feature.notification.push

/**
 * Implemented by Swift (PushServiceIos), set on [iosNativePushService] from the AppDelegate
 * before doInitKoin. Bridges FirebaseMessaging + UNUserNotificationCenter calls that aren't
 * reachable from Kotlin/Native directly — mirrors NativeGoogleSignInLauncher.
 */
interface NativePushService {
    /** The latest FCM registration token Swift received, or null if not yet available. */
    fun currentFcmToken(): String?
    /** True when iOS notification permission has not yet been requested (UNAuthorizationStatus.notDetermined). */
    fun authorizationUndetermined(): Boolean
    /** Request the iOS notification permission; on grant, register for remote notifications. */
    fun requestAuthorization()
    /** Delete the device's FCM token (Messaging.deleteToken) — used by sign-out invalidation. */
    fun deleteToken()
}
```

- [ ] **Step 2: Add the settable bridge var**

In `PlatformModule.ios.kt`, next to the existing `iosNativeGoogleSignInLauncher`/`iosNativeAppleSignInLauncher` vars, add:
```kotlin
import com.danzucker.stitchpad.feature.notification.push.NativePushService
// ...
var iosNativePushService: NativePushService? = null
```

- [ ] **Step 3: Real `PushTokenProvider.ios`**

Replace `PushTokenProvider.ios.kt` body:
```kotlin
package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService

actual class PushTokenProvider {
    actual suspend fun currentToken(): String? = iosNativePushService?.currentFcmToken()
    actual suspend fun invalidateToken() {
        iosNativePushService?.deleteToken()
    }
}
```

- [ ] **Step 4: Real `IosPushPermissionController`**

Replace `IosPushPermissionController.kt` impl:
```kotlin
package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.di.iosNativePushService

class IosPushPermissionController : PushPermissionController {
    override fun shouldRequest(): Boolean =
        iosNativePushService?.authorizationUndetermined() ?: false

    override fun requestPermission(): Boolean {
        val service = iosNativePushService ?: return false
        service.requestAuthorization()
        return true
    }
}
```

- [ ] **Step 5: Verify iOS compile**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q ; echo "IOS=$?"`
Expected: `IOS=0` (the iosMain bridge + actuals compile; `iosNativePushService` is null at compile time — fine).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/ composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt
git commit -m "feat(notifications): iOS push bridge interface + real PushTokenProvider/PermissionController

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Platform-correct registration + the Swift→Kotlin event bridge

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/PushTokenRegistrar.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/IosPushBridge.kt`

- [ ] **Step 1: Tag the token's platform via `Platform.isIos`**

In `PushTokenRegistrar.kt`, `DefaultPushTokenRegistrar.register` currently calls `repository.registerToken(userId, token)` (which defaults `platform="android"`). Make it platform-correct so BOTH the iOS bridge AND the existing dashboard-pull path (`registerForUser` → `register`) tag the right platform. Add the import and change the call:
```kotlin
import com.danzucker.stitchpad.util.Platform
// ...
suspend fun register(userId: String, token: String) {
    runCatching {
        repository.registerToken(userId, token, platform = if (Platform.isIos) "ios" else "android")
    }.onFailure { AppLogger.w { "push token register failed: ${it.message}" } }
}
```
(Keep the exact existing method shape/logging; only the `platform =` argument is new. `register` is the method `StitchPadMessagingService.onNewToken` and `registerForUser` both call, so no other change is needed. The Android caller is unaffected — `Platform.isIos` is false there.)

- [ ] **Step 2: The event bridge (Swift → Kotlin)**

Create `IosPushBridge.kt`:
```kotlin
package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.navigation.DeepLinkTarget
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val iosPushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Called by Swift (MessagingDelegate.didReceiveRegistrationToken) when an FCM token is
 * received or refreshed. Registers it for the signed-in user — the iOS analog of Android's
 * FirebaseMessagingService.onNewToken. No-ops when logged out.
 */
fun iosOnFcmTokenReceived(token: String) {
    iosPushScope.launch {
        val koin = KoinPlatform.getKoin()
        val uid = koin.get<AuthRepository>().getCurrentUser()?.id ?: return@launch
        koin.get<PushTokenRegistrar>().register(uid, token)
    }
}

/**
 * Called by Swift (UNUserNotificationCenter delegate) when the user taps a push targeting the
 * inbox. Sets the pending deep link; the existing PushDeepLinkRedirectEffect + MainRoot consume it.
 */
fun iosOnPushInboxTap() {
    KoinPlatform.getKoin().get<PendingDeepLinkHolder>().set(DeepLinkTarget.INBOX)
}
```
(Match the exact `AuthRepository` import path the codebase uses — it's `com.danzucker.stitchpad.feature.auth.domain.AuthRepository` per the Android service. `KoinPlatform.getKoin().get<T>()` is the iosMain Koin-resolution idiom, same as `IosOfflineUploadBackgroundTasks.kt`.)

- [ ] **Step 3: Verify**

Run:
```
cd /Users/danzucker/Desktop/Project/StitchPad
./gradlew :composeApp:compileDebugKotlinAndroid -q ; echo "ANDROID=$?"
./gradlew :composeApp:testDebugUnitTest -q ; echo "TESTS=$?"
./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q ; echo "IOS=$?"
./gradlew detekt ; echo "DETEKT=$?"
```
All `=0`. (Android compile + tests confirm the registrar change is safe on Android; iOS compile confirms the bridge.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/PushTokenRegistrar.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/IosPushBridge.kt
git commit -m "feat(notifications): platform-correct token registration + iOS push event bridge

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Un-hide the Settings push toggle on iOS

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt:226`
- Test: the existing settings VM test (search `pushReminderSupported` / `SettingsPushToggleTest`)

- [ ] **Step 1: Update the gate**

Line 226 is `pushReminderSupported = !Platform.isIos,`. Push now works on iOS, so the toggle should show on both platforms. Change it to:
```kotlin
            pushReminderSupported = true,
```
(If `Platform` becomes an unused import after this, remove it; if it's used elsewhere in the file, leave it.)

- [ ] **Step 2: Update/confirm the test**

Find the test asserting `pushReminderSupported` (likely in `SettingsPushToggleTest` or `SettingsDigestToggleTest`). If a test asserts it is false on iOS / true on Android, update it to expect `true` unconditionally. If there's no such assertion, add a one-line check that `buildState` yields `pushReminderSupported = true`.

- [ ] **Step 3: Verify**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest -q ; echo "TESTS=$?" && ./gradlew detekt ; echo "DETEKT=$?" && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q ; echo "IOS=$?"`
All `=0`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/ composeApp/src/commonTest/
git commit -m "feat(notifications): show the daily push toggle on iOS too

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Xcode project config + entitlement + Info.plist (Daniel-driven)

These are Xcode-UI changes (the `.pbxproj` is fragile to hand-edit). Do them in Xcode; the plain-text files (`Info.plist`, `iosApp.entitlements`) an agent can edit, but the SPM product + capability are UI.

- [ ] **Step 1: Add FirebaseMessaging (Xcode UI)**

`iosApp/iosApp.xcodeproj` → target **iosApp** → **General → Frameworks, Libraries, and Embedded Content** → **+** → from the existing `firebase-ios-sdk` package, add **FirebaseMessaging**. (Or Project → Package Dependencies → firebase-ios-sdk → add the FirebaseMessaging product to the iosApp target.) Build once to confirm it links.

- [ ] **Step 2: Enable Push Notifications capability (Xcode UI)**

Target **iosApp** → **Signing & Capabilities** → **+ Capability** → **Push Notifications**. This adds `aps-environment` to `iosApp.entitlements` automatically. Confirm `iosApp/iosApp/iosApp.entitlements` now contains:
```xml
	<key>aps-environment</key>
	<string>development</string>
```
(Xcode manages dev/prod; the APNs auth key serves both.)

- [ ] **Step 3: Info.plist background mode (text edit OK)**

In `iosApp/iosApp/Info.plist`, the `UIBackgroundModes` array is currently `["processing"]`. Add `remote-notification`:
```xml
	<key>UIBackgroundModes</key>
	<array>
		<string>processing</string>
		<string>remote-notification</string>
	</array>
```

- [ ] **Step 4 (Ops): APNs auth key → Firebase**

Apple Developer → **Certificates, IDs & Profiles → Keys → +** → enable **Apple Push Notifications service (APNs)** → download the **.p8**. In **Firebase Console → Project settings → Cloud Messaging → Apple app configuration (`com.danzucker.stitchpad`) → APNs Authentication Key → Upload** the .p8 with its **Key ID** and your **Team ID**. (One key, dev + prod.)

- [ ] **Step 5: Commit the text files**

```bash
git add iosApp/iosApp/Info.plist iosApp/iosApp/iosApp.entitlements iosApp/iosApp.xcodeproj/project.pbxproj
git commit -m "build(ios): FirebaseMessaging + push capability + remote-notification background mode

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: `PushServiceIos.swift` (the bridge implementation)

**Files:**
- Create: `iosApp/iosApp/PushServiceIos.swift`

- [ ] **Step 1: Implement the Swift bridge**

Create `iosApp/iosApp/PushServiceIos.swift`. (Use the same Kotlin-framework import as `iOSApp.swift` — e.g. `import ComposeApp`; match it exactly. `NativePushService` is the Kotlin protocol exported to Swift.)
```swift
import Foundation
import UIKit
import FirebaseMessaging
import UserNotifications
import ComposeApp // match iOSApp.swift's import of the KMP framework

/// Swift implementation of the Kotlin NativePushService bridge. Holds the latest FCM token and
/// performs the UNUserNotificationCenter + Messaging calls that Kotlin/Native can't reach directly.
final class PushServiceIos: NativePushService {
    static let shared = PushServiceIos()
    private var latestToken: String?

    func updateToken(_ token: String?) { latestToken = token }

    func currentFcmToken() -> String? { latestToken }

    // Cached authorization status — refreshed at launch and on foreground (Step 1a) so the
    // Kotlin `shouldRequest()` read is non-blocking (it may run on the main dispatcher; a
    // semaphore-wait there could stall the UI). Defaults to true (not-yet-asked) until refreshed.
    private var notDetermined = true

    func refreshAuthorizationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            self?.notDetermined = settings.authorizationStatus == .notDetermined
        }
    }

    func authorizationUndetermined() -> Bool { notDetermined }

    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func deleteToken() {
        Messaging.messaging().deleteToken { _ in }
        latestToken = nil
    }
}
```
(If `authorizationUndetermined()`'s semaphore-on-Kotlin-thread is flagged in review, an alternative is to cache the status from a `getNotificationSettings` call made at launch; the semaphore form is acceptable since Kotlin calls it off the main thread.)

- [ ] **Step 2: Build in Xcode** — confirm it compiles against the KMP framework (the `NativePushService` protocol resolves after the framework is rebuilt with Task 1's Kotlin).

---

## Task 6: `iOSApp.swift` — AppDelegate delegates + bridge wiring

**Files:**
- Modify: `iosApp/iosApp/iOSApp.swift`

- [ ] **Step 1: Wire the delegates + bridge**

Update `AppDelegate`. Keep the existing `FirebaseApp.configure()`, offline-tasks, and SSO-launcher lines; add the push wiring **before** `doInitKoin`, and the delegate conformances:
```swift
class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        IosOfflineUploadBackgroundTasksKt.registerIosOfflineUploadTasks()
        PlatformModule_iosKt.iosNativeGoogleSignInLauncher = GoogleSignInLauncherIos()
        PlatformModule_iosKt.iosNativeAppleSignInLauncher = AppleSignInLauncherIos()

        // Push: delegates + the Swift bridge, set BEFORE doInitKoin so Koin can wire it.
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        PlatformModule_iosKt.iosNativePushService = PushServiceIos.shared
        PushServiceIos.shared.refreshAuthorizationStatus()

        StitchPadAppKt.doInitKoin(platformConfig: { _ in })
        return true
    }

    // Keep the cached authorization status fresh (e.g. user toggled it in iOS Settings).
    func applicationDidBecomeActive(_ application: UIApplication) {
        PushServiceIos.shared.refreshAuthorizationStatus()
    }

    // Existing Google Sign-In URL handler stays unchanged.
    func application(_ app: UIApplication, open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }

    // APNs token → FCM handshake (robust to swizzling being off).
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }
    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs registration failed: \(error.localizedDescription)")
    }

    // FCM token received/refreshed → store it + register for the signed-in user via Kotlin.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushServiceIos.shared.updateToken(token)
        IosPushBridgeKt.iosOnFcmTokenReceived(token: token)
    }

    // Foreground: show the notification (iOS analog of Android's manual post).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .list])
    }

    // Tap → route to the inbox via Kotlin.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        if let target = userInfo["target"] as? String, target == "inbox" {
            IosPushBridgeKt.iosOnPushInboxTap()
        }
        completionHandler()
    }
}
```
Add the imports at the top of the file if missing: `import FirebaseMessaging`, `import UserNotifications`. (`IosPushBridgeKt` is the Swift name for the Kotlin `IosPushBridge.kt` top-level funcs; `PlatformModule_iosKt` is already used for the SSO launchers.)

- [ ] **Step 2: Build in Xcode** — the app compiles + links FirebaseMessaging.

---

## Task 7: Build, framework rebuild, and device smoke test (Daniel)

- [ ] **Step 1: Rebuild the KMP framework** so Xcode sees the new Kotlin symbols (`NativePushService`, `IosPushBridgeKt`, `iosNativePushService`): in Xcode, clean build folder (⇧⌘K) and build; or the embedAndSign gradle task runs on build. Confirm the Swift compiles against the regenerated framework.

- [ ] **Step 2: Final compile gates (agent-runnable):**
```
cd /Users/danzucker/Desktop/Project/StitchPad
./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q ; echo "IOS_KT=$?"
./gradlew :composeApp:testDebugUnitTest -q ; echo "TESTS=$?"
./gradlew detekt ; echo "DETEKT=$?"
```
All `=0`.

- [ ] **Step 3: Manual smoke (real iPhone — APNs needs a device):**
  1. Run the iOS app on the device from Xcode. Sign in.
  2. On the dashboard, accept the **pre-prompt → grant** the iOS notification permission.
  3. In Firestore, confirm a `users/{uid}/notificationTokens/{token}` doc with **`platform = "ios"`**.
  4. **Debug → Notifications → "Send digest + push (test)"** (seed an actionable order first).
  5. Receive the push with the app **foreground, background, and killed** → **tap → lands on the Notifications inbox**.
  6. Toggle the **Daily push reminder** off (or revoke OS permission) → confirm no push.
  7. (Cross-platform) confirm the same scan also pushes to a registered Android device.

- [ ] **Step 4: Open the PR** (after the device smoke passes) with the smoke-test steps in the body; review rotation (Cursor + codex). Note the Apple ops (.p8 key) are an environment prerequisite, not code.

---

## Notes
- No backend / Cloud Functions changes — the daily scan already multicasts to all tokens.
- The dashboard pre-prompt + Settings toggle now drive iOS permission via the bridged `PushPermissionController`.
- Pre-rollout follow-ups (live-cron verify, server-side token-ownership, `STAGING=false`) remain shared across platforms.
