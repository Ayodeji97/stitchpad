# Add-Style Flexible Photos + Per-Style Title Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In "Add style", let users remove/add individual photos without restarting; retire the description field everywhere; add an optional per-style title set via a CTA on the closet/inspiration cards.

**Architecture:** MVI. ViewModel logic changes are unit-tested (JUnit5 + Turbine + fake repo); Compose UI changes are manual-smoke-tested. The persisted Firestore/domain field stays named `description` (it now *is* the title) to avoid a wide rename + data migration — the UI presents it as "Title". A future cleanup can rename it.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, peekaboo image picker, Koin, GitLive Firestore.

---

## File Structure

- `feature/style/presentation/form/StyleFormAction.kt` — add `OnRemovePhoto`, drop `OnDescriptionChange`.
- `feature/style/presentation/form/StyleFormState.kt` — drop `description`.
- `feature/style/presentation/form/StyleFormViewModel.kt` — additive pick, remove-photo, drop description from save.
- `feature/style/presentation/form/StyleFormScreen.kt` — ✕ per photo + "Add more" tile; delete the description field.
- `core/domain/repository/StyleRepository.kt` + `feature/style/data/FirebaseStyleRepository.kt` — add `setStyleTitle`.
- `feature/style/presentation/gallery/StyleGalleryState.kt` / `StyleGalleryAction.kt` / `StyleGalleryViewModel.kt` / `StyleGalleryScreen.kt` — title CTA + editor sheet.
- `composeApp/src/commonMain/composeResources/values/strings.xml` — new strings.
- Tests: `StyleFormViewModelTest.kt`, `StyleGalleryViewModelTest.kt`, `FakeStyleRepository.kt`.

---

## Task 1: Photo selection becomes additive + removable

**Files:**
- Modify: `feature/style/presentation/form/StyleFormAction.kt`
- Modify: `feature/style/presentation/form/StyleFormViewModel.kt:119-140,94-113`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt`

- [ ] **Step 1: Add the remove action**

In `StyleFormAction.kt`, add inside the sealed interface:
```kotlin
    data class OnRemovePhoto(val index: Int) : StyleFormAction
```

- [ ] **Step 2: Write failing tests for additive pick + remove**

Add to `StyleFormViewModelTest.kt` (mirror existing tests for construction — they use a `FakeStyleRepository`, `SavedStateHandle`, fake auth, a fake `ImageCompressor` that returns input unchanged). Use small `ByteArray(1)` payloads so they pass the size guard.
```kotlin
    @Test
    fun picking_photos_twice_appends_instead_of_replacing() = runTest {
        val vm = createViewModel() // closet-add mode (allowMultiPhoto = true), maxPhotoSelection high
        vm.state.test {
            awaitItem() // initial
            vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))
            assertThat(awaitItem().selectedPhotos).hasSize(2)
            vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(3))))
            assertThat(awaitItem().selectedPhotos).hasSize(3) // appended, not replaced
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appending_is_capped_at_maxPhotoSelection() = runTest {
        val vm = createViewModel(maxPhotoSelection = 2)
        vm.state.test {
            awaitItem()
            vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))
            awaitItem()
            vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(3))))
            // 3rd dropped — already at cap of 2
            assertThat(awaitItem().selectedPhotos).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removing_a_photo_drops_only_that_index() = runTest {
        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))))
            awaitItem()
            vm.onAction(StyleFormAction.OnRemovePhoto(1))
            val remaining = awaitItem().selectedPhotos
            assertThat(remaining).hasSize(2)
            assertThat(remaining[0][0]).isEqualTo(1.toByte())
            assertThat(remaining[1][0]).isEqualTo(3.toByte())
            cancelAndIgnoreRemainingEvents()
        }
    }
```
Note: if `createViewModel(maxPhotoSelection=...)` isn't an existing helper, seed it by constructing the VM and letting `computeMaxPhotoSelection` resolve from the fake repo's count, OR add a test-only overload. Match the file's existing helper style.

- [ ] **Step 3: Run tests, verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*StyleFormViewModelTest*'`
Expected: FAIL (append/remove not implemented; `OnRemovePhoto` unhandled).

