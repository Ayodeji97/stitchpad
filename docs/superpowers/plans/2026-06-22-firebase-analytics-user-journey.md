# Firebase Analytics — User-Journey Instrumentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrument StitchPad with Firebase Analytics (GA4) so we can see the full user journey — screen-to-screen flow plus a conversion funnel — segmentable by subscription tier and platform.

**Architecture:** A thin, injectable `Analytics` interface in `core/analytics/domain`, implemented by `FirebaseAnalyticsTracker` (GitLive `dev.gitlive:firebase-analytics`) in `core/analytics/data`, provided as a Koin `single`. ViewModels fire type-safe `AnalyticsEvent`s from existing MVI success branches. A single nav-graph effect auto-logs `screen_view` for every route. An app-scoped `AnalyticsIdentitySync` sets the Firebase user id and `subscription_tier` user property from auth + entitlements. Tracking is fire-and-forget and never throws.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, GitLive Firebase SDK 2.4.0, Napier (via `AppLogger`), JUnit5 + Turbine (tests).

## Global Constraints

- KMP common code only unless a task says otherwise; every change MUST compile on **both** Android and iOS. Run an iOS compile before declaring any task done (this project repeatedly ships JVM-only/iOS-link regressions).
- GitLive analytics version is pinned to the existing `firebase-gitlive = "2.4.0"` ref — do NOT introduce a new version.
- Result<T, E> for expected failures; but analytics is the documented exception — it is **fire-and-forget, never returns Result, never throws, never blocks a user flow**. Swallow errors via `AppLogger.w`.
- **No PII in any event or user property** — counts, enums, and tier only. Never names, phone numbers, business names, customer/order data, measurements, or free text.
- Name implementations descriptively (`FirebaseAnalyticsTracker`, never `AnalyticsImpl`). GitLive's own class is `dev.gitlive.firebase.analytics.FirebaseAnalytics` — do not shadow it.
- ViewModels call analytics; composables never do. No business logic in composables.
- Every new Screen composable keeps its `@Preview` working — `NoOpAnalytics` exists so previews/tests need no Firebase.
- Detekt must pass (`./gradlew detekt`). Functions/back-end are untouched by this plan.
- GA4 event names are `snake_case`; defined once in `AnalyticsEvent`. Reserved screen event is exactly `screen_view` with param `screen_name`.

## Deviation from the approved spec (confirm at review)

The spec listed `first_customer_created` / `first_order_created` (fire-once-per-user, guarded). This plan fires plain **`customer_created`** / **`order_created`** on each successful *create* (not edit). Rationale: GA4 funnels already count "users who fired the event ≥ once" = activation, and "first occurrence / time-to-first" is a `MIN(event_timestamp)` query in BigQuery. This removes a racy/persisted client-side first-only guard entirely (a known bug-class for this team) while answering the exact same funnel questions. The create-vs-edit guard is kept (cheap, local: `orderId == null` / the create branch). If you prefer the literal once-per-user client guard, stop and revisit before Task 9.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/Analytics.kt` — interface + `AnalyticsUserProperty` enum
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt` — sealed interface, one subtype per milestone
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/FirebaseAnalyticsTracker.kt` — GitLive impl
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/NoOpAnalytics.kt` — no-op impl
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/AnalyticsIdentitySync.kt` — userId + tier property
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/ScreenName.kt` — pure route→name mapper
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt` — `analyticsModule`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/FakeAnalytics.kt` — test double
- Test files per task under `composeApp/src/commonTest/kotlin/...`

**Modify:**
- `gradle/libs.versions.toml` — add `firebase-analytics` library line
- `composeApp/build.gradle.kts` — add `implementation(libs.firebase.analytics)` to commonMain
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt` — register `analyticsModule`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt` — screen_view effect
- The 6 event ViewModels + 2 of their Koin modules (Auth, Smart) — see tasks
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/` + debug menu — analytics toggle

---

## Task 1: Add the GitLive analytics dependency

**Files:**
- Modify: `gradle/libs.versions.toml` (Firebase library block, ~line 82)
- Modify: `composeApp/build.gradle.kts` (commonMain GitLive block, ~line 84)

**Interfaces:**
- Produces: `libs.firebase.analytics` Gradle accessor; `dev.gitlive.firebase.analytics.*` available in commonMain.

- [ ] **Step 1: Add the catalog entry.** In `gradle/libs.versions.toml`, directly after the `firebase-functions` line (~82) add:

```toml
firebase-analytics = { module = "dev.gitlive:firebase-analytics", version.ref = "firebase-gitlive" }
```

- [ ] **Step 2: Add the dependency.** In `composeApp/build.gradle.kts`, in the commonMain `// Firebase (GitLive KMP SDK)` block (after `implementation(libs.firebase.functions)`, ~line 84) add:

```kotlin
implementation(libs.firebase.analytics)
```

- [ ] **Step 3: Verify it resolves (Android).**

Run: `./gradlew :composeApp:dependencies --configuration androidDebugRuntimeClasspath | grep -i firebase-analytics`
Expected: a line resolving `dev.gitlive:firebase-analytics:2.4.0` (or `-android` variant). Capture exit via `echo $?` separately — piped gradle hides failures.

