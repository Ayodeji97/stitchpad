# Auth Phase 3 — Google + Apple SSO + Account Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `Continue with Google` / `Continue with Apple` "Coming soon" stubs on Login + Signup with real SSO flows backed by Firebase Auth, plus add an in-app account deletion entry, satisfying App Store Guideline 4.8 and 5.1.1(v).

**Architecture:** Platform-specific credential acquisition lives behind an `expect class SsoCredentialProvider` (Credential Manager on Android, GoogleSignIn iOS SDK + AuthenticationServices on iOS). `FirebaseAuthRepository` orchestrates: ask the provider for credentials, exchange with Firebase, return a domain `User`. Apple is iOS-only — the Apple button is hidden on Android. Account deletion is short-dialog + inline disclosure link (modern Apple/Google pattern).

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform), gitlive `firebase-auth`, AndroidX Credential Manager + GoogleId, SwiftPM `GoogleSignIn-iOS` + `AuthenticationServices` framework, Koin DI, JUnit5 + Turbine + AssertK + UnconfinedTestDispatcher for tests.

**Spec:** `docs/superpowers/specs/2026-05-11-auth-phase3-sso-design.md`

---

## File Structure

### New files (commonMain)

| Path | Responsibility |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.kt` | `expect class` — platform credential acquisition seam |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/SsoError.kt` | Enum: CANCELLED, NO_PROVIDER, NETWORK, UNKNOWN |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AppleCredential.kt` | Data class: idToken + rawNonce + fullName? |

### New files (androidMain)

| Path | Responsibility |
|---|---|
| `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/CurrentActivityHolder.kt` | Weak-ref holder of the current Activity, set by `MainActivity` |
| `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.android.kt` | `actual class` — Credential Manager + GoogleId |

### New files (iosMain)

| Path | Responsibility |
|---|---|
| `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.ios.kt` | `actual class` — GoogleSignIn-iOS + ASAuthorizationController |
| `iosApp/iosApp/SsoPresentingViewControllerBridge.swift` | Static var bridge for current presenting UIViewController |
| `iosApp/iosApp/iosApp.entitlements` | Adds Sign in with Apple capability (created by Xcode when capability is added) |

### New files (UI for delete account)

| Path | Responsibility |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/deleteaccount/DeleteAccountDialog.kt` | Compose dialog + bottom sheet for delete confirmation |

### Modified files

| Path | What changes |
|---|---|
| `gradle/libs.versions.toml` | Add `androidx-credentials`, `androidx-credentials-play-services-auth`, `googleid` versions |
| `composeApp/build.gradle.kts` | Add Credential Manager + GoogleId deps to androidMain |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | Add SwiftPM dep on `GoogleSignIn-iOS`; add entitlements file ref; Sign in with Apple capability |
| `iosApp/iosApp/Info.plist` | Add `CFBundleURLTypes` with reversed client ID for Google iOS |
| `iosApp/iosApp/iOSApp.swift` (or `ContentView.swift`) | Wire `SsoPresentingViewControllerBridge.setVC()` on root view |
| `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/MainActivity.kt` | Set `CurrentActivityHolder.activity = this` in onCreate; clear in onDestroy |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthError.kt` | Add 4 new cases |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt` | Add `signInWithGoogle`, `signInWithApple`, `deleteAccount` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt` | Add `deleteUserDoc` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt` | Implement 3 new methods |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt` | Implement `deleteUserDoc` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeAuthRepository.kt` | Add 3 SSO methods; add `FakeSsoCredentialProvider` companion |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeUserRepository.kt` | Add `deleteUserDoc` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModel.kt` | Replace `ShowComingSoon` with real Google/Apple flow |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt` | Same |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/SsoButtonRow.kt` | Hide Apple button on Android |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/home/presentation/HomeScreen.kt` (or equivalent) | Add Delete account entry (if Settings not merged yet) |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt` | (No change yet — provider wires through `platformModule`) |
| `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt` | Wire `AndroidSsoCredentialProvider` |
| `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt` | Wire `IosSsoCredentialProvider` |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Add ~12 new strings (delete dialog, SSO errors) |

---

## Task 1: Branch hygiene + Android deps

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Confirm branch state**

Run: `git branch --show-current && git log --oneline -3`
Expected: on `feature/auth-phase3-sso`, with `783718e docs(auth): Phase 3 SSO + account deletion design spec` as HEAD.

- [ ] **Step 2: Find current Credential Manager + GoogleId versions to use**

Open https://developer.android.com/jetpack/androidx/releases/credentials and https://developers.google.com/identity/android-credential-manager/migration. At plan-write time, use:
- `androidx.credentials:credentials` — `1.3.0`
- `androidx.credentials:credentials-play-services-auth` — `1.3.0`
- `com.google.android.libraries.identity.googleid:googleid` — `1.1.1`

If newer stable versions exist, prefer those.

- [ ] **Step 3: Add version refs to `libs.versions.toml`**

In `[versions]`:

```toml
androidx-credentials = "1.3.0"
googleid = "1.1.1"
```

In `[libraries]`:

```toml
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidx-credentials" }
androidx-credentials-play-services-auth = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidx-credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
```

- [ ] **Step 4: Add deps to androidMain in `composeApp/build.gradle.kts`**

Find the `androidMain.dependencies` block and add:

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.googleid)
```

- [ ] **Step 5: Sync + smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "feat(auth): add Credential Manager + GoogleId deps for Android SSO"
```

---

## Task 2: iOS Xcode setup — capabilities + URL scheme + SwiftPM dep

> **Manual Xcode work; cannot be automated. The implementer must do these in Xcode GUI.** Follow each sub-step, then verify and commit the resulting file diffs.

**Files:**
- Modify: `iosApp/iosApp/Info.plist`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (Xcode writes this when capability + dep added)
- Create: `iosApp/iosApp/iosApp.entitlements` (Xcode writes this)
- Modify: `iosApp/Configuration/Config.xcconfig` (only if bundle ID verification fails)

- [ ] **Step 1: Verify bundle ID resolution**

Run: `grep PRODUCT_BUNDLE_IDENTIFIER iosApp/Configuration/Config.xcconfig`
Expected: `PRODUCT_BUNDLE_IDENTIFIER=com.danzucker.stitchpad$(TEAM_ID)`.

In Xcode, open Build Settings → search "Product Bundle Identifier" → confirm the **resolved** value. If it shows `com.danzucker.stitchpad` (TEAM_ID resolves to empty), good. If it shows `com.danzucker.stitchpad<something>`, edit `Config.xcconfig` to drop `$(TEAM_ID)` so the bundle ID is the bare `com.danzucker.stitchpad`. This must match the Firebase console and Apple Developer Service ID.

- [ ] **Step 2: Add Sign in with Apple capability**

In Xcode → select `iosApp` project → `iosApp` target → Signing & Capabilities → `+ Capability` → `Sign in with Apple`. Xcode creates `iosApp/iosApp/iosApp.entitlements` automatically.

