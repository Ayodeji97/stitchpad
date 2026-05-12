# Auth Phase 3 — Google + Apple Sign-In (+ account deletion)

**Status:** Design — pending approval
**Author:** Daniel Ogunleye (with Claude)
**Date:** 2026-05-11
**Phase:** Auth Phase 3 (final auth surface before Settings phone field)
**Depends on:** Phase 1 (login redesign) + Phase 2 (WhatsApp onboarding) — both merged on `main`

## Goal

Replace the `Continue with Google` / `Continue with Apple` "Coming soon" snackbar stubs on Login + Signup with real SSO flows, plus add in-app account deletion. After this PR, the app meets App Store Guideline 4.8 (any third-party SSO on iOS must have an Apple peer) and 5.1.1(v) (in-app account deletion required for accounts).

## Out of scope (intentional)

These are real follow-ups, listed so reviewers can flag if any should move in:

- **Apple Sign-In on Android.** App Store 4.8 only requires Apple-on-iOS. On Android the Apple button stays hidden; symmetric cross-platform support is a future enhancement (Firebase `OAuthProvider` web flow).
- **Provider linking UI.** A Settings entry to link Google to an existing email account, or unlink a provider. Out of scope; collision is handled by surfacing an error.
- **Reauthentication for other sensitive actions.** Only `deleteAccount()` triggers re-auth in this PR. Email change, password change, etc. follow later if needed.
- **Server-side cascading deletion.** This PR deletes `users/{uid}` only. Related Firestore data (customers, orders, measurements, etc.) is **not** deleted in this PR. The spec calls this out so product can decide whether to add a Cloud Function later or surface a "your data will remain" notice. Recommendation: add a Cloud Function in a follow-up; for now the in-app delete is GDPR-incomplete but App-Store-compliant (the auth account is gone).
- **Account merge / migration.** Users who previously signed up with email/password can't currently merge to a Google identity. They keep their existing account.

## High-level architecture

```
┌─ commonMain ──────────────────────────────────────────────┐
│                                                           │
│  expect class SsoCredentialProvider {                     │
│    suspend fun getGoogleIdToken(): Result<String, SsoErr> │
│    suspend fun getAppleCredential(): Result<AppleCred,    │
│                                              SsoErr>      │
│  }                                                        │
│                                                           │
│  data class AppleCredential(                              │
│    val idToken: String,                                   │
│    val rawNonce: String,                                  │
│    val fullName: String? // populated once, first sign-in │
│  )                                                        │
│                                                           │
│  AuthRepository (extended)                                │
│    + suspend fun signInWithGoogle(): Result<User, AuthErr>│
│    + suspend fun signInWithApple(): Result<User, AuthErr> │
│    + suspend fun deleteAccount(): EmptyResult<AuthError>  │
│                                                           │
│  FirebaseAuthRepository                                   │
│    consumes SsoCredentialProvider                         │
│    orchestrates Firebase signInWithCredential + delete    │
│                                                           │
└───────────────────────────────────────────────────────────┘
            │                              │
            ▼                              ▼
┌─ androidMain ────────────────┐  ┌─ iosMain ──────────────────┐
│  AndroidSsoCredentialProvider│  │  IosSsoCredentialProvider  │
│  - Credential Manager        │  │  - GoogleSignIn-iOS SDK    │
│  - GoogleIdOption            │  │  - AuthenticationServices  │
│  - activityProvider() -> Act │  │    (ASAuthorizationCtl)    │
│  - getAppleCred = throws     │  │  - presentingViewController│
│                              │  │    bridge from Swift       │
└──────────────────────────────┘  └────────────────────────────┘
```

**Why this split:** keeps `FirebaseAuthRepository` Firebase-only (no platform UI knowledge), keeps platform UI concerns in `iosMain`/`androidMain`, and matches the existing `WhatsAppLauncher` / `DialerLauncher` / `PhoneNormaliser` patterns in the codebase.

## Component design

### `commonMain` — `SsoCredentialProvider`

`expect class` injected via Koin. Each platform's `actual` returns either an OAuth ID token (Google) or an Apple credential bundle. Errors are surfaced as a typed `SsoError` enum:

```kotlin
enum class SsoError {
    CANCELLED,                  // user dismissed the OS sheet
    NO_PROVIDER,                // Apple on Android, etc.
    NETWORK,
    UNKNOWN
}
```

