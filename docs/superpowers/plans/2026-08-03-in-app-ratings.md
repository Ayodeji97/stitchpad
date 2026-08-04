# In-App Ratings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ask proven, happy StitchPad users to rate at a delight moment — routing happy users to the native in-app review UI and unhappy users to the Tally feedback hub — plus a manual "Rate StitchPad" row in Settings.

**Architecture:** Mirror the existing `CelebrationController` pattern. A `ReviewController` (app-lifetime Koin `single`) owns a `current: StateFlow<Boolean>` sentiment-sheet flag; ViewModels report delight moments and order-creates through a narrow `ReviewArmer` interface (fire-and-forget). A pure `ReviewGate` decides eligibility from device/user signals persisted in `ReviewPreferences` (SharedPreferences/NSUserDefaults, exactly like `OnboardingPreferences`). Two platform surfaces — native in-app review (auto-prompt) and a store deep-link (manual button) — sit behind a `StoreReviewLauncher` interface with Android/iOS actuals.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, kotlinx-coroutines, kotlinx-datetime, Google Play In-App Review (`review-ktx`) on Android, StoreKit `SKStoreReviewController` on iOS.

**Spec:** `docs/superpowers/specs/2026-08-03-in-app-ratings-design.md`

## Global Constraints

- MVI + Root/Screen split; all state in ViewModel; every Screen composable has a `@Preview` (light + dark).
- No hardcoded user-facing strings — use `compose.resources` (`Res.string.*`). In `strings.xml` never use a backslash escape; write `&apos;` for apostrophes (CMP iOS renders `\'` literally).
- `Result<T,E>` for expected failures; analytics is fire-and-forget and must never throw or block a flow.
- Koin: `single`/`viewModelOf` constructor refs; `koinViewModel()`/`koinInject()` in Root composables only.
- Time comes from an injected `now: () -> Long` (never `Clock.System.now()` inside the controller) — per the iOS Clock-injection gotcha. `viewModelOf` cannot skip defaulted params; keep injected params non-defaulted.
- `toEpochDays()` returns `Long` on iOS / `Int` on JVM — always `.toLong()` it before storing.
- Store identifiers (verbatim): iOS app id `6770673562`; Android package `com.danzucker.stitchpad`; Tally feedback URL `https://tally.so/r/5BgVVb`.
- Gate defaults (tunable constants in `ReviewGate`): min 3 days since install, min 2 distinct open-days, min 3 orders created; per-outcome cooldowns — rated 120 days, gave-feedback 60 days, dismissed 30 days.

---

### Task 1: `ReviewGate` pure eligibility (domain) + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain/ReviewOutcome.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain/ReviewSignals.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain/ReviewGate.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/domain/ReviewGateTest.kt`

