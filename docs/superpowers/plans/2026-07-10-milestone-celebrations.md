# Milestone Celebrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One-time celebration overlay (springy card + tailor-themed confetti) for three milestones: workshop setup done, first customer created, first order created.

**Architecture:** A Koin-singleton `CelebrationController` holds a `StateFlow<Milestone?>`; ViewModels call `trigger(userId, milestone)` in their existing create-success branches; a global `CelebrationOverlayHost` layered over `StitchPadNavHost` in `App.kt` renders the animation. One-shot gating via new per-user flags in `OnboardingPreferencesStore` (persisted at trigger time). Confetti is hand-built Compose Canvas — **no new dependencies**.

**Tech Stack:** KMP + Compose Multiplatform 1.11.1, Koin, kotlin.test + Turbine (commonTest), GitLive Firebase.

**Spec:** `docs/superpowers/specs/2026-07-10-milestone-celebrations-design.md`

## Global Constraints

- Branch: `feat/milestone-celebrations` (already created; spec committed on it). Never push to main.
- No new library dependencies. Confetti is hand-drawn Canvas.
- All user-facing strings in `composeApp/src/commonMain/composeResources/values/strings.xml`; **never** `\'` — use `’` (project rule: CMP iOS renders `\'` literally).
- All copy exactly as specced (see Task 7 strings block).
- Saffron `DesignTokens.saffron500` on exactly every 12th particle (index % 12 == 0) — rare heritage accent.
- Both color modes specified: dark mode swaps paper-tone particles for lighter indigo/cream (see `confettiPalette`).
- Flags persist at **trigger** time, not dismissal.
- Koin gotchas: `viewModelOf` resolves every ctor param via `get()` — `CelebrationController` must be a `single`. `WorkshopSetupViewModel` is wired with an explicit lambda (it has defaulted params) — update that lambda, don't convert to `viewModelOf`.
- Test commands: `./gradlew :composeApp:testDebugUnitTest` (full run — backtick test names with parentheses are illegal on JVM, full run catches them). Capture exit codes directly, never `cmd | tail; echo $?`.
- iOS gate: `./gradlew :composeApp:compileKotlinIosSimulatorArm64` must pass before the branch is done (JVM-only APIs compile on Android but fail iOS link).
- Detekt: `./gradlew detekt`. Preview-heavy files may need `@file:Suppress("TooManyFunctions")`; mirror existing suppressions if a rule fires on a file you touched.
- Commit after every task with the `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` trailer.

---

### Task 1: Milestone model + one-shot celebration flags

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/Milestone.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferencesStore.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.ios.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/data/FakeOnboardingPreferences.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/presentation/celebration/MilestoneTest.kt`

**Interfaces:**
- Consumes: existing `OnboardingPreferencesStore` patterns (per-user keys, `resetForDebug`).
- Produces: `sealed interface Milestone { val key: String }` with `WorkshopReady`, `FirstCustomer(customerFirstName: String)`, `FirstOrder(customerFirstName: String)`; store methods `suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean`, `suspend fun setCelebrated(userId: String, milestoneKey: String)`, `suspend fun clearCelebrationsForDebug()`.

- [ ] **Step 1: Write the failing test**

`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/presentation/celebration/MilestoneTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.presentation.celebration

