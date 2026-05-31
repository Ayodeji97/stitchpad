# WhatsApp Number Validation + "Confirm on WhatsApp" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden Nigerian-mobile validation on the `whatsappNumber` field (both Workshop Setup and Edit Profile) and add an optional, no-backend "Confirm on WhatsApp" pseudo-OTP that proves the number is reachable on WhatsApp, persisting a `whatsappConfirmed` flag.

**Architecture:** Tighten the existing hand-rolled `validateNigerianMobileE164` (no new dependency, iOS-safe). Add a shared 4-digit code generator + a shared `WhatsAppConfirmUiState` + a stateless `WhatsAppConfirmRow` composable used by both screens. Each ViewModel gains identical confirm actions/state and emits a `LaunchWhatsAppConfirm` event that the Root resolves into a `wa.me` deep link via the existing `WhatsAppLauncher`. Persist `whatsappConfirmed` through `User`/`UserDto`/mapper/repository.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI (State/Action/Event + ViewModel, Root/Screen split), Koin, GitLive Firestore, JUnit5 + Turbine + AssertK + UnconfinedTestDispatcher.

**Spec:** `docs/superpowers/specs/2026-05-31-whatsapp-number-confirm-design.md`

**Honest scope reminder (from spec):** this proves WhatsApp *reachability*, not *ownership*. UI copy says "WhatsApp confirmed", never "Verified owner". Confirmation is **optional** — a valid-format number still saves unconfirmed.

**Conventions to honor:** apostrophes in strings.xml as `&apos;` (never `\'`); no `String.format` (use `padStart` / compose-resources `%1$s`); run iOS compile before declaring done; every Screen composable has a `@Preview`; aggregation/pure logic in helpers, not inlined.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCode.kt` — pure 4-digit code generator + match helper
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/WhatsAppConfirmUiState.kt` — shared confirm sub-state (shared UI utility, per CLAUDE.md `core/presentation/`)
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/WhatsAppConfirmRow.kt` — stateless composable + `@Preview`s
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCodeTest.kt`

**Modify:**
- `core/sharing/PhoneNormaliser.kt` — harden `validateNigerianMobileE164`
- `core/domain/model/User.kt`, `core/data/dto/UserDto.kt`, `core/data/mapper/UserMapper.kt`
- `core/domain/repository/UserRepository.kt`, `core/data/repository/FirebaseUserRepository.kt`
- `feature/onboarding/presentation/workshop/` — `WorkshopSetupState.kt`, `WorkshopSetupAction.kt`, `WorkshopSetupEvent.kt`, `WorkshopSetupViewModel.kt`, `WorkshopSetupScreen.kt` (Screen + Root)
- `feature/settings/presentation/editprofile/` — `EditProfileState.kt`, `EditProfileAction.kt`, `EditProfileEvent.kt`, `EditProfileViewModel.kt`, `EditProfileScreen.kt`, `EditProfileRoot.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- Tests: `PhoneNormaliserTest.kt`, `WorkshopSetupViewModelTest.kt`, an EditProfile VM test, and the test `FakeUserRepository`

**Koin:** no changes — both VMs use the explicit `viewModel { … }` lambda form (`AuthModule.kt:44`, `SettingsModule.kt`), so the new defaulted `confirmCodeGenerator: () -> String` param falls through to its default in production and is overridden in tests.

---

## Task 1: Harden Nigerian-mobile validation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliser.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliserTest.kt`

- [ ] **Step 1: Add failing tests for the tightened validator**

Append these tests inside the existing `PhoneNormaliserTest` class body:

```kotlin
@Test
fun validateNigerianMobileE164_acceptsRealMobilePrefixes() {
    listOf(
        "08031234567",   // MTN 80
        "0703 123 4567", // MTN 70
        "08101234567",   // MTN 81
        "09031234567",   // 90
        "09161234567",   // 91
        "07051234567",   // Glo 70
        "+2348141234567",
        "2348171234567",
    ).forEach { assertThat(validateNigerianMobileE164(it)).isTrue() }
}

@Test
fun validateNigerianMobileE164_rejectsNonMobileAndGarbage() {
    listOf(
        "23400000000000",  // leading 0 after 234 → not [789]
        "+234 200 000 0000", // 20x is not a mobile lead
        "08231234567",     // second digit 2 → not [01]
        "0123456789",      // landline-ish, no 234 mobile shape
        "0803123",         // too short
        "080312345678",    // too long (11 subscriber digits)
        "",
    ).forEach { assertThat(validateNigerianMobileE164(it)).isFalse() }
}
```

Ensure these imports exist at the top of the test file (add any missing):

```kotlin
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import assertk.assertThat
import assertk.assertions.isTrue
import assertk.assertions.isFalse
import kotlin.test.Test
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*PhoneNormaliserTest*"`
Expected: FAIL — `validateNigerianMobileE164_rejectsNonMobileAndGarbage` fails because the current implementation accepts `23400000000000` and `+234 200…` (it only checks "13 digits starting with 234").

