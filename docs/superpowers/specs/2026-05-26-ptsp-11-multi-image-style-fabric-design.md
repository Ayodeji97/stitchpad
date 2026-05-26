# Order Item — Multi-image Style & Fabric (PTSP-11 + UX polish)

**Date:** 2026-05-26
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** PTSP-11 ("Add more than one image to fabric and style") + bundled UX polish (hide vs disable, rename, visual redesign)

---

## 1. Context

PTSP-9 / PTSP-10 shipped single-image style + fabric upload during order creation. During smoke testing, three UX issues surfaced:

1. **"Pick from gallery" is ambiguous** — sounds like the phone's photo gallery (the camera roll) rather than the user's saved-styles library
2. **The button is *disabled* when empty** instead of hidden, which adds confusion
3. **The Style section is visually flat** — two text-led links floating with no chrome, easy to miss

PM also filed PTSP-11 asking for **multiple images per fabric / style** so a tailor can reference 2–3 inspirations or fabric swatches per order item.

This spec bundles PTSP-11 with the three UX fixes — all touch the same Style/Fabric section of the per-item card and the order detail screen.

## 2. Branch & PR shape

- **Branch:** `feature/ptsp-11-multi-image-style-fabric`
- **Single PR** covering PTSP-11 + the three UX polish items
- Spec + plan + visual mockups (HTML preview) all commit to the branch

## 3. Design decisions (locked during brainstorm)

| Decision | Value |
|---|---|
| Max images per category | **3** (style + fabric each) |
| Rename of "Pick from gallery" | **"Choose from saved styles"** (verb-led, unambiguous) |
| Empty-gallery behavior | **Hide** the chip (not disable) — only "Upload new" shows |
| Toggle + description scope | **Single shared** per section — one toggle + one optional description applies to all uploaded images in that section |
| Source-of-image label on thumbs | **Keep** small badges (`LIBRARY` / `NEW`) — 8.5px text, dark scrim |
| Hero image priority on detail screen | **First image in the list** wins — no separate "primary" flag |
| Visual direction | **Variant B+** (inline structure, no nested cards, strong chip affordance) |
| Detail-screen multi-image presentation | **Carousel** (`HorizontalPager` with dots + counter) when 2+ images |
| Fabric multi-image in fullscreen viewer | Same carousel — `FullScreenImageViewer` extended to accept a list + start index |

Visual reference: `preview/ptsp-11-style-fabric-redesign.html` (form section) + `preview/ptsp-11-order-detail-multi-image.html` (detail screen).

## 4. Data model

### Domain (`core/domain/model/Order.kt`)

```kotlin
data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11 multi-image
    val styleImages: List<StyleImageRef> = emptyList(),
    val fabricImages: List<FabricImageRef> = emptyList(),
)

data class StyleImageRef(
    val source: StyleImageSource,
    val styleId: String? = null,         // set when source == LIBRARY
    val photoUrl: String? = null,        // set when source == UPLOADED
    val photoStoragePath: String? = null,// set when source == UPLOADED
)

enum class StyleImageSource { LIBRARY, UPLOADED }

data class FabricImageRef(
    val photoUrl: String,    // always uploaded (no library concept)
    val photoStoragePath: String,
)
```

### State machine for `StyleImageRef`

`source` determines which other fields are non-null:
- `LIBRARY` → `styleId` non-null, `photoUrl`/`photoStoragePath` null. The image is fetched via the `Style` entity in the customer's gallery.
- `UPLOADED` → `photoUrl` + `photoStoragePath` non-null, `styleId` null. The image lives at the OrderItem-scoped Firebase Storage path.

There's no `BOTH` state — these are mutually exclusive by construction.

### Firestore DTO (`core/data/dto/OrderDto.kt`)

