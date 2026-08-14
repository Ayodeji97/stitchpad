# Coroutine Exception-Handling & ANR Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the crash classes and main-thread (ANR) hazards found in the 2026-08-14 coroutine audit: wire the listener-error absorber everywhere, guard `openUri`, add exception handlers to app scopes, move data-layer work off Main, restore `CancellationException` propagation, and make Firestore listeners survive transient errors.

**Architecture:** All changes are inside `:composeApp` following existing layer boundaries. New shared helpers go in `core/` (an app-scope factory, a safe-uri helper, a generic listener-retry operator, a `safeCall` wrapper); every other task is a mechanical migration of existing sites onto those helpers or onto explicit dispatchers. No behavioral redesign — error surfaces, Result types, and UI contracts stay identical except where the audit showed them to be wrong (listeners dying permanently, cancellation masquerading as network errors).

**Tech Stack:** Kotlin Multiplatform, kotlinx-coroutines (Dispatchers.IO is available on both JVM and Native), GitLive firebase-kotlin-sdk, Koin, Compose Multiplatform, kotlin-test in `commonTest`.

## Global Constraints

- Audit report (spec) summary: memory file `coroutine-anr-audit-2026-08-14.md`; artifact https://claude.ai/code/artifact/12171ae3-56ef-4dde-812c-c986b3ef3635
- `commonMain` code must compile for Android + iOS: no JVM-only APIs.
- Never hardcode user-facing strings (project rule). None of these tasks adds UI copy — guards log via `AppLogger`, matching existing guarded sites.
- `AppLogger` call shape used throughout: `AppLogger.e(tag = TAG, throwable = t) { "message" }` (also `.w`).
- After every task: `./gradlew :composeApp:compileDebugKotlinAndroid` must pass (fast signal), and the task's listed tests must pass. Run `./gradlew detekt` before each commit; fix any new findings.
- Full suite `./gradlew :composeApp:allTests` runs in Task 15 (and earlier where a task adds tests).
- Commit after every task with a conventional-commit message. Do not commit `iosApp/Configuration/Config.xcconfig` (it has unrelated local modifications) — stage files explicitly, never `git add -A`.
- Existing test style: mirror `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/ListenerErrorAbsorberTest.kt` (kotlin-test + `runTest`); read it before writing any new test.
- `absorbLateListenerErrors(tag)` already exists at `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/ListenerErrorAbsorber.kt:32` — it is `Flow<T>.absorbLateListenerErrors(tag: String): Flow<T>` implemented as `flowOn(CoroutineExceptionHandler { … })`. `flowOn` context fusion means it composes safely with a later `.flowOn(Dispatchers.Default)` (different context keys).
- Canonical operator order for listener flows after this plan: `source.absorbLateListenerErrors(TAG)` → `.map { decode }` → `.retryWithFallback(…)` (or `.catch` where a terminating catch is deliberate) → `.flowOn(Dispatchers.Default)` (heavy flows only).

---

### Task 1: App-lifetime scope factory with CoroutineExceptionHandler

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/AppLifetimeScope.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/AppLifetimeScopeTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt` (4 scopes: lines 44-53, 119-121)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt` (~line 46)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt` (~line 24)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt` (~line 23)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/FreemiumModule.kt` (~line 23)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReviewModule.kt` (~line 21)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/TutorialsModule.kt` (~line 23)
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/offline/IosOfflineUploadBackgroundTasks.kt:18,52-56`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/data/store/IosStoreKitBridge.kt:11` (locate exact path with grep if it differs)
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/IosPushBridge.kt:13` (locate exact path with grep if it differs)

**Interfaces:**
- Produces: `fun appLifetimeScope(tag: String, onUncaught: (Throwable) -> Unit = …): CoroutineScope` in package `com.danzucker.stitchpad.core.data`. Task 8 consumes it for a new scope.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLifetimeScopeTest {

    @Test
    fun uncaughtThrowIsRoutedToHandlerAndSiblingsSurvive() = runTest {
        val absorbed = mutableListOf<Throwable>()
        val scope = appLifetimeScope(tag = "test") { absorbed += it }

        val failing = scope.launch { throw IllegalStateException("boom") }
        failing.join()

        val sibling = scope.launch { /* still schedulable */ }
        sibling.join()

        assertEquals(1, absorbed.size)
        assertEquals("boom", absorbed.first().message)
        assertTrue(scope.coroutineContext.job.isActive, "SupervisorJob must survive a child failure")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.core.data.AppLifetimeScopeTest"` (if there is no `jvmTest` target, use `./gradlew :composeApp:testDebugUnitTest --tests …` — check which target `ListenerErrorAbsorberTest` runs under and use the same)
Expected: FAIL — `appLifetimeScope` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-lifetime scope with the project-standard context: SupervisorJob so one
 * failed child never cancels its siblings, Dispatchers.Default so launches
 * never land on Main by accident, and a CoroutineExceptionHandler so an
 * uncaught throw is logged instead of reaching the platform default handler —
 * which terminates the process on both Android and Kotlin/Native.
 *
 * Every Koin app scope and iOS bridge scope must be built through this factory;
 * a bare `CoroutineScope(SupervisorJob() + Dispatchers.Default)` has NO handler
 * (SupervisorJob isolates siblings but does not catch anything).
 */
fun appLifetimeScope(
    tag: String,
    onUncaught: (Throwable) -> Unit = { throwable ->
        AppLogger.e(tag = tag, throwable = throwable) { "uncaught exception in app-lifetime scope" }
    },
): CoroutineScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
        onUncaught(throwable)
    },
)
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Migrate all 10 Koin scope definitions**

In each DI module, replace the construction expression only — qualifiers and consumers unchanged. Example (CoreModule, repeat for all four in this file):

```kotlin
// Before
single<CoroutineScope>(qualifier = named("entitlementsAppScope")) {
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
// After
single<CoroutineScope>(qualifier = named("entitlementsAppScope")) {
    appLifetimeScope(tag = "entitlementsAppScope")
}
```

Apply to: `entitlementsAppScope`, `offlineWriteAppScope`, `workshopSessionAppScope`, `celebrationAppScope` (CoreModule), and the single scope in each of SmartModule, ReferralModule, AnalyticsModule, FreemiumModule, ReviewModule, TutorialsModule (find each with `grep -n "CoroutineScope(SupervisorJob" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/*.kt`). Add import `com.danzucker.stitchpad.core.data.appLifetimeScope`; remove now-unused `CoroutineScope`/`SupervisorJob`/`Dispatchers` imports per file.

- [ ] **Step 6: Migrate the 3 iOS file-level scopes and guard `drainInForeground`**

