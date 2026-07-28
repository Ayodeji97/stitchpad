# Settings IA Hub (Frequency Hybrid) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the Settings landing into a short, scannable hub — preferences inline, low-frequency management collapsed into four drill-down category rows — behind a remote feature flag.

**Architecture:** The landing (`SettingsScreen`) branches on a new `AppConfig.settingsHubEnabled` flag: flag-off renders today's flat layout unchanged; flag-on renders the new hub (Preferences inline + a "Manage" section of four rows). Each Manage row opens a sub-screen (`Account & security`, `Invite & rewards`, `Help & support`, `Legal & about`), each a stateless Screen + thin Root that reuses the existing `SettingsViewModel` (a fresh instance per screen — the VM's `events` Channel is single-consumer, so sharing one instance across screens would race). Event-handling is extracted once into `SettingsEventEffect` and reused by every Root.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin (`viewModelOf`), androidx.navigation type-safe routes, kotlinx.coroutines Flow, compose.resources strings, JUnit + Turbine + kotlin.test.

## Global Constraints

- MVI everywhere: State / Action / Event sealed classes + ViewModel; Root(has VM)/Screen(stateless, previewable) split. Every Screen composable has a `@Preview`.
- No hardcoded user-facing strings — use compose.resources (`Res.string.*`). New strings go in `composeApp/src/commonMain/composeResources/values/strings.xml`.
- In `strings.xml`, `&` MUST be written `&amp;`; do NOT use backslash escapes; use a curly `’` (U+2019) for apostrophes (CMP iOS renders `\'` literally — see project memory).
- Result<T, E> for expected failures; never throw. (No new error paths here — reuse existing.)
- State lives in the ViewModel, never in `remember`/`rememberSaveable` (except Compose-internal state like `SnackbarHostState`, `rememberScrollState`).
- Test task command: `./gradlew :composeApp:testDebugUnitTest`. iOS compile gate: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64`. Lint: `./gradlew detekt`.
- Test function names: use plain camelCase-with-underscores identifiers like the existing settings tests (e.g. `toggleOff_snapshotDriven_disablesAndPersists`). Do NOT use backtick names (iOS backtick-name restriction — project memory).
- Feature branch / worktree: work happens in `.claude/worktrees/settings-ia-hub` on branch `worktree-settings-ia-hub` (already created off `main`).

---

## File map

**Create:**
- `feature/settings/presentation/home/SettingsEventEffect.kt` — reusable event→callback effect (extracted from `SettingsRoot`).
- `feature/settings/presentation/account/SettingsAccountRoot.kt`, `SettingsAccountScreen.kt`
- `feature/settings/presentation/inviterewards/SettingsInviteRewardsRoot.kt`, `SettingsInviteRewardsScreen.kt`
- `feature/settings/presentation/helpsupport/SettingsHelpSupportRoot.kt`, `SettingsHelpSupportScreen.kt`
- `feature/settings/presentation/legalabout/SettingsLegalAboutRoot.kt`, `SettingsLegalAboutScreen.kt`
- `commonTest/.../feature/settings/SettingsHubNavigationTest.kt`
- `commonTest/.../core/config/AppConfigMapperTest.kt` (if no existing mapper test)

**Modify:**
- `core/config/data/dto/AppConfigDto.kt`, `core/config/domain/model/AppConfig.kt`, `core/config/data/mapper/AppConfigMapper.kt`
- `feature/settings/presentation/home/SettingsState.kt`, `SettingsViewModel.kt`, `SettingsAction.kt`, `SettingsEvent.kt`, `SettingsRoot.kt`, `SettingsScreen.kt`
- `navigation/Routes.kt`
- `feature/main/presentation/MainScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

Base package: `com.danzucker.stitchpad`, root path `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/`.

---

## Task 1: Remote flag `settingsHubEnabled` surfaced in Settings state

**Files:**
- Modify: `core/config/data/dto/AppConfigDto.kt`
- Modify: `core/config/domain/model/AppConfig.kt`
- Modify: `core/config/data/mapper/AppConfigMapper.kt`
- Modify: `feature/settings/presentation/home/SettingsState.kt`
- Modify: `feature/settings/presentation/home/SettingsViewModel.kt` (`buildState`)
- Test: `commonTest/.../core/config/AppConfigMapperTest.kt`, `commonTest/.../feature/settings/SettingsHubFlagTest.kt`

**Interfaces:**
- Produces: `AppConfig.settingsHubEnabled: Boolean` (default `false`); `SettingsState.settingsHubEnabled: Boolean` (default `false`).

- [ ] **Step 1: Write the failing mapper test**

Create `commonTest/kotlin/com/danzucker/stitchpad/core/config/AppConfigMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.data.mapper.toAppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigMapperTest {
    @Test
    fun settingsHubEnabled_defaultsFalse_whenAbsent() {
        assertFalse(AppConfigDto().toAppConfig().settingsHubEnabled)
    }

    @Test
    fun settingsHubEnabled_mapsThrough_whenTrue() {
        assertEquals(true, AppConfigDto(settingsHubEnabled = true).toAppConfig().settingsHubEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AppConfigMapperTest*"`
