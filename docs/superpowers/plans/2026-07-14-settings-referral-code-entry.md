# Settings Referral Code Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every signed-in user a persistent Settings entry to type a referral code, so SSO users (who never saw the SignUp field) can be sent there by their marketer.

**Architecture:** One new self-contained MVI screen (`ReferralCode*`) in `feature/referral/presentation/entry/`, reached from a new row on the Settings home. It calls the existing `ReferralRepository.recordAttribution(code, deviceHash, MANUAL)` and reports the result. No server, Login, or SignUp changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin DI, kotlinx.coroutines Flow/Channel, kotlin.test + Turbine (commonTest), compose.resources string resources.

## Global Constraints

- MVI: every screen has State/Action/Event sealed types + a ViewModel. (CLAUDE.md)
- Root/Screen split: `ReferralCodeRoot` holds the `koinViewModel()`; `ReferralCodeScreen` is stateless and has a `@Preview`.
- `Result<T, E>` for expected failures — never throw. Errors map to `UiText` via a `toUiText()` extension in the presentation layer.
- No hardcoded user-facing strings — use compose.resources (`Res.string.*`). No backslash escapes in `strings.xml`; use `&apos;` for apostrophes (CMP iOS renders `\'` literally).
- All state lives in the ViewModel (no `remember`/`rememberSaveable` for business state).
- Koin: `viewModelOf` constructor ref; `koinViewModel()` only in Root composables.
- Navigation: `@Serializable` route object; cross-screen navigation via callbacks.
- Test convention: ViewModel tests set `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@BeforeTest` and `resetMain()` in `@AfterTest` (eager execution — no manual scheduler advance). kotlin.test + Turbine.
- Test gates before "done": `./gradlew :composeApp:testDebugUnitTest` (JVM), `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64` (iOS test-compile — required because backtick names + KMP), `./gradlew detekt`.
- Backtick test names use letters/digits/spaces/hyphens ONLY.

---

### Task 1: ReferralCode state + ViewModel (core logic, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeState.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeAction.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeEvent.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/ReferralErrorUiText.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModel.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeViewModelTest.kt`

**Interfaces:**
- Consumes (already exist):
  - `ReferralRepository.recordAttribution(code: String, deviceHash: String, source: ReferralSource): Result<AttributionOutcome, ReferralError>`
  - `AttributionOutcome(alreadyAttributed: Boolean, marketerId: String)`
  - `ReferralError { CODE_NOT_FOUND, UNAUTHENTICATED, NETWORK, UNKNOWN }`
  - `ReferralSource.MANUAL`
  - `ReferralPreferencesStore.getOrCreateDeviceId(): String`, `setAttributed()`
  - Test doubles (exist): `FakeReferralRepository` (`result`, `lastCode`, `lastDeviceHash`, `lastSource`, `callCount`), `FakeReferralPreferencesStore` (`deviceId`, `attributed`, `setAttributed()`)
- Produces (later tasks rely on these exact names):
  - `ReferralCodeState(codeInput: String = "", isSubmitting: Boolean = false)` with `val code: String`, `val canSubmit: Boolean`
  - `ReferralCodeAction`: `OnCodeChange(value: String)`, `OnApplyClick`, `OnBackClick`
  - `ReferralCodeEvent`: `NavigateBack`, `ShowMessage(message: UiText)`
  - `ReferralCodeViewModel(referralRepository: ReferralRepository, preferences: ReferralPreferencesStore)` with `val state: StateFlow<ReferralCodeState>`, `val events: Flow<ReferralCodeEvent>`, `fun onAction(action: ReferralCodeAction)`
  - `fun ReferralError.toUiText(): UiText`

- [ ] **Step 1: Add string resources**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add near the other `settings_row_*` / referral strings:

```xml
<string name="referral_code_settings_row">Have a referral code?</string>
<string name="referral_code_settings_subtitle">Enter a code from whoever referred you</string>
<string name="referral_code_title">Referral code</string>
<string name="referral_code_field_label">Referral code</string>
<string name="referral_code_field_placeholder">e.g. ABCD1234</string>
<string name="referral_code_apply">Apply code</string>
<string name="referral_code_applied">Referral code applied</string>
<string name="referral_code_not_recognized">That code wasn&apos;t recognized. Check it and try again.</string>
<string name="referral_code_network_error">No connection. Check your internet and try again.</string>
<string name="referral_code_generic_error">Something went wrong. Please try again.</string>
```