```kotlin
@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11
    val styleImages: List<StyleImageRefDto> = emptyList(),
    val fabricImages: List<FabricImageRefDto> = emptyList(),
    // Legacy fields kept for backward read + forward double-write (see §5)
    val styleId: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
)

@Serializable
data class StyleImageRefDto(
    val source: String = "UPLOADED",
    val styleId: String? = null,
    val photoUrl: String? = null,
    val photoStoragePath: String? = null,
)

@Serializable
data class FabricImageRefDto(
    val photoUrl: String = "",
    val photoStoragePath: String = "",
)
```

## 5. Backward compatibility

### Read path (`OrderMapper.toOrderItem`)

If `styleImages` is non-empty → use it as-is.
Else (pre-PTSP-11 document) → synthesize a 1-element list from legacy fields:

| Legacy fields | Synthesized `styleImages` |
|---|---|
| `styleId` non-null, `stylePhotoUrl` null | `[StyleImageRef(LIBRARY, styleId, null, null)]` |
| `styleId` null, `stylePhotoUrl` non-null | `[StyleImageRef(UPLOADED, null, stylePhotoUrl, stylePhotoStoragePath)]` |
| Both null | `[]` |

Same shape for fabric:

| Legacy fields | Synthesized `fabricImages` |
|---|---|
| `fabricPhotoUrl` non-null | `[FabricImageRef(fabricPhotoUrl, fabricPhotoStoragePath ?: "")]` |
| `fabricPhotoUrl` null | `[]` |

### Write path (forward-compat double-write)

For 12 months after PTSP-11 ships, every write populates BOTH the new list and the legacy single fields so older app versions can still read something:

| Field | Source |
|---|---|
| `styleImages` (new list) | Source of truth |
| `styleId` (legacy) | `styleImages.firstOrNull { it.source == LIBRARY }?.styleId` |
| `stylePhotoUrl` (legacy) | `styleImages.firstOrNull { it.source == UPLOADED }?.photoUrl` |
| `stylePhotoStoragePath` (legacy) | `styleImages.firstOrNull { it.source == UPLOADED }?.photoStoragePath` |
| `fabricPhotoUrl` (legacy) | `fabricImages.firstOrNull()?.photoUrl` |
| `fabricPhotoStoragePath` (legacy) | `fabricImages.firstOrNull()?.photoStoragePath` |

Old app versions see only the FIRST image of each category (which is also the hero image new versions show), so no functional regression.

Tracking note: the legacy-write code path should be removed in a follow-up cleanup in mid-2027 (12+ months out).

## 6. UI — order form (Variant B+)

### Layout (top-to-bottom inside each section)

```
1. Header row:    STYLE REFERENCES                         2 of 3
2. Image strip:   [thumb (LIB)] [thumb (NEW)] [+ ADD]      (horizontal scroll, max 3)
3. Action chips:  [Choose from saved] [Upload new]         (hidden when count == 3 OR (no saved styles AND no chips))
4. (Style only, when ≥1 uploaded image exists in this session):
                  [____ Style description (optional) ____]
                  ☑  Save uploaded styles to my library    (toggle, default ON)
```

Fabric section is identical except:
- No "Choose from saved" chip (fabric has no library concept) — only "Upload new" appears, full-width
- No description/toggle row (no Fabric entity to save)
- An additional "Fabric name (optional)" text field below the section

### Action-chip visibility matrix

| Count | Has saved styles | Has uploaded this session | Chips shown |
|---|---|---|---|
| 0 | yes | no | `[Choose from saved]` `[Upload new]` |
| 0 | no | no | `[Upload new]` (full width) |
| 1–2 | yes | (any) | `[Choose from saved]` `[Upload new]` |
| 1–2 | no | (any) | `[Upload new]` (full width) |
| 3 | (any) | (any) | hidden (max reached) |

### Per-thumbnail affordances

- **Source badge** (bottom-left): `LIBRARY` for refs from the customer's gallery, `NEW` for refs uploaded **this session** (post-save the badge disappears — "NEW" loses meaning once saved). Refs that were uploaded in a previous session and re-loaded in edit mode render WITHOUT a badge.
- **Remove** (top-right, 20dp dark circle with ×)
- **Tap thumb body** → opens `FullScreenImageViewer` with the full set of images in the section (so the user can swipe to the others)

