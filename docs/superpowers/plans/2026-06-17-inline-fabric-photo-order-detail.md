# Inline "Add fabric photo" on Order Details — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping "Add fabric photo" on the Order Details screen adds the photo inline (Camera/Gallery sheet → upload → attach), staying on the screen, instead of navigating back to the order form.

**Architecture:** Mirror the existing inline "Add style" flow (`OrderDetailViewModel.linkExistingStyle`) and the order form's fabric upload. Tapping the CTA opens a shared `PhotoSourceSheet`; the chosen source launches a peekaboo camera/gallery picker via the order form's "launch-after-dismiss" pattern; the returned bytes go through `orderRepository.uploadFabricPhotos` (offline-first) and are appended as a `FabricImageRef` to the first item, persisted with `updateOrder`. Scope: the **first** fabric photo (the empty-slot case); more photos still use the edit form.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, peekaboo image picker, GitLive Firebase Storage, Koin.

**Spec:** `docs/superpowers/specs/2026-06-16-inline-fabric-photo-order-detail-design.md`
**Branch:** `feat/inline-fabric-photo`

**Conventions (CLAUDE.md + project memory):**
- No hardcoded user-facing strings — use `compose.resources` (the photo-source strings already exist).
- All state in the ViewModel (picker launchers + `pendingSource` are Compose-internal UI plumbing, the allowed exception — same as the order form).
- Run iOS compile before declaring done; capture gradle exit codes directly.
- Every Screen/visible composable keeps its `@Preview`.

**Testing note:** there is **no `OrderDetailViewModelTest`** harness — the VM takes ~12 deps including platform types (`ImageLoader`, `PlatformContext`, `OrderReceiptSharer`) not constructable in `commonTest`, which is why `linkExistingStyle` ships without a unit test. This change is verified by build gates + the manual smoke test in Task 5. The new handler is a faithful mirror of already-shipped code.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PhotoSourceSheet.kt` — reusable Camera/Gallery bottom sheet + `PhotoSource` enum.

**Modify:**
- `feature/order/presentation/detail/OrderDetailState.kt` — add `showFabricSourceSheet`, `isUploadingFabric`.
- `feature/order/presentation/detail/OrderDetailAction.kt` — add `OnFabricPhotoPicked`, `OnDismissFabricSourceSheet`.
- `feature/order/presentation/detail/OrderDetailViewModel.kt` — repurpose `OnAddFabricClick`, add the two handlers + the upload/append/persist function.
- `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` — thread an `isUploadingFabric` loading flag into the fabric slot.
- `feature/order/presentation/detail/OrderDetailScreen.kt` — fabric picker launchers + render `PhotoSourceSheet` + pass the loading flag.

---

## Task 1: PhotoSourceSheet shared composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PhotoSourceSheet.kt`

No unit test (stateless UI). Verified by compile + its `@Preview`.

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_photo_pick
import stitchpad.composeapp.generated.resources.order_form_photo_pick_support
import stitchpad.composeapp.generated.resources.order_form_photo_take
import stitchpad.composeapp.generated.resources.order_form_photo_take_support

/** Where a photo comes from. Shared so the detail screen and order form agree. */
enum class PhotoSource { Camera, Gallery }