**Interfaces:**
- Produces: `enum class ReviewOutcome { NONE, RATED, GAVE_FEEDBACK, DISMISSED }`; `data class ReviewSignals(installedAtMillis: Long, distinctOpenDays: Int, ordersCreated: Int, lastPromptAtMillis: Long, lastOutcome: ReviewOutcome)`; `object ReviewGate { fun isEligible(signals: ReviewSignals, nowMillis: Long): Boolean }` plus public threshold consts.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.review.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewGateTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1_000L * dayMs // day 1000 in epoch millis

    private fun eligibleSignals() = ReviewSignals(
        installedAtMillis = now - 5 * dayMs,
        distinctOpenDays = 3,
        ordersCreated = 4,
        lastPromptAtMillis = 0L,
        lastOutcome = ReviewOutcome.NONE,
    )

    @Test
    fun fully_qualified_user_is_eligible() {
        assertTrue(ReviewGate.isEligible(eligibleSignals(), now))
    }

    @Test
    fun brand_new_install_is_not_eligible() {
        val s = eligibleSignals().copy(installedAtMillis = now - 1 * dayMs)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun unstamped_install_is_not_eligible() {
        val s = eligibleSignals().copy(installedAtMillis = 0L)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun single_open_day_is_not_eligible() {
        val s = eligibleSignals().copy(distinctOpenDays = 1)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun too_few_orders_is_not_eligible() {
        val s = eligibleSignals().copy(ordersCreated = 2)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun rated_user_within_cooldown_is_not_eligible() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.RATED,
            lastPromptAtMillis = now - 100 * dayMs,
        )
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun rated_user_past_cooldown_is_eligible_again() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.RATED,
            lastPromptAtMillis = now - 121 * dayMs,
        )
        assertTrue(ReviewGate.isEligible(s, now))
    }

    @Test
    fun dismissed_user_past_short_cooldown_is_eligible() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.DISMISSED,
            lastPromptAtMillis = now - 31 * dayMs,
        )
        assertTrue(ReviewGate.isEligible(s, now))
    }

    @Test
    fun cooldown_lengths_are_ordered_rated_longest() {
        assertTrue(ReviewGate.cooldownMillisFor(ReviewOutcome.RATED) > ReviewGate.cooldownMillisFor(ReviewOutcome.GAVE_FEEDBACK))
        assertTrue(ReviewGate.cooldownMillisFor(ReviewOutcome.GAVE_FEEDBACK) > ReviewGate.cooldownMillisFor(ReviewOutcome.DISMISSED))
        assertEquals(0L, ReviewGate.cooldownMillisFor(ReviewOutcome.NONE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.review.domain.ReviewGateTest"`
Expected: FAIL — unresolved references `ReviewGate`, `ReviewSignals`, `ReviewOutcome`.

- [ ] **Step 3: Write the implementation**

`ReviewOutcome.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.domain

/** The last thing the user did with a review prompt. NONE = never prompted. */
enum class ReviewOutcome { NONE, RATED, GAVE_FEEDBACK, DISMISSED }
```

`ReviewSignals.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.domain

/**
 * Everything [ReviewGate] needs to decide eligibility. installedAt + distinctOpenDays
 * are device-wide (device tenure); ordersCreated + lastPrompt* are per signed-in user.
 */
data class ReviewSignals(
    val installedAtMillis: Long,
    val distinctOpenDays: Int,
    val ordersCreated: Int,
    val lastPromptAtMillis: Long,
    val lastOutcome: ReviewOutcome,
)
```

`ReviewGate.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.domain

/**
 * Pure, testable eligibility for the review prompt. Asks a proven, engaged user at a
 * happy moment — never a brand-new install, never inside a per-outcome cooldown.
 */
object ReviewGate {
    const val MIN_DAYS_SINCE_INSTALL = 3
    const val MIN_DISTINCT_OPEN_DAYS = 2
    const val MIN_ORDERS_CREATED = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val COOLDOWN_RATED_DAYS = 120
    private const val COOLDOWN_FEEDBACK_DAYS = 60
    private const val COOLDOWN_DISMISSED_DAYS = 30

    fun cooldownMillisFor(outcome: ReviewOutcome): Long = when (outcome) {
        ReviewOutcome.NONE -> 0L
        ReviewOutcome.RATED -> COOLDOWN_RATED_DAYS * DAY_MS
        ReviewOutcome.GAVE_FEEDBACK -> COOLDOWN_FEEDBACK_DAYS * DAY_MS
        ReviewOutcome.DISMISSED -> COOLDOWN_DISMISSED_DAYS * DAY_MS
    }

    fun isEligible(signals: ReviewSignals, nowMillis: Long): Boolean {
        if (signals.installedAtMillis <= 0L) return false
        val daysSinceInstall = (nowMillis - signals.installedAtMillis) / DAY_MS
        if (daysSinceInstall < MIN_DAYS_SINCE_INSTALL) return false
        if (signals.distinctOpenDays < MIN_DISTINCT_OPEN_DAYS) return false
        if (signals.ordersCreated < MIN_ORDERS_CREATED) return false
        if (signals.lastOutcome != ReviewOutcome.NONE) {
            val cooldown = cooldownMillisFor(signals.lastOutcome)
            if (nowMillis - signals.lastPromptAtMillis < cooldown) return false
        }
        return true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.review.domain.ReviewGateTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/domain
git commit -m "feat(review): pure ReviewGate eligibility + signals"
```

---

### Task 2: Analytics events

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt` (append new variants before the closing brace)

**Interfaces:**
- Produces: `AnalyticsEvent.ReviewPromptShown`, `AnalyticsEvent.ReviewSentiment(sentiment)`, `AnalyticsEvent.ReviewInAppRequested`, `AnalyticsEvent.ReviewFeedbackOpened`, `AnalyticsEvent.ReviewStoreListingOpened`.

- [ ] **Step 1: Add the events**

Append inside the `AnalyticsEvent` sealed interface:
```kotlin
    /** The sentiment bottom sheet was shown (gate passed). */
    data object ReviewPromptShown : AnalyticsEvent {
        override val name = "review_prompt_shown"
    }

    /** [sentiment] ∈ positive|negative|dismissed — the user's answer to the sentiment sheet. */
    data class ReviewSentiment(val sentiment: String) : AnalyticsEvent {
        override val name = "review_sentiment"
        override val params = mapOf("sentiment" to sentiment)
    }

    /** The native in-app review API was invoked (fire-and-forget; may be OS-throttled). */
    data object ReviewInAppRequested : AnalyticsEvent {
        override val name = "review_inapp_requested"
    }

    /** An unhappy user was routed to the Tally feedback hub. */
    data object ReviewFeedbackOpened : AnalyticsEvent {
        override val name = "review_feedback_opened"
    }

    /** The Settings "Rate StitchPad" row opened the store listing. */
    data object ReviewStoreListingOpened : AnalyticsEvent {
        override val name = "review_store_listing_opened"
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt
git commit -m "feat(review): analytics events for review prompt flow"
```

---

### Task 3: `ReviewPreferences` store (data) — interface + expect/actual

Mirrors `OnboardingPreferences`. Device-wide keys: install millis, last-open-epoch-day, distinct-open-days count. Per-user keys (prefixed by userId): orders-created count, last-prompt millis, last outcome.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/data/ReviewPreferencesStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/data/ReviewPreferences.kt`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/review/data/ReviewPreferences.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/review/data/ReviewPreferences.ios.kt`

**Interfaces:**
- Consumes: `ReviewSignals`, `ReviewOutcome` (Task 1).
- Produces: `interface ReviewPreferencesStore` with the methods below; `expect class ReviewPreferences : ReviewPreferencesStore`.

- [ ] **Step 1: Write the common interface**

`ReviewPreferencesStore.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

/**
 * Local, offline-safe persistence for review-prompt signals. Device-wide: install
 * timestamp + distinct open-days. Per-user (keyed by authUid): orders created + last
 * prompt time/outcome. No Firestore — mirrors OnboardingPreferences.
 */
interface ReviewPreferencesStore {
    /** Records now as the install time on first ever call; later calls are no-ops. */
    suspend fun stampInstallIfAbsent(nowMillis: Long)

    /** If [epochDay] differs from the last recorded open-day, bumps the distinct-day count. */
    suspend fun recordOpenDay(epochDay: Long)

    suspend fun incrementOrdersCreated(userId: String)

    suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long)

    /** Aggregates the device + per-user signals for [ReviewGate]. */
    suspend fun loadSignals(userId: String): ReviewSignals

    /** Debug-menu only: clears every review key. Idempotent. */
    suspend fun resetForDebug()
}
```

`ReviewPreferences.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.data

expect class ReviewPreferences : ReviewPreferencesStore
```

- [ ] **Step 2: Write the Android actual**

`ReviewPreferences.android.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.data

import android.content.Context
import android.content.SharedPreferences
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

actual class ReviewPreferences(context: Context) : ReviewPreferencesStore {
    // Same store file OnboardingPreferences uses — distinct key namespace ("review_").
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stitchpad_prefs", Context.MODE_PRIVATE)

    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (prefs.getLong(KEY_INSTALL_MILLIS, 0L) == 0L) {
            prefs.edit().putLong(KEY_INSTALL_MILLIS, nowMillis).apply()
        }
    }

    override suspend fun recordOpenDay(epochDay: Long) {
        val last = prefs.getLong(KEY_LAST_OPEN_DAY, Long.MIN_VALUE)
        if (epochDay != last) {
            prefs.edit()
                .putLong(KEY_LAST_OPEN_DAY, epochDay)
                .putInt(KEY_DISTINCT_OPEN_DAYS, prefs.getInt(KEY_DISTINCT_OPEN_DAYS, 0) + 1)
                .apply()
        }
    }

    override suspend fun incrementOrdersCreated(userId: String) {
        val key = ordersKey(userId)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        prefs.edit()
            .putLong(lastPromptKey(userId), nowMillis)
            .putString(outcomeKey(userId), outcome.name)
            .apply()
    }

    override suspend fun loadSignals(userId: String): ReviewSignals = ReviewSignals(
        installedAtMillis = prefs.getLong(KEY_INSTALL_MILLIS, 0L),
        distinctOpenDays = prefs.getInt(KEY_DISTINCT_OPEN_DAYS, 0),
        ordersCreated = prefs.getInt(ordersKey(userId), 0),
        lastPromptAtMillis = prefs.getLong(lastPromptKey(userId), 0L),
        lastOutcome = prefs.getString(outcomeKey(userId), null)
            ?.let { runCatching { ReviewOutcome.valueOf(it) }.getOrNull() }
            ?: ReviewOutcome.NONE,
    )

    override suspend fun resetForDebug() {
        val editor = prefs.edit()
            .remove(KEY_INSTALL_MILLIS)
            .remove(KEY_LAST_OPEN_DAY)
            .remove(KEY_DISTINCT_OPEN_DAYS)
        prefs.all.keys
            .filter {
                it.startsWith(PREFIX_ORDERS) ||
                    it.startsWith(PREFIX_LAST_PROMPT) ||
                    it.startsWith(PREFIX_OUTCOME)
            }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    private fun ordersKey(userId: String) = "$PREFIX_ORDERS$userId"
    private fun lastPromptKey(userId: String) = "$PREFIX_LAST_PROMPT$userId"
    private fun outcomeKey(userId: String) = "$PREFIX_OUTCOME$userId"

    companion object {
        private const val KEY_INSTALL_MILLIS = "review_install_millis"
        private const val KEY_LAST_OPEN_DAY = "review_last_open_day"
        private const val KEY_DISTINCT_OPEN_DAYS = "review_distinct_open_days"
        private const val PREFIX_ORDERS = "review_orders_created_"
        private const val PREFIX_LAST_PROMPT = "review_last_prompt_millis_"
        private const val PREFIX_OUTCOME = "review_last_outcome_"
    }
}
```

- [ ] **Step 3: Write the iOS actual**

`ReviewPreferences.ios.kt` — same keys, `NSUserDefaults`. `setInteger`/`integerForKey` store `NSInteger` (64-bit `Long` on device), fine for millis, epoch-day, and counts. Outcome is a `String`.
```kotlin
package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals
import platform.Foundation.NSUserDefaults

actual class ReviewPreferences : ReviewPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (defaults.integerForKey(KEY_INSTALL_MILLIS) == 0L) {
            defaults.setInteger(nowMillis, forKey = KEY_INSTALL_MILLIS)
        }
    }

    override suspend fun recordOpenDay(epochDay: Long) {
        // Absent key reads back as 0; guard with an explicit "seen" flag so epochDay 0 counts once.
        val seen = defaults.boolForKey(KEY_HAS_OPEN_DAY)
        val last = defaults.integerForKey(KEY_LAST_OPEN_DAY)
        if (!seen || epochDay != last) {
            defaults.setBool(true, forKey = KEY_HAS_OPEN_DAY)
            defaults.setInteger(epochDay, forKey = KEY_LAST_OPEN_DAY)
            defaults.setInteger(defaults.integerForKey(KEY_DISTINCT_OPEN_DAYS) + 1, forKey = KEY_DISTINCT_OPEN_DAYS)
        }
    }

    override suspend fun incrementOrdersCreated(userId: String) {
        val key = ordersKey(userId)
        defaults.setInteger(defaults.integerForKey(key) + 1, forKey = key)
    }

    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        defaults.setInteger(nowMillis, forKey = lastPromptKey(userId))
        defaults.setObject(outcome.name, forKey = outcomeKey(userId))
    }

    override suspend fun loadSignals(userId: String): ReviewSignals = ReviewSignals(
        installedAtMillis = defaults.integerForKey(KEY_INSTALL_MILLIS),
        distinctOpenDays = defaults.integerForKey(KEY_DISTINCT_OPEN_DAYS).toInt(),
        ordersCreated = defaults.integerForKey(ordersKey(userId)).toInt(),
        lastPromptAtMillis = defaults.integerForKey(lastPromptKey(userId)),
        lastOutcome = (defaults.stringForKey(outcomeKey(userId)))
            ?.let { runCatching { ReviewOutcome.valueOf(it) }.getOrNull() }
            ?: ReviewOutcome.NONE,
    )

    override suspend fun resetForDebug() {
        listOf(KEY_INSTALL_MILLIS, KEY_HAS_OPEN_DAY, KEY_LAST_OPEN_DAY, KEY_DISTINCT_OPEN_DAYS)
            .forEach { defaults.removeObjectForKey(it) }
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(PREFIX_ORDERS) || it.startsWith(PREFIX_LAST_PROMPT) || it.startsWith(PREFIX_OUTCOME) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun ordersKey(userId: String) = "$PREFIX_ORDERS$userId"
    private fun lastPromptKey(userId: String) = "$PREFIX_LAST_PROMPT$userId"
    private fun outcomeKey(userId: String) = "$PREFIX_OUTCOME$userId"

    companion object {
        private const val KEY_INSTALL_MILLIS = "review_install_millis"
        private const val KEY_HAS_OPEN_DAY = "review_has_open_day"
        private const val KEY_LAST_OPEN_DAY = "review_last_open_day"
        private const val KEY_DISTINCT_OPEN_DAYS = "review_distinct_open_days"
        private const val PREFIX_ORDERS = "review_orders_created_"
        private const val PREFIX_LAST_PROMPT = "review_last_prompt_millis_"
        private const val PREFIX_OUTCOME = "review_last_outcome_"
    }
}
```

- [ ] **Step 4: Verify it compiles on both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (`expect`/`actual` matched on both).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/data composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/review/data composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/review/data
git commit -m "feat(review): ReviewPreferences store (SharedPreferences + NSUserDefaults)"
```

---

### Task 4: `StoreReviewLauncher` — in-app review API + store deep-link

Interface in common; Android uses Play In-App Review + a `market://` intent; iOS uses `SKStoreReviewController` + an `itms-apps://` deep link. Adds the Play dependency.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain/StoreReviewLauncher.kt`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/review/data/AndroidStoreReviewLauncher.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/review/data/IosStoreReviewLauncher.kt`
- Modify: `gradle/libs.versions.toml` (add `play-review` + `coroutines-play-services` libs)
- Modify: `composeApp/build.gradle.kts` (add both to the Android source set / dependencies)

**Interfaces:**
- Consumes: `CurrentActivityHolder` (existing, androidMain), `AppLogger` (existing).
- Produces: `interface StoreReviewLauncher { suspend fun requestInAppReview(); fun openStoreListing() }`.

- [ ] **Step 1: Common interface**

`StoreReviewLauncher.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.domain

/**
 * Two store-rating surfaces. [requestInAppReview] shows the OS-native in-app rating UI
 * (auto-prompt path; OS-throttled, silent, fire-and-forget). [openStoreListing] deep-links
 * to the store's write-review page (manual "Rate" button path). Neither throws — platform
 * failures are swallowed and logged.
 */
interface StoreReviewLauncher {
    suspend fun requestInAppReview()
    fun openStoreListing()
}
```

- [ ] **Step 2: Add dependencies**

In `gradle/libs.versions.toml` under `[versions]` add `playReview = "2.0.2"` and `coroutinesPlayServices` matching the existing coroutines version; under `[libraries]`:
```toml
play-review = { module = "com.google.android.play:review-ktx", version.ref = "playReview" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
```
In `composeApp/build.gradle.kts`, add both to the `androidMain.dependencies { ... }` block:
```kotlin
implementation(libs.play.review)
implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 3: Android actual**

`AndroidStoreReviewLauncher.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

private const val PACKAGE = "com.danzucker.stitchpad"
private const val MARKET_URL = "market://details?id=$PACKAGE"
private const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

class AndroidStoreReviewLauncher(
    private val activityHolder: CurrentActivityHolder,
    private val context: Context,
) : StoreReviewLauncher {

    override suspend fun requestInAppReview() {
        val activity = activityHolder.currentActivity() ?: return // match the accessor AndroidSsoCredentialProvider uses
        try {
            val manager = ReviewManagerFactory.create(context)
            val info = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, info).await()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = "StoreReview", throwable = e) { "in-app review flow failed" }
        }
    }

    override fun openStoreListing() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(tag = "StoreReview", throwable = e) { "Play app missing; falling back to web" }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
