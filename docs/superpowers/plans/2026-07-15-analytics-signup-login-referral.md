# Analytics: SSO sign_up + login + referral_code_applied Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the ~50% `sign_up` undercount (SSO sign-ups were never logged), add a `login` event, and add `referral_code_applied` — all with a `method`/`source`/`surface` param taxonomy, targeting the in-flight 1.1.0 release.

**Architecture:** SSO repository methods change from `Result<User, AuthError>` to `Result<SsoSignIn, AuthError>` (`SsoSignIn(user, isNewUser)` read from GitLive's `AuthResult.additionalUserInfo`). ViewModels pick `sign_up` vs `login` from `isNewUser`. Both `recordAttribution` call paths log `referral_code_applied` on fresh (non-replay) success. Spec: `docs/superpowers/specs/2026-07-15-analytics-signup-login-referral-design.md`.

**Tech Stack:** Kotlin Multiplatform, Compose MP, Koin (`viewModelOf`), GitLive firebase-auth/analytics 2.4.0, kotlin.test + Turbine via `:composeApp:testDebugUnitTest`.

## Global Constraints

- Branch: `feat/analytics-auth-referral-events` (already created; spec committed). PR to `main`; never push to main directly.
- Analytics params are PII-free enums/strings only: `method` ∈ `email|google|apple`; `source` ∈ `manual|install_referrer|clipboard` (must equal `ReferralSource.wire`); `surface` ∈ `signup|settings`.
- `Analytics` is fire-and-forget; never change flow semantics around a `logEvent` call.
- Backtick test names: letters/digits/spaces/hyphens ONLY (iOS gate). New tests in a file follow that file's existing naming style.
- Run commands as plain single commands (no `| tail` pipes — the wrapper exit code gets masked).
- Gates before PR: `./gradlew :composeApp:testDebugUnitTest`, `./gradlew detekt`, `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64`.
- Working directory: `/Users/danzucker/Desktop/Project/StitchPad`. All paths below are relative to it.

---

### Task 1: Event types — `SignUp(method)`, `Login(method)`, `ReferralCodeApplied(source, surface)`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsEventTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt:216` (compile fix — `SignUp` becomes a data class)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt` (compile fix)

**Interfaces:**
- Consumes: existing `AnalyticsEvent` sealed interface.
- Produces (later tasks rely on these exact shapes):
  - `AnalyticsEvent.SignUp(val method: String)` → name `sign_up`, params `{"method": method}`
  - `AnalyticsEvent.Login(val method: String)` → name `login`, params `{"method": method}`
  - `AnalyticsEvent.ReferralCodeApplied(val source: String, val surface: String)` → name `referral_code_applied`, params `{"source": source, "surface": surface}`

- [ ] **Step 1: Write the failing tests**

In `AnalyticsEventTest.kt`, the first test currently asserts `AnalyticsEvent.SignUp` (object) — replace it and add three tests:

```kotlin
    @Test
    fun parameterlessEventsUseSnakeCaseNamesAndNoParams() {
        assertEquals("workshop_setup_completed", AnalyticsEvent.WorkshopSetupCompleted.name)
        assertEquals("customer_created", AnalyticsEvent.CustomerCreated.name)
        assertEquals("order_created", AnalyticsEvent.OrderCreated.name)
        assertTrue(AnalyticsEvent.WorkshopSetupCompleted.params.isEmpty())
    }

    @Test
    fun signUpCarriesMethodParam() {
        val event = AnalyticsEvent.SignUp(method = "google")
        assertEquals("sign_up", event.name)
        assertEquals(mapOf("method" to "google"), event.params)
    }

    @Test
    fun loginCarriesMethodParam() {
        val event = AnalyticsEvent.Login(method = "email")
        assertEquals("login", event.name)
        assertEquals(mapOf("method" to "email"), event.params)
    }

    @Test
    fun referralCodeAppliedCarriesSourceAndSurface() {
        val event = AnalyticsEvent.ReferralCodeApplied(source = "manual", surface = "settings")
        assertEquals("referral_code_applied", event.name)
        assertEquals(mapOf("source" to "manual", "surface" to "settings"), event.params)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.analytics.AnalyticsEventTest"`
Expected: FAIL — compile error (`SignUp` is an object; `Login`/`ReferralCodeApplied` unresolved).

- [ ] **Step 3: Change the event types**

In `AnalyticsEvent.kt`, replace the `SignUp` object:

```kotlin
    /** [method] ∈ email|google|apple — which auth method created the account. */
    data class SignUp(val method: String) : AnalyticsEvent {
        override val name = "sign_up"
        override val params = mapOf("method" to method)
    }

    /** An existing account signed in. [method] ∈ email|google|apple. */
    data class Login(val method: String) : AnalyticsEvent {
        override val name = "login"
        override val params = mapOf("method" to method)
    }
```

Append after `CelebrationShown`:

```kotlin
    /**
     * A referral code attributed successfully (fresh, not an idempotent replay).
     * [source] must be a [com.danzucker.stitchpad.feature.referral.domain.ReferralSource.wire]
     * value; [surface] ∈ signup|settings.
     */
    data class ReferralCodeApplied(val source: String, val surface: String) : AnalyticsEvent {
        override val name = "referral_code_applied"
        override val params = mapOf("source" to source, "surface" to surface)
    }
```

- [ ] **Step 4: Fix the two broken references**

`SignUpViewModel.kt:216`: `analytics.logEvent(AnalyticsEvent.SignUp)` → `analytics.logEvent(AnalyticsEvent.SignUp(method = "email"))`.

`SignUpViewModelAnalyticsTest.kt`: replace all four `AnalyticsEvent.SignUp` references:
- `emailSignUpSuccessLogsSignUpEvent`: `assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "email")))`
- the three negative tests: `assertFalse(analytics.events.any { it is AnalyticsEvent.SignUp })`

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.analytics.AnalyticsEventTest" --tests "com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModelAnalyticsTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsEventTest.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt
git commit -m "feat(analytics): sign_up/login method param + referral_code_applied event types"
```

---

### Task 2: Auth layer — `SsoSignIn` wrapper exposing `isNewUser`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/SsoSignIn.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt:11-12`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt` (both `exchange*Credential` functions)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeAuthRepository.kt:69-97`

**Interfaces:**
- Consumes: GitLive `AuthResult.additionalUserInfo?.isNewUser` (verified present in dev.gitlive:firebase-auth 2.4.0 commonMain).
- Produces: `data class SsoSignIn(val user: User, val isNewUser: Boolean)`; `AuthRepository.signInWithGoogle(): Result<SsoSignIn, AuthError>` and `signInWithApple(): Result<SsoSignIn, AuthError>`; `FakeAuthRepository.ssoIsNewUser: Boolean` (default `false`) test control.
- Note: `SignUpViewModel`/`LoginViewModel` compile unchanged — their `Result.Success` branches don't touch `result.data` yet.

- [ ] **Step 1: Write the failing test**

Append to `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt` (drives out the fake's control; VM wiring lands in Task 3 — this test asserts only the fake's contract for now, in a dedicated repo test file instead):

Create nothing new for the fake itself — instead verify via compile + existing suites. The real failing-test cycle for `isNewUser` behavior lives in Tasks 3/4. Here, make the type change test-covered by the compiler:

- [ ] **Step 2: Create `SsoSignIn.kt`**

```kotlin
package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.model.User

/**
 * Outcome of an SSO credential exchange. [isNewUser] distinguishes account creation
 * from a returning login (Firebase's additionalUserInfo), so analytics can log
 * sign_up vs login correctly — SSO buttons live on BOTH auth screens.
 */
data class SsoSignIn(val user: User, val isNewUser: Boolean)
```

- [ ] **Step 3: Change the interface** (`AuthRepository.kt:11-12`)

```kotlin
    suspend fun signInWithGoogle(): Result<SsoSignIn, AuthError>
    suspend fun signInWithApple(): Result<SsoSignIn, AuthError>
```

- [ ] **Step 4: Update `FirebaseAuthRepository`**

`signInWithGoogle`/`signInWithApple` signatures become `Result<SsoSignIn, AuthError>` (also on the two private `exchange*Credential` helpers). In `exchangeGoogleCredential`, replace `Result.Success(firebaseUser.toDomainUser())` with:

```kotlin
            Result.Success(
                SsoSignIn(
                    user = firebaseUser.toDomainUser(),
                    // Absent additionalUserInfo counts as returning — undercounting
                    // sign_up is safer than overcounting it.
                    isNewUser = authResult.additionalUserInfo?.isNewUser ?: false,
                )
            )
```

In `exchangeAppleCredential`, replace `Result.Success(firebaseUser.toDomainUser())` the same way. Add import `com.danzucker.stitchpad.feature.auth.domain.SsoSignIn`.

- [ ] **Step 5: Update `FakeAuthRepository`**

Add control field near the top with the other vars: `var ssoIsNewUser = false`. Replace both SSO overrides' signatures and returns:

```kotlin
    override suspend fun signInWithGoogle(): Result<SsoSignIn, AuthError> {
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
        return Result.Success(SsoSignIn(user = user, isNewUser = ssoIsNewUser))
    }

    override suspend fun signInWithApple(): Result<SsoSignIn, AuthError> {
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
        return Result.Success(SsoSignIn(user = user, isNewUser = ssoIsNewUser))
    }
```

Add import `com.danzucker.stitchpad.feature.auth.domain.SsoSignIn`.

- [ ] **Step 6: Full unit suite + iOS compile**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (no behavior change anywhere yet)
Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (KMP interface changed — always gate iOS)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/SsoSignIn.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/domain/AuthRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/data/FirebaseAuthRepository.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/data/FakeAuthRepository.kt
git commit -m "feat(auth): expose isNewUser from SSO sign-in via SsoSignIn wrapper"
```

---

### Task 3: SignUpViewModel — SSO sign_up/login matrix

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt:100-142` (both SSO functions)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `SsoSignIn.isNewUser` (Task 2), `AnalyticsEvent.SignUp(method)`/`Login(method)` (Task 1), `FakeAuthRepository.ssoIsNewUser`.
- Produces: SSO success on SignUp screen logs `SignUp("google"|"apple")` when `isNewUser`, else `Login("google"|"apple")`. Email path already logs `SignUp("email")` (Task 1).

- [ ] **Step 1: Write the failing tests**

Replace `googleSignInSuccessDoesNotLogSignUpEvent` and `appleSignInSuccessDoesNotLogSignUpEvent` with:

```kotlin
    @Test
    fun googleSignInNewUserLogsSignUpWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(SignUpAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.Login })
    }

    @Test
    fun googleSignInReturningUserLogsLoginWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(SignUpAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.SignUp })
    }

    @Test
    fun appleSignInNewUserLogsSignUpWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(SignUpAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "apple")))
    }

    @Test
    fun appleSignInReturningUserLogsLoginWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(SignUpAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "apple")))
    }

    @Test
    fun googleSignInFailureLogsNothing() = runTest {
        authRepository.shouldReturnError = AuthError.UNKNOWN
        viewModel.onAction(SignUpAction.OnGoogleSignInClick)

        assertTrue(analytics.events.isEmpty())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModelAnalyticsTest"`
Expected: FAIL — the four matrix tests (no events logged on SSO paths yet)

- [ ] **Step 3: Wire the events**

In `SignUpViewModel.googleSignIn()`, the `Result.Success` branch becomes:

```kotlin
                    is Result.Success -> {
                        analytics.logEvent(
                            if (result.data.isNewUser) AnalyticsEvent.SignUp(method = "google")
                            else AnalyticsEvent.Login(method = "google")
                        )
                        // The referral field lives on the same screen as the SSO buttons,
                        // so a code typed before tapping Google must still be attributed.
                        referralAttribution.submitPendingAttribution(_state.value.referralCode)
                        _events.send(SignUpEvent.NavigateToHome)
                    }
```

In `appleSignIn()` likewise with `method = "apple"`:

```kotlin
                    is Result.Success -> {
                        analytics.logEvent(
                            if (result.data.isNewUser) AnalyticsEvent.SignUp(method = "apple")
                            else AnalyticsEvent.Login(method = "apple")
                        )
                        referralAttribution.submitPendingAttribution(_state.value.referralCode)
                        _events.send(SignUpEvent.NavigateToHome)
                    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModelAnalyticsTest" --tests "com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt
git commit -m "feat(analytics): SSO on SignUp screen logs sign_up or login by isNewUser"
```

---

### Task 4: LoginViewModel — analytics dependency + full matrix

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModelTest.kt:34,73` (constructor gains a param)
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `AnalyticsEvent.SignUp(method)`/`Login(method)`, `SsoSignIn.isNewUser`, `FakeAnalytics` (`com.danzucker.stitchpad.core.analytics.FakeAnalytics`), `FakeAuthRepository.ssoIsNewUser`, `FakePatternValidator(shouldMatch = true)` (`com.danzucker.stitchpad.feature.auth.data`).
- Produces: `LoginViewModel(authRepository, emailValidator, analytics)` — Koin `viewModelOf(::LoginViewModel)` in `AuthModule` resolves the new param automatically (no defaulted params; no DI change needed).

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelAnalyticsTest {

    private lateinit var analytics: FakeAnalytics
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        analytics = FakeAnalytics()
        authRepository = FakeAuthRepository()
        viewModel = LoginViewModel(authRepository, FakePatternValidator(shouldMatch = true), analytics)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emailLoginSuccessLogsLoginWithEmailMethod() = runTest {
        authRepository.signUpWithEmail("ade@gmail.com", "pass123", "Ade Fashions")
        fillCredentials()
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "email")))
    }

    @Test
    fun emailLoginFailureLogsNothing() = runTest {
        authRepository.shouldReturnError = AuthError.INVALID_CREDENTIALS
        fillCredentials()
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun googleSignInReturningUserLogsLoginWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.SignUp })
    }

    @Test
    fun googleSignInNewUserLogsSignUpWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.Login })
    }

    @Test
    fun appleSignInReturningUserLogsLoginWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(LoginAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "apple")))
    }

    @Test
    fun appleSignInNewUserLogsSignUpWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(LoginAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "apple")))
    }

    @Test
    fun ssoFailureLogsNothing() = runTest {
        authRepository.shouldReturnError = AuthError.UNKNOWN
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.isEmpty())
    }

    // --- Helper ---

    private fun fillCredentials() {
        viewModel.onAction(LoginAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
    }
}
```

(Check `LoginViewModelTest` for the exact `AuthError` used in its failure tests — use the same constant if `INVALID_CREDENTIALS` doesn't exist; any non-`SSO_CANCELLED` error works.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.auth.presentation.login.LoginViewModelAnalyticsTest"`
Expected: FAIL — compile error (LoginViewModel has no analytics param)

- [ ] **Step 3: Implement**

`LoginViewModel.kt` — constructor and imports:

```kotlin
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
```

```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator,
    private val analytics: Analytics,
) : ViewModel() {
```

`login()` success branch:

```kotlin
                    is Result.Success -> {
                        analytics.logEvent(AnalyticsEvent.Login(method = "email"))
                        _events.send(LoginEvent.NavigateToHome(fromPasswordLogin = true))
                    }
```

`googleSignIn()` success branch:

```kotlin
                    is Result.Success -> {
                        analytics.logEvent(
                            if (result.data.isNewUser) AnalyticsEvent.SignUp(method = "google")
                            else AnalyticsEvent.Login(method = "google")
                        )
                        _events.send(LoginEvent.NavigateToHome(fromPasswordLogin = false))
                    }
```

`appleSignIn()` success branch: same with `method = "apple"`.

`LoginViewModelTest.kt` lines 34 and 73: `LoginViewModel(authRepository, emailValidator)` → `LoginViewModel(authRepository, emailValidator, FakeAnalytics())` (add import `com.danzucker.stitchpad.core.analytics.FakeAnalytics`).

No Koin change: `AuthModule` line 46 `viewModelOf(::LoginViewModel)` resolves `Analytics` via `get()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.auth.presentation.login.*"`
Expected: PASS (both LoginViewModelTest and LoginViewModelAnalyticsTest)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/
git commit -m "feat(analytics): login event — email + SSO on Login screen, sign_up for SSO new users"
```

---

### Task 5: ReferralAttributionCoordinator — referral_code_applied (signup surface)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralAttributionCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt` (constructor call gains `analytics = get()`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralAttributionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `AnalyticsEvent.ReferralCodeApplied(source, surface)`, `Analytics`, `AttributionOutcome.alreadyAttributed`, `ReferralSource.wire`.
- Produces: `ReferralAttributionCoordinator(referralRepository, preferences, installReferrerReader, clipboardReferralReader, pendingDeepLink, scope, uidFlow, analytics, clipboardCaptureEnabled = false)` — **`analytics` param sits before `clipboardCaptureEnabled`** so the defaulted param stays last.

- [ ] **Step 1: Write the failing tests**

In `ReferralAttributionCoordinatorTest.kt`, extend the `coordinator(...)` helper (it passes args positionally — insert `analytics` before `clipboardEnabled`):

```kotlin
    private fun coordinator(
        scope: CoroutineScope,
        repo: FakeReferralRepository = FakeReferralRepository(),
        prefs: FakeReferralPreferences = FakeReferralPreferences(),
        reader: FakeInstallReferrerReader = FakeInstallReferrerReader(null),
        clipboard: FakeClipboardReferralReader = FakeClipboardReferralReader(null),
        holder: PendingDeepLinkHolder = PendingDeepLinkHolder(),
        uidFlow: Flow<String?> = flowOf(null),
        analytics: FakeAnalytics = FakeAnalytics(),
        clipboardEnabled: Boolean = true,
    ) = ReferralAttributionCoordinator(
        repo, prefs, reader, clipboard, holder, scope, uidFlow, analytics, clipboardEnabled,
    )
```

Add imports `com.danzucker.stitchpad.core.analytics.FakeAnalytics` and `com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent`, then add tests (file uses `lowerCamel_withUnderscores` naming — match it):

```kotlin
    @Test
    fun freshAttribution_logsReferralCodeApplied_signupSurface() = runTest {
        val analytics = FakeAnalytics()
        val c = coordinator(this, analytics = analytics)

        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals(
            listOf(AnalyticsEvent.ReferralCodeApplied(source = "manual", surface = "signup")),
            analytics.events,
        )
    }

    @Test
    fun alreadyAttributedReplay_doesNotLogEvent() = runTest {
        val repo = FakeReferralRepository(
            result = Result.Success(AttributionOutcome(alreadyAttributed = true, marketerId = "m1")),
        )
        val analytics = FakeAnalytics()
        val c = coordinator(this, repo = repo, analytics = analytics)

        c.attributeOnce(manualCode = "MANUAL01")

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun failedAttribution_doesNotLogEvent() = runTest {
        val repo = FakeReferralRepository(result = Result.Error(ReferralError.UNKNOWN))
        val analytics = FakeAnalytics()
        val c = coordinator(this, repo = repo, analytics = analytics)

        c.attributeOnce(manualCode = "MANUAL01")

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun pendingCode_logsInstallReferrerSource() = runTest {
        val analytics = FakeAnalytics()
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, holder = holder, analytics = analytics)

        c.attributeOnce(manualCode = null)

        assertEquals(
            listOf(AnalyticsEvent.ReferralCodeApplied(source = "install_referrer", surface = "signup")),
            analytics.events,
        )
    }
```

(Check the file's existing error constant — if `ReferralError.UNKNOWN` doesn't exist, use whatever error value the file's failure tests already use.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.referral.domain.ReferralAttributionCoordinatorTest"`
Expected: FAIL — compile error (constructor has no analytics param)

- [ ] **Step 3: Implement**

`ReferralAttributionCoordinator.kt` — add imports:

```kotlin
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
```

Constructor — insert before `clipboardCaptureEnabled`:

```kotlin
    private val uidFlow: Flow<String?>,
    private val analytics: Analytics,
    // Clipboard capture stays OFF until the /r/ web landing page ships. ...
    private val clipboardCaptureEnabled: Boolean = false,
```

`attributeOnce` success branch — log only fresh attributions:

```kotlin
            is Result.Success -> {
                preferences.setAttributed()
                manualOverride = null
                if (!result.data.alreadyAttributed) {
                    analytics.logEvent(
                        AnalyticsEvent.ReferralCodeApplied(source = source.wire, surface = "signup")
                    )
                }
                AppLogger.d(tag = TAG) {
                    "attributed (source=${source.wire}, already=${result.data.alreadyAttributed})"
                }
            }
```

`ReferralModule.kt` — in the coordinator construction, add `analytics = get(),` after `uidFlow = auth.authStateChanged.map { it?.uid },`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.referral.domain.ReferralAttributionCoordinatorTest"`
Expected: PASS (existing + 4 new)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralAttributionCoordinator.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralAttributionCoordinatorTest.kt
git commit -m "feat(analytics): referral_code_applied on fresh attribution (signup surface)"
```

---

### Task 6: Settings ReferralCodeViewModel — referral_code_applied (settings surface)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModelTest.kt`

**Interfaces:**
- Consumes: `AnalyticsEvent.ReferralCodeApplied`, shared `FakeReferralRepository` (`feature/referral/data/`), `FakeAnalytics`.
- Produces: `ReferralCodeViewModel(referralRepository, preferences, analytics)` — Koin `viewModelOf(::ReferralCodeViewModel)` in `ReferralModule` resolves it; no DI change.

- [ ] **Step 1: Write the failing tests**

In `ReferralCodeViewModelTest.kt`, update the fixture:

```kotlin
    private val repo = FakeReferralRepository()
    private val prefs = FakeReferralPreferencesStore()
    private val analytics = FakeAnalytics()

    private fun viewModel() = ReferralCodeViewModel(repo, prefs, analytics)
```

Add imports `com.danzucker.stitchpad.core.analytics.FakeAnalytics` and `com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent`, then add tests (this file uses backtick names — letters/digits/spaces/hyphens only):

```kotlin
    @Test
    fun `apply success logs referral code applied with settings surface`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = false, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.onAction(ReferralCodeAction.OnApplyClick)

        assertEquals(
            listOf(AnalyticsEvent.ReferralCodeApplied(source = "manual", surface = "settings")),
            analytics.events,
        )
    }

    @Test
    fun `already attributed replay does not log event`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = true, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.onAction(ReferralCodeAction.OnApplyClick)

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `apply failure does not log event`() = runTest {
        repo.result = Result.Error(ReferralError.UNKNOWN)
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.onAction(ReferralCodeAction.OnApplyClick)

        assertTrue(analytics.events.isEmpty())
    }
```

(Same note as Task 5 on the exact `ReferralError` constant — reuse what the file's existing failure test uses.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.referral.presentation.entry.ReferralCodeViewModelTest"`
Expected: FAIL — compile error (constructor has no analytics param)

- [ ] **Step 3: Implement**

`ReferralCodeViewModel.kt` — imports:

```kotlin
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
```

Constructor:

```kotlin
class ReferralCodeViewModel(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
    private val analytics: Analytics,
) : ViewModel() {
```

`apply()` success branch:

```kotlin
                is Result.Success -> {
                    // Keep local capture state consistent so the auto-capture
                    // coordinator doesn't re-attempt on next launch.
                    preferences.setAttributed()
                    if (!result.data.alreadyAttributed) {
                        analytics.logEvent(
                            AnalyticsEvent.ReferralCodeApplied(source = ReferralSource.MANUAL.wire, surface = "settings")
                        )
                    }
                    emit(ReferralCodeEvent.ApplySucceeded(UiText.StringResourceText(Res.string.referral_code_applied)))
                }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.referral.presentation.entry.ReferralCodeViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModelTest.kt
git commit -m "feat(analytics): referral_code_applied from Settings referral entry"
```

---

### Task 7: Docs, full gates, PR

**Files:**
- Modify: `docs/analytics/ga4-explorations-and-bigquery.md` (event taxonomy table + the `sign_up is email-only` gotcha)

**Interfaces:**
- Consumes: everything above, merged and green.
- Produces: PR to `main` for the 1.1.0 train.

- [ ] **Step 1: Update the analytics playbook**

In `docs/analytics/ga4-explorations-and-bigquery.md`:

1. Taxonomy table — update the `sign_up` row and add two rows:

```markdown
| `sign_up` | `method` (`email`/`google`/`apple`) | account created (email or SSO new-user) |
| `login` | `method` (`email`/`google`/`apple`) | existing account signed in |
| `referral_code_applied` | `source` (`manual`/`install_referrer`/`clipboard`), `surface` (`signup`/`settings`) | referral attributed (fresh, replays excluded) |
```

2. Replace the `sign_up is email-only by design` gotcha bullet with:

```markdown
- **`sign_up` completeness boundary:** before v1.1.0 only email signups were logged
  (SSO missed — undercounted ~50%). From 1.1.0 all methods log with a `method` param.
  Funnels spanning the boundary should treat pre-1.1.0 `sign_up` as email-only and
  cross-check totals against the Firebase Auth dashboard.
```

3. Add to the custom-dimensions table (registration itself is a post-merge console step):

```markdown
| Auth method | Event | `method` |
| Referral source | Event | `source` |
| Referral surface | Event | `surface` |
```

- [ ] **Step 2: Run all gates**

Run each as a separate command; all must succeed:

```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew detekt
./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64
```

- [ ] **Step 3: Commit docs + push + PR**

```bash
git add docs/analytics/ga4-explorations-and-bigquery.md
git commit -m "docs(analytics): taxonomy + boundary notes for sign_up/login/referral_code_applied"
git push -u origin feat/analytics-auth-referral-events
```

Create the PR with `gh pr create` — body must include QA smoke-test steps (Daniel is QA) covering: email signup → `sign_up{method:email}` in DebugView; Google SSO with a fresh account → `sign_up{method:google}`; Google SSO with an existing account → `login{method:google}`; email login → `login{method:email}`; Settings → referral code apply → `referral_code_applied{source:manual,surface:settings}`; re-apply same code → no second event. Note in the body: non-trivial PR → run both Cursor Bugbot and `codex review` before merge. End the body with the standard generated-with-Claude-Code footer.

- [ ] **Step 4: Post-merge ops checklist (console, not code — list in PR body)**

- GA4 Admin → Custom definitions: register event-scoped dimensions `method`, `source`, `surface`.
- Verify events in Firebase DebugView from a 1.1.0 build before the store rollout.

---

## Self-Review Notes

- Spec coverage: events (T1), SsoSignIn + default-false (T2), auth matrix both screens (T3/T4), coordinator + alreadyAttributed skip (T5), settings surface (T6), docs + dimensions + gates (T7). Koin: both changed VMs use `viewModelOf` with no defaulted params → auto-resolution confirmed against AuthModule:46-47 and ReferralModule.
- Type consistency: `SsoSignIn(user, isNewUser)` used identically in T2 fake/impl and consumed via `result.data.isNewUser` in T3/T4; `ReferralCodeApplied(source, surface)` identical in T1/T5/T6.
- Known judgment calls for the implementer: exact `AuthError`/`ReferralError` constants in two test files — reuse whatever the neighboring failure tests in the same file already use.