- [ ] **Step 3: Add GoogleSignIn-iOS SwiftPM dependency**

In Xcode → File → Add Package Dependencies → enter `https://github.com/google/GoogleSignIn-iOS` → choose "Up to Next Major Version" from `8.0.0` → add to `iosApp` target. Verify `GoogleSignIn` and `GoogleSignInSwift` appear under Frameworks.

- [ ] **Step 4: Read reversed client ID from `GoogleService-Info.plist`**

Run: `plutil -extract REVERSED_CLIENT_ID raw iosApp/iosApp/GoogleService-Info.plist`
Output: a string like `com.googleusercontent.apps.123456789-abc`.

Note this value for Step 5.

- [ ] **Step 5: Add URL scheme to Info.plist**

Edit `iosApp/iosApp/Info.plist` to add `CFBundleURLTypes` array. The final file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CADisableMinimumFrameDurationOnPhone</key>
    <true/>
    <key>NSPhotoLibraryUsageDescription</key>
    <string>StitchPad needs access to your photos so you can attach style references to customers.</string>
    <key>NSCameraUsageDescription</key>
    <string>StitchPad needs camera access so you can snap a photo of the fabric or style reference.</string>
    <key>CFBundleURLTypes</key>
    <array>
        <dict>
            <key>CFBundleURLSchemes</key>
            <array>
                <string><!-- paste the value from Step 4 here, e.g. com.googleusercontent.apps.123456789-abc --></string>
            </array>
        </dict>
    </array>
</dict>
</plist>
```

- [ ] **Step 6: Configure Firebase console (manual, no code)**

In Firebase console (https://console.firebase.google.com → `stitchpad-30607` project → Authentication → Sign-in method):
1. Enable **Google** provider — confirm the project's `support_email` is set.
2. Enable **Apple** provider — input the Apple Services ID, Team ID, Key ID, and the private key (`.p8`) from Apple Developer portal.

- [ ] **Step 7: Smoke build iOS**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17 Pro Max" -configuration Debug build 2>&1 | tail -5`
Expected: `** BUILD SUCCEEDED **`.

If the build fails on missing `GoogleSignIn` import — the SwiftPM dep didn't link correctly. Re-open Xcode and verify the target is selected when adding the package.

- [ ] **Step 8: Commit**

```bash
git add iosApp/iosApp/Info.plist iosApp/iosApp/iosApp.entitlements iosApp/iosApp.xcodeproj iosApp/Configuration/Config.xcconfig
git commit -m "chore(ios): add Sign-in-with-Apple capability, GoogleSignIn-iOS SwiftPM dep, and Google URL scheme"
```

---

## Task 3: AuthError + SsoError + AppleCredential

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthError.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/SsoError.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AppleCredential.kt`

- [ ] **Step 1: Extend AuthError enum**

Replace the contents of `AuthError.kt`:

```kotlin
package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class AuthError : Error {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    TOO_MANY_REQUESTS,
    NETWORK_ERROR,
    EMAIL_REGISTERED_WITH_OTHER_PROVIDER,
    REQUIRES_RECENT_LOGIN,
    SSO_CANCELLED,
    SSO_UNAVAILABLE,
    UNKNOWN
}
```

- [ ] **Step 2: Create SsoError.kt**

```kotlin
package com.danzucker.stitchpad.feature.auth.domain

enum class SsoError {
    CANCELLED,
    NO_PROVIDER,
    NETWORK,
    UNKNOWN
}
```

- [ ] **Step 3: Create AppleCredential.kt**

```kotlin
package com.danzucker.stitchpad.feature.auth.domain

/**
 * Apple Sign-In credential payload returned by the iOS-side credential provider.
 *
 * `fullName` is populated only on the very first Sign-In with Apple ever (per
 * Apple's privacy model — once you've signed in once, it's never returned again).
 * The repository layer uses it to seed Firebase Auth's displayName.
 */
data class AppleCredential(
    val idToken: String,
    val rawNonce: String,
    val fullName: String? = null,
)
```

- [ ] **Step 4: Smoke compile**

Run: `./gradlew :composeApp:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/
git commit -m "feat(auth): add SsoError + AppleCredential domain types + extend AuthError"
```

---

## Task 4: SsoCredentialProvider expect class

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.kt`

- [ ] **Step 1: Create the expect class**

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

/**
 * Platform credential acquisition for SSO sign-in.
 *
 * - Android: Credential Manager + GoogleIdOption. Apple returns NO_PROVIDER.
 * - iOS: GoogleSignIn-iOS SDK + AuthenticationServices (ASAuthorizationController).
 *
 * Implementations must NOT perform any Firebase calls — they return raw credentials
 * that FirebaseAuthRepository exchanges for a User.
 */
expect class SsoCredentialProvider {
    suspend fun getGoogleIdToken(): Result<String, SsoError>
    suspend fun getAppleCredential(): Result<AppleCredential, SsoError>
}
```

- [ ] **Step 2: Smoke compile (will fail until actuals exist — that's expected for now)**

Run: `./gradlew :composeApp:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL (expect classes compile fine in commonMain alone).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.kt
git commit -m "feat(auth): add SsoCredentialProvider expect class"
```

---

## Task 5: CurrentActivityHolder (Android)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/CurrentActivityHolder.kt`

- [ ] **Step 1: Create the holder**

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Weak-ref holder for the foreground Activity. MainActivity sets `activity = this`
 * in onCreate and `activity = null` in onDestroy. Read by AndroidSsoCredentialProvider
 * to obtain an Activity context for Credential Manager.
 *
 * Using WeakReference avoids leaking the Activity if onDestroy doesn't run before
 * the process is reused.
 */
class CurrentActivityHolder {
    private var ref: WeakReference<Activity>? = null

    var activity: Activity?
        get() = ref?.get()
        set(value) {
            ref = value?.let { WeakReference(it) }
        }
}
```

- [ ] **Step 2: Smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/CurrentActivityHolder.kt
git commit -m "feat(auth): add CurrentActivityHolder for Android Credential Manager"
```

---

