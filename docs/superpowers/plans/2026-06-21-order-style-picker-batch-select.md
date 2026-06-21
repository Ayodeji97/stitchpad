# Order Style-Picker Batch-Select Implementation Plan (Item 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** In the New/Edit-Order "Add style reference" flow, make phone-gallery picking multi-select (up to remaining cap), and redesign the saved-style picker to batch-select — tap to select/deselect with a numbered badge, "already added" dimmed and counting toward the cap of 3, committing all picks via a sticky **Done** bar (no more tap-immediately-adds + auto-dismiss).

**Architecture:** MVI. The picker's in-progress selection lives in `OrderFormState` (per "all state in ViewModel"). The `StylePickerSheet` stays a stateless composable driven by props (pending ids ordered, already-added ids, capacity, toggle/done callbacks). Cap = 3 = alreadyAdded + pending. ViewModel logic is unit-tested; UI is manual-smoke-tested.

**Tech Stack:** KMP, Compose Multiplatform, peekaboo picker (`SelectionMode.Multiple` + the `styleFormSelectionMode` ≤1→Single guard), Koin, GitLive Firestore.

**Spec:** `docs/superpowers/specs/2026-06-21-style-image-selection-ux-design.md` (Item 3). Locked mockup: `.superpowers/brainstorm/61749-1782058084/content/item3-saved-style-picker-v3.html`.

---

## File structure
- `feature/order/presentation/form/OrderFormScreen.kt` — gallery picker → multi; host the redesigned `StylePickerSheet` with pending/done.
- `feature/order/presentation/detail/OrderDetailScreen.kt` — gallery picker → multi.
- `feature/order/presentation/form/OrderFormState.kt` — add `stylePickerPendingIds: List<String>`.
- `feature/order/presentation/form/OrderFormAction.kt` — add `OnItemTogglePendingStyle`, `OnItemCommitPendingStyles`; keep `OnDismissStylePickerSheet`/`OnOpenStylePickerSheet` (now also clear/seed pending). Remove `OnItemPickSavedStyle` (replaced).
- `feature/order/presentation/form/OrderFormViewModel.kt` — toggle/commit handlers + clear-on-open/dismiss.
- `feature/order/presentation/form/components/StylePickerSheet.kt` — numbered badges, deselect, Done bar, already-added.
- `composeApp/src/commonMain/composeResources/values/strings.xml` — new strings.
- Tests: `OrderFormViewModelTest.kt`.

---

## Task 1: Phone-gallery multi-select (OrderForm + OrderDetail)

**Files:**
- Modify: `feature/order/presentation/form/OrderFormScreen.kt:1370-1378`
- Modify: `feature/order/presentation/detail/OrderDetailScreen.kt:1060-1068`

Camera stays single (unchanged). The peekaboo gallery picker becomes `Multiple` up to the remaining slots, reusing the existing `styleFormSelectionMode(allowMultiPhoto, maxPhotoSelection)` helper (`feature/style/presentation/form/StyleFormSelectionMode.kt`) which already guards the `Multiple(1)` crash.

- [ ] **Step 1: OrderForm gallery picker → multi**

In `OrderFormScreen.kt`, where `styleGalleryPicker` is created (inside the item scope, `item` available), compute remaining and key the picker on it:
```kotlin
val styleRemaining = (MAX_IMAGES_PER_CATEGORY -
    (item.styleImageRefs.size + item.uploadedStyleBytesList.size)).coerceAtLeast(1)
val styleGalleryPicker = key(styleRemaining) {
    rememberImagePickerLauncher(
        selectionMode = styleFormSelectionMode(allowMultiPhoto = true, maxPhotoSelection = styleRemaining),
        scope = pickerScope,
        onResult = { byteArrays ->
            byteArrays.forEach { onAction(OrderFormAction.OnItemAddStylePhoto(item.id, it)) }
        },
    )
}
```
Add the import `com.danzucker.stitchpad.feature.style.presentation.form.styleFormSelectionMode`. The VM's `addStylePhoto` already cap-guards each add (`total >= 3 → no-op`), so the per-photo loop is safe; the picker cap is the primary limiter.

- [ ] **Step 2: OrderDetail gallery picker → multi**