- [ ] **Step 4: Make pick additive + handle remove**

In `StyleFormViewModel.kt`, replace `onPhotosPicked` (lines 119-140) with an additive version that keeps existing photos and only rejects newly-picked oversize images:
```kotlin
    private fun onPhotosPicked(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        photoProcessingJob?.cancel()
        photoProcessingJob = viewModelScope.launch {
            val processed = photos.map { imageCompressor.compress(it) ?: it }
            if (processed.any { it.size > MAX_PHOTO_SIZE_BYTES }) {
                // Reject the new batch but keep what was already chosen.
                _state.update { it.copy(errorMessage = StyleError.PHOTO_TOO_LARGE.toUiText()) }
                return@launch
            }
            _state.update { current ->
                val merged = (current.selectedPhotos + processed).take(current.maxPhotoSelection)
                current.copy(selectedPhotos = merged, errorMessage = null)
            }
        }
    }
```
Add the remove handler in `onAction` (after `OnPhotosPicked`):
```kotlin
            is StyleFormAction.OnRemovePhoto -> _state.update {
                it.copy(selectedPhotos = it.selectedPhotos.filterIndexed { i, _ -> i != action.index })
            }
```

- [ ] **Step 5: Run tests, verify pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*StyleFormViewModelTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormAction.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModel.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt
git commit -m "feat(style): additive photo pick + per-photo remove in add-style"
```

---

## Task 2: Retire the description field from the form

**Files:**
- Modify: `feature/style/presentation/form/StyleFormState.kt`
- Modify: `feature/style/presentation/form/StyleFormAction.kt`
- Modify: `feature/style/presentation/form/StyleFormViewModel.kt:96-98,178-210,278-435`
- Modify: `feature/style/presentation/form/StyleFormScreen.kt:226-231,488-574`

- [ ] **Step 1: Drop `description` from state + action**

In `StyleFormState.kt`, remove the `val description: String = "",` line.
In `StyleFormAction.kt`, remove `data class OnDescriptionChange(...)`.

- [ ] **Step 2: Drop description handling in the ViewModel**

In `StyleFormViewModel.kt`:
- Remove the `is StyleFormAction.OnDescriptionChange -> ...` branch (lines 96-98).
- In `loadStyle` remove `description = style.description,` from the state copy (lines ~191).
- In `save()`: remove `val trimmedDescription = s.description.trim()` (line 297). For **edit**, write the style unchanged: `style = s.existingStyle` (drop `.copy(description = ...)`, line 312). For **create** paths pass `description = ""` to `createStyle`/`createStyles` (lines 383, 399) — title is set later via the CTA.

- [ ] **Step 3: Delete the description field from the screen**

In `StyleFormScreen.kt`: remove the `StyleDescriptionField(...)` call (lines ~226-231) and delete the `StyleDescriptionField` composable (lines 488-574). Remove now-unused imports + the `style_description_label` / `style_description_placeholder` `Res` references in this file.

- [ ] **Step 4: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: success. Fix any remaining `description` references in `StyleFormScreen.kt` / tests (a removed test for `OnDescriptionChange` is expected — delete it).

- [ ] **Step 5: Run style tests + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*StyleForm*' detekt -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(style): retire description field from add/edit style form"
```

---

## Task 3: Add `setStyleTitle` to the repository

**Files:**
- Modify: `core/domain/repository/StyleRepository.kt`
- Modify: `feature/style/data/FirebaseStyleRepository.kt`
- Modify: `composeApp/src/commonTest/.../FakeStyleRepository.kt`

- [ ] **Step 1: Add to the interface**

In `StyleRepository.kt`, add (mirrors the `description` field name — that field holds the title now):
```kotlin
    /** Sets (or clears, when blank) a style's optional title. */
    suspend fun setStyleTitle(
        userId: String,
        location: StyleLocation,
        styleId: String,
        title: String,
    ): EmptyResult<DataError.Network>
```