## Task 6: Wire CurrentActivityHolder in MainActivity

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/MainActivity.kt`

- [ ] **Step 1: Read current MainActivity**

Run: `cat composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/MainActivity.kt`
Note: the existing class extends `ComponentActivity`, calls `enableEdgeToEdge()`, and sets `setContent { App() }`. We'll inject the holder via Koin and set it in onCreate.

- [ ] **Step 2: Inject + wire**

Edit `MainActivity.kt` — add Koin inject + lifecycle hooks:

```kotlin
package com.danzucker.stitchpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val currentActivityHolder: CurrentActivityHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentActivityHolder.activity = this
        setContent { App() }
    }

    override fun onDestroy() {
        if (currentActivityHolder.activity === this) {
            currentActivityHolder.activity = null
        }
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

Note: Koin will throw at runtime if the holder isn't registered. We register it in Task 8 alongside `AndroidSsoCredentialProvider`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/MainActivity.kt
git commit -m "feat(auth): wire CurrentActivityHolder lifecycle in MainActivity"
```

---

## Task 7: Android web client ID from google-services.json

**Files:**
- Read-only inspection.

- [ ] **Step 1: Locate the web client ID**

Run: `plutil -convert json -o - composeApp/google-services.json 2>/dev/null | jq -r '.client[0].oauth_client[] | select(.client_type==3) | .client_id'`
(or `python3 -c "import json; d=json.load(open('composeApp/google-services.json')); print([c for c in d['client'][0]['oauth_client'] if c['client_type']==3][0]['client_id'])"`)

This is the **web OAuth client ID** Credential Manager needs as `serverClientId`. Note the value.

- [ ] **Step 2: Add to BuildConfig**

Edit `composeApp/build.gradle.kts` — inside the `android { defaultConfig { ... } }` block:

```kotlin
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"<paste the value from Step 1>\"")
```

Confirm `buildFeatures.buildConfig = true` is also set in the `android { ... }` block. If not, add it.

- [ ] **Step 3: Smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts
git commit -m "chore(android): expose Google web client ID via BuildConfig"
```

---

## Task 8: AndroidSsoCredentialProvider (Google) + DI wiring

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt`

- [ ] **Step 1: Create the actual class**

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

private const val TAG = "SsoProvider"

actual class SsoCredentialProvider(
    private val context: Context,
    private val activityHolder: CurrentActivityHolder,
    private val webClientId: String,
) {

    actual suspend fun getGoogleIdToken(): Result<String, SsoError> {
        val activity = activityHolder.activity
            ?: return Result.Error(SsoError.UNKNOWN)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
        return try {
            val response = CredentialManager.create(context).getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.Success(credential.idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.Error(SsoError.CANCELLED)
        } catch (e: NoCredentialException) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in: no credential on device" }
            Result.Error(SsoError.NO_PROVIDER)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in failed" }
            Result.Error(SsoError.UNKNOWN)
        }
    }

    actual suspend fun getAppleCredential(): Result<AppleCredential, SsoError> =
        Result.Error(SsoError.NO_PROVIDER)
}
```

- [ ] **Step 2: Wire in PlatformModule.android**

Replace the contents of `PlatformModule.android.kt` so the imports + module block become:

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.BuildConfig
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { OnboardingPreferences(androidContext()) } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences(androidContext()) } bind MeasurementPreferencesStore::class
    single { OrderReceiptSharer(androidContext()) }
    single { WhatsAppLauncher(androidContext()) }
    single { DialerLauncher(androidContext()) }
    single { CurrentActivityHolder() }
    single {
        SsoCredentialProvider(
            context = androidContext(),
            activityHolder = get(),
            webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
    }
}
```

- [ ] **Step 3: Smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.android.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt
git commit -m "feat(auth): Android SsoCredentialProvider (Google) + DI wiring"
```

---

## Task 9: Swift bridge for presenting view controller (iOS)

**Files:**
- Create: `iosApp/iosApp/SsoPresentingViewControllerBridge.swift`
- Modify: `iosApp/iosApp/iOSApp.swift`

- [ ] **Step 1: Create the Swift bridge**

Create `iosApp/iosApp/SsoPresentingViewControllerBridge.swift`:

```swift
import UIKit

/// Static bridge so KMP iOS-side actuals can reach the foreground UIViewController.
/// SwiftUI root sets this on appear; KMP reads it when launching the OS sign-in sheet.
///
/// Holds a weak reference so SwiftUI lifecycle doesn't leak.
@objc public class SsoPresentingViewControllerBridge: NSObject {
    @objc public static weak var presentingViewController: UIViewController?
}
```

- [ ] **Step 2: Wire it from iOSApp.swift root**

Read the current `iosApp/iosApp/iOSApp.swift`. Inside the SwiftUI root view (typically `ContentView` or wherever you do `.onAppear`), find a `UIViewController` to expose. Easiest path: use a `UIViewControllerRepresentable` or a `View.background(...)` with a helper. Add to `iOSApp.swift`:

```swift
import SwiftUI
import GoogleSignIn  // ensures the SDK is linked

@main
struct iOSApp: App {
    init() {
        // GIDSignIn config — clientId is read from GoogleService-Info.plist's CLIENT_ID
        if let clientId = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .background(PresentingViewControllerProvider())
        }
    }
}

private struct PresentingViewControllerProvider: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = UIViewController()
        DispatchQueue.main.async {
            SsoPresentingViewControllerBridge.presentingViewController =
                vc.view.window?.rootViewController
        }
        return vc
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

If `Info.plist` doesn't have `GIDClientID` yet, add it: copy the `CLIENT_ID` value from `GoogleService-Info.plist`.

- [ ] **Step 3: Smoke build iOS**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17 Pro Max" -configuration Debug build 2>&1 | tail -5`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add iosApp/iosApp/SsoPresentingViewControllerBridge.swift iosApp/iosApp/iOSApp.swift iosApp/iosApp/Info.plist
git commit -m "feat(ios): add SsoPresentingViewControllerBridge + GIDSignIn config"
```

---

## Task 10: IosSsoCredentialProvider — Google only

**Files:**
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.ios.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt`

- [ ] **Step 1: Create the actual class (Google path, Apple stubbed)**

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import cocoapods.GoogleSignIn.GIDSignIn  // or via swift-interop if not Cocoapods
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

private const val TAG = "SsoProvider"