Find them: `grep -rn "CoroutineScope(SupervisorJob" composeApp/src/iosMain`. Replace each `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` with `private val scope = appLifetimeScope(tag = "<ObjectName>")` (tags: `"IosOfflineUploadBackgroundTasks"`, `"IosStoreKitBridge"`, `"IosPushBridge"`).

In `IosOfflineUploadBackgroundTasks.kt`, also align `drainInForeground` with its already-guarded background twin (`drain(task:)` wraps in `runCatching`; the foreground path does not — even `KoinPlatform.getKoin()` can throw before Koin starts):

```kotlin
fun drainInForeground() {
    scope.launch {
        runCatching {
            KoinPlatform.getKoin().get<OfflineUploadOutbox>().drain()
        }.onFailure {
            // Foreground drain is best-effort; the BGProcessingTask retry and the
            // next enqueue still cover the upload path.
        }
    }
}
```

- [ ] **Step 7: Verify and commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid` then the test target from Step 2, then `./gradlew detekt`.

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/AppLifetimeScope.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/AppLifetimeScopeTest.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ \
        composeApp/src/iosMain
git commit -m "fix(core): route uncaught app-scope exceptions to AppLogger instead of process death"
```

---

### Task 2: Wire `absorbLateListenerErrors` into all 26 `snapshots` sources

**Files (Modify — all under `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/`):**
- `di/CoreModule.kt:96`
- `core/config/data/FirebaseAppConfigRepository.kt:25`
- `core/smartinfra/data/quota/FirebaseSmartUsageDocSource.kt:24`
- `core/data/repository/FirebaseUserRepository.kt:171`
- `core/data/staff/FirebaseTeamRosterRepository.kt:62`
- `core/data/staff/CloudFunctionsStaffRepository.kt:35`
- `core/data/sync/SyncStatusObserver.kt:28`
- `core/data/entitlement/UserDocEntitlementsProvider.kt:131`
- `feature/order/data/FirebaseCustomGarmentTypeRepository.kt:55`
- `feature/order/data/FirebaseOrderRepository.kt:317,333,422,439,458`
- `feature/measurement/data/FirebaseMeasurementRepository.kt:39`
- `feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt:36`
- `feature/goals/data/FirebaseWeeklyGoalRepository.kt:33`
- `feature/notification/data/FirebaseNotificationRepository.kt:36,71`
- `feature/style/data/FirebaseStyleRepository.kt:105,180`
- `feature/tutorials/data/repository/FirebaseTutorialsRepository.kt:32`
- `feature/customer/data/FirebaseCustomerRepository.kt:111,127,148,174`

**Interfaces:**
- Consumes: `Flow<T>.absorbLateListenerErrors(tag: String): Flow<T>` from `com.danzucker.stitchpad.core.data` (already exists).

- [ ] **Step 1: Apply the operator immediately after every snapshots source**

The rule, per the helper's own KDoc: **at the source, before any other operator**. Placing it later would drag downstream operators into the producer context. Use each file's existing `TAG` constant (add `private const val TAG = "<ClassName>"` only if a file has none — check first). Two shapes exist:

Plain chain (23 sites) — example `SyncStatusObserver.kt:27-29`:

```kotlin
firestore.collection(USERS).document(userId)
    .snapshots(includeMetadataChanges = true)
    .absorbLateListenerErrors(TAG)
    .map { snapshot -> … }
```

Inside `combine` (5 sites: `FirebaseOrderRepository.kt:422,439,458`, `FirebaseCustomerRepository.kt:148,174`) — the absorber must wrap **each source argument individually**; a `flowOn` after the `combine` installs the handler in the wrong context and silently does nothing for the individual listeners. Example `observeOrders`:

```kotlin
combine(
    ordersCollection(userId).snapshots(includeMetadataChanges = true).absorbLateListenerErrors(TAG),
    moneyByOrderId(userId),
) { snapshot, money -> … }
```

