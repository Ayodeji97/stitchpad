# WhatsApp Community Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tailors join StitchPad's official WhatsApp community from a permanent Settings row and a dismissible, swipeable Dashboard banner, with the invite link remote-controlled via Firestore and a lightweight flag recording who taps Join.

**Architecture:** A new generic `core/config` layer reads a single `config/app` Firestore doc (snapshot-listener → `Flow<AppConfig>`) that both the Settings and Dashboard ViewModels observe; it is the seed of a future feature-flag system. The Dashboard banner is a clone of `WelcomeEndingBanner` hosted inside a new presentational `DashboardBannerPager` so the welcome and community banners can coexist as swipeable pages. Dismiss state is a device-wide local-preferences flag; join tracking is a fire-and-forget Firestore write.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, GitLive Firebase (`dev.gitlive.firebase`) Firestore + Auth, Koin DI, kotlinx.serialization, JUnit5 + Turbine + kotlinx-coroutines-test.

## Global Constraints

- All shared code in `composeApp/src/commonMain`; platform code in `androidMain`/`iosMain`. Run an iOS compile before declaring done (KMP-native gotchas).
- MVI: every screen has State/Action/Event sealed classes + ViewModel; Root (has ViewModel) / Screen (stateless, previewable) split.
- `Result<T, E>` for expected failures — never throw across layers.
- DTOs (`@Serializable`, nullable fields with defaults) separate from domain models; mappers as extension functions in the data layer. Read Firestore via a typed DTO (`snapshot.data<Dto>()`), never `data<Map<String, Any?>>()` (iOS Native crash).
- Koin: `singleOf(::Impl) bind Interface::class`; descriptive impl names (`FirebaseAppConfigRepository`, not `AppConfigRepositoryImpl`).
- All user-facing strings in `composeApp/src/commonMain/composeResources/values/strings.xml`; apostrophes as `&apos;`, ampersands as `&amp;` — never backslash escapes.
- Every Screen/banner composable has a `@Preview`; new visible surfaces define light AND dark treatment.
- Firestore writes that the UI doesn't need to await are fire-and-forget (GitLive `set()`/`update()` suspend until server ACK and hang offline).
- All state lives in the ViewModel except Compose-internal state (`LazyListState`, `PagerState`), which may use `remember`.
- Design tokens: Primary Indigo `#2C3E7C` (theme `primary`/`primaryContainer`). Saffron is never used here.

---

### Task 1: `core/config` domain model, DTO, and mapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/domain/model/AppConfig.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/domain/repository/AppConfigRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/dto/AppConfigDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/mapper/AppConfigMapper.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/data/mapper/AppConfigMapperTest.kt`

**Interfaces:**
- Produces: `AppConfig(communityEnabled: Boolean, communityInviteUrl: String?)` with `AppConfig.Disabled` default; `AppConfigRepository.config: Flow<AppConfig>`; `AppConfigDto(communityEnabled: Boolean = false, communityInviteUrl: String? = null)`; `fun AppConfigDto.toAppConfig(): AppConfig`.

- [ ] **Step 1: Write the failing mapper test**

`AppConfigMapperTest.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.data.mapper

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigMapperTest {

    @Test
    fun fullDto_mapsAllFields() {
        val dto = AppConfigDto(
            communityEnabled = true,
            communityInviteUrl = "https://chat.whatsapp.com/ABC123",
        )

        val config = dto.toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("https://chat.whatsapp.com/ABC123", config.communityInviteUrl)
    }

    @Test
    fun emptyDto_mapsToDisabledDefaults() {
        val config = AppConfigDto().toAppConfig()

        assertFalse(config.communityEnabled)
        assertNull(config.communityInviteUrl)
    }

    @Test
    fun enabledWithBlankUrl_keepsBlankUrlForCallerToGuard() {
        val config = AppConfigDto(communityEnabled = true, communityInviteUrl = "").toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("", config.communityInviteUrl)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.config.data.mapper.AppConfigMapperTest"`
Expected: FAIL — unresolved references `AppConfigDto` / `toAppConfig`.

- [ ] **Step 3: Create the domain model**

`AppConfig.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.domain.model

/**
 * Remote, console-controllable app configuration read from the `config/app`
 * Firestore document. Intentionally generic — this is the seed of the app's
 * feature-flag layer; the community fields are simply its first occupants.
 */
data class AppConfig(
    val communityEnabled: Boolean,
    val communityInviteUrl: String?,
) {
    companion object {
        /** Safe fallback used before config loads or on read failure: feature hidden. */
        val Disabled = AppConfig(communityEnabled = false, communityInviteUrl = null)
    }
}
```

- [ ] **Step 4: Create the repository interface**

`AppConfigRepository.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.domain.repository

import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

interface AppConfigRepository {
    /**
     * Hot stream of remote app config, backed by a Firestore snapshot listener.
     * Emits [AppConfig.Disabled] before first load and on any read error, so
     * consumers never see a broken/partial config.
     */
    val config: Flow<AppConfig>
}
```