Expected: FAIL to compile — `settingsHubEnabled` unresolved on DTO/domain.

- [ ] **Step 3: Add the field to DTO, domain, and mapper**

In `AppConfigDto.kt`, add after `maintenanceMessage`:

```kotlin
    val maintenanceMessage: String? = null,
    val settingsHubEnabled: Boolean = false,
```

In `AppConfig.kt`, add a documented field after `maintenanceMessage`:

```kotlin
    /** Remote toggle for the restructured Settings hub (drill-down categories).
     * Default false — fail-open to the legacy flat layout on a missing/unreadable
     * config, matching [communityEnabled]. Flip `config/app.settingsHubEnabled`
     * to roll out; flip back to revert with no app release. */
    val settingsHubEnabled: Boolean = false,
```

In `AppConfigMapper.kt`, add the mapping line:

```kotlin
    maintenanceMessage = maintenanceMessage,
    settingsHubEnabled = settingsHubEnabled,
```

- [ ] **Step 4: Run mapper test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AppConfigMapperTest*"`
Expected: PASS.

- [ ] **Step 5: Write the failing VM-surface test**

Create `commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsHubFlagTest.kt`. Reuse the fake-builder pattern from `SettingsDigestToggleTest.kt` — copy its `buildSettingsVmForDigest` helper and inline fakes into this file, or extract a shared builder (see note below). Add a `FakeAppConfigRepository` param:

```kotlin
package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsHubFlagTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun settingsHubEnabled_false_byDefault() = runTest {
        val vm = buildSettingsVm(appConfig = FakeAppConfigRepository())
        vm.state.test {
            assertFalse(awaitItem().settingsHubEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsHubEnabled_true_whenRemoteFlagOn() = runTest {
        val config = FakeAppConfigRepository(AppConfig.Disabled.copy(settingsHubEnabled = true))
        val vm = buildSettingsVm(appConfig = config)
        vm.state.test {
            assertTrue(awaitItem().settingsHubEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

Note: `AppConfig.Disabled` is a val; `.copy(settingsHubEnabled = true)` works because `AppConfig` is a data class. Provide a `buildSettingsVm(appConfig: AppConfigRepository = FakeAppConfigRepository(), ...)` helper in this file mirroring `buildSettingsVmForDigest` (same 15 constructor args, same inline fakes). To avoid copy-paste across settings test files, you MAY instead extract the builder + inline fakes into a new `commonTest/.../feature/settings/SettingsVmTestFixtures.kt` and have `SettingsDigestToggleTest` and this file both use it — do that extraction in this step if you prefer DRY; otherwise inline.

- [ ] **Step 6: Run VM-surface test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsHubFlagTest*"`
Expected: FAIL — `settingsHubEnabled` unresolved on `SettingsState`.

- [ ] **Step 7: Surface the flag in state**

In `SettingsState.kt`, add after `communityUrl`:

```kotlin
    val communityUrl: String? = null,
    val settingsHubEnabled: Boolean = false,
```

In `SettingsViewModel.kt` `buildState(...)`, set it from `appConfig` (the param already exists):

```kotlin
            communityEnabled = appConfig.communityEnabled,
            communityUrl = appConfig.communityInviteUrl,
            settingsHubEnabled = appConfig.settingsHubEnabled,
```