/**
 * A small bottom sheet offering "Take photo" / "Choose from gallery". The caller is
 * responsible for dismissing the sheet and launching the matching picker after dismiss
 * (the launch-after-dismiss pattern that sidesteps the iOS present-after-dismiss timing bug).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(
    onPick: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
            supportingContent = { Text(stringResource(Res.string.order_form_photo_take_support)) },
            leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            modifier = Modifier.clickable { onPick(PhotoSource.Camera) },
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
            supportingContent = { Text(stringResource(Res.string.order_form_photo_pick_support)) },
            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.clickable { onPick(PhotoSource.Gallery) },
        )
    }
}
```

(`order_form_photo_take`, `order_form_photo_take_support`, `order_form_photo_pick`, `order_form_photo_pick_support` already exist in `strings.xml` — reused as-is. The wording is source-generic, not order-form-specific.)

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :composeApp:assembleDebug; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PhotoSourceSheet.kt
git commit -m "feat(ui): shared PhotoSourceSheet (Camera/Gallery) component"
```

---

## Task 2: State + Action + ViewModel handler

**Files:**
- Modify: `feature/order/presentation/detail/OrderDetailState.kt`
- Modify: `feature/order/presentation/detail/OrderDetailAction.kt`
- Modify: `feature/order/presentation/detail/OrderDetailViewModel.kt`

- [ ] **Step 1: Add state fields**

In `OrderDetailState.kt`, add next to `showStylePickerSheet`:

```kotlin
    val showFabricSourceSheet: Boolean = false,
    val isUploadingFabric: Boolean = false,
```

- [ ] **Step 2: Add actions**

In `OrderDetailAction.kt`, add next to the existing fabric actions (after `OnAddFabricClick`):

```kotlin
    data class OnFabricPhotoPicked(val photoBytes: ByteArray) : OrderDetailAction
    data object OnDismissFabricSourceSheet : OrderDetailAction
```

(A `data class` with a `ByteArray` mirrors the order form's `OnItemAddFabricPhoto` — the same shape already passes detekt in this codebase.)

- [ ] **Step 3: Add imports + constants to the ViewModel**

In `OrderDetailViewModel.kt`, add these imports (alongside the existing `core.domain.model` imports):

```kotlin
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import stitchpad.composeapp.generated.resources.error_order_photo_too_large
```

Add two private top-level constants near the top of the file (above the class, next to any existing file-level consts):

```kotlin
private const val MAX_FABRIC_IMAGES = 3
private const val MAX_ORDER_PHOTO_BYTES = 5 * 1024 * 1024
```

- [ ] **Step 4: Repurpose `OnAddFabricClick` and add the two handlers**

In the `when (action)` block, REPLACE the current `OnAddFabricClick` branch (which sends `NavigateToOrderForm`):

```kotlin
            OrderDetailAction.OnAddFabricClick -> {
                // Open the inline Camera/Gallery sheet instead of navigating to the form.
                // Defensive cap guard (the CTA only shows when the slot is empty).
                val firstItem = _state.value.order?.items?.firstOrNull()
                if (firstItem != null && firstItem.fabricImages.size < MAX_FABRIC_IMAGES) {
                    _state.update { it.copy(showFabricSourceSheet = true) }
                }
            }
            OrderDetailAction.OnDismissFabricSourceSheet ->
                _state.update { it.copy(showFabricSourceSheet = false) }
            is OrderDetailAction.OnFabricPhotoPicked -> addFabricPhoto(action.photoBytes)
```

(`NavigateToOrderForm` stays in `OrderDetailEvent` and is still used by the general edit CTA at ~line 125 — do not remove it.)

- [ ] **Step 5: Add the `addFabricPhoto` function**

Add this private function near `linkExistingStyle` (mirror its structure):

```kotlin
    private fun addFabricPhoto(photoBytes: ByteArray) {
        _state.update { it.copy(showFabricSourceSheet = false) }
        if (photoBytes.size > MAX_ORDER_PHOTO_BYTES) {
            _state.update {
                it.copy(errorMessage = UiText.StringResourceText(Res.string.error_order_photo_too_large))
            }
            return
        }
        val order = _state.value.order ?: return
        val firstItem = order.items.firstOrNull() ?: return
        if (firstItem.fabricImages.size >= MAX_FABRIC_IMAGES) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            _state.update { it.copy(isUploadingFabric = true) }
            // Offline-first: uploadFabricPhotos saves a local copy + enqueues the upload and
            // returns (localPath, storagePath); the local image renders immediately and syncs
            // later. (The single uploadFabricPhoto blocks online and is NOT offline-first.)
            when (
                val upload = orderRepository.uploadFabricPhotos(
                    userId = userId,
                    orderId = order.id,
                    itemId = firstItem.id,
                    photoBytesList = listOf(photoBytes),
                )
            ) {
                is Result.Success -> {
                    val newRefs = upload.data.map { (localPath, path) ->
                        FabricImageRef(
                            photoUrl = "",
                            photoStoragePath = path,
                            syncState = ImageSyncState.PENDING,
                            localPhotoPath = localPath,
                        )
                    }
                    val updatedItem = firstItem.copy(fabricImages = firstItem.fabricImages + newRefs)
                    val updatedItems = listOf(updatedItem) + order.items.drop(1)
                    when (
                        val res = orderRepository.updateOrder(userId, order.copy(items = updatedItems))
                    ) {
                        is Result.Success -> Unit
                        is Result.Error -> _state.update {
                            it.copy(errorMessage = res.error.toOrderUiText())
                        }
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(errorMessage = upload.error.toOrderUiText())
                }
            }
            _state.update { it.copy(isUploadingFabric = false) }
        }
    }
```

- [ ] **Step 6: Build + detekt + iOS compile**

Run: `./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`. If detekt flags the `ByteArray` `data class` (`ArrayInDataClass`), confirm the order form's `OnItemAddFabricPhoto` carries the same and matches its handling (it passes today); only suppress if the order form does.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt
git commit -m "feat(order-detail): add inline fabric-photo upload handler"
```

---

## Task 3: Fabric slot loading state

**Files:**
- Modify: `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`

Thread an `isUploadingFabric` flag down to the first item's fabric slot so it shows `LoadingDots` during the upload+persist gap. No unit test (UI); keep all `@Preview`s compiling.

- [ ] **Step 1: Add `isLoading` to `ReferenceColumn`**

In `ReferenceColumn`, add a parameter `isLoading: Boolean = false` (after `urls`), and make the empty branch show a loading box. Replace the `when { urls.isEmpty() -> ReferencePlaceholder(...) ... }` head so loading takes precedence:

```kotlin
@Composable
private fun ReferenceColumn(
    label: String,
    icon: ImageVector,
    urls: List<String>,
    isLoading: Boolean = false,
    ctaLabel: StringResource?,
    onCtaClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
```

and in the `when`:

```kotlin
        when {
            isLoading && urls.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REFERENCE_MEDIA_HEIGHT)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { LoadingDots(dotSize = 6.dp) }
            urls.isEmpty() -> ReferencePlaceholder(
                icon = icon,
                onClick = if (ctaLabel != null) onCtaClick else null,
            )
            urls.size == 1 -> SingleReferenceTile(...)
            else -> MultiReferenceStrip(...)
        }
```

Keep the existing `urls.size == 1` / `else` branches unchanged. Hide the CTA while loading by guarding the trailing CTA block:

```kotlin
        if (ctaLabel != null && !isLoading) {
            Spacer(Modifier.height(DesignTokens.space1))
            TextButton(onClick = onCtaClick, ...) { ... }
        }
```

Use the same fixed media height the placeholder/tiles already use. If a named constant (e.g. `REFERENCE_MEDIA_HEIGHT`) does not already exist in the file, reuse the literal height that `ReferencePlaceholder`/`SingleReferenceTile` use (read those composables and match — do not invent a new size). `LoadingDots` is imported from `com.danzucker.stitchpad.ui.components.LoadingDots`; `Box`, `height`, `background`, `clip`, `RoundedCornerShape` are already used in this file.

- [ ] **Step 2: Thread the flag through `FabricColumn`**

Add `isUploadingFabric: Boolean = false` to `FabricColumn` (after `showCta`), and pass it to `ReferenceColumn`:

```kotlin
private fun FabricColumn(
    item: OrderItem,
    showCta: Boolean,
    isUploadingFabric: Boolean = false,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ...
    ReferenceColumn(
        label = stringResource(Res.string.order_detail_fabric_caption),
        icon = Icons.Default.Texture,
        urls = urls,
        isLoading = isUploadingFabric,
        ctaLabel = ctaLabel,
        onCtaClick = onCtaClick,
        onImageClick = onImageClick,
        modifier = modifier,
    )
}
```

- [ ] **Step 3: Add the flag to `OrderGarmentDetailsCard` and pass to the first item's `FabricColumn`**

Add `isUploadingFabric: Boolean = false` to the `OrderGarmentDetailsCard` signature (after `onAddFabricNameClick`), and pass `isUploadingFabric = isUploadingFabric` into the `index == 0` `FabricColumn(...)` call. Leave the additional-items full-width fabric columns at the default `false` (the inline add only targets the first item).

- [ ] **Step 4: Build to verify previews + compile**

Run: `./gradlew :composeApp:assembleDebug; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt
git commit -m "feat(order-detail): show LoadingDots on fabric slot while uploading"
```

---

## Task 4: Wire the screen — launchers + sheet

**Files:**
- Modify: `feature/order/presentation/detail/OrderDetailScreen.kt`

Mirror the order form's `FabricImageSection` picker plumbing, scoped to the detail screen's stateless `OrderDetailScreen` composable (the one that already renders `StylePickerSheet` and takes `state` + `onAction`).

- [ ] **Step 1: Add imports**

```kotlin
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.ui.components.PhotoSource
import com.danzucker.stitchpad.ui.components.PhotoSourceSheet
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
```

(Several of these may already be imported — add only the missing ones.)

- [ ] **Step 2: Add the launchers + pending-source plumbing**

Near the top of the stateless `OrderDetailScreen` composable body (where other `remember`/launcher setup lives), add:

```kotlin
    val fabricPickerScope = rememberCoroutineScope()
    var pendingFabricSource by remember { mutableStateOf<PhotoSource?>(null) }

    val fabricGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = fabricPickerScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let { onAction(OrderDetailAction.OnFabricPhotoPicked(it)) }
        },
    )
    val fabricCameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) onAction(OrderDetailAction.OnFabricPhotoPicked(bytes))
    }

    // Launch the chosen source only AFTER the sheet has dismissed — the order form uses this
    // exact ordering to dodge the iOS "present right after Compose sheet dismiss" no-op.
    LaunchedEffect(state.showFabricSourceSheet, pendingFabricSource) {
        if (!state.showFabricSourceSheet && pendingFabricSource != null) {
            when (pendingFabricSource) {
                PhotoSource.Camera -> fabricCameraLauncher.launch()
                PhotoSource.Gallery -> fabricGalleryPicker.launch()
                null -> Unit
            }
            pendingFabricSource = null
        }
    }
```

- [ ] **Step 3: Render the sheet**

Next to the existing `if (state.showStylePickerSheet ...) { StylePickerSheet(...) }` block, add:

```kotlin
    if (state.showFabricSourceSheet) {
        PhotoSourceSheet(
            onPick = { source ->
                pendingFabricSource = source
                onAction(OrderDetailAction.OnDismissFabricSourceSheet)
            },
            onDismiss = { onAction(OrderDetailAction.OnDismissFabricSourceSheet) },
        )
    }
```

- [ ] **Step 4: Pass the loading flag to the card**

In the `OrderGarmentDetailsCard(...)` call (~line 1034), add:

```kotlin
                isUploadingFabric = state.isUploadingFabric,
```

- [ ] **Step 5: Build + iOS compile**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(order-detail): inline Add fabric photo via Camera/Gallery sheet"
```

---

## Task 5: Final verification + PR

- [ ] **Step 1: Full test suite (regression check)**

Run: `./gradlew :composeApp:testDebugUnitTest; echo "exit=$?"`
Expected: all green, `exit=0` (no tests were changed; this confirms nothing regressed).

- [ ] **Step 2: detekt + iOS compile (gates)**

Run: `./gradlew detekt :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: `exit=0`. Do a clean Xcode build of `iosApp` if the picker/sheet behaviour needs the Swift target (build-ios CI compiles the framework, not the app).

- [ ] **Step 3: Manual smoke test (Daniel is QA) — Android + iOS**
  1. Open an order with no fabric photo → tap **Add fabric photo** → Camera/Gallery sheet opens (no navigation to the form).
  2. **Choose from gallery** → pick a photo → fabric tile shows `LoadingDots` → photo appears; still on Order Details.
  3. On another empty-slot order → **Take photo** → capture → fabric photo appears.
  4. After a photo exists, the slot's CTA reads **Add fabric name**; adding more fabric photos goes through the edit form (confirm that still works).
  5. Airplane mode → add a fabric photo → appears immediately from local cache; syncs on reconnect.
  6. Force an upload failure → error snackbar, tile unchanged, no spinner left stuck.
  7. Confirm the edit pencil still opens the form and fabric *name* editing is unaffected.
  8. iOS: confirm the picker actually opens after the sheet dismisses (no silent no-op).

- [ ] **Step 4: Push + open PR**

```bash
git push -u origin feat/inline-fabric-photo
gh pr create --base main --title "feat(order-detail): inline Add fabric photo" \
  --body "Implements docs/superpowers/specs/2026-06-16-inline-fabric-photo-order-detail-design.md. Tapping Add fabric photo now opens a Camera/Gallery sheet and uploads inline (offline-first), updating the fabric tile in place — mirroring Add style — instead of navigating back to the order form. Scope: the first fabric photo (empty-slot case); more photos still use the edit form. Includes the manual smoke test above."
```

Then run the required reviews before merge: Cursor Bugbot (auto) **and** `codex review` (pre-push hook).

---

## Self-Review notes
- **Spec coverage:** Camera/Gallery sheet (Task 1 + 4), state/actions/handler (Task 2), offline-first upload + append + persist + cap guard + oversize + error (Task 2), LoadingDots (Task 3), screen wiring + iOS timing pattern (Task 4), first-photo scope + `NavigateToOrderForm` kept for edit (Task 2), build gates + manual smoke (Task 5). All spec sections map to a task.
- **Type consistency:** `PhotoSource`/`PhotoSourceSheet` (Task 1) used in Task 4; `OnFabricPhotoPicked`/`OnDismissFabricSourceSheet`/`showFabricSourceSheet`/`isUploadingFabric` defined in Task 2 and consumed in Tasks 3–4 with identical names; `uploadFabricPhotos` returns `(localPath, storagePath)` and the `FabricImageRef` is built exactly as the order form does.
- **Out of scope (unchanged):** multi-photo "add more" tile on the detail card, the style flow, the order form's own inline sheet (a later DRY cleanup), removing `NavigateToOrderForm`.
