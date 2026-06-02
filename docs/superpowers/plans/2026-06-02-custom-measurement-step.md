# Custom Measurements First-Class Step — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote custom measurements to a first-class step in the Add/Edit Measurements wizard, reachable from any step via an always-visible "Custom" pill in the pinned progress row.

**Architecture:** The `HorizontalPager` gains one trailing page (the custom step) at index `sections.size`. `SectionProgressRow` (already in the pinned header) gains a trailing tappable `✛ Custom` pill plus tappable dots, all firing the existing `OnSectionChange(index)` action. `CustomFieldsSection` moves off the bottom of the last default section onto the new page, with its Add button hoisted to the top. The only ViewModel change is widening the `OnNextSection` coerce bound so Next can walk onto the custom page.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI, Koin. Tests: kotlin.test + Turbine via `:composeApp:testDebugUnitTest`. Spec: `docs/superpowers/specs/2026-06-02-custom-measurement-step-design.md`.

---

## File Structure

- **Modify** `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt`
  — widen `OnNextSection` coerce bound (`sections.size - 1` → `sections.size`).
- **Modify** `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt`
  — pager `pageCount`, pager content branch for the custom page, `SectionProgressRow` pill + tappable dots, `CustomFieldsSection` Add-at-top, `SectionNavigation` total, previews.
- **Modify** `composeApp/src/commonMain/composeResources/values/strings.xml`
  — add `measurement_custom_step` label.
- **Modify** `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`
  — update the existing cap test; add custom-page navigation tests.

No new files. No State/Action members are added — `OnSectionChange(index)` already carries the target index.

---

## Task 1: Widen `OnNextSection` so Next reaches the custom page (TDD)

The custom page lives at pager index `sections.size`. Today `OnNextSection` caps at `sections.size - 1`, so Next can never reach it. This is the only VM change and is fully unit-testable.

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt:269-276`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt:92-97`

- [ ] **Step 1: Update the existing cap test and add a custom-page test**

In the test file, **replace** the existing `onNextSection_doesNotExceedLastSection` test (lines 269-276) with the two tests below. The old test asserted Next caps at `sections.size - 1`; the new behavior caps at `sections.size` (the custom page).

```kotlin
    @Test
    fun onNextSection_fromLastDefaultSection_landsOnCustomPage() = runTest {
        val vm = createViewModel()
        val lastDefaultIndex = vm.state.value.sections.size - 1
        vm.onAction(MeasurementFormAction.OnSectionChange(lastDefaultIndex))
        vm.onAction(MeasurementFormAction.OnNextSection)
        // Custom page index == sections.size (one past the last default section).
        assertEquals(vm.state.value.sections.size, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onNextSection_doesNotExceedCustomPage() = runTest {
        val vm = createViewModel()
        val customPageIndex = vm.state.value.sections.size
        vm.onAction(MeasurementFormAction.OnSectionChange(customPageIndex))
        vm.onAction(MeasurementFormAction.OnNextSection)
        assertEquals(customPageIndex, vm.state.value.currentSectionIndex)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModelTest"`
Expected: FAIL — `onNextSection_fromLastDefaultSection_landsOnCustomPage` expects `3` but current code coerces to `2`.

- [ ] **Step 3: Widen the coerce bound**

In `MeasurementFormViewModel.kt`, change the `OnNextSection` branch (lines 92-97):

```kotlin
            MeasurementFormAction.OnNextSection -> {
                _state.update { s ->
                    // Last page is the custom step at index sections.size, so Next
                    // walks one past the last default section (sections.size - 1).
                    val next = (s.currentSectionIndex + 1).coerceAtMost(s.sections.size)
                    s.copy(currentSectionIndex = next, isCurrentSectionExpanded = true)
                }
            }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModelTest"`
Expected: PASS (all navigation tests green).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt
git commit -m "feat(ptsp): Next walks onto the custom measurements page"
```

---

## Task 2: Add the "Custom" step label string resource

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml:335`

- [ ] **Step 1: Add the string**

After line 335 (`measurement_section_of`), add:

```xml
    <string name="measurement_custom_step">Custom</string>
```

This is used both as the pill label and as the progress-row counter label on the custom page.