- [ ] **Step 8: Run both test classes to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AppConfigMapperTest*" --tests "*SettingsHubFlagTest*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/config/AppConfigMapperTest.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsHubFlagTest.kt
git commit -m "feat(settings): add remote settingsHubEnabled flag, surface in state"
```

---

## Task 2: Extract `SettingsEventEffect` (behavior-preserving)

**Files:**
- Create: `feature/settings/presentation/home/SettingsEventEffect.kt`
- Modify: `feature/settings/presentation/home/SettingsRoot.kt`

**Interfaces:**
- Produces: `@Composable fun SettingsEventEffect(events: Flow<SettingsEvent>, snackbarHostState: SnackbarHostState, onNavigateBack: () -> Unit = {}, onNavigateToEditProfile: () -> Unit = {}, onNavigateToChangeEmail: () -> Unit = {}, onNavigateToChangePassword: () -> Unit = {}, onNavigateToReferralCode: () -> Unit = {}, onNavigateToDeleteAccount: () -> Unit = {}, onSignedOut: () -> Unit = {}, onNavigateToDebugMenu: () -> Unit = {}, onNavigateToUpgrade: () -> Unit = {}, onNavigateToFoundersNote: () -> Unit = {}, onNavigateToShareGiftLink: () -> Unit = {}, onNavigateToRedeemGift: () -> Unit = {}, onNavigateToHelpTutorials: () -> Unit = {})` — all navigation callbacks default to no-op so each Root passes only the subset it needs.

- [ ] **Step 1: Create `SettingsEventEffect.kt`**

Move the entire `ObserveAsEvents(viewModel.events) { event -> when (event) { ... } }` block out of `SettingsRoot.kt` into this new composable. It owns the `LocalUriHandler`, a `rememberCoroutineScope`, and the snackbar/whatsapp/URL resolution — identical logic to today. Each `Navigate*` event calls the matching callback param. Skeleton (fill the `when` with the exact branches currently in `SettingsRoot`):

```kotlin
package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun SettingsEventEffect(
    events: Flow<SettingsEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeEmail: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToReferralCode: () -> Unit = {},
    onNavigateToDeleteAccount: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onNavigateToDebugMenu: () -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    onNavigateToFoundersNote: () -> Unit = {},
    onNavigateToShareGiftLink: () -> Unit = {},
    onNavigateToRedeemGift: () -> Unit = {},
    onNavigateToHelpTutorials: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    ObserveAsEvents(events) { event ->
        when (event) {
            SettingsEvent.NavigateBack -> onNavigateBack()
            SettingsEvent.NavigateToEditProfile -> onNavigateToEditProfile()
            SettingsEvent.NavigateToChangeEmail -> onNavigateToChangeEmail()
            SettingsEvent.NavigateToChangePassword -> onNavigateToChangePassword()
            SettingsEvent.NavigateToReferralCode -> onNavigateToReferralCode()
            SettingsEvent.NavigateToDeleteAccount -> onNavigateToDeleteAccount()
            SettingsEvent.NavigateToLoginAfterSignOut -> onSignedOut()
            SettingsEvent.NavigateToDebugMenu -> onNavigateToDebugMenu()
            SettingsEvent.NavigateToUpgrade -> onNavigateToUpgrade()
            SettingsEvent.NavigateToFoundersNote -> onNavigateToFoundersNote()
            SettingsEvent.NavigateToShareGiftLink -> onNavigateToShareGiftLink()
            SettingsEvent.NavigateToRedeemGift -> onNavigateToRedeemGift()
            SettingsEvent.NavigateToHelpTutorials -> onNavigateToHelpTutorials()
            is SettingsEvent.OpenUrl -> uriHandler.openUri(event.url)
            is SettingsEvent.OpenCommunityLink ->
                runCatching { uriHandler.openUri(event.url) }
                    .onFailure {
                        AppLogger.e(tag = "SettingsEventEffect", throwable = it) {
                            "No handler to open community invite"
                        }
                    }
            is SettingsEvent.OpenWhatsApp -> scope.launch {
                val message = getString(event.messageRes)
                uriHandler.openUri(buildWhatsAppUrl(event.phoneNumber, message))
            }
            is SettingsEvent.ShowSnackbar -> scope.launch {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> getString(text.id)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}
```

Note: `SettingsEvent` is an exhaustive `sealed interface` — the `when` must stay exhaustive, so copy every branch that exists in `SettingsRoot` today (the block above matches the current file; verify against `SettingsRoot.kt` before saving in case new events were added).

- [ ] **Step 2: Rewrite `SettingsRoot.kt` to delegate**

Replace the inline `ObserveAsEvents { ... }` block with a call to `SettingsEventEffect(...)`, passing all callbacks. `SettingsRoot` keeps `koinViewModel`, `collectAsStateWithLifecycle`, the `snackbarHostState`, and rendering `SettingsScreen`. Result:

```kotlin
@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToReferralCode: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToDebugMenu: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToFoundersNote: () -> Unit,
    onNavigateToShareGiftLink: () -> Unit,
    onNavigateToRedeemGift: () -> Unit,
    onNavigateToHelpTutorials: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToEditProfile = onNavigateToEditProfile,
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        onNavigateToChangePassword = onNavigateToChangePassword,
        onNavigateToReferralCode = onNavigateToReferralCode,
        onNavigateToDeleteAccount = onNavigateToDeleteAccount,
        onSignedOut = onSignedOut,
        onNavigateToDebugMenu = onNavigateToDebugMenu,
        onNavigateToUpgrade = onNavigateToUpgrade,
        onNavigateToFoundersNote = onNavigateToFoundersNote,
        onNavigateToShareGiftLink = onNavigateToShareGiftLink,
        onNavigateToRedeemGift = onNavigateToRedeemGift,
        onNavigateToHelpTutorials = onNavigateToHelpTutorials,
    )

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}
```

Remove now-unused imports from `SettingsRoot.kt` (`LocalUriHandler`, `rememberCoroutineScope`, `buildWhatsAppUrl`, `getString`, `AppLogger`, `UiText`, `ObserveAsEvents`, `launch`, the two message string imports) — detekt flags unused imports.

- [ ] **Step 3: Verify build + existing settings tests still green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*settings*" && ./gradlew detekt`
Expected: PASS (no behavior change; existing settings tests + lint green).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEventEffect.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsRoot.kt
git commit -m "refactor(settings): extract reusable SettingsEventEffect"
```

---

## Task 3: New routes, actions, events + VM navigation mapping

**Files:**
- Modify: `navigation/Routes.kt`
- Modify: `feature/settings/presentation/home/SettingsAction.kt`
- Modify: `feature/settings/presentation/home/SettingsEvent.kt`
- Modify: `feature/settings/presentation/home/SettingsViewModel.kt` (`onAction`)
- Modify: `feature/settings/presentation/home/SettingsEventEffect.kt` (handle 4 new events)
- Test: `commonTest/.../feature/settings/SettingsHubNavigationTest.kt`

**Interfaces:**
- Produces routes: `SettingsAccountRoute`, `SettingsInviteRewardsRoute`, `SettingsHelpSupportRoute`, `SettingsLegalAboutRoute` (all `@Serializable data object`).
- Produces actions: `SettingsAction.OnAccountSecurityClick`, `OnInviteRewardsClick`, `OnHelpSupportClick`, `OnLegalAboutClick`.
- Produces events: `SettingsEvent.NavigateToAccountSecurity`, `NavigateToInviteRewards`, `NavigateToHelpSupport`, `NavigateToLegalAbout`.
- Produces `SettingsEventEffect` params: `onNavigateToAccountSecurity`, `onNavigateToInviteRewards`, `onNavigateToHelpSupport`, `onNavigateToLegalAbout` (default `{}`).

- [ ] **Step 1: Write the failing navigation test**

Create `commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsHubNavigationTest.kt`. Use the `buildSettingsVm(...)` helper/fixtures from Task 1 (import or duplicate):

```kotlin
package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsHubNavigationTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun accountSecurityClick_emitsNavigateToAccountSecurity() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnAccountSecurityClick)
            assertEquals(SettingsEvent.NavigateToAccountSecurity, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inviteRewardsClick_emitsNavigateToInviteRewards() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnInviteRewardsClick)
            assertEquals(SettingsEvent.NavigateToInviteRewards, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun helpSupportClick_emitsNavigateToHelpSupport() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnHelpSupportClick)
            assertEquals(SettingsEvent.NavigateToHelpSupport, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun legalAboutClick_emitsNavigateToLegalAbout() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnLegalAboutClick)
            assertEquals(SettingsEvent.NavigateToLegalAbout, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsHubNavigationTest*"`
Expected: FAIL to compile — new actions/events unresolved.

- [ ] **Step 3: Add actions**

In `SettingsAction.kt`, add:

```kotlin
    data object OnAccountSecurityClick : SettingsAction
    data object OnInviteRewardsClick : SettingsAction
    data object OnHelpSupportClick : SettingsAction
    data object OnLegalAboutClick : SettingsAction
```

- [ ] **Step 4: Add events**

In `SettingsEvent.kt`, add:

```kotlin
    data object NavigateToAccountSecurity : SettingsEvent
    data object NavigateToInviteRewards : SettingsEvent
    data object NavigateToHelpSupport : SettingsEvent
    data object NavigateToLegalAbout : SettingsEvent
```

- [ ] **Step 5: Map actions → events in `SettingsViewModel.onAction`**

```kotlin
            SettingsAction.OnAccountSecurityClick -> emit(SettingsEvent.NavigateToAccountSecurity)
            SettingsAction.OnInviteRewardsClick -> emit(SettingsEvent.NavigateToInviteRewards)
            SettingsAction.OnHelpSupportClick -> emit(SettingsEvent.NavigateToHelpSupport)
            SettingsAction.OnLegalAboutClick -> emit(SettingsEvent.NavigateToLegalAbout)
```

- [ ] **Step 6: Handle the 4 new events in `SettingsEventEffect`**

Add four params (default `{}`) and four `when` branches:

```kotlin
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToInviteRewards: () -> Unit = {},
    onNavigateToHelpSupport: () -> Unit = {},
    onNavigateToLegalAbout: () -> Unit = {},
```
```kotlin
            SettingsEvent.NavigateToAccountSecurity -> onNavigateToAccountSecurity()
            SettingsEvent.NavigateToInviteRewards -> onNavigateToInviteRewards()
            SettingsEvent.NavigateToHelpSupport -> onNavigateToHelpSupport()
            SettingsEvent.NavigateToLegalAbout -> onNavigateToLegalAbout()
```

- [ ] **Step 7: Add routes**

In `Routes.kt`, after `FoundersNoteRoute`:

```kotlin
@Serializable
data object SettingsAccountRoute

@Serializable
data object SettingsInviteRewardsRoute

@Serializable
data object SettingsHelpSupportRoute

@Serializable
data object SettingsLegalAboutRoute
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsHubNavigationTest*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEvent.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEventEffect.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsHubNavigationTest.kt
git commit -m "feat(settings): add hub category routes, actions, events"
```

---

## Task 4: Account & security sub-screen

**Files:**
- Create: `feature/settings/presentation/account/SettingsAccountScreen.kt`
- Create: `feature/settings/presentation/account/SettingsAccountRoot.kt`
- Modify: `feature/settings/presentation/home/SettingsScreen.kt` (move `SignOutConfirmDialog` + `providerSubtitle` to shared location — see below)
- Modify: `feature/main/presentation/MainScreen.kt` (add `composable<SettingsAccountRoute>`, add `onNavigateToAccountSecurity` to the `SettingsRoot` call)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (add `settings_account_title`)

**Interfaces:**
- Consumes: `SettingsState`, `SettingsAction`, `SettingsEventEffect` (Task 2/3), `SettingsAccountRoute` (Task 3).
- Produces: `SettingsAccountScreen(state, snackbarHostState, onAction)`, `SettingsAccountRoot(onNavigateBack, onNavigateToChangeEmail, onNavigateToChangePassword, onSignedOut, viewModel)`.

- [ ] **Step 1: Make `SignOutConfirmDialog` + `providerSubtitle` shared**

These are currently `private` in `SettingsScreen.kt` and are needed by BOTH the legacy landing (flag-off, still present) and the new Account screen. Move both to a new file `feature/settings/presentation/account/SettingsAccountShared.kt` as `internal` top-level composables (keep identical bodies — copy verbatim from `SettingsScreen.kt` lines defining `providerSubtitle` and `SignOutConfirmDialog`). Then in `SettingsScreen.kt`, delete the two private copies and import the shared ones. Leave `receiptImageStyleLabel` in `SettingsScreen.kt` (landing-only).

- [ ] **Step 2: Create `SettingsAccountScreen.kt`**

Stateless full screen: `Scaffold` + `TopAppBar` (title `settings_account_title`, back arrow → `onAction(SettingsAction.OnBackClick)`), one `SettingsSectionCard` with the account rows, and the sign-out dialog. Mirror the row structure from the current `settings_section_account` block in `SettingsScreen.kt`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.settings_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space3),
        ) {
            Spacer(Modifier.height(DesignTokens.space2))
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    label = stringResource(Res.string.settings_row_signin_method),
                    onClick = null,
                    subtitle = providerSubtitle(state.signInProvider, state.maskedSignInIdentifier),
                )
                if (state.showChangeEmailRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Email,
                        label = stringResource(Res.string.settings_row_email),
                        subtitle = state.email,
                        onClick = { onAction(SettingsAction.OnEmailRowClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
                if (state.showChangePasswordRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        label = stringResource(Res.string.settings_row_change_password),
                        onClick = { onAction(SettingsAction.OnChangePasswordClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Logout,
                    label = stringResource(Res.string.settings_row_sign_out),
                    onClick = { onAction(SettingsAction.OnSignOutRowClick) },
                )
            }
            Spacer(Modifier.height(DesignTokens.space5))
        }
        if (state.showSignOutDialog) {
            SignOutConfirmDialog(
                onConfirm = { onAction(SettingsAction.OnSignOutConfirm) },
                onDismiss = { onAction(SettingsAction.OnSignOutDismiss) },
            )
        }
    }
}
```

Add two `@Preview`s (email/password provider — shows all rows; SSO provider — hides email + password rows), light and one dark, using `SettingsState(...)` literals like the existing `SettingsScreenPreview`.

- [ ] **Step 3: Create `SettingsAccountRoot.kt`**

```kotlin
@Composable
fun SettingsAccountRoot(
    onNavigateBack: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        onNavigateToChangePassword = onNavigateToChangePassword,
        onSignedOut = onSignedOut,
    )
    SettingsAccountScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}