- [ ] **Step 4: Verify common compiles.**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (the iOS analytics artifact links; note GitLive issue #645 historically affected analytics linking — if it fails here, that is the real blocker, resolve before proceeding).

- [ ] **Step 5: Commit.**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "build(analytics): add GitLive firebase-analytics dependency"
```

---

## Task 2: Analytics domain — interface, events, user properties, NoOp, Fake

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/Analytics.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/NoOpAnalytics.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/FakeAnalytics.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsEventTest.kt`

**Interfaces:**
- Produces:
  - `interface Analytics { fun logEvent(event: AnalyticsEvent); fun logScreenView(screenName: String); fun setUserId(userId: String?); fun setUserProperty(property: AnalyticsUserProperty, value: String) }`
  - `enum class AnalyticsUserProperty(val key: String) { SUBSCRIPTION_TIER("subscription_tier") }`
  - `sealed interface AnalyticsEvent { val name: String; val params: Map<String, Any> }` with subtypes `SignUp`, `WorkshopSetupCompleted`, `CustomerCreated`, `OrderCreated`, `AiFeatureUsed(feature: String)`, `UpgradeCompleted(tier: String)`.
  - `class NoOpAnalytics : Analytics` (all no-ops).
  - `class FakeAnalytics : Analytics` recording `events: List<AnalyticsEvent>`, `screenViews: List<String>`, `userIds: List<String?>`, `userProperties: List<Pair<AnalyticsUserProperty, String>>`.

- [ ] **Step 1: Write the failing test** at `AnalyticsEventTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventTest {

    @Test
    fun parameterlessEventsUseSnakeCaseNamesAndNoParams() {
        assertEquals("sign_up", AnalyticsEvent.SignUp.name)
        assertEquals("workshop_setup_completed", AnalyticsEvent.WorkshopSetupCompleted.name)
        assertEquals("customer_created", AnalyticsEvent.CustomerCreated.name)
        assertEquals("order_created", AnalyticsEvent.OrderCreated.name)
        assertTrue(AnalyticsEvent.SignUp.params.isEmpty())
    }

    @Test
    fun aiFeatureUsedCarriesFeatureParam() {
        val event = AnalyticsEvent.AiFeatureUsed(feature = "draft_message")
        assertEquals("ai_feature_used", event.name)
        assertEquals(mapOf("feature" to "draft_message"), event.params)
    }

    @Test
    fun upgradeCompletedCarriesTierParam() {
        val event = AnalyticsEvent.UpgradeCompleted(tier = "pro")
        assertEquals("upgrade_completed", event.name)
        assertEquals(mapOf("tier" to "pro"), event.params)
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsEventTest*"`
Expected: FAIL — unresolved reference `AnalyticsEvent`.

- [ ] **Step 3: Create `AnalyticsEvent.kt`:**

```kotlin
package com.danzucker.stitchpad.core.analytics.domain

/**
 * Type-safe analytics events. Each maps to its GA4 snake_case name + params in ONE place
 * so client constants can't drift. NO PII — counts, enums, and tier only.
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Any> get() = emptyMap()

    data object SignUp : AnalyticsEvent {
        override val name = "sign_up"
    }

    data object WorkshopSetupCompleted : AnalyticsEvent {
        override val name = "workshop_setup_completed"
    }

    data object CustomerCreated : AnalyticsEvent {
        override val name = "customer_created"
    }

    data object OrderCreated : AnalyticsEvent {
        override val name = "order_created"
    }

    data class AiFeatureUsed(val feature: String) : AnalyticsEvent {
        override val name = "ai_feature_used"
        override val params = mapOf("feature" to feature)
    }

    data class UpgradeCompleted(val tier: String) : AnalyticsEvent {
        override val name = "upgrade_completed"
        override val params = mapOf("tier" to tier)
    }
}
```

- [ ] **Step 4: Create `Analytics.kt`:**

```kotlin
package com.danzucker.stitchpad.core.analytics.domain

/**
 * Fire-and-forget product analytics. Implementations MUST NOT throw or block — a failed
 * analytics call must never affect a user flow. Injected into ViewModels; never called
 * from composables.
 */
interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun logScreenView(screenName: String)
    fun setUserId(userId: String?)
    fun setUserProperty(property: AnalyticsUserProperty, value: String)
}

/** GA4 user properties (set once, attached to every event for segmentation). */
enum class AnalyticsUserProperty(val key: String) {
    SUBSCRIPTION_TIER("subscription_tier"),
}
```

- [ ] **Step 5: Create `NoOpAnalytics.kt`:**

```kotlin
package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty

/** No-op analytics for previews and tests. */
class NoOpAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setUserProperty(property: AnalyticsUserProperty, value: String) = Unit
}
```

- [ ] **Step 6: Create `FakeAnalytics.kt`** (commonTest):

```kotlin
package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty

/** Records calls for assertions in ViewModel tests. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val screenViews = mutableListOf<String>()
    val userIds = mutableListOf<String?>()
    val userProperties = mutableListOf<Pair<AnalyticsUserProperty, String>>()

    override fun logEvent(event: AnalyticsEvent) { events += event }
    override fun logScreenView(screenName: String) { screenViews += screenName }
    override fun setUserId(userId: String?) { userIds += userId }
    override fun setUserProperty(property: AnalyticsUserProperty, value: String) {
        userProperties += property to value
    }
}
```

- [ ] **Step 7: Run tests to verify they pass.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsEventTest*"`
Expected: PASS.

- [ ] **Step 8: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics
git commit -m "feat(analytics): Analytics interface, events, NoOp + Fake"
```

---

## Task 3: FirebaseAnalyticsTracker implementation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/FirebaseAnalyticsTracker.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/FirebaseAnalyticsTrackerTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent`, `AnalyticsUserProperty` (Task 2); GitLive `dev.gitlive.firebase.analytics.FirebaseAnalytics` (Task 1) — methods `logEvent(name: String, parameters: Map<String, Any>?)`, `setUserId(id: String?)`, `setUserProperty(name: String, value: String)`.
- Produces: `class FirebaseAnalyticsTracker(private val firebaseAnalytics: FirebaseAnalytics) : Analytics`.

Note: the GitLive class shares the name `FirebaseAnalytics`; import it explicitly and keep our class `FirebaseAnalyticsTracker`. The unit test cannot instantiate the real GitLive object, so the test asserts the **error-swallowing contract** via a throwing fake of the same shape. To keep the impl testable, extract the SDK behind a tiny internal sink.

- [ ] **Step 1: Write the failing test** at `FirebaseAnalyticsTrackerTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.data.AnalyticsSink
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsTracker
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingSink(val throwOnEvent: Boolean = false) : AnalyticsSink {
    val logged = mutableListOf<Pair<String, Map<String, Any>?>>()
    var userId: String? = "unset"
    val properties = mutableListOf<Pair<String, String>>()
    override fun logEvent(name: String, parameters: Map<String, Any>?) {
        if (throwOnEvent) error("boom")
        logged += name to parameters
    }
    override fun setUserId(id: String?) { userId = id }
    override fun setUserProperty(name: String, value: String) { properties += name to value }
}

class FirebaseAnalyticsTrackerTest {

    @Test
    fun logEventForwardsNameAndParams_nullWhenEmpty() {
        val sink = RecordingSink()
        val tracker = FirebaseAnalyticsTracker(sink)
        tracker.logEvent(AnalyticsEvent.SignUp)
        tracker.logEvent(AnalyticsEvent.AiFeatureUsed(feature = "draft_message"))
        assertEquals("sign_up" to null, sink.logged[0])
        assertEquals("ai_feature_used" to mapOf("feature" to "draft_message"), sink.logged[1])
    }

    @Test
    fun logScreenViewEmitsReservedEvent() {
        val sink = RecordingSink()
        FirebaseAnalyticsTracker(sink).logScreenView("HomeRoute")
        assertEquals("screen_view" to mapOf("screen_name" to "HomeRoute"), sink.logged[0])
    }

    @Test
    fun setUserPropertyUsesKey() {
        val sink = RecordingSink()
        FirebaseAnalyticsTracker(sink).setUserProperty(AnalyticsUserProperty.SUBSCRIPTION_TIER, "pro")
        assertEquals("subscription_tier" to "pro", sink.properties[0])
    }

    @Test
    fun sinkExceptionsAreSwallowed() {
        val tracker = FirebaseAnalyticsTracker(RecordingSink(throwOnEvent = true))
        tracker.logEvent(AnalyticsEvent.OrderCreated) // must NOT throw
    }
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FirebaseAnalyticsTrackerTest*"`
Expected: FAIL — unresolved `AnalyticsSink` / `FirebaseAnalyticsTracker`.

- [ ] **Step 3: Create `FirebaseAnalyticsTracker.kt`** (sink abstraction + GitLive-backed factory):

```kotlin
package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.analytics.FirebaseAnalytics

/** Minimal seam over the SDK so the tracker is unit-testable without a real Firebase object. */
interface AnalyticsSink {
    fun logEvent(name: String, parameters: Map<String, Any>?)
    fun setUserId(id: String?)
    fun setUserProperty(name: String, value: String)
}

/** [AnalyticsSink] backed by the GitLive Firebase Analytics SDK. */
class FirebaseAnalyticsSink(private val analytics: FirebaseAnalytics) : AnalyticsSink {
    override fun logEvent(name: String, parameters: Map<String, Any>?) =
        analytics.logEvent(name, parameters)
    override fun setUserId(id: String?) = analytics.setUserId(id)
    override fun setUserProperty(name: String, value: String) =
        analytics.setUserProperty(name, value)
}

/**
 * Fire-and-forget analytics over Firebase. Every call is wrapped so a failure logs a
 * warning and is swallowed — analytics must never affect a user flow.
 */
class FirebaseAnalyticsTracker(private val sink: AnalyticsSink) : Analytics {

    override fun logEvent(event: AnalyticsEvent) = safely("logEvent ${event.name}") {
        sink.logEvent(event.name, event.params.ifEmpty { null })
    }

    override fun logScreenView(screenName: String) = safely("screen_view") {
        sink.logEvent(SCREEN_VIEW_EVENT, mapOf(SCREEN_NAME_PARAM to screenName))
    }

    override fun setUserId(userId: String?) = safely("setUserId") {
        sink.setUserId(userId)
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String) =
        safely("setUserProperty ${property.key}") {
            sink.setUserProperty(property.key, value)
        }

    private inline fun safely(label: String, block: () -> Unit) {
        runCatching { block() }.onFailure { t ->
            AppLogger.w(TAG, t) { "analytics $label failed: ${t::class.simpleName}" }
        }
    }

    private companion object {
        const val TAG = "Analytics"
        const val SCREEN_VIEW_EVENT = "screen_view"
        const val SCREEN_NAME_PARAM = "screen_name"
    }
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FirebaseAnalyticsTrackerTest*"`
Expected: PASS.

- [ ] **Step 5: Verify iOS compiles** (the GitLive `logEvent` signature must link on Native):

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/FirebaseAnalyticsTracker.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/FirebaseAnalyticsTrackerTest.kt
git commit -m "feat(analytics): FirebaseAnalyticsTracker with error-swallowing sink"
```

---

## Task 4: Koin module + wiring

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt`

**Interfaces:**
- Consumes: `Analytics`, `FirebaseAnalyticsTracker`, `FirebaseAnalyticsSink` (Tasks 2–3); GitLive `Firebase.analytics`.
- Produces: `val analyticsModule` exposing `single<Analytics>`; registered in `initKoin`.

- [ ] **Step 1: Create `AnalyticsModule.kt`:**

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsSink
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsTracker
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import org.koin.dsl.module

val analyticsModule = module {
    single<Analytics> { FirebaseAnalyticsTracker(FirebaseAnalyticsSink(Firebase.analytics)) }
}
```

Note: confirm the accessor import is `dev.gitlive.firebase.analytics.analytics` (mirrors `auth`/`firestore`/`storage` in `CoreModule.kt`). If unresolved, use `dev.gitlive.firebase.analytics.FirebaseAnalytics.Companion`-style accessor per the resolved artifact and fix the import.

- [ ] **Step 2: Register the module.** In `StitchPadApp.kt`, add the import `import com.danzucker.stitchpad.di.analyticsModule` and add `analyticsModule,` to the `modules(...)` list (place it right after `coreModule,`).

- [ ] **Step 3: Verify the app graph builds.**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt
git commit -m "feat(analytics): provide Analytics via Koin analyticsModule"
```

---

## Task 5: Automatic screen_view logging

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/ScreenName.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/ScreenNameTest.kt`

**Interfaces:**
- Consumes: `Analytics` (Koin), `NavHostController.currentBackStackEntryAsState()` (already imported in NavGraph).
- Produces: `fun screenNameFor(route: String?): String`; `@Composable fun ScreenViewTrackingEffect(navController: NavHostController)`.

Route strings are fully-qualified for type-safe nav (e.g. `com.danzucker.stitchpad.navigation.HomeRoute`, or `...OrderDetailRoute/{orderId}` for classes with args). `screenNameFor` reduces this to the simple class name without args/`Route` suffix (e.g. `Home`, `OrderDetail`) and never returns PII (arg values are stripped).

- [ ] **Step 1: Write the failing test** at `ScreenNameTest.kt`:

```kotlin
package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenNameTest {
    @Test fun stripsPackageAndRouteSuffix() =
        assertEquals("Home", screenNameFor("com.danzucker.stitchpad.navigation.HomeRoute"))

    @Test fun stripsArgsAndKeepsClassName() =
        assertEquals(
            "OrderDetail",
            screenNameFor("com.danzucker.stitchpad.navigation.OrderDetailRoute/123abc"),
        )

    @Test fun stripsQueryStyleArgs() =
        assertEquals(
            "CustomerForm",
            screenNameFor("com.danzucker.stitchpad.navigation.CustomerFormRoute?customerId=9"),
        )

    @Test fun nullRouteFallsBackToUnknown() =
        assertEquals("Unknown", screenNameFor(null))
}
```

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ScreenNameTest*"`
Expected: FAIL — unresolved `screenNameFor`.

- [ ] **Step 3: Create `ScreenName.kt`:**

```kotlin
package com.danzucker.stitchpad.navigation

/**
 * Reduces a type-safe nav route string (fully-qualified class name, possibly with
 * path/query args) to a clean, PII-free screen name for analytics.
 * e.g. "com...OrderDetailRoute/123" -> "OrderDetail".
 */
fun screenNameFor(route: String?): String {
    if (route.isNullOrBlank()) return "Unknown"
    val classOnly = route
        .substringBefore('/')
        .substringBefore('?')
        .substringAfterLast('.')
    return classOnly.removeSuffix("Route").ifBlank { "Unknown" }
}
```

- [ ] **Step 4: Add the effect to `NavGraph.kt`.** Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import com.danzucker.stitchpad.core.analytics.domain.Analytics
```

(`LaunchedEffect`, `getValue`, `currentBackStackEntryAsState`, `koinInject` are already imported.) Add this composable above `StitchPadNavHost`:

```kotlin
/**
 * Logs a screen_view for every destination the user lands on. One hook covers every
 * route — no per-screen code. Keys on the route string so re-landing the same screen
 * (e.g. tab reselects to the same destination) does not spam duplicates.
 */
@Composable
private fun ScreenViewTrackingEffect(navController: NavHostController) {
    val analytics: Analytics = koinInject()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    LaunchedEffect(route) {
        if (route != null) analytics.logScreenView(screenNameFor(route))
    }
}
```

- [ ] **Step 5: Invoke it.** Inside `StitchPadNavHost`, directly after the existing `PushDeepLinkRedirectEffect(navController)` call (line ~120), add:

```kotlin
ScreenViewTrackingEffect(navController)
```

- [ ] **Step 6: Run tests + compile.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ScreenNameTest*" && ./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/ScreenName.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/ScreenNameTest.kt
git commit -m "feat(analytics): auto-log screen_view for every nav destination"
```

---

## Task 6: Identity sync — userId + subscription_tier

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/AnalyticsIdentitySync.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt`
- Read first: `core/domain/entitlement/EntitlementsProvider.kt` (to learn its tier/flow shape), `core/data/entitlement/UserDocEntitlementsProvider.kt`, `CoreModule.kt` (named app-scope pattern).
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsIdentitySyncTest.kt`

**Interfaces:**
- Consumes: `Analytics`; `Firebase.auth.authStateChanged: Flow<FirebaseUser?>`; `EntitlementsProvider` (read its API — expected to expose a `Flow` of entitlements from which a tier string `"free"|"pro"|"atelier"` is derivable); a `CoroutineScope`.
- Produces: `class AnalyticsIdentitySync(...)` that, when started, sets the analytics user id on auth changes and the `SUBSCRIPTION_TIER` property on entitlement changes.

Read the EntitlementsProvider first; map its tier representation to the lowercase strings `free`/`pro`/`atelier`. If it exposes an enum, map exhaustively. Keep the mapping in a private `tierLabel(...)` function so it is unit-testable. Use the existing `subscriptionTier` field semantics (note the `tier` vs `subscriptionTier` consolidation backlog — read whatever EntitlementsProvider already reads; do not introduce a second field).

- [ ] **Step 1: Write the failing test** at `AnalyticsIdentitySyncTest.kt`. Drive the public `tierLabel` mapping and the collection wiring via injected flows:

```kotlin
package com.danzucker.stitchpad.core.analytics

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.data.AnalyticsIdentitySync
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsIdentitySyncTest {

    @Test
    fun setsUserIdOnAuthChangesAndTierOnEntitlementChanges() = runTest {
        val analytics = FakeAnalytics()
        val userIdFlow = MutableStateFlow<String?>(null)
        val tierFlow = MutableStateFlow("free")
        val sync = AnalyticsIdentitySync(
            userIdSource = userIdFlow,
            tierSource = tierFlow,
            analytics = analytics,
            scope = backgroundScope,
        )
        sync.start()

        userIdFlow.value = "uid-123"
        tierFlow.value = "pro"

        assertEquals(listOf(null, "uid-123"), analytics.userIds)
        assertEquals(
            listOf(
                AnalyticsUserProperty.SUBSCRIPTION_TIER to "free",
                AnalyticsUserProperty.SUBSCRIPTION_TIER to "pro",
            ),
            analytics.userProperties,
        )
    }
}
```

(`backgroundScope` + `UnconfinedTestDispatcher` is the project's runTest convention; if collection needs a dispatcher, pass `UnconfinedTestDispatcher(testScheduler)` into the scope.)

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsIdentitySyncTest*"`
Expected: FAIL — unresolved `AnalyticsIdentitySync`.

- [ ] **Step 3: Create `AnalyticsIdentitySync.kt`.** Take the two sources as `Flow`s so it is testable; the Koin definition adapts auth + entitlements into those flows:

```kotlin
package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the analytics identity in sync with the session: the Firebase user id (so journeys
 * can be stitched per-user in BigQuery) and the subscription_tier user property (so every
 * funnel is segmentable by plan). App-scoped; started once at startup.
 */
class AnalyticsIdentitySync(
    private val userIdSource: Flow<String?>,
    private val tierSource: Flow<String>,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
) {
    fun start() {
        userIdSource
            .onEach { analytics.setUserId(it) }
            .launchIn(scope)
        tierSource
            .onEach { analytics.setUserProperty(AnalyticsUserProperty.SUBSCRIPTION_TIER, it) }
            .launchIn(scope)
    }
}
```

- [ ] **Step 4: Wire in `AnalyticsModule.kt`.** Add a named app scope (mirroring `entitlementsAppScope` in `CoreModule.kt`) and a `createdAtStart` definition that maps auth + entitlements to the two flows and calls `start()`:

```kotlin
import com.danzucker.stitchpad.core.analytics.data.AnalyticsIdentitySync
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named

// inside module { ... }, after the Analytics single:
single<CoroutineScope>(qualifier = named("analyticsAppScope")) {
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
single(createdAtStart = true) {
    val userIdSource = Firebase.auth.authStateChanged.map { it?.uid }
    val tierSource = get<EntitlementsProvider>().tierLabelFlow() // adapt to actual API
    AnalyticsIdentitySync(
        userIdSource = userIdSource,
        tierSource = tierSource,
        analytics = get(),
        scope = get<CoroutineScope>(qualifier = named("analyticsAppScope")),
    ).also { it.start() }
}
```

Replace `get<EntitlementsProvider>().tierLabelFlow()` with the real derivation you found in Step 0: map the provider's entitlements flow to the lowercase tier string. If the provider only exposes a suspend/current value, expose a `Flow` via its existing observable; keep the `free/pro/atelier` mapping in a private function and add a focused unit test for it.

- [ ] **Step 5: Run tests + Android compile.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsIdentitySyncTest*" && ./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/data/AnalyticsIdentitySync.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AnalyticsModule.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsIdentitySyncTest.kt
git commit -m "feat(analytics): sync userId + subscription_tier from auth + entitlements"
```

---

## Task 7: `sign_up` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.SignUp`, `FakeAnalytics`.
- Koin: `SignUpViewModel` is registered via `viewModelOf(::SignUpViewModel)` (AuthModule.kt:47) — adding a constructor param resolves via `get()`; **no module change needed**.

Scope note: fire `sign_up` only on the **email/password** create path (`signUpWithEmail` success, line ~199). SSO (`NavigateToHome`) can be either sign-up OR returning sign-in; firing there would over-count returning users. SSO sign-up tracking (via `additionalUserInfo.isNewUser`) is backlog (PR 2).

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.danzucker.stitchpad.feature.auth.presentation.signup

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
// + existing test deps: a fake AuthRepository returning Result.Success from signUpWithEmail,
//   a PatternValidator stub, UnconfinedTestDispatcher (follow AuthViewModel test patterns)
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SignUpViewModelAnalyticsTest {
    @Test
    fun emailSignUpSuccessLogsSignUpEvent() = runTest {
        val analytics = FakeAnalytics()
        val vm = SignUpViewModel(
            authRepository = FakeAuthRepository(signUpResult = success()),
            emailValidator = alwaysValidValidator(),
            analytics = analytics,
        )
        // set valid fields + accept terms, then:
        vm.onAction(SignUpAction.OnSignUpClick)
        // allow the coroutine to run, then assert:
        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp))
    }
}
```

(Use the existing auth test fakes/helpers; if none exist, write a minimal `FakeAuthRepository` in this test file implementing `AuthRepository` with `signUpWithEmail` returning `Result.Success`.)

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SignUpViewModelAnalyticsTest*"`
Expected: FAIL — `SignUpViewModel` has no `analytics` param.