(`moneyByOrderId` / `orderMoneyFlow` / `customerContactFlow` / `contactByCustomerId` get the absorber at their own `.snapshots` line — sites :317, :333, :111, :127 — so the combine's second argument is already covered.)

The DI-lambda site `CoreModule.kt:94-101` (membership watch):

```kotlin
membershipStatusFlow = { workshopUid, authUid ->
    firestore.collection("users").document(workshopUid)
        .collection("memberships").document(authUid).snapshots
        .absorbLateListenerErrors("ActiveWorkshopProvider")
        .map { snap -> … }
```

`UserDocEntitlementsProvider.kt:131` is a `.snapshots` inside `flatMapLatest` — same rule, attach directly to that inner `.snapshots` chain.

Note the property-vs-function split: document listeners are `.snapshots` (property), query listeners are `.snapshots()` / `.snapshots(includeMetadataChanges = true)`. Cover both.

- [ ] **Step 2: Verify completeness mechanically**

Run: `grep -rn "\.snapshots" composeApp/src/commonMain --include="*.kt" | grep -v absorbLateListenerErrors`
Expected: zero lines whose statement chain lacks the absorber (a hit is acceptable only if the absorber call is on the immediately following line — inspect any hits).

- [ ] **Step 3: Build, run existing tests, commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid` and the unit-test target (repository tests must still pass — the absorber is transparent when the collector is alive, so no test behavior changes). Then `./gradlew detekt`.

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "fix(data): absorb late Firestore listener errors at every snapshots source"
```

---

### Task 3: Wire the absorber into the 8 GitLive auth callbackFlows

**Files (Modify):**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt:84,126`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt:27`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt:40`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/FreemiumModule.kt:44`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReviewModule.kt:28`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/InMemorySmartUsageStore.kt:28`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/entitlement/UserDocEntitlementsProvider.kt:124`

**Interfaces:** same as Task 2.

- [ ] **Step 1: Apply at each auth-flow source**

`authStateChanged` / `idTokenChanged` are GitLive callbackFlows with the identical undeliverable-close-cause mechanism, and all 8 sites are collected on app-lifetime scopes where an escape used to be fatal (Task 1's handler now logs it; the absorber keeps it out of the handler entirely and preserves normal delivery semantics). Same placement rule — on the source, before `.map`:

```kotlin
// CoreModule.kt:84
authClaims = get<FirebaseAuth>().idTokenChanged
    .absorbLateListenerErrors("ActiveWorkshopProvider")
    .map { authRepository.getWorkshopClaims() },

// CoreModule.kt:126
authUserIds = get<FirebaseAuth>().authStateChanged
    .absorbLateListenerErrors("CelebrationController")
    .map { it?.uid },
```

Repeat the `authStateChanged.absorbLateListenerErrors("<ConsumerName>")` pattern at the six remaining sites (tags: `"AnalyticsIdentitySync"`, `"ReferralAttribution"`, `"ReconcileCoordinator"`, `"ReviewController"`, `"InMemorySmartUsageStore"`, `"UserDocEntitlementsProvider"`). Locate exact expressions with `grep -rn "authStateChanged" composeApp/src/commonMain --include="*.kt"`.

- [ ] **Step 2: Build, verify, commit**

Run: `grep -rn "authStateChanged\|idTokenChanged" composeApp/src/commonMain --include="*.kt" | grep -v absorbLateListenerErrors | grep -v "import\|interface\|abstract"` — inspect any remaining hits (multiline chains are fine if the absorber is on the next line). Then compile + detekt.

```bash
git add composeApp/src/commonMain
git commit -m "fix(di): absorb late auth-listener errors on app-lifetime scopes"
```

---

### Task 4: Safe `openUri` helper + guard the 9 unguarded sites

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/UriHandlerExt.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/presentation/UriHandlerExtTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEventEffect.kt:64,66,76`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/foundersnote/FoundersNoteRoot.kt:34`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/team/TeamScreen.kt:155`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/gift/presentation/sharelink/ShareGiftLinkRoot.kt:44`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsRoot.kt:31,35`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeRoot.kt:30`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpScreen.kt:126`

**Interfaces:**
- Produces: `fun UriHandler.openUriSafely(url: String, tag: String)` in `com.danzucker.stitchpad.core.presentation`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.presentation

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class UriHandlerExtTest {

    private class ThrowingUriHandler : UriHandler {
        override fun openUri(uri: String) {
            throw IllegalArgumentException("Can't open $uri.")
        }
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) { opened += uri }
    }

    @Test
    fun openUriSafelySwallowsMissingHandlerApp() {
        // Must not throw — this is the WhatsApp-not-installed crash.
        ThrowingUriHandler().openUriSafely("https://wa.me/123", tag = "test")
    }

    @Test
    fun openUriSafelyDelegatesOnSuccess() {
        val handler = RecordingUriHandler()
        handler.openUriSafely("https://example.com", tag = "test")
        assertEquals(listOf("https://example.com"), handler.opened)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (unresolved `openUriSafely`), same test target as Task 1.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.danzucker.stitchpad.core.presentation

import androidx.compose.ui.platform.UriHandler
import com.danzucker.stitchpad.core.logging.AppLogger

/**
 * [UriHandler.openUri] throws when no installed app can handle the URI —
 * on Android, `AndroidUriHandler` rethrows ActivityNotFoundException as
 * IllegalArgumentException. Every event-effect call site runs inside a
 * LaunchedEffect coroutine, so an unguarded call is a process-killing crash
 * (a tailor without WhatsApp tapping any "share via WhatsApp" action).
 *
 * Never log the URL: share links can carry invite/gift tokens.
 */
fun UriHandler.openUriSafely(url: String, tag: String) {
    runCatching { openUri(url) }
        .onFailure { throwable ->
            AppLogger.e(tag = tag, throwable = throwable) { "No handler available to open URI" }
        }
}
```

- [ ] **Step 4: Run test to verify it passes.**

- [ ] **Step 5: Migrate all sites (9 unguarded + 1 already-guarded for consistency)**

Replace each `uriHandler.openUri(x)` with `uriHandler.openUriSafely(x, tag = "<ComposableName>")`. In `SettingsEventEffect.kt` also collapse the hand-rolled `OpenCommunityLink` guard (lines 65-72) onto the helper:

```kotlin
is SettingsEvent.OpenUrl -> uriHandler.openUriSafely(event.url, tag = "SettingsEventEffect")
is SettingsEvent.OpenCommunityLink -> uriHandler.openUriSafely(event.url, tag = "SettingsEventEffect")
is SettingsEvent.OpenWhatsApp -> {
    scope.launch {
        val message = getString(event.messageRes)
        uriHandler.openUriSafely(buildWhatsAppUrl(event.phoneNumber, message), tag = "SettingsEventEffect")
    }
}
```

Do NOT touch the other pre-guarded sites (`DashboardScreen.kt:318`, `ReviewPromptHost.kt:49`, `AppGateScreen.kt:55`) in this task — they work; migrating them is optional cleanup with no user benefit. Tags for the rest: `"FoundersNoteRoot"`, `"TeamScreen"`, `"ShareGiftLinkRoot"`, `"FoundingTailorsRoot"`, `"UpgradeRoot"`, `"SignUpScreen"`.

- [ ] **Step 6: Verify no unguarded sites remain**

Run: `grep -rn "uriHandler.openUri(" composeApp/src/commonMain --include="*.kt"`
Expected: hits only inside `runCatching` blocks in the three untouched pre-guarded files. Compile + detekt.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "fix(ui): guard all openUri calls against missing handler apps"
```

---

### Task 5: Guard the FCM `onNewToken` callback

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/StitchPadMessagingService.kt:26-33`

- [ ] **Step 1: Wrap in runCatching + timeout**

`onNewToken` runs on FCM's background executor under a wakelock with a ~10-20 s OS budget; `getCurrentUser()` is unguarded (a throw crashes a system-invoked service — the iOS twin `IosPushBridge.kt` wraps in `runCatching`) and can hang on a token refresh with no network:

```kotlin
override fun onNewToken(token: String) {
    // FCM holds a wakelock for the duration of this callback (background thread),
    // so block until the refreshed token is persisted rather than fire-and-forget.
    // Bounded: FCM force-stops the service after its wakelock budget (~10-20s on
    // OEM builds), and getCurrentUser() can hang on a token refresh with no
    // network — better to drop one registration (retried on next app open) than
    // be killed mid-callback.
    runBlocking {
        runCatching {
            withTimeout(TOKEN_PERSIST_BUDGET_MS) {
                val userId = authRepository.getCurrentUser()?.id ?: return@withTimeout
                registrar.register(userId, token)
            }
        }.onFailure {
            AppLogger.w(tag = "StitchPadMessagingService", throwable = it) { "onNewToken persist failed" }
        }
    }
}
```

Add near the existing file-level constants: `private const val TOKEN_PERSIST_BUDGET_MS = 10_000L`. Imports: `kotlinx.coroutines.withTimeout`, `com.danzucker.stitchpad.core.logging.AppLogger`.

- [ ] **Step 2: Compile, detekt, commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/StitchPadMessagingService.kt
git commit -m "fix(push): bound and guard FCM onNewToken token persistence"
```

---

### Task 6: Move order/customer listener decoding off Main with `flowOn`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt` (ends of `observeOrders`, `observeArchivedOrders`, `observeOrder` chains — after each terminal error operator)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt` (ends of `observeCustomers`, `observeCustomer` chains)

- [ ] **Step 1: Append `.flowOn(Dispatchers.Default)` to the five observe chains**

A single `flowOn` at the end of the chain moves everything upstream — the `combine` machinery, both source collections, DTO decoding, and the 327-line order mapper — onto Default, leaving only the collector's `_state.update` on Main. Placement: **last operator in the chain** (after `.catch`). Example `observeOrders`:

```kotlin
override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
    combine(
        ordersCollection(userId).snapshots(includeMetadataChanges = true).absorbLateListenerErrors(TAG),
        moneyByOrderId(userId),
    ) { snapshot, money ->
        val orders = snapshot.documents.toOrders(userId)
            .filter { it.archivedAt == null }
            .map { it.withMoney(money[it.id]) }
        Result.Success(orders) as Result<List<Order>, DataError.Network>
    }
        .catch { throwable ->
            AppLogger.e(tag = TAG, throwable = throwable) { "observeOrders failed" }
            emit(Result.Error(DataError.Network.UNKNOWN))
        }
        .flowOn(Dispatchers.Default)
```

Imports to add per file: `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.flow.flowOn`. Context fusion note: this composes with the absorber's `flowOn(handler)` — different context keys, both apply.

- [ ] **Step 2: Run existing repository/ViewModel tests**

Unit-test target + compile. Tests using `UnconfinedTestDispatcher`-backed `Dispatchers.setMain` still pass because `flowOn(Default)` only shifts threads, not values; if any test asserts emission threading, read and adjust it deliberately.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain
git commit -m "perf(data): decode order/customer snapshots on Dispatchers.Default"
```

---

### Task 7: Reports recompute off Main

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/ReportsViewModel.kt:129,141-205`

- [ ] **Step 1: Compute on Default, publish on the caller**

`recompute` runs four aggregation passes + two sorts over the full order set per emission. Restructure so the pure computation runs in `withContext(Dispatchers.Default)` and returns a value; field writes (`cachedCustomers`, `cachedDebtors`) and `_state.update` stay on the collecting coroutine to avoid cross-thread mutation of ViewModel vars:

```kotlin
}.collect { recompute(it) }
```
becomes (collect call site unchanged — `recompute` becomes `suspend`):

```kotlin
@Suppress("LongMethod")
private suspend fun recompute(inputs: Inputs) {
    val orders = (inputs.ordersResult as? Result.Success)?.data ?: emptyList()
    val customers = (inputs.customersResult as? Result.Success)?.data ?: emptyList()
    cachedCustomers = customers
    val error = when {
        inputs.ordersResult is Result.Error -> inputs.ordersResult.error.toReportsUiText()
        inputs.customersResult is Result.Error -> inputs.customersResult.error.toReportsUiText()
        else -> null
    }
    val today = Instant.fromEpochMilliseconds(nowMillis())
        .toLocalDateTime(timeZone).date
    val hasAnyOrders = orders.isNotEmpty()
    val effectivePeriod = if (
        inputs.period == ReportsPeriod.CUSTOM && inputs.customRange == null
    ) {
        ReportsPeriod.WEEK
    } else {
        inputs.period
    }
    // The aggregation passes are O(orders × buckets) and re-fire on every
    // snapshot; off Main so a large workshop can't stall input dispatch.
    val computed = withContext(Dispatchers.Default) {
        Computed(
            kpiSummary = if (hasAnyOrders) {
                KpiCalculator.computeSummary(
                    orders = orders,
                    period = effectivePeriod,
                    today = today,
                    timeZone = timeZone,
                    customRange = inputs.customRange,
                )
            } else {
                null
            },
            productionCounts = if (hasAnyOrders) ProductionCountsCalculator.compute(orders) else null,
            topCustomers = CustomerInsightsCalculator.topCustomers(
                orders = orders,
                customers = customers,
                period = effectivePeriod,
                today = today,
                timeZone = timeZone,
                customRange = inputs.customRange,
            ),
            debtors = CustomerInsightsCalculator.debtors(orders, customers, timeZone),
        )
    }
    cachedDebtors = computed.debtors.items
    _state.update {
        it.copy(
            isLoading = false,
            isPremium = inputs.isPremium,
            selectedPeriod = inputs.period,
            customRange = inputs.customRange,
            hasAnyOrders = hasAnyOrders,
            kpiSummary = computed.kpiSummary,
            productionCounts = computed.productionCounts,
            topCustomers = computed.topCustomers,
            debtors = computed.debtors,
            today = today,
            errorMessage = error,
        )
    }
}
```

Add a private holder next to `Inputs` (match the real return types of the calculators — read their signatures before writing it):

```kotlin
private data class Computed(
    val kpiSummary: KpiSummary?,          // actual type per KpiCalculator.computeSummary
    val productionCounts: ProductionCounts?,
    val topCustomers: TopCustomers,       // actual type per CustomerInsightsCalculator
    val debtors: Debtors,                 // actual type per CustomerInsightsCalculator
)
```

Imports: `kotlinx.coroutines.withContext`, `kotlinx.coroutines.Dispatchers`.

- [ ] **Step 2: Run ReportsViewModel tests** (there may be existing ones — `grep -rn "ReportsViewModel" composeApp/src/commonTest`). Under `runTest`, `withContext(Dispatchers.Default)` is real parallelism — if a test hangs or flakes, inject a dispatcher: add `private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default` constructor param with default, use it in `withContext`, and pass the test dispatcher in tests. Compile + detekt.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "perf(reports): run KPI aggregation off the main thread"
```

---

### Task 8: Share `observeOrders` across its 8 collectors

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt:290-296` (constructor) and `observeOrders`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/OrderModule.kt:16`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt` (new named scope)

**Interfaces:**
- Consumes: `appLifetimeScope(tag)` from Task 1.
- Produces: unchanged `OrderRepository.observeOrders` signature; emissions become shared/replayed.

- [ ] **Step 1: Add the share scope in CoreModule**

```kotlin
// App-lifetime scope hosting the shared orders listener; WhileSubscribed keeps
// the Firestore listener alive only while at least one screen collects.
single<CoroutineScope>(qualifier = named("orderShareAppScope")) {
    appLifetimeScope(tag = "orderShareAppScope")
}
```

- [ ] **Step 2: Wire it through OrderModule**

Replace `singleOf(::FirebaseOrderRepository) bind OrderRepository::class` with explicit construction (constructor-ref resolution can't pick a qualified `CoroutineScope`):

```kotlin
single<OrderRepository> {
    FirebaseOrderRepository(
        firestore = get(),
        storage = get(),
        offlineWrites = get(),
        photoStore = get(),
        uploadOutbox = get(),
        shareScope = get(qualifier = named("orderShareAppScope")),
    )
}
```

Imports in OrderModule: `kotlinx.coroutines.CoroutineScope`, `org.koin.core.qualifier.named`; drop the now-unused `singleOf` import if nothing else uses it.

- [ ] **Step 3: Share the flow in the repository**

Add `private val shareScope: CoroutineScope` as the last constructor parameter. Rename the existing `observeOrders` body to a private builder and memoize one shared instance per userId (only one user is signed in at a time; the var swap on user change is benign — the old shared flow just loses its subscribers and `WhileSubscribed` stops its upstream):

```kotlin
// 8 screens collect the orders list concurrently (dashboard, list, reports,
// to-collect, customer screens, team). Sharing means one Firestore listener
// and ONE decode pass per snapshot instead of one per collector — the audit
// measured the unshared decode at seconds of main-thread work per snapshot
// burst at realistic order counts.
private var sharedOrders: Pair<String, Flow<Result<List<Order>, DataError.Network>>>? = null

override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> {
    sharedOrders?.let { (cachedUserId, flow) -> if (cachedUserId == userId) return flow }
    val shared = buildOrdersFlow(userId).shareIn(
        scope = shareScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS),
        replay = 1,
    )
    sharedOrders = userId to shared
    return shared
}

private fun buildOrdersFlow(userId: String): Flow<Result<List<Order>, DataError.Network>> =
    combine( /* …the exact chain observeOrders had after Tasks 2+6, unchanged… */ )
```

Constants: `private const val SHARE_STOP_TIMEOUT_MS = 5_000L` at file level. Imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.shareIn`.

Leave `observeArchivedOrders` and `observeOrder` unshared — archived has one collector; per-order docs are cheap.

- [ ] **Step 4: Run order-related tests**

`grep -rln "FirebaseOrderRepository" composeApp/src/commonTest` — run those test classes. Any test constructing the repository directly needs the new `shareScope` argument: pass `CoroutineScope(UnconfinedTestDispatcher(testScheduler))` or the test's own `backgroundScope`. Behavior notes for assertions: `replay = 1` means a re-collector immediately gets the last list (then live updates) — this is the desired UX (no flash of loading); update any test asserting a fresh fetch per collect.

- [ ] **Step 5: Compile, detekt, commit**

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "perf(orders): share the orders listener across all collecting screens"
```

---

### Task 9: `OfflinePhotoStore` blocking I/O onto Dispatchers.IO

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/offline/OfflinePhotoStore.android.kt` (all 5 suspend functions)
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/offline/OfflinePhotoStore.ios.kt` (all 5 suspend functions)

- [ ] **Step 1: Wrap every suspend body**

These run raw blocking file I/O on the caller's dispatcher, and the callers are `viewModelScope` (Main) at the order-save tap — up to 6 photo writes back-to-back. Android:

```kotlin
actual class OfflinePhotoStore(
    private val context: Context,
) {
    actual suspend fun save(bytes: ByteArray, fileName: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "offline_uploads").apply { mkdirs() }
        val file = File(dir, fileName.safeFileName())
        file.writeBytes(bytes)
        file.absolutePath
    }

    actual suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    actual suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }

    actual suspend fun readUploadJobs(): String? = withContext(Dispatchers.IO) {
        uploadJobsFile().takeIf { it.exists() }?.readText()
    }

    actual suspend fun writeUploadJobs(json: String) {
        withContext(Dispatchers.IO) {
            uploadJobsFile().apply {
                parentFile?.mkdirs()
                writeText(json)
            }
        }
    }
    …
}
```

iOS: identical wrapping of the five `actual suspend fun` bodies (`Dispatchers.IO` exists on Kotlin/Native since coroutines 1.7). The `private fun` helpers stay as they are.

- [ ] **Step 2: Compile both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` — the iOS compile is required here since iosMain changed. Then detekt.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain composeApp/src/iosMain
git commit -m "perf(offline): move photo store file I/O to Dispatchers.IO"
```

---

### Task 10: PNG re-encode off Main

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt:794`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleImageBytesLoader.kt:36`

- [ ] **Step 1: Wrap both `toPngBytes()` calls**

Coil's `execute()` decodes off-main, but `toPngBytes()` (`Bitmap.compress(PNG, 100)` + possible full-size rasterize) runs on the caller — Main — for ~80-350 ms right at the share tap. At both sites:

```kotlin
return withContext(Dispatchers.Default) { result.image.toPngBytes() }
```

Imports at each file: `kotlinx.coroutines.withContext`, `kotlinx.coroutines.Dispatchers`. Both enclosing functions are already `suspend` (verify; if `CoilStyleImageBytesLoader.load` isn't, it is only called from coroutines — make it suspend).

- [ ] **Step 2: Compile, run any OrderDetail/StyleShare tests, detekt, commit**

```bash
git add composeApp/src/commonMain
git commit -m "perf(share): encode PNG bytes off the main thread"
```

---

### Task 11: `safeCall` helper + restore CancellationException propagation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/SafeCall.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/SafeCallTest.kt`
- Modify (insert rethrow clause; exact lines drift after earlier tasks — locate each `catch` by grep):
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/GitLiveFunctionsCaller.kt:31`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt:111` (`deleteUserDoc`)
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/data/CloudFunctionsFreemiumRepository.kt:30,69`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/data/CloudFunctionsPaymentRepository.kt:46`
  - `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/freemium/data/StoreKitPaymentRepository.kt:119`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/data/CloudFunctionsReferralRepository.kt:50,76,99`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/gift/data/CloudFunctionsGiftRepository.kt:39,61`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/data/FirebaseStyleRepository.kt:130,149,170,238,315,346,361,392,410` (9 sites)
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt:490,806,823,902` (4 sites)
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt:210`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/data/FirestoreDeletionFeedbackRepository.kt:41`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileViewModel.kt:179-181`
  - `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/deleteaccount/DeleteAccountViewModel.kt:209`
  - Debug-only, same mechanical fix: `core/debug/FreemiumDebugActions.kt:154,192,222`, `core/debug/ReminderDebugActions.kt:45`, `core/debug/DigestDebugActions.kt:57`, `core/debug/ReferralAdminDebugActions.kt:56`

**Interfaces:**
- Produces: `suspend fun <T> safeCall(tag: String, op: String, block: suspend () -> T): Result<T, DataError.Network>` in `com.danzucker.stitchpad.core.data` — for NEW code and simple UNKNOWN-only sites; existing sites with richer error mapping get the minimal rethrow clause instead (no error-mapping behavior change).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeCallTest {

    @Test
    fun successWrapsValue() = runTest {
        val result = safeCall(tag = "test", op = "op") { 42 }
        assertEquals(Result.Success(42), result)
    }

    @Test
    fun expectedFailureBecomesResultError() = runTest {
        val result = safeCall<Int>(tag = "test", op = "op") { error("network broke") }
        assertTrue(result is Result.Error && result.error == DataError.Network.UNKNOWN)
    }

    @Test
    fun cancellationIsRethrownNotSwallowed() = runTest {
        // The audit's core finding: converting cancellation into Result.Error makes
        // a torn-down screen take its error branch. Cancellation must propagate.
        assertFailsWith<CancellationException> {
            safeCall<Int>(tag = "test", op = "op") { throw CancellationException("cancelled") }
        }
    }
}
```

- [ ] **Step 2: Run to verify FAIL** (unresolved `safeCall`).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Standard suspend-call wrapper: expected failures become [Result.Error],
 * [CancellationException] is ALWAYS rethrown so structured cancellation works.
 *
 * Why the rethrow is non-negotiable: a `catch (e: Exception)` without it turns
 * a cancelled coroutine (user navigated away) into the error branch — flipping
 * state and flashing error UI on a screen that is being torn down. See
 * `staffCall` in CloudFunctionsStaffRepository for the same shape with
 * feature-specific error mapping.
 */
suspend fun <T> safeCall(
    tag: String,
    op: String,
    block: suspend () -> T,
): Result<T, DataError.Network> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    AppLogger.e(tag = tag, throwable = e) { "$op failed" }
    Result.Error(DataError.Network.UNKNOWN)
}
```

- [ ] **Step 4: Run to verify PASS.**

- [ ] **Step 5: Insert the rethrow clause at every listed catch site**

For each `catch (… e: Exception)` / `catch (… e: Throwable)` in the site list, insert immediately before it (this is the minimal, mapping-preserving fix — reference implementation is `staffCall` at `CloudFunctionsStaffRepository.kt:75-76`):

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    // …existing body unchanged…
```

Add `import kotlinx.coroutines.CancellationException` per file. Note: where the existing generic catch already sits after typed catches (e.g. `FirebaseFunctionsException` in `GitLiveFunctionsCaller`), put the CancellationException clause between the typed catch and the generic one.

The two ViewModel `runCatching`-around-suspend sites get an explicit try/catch instead (a `runCatching` catches `Throwable` including cancellation):

`EditProfileViewModel.kt:179-181`:

```kotlin
val firestoreUser = try {
    userRepository.observeUser(authUser.id).first()
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    null
}
```

`DeleteAccountViewModel.kt:209` (best-effort feedback submit — same shape, result discarded):

```kotlin
try {
    deletionFeedbackRepository.submitFeedback(feedback)
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    // Best-effort: deletion proceeds regardless of feedback delivery.
}
```

Leave alone: `runCatching` in pure non-suspend mappers (`core/data/mapper/*`, `FirestoreDecode.kt`, prefs enum parsing) — no suspension point, nothing to swallow. Also leave the fire-and-forget `runCatching` sites on app-lifetime scopes (`PushTokenRegistrar`, iOS bridges, `TutorialMediaResolver`, `FirebaseAuthRepository`'s best-effort side effects) — cancellation of an app-lifetime job only happens at process death.

- [ ] **Step 6: Verify no site was missed**

Run: `grep -rn "catch (@Suppress(\"TooGenericExceptionCaught\") e: \(Exception\|Throwable\))" composeApp/src/commonMain composeApp/src/iosMain --include="*.kt" -B2 | grep -v CancellationException` — then inspect: every generic catch in a suspend context must be preceded by the rethrow clause. Compile + run tests + detekt.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain composeApp/src/iosMain composeApp/src/commonTest
git commit -m "fix(data): rethrow CancellationException at every suspend catch site"
```

---

### Task 12: Generic `retryWithFallback` + revive listeners killed by terminating `catch`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/RetryingListener.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/RetryingListenerTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapper.kt:74-84` (delete the SyncStatus-typed `retryWithFallback`; keep `backoffDelayMs` + its `MAX_BACKOFF_SHIFT`, make `backoffDelayMs` importable from the new file's call — it already is top-level `fun` in `core.data.sync`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusObserver.kt:57` (import update only)
- Modify (replace terminating `.catch { emit(fallback) }` with `.retryWithFallback(...)`, keeping the same fallback value and log message; all under `commonMain/kotlin/com/danzucker/stitchpad/`):
  - `core/config/data/FirebaseAppConfigRepository.kt:28` (fallback `AppConfig.Disabled` — confirm exact sentinel in file)
  - `core/smartinfra/data/quota/FirebaseSmartUsageDocSource.kt` (fallback `SmartUsageSnapshot.Empty` — confirm)
  - `core/data/repository/FirebaseUserRepository.kt` `observeUser` (fallback `null`)
  - `core/data/staff/FirebaseTeamRosterRepository.kt` (fallback = its current catch emission)
  - `core/data/staff/CloudFunctionsStaffRepository.kt:48-51` (fallback `Result.Error(StaffError.NETWORK)`)
  - `feature/order/data/FirebaseCustomGarmentTypeRepository.kt`
  - `feature/order/data/FirebaseOrderRepository.kt`: `moneyByOrderId` (fallback `emptyMap()`), `orderMoneyFlow` (fallback `null`), `observeOrders`/`observeArchivedOrders`/`observeOrder` (fallback `Result.Error(DataError.Network.UNKNOWN)`)
  - `feature/measurement/data/FirebaseMeasurementRepository.kt`, `feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt`
  - `feature/goals/data/FirebaseWeeklyGoalRepository.kt`
  - `feature/style/data/FirebaseStyleRepository.kt` (`observeFolders`, `observeStyles`)
  - `feature/tutorials/data/repository/FirebaseTutorialsRepository.kt` (fallback = bundled tutorials emission)
  - `feature/customer/data/FirebaseCustomerRepository.kt` (`contactByCustomerId`, `customerContactFlow`, `observeCustomers`, `observeCustomer`)

**Interfaces:**
- Produces: `fun <T> Flow<T>.retryWithFallback(fallback: T, initialBackoffMs: Long = LISTENER_RETRY_INITIAL_BACKOFF_MS, maxBackoffMs: Long = LISTENER_RETRY_MAX_BACKOFF_MS, onError: (cause: Throwable, attempt: Long) -> Unit): Flow<T>` in `com.danzucker.stitchpad.core.data`, plus consts `LISTENER_RETRY_INITIAL_BACKOFF_MS = 500L`, `LISTENER_RETRY_MAX_BACKOFF_MS = 60_000L`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.data

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryingListenerTest {

    @Test
    fun emitsFallbackOnFailureThenResubscribes() = runTest {
        var subscriptions = 0
        val upstream = flow {
            subscriptions += 1
            if (subscriptions == 1) throw IllegalStateException("transient permission-denied")
            emit(7)
        }
        val errors = mutableListOf<Throwable>()

        val collected = upstream
            .retryWithFallback(fallback = -1, initialBackoffMs = 1, maxBackoffMs = 1) { cause, _ ->
                errors += cause
            }
            .take(2)
            .toList()

        // The terminating-catch bug this replaces: one transient error must NOT
        // end the flow — it emits the fallback, then recovers with live data.
        assertEquals(listOf(-1, 7), collected)
        assertEquals(1, errors.size)
        assertEquals(2, subscriptions)
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**

- [ ] **Step 3: Write the implementation**

```kotlin
package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.data.sync.backoffDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

const val LISTENER_RETRY_INITIAL_BACKOFF_MS = 500L
const val LISTENER_RETRY_MAX_BACKOFF_MS = 60_000L

/**
 * Keeps a listener-backed flow alive across upstream failures: on error it
 * reports through [onError], emits [fallback] so the UI stops asserting
 * anything it can no longer verify, waits with capped exponential backoff,
 * then resubscribes — indefinitely.
 *
 * This exists because `.catch { emit(fallback) }` TERMINATES the flow: one
 * transient permission-denied during a workshop session flip would freeze the
 * screen's live data for the rest of the ViewModel's life (documented
 * independently at four call sites before this was extracted; generalized from
 * the SyncStatus-typed original in SyncStatusMapper).
 */
fun <T> Flow<T>.retryWithFallback(
    fallback: T,
    initialBackoffMs: Long = LISTENER_RETRY_INITIAL_BACKOFF_MS,
    maxBackoffMs: Long = LISTENER_RETRY_MAX_BACKOFF_MS,
    onError: (cause: Throwable, attempt: Long) -> Unit,
): Flow<T> = retryWhen { cause, attempt ->
    onError(cause, attempt)
    emit(fallback)
    delay(backoffDelayMs(attempt, initialBackoffMs, maxBackoffMs))
    true
}
```

Delete the `Flow<SyncStatus>.retryWithFallback` from `SyncStatusMapper.kt` (keep `backoffDelayMs` and `MAX_BACKOFF_SHIFT` there); in `SyncStatusObserver.kt` change the import to `com.danzucker.stitchpad.core.data.retryWithFallback` — its call site compiles unchanged (same parameter names).

- [ ] **Step 4: Run to verify PASS** (new test + existing SyncStatus tests: `grep -rln "retryWithFallback\|SyncStatusObserver" composeApp/src/commonTest`).

- [ ] **Step 5: Migrate the terminating-catch sites**

Mechanical transform at each listed site — same fallback, same log line, `.w` level (recoverable now):

```kotlin
// Before
.catch { throwable ->
    AppLogger.e(tag = TAG, throwable = throwable) { "observeMemberships failed ownerUid=$ownerUid" }
    emit(Result.Error(StaffError.NETWORK))
}
// After
.retryWithFallback(fallback = Result.Error(StaffError.NETWORK)) { throwable, attempt ->
    AppLogger.w(tag = TAG, throwable = throwable) {
        "observeMemberships failed ownerUid=$ownerUid; retrying (attempt ${attempt + 1})"
    }
}
```

Type note: where the flow's element type is `Result<X, E>`, the fallback argument forces the flow to be typed `Flow<Result<X, E>>` already (they all are — the `as Result<…>` casts in the map blocks exist for exactly this). Operator order after this task: absorber → map → `retryWithFallback` → (`flowOn` where Task 6 added it, stays last). Do NOT migrate the four sites that already use `retryWhen` deliberately (`CoreModule` membership watch, `UserDocEntitlementsProvider`, `FirebaseNotificationRepository` ×2) nor `SyncStatusObserver` (already correct); its trailing safety-net `.catch` also stays.

- [ ] **Step 6: Verify no terminating catch remains on a listener flow**

Run: `grep -rn -A3 "\.catch {" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad --include="*.kt" | grep -B2 "emit("` — remaining hits must be only: `SyncStatusObserver`'s documented safety net, and non-listener (one-shot) flows. Compile + full unit-test target + detekt. Existing repository tests that asserted "error emission ends the flow" (if any) must be updated to expect fallback-then-retry — read each failure and adjust deliberately, this behavior change is the point of the task.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "fix(data): retry listener flows on error instead of permanently killing them"
```

---

### Task 13: Guard the 5 unguarded snapshot decodes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/staff/CloudFunctionsStaffRepository.kt:37-45`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt` (`observeOrder`'s `snapshot.data<OrderDto>()`, formerly :464)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt` (`observeCustomer`'s `snapshot.data<CustomerDto>()`, formerly :180)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/FirebaseSmartUsageDocSource.kt:27`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt:174`

**Interfaces:**
- Consumes: `decodeDocOrLog(tag, docId) { … }` from `core/data/FirestoreDecode.kt:27` (existing; returns `T?`, logs on decode failure).

- [ ] **Step 1: Wrap each decode and handle null explicitly**

One malformed doc must not take down the whole listener (post-Task-12 it would retry forever against a permanently bad doc — worse). List shape (memberships — skip the bad doc):

```kotlin
val members = snapshot.documents.mapNotNull { doc ->
    decodeDocOrLog(tag = TAG, docId = doc.id) {
        val dto = doc.data<MembershipDto>()
        Membership(
            staffAuthUid = dto.staffAuthUid.ifBlank { doc.id },
            staffEmail = dto.staffEmail,
            staffName = dto.staffName,
            status = MembershipStatus.fromWire(dto.status) ?: MembershipStatus.PENDING,
        )
    }
}
```

Single-doc shape (`observeOrder` — bad doc becomes a stable error emission instead of a dead listener):

```kotlin
} else {
    val dto = decodeDocOrLog(tag = TAG, docId = snapshot.id) {
        snapshot.data<OrderDto>().withDocumentId(snapshot.id)
    }
    if (dto == null) {
        Result.Error(DataError.Network.UNKNOWN) as Result<Order, DataError.Network>
    } else {
        Result.Success(
            dto.toOrder(userId).withLocalPendingImages().withMoney(money),
        ) as Result<Order, DataError.Network>
    }
}
```

Apply the same single-doc shape to `observeCustomer` (error type per its signature) and `FirebaseSmartUsageDocSource` (null → its existing `.Empty` sentinel). `FirebaseUserRepository.observeUser:174` (a `Flow<User?>`): null-decode currently indistinguishable from "no profile" — keep `null` but the decode failure is now logged via `decodeDocOrLog`, which is the audit's ask.

- [ ] **Step 2: Compile, run repo tests, detekt, commit**

```bash
git add composeApp/src/commonMain
git commit -m "fix(data): survive malformed Firestore docs at the 5 unguarded decode sites"
```

---

### Task 14: Supervise the two sibling-cancelling fan-outs

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt:400-440`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailViewModel.kt:207-219`

- [ ] **Step 1: Dashboard measurement-count fan-out**

The `async` children currently sit under the plain `launch` Job: one throw cancels every sibling and rethrows out of `awaitAll()` uncaught. Wrap in `supervisorScope` and guard each child so a single bad customer degrades to the existing unknown-count row:

```kotlin
val rows = supervisorScope {
    latestCustomers.map { customer ->
        async {
            val result = try {
                withTimeoutOrNull(COUNT_FETCH_TIMEOUT_MS) {
                    measurementRepository.observeMeasurements(userId, customer.id).first()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // A throwing repo (future refactor) must degrade to the same
                // unknown-count row as a timeout, not cancel every sibling.
                null
            }
            when (result) {
                is Result.Success -> MeasurementsPickerRow(
                    customerId = customer.id,
                    name = customer.name,
                    measurementCount = result.data.size,
                    singleMeasurementId = result.data.singleOrNull()?.id,
                )
                else -> MeasurementsPickerRow(
                    customerId = customer.id,
                    name = customer.name,
                    measurementCount = null,
                    singleMeasurementId = null,
                )
            }
        }
    }.awaitAll()
}
    .sortedWith(
        compareByDescending<MeasurementsPickerRow> { (it.measurementCount ?: 1) > 0 }
            .thenBy { it.name.lowercase() }
    )
```

Preserve the existing explanatory comments above the block. Imports: `kotlinx.coroutines.supervisorScope`, `kotlinx.coroutines.CancellationException`.

- [ ] **Step 2: CustomerDetail sibling collectors**

The four collectors are children of the outer `launch`'s plain Job — one failing flow cancels the other three sections. Supervise them:

```kotlin
private fun loadData() {
    val customerId = customerId ?: return
    viewModelScope.launch {
        val userId = activeWorkshopProvider.workshopUidOrNull() ?: run {
            _state.update { it.copy(isLoading = false) }
            return@launch
        }
        // supervisorScope: these four listeners render independent screen
        // sections — one failing must not blank the other three.
        supervisorScope {
            launch { observeCustomer(userId, customerId) }
            launch { observeCustomFieldLabels(userId) }
            launch { observeOrders(userId, customerId) }
            launch { observeMeasurements(userId, customerId) }
        }
    }
}
```

Import: `kotlinx.coroutines.supervisorScope`.

- [ ] **Step 3: Run Dashboard + CustomerDetail ViewModel tests, compile, detekt, commit**

```bash
git add composeApp/src/commonMain
git commit -m "fix(presentation): supervise sibling collectors in dashboard and customer detail"
```

---

### Task 15: CI guardrail + full verification

**Files:**
- Modify: `composeApp/build.gradle.kts` (append at end)

- [ ] **Step 1: Add the absorber-coverage check task**

Manual discipline will not hold at 34 sites and growing; encode the invariant as a build check:

```kotlin
// Guardrail from the 2026-08 coroutine audit: every GitLive listener source in
// commonMain must be wrapped in absorbLateListenerErrors — an undeliverable
// listener close-cause otherwise bypasses catch/retryWhen and kills the process.
// Heuristic: per file, absorber call count must cover snapshots + auth sources.
tasks.register("checkListenerAbsorbers") {
    group = "verification"
    description = "Every .snapshots/.authStateChanged/.idTokenChanged source must be absorbed"
    val srcDir = layout.projectDirectory.dir("src/commonMain/kotlin")
    inputs.dir(srcDir)
    doLast {
        val sourcePattern = Regex("""\.snapshots\b|\.authStateChanged\b|\.idTokenChanged\b""")
        val absorberPattern = Regex("""\.absorbLateListenerErrors\(""")
        val offenders = srcDir.asFileTree.matching { include("**/*.kt") }.files
            .filterNot { it.name == "ListenerErrorAbsorber.kt" }
            .mapNotNull { file ->
                val text = file.readText()
                val sources = sourcePattern.findAll(text).count()
                val absorbed = absorberPattern.findAll(text).count()
                if (sources > absorbed) "${file.relativeTo(projectDir)}: $sources listener source(s), $absorbed absorbed" else null
            }
        check(offenders.isEmpty()) {
            "Unabsorbed listener sources (wrap each in .absorbLateListenerErrors(TAG)):\n" +
                offenders.joinToString("\n")
        }
    }
}
tasks.named("check") { dependsOn("checkListenerAbsorbers") }
```

- [ ] **Step 2: Prove the guardrail works**

Run: `./gradlew :composeApp:checkListenerAbsorbers` — PASS. Then temporarily delete one `.absorbLateListenerErrors(TAG)` call, run again — must FAIL naming the file. Restore the call, run again — PASS.

- [ ] **Step 3: Full verification sweep**

Run, in order, and fix anything that fails before proceeding:
1. `./gradlew :composeApp:allTests`
2. `./gradlew detekt`
3. `./gradlew :composeApp:assembleDebug`
4. `./gradlew :composeApp:compileKotlinIosSimulatorArm64`

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts
git commit -m "chore(build): enforce listener-error absorber coverage in check"
```

---

## Deliberately out of scope (recorded so nobody "helpfully" adds them)

- SharedPreferences suspend bodies onto Dispatchers.IO (`*Preferences.android.kt`, `StaffMembershipPrefs` constructor reads) — jank-level, not ANR; separate cleanup PR.
- `stateIn`/`shareIn` presentation-layer restructuring, `AppGateViewModel`'s `Eagerly` — guarded upstream; no change needed.
- Migrating existing correct sites (auth repo catches, notification retryWhen, receipt renderers) — already right.
- A detekt custom-rule module for blocking-call detection in commonMain — the Gradle guardrail covers the highest-risk invariant; a full rule set is its own project.