```
Note: use the same current-activity accessor that `AndroidSsoCredentialProvider`/`AndroidPushPermissionController` use from `CurrentActivityHolder` (adjust `currentActivity()` to the real property/method name).

- [ ] **Step 4: iOS actual**

`IosStoreReviewLauncher.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication

private const val APP_ID = "6770673562"
private const val WRITE_REVIEW_URL = "itms-apps://itunes.apple.com/app/id$APP_ID?action=write-review"

class IosStoreReviewLauncher : StoreReviewLauncher {

    override suspend fun requestInAppReview() {
        // Deprecated-but-supported no-scene form keeps the K/N binding simple; StoreKit
        // routes it to the active scene. Adjust to requestReviewInScene(...) if the
        // compiler requires a UIWindowScene on the linked StoreKit headers.
        SKStoreReviewController.requestReview()
    }

    override fun openStoreListing() {
        val url = NSURL.URLWithString(WRITE_REVIEW_URL) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
```

- [ ] **Step 5: Verify both targets compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (If the StoreKit symbol differs on the linked headers, adjust the call per the compiler error — logic is unchanged.)

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/domain/StoreReviewLauncher.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/review/data/AndroidStoreReviewLauncher.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/review/data/IosStoreReviewLauncher.kt
git commit -m "feat(review): StoreReviewLauncher (Play in-app review + StoreKit + deep links)"
```

---

### Task 5: `ReviewController` + `ReviewArmer` + effects (presentation) + tests

App-lifetime orchestrator, mirroring `CelebrationController`. Fire-and-forget public API so call sites need no coroutine.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/presentation/ReviewArmer.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/presentation/ReviewEffect.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/presentation/ReviewConfig.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/presentation/ReviewController.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/presentation/ReviewControllerTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/presentation/FakeReviewPreferences.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/presentation/FakeStoreReviewLauncher.kt`

**Interfaces:**
- Consumes: `ReviewPreferencesStore` (Task 3), `StoreReviewLauncher` (Task 4), `ReviewGate`/`ReviewSignals`/`ReviewOutcome` (Task 1), `Analytics`/`AnalyticsEvent` (Task 2).
- Produces:
  - `interface ReviewArmer { fun armFromDelight(); fun recordOrderCreated() }`
  - `sealed interface ReviewEffect { data class OpenFeedback(val url: String) : ReviewEffect }`
  - `object ReviewConfig { const val FEEDBACK_URL = "https://tally.so/r/5BgVVb" }`
  - `class ReviewController(...) : ReviewArmer` exposing `current: StateFlow<Boolean>`, `effects: Flow<ReviewEffect>`, `fun ensureRunning()`, `fun onLoveIt()`, `fun onNotReally()`, `fun onDismiss()`, `fun forceArmForDebug()`.

- [ ] **Step 1: Write the failing test + fakes**

`FakeReviewPreferences.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.feature.review.data.ReviewPreferencesStore
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

class FakeReviewPreferences(
    var installedAtMillis: Long = 0L,
    var distinctOpenDays: Int = 0,
    var ordersByUser: MutableMap<String, Int> = mutableMapOf(),
    var lastPromptByUser: MutableMap<String, Long> = mutableMapOf(),
    var outcomeByUser: MutableMap<String, ReviewOutcome> = mutableMapOf(),
) : ReviewPreferencesStore {
    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (installedAtMillis == 0L) installedAtMillis = nowMillis
    }
    override suspend fun recordOpenDay(epochDay: Long) { distinctOpenDays += 1 }
    override suspend fun incrementOrdersCreated(userId: String) {
        ordersByUser[userId] = (ordersByUser[userId] ?: 0) + 1
    }
    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        lastPromptByUser[userId] = nowMillis
        outcomeByUser[userId] = outcome
    }
    override suspend fun loadSignals(userId: String) = ReviewSignals(
        installedAtMillis = installedAtMillis,
        distinctOpenDays = distinctOpenDays,
        ordersCreated = ordersByUser[userId] ?: 0,
        lastPromptAtMillis = lastPromptByUser[userId] ?: 0L,
        lastOutcome = outcomeByUser[userId] ?: ReviewOutcome.NONE,
    )
    override suspend fun resetForDebug() {}
}
```

`FakeStoreReviewLauncher.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher

