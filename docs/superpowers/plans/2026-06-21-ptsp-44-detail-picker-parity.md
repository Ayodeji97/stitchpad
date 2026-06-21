# PTSP-44 — Order-Detail Saved-Style Picker Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Fix PTSP-44 (order-detail saved-style picker doesn't dismiss/give feedback after picking) by UNIFYING it with the order-form's redesigned batch picker — one shared `StylePickerSheet` (numbered select/deselect, "already added" dimmed, Closet/Inspiration tabs + folders, sticky Done) used by both screens.

**Architecture:** Relocate the form's `StylePickerSheet` to a shared `order/presentation/components/` package + add an optional `onCreateNew` callback. The order-detail screen adopts the same folder/source/pending state + toggle/commit MVI. KEY difference from the form: the detail **persists** on commit (`updateOrder` — it edits a live order), whereas the form mutates local state saved later. ViewModel logic is unit-tested; UI is manual-smoke-tested.

**Tech Stack:** KMP, Compose Multiplatform, Koin, GitLive Firestore.

**Spec/design:** parity with `docs/superpowers/specs/2026-06-20-style-image-selection-ux-design.md` Item 3 (the form picker, PR #204). Same numbered-badge/Done mockup.

---

## File structure
- MOVE: `feature/order/presentation/form/components/StylePickerSheet.kt` → `feature/order/presentation/components/StylePickerSheet.kt` (shared); add `onCreateNew: (() -> Unit)? = null` param.
- DELETE: `feature/order/presentation/detail/components/StylePickerSheet.kt` (the flat one).
- `feature/order/presentation/form/OrderFormScreen.kt` — update the import (file moved); pass `onCreateNew = null`.
- `feature/order/presentation/detail/OrderDetailState.kt` / `OrderDetailAction.kt` / `OrderDetailViewModel.kt` — add folders/source/pending + toggle/commit-with-persistence.
- `feature/order/presentation/detail/OrderDetailScreen.kt` — host the shared picker.
- Tests: new `composeApp/src/commonTest/.../detail/DetailStylePickerTest.kt`.

---

## Task 1: Relocate the shared picker + optional "Create new"

**Files:**
- Move: `feature/order/presentation/form/components/StylePickerSheet.kt` → `feature/order/presentation/components/StylePickerSheet.kt`
- Modify: `feature/order/presentation/form/OrderFormScreen.kt` (import + `onCreateNew = null`)

- [ ] **Step 1: Move the file + fix the package**

`git mv composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/components/StylePickerSheet.kt`
Change the file's `package` declaration to `com.danzucker.stitchpad.feature.order.presentation.components`. Keep all its private helpers (StylePickerCard, FolderGrid, badge code, Done bar) in the same file.

- [ ] **Step 2: Add the optional `onCreateNew` param**

In the `StylePickerSheet` signature (after `onDismiss`), add `onCreateNew: (() -> Unit)? = null`. Render a "Create new style" `TextButton` ABOVE the sticky Done `Button` (line ~231) only when `onCreateNew != null`:
```kotlin
        onCreateNew?.let { createNew ->
            TextButton(
                onClick = createNew,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.space4),
            ) { Text(stringResource(Res.string.style_picker_create_new)) }
        }
```
Add string `style_picker_create_new` = `"Create new style"` to `strings.xml`. Both the create-new button and the Done button live in the pinned (non-scrolling) bottom region.

- [ ] **Step 3: Fix the form's import**

In `OrderFormScreen.kt`, change `import ...form.components.StylePickerSheet` → `import com.danzucker.stitchpad.feature.order.presentation.components.StylePickerSheet`. Add `onCreateNew = null` is NOT needed (it defaults to null) — but verify the form call still compiles. Grep for any other importer of the old path and fix it.

- [ ] **Step 4: Compile + detekt + tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Then: `./gradlew :composeApp:testDebugUnitTest --tests '*Order*' -q`
Expected: green. The form behaves identically (onCreateNew defaults null → no create-new button in the form).

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "refactor(order): relocate StylePickerSheet to shared package + optional onCreateNew"
```

---

## Task 2: OrderDetail MVI — folders, source, pending, toggle/commit (persisted)

**Files:**
- Modify: `feature/order/presentation/detail/OrderDetailState.kt`
- Modify: `feature/order/presentation/detail/OrderDetailAction.kt`
- Modify: `feature/order/presentation/detail/OrderDetailViewModel.kt` (`loadStylesIfNeeded` L790-811, `linkExistingStyle` L813-841, dismiss handler)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/DetailStylePickerTest.kt`

- [ ] **Step 1: State**

`OrderDetailState.kt` — add (keep `availableStyles` for the hero/thumbnail lookups):
```kotlin
    val closetFolders: List<StylePickerFolder> = emptyList(),
    val inspirationFolders: List<StylePickerFolder> = emptyList(),
    val stylePickerSource: StylePickerSource = StylePickerSource.CLOSET,
    val pickerOpenFolderKey: String? = null,
    /** In-progress (uncommitted) saved-style picks for the open picker, in tap order. */
    val stylePickerPendingIds: List<String> = emptyList(),
```
Add imports `StylePickerFolder`, `StylePickerSource`.

- [ ] **Step 2: Actions**

`OrderDetailAction.kt` — REMOVE `OnSelectStyle`; add:
```kotlin
    data class OnStylePickerSourceChange(val source: StylePickerSource) : OrderDetailAction
    data class OnPickerFolderOpen(val folder: StylePickerFolder) : OrderDetailAction
    data object OnPickerFolderBack : OrderDetailAction
    data class OnItemTogglePendingStyle(val styleId: String) : OrderDetailAction
    data class OnItemCommitPendingStyles(val itemId: String) : OrderDetailAction
```
Keep `OnCreateNewStyleClick`, `OnDismissStylePickerSheet`, and the open-picker action (find it — it sets `showStylePickerSheet`/`stylePickerItemId`).

- [ ] **Step 3: VM — load folders (closet + inspiration)**

Replace `loadStylesIfNeeded` (L790-811): instead of merging flat lists, load FOLDERS like the form VM does (form VM L433 inspiration, L486 closet). Keep `availableStyles`/`styles` (the flat union + map) for the existing hero/thumbnail renderers — derive them from the folders:
```kotlin
    private fun loadStylesIfNeeded(customerId: String, userId: String) {
        if (loadedStylesCustomerId == customerId) return
        loadedStylesCustomerId = customerId
        styleJob?.cancel()
        styleJob = viewModelScope.launch {
            combine(
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.CustomerCloset(customerId)),
                styleRepository.observeFoldersWithStyles(userId, StyleLocation.Inspiration()),
            ) { closet, inspiration -> closet to inspiration }
                .collect { (closet, inspiration) ->
                    val merged = closet.flatMap { it.styles } + inspiration.flatMap { it.styles }
                    _state.update {
                        it.copy(
                            closetFolders = closet,
                            inspirationFolders = inspiration,
                            availableStyles = merged,
                            styles = merged.associateBy { s -> s.id },
                        )
                    }
                }
        }
    }
```
Add imports `observeFoldersWithStyles`, `StyleLocation`, `kotlinx.coroutines.flow.combine`. Verify `observeFoldersWithStyles` exists for `StyleLocation.Inspiration()` (form VM uses it at L433).

- [ ] **Step 4: Failing tests**

Create `DetailStylePickerTest.kt`. Read a sibling focused detail test (e.g. `StatusTransitionsTest.kt`/`PaymentMathTest.kt`) for how they build the `OrderDetailViewModel` + fakes (`FakeOrderRepository`, `FakeStyleRepository`, `FakeAuthRepository`) + `SavedStateHandle(orderId)`. Seed: an order with one item, the fake style repo with closet styles s1..s4 (use the fake's folder/style seeding — check `FakeStyleRepository.observeFoldersWithStyles`), open the picker. Tests:
- `togglePendingStyle_selectsThenDeselects` — toggle s1,s2 → [s1,s2]; toggle s1 → [s2].
- `togglePendingStyle_respectsCap` — with an item already at 2 styleImages, toggling allows only 1 pending (cap 3).
- `commitPendingStyles_persistsLibraryRefs_andClosesSheet` — toggle s1,s2; commit → the item's `styleImages` gained LIBRARY refs [s1,s2]; `stylePickerPendingIds` empty; `showStylePickerSheet == false`; AND the fake order repo received an `updateOrder` with those refs (assert via the fake's captured last-updated order).
- `dismiss_clearsPending`.
Run them → FAIL.

- [ ] **Step 5: VM — toggle + commit (persisted) + dismiss/source/folder handlers**

Add to `OrderDetailViewModel.onAction` (REMOVE the old `OnSelectStyle`/`linkExistingStyle`; reuse its `updateOrder` + error pattern in commit). `MAX_IMAGES_PER_CATEGORY` already exists in this VM.
```kotlin
            is OrderDetailAction.OnStylePickerSourceChange ->
                _state.update { it.copy(stylePickerSource = action.source, pickerOpenFolderKey = null) }
            is OrderDetailAction.OnPickerFolderOpen ->
                _state.update { it.copy(pickerOpenFolderKey = action.folder.key) }
            OrderDetailAction.OnPickerFolderBack ->
                _state.update { it.copy(pickerOpenFolderKey = null) }
            is OrderDetailAction.OnItemTogglePendingStyle -> _state.update { st ->
                val itemId = st.stylePickerItemId ?: return@update st
                val item = st.order?.items?.find { it.id == itemId } ?: return@update st
                val committedSlots = item.styleImages.size
                val pending = st.stylePickerPendingIds
                val newPending = when {
                    action.styleId in pending -> pending - action.styleId
                    committedSlots + pending.size >= MAX_IMAGES_PER_CATEGORY -> pending
                    else -> pending + action.styleId
                }
                st.copy(stylePickerPendingIds = newPending)
            }
            is OrderDetailAction.OnItemCommitPendingStyles -> commitPendingStyles(action.itemId)
            OrderDetailAction.OnDismissStylePickerSheet ->
                _state.update {
                    it.copy(showStylePickerSheet = false, stylePickerItemId = null,
                            stylePickerPendingIds = emptyList(), pickerOpenFolderKey = null)
                }
```
And the persisted commit (mirror `linkExistingStyle`'s persistence):
```kotlin
    private fun commitPendingStyles(itemId: String) {
        val st = _state.value
        val pending = st.stylePickerPendingIds
        val order = st.order ?: return
        val item = order.items.firstOrNull { it.id == itemId } ?: return
        val existing = item.styleImages.mapNotNull { it.styleId }.toSet()
        val room = (MAX_IMAGES_PER_CATEGORY - item.styleImages.size).coerceAtLeast(0)
        val toAdd = pending.filter { it !in existing }.take(room)
            .map { StyleImageRef(source = StyleImageSource.LIBRARY, styleId = it) }
        val updatedItems = order.items.map {
            if (it.id == itemId) it.copy(styleImages = it.styleImages + toAdd) else it
        }
        // Optimistic local update + close, then persist.
        _state.update {
            it.copy(
                order = order.copy(items = updatedItems),
                stylePickerPendingIds = emptyList(),
                showStylePickerSheet = false,
                stylePickerItemId = null,
                pickerOpenFolderKey = null,
            )
        }
        if (toAdd.isEmpty()) return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val res = orderRepository.updateOrder(userId, order.copy(items = updatedItems))) {
                is Result.Success -> Unit
                is Result.Error -> _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
            }
        }
    }
```
Also make the OPEN-picker handler clear `stylePickerPendingIds` (find it; add `stylePickerPendingIds = emptyList()`).

- [ ] **Step 6: Run tests → PASS; compile/detekt**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*DetailStylePicker*' --tests '*Order*' -q`
Then: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Fix any caller of the removed `OnSelectStyle` (the Screen, Task 3).

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(order): detail saved-style picker — folders + batch toggle/commit (persisted)"
```

---

## Task 3: OrderDetailScreen — host the shared picker; delete the flat one

**Files:**
- Modify: `feature/order/presentation/detail/OrderDetailScreen.kt:496-521` + the import
- Delete: `feature/order/presentation/detail/components/StylePickerSheet.kt`

- [ ] **Step 1: Swap the import + hosting**

Change the import to `com.danzucker.stitchpad.feature.order.presentation.components.StylePickerSheet`. Replace the hosting block (L496-521) with:
```kotlin
    if (state.showStylePickerSheet && state.order != null) {
        val pickerItemId = state.stylePickerItemId ?: state.order.items.firstOrNull()?.id
        val targetItem = state.order.items.firstOrNull { it.id == pickerItemId }
        if (pickerItemId != null && targetItem != null) {
            val alreadyAddedIds = targetItem.styleImages
                .filter { it.source == StyleImageSource.LIBRARY }
                .mapNotNull { it.styleId }.toSet()
            StylePickerSheet(
                closetFolders = state.closetFolders,
                inspirationFolders = state.inspirationFolders,
                selectedSource = state.stylePickerSource,
                onSourceChange = { onAction(OrderDetailAction.OnStylePickerSourceChange(it)) },
                pickerOpenFolderKey = state.pickerOpenFolderKey,
                onFolderOpen = { onAction(OrderDetailAction.OnPickerFolderOpen(it)) },
                onFolderBack = { onAction(OrderDetailAction.OnPickerFolderBack) },
                alreadyAddedStyleIds = alreadyAddedIds,
                committedSlots = targetItem.styleImages.size,
                pendingStyleIds = state.stylePickerPendingIds,
                maxRefs = MAX_STYLE_IMAGES,
                onToggle = { onAction(OrderDetailAction.OnItemTogglePendingStyle(it.id)) },
                onDone = { onAction(OrderDetailAction.OnItemCommitPendingStyles(pickerItemId)) },
                onDismiss = { onAction(OrderDetailAction.OnDismissStylePickerSheet) },
                onCreateNew = { onAction(OrderDetailAction.OnCreateNewStyleClick(pickerItemId)) },
            )
        }
    }
```
(`MAX_STYLE_IMAGES` already exists in this file = 3.)

- [ ] **Step 2: Delete the flat picker**

`git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/StylePickerSheet.kt`. Remove any now-orphaned strings it used (grep their keys first; only remove zero-reference ones).

- [ ] **Step 3: Compile (all gates) + detekt + tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Then: `./gradlew :composeApp:testDebugUnitTest --tests '*Order*' --tests '*DetailStylePicker*' -q`
Expected: all green.

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "feat(order): order-detail uses the shared batch style picker (PTSP-44)"
```

---

## Manual smoke test (device — Daniel is QA)
1. Open an existing order → an item → Add style reference → **Choose saved style**.
2. The picker now has **Closet / Inspiration tabs** + folders (parity with the order form). Switch to **Inspiration**, tap 2 styles (badges ①②), deselect one, an "Already added" style stays dimmed; at 3 total the rest gray out.
3. Tap **Done** → the picker **dismisses** and the styles attach to the item (persisted — reopen the order to confirm). This fixes PTSP-44's "doesn't leave the screen / no feedback".
4. **"Create new style"** button still works (navigates to the style-create form).

## Self-review notes
- Unify = one shared `StylePickerSheet` (Task 1 relocate + optional onCreateNew); detail flat picker deleted (Task 3). ✓
- Detail commit PERSISTS via `updateOrder` (Task 2 `commitPendingStyles`), unlike the form's local state. ✓
- Cap = 3 on `styleImages.size` (detail has no separate uploaded list — uploads are UPLOADED refs already in styleImages). ✓
- iOS **test** compile in every gate. ✓
- `OnSelectStyle`/`linkExistingStyle` removed; toggle/commit replace them. ✓