- [ ] **Step 3: Add the dependency + fire the event.** Add the import `import com.danzucker.stitchpad.core.analytics.domain.Analytics` and `import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent`. Change the constructor (line 26):

```kotlin
class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator,
    private val analytics: Analytics,
) : ViewModel() {
```

In `signUp()`, the success branch (line ~199), fire before sending the nav event:

```kotlin
is Result.Success -> {
    analytics.logEvent(AnalyticsEvent.SignUp)
    _events.send(SignUpEvent.NavigateToEmailVerification)
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SignUpViewModelAnalyticsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log sign_up on email signup success"
```

---

## Task 8: `workshop_setup_completed` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt` (line 55 — explicit lambda)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.WorkshopSetupCompleted`, `FakeAnalytics`.
- Koin: registered via explicit lambda `viewModel { WorkshopSetupViewModel(get(), get(), get(), get()) }` (AuthModule.kt:55) — **add one more `get()`**.

Fire on the save-success path that emits `WorkshopSetupEvent.NavigateToHome` after the profile is saved (the primary completion, ~line 259). Do NOT fire on a skip/abandon path. Read the file to confirm which `NavigateToHome` is the save-complete one (there are two sends — the post-save one, and one at ~422 that may be a different path); fire only on the genuine "setup saved" branch.

