# Freemium Read-Only Styles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On Free, show all of a customer's (and Inspiration's) styles instead of hiding folder/over-cap ones — newest up to the tier cap stay editable, the rest are read-only ("locked") with an Upgrade path. Also lock per-folder over-cap images on Atelier→Pro. Non-destructive, client-side only.

**Architecture:** A new pure `StyleLockPolicy` decides which styles are locked (newest `cap` active, rest locked). `StyleGalleryViewModel` resolves the tier limits at observe time and picks its source stream: on Free it flattens all folders via the existing `observeAllCustomerStyles`/`observeAllInspirationStyles` + `observeFoldersWithStyles` (so each style keeps its true folder location for edit/delete), on Pro/Atelier it keeps the per-folder stream. Locked styles route to the existing style edit screen in a new `readOnly` mode. Cap counting on Free uses the flattened total.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI ViewModels, Koin, kotlinx.coroutines Flow, kotlin.test + Turbine (`:composeApp:testDebugUnitTest`), detekt.

**Base branch:** `feat/freemium-readonly-styles` (off `main`). Land after / rebase onto PR #173 (both touch the style-form area).

**Spec:** `docs/superpowers/specs/2026-06-16-freemium-readonly-styles-design.md`

---

## File Structure

- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicy.kt` — pure lock decision.
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicyTest.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryState.kt` — add `lockedStyleIds`.
- Modify: `feature/style/presentation/gallery/StyleGalleryEvent.kt` — `NavigateToEditStyle.readOnly`.
- Modify: `feature/style/presentation/gallery/StyleGalleryViewModel.kt` — limit-aware observe (flatten on Free, per-folder lock on paid), per-style location for ops, locked gating.
- Modify: `feature/style/presentation/gallery/StyleGalleryScreen.kt` — lock badge overlay.
- Modify: `feature/style/presentation/gallery/StyleGalleryRoot.kt` (callback signature) and `feature/main/presentation/MainScreen.kt` — thread `readOnly` through nav.
- Modify: `navigation/Routes.kt` — `StyleFormRoute.readOnly`.
- Modify: `feature/style/presentation/form/StyleFormState.kt` — add `readOnly`.
- Modify: `feature/style/presentation/form/StyleFormViewModel.kt` — read `readOnly`, block save → Upgrade, flattened cap count on Free.
- Modify: `feature/style/presentation/form/StyleFormScreen.kt` — read-only rendering + Upgrade CTA.
- Tests: `feature/style/presentation/gallery/StyleGalleryViewModelTest.kt`, `feature/style/presentation/form/StyleFormViewModelTest.kt`.

**Convention reminders:** run unit tests with `./gradlew :composeApp:testDebugUnitTest --tests "<FQN>"`; iOS compile gate `./gradlew :composeApp:compileKotlinIosSimulatorArm64`; `./gradlew detekt`. commonTest uses `kotlin.test` + Turbine + `runTest` (`UnconfinedTestDispatcher` where a VM needs it). Fakes live in `commonTest` (`FakeStyleRepository`, `FakeAuthRepository`, fake `EntitlementsProvider`, `FakeCustomerRepository`) — read `StyleGalleryViewModelTest.kt` for the existing setup pattern before writing VM tests.

---