class FakeStoreReviewLauncher : StoreReviewLauncher {
    var inAppRequests = 0
    var listingOpens = 0
    override suspend fun requestInAppReview() { inAppRequests += 1 }
    override fun openStoreListing() { listingOpens += 1 }
}
```

`ReviewControllerTest.kt` (use the project's existing `FakeAnalytics` from `commonTest` if present; otherwise add a trivial recorder). Runs on `UnconfinedTestDispatcher` so the controller's `scope` work completes inline:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewControllerTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private var nowMillis = 1_000L * dayMs
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun logEvent(event: AnalyticsEvent) { events += event }
        override fun logScreenView(screenName: String) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(property: AnalyticsUserProperty, value: String) {}
    }

    private fun controller(
        prefs: FakeReviewPreferences,
        launcher: FakeStoreReviewLauncher,
        users: MutableStateFlow<String?>,
        scope: CoroutineScope,
    ) = ReviewController(
        preferences = prefs,
        analytics = analytics,
        launcher = launcher,
        authUserIds = users,
        scope = scope,
        now = { nowMillis },
    )

    private fun eligiblePrefs() = FakeReviewPreferences(
        installedAtMillis = nowMillis - 5 * dayMs,
        distinctOpenDays = 3,
        ordersByUser = mutableMapOf("u1" to 4),
    )

    @Test
    fun arm_from_delight_shows_sheet_when_eligible() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val c = controller(eligiblePrefs(), FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertTrue(c.current.value)
        assertTrue(events.any { it is AnalyticsEvent.ReviewPromptShown })
    }

    @Test
    fun arm_from_delight_no_show_when_too_few_orders() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs().also { it.ordersByUser["u1"] = 1 }
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertFalse(c.current.value)
    }

    @Test
    fun love_it_requests_native_review_and_records_rated() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val launcher = FakeStoreReviewLauncher()
        val c = controller(prefs, launcher, MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.onLoveIt()
        assertFalse(c.current.value)
        assertEquals(1, launcher.inAppRequests)
        assertEquals(ReviewOutcome.RATED, prefs.outcomeByUser["u1"])
        assertTrue(events.any { it is AnalyticsEvent.ReviewInAppRequested })
    }

    @Test
    fun not_really_emits_feedback_effect_and_records_feedback() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.effects.test {
            c.onNotReally()
            val effect = awaitItem()
            assertTrue(effect is ReviewEffect.OpenFeedback)
            assertEquals(ReviewConfig.FEEDBACK_URL, (effect as ReviewEffect.OpenFeedback).url)
        }
        assertEquals(ReviewOutcome.GAVE_FEEDBACK, prefs.outcomeByUser["u1"])
    }

    @Test
    fun dismiss_records_dismissed() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.onDismiss()
        assertEquals(ReviewOutcome.DISMISSED, prefs.outcomeByUser["u1"])
        assertFalse(c.current.value)
    }

    @Test
    fun rated_user_within_cooldown_is_not_re_armed() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs().also {
            it.outcomeByUser["u1"] = ReviewOutcome.RATED
            it.lastPromptByUser["u1"] = nowMillis - 10 * dayMs
        }
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertFalse(c.current.value)
    }

    @Test
    fun auth_user_change_clears_visible_prompt() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val users = MutableStateFlow<String?>("u1")
        val c = controller(eligiblePrefs(), FakeStoreReviewLauncher(), users, scope)
        c.armFromDelight()
        assertTrue(c.current.value)
        users.value = "u2"
        assertFalse(c.current.value)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.review.presentation.ReviewControllerTest"`