import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MilestoneTest {

    @Test
    fun milestoneKeysAreStable() {
        assertEquals("workshop_ready", Milestone.WorkshopReady.key)
        assertEquals("first_customer", Milestone.FirstCustomer("Adaeze").key)
        assertEquals("first_order", Milestone.FirstOrder("Adaeze").key)
    }

    @Test
    fun celebrationFlagsArePerUserAndPerMilestone() = runTest {
        val prefs = FakeOnboardingPreferences()
        assertFalse(prefs.hasCelebrated("u1", "first_customer"))
        prefs.setCelebrated("u1", "first_customer")
        assertTrue(prefs.hasCelebrated("u1", "first_customer"))
        assertFalse(prefs.hasCelebrated("u2", "first_customer"))
        assertFalse(prefs.hasCelebrated("u1", "first_order"))
    }

    @Test
    fun clearCelebrationsForDebugClearsAllFlags() = runTest {
        val prefs = FakeOnboardingPreferences()
        prefs.setCelebrated("u1", "first_customer")
        prefs.setCelebrated("u2", "workshop_ready")
        prefs.clearCelebrationsForDebug()
        assertFalse(prefs.hasCelebrated("u1", "first_customer"))
        assertFalse(prefs.hasCelebrated("u2", "workshop_ready"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.presentation.celebration.MilestoneTest"`
Expected: FAIL — compilation error, `Milestone` / `hasCelebrated` do not exist.

- [ ] **Step 3: Create the Milestone model**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/Milestone.kt`:

```kotlin
package com.danzucker.stitchpad.core.presentation.celebration

/**
 * A once-ever moment worth celebrating. [key] is the stable identifier used for
 * the one-shot preference flag and the GA4 `celebration_shown` param — never
 * rename a key once shipped or users could see the celebration a second time.
 */
sealed interface Milestone {
    val key: String

    /** Workshop setup finished OR skipped — either way, the tailor is in. */
    data object WorkshopReady : Milestone {
        override val key: String = "workshop_ready"
    }

    data class FirstCustomer(val customerFirstName: String) : Milestone {
        override val key: String = "first_customer"
    }

    data class FirstOrder(val customerFirstName: String) : Milestone {
        override val key: String = "first_order"
    }
}
```

- [ ] **Step 4: Add the store methods to the interface**

In `OnboardingPreferencesStore.kt`, add before `resetForDebug()`:

```kotlin
    /**
     * One-shot "this milestone's celebration has been shown" flag, per user per
     * milestone. Set at trigger time (not dismissal) so a crash mid-celebration
     * can never cause a re-show. [milestoneKey] is a
     * [Milestone.key][com.danzucker.stitchpad.core.presentation.celebration.Milestone].
     */
    suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean
    suspend fun setCelebrated(userId: String, milestoneKey: String)

    /** Debug-menu only: clears every celebration flag for every user. Idempotent. */
    suspend fun clearCelebrationsForDebug()
```

- [ ] **Step 5: Implement the Android actual**

In `OnboardingPreferences.android.kt`, add alongside the other overrides:

```kotlin
    override suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean {
        return prefs.getBoolean(celebratedKey(userId, milestoneKey), false)
    }

    override suspend fun setCelebrated(userId: String, milestoneKey: String) {
        prefs.edit().putBoolean(celebratedKey(userId, milestoneKey), true).apply()
    }

    override suspend fun clearCelebrationsForDebug() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_CELEBRATED_PREFIX) }
            .forEach { editor.remove(it) }
        editor.commit()
    }
```

Add the private key helper next to `tutorialSeenKey`:

```kotlin
    private fun celebratedKey(userId: String, milestoneKey: String): String =
        "$KEY_CELEBRATED_PREFIX${milestoneKey}_$userId"
```

Add to the companion object: `private const val KEY_CELEBRATED_PREFIX = "celebrated_"`

In `resetForDebug()`, extend the existing prefix filter:

```kotlin
            .filter {
                it.startsWith(KEY_COMPLETED_WORKSHOP_PREFIX) ||
                    it.startsWith(KEY_CONFIRMED_WORKSHOP_PREFIX) ||
                    it.startsWith(KEY_TUTORIAL_SEEN_PREFIX) ||
                    it.startsWith(KEY_CELEBRATED_PREFIX)
            }
```

- [ ] **Step 6: Implement the iOS actual**

In `OnboardingPreferences.ios.kt`, mirror the Android actual (same key scheme, same companion const). NSUserDefaults version:

```kotlin
    override suspend fun hasCelebrated(userId: String, milestoneKey: String): Boolean {
        return defaults.boolForKey(celebratedKey(userId, milestoneKey))
    }

    override suspend fun setCelebrated(userId: String, milestoneKey: String) {
        defaults.setBool(true, forKey = celebratedKey(userId, milestoneKey))
    }

    override suspend fun clearCelebrationsForDebug() {
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(KEY_CELEBRATED_PREFIX) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun celebratedKey(userId: String, milestoneKey: String): String =
        "$KEY_CELEBRATED_PREFIX${milestoneKey}_$userId"
```

Also extend the iOS `resetForDebug()` the same way as Android (add the `KEY_CELEBRATED_PREFIX` filter to however it clears prefixed keys — read the file first; it mirrors the Android structure).

- [ ] **Step 7: Extend the fake**

In `FakeOnboardingPreferences.kt`, add:

```kotlin
    val celebrated = mutableSetOf<String>()

    override suspend fun hasCelebrated(userId: String, milestoneKey: String) =
        celebrated.contains("$userId:$milestoneKey")
    override suspend fun setCelebrated(userId: String, milestoneKey: String) {
        celebrated.add("$userId:$milestoneKey")
    }
    override suspend fun clearCelebrationsForDebug() { celebrated.clear() }
```

And add `celebrated.clear()` inside its `resetForDebug()`.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.presentation.celebration.MilestoneTest"`
Expected: PASS (3 tests)

- [ ] **Step 9: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): Milestone model + one-shot celebration flags"
```

---

### Task 2: CelebrationController + analytics event + Koin wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/CelebrationController.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/presentation/celebration/CelebrationControllerTest.kt`

**Interfaces:**
- Consumes: `Milestone`, `OnboardingPreferencesStore.hasCelebrated/setCelebrated` (Task 1), `Analytics.logEvent`.
- Produces: `class CelebrationController(preferences: OnboardingPreferencesStore, analytics: Analytics, authUserIds: Flow<String?>, scope: CoroutineScope)` with `val current: StateFlow<Milestone?>`, `suspend fun trigger(userId: String, milestone: Milestone)`, `fun dismiss()`. New `AnalyticsEvent.CelebrationShown(milestone: String)`. Koin `single { CelebrationController(...) }`.

- [ ] **Step 1: Write the failing tests**

`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/presentation/celebration/CelebrationControllerTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.presentation.celebration

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CelebrationControllerTest {

    private lateinit var preferences: FakeOnboardingPreferences
    private lateinit var analytics: FakeAnalytics
    private lateinit var authUserIds: MutableSharedFlow<String?>
    private lateinit var controller: CelebrationController

    @BeforeTest
    fun setUp() {
        preferences = FakeOnboardingPreferences()
        analytics = FakeAnalytics()
        authUserIds = MutableSharedFlow()
        controller = CelebrationController(
            preferences = preferences,
            analytics = analytics,
            authUserIds = authUserIds,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @Test
    fun firstTriggerShowsAndPersistsAndLogs() = runTest {
        controller.trigger("u1", Milestone.WorkshopReady)
        assertEquals(Milestone.WorkshopReady, controller.current.value)
        assertTrue(preferences.hasCelebrated("u1", "workshop_ready"))
        assertTrue(analytics.events.contains(AnalyticsEvent.CelebrationShown("workshop_ready")))
    }

    @Test
    fun secondTriggerOfSameMilestoneIsNoOp() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.dismiss()
        controller.trigger("u1", Milestone.FirstCustomer("Bola"))
        assertNull(controller.current.value)
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun differentUsersCelebrateIndependently() = runTest {
        controller.trigger("u1", Milestone.WorkshopReady)
        controller.dismiss()
        controller.trigger("u2", Milestone.WorkshopReady)
        assertEquals(Milestone.WorkshopReady, controller.current.value)
    }

    @Test
    fun triggerWhileShowingQueuesAndDismissPromotes() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.trigger("u1", Milestone.FirstOrder("Adaeze"))
        assertEquals(Milestone.FirstCustomer("Adaeze"), controller.current.value)
        controller.dismiss()
        assertEquals(Milestone.FirstOrder("Adaeze"), controller.current.value)
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun authChangeClearsCurrentAndQueue() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.trigger("u1", Milestone.FirstOrder("Adaeze"))
        authUserIds.emit(null) // sign-out
        controller.current.test {
            assertNull(awaitItem())
        }
        controller.dismiss() // must NOT resurrect the queued item
        assertNull(controller.current.value)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.presentation.celebration.CelebrationControllerTest"`
Expected: FAIL — `CelebrationController` / `CelebrationShown` unresolved.

- [ ] **Step 3: Add the analytics event**

In `AnalyticsEvent.kt`, add after `WhatsAppMessageSent`:

```kotlin
    data class CelebrationShown(val milestone: String) : AnalyticsEvent {
        override val name = "celebration_shown"
        override val params = mapOf("milestone" to milestone)
    }
```

- [ ] **Step 4: Implement the controller**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/CelebrationController.kt`:

```kotlin
package com.danzucker.stitchpad.core.presentation.celebration

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime owner of milestone celebrations ("tell, don't ask"): ViewModels
 * report every milestone via [trigger]; the controller decides whether it shows.
 * The one-shot flag persists at trigger time — not dismissal — so a crash
 * mid-confetti can never re-show, and "first" never re-fires even if the user
 * later deletes everything. Back-to-back milestones queue FIFO. Any auth-user
 * change clears both the visible celebration and the queue so confetti never
 * plays over the login screen or leaks across accounts.
 */
class CelebrationController(
    private val preferences: OnboardingPreferencesStore,
    private val analytics: Analytics,
    authUserIds: Flow<String?>,
    private val scope: CoroutineScope,
) {
    private val _current = MutableStateFlow<Milestone?>(null)
    val current: StateFlow<Milestone?> = _current.asStateFlow()

    private val queue = ArrayDeque<Milestone>()
    private val mutex = Mutex()

    init {
        scope.launch {
            authUserIds.distinctUntilChanged().collect {
                mutex.withLock {
                    queue.clear()
                    _current.value = null
                }
            }
        }
    }

    suspend fun trigger(userId: String, milestone: Milestone) {
        mutex.withLock {
            if (preferences.hasCelebrated(userId, milestone.key)) return
            preferences.setCelebrated(userId, milestone.key)
            analytics.logEvent(AnalyticsEvent.CelebrationShown(milestone.key))
            if (_current.value == null) {
                _current.value = milestone
            } else {
                queue.addLast(milestone)
            }
        }
    }

    fun dismiss() {
        scope.launch {
            mutex.withLock { _current.value = queue.removeFirstOrNull() }
        }
    }
}
```

- [ ] **Step 5: Register in Koin**

In `di/CoreModule.kt`, add imports:

```kotlin
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.map
```

Add inside `coreModule` after the `EntitlementsProvider` single:

```kotlin
    single<CoroutineScope>(qualifier = named("celebrationAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        CelebrationController(
            preferences = get(),
            analytics = get(),
            authUserIds = get<FirebaseAuth>().authStateChanged.map { it?.uid },
            scope = get<CoroutineScope>(qualifier = named("celebrationAppScope")),
        )
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.presentation.celebration.CelebrationControllerTest"`
Expected: PASS (5 tests)

- [ ] **Step 7: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): CelebrationController with one-shot gating, queue, auth-clear"
```

---

### Task 3: FirstCustomer trigger in CustomerFormViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt` (constructor only)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelAnalyticsTest.kt` (constructor only)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelCelebrationTest.kt`

**Interfaces:**
- Consumes: `CelebrationController.trigger`, `Milestone.FirstCustomer` (Tasks 1–2).
- Produces: `CustomerFormViewModel` gains ctor param `private val celebrations: CelebrationController` (last position). DI needs **no change** — `viewModelOf(::CustomerFormViewModel)` resolves the new param from the Task 2 single.

- [ ] **Step 1: Write the failing test**

`CustomerFormViewModelCelebrationTest.kt` — mirrors `CustomerFormViewModelAnalyticsTest` setup exactly (copy its `FakeEntitlementsProvider` inner class verbatim), plus a controller:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerFormViewModelCelebrationTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var celebrationPrefs: FakeOnboardingPreferences
    private lateinit var celebrations: CelebrationController

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        celebrationPrefs = FakeOnboardingPreferences()
        celebrations = CelebrationController(
            preferences = celebrationPrefs,
            analytics = FakeAnalytics(),
            authUserIds = emptyFlow(),
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(customerId: String? = null): CustomerFormViewModel {
        val args = if (customerId != null) mapOf("customerId" to customerId) else emptyMap()
        val vm = CustomerFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            customerRepository = customerRepository,
            authRepository = authRepository,
            emailValidator = FakePatternValidator(shouldMatch = true),
            entitlements = FakeEntitlementsProvider(),
            analytics = FakeAnalytics(),
            celebrations = celebrations,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private class FakeEntitlementsProvider : EntitlementsProvider {
        private val entitlements = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = 15,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = false,
        )
        private val _flow = MutableStateFlow(entitlements)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = entitlements
        override suspend fun awaitHydrated(): UserEntitlements = entitlements
    }

    @Test
    fun `first create triggers FirstCustomer celebration with first name`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertEquals(Milestone.FirstCustomer("Adaeze"), celebrations.current.value)
    }

    @Test
    fun `second create does NOT re-trigger`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm1 = createViewModel()
        vm1.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm1.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm1.onAction(CustomerFormAction.OnSaveClick)
        celebrations.dismiss()

        val vm2 = createViewModel()
        vm2.onAction(CustomerFormAction.OnNameChange("Bola Ade"))
        vm2.onAction(CustomerFormAction.OnPhoneChange("+2348012345679"))
        vm2.onAction(CustomerFormAction.OnSaveClick)

        assertNull(celebrations.current.value)
    }

    @Test
    fun `edit does NOT trigger celebration`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Old Name",
            phone = "+2340000000000",
        )
        val vm = createViewModel(customerId = "customer-123")
        vm.onAction(CustomerFormAction.OnNameChange("New Name"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertNull(celebrations.current.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelCelebrationTest"`
Expected: FAIL — no `celebrations` parameter on `CustomerFormViewModel`.

- [ ] **Step 3: Add the parameter and trigger**

In `CustomerFormViewModel.kt` add imports:

```kotlin
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
```

Add ctor param after `analytics`:

```kotlin
    private val analytics: Analytics,
    private val celebrations: CelebrationController,
) : ViewModel() {
```

In `save()`, extend the create-only success branch (currently `if (customerId == null) { analytics.logEvent(...) }`):

```kotlin
                is Result.Success -> {
                    if (customerId == null) {
                        analytics.logEvent(AnalyticsEvent.CustomerCreated)
                        celebrations.trigger(
                            userId = userId,
                            milestone = Milestone.FirstCustomer(customer.name.substringBefore(' ')),
                        )
                    }
                    _events.send(postSaveEvent(s, newId))
                }
```

- [ ] **Step 4: Fix the two existing test files**

`CustomerFormViewModelTest.kt` and `CustomerFormViewModelAnalyticsTest.kt` construct the VM directly — add to each constructor call:

```kotlin
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
```

with imports `com.danzucker.stitchpad.core.presentation.celebration.CelebrationController`, `com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.emptyFlow` (and `FakeAnalytics` where not already imported).

- [ ] **Step 5: Run the full customer test package**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.*"`
Expected: PASS — new tests plus all pre-existing form tests.

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): trigger FirstCustomer on first customer create"
```

---

### Task 4: FirstOrder trigger in OrderFormViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt` (constructor only)
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelAnalyticsTest.kt` (constructor only)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelCelebrationTest.kt`

**Interfaces:**
- Consumes: `CelebrationController.trigger`, `Milestone.FirstOrder`.
- Produces: `OrderFormViewModel` gains ctor param `private val celebrations: CelebrationController` (last position). DI: no change (`viewModelOf`).

- [ ] **Step 1: Write the failing test**

`OrderFormViewModelCelebrationTest.kt` — copy the entire setup of `OrderFormViewModelAnalyticsTest.kt` (fields, `testCustomer`, `testUser`, `setUp`, `tearDown`, `createViewModel`, `seedOrder`), add these fields initialized in `setUp`:

```kotlin
    private lateinit var celebrationPrefs: FakeOnboardingPreferences
    private lateinit var celebrations: CelebrationController

    // in setUp(), after the existing fakes:
    celebrationPrefs = FakeOnboardingPreferences()
    celebrations = CelebrationController(
        preferences = celebrationPrefs,
        analytics = FakeAnalytics(),
        authUserIds = emptyFlow(),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )
```

(imports: `com.danzucker.stitchpad.core.presentation.celebration.CelebrationController`, `com.danzucker.stitchpad.core.presentation.celebration.Milestone`, `com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.emptyFlow`), pass `celebrations = celebrations` in `createViewModel`, and replace the test methods with:

```kotlin
    @Test
    fun `first create triggers FirstOrder celebration with first name`() = runTest {
        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)

        assertEquals(Milestone.FirstOrder("Test"), celebrations.current.value)
    }

    @Test
    fun `edit does NOT trigger celebration`() = runTest {
        seedOrder()
        val vm = createViewModel(orderId = "order-1")
        vm.onAction(OrderFormAction.OnPriorityChange(OrderPriority.RUSH))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(celebrations.current.value)
    }
```

(Imports as in Task 3 plus the order-model imports already present in the analytics test; `assertEquals`/`assertNull` from `kotlin.test`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModelCelebrationTest"`
Expected: FAIL — no `celebrations` parameter.

- [ ] **Step 3: Add the parameter and trigger**

In `OrderFormViewModel.kt` add the same two imports as Task 3, add `private val celebrations: CelebrationController,` after `private val analytics: Analytics,` in the ctor, and extend the create-success branch (`OrderFormViewModel.kt:891-899`):

```kotlin
                is Result.Success -> {
                    if (!isEdit) {
                        analytics.logEvent(AnalyticsEvent.OrderCreated)
                        celebrations.trigger(
                            userId = uid,
                            milestone = Milestone.FirstOrder(customer.name.substringBefore(' ')),
                        )
                    }
                    cleanUpPendingStorageDeletions(formItems)
                    _state.update { it.copy(isSaving = false) }
                    _events.send(
                        if (isEdit) OrderFormEvent.OrderSaved else OrderFormEvent.OrderCreated,
                    )
                }
```

(`customer` is the non-null `s.selectedCustomer` already in scope; `uid` likewise.)

- [ ] **Step 4: Fix the two existing test files**

`OrderFormViewModelTest.kt` and `OrderFormViewModelAnalyticsTest.kt` construct the VM directly — add to each constructor call:

```kotlin
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
```

with imports `com.danzucker.stitchpad.core.presentation.celebration.CelebrationController`, `com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.emptyFlow` (and `FakeAnalytics` where not already imported).

- [ ] **Step 5: Run the full order form test package**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): trigger FirstOrder on first order create"
```

---

### Task 5: WorkshopReady trigger in WorkshopSetupViewModel (continue AND skip)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt:55`
- Modify: constructors in `WorkshopSetupViewModelTest.kt`, `WorkshopSetupViewModelAnalyticsTest.kt`, `WorkshopSetupViewModelBankTest.kt`, `WorkshopSetupViewModelLogoTest.kt` (all in `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelCelebrationTest.kt`

**Interfaces:**
- Consumes: `CelebrationController.trigger`, `Milestone.WorkshopReady`.
- Produces: `WorkshopSetupViewModel` gains ctor param `private val celebrations: CelebrationController` (last position, after `analytics`). AuthModule lambda becomes `viewModel { WorkshopSetupViewModel(get(), get(), get(), get(), analytics = get(), celebrations = get()) }`.

- [ ] **Step 1: Write the failing test**

`WorkshopSetupViewModelCelebrationTest.kt` — mirror `WorkshopSetupViewModelAnalyticsTest` setup (FakeUserRepository, FakeAuthRepository with user id `"u1"`, FakeOnboardingPreferences, FakeAnalytics), add a `celebrations` field built in `setup()`:

```kotlin
    private lateinit var celebrations: CelebrationController

    // in setup(), before constructing the ViewModel:
    celebrations = CelebrationController(
        preferences = FakeOnboardingPreferences(),
        analytics = FakeAnalytics(),
        authUserIds = emptyFlow(),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )
```

pass it as `celebrations = celebrations` (last ctor arg, after `analytics = fakeAnalytics`), import `com.danzucker.stitchpad.core.presentation.celebration.CelebrationController`, `com.danzucker.stitchpad.core.presentation.celebration.Milestone`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.emptyFlow`, `kotlinx.coroutines.flow.first`, and test:

```kotlin
    @Test
    fun `successful continue triggers WorkshopReady`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        viewModel.events.first() // await NavigateToHome

        assertEquals(Milestone.WorkshopReady, celebrations.current.value)
    }

    @Test
    fun `skip ALSO triggers WorkshopReady`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)
        viewModel.events.first() // await NavigateToHome

        assertEquals(Milestone.WorkshopReady, celebrations.current.value)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModelCelebrationTest"`
Expected: FAIL — no `celebrations` parameter.

- [ ] **Step 3: Add the parameter and both triggers**

Ctor (after `analytics`): `private val celebrations: CelebrationController,` plus the two imports.

In `onContinue()` (`WorkshopSetupViewModel.kt:424-426`):

```kotlin
                onboardingPreferences.setWorkshopSetupCompleted(user.id)
                analytics.logEvent(AnalyticsEvent.WorkshopSetupCompleted)
                celebrations.trigger(user.id, Milestone.WorkshopReady)
                _events.send(WorkshopSetupEvent.NavigateToHome)
```

In the skip path (`WorkshopSetupViewModel.kt:259-262`):

```kotlin
            authRepository.getCurrentUser()?.id?.let {
                onboardingPreferences.setWorkshopSetupCompleted(it)
                celebrations.trigger(it, Milestone.WorkshopReady)
            }
            _events.send(WorkshopSetupEvent.NavigateToHome)
```

- [ ] **Step 4: Update AuthModule and the four existing test files**

`AuthModule.kt:55`:

```kotlin
    viewModel { WorkshopSetupViewModel(get(), get(), get(), get(), analytics = get(), celebrations = get()) }
```

Each of the four workshop test files constructs the VM with `analytics = fakeAnalytics` (named) — append to each construction:

```kotlin
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
```

with imports `com.danzucker.stitchpad.core.presentation.celebration.CelebrationController`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.emptyFlow` (plus `FakeOnboardingPreferences`/`FakeAnalytics` where not already imported).

- [ ] **Step 5: Run the full workshop test package**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.onboarding.presentation.workshop.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): trigger WorkshopReady on setup complete and skip"
```

---

### Task 6: Confetti particle engine (pure logic)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/ConfettiParticles.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/celebration/ConfettiParticlesTest.kt`

**Interfaces:**
- Consumes: nothing app-specific (pure Kotlin + `androidx.compose.ui.graphics.Color`).
- Produces: `ConfettiParticle`, `ConfettiShape { FABRIC, BUTTON, THREAD }`, `generateConfetti(random, palette, saffron, count): List<ConfettiParticle>`, position/rotation/alpha functions, consts `CONFETTI_COUNT = 70`, `CONFETTI_DURATION_SECONDS = 2.5f`, `CONFETTI_GRAVITY = 1.6f`. All coordinates are **screen fractions** (0..1), converted to px at draw time by Task 7.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfettiParticlesTest {

    private val palette = listOf(Color.Blue, Color.Red, Color.White)
    private val saffron = Color.Yellow

    @Test
    fun generatesRequestedCount() {
        assertEquals(70, generateConfetti(Random(1), palette, saffron).size)
    }

    @Test
    fun everyTwelfthParticleIsSaffron() {
        val particles = generateConfetti(Random(1), palette, saffron)
        particles.forEachIndexed { i, p ->
            if (i % 12 == 0) {
                assertEquals(saffron, p.color, "particle $i should be saffron")
            }
        }
        // Saffron stays rare: only the forced ones.
        assertEquals(6, particles.count { it.color == saffron })
    }

    @Test
    fun allThreeShapesArePresent() {
        val shapes = generateConfetti(Random(1), palette, saffron).map { it.shape }.toSet()
        assertEquals(ConfettiShape.entries.toSet(), shapes)
    }

    @Test
    fun particlesStartAtOriginAndFollowGravity() {
        val p = generateConfetti(Random(1), palette, saffron).first()
        assertEquals(p.startX, p.xAt(0f))
        assertEquals(p.startY, p.yAt(0f))
        // Burst goes up first...
        assertTrue(p.velocityY < 0f)
        // ...but gravity wins by the end of the animation.
        assertTrue(p.yAt(CONFETTI_DURATION_SECONDS) > p.startY)
    }

    @Test
    fun alphaIsOpaqueThenFadesToZero() {
        assertEquals(1f, confettiAlphaAt(0f))
        assertEquals(1f, confettiAlphaAt(0.69f))
        assertEquals(0f, confettiAlphaAt(1f))
        assertTrue(confettiAlphaAt(0.85f) in 0.01f..0.99f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.ui.components.celebration.ConfettiParticlesTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`ConfettiParticles.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Pure confetti model — no Compose runtime, fully unit-testable. All positions
 * and sizes are fractions of the screen (x,size: of width; y: of height) so the
 * physics is density- and screen-size-independent; the overlay scales to px at
 * draw time. Particle position is a closed-form function of elapsed time, so
 * rendering is a cheap pure computation per frame with zero per-frame state.
 */
enum class ConfettiShape { FABRIC, BUTTON, THREAD }

data class ConfettiParticle(
    val shape: ConfettiShape,
    val color: Color,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val sizeFraction: Float,
    val rotation0: Float,
    val spin: Float,
)

const val CONFETTI_COUNT = 70
const val CONFETTI_DURATION_SECONDS = 2.5f
const val CONFETTI_GRAVITY = 1.6f
private const val SAFFRON_EVERY = 12
private const val FABRIC_WEIGHT = 0.45f
private const val BUTTON_WEIGHT = 0.30f

@Suppress("MagicNumber")
fun generateConfetti(
    random: Random,
    palette: List<Color>,
    saffron: Color,
    count: Int = CONFETTI_COUNT,
): List<ConfettiParticle> = List(count) { i ->
    // Three burst origins: top-center, top-left, top-right.
    val origin = i % 3
    val startX = when (origin) {
        0 -> 0.5f
        1 -> 0.08f
        else -> 0.92f
    }
    val velocityX = when (origin) {
        0 -> random.nextFloat() * 0.8f - 0.4f
        1 -> random.nextFloat() * 0.5f
        else -> -(random.nextFloat() * 0.5f)
    }
    val shapeRoll = random.nextFloat()
    val shape = when {
        shapeRoll < FABRIC_WEIGHT -> ConfettiShape.FABRIC
        shapeRoll < FABRIC_WEIGHT + BUTTON_WEIGHT -> ConfettiShape.BUTTON
        else -> ConfettiShape.THREAD
    }
    ConfettiParticle(
        shape = shape,
        color = if (i % SAFFRON_EVERY == 0) saffron else palette[random.nextInt(palette.size)],
        startX = startX,
        startY = 0.18f,
        velocityX = velocityX,
        velocityY = -(0.25f + random.nextFloat() * 0.45f),
        sizeFraction = 0.012f + random.nextFloat() * 0.018f,
        rotation0 = random.nextFloat() * 360f,
        spin = (random.nextFloat() - 0.5f) * 720f,
    )
}

fun ConfettiParticle.xAt(t: Float): Float = startX + velocityX * t

fun ConfettiParticle.yAt(t: Float): Float =
    startY + velocityY * t + 0.5f * CONFETTI_GRAVITY * t * t

fun ConfettiParticle.rotationAt(t: Float): Float = rotation0 + spin * t

private const val FADE_START = 0.7f

/** Fully opaque for the first 70% of the animation, linear fade over the last 30%. */
fun confettiAlphaAt(progress: Float): Float =
    if (progress < FADE_START) 1f else ((1f - progress) / (1f - FADE_START)).coerceIn(0f, 1f)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.ui.components.celebration.ConfettiParticlesTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): pure confetti particle engine with closed-form physics"
```

---

### Task 7: CelebrationOverlay UI, strings, reduce-motion, App.kt host

No unit test — this is the animation layer; verification is compile (Android + iOS), preview, and the Task 9 smoke run.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/ReduceMotion.kt`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/ReduceMotion.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/ReduceMotion.ios.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/CelebrationOverlay.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt`

**Interfaces:**
- Consumes: `CelebrationController.current/dismiss`, `Milestone`, `ConfettiParticles` (Task 6), `util/BackHandler` (existing expect/actual), `StitchPadButton(text, onClick)`, `StitchPadMark(size)`, `DesignTokens`, `LocalStitchPadColors.current.heritageAccent`.
- Produces: `@Composable fun CelebrationOverlayHost()` (self-contained; injects the controller), `@Composable expect fun rememberReduceMotionEnabled(): Boolean`.

- [ ] **Step 1: Add the strings**

Append to `strings.xml` (note `’`, never `\'`):

```xml
    <!-- Milestone celebrations -->
    <string name="celebration_workshop_title">Your workshop is open!</string>
    <string name="celebration_workshop_body">Everything you need to run your tailoring business is right here.</string>
    <string name="celebration_workshop_button">Let’s go</string>
    <string name="celebration_first_customer_title">Your first customer!</string>
    <string name="celebration_first_customer_body">%1$s is in your workshop. Every great atelier starts with one.</string>
    <string name="celebration_first_order_title">First order in the books!</string>
    <string name="celebration_first_order_body">You’re officially in business. Let’s get %1$s’s outfit made.</string>
    <string name="celebration_continue">Continue</string>
```

- [ ] **Step 2: Reduce-motion expect/actual**

`ReduceMotion.kt` (commonMain):

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.runtime.Composable

/**
 * Whether the OS asks apps to minimise motion (Android: animator duration scale
 * 0 / "Remove animations"; iOS: Reduce Motion). When true the celebration skips
 * confetti and springs and simply fades in.
 */
@Composable
expect fun rememberReduceMotionEnabled(): Boolean
```

`ReduceMotion.android.kt` (androidMain):

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
```

`ReduceMotion.ios.kt` (iosMain):

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberReduceMotionEnabled(): Boolean =
    remember { UIAccessibilityIsReduceMotionEnabled() }
```

- [ ] **Step 3: The overlay**

`CelebrationOverlay.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadMark
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.celebration_continue
import stitchpad.composeapp.generated.resources.celebration_first_customer_body
import stitchpad.composeapp.generated.resources.celebration_first_customer_title
import stitchpad.composeapp.generated.resources.celebration_first_order_body
import stitchpad.composeapp.generated.resources.celebration_first_order_title
import stitchpad.composeapp.generated.resources.celebration_workshop_body
import stitchpad.composeapp.generated.resources.celebration_workshop_button
import stitchpad.composeapp.generated.resources.celebration_workshop_title
import kotlin.random.Random

private const val SCRIM_FADE_MS = 200
private const val CONFETTI_DURATION_MS = 2_500
private const val CARD_DELAY_MS = 150L
private const val EMBLEM_DELAY_MS = 120L
private const val SCRIM_ALPHA_LIGHT = 0.45f
private const val SCRIM_ALPHA_DARK = 0.60f
private const val CARD_START_SCALE = 0.6f

/**
 * App-root host: layered over the NavHost in App.kt so celebrations play over
 * whatever screen the milestone's own navigation lands on — no flow is delayed.
 */
@Composable
fun CelebrationOverlayHost(modifier: Modifier = Modifier) {
    val controller = koinInject<CelebrationController>()
    val milestone by controller.current.collectAsState()
    val current = milestone ?: return
    CelebrationOverlay(
        milestone = current,
        onDismiss = controller::dismiss,
        modifier = modifier,
    )
}

@Composable
private fun CelebrationOverlay(
    milestone: Milestone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val overlayAlpha = remember(milestone) { Animatable(0f) }
    val confettiProgress = remember(milestone) { Animatable(0f) }
    val cardScale = remember(milestone) { Animatable(if (reduceMotion) 1f else CARD_START_SCALE) }
    val emblemScale = remember(milestone) { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(milestone) {
        launch { overlayAlpha.animateTo(1f, tween(SCRIM_FADE_MS)) }
        if (!reduceMotion) {
            launch {
                confettiProgress.animateTo(
                    1f,
                    tween(CONFETTI_DURATION_MS, easing = LinearEasing),
                )
            }
            launch {
                delay(CARD_DELAY_MS)
                cardScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                delay(CARD_DELAY_MS + EMBLEM_DELAY_MS)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                emblemScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    val scrimAlpha = if (isDark) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color.Black.copy(alpha = scrimAlpha))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        if (!reduceMotion) {
            ConfettiField(
                progress = { confettiProgress.value },
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CelebrationCard(
            milestone = milestone,
            emblemScale = { emblemScale.value },
            onDismiss = onDismiss,
            modifier = Modifier.graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
            },
        )
    }
}

@Composable
private fun ConfettiField(
    progress: () -> Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    // Dark mode swaps paper-tone pieces for lighter indigo/cream so particles
    // stay visible on the darker scrim (spec: both color modes defined).
    val palette = if (isDark) {
        listOf(
            DesignTokens.indigo200,
            DesignTokens.indigo400,
            DesignTokens.sienna300,
            DesignTokens.paperLight,
        )
    } else {
        listOf(
            DesignTokens.indigo500,
            DesignTokens.indigo400,
            DesignTokens.sienna500,
            DesignTokens.paperLight,
        )
    }
    val saffron = DesignTokens.saffron500
    val particles = remember(isDark) { generateConfetti(Random, palette, saffron) }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val t = progress() * CONFETTI_DURATION_SECONDS
        val alpha = confettiAlphaAt(progress())
        if (alpha <= 0f) return@Canvas
        particles.forEach { particle -> drawParticle(particle, t, alpha) }
    }
}

private fun DrawScope.drawParticle(p: ConfettiParticle, t: Float, alpha: Float) {
    val x = p.xAt(t) * size.width
    val y = p.yAt(t) * size.height
    if (y > size.height) return
    val sizePx = p.sizeFraction * size.width
    val color = p.color.copy(alpha = alpha)
    rotate(degrees = p.rotationAt(t), pivot = Offset(x, y)) {
        when (p.shape) {
            ConfettiShape.FABRIC -> drawRoundRect(
                color = color,
                topLeft = Offset(x - sizePx / 2, y - sizePx / 2),
                size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(sizePx * 0.2f),
            )
            ConfettiShape.BUTTON -> {
                drawCircle(color = color, radius = sizePx / 2, center = Offset(x, y))
                val holeOffset = sizePx * 0.15f
                val holeRadius = sizePx * 0.07f
                val holeColor = Color.Black.copy(alpha = alpha * 0.3f)
                drawCircle(holeColor, holeRadius, Offset(x - holeOffset, y - holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x + holeOffset, y - holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x - holeOffset, y + holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x + holeOffset, y + holeOffset))
            }
            ConfettiShape.THREAD -> {
                val path = Path().apply {
                    moveTo(x - sizePx, y)
                    quadraticTo(x - sizePx / 2, y - sizePx / 2, x, y)
                    quadraticTo(x + sizePx / 2, y + sizePx / 2, x + sizePx, y)
                }
                drawPath(path, color, style = Stroke(width = sizePx * 0.12f))
            }
        }
    }
}

@Composable
private fun CelebrationCard(
    milestone: Milestone,
    emblemScale: () -> Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(fraction = 0.85f)
            // Consume taps so a tap on the card doesn't fall through to the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation3,
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = emblemScale()
                    scaleY = emblemScale()
                },
            ) {
                CelebrationEmblem(milestone)
            }
            Spacer(Modifier.height(DesignTokens.space4))
            Text(
                text = milestone.title(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = milestone.body(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space6))
            StitchPadButton(
                text = milestone.buttonLabel(),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CelebrationEmblem(milestone: Milestone) {
    when (milestone) {
        is Milestone.WorkshopReady -> StitchPadMark(size = 64.dp)
        is Milestone.FirstCustomer -> Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        is Milestone.FirstOrder -> Icon(
            imageVector = Icons.Outlined.Checkroom,
            contentDescription = null,
            tint = LocalStitchPadColors.current.heritageAccent,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun Milestone.title(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_title)
    is Milestone.FirstCustomer -> stringResource(Res.string.celebration_first_customer_title)
    is Milestone.FirstOrder -> stringResource(Res.string.celebration_first_order_title)
}

@Composable
private fun Milestone.body(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_body)
    is Milestone.FirstCustomer ->
        stringResource(Res.string.celebration_first_customer_body, customerFirstName)
    is Milestone.FirstOrder ->
        stringResource(Res.string.celebration_first_order_body, customerFirstName)
}

@Composable
private fun Milestone.buttonLabel(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_button)
    else -> stringResource(Res.string.celebration_continue)
}

@Preview
@Composable
private fun CelebrationCardPreview() {
    StitchPadTheme {
        CelebrationCard(
            milestone = Milestone.FirstCustomer("Adaeze"),
            emblemScale = { 1f },
            onDismiss = {},
        )
    }
}
```

Build notes for the implementer:
- If `Icons.Outlined.Checkroom` doesn't resolve (material-icons-extended not on the classpath — check whether `DebugMenuScreen.kt`'s `Icons.AutoMirrored.Outlined.Logout` import comes from extended), fall back to `Icons.Outlined.ShoppingBag`, then `Icons.Outlined.Star` (core).
- If `quadraticTo` is unavailable on this Compose version, use `quadraticBezierTo` (same args).
- Verify `LocalStitchPadColors` exposes `heritageAccent` (it does — `StitchPadMark.kt:53` uses it).
- Match `StitchPadMark`'s actual parameters if `size` alone doesn't compile (`StitchPadMark.kt:47-55`).
- If the `@Preview` import differs in this project (some files use `org.jetbrains.compose.ui.tooling.preview.Preview`), copy the import used by `StitchPadMark.kt`'s preview.

- [ ] **Step 4: Host it in App.kt**

Wrap the NavHost (`App.kt:43-50`) in a Box:

```kotlin
        AppGateRoot {
            val navController = rememberNavController()
            val onboardingPreferences: OnboardingPreferences = koinInject()
            Box {
                StitchPadNavHost(
                    navController = navController,
                    onboardingPreferences = onboardingPreferences
                )
                CelebrationOverlayHost()
            }
        }
```

Imports: `androidx.compose.foundation.layout.Box`, `com.danzucker.stitchpad.ui.components.celebration.CelebrationOverlayHost`.

- [ ] **Step 5: Compile both platforms**

Run: `./gradlew :composeApp:assembleDebug` — Expected: BUILD SUCCESSFUL
Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64` — Expected: BUILD SUCCESSFUL (catches JVM-only APIs and the iOS actual)

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(celebration): confetti overlay, springy card, global host in App"
```

---

### Task 8: Debug menu "Reset celebrations"

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuScreen.kt`

**Interfaces:**
- Consumes: `OnboardingPreferencesStore.clearCelebrationsForDebug()` (Task 1; `DebugSessionActions` already holds `onboardingPreferences`).
- Produces: debug-menu row that clears all celebration flags so QA can replay them.

- [ ] **Step 1: Add the session action**

In `DebugSessionActions.kt`, after `clearCommunityBannerDismissed()`:

```kotlin
    suspend fun resetCelebrations() {
        onboardingPreferences.clearCelebrationsForDebug()
    }
```

- [ ] **Step 2: Add the action**

In `DebugMenuAction.kt`, next to `OnResetCommunityBannerClick` (match the file's declaration style):

```kotlin
    data object OnResetCelebrationsClick : DebugMenuAction
```

- [ ] **Step 3: Handle it in the ViewModel**

In `DebugMenuViewModel.onAction`, after the `OnResetCommunityBannerClick` branch (`DebugMenuViewModel.kt:79-82`):

```kotlin
            DebugMenuAction.OnResetCelebrationsClick -> runJob {
                sessionActions.resetCelebrations()
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Celebrations reset")))
            }
```

- [ ] **Step 4: Add the row**

In `DebugMenuScreen.kt`, in the "Session" `SettingsSectionCard` after the "Reset community banner" row (`DebugMenuScreen.kt:144-148`):

```kotlin
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    label = "Reset celebrations",
                    onClick = { onAction(DebugMenuAction.OnResetCelebrationsClick) },
                )
```

- [ ] **Step 5: Compile and commit**

Run: `./gradlew :composeApp:assembleDebug` — Expected: BUILD SUCCESSFUL

```bash
git add -A composeApp/src
git commit -m "feat(debug): Reset celebrations entry for QA replay"
```

---

### Task 9: Full verification + PR

- [ ] **Step 1: Full test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, zero failures. (Full run, not filtered — catches backtick-name issues and any test file missed in the constructor updates.)

- [ ] **Step 2: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If `LongParameterList` fires on a ViewModel you touched, mirror the project's existing handling on that class (OrderFormViewModel already has 9 params — check how it's handled before adding any suppression).

- [ ] **Step 3: Both-platform build gate**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Push and open PR**

```bash
git push -u origin feat/milestone-celebrations
gh pr create --title "feat(celebration): milestone celebrations — workshop, first customer, first order" --body "$(cat <<'EOF'
## Summary
- One-time celebration overlay (springy card + hand-drawn tailor-themed confetti) for three milestones: workshop setup done/skipped, first customer created, first order created
- `CelebrationController` singleton + global `CelebrationOverlayHost` over the NavHost — existing navigation untouched, confetti plays over the destination screen
- One-shot per-user flags in `OnboardingPreferencesStore`, persisted at trigger time; FIFO queue for back-to-back milestones; cleared on auth change
- New GA4 event `celebration_shown` (param: milestone)
- Debug menu: "Reset celebrations" to replay
- No new dependencies

Spec: `docs/superpowers/specs/2026-07-10-milestone-celebrations-design.md`

## Smoke test (QA)
1. Debug menu → Reset celebrations (or use a fresh account)
2. Fresh account: sign up → complete workshop setup → **celebration 1** plays over the dashboard (confetti + "Your workshop is open!")
3. Skip path: second fresh account → Skip on workshop setup → celebration 1 still plays
4. Create first customer → **celebration 2** over the customer list, body shows the customer's first name
5. Create first order → **celebration 3**, body shows the customer's first name
6. Create a second customer and second order → no celebration re-fires
7. Dismissal: Continue button, tap outside the card, Android back — all dismiss
8. Dark mode: scrim darker, particles still visible (lighter indigo/cream swap)
9. Enable "Remove animations" (Android) / Reduce Motion (iOS) → card fades in without confetti
10. iOS simulator: repeat steps 2, 4, 5; haptic fires on card landing (physical device only)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Reviews**

Non-trivial PR → run BOTH review passes before merge (project rule): Cursor Bugbot on the PR + `codex review` locally.