- [ ] **Step 2: Write the failing ViewModel test**

Create `ReferralCodeViewModelTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.referral.data.FakeReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.data.FakeReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferralCodeViewModelTest {

    private val repo = FakeReferralRepository()
    private val prefs = FakeReferralPreferencesStore()

    private fun viewModel() = ReferralCodeViewModel(repo, prefs)

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `apply submits normalized code with manual source and device hash`() = runTest {
        prefs.deviceId = "dev-42"
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange(" abcd-1234 "))
        vm.onAction(ReferralCodeAction.OnApplyClick)
        assertEquals("ABCD1234", repo.lastCode)
        assertEquals("dev-42", repo.lastDeviceHash)
        assertEquals(ReferralSource.MANUAL, repo.lastSource)
    }

    @Test
    fun `apply success shows message navigates back and marks attributed`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = false, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ShowMessage)
            assertEquals(ReferralCodeEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(prefs.attributed)
    }

    @Test
    fun `already attributed is treated as success`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = true, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ShowMessage)
            assertEquals(ReferralCodeEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `code not found shows message and does not navigate back`() = runTest {
        repo.result = Result.Error(ReferralError.CODE_NOT_FOUND)
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("NOPE9999"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ShowMessage)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(prefs.attributed)
    }

    @Test
    fun `blank code does not call the repository`() = runTest {
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("   "))
        vm.onAction(ReferralCodeAction.OnApplyClick)
        assertEquals(0, repo.callCount)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferralCodeViewModelTest*"`
Expected: FAIL — `ReferralCodeViewModel`, `ReferralCodeAction`, `ReferralCodeEvent` unresolved.

- [ ] **Step 4: Create the State**

`ReferralCodeState.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

data class ReferralCodeState(
    val codeInput: String = "",
    val isSubmitting: Boolean = false,
) {
    /** Normalized to the server's code shape (strip spaces/hyphens, uppercase). */
    val code: String get() = codeInput.replace(Regex("[\\s-]"), "").uppercase()
    val canSubmit: Boolean get() = code.isNotBlank() && !isSubmitting
}
```

- [ ] **Step 5: Create the Action**

`ReferralCodeAction.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

sealed interface ReferralCodeAction {
    data class OnCodeChange(val value: String) : ReferralCodeAction
    data object OnApplyClick : ReferralCodeAction
    data object OnBackClick : ReferralCodeAction
}
```

- [ ] **Step 6: Create the Event**

`ReferralCodeEvent.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ReferralCodeEvent {
    data object NavigateBack : ReferralCodeEvent
    data class ShowMessage(val message: UiText) : ReferralCodeEvent
}
```

- [ ] **Step 7: Create the ReferralError → UiText mapping**

`ReferralErrorUiText.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_generic_error
import stitchpad.composeapp.generated.resources.referral_code_network_error
import stitchpad.composeapp.generated.resources.referral_code_not_recognized

fun ReferralError.toUiText(): UiText = when (this) {
    ReferralError.CODE_NOT_FOUND -> UiText.StringResourceText(Res.string.referral_code_not_recognized)
    ReferralError.NETWORK -> UiText.StringResourceText(Res.string.referral_code_network_error)
    ReferralError.UNAUTHENTICATED,
    ReferralError.UNKNOWN -> UiText.StringResourceText(Res.string.referral_code_generic_error)
}
```

- [ ] **Step 8: Create the ViewModel**

`ReferralCodeViewModel.kt`:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import com.danzucker.stitchpad.feature.referral.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_applied