Expected: FAIL — unresolved `ReviewController`, `ReviewArmer`, `ReviewEffect`, `ReviewConfig`.

- [ ] **Step 3: Write the implementation**

`ReviewArmer.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

/**
 * Narrow seam ViewModels use to feed the review system without seeing the whole
 * controller. Both methods are fire-and-forget (they launch on the controller scope).
 */
interface ReviewArmer {
    /** A delight moment happened (payment recorded / order delivered). Maybe shows the sheet. */
    fun armFromDelight()

    /** An order was created — feeds the engagement gate. */
    fun recordOrderCreated()
}
```

`ReviewEffect.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

/** One-shot side effects the host runs (Compose-bound work the controller can't do). */
sealed interface ReviewEffect {
    data class OpenFeedback(val url: String) : ReviewEffect
}
```

`ReviewConfig.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

/** Static review config. Store deep-links live in the platform StoreReviewLauncher actuals. */
object ReviewConfig {
    const val FEEDBACK_URL = "https://tally.so/r/5BgVVb"
}
```

`ReviewController.kt`:
```kotlin
package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.review.data.ReviewPreferencesStore
import com.danzucker.stitchpad.feature.review.domain.ReviewGate
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * App-lifetime owner of the review prompt ("tell, don't ask"): ViewModels report delight
 * moments; the controller decides whether the sentiment sheet shows. Install time and the
 * distinct-open-day count are stamped on [ensureRunning]. Any auth-user change clears the
 * visible prompt so it never leaks across accounts. The gate's ordersCreated>=3 threshold
 * structurally prevents overlap with the only three (first-time) milestone celebrations.
 */
class ReviewController(
    private val preferences: ReviewPreferencesStore,
    private val analytics: Analytics,
    private val launcher: com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher,
    authUserIds: Flow<String?>,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) : ReviewArmer {

    private val _current = MutableStateFlow(false)
    val current: StateFlow<Boolean> = _current.asStateFlow()

    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = _effects.receiveAsFlow()

    private var currentUserId: String? = null
    private val mutex = Mutex()

    init {
        scope.launch {
            preferences.stampInstallIfAbsent(now())
            preferences.recordOpenDay(todayEpochDay())
        }
        scope.launch {
            authUserIds.distinctUntilChanged().collect { uid ->
                mutex.withLock {
                    currentUserId = uid
                    _current.value = false
                }
            }
        }
    }

    /** No-op; forces Koin to materialize the singleton at app start (see App.kt). */
    fun ensureRunning() = Unit

    override fun armFromDelight() {
        scope.launch {
            mutex.withLock {
                val uid = currentUserId ?: return@withLock
                if (_current.value) return@withLock
                val signals = preferences.loadSignals(uid)
                if (!ReviewGate.isEligible(signals, now())) return@withLock
                analytics.logEvent(AnalyticsEvent.ReviewPromptShown)
                _current.value = true
            }
        }
    }

    override fun recordOrderCreated() {
        scope.launch { currentUserId?.let { preferences.incrementOrdersCreated(it) } }
    }

    fun onLoveIt() {
        scope.launch {
            _current.value = false
            currentUserId?.let { preferences.recordPrompt(it, ReviewOutcome.RATED, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("positive"))
            analytics.logEvent(AnalyticsEvent.ReviewInAppRequested)
            launcher.requestInAppReview()
        }
    }

    fun onNotReally() {
        scope.launch {
            _current.value = false
            currentUserId?.let { preferences.recordPrompt(it, ReviewOutcome.GAVE_FEEDBACK, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("negative"))
            analytics.logEvent(AnalyticsEvent.ReviewFeedbackOpened)
            _effects.send(ReviewEffect.OpenFeedback(ReviewConfig.FEEDBACK_URL))
        }
    }

    fun onDismiss() {
        scope.launch {
            _current.value = false
            currentUserId?.let { preferences.recordPrompt(it, ReviewOutcome.DISMISSED, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("dismissed"))
        }
    }

    /** Debug-menu only: bypass the gate and show the sheet immediately. */
    fun forceArmForDebug() {
        scope.launch { _current.value = true }
    }

    private fun todayEpochDay(): Long =
        Instant.fromEpochMilliseconds(now())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toEpochDays()
            .toLong()
}
```
Note: `kotlin.time.Instant` + `kotlinx.datetime.toLocalDateTime` match the project's existing datetime usage; if the installed kotlinx-datetime version still exposes `kotlinx.datetime.Instant`, use that import instead — the logic is identical.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.review.presentation.ReviewControllerTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/review/presentation composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/review/presentation
git commit -m "feat(review): ReviewController orchestrator + ReviewArmer seam"
```

---

### Task 6: DI wiring — `reviewModule` + platform bindings + Koin registration

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReviewModule.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt`

