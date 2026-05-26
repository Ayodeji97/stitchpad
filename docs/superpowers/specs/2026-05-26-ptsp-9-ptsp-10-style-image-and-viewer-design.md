# Order Form — Style Image Upload + Fullscreen Viewer (PTSP-9 + PTSP-10)

**Date:** 2026-05-26
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** PTSP-9 (Add customer styles and fabric to new order), PTSP-10 (Styles and Fabric image should be visible when tapped on)

---

## 1. Context

Two PM tickets that pair naturally and ship together:

- **PTSP-9** — Today the order form can attach a fabric image directly, but a style is only attachable via the existing `availableStyles` dropdown (pick from the customer's gallery). Tailors want to **attach a style image during order creation**, same UX as the fabric photo. Optionally that image becomes a new gallery entry.
- **PTSP-10** — Once an image is attached (fabric or style), the user cannot tap the thumbnail to view the full image. Tailors want **tap-to-fullscreen** on every fabric / style thumbnail in the order form **and** the order detail screen.

The two tickets share the same files (order form + order detail) and complement each other (the new style image picker is incomplete without a way to view what was uploaded). Bundling cuts review overhead and avoids a half-shipped feature.

## 2. Branch & PR shape

- **Branch:** `feature/ptsp-9-style-image-on-new-order`
- **Single PR** covering both tickets. Commit history will separate the two via dedicated commits where it's easy (e.g., `feat(order-form): style image upload (PTSP-9)` and `feat(order): tap-to-fullscreen on fabric/style thumbnails (PTSP-10)`), but they go to main together.

## 3. PTSP-9 — Style image upload on the order form

### 3.1 Data model changes

`OrderItem` gains two style image fields, mirroring the existing fabric photo fields:

```kotlin
data class OrderItem(
    // ...existing fields unchanged...
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
    // NEW:
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
)
```

**Possible style states on an order item:**

| State                              | `styleId`     | `stylePhotoUrl` | Meaning                                                                                       |
|-----------------------------------|---------------|------------------|-----------------------------------------------------------------------------------------------|
| Empty                              | null          | null             | No style attached.                                                                            |
| Picked from gallery                | "abc"         | null             | Existing `Style` from the customer's gallery; image lives at `Style.photoUrl`.                |
| Uploaded + saved to gallery        | "xyz" (new)   | null             | A new `Style` entity was created during order save; `styleId` points to it.                   |
| Uploaded one-off (toggle OFF)      | null          | "https://..."    | Image lives on the order item only; never enters the gallery.                                  |

`stylePhotoBytes` is form-state-only (transient between picker and save), never persisted on the domain model — same pattern as `fabricPhotoBytes`.

**Firestore mapping:** `OrderItemDto` + `OrderMapper` get the same two new fields with null defaults. Old orders read back with both new fields null — fully backward-compatible.

### 3.2 UI restructure — unified Style section

The existing per-item Style **dropdown** (currently rendered conditionally with `if (availableStyles.isNotEmpty())`) is **replaced** with a unified Style section that always renders. Three visual states:

**State A — empty (no style attached):**
```
STYLE
[ Pick from gallery ]   [ Upload new ]
```
- "Pick from gallery" is **disabled** (not hidden) when `availableStyles.isEmpty()` — keeps the upload affordance always visible while preserving today's "no gallery, no picker" behavior.
- "Upload new" opens the same camera/gallery picker pattern used by the fabric photo (small picker sheet → camera or gallery).

**State B — existing style picked:**
```
STYLE
[thumb]  Ankara wedding suit
         From your style gallery
                                 [Change]  [Remove]
```
- Thumbnail loaded from `Style.photoUrl` via Coil `SubcomposeAsyncImage` with `LoadingDots` placeholder (per `feedback_image_loading_dots`).
- Description shown read-only.
- "Change" reopens the picker chooser sheet; "Remove" clears `styleId`.

**State C — new image uploaded:**
```
STYLE
[thumb]  [Style description ____________ ]
         ☑  Save to style gallery
                                 [Change]  [Remove]
```
- Thumbnail from in-memory bytes (or `stylePhotoUrl` if editing a saved order).
- Description text field, editable, **optional**. Toggle defaults to **ON**.
- "Change" reopens the upload picker; "Remove" clears the image + description + resets toggle to default.

**Style picker chooser sheet (replaces `ExposedDropdownMenuBox`):** Tapping "Pick from gallery" opens a `ModalBottomSheet` listing the customer's gallery styles (thumbnail + description per row). Tap one → fires `OrderFormAction.OnItemStyleChange(itemId, styleId)`. Cleaner UX with images; consistent with `feedback_notification_patterns` ("Bottom Sheet for choices").

### 3.3 Validation rules

- **Image is required to save** in either Upload state (toggle ON or OFF) — same as the existing fabric photo "save requires bytes-or-URL" pattern.
- **Description is optional**, even when the toggle is ON. If toggle ON + description blank → save creates the Style with an empty `description` string. The standalone Style Gallery form still requires non-blank description; this inline order-form flow is a deliberate exception. **Future readers:** if the gallery rendering of empty-description styles looks bad, that's a polish concern, not a PTSP-9 blocker.

### 3.4 Save logic + Firebase uploads

`OrderFormViewModel.save()` per-item extension:

```
for each item:
    1. If fabricPhotoBytes != null   →   upload fabric to Firebase Storage   →   fabric URL + storage path
    2. If stylePhotoBytes != null:
       2a. If saveToGallery toggle is ON:
           - styleRepository.createStyle(
                 customerId,
                 description = item.styleDescription (may be ""),
                 photoBytes = stylePhotoBytes,
             )
             → newStyleId
           - item.styleId = newStyleId
           - item.stylePhotoUrl stays null   (image lives on the new Style entity)
       2b. If saveToGallery toggle is OFF:
           - Upload directly to Firebase Storage   →   style URL + storage path
           - item.styleId stays null
           - item.stylePhotoUrl + item.stylePhotoStoragePath are set
    3. orderRepository.createOrder(...)   (or updateOrder on edit)
```

**Failure handling:**
- Fabric upload fails → existing behavior preserved (error UiText, order not saved).
- Style upload fails (toggle OFF) → same shape: error UiText, order not saved.
- `createStyle` fails (toggle ON) → error UiText, order not saved. `StyleRepository.createStyle` is atomic over photo + Firestore doc; partial state is a repository-level concern.

**Documented limitation (matches existing fabric flow):** Upload order is sequential per item. If the second upload (style after fabric, or order doc after style) fails, the first upload's storage object is orphaned. Firebase Storage cleanup of orphans is out of scope.

### 3.5 Edit mode (existing orders)

- **Pre-PTSP-9 orders** (no new fields populated): Style section starts in State A. User can pick or upload as if creating.
- **Order with `styleId`** → State B. Description read-only, change/remove available.
- **Order with `stylePhotoUrl` (toggle OFF previously)** → State C, BUT loaded from URL not bytes, with:
  - No description text field (the field was never saved on the order item; the upload was one-off without a description)
  - No "Save to gallery" toggle (the moment for that decision has passed; if the user wants the image in the gallery, they can re-upload via the Style Gallery form)
  - Just thumbnail + Change + Remove

## 4. PTSP-10 — Fullscreen image viewer

### 4.1 New reusable composable

`ui/components/FullScreenImageViewer.kt`:

```kotlin
@Composable
fun FullScreenImageViewer(
    model: Any?,                       // URL String or ByteArray; Coil handles both
    contentDescription: String?,
    onDismiss: () -> Unit,
)
```

Rendered as a `Dialog` with `DialogProperties(usePlatformDefaultWidth = false)`:
- `Box(fillMaxSize)` with `MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)` background
- Centered `SubcomposeAsyncImage` with `ContentScale.Fit` (entire image visible, no crop)
- Loading uses existing `LoadingDots`
- Top-end `IconButton` with `Icons.Default.Close` for explicit dismiss
- Outer Box has `clickable(onClick = onDismiss)` with `indication = null` for tap-outside-to-dismiss without ripple
- Android system back is handled by `Dialog`'s `onDismissRequest`

One image per invocation; no swipe-between, no pinch-to-zoom, no save/share — all out of scope.

### 4.2 Wiring sites

| Surface                                          | Thumbnails to make tappable                                                                   |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `OrderFormScreen` item card                      | Fabric thumbnail (existing) + Style thumbnail (new from PTSP-9, all three rendered states)    |
| `OrderGarmentDetailsCard.FabricThumbnail`        | Fabric thumbnail on the order detail screen                                                    |
| `OrderHeroCard` (style image, around line 195)   | Style image rendered on the order detail hero                                                  |

Each site adds:
```kotlin
var fullScreenImage: Any? by remember { mutableStateOf<Any?>(null) }
var fullScreenCd: String? by remember { mutableStateOf<String?>(null) }
// ...
Modifier.clickable { fullScreenImage = <bytes-or-url>; fullScreenCd = "<label>" }
// ...
fullScreenImage?.let {
    FullScreenImageViewer(model = it, contentDescription = fullScreenCd, onDismiss = {
        fullScreenImage = null
        fullScreenCd = null
    })
}
```

State is Compose-internal (per CLAUDE.md "All state in ViewModel, never in remember… except Compose-internal"). No new actions/events/state-fields on any ViewModel.

### 4.3 Detail screen — `stylePhotoUrl` fallback wiring

`OrderHeroCard` today receives `stylePhotoUrl` from `state.style?.photoUrl` (the looked-up Style entity). After PTSP-9, an item can have `stylePhotoUrl` set without a `styleId`. The detail-screen wiring needs to **fall back to `item.stylePhotoUrl`** when `state.style` is null but the item has a one-off URL.

The hero card today shows the *first* item's style. Multi-item orders with mixed styles are pre-existing detail-screen behavior; PTSP-9 doesn't change that. Just add the fallback so a one-off-uploaded style still renders.

## 5. Files touched

| File                                                                                                              | Change                                                                                                  |
|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `core/domain/model/Order.kt`                                                                                       | Add `stylePhotoUrl` + `stylePhotoStoragePath` to `OrderItem`.                                            |
| `core/data/dto/OrderItemDto.kt` *(or wherever it lives)*                                                           | Mirror the 2 new fields; null defaults.                                                                  |
| `core/data/mapper/OrderMapper.kt`                                                                                  | Round-trip the 2 new fields.                                                                            |
| `feature/order/presentation/form/OrderFormState.kt`                                                                | `OrderFormItemState` gets: `stylePhotoBytes`, `stylePhotoUrl`, `stylePhotoStoragePath`, `styleDescription`, `saveStyleToGallery` (defaults to `true`). |
| `feature/order/presentation/form/OrderFormAction.kt`                                                               | New actions: `OnItemStylePhotoPicked`, `OnItemStylePhotoRemoved`, `OnItemStyleDescriptionChange`, `OnItemSaveStyleToGalleryToggle`, `OnItemPickExistingStyleClick`. |
| `feature/order/presentation/form/OrderFormViewModel.kt`                                                            | Handlers; `save()` extended per §3.4.                                                                    |
| `feature/order/presentation/form/OrderFormScreen.kt`                                                               | Replace style dropdown with unified Style section (§3.2); wire `FullScreenImageViewer` on fabric + style thumbnails (§4.2). |
| `feature/order/presentation/form/components/StylePickerSheet.kt` *(new)*                                           | `ModalBottomSheet` listing customer's gallery styles.                                                    |
| `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`                                          | Wrap `FabricThumbnail` in clickable; render viewer Dialog.                                               |
| `feature/order/presentation/detail/components/OrderHeroCard.kt`                                                    | Wrap style image render in clickable; render viewer Dialog. Fall back to `item.stylePhotoUrl` when `state.style` is null. |
| `feature/order/presentation/detail/OrderDetailScreen.kt` / VM / state                                              | Plumb `item.stylePhotoUrl` so `OrderHeroCard` can fall back to it.                                       |
| `ui/components/FullScreenImageViewer.kt` *(new)*                                                                   | Reusable Dialog-based viewer.                                                                            |
| `composeApp/src/commonMain/composeResources/values/strings.xml`                                                    | New strings (~8–10 keys for section label, button labels, toggle label, content descriptions; final list in plan). |

**Tests:** `OrderFormViewModelTest` coverage may or may not exist — confirm during planning. Adding tests for the new save branches is in-scope-when-easy, not blocking.

## 6. QA smoke checklist

1. Customer with no gallery styles: Style section A renders; "Pick from gallery" disabled; "Upload new" works.
2. Customer with gallery styles: "Pick from gallery" opens picker sheet; selecting one transitions to State B with preview + description.
3. Upload an image: State C with preview, editable description, toggle ON by default.
4. Toggle OFF + save → order is created with `item.stylePhotoUrl` set, no Style entity in gallery.
5. Toggle ON + save → order created with new `item.styleId`, new Style appears in customer's gallery.
6. Toggle ON + description blank + save → save succeeds; Style entity has empty `description`.
7. Tap fabric thumbnail on the form → viewer opens; close via X, tap-outside, and back all work.
8. Tap style thumbnail on the form (all three states) → viewer opens; close works.
9. Tap fabric thumbnail on order detail → viewer opens; close works.
10. Tap style thumbnail on order detail → viewer opens; close works.
11. Edit a pre-PTSP-9 order: Style section starts empty (State A); switching to State B or C and saving applies cleanly.
12. iOS hardware: both Firebase Storage uploads on a real iPhone (sim sometimes masks upload bugs).

## 7. Out of scope

- Pinch-to-zoom, pan, swipe-between-images, save-to-device, share — viewer is V1.
- Cleaning up orphaned Firebase Storage objects when a multi-step save fails.
- Adding a `description` field to the order item itself (description is a Style attribute, not an item attribute).
- Multiple style images per item.
- Tappable thumbnails on Style Gallery, Customer Detail, or other surfaces (could be a follow-up; out of scope here).

## 8. Success criteria

- PR merged with both Cursor and `codex review` clean.
- All 12 smoke steps pass on Android emulator + iPhone hardware.
- No regressions in fabric photo flow, existing gallery style picker, or order detail rendering.
- Customer's Style Gallery shows newly-uploaded styles from PTSP-9 the next time they open it.
