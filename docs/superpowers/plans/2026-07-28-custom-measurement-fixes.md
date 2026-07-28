# Custom Measurement Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the measurement form's cursor-jump bug, confirm custom-field additions with a snackbar, and give the step indicator distinct current/completed/unvisited states plus a section-name heading.

**Architecture:** Purely presentation-layer changes in `feature/measurement/presentation/form/`. A new caret-preserving `TextFieldValue` helper fixes the cursor bug across all four text inputs. A pure message-builder + a nullable `UiText` on state (mirroring the existing `errorMessage` snackbar path) drives the confirmation. The step indicator gains three dot states and reuses the detail screen's section-title resolver, extracted to a shared composable.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI, Koin, `kotlin.test` + Turbine + AssertK (commonTest).

## Global Constraints

- **String resources:** never hardcode user-facing strings; use compose.resources. **Positional args only** (`%1$s`, `%2$s`) — non-positional `%s` is not substituted reliably.
- **No backslash escapes in strings.xml** — CMP iOS renders `\'` literally. Use `’` (U+2019) / `&apos;` and typographic quotes.
- **State lives in the ViewModel** — no `remember`/`rememberSaveable` business state in composables (Compose-internal state like `TextFieldValue` binding, `PagerState` is allowed).
- **Every Screen composable keeps a `@Preview`.**
- **Result<T,E>** for expected failures; never throw.
- **Quality gates before "done":** `./gradlew detekt` and iOS compile `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64` (this feature touches text input + previews — JVM-only APIs and cursor behavior differ on iOS).
- **Tests:** `./gradlew :composeApp:testDebugUnitTest`. commonTest uses `kotlin.test` + Turbine + AssertK.
- **Backtick test names:** letters/digits/spaces/hyphens ONLY (iOS test-compile gate).
- **PR workflow:** we are on branch `fix/measurement-input-punctuation`; commit per task, no direct main pushes.

**Reference paths (read before starting):**
- `feature/measurement/presentation/form/MeasurementFormScreen.kt` — Root (134), name field (255), pager (300-378), `SectionProgressRow` (536), `CustomStepPill` (604), `MeasurementFieldInput` (675), `MeasurementTextField` (819), `CustomFieldsSection` (917, value field 1055).
- `feature/measurement/presentation/form/components/AddCustomFieldSheet.kt` — label (102), value (112).
- `feature/measurement/presentation/form/MeasurementFormViewModel.kt` — `onAction` (112), `saveCustomField` (519), `shouldSeedInitialCustomValue` (591).
- `feature/measurement/presentation/form/MeasurementFormState.kt`, `MeasurementFormAction.kt`.
- `feature/measurement/presentation/MeasurementPreview.kt` — `sanitizeMeasurementInput` (47), `isPersistableMeasurementValue` (56).
- `feature/measurement/presentation/detail/MeasurementDetailScreen.kt` — `sectionTitle` (557-568).
- `core/presentation/UiText.kt` — `DynamicString`, `StringResourceText(id, args)`, `@Composable asString()`.
- `core/domain/model/CustomerGender.kt` — `enum { FEMALE, MALE }`.

---

### Task 1: Caret-preserving text-field helper

Fixes the root cause of the cursor bug: fields bind a raw `String`, so the StateFlow round-trip resets the caret to the end. This helper holds a local `TextFieldValue` and reconciles it against the hoisted `String`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/SanitizedTextField.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/SanitizedCaretTest.kt`

**Interfaces:**
- Produces:
  - `fun sanitizedCaret(rawText: String, selectionEnd: Int, sanitize: (String) -> String): Int`
  - `@Composable fun rememberSanitizedTextFieldValue(value: String, sanitize: (String) -> String = { it }, maxLength: Int? = null, onValueChange: (String) -> Unit): Pair<TextFieldValue, (TextFieldValue) -> Unit>`

- [ ] **Step 1: Write the failing test**

Create `SanitizedCaretTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.feature.measurement.presentation.form.components.sanitizedCaret
import com.danzucker.stitchpad.feature.measurement.presentation.sanitizeMeasurementInput
import kotlin.test.Test
import kotlin.test.assertEquals

class SanitizedCaretTest {