actual class SsoCredentialProvider(
    private val presentingViewController: () -> UIViewController?,
) {

    actual suspend fun getGoogleIdToken(): Result<String, SsoError> {
        val vc = presentingViewController()
            ?: return Result.Error(SsoError.UNKNOWN)
        return suspendCancellableCoroutine { cont ->
            GIDSignIn.sharedInstance.signInWithPresentingViewController(vc) { result, error ->
                when {
                    error != null -> {
                        AppLogger.e(tag = TAG) { "Google sign-in error: ${error.localizedDescription}" }
                        // GIDSignInError.canceled is code -5
                        if (error.code == -5L) cont.resume(Result.Error(SsoError.CANCELLED))
                        else cont.resume(Result.Error(SsoError.UNKNOWN))
                    }
                    result?.user?.idToken?.tokenString != null ->
                        cont.resume(Result.Success(result.user.idToken!!.tokenString))
                    else ->
                        cont.resume(Result.Error(SsoError.UNKNOWN))
                }
            }
        }
    }

    actual suspend fun getAppleCredential(): Result<AppleCredential, SsoError> {
        // Implemented in Task 16. Returns UNKNOWN for now so the iOS Apple button
        // produces a useful error rather than hanging.
        return Result.Error(SsoError.UNKNOWN)
    }
}
```

> **Note on cinterop:** if the project uses SwiftPM (not CocoaPods) for `GoogleSignIn-iOS`, the import path may be different — the SwiftPM dep adds a Swift module, not a CocoaPods Klib. You may need to:
> 1. Wrap `GIDSignIn` calls in a small Swift file (`SsoGoogleBridge.swift`) and call that from Kotlin via the standard objc-interop, or
> 2. Add a cocoapod for `GoogleSignIn` in `composeApp/build.gradle.kts` cocoapods block.
>
> Check whether the iosApp uses CocoaPods or pure SwiftPM. If pure SwiftPM, prefer option 1 (Swift bridge file) — write `SsoGoogleBridge.swift` exposing `@objc class func signIn(_:completion:)` and call from Kotlin via `cinterop` headers or via the `objcnames` of the Swift `@objc` class.

- [ ] **Step 2: Wire in PlatformModule.ios**

Replace the contents of `PlatformModule.ios.kt`:

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.UIKit.UIApplication

actual val platformModule: Module = module {
    single { OnboardingPreferences() } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences() } bind MeasurementPreferencesStore::class
    single { OrderReceiptSharer() }
    single { WhatsAppLauncher() }
    single { DialerLauncher() }
    single {
        SsoCredentialProvider(
            presentingViewController = {
                // Reads the static set by the SwiftUI root in iOSApp.swift.
                // Falls back to UIApplication.sharedApplication.keyWindow's rootVC
                // if the SwiftUI-side bridge hasn't fired yet.
                cocoapods.iosApp.SsoPresentingViewControllerBridge.presentingViewController()
                    ?: UIApplication.sharedApplication.keyWindow?.rootViewController()
            }
        )
    }
}
```