- [ ] **Step 5: Create the DTO**

`AppConfigDto.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val communityEnabled: Boolean = false,
    val communityInviteUrl: String? = null,
)
```

- [ ] **Step 6: Create the mapper**

`AppConfigMapper.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.data.mapper

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.domain.model.AppConfig

fun AppConfigDto.toAppConfig(): AppConfig = AppConfig(
    communityEnabled = communityEnabled,
    communityInviteUrl = communityInviteUrl,
)
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.config.data.mapper.AppConfigMapperTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config
git commit -m "feat(config): AppConfig domain model, DTO, and mapper"
```

---

### Task 2: `FirebaseAppConfigRepository`, a fake, Koin module, and Firestore rule

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/FirebaseAppConfigRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/FakeAppConfigRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ConfigModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt` (add `configDataModule` to the `modules(...)` list)
- Modify: `firestore.rules` (add a top-level `config/{doc}` read-only match)

**Interfaces:**
- Consumes: `AppConfigRepository`, `AppConfigDto`, `toAppConfig()` from Task 1; GitLive `FirebaseFirestore`.
- Produces: `FirebaseAppConfigRepository(firestore)`; `val configDataModule`; `FakeAppConfigRepository` (test helper exposing `fun emit(config: AppConfig)`).

- [ ] **Step 1: Create the Firestore repository**

`FirebaseAppConfigRepository.kt` (mirrors `FirebaseWeeklyGoalRepository`'s `.snapshots` + `.map` + `.catch` pattern; reads the single top-level doc `config/app`):
```kotlin
package com.danzucker.stitchpad.core.config.data

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.data.mapper.toAppConfig
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val TAG = "AppConfigRepo"
private const val CONFIG_COLLECTION = "config"
private const val CONFIG_DOC_ID = "app"

class FirebaseAppConfigRepository(
    private val firestore: FirebaseFirestore,
) : AppConfigRepository {

    override val config: Flow<AppConfig> =
        firestore.collection(CONFIG_COLLECTION)
            .document(CONFIG_DOC_ID)
            .snapshots
            .map { snapshot ->
                if (snapshot.exists) {
                    runCatching { snapshot.data<AppConfigDto>().toAppConfig() }
                        .getOrElse { AppConfig.Disabled }
                } else {
                    AppConfig.Disabled
                }
            }
            .onStart { emit(AppConfig.Disabled) }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observe app config failed" }
                emit(AppConfig.Disabled)
            }
}
```

- [ ] **Step 2: Create the fake for downstream ViewModel tests**

`FakeAppConfigRepository.kt` (in `commonTest`):
```kotlin
package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppConfigRepository(
    initial: AppConfig = AppConfig.Disabled,
) : AppConfigRepository {
    private val _config = MutableStateFlow(initial)
    override val config: Flow<AppConfig> = _config

    fun emit(config: AppConfig) {
        _config.value = config
    }
}
```

- [ ] **Step 3: Create the Koin module**

`ConfigModule.kt`:
```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.config.data.FirebaseAppConfigRepository
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val configDataModule = module {
    singleOf(::FirebaseAppConfigRepository) bind AppConfigRepository::class
}
```

- [ ] **Step 4: Register the module in `StitchPadApp.kt`**

In the `modules(...)` call, add `configDataModule` next to the other data modules (e.g. immediately after `coreModule`):
```kotlin
        modules(
            coreModule,
            configDataModule,
            authDataModule,
            // ... existing modules unchanged
        )
```
Add the import: `import com.danzucker.stitchpad.di.configDataModule` (same package, so no import needed if `StitchPadApp.kt` is in `com.danzucker.stitchpad`; verify and add only if the build complains).

- [ ] **Step 5: Add the Firestore security rule**

In `firestore.rules`, add a top-level match as a sibling of `/users/{uid}` (NOT inside it — `config/app` is a global doc), before the closing brace of `match /databases/{database}/documents`:
```
    // ── Global remote app config (community link, future feature flags) ──
    // Read-only for any signed-in user; only console/admin writes.
    match /config/{configDoc} {
      allow read: if request.auth != null;
      allow write: if false;
    }
```

- [ ] **Step 6: Build to verify wiring compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/FirebaseAppConfigRepository.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/FakeAppConfigRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ConfigModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt firestore.rules
git commit -m "feat(config): Firestore app-config repository, Koin module, and read-only rule"
```

---

### Task 3: Local dismiss flag in `OnboardingPreferencesStore`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferencesStore.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.ios.kt`

**Interfaces:**
- Produces: `suspend fun hasDismissedCommunityBanner(): Boolean`, `suspend fun setCommunityBannerDismissed()`, `suspend fun clearCommunityBannerDismissed()` on `OnboardingPreferencesStore` (+ both platform impls).