    @Test
    fun `caret keeps position when typing a comma mid string`() {
        // "40 45" with caret after "40" (index 2), user types a comma -> "40, 45"?
        // Here the raw text already contains the inserted comma; caret is right after it.
        val raw = "40,45"
        val caret = sanitizedCaret(rawText = raw, selectionEnd = 3, sanitize = ::sanitizeMeasurementInput)
        assertEquals(3, caret)
    }

    @Test
    fun `caret does not advance past a rejected letter`() {
        // User typed "a" after "30" -> raw "30a", caret at 3. Sanitize strips the
        // letter, so the caret must land at 2 (after "30"), not 3.
        val caret = sanitizedCaret(rawText = "30a", selectionEnd = 3, sanitize = ::sanitizeMeasurementInput)
        assertEquals(2, caret)
    }

    @Test
    fun `caret clamps when selection end exceeds text length`() {
        val caret = sanitizedCaret(rawText = "16.5", selectionEnd = 99, sanitize = ::sanitizeMeasurementInput)
        assertEquals(4, caret)
    }

    @Test
    fun `identity sanitize preserves caret exactly`() {
        val caret = sanitizedCaret(rawText = "Owambe", selectionEnd = 3, sanitize = { it })
        assertEquals(3, caret)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SanitizedCaretTest*"`
Expected: FAIL — `sanitizedCaret` unresolved (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `SanitizedTextField.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Caret offset after [sanitize] runs on user input. Because [sanitize] only ever
 * REMOVES characters, the new caret is the length of the sanitized prefix up to the
 * old caret — so inserting a comma/decimal mid-string keeps the caret in place and a
 * rejected character does not nudge it forward. Pure + unit-testable.
 */
fun sanitizedCaret(
    rawText: String,
    selectionEnd: Int,
    sanitize: (String) -> String,
): Int = sanitize(rawText.take(selectionEnd.coerceIn(0, rawText.length))).length

/**
 * Binds a hoisted [value] String to a local [TextFieldValue] so the caret survives
 * the ViewModel round-trip. A String-bound field loses its caret to the end when the
 * StateFlow pushes the value back (see the "Compose TextFieldValue cursor" note).
 * Optionally [sanitize]s input (default identity) and enforces [maxLength] by
 * rejecting the keystroke.
 *
 * @return the current [TextFieldValue] to display and the change handler for the field.
 */
@Composable
fun rememberSanitizedTextFieldValue(
    value: String,
    sanitize: (String) -> String = { it },
    maxLength: Int? = null,
    onValueChange: (String) -> Unit,
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    // Reconcile external (programmatic) changes: value seeding, gender switch, load.
    // Caret-to-end is correct here — the user is not mid-edit.
    if (tfv.text != value) {
        tfv = TextFieldValue(value, TextRange(value.length))
    }

    val onChange: (TextFieldValue) -> Unit = onChange@{ raw ->
        val sanitized = sanitize(raw.text)
        if (maxLength != null && sanitized.length > maxLength) return@onChange
        val caret = sanitizedCaret(raw.text, raw.selection.end, sanitize)
        tfv = TextFieldValue(sanitized, TextRange(caret.coerceIn(0, sanitized.length)))
        if (sanitized != value) onValueChange(sanitized)
    }
    return tfv to onChange
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SanitizedCaretTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/SanitizedTextField.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/SanitizedCaretTest.kt
git commit -m "feat(measurement): caret-preserving text-field helper

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Apply caret fix to the value fields (`MeasurementTextField`)

Route `MeasurementTextField` through the helper and move sanitization into it. This fixes both template value fields and custom value fields at once.

**Files:**
- Modify: `feature/measurement/presentation/form/MeasurementFormScreen.kt` — `MeasurementTextField` (819-906), `MeasurementFieldInput` (675-694), `CustomFieldsSection` value field (1055-1068).

**Interfaces:**
- Consumes: `rememberSanitizedTextFieldValue` (Task 1), `sanitizeMeasurementInput`.
- Produces: `MeasurementTextField(..., sanitize: (String) -> String = { it }, ...)` — new optional param.

- [ ] **Step 1: Add `sanitize` param and drive `MeasurementTextField` from the helper**

In `MeasurementTextField` (line 819), add the `sanitize` parameter (before `modifier`):

```kotlin
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    suffix: String? = null,
    sanitize: (String) -> String = { it },
    modifier: Modifier = Modifier
) {
```

Inside, after `val interactionSource = remember { MutableInteractionSource() }`, add:

```kotlin
    val (textFieldValue, onTextFieldChange) = rememberSanitizedTextFieldValue(
        value = value,
        sanitize = sanitize,
        onValueChange = onValueChange,
    )
```

Change the `BasicTextField` to bind the `TextFieldValue`:

```kotlin
        BasicTextField(
            value = textFieldValue,
            onValueChange = onTextFieldChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = textFieldValue.text,
```

(only the `value =` on `BasicTextField` and on `DecorationBox` change; the rest of `decorationBox` is untouched).

Add the import at the top of the file:

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.rememberSanitizedTextFieldValue
```

- [ ] **Step 2: Update `MeasurementFieldInput` to pass `sanitize` and drop inline sanitize**

Replace `MeasurementFieldInput` (675-694) body call with:

```kotlin
    MeasurementTextField(
        value = value,
        // Sanitization now lives inside MeasurementTextField so the caret is mapped
        // as characters are stripped. Text keyboard (not Decimal) keeps comma/space
        // keys reachable for segmented lengths ("40, 45, 56") and half sizes ("16.5").
        onValueChange = onValueChange,
        sanitize = ::sanitizeMeasurementInput,
        label = field.label,
        placeholder = "0",
        suffix = unitSuffix,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
```

- [ ] **Step 3: Update the custom value field in `CustomFieldsSection`**

Replace the `MeasurementTextField` call at 1055-1068 with:

```kotlin
                    MeasurementTextField(
                        value = fieldValues[field.id] ?: "",
                        onValueChange = { newVal -> onFieldValueChange(field.id, newVal) },
                        sanitize = ::sanitizeMeasurementInput,
                        label = "", // label rendered above by the long-pressable Text
                        placeholder = "0",
                        suffix = unitSuffix,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
```

- [ ] **Step 4: Build to verify it compiles and existing tests pass**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (no regressions; existing `MeasurementFormStateTest`, `MeasurementPreviewTest` still green).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "fix(measurement): preserve caret in measurement value fields

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Apply caret fix to the name + custom-field-sheet inputs

The three `OutlinedTextField`s (measurement name, custom field name, sheet Value) switch to the `TextFieldValue` overload driven by the helper.

**Files:**
- Modify: `feature/measurement/presentation/form/MeasurementFormScreen.kt` — name field (255-268).
- Modify: `feature/measurement/presentation/form/components/AddCustomFieldSheet.kt` — label (102-109), value (111-124).

**Interfaces:**
- Consumes: `rememberSanitizedTextFieldValue` (Task 1), `sanitizeMeasurementInput`, `MAX_LABEL_LENGTH` (30, already in `AddCustomFieldSheet.kt`).

- [ ] **Step 1: Convert the measurement name field**

In `MeasurementFormScreen.kt`, immediately before the `OutlinedTextField` at line 255, add:

```kotlin
                val (nameFieldValue, onNameFieldChange) = rememberSanitizedTextFieldValue(
                    value = state.name,
                    onValueChange = { onAction(MeasurementFormAction.OnNameChange(it)) },
                )
```

Change the field's `value`/`onValueChange`:

```kotlin
                OutlinedTextField(
                    value = nameFieldValue,
                    onValueChange = onNameFieldChange,
                    label = { Text(stringResource(Res.string.measurement_name_label)) },
                    placeholder = { Text(stringResource(Res.string.measurement_name_placeholder)) },
                    singleLine = true,
                    isError = state.name.isBlank(),
                    supportingText = if (state.name.isBlank()) {
                        { Text(stringResource(Res.string.measurement_name_required_hint)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
```

Add the import (if not already present from Task 2 — it is the same file, so it will be):

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.rememberSanitizedTextFieldValue
```

- [ ] **Step 2: Convert the custom field-name input**

In `AddCustomFieldSheet.kt`, replace the label `OutlinedTextField` (102-109) with:

```kotlin
            val (labelFieldValue, onLabelFieldChange) = rememberSanitizedTextFieldValue(
                value = draft.label,
                maxLength = MAX_LABEL_LENGTH,
                onValueChange = onLabelChange,
            )
            OutlinedTextField(
                value = labelFieldValue,
                onValueChange = onLabelFieldChange,
                label = { Text(stringResource(Res.string.custom_field_sheet_label)) },
                placeholder = { Text(stringResource(Res.string.custom_field_sheet_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
```

- [ ] **Step 3: Convert the sheet Value input**

Replace the value `OutlinedTextField` block (111-124) with:

```kotlin
            if (initial == null) {
                val (valueFieldValue, onValueFieldChange) = rememberSanitizedTextFieldValue(
                    value = draft.initialValue,
                    sanitize = ::sanitizeMeasurementInput,
                    onValueChange = onInitialValueChange,
                )
                OutlinedTextField(
                    value = valueFieldValue,
                    onValueChange = onValueFieldChange,
                    label = { Text(stringResource(Res.string.custom_field_sheet_value)) },
                    placeholder = { Text(stringResource(Res.string.custom_field_sheet_value_placeholder)) },
                    suffix = { Text(unitSuffix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
```

Add imports to `AddCustomFieldSheet.kt`:

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.rememberSanitizedTextFieldValue
```

(`sanitizeMeasurementInput` is already imported; `KeyboardOptions`/`KeyboardType` already imported.)

- [ ] **Step 4: Build to verify it compiles and tests pass**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/AddCustomFieldSheet.kt
git commit -m "fix(measurement): preserve caret in name and custom-field sheet inputs

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: "Field added" confirmation snackbar

On create, show an informational snackbar. Same-gender/Both → `"‘X’ added"`; opposite-gender-only → `"‘X’ added to your men’s/women’s measurements"` (no action — decision (b)). Implemented via a nullable `UiText` on state, mirroring the existing `errorMessage` snackbar path so the `@Composable` resolution stays clean.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `feature/measurement/presentation/form/CustomFieldAddedMessage.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/CustomFieldAddedMessageTest.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormState.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormAction.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormViewModel.kt` — `onAction` (112-194), `saveCustomField` success branch (562-580).
- Modify: `feature/measurement/presentation/form/MeasurementFormScreen.kt` — Root (156-162).

**Interfaces:**
- Produces:
  - `fun customFieldAddedMessage(label: String, currentGender: CustomerGender?, genders: Set<CustomerGender>): UiText`
  - `MeasurementFormState.confirmationMessage: UiText?`
  - `MeasurementFormAction.OnConfirmationDismiss`
- Consumes: `UiText.StringResourceText`, `CustomerGender`.

- [ ] **Step 1: Add the string resources**

In `strings.xml`, add near the other `custom_field_*` strings:

```xml
    <string name="custom_field_added">‘%1$s’ added</string>
    <string name="custom_field_added_female">‘%1$s’ added to your women’s measurements</string>
    <string name="custom_field_added_male">‘%1$s’ added to your men’s measurements</string>
```

- [ ] **Step 2: Write the failing test for the message builder**

Create `CustomFieldAddedMessageTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.presentation.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_added
import stitchpad.composeapp.generated.resources.custom_field_added_female
import stitchpad.composeapp.generated.resources.custom_field_added_male

class CustomFieldAddedMessageTest {

    @Test
    fun `field on current gender uses the plain added message`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.FEMALE,
            genders = setOf(CustomerGender.FEMALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added, text.id)
        assertEquals("Sleeve", text.args.single())
    }

    @Test
    fun `both-gender field uses the plain added message`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.MALE,
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added, text.id)
    }

    @Test
    fun `male-only field added while on female tells the tailor it is on male`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.FEMALE,
            genders = setOf(CustomerGender.MALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added_male, text.id)
    }

    @Test
    fun `female-only field added while on male tells the tailor it is on female`() {
        val msg = customFieldAddedMessage(
            label = "Bust",
            currentGender = CustomerGender.MALE,
            genders = setOf(CustomerGender.FEMALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added_female, text.id)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomFieldAddedMessageTest*"`
Expected: FAIL — `customFieldAddedMessage` unresolved.

- [ ] **Step 4: Implement the message builder**

Create `CustomFieldAddedMessage.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_added
import stitchpad.composeapp.generated.resources.custom_field_added_female
import stitchpad.composeapp.generated.resources.custom_field_added_male

/**
 * Confirmation shown after a custom field is created. If the field shows on the
 * current gender (or on both), the tailor sees it appear immediately, so a plain
 * "added" confirmation is enough. If it is scoped to the OTHER gender only, the
 * field will not appear on this measurement — so the message says which set it
 * landed in (informational; no gender-switch action — that field is for the
 * tailor's other customers).
 */
fun customFieldAddedMessage(
    label: String,
    currentGender: CustomerGender?,
    genders: Set<CustomerGender>,
): UiText {
    val shownHere = currentGender == null || currentGender in genders
    if (shownHere) {
        return UiText.StringResourceText(custom_field_added, arrayOf(label))
    }
    // Not shown here → genders excludes the current gender. With only two genders,
    // the field is scoped to exactly the opposite one.
    val resource = if (CustomerGender.FEMALE in genders) {
        custom_field_added_female
    } else {
        custom_field_added_male
    }
    return UiText.StringResourceText(resource, arrayOf(label))
}
```

(`custom_field_added*` are imported members of `Res.string`; reference them as shown or as `Res.string.custom_field_added` — match the file's existing style.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomFieldAddedMessageTest*"`
Expected: PASS (4 tests).

- [ ] **Step 6: Add `confirmationMessage` to state and the dismiss action**

In `MeasurementFormState.kt`, add to the `MeasurementFormState` data class (next to `errorMessage`):

```kotlin
    val confirmationMessage: UiText? = null,
```

(ensure `import com.danzucker.stitchpad.core.presentation.UiText` is present — it is, for `errorMessage`.)

In `MeasurementFormAction.kt`, add:

```kotlin
    data object OnConfirmationDismiss : MeasurementFormAction
```

- [ ] **Step 7: Wire the ViewModel**

In `MeasurementFormViewModel.kt` `onAction`, next to `OnErrorDismiss` (190-192), add:

```kotlin
            MeasurementFormAction.OnConfirmationDismiss -> {
                _state.update { it.copy(confirmationMessage = null) }
            }
```

In `saveCustomField`, replace the success `_state.update` block (562-580) with:

```kotlin
            if (result is Result.Success) {
                _state.update { current ->
                    val valueToApply = initialValue.trim()
                    val shouldSeedInitialValue = shouldSeedInitialCustomValue(
                        isCreate = isCreate,
                        value = valueToApply,
                        currentGender = current.gender,
                        field = field,
                    )
                    val updatedFields = if (shouldSeedInitialValue) {
                        current.fields + (field.id to valueToApply)
                    } else {
                        current.fields
                    }
                    current.copy(
                        fields = updatedFields,
                        customFieldSheet = null,
                        // Confirm only on create; edits just close the sheet.
                        confirmationMessage = if (isCreate) {
                            customFieldAddedMessage(
                                label = trimmed,
                                currentGender = current.gender,
                                genders = genders,
                            )
                        } else {
                            current.confirmationMessage
                        },
                    )
                }
            } else {
```

(`trimmed` and `genders` are already in scope in `saveCustomField`.)

- [ ] **Step 8: Wire the snackbar in the Root composable**

In `MeasurementFormScreen.kt` Root, after the existing `errorMessage` `LaunchedEffect` (162), add:

```kotlin
    val confirmationMessage = state.confirmationMessage?.asString()
    LaunchedEffect(confirmationMessage) {
        if (confirmationMessage != null) {
            snackbarHostState.showSnackbar(confirmationMessage)
            viewModel.onAction(MeasurementFormAction.OnConfirmationDismiss)
        }
    }
```

- [ ] **Step 9: Run the full test suite + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest detekt`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/CustomFieldAddedMessage.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/CustomFieldAddedMessageTest.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(measurement): confirm custom-field additions with a snackbar

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Step indicator states + section-name heading

Give dots three distinct states (completed ✓ / current ◎ / unvisited ·) and add the section name as a page heading. Reuse the detail screen's title resolver, extracted to a shared composable.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/MeasurementSectionTitle.kt`
- Modify: `feature/measurement/presentation/detail/MeasurementDetailScreen.kt` — `sectionTitle` (557-568).
- Modify: `feature/measurement/presentation/form/MeasurementFormScreen.kt` — `SectionProgressRow` dots (555-583), pager template branch (315-316), add a preview.

**Interfaces:**
- Produces: `@Composable fun measurementSectionTitle(titleKey: String?): String`
- Consumes: `isPersistableMeasurementValue`, `Icons.Default.Check`.

- [ ] **Step 1: Extract the shared section-title resolver**

Create `MeasurementSectionTitle.kt` (copy the exact `when` from `MeasurementDetailScreen.sectionTitle`, 557-568, so no key is dropped):

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_section_title
import stitchpad.composeapp.generated.resources.section_arms
import stitchpad.composeapp.generated.resources.section_body_lengths
import stitchpad.composeapp.generated.resources.section_bust
import stitchpad.composeapp.generated.resources.section_neck_shoulders
import stitchpad.composeapp.generated.resources.section_trouser
import stitchpad.composeapp.generated.resources.section_upper_body
import stitchpad.composeapp.generated.resources.section_waist_hip

/**
 * Resolves a [MeasurementSection.titleKey] to a localized title. Shared by the
 * measurement form (page heading) and the read-only detail screen. Unknown future
 * keys degrade to the raw key rather than crash; a null key is the custom section.
 */
@Composable
fun measurementSectionTitle(titleKey: String?): String = when (titleKey) {
    "section_upper_body" -> stringResource(Res.string.section_upper_body)
    "section_body_lengths" -> stringResource(Res.string.section_body_lengths)
    "section_trouser" -> stringResource(Res.string.section_trouser)
    "section_neck_shoulders" -> stringResource(Res.string.section_neck_shoulders)
    "section_bust" -> stringResource(Res.string.section_bust)
    "section_waist_hip" -> stringResource(Res.string.section_waist_hip)
    "section_arms" -> stringResource(Res.string.section_arms)
    null -> stringResource(Res.string.custom_field_section_title)
    else -> titleKey
}
```

- [ ] **Step 2: Delegate the detail screen's resolver to the shared one**

In `MeasurementDetailScreen.kt`, replace the private `sectionTitle` body (557-568) with a delegation (keeps existing call sites unchanged):

```kotlin
@Composable
private fun sectionTitle(titleKey: String?): String =
    com.danzucker.stitchpad.feature.measurement.presentation.measurementSectionTitle(titleKey)
```

(or add an import and call `measurementSectionTitle(titleKey)` directly — match file style. If the previous per-key string imports in the detail file are now unused, remove them so detekt stays green.)

- [ ] **Step 3: Run tests to confirm the extraction is behavior-preserving**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (no behavior change; detail screen renders identical titles).

- [ ] **Step 4: Commit the resolver extraction**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/MeasurementSectionTitle.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt
git commit -m "refactor(measurement): share the section-title resolver

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Add the three-state dot to `SectionProgressRow`**

In `MeasurementFormScreen.kt`, replace the `sections.forEachIndexed { index, section -> ... }` block inside `SectionProgressRow` (555-583) with:

```kotlin
            sections.forEachIndexed { index, section ->
                val isCurrent = index == currentIndex
                // Same persistable predicate as MeasurementFormState.canSave so a dot
                // only counts as "completed" for values that will actually persist.
                val isFilled = section.fields.any { f ->
                    fields[f.key]?.let { isPersistableMeasurementValue(it) } == true
                }
                val goToSectionLabel = stringResource(Res.string.measurement_go_to_section, index + 1)
                SectionDot(
                    isCurrent = isCurrent,
                    isFilled = isFilled,
                    onClickLabel = goToSectionLabel,
                    onClick = { onJumpToSection(index) },
                )
            }
```

Add the `SectionDot` composable directly below `SectionProgressRow` (before `CustomStepPill`):

```kotlin
@Composable
private fun SectionDot(
    isCurrent: Boolean,
    isFilled: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val click = Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    when {
        // Current step = hollow ring + soft halo ("you are here"). Slightly larger
        // than the plain dots for emphasis. Shows a check inside when also filled.
        isCurrent -> Box(
            modifier = Modifier
                .size(18.dp)
                .background(primary.copy(alpha = 0.18f), CircleShape)
                .then(click),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(BorderStroke(2.dp, primary), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isFilled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(8.dp),
                    )
                }
            }
        }
        // Completed (not current) = solid dot with a check.
        isFilled -> Box(
            modifier = Modifier
                .size(14.dp)
                .background(primary, CircleShape)
                .then(click),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(9.dp),
            )
        }
        // Not visited = soft tint so unvisited dots stay visible without a muddy gray.
        else -> Box(
            modifier = Modifier
                .size(10.dp)
                .background(primary.copy(alpha = 0.3f), CircleShape)
                .then(click),
        )
    }
}
```

Add the import:

```kotlin
import androidx.compose.material.icons.filled.Check
```

(`Icons`, `BorderStroke`, `border`, `background`, `CircleShape`, `Role`, `Alignment` are already imported — `border`/`BorderStroke`/`Role` are used by `CustomStepPill`.)

- [ ] **Step 6: Add the section-name heading to the pager**

In the pager template branch, right after `val section = state.sections[pageIndex]` (316), add:

```kotlin
                            Text(
                                text = measurementSectionTitle(section.titleKey),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
```

(The enclosing `Column` uses `Arrangement.spacedBy(DesignTokens.space4)`, so the heading spaces itself from the first field. The custom page keeps its own existing "Custom" header — no change there.)

Add the import:

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.measurementSectionTitle
```

- [ ] **Step 7: Add a preview for the indicator states**

Add near the other previews at the bottom of `MeasurementFormScreen.kt` (wrap in the app theme as sibling previews do — match the existing preview style in the file):

```kotlin
@Preview
@Composable
private fun SectionProgressRowPreview() {
    StitchPadTheme {
        SectionProgressRow(
            sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE),
            currentIndex = 1, // second section = current (ring); first = completed if filled
            fields = mapOf(
                // Fill a field in the first section so it renders the completed check.
                BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE).first().fields.first().key to "16",
            ),
            customLocked = false,
            customHasData = false,
            onJumpToSection = {},
        )
    }
}
```

(Match the exact preview wrapper/theme import used by the file's existing `@Preview`s; add imports for `BodyProfileTemplate` and `CustomerGender` if not present.)

- [ ] **Step 8: Build, run tests, detekt, and iOS compile**

Run: `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileTestKotlinIosSimulatorArm64`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(measurement): distinct step-indicator states + section heading

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Manual smoke test (Daniel = QA), Android + iOS

Run after all tasks land, per the QA-smoke-tests rule:

1. **Cursor** — In a value field containing `58, 30, 45`, tap to place the caret between `30,` and `45`, type a digit. The caret stays put; the digit lands where you typed (not at the end). Repeat for the measurement name and the sheet Value field.
2. **Confirm same-gender** — On a Female measurement, add a custom field with SHOW ON = Female (or Both). Sheet closes; snackbar reads `‘<name>’ added`; the field is visible on the custom page.
3. **Confirm cross-gender** — On a Female measurement, add a field with SHOW ON = Male. Sheet closes; snackbar reads `‘<name>’ added to your men’s measurements`; the field does NOT appear on this measurement.
4. **Step indicator** — Swipe through sections: the current step shows the ring ◎, completed steps show the check ✓, unvisited stay faint. The bold section heading updates: Upper Body → Body Lengths → Trouser → (custom page keeps its "Custom" header).

## Self-review notes (traceability)

- Spec §1 cursor fix → Tasks 1-3 (helper + all four inputs). Spec §2 punctuation → satisfied by Task 2 (no functional change). Spec §3 confirmation → Task 4. Spec §4 indicator + heading → Task 5.
- Deviation from spec §3: implemented via `state.confirmationMessage: UiText?` + dismiss action rather than a channel event. Rationale: `UiText.asString()` is `@Composable`; the state+dismiss path already exists here for `errorMessage`, resolves cleanly in composition, and does not re-fire (dismissed after showing). Same one-shot UX.
- Type consistency: `customFieldAddedMessage(label, currentGender, genders)` signature identical in Task 4 definition, test, and VM call site. `rememberSanitizedTextFieldValue(value, sanitize, maxLength, onValueChange)` identical across Tasks 1-3. `measurementSectionTitle(titleKey)` identical in Task 5 definition, detail delegation, and pager call.