> **Note on the `cocoapods.iosApp.*` import path:** the exact name depends on how the iOS Swift code is exposed to Kotlin/Native. Two common patterns in this codebase:
>
> 1. **CocoaPods integration** (Kotlin's `cocoapods` block in `composeApp/build.gradle.kts`): import as `cocoapods.iosApp.SsoPresentingViewControllerBridge`.
> 2. **Plain Swift bridge via objc-interop** (no cocoapods for our own Swift code): import as `iosApp.SsoPresentingViewControllerBridge` or via the generated `objcnames`.
>
> If the build fails on unresolved reference, run `./gradlew :composeApp:compileKotlinIosSimulatorArm64` once — the error message will name the correct import path. If neither works, add a `@objc public class func currentVC() -> UIViewController?` static to `SsoPresentingViewControllerBridge.swift` (Task 9) and call via the simpler name.

- [ ] **Step 3: Smoke compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. If `GIDSignIn` import fails, see the Swift-bridge fallback in Step 1's note.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.ios.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt
git commit -m "feat(auth): iOS SsoCredentialProvider (Google) + DI wiring"
```

---

## Task 11: AuthRepository contract additions + UserRepository.deleteUserDoc

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`

- [ ] **Step 1: Read current AuthRepository**

Run: `cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt`

- [ ] **Step 2: Add three SSO methods**

Edit `AuthRepository.kt` — the final interface:

```kotlin
package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User

interface AuthRepository {
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User, AuthError>
    suspend fun signInWithEmail(email: String, password: String): Result<User, AuthError>
    suspend fun signInWithGoogle(): Result<User, AuthError>
    suspend fun signInWithApple(): Result<User, AuthError>
    suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError>
    suspend fun signOut(): Result<Unit, AuthError>
    suspend fun deleteAccount(): EmptyResult<AuthError>
    suspend fun getCurrentUser(): User?
    val isLoggedIn: Boolean
}
```

- [ ] **Step 3: Add deleteUserDoc to UserRepository**

Read current contract:

Run: `cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`

Add the method to the interface (keep all existing methods, add one):

```kotlin
suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network>
```

- [ ] **Step 4: Smoke compile (will fail — impls don't exist yet)**

Run: `./gradlew :composeApp:compileCommonMainKotlinMetadata`
Expected: FAIL — `FirebaseAuthRepository` and `FirebaseUserRepository` and `FakeAuthRepository` and `FakeUserRepository` no longer satisfy their interfaces. We fix in the next tasks; for now we just commit the contract.

- [ ] **Step 5: Add stub impls to silence compiler (temporary)**

Add to `FirebaseAuthRepository.kt`:

```kotlin
override suspend fun signInWithGoogle(): Result<User, AuthError> =
    Result.Error(AuthError.UNKNOWN) // Implemented in Task 13
override suspend fun signInWithApple(): Result<User, AuthError> =
    Result.Error(AuthError.UNKNOWN) // Implemented in Task 17
override suspend fun deleteAccount(): EmptyResult<AuthError> =
    Result.Error(AuthError.UNKNOWN) // Implemented in Task 21
```

Add to `FirebaseUserRepository.kt`:

```kotlin
override suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network> {
    return try {
        firestore.collection("users").document(userId).delete()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "deleteUserDoc failed userId=$userId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}
```

(This one is a real impl since it's trivial — Firestore one-liner.)

- [ ] **Step 6: Smoke compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: FAIL on `FakeAuthRepository` and `FakeUserRepository` missing overrides. Continue.

- [ ] **Step 7: Add stub impls to `FakeAuthRepository`**

In `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeAuthRepository.kt`, add:

```kotlin
override suspend fun signInWithGoogle(): Result<User, AuthError> {
    shouldReturnError?.let { return Result.Error(it) }
    val user = currentUser ?: User(
        id = "test-google-uid",
        email = "google@example.com",
        displayName = "Google User",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0
    )
    currentUser = user
    return Result.Success(user)
}

override suspend fun signInWithApple(): Result<User, AuthError> {
    shouldReturnError?.let { return Result.Error(it) }
    val user = currentUser ?: User(
        id = "test-apple-uid",
        email = "apple@privaterelay.appleid.com",
        displayName = "Apple User",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0
    )
    currentUser = user
    return Result.Success(user)
}

var deleteAccountInvocationCount = 0
override suspend fun deleteAccount(): EmptyResult<AuthError> {
    deleteAccountInvocationCount++
    shouldReturnError?.let { return Result.Error(it) }
    currentUser = null
    return Result.Success(Unit)
}
```

- [ ] **Step 8: Add stub impl to `FakeUserRepository`**

```kotlin
var deletedUserId: String? = null

override suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network> {
    deletedUserId = userId
    return Result.Success(Unit)
}
```

- [ ] **Step 9: Smoke compile + tests**

Run: `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeAuthRepository.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeUserRepository.kt
git commit -m "feat(auth): extend repository contracts for SSO + account deletion"
```

---

## Task 12: FakeSsoCredentialProvider for tests

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeSsoCredentialProvider.kt`

> **Note:** `SsoCredentialProvider` is an `expect class` — KMP doesn't allow direct subclassing of expect classes for tests. We use a workaround: define the test fake as a separate class that implements an interface, and have the repository code take either the expect class **or** the interface. For this codebase, the cleaner approach is to make `SsoCredentialProvider` an `interface` (not expect class) and have `AndroidSsoCredentialProvider` / `IosSsoCredentialProvider` implement it. Below assumes we keep the expect class and use a small adapter.

- [ ] **Step 1: Refactor — convert to interface**

Actually, simplify: replace `expect class SsoCredentialProvider` with an interface, and have each platform class implement it. This is more testable.

Edit `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.kt`:

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

interface SsoCredentialProvider {
    suspend fun getGoogleIdToken(): Result<String, SsoError>
    suspend fun getAppleCredential(): Result<AppleCredential, SsoError>
}
```

- [ ] **Step 2: Update Android impl to implement, not actualize**

Edit `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.android.kt` — rename the file to `AndroidSsoCredentialProvider.kt` and replace the class signature:

```kotlin
class AndroidSsoCredentialProvider(
    private val context: Context,
    private val activityHolder: CurrentActivityHolder,
    private val webClientId: String,
) : SsoCredentialProvider {

    override suspend fun getGoogleIdToken(): Result<String, SsoError> { /* same as before */ }
    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> = Result.Error(SsoError.NO_PROVIDER)
}
```

Update `PlatformModule.android.kt` to bind: `single<SsoCredentialProvider> { AndroidSsoCredentialProvider(androidContext(), get(), BuildConfig.GOOGLE_WEB_CLIENT_ID) }`.

- [ ] **Step 3: Update iOS impl similarly**

Rename `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/SsoCredentialProvider.ios.kt` to `IosSsoCredentialProvider.kt`:

```kotlin
class IosSsoCredentialProvider(
    private val presentingViewController: () -> UIViewController?,
) : SsoCredentialProvider {
    /* same impls, but `override` instead of `actual` */
}
```

Update `PlatformModule.ios.kt` accordingly.

- [ ] **Step 4: Create FakeSsoCredentialProvider**

```kotlin
package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

class FakeSsoCredentialProvider : SsoCredentialProvider {
    var googleResult: Result<String, SsoError> = Result.Success("fake-google-id-token")
    var appleResult: Result<AppleCredential, SsoError> =
        Result.Success(AppleCredential(idToken = "fake-apple-id-token", rawNonce = "fake-nonce", fullName = "Apple Tester"))
    var googleCallCount = 0
    var appleCallCount = 0

    override suspend fun getGoogleIdToken(): Result<String, SsoError> {
        googleCallCount++
        return googleResult
    }

    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> {
        appleCallCount++
        return appleResult
    }
}
```

- [ ] **Step 5: Smoke compile + tests**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/
git commit -m "refactor(auth): convert SsoCredentialProvider to interface + add FakeSsoCredentialProvider"
```

---

## Task 13: FirebaseAuthRepository.signInWithGoogle — TDD

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt`

> **Note on testing FirebaseAuthRepository directly:** the gitlive `FirebaseAuth` SDK is hard to mock in commonTest. Pragmatic approach used elsewhere in this codebase: test the ViewModel paths through `FakeAuthRepository` (which already exists), and rely on manual smoke testing for the repository itself. We follow the same pattern here — no unit test for `signInWithGoogle` directly; manual smoke covers it.

- [ ] **Step 1: Inject SsoCredentialProvider into the repository**

Edit `FirebaseAuthRepository.kt` constructor:

```kotlin
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val ssoCredentialProvider: SsoCredentialProvider,
) : AuthRepository {
```

- [ ] **Step 2: Implement signInWithGoogle**

Replace the stub from Task 11:

```kotlin
override suspend fun signInWithGoogle(): Result<User, AuthError> {
    return when (val tokenResult = ssoCredentialProvider.getGoogleIdToken()) {
        is Result.Error -> Result.Error(tokenResult.error.toAuthError())
        is Result.Success -> exchangeGoogleToken(tokenResult.data)
    }
}

private suspend fun exchangeGoogleToken(idToken: String): Result<User, AuthError> {
    return try {
        val credential = GoogleAuthProvider.credential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential)
        val user = authResult.user
            ?: return Result.Error(AuthError.UNKNOWN)
        Result.Success(user.toDomainUser())
    } catch (e: FirebaseAuthUserCollisionException) {
        AppLogger.e(tag = TAG, throwable = e) { "Google sign-in collision" }
        Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "Google credential exchange failed" }
        Result.Error(e.toAuthError())
    }
}

private fun SsoError.toAuthError(): AuthError = when (this) {
    SsoError.CANCELLED -> AuthError.SSO_CANCELLED
    SsoError.NO_PROVIDER -> AuthError.SSO_UNAVAILABLE
    SsoError.NETWORK -> AuthError.NETWORK_ERROR
    SsoError.UNKNOWN -> AuthError.UNKNOWN
}
```

Imports to add at the top of the file:

```kotlin
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.GoogleAuthProvider
```

- [ ] **Step 3: Update Koin wiring**

Edit `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt`:

```kotlin
singleOf(::FirebaseAuthRepository) bind AuthRepository::class
```

Koin's `singleOf` should resolve `SsoCredentialProvider` from `platformModule`. Confirm by smoke compile.

- [ ] **Step 4: Smoke compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt
git commit -m "feat(auth): implement FirebaseAuthRepository.signInWithGoogle"
```

---

## Task 14: LoginViewModel + SignUpViewModel — Google flow + tests

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModelTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelTest.kt`

- [ ] **Step 1: Write the failing test (Login Google success)**

In `LoginViewModelTest.kt`, replace the existing `OnGoogleSignInClick emits ShowComingSoon` test with:

```kotlin
@Test
fun `OnGoogleSignInClick on success emits NavigateToHome and clears isSsoLoading`() = runTest {
    viewModel.onAction(LoginAction.OnGoogleSignInClick)
    runCurrent()

    val event = viewModel.events.first()
    assertIs<LoginEvent.NavigateToHome>(event)
    assertFalse(viewModel.state.value.isSsoLoading)
}

@Test
fun `OnGoogleSignInClick on cancellation does not emit and clears isSsoLoading`() = runTest {
    fakeAuth.shouldReturnError = AuthError.SSO_CANCELLED
    viewModel.onAction(LoginAction.OnGoogleSignInClick)
    runCurrent()

    assertFalse(viewModel.state.value.isSsoLoading)
    // No event should be emitted for cancellation (silent)
}

@Test
fun `OnGoogleSignInClick on collision emits ShowError`() = runTest {
    fakeAuth.shouldReturnError = AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER
    viewModel.onAction(LoginAction.OnGoogleSignInClick)
    runCurrent()

    val event = viewModel.events.first()
    assertIs<LoginEvent.ShowError>(event)
}
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*LoginViewModelTest*OnGoogleSignInClick*"`
Expected: FAIL — the VM still emits `ShowComingSoon`.

- [ ] **Step 3: Update LoginViewModel handler**

Replace the `OnGoogleSignInClick` branch in `LoginViewModel.kt`:

```kotlin
LoginAction.OnGoogleSignInClick -> viewModelScope.launch {
    _state.update { it.copy(isSsoLoading = true) }
    when (val result = authRepository.signInWithGoogle()) {
        is Result.Success -> _events.send(LoginEvent.NavigateToHome)
        is Result.Error -> {
            if (result.error != AuthError.SSO_CANCELLED) {
                _events.send(LoginEvent.ShowError(result.error.toUiText()))
            }
        }
    }
    _state.update { it.copy(isSsoLoading = false) }
}
```

Ensure `AuthError.toUiText()` extension handles the new cases — add to `feature/auth/presentation/AuthErrorExtensions.kt` (or wherever `toUiText` lives — search for it):

```kotlin
AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER -> UiText.StringResourceText(Res.string.error_sso_email_collision)
AuthError.REQUIRES_RECENT_LOGIN -> UiText.StringResourceText(Res.string.error_requires_recent_login)
AuthError.SSO_CANCELLED -> UiText.StringResourceText(Res.string.error_sso_cancelled) // unused, kept for completeness
AuthError.SSO_UNAVAILABLE -> UiText.StringResourceText(Res.string.error_sso_unavailable)
```

- [ ] **Step 4: Add string resources**

In `composeApp/src/commonMain/composeResources/values/strings.xml` add:

```xml
<string name="error_sso_email_collision">This email is already registered. Sign in with email &amp; password.</string>
<string name="error_requires_recent_login">Please sign in again, then retry.</string>
<string name="error_sso_cancelled">Sign-in cancelled.</string>
<string name="error_sso_unavailable">This sign-in method is not available on this device.</string>
```

- [ ] **Step 5: Run tests to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*LoginViewModelTest*"`
Expected: PASS.

- [ ] **Step 6: Repeat for SignUpViewModel**

Mirror Steps 1–5 for `SignUpViewModel` — the only difference is the event emitted: signup's `OnGoogleSignInClick` should emit `SignUpEvent.NavigateToHome` (Firebase auto-creates the user; the splash router decides Home vs Workshop based on `hasCompletedWorkshopSetup`).

- [ ] **Step 7: Smoke compile + full test pass**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): wire Google sign-in through Login + Signup ViewModels"
```

---

## Task 15: First Google smoke test on real platform

**Files:** (no code; manual smoke)

- [ ] **Step 1: Install on Android emulator with a Google account**

Run: `./gradlew :composeApp:installDebug && adb shell am start -n com.danzucker.stitchpad/.MainActivity`
On the Login screen, tap `Continue with Google`. Expected: OS account picker → select an account → Firebase creates the user → splash routes to Workshop (first time) or Home.

- [ ] **Step 2: Verify Firebase Auth console**

Open Firebase console → Authentication → Users. Confirm the new Google user appears with the correct email and provider `google.com`.

- [ ] **Step 3: Smoke iOS**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17 Pro Max" build` + install + launch. Tap `Continue with Google` on Login. Expected: Safari opens for Google sign-in → returns to app → routes correctly.

- [ ] **Step 4: If anything is broken, fix and commit**

Common issues:
- Android: missing OAuth `serverClientId` → check `GOOGLE_WEB_CLIENT_ID` in BuildConfig matches `google-services.json`'s web client.
- iOS: `GIDSignIn` not initialized → check `iOSApp.swift` reads `GIDClientID` from Info.plist.
- iOS: URL scheme mismatch → check `CFBundleURLTypes` matches `REVERSED_CLIENT_ID`.

- [ ] **Step 5: No commit unless fixes needed**

If everything works, no commit. If fixes were needed, commit them with `fix(auth): <whatever>`.

---

## Task 16: Apple Sign-In iOS — Swift bridge for nonce + ASAuthorization

**Files:**
- Create: `iosApp/iosApp/SsoAppleBridge.swift`

- [ ] **Step 1: Create the Apple bridge**

The Apple Sign-In flow requires:
1. A cryptographic nonce (raw + SHA256-hashed for the request)
2. `ASAuthorizationAppleIDProvider` to build a request
3. `ASAuthorizationController` to present
4. A delegate that handles the credential or error

```swift
import AuthenticationServices
import CryptoKit
import Foundation

@objc public class SsoAppleBridge: NSObject {

    @objc public static func signIn(completion: @escaping (String?, String?, String?, String?) -> Void) {
        // Returns: (idTokenString, rawNonce, fullName?, errorMessage?)
        let rawNonce = randomNonceString()
        let hashedNonce = sha256(rawNonce)
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = hashedNonce

        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = AppleSignInDelegate(rawNonce: rawNonce, completion: completion)
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        // Retain delegate for the duration of the request
        objc_setAssociatedObject(controller, "delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        controller.performRequests()
    }

    private static func randomNonceString(length: Int = 32) -> String {
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            if status == errSecSuccess && random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

private class AppleSignInDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    let rawNonce: String
    let completion: (String?, String?, String?, String?) -> Void

    init(rawNonce: String, completion: @escaping (String?, String?, String?, String?) -> Void) {
        self.rawNonce = rawNonce
        self.completion = completion
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            completion(nil, nil, nil, "Missing identity token")
            return
        }
        let fullName = [cred.fullName?.givenName, cred.fullName?.familyName]
            .compactMap { $0 }.joined(separator: " ").nilIfEmpty()
        completion(token, rawNonce, fullName, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let nsError = error as NSError
        if nsError.code == ASAuthorizationError.canceled.rawValue {
            completion(nil, nil, nil, "CANCELLED")
        } else {
            completion(nil, nil, nil, nsError.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return SsoPresentingViewControllerBridge.presentingViewController?.view.window
            ?? UIApplication.shared.windows.first { $0.isKeyWindow }
            ?? ASPresentationAnchor()
    }
}

private extension String {
    func nilIfEmpty() -> String? { isEmpty ? nil : self }
}
```

- [ ] **Step 2: Smoke build iOS**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17 Pro Max" -configuration Debug build 2>&1 | tail -5`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add iosApp/iosApp/SsoAppleBridge.swift
git commit -m "feat(ios): add SsoAppleBridge with nonce generation + ASAuthorization delegate"
```

---

## Task 17: IosSsoCredentialProvider — Apple path

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/IosSsoCredentialProvider.kt`

- [ ] **Step 1: Implement getAppleCredential**

Replace the stub from Task 10 Step 1 with the real impl. Calls into the Swift bridge via objc-interop:

```kotlin
override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> {
    return suspendCancellableCoroutine { cont ->
        cocoapods.iosApp.SsoAppleBridge.signIn { idToken, rawNonce, fullName, errorMessage ->
            when {
                errorMessage == "CANCELLED" -> cont.resume(Result.Error(SsoError.CANCELLED))
                errorMessage != null -> {
                    AppLogger.e(tag = TAG) { "Apple sign-in error: $errorMessage" }
                    cont.resume(Result.Error(SsoError.UNKNOWN))
                }
                idToken != null && rawNonce != null ->
                    cont.resume(Result.Success(AppleCredential(idToken, rawNonce, fullName)))
                else ->
                    cont.resume(Result.Error(SsoError.UNKNOWN))
            }
        }
    }
}
```

> **Note:** the exact import path for `SsoAppleBridge` depends on how the Swift bridge is exposed. If using SwiftPM only (no cocoapods), the `@objc public class SsoAppleBridge` is reachable via the generated objc-interop module name (typically `iosApp.SsoAppleBridge`). If using cocoapods, it's `cocoapods.iosApp.SsoAppleBridge`. Verify which path by trying to compile and reading the unresolved-reference error.

- [ ] **Step 2: Smoke compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/auth/data/IosSsoCredentialProvider.kt
git commit -m "feat(ios): wire SsoAppleBridge into IosSsoCredentialProvider"
```

---

## Task 18: FirebaseAuthRepository.signInWithApple

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt`

- [ ] **Step 1: Implement signInWithApple**

Replace the stub from Task 11:

```kotlin
override suspend fun signInWithApple(): Result<User, AuthError> {
    return when (val credResult = ssoCredentialProvider.getAppleCredential()) {
        is Result.Error -> Result.Error(credResult.error.toAuthError())
        is Result.Success -> exchangeAppleCredential(credResult.data)
    }
}

private suspend fun exchangeAppleCredential(cred: AppleCredential): Result<User, AuthError> {
    return try {
        val provider = OAuthProvider("apple.com")
        val firebaseCredential = provider.credential(cred.idToken, cred.rawNonce)
        val authResult = firebaseAuth.signInWithCredential(firebaseCredential)
        val firebaseUser = authResult.user
            ?: return Result.Error(AuthError.UNKNOWN)

        // Apple returns fullName only on the very first Sign-In. Seed displayName.
        if (firebaseUser.displayName.isNullOrBlank() && !cred.fullName.isNullOrBlank()) {
            runCatching { firebaseUser.updateProfile(displayName = cred.fullName) }
                .onFailure { AppLogger.e(tag = TAG, throwable = it) { "Apple displayName update failed" } }
        }

        Result.Success(firebaseUser.toDomainUser())
    } catch (e: FirebaseAuthUserCollisionException) {
        Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "Apple credential exchange failed" }
        Result.Error(e.toAuthError())
    }
}
```

Add imports: `import dev.gitlive.firebase.auth.OAuthProvider`. Verify the API shape in gitlive 1.x — if `OAuthProvider("apple.com").credential(...)` is not the right call, check the gitlive docs for the equivalent.

- [ ] **Step 2: Smoke compile both targets**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt
git commit -m "feat(auth): implement FirebaseAuthRepository.signInWithApple"
```

---

## Task 19: LoginViewModel + SignUpViewModel — Apple flow + tests

Same shape as Task 14 but for Apple. Replace `OnAppleSignInClick` handler in both VMs, replace the test case that asserts `ShowComingSoon`.

- [ ] **Step 1: Update Login + Signup Apple handlers**

```kotlin
LoginAction.OnAppleSignInClick -> viewModelScope.launch {
    _state.update { it.copy(isSsoLoading = true) }
    when (val result = authRepository.signInWithApple()) {
        is Result.Success -> _events.send(LoginEvent.NavigateToHome)
        is Result.Error -> {
            if (result.error != AuthError.SSO_CANCELLED) {
                _events.send(LoginEvent.ShowError(result.error.toUiText()))
            }
        }
    }
    _state.update { it.copy(isSsoLoading = false) }
}
```

- [ ] **Step 2: Update tests**

Replace `OnAppleSignInClick emits ShowComingSoon` in `LoginViewModelTest` and `SignUpViewModelTest` with success / cancellation / collision tests (mirror Task 14 Step 1).

- [ ] **Step 3: Run tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ViewModelTest*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): wire Apple sign-in through Login + Signup ViewModels"
```

---

## Task 20: Hide Apple button on Android

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/util/Platform.kt` (if doesn't exist)
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/util/Platform.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/util/Platform.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/SsoButtonRow.kt`

- [ ] **Step 1: Add Platform expect/actual**

If a `Platform` object doesn't already exist in this codebase (search first: `grep -rn "expect val isIos\|isAndroid" composeApp/src/`), create one.

`Platform.kt` (commonMain):

```kotlin
package com.danzucker.stitchpad.util

expect object Platform {
    val isIos: Boolean
}
```

`Platform.android.kt`:

```kotlin
package com.danzucker.stitchpad.util

actual object Platform {
    actual val isIos: Boolean = false
}
```

`Platform.ios.kt`:

```kotlin
package com.danzucker.stitchpad.util

actual object Platform {
    actual val isIos: Boolean = true
}
```

- [ ] **Step 2: Read current SsoButtonRow**

Run: `cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/SsoButtonRow.kt`

Identify the Apple button section.

- [ ] **Step 3: Wrap Apple button in `if (Platform.isIos)`**

In `SsoButtonRow.kt`, find the `AppleButton` (or equivalent composable call) and wrap:

```kotlin
if (Platform.isIos) {
    // existing Apple button code
}
```

- [ ] **Step 4: Smoke compile both targets**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): hide Apple sign-in button on Android"
```

---

## Task 21: FirebaseAuthRepository.deleteAccount

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt`

- [ ] **Step 1: Inject UserRepository**

Edit `FirebaseAuthRepository.kt` constructor to also take a `UserRepository`:

```kotlin
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val ssoCredentialProvider: SsoCredentialProvider,
    private val userRepository: UserRepository,
) : AuthRepository {
```

Update `AuthModule.kt` Koin wiring — `singleOf(::FirebaseAuthRepository)` should auto-resolve the new dep.

- [ ] **Step 2: Implement deleteAccount**

Replace the stub from Task 11:

```kotlin
override suspend fun deleteAccount(): EmptyResult<AuthError> {
    val user = firebaseAuth.currentUser
        ?: return Result.Error(AuthError.USER_NOT_FOUND)
    val uid = user.uid
    return try {
        // Auth-first: this is the App Store 4.8 requirement and the irreversible step.
        // If it throws REQUIRES_RECENT_LOGIN, the Firestore doc remains intact.
        user.delete()
        // Best-effort Firestore cleanup.
        runCatching { userRepository.deleteUserDoc(uid) }
            .onFailure { AppLogger.e(tag = TAG, throwable = it) { "post-delete Firestore cleanup failed uid=$uid" } }
        Result.Success(Unit)
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        Result.Error(AuthError.REQUIRES_RECENT_LOGIN)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "deleteAccount failed uid=$uid" }
        Result.Error(e.toAuthError())
    }
}
```

Add import: `import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException`.

- [ ] **Step 3: Smoke compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): implement FirebaseAuthRepository.deleteAccount (auth-first ordering)"
```

---

## Task 22: Delete account UI — dialog + bottom sheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/deleteaccount/DeleteAccountDialog.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add string resources**

Add to `strings.xml`:

```xml
<string name="settings_delete_account">Delete account</string>
<string name="settings_delete_account_dialog_title">Delete your account?</string>
<string name="settings_delete_account_dialog_body">You\'ll be signed out.</string>
<string name="settings_delete_account_dialog_data_link">What happens to my shop data?</string>
<string name="settings_delete_account_confirm">Delete account</string>
<string name="settings_delete_account_cancel">Cancel</string>
<string name="settings_delete_account_data_sheet_title">Your shop data</string>
<string name="settings_delete_account_data_sheet_body">Deleting your account signs you out and removes your StitchPad sign-in. Your shop\'s data — customers, orders, measurements, and payment history — is retained on our servers per our retention policy and is not removed by this action.\n\nIf you also want your shop data removed, contact support after deleting your account.</string>
<string name="settings_delete_account_data_sheet_dismiss">Got it</string>
<string name="settings_delete_account_success">Account deleted</string>
```

- [ ] **Step 2: Create DeleteAccountDialog composable**

```kotlin
package com.danzucker.stitchpad.feature.auth.presentation.deleteaccount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_delete_account_cancel
import stitchpad.composeapp.generated.resources.settings_delete_account_confirm
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_body
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_dismiss
import stitchpad.composeapp.generated.resources.settings_delete_account_data_sheet_title
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_body
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_data_link
import stitchpad.composeapp.generated.resources.settings_delete_account_dialog_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountDialog(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDataSheet by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_account_dialog_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.settings_delete_account_dialog_body))
                Text(
                    text = stringResource(Res.string.settings_delete_account_dialog_data_link),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesignTokens.primary400,
                    ),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showDataSheet = true },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.error500),
            ) {
                Text(stringResource(Res.string.settings_delete_account_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_delete_account_cancel))
            }
        },
    )

    if (showDataSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_delete_account_data_sheet_title),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                Text(stringResource(Res.string.settings_delete_account_data_sheet_body))
                Button(
                    onClick = { showDataSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.settings_delete_account_data_sheet_dismiss))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Smoke compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): add DeleteAccountDialog with disclosure bottom sheet"