- [ ] **Step 3: Tighten the implementation**

Replace the existing `validateNigerianMobileE164` function in `PhoneNormaliser.kt` with:

```kotlin
// Nigerian mobile NSN leading shape: first digit 7/8/9, second digit 0/1,
// then 8 more digits. Covers operator blocks 70x/71x/80x/81x/90x/91x
// (MTN/Glo/Airtel/9mobile/etc.) and rejects landlines, 20x, and +234 000…
// It is deliberately broad (does not enumerate every assigned block) — the
// "Confirm on WhatsApp" round-trip is the backstop for well-formed fakes,
// since they will not be reachable on WhatsApp.
private val NIGERIAN_MOBILE_E164 = Regex("^234[789][01]\\d{8}$")

/**
 * Returns true iff [raw] normalises to a Nigerian mobile number in E.164 form
 * (234 + a 10-digit subscriber number whose leading pair matches `[789][01]`).
 */
fun validateNigerianMobileE164(raw: String): Boolean {
    val normalised = normaliseNigerianPhone(raw)
    return NIGERIAN_MOBILE_E164.matches(normalised)
}
```

Note: the existing constant `EXPECTED_NIGERIAN_E164_LENGTH` may now be unused — if detekt flags it, delete it.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*PhoneNormaliserTest*"`
Expected: PASS (all old + new cases).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliser.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PhoneNormaliserTest.kt
git commit -m "feat(validation): tighten Nigerian mobile E.164 check to [789][01] shape"
```

---

## Task 2: Shared 4-digit confirm-code helper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCode.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCodeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `WhatsAppConfirmCodeTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isTrue
import kotlin.test.Test

class WhatsAppConfirmCodeTest {