```

- [ ] **Step 4: Add the title string**

In `strings.xml` (near the other settings strings):

```xml
    <string name="settings_account_title">Account</string>
```

- [ ] **Step 5: Wire `MainScreen`**

Add the `onNavigateToAccountSecurity` callback to the existing `SettingsRoot(...)` call:

```kotlin
                onNavigateToHelpTutorials = { navController.navigate(HelpTutorialsRoute) },
                onNavigateToAccountSecurity = { navController.navigate(SettingsAccountRoute) },
```

(That parameter is added to `SettingsRoot`'s signature in Task 8; if implementing Task 4 before Task 8, add the param to `SettingsRoot` now with a default `{}` and forward it into `SettingsEventEffect`.) Add the composable entry next to the other settings destinations:

```kotlin
        composable<SettingsAccountRoute> {
            SettingsAccountRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToChangeEmail = { navController.navigate(ChangeEmailRoute) },
                onNavigateToChangePassword = { navController.navigate(ChangePasswordRoute) },
                onSignedOut = onSignedOut,
            )
        }
```

Add imports for `SettingsAccountRoot` and `SettingsAccountRoute`.

- [ ] **Step 6: Build + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: PASS. (Previews render in Android Studio; verify the Account screen preview shows correctly light + dark.)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/account \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(settings): add Account & security sub-screen"
```

