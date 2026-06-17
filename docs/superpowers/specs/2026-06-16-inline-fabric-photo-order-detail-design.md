# Inline "Add fabric photo" on Order Details

**Date:** 2026-06-16 (revised 2026-06-17)
**Status:** Approved design, pending implementation plan
**Branch:** `feat/inline-fabric-photo`

## Problem

On the Order Details screen, when a garment has no fabric photo, tapping **Add fabric photo**
navigates the tailor all the way back to the Edit-Order form (step 1) just to attach one
photo — a long detour for a small action. Adding a **style**, by contrast, happens inline
(a sheet that updates the order in place without leaving the screen). Fabric should be just
as quick.

## Goal

Tapping **Add fabric photo** lets the tailor add the photo right on the Order Details screen —
choose camera or gallery, the photo uploads, and the fabric tile updates in place. No
navigation to the form.

## Decision: a Camera / Gallery sheet (revised 2026-06-17)

An earlier draft of this spec went straight to the gallery with no sheet (reasoning: fabric
has no reusable library like style, so "there is nothing to choose between"). On review the
decision was changed: tapping **Add fabric photo** opens a small **bottom sheet** offering
**Take photo** / **Choose from gallery** — the same two sources the order form's fabric add
already offers, and consistent with the app's "bottom sheet for choices" convention. This lets
a tailor snap the fabric with the camera on the spot, which is a common real-world flow.

This is a behaviour change on **one affordance only** — the "Add fabric photo" CTA on the
detail screen. Everything else (style flow, the edit pencil, fabric-name editing, existing
thumbnails) is unchanged.

## Scope

In scope:
- Change the **Add fabric photo** affordance to open a Camera/Gallery sheet → pick → upload →
  append, inline on the detail screen.
- A small reusable `PhotoSourceSheet` composable (Camera/Gallery) for the detail screen.

Out of scope (unchanged):
- Existing fabric-image viewing/thumbnails.
- The edit pencil (still the path for fabric *name* and full order edits).
- The entire style flow.
- Refactoring the order form's own (currently inline) camera/gallery sheet to use the new
  shared component — noted as a possible later DRY cleanup, not done here.

## Decisions

- **Entry:** Camera/Gallery bottom sheet (see above), reusing the order form's
  launch-after-dismiss pattern.
- **Photo count (refined 2026-06-17):** the inline add handles the **first** fabric photo —
  the empty-slot case. The detail card's fabric slot only surfaces an "Add fabric photo" CTA
  when there are no photos yet (it switches to "Add fabric name" once a photo exists), so
  adding a 2nd/3rd photo still goes through the edit form. The VM still **appends** and guards
  the 3-image cap defensively (so it can never exceed it), but no multi-photo "add more" tile
  is added to the detail card. Photos are picked one at a time (`SelectionMode.Single`), which
  also avoids the peekaboo `Multiple(maxSelection<=1)` Android crash.
- **First item only:** operates on `order.items.first()`, matching how the detail screen's
  style/fabric slots already behave.
- **Upload feedback:** show the standard `LoadingDots` on the fabric tile while uploading
  (transient `isUploadingFabric` flag).
- **At cap (3 images):** hide the "Add fabric photo" affordance entirely (same as style and
  the order form).
- **Errors:** surface via the existing `errorMessage` → `toOrderUiText()` snackbar, exactly as
  `linkExistingStyle` does today.
- **Oversize photos:** mirror the form's oversize guard (`error_order_photo_too_large`).

## Behaviour

1. Tap **Add fabric photo** → `OnAddFabricClick` → VM sets `showFabricSourceSheet = true`
   (replaces the old `NavigateToOrderForm` emission for *this* CTA).
2. The `PhotoSourceSheet` shows **Take photo** / **Choose from gallery**. Picking a source
   dismisses the sheet and launches the matching peekaboo launcher (camera or gallery) via the
   `LaunchedEffect(showSheet, pendingSource)` "launch-after-dismiss" pattern copied from
   `OrderFormScreen` — which also sidesteps the iOS "present right after Compose sheet dismiss"
   timing bug.
3. Pick one photo → tile shows `LoadingDots` → photo attaches inline → tile shows the image
   (offline-first: the local image appears immediately, syncs later).
4. The slot now shows the photo; its CTA becomes "Add fabric name" (existing behavior).
   Adding further fabric photos is done via the edit form.

## Changes by layer

### 1. `PhotoSourceSheet` (new shared composable)
- A small `ModalBottomSheet` with two rows — **Take photo** / **Choose from gallery** —
  plus a public `enum class PhotoSource { Camera, Gallery }`. Lives in a shared UI location
  (e.g. `ui/components/`) so the detail screen can use it. Modeled on the order form's inline
  sheet (currently private in `OrderFormScreen.kt`).

### 2. `OrderDetailState`
- Add `showFabricSourceSheet: Boolean = false`.
- Add `isUploadingFabric: Boolean = false`.

### 3. `OrderDetailAction`
- Add `data class OnFabricPhotoPicked(val photoBytes: ByteArray)`.
- Add `data object OnDismissFabricSourceSheet`.
- Keep `OnAddFabricClick` but change its handler to open the sheet (below). **Keep**
  `OrderDetailEvent.NavigateToOrderForm` — it is still used by the general edit CTA
  (`OrderDetailViewModel` ~line 125); only the fabric CTA stops using it.