**Interfaces:**
- Consumes: `ReviewController`, `ReviewArmer`, `ReviewPreferences`/`ReviewPreferencesStore`, `AndroidStoreReviewLauncher`/`IosStoreReviewLauncher`/`StoreReviewLauncher`, `CurrentActivityHolder`.
- Produces: `val reviewModule: Module` providing `ReviewController` (bound also as `ReviewArmer`).

- [ ] **Step 1: Create `reviewModule`**

`ReviewModule.kt`:
```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.review.presentation.ReviewArmer
import com.danzucker.stitchpad.feature.review.presentation.ReviewController
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

val reviewModule = module {
    single<CoroutineScope>(qualifier = named("reviewAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        ReviewController(
            preferences = get(),
            analytics = get(),
            launcher = get(),
            authUserIds = get<FirebaseAuth>().authStateChanged.map { it?.uid },
            scope = get(qualifier = named("reviewAppScope")),
            now = ::nowEpochMs,
        )
    } bind ReviewArmer::class
}
```

- [ ] **Step 2: Add platform bindings**

In `PlatformModule.android.kt` add (with imports):
```kotlin
single { ReviewPreferences(androidContext()) } bind ReviewPreferencesStore::class
single<StoreReviewLauncher> { AndroidStoreReviewLauncher(activityHolder = get(), context = androidContext()) }
```
In `PlatformModule.ios.kt` add (with imports):
```kotlin
single { ReviewPreferences() } bind ReviewPreferencesStore::class
single<StoreReviewLauncher> { IosStoreReviewLauncher() }
```

- [ ] **Step 3: Register `reviewModule`**

In `StitchPadApp.kt`, add `import com.danzucker.stitchpad.di.reviewModule` and add `reviewModule,` to the `modules(...)` list (e.g., right after `tutorialsModule,`).

- [ ] **Step 4: Verify Koin graph builds**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.review.*"` then a full `:composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`.
Expected: BUILD SUCCESSFUL. If the project has a Koin module-verification test, run it too and confirm it passes.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReviewModule.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt
git commit -m "feat(review): wire ReviewController + platform launchers into Koin"
```

---

### Task 7: `ReviewPromptHost` + sentiment sheet (UI) + strings + App.kt

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (add review strings)
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/review/ReviewPromptHost.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt`

**Interfaces:**
- Consumes: `ReviewController` (`current`, `effects`, `onLoveIt`, `onNotReally`, `onDismiss`, `ensureRunning`), `StitchPadButton`, `DesignTokens`.
- Produces: `@Composable fun ReviewPromptHost(content: @Composable () -> Unit)`.

- [ ] **Step 1: Add strings**

In `strings.xml` (no backslash escapes; `&apos;` for apostrophes — none needed here):
```xml
<string name="review_sentiment_title">Enjoying StitchPad?</string>
<string name="review_sentiment_subtitle">Your feedback helps us build the best tool for your business.</string>
<string name="review_sentiment_love">Love it</string>
<string name="review_sentiment_not_really">Not really</string>
<string name="review_sentiment_dismiss_cd">Close</string>
```