Same change in `OrderDetailScreen.kt`. The detail screen tracks the target item via `pendingStylePhotoItemId`; compute remaining for THAT item. If the detail screen doesn't have the item's ref counts handy at picker-construction time, compute remaining from `state` for `pendingStylePhotoItemId` (read how the detail screen accesses the item's `styleImageRefs`/uploaded list — mirror the form). Loop `byteArrays.forEach { onAction(OrderDetailAction.OnAddStylePhoto(itemId, it)) }`.

- [ ] **Step 3: Compile + smoke-build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Expected: clean. (Note the iOS **test** compile — required gate.)

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "feat(order): phone-gallery style pick is multi-select up to remaining cap"
```

---

## Task 2: Pending-selection state + actions + ViewModel

**Files:**
- Modify: `OrderFormState.kt`, `OrderFormAction.kt`, `OrderFormViewModel.kt:171-189`
- Test: `OrderFormViewModelTest.kt`

- [ ] **Step 1: State + actions**

`OrderFormState.kt` — add near the picker fields:
```kotlin
    /** In-progress (uncommitted) saved-style picks for the open picker, in tap order. */
    val stylePickerPendingIds: List<String> = emptyList(),
```
`OrderFormAction.kt` — remove `OnItemPickSavedStyle`; add:
```kotlin
    /** Toggle a saved style in the open picker's pending selection (select/deselect). */
    data class OnItemTogglePendingStyle(val styleId: String) : OrderFormAction
    /** Commit all pending picks as LIBRARY refs on the item, then close the picker. */
    data class OnItemCommitPendingStyles(val itemId: String) : OrderFormAction
```

- [ ] **Step 2: Failing VM tests**

Add to `OrderFormViewModelTest.kt` (match the existing `createViewModel`/fake setup; seed a style library via `styleRepository` so the picker has styles, and open an order with one item). Cover:
```kotlin
    @Test
    fun togglePendingStyle_selectsThenDeselects() = runTest {
        val vm = createOrderWithItemAndStyles() // helper: order + 1 item + closet styles s1,s2,s3
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        assertThat(vm.state.value.stylePickerPendingIds).containsExactly("s1")
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        assertThat(vm.state.value.stylePickerPendingIds).containsExactly("s1", "s2").inOrder()
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1")) // deselect
        assertThat(vm.state.value.stylePickerPendingIds).containsExactly("s2")
    }

    @Test
    fun togglePendingStyle_respectsCapWithAlreadyAdded() = runTest {
        val vm = createOrderWithItemAndStyles()
        val itemId = vm.state.value.items.first().id
        // 1 already added (commit s1 first), then pending can only reach 2 more (cap 3).
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId)) // s1 committed; pending cleared
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s3"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s4")) // would be 4th → blocked
        assertThat(vm.state.value.stylePickerPendingIds).containsExactly("s2", "s3")
    }

    @Test
    fun commitPendingStyles_appendsLibraryRefs_andClearsPending() = runTest {
        val vm = createOrderWithItemAndStyles()
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))
        val refs = vm.state.value.items.first().styleImageRefs
        assertThat(refs.mapNotNull { it.styleId }).containsExactly("s1", "s2").inOrder()
        assertThat(refs.all { it.source == StyleImageSource.LIBRARY }).isTrue()
        assertThat(vm.state.value.stylePickerPendingIds).isEmpty()
        assertThat(vm.state.value.stylePickerSheetForItemId).isNull() // sheet closed
    }

    @Test
    fun openingOrDismissingPicker_clearsPending() = runTest {
        val vm = createOrderWithItemAndStyles()
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnDismissStylePickerSheet)
        assertThat(vm.state.value.stylePickerPendingIds).isEmpty()
    }
```
Write `createOrderWithItemAndStyles()` mirroring existing helpers (the file already seeds styles + opens an order; reuse those patterns — the fake `styleRepository` and an `orderId` from a `FakeOrderRepository` order with one item, or the new-order path that starts with one item).

- [ ] **Step 3: Run tests, verify FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*OrderFormViewModelTest*'`
Expected: FAIL (actions unhandled).

- [ ] **Step 4: ViewModel handlers**