- [ ] **Step 1: Add methods to the interface**

In `OnboardingPreferencesStore.kt`, add after `setAskedPushPermission()`:
```kotlin
    /**
     * Whether the user has dismissed (via ✕) or already acted on (tapped Join)
     * the Dashboard community banner. Device-wide, like the push-permission
     * flag — once set, the banner never shows again. The Settings row is
     * unaffected and remains the permanent entry point.
     */
    suspend fun hasDismissedCommunityBanner(): Boolean
    suspend fun setCommunityBannerDismissed()

    /** Debug-menu only: re-show the community banner by clearing the dismiss flag. */
    suspend fun clearCommunityBannerDismissed()
```

- [ ] **Step 2: Implement on Android**

In `OnboardingPreferences.android.kt`, add a key constant and three methods:
```kotlin
    override suspend fun hasDismissedCommunityBanner(): Boolean {
        return prefs.getBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false)
    }

    override suspend fun setCommunityBannerDismissed() {
        prefs.edit().putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, true).apply()
    }

    override suspend fun clearCommunityBannerDismissed() {
        prefs.edit().putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false).apply()
    }
```
Add to the `companion object`:
```kotlin
        private const val KEY_DISMISSED_COMMUNITY_BANNER = "dismissed_community_banner"
```
Add `.putBoolean(KEY_DISMISSED_COMMUNITY_BANNER, false)` to the `resetForDebug()` editor chain.

- [ ] **Step 3: Implement on iOS**

In `OnboardingPreferences.ios.kt`, add:
```kotlin
    override suspend fun hasDismissedCommunityBanner(): Boolean {
        return defaults.boolForKey(KEY_DISMISSED_COMMUNITY_BANNER)
    }

    override suspend fun setCommunityBannerDismissed() {
        defaults.setBool(true, forKey = KEY_DISMISSED_COMMUNITY_BANNER)
    }

    override suspend fun clearCommunityBannerDismissed() {
        defaults.setBool(false, forKey = KEY_DISMISSED_COMMUNITY_BANNER)
    }
```
Add to the `companion object`:
```kotlin
        private const val KEY_DISMISSED_COMMUNITY_BANNER = "dismissed_community_banner"
```
Add `defaults.setBool(false, forKey = KEY_DISMISSED_COMMUNITY_BANNER)` to `resetForDebug()`.