- [ ] **Step 2: Write `ReviewPromptHost`**

```kotlin
package com.danzucker.stitchpad.ui.components.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.review.presentation.ReviewController
import com.danzucker.stitchpad.feature.review.presentation.ReviewEffect
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.review_sentiment_love
import stitchpad.composeapp.generated.resources.review_sentiment_not_really
import stitchpad.composeapp.generated.resources.review_sentiment_subtitle
import stitchpad.composeapp.generated.resources.review_sentiment_title

/**
 * App-root host: shows the sentiment bottom sheet over whatever screen the user is on
 * when the controller arms it, and runs Compose-bound effects (opening the feedback URL).
 */
@Composable
fun ReviewPromptHost(content: @Composable () -> Unit) {
    val controller = koinInject<ReviewController>()
    val show by controller.current.collectAsState()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        controller.effects.collect { effect ->
            when (effect) {
                is ReviewEffect.OpenFeedback -> runCatching { uriHandler.openUri(effect.url) }
            }
        }
    }
    content()
    if (show) {
        ReviewSentimentSheet(
            onLoveIt = controller::onLoveIt,
            onNotReally = controller::onNotReally,
            onDismiss = controller::onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSentimentSheet(
    onLoveIt: () -> Unit,
    onNotReally: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.space6),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.review_sentiment_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.review_sentiment_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space4))
            StitchPadButton(
                text = stringResource(Res.string.review_sentiment_love),
                onClick = onLoveIt,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onNotReally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.review_sentiment_not_really))
            }
            Spacer(Modifier.height(DesignTokens.space2))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReviewSentimentSheetPreviewLight() {
    StitchPadTheme { ReviewSentimentSheet(onLoveIt = {}, onNotReally = {}, onDismiss = {}) }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReviewSentimentSheetPreviewDark() {
    StitchPadTheme(darkTheme = true) { ReviewSentimentSheet(onLoveIt = {}, onNotReally = {}, onDismiss = {}) }
}
```

- [ ] **Step 3: Wire into `App.kt`**

Add `import com.danzucker.stitchpad.feature.review.presentation.ReviewController` and `import com.danzucker.stitchpad.ui.components.review.ReviewPromptHost`. Near the existing `ensureRunning()` calls add:
```kotlin
koinInject<ReviewController>().ensureRunning()
```
Wrap the nav host inside the celebration host:
```kotlin
CelebrationOverlayHost {
    ReviewPromptHost {
        StitchPadNavHost(
            navController = navController,
            onboardingPreferences = onboardingPreferences,
        )
    }
}
```

- [ ] **Step 4: Verify build + previews render**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. In Android Studio, confirm both `ReviewSentimentSheet` previews render in light and dark.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/review composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt
git commit -m "feat(review): sentiment bottom sheet host wired at app root"
```

---

### Task 8: Delight + order-create triggers in ViewModels

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

`viewModelOf(::OrderDetailViewModel)` and the order-form VM factory resolve the new `ReviewArmer` param automatically (it is a Koin `single`), so no `OrderModule` change is needed.

**Interfaces:**
- Consumes: `ReviewArmer` (`armFromDelight()`, `recordOrderCreated()`).

- [ ] **Step 1: Inject `ReviewArmer` into `OrderDetailViewModel`**

Add constructor param (after `analytics`):
```kotlin
    private val reviewArmer: com.danzucker.stitchpad.feature.review.presentation.ReviewArmer,
```

- [ ] **Step 2: Arm on payment success**

In `submitPayment`, in the `is Result.Success ->` branch, immediately after `_events.send(OrderDetailEvent.PaymentRecorded)`:
```kotlin
                    reviewArmer.armFromDelight()
```

- [ ] **Step 3: Arm on order delivered**

In `performStatusUpdate`, after `analytics.logEvent(AnalyticsEvent.OrderStatusAdvanced(...))`, add:
```kotlin
            if (newStatus == OrderStatus.DELIVERED) reviewArmer.armFromDelight()
```

- [ ] **Step 4: Count order creates in `OrderFormViewModel`**

Add the same constructor param, then in the create-success branch immediately after `analytics.logEvent(AnalyticsEvent.OrderCreated)`:
```kotlin
                        reviewArmer.recordOrderCreated()
```

- [ ] **Step 5: Fix ViewModel tests + verify**

If `OrderDetailViewModelTest` / `OrderFormViewModelTest` construct the VM directly, add a fake armer:
```kotlin
class NoopReviewArmer : com.danzucker.stitchpad.feature.review.presentation.ReviewArmer {
    override fun armFromDelight() {}
    override fun recordOrderCreated() {}
}
```
Pass `reviewArmer = NoopReviewArmer()` in those tests.
Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderDetailViewModelTest" --tests "*OrderFormViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order
git commit -m "feat(review): arm review prompt on payment + delivery, count order creates"
```

---

### Task 9: Settings "Rate StitchPad" row (manual deep-link path)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/helpsupport/SettingsHelpSupportScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SettingsModule.kt` (only if the settings VM is built with an explicit `viewModel { ... }` factory — add `storeReviewLauncher = get()`; a `viewModelOf(::SettingsViewModel)` needs no change)

**Interfaces:**
- Consumes: `StoreReviewLauncher.openStoreListing()`, `Analytics`, `SettingsRow`/`SettingsRowChevron`.
- Produces: `SettingsAction.OnRateAppClick`.

- [ ] **Step 1: Add the action + strings**

In `SettingsAction.kt` add:
```kotlin
    data object OnRateAppClick : SettingsAction
```
In `strings.xml`:
```xml
<string name="settings_row_rate_app">Rate StitchPad</string>
<string name="settings_row_rate_app_subtitle">Tell others what you think on the store</string>
```

- [ ] **Step 2: Handle it in `SettingsViewModel`**

Inject the launcher (add constructor param `private val storeReviewLauncher: StoreReviewLauncher,`). In `onAction`:
```kotlin
            SettingsAction.OnRateAppClick -> {
                analytics.logEvent(AnalyticsEvent.ReviewStoreListingOpened)
                storeReviewLauncher.openStoreListing()
            }
```
(If `SettingsViewModel` doesn't already inject `Analytics`, drop the analytics line or add the dependency following the module's pattern.)