- [ ] **Step 1: Write the failing test** (inject `FakeAnalytics`; drive a successful save; assert `analytics.events.contains(AnalyticsEvent.WorkshopSetupCompleted)`). Follow the same shape as Task 7's test, using the workshop VM's existing fakes.

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*WorkshopSetupViewModelAnalyticsTest*"`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Add the dependency + fire.** Add `analytics: Analytics` as the last constructor param of `WorkshopSetupViewModel` (line 41) and the two analytics imports. At the save-success branch, immediately before the post-save `_events.send(WorkshopSetupEvent.NavigateToHome)` (~line 259):

```kotlin
analytics.logEvent(AnalyticsEvent.WorkshopSetupCompleted)
_events.send(WorkshopSetupEvent.NavigateToHome)
```

- [ ] **Step 4: Update Koin.** In `AuthModule.kt` line 55:

```kotlin
viewModel { WorkshopSetupViewModel(get(), get(), get(), get(), get()) }
```

- [ ] **Step 5: Run test + Android compile.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*WorkshopSetupViewModelAnalyticsTest*" && ./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log workshop_setup_completed on save success"
```

---

## Task 9: `customer_created` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.CustomerCreated`, `FakeAnalytics`.
- Koin: `viewModelOf(::CustomerFormViewModel)` (CustomerModule.kt:28) — **no module change**.