### `commonMain` — Repository contract additions

```kotlin
interface AuthRepository {
    // existing email/password methods unchanged

    suspend fun signInWithGoogle(): Result<User, AuthError>
    suspend fun signInWithApple(): Result<User, AuthError>
    suspend fun deleteAccount(): EmptyResult<AuthError>
}
```

`AuthError` enum gains two new cases:

```kotlin
enum class AuthError {
    // existing cases unchanged
    EMAIL_REGISTERED_WITH_OTHER_PROVIDER,
    REQUIRES_RECENT_LOGIN,
    SSO_CANCELLED,
    SSO_UNAVAILABLE
}
```

### `commonMain` — `FirebaseAuthRepository` extensions

```kotlin
override suspend fun signInWithGoogle(): Result<User, AuthError> {
    val tokenResult = credentialProvider.getGoogleIdToken()
    if (tokenResult is Result.Error) return tokenResult.toAuthResult()
    val token = (tokenResult as Result.Success).data
    return try {
        val credential = GoogleAuthProvider.credential(token, null)
        val authResult = firebaseAuth.signInWithCredential(credential)
        Result.Success(authResult.user!!.toDomainUser())
    } catch (e: FirebaseAuthUserCollisionException) {
        Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }
}
```

`signInWithApple()` mirrors this with `OAuthProvider.credential("apple.com", appleCred.idToken, appleCred.rawNonce)`. After first Apple sign-in, if `appleCred.fullName != null` and the Firebase user's `displayName` is null, update it via `user.updateProfile { this.displayName = ... }`.

`deleteAccount()`:

```kotlin
override suspend fun deleteAccount(): EmptyResult<AuthError> {
    val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.SESSION_EXPIRED)
    val uid = user.uid
    return try {
        // Order matters: delete the auth account FIRST. If it throws
        // REQUIRES_RECENT_LOGIN, the Firestore doc is still intact and
        // the user can re-auth and retry. Reverse order would leave an
        // orphan auth account pointing at deleted Firestore data.
        user.delete()
        // Best-effort Firestore cleanup. If this fails, log and continue —
        // auth account is gone (this is the App Store 4.8 requirement); the
        // doc becomes orphaned data, which is acceptable per our scope
        // and will be cleaned up by the planned Cloud Function follow-up.
        runCatching { userRepository.deleteUserDoc(uid) }
            .onFailure { AppLogger.e(tag = TAG, throwable = it) { "post-delete Firestore cleanup failed uid=$uid" } }
        Result.Success(Unit)
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        Result.Error(AuthError.REQUIRES_RECENT_LOGIN)
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }
}
```

**Order rationale:** delete the Auth account first because that's the App-Store-4.8 requirement and the irreversible step. Firestore cleanup is best-effort — if it fails, we have orphaned data (covered in out-of-scope and acceptable), but the user's auth identity is gone. Reverse order risks the worst outcome: Firestore doc deleted but auth account still pointing at a non-existent profile, leaving the user in a broken state on next sign-in.

### `commonMain` — `UserRepository.deleteUserDoc()`

New method. Just deletes the `users/{uid}` doc. Subcollections (if any) are not cascaded — this is the in-scope behaviour. Documented in the out-of-scope section.

### Android — `AndroidSsoCredentialProvider`

```kotlin
class AndroidSsoCredentialProvider(
    private val context: Context,
    private val activityProvider: () -> Activity?,
    private val webClientId: String, // from google-services.json
) : SsoCredentialProvider {

    override suspend fun getGoogleIdToken(): Result<String, SsoError> {
        val activity = activityProvider() ?: return Result.Error(SsoError.UNKNOWN)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
        return try {
            val result = CredentialManager.create(context).getCredential(activity, request)
            val googleIdCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            Result.Success(googleIdCredential.idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.Error(SsoError.CANCELLED)
        } catch (e: NoCredentialException) {
            Result.Error(SsoError.NO_PROVIDER)
        } catch (e: Exception) {
            AppLogger.e(tag = "SsoProvider", throwable = e) { "Google sign-in failed" }
            Result.Error(SsoError.UNKNOWN)
        }
    }

    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> =
        Result.Error(SsoError.NO_PROVIDER)
}
```

**`activityProvider` setup:** `MainActivity` registers itself in `onCreate` via a Koin-managed `CurrentActivityHolder` (weak reference); the Koin module wires `activityProvider = { holder.current }` when constructing the provider.