- [ ] **Step 2: Implement in FirebaseStyleRepository**

Mirror the single-field `renameFolder` update pattern. Add:
```kotlin
    override suspend fun setStyleTitle(
        userId: String,
        location: StyleLocation,
        styleId: String,
        title: String,
    ): EmptyResult<DataError.Network> = try {
        val now = Clock.System.now().toEpochMilliseconds()
        stylesCollectionFor(userId, location).document(styleId)
            .update("description" to title.trim(), "updatedAt" to now)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.toNetworkError())
    }
```
Use the file's existing collection accessor (the one `updateStyle` uses — likely `stylesCollectionFor(userId, location)`) and its existing `toNetworkError()` helper. Match the existing try/catch idiom in that file exactly.

- [ ] **Step 3: Implement in the test fake**

In `FakeStyleRepository.kt`, store styles in a mutable map keyed by id and implement:
```kotlin
    override suspend fun setStyleTitle(
        userId: String, location: StyleLocation, styleId: String, title: String,
    ): EmptyResult<DataError.Network> {
        styles = styles.map { if (it.id == styleId) it.copy(description = title.trim()) else it }
        return Result.Success(Unit)
    }
```
Match how the fake already holds/emits its style list.

- [ ] **Step 4: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: success (no callers yet — Task 4 wires it).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(style): add setStyleTitle repository method"
```

---

## Task 4: Per-style title CTA + editor sheet (gallery cards)

**Files:**
- Modify: `feature/style/presentation/gallery/StyleGalleryState.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryAction.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryViewModel.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryScreen.kt:522-607` (StyleCard) + sheet host
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/.../StyleGalleryViewModelTest.kt`

- [ ] **Step 1: Add strings**

In `strings.xml`:
```xml
    <string name="style_add_title_cta">＋ Add title</string>
    <string name="style_title_sheet_heading">Title (optional)</string>
    <string name="style_title_placeholder">e.g. Red agbada with gold trim</string>
    <string name="style_title_save">Save title</string>
    <string name="style_title_remove">Remove title</string>
```

- [ ] **Step 2: State + actions**

`StyleGalleryState.kt`: add `val titleEditTarget: Style? = null,`.
`StyleGalleryAction.kt`: add
```kotlin
    data class OnEditTitleClick(val style: Style) : StyleGalleryAction
    data class OnConfirmTitle(val title: String) : StyleGalleryAction
    data object OnDismissTitleSheet : StyleGalleryAction
```

- [ ] **Step 3: Failing VM test**

Add to `StyleGalleryViewModelTest.kt`:
```kotlin
    @Test
    fun confirming_a_title_persists_it_and_closes_the_sheet() = runTest {
        val style = sampleStyle(id = "s1", description = "")
        val vm = createViewModel(initialStyles = listOf(style))
        vm.state.test {
            awaitItem()
            vm.onAction(StyleGalleryAction.OnEditTitleClick(style))
            assertThat(awaitItem().titleEditTarget?.id).isEqualTo("s1")
            vm.onAction(StyleGalleryAction.OnConfirmTitle("Red agbada"))
            val after = awaitItem()
            assertThat(after.titleEditTarget).isNull()
            assertThat(fakeStyleRepository.styles.first { it.id == "s1" }.description).isEqualTo("Red agbada")
            cancelAndIgnoreRemainingEvents()
        }
    }
```
Use the file's existing `createViewModel`/`sampleStyle`/fake accessors (adapt names to match).

- [ ] **Step 4: Run, verify fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*StyleGalleryViewModelTest*'`
Expected: FAIL (actions unhandled).

- [ ] **Step 5: Handle actions in the VM**