Fire only on the **create** branch (the `is Result.Success ->` at ~line 154 that produces `newId` and emits `NavigateToNewCustomerMeasurement`), NOT the update branch (~line 96).

- [ ] **Step 1: Write the failing test** (fake customer repository returns `Result.Success` for create; assert `analytics.events.contains(AnalyticsEvent.CustomerCreated)`; also assert it does NOT fire on the edit/update path).

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomerFormViewModelAnalyticsTest*"`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Add dependency + fire.** Add `private val analytics: Analytics` to the constructor (line 30) and the imports. In the create success branch (~line 154):

```kotlin
is Result.Success -> {
    analytics.logEvent(AnalyticsEvent.CustomerCreated)
    val event = CustomerFormEvent.NavigateToNewCustomerMeasurement(customerId = newId)
    _events.send(event)
    // ...existing
}
```

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomerFormViewModelAnalyticsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log customer_created on create success"
```

---

## Task 10: `order_created` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.OrderCreated`, `FakeAnalytics`.
- Koin: `viewModelOf(::OrderFormViewModel)` (OrderModule.kt:22) — **no module change**.

In `save()` (~line 631), the create path calls `orderRepository.createOrder(uid, order)` (~line 886) and then emits `OrderFormEvent.OrderSaved` (~line 892); the edit path calls `updateOrder` (~line 884). Fire `order_created` only on the create branch (`orderId == null`), after `createOrder` succeeds.