### iOS — `IosSsoCredentialProvider`

```kotlin
class IosSsoCredentialProvider(
    private val presentingViewController: () -> UIViewController?,
    private val googleClientId: String, // from GoogleService-Info.plist
) : SsoCredentialProvider {

    override suspend fun getGoogleIdToken(): Result<String, SsoError> {
        val vc = presentingViewController() ?: return Result.Error(SsoError.UNKNOWN)
        return suspendCancellableCoroutine { cont ->
            GIDSignIn.sharedInstance.signInWithPresenting(vc) { result, error ->
                when {
                    error != null && error.code == kGIDSignInErrorCodeCanceled ->
                        cont.resume(Result.Error(SsoError.CANCELLED))
                    error != null ->
                        cont.resume(Result.Error(SsoError.UNKNOWN))
                    result?.user?.idToken?.tokenString != null ->
                        cont.resume(Result.Success(result.user.idToken.tokenString))
                    else ->
                        cont.resume(Result.Error(SsoError.UNKNOWN))
                }
            }
        }
    }

    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> {
        // ASAuthorizationAppleIDProvider + ASAuthorizationController
        // Generate a cryptographic nonce, hash it for the request, pass raw nonce to Firebase
        // ...
    }
}
```

**`presentingViewController` setup:** Swift code in `iosApp` (the `ContentView` or root) exposes a `setPresentingViewController(UIViewController?)` static, called from `onAppear`. The Koin iOS module wires `presentingViewController = { SwiftBridge.currentVC }`.

### `commonMain` — ViewModel changes

`LoginViewModel` and `SignUpViewModel` replace the `ShowComingSoon` stub:

```kotlin
LoginAction.OnGoogleSignInClick -> viewModelScope.launch {
    _state.update { it.copy(isSsoLoading = true) }
    when (val result = authRepository.signInWithGoogle()) {
        is Result.Success -> _events.send(LoginEvent.NavigateToHome)
        is Result.Error -> _events.send(LoginEvent.ShowError(result.error.toUiText()))
    }
    _state.update { it.copy(isSsoLoading = false) }
}
```

Same shape for Apple, except wrapped in a platform guard:

```kotlin
LoginAction.OnAppleSignInClick -> {
    if (!Platform.supportsAppleSignIn) { ...do nothing or hide button at composable level... }
    // else same as Google but signInWithApple()
}
```

(In practice the Apple button is hidden on Android via `SsoButtonRow(showApple = Platform.isIos)`.)

### Settings — account deletion

A new `Settings` screen entry (or wherever the existing settings live; check `feature/settings-redesign` worktree before implementation):

- `Delete account` row, error-color text
- On tap: short, scannable confirm dialog with an inline disclosure link (the modern Apple / Google pattern — short and respectful, with details one tap away for users who want them):

  > **Delete your account?**
  >
  > You'll be signed out. [What happens to my shop data?](#)
  >
  > [ Cancel ] [ Delete account ]

  The "What happens to my shop data?" is a tappable link styled like `DesignTokens.primary400`. On tap, opens a small bottom sheet (`ModalBottomSheet`) containing the full disclosure:

  > **Your shop data**
  >
  > Deleting your account signs you out and removes your StitchPad sign-in. Your shop's data — customers, orders, measurements, and payment history — is retained on our servers per our retention policy and is not removed by this action.
  >
  > If you also want your shop data removed, contact support after deleting your account.
  >
  > [ Got it ]

  Rationale: the bottom sheet keeps the dialog scannable while preserving the same legal disclosure. Most users won't tap the link; the ones who care get the full picture. The re-auth step that Firebase forces (`REQUIRES_RECENT_LOGIN`) acts as a natural second confirmation for free, so the dialog itself doesn't need to nag.

- On confirm: call `authRepository.deleteAccount()`
- On success: navigate to login, show snackbar "Account deleted"
- On `REQUIRES_RECENT_LOGIN`: trigger fresh SSO (same provider), retry once. If that fails, snackbar "Please sign in again, then retry."