- [ ] **Step 4: Build both platforms**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.
(iOS compile is verified in the final task; the `actual` signatures must match the new `expect`/interface members or the iOS link fails.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferencesStore.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.ios.kt
git commit -m "feat(community): add device-wide community-banner dismiss flag to prefs"
```

---

### Task 4: `CommunityJoinTracker` (fire-and-forget join metric)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/domain/CommunityJoinTracker.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/FirebaseCommunityJoinTracker.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/FakeCommunityJoinTracker.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ConfigModule.kt`

**Interfaces:**
- Consumes: GitLive `FirebaseAuth`, `FirebaseFirestore`.
- Produces: `interface CommunityJoinTracker { suspend fun trackJoinTapped() }`; `FirebaseCommunityJoinTracker(auth, firestore, nowMillis)`; `FakeCommunityJoinTracker` with `var tapCount: Int`.

- [ ] **Step 1: Create the interface**

`CommunityJoinTracker.kt`:
```kotlin
package com.danzucker.stitchpad.core.config.domain

/**
 * Records that the signed-in user tapped "Join community". Implementations
 * MUST be safe to fire-and-forget — callers launch this without awaiting and
 * never block UI on it (the underlying Firestore write suspends until ACK).
 */
interface CommunityJoinTracker {
    suspend fun trackJoinTapped()
}
```

- [ ] **Step 2: Create the Firebase implementation**

`FirebaseCommunityJoinTracker.kt` (writes `users/{uid}.communityJoinTappedAt`; swallows failures — it is only a metric):
```kotlin
package com.danzucker.stitchpad.core.config.data

import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "CommunityJoinTracker"

@OptIn(ExperimentalTime::class)
class FirebaseCommunityJoinTracker(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CommunityJoinTracker {

    override suspend fun trackJoinTapped() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid).update(
                "communityJoinTappedAt" to nowMillis(),
                "communityJoinTapCount" to FieldValue.increment(1),
            )
        }.onFailure { throwable ->
            AppLogger.e(tag = TAG, throwable = throwable) { "trackJoinTapped failed" }
        }
    }
}
```
> Note: verify the GitLive `update(vararg Pair)` overload and `FieldValue.increment` import path against the version in `gradle/libs.versions.toml`. If `FieldValue.increment` is unavailable, drop the count and write only `update("communityJoinTappedAt" to nowMillis())`. If `Clock` from `kotlin.time` is unresolved, fall back to `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` (whichever the rest of the data layer uses).

- [ ] **Step 3: Create the fake**

`FakeCommunityJoinTracker.kt`:
```kotlin
package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker

class FakeCommunityJoinTracker : CommunityJoinTracker {
    var tapCount: Int = 0
        private set

    override suspend fun trackJoinTapped() {
        tapCount++
    }
}
```

- [ ] **Step 4: Register in Koin**

In `ConfigModule.kt`, add inside the `configDataModule`:
```kotlin
    singleOf(::FirebaseCommunityJoinTracker) bind CommunityJoinTracker::class
```
Add imports for `FirebaseCommunityJoinTracker` and `CommunityJoinTracker`.
> If `FirebaseCommunityJoinTracker` has the `nowMillis` default-lambda param, `singleOf` constructor-ref reflection still resolves `auth`/`firestore` via `get()` and uses the default for `nowMillis` only if it is the LAST param with a default — confirmed it is. If Koin complains about the lambda param, switch to the lambda form: `single { FirebaseCommunityJoinTracker(get(), get()) } bind CommunityJoinTracker::class`.

- [ ] **Step 5: Build to verify**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/domain/CommunityJoinTracker.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/data/FirebaseCommunityJoinTracker.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/FakeCommunityJoinTracker.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ConfigModule.kt
git commit -m "feat(community): fire-and-forget CommunityJoinTracker writing communityJoinTappedAt"
```

---

### Task 5: String resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Produces: resource ids `community_banner_title`, `community_banner_body`, `community_banner_cta`, `community_banner_dismiss_cd`, `community_banner_icon_cd`, `settings_row_community`, `settings_row_community_subtitle`.

- [ ] **Step 1: Add the strings**

Add to `strings.xml` (note `&apos;` and `&amp;`):
```xml
    <string name="community_banner_title">Join our WhatsApp community</string>
    <string name="community_banner_body">Get product updates, tips &amp; early news. Connect with other Nigerian tailors on WhatsApp.</string>
    <string name="community_banner_cta">Join community</string>
    <string name="community_banner_dismiss_cd">Dismiss</string>
    <string name="community_banner_icon_cd">Community</string>
    <string name="settings_row_community">Join our community</string>
    <string name="settings_row_community_subtitle">Updates, tips &amp; other tailors on WhatsApp</string>
```

- [ ] **Step 2: Generate resource accessors**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL (new `Res.string.community_*` accessors generated).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(community): banner + settings-row string resources"
```

---

### Task 6: `CommunityBanner` composable + `DashboardBannerPager`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityBanner.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardBannerPager.kt`

**Interfaces:**
- Consumes: `DesignTokens`, `StitchPadTheme`, community strings from Task 5.
- Produces: `@Composable fun CommunityBanner(onJoin: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`; `@Composable fun DashboardBannerPager(banners: List<@Composable () -> Unit>, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Create `CommunityBanner` (Variant A · Brand Indigo)**

`CommunityBanner.kt` — clones `WelcomeEndingBanner`'s Surface/typography, uses `primaryContainer`/`onPrimaryContainer` for the indigo treatment, an icon tile, a dismiss ✕ (top-right), and a filled Join button:
```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.community_banner_body
import stitchpad.composeapp.generated.resources.community_banner_cta
import stitchpad.composeapp.generated.resources.community_banner_dismiss_cd
import stitchpad.composeapp.generated.resources.community_banner_icon_cd
import stitchpad.composeapp.generated.resources.community_banner_title

@Composable
fun CommunityBanner(
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
    ) {
        Box {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.community_banner_dismiss_cd),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(DesignTokens.space4)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusSm))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = rememberVectorPainter(Icons.Outlined.Groups),
                        contentDescription = stringResource(Res.string.community_banner_icon_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(DesignTokens.space3))
                Text(
                    text = stringResource(Res.string.community_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DesignTokens.space1))
                Text(
                    text = stringResource(Res.string.community_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DesignTokens.space3))
                Button(onClick = onJoin) {
                    Text(stringResource(Res.string.community_banner_cta))
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityBannerPreviewLight() {
    StitchPadTheme(darkTheme = false) {
        CommunityBanner(onJoin = {}, onDismiss = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityBannerPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        CommunityBanner(onJoin = {}, onDismiss = {})
    }
}
```
> Verify `Icons.Outlined.Groups` exists in the bundled material-icons set; if not, use `Icons.Outlined.Diversity3` or `Icons.AutoMirrored.Outlined.Chat`. Verify `DesignTokens.space1`/`radiusSm` names against `DesignTokens.kt`; adjust to the nearest existing token. Verify `StitchPadTheme`'s dark-mode parameter name (`darkTheme`) against its actual signature.

- [ ] **Step 2: Create `DashboardBannerPager`**

`DashboardBannerPager.kt` — 0 → nothing, 1 → bare banner, 2+ → `HorizontalPager` + dot indicators:
```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

@Composable
fun DashboardBannerPager(
    banners: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    when (banners.size) {
        0 -> Unit
        1 -> Row(modifier = modifier.fillMaxWidth()) { banners[0]() }
        else -> {
            val pagerState = rememberPagerState(pageCount = { banners.size })
            androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = DesignTokens.space3,
                ) { page ->
                    banners[page]()
                }
                Spacer(Modifier.height(DesignTokens.space2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(banners.size) { index ->
                        val selected = pagerState.currentPage == index
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                        )
                    }
                }
            }
        }
    }
}
```
> `PagerState` via `rememberPagerState` is Compose-internal state, allowed in `remember` per the project rules. No ViewModel sync, so the `settledPage` notify gotcha does not apply.

- [ ] **Step 3: Build to verify the composables compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/CommunityBanner.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardBannerPager.kt
git commit -m "feat(community): CommunityBanner (indigo) + swipeable DashboardBannerPager"
```