- [ ] **Step 1: Write the failing test** (fake order repository; drive a successful new-order save with `orderId == null`; assert `analytics.events.contains(AnalyticsEvent.OrderCreated)`; assert it does NOT fire on an edit, i.e. when `orderId != null`).

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderFormViewModelAnalyticsTest*"`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Add dependency + fire.** Add `private val analytics: Analytics` to the constructor (line 54) and imports. Read the create/update fork around lines 884–892 and place the call in the create branch only, e.g.:

```kotlin
if (orderId == null) {
    orderRepository.createOrder(uid, order)
    analytics.logEvent(AnalyticsEvent.OrderCreated)
} else {
    orderRepository.updateOrder(uid, order)
}
```

(Match the actual control flow you find — the create call may be inside a `when`/`if`; the rule is: fire exactly once, only when a NEW order was created and the repository call succeeded, before/at `OrderSaved`.)

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderFormViewModelAnalyticsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log order_created on new-order save success"
```

---

## Task 11: `ai_feature_used` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt` (line ~68 — explicit lambda)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.AiFeatureUsed`, `FakeAnalytics`.
- Koin: registered via explicit lambda `DraftMessageViewModel( ... )` (SmartModule.kt:68) — **add a `get()` for analytics** in the correct constructor position.

Fire on the successful draft generation: in `generate()` the `is Result.Success ->` branch (~line 158), with `feature = "draft_message"`.

- [ ] **Step 1: Write the failing test** (fake draft use-case/repository returns `Result.Success`; assert `analytics.events.contains(AnalyticsEvent.AiFeatureUsed("draft_message"))`).

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageViewModelAnalyticsTest*"`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Add dependency + fire.** Add `private val analytics: Analytics` to `DraftMessageViewModel` (line 30) and imports. In the success branch (~line 158):