### Picker chooser sheet

Tapping **"Choose from saved"** opens the existing `StylePickerSheet` from PTSP-9. Modifications for PTSP-11:
- Already-selected styles render with a small "Added" pill or grayed-out state so the user can't pick the same one twice
- After a pick, the sheet stays open if there's still capacity (count < 3 after the pick) so the user can pick a second one without re-tapping the chip
- The sheet auto-closes when capacity is reached OR the user dismisses it manually

### Style description + Save-to-gallery toggle

Appears below the chips ONLY when the user has just-uploaded image bytes in this session (not previously-saved uploaded refs from an edit-load). On save:
- **Toggle ON** (default) → all uploaded images become `Style` entities in the customer's gallery, sharing the description (which may be empty). After save, the form's "uploaded bytes" list clears and the refs become `LIBRARY`-source with the new styleIds.
- **Toggle OFF** → all uploaded images upload to Firebase Storage as one-off URLs on the OrderItem (`UPLOADED` source). No gallery entries created.

If the user uploads → toggles → uploads again, the state stays correctly per-image-batch — the toggle/description apply to ALL uploaded-this-session images uniformly.

## 7. UI — order detail screen

### `OrderHeroCard` — style carousel

The 180dp hero image area becomes count-aware:

| Image count | Layout |
|---|---|
| 0 | Empty add-style CTA (unchanged from today) |
| 1 | Single hero image (unchanged from today's behavior) |
| 2+ | `HorizontalPager` carousel with dots (bottom-center) + counter pill (top-right, e.g. `1 / 3`) |

Tap any image → `FullScreenImageViewer` opens at that index, swipe to navigate.

### `OrderGarmentDetailsCard` — fabric strip

Replaces today's single `FabricThumbnail` with `FabricStrip(fabricImages: List<FabricImageRef>)`:
- Up to 3 thumbnails at 64dp, 8dp gap
- First thumbnail keeps the existing "Fabric" caption pill
- Empty list → existing `FabricPlaceholder` (unchanged)
- Tap any thumb → `FullScreenImageViewer` with the full fabric list, swipe between

### `OrderDetailViewModel` — multi-style observation

Today observes one `Style` entity for the item's `styleId`. With multi-image, observes ALL library-source styles in the FIRST item:

```kotlin
data class OrderDetailState(
    // ...
    val styles: Map<String, Style> = emptyMap(),  // styleId → Style entity
)
```

The VM resolves the hero images list via:
```kotlin
fun resolveHeroImages(item: OrderItem?, styles: Map<String, Style>): List<StyleImageDisplay> =
    item?.styleImages.orEmpty().mapNotNull { ref ->
        when (ref.source) {
            LIBRARY -> styles[ref.styleId]?.let { StyleImageDisplay(it.photoUrl, it.description) }
            UPLOADED -> ref.photoUrl?.let { StyleImageDisplay(it, null) }
        }
    }
```

If a library style has been deleted from the gallery since the order was saved, that entry silently drops from the carousel. The carousel shows only what resolves.

### OrderDetail link-style flow

The existing "link a gallery style to this order" flow (from `StyleFormRoute(linkToOrderId)`) today only sets the OrderItem's `styleId`. With PTSP-11, this flow needs to **append** a `StyleImageRef(LIBRARY, styleId)` to `styleImages` instead of replacing the single field. Implementation-plan-time follow-up.

## 8. Fullscreen viewer (PTSP-10 extension)

`FullScreenImageViewer` extended to accept a list + start index:

```kotlin
@Composable
fun FullScreenImageViewer(
    images: List<Any>,              // URLs (String) or ByteArrays
    startIndex: Int = 0,
    contentDescription: String? = null,
    onDismiss: () -> Unit,
)
```

- Internally uses `HorizontalPager(state = rememberPagerState(initialPage = startIndex) { images.size })`
- Dots + counter at bottom
- All dismiss patterns from PTSP-10 carry through (X, scrim, back)
- Existing single-image call sites wrap in a 1-element list (e.g. `FullScreenImageViewer(images = listOf(model), ...)`)

The viewer becomes a shared swipeable image-set component used by both the form-side image strips and the detail-screen carousel/strip.

## 9. Save flow

`OrderFormViewModel.save()` extends PTSP-9's resolution:

```
for each item:
    1. fabricImageRefs = uploadFabricBatch(uid, orderId, itemId, item.uploadedFabricBytesList)
       ─ each upload to Firebase Storage; storage path: users/{uid}/orders/{orderId}/fabrics/{itemId}-{index}.jpg
       ─ on ANY failure → abort save with UiText error
       ─ on success: existing fabric refs + new fabric refs combined into final list

    2. styleImageRefs = resolveStyleImageRefs(uid, customerId, orderId, item)
       ─ For library-source refs: pass through unchanged
       ─ For previously-uploaded refs (from edit mode): pass through unchanged
       ─ For NEW uploaded bytes (this session):
           • toggle ON  → styleRepository.createStyles(uid, customerId, description, bytesList)
                          → for each newly-created styleId: StyleImageRef(LIBRARY, styleId)
           • toggle OFF → orderRepository.uploadStylePhotos(uid, orderId, itemId, bytesList)
                          → for each upload: StyleImageRef(UPLOADED, url, path)
       ─ on ANY failure → abort save with UiText error

    3. Build OrderItem with both lists populated.

    4. orderRepository.createOrder(...) (or updateOrder on edit).
```

### Storage path scheme

- Fabric: `users/{uid}/orders/{orderId}/fabrics/{itemId}-{index}.jpg`
- Style (uploaded one-off): `users/{uid}/orders/{orderId}/styles/{itemId}-{index}.jpg`
- Index = position in the upload batch at save time

### Repository surface additions

```kotlin
// OrderRepository
suspend fun uploadFabricPhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network>

suspend fun uploadStylePhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network>

// StyleRepository
suspend fun createStyles(
    userId: String,
    customerId: String,
    description: String,
    photoBytesList: List<ByteArray>,
): Result<List<String>, DataError.Network>
```

Each batch method internally loops the existing singular method with early-error-return — small implementation surface, easy to test, keeps the single-image path available for the standalone Style Gallery form.

### Idempotency

Reuses PTSP-9's synchronous `_state.update { isSaving = true }` guard before `viewModelScope.launch`. No new race window.

### Storage cleanup on order delete

`FirebaseOrderRepository.deleteOrder` (today: iterates `fabricPhotoStoragePath` + `stylePhotoStoragePath` from single fields) updates to iterate `fabricImages.map { it.photoStoragePath }` + `styleImages.filter { it.source == UPLOADED }.map { it.photoStoragePath }`. Library-source style images are NOT deleted (they're owned by the `Style` entity in the customer's gallery, which has its own lifecycle).

### Failure modes (recap)

| Failure | Behavior |
|---|---|
| One image in a fabric batch fails | Abort save with UiText. Preceding uploads in the batch are orphaned in storage (accepted limitation, matches PTSP-9). |
| One image in a style batch fails (toggle OFF) | Same — abort, orphaned uploads. |
| One createStyle in a batch fails (toggle ON) | Abort save. Styles created BEFORE the failure stay in the customer's gallery (not orphaned — just disconnected from the order). User can re-attach them via the order detail's link flow. |
| Network drops mid-batch | Same as individual failures. User retries from the form. |

## 10. Edit mode

`loadOrder(orderId)` → `order.items.map { it.toOrderItemFormState() }`:
- Existing `styleImages` + `fabricImages` lists load into form state as-is
- `uploadedStyleBytesList` / `uploadedFabricBytesList` start empty (no this-session uploads yet)
- `styleDescription` starts blank; `saveStyleToGallery` defaults to true
- Description + toggle row stays HIDDEN until the user uploads new bytes this session

### Add / remove in edit mode

- **Remove a LIBRARY ref** → just drops the entry from `styleImages`. No storage cleanup needed.
- **Remove an UPLOADED ref** → drops the entry from `styleImages` AND queues `photoStoragePath` for storage deletion on successful save. On save failure, the local state reverts (the ref re-appears, the deletion queue clears).
- **Remove an in-session uploaded ref (still bytes, not yet saved)** → just drops the bytes from `uploadedStyleBytesList`.
- **Add** behaves the same as new-order mode — picker sheet → bytes appended.

### Duplicate-from-order (`loadOrderForSeed`)

Carries forward PTSP-9's hardening:
- Each item gets a fresh `id`
- For each item: drop ALL `UPLOADED`-source style refs (their storage paths point at the source order). Drop ALL fabric refs (same hazard, since fabric is always uploaded).
- Keep `LIBRARY`-source style refs (they point at customer-gallery entities, shared across orders by design).

Result: a duplicated order has the same library-style references as the source, but starts with zero uploaded one-off images. User re-uploads if they want those references.

### PTSP-9's State C-readonly retires

The single-image PTSP-9 implementation had a special "edit-loaded one-off uploaded image, no description, no toggle" branch. With multi-image B+:
- All uploaded refs render as regular thumbnails with Remove affordance
- The toggle + description row only appears when `uploadedStyleBytesList` is non-empty (i.e., user uploaded NEW bytes this session)
- An edit-loaded order with only previously-saved uploaded refs simply doesn't show the toggle area

The State C-readonly composable branch can be deleted as a cleanup.

## 11. Files touched

### Domain
- `core/domain/model/Order.kt` — add `styleImages`, `fabricImages`, `StyleImageRef`, `StyleImageSource`, `FabricImageRef`

### Data layer
- `core/data/dto/OrderDto.kt` — mirror new fields + add `StyleImageRefDto`, `FabricImageRefDto`
- `core/data/mapper/OrderMapper.kt` — round-trip; legacy → list synthesis; double-write
- `core/data/mapper/OrderMapperTest.kt` — round-trip cases + legacy migration cases

### Repository
- `core/domain/repository/OrderRepository.kt` — add `uploadFabricPhotos`, `uploadStylePhotos` batch methods
- `core/domain/repository/StyleRepository.kt` — add `createStyles` batch method
- `feature/order/data/FirebaseOrderRepository.kt` — implement batch upload methods; per-image storage path with `-{index}` suffix; update `deleteOrder` cleanup
- `feature/style/data/FirebaseStyleRepository.kt` — implement `createStyles` batch
- `core/data/repository/FakeOrderRepository.kt` — implement batch methods
- `core/data/repository/FakeStyleRepository.kt` *(if present; verify in plan recon)* — implement `createStyles`

### Form state / actions / VM
- `feature/order/presentation/form/OrderFormState.kt` — replace single-image fields on `OrderItemFormState` with lists + bytes lists + toggle/description
- `feature/order/presentation/form/OrderFormAction.kt` — list-based actions: `OnItemAddStylePhoto`, `OnItemRemoveStyleImage(index)`, `OnItemAddFabricPhoto`, `OnItemRemoveFabricImage(index)`; keep `OnItemStyleDescriptionChange`, `OnItemSaveStyleToGalleryToggle`
- `feature/order/presentation/form/OrderFormViewModel.kt` — handlers; `save()` uses batch resolution path; `toOrderItemFormState` loads list fields; `loadOrderForSeed` strips uploaded refs

### Form UI
- `feature/order/presentation/form/OrderFormScreen.kt` — replace the existing `StyleSection*` composables with Variant B+ section composable (header row + image strip + chip row + toggle/description). Fabric section similarly. Source badges on thumbs.
- `feature/order/presentation/form/components/StylePickerSheet.kt` — minor: mark already-selected styles; stay open after pick until capacity is reached

### Detail screen
- `feature/order/presentation/detail/OrderDetailViewModel.kt` — change state from `style: Style?` to `styles: Map<String, Style>`; observe all library-source styleIds in the first item; update link-style flow to append
- `feature/order/presentation/detail/OrderDetailScreen.kt` — resolve `List<StyleImageDisplay>` via VM helper, pass into hero card
- `feature/order/presentation/detail/components/OrderHeroCard.kt` — `HeroImage` becomes count-aware (0 / 1 / 2+); 2+ uses `HorizontalPager` + dots + counter; tap → fullscreen viewer with the full list
- `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` — `FabricThumbnail` → `FabricStrip(fabricImages)`; first thumb keeps "Fabric" caption pill

### Shared
- `ui/components/FullScreenImageViewer.kt` — extend signature to `images: List<Any>, startIndex: Int = 0`; internal `HorizontalPager` + dots + counter; all existing single-image call sites updated to wrap in 1-element list

### Strings
~10–15 new keys covering:
- Section titles, count formatter (`%1$d of %2$d`)
- Source badges (`LIBRARY`, `NEW`)
- Picker sheet labels
- Renamed action chips (`Choose from saved styles`, `Upload new`)
- Save-to-gallery toggle label (renamed to plural: "Save uploaded styles to my library")

Finalized in the implementation plan (final string list locked there).

## 12. QA smoke (12 steps for the implementation plan to extend)

1. New order, customer with no saved styles → Style section shows `[Upload new]` chip only (no `Choose from saved`)
2. New order, customer with saved styles → both chips visible; tap `Choose from saved` opens picker sheet
3. Pick 3 styles from gallery → strip fills with 3 LIBRARY-badged thumbs; chips hide (max reached)
4. Upload 2 style images (toggle ON, with description) → save → both appear in customer's Style Gallery with the description
5. Upload 2 style images (toggle OFF) → save → order has `stylePhotoUrl` set on each via the new list; Style Gallery does NOT show them; OrderHeroCard renders the first via fallback
6. Mix: 1 library + 2 uploaded (toggle ON) → save → order has 3 LIBRARY refs (the original library one + 2 newly-created); customer gallery has 2 new entries
7. Mix: 1 library + 2 uploaded (toggle OFF) → save → order has 1 LIBRARY + 2 UPLOADED refs; gallery unchanged
8. Multi-image fabric (3 photos) → save → order has 3 fabric refs
9. Open order detail with 3 style images → carousel hero, swipe through, dots update, counter increments
10. Tap a fabric thumbnail in the garment card → fullscreen viewer opens, swipe between all 3 fabrics
11. Edit an existing PTSP-9 single-image order → Style/Fabric strip shows 1 image each (legacy → list migration); add more works
12. Duplicate a PTSP-9 single-image order via seedFromOrderId → new order starts with library-style refs preserved but uploaded refs stripped; user re-uploads if needed

## 13. Out of scope

- Long-press to reorder thumbnails (order = add order, fixed)
- Pinch-to-zoom in the fullscreen viewer
- "Primary" image flag distinct from list ordering
- Per-image description (single shared per section per the locked decision)
- Removing the legacy double-write code path (separate cleanup task in mid-2027)
- Bulk re-upload of multiple library styles' images at once (the gallery flow stays per-style)
- Reordering library refs via the picker sheet (sheet just appends in pick order)

## 14. Success criteria

- All 12 smoke steps pass on Android emulator AND iPhone hardware
- Cursor BugBot + `codex review` both clean before merge
- Old app versions (pre-PTSP-11) reading new orders see the first image of each category and don't crash
- New app versions reading old orders see the single image as a 1-element list and render correctly
- No regression in OrderHeroCard rendering for 0-style and 1-style orders
- Storage cleanup on order delete removes ALL uploaded photos (style + fabric) across the new list shape