In `OrderFormViewModel.onAction`, REMOVE the `OnItemPickSavedStyle` branch (lines ~171-189) and add:
```kotlin
            is OrderFormAction.OnItemTogglePendingStyle -> _state.update { st ->
                val itemId = st.stylePickerSheetForItemId ?: return@update st
                val item = st.items.find { it.id == itemId } ?: return@update st
                // All already-committed slots count toward the cap (LIBRARY refs AND
                // session-uploaded photos), not just LIBRARY refs.
                val committedSlots = item.styleImageRefs.size + item.uploadedStyleBytesList.size
                val pending = st.stylePickerPendingIds
                val newPending = when {
                    action.styleId in pending -> pending - action.styleId            // deselect
                    committedSlots + pending.size >= MAX_STYLE_REFS -> pending         // cap full → no-op
                    else -> pending + action.styleId                                   // select
                }
                st.copy(stylePickerPendingIds = newPending)
            }
            is OrderFormAction.OnItemCommitPendingStyles -> _state.update { st ->
                val pending = st.stylePickerPendingIds
                val withRefs = st.items.map { item ->
                    if (item.id != action.itemId) return@map item
                    val existing = item.styleImageRefs.mapNotNull { it.styleId }.toSet()
                    val toAdd = pending.filter { it !in existing }
                        .map { StyleImageRef(source = StyleImageSource.LIBRARY, styleId = it) }
                    item.copy(styleImageRefs = item.styleImageRefs + toAdd)
                }
                st.copy(
                    items = withRefs,
                    stylePickerPendingIds = emptyList(),
                    stylePickerSheetForItemId = null,
                    pickerOpenFolderKey = null,
                )
            }
```
Add `private const val MAX_STYLE_REFS = 3` near the top of the VM file (or reuse the existing screen constant by lifting it — prefer a single source; if `MAX_IMAGES_PER_CATEGORY` is `private` in the Screen, define `MAX_STYLE_REFS = 3` in the VM and leave the screen's as-is, or lift to a shared `const`). Make the `OnDismissStylePickerSheet` and `OnOpenStylePickerSheet` handlers ALSO clear `stylePickerPendingIds` (dismiss: clear; open: clear/start fresh). If those handlers live elsewhere, update them.

- [ ] **Step 5: Run tests, verify PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*OrderFormViewModelTest*'`
Expected: PASS. Fix any existing test that referenced `OnItemPickSavedStyle` (re-express via toggle+commit).

- [ ] **Step 6: Compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(order): batch pending-selection for saved-style picker (toggle + commit)"
```

---

## Task 3: StylePickerSheet redesign UI + wiring

**Files:**
- Modify: `feature/order/presentation/form/components/StylePickerSheet.kt`
- Modify: `feature/order/presentation/form/OrderFormScreen.kt:333-362` (host with new props)
- Modify: `strings.xml`

- [ ] **Step 1: Strings**
```xml
    <string name="style_picker_done_fmt">Done · %d selected</string>
    <string name="style_picker_cap_fmt">%1$d selected · %2$d already added · %3$d of %4$d</string>
    <string name="style_picker_remove_photo_hint">Remove already-added refs from the references row</string>
    <string name="cd_style_selected_fmt">Selected, position %d</string>
    <string name="cd_style_select">Select style</string>
```
(Keep the existing `style_picker_already_added` string.)

- [ ] **Step 2: New `StylePickerSheet` signature**

Replace `alreadySelectedStyleIds` + `onSelect` with the batch props:
```kotlin
fun StylePickerSheet(
    closetFolders: List<StylePickerFolder>,
    inspirationFolders: List<StylePickerFolder>,
    selectedSource: StylePickerSource,
    onSourceChange: (StylePickerSource) -> Unit,
    pickerOpenFolderKey: String?,
    onFolderOpen: (StylePickerFolder) -> Unit,
    onFolderBack: () -> Unit,
    alreadyAddedStyleIds: Set<String>,   // committed LIBRARY ref styleIds — dimmed + "Already added"
    committedSlots: Int,                 // all used slots (LIBRARY refs + uploaded photos)
    pendingStyleIds: List<String>,       // ordered new picks — numbered badges
    maxRefs: Int,                        // 3
    onToggle: (Style) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
)
```

- [ ] **Step 3: Card states + numbered badge**

Replace `StylePickerCard`'s disabled/label logic with three states. For each style card:
```kotlin
val alreadyAdded = style.id in alreadyAddedStyleIds
val selectedIndex = pendingStyleIds.indexOf(style.id)   // -1 if not pending
val isSelected = selectedIndex >= 0
val capFull = committedSlots + pendingStyleIds.size >= maxRefs
val selectable = !alreadyAdded && (isSelected || !capFull)
```
Render the thumbnail in a `Box`; overlay top-end:
- `alreadyAdded` → the "✓ Already added" pill (existing `style_picker_already_added`); card alpha 0.5; not clickable.
- `isSelected` → a filled badge: a `Box` size 26.dp, `CircleShape`, `LocalStitchPadColors.current.brandAccent` background, 2.dp white border, centered `Text("${selectedIndex + 1}")` white bold. `contentDescription` = `cd_style_selected_fmt`. Card outlined `2.5.dp` brandAccent. Clickable → `onToggle(style)` (deselect).
- selectable & !selected → an empty ring: `Box` size 26.dp, `CircleShape`, 2.dp `Color.White.copy(alpha=.6f)` border, transparent. Clickable → `onToggle(style)`.
- !selectable (cap full, not selected, not added) → card alpha 0.35, empty dim ring, not clickable.

Keep the `SubcomposeAsyncImage` + its `LoadingDots` loading slot. Show `style.description` (the title) below if non-blank, else nothing (no "Untitled" needed here — thumbnail identifies it).

- [ ] **Step 4: Cap header line + sticky Done bar**

Above the grid, show the cap line: `stringResource(style_picker_cap_fmt, pendingStyleIds.size, committedSlots, committedSlots + pendingStyleIds.size, maxRefs)` (selected · already-added · used-of-max).
At the BOTTOM of the sheet (outside the scrolling grid, sticky), a full-width Button:
```kotlin
Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
    Text(stringResource(Res.string.style_picker_done_fmt, pendingStyleIds.size))
}
```
The Done button commits even when `pendingStyleIds` is empty (it just closes — harmless), but you may disable it when empty for clarity: `enabled = pendingStyleIds.isNotEmpty()`. Use `enabled = pendingStyleIds.isNotEmpty()`.

- [ ] **Step 5: Host the sheet in OrderFormScreen**

Replace the `StylePickerSheet(...)` call (lines 333-362) — drop the auto-dismiss-on-cap `onSelect`; pass:
```kotlin
        val alreadyAddedIds = targetItem.styleImageRefs
            .filter { it.source == StyleImageSource.LIBRARY }
            .mapNotNull { it.styleId }.toSet()
        StylePickerSheet(
            closetFolders = state.closetFolders,
            inspirationFolders = state.inspirationFolders,
            selectedSource = state.stylePickerSource,
            onSourceChange = { onAction(OrderFormAction.OnStylePickerSourceChange(it)) },
            pickerOpenFolderKey = state.pickerOpenFolderKey,
            onFolderOpen = { onAction(OrderFormAction.OnPickerFolderOpen(it)) },
            onFolderBack = { onAction(OrderFormAction.OnPickerFolderBack) },
            alreadyAddedStyleIds = alreadyAddedIds,
            committedSlots = targetItem.styleImageRefs.size + targetItem.uploadedStyleBytesList.size,
            pendingStyleIds = state.stylePickerPendingIds,
            maxRefs = MAX_IMAGES_PER_CATEGORY,
            onToggle = { onAction(OrderFormAction.OnItemTogglePendingStyle(it.id)) },
            onDone = { onAction(OrderFormAction.OnItemCommitPendingStyles(itemId)) },
            onDismiss = { onAction(OrderFormAction.OnDismissStylePickerSheet) },
        )