- [ ] **Step 2: Generate resource accessors**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL — generates `Res.string.measurement_custom_step`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(ptsp): add Custom step label string"
```

---

## Task 3: Render the custom step as a trailing pager page

Move `CustomFieldsSection` off the bottom of the last default section onto a new trailing page, and hoist its Add button to the top.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt:163` (pager state), `:241-314` (pager content), `:322` (SectionNavigation), `:795-935` (CustomFieldsSection ordering)

- [ ] **Step 1: Widen the pager page count**

Change line 163 from:

```kotlin
    val pagerState = rememberPagerState(pageCount = { state.sections.size })
```

to:

```kotlin
    // +1 trailing page for the custom-measurements step (index == sections.size).
    // When sections is empty the pager block below isn't rendered, so the lone
    // page is harmless.
    val pagerState = rememberPagerState(pageCount = { state.sections.size + 1 })
```

- [ ] **Step 2: Branch the pager content onto the custom page**

Replace the entire `HorizontalPager` content lambda (lines 244-313, from `val section = state.sections[pageIndex]` through the close of the custom-fields `if` block) so the inner Column branches on whether this is a default section or the custom page. The new lambda body:

```kotlin
                ) { pageIndex ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = DesignTokens.space4,
                                vertical = DesignTokens.space3
                            )
                    ) {
                        if (pageIndex < state.sections.size) {
                            val section = state.sections[pageIndex]
                            val essentialFields = section.fields.filter { it.isEssential }
                            val extraFields = section.fields.filter { !it.isEssential }
                            val isExpanded =
                                pageIndex != state.currentSectionIndex || state.isCurrentSectionExpanded

                            essentialFields.forEach { field ->
                                MeasurementFieldInput(
                                    field = field,
                                    value = state.fields[field.key] ?: "",
                                    unitSuffix = unitSuffix,
                                    onValueChange = {
                                        onAction(MeasurementFormAction.OnFieldChange(field.key, it))
                                    }
                                )
                            }

                            if (isExpanded) {
                                extraFields.forEach { field ->
                                    MeasurementFieldInput(
                                        field = field,
                                        value = state.fields[field.key] ?: "",
                                        unitSuffix = unitSuffix,
                                        onValueChange = {
                                            onAction(MeasurementFormAction.OnFieldChange(field.key, it))
                                        }
                                    )
                                }
                            }

                            if (extraFields.isNotEmpty() && pageIndex == state.currentSectionIndex) {
                                ShowMoreToggle(
                                    isExpanded = state.isCurrentSectionExpanded,
                                    extraCount = extraFields.size,
                                    onClick = { onAction(MeasurementFormAction.OnToggleShowMore) }
                                )
                            }
                        } else {
                            // Trailing custom-measurements step. Filtering by gender
                            // is handled in the ViewModel; this renders the result.
                            CustomFieldsSection(
                                fields = state.customFields,
                                fieldValues = state.fields,
                                unitSuffix = unitSuffix,
                                canUseCustomMeasurements = state.canUseCustomMeasurements,
                                isEditMode = state.isEditMode,
                                isInWelcomeWindow = state.isInWelcomeWindow,
                                tier = state.tier,
                                onFieldValueChange = { key, value ->
                                    onAction(MeasurementFormAction.OnFieldChange(key, value))
                                },
                                onAddClick = { onAction(MeasurementFormAction.OnAddCustomFieldClick) },
                                onLockedAddClick = { onAction(MeasurementFormAction.OnLockedCustomFieldClick) },
                                onEditField = { id ->
                                    onAction(MeasurementFormAction.OnEditCustomFieldClick(id))
                                },
                                onDeleteField = { id ->
                                    onAction(MeasurementFormAction.OnArchiveCustomFieldRequest(id))
                                },
                            )
                        }
                    }
                }
```

- [ ] **Step 3: Pass the custom page into SectionNavigation's total**

Change the `SectionNavigation` call (around line 322) so Next stays enabled through the last default section and disables on the custom page:

```kotlin
                    SectionNavigation(
                        currentIndex = state.currentSectionIndex,
                        totalSections = state.sections.size + 1,
                        onPrevious = { onAction(MeasurementFormAction.OnPreviousSection) },
                        onNext = { onAction(MeasurementFormAction.OnNextSection) }
                    )
```

