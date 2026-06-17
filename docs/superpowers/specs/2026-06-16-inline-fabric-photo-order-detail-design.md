# Inline "Add fabric photo" on Order Details

**Date:** 2026-06-16
**Status:** Approved design, pending implementation plan

## Problem

On the Order Details screen, when a garment has no fabric photo, tapping **Add fabric photo**
navigates the tailor all the way to the Edit-Order form just to attach one photo — a long
detour for a small action. Adding a **style**, by contrast, happens inline (a picker sheet that
updates the order in place). Fabric should be just as quick.

## Goal

Tapping **Add fabric photo** opens the device photo picker directly and uploads the photo
inline, updating the fabric tile in place. No navigation to the form.

## Key asymmetry (why this is simpler than style, not identical)

"Add style" opens a **picker sheet** because styles have a reusable **library** (saved styles
linked across orders). Fabric has **no library** — a fabric photo is a one-off photo attached
to this order. So the faithful analog is even simpler: go straight to the OS photo picker,
upload, append. No sheet, because there is nothing to choose between.

## Scope

In scope:
- Change only the **Add fabric photo** affordance to launch an inline picker + upload.

Out of scope (unchanged):
- Existing fabric-image viewing/thumbnails.
- The edit pencil (still the path for fabric *name* and full order edits).
- The entire style flow.

## Decisions

- **Photo count:** Multiple, up to **3** per garment — matches the existing style cap
  (`MAX_IMAGES_PER_CATEGORY = 3`). Added one at a time (`SelectionMode.Single`), repeatable,
  which also avoids the peekaboo `Multiple(maxSelection<=1)` Android crash.
- **Upload feedback:** Show the standard `LoadingDots` on the fabric tile while uploading
  (transient `isUploadingFabric` flag).
- **At cap (3 images):** Hide the "Add fabric photo" affordance entirely.
- **Errors:** Surface via the existing `errorMessage` → `toOrderUiText()` snackbar, exactly as
  `linkExistingStyle` does today.
- **Oversize photos:** Mirror the form's `rejectOversizedPhoto` guard.

## Behaviour

1. Tap **Add fabric photo** → OS photo picker (`SelectionMode.Single`).
2. Pick one photo → tile shows `LoadingDots` → photo uploads inline → tile shows the image
   (offline-first: local image appears immediately, syncs later).
3. Repeatable until 3 images exist, after which the affordance is hidden.

## Changes by layer

### 1. `OrderDetailScreen.kt`
- Add a `rememberImagePickerLauncher` for fabric (`SelectionMode.Single`), `onResult` →
  `onAction(OrderDetailAction.OnFabricPhotoPicked(bytes))`.
- The garment card's `onAddFabricPhotoClick` calls `fabricPicker.launch()` directly, mirroring
  how `StyleFormScreen` launches its picker from the composable.
- Hide the add affordance when the first item's `fabricImages.size >= 3`.

### 2. `OrderDetailAction`
- Add `OnFabricPhotoPicked(photoBytes: ByteArray)`.
- Remove the now-dead `OnAddFabricClick` and its `OrderDetailEvent.NavigateToOrderForm`
  emission — **after** confirming no other caller depends on that event.

### 3. `OrderDetailViewModel`
- New handler modeled on `linkExistingStyle` + the form's fabric upload block:
  1. Resolve `order`, `firstItem`, `userId`; guard `firstItem.fabricImages.size < 3`.
  2. Oversize guard (`rejectOversizedPhoto`).
  3. Set `isUploadingFabric = true`.
  4. `orderRepository.uploadFabricPhotos(userId, order.id, firstItem.id, listOf(bytes))`.
  5. On success, build
     `FabricImageRef(photoUrl = "", photoStoragePath = path, syncState = ImageSyncState.PENDING, localPhotoPath = localPath)`,
     append to `firstItem.fabricImages`, then `orderRepository.updateOrder(...)`.
  6. On any error, set `errorMessage = error.toOrderUiText()`.
  7. Clear `isUploadingFabric` in all paths.

### 4. State
- Add `isUploadingFabric: Boolean = false` to `OrderDetailState`.

### 5. Tests (`OrderDetailViewModelTest`)
Mirror the existing style-link tests:
- Success: picked fabric photo → `uploadFabricPhotos` called → ref appended → `updateOrder`
  called with the new `fabricImages`.
- At cap: with 3 fabric images, `OnFabricPhotoPicked` is a no-op (no upload, no update).
- Upload error: `errorMessage` set, no `updateOrder`.
- Oversize: rejected, no upload.

## Data flow

```
bytes
  → uploadFabricPhotos()   // saves local copy + enqueues offline upload job, returns (localPath, storagePath)
  → FabricImageRef(PENDING, localPhotoPath)
  → firstItem.fabricImages + ref
  → updateOrder()
  → snapshot listener refreshes the fabric tile
```

Legacy single-fabric fields (`fabricPhotoUrl`, `fabricPhotoStoragePath`) are written
automatically by `OrderMapper.toOrderItemDto()` from the first fabric ref — no manual
double-write needed.

## Reference points in existing code

- Inline style link (pattern to mirror): `OrderDetailViewModel.linkExistingStyle()`.
- Fabric upload + `FabricImageRef` construction: `OrderFormViewModel` save block.
- Picker launch from composable: `StyleFormScreen` / `OrderFormScreen` fabric picker.
- Cap constant: `MAX_IMAGES_PER_CATEGORY = 3` (`OrderFormScreen.kt`).
- Mapper legacy double-write: `OrderMapper.toOrderItemDto()`.

## QA smoke test

1. Open an order with no fabric photo → tap **Add fabric photo** → picker opens (no navigation).
2. Pick a photo → tile shows LoadingDots → fabric photo appears; stays on Order Details.
3. Add a 2nd and 3rd → after the 3rd, the add affordance disappears.
4. Airplane mode → add a fabric photo → appears immediately from local cache; syncs on reconnect.
5. Trigger an upload failure → snackbar error, tile unchanged.
6. Confirm the edit pencil still opens the form and fabric *name* editing is unaffected.