```

---

## Task 23: Hook delete dialog into HomeScreen (or Settings if available)

**Files:**
- Determine: `feature/settings-redesign` worktree state
- Modify: home placeholder or Settings screen

- [ ] **Step 1: Check whether Settings has shipped**

Run: `git fetch origin main && git log --oneline origin/main | grep -i settings | head -3`
If a Settings screen is on main, target that. Otherwise, target the home placeholder.

- [ ] **Step 2: Find the target screen**

Run: `find composeApp/src/commonMain/kotlin -name "HomeScreen.kt" -o -name "SettingsScreen.kt" 2>/dev/null`

Open whichever exists.

- [ ] **Step 3: Add a `Delete account` row + state hook**

Add a `var showDeleteDialog by remember { mutableStateOf(false) }`, a delete-account button styled with error color, and the dialog:

```kotlin
TextButton(
    onClick = { showDeleteDialog = true },
    colors = ButtonDefaults.textButtonColors(contentColor = DesignTokens.error500),
) {
    Text(stringResource(Res.string.settings_delete_account))
}

if (showDeleteDialog) {
    DeleteAccountDialog(
        isLoading = state.isDeletingAccount,
        onConfirm = {
            onAction(SomeAction.OnDeleteAccountConfirm) // wire to your screen's actions
            showDeleteDialog = false
        },
        onDismiss = { showDeleteDialog = false },
    )
}
```

- [ ] **Step 4: Wire the action in the corresponding ViewModel**

```kotlin
SomeAction.OnDeleteAccountConfirm -> viewModelScope.launch {
    _state.update { it.copy(isDeletingAccount = true) }
    when (val result = authRepository.deleteAccount()) {
        is Result.Success -> {
            _events.send(SomeEvent.AccountDeleted)
        }
        is Result.Error -> {
            if (result.error == AuthError.REQUIRES_RECENT_LOGIN) {
                _events.send(SomeEvent.ReauthRequired)
            } else {
                _events.send(SomeEvent.ShowError(result.error.toUiText()))
            }
        }
    }
    _state.update { it.copy(isDeletingAccount = false) }
}
```

`AccountDeleted` event triggers navigation to login + a success snackbar.

- [ ] **Step 5: Smoke compile + tests**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/
git commit -m "feat(auth): add Delete account entry on home/settings screen"
```