- [ ] **Step 3: Add the row to Help & Support**

In `SettingsHelpSupportScreen.kt`, inside the `SettingsSectionCard` after the Contact row (with a `SettingsRowDivider()` before it), add a row using `Icons.Outlined.StarOutline` (or `Icons.Outlined.Star`):
```kotlin
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.StarOutline,
                    label = stringResource(Res.string.settings_row_rate_app),
                    subtitle = stringResource(Res.string.settings_row_rate_app_subtitle),
                    onClick = { onAction(SettingsAction.OnRateAppClick) },
                    trailing = { SettingsRowChevron() },
                )
```
Add the icon + string imports.

- [ ] **Step 4: Verify build + previews**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL; the Help & Support previews show the new "Rate StitchPad" row.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SettingsModule.kt
git commit -m "feat(review): Rate StitchPad row in Settings (store deep-link)"
```

---

### Task 10: Debug-menu "Force review prompt" (QA hook)

Mirror the existing `resetCelebrations` debug action end-to-end.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt` (pass `reviewController = get()` into `DebugSessionActions`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuScreen.kt`

**Interfaces:**
- Consumes: `ReviewController.forceArmForDebug()` (Task 5), `ReviewPreferencesStore.resetForDebug()` (Task 3).

- [ ] **Step 1: Add the debug actions**

In `DebugSessionActions.kt` add a `reviewController: ReviewController` constructor param and a `reviewPreferences: ReviewPreferencesStore` param, plus:
```kotlin
    fun forceReviewPrompt() {
        reviewController.forceArmForDebug()
    }

    suspend fun resetReviewSignals() {
        reviewPreferences.resetForDebug()
    }
```
In `DebugModule.kt` add `reviewController = get()` and `reviewPreferences = get()` to the `DebugSessionActions(...)` construction.

- [ ] **Step 2: Wire the menu entry**

Following the exact pattern of the existing `resetCelebrations` action (`DebugMenuAction.OnResetCelebrations` → `DebugMenuViewModel` handler → a `DebugSessionActions.resetCelebrations()` call → a `DebugMenuScreen` row): add `DebugMenuAction.OnForceReviewPrompt` (and `OnResetReviewSignals`), handle them in `DebugMenuViewModel` by calling `sessionActions.forceReviewPrompt()` (and `viewModelScope.launch { sessionActions.resetReviewSignals() }`), and add two rows to `DebugMenuScreen` labeled "Force review prompt" and "Reset review signals" (debug-only screen; hardcoded labels are acceptable there, matching the surrounding rows).

- [ ] **Step 3: Verify build + manual check**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Launch a debug build, open Settings → Debug, tap "Force review prompt", confirm the sentiment sheet appears over the current screen.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation
git commit -m "feat(review): debug-menu force-prompt + reset-signals hooks"
```

---

### Task 11: Full verification, detekt, and manual smoke test

- [ ] **Step 1: Run the whole test + lint suite**

Run: `./gradlew :composeApp:testDebugUnitTest detekt`
Expected: all tests PASS; detekt clean. (If `OrderDetailViewModel`'s constructor now exceeds the `LongParameterList` threshold, it already carries `@Suppress("LongParameterList")` — confirm it still covers the new param.)

- [ ] **Step 2: iOS compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke (Daniel = QA)** — record results in the PR description:
  - Debug → "Force review prompt": sheet appears; "Love it" shows the native review sheet (Android in-app review / iOS StoreKit); "Not really" opens Tally in the browser; scrim/back dismiss records nothing visible.
  - Settings → Help & Support → "Rate StitchPad": opens the store listing (Play / App Store write-review).
  - Real gate: a fresh account (no orders) never sees the prompt on the first payment; after ≥3 orders + qualifying tenure, recording a payment shows it once; a second payment same-session does not re-show (cooldown).
  - Rotate the device while the sheet is up (Android): the sheet survives config change and the selected action still fires.

- [ ] **Step 4: Final commit (if any smoke fixes)**

```bash
git add -A
git commit -m "test(review): smoke fixes + detekt cleanup"
```

---

## Self-Review (completed against the spec)

- **Two mechanisms** → Task 4 (`StoreReviewLauncher`: in-app API + deep link). ✔
- **Architecture mirrors CelebrationController** → Task 5. ✔
- **Gate (5 signals, per-outcome cooldowns)** → Task 1 + tests. ✔
- **Signal collection (install / open-days / orders), offline-safe** → Task 3 (prefs) + Task 5 (init stamps) + Task 8 (order-create counter). ✔
- **Flow (payment/delivery → sentiment → branch)** → Task 5 + Task 7 + Task 8. ✔
- **Settings entry (deep-link)** → Task 9. ✔
- **Analytics (5 events)** → Task 2, emitted in Tasks 5 + 9. ✔
- **Error handling (never throw; https fallback; snackbar)** → Task 4 (swallow+log+fallback); the feedback open uses `runCatching`. ✔
- **Testing (gate pure tests, controller tests, previews, platform manual)** → Tasks 1, 5, 7, 11. ✔
- **Rollout/QA (debug force-prompt)** → Task 10 + Task 11. ✔

**Naming consistency:** `ReviewArmer.armFromDelight()`/`recordOrderCreated()`, `ReviewController.onLoveIt/onNotReally/onDismiss/forceArmForDebug/ensureRunning`, `ReviewPreferencesStore.stampInstallIfAbsent/recordOpenDay/incrementOrdersCreated/recordPrompt/loadSignals/resetForDebug`, `StoreReviewLauncher.requestInAppReview/openStoreListing`, `ReviewGate.isEligible/cooldownMillisFor` — used identically across all tasks. ✔

**One deliberate refinement vs. spec:** the spec listed the guard "never arm while a celebration overlay is active" as an explicit runtime check; this plan instead relies on the `ordersCreated ≥ 3` gate, which structurally excludes the only three celebrations (all first-time: workshop-ready, first-customer, first-order). No cross-dependency between the two controllers is introduced (YAGNI). Documented in `ReviewController`'s KDoc.