In `StyleGalleryViewModel.onAction`, add branches (use the VM's existing `userId`, `location`, and `styleRepository`):
```kotlin
            is StyleGalleryAction.OnEditTitleClick ->
                _state.update { it.copy(titleEditTarget = action.style) }
            is StyleGalleryAction.OnConfirmTitle -> {
                val target = _state.value.titleEditTarget ?: return
                _state.update { it.copy(titleEditTarget = null) }
                viewModelScope.launch {
                    styleRepository.setStyleTitle(userId, location, target.id, action.title)
                }
            }
            StyleGalleryAction.OnDismissTitleSheet ->
                _state.update { it.copy(titleEditTarget = null) }
```
(The observed style flow refreshes the card after the write. If this VM doesn't already hold `userId`/`location`, read how `OnStyleClick` obtains them and reuse.)

- [ ] **Step 6: Run, verify pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*StyleGalleryViewModelTest*'`
Expected: PASS.

- [ ] **Step 7: Card UI — replace the description label with title/CTA**

In `StyleGalleryScreen.kt` `StyleCard` (lines 592-604), replace the `if (style.description.isNotBlank()) { Text(...) }` block with a row that shows the title + edit pencil when set, or the CTA when blank. `StyleCard` needs a new `onEditTitle: () -> Unit` param (thread it from the grid `items` block, calling `onAction(StyleGalleryAction.OnEditTitleClick(style))`):
```kotlin
            if (style.description.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                    modifier = Modifier
                        .clickable(onClick = onEditTitle)
                        .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
                ) {
                    Text(
                        text = style.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(Res.string.style_title_sheet_heading),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.style_add_title_cta),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalStitchPadColors.current.brandAccent,
                    modifier = Modifier
                        .clickable(onClick = onEditTitle)
                        .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
                )
            }
```
Add imports: `Icons.Outlined.Edit`, `androidx.compose.foundation.clickable`, `LocalStitchPadColors`, `Row`, `Arrangement`. (Do NOT show the CTA on a locked card — wrap in `if (!isLocked)` to mirror existing lock handling.)

- [ ] **Step 8: Title editor sheet**

Add a `StyleTitleSheet` composable to `StyleGalleryScreen.kt`, mirroring `FolderNameSheet` in `StyleFoldersScreen.kt:384-450`, but: heading `style_title_sheet_heading`, placeholder `style_title_placeholder`, allow **blank** (blank = remove title, so no `isNotBlank()` guard on the button), and a secondary "Remove title" text button (`style_title_remove`) that calls `onConfirm("")`. Host it after the grid:
```kotlin
        state.titleEditTarget?.let { target ->
            StyleTitleSheet(
                initialTitle = target.description,
                onConfirm = { onAction(StyleGalleryAction.OnConfirmTitle(it)) },
                onDismiss = { onAction(StyleGalleryAction.OnDismissTitleSheet) },
            )
        }
```

- [ ] **Step 9: Compile + detekt + tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt -q`
Then: `./gradlew :composeApp:testDebugUnitTest --tests '*Style*' -q`
Expected: all PASS.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(style): optional per-style title via CTA + editor sheet"
```

---

## Manual smoke test (run on device before PR — Daniel is QA)

1. Customer closet → Add style → pick 4 photos. Tap ✕ on one → it's removed (others stay). Tap "Add more" → pick 2 → they append (total 5), nothing wiped. Save → 5 styles created.
2. The Add-style screen shows **no description field**.
3. In the closet grid, a new style shows **"＋ Add title"**. Tap → editor sheet → type "Red agbada" → Save → card shows the title + pencil. Tap the title → edit → "Remove title" → CTA returns.
4. Repeat (3) on the Inspiration grid.
5. Edit an existing style → only the photo can be changed (no description field); title still managed via the card CTA.

---

## Self-review notes

- Spec coverage: 2a (Task 1), 2b retire description (Task 2) + field-name decision documented, 2c title CTA + editor (Tasks 3–4). ✓
- The persisted field stays `description`; UI says "Title". Documented in Architecture + Task 3.
- Strings added (no hardcoded user-facing text). ✓
- `setStyleTitle` defined in Task 3, consumed in Task 4 (name consistent). ✓