## Task 1: StyleLockPolicy (pure)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.Style
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleLockPolicyTest {

    private fun style(id: String, createdAt: Long) = Style(
        id = id,
        customerId = "c1",
        description = id,
        photoUrl = "",
        photoStoragePath = "",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun underCap_nothingLocked() {
        val styles = listOf(style("a", 1), style("b", 2))
        assertEquals(emptySet(), StyleLockPolicy.lockedStyleIds(styles, activeCap = 5))
    }

    @Test
    fun overCap_newestStayActive_oldestLocked() {
        // newest = highest createdAt. cap 2 keeps c(3) and b(2) active; a(1) locked.
        val styles = listOf(style("a", 1), style("b", 2), style("c", 3))
        assertEquals(setOf("a"), StyleLockPolicy.lockedStyleIds(styles, activeCap = 2))
    }

    @Test
    fun capZero_everythingLocked() {
        val styles = listOf(style("a", 1), style("b", 2))
        assertEquals(setOf("a", "b"), StyleLockPolicy.lockedStyleIds(styles, activeCap = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.domain.StyleLockPolicyTest"`
Expected: FAIL — `StyleLockPolicy` unresolved (compile error).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.Style

/**
 * Decides which styles are "locked" (read-only) for the current tier. The newest
 * [activeCap] styles (by createdAt) stay active/editable; everything beyond the cap is
 * locked. Used for Free flattened closets and Pro/Atelier per-folder over-cap. Purely a
 * presentation decision — never deletes or moves data.
 */
object StyleLockPolicy {
    fun lockedStyleIds(styles: List<Style>, activeCap: Int): Set<String> {
        if (activeCap <= 0) return styles.mapTo(mutableSetOf()) { it.id }
        return styles
            .sortedByDescending { it.createdAt }
            .drop(activeCap)
            .mapTo(mutableSetOf()) { it.id }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.domain.StyleLockPolicyTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicy.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleLockPolicyTest.kt
git commit -m "feat(style): pure StyleLockPolicy (newest-active, rest locked)"
```

---

## Task 2: Gallery state carries locked ids

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryState.kt`

- [ ] **Step 1: Add the field**

In `data class StyleGalleryState`, add after `capSheet`:

```kotlin
    /** Ids of styles shown read-only (over the current tier cap). Empty on paid within cap. */
    val lockedStyleIds: Set<String> = emptySet(),
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryState.kt
git commit -m "feat(style): add lockedStyleIds to gallery state"
```

---

## Task 3: Gallery observe — flatten on Free, lock per cap

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModelTest.kt`

**Context:** Today `observeStyles()` reads a single `location`. We make it tier-aware: on Free flatten all folders (each style keeps its true folder location in `entryLocations`), on paid keep the single location; both compute `lockedStyleIds` via `StyleLockPolicy` with the gallery's existing cap rule (`if (!foldersEnabled) flatCap else maxImagesPerFolder`).

- [ ] **Step 1: Write the failing test**

Add to `StyleGalleryViewModelTest.kt` (reuse the file's existing fakes/setup helpers — match how other tests build the VM with `SavedStateHandle(mapOf("customerId" to "c1"))`, a `FakeStyleRepository`, fake entitlements set to FREE, etc.):

```kotlin
@Test
fun free_flattensAllFolders_andLocksOverCap() = runTest {
    // FREE customer flatCap = 5. Seed 4 root + 3 in a named folder = 7 total.
    // Build the fake so observeFolders returns one folder and observeStyles returns
    // each location's styles (see FakeStyleRepository usage in this file).
    val vm = buildViewModel(tier = SubscriptionTier.FREE, customerId = "c1") // existing helper
    fakeStyleRepository.seedCustomerRoot("c1", styles = stylesNewest(ids = listOf("r1","r2","r3","r4"), base = 100))
    fakeStyleRepository.seedCustomerFolder("c1", folderId = "f1", styles = stylesNewest(ids = listOf("o1","o2","o3"), base = 1))

    vm.state.test {
        awaitItem() // initial loading
        val loaded = awaitItem()
        assertEquals(7, loaded.styles.size)                       // nothing hidden
        assertEquals(setOf("o1","o2","o3"), loaded.lockedStyleIds) // oldest 2-over-cap locked (3 here)
        cancelAndIgnoreRemainingEvents()
    }
}
```

> If `FakeStyleRepository` lacks `seedCustomerFolder`/`seedCustomerRoot`/`stylesNewest` helpers, add them to the fake/test-helpers in the same style as existing seeding. Each seeded style needs a distinct `createdAt` so ordering is deterministic (root ids use higher `base` so they're newest → active).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModelTest"`
Expected: FAIL — only 4 root styles returned, `lockedStyleIds` empty.

- [ ] **Step 3: Implement the tier-aware observe**

Add imports to `StyleGalleryViewModel.kt`:

```kotlin
import com.danzucker.stitchpad.feature.style.domain.StyleLockPolicy
import com.danzucker.stitchpad.feature.style.domain.observeAllCustomerStyles
import com.danzucker.stitchpad.feature.style.domain.observeAllInspirationStyles
import com.danzucker.stitchpad.feature.style.domain.observeFoldersWithStyles
import com.danzucker.stitchpad.core.domain.model.Style
import kotlinx.coroutines.flow.map
```

Add a field near `location`:

```kotlin
    // styleId -> the style's TRUE location (its folder). On Free the flat gallery shows
    // styles from many folders; edits/deletes must target each style's real folder, not
    // the gallery's root location. Rebuilt on every emission.
    private var entryLocations: Map<String, StyleLocation> = emptyMap()
```

Replace `observeStyles()` with:

```kotlin
    private fun observeStyles() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val tier = entitlements.awaitHydrated().tier
            val limits = if (customerId == null) {
                StyleCollectionLimits.forInspiration(tier)
            } else {
                StyleCollectionLimits.forCustomer(tier)
            }
            val cap = if (!limits.foldersEnabled) limits.flatCap else limits.maxImagesPerFolder

            if (!limits.foldersEnabled) {
                observeFlattened(userId, cap)
            } else {
                observePerFolder(userId, cap)
            }
        }
    }

    /** Free: flatten root + every named folder, retaining each style's folder location. */
    private suspend fun observeFlattened(userId: String, cap: Int) {
        foldersWithStylesFlow(userId)
            .map { entries ->
                val styles = entries
                    .flatMap { folder -> folder.styles.map { it to folder.folderId } }
                    .sortedByDescending { it.first.createdAt }
                styles
            }
            .collect { pairs ->
                entryLocations = pairs.associate { (style, folderId) -> style.id to locationFor(folderId) }
                val ordered = pairs.map { it.first }
                _state.update {
                    it.copy(
                        styles = ordered,
                        lockedStyleIds = StyleLockPolicy.lockedStyleIds(ordered, cap),
                        isLoading = false,
                    )
                }
            }
    }

    /** Paid: a single folder/location; lock per-folder over-cap (e.g. Atelier->Pro). */
    private suspend fun observePerFolder(userId: String, cap: Int) {
        styleRepository.observeStyles(userId, location).collect { result ->
            when (result) {
                is Result.Success -> {
                    entryLocations = result.data.associate { it.id to location }
                    _state.update {
                        it.copy(
                            styles = result.data,
                            lockedStyleIds = StyleLockPolicy.lockedStyleIds(result.data, cap),
                            isLoading = false,
                        )
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toStyleUiText())
                }
            }
        }
    }

    private fun foldersWithStylesFlow(userId: String) =
        when (val loc = location) {
            is StyleLocation.CustomerCloset ->
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.CustomerCloset(loc.customerId))
            is StyleLocation.Inspiration ->
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.Inspiration())
        }

    /** Resolve a style's real location from its folderId, scoped to this gallery's root. */
    private fun locationFor(folderId: String?): StyleLocation = when (val loc = location) {
        is StyleLocation.CustomerCloset -> StyleLocation.CustomerCloset(loc.customerId, folderId)
        is StyleLocation.Inspiration -> StyleLocation.Inspiration(folderId)
    }

    /** The true location of a loaded style (its folder), falling back to the gallery location. */
    private fun locationOf(styleId: String): StyleLocation = entryLocations[styleId] ?: location
```

> `observeFoldersWithStyles` returns `List<StylePickerFolder>` (each has `folderId` + `styles`), already resilient via keep-last. Using it (instead of `observeAllCustomerStyles`) is what lets us retain per-style folder location. Keep the `observeAllCustomerStyles`/`observeAllInspirationStyles` imports only if used; otherwise drop them to avoid unused-import detekt failures.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModelTest.kt
git commit -m "feat(style): flatten Free closet + compute locked ids per tier cap"
```

---

## Task 4: Operations use per-style location; lock gating

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModel.kt`
- Test: `StyleGalleryViewModelTest.kt`

- [ ] **Step 1: Add `readOnly` to the edit event**

In `StyleGalleryEvent.kt`:

```kotlin
    data class NavigateToEditStyle(
        val customerId: String?,
        val folderId: String?,
        val styleId: String,
        val readOnly: Boolean = false,
    ) : StyleGalleryEvent
```

- [ ] **Step 2: Write the failing tests**

Add to `StyleGalleryViewModelTest.kt`:

```kotlin
@Test
fun free_tapLockedStyle_navigatesReadOnly_withItsFolder() = runTest {
    val vm = buildViewModel(tier = SubscriptionTier.FREE, customerId = "c1")
    fakeStyleRepository.seedCustomerRoot("c1", stylesNewest(listOf("r1","r2","r3","r4","r5"), base = 100))
    fakeStyleRepository.seedCustomerFolder("c1", "f1", stylesNewest(listOf("o1"), base = 1)) // over cap -> locked
    vm.state.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }

    vm.events.test {
        vm.onAction(StyleGalleryAction.OnStyleClick(styleWithId("o1")))
        val e = awaitItem() as StyleGalleryEvent.NavigateToEditStyle
        assertEquals("o1", e.styleId); assertEquals(true, e.readOnly); assertEquals("f1", e.folderId)
    }
}

@Test
fun free_tapActiveStyle_navigatesEditable() = runTest {
    val vm = buildViewModel(tier = SubscriptionTier.FREE, customerId = "c1")
    fakeStyleRepository.seedCustomerRoot("c1", stylesNewest(listOf("r1"), base = 100))
    vm.state.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }

    vm.events.test {
        vm.onAction(StyleGalleryAction.OnStyleClick(styleWithId("r1")))
        val e = awaitItem() as StyleGalleryEvent.NavigateToEditStyle
        assertEquals(false, e.readOnly)
    }
}

@Test
fun free_longPressLockedStyle_showsUpgradeNotActionSheet() = runTest {
    val vm = buildViewModel(tier = SubscriptionTier.FREE, customerId = "c1")
    fakeStyleRepository.seedCustomerRoot("c1", stylesNewest(listOf("r1","r2","r3","r4","r5"), base = 100))
    fakeStyleRepository.seedCustomerFolder("c1", "f1", stylesNewest(listOf("o1"), base = 1))
    vm.state.test {
        awaitItem(); awaitItem()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(styleWithId("o1")))
        val s = awaitItem()
        assertNull(s.actionSheetStyle)      // no copy/move/delete sheet for locked
        assertNotNull(s.capSheet)           // upgrade prompt instead
        cancelAndIgnoreRemainingEvents()
    }
}
```

> `styleWithId("x")` = the loaded style instance with that id from `vm.state.value.styles`. Add a tiny test helper if not present.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModelTest"`
Expected: FAIL — click always non-readOnly with gallery folderId; long-press always opens action sheet.

- [ ] **Step 4: Implement**

In `onAction`, replace the `OnStyleClick` and `OnStyleLongPress` branches:

```kotlin
            is StyleGalleryAction.OnStyleClick -> {
                viewModelScope.launch {
                    val locked = action.style.id in _state.value.lockedStyleIds
                    val loc = locationOf(action.style.id)
                    val targetFolderId = (loc as? StyleLocation.CustomerCloset)?.folderId
                        ?: (loc as? StyleLocation.Inspiration)?.folderId
                    _events.send(
                        StyleGalleryEvent.NavigateToEditStyle(
                            customerId = customerId,
                            folderId = targetFolderId,
                            styleId = action.style.id,
                            readOnly = locked,
                        )
                    )
                }
            }
            is StyleGalleryAction.OnStyleLongPress -> {
                if (action.style.id in _state.value.lockedStyleIds) {
                    viewModelScope.launch {
                        val tier = entitlements.awaitHydrated().tier
                        _state.update { it.copy(capSheet = stylesCapInfo(tier)) }
                    }
                } else {
                    _state.update { it.copy(actionSheetStyle = action.style) }
                }
            }
```

Update `deleteStyle()` to use the style's true location:

```kotlin
    private fun deleteStyle() {
        val style = _state.value.styleToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, styleToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = styleRepository.deleteStyle(userId, locationOf(style.id), style)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toStyleUiText()) }
            }
        }
    }
```

In `performTransfer`, change the copy/move source from `location` to the style's location:

```kotlin
        val sourceLocation = locationOf(transfer.style.id)
        val result = when (transfer.mode) {
            StyleTransferMode.COPY ->
                styleRepository.copyStyle(userId, from = sourceLocation, transfer.style, to = destinationLocation)
            StyleTransferMode.MOVE ->
                styleRepository.moveStyle(userId, from = sourceLocation, transfer.style, to = destinationLocation)
        }
```

> Locked styles never reach transfer (long-press is gated above), so transfer always operates on active styles; `locationOf` still resolves their real folder.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/
git commit -m "feat(style): per-style location for ops + lock gating in gallery"
```

---

## Task 5: Thread `readOnly` through navigation

**Files:**
- Modify: `navigation/Routes.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryRoot.kt`
- Modify: `feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: Add the route arg**

In `Routes.kt` `StyleFormRoute`:

```kotlin
@Serializable
data class StyleFormRoute(
    val customerId: String? = null,
    val styleId: String? = null,
    val linkToOrderId: String? = null,
    val folderId: String? = null,
    val readOnly: Boolean = false,
)
```

- [ ] **Step 2: Update the gallery Root callback**

In `StyleGalleryRoot.kt`, find the `onNavigateToEditStyle` parameter and the `ObserveAsEvents` mapping for `NavigateToEditStyle`. Change the callback type to include `readOnly` and forward `event.readOnly`:

```kotlin
    onNavigateToEditStyle: (customerId: String?, folderId: String?, styleId: String, readOnly: Boolean) -> Unit,
```
```kotlin
            is StyleGalleryEvent.NavigateToEditStyle ->
                onNavigateToEditStyle(event.customerId, event.folderId, event.styleId, event.readOnly)
```

- [ ] **Step 3: Update MainScreen wiring**

In `MainScreen.kt`, the `StyleGalleryRoot` block:

```kotlin
                onNavigateToEditStyle = { customerId, folderId, styleId, readOnly ->
                    navController.navigate(
                        StyleFormRoute(
                            customerId = customerId,
                            folderId = folderId,
                            styleId = styleId,
                            readOnly = readOnly,
                        )
                    )
                },
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryRoot.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(style): carry readOnly through style edit navigation"
```

---

## Task 6: Form VM read-only mode + flattened cap count

**Files:**
- Modify: `feature/style/presentation/form/StyleFormState.kt`
- Modify: `feature/style/presentation/form/StyleFormViewModel.kt`
- Test: `feature/style/presentation/form/StyleFormViewModelTest.kt`

- [ ] **Step 1: Add `readOnly` to state**

In `StyleFormState`, add:

```kotlin
    /** True when the style is shown read-only (a locked, over-cap style on Free). */
    val readOnly: Boolean = false,
```
Add the field to `equals`/`hashCode` if the class overrides them manually (it does — mirror the existing pattern for a Boolean).

- [ ] **Step 2: Write the failing tests**

Add to `StyleFormViewModelTest.kt` (reuse existing VM-build helpers; the VM reads args from `SavedStateHandle`):

```kotlin
@Test
fun readOnly_save_emitsUpgrade_doesNotPersist() = runTest {
    val vm = buildFormViewModel(customerId = "c1", styleId = "s1", readOnly = true) // helper sets SavedStateHandle
    vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }

    vm.events.test {
        vm.onAction(StyleFormAction.OnSaveClick)
        assertEquals(StyleFormEvent.NavigateToUpgrade, awaitItem())
    }
    assertNull(fakeStyleRepository.lastUpdatedStyle) // nothing written
}

@Test
fun readOnly_stateExposesReadOnly() = runTest {
    val vm = buildFormViewModel(customerId = "c1", styleId = "s1", readOnly = true)
    vm.state.test {
        assertTrue(awaitItem().readOnly)
        cancelAndIgnoreRemainingEvents()
    }
}
```

> If `buildFormViewModel` lacks a `readOnly` param, extend it to put `"readOnly" to readOnly` into the `SavedStateHandle`. Use the existing fake's write-capture (e.g. `lastUpdatedStyle`); if absent, capture it in `FakeStyleRepository.updateStyle`.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModelTest"`
Expected: FAIL — save persists / no Upgrade event; `readOnly` unset.

- [ ] **Step 4: Implement**

In `StyleFormViewModel.kt`:

Read the arg and seed state:

```kotlin
    private val readOnly: Boolean = savedStateHandle["readOnly"] ?: false
```
Update both `StyleFormState(...)` constructions (initial `_state` and `stateIn` initialValue) to pass `readOnly = readOnly`.

Guard the save action (find the `OnSaveClick`/`onContinue`/save handler). At the very top of the save handler:

```kotlin
        if (readOnly) {
            viewModelScope.launch { _events.send(StyleFormEvent.NavigateToUpgrade) }
            return
        }
```

Fix the Free cap count in `computeMaxPhotoSelection()` — count the flattened closet, not just `location`:

```kotlin
    private fun computeMaxPhotoSelection() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val cap = resolveImageCap()
            val current = currentClosetCount(userId)
            val remaining = (cap - current).coerceIn(1, STYLE_MULTI_PICK_CEILING)
            _state.update { it.copy(maxPhotoSelection = remaining) }
        }
    }

    /** On Free (no folders) the cap is per-closet, so count across ALL folders. */
    private suspend fun currentClosetCount(userId: String): Int {
        val tier = entitlements.awaitHydrated().tier
        val foldersEnabled = if (customerId == null) {
            StyleCollectionLimits.forInspiration(tier).foldersEnabled
        } else {
            StyleCollectionLimits.forCustomer(tier).foldersEnabled
        }
        if (foldersEnabled) {
            return when (val r = styleRepository.observeStyles(userId, location).first()) {
                is Result.Success -> r.data.size
                is Result.Error -> 0
            }
        }
        return when (val loc = location) {
            is StyleLocation.CustomerCloset ->
                styleRepository.observeAllCustomerStyles(userId, loc.customerId).first().size
            is StyleLocation.Inspiration ->
                styleRepository.observeAllInspirationStyles(userId).first().size
        }
    }
```
Add imports: `import com.danzucker.stitchpad.feature.style.domain.observeAllCustomerStyles`, `observeAllInspirationStyles`. (Adjust to match the actual existing `computeMaxPhotoSelection` body from the crash-fix; preserve its cap-resolution call `resolveImageCap()`.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormState.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt
git commit -m "feat(style): read-only form mode + flattened Free cap count"
```

---

## Task 7: Form screen — read-only rendering

**Files:**
- Modify: `feature/style/presentation/form/StyleFormScreen.kt`

- [ ] **Step 1: Render read-only**

In `StyleFormScreen`, when `state.readOnly`:
- Hide/disable the photo picker tap (no `OnPhotosPicked`), still show the existing photo (`state.existingStyle?.photoUrl` / preview) read-only.
- Render the description as read-only text (disable the `BasicTextField`/input — pass `enabled = false` / `readOnly = true`).
- Replace the Save button with a full-width Button labelled "Upgrade to edit" that calls `onAction(StyleFormAction.OnSaveClick)` (the VM converts it to an Upgrade event in read-only mode) — or add a dedicated `StyleFormAction.OnUpgradeClick` and emit `NavigateToUpgrade`. Use a new string resource `style_readonly_upgrade_cta` ("Upgrade to edit") in `composeResources` (no hardcoded strings, no `\'` — use `&apos;` if needed).

Minimal button swap:

```kotlin
if (state.readOnly) {
    Button(
        onClick = { onAction(StyleFormAction.OnSaveClick) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(Res.string.style_readonly_upgrade_cta)) }
} else {
    // existing Save button
}
```

- [ ] **Step 2: Update/Add the preview**

Add a `@Preview` `StyleFormScreenReadOnlyPreview` passing `state = StyleFormState(isEditMode = true, readOnly = true, existingStyle = <sample>)`. Keep the file's `@file:Suppress("TooManyFunctions")` if present (add it if the preview pushes over the limit).

- [ ] **Step 3: Verify compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormScreen.kt \
        composeApp/src/commonMain/composeResources/
git commit -m "feat(style): read-only style screen with Upgrade CTA"
```

---

## Task 8: Gallery screen — lock badge overlay

**Files:**
- Modify: `feature/style/presentation/gallery/StyleGalleryScreen.kt`

- [ ] **Step 1: Render the lock badge**

In the thumbnail item composable, when the style's id is in `state.lockedStyleIds`, overlay a lock affordance: a small rounded badge in a corner using `Icons.Filled.Lock` (or design-system equivalent) over a subtle scrim. Define BOTH light and dark treatment (per spec rule — e.g. scrim `Color.Black.copy(alpha = 0.32f)`, badge `MaterialTheme.colorScheme.surface`/`onSurface`). Keep the existing click/long-press wiring unchanged (the VM already routes locked taps to read-only / Upgrade). Add a `contentDescription` (e.g. `style_locked_a11y` = "Locked — upgrade to edit") for the lock icon.

```kotlin
Box {
    StyleThumbnail(style = style, /* existing */)
    if (style.id in state.lockedStyleIds) {
        Box(
            Modifier.matchParentSize().clip(/* same shape */).background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.TopEnd,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(Res.string.style_locked_a11y),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(6.dp).size(18.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(3.dp),
            )
        }
    }
}
```

Add string resource `style_locked_a11y`. (Match the screen's actual thumbnail composable name and layout; this is the overlay pattern, not necessarily the exact widget tree.)

- [ ] **Step 2: Verify compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryScreen.kt \
        composeApp/src/commonMain/composeResources/
git commit -m "feat(style): lock badge overlay on over-cap thumbnails"
```

---

## Task 9: Full verification + Inspiration parity

**Files:** none (verification).

- [ ] **Step 1: Confirm Inspiration uses the same gallery**

Grep that the Inspiration gallery routes through the same `StyleGalleryViewModel`/`StyleGalleryScreen` (customerId == null path). Confirm the flatten branch handles `StyleLocation.Inspiration` (it does via `foldersWithStylesFlow`/`locationFor`). No separate Inspiration screen change needed; if there IS one, apply the lock badge there too.

Run: `grep -rn "StyleGalleryViewModel\|StyleGalleryScreen\|StyleGalleryRoot" composeApp/src/commonMain/kotlin | grep -i inspiration`

- [ ] **Step 2: Full test + iOS compile + detekt**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 detekt
```
Expected: BUILD SUCCESSFUL; new StyleLockPolicy/gallery/form tests green.

- [ ] **Step 3: Manual smoke (device) — record results in the PR**

Follow the spec's "Manual smoke test" list (Pro→Free flatten + lock + read-only view + re-add gated; re-subscribe restores; Atelier→Pro per-folder lock; Inspiration parity).

- [ ] **Step 4: Commit any fixups, then open PR**

```bash
git push -u origin feat/freemium-readonly-styles
gh pr create --base main --title "feat(freemium): read-only locked styles instead of disappearing on downgrade" --body "<summary + smoke results; link spec>"
```

---

## Self-Review Notes

- **Spec coverage:** data-layer reuse (Task 3), lock policy (1), cap counting (3,6), presentation/read-only (6,7) + lock badge (8), scope customer+inspiration (3,9), Atelier→Pro per-folder (3). Out-of-scope folder-count over-cap is intentionally untouched.
- **Type consistency:** `lockedStyleIds: Set<String>` (state), `StyleLockPolicy.lockedStyleIds(styles, activeCap)`, `NavigateToEditStyle.readOnly`, `StyleFormRoute.readOnly`, `StyleFormState.readOnly`, `locationOf(styleId)`/`locationFor(folderId)` used consistently.
- **Known adaptation points** (engineer must match real code, flagged inline): exact `FakeStyleRepository` seeding helpers; the precise save-action name in `StyleFormViewModel`; the thumbnail composable name in `StyleGalleryScreen`; `StyleFormState` manual equals/hashCode. These are existing-code shapes to follow, not new decisions.
```