**String resources to add (commonMain/composeResources/values/strings.xml):**

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
<string name="settings_delete_account_reauth">Please sign in again, then retry.</string>
```

**iOS modal bottom sheet timing note (memory `feedback_ios_modal_bottom_sheet_timing`):** because the data-disclosure bottom sheet can be opened and dismissed before the user confirms deletion, and the SSO re-auth flow may need to present a UIKit sheet (`ASAuthorizationController`) right after, add a `delay(~450ms)` between the bottom sheet's `onDismiss` and any subsequent UIKit presentation. Without it, UIKit's `present()` silently fails.

**Settings screen coordination:** Daniel has an in-flight `feature/settings-redesign` worktree at `/Users/danzucker/Desktop/Project/StitchPad`. Two paths:

- **If `feature/settings-redesign` is merged to main by the time this Phase 3 PR starts:** add the Delete account row to the real Settings screen as a new entry. Tasks plan should explicitly check `origin/main` for a Settings screen at implementation kickoff.
- **If `feature/settings-redesign` is still in flight:** put the bare minimum in this PR — add `Delete account` (plus the existing `Sign Out`) to the home-placeholder screen at `feature/home/HomeScreen.kt`. Mark with a code comment that it should move to Settings once that branch lands. This keeps Phase 3 unblocked.

The implementation plan should decide which path applies at kickoff time and not block on the other PR.

## Data flow

**Google sign-in (happy path):**

1. User taps `Continue with Google` on Login
2. `LoginViewModel.onAction(OnGoogleSignInClick)` → sets `isSsoLoading = true`
3. `authRepository.signInWithGoogle()` →
   1. `credentialProvider.getGoogleIdToken()` — opens OS account picker, returns token
   2. `firebaseAuth.signInWithCredential(GoogleAuthProvider.credential(token, null))`
   3. Wraps result `User` and returns
4. VM emits `NavigateToHome` (or `NavigateToWorkshop` based on existing splash logic — actually splash handles this; VM just emits NavigateToHome and the router decides)
5. `isSsoLoading = false`

**First-time SSO user → Workshop:**

The existing splash/router logic already routes to `WorkshopSetupRoute` if `onboardingPreferences.hasCompletedWorkshopSetup() == false`. New SSO signups have not completed workshop, so they land there naturally. No new VM logic needed.

`displayName` from the provider is preserved on the Firebase Auth user; the workshop screen doesn't use it (workshop only collects `businessName` + `whatsappNumber`), but it's available later from `userRepository.getUser(uid)`.

**Apple first sign-in name persistence:**

Apple's `ASAuthorizationAppleIDCredential` returns `fullName` only on the first sign-in ever (across all of Apple's history with this app). `IosSsoCredentialProvider` packages it into `AppleCredential`. `FirebaseAuthRepository.signInWithApple()`, after a successful Firebase sign-in, checks if `firebaseAuth.currentUser?.displayName.isNullOrBlank() && appleCred.fullName != null` and calls `user.updateProfile { this.displayName = appleCred.fullName }`.

**Collision (email already registered with another provider):**

1. User signs in with Google for an email that already exists in Firebase (e.g., they signed up with email/password before)
2. `firebaseAuth.signInWithCredential` throws `FirebaseAuthUserCollisionException`
3. Repo catches, maps to `AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER`
4. VM surfaces a snackbar: "This email is already registered. Sign in with email & password."

**Account deletion:**

1. User taps Delete account → confirm dialog → confirm
2. `authRepository.deleteAccount()` →
   1. `userRepository.deleteUserDoc(uid)` — Firestore doc gone
   2. `firebaseAuth.currentUser!!.delete()` — Auth account gone
   3. Returns Success
3. VM emits `NavigateToLogin` + snackbar
4. If `REQUIRES_RECENT_LOGIN`: re-prompt SSO with the original provider, retry once

## Error handling

| Scenario | `AuthError` | UI |
|---|---|---|
| User cancels OS picker | `SSO_CANCELLED` | No snackbar (silent — user knows they cancelled) |
| Network failure during token exchange | `NETWORK` | Existing "No internet" snackbar |
| Email already registered with another provider | `EMAIL_REGISTERED_WITH_OTHER_PROVIDER` | "This email is already registered. Sign in with [provider]." |
| Recent login required for delete | `REQUIRES_RECENT_LOGIN` | Re-prompt SSO once; if still failing, "Please sign in again, then retry." |
| Apple on Android (shouldn't happen — button hidden) | `SSO_UNAVAILABLE` | Defensive snackbar; should be unreachable |
| Token expired / signature invalid | `UNKNOWN` | Generic "Something went wrong. Please try again" |

## Testing

**Unit tests (commonTest):**

- `FakeSsoCredentialProvider` with configurable token / error
- `FakeAuthRepository.signInWithGoogle / signInWithApple / deleteAccount` for VM tests
- `LoginViewModelTest` / `SignUpViewModelTest` extended:
   - SSO success → emits NavigateToHome, isSsoLoading lifecycle
   - SSO cancelled → no snackbar (silent), isSsoLoading reset
   - SSO collision error → emits ShowError with correct UiText
- `SettingsViewModelTest` (or wherever delete lives):
   - Delete success → emits NavigateToLogin
   - Delete REQUIRES_RECENT_LOGIN → triggers re-auth event

**Integration / smoke (manual on real devices):**

- Real Google account on Android emulator (must add account to emulator first)
- Real Google account on iOS sim (configured via Safari sign-in once)
- Real Apple ID on iOS sim with Sign in with Apple set up
- Verify: first sign-in writes Firebase Auth user; `users/{uid}` doc absent until workshop completes (workshop creates it); displayName populated for Apple first sign-in
- Verify: delete account → Firebase Auth console shows the user gone; `users/{uid}` doc gone

**KMP traps to watch for (memories):**

- `String.format` — not used (no need to introduce)
- `kotlinx.datetime` epoch-days — not relevant here
- gitlive Firebase API shape may differ slightly between Android (real SDK) and iOS (Objective-C bridge) — verify `GoogleAuthProvider.credential` and `OAuthProvider.credential` signatures work in both before declaring done. Run `./gradlew :composeApp:compileKotlinIosSimulatorArm64` after every meaningful change.

## Setup & migration

**Android:**

1. Add to `composeApp/build.gradle.kts` (androidMain dependencies):
   - `androidx.credentials:credentials`
   - `androidx.credentials:credentials-play-services-auth`
   - `com.google.android.libraries.identity.googleid:googleid`
2. Read the `web_client_id` from `google-services.json` (already in the repo; the file lists OAuth client IDs).
3. Add `MainActivity` hook to set/clear the activity in `CurrentActivityHolder` on `onCreate`/`onDestroy`.

**iOS (HARD BLOCKER — none of these are wired up as of 2026-05-11):**

Verified by inspecting the repo. The Apple Developer portal side may be configured (Daniel confirms an active developer account with Sign in with Apple set up for the bundle), but the Xcode project is empty on all five items below. Treat this as **Task 1** of the implementation plan and do not start any iOS Kotlin code until these are done and a smoke compile passes.

1. **SwiftPM dependency on `GoogleSignIn-iOS`** — add via Xcode → File → Add Package Dependencies → `https://github.com/google/GoogleSignIn-iOS` (latest 8.x).
2. **Sign in with Apple capability** — Xcode → iosApp target → Signing & Capabilities → `+ Capability` → `Sign in with Apple`. This creates `iosApp.entitlements` (currently missing — there is no entitlements file in `iosApp/iosApp/` today).
3. **URL scheme for Google** in `Info.plist`. Add a `CFBundleURLTypes` array with the reversed client ID from `GoogleService-Info.plist` (the value of `REVERSED_CLIENT_ID`). Today's `Info.plist` has no `CFBundleURLTypes` at all — only photo / camera usage strings.
4. **Verify Bundle ID** — `iosApp/Configuration/Config.xcconfig` currently sets `PRODUCT_BUNDLE_IDENTIFIER=com.danzucker.stitchpad$(TEAM_ID)`. Confirm the `$(TEAM_ID)` suffix is intentional before configuring the Firebase Apple provider — Firebase needs the **exact** bundle ID, and a team-suffixed bundle could fail Apple's Service ID match. If unintentional, change to `com.danzucker.stitchpad`.
5. **Swift bridge file `SsoPresentingViewControllerBridge.swift`** exposing a static var for the current VC, set from `ContentView` / root SwiftUI view on appear, read by `IosSsoCredentialProvider` via cinterop or KMP iOS-side actuals.