---

### Task 7: Settings row — state, action, event, ViewModel, screen, Root

**Files:**
- Modify: `.../feature/settings/presentation/home/SettingsState.kt`
- Modify: `.../feature/settings/presentation/home/SettingsAction.kt`
- Modify: `.../feature/settings/presentation/home/SettingsEvent.kt`
- Modify: `.../feature/settings/presentation/home/SettingsViewModel.kt`
- Modify: `.../feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `.../feature/settings/presentation/home/SettingsRoot.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsCommunityTest.kt`

**Interfaces:**
- Consumes: `AppConfigRepository`, `CommunityJoinTracker`, `FakeAppConfigRepository`, `FakeCommunityJoinTracker`.
- Produces: `SettingsState.communityEnabled`, `SettingsState.communityUrl`, `SettingsState.showCommunityRow`; `SettingsAction.OnCommunityClick`; `SettingsEvent.OpenCommunityLink(url)`.

- [ ] **Step 1: Add state fields**

In `SettingsState.kt`, add to the data class:
```kotlin
    val communityEnabled: Boolean = false,
    val communityUrl: String? = null,
```
And add a derived flag in the body:
```kotlin
    /** Settings community row shows only when remotely enabled with a usable link. */
    val showCommunityRow: Boolean
        get() = communityEnabled && !communityUrl.isNullOrBlank()
```

- [ ] **Step 2: Add the action**

In `SettingsAction.kt`, add:
```kotlin
    data object OnCommunityClick : SettingsAction
```

- [ ] **Step 3: Add the event**

In `SettingsEvent.kt`, add:
```kotlin
    /** Open the WhatsApp community invite (a chat.whatsapp.com link) directly. */
    data class OpenCommunityLink(val url: String) : SettingsEvent