    @Test
    fun defaultWhatsAppConfirmCode_isAlwaysFourDigits() {
        repeat(500) {
            val code = defaultWhatsAppConfirmCode()
            assertThat(code).hasLength(4)
            assertThat(code.all { it.isDigit() }).isTrue()
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "*WhatsAppConfirmCodeTest*"`
Expected: FAIL — `defaultWhatsAppConfirmCode` is unresolved.

- [ ] **Step 3: Implement the helper**

Create `WhatsAppConfirmCode.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import kotlin.random.Random

/**
 * Generates the 4-digit code shown to the user inside WhatsApp during the
 * "Confirm on WhatsApp" round-trip. Always zero-padded to 4 chars.
 *
 * padStart (NOT String.format — JVM-only, breaks iOS native). Injected into
 * the ViewModels as `() -> String` so tests can make it deterministic.
 */
fun defaultWhatsAppConfirmCode(): String =
    Random.nextInt(0, 10_000).toString().padStart(4, '0')
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:jvmTest --tests "*WhatsAppConfirmCodeTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCode.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/WhatsAppConfirmCodeTest.kt
git commit -m "feat(sharing): add 4-digit WhatsApp confirm-code generator"
```

---

## Task 3: Persist `whatsappConfirmed` through the data layer

**Files:**
- Modify: `core/domain/model/User.kt`, `core/data/dto/UserDto.kt`, `core/data/mapper/UserMapper.kt`
- Modify: `core/domain/repository/UserRepository.kt`, `core/data/repository/FirebaseUserRepository.kt`
- Modify: test `FakeUserRepository` (see Step 6)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapperTest.kt` (create if absent)

- [ ] **Step 1: Write the failing mapper test**

If `UserMapperTest.kt` does not exist, create it with this; if it exists, add the test method and reuse its imports:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import assertk.assertThat
import assertk.assertions.isTrue
import assertk.assertions.isFalse
import kotlin.test.Test

class UserMapperTest {

    @Test
    fun whatsappConfirmed_roundTripsBothDirections() {
        val dto = UserDto(id = "u1", whatsappNumber = "+2348031234567", whatsappConfirmed = true)
        val user = dto.toUser()
        assertThat(user.whatsappConfirmed).isTrue()

        val backToDto = user.toUserDto()
        assertThat(backToDto.whatsappConfirmed).isTrue()
    }

    @Test
    fun whatsappConfirmed_defaultsFalseWhenAbsent() {
        assertThat(UserDto(id = "u2").toUser().whatsappConfirmed).isFalse()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "*UserMapperTest*"`
Expected: FAIL — `whatsappConfirmed` is not a member of `User`/`UserDto`.

- [ ] **Step 3: Add the field to domain, DTO, and mapper**

In `User.kt`, add after `whatsappNumber`:

```kotlin
    /**
     * True once the tailor passed the "Confirm on WhatsApp" round-trip for the
     * current [whatsappNumber]. Proves WhatsApp *reachability*, not ownership.
     * Resets to false whenever the number is edited.
     */
    val whatsappConfirmed: Boolean = false,
```

In `UserDto.kt`, add after `legacyWhatsappNumber`:

```kotlin
    @SerialName("whatsappConfirmed")
    val whatsappConfirmed: Boolean = false,
```

In `UserMapper.kt`, add `whatsappConfirmed = whatsappConfirmed,` to **both** `toUser()` and `toUserDto()` builders.

- [ ] **Step 4: Run to verify the mapper test passes**

Run: `./gradlew :composeApp:jvmTest --tests "*UserMapperTest*"`
Expected: PASS.

- [ ] **Step 5: Thread `whatsappConfirmed` through the repository**

In `UserRepository.kt`, add a trailing defaulted param to **both** methods:

```kotlin
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
        bankName: String? = null,
        bankAccountName: String? = null,
        bankAccountNumber: String? = null,
        whatsappConfirmed: Boolean = false,
    ): EmptyResult<DataError.Network>
```

```kotlin
    @Suppress("LongParameterList")
    suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?,
        bankName: String? = null,
        bankAccountName: String? = null,
        bankAccountNumber: String? = null,
        whatsappConfirmed: Boolean = false,
    ): EmptyResult<DataError.Network>
```

In `FirebaseUserRepository.kt`:

- Add the same `whatsappConfirmed: Boolean = false,` param to the `createUserProfile` override. After the line `whatsappNumber?.let { data["whatsapp"] = it }`, add:

```kotlin
            // Boolean (not null-guarded): always reflects the current confirm state.
            // The form layer sends false when there is no number or it was edited.
            data["whatsappConfirmed"] = whatsappConfirmed
```

- Add the same param to the `updateProfile` override. After the line `data["whatsapp"] = whatsappNumber ?: FieldValue.delete`, add:

```kotlin
            data["whatsappConfirmed"] = whatsappConfirmed
```

- [ ] **Step 6: Update the test `FakeUserRepository`**

Find it: `grep -rln "class FakeUserRepository" composeApp/src/commonTest`. Add the matching `whatsappConfirmed: Boolean = false,` trailing param to its `createUserProfile` and `updateProfile` overrides so the interface still compiles. If the fake records call args (e.g. a `lastUpdateProfileWhatsappConfirmed` field or a captured-args object), store `whatsappConfirmed` too so Task 6/7 VM tests can assert on it; otherwise just accept and ignore it.

- [ ] **Step 7: Compile + run the data-layer tests**

Run: `./gradlew :composeApp:jvmTest --tests "*UserMapperTest*"`
Expected: PASS, and the project still compiles (`./gradlew :composeApp:compileKotlinJvm`).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt \
        composeApp/src/commonTest
git commit -m "feat(data): persist whatsappConfirmed flag on the user doc"
```

---

## Task 4: Shared confirm sub-state

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/WhatsAppConfirmUiState.kt`

- [ ] **Step 1: Create the data class**

```kotlin
package com.danzucker.stitchpad.core.presentation

import org.jetbrains.compose.resources.StringResource

/**
 * Shared UI state for the optional "Confirm on WhatsApp" round-trip, embedded
 * in both WorkshopSetupState and EditProfileState so the two screens never drift.
 *
 * [confirmed] is persisted (mirrors User.whatsappConfirmed). [code]/[input]/
 * [promptVisible]/[error] are session-only and reset on dismiss, success, or
 * any edit to the WhatsApp number.
 */
data class WhatsAppConfirmUiState(
    val confirmed: Boolean = false,
    val code: String? = null,
    val input: String = "",
    val promptVisible: Boolean = false,
    val error: StringResource? = null,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/WhatsAppConfirmUiState.kt
git commit -m "feat(presentation): add shared WhatsAppConfirmUiState"
```

---

## Task 5: String resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the strings**

Add these near the existing `workshop_whatsapp_*` block (around line 1078). Apostrophes use `&apos;`; the code placeholder uses `%1$s`:

```xml
    <string name="whatsapp_confirm_cta">Confirm on WhatsApp</string>
    <string name="whatsapp_confirmed_badge">WhatsApp confirmed</string>
    <string name="whatsapp_confirm_instructions">Open WhatsApp, read the 4-digit code in the message we drafted, then enter it below. You don&apos;t need to send the message.</string>
    <string name="whatsapp_confirm_input_label">Enter the 4-digit code</string>
    <string name="whatsapp_confirm_message">StitchPad confirmation code: %1$s — you can delete this message.</string>
    <string name="whatsapp_confirm_error_mismatch">That code doesn&apos;t match. Check WhatsApp and try again.</string>
```

- [ ] **Step 2: Generate the resource accessors**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: SUCCESS — `Res.string.whatsapp_confirm_cta` etc. become available. (If the task name differs locally, a normal `:composeApp:compileKotlinJvm` after a Kotlin file references them also regenerates accessors.)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(strings): add WhatsApp confirm copy"
```

---

## Task 6: Workshop Setup — confirm flow (state, action, event, VM)

**Files:**
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupState.kt`
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupAction.kt`
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt`
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt`

- [ ] **Step 1: Add state, action, and event members**

In `WorkshopSetupState.kt`: add the import and field.

```kotlin
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
```
```kotlin
    val whatsappConfirm: WhatsAppConfirmUiState = WhatsAppConfirmUiState(),
```

In `WorkshopSetupAction.kt`, add:

```kotlin
    data object OnConfirmWhatsAppClick : WorkshopSetupAction
    data class OnConfirmCodeChange(val value: String) : WorkshopSetupAction
    data object OnDismissConfirm : WorkshopSetupAction
```

In `WorkshopSetupEvent.kt`, add:

```kotlin
    data class LaunchWhatsAppConfirm(val phoneE164: String, val code: String) : WorkshopSetupEvent
```

- [ ] **Step 2: Write failing ViewModel tests**

Add to `WorkshopSetupViewModelTest.kt`. First, make the generator deterministic by passing it explicitly when building the VM under test — update the `setup()` construction to:

```kotlin
    viewModel = WorkshopSetupViewModel(
        fakeUserRepository, fakeAuth, onboardingPreferences,
        confirmCodeGenerator = { "1234" },
    )
```

Then add:

```kotlin
@Test
fun confirmFlow_correctCode_marksConfirmedAndEmitsLaunch() = runTest {
    viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
    viewModel.events.test {
        viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
        val event = awaitItem()
        assertThat(event).isInstanceOf(WorkshopSetupEvent.LaunchWhatsAppConfirm::class)
        val launch = event as WorkshopSetupEvent.LaunchWhatsAppConfirm
        assertThat(launch.phoneE164).isEqualTo("+2348031234567")
        assertThat(launch.code).isEqualTo("1234")
        cancelAndIgnoreRemainingEvents()
    }
    assertThat(viewModel.state.value.whatsappConfirm.promptVisible).isTrue()

    viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
    val confirm = viewModel.state.value.whatsappConfirm
    assertThat(confirm.confirmed).isTrue()
    assertThat(confirm.promptVisible).isFalse()
    assertThat(confirm.error).isNull()
}

@Test
fun confirmFlow_wrongCode_setsErrorNotConfirmed() = runTest {
    viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
    viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
    viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("9999"))
    val confirm = viewModel.state.value.whatsappConfirm
    assertThat(confirm.confirmed).isFalse()
    assertThat(confirm.error).isNotNull()
}

@Test
fun editingNumber_resetsConfirmed() = runTest {
    viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
    viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
    viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
    assertThat(viewModel.state.value.whatsappConfirm.confirmed).isTrue()

    viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234568"))
    assertThat(viewModel.state.value.whatsappConfirm.confirmed).isFalse()
}

@Test
fun confirmClick_invalidNumber_setsWhatsappErrorAndNoPrompt() = runTest {
    viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("123"))
    viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
    assertThat(viewModel.state.value.whatsappError).isNotNull()
    assertThat(viewModel.state.value.whatsappConfirm.promptVisible).isFalse()
}
```

Ensure imports: `assertk.assertions.isInstanceOf`, `isEqualTo`, `isTrue`, `isFalse`, `isNull`, `isNotNull`; `app.cash.turbine.test`; `kotlinx.coroutines.test.runTest`.

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*WorkshopSetupViewModelTest*"`
Expected: FAIL — `confirmCodeGenerator` param and the new actions/events don't exist yet.

- [ ] **Step 4: Implement the VM**

In `WorkshopSetupViewModel.kt`:

Add imports:
```kotlin
import com.danzucker.stitchpad.core.sharing.defaultWhatsAppConfirmCode
import stitchpad.composeapp.generated.resources.whatsapp_confirm_error_mismatch
```

Add the constructor param (last, with default — keeps `AuthModule.kt:44` unchanged):
```kotlin
    private val confirmCodeGenerator: () -> String = ::defaultWhatsAppConfirmCode,
```

Replace the `OnWhatsAppNumberChange` branch to also reset confirm:
```kotlin
            is WorkshopSetupAction.OnWhatsAppNumberChange ->
                _state.update {
                    it.copy(
                        whatsappNumber = capWhatsAppDigits(action.raw),
                        whatsappError = null,
                        whatsappConfirm = it.whatsappConfirm.copy(
                            confirmed = false, promptVisible = false,
                            code = null, input = "", error = null,
                        ),
                    )
                }
```

Add new branches to the `when (action)`:
```kotlin
            WorkshopSetupAction.OnConfirmWhatsAppClick -> onConfirmWhatsAppClick()
            is WorkshopSetupAction.OnConfirmCodeChange -> onConfirmCodeChange(action.value)
            WorkshopSetupAction.OnDismissConfirm -> _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        promptVisible = false, input = "", error = null,
                    )
                )
            }
```

Add the handlers (e.g. below `validateWhatsAppNumber`):
```kotlin
    private fun onConfirmWhatsAppClick() {
        val raw = _state.value.whatsappNumber.trim()
        val withCountry = applyImpliedNigerianCountryCode(raw)
        if (!validateNigerianMobileE164(withCountry)) {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            return
        }
        val code = confirmCodeGenerator()
        val phoneE164 = "+" + normaliseNigerianPhone(withCountry)
        _state.update {
            it.copy(
                whatsappConfirm = it.whatsappConfirm.copy(
                    code = code, input = "", promptVisible = true, error = null,
                )
            )
        }
        viewModelScope.launch {
            _events.send(WorkshopSetupEvent.LaunchWhatsAppConfirm(phoneE164, code))
        }
    }

    private fun onConfirmCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(CONFIRM_CODE_LENGTH)
        _state.update {
            it.copy(whatsappConfirm = it.whatsappConfirm.copy(input = digits, error = null))
        }
        if (digits.length == CONFIRM_CODE_LENGTH) submitConfirmCode()
    }

    private fun submitConfirmCode() {
        val confirm = _state.value.whatsappConfirm
        if (confirm.code != null && confirm.input == confirm.code) {
            _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        confirmed = true, promptVisible = false,
                        code = null, input = "", error = null,
                    )
                )
            }
        } else {
            _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        error = Res.string.whatsapp_confirm_error_mismatch,
                    )
                )
            }
        }
    }
```

In the `companion object`, add:
```kotlin
        const val CONFIRM_CODE_LENGTH = 4
```

Persist the flag in `onContinue()` — change the `createUserProfile(...)` call to include:
```kotlin
                    whatsappConfirmed = s.whatsappConfirm.confirmed && whatsappE164 != null,
```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*WorkshopSetupViewModelTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelTest.kt
git commit -m "feat(workshop): WhatsApp confirm flow in ViewModel"
```

---

## Task 7: Edit Profile — unified validation + confirm flow (state, action, event, VM)

**Files:**
- Modify: `feature/settings/presentation/editprofile/EditProfileState.kt`
- Modify: `feature/settings/presentation/editprofile/EditProfileAction.kt`
- Modify: `feature/settings/presentation/editprofile/EditProfileEvent.kt`
- Modify: `feature/settings/presentation/editprofile/EditProfileViewModel.kt`
- Test: an EditProfile VM test (mirror `EditProfileViewModelBankTest.kt`'s `buildVm` helper)

- [ ] **Step 1: Add state, action, event members**

In `EditProfileState.kt`:
- Add import `import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState`.
- Add fields:
```kotlin
    val whatsappConfirm: WhatsAppConfirmUiState = WhatsAppConfirmUiState(),
    val originalWhatsappConfirmed: Boolean = false,
```
- Include confirm changes in dirty tracking. Find the `isDirty` computed property and add this disjunct:
```kotlin
        whatsappConfirm.confirmed != originalWhatsappConfirmed ||
```

In `EditProfileAction.kt`, add:
```kotlin
    data object OnConfirmWhatsAppClick : EditProfileAction
    data class OnConfirmCodeChange(val value: String) : EditProfileAction
    data object OnDismissConfirm : EditProfileAction
```

In `EditProfileEvent.kt`, add:
```kotlin
    data class LaunchWhatsAppConfirm(val phoneE164: String, val code: String) : EditProfileEvent
```

- [ ] **Step 2: Write failing VM tests**

Create `EditProfileViewModelWhatsappTest.kt` next to `EditProfileViewModelBankTest.kt`, reusing its `buildVm` pattern but adding the generator param. Minimum coverage:

```kotlin
@Test
fun confirmFlow_correctCode_confirmsAndPersistsOnSave() = runTest {
    val repo = FakeUserRepository()
    val vm = buildVm(
        repo = repo,
        existingUser = userWith(whatsapp = "+2348031234567", confirmed = false),
        confirmCodeGenerator = { "1234" },
    )
    vm.state.test {
        awaitItem() // initial / loaded
        vm.onAction(EditProfileAction.OnConfirmWhatsAppClick)
        vm.onAction(EditProfileAction.OnConfirmCodeChange("1234"))
        assertThat(awaitItemUntil { it.whatsappConfirm.confirmed }).isNotNull()
        cancelAndIgnoreRemainingEvents()
    }
    vm.onAction(EditProfileAction.OnSaveClick)
    // assert the fake captured whatsappConfirmed = true (see Task 3 Step 6)
}

@Test
fun unifiedValidation_rejectsNumberOldCheckAccepted() = runTest {
    val repo = FakeUserRepository()
    val vm = buildVm(repo, existingUser = userWith(whatsapp = ""))
    vm.onAction(EditProfileAction.OnWhatsappChange("2000000000")) // 10 digits, old check passed
    vm.onAction(EditProfileAction.OnWhatsappBlur)
    assertThat(vm.state.value.whatsappError).isNotNull()
}

@Test
fun editingNumber_resetsConfirmed() = runTest {
    val repo = FakeUserRepository()
    val vm = buildVm(repo, existingUser = userWith(whatsapp = "+2348031234567", confirmed = true),
        confirmCodeGenerator = { "1234" })
    vm.onAction(EditProfileAction.OnWhatsappChange("8031234568"))
    assertThat(vm.state.value.whatsappConfirm.confirmed).isFalse()
}
```

Provide local helpers `userWith(...)` (builds a `User`) and, if not already present, `awaitItemUntil { predicate }` (collect until predicate true). Match the exact `buildVm` signature already in `EditProfileViewModelBankTest.kt`, extended with `confirmCodeGenerator: () -> String = { "0000" }` forwarded to the VM constructor.

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*EditProfileViewModelWhatsappTest*"`
Expected: FAIL — new param/actions/validation not present.

- [ ] **Step 4: Implement the VM**

In `EditProfileViewModel.kt`:

Add imports:
```kotlin
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
import com.danzucker.stitchpad.core.sharing.applyImpliedNigerianCountryCode
import com.danzucker.stitchpad.core.sharing.normaliseNigerianPhone
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import com.danzucker.stitchpad.core.sharing.defaultWhatsAppConfirmCode
import stitchpad.composeapp.generated.resources.whatsapp_confirm_error_mismatch
```

Add the constructor param (last, with default — keeps `SettingsModule.kt` unchanged):
```kotlin
    private val confirmCodeGenerator: () -> String = ::defaultWhatsAppConfirmCode,
```

Replace `validateWhatsapp()` to use the unified validator:
```kotlin
    private fun validateWhatsapp(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) {
            _state.update { it.copy(whatsappError = null) }
            return true
        }
        val withCountry = applyImpliedNigerianCountryCode(raw)
        return if (validateNigerianMobileE164(withCountry)) {
            _state.update { it.copy(whatsappError = null) }
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_format) }
            false
        }
    }
```

Replace the `OnWhatsappChange` branch to also reset confirm:
```kotlin
            is EditProfileAction.OnWhatsappChange -> _state.update {
                val filtered = action.value.filter { c -> c.isDigit() || c in "+- ()" }
                    .take(MAX_PHONE_DIGITS + 5)
                it.copy(
                    whatsappNumber = filtered,
                    whatsappError = null,
                    whatsappConfirm = it.whatsappConfirm.copy(
                        confirmed = false, promptVisible = false,
                        code = null, input = "", error = null,
                    ),
                )
            }
```

Add the new action branches:
```kotlin
            EditProfileAction.OnConfirmWhatsAppClick -> onConfirmWhatsAppClick()
            is EditProfileAction.OnConfirmCodeChange -> onConfirmCodeChange(action.value)
            EditProfileAction.OnDismissConfirm -> _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        promptVisible = false, input = "", error = null,
                    )
                )
            }
```

Add the handlers (copy the three private functions `onConfirmWhatsAppClick`, `onConfirmCodeChange`, `submitConfirmCode` verbatim from Task 6 Step 4, but emit `EditProfileEvent.LaunchWhatsAppConfirm` and use the `emit(...)` helper this VM already uses for events; and use `Res.string.error_whatsapp_format` in the invalid branch to match this screen's existing copy). Add `private const val CONFIRM_CODE_LENGTH = 4` to this file's companion/top-level consts if not already shared.

In `loadCurrentProfile()`, after `val whatsapp = firestoreUser?.whatsappNumber.orEmpty()`, add:
```kotlin
            val whatsappConfirmed = firestoreUser?.whatsappConfirmed ?: false
```
and in the `_state.update { it.copy(...) }` that loads the profile, add:
```kotlin
                    whatsappConfirm = WhatsAppConfirmUiState(confirmed = whatsappConfirmed),
                    originalWhatsappConfirmed = whatsappConfirmed,
```

In `save()`, add to the `updateProfile(...)` call:
```kotlin
                whatsappConfirmed = current.whatsappConfirm.confirmed,
```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*EditProfileViewModel*"`
Expected: PASS (new whatsapp test + existing bank test).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/
git commit -m "feat(edit-profile): unify WhatsApp validation + add confirm flow"
```

---

## Task 8: Shared `WhatsAppConfirmRow` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/WhatsAppConfirmRow.kt`

- [ ] **Step 1: Implement the stateless composable + previews**

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.whatsapp_confirm_cta
import stitchpad.composeapp.generated.resources.whatsapp_confirm_input_label
import stitchpad.composeapp.generated.resources.whatsapp_confirm_instructions
import stitchpad.composeapp.generated.resources.whatsapp_confirmed_badge

/**
 * Stateless "Confirm on WhatsApp" affordance rendered under the WhatsApp field
 * on both Workshop Setup and Edit Profile. Only shown when [numberValid] is true.
 * Proves WhatsApp reachability, not ownership — copy says "WhatsApp confirmed".
 */
@Composable
fun WhatsAppConfirmRow(
    state: WhatsAppConfirmUiState,
    numberValid: Boolean,
    onConfirmClick: () -> Unit,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!numberValid && !state.confirmed) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.confirmed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.whatsapp_confirmed_badge),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            state.promptVisible -> {
                Text(
                    text = stringResource(Res.string.whatsapp_confirm_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(Res.string.whatsapp_confirm_input_label)) },
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> TextButton(onClick = onConfirmClick) {
                Text(stringResource(Res.string.whatsapp_confirm_cta))
            }
        }
    }
}

@Preview
@Composable
private fun WhatsAppConfirmRowIdlePreview() {
    StitchPadTheme { WhatsAppConfirmRow(WhatsAppConfirmUiState(), true, {}, {}) }
}

@Preview
@Composable
private fun WhatsAppConfirmRowPromptingPreview() {
    StitchPadTheme {
        WhatsAppConfirmRow(WhatsAppConfirmUiState(promptVisible = true, input = "12"), true, {}, {})
    }
}

@Preview
@Composable
private fun WhatsAppConfirmRowConfirmedPreview() {
    StitchPadTheme {
        WhatsAppConfirmRow(WhatsAppConfirmUiState(confirmed = true), true, {}, {})
    }
}
```

Note: verify the theme composable name/import (`StitchPadTheme`) and the `Preview` import the repo uses (`org.jetbrains.compose.ui.tooling.preview.Preview` vs `androidx.compose.ui.tooling.preview.Preview`) by matching a sibling file in `ui/components/`. Use whichever that file uses.

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/WhatsAppConfirmRow.kt
git commit -m "feat(ui): add WhatsAppConfirmRow component with previews"
```

---

## Task 9: Wire into Workshop Setup screen + Root

**Files:**
- Modify: `feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt` (Screen + Root in same file)

- [ ] **Step 1: Render the row under the WhatsApp field**

In `WorkshopSetupScreen` (stateless), directly below the `AuthTextField` block for WhatsApp (around lines 285–297), add:

```kotlin
            WhatsAppConfirmRow(
                state = state.whatsappConfirm,
                numberValid = state.whatsappError == null && state.whatsappNumber.isNotBlank(),
                onConfirmClick = { onAction(WorkshopSetupAction.OnConfirmWhatsAppClick) },
                onCodeChange = { onAction(WorkshopSetupAction.OnConfirmCodeChange(it)) },
            )
```
Add import `import com.danzucker.stitchpad.ui.components.WhatsAppConfirmRow`.

- [ ] **Step 2: Handle the launch event in `WorkshopSetupRoot`**

Add a launcher and handle the new event. Imports:
```kotlin
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.whatsapp_confirm_message
```
In the Root body (near `val scope = rememberCoroutineScope()`):
```kotlin
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
```
In the `ObserveAsEvents(viewModel.events) { event -> when (event) { … } }` block, add:
```kotlin
            is WorkshopSetupEvent.LaunchWhatsAppConfirm -> scope.launch {
                val message = getString(Res.string.whatsapp_confirm_message, event.code)
                whatsAppLauncher.launch(event.phoneE164, message)
            }
```
Ensure `Res` is imported in this file (it already references resources; if not, `import stitchpad.composeapp.generated.resources.Res`).

- [ ] **Step 3: Compile + verify exhaustiveness**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: SUCCESS — the `when (event)` must now handle `LaunchWhatsAppConfirm` (compiler enforces exhaustive sealed `when`).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt
git commit -m "feat(workshop): wire WhatsAppConfirmRow + launch event into screen"
```

---

## Task 10: Wire into Edit Profile screen + Root

**Files:**
- Modify: `feature/settings/presentation/editprofile/EditProfileScreen.kt`
- Modify: `feature/settings/presentation/editprofile/EditProfileRoot.kt`

- [ ] **Step 1: Render the row under the WhatsApp `ProfileTextField`**

Below the `ProfileTextField` for whatsapp (lines 260–269), add:
```kotlin
            WhatsAppConfirmRow(
                state = state.whatsappConfirm,
                numberValid = state.whatsappError == null && state.whatsappNumber.isNotBlank(),
                onConfirmClick = { onAction(EditProfileAction.OnConfirmWhatsAppClick) },
                onCodeChange = { onAction(EditProfileAction.OnConfirmCodeChange(it)) },
            )
```
Add import `import com.danzucker.stitchpad.ui.components.WhatsAppConfirmRow`.

- [ ] **Step 2: Handle the launch event in `EditProfileRoot`**

Add the launcher + event handling, mirroring Task 9 Step 2 but for `EditProfileEvent.LaunchWhatsAppConfirm`:
```kotlin
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
```
In the `ObserveAsEvents(viewModel.events)` `when (event)`:
```kotlin
            is EditProfileEvent.LaunchWhatsAppConfirm -> scope.launch {
                val message = getString(Res.string.whatsapp_confirm_message, event.code)
                whatsAppLauncher.launch(event.phoneE164, message)
            }
```
Add the same imports as Task 9 Step 2 (`WhatsAppLauncher`, `koinInject`, `getString`, `whatsapp_confirm_message`, `Res`). If `EditProfileRoot` has no `rememberCoroutineScope()`, add `val scope = rememberCoroutineScope()`.

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: SUCCESS — exhaustive `when (event)` now covers `LaunchWhatsAppConfirm`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileRoot.kt
git commit -m "feat(edit-profile): wire WhatsAppConfirmRow + launch event into screen"
```

---

## Task 11: Debug affordance (reveal code without app-switch)

**Files:**
- Modify: `WhatsAppConfirmRow.kt` (guarded) OR add a debug overlay. Keep minimal.

- [ ] **Step 1: Surface the generated code in debug builds only**

The cleanest no-new-expect/actual approach: pass an optional `debugCode: String? = null` to `WhatsAppConfirmRow` and render it under the input when non-null. Each Screen passes `state.whatsappConfirm.code.takeIf { BuildKonfig.DEBUG }` (use the project's existing debug flag — check how the debug menu gates itself, e.g. a `BuildKonfig`/`isDebug` constant; reuse that exact symbol). When `promptVisible`, render:
```kotlin
                debugCode?.let {
                    Text(
                        "DEBUG code: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
```
If the repo's debug flag is not trivially reachable in `commonMain`, SKIP this task and note it in the PR as a follow-up rather than introducing a new expect/actual just for QA. (Per the debug-menu-per-feature convention, document the decision either way.)

- [ ] **Step 2: Compile + commit (only if implemented)**

Run: `./gradlew :composeApp:compileKotlinJvm`
```bash
git add -A && git commit -m "chore(debug): reveal WhatsApp confirm code in debug builds"
```

---

## Task 12: Full verification (Android + iOS + detekt) and PR prep

- [ ] **Step 1: Full JVM test suite**

Run: `./gradlew :composeApp:jvmTest`
Expected: PASS, no regressions.

- [ ] **Step 2: detekt**

Run: `./gradlew detekt`
Expected: no new issues. If `CyclomaticComplexMethod`/`LongMethod` trips on the expanded `onAction`, add the same `@Suppress` already used on those methods.

- [ ] **Step 3: Android assemble**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: iOS compile (mandatory — regex, padStart, openURL all touch native)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (This is the gate from `[[feedback_kmp_jvm_only_apis]]` / `[[feedback_kotlin_native_epoch_days]]`.)

- [ ] **Step 5: Manual smoke test (Daniel is QA — include in PR description)**

1. Onboarding → Workshop Setup: enter a valid Nigerian WhatsApp number → tap **Confirm on WhatsApp** → read the 4-digit code in WhatsApp → return → enter it → "WhatsApp confirmed ✓".
2. Edit a digit → chip disappears (confirmed reset).
3. Enter a non-WhatsApp / fake-but-well-formed number → WhatsApp shows "isn't on WhatsApp", no code retrievable → cannot confirm; number still saves.
4. Enter an invalid-format number (e.g. `+234 200…`) → inline validation error, no confirm affordance.
5. Repeat 1–2 in Settings → Edit Profile; confirm the flag round-trips through Firestore (re-open the screen → chip persists).
6. iOS + Android, light + dark.

- [ ] **Step 6: Open the PR**

```bash
git push -u origin HEAD
gh pr create --fill
```
Per the review-rotation convention, run BOTH Cursor and `codex review` before merge.

---

## Self-Review (completed by plan author)

**Spec coverage:**
- Hardened validation → Task 1. Unify Edit Profile onto it → Task 7 Step 4. ✓
- Pseudo-OTP (4-digit, optional, wa.me prefill, auto-verify, mismatch error) → Tasks 2, 6, 7, 8. ✓
- Persist `whatsappConfirmed` (+ reset on edit) → Task 3 (data), Tasks 6/7 (reset + save). ✓
- Shared component + previews → Task 8; wired both screens → Tasks 9, 10. ✓
- Strings (`&apos;`, `%1$s`, no `String.format`) → Task 5. ✓
- Injected `() -> String`, no Koin churn → Tasks 6/7 (defaulted last param). ✓
- Debug affordance → Task 11 (with documented skip path). ✓
- iOS compile + smoke test + review rotation → Task 12. ✓
- Honest copy ("WhatsApp confirmed", never "Verified owner") → Tasks 5, 8. ✓

**Type consistency:** `WhatsAppConfirmUiState` fields, `confirmCodeGenerator: () -> String`, `LaunchWhatsAppConfirm(phoneE164, code)`, and `CONFIRM_CODE_LENGTH = 4` are used identically across Tasks 4/6/7/8/9/10.

**Open verification points flagged inline for the implementer (not placeholders):** exact `Preview`/`StitchPadTheme` import (match a sibling in `ui/components/`), the repo's debug flag symbol for Task 11, and the `FakeUserRepository` capture shape for asserting the persisted flag.