`SectionNavigation` itself is unchanged — it already disables Next when `currentIndex >= totalSections - 1` and Previous when `currentIndex == 0`.

- [ ] **Step 4: Hoist the Add button to the top of `CustomFieldsSection`**

In `CustomFieldsSection` (lines ~795-935), the current order is: header column → fields/empty-caption → `AddCustomFieldButton`. Reorder so the button sits directly under the header column, before the fields. Concretely:

1. **Cut** the `AddCustomFieldButton(...)` call (lines ~930-933) from the bottom of the outer `Column`.
2. **Paste** it immediately after the closing brace of the header `Column` (the one ending at line ~845, right after the subtitle `Text`) and before the `val visibleFields = ...` block.

The resulting structure of the outer `Column` body is:

```kotlin
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
            // ... title row with pill ...
            // ... subtitle Text ...
        }

        AddCustomFieldButton(
            enabled = canUseCustomMeasurements,
            onClick = if (canUseCustomMeasurements) onAddClick else onLockedAddClick,
        )

        val visibleFields = if (canUseCustomMeasurements) {
            fields
        } else if (isEditMode) {
            fields.filter { (fieldValues[it.id] ?: "").isNotBlank() }
        } else {
            emptyList()
        }

        if (visibleFields.isEmpty()) {
            // ... empty / locked caption ...
        } else {
            // ... field rows ...
        }
```

The `custom_field_empty_caption` text ("Add measurements that aren't on the default list…") reads fine below the button, so no copy change is needed.

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(ptsp): custom measurements render as a trailing wizard page"
```

---

## Task 4: Add the tappable "Custom" pill + tappable dots to the progress row

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt:225-234` (call site), `:479-514` (`SectionProgressRow`)

- [ ] **Step 1: Update the `SectionProgressRow` call site**

In the fixed-header block (lines ~225-234), replace the `SectionProgressRow(...)` call with one that passes the custom-pill signals and a jump callback:

```kotlin
                if (state.sections.isNotEmpty()) {
                    Spacer(Modifier.height(DesignTokens.space4))
                    SectionProgressRow(
                        sections = state.sections,
                        currentIndex = state.currentSectionIndex,
                        fields = state.fields,
                        customLocked = !state.canUseCustomMeasurements,
                        // Mirror the dot "has data" rule: light the pill when any
                        // custom field holds a value that will actually persist.
                        customHasData = state.customFields.any { f ->
                            (state.fields[f.id]?.toDoubleOrNull() ?: 0.0) > 0.0
                        },
                        onJumpToSection = { index ->
                            onAction(MeasurementFormAction.OnSectionChange(index))
                        }
                    )
                    Spacer(Modifier.height(DesignTokens.space2))
                }
```

- [ ] **Step 2: Replace `SectionProgressRow` with the pill-aware version**

Replace the whole `SectionProgressRow` composable (lines 479-514) with:

```kotlin
@Composable
private fun SectionProgressRow(
    sections: List<MeasurementSection>,
    currentIndex: Int,
    fields: Map<String, String>,
    customLocked: Boolean,
    customHasData: Boolean,
    onJumpToSection: (Int) -> Unit,
) {
    val customPageIndex = sections.size
    val isCustomActive = currentIndex >= customPageIndex
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEachIndexed { index, section ->
                val color = when {
                    index == currentIndex -> MaterialTheme.colorScheme.primary
                    // Same parsable-positive predicate as MeasurementFormState.canSave
                    // so a dot only lights for values that will actually persist.
                    section.fields.any { f ->
                        (fields[f.key]?.toDoubleOrNull() ?: 0.0) > 0.0
                    } -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = color, shape = CircleShape)
                        .clickable { onJumpToSection(index) }
                )
            }
            CustomStepPill(
                isActive = isCustomActive,
                isLocked = customLocked,
                hasData = customHasData,
                onClick = { onJumpToSection(customPageIndex) },
            )
        }
        Text(
            text = if (isCustomActive) {
                stringResource(Res.string.measurement_custom_step)
            } else {
                stringResource(Res.string.measurement_section_of, currentIndex + 1, sections.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomStepPill(
    isActive: Boolean,
    isLocked: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
) {
    // Filled when the step is open or holds data; outlined otherwise.
    val filled = isActive || hasData
    val borderColor = when {
        isLocked -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = if (filled && !isLocked) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = when {
        isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
        filled -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(color = containerColor, shape = RoundedCornerShape(DesignTokens.radiusFull))
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(DesignTokens.radiusFull),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Add,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(Res.string.measurement_custom_step),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}
```