```

- [ ] **Step 4: Write the failing ViewModel test**

`SettingsCommunityTest.kt` — mirror the existing Settings/Dashboard test harness (Turbine + `UnconfinedTestDispatcher`, fakes for all constructor deps). Construct the VM with `FakeAppConfigRepository` + `FakeCommunityJoinTracker`. Tests:
```kotlin
    @Test
    fun communityEnabledWithUrl_showsRow() = runTest {
        appConfigRepository.emit(
            AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"),
        )
        val vm = createViewModel()
        vm.state.test {
            val state = awaitItem() // advance to the hydrated state
            assertTrue(state.showCommunityRow)
            assertEquals("https://chat.whatsapp.com/X", state.communityUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCommunityClick_emitsOpenLinkAndTracks() = runTest {
        appConfigRepository.emit(
            AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"),
        )
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(SettingsAction.OnCommunityClick)
            assertEquals(SettingsEvent.OpenCommunityLink("https://chat.whatsapp.com/X"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, communityJoinTracker.tapCount)
    }
```
> Match the existing `SettingsViewModelTest` setup for the other constructor fakes; if no such test file exists, follow `DashboardViewModelTest.kt`'s fake/`createViewModel` pattern. Add the two new fakes to the VM construction.

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsCommunityTest"`
Expected: FAIL — VM doesn't accept the new deps / no `OnCommunityClick` handling.

- [ ] **Step 6: Wire the ViewModel**

In `SettingsViewModel.kt`:
1. Add constructor params `private val appConfigRepository: AppConfigRepository,` and `private val communityJoinTracker: CommunityJoinTracker,`.
2. Feed config into state: add `appConfigRepository.config` as an additional source in the existing state-assembly `combine` (the VM builds state via `combine(...).collect { emit(it) }`). Add a parameter to `buildState(... , appConfig: AppConfig)` and map:
   ```kotlin
   communityEnabled = appConfig.communityEnabled,
   communityUrl = appConfig.communityInviteUrl,
   ```
   `combine` takes at most 5 flows positionally; the VM already nests (`userWithUsageFlow` pre-combines two). If adding a 6th source, pre-combine `appConfigRepository.config` with an existing low-frequency source (e.g. `customerCountFlow`) into a `Pair`, exactly as `userWithUsageFlow` does, then unpack in the lambda. Keep the existing sources unchanged.
3. Handle the action in `onAction`:
   ```kotlin
   SettingsAction.OnCommunityClick -> {
       val url = state.value.communityUrl ?: return
       emit(SettingsEvent.OpenCommunityLink(url))
       viewModelScope.launch { communityJoinTracker.trackJoinTapped() }
   }
   ```
   (Use the VM's existing state accessor; if it exposes `uiState`/`state`, read the current `communityUrl` from there. `emit` is the VM's existing event helper used by `OnContactClick`.)

- [ ] **Step 7: Add the Koin constructor args**

The Settings ViewModel is registered with `viewModelOf(::SettingsViewModel)` or a `viewModel { }` factory in `settingsPresentationModule`. Because `SettingsViewModel` now has more constructor params resolved via `get()`, `viewModelOf` continues to work (all new params are interface singletons). If the VM uses a lambda factory, add `get()`/`get()` for the two new deps. Verify the module compiles.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsCommunityTest"`
Expected: PASS (2 tests).

- [ ] **Step 9: Add the Settings row**

In `SettingsScreen.kt`, inside the Support `SettingsSectionCard`, add a row (gated on `state.showCommunityRow`) after the Contact row + a divider:
```kotlin
                if (state.showCommunityRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Groups,
                        label = stringResource(Res.string.settings_row_community),
                        subtitle = stringResource(Res.string.settings_row_community_subtitle),
                        onClick = { onAction(SettingsAction.OnCommunityClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
```
Add imports for `Icons.Outlined.Groups` and the two new string resources. (Reuse the same icon fallback chosen in Task 6 Step 1 if `Groups` is unavailable.)

- [ ] **Step 10: Handle the event in `SettingsRoot.kt`**

In the `ObserveAsEvents` `when`, add:
```kotlin
            is SettingsEvent.OpenCommunityLink -> uriHandler.openUri(event.url)
```
(Direct `chat.whatsapp.com` link — no `buildWhatsAppUrl`, no message resolution.)

- [ ] **Step 11: Build + run the Settings tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid && ./gradlew :composeApp:testDebugUnitTest --tests "*Settings*"`
Expected: BUILD SUCCESSFUL; all Settings tests PASS.

- [ ] **Step 12: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings
git commit -m "feat(community): settings row to join the WhatsApp community"
```

---

### Task 8: Dashboard banner — state, action, event, ViewModel, carousel wiring, Root

**Files:**
- Modify: `.../feature/dashboard/presentation/DashboardState.kt`
- Modify: `.../feature/dashboard/presentation/DashboardAction.kt`
- Modify: `.../feature/dashboard/presentation/DashboardEvent.kt`
- Modify: `.../feature/dashboard/presentation/DashboardViewModel.kt`
- Modify: `.../feature/dashboard/presentation/DashboardScreen.kt` (banner render block + the event handler / `DashboardRoot`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardCommunityBannerTest.kt`

**Interfaces:**
- Consumes: `AppConfigRepository`, `CommunityJoinTracker`, `OnboardingPreferencesStore`, `FakeAppConfigRepository`, `FakeCommunityJoinTracker`; `DashboardBannerPager`, `CommunityBanner`, `WelcomeEndingBanner`.
- Produces: `DashboardState.showCommunityBanner`, `DashboardState.communityUrl`; `DashboardAction.OnJoinCommunity`, `DashboardAction.OnDismissCommunityBanner`; `DashboardEvent.OpenCommunityLink(url)`.

- [ ] **Step 1: Add state fields**

In `DashboardState.kt`, next to the welcome-banner fields:
```kotlin
    // Community banner — shown when remote config enables it with a usable link
    // and the user hasn't dismissed/joined. communityUrl is null when hidden.
    val communityUrl: String? = null,
    val showCommunityBanner: Boolean = false,
```

- [ ] **Step 2: Add actions**

In `DashboardAction.kt`:
```kotlin
    /** Community banner Join tapped → open the invite + record + dismiss. */
    data object OnJoinCommunity : DashboardAction

    /** Community banner ✕ tapped → hide it for good (local flag). */
    data object OnDismissCommunityBanner : DashboardAction
```

- [ ] **Step 3: Add the event**

In `DashboardEvent.kt`:
```kotlin
    /** Open the WhatsApp community invite (chat.whatsapp.com) directly. */
    data class OpenCommunityLink(val url: String) : DashboardEvent
```

- [ ] **Step 4: Write the failing ViewModel test**

`DashboardCommunityBannerTest.kt` (mirror `DashboardViewModelTest.kt`'s harness — add `FakeAppConfigRepository`, `FakeCommunityJoinTracker`, and a fake/real `OnboardingPreferencesStore` to `createViewModel`):
```kotlin
    @Test
    fun configEnabledAndNotDismissed_showsCommunityBanner() = runTest {
        prefs.clearCommunityBannerDismissed()
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        assertTrue(vm.state.value.showCommunityBanner)
        assertEquals("https://chat.whatsapp.com/X", vm.state.value.communityUrl)
    }

    @Test
    fun dismissed_hidesCommunityBanner() = runTest {
        prefs.setCommunityBannerDismissed()
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        assertFalse(vm.state.value.showCommunityBanner)
    }

    @Test
    fun onDismiss_setsFlagAndHides() = runTest {
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnDismissCommunityBanner)
        assertFalse(vm.state.value.showCommunityBanner)
        assertTrue(prefs.hasDismissedCommunityBanner())
    }

    @Test
    fun onJoin_emitsOpenLinkTracksAndDismisses() = runTest {
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(DashboardAction.OnJoinCommunity)
            assertEquals(DashboardEvent.OpenCommunityLink("https://chat.whatsapp.com/X"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, communityJoinTracker.tapCount)
        assertTrue(prefs.hasDismissedCommunityBanner())
        assertFalse(vm.state.value.showCommunityBanner)
    }
```
> Use a simple fake `OnboardingPreferencesStore` in `commonTest` holding the dismiss flag in memory (implement the full interface, no-op the unrelated members). If one already exists for dashboard push-permission tests, reuse it and add the community-flag behavior.

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DashboardCommunityBannerTest"`
Expected: FAIL.

- [ ] **Step 6: Wire the ViewModel**

In `DashboardViewModel.kt`:
1. Add constructor params: `private val appConfigRepository: AppConfigRepository,`, `private val communityJoinTracker: CommunityJoinTracker,`, `private val onboardingPrefs: OnboardingPreferencesStore,`.
2. Add `private var communityBannerDismissed = false` and load it once in `init`/an observer:
   ```kotlin
   private fun observeCommunity() {
       viewModelScope.launch {
           communityBannerDismissed = onboardingPrefs.hasDismissedCommunityBanner()
           appConfigRepository.config.collect { cfg ->
               _state.update {
                   it.copy(
                       communityUrl = cfg.communityInviteUrl,
                       showCommunityBanner = cfg.communityEnabled &&
                           !cfg.communityInviteUrl.isNullOrBlank() &&
                           !communityBannerDismissed,
                   )
               }
           }
       }
   }
   ```
   Call `observeCommunity()` wherever `observeEntitlements()` is called.
3. Handle actions in `onAction`:
   ```kotlin
   DashboardAction.OnJoinCommunity -> {
       val url = state.value.communityUrl
       if (url != null) emitEvent(DashboardEvent.OpenCommunityLink(url))
       viewModelScope.launch { communityJoinTracker.trackJoinTapped() }
       dismissCommunityBanner()
   }
   DashboardAction.OnDismissCommunityBanner -> dismissCommunityBanner()
   ```
   ```kotlin
   private fun dismissCommunityBanner() {
       communityBannerDismissed = true
       _state.update { it.copy(showCommunityBanner = false) }
       viewModelScope.launch { onboardingPrefs.setCommunityBannerDismissed() }
   }
   ```
   (`emitEvent` and `_state.update` are the VM's existing helpers.)

- [ ] **Step 7: Add Koin constructor args**

`DashboardViewModel` is registered via `viewModelOf(::DashboardViewModel)` (or a `viewModel { }` factory with explicit args for `nowMillis`/`timeZone`). Add `get()` for the three new interface deps in the factory; if `viewModelOf` is used, the new interface params resolve automatically. Verify `dashboardPresentationModule` compiles.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DashboardCommunityBannerTest"`
Expected: PASS (4 tests).

- [ ] **Step 9: Render via the carousel**

In `DashboardScreen.kt`, replace the existing `if (state.showWelcomeBanner ...)` block with a carousel that composes the live banners:
```kotlin
        val banners = buildList<@Composable () -> Unit> {
            if (state.showWelcomeBanner && state.welcomeBannerDaysLeft != null) {
                add {
                    WelcomeEndingBanner(
                        daysLeft = state.welcomeBannerDaysLeft,
                        onSeeUpgrade = { onAction(DashboardAction.OpenUpgrade) },
                    )
                }
            }
            if (state.showCommunityBanner) {
                add {
                    CommunityBanner(
                        onJoin = { onAction(DashboardAction.OnJoinCommunity) },
                        onDismiss = { onAction(DashboardAction.OnDismissCommunityBanner) },
                    )
                }
            }
        }
        DashboardBannerPager(banners = banners)
```
Add imports for `DashboardBannerPager`, `CommunityBanner` (and keep `WelcomeEndingBanner`).

- [ ] **Step 10: Handle the event in `DashboardRoot` / `handleDashboardEvent`**

In the dashboard event handler, add a `LocalUriHandler`-backed open. `DashboardRoot` currently routes events through `handleDashboardEvent(...)`; add a `uriHandler: UriHandler` param (`val uriHandler = LocalUriHandler.current` in `DashboardRoot`, passed through) and a branch:
```kotlin
            is DashboardEvent.OpenCommunityLink -> uriHandler.openUri(event.url)
```
> If wiring the param through `handleDashboardEvent` is awkward, handle `OpenCommunityLink` inline in the `ObserveAsEvents` block in `DashboardRoot` (where `LocalUriHandler.current` is directly in scope) before delegating the rest to `handleDashboardEvent`.

- [ ] **Step 11: Build + dashboard tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid && ./gradlew :composeApp:testDebugUnitTest --tests "*Dashboard*"`
Expected: BUILD SUCCESSFUL; all Dashboard tests PASS.

- [ ] **Step 12: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard
git commit -m "feat(community): dashboard community banner in swipeable carousel"
```

---

### Task 9: Debug-menu "Reset community banner" entry

**Files:**
- Modify: `.../feature/debug/presentation/DebugMenuAction.kt`
- Modify: `.../feature/debug/presentation/DebugMenuViewModel.kt`
- Modify: `.../feature/debug/presentation/DebugMenuScreen.kt`

**Interfaces:**
- Consumes: `OnboardingPreferencesStore.clearCommunityBannerDismissed()`.
- Produces: `DebugMenuAction.OnResetCommunityBannerClick`.

- [ ] **Step 1: Add the action**

In `DebugMenuAction.kt`:
```kotlin
    data object OnResetCommunityBannerClick : DebugMenuAction
```

- [ ] **Step 2: Handle it in the ViewModel**

In `DebugMenuViewModel.kt`'s `onAction`, mirroring an existing reset entry (the VM already injects `OnboardingPreferencesStore` for `resetForDebug`; if not, add it):
```kotlin
            DebugMenuAction.OnResetCommunityBannerClick -> runJob {
                onboardingPrefs.clearCommunityBannerDismissed()
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Community banner reset")))
            }
```
> Match the VM's actual job/emit helpers (`runJob`/`emit`) and snackbar event shape; copy the structure of an adjacent reset handler verbatim.

- [ ] **Step 3: Add the row**

In `DebugMenuScreen.kt`, in the Session (or reset) `SettingsSectionCard`, add:
```kotlin
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    label = "Reset community banner",
                    onClick = { onAction(DebugMenuAction.OnResetCommunityBannerClick) },
                )
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug
git commit -m "chore(debug): reset community banner debug-menu entry"
```

---

### Task 10: Full verification (Android, iOS, detekt) + manual smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. (If a screen file trips `TooManyFunctions` from added previews, add `@file:Suppress("TooManyFunctions")` per project convention — do not split the file.)

- [ ] **Step 3: iOS compile (REQUIRED — KMP-native gate)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (Catches `actual` signature drift in `OnboardingPreferences.ios.kt`, the `Clock`/`FieldValue` choices, and any JVM-only API in the tracker.)

- [ ] **Step 4: Android assemble**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke test (Daniel — QA)**

1. In the Firestore console, create doc `config/app` = `{ communityEnabled: true, communityInviteUrl: "<real chat.whatsapp.com invite>" }`.
2. Launch the app → Dashboard shows the indigo "Join our WhatsApp community" banner at the top.
3. Trigger the welcome-ending banner too (debug seed / Pro-window state) → both banners appear in a swipeable pager with dot indicators; swipe between them.
4. Tap ✕ on the community banner → it disappears (pager collapses to just the welcome banner). Kill + relaunch → still gone.
5. Debug menu → "Reset community banner" → relaunch dashboard → banner returns.
6. Tap **Join community** → WhatsApp opens to the invite; banner gone afterwards; `users/{uid}.communityJoinTappedAt` (and `communityJoinTapCount`) is set in Firestore.
7. Settings → Support → "Join our community" row present → tap → WhatsApp opens to the invite.
8. Set `communityEnabled: false` → relaunch → banner AND Settings row both hidden.
9. Repeat steps 2, 6, 7 on **iOS** (clean Xcode build; real device for the WhatsApp open).

- [ ] **Step 6: Final review + push**

Run `codex review` and let Cursor Bugbot review the PR (both required per project workflow). Address findings, then push the branch and open the PR with the smoke-test steps in the description.

---

## Notes for the implementer

- **`config/app` is a global doc**, not under `users/{uid}` — the security rule is a top-level `match /config/{configDoc}` and the repository reads `firestore.collection("config").document("app")`.
- **Verify-before-trust on a few API spots** flagged inline: GitLive `update(vararg Pair)` + `FieldValue.increment`, the `Clock` import (`kotlin.time` vs `kotlinx.datetime`), the material-icon name (`Icons.Outlined.Groups`), `DesignTokens` token names, and `StitchPadTheme`'s dark-mode parameter. Each has a stated fallback.
- **Tracking is best-effort**: never let it block the link-open or the UI; failures are logged and swallowed.
- **iOS compile is a required gate**, not optional — run it before claiming done.