---

## Task 24: End-to-end smoke + PR description

**Files:** (no code; manual)

- [ ] **Step 1: Manual smoke checklist on Android**

- [ ] Continue with Google → account picker → sign in → routes to Workshop (first time) / Home (returning)
- [ ] Same email but signing in with Google when previously email/password → snackbar "This email is already registered…"
- [ ] Delete account → confirmation dialog → tap "What happens to my shop data?" → bottom sheet appears → "Got it" dismisses → confirm delete → navigates to login + snackbar "Account deleted"
- [ ] Firebase Auth console: user removed; Firestore `users/{uid}` removed

- [ ] **Step 2: Manual smoke checklist on iOS**

- [ ] Continue with Google → Safari → sign in → returns to app → routes correctly
- [ ] Continue with Apple → Sign in with Apple sheet → Face ID / passcode → returns → first-time signup populates displayName
- [ ] Same email collision on Apple after email/password signup → snackbar
- [ ] Apple button hidden on Android (verify visually)
- [ ] Delete account on iOS: confirms re-auth requirement path works (sign out, sign back in, retry)

- [ ] **Step 3: Auto checks**

Run:

```bash
./gradlew :composeApp:allTests :composeApp:compileKotlinIosSimulatorArm64 detekt
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17 Pro Max" build
```

Expected: all green.

- [ ] **Step 4: Push branch + open PR**

```bash
git push -u origin feature/auth-phase3-sso
gh pr create --title "feat(auth): Phase 3 — Google + Apple SSO + account deletion" --body "<see spec>"
```

PR body draft lives in `docs/superpowers/specs/2026-05-11-auth-phase3-sso-design.md` (the spec). Copy the Goal + What's Included + What's NOT included + Verification + Manual smoke test plan sections into the PR body.

---

## Out of scope (reaffirmed)

- Apple Sign-In on Android (button hidden)
- Provider linking UI in Settings
- Cascading Firestore deletion (customers/orders/measurements) — follow-up Cloud Function
- Reauth flow for non-delete sensitive actions

## Done criteria

- [ ] All 24 tasks completed
- [ ] Android + iOS unit tests green
- [ ] iOS + Android smoke runs through Google sign-in successfully
- [ ] iOS smoke runs through Apple sign-in successfully (first sign-in populates displayName)
- [ ] Delete account flow removes Auth user + Firestore doc on both platforms
- [ ] Detekt 0 issues
- [ ] CI green on PR