---

## Task 5: Invite & rewards sub-screen

**Files:**
- Create: `feature/settings/presentation/inviterewards/SettingsInviteRewardsScreen.kt`, `SettingsInviteRewardsRoot.kt`
- Modify: `feature/main/presentation/MainScreen.kt`
- Modify: `strings.xml` (`settings_invite_rewards_title`)

**Interfaces:**
- Produces: `SettingsInviteRewardsScreen(state, onAction)`, `SettingsInviteRewardsRoot(onNavigateBack, onNavigateToReferralCode, onNavigateToShareGiftLink, onNavigateToRedeemGift, viewModel)`.

- [ ] **Step 1: Create the Screen**

Same Scaffold/TopAppBar shell as Task 4 (title `settings_invite_rewards_title`). Body is one `SettingsSectionCard` with the invite + referral rows copied from the current `settings_section_business` block, including the `GIFTING_ENABLED`-gated gift rows. `GIFTING_ENABLED` is a `private const` in `SettingsScreen.kt`; to reuse it, change it to `internal const val GIFTING_ENABLED` in `SettingsScreen.kt` and reference it here, OR redeclare the same `private const val GIFTING_ENABLED = false` at the top of this file with a comment pointing at the canonical one. Prefer making it `internal` in `SettingsScreen.kt` (single source of truth).