```kotlin
is Result.Success -> {
    analytics.logEvent(AnalyticsEvent.AiFeatureUsed(feature = "draft_message"))
    // ...existing success handling
}
```

- [ ] **Step 4: Update Koin.** In `SmartModule.kt`, add `get()` for the new `analytics` param in the `DraftMessageViewModel( ... )` lambda (match its constructor order — analytics is last).

- [ ] **Step 5: Run test + Android compile.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageViewModelAnalyticsTest*" && ./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log ai_feature_used on draft generation"
```

---

## Task 12: `upgrade_completed` event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModel.kt`
- Read first: the VM around lines 40–60 (`UpgradeEvent.UpgradeDetected`) and 140–170 (checkout outcomes) to find the tier value.
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent.UpgradeCompleted`, `FakeAnalytics`.
- Koin: `viewModelOf(::UpgradeViewModel)` (FreemiumModule.kt:50) — **no module change**.

Fire when the upgrade is **confirmed**. Prefer the `UpgradeEvent.UpgradeDetected` path (~line 50) because it fires for BOTH Apple-IAP-async grants and Paystack-immediate grants — a single correct completion signal. Use the tier the entitlement resolves to (read the VM/state for the granted tier; if only "upgraded" is known without a tier, pass the resolved tier string from entitlements, defaulting to `"pro"` only if the VM genuinely cannot distinguish — note this limitation in the commit). Ensure it fires once per detected upgrade, not on every recomposition/poll (guard on a state transition into "upgraded").

- [ ] **Step 1: Write the failing test** (drive the upgrade-detected transition; assert exactly one `AnalyticsEvent.UpgradeCompleted(tier = ...)` is logged).

- [ ] **Step 2: Run test to verify it fails.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*UpgradeViewModelAnalyticsTest*"`
Expected: FAIL — constructor mismatch.

- [ ] **Step 3: Add dependency + fire.** Add `private val analytics: Analytics` to `UpgradeViewModel` (line 26) and imports. At the confirmed-upgrade transition (before/with emitting `UpgradeEvent.UpgradeDetected`, ~line 50):

```kotlin
analytics.logEvent(AnalyticsEvent.UpgradeCompleted(tier = resolvedTier))
_events.send(UpgradeEvent.UpgradeDetected)
```

where `resolvedTier` is the granted tier string from entitlements/state. Guard so this fires only on the transition into the upgraded state.

- [ ] **Step 4: Run test to verify it passes.**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*UpgradeViewModelAnalyticsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModelAnalyticsTest.kt
git commit -m "feat(analytics): log upgrade_completed when an upgrade is granted"
```

---

## Task 13: Debug-menu analytics toggle (DebugView control)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/AnalyticsDebugActions.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt`
- Modify: the debug menu screen + its ViewModel under `feature/debug/presentation/` (follow how `FreemiumDebugActions` / `DigestDebugActions` are surfaced)
- Read first: an existing `*DebugActions.kt` (e.g. `FreemiumDebugActions.kt`) and `DebugModule.kt` for the exact pattern.

**Interfaces:**
- Consumes: GitLive `Firebase.analytics.setAnalyticsCollectionEnabled(enabled: Boolean)`; a persisted boolean (use the same settings/preferences mechanism the other debug actions use).
- Produces: `class AnalyticsDebugActions` with `fun isAnalyticsEnabled(): Boolean` and `suspend fun setAnalyticsEnabled(enabled: Boolean)`.

Purpose: let Daniel turn analytics collection off in debug so manual smoke testing doesn't pollute production funnels, and on to verify events in **DebugView**. DebugView itself is enabled per-run via the platform debug flag (Android: `adb shell setprop debug.firebase.analytics.app com.danzucker.stitchpad`; iOS: add `-FIRDebugEnabled` / `-FIRAnalyticsDebugEnabled` launch argument in the Xcode scheme) — document this in the PR; the toggle controls whether events are sent at all. This is debug-source-only behaviour; release builds always collect (Firebase default).

- [ ] **Step 1: Create `AnalyticsDebugActions.kt`:**