class ReferralCodeViewModel(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferralCodeState())
    val state = _state.asStateFlow()

    private val _events = Channel<ReferralCodeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: ReferralCodeAction) {
        when (action) {
            is ReferralCodeAction.OnCodeChange -> _state.update { it.copy(codeInput = action.value) }
            ReferralCodeAction.OnApplyClick -> apply()
            ReferralCodeAction.OnBackClick -> emit(ReferralCodeEvent.NavigateBack)
        }
    }

    private fun apply() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val deviceHash = preferences.getOrCreateDeviceId()
            val result = referralRepository.recordAttribution(
                code = current.code,
                deviceHash = deviceHash,
                source = ReferralSource.MANUAL,
            )
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Result.Success -> {
                    // Keep local capture state consistent so the auto-capture
                    // coordinator doesn't re-attempt on next launch.
                    preferences.setAttributed()
                    emit(ReferralCodeEvent.ShowMessage(UiText.StringResourceText(Res.string.referral_code_applied)))
                    emit(ReferralCodeEvent.NavigateBack)
                }
                is Result.Error -> emit(ReferralCodeEvent.ShowMessage(result.error.toUiText()))
            }
        }
    }

    private fun emit(event: ReferralCodeEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferralCodeViewModelTest*"`
Expected: PASS (5 tests).

- [ ] **Step 10: Verify iOS test-compile + detekt**

Run: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/referral/presentation \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(referral): ReferralCode entry ViewModel + logic"
```

---

### Task 2: ReferralCodeScreen + Root (UI)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry/ReferralCodeRoot.kt`

**Interfaces:**
- Consumes: `ReferralCodeState`, `ReferralCodeAction`, `ReferralCodeEvent`, `ReferralCodeViewModel` (Task 1).
- Produces: `ReferralCodeRoot(onNavigateBack: () -> Unit, viewModel: ReferralCodeViewModel = koinViewModel())`.

- [ ] **Step 1: Create the stateless Screen (with @Preview)**

`ReferralCodeScreen.kt` — a Scaffold with a back top bar (mirrors `ChangePasswordScreen` chrome), a single code field, and an apply button:

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_apply
import stitchpad.composeapp.generated.resources.referral_code_field_label
import stitchpad.composeapp.generated.resources.referral_code_field_placeholder
import stitchpad.composeapp.generated.resources.referral_code_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralCodeScreen(
    state: ReferralCodeState,
    snackbarHostState: SnackbarHostState,
    onAction: (ReferralCodeAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.referral_code_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(ReferralCodeAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = { onAction(ReferralCodeAction.OnCodeChange(it)) },
                label = { Text(stringResource(Res.string.referral_code_field_label)) },
                placeholder = { Text(stringResource(Res.string.referral_code_field_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            StitchPadButton(
                text = stringResource(Res.string.referral_code_apply),
                onClick = { onAction(ReferralCodeAction.OnApplyClick) },
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ReferralCodeScreenPreview() {
    StitchPadTheme {
        ReferralCodeScreen(
            state = ReferralCodeState(codeInput = "ABCD1234"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
```

Note: `StitchPadButton`'s param names (`text`, `onClick`, `enabled`, `isLoading`, `modifier`) match SignUpScreen's usage — verify against `ui/components/StitchPadButton.kt` and adjust if the signature differs.

- [ ] **Step 2: Create the Root**

`ReferralCodeRoot.kt` (mirrors `ChangePasswordRoot`):

```kotlin
package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReferralCodeRoot(
    onNavigateBack: () -> Unit,
    viewModel: ReferralCodeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ReferralCodeEvent.NavigateBack -> onNavigateBack()
            is ReferralCodeEvent.ShowMessage -> {
                scope.launch { snackbarHostState.showSnackbar(resolve(event.message)) }
            }
        }
    }

    ReferralCodeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@Suppress("SpreadOperator")
private suspend fun resolve(text: UiText): String = when (text) {
    is UiText.DynamicString -> text.value
    is UiText.StringResourceText -> getString(text.id, *text.args)
}
```

- [ ] **Step 3: Verify it compiles (Android + iOS)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL. (No unit test — Compose UI verified by compile + preview.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/presentation/entry
git commit -m "feat(referral): ReferralCode entry Screen + Root"
```

---

### Task 3: Wire into navigation, Settings, and DI

Task 3 is pure wiring — a route, a Settings row, callbacks, and DI registration that mirror the existing sibling entries (ChangePassword, ChangeEmail, DeleteAccount). It adds **no new behavior logic**, so per the codebase convention (the sibling nav events have no dedicated unit tests, and `SettingsViewModel` has 13 constructor deps) it is verified by a full compile + the manual smoke test rather than a bespoke ViewModel test. The behavior that *does* carry risk (the attribution submit) is fully unit-tested in Task 1.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`

**Interfaces:**
- Consumes: `ReferralCodeRoot(onNavigateBack)`, `ReferralCodeViewModel` (Tasks 1–2).
- Produces: `ReferralCodeRoute`, `SettingsAction.OnReferralCodeClick`, `SettingsEvent.NavigateToReferralCode`, `SettingsRoot(..., onNavigateToReferralCode: () -> Unit, ...)`.

- [ ] **Step 1: Add the route**

In `Routes.kt`, next to `ChangePasswordRoute` (each route object is annotated `@Serializable`; match the exact annotation used by its neighbors):

```kotlin
@Serializable
data object ReferralCodeRoute
```

- [ ] **Step 2: Add the Settings action + event**

In `SettingsAction.kt`, add to the sealed interface:

```kotlin
data object OnReferralCodeClick : SettingsAction
```

In `SettingsEvent.kt`, add (mirror `NavigateToChangePassword`):

```kotlin
data object NavigateToReferralCode : SettingsEvent
```

- [ ] **Step 3: Map the action in SettingsViewModel**

In `SettingsViewModel.onAction`, next to the existing `SettingsAction.OnChangePasswordClick -> ...` branch, add — using the same event-emit mechanism that branch uses:

```kotlin
SettingsAction.OnReferralCodeClick -> emit(SettingsEvent.NavigateToReferralCode)
```

(If the neighbor uses a helper other than `emit(...)`, use that same helper.)

- [ ] **Step 4: Add the callback to SettingsRoot**

In `SettingsRoot.kt`, add the parameter next to `onNavigateToChangePassword`:

```kotlin
onNavigateToReferralCode: () -> Unit,
```

and in the `ObserveAsEvents` `when`, add:

```kotlin
SettingsEvent.NavigateToReferralCode -> onNavigateToReferralCode()
```

- [ ] **Step 5: Add the Settings row**

In `SettingsScreen.kt`, inside the `settings_section_business` `SettingsSectionCard` (right after the invite `SettingsRow`), add:

```kotlin
SettingsRow(
    icon = Icons.Outlined.Redeem,
    label = stringResource(Res.string.referral_code_settings_row),
    subtitle = stringResource(Res.string.referral_code_settings_subtitle),
    onClick = { onAction(SettingsAction.OnReferralCodeClick) },
    trailing = { SettingsRowChevron() },
)
```

Add imports `stitchpad.composeapp.generated.resources.referral_code_settings_row` and `..._subtitle`. `Icons.Outlined.Redeem` is already imported (used by the gift row).

- [ ] **Step 6: Wire the route in MainScreen**

In `MainScreen.kt`, in the `composable<SettingsRoute>` `SettingsRoot(...)` call, add:

```kotlin
onNavigateToReferralCode = { navController.navigate(ReferralCodeRoute) },
```

and add a new composable entry near the other settings sub-screens (e.g. after `composable<ChangePasswordRoute>`):

```kotlin
composable<ReferralCodeRoute> {
    ReferralCodeRoot(onNavigateBack = { navController.navigateUp() })
}
```

Add imports for `ReferralCodeRoute` and `ReferralCodeRoot`.

- [ ] **Step 7: Register the ViewModel in DI**

In `ReferralModule.kt`, add the imports:

```kotlin
import com.danzucker.stitchpad.feature.referral.presentation.entry.ReferralCodeViewModel
import org.koin.core.module.dsl.viewModelOf
```

and inside the `module { ... }` block:

```kotlin
viewModelOf(::ReferralCodeViewModel)
```

(`ReferralRepository` and `ReferralPreferencesStore` are already provided — the constructor resolves.)

- [ ] **Step 8: Full build + tests + iOS compile + detekt**

Run:
```
./gradlew :composeApp:testDebugUnitTest \
          :composeApp:compileTestKotlinIosSimulatorArm64 \
          :composeApp:compileKotlinIosSimulatorArm64 detekt
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ReferralModule.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(referral): reach ReferralCode entry from Settings"
```

---

## Manual smoke test (Daniel is QA)

1. Sign in (any method). Open **Settings → Have a referral code?**
2. Enter a live code (e.g. `H2HYZNEP`) → tap **Apply code**. Expect "Referral code applied" snackbar + return to Settings.
3. In the Firestore console, confirm `referrals/{yourUid}` now exists with that `marketerId`, `attributionSource: "manual"`, and `qualificationWindowStartsAt` ≈ now.
4. Enter a bogus code (e.g. `ZZZZZZZZ`) → expect "That code wasn't recognized", stays on screen.
5. Re-enter a code after already attributing → expect success (idempotent, first-wins), no error.
6. iOS: repeat 1–2 on the iOS build (verify the screen presents and the snackbar shows).

## Out of scope (from spec)

- Post-signup nudge, Login/SignUp screen changes, server changes, iOS clipboard reader activation, showing referral status in-app.