**Firebase console (no code, but blocking):**

1. Enable **Google** provider in Firebase Authentication → Sign-in method.
2. Enable **Apple** provider, configure with Apple Developer team ID, Services ID, and private key.
3. Add the iOS bundle ID's reversed client to OAuth redirect URIs.

These steps belong in the PR description checklist (the implementer can't fully verify SSO locally without them).

## Risks

1. **iOS Xcode-side setup is the hard blocker, not the Apple Developer portal.** Daniel confirmed an active Apple Developer account with Sign in with Apple configured for the bundle. However, I verified the Xcode project on 2026-05-11 and found: no `.entitlements` file (Apple capability not enabled), no `CFBundleURLTypes` URL scheme for Google's reversed client ID, no SwiftPM dep on `GoogleSignIn-iOS`, and the bundle ID has an unusual `$(TEAM_ID)` suffix. All five items in the iOS Setup section above must be done as Task 1 of the implementation plan before any iOS Kotlin code is worth writing.
2. **Firestore data orphaning on delete is disclosed but not loud.** The confirm dialog is short and scannable ("You'll be signed out") with an inline "What happens to my shop data?" link that opens a bottom sheet with the full data-retention disclosure (see Settings — account deletion section above). When the Cloud Function follow-up lands, both the dialog copy and the bottom sheet should be revisited to reflect actual data deletion.
3. **Credential Manager Android API levels.** Credential Manager requires API 19+ (Compose minimum is higher anyway); `GoogleIdOption` requires Google Play Services on the device. Already required by Firebase; not a new constraint.
4. **iOS GoogleSignIn SDK SwiftPM integration.** Adding a SwiftPM dep to an existing KMP iosApp project is occasionally fiddly — there's a chance we hit a linker issue. Already mitigated in the implementation order: Task 1 is "setup + smoke compile" before any other code.
5. **Bundle ID mismatch.** `iosApp/Configuration/Config.xcconfig` has `PRODUCT_BUNDLE_IDENTIFIER=com.danzucker.stitchpad$(TEAM_ID)`. Firebase Auth's Apple provider and Apple Developer Service ID need to match the **resolved** bundle ID exactly. If `$(TEAM_ID)` resolves to something non-empty in your build (it shouldn't in most setups but could), Apple Sign-In tokens won't validate. Verify the resolved bundle ID matches Firebase + Apple Developer console settings as part of the setup smoke test.