```kotlin
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Outlined.PersonAddAlt,
                    label = stringResource(Res.string.settings_row_invite),
                    subtitle = stringResource(Res.string.settings_row_invite_subtitle),
                    onClick = { onAction(SettingsAction.OnInviteClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Redeem,
                    label = stringResource(Res.string.referral_code_settings_row),
                    subtitle = stringResource(Res.string.referral_code_settings_subtitle),
                    onClick = { onAction(SettingsAction.OnReferralCodeClick) },
                    trailing = { SettingsRowChevron() },
                )
                if (GIFTING_ENABLED) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.CardGiftcard,
                        label = stringResource(Res.string.gift_share_settings_row),
                        subtitle = stringResource(Res.string.gift_share_settings_subtitle),
                        onClick = { onAction(SettingsAction.OnGetGiftedClick) },
                        trailing = { SettingsRowChevron() },
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Redeem,
                        label = stringResource(Res.string.gift_redeem_title),
                        subtitle = stringResource(Res.string.gift_redeem_settings_subtitle),
                        onClick = { onAction(SettingsAction.OnRedeemGiftClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
            }
```

Add a light and a dark `@Preview`.

- [ ] **Step 2: Create the Root**

```kotlin
@Composable
fun SettingsInviteRewardsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToReferralCode: () -> Unit,
    onNavigateToShareGiftLink: () -> Unit,
    onNavigateToRedeemGift: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToReferralCode = onNavigateToReferralCode,
        onNavigateToShareGiftLink = onNavigateToShareGiftLink,
        onNavigateToRedeemGift = onNavigateToRedeemGift,
    )
    SettingsInviteRewardsScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}
```

(Invite share is an `OpenWhatsApp` event handled inside `SettingsEventEffect` — no extra callback needed.)

- [ ] **Step 3: Add the title string**

```xml
    <string name="settings_invite_rewards_title">Invite &amp; rewards</string>
```

- [ ] **Step 4: Wire `MainScreen`**

Add `onNavigateToInviteRewards = { navController.navigate(SettingsInviteRewardsRoute) }` to the `SettingsRoot(...)` call, and:

```kotlin
        composable<SettingsInviteRewardsRoute> {
            SettingsInviteRewardsRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToReferralCode = { navController.navigate(ReferralCodeRoute) },
                onNavigateToShareGiftLink = { navController.navigate(ShareGiftLinkRoute) },
                onNavigateToRedeemGift = { navController.navigate(RedeemGiftRoute) },
            )
        }
```

- [ ] **Step 5: Build + detekt + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/inviterewards \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(settings): add Invite & rewards sub-screen"
```

---

## Task 6: Help & support sub-screen

**Files:**
- Create: `feature/settings/presentation/helpsupport/SettingsHelpSupportScreen.kt`, `SettingsHelpSupportRoot.kt`
- Modify: `feature/main/presentation/MainScreen.kt`
- Modify: `strings.xml` (`settings_help_support_title`)

**Interfaces:**
- Produces: `SettingsHelpSupportScreen(state, onAction)`, `SettingsHelpSupportRoot(onNavigateBack, onNavigateToHelpTutorials, viewModel)`.

- [ ] **Step 1: Create the Screen**

Scaffold/TopAppBar (title `settings_help_support_title`). Body = one `SettingsSectionCard` with the support rows copied from the current `settings_section_support` block, EXCEPT "About your plan" (founder's note), which moves to Legal & about (Task 7):

```kotlin
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    label = stringResource(Res.string.settings_row_tutorials),
                    subtitle = stringResource(Res.string.settings_row_tutorials_subtitle),
                    onClick = { onAction(SettingsAction.OnHelpTutorialsClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    label = stringResource(Res.string.settings_row_contact),
                    subtitle = stringResource(Res.string.settings_row_contact_subtitle),
                    onClick = { onAction(SettingsAction.OnContactClick) },
                    trailing = { SettingsRowChevron() },
                )
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
            }
```

Add a `@Preview` with `showCommunityRow` true (community enabled) and one dark.

- [ ] **Step 2: Create the Root**

```kotlin
@Composable
fun SettingsHelpSupportRoot(
    onNavigateBack: () -> Unit,
    onNavigateToHelpTutorials: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToHelpTutorials = onNavigateToHelpTutorials,
    )
    SettingsHelpSupportScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}
```

(Contact = `OpenWhatsApp`, community = `OpenCommunityLink` — both handled inside `SettingsEventEffect`.)

- [ ] **Step 3: Add the title string**

```xml
    <string name="settings_help_support_title">Help &amp; support</string>
```

- [ ] **Step 4: Wire `MainScreen`**

Add `onNavigateToHelpSupport = { navController.navigate(SettingsHelpSupportRoute) }` to the `SettingsRoot(...)` call, and:

```kotlin
        composable<SettingsHelpSupportRoute> {
            SettingsHelpSupportRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToHelpTutorials = { navController.navigate(HelpTutorialsRoute) },
            )
        }