```
Cap consistency: `committedSlots = styleImageRefs.size + uploadedStyleBytesList.size` is used for the sheet's `capFull` AND mirrored in the VM toggle handler (Task 2 Step 4 uses the same `committedSlots` expression). So the cap of 3 spans LIBRARY refs + uploaded photos + pending, identically in UI and VM.

- [ ] **Step 6: Compile + detekt + tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Then: `./gradlew :composeApp:testDebugUnitTest --tests '*Order*' -q`
Expected: all clean/pass.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(order): saved-style picker — numbered select/deselect + Done (PTSP-43)"
```

---

## Manual smoke test (device — Daniel is QA)

1. New order → item → Add style reference → **Pick from phone gallery** → select 2 photos at once → both attach (up to 3 total).
2. Add style reference → **Choose saved style** → tap 2 styles (badges ①②), tap one again to deselect (renumbers), an "Already added" style stays dimmed and counts toward 3; with 3 total the rest gray out; tap **Done · N selected** → both attach; reopening shows them as "Already added".
3. Confirm camera is still single-capture.

## Self-review notes
- 3a gallery multi (Task 1), 3b/3c batch picker (Tasks 2–3). ✓
- Cap = 3 across LIBRARY refs + pending + uploaded; enforced in VM toggle AND the sheet's `maxRefs`. ✓
- `OnItemPickSavedStyle` removed; toggle/commit replace it. Update any test/caller. ✓
- iOS **test** compile in every gate (the JVM-only-API trap). ✓