## Success criteria

- [ ] User can sign up via Google on both Android + iOS, lands in Workshop
- [ ] User can sign in via Google on both Android + iOS (existing SSO account), lands in Home or Workshop based on `hasCompletedWorkshopSetup`
- [ ] User can sign up + sign in via Apple on iOS, lands appropriately. Apple-first-signin populates `displayName`. Apple button is hidden on Android.
- [ ] Email-already-registered collision shows a clear snackbar, no crash
- [ ] User can delete their account from Settings (or home placeholder). Firebase Auth user removed; `users/{uid}` doc removed; redirected to login
- [ ] All unit tests pass; Android + iOS compile clean; detekt 0 issues
- [ ] App Store submission with this PR would not be rejected for Guideline 4.8 or 5.1.1(v)

## Implementation order

Will be detailed in the implementation plan, but rough order:

1. **iOS setup blocker (do this first or nothing iOS-side will compile)** — SwiftPM `GoogleSignIn-iOS` dep, Sign-in-with-Apple capability + entitlements file, Google URL scheme in `Info.plist`, verify resolved bundle ID matches Firebase + Apple Developer Service ID, Swift bridge file scaffolded. Smoke compile + smoke run on iOS sim with the SsoButtonRow buttons still showing "Coming soon" (no behavior change yet).
2. **Firebase console** — enable Google + Apple providers; configure Apple Service ID + key. (Manual, no code.)
3. **Android deps** — add Credential Manager + GoogleId deps; wire `CurrentActivityHolder` in `MainActivity`; smoke compile.
4. `SsoCredentialProvider` expect + Android actual (Google only).
5. Repository contract additions + `FirebaseAuthRepository.signInWithGoogle()` + unit tests with `FakeSsoCredentialProvider`.
6. Wire `LoginViewModel` + `SignUpViewModel` Google paths + tests; replace "Coming soon" with real flow.
7. iOS actual (Google only) + smoke run on iOS sim — first end-to-end SSO sign-in.
8. Apple iOS actual + `signInWithApple()` repository impl + tests.
9. Wire Apple in VMs; hide Apple button on Android via `SsoButtonRow(showApple = Platform.isIos)`.
10. Account deletion: `deleteAccount()` repo method + `deleteUserDoc()` on UserRepository + Settings entry (or home-placeholder fallback) + confirm dialog + tests.
11. End-to-end smoke on both platforms; verify Firebase Auth console + Firestore console state after each scenario; PR description with manual smoke checklist.