- [ ] **Step 3: Add the new imports**

At the top of `MeasurementFormScreen.kt`, add these imports (keep the existing alphabetical grouping):

```kotlin
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
```

`Icons.Default.Add`, `Icons.Default.Lock`, `BorderStroke`, `RoundedCornerShape`, `Color`, and `stringResource` are already imported.

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(ptsp): always-visible Custom pill + tappable dots in progress row"
```

---

## Task 5: Previews for the custom step

Every Screen composable must have previews (project rule). Add previews that land on the custom page so the new step renders in the IDE.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt` (append after the existing previews, ~line 1055)

- [ ] **Step 1: Add the custom-step previews**

Append these three previews after `MeasurementFormScreenMalePreview`:

```kotlin
@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCustomStepEntitledPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = sections.size, // custom page
                canUseCustomMeasurements = true,
                customFields = listOf(
                    CustomMeasurementField(
                        id = "cf-1",
                        label = "Sleeve cuff width",
                        genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
                        createdAt = 0L,
                        updatedAt = 0L,
                    )
                ),
                fields = mapOf("cf-1" to "6"),
                unit = MeasurementUnit.INCHES
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCustomStepEmptyPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = sections.size,
                canUseCustomMeasurements = true,
                customFields = emptyList(),
                unit = MeasurementUnit.INCHES
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCustomStepLockedPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = sections.size,
                canUseCustomMeasurements = false,
                customFields = emptyList(),
                unit = MeasurementUnit.INCHES
            ),
            onAction = {}
        )
    }
}
```

`CustomMeasurementField` is already imported. Its constructor is `(id, label, genders, isArchived = false, createdAt, updatedAt)` — `createdAt`/`updatedAt` have no defaults, hence the `0L` values above.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "test(ptsp): previews for the custom measurements step"
```

---

## Task 6: Full verification — tests, detekt, iOS compile, smoke test

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (especially the `MeasurementFormViewModelTest` navigation tests).

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL, no new violations. If `SectionProgressRow`/`CustomFieldsSection` trip CyclomaticComplexMethod, they already carry `@Suppress` — keep it.

- [ ] **Step 3: Compile for iOS (KMP — JVM-only API trap)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. `border`, `clickable`, and `HorizontalPager` are all common Compose — this should pass, but verify before declaring done (per project memory: always run iOS compile).

- [ ] **Step 4: Manual smoke test (Daniel is QA)**

Run the app (Android or iOS sim) and verify:
1. New measurement → select a gender → the `Custom` pill appears in the header on section 1 **without scrolling**.
2. Tap the pill from section 1 → lands on the custom step; the Add button is at the **top**.
3. Add a custom field → the pill **fills** (has-data) and the field appears below the button.
4. Step through with Next/Previous → Next reaches the custom step and **disables** there; Previous returns to the last default section.
5. Tap a section dot → jumps to that section.
6. Free tier past welcome window → pill shows the **lock** glyph; tapping it opens the custom step with the locked Add button + upgrade CTA (it does **not** jump straight to upgrade).
7. Edit a past measurement that has a recorded custom value on Free post-welcome → the value is still visible on the custom step.

- [ ] **Step 5: Final commit if any tweaks were needed during smoke test**

```bash
git add -A
git commit -m "fix(ptsp): smoke-test adjustments for custom measurements step"
```

(Skip if no changes.)

---

## Notes for the implementer

- **Do not** add custom fields to the last default section anymore — Task 3 Step 2 removes that block. If you see custom fields rendering twice, the old block wasn't fully removed.
- The two `LaunchedEffect`s syncing `pagerState.currentPage` ↔ `currentSectionIndex` (lines ~166-175) need **no change** — both indices now range `0..sections.size`.
- No State or Action members are added. If you find yourself adding one, re-read the plan — `OnSectionChange(index)` is the only action needed.
- Verify nothing else in the file assumes `currentSectionIndex <= sections.size - 1` (the spec flagged this). The pager content, `SectionProgressRow`, and `SectionNavigation` are the only consumers and are all updated here.