```kotlin
package com.danzucker.stitchpad.core.debug

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

/**
 * Debug-only control over analytics collection so manual smoke tests don't pollute
 * production funnels. Persists the choice and applies it to the SDK. Release builds
 * never expose this and always collect (Firebase default).
 */
class AnalyticsDebugActions(/* inject the same settings store other DebugActions use */) {
    fun isAnalyticsEnabled(): Boolean = /* read persisted flag, default true */ true

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        // persist the flag
        Firebase.analytics.setAnalyticsCollectionEnabled(enabled)
    }
}
```

Replace the constructor + persistence with the exact mechanism used by `FreemiumDebugActions` (read it first). Apply the persisted value once at startup too (in the same place other debug actions are initialised, or in `AnalyticsModule`'s `createdAtStart` block when `isDebugBuild`).

- [ ] **Step 2: Register in `DebugModule.kt`** following the existing `single { ... }` pattern for the other DebugActions.

- [ ] **Step 3: Surface in the debug menu** — add a toggle row to the debug menu screen + a state/action in its ViewModel, mirroring an existing toggle (e.g. the freemium ones). Keep the Screen `@Preview` working.

- [ ] **Step 4: Compile (debug).**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/AnalyticsDebugActions.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug
git commit -m "feat(analytics): debug-menu toggle for analytics collection"
```

---

## Task 14: Cross-platform verification, ops, PR

**Files:** none (verification + ops + PR).

**Ops (outside code) — do these before device verification:**
1. In the Firebase console for project `stitchpad-30607`, **enable Google Analytics** (links/creates a GA4 property).
2. Re-download `google-services.json` → `composeApp/` and `GoogleService-Info.plist` → `iosApp/iosApp/` so they include the analytics/measurement config. Do NOT commit them (gitignored).
3. In GA4 admin, **enable the BigQuery export** (the day-one insurance — data only banks from when it's on).
4. iOS: confirm the analytics SDK/pod is pulled (clean Xcode build) and the plist has the measurement id.

- [ ] **Step 1: Full detekt + unit tests + both compiles.**

Run: `./gradlew detekt && ./gradlew :composeApp:testDebugUnitTest && ./gradlew :composeApp:compileDebugKotlinAndroid && ./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: all BUILD SUCCESSFUL / tests green. (Capture each exit code directly — piped gradle hides failures.)

- [ ] **Step 2: Android device DebugView verification.** Enable DebugView (`adb shell setprop debug.firebase.analytics.app com.danzucker.stitchpad`), install debug, run: fresh signup → workshop setup → add a customer → add an order → use Draft Message → upgrade. Confirm in Firebase console → DebugView that `screen_view` fires across screens and all 6 events fire once each with correct params, and that `subscription_tier` + user id are set.

- [ ] **Step 3: iOS device/sim DebugView verification.** Add `-FIRDebugEnabled` to the scheme, clean-build the iOS app in Xcode (the real GitLive-analytics link gate — `build-ios` CI compiles the framework, not the Swift target), repeat the same journey, confirm the same events in DebugView on iOS. This is the mandatory ship gate.

- [ ] **Step 4: Release smoke test (R8).** Build a release variant and confirm analytics still initialises and events fire (per the APK/R8 note — add any ProGuard keep rules the analytics SDK needs if events stop in release).

- [ ] **Step 5: Open the PR** with the manual smoke-test steps above in the description (Daniel is QA). Ensure Cursor Bugbot + `codex review` (pre-push hook) both run.

```bash
git push -u origin feat/analytics-user-journey
gh pr create --title "feat(analytics): Firebase Analytics user-journey instrumentation" --body "<smoke test steps + screenshots from DebugView (Android + iOS)>"
```

---

## Backlog (future PRs — not in this plan)

- **PR 2 — mid-funnel events** (same interface, more `AnalyticsEvent` subtypes): measurement added, order status advanced, receipt sent, WhatsApp confirm tapped, customer/order deleted, search used, style/fabric photo added; plus SSO `sign_up` via `additionalUserInfo.isNewUser`.
- **PR 3 — iOS Crashlytics wiring** (existing known gap; pairs naturally with Firebase init).
- **Later** — Looker Studio dashboard over BigQuery once funnels reveal what to chart; tap-level events only where a funnel shows a drop-off worth dissecting.
- **Dependency** — `subscription_tier` must follow the `tier` vs `subscriptionTier` field consolidation; don't introduce a second field.

## Self-Review

- **Spec coverage:** architecture (Tasks 2–4), screen views (5), 6 milestones (7–12), user properties/userId (6), no-PII + fire-and-forget (2–3 contracts), DebugView toggle (13), BigQuery + ops + iOS gate + smoke test (14), backlog (final section). All spec sections map to a task. The one intentional change (plain `customer_created`/`order_created` vs `first_*`) is flagged at the top for review.
- **Placeholders:** the EntitlementsProvider tier-flow adaptation (Task 6 Step 4) and the debug persistence mechanism (Task 13 Step 1) are the only "read the real API and match it" steps — unavoidable without reading those files at execution time; both name the exact file to read and the exact shape to produce.
- **Type consistency:** `Analytics`, `AnalyticsEvent.*`, `AnalyticsUserProperty.SUBSCRIPTION_TIER`, `FakeAnalytics`, `FirebaseAnalyticsTracker`, `AnalyticsSink` used consistently across all tasks. Koin registration form (viewModelOf vs lambda) verified per VM against the actual modules.