```

- [ ] **Step 5: Build + detekt + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/helpsupport \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(settings): add Help & support sub-screen"
```

---

## Task 7: Legal & about sub-screen

**Files:**
- Create: `feature/settings/presentation/legalabout/SettingsLegalAboutScreen.kt`, `SettingsLegalAboutRoot.kt`
- Modify: `feature/main/presentation/MainScreen.kt`
- Modify: `strings.xml` (`settings_legal_about_title`)

**Interfaces:**
- Produces: `SettingsLegalAboutScreen(state, onAction)`, `SettingsLegalAboutRoot(onNavigateBack, onNavigateToFoundersNote, viewModel)`.

- [ ] **Step 1: Create the Screen**

Scaffold/TopAppBar (title `settings_legal_about_title`). Body = one `SettingsSectionCard` with privacy + terms (external) + founder's note:

```kotlin
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    label = stringResource(Res.string.settings_row_privacy),
                    onClick = { onAction(SettingsAction.OnPrivacyClick) },
                    trailing = { SettingsRowExternalIcon() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    label = stringResource(Res.string.settings_row_terms),
                    onClick = { onAction(SettingsAction.OnTermsClick) },
                    trailing = { SettingsRowExternalIcon() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(Res.string.settings_row_founders_note),
                    subtitle = stringResource(Res.string.settings_row_founders_note_subtitle),
                    onClick = { onAction(SettingsAction.OnFoundersNoteClick) },
                    trailing = { SettingsRowChevron() },
                )
            }
```

Add a light and a dark `@Preview`.

- [ ] **Step 2: Create the Root**

```kotlin
@Composable
fun SettingsLegalAboutRoot(
    onNavigateBack: () -> Unit,
    onNavigateToFoundersNote: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToFoundersNote = onNavigateToFoundersNote,
    )
    SettingsLegalAboutScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}
```

(Privacy/terms = `OpenUrl` handled inside `SettingsEventEffect`.)

- [ ] **Step 3: Add the title string**

```xml
    <string name="settings_legal_about_title">Legal &amp; about</string>
```

- [ ] **Step 4: Wire `MainScreen`**

Add `onNavigateToLegalAbout = { navController.navigate(SettingsLegalAboutRoute) }` to the `SettingsRoot(...)` call, and:

```kotlin
        composable<SettingsLegalAboutRoute> {
            SettingsLegalAboutRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToFoundersNote = { navController.navigate(FoundersNoteRoute) },
            )
        }
```

- [ ] **Step 5: Build + detekt + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/legalabout \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(settings): add Legal & about sub-screen"
```

---

## Task 8: Flag-gated landing (hub layout)

**Files:**
- Modify: `feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `feature/settings/presentation/home/SettingsRoot.kt` (add the four `onNavigateTo*` category callbacks if not already added in Task 4–7)
- Modify: `feature/main/presentation/MainScreen.kt` (confirm all four category callbacks are wired on the `SettingsRoot` call)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: `state.settingsHubEnabled` (Task 1); the four category actions (Task 3).

- [ ] **Step 1: Add the Manage-section strings**

```xml
    <string name="settings_section_manage">Manage</string>
    <string name="settings_row_account_security">Account &amp; security</string>
    <string name="settings_row_account_security_subtitle">Email, password, sign-in method</string>
    <string name="settings_row_invite_rewards">Invite &amp; rewards</string>
    <string name="settings_row_invite_rewards_subtitle">Invite tailors, enter a referral code</string>
    <string name="settings_row_help_support">Help &amp; support</string>
    <string name="settings_row_help_support_subtitle">Tutorials, contact, community</string>
    <string name="settings_row_legal_about">Legal &amp; about</string>
    <string name="settings_row_legal_about_subtitle">Privacy, terms, founder’s note</string>
```

(Note the curly `’` in the last subtitle — not `'`.)

- [ ] **Step 2: Split the landing body into legacy + hub, branch on the flag**

In `SettingsScreen.kt`, keep the current body as a private `SettingsLandingLegacy(state, onAction)` composable (the existing hero + plan + six sections + delete + debug, unchanged — this is the flag-off path). Add a private `SettingsLandingHub(state, onAction)` composable with the new layout: hero, plan, the PREFERENCES section (identical rows to today — measurement/appearance/receipt/digest toggle/push toggle), then the MANAGE section, then the pinned delete + debug cards. Inside the `Scaffold`'s `Column`, branch:

```kotlin
            if (state.settingsHubEnabled) {
                SettingsLandingHub(state = state, onAction = onAction)
            } else {
                SettingsLandingLegacy(state = state, onAction = onAction)
            }
```

The MANAGE section:

```kotlin
            SettingsSectionCard(label = stringResource(Res.string.settings_section_manage)) {
                SettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    label = stringResource(Res.string.settings_row_account_security),
                    subtitle = stringResource(Res.string.settings_row_account_security_subtitle),
                    onClick = { onAction(SettingsAction.OnAccountSecurityClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.PersonAddAlt,
                    label = stringResource(Res.string.settings_row_invite_rewards),
                    subtitle = stringResource(Res.string.settings_row_invite_rewards_subtitle),
                    onClick = { onAction(SettingsAction.OnInviteRewardsClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    label = stringResource(Res.string.settings_row_help_support),
                    subtitle = stringResource(Res.string.settings_row_help_support_subtitle),
                    onClick = { onAction(SettingsAction.OnHelpSupportClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    label = stringResource(Res.string.settings_row_legal_about),
                    subtitle = stringResource(Res.string.settings_row_legal_about_subtitle),
                    onClick = { onAction(SettingsAction.OnLegalAboutClick) },
                    trailing = { SettingsRowChevron() },
                )
            }
```

The sign-out dialog: in the hub path, sign-out lives in the Account sub-screen, so the hub landing does NOT render `SignOutConfirmDialog`. Keep the existing `if (state.showSignOutDialog) SignOutConfirmDialog(...)` at the `SettingsScreen` level guarded so it still serves the legacy path — it is harmless in the hub path because the hub landing never dispatches `OnSignOutRowClick` (that row isn't shown), so `showSignOutDialog` stays false.

If splitting `SettingsScreen.kt` trips detekt `TooManyFunctions` (previews + two layouts), add `@file:Suppress("TooManyFunctions")` at the top (project convention — do not split preview files; see memory).

- [ ] **Step 3: Ensure `SettingsRoot` forwards the four category callbacks**

`SettingsRoot` must accept `onNavigateToAccountSecurity`, `onNavigateToInviteRewards`, `onNavigateToHelpSupport`, `onNavigateToLegalAbout` and pass them into `SettingsEventEffect`. (Added incrementally in Tasks 4–7; confirm all four are present with no default, and that `MainScreen`'s `SettingsRoot(...)` call passes all four navigating to the respective routes.)

- [ ] **Step 4: Update/add landing previews**

Add a hub-layout preview (`SettingsState(settingsHubEnabled = true, ...)`, light) and a dark hub preview. Keep at least one legacy preview (`settingsHubEnabled = false`) so the fallback stays covered. Existing `SettingsScreenPreview` etc. default `settingsHubEnabled=false` → they now exercise the legacy path; add `settingsHubEnabled = true` variants.

- [ ] **Step 5: Full build + tests + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest && ./gradlew detekt`
Expected: PASS. Visually verify in Android Studio: hub preview shows Preferences + Manage (4 rows) + Delete + (debug) Debug; legacy preview unchanged.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsRoot.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(settings): flag-gated hub landing with Manage category rows"
```

---

## Task 9: Cross-platform verification + manual QA

**Files:** none (verification only).

- [ ] **Step 1: iOS compile gate**

Run: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64`
Expected: PASS (catches any JVM-only API, backtick-name, or KMP linkage regressions — settings back-navigation has bitten iOS before).

- [ ] **Step 2: Full JVM test suite + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest && ./gradlew detekt`
Expected: PASS.

- [ ] **Step 3: Manual smoke test (Daniel is QA), flag ON**

Temporarily enable by setting `config/app.settingsHubEnabled = true` in Firestore (or emit `true` via the debug menu / a debug override if available), then:
1. Settings landing shows Preferences inline + four Manage rows; toggles (units, appearance, receipt, digest, push) flip in place.
2. Each Manage row opens its sub-screen; system back + top-bar back both return to the landing.
3. Account & security: change email, change password, sign out (dialog shows; confirm signs out to login).
4. Invite & rewards: invite opens WhatsApp share picker; referral code entry opens.
5. Help & support: tutorials open; "Contact us" opens WhatsApp; community link opens (when enabled).
6. Legal & about: privacy + terms open externally; founder's note opens.
7. Delete account + Debug menu (debug build) still reachable on the landing.
8. Set `settingsHubEnabled = false` → landing renders the old flat layout unchanged; every old row still works.

- [ ] **Step 4: Push branch + open PR (only when the user asks)**

Follow the project PR workflow (feature branch + CI + Cursor + `codex review`). Do NOT merge without both reviews. Keep `settingsHubEnabled` default false so merging is inert until the flag is flipped.

---

## Self-review notes

- **Spec coverage:** landing restructure (Task 8), four sub-screens (Tasks 4–7), preferences-inline + toggles-inline decision (Task 8), invite+referral nested (Task 5), remote-flag rollout via `AppConfig` (Task 1 + Task 8 gating), founder's note under Legal & about (Task 7), reuse of `SettingsViewModel` + shared event handling (Tasks 2–7). Out-of-scope items (Notifications sub-screen, search, Team row) are intentionally absent.
- **Deviation from spec:** spec preferred a graph-scoped shared VM; this plan uses a per-screen `SettingsViewModel` instance because the VM's `events` is a single-consumer `Channel` (a shared instance would race across the landing and a drill-down). Cost is a redundant flow subscription per drill-down, made cheap by `WhileSubscribed(5_000L)`.
- **Type consistency:** action names (`On*Click`) ↔ event names (`NavigateTo*`) ↔ `SettingsEventEffect` callback params are aligned across Tasks 3–8. `settingsHubEnabled` is spelled identically in DTO, domain, mapper, state, and tests.