### 4. `OrderDetailViewModel`
- `OnAddFabricClick` → if the first item is below the cap, `showFabricSourceSheet = true`
  (no navigation). At cap, no-op (CTA will already be hidden).
- `OnDismissFabricSourceSheet` → `showFabricSourceSheet = false`.
- `OnFabricPhotoPicked(bytes)`, modeled on `linkExistingStyle()` + the form's fabric upload:
  1. Resolve `order`, `firstItem`, `userId`; guard `firstItem.fabricImages.size < 3`.
  2. Oversize guard (reject + `errorMessage`, matching the form).
  3. `showFabricSourceSheet = false`, `isUploadingFabric = true`.
  4. `orderRepository.uploadFabricPhoto(userId, order.id, firstItem.id, bytes)` → returns
     `Pair(photoUrl, storagePath)`.
  5. On success, build the `FabricImageRef` the same way the form does (PENDING sync state +
     local path as applicable), append to `firstItem.fabricImages`, then
     `orderRepository.updateOrder(userId, order.copy(items = updatedItems))`.
  6. On any error, set `errorMessage = error.toOrderUiText()`.
  7. Clear `isUploadingFabric` in all paths.

### 5. `OrderDetailScreen`
- Create a fabric `rememberImagePickerLauncher` (`SelectionMode.Single`) and a
  `rememberImageCaptureLauncher`, plus a `pendingFabricSource` + `LaunchedEffect` that launches
  the chosen source after the sheet dismisses — mirroring `OrderFormScreen`'s `FabricImageSection`.
- Render `PhotoSourceSheet` when `state.showFabricSourceSheet`; its `onPick` sets the pending
  source and dismisses; `onResult` of the launchers → `onAction(OnFabricPhotoPicked(bytes))`.
- The garment card's `onAddFabricPhotoClick` still fires `OnAddFabricClick`.
- Pass an `isUploadingFabric` flag into the garment card so the fabric tile shows `LoadingDots`.
- Hide the add affordance when the first item's `fabricImages.size >= 3`.

### 6. Verification
There is **no `OrderDetailViewModelTest`** harness: `OrderDetailViewModel` takes ~12
dependencies including platform types (`ImageLoader`, `PlatformContext`, `OrderReceiptSharer`)
that aren't constructable in `commonTest`, which is why `linkExistingStyle` — the exact pattern
this mirrors — also ships without a VM unit test. So this change is verified by:
- **Build gates:** `./gradlew detekt`, `:composeApp:assembleDebug`, and
  `:composeApp:compileKotlinIosSimulatorArm64` all green.
- **The manual smoke test below** (Daniel is QA), which covers the end-to-end behaviour,
  offline-first, error path, and the iOS sheet→picker timing.

The VM handler is a faithful mirror of `linkExistingStyle` + the order form's fabric-upload
block (both proven in production), keeping the orchestration identical to code already shipped.

## Data flow

```
tap Add fabric photo
  → showFabricSourceSheet = true
  → PhotoSourceSheet (Camera / Gallery)
  → launcher returns bytes
  → uploadFabricPhoto()        // saves local copy + enqueues offline upload, returns (url, path)
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
- Fabric upload + `FabricImageRef` construction: `OrderFormViewModel` save block;
  `OrderRepository.uploadFabricPhoto(userId, orderId, itemId, bytes): Result<Pair<String,String>, DataError.Network>`.
- Camera/Gallery sheet + launch-after-dismiss + `PhotoSource` enum: `OrderFormScreen.kt`
  `FabricImageSection` (~L1535) and `enum class PhotoSource` (~L890).
- Picker launch from a composable: `OrderFormScreen` / `StyleFormScreen` fabric picker.
- Cap constant: `MAX_IMAGES_PER_CATEGORY = 3` (`OrderFormScreen.kt`).
- iOS sheet→picker timing: the order form's `LaunchedEffect(showSheet, pendingSource)` already
  launches the picker only after the sheet has dismissed; reuse that ordering.
- Mapper legacy double-write: `OrderMapper.toOrderItemDto()`.

## QA smoke test (Daniel is QA) — Android + iOS

1. Open an order with no fabric photo → tap **Add fabric photo** → sheet opens (no navigation
   to the form).
2. Choose **Choose from gallery** → pick a photo → tile shows LoadingDots → fabric photo
   appears; still on Order Details.
3. Repeat on another empty-slot order → **Take photo** → capture → fabric photo appears.
4. After a photo exists, the slot's CTA reads **Add fabric name** (the add-photo CTA is gone);
   adding more fabric photos goes through the edit form.
5. Airplane mode → add a fabric photo → appears immediately from local cache; syncs on
   reconnect.
6. Trigger an upload failure → snackbar error, tile unchanged, no spinner left stuck.
7. Confirm the edit pencil still opens the form and fabric *name* editing is unaffected.
8. iOS specifically: confirm the picker actually opens after the sheet dismisses (no silent
   no-op from the present-after-dismiss timing bug).
