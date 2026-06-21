# Style-image selection UX — design

**Status:** Design locked (2026-06-21)
**Branch:** `feat/style-image-selection-ux`
**Tickets:** PTSP-43 (+ order-flow alignment) now; PTSP-44 deferred.
**Mockups:** `.superpowers/brainstorm/61749-1782058084/content/` (item2-title-cta, item3-saved-style-picker-v3)

## Problem

Three related friction points in how tailors attach style photos:

- **PTSP-43** — In "Add style", once photos are selected, "tap to change" re-opens the
  picker and **replaces the entire batch**; you can't remove or add one photo without
  starting over.
- **Order-flow inconsistency** — The three "Add style reference" sources behave
  differently. Saved-style picking is one-tap-adds-immediately with no deselect and
  no clear feedback (the PM thought it was broken); phone-gallery is single-select.
- **PTSP-44** (deferred) — On the Order-detail screen the picker sheet doesn't dismiss
  after a pick, so users think the upload failed.

This spec covers **two work items**, each shipping as its own PR. PTSP-44 and any
multi-capture camera behavior are **out of scope** (camera stays single-select).

---

## Work item 2 — "Add style": flexible photos, retire description, per-style title

PR: `feat/ptsp-43-add-style-flexible` (off this spec).

### 2a. Editable selected-photos grid
**Screen:** `feature/style/presentation/form/StyleFormScreen.kt` (`MultiPhotoPreview`,
`SinglePhotoPreview`), VM `StyleFormViewModel.kt`, state `StyleFormState.selectedPhotos`.

- Each selected thumbnail shows a **✕ (top-right)** that removes just that photo.
- A dashed **"＋ Add more"** tile (shown while under the cap) opens the gallery and
  **appends** to `selectedPhotos` (up to the remaining cap) instead of replacing.
- Tapping a photo no longer "replaces all" — that behavior is removed.
- Count line updates live ("4 of 6"); the ＋ tile disappears at the cap.
- New actions: `OnRemovePhoto(index)`, and `OnPhotosPicked` becomes **additive**
  (`selectedPhotos = (existing + processed).take(cap)`), not a wholesale replace.
- Keep the existing `maxPhotoSelection` cap logic and the peekaboo
  `SelectionMode.Multiple` guard (falls back to `Single` when only 1 slot remains —
  see [[feedback_peekaboo_multiple_maxselection]]).

### 2b. Retire `description`
- Remove the description field from `StyleFormScreen` in **all** modes (single-add,
  multi-add, edit) and from any closet/inspiration add path. **Edit mode becomes
  photo-only** (replace the style's photo); the title is no longer set here — it's set
  via the per-style CTA in 2c.
- **Data:** repurpose the existing `Style.description` field as the new `title` — no
  migration; existing values carry over as titles. (Rename in the domain model to
  `title`; keep the Firestore field name to avoid a data migration, mapping
  `description` ⇄ `title` in the DTO mapper.)

### 2c. Optional per-style title + CTA
- On each **style card in the customer-closet and inspiration browsing grids** (the
  screens where saved styles are listed — not the order picker): when the title is
  empty, show a **"＋ Add title"** CTA; when set, show the **title + an edit pencil**.
- Tapping opens a **bottom-sheet title editor**: a single-line text field +
  "Save title" + "Remove title". (Bottom sheet per [[feedback_notification_patterns]].)
- New: a `setStyleTitle(styleId, title?)` repository call (update only the title
  field); a small `StyleTitleSheet` composable + state on the screens that list styles.
- Title is optional and per-style — different photos saved in one batch can be
  titled independently or left blank.

---

## Work item 3 — order-flow: gallery multi-select + saved-style picker redesign

PR: `feat/ptsp-43-order-style-picker` (off this spec / main after item 2).

### 3a. Phone-gallery multi-select
**Sites:** `OrderFormScreen.kt` (`styleGalleryPicker`, ~L1370) and the equivalent in
`OrderDetailScreen.kt`.

- Change `SelectionMode.Single` → `SelectionMode.Multiple(maxSelection = remaining)`
  where `remaining = MAX_IMAGES_PER_CATEGORY (3) − current refs`, with the same
  `≤1 → Single` guard. `onResult` adds each returned image as an `UPLOADED`
  `StyleImageRef`, capped at `remaining`.
- **Camera unchanged** (single capture).

### 3b. Saved-style picker redesign (`StylePickerSheet`)
**File:** `feature/order/presentation/form/components/StylePickerSheet.kt`; cap
`MAX_IMAGES_PER_CATEGORY = 3` in `OrderFormScreen.kt`; state in `OrderFormState`
(`stylePickerSheetForItemId`, `stylePickerSource`, `pickerOpenFolderKey`).

Today: tap immediately adds one ref and the sheet auto-dismisses at the cap; no
deselect. New model = **batch select, commit on Done**:

- **Local pending-selection state** in the picker (an ordered list of selected style
  ids), separate from the item's committed refs. Nothing is added to the order item
  until **Done**.
- **Selection badge (top-right):** an empty white ring on selectable cards; tapping
  fills it with a **blue numbered badge ①②③** (white ring, design-token indigo
  `#5871B8`). Tap again to **deselect**; remaining badges **renumber**.
- **"Already added" pill (top-right):** styles already referenced on this item —
  dimmed, **count toward the cap**, not selectable here (removed via the form's ✕
  chips, not the picker).
- **Cap = 3 total** per item (already-added + newly selected). Header line:
  "N selected · M already added · X of 3". When **full**, unselected cards **gray out
  + disable**; deselecting one frees a slot and re-enables them.
- **Sticky "Done · N selected" bar** commits all pending picks (appends `LIBRARY`
  `StyleImageRef`s) and dismisses. Back / swipe-down cancels without adding. The old
  auto-dismiss-on-cap behavior is removed in favor of explicit Done.
- Folder drilling + Closet/Inspiration tabs are unchanged.

### 3c. (Stays as-is)
The Order-form "tap immediately adds" handler (`OnItemPickSavedStyle`) is replaced by
a commit-on-Done batch action (`OnItemCommitPickedStyles(itemId, styleIds)`).

---

## Out of scope

- **PTSP-44** (picker sheet not dismissing on Order-detail) — deferred; the user will
  provide extra clarity, then it ships separately.
- **Multi-capture camera** — system cameras return one photo per capture; camera stays
  single-select. Frictionless re-capture is the existing behavior.
- Unifying the Order-form vs Order-detail picker variants (noted as a future cleanup).

## Affected files (map)

- Item 2: `StyleFormScreen.kt`, `StyleFormViewModel.kt`, `StyleFormState/Action.kt`;
  `Style.kt` (description→title); the style DTO + mapper; the closet/inspiration style
  grids + a new `StyleTitleSheet`; `StyleRepository` (+ impl) `setStyleTitle`.
- Item 3: `StylePickerSheet.kt`, `OrderFormScreen.kt`, `OrderFormViewModel.kt`,
  `OrderFormState.kt` (pending-selection); `OrderDetailScreen.kt` gallery mode.

## Error handling

- Photo-too-large already handled in `StyleFormViewModel.onPhotosPicked`; keep, but
  scope the error to the failing photo(s) rather than wiping the batch where feasible.
- Gallery multi-pick exceeding `remaining` is truncated to `remaining` (no error).
- All Coil/AsyncImage usages keep the `LoadingDots` loading slot
  ([[feedback_image_loading_dots]]).

## Testing

- ViewModel unit tests: additive `OnPhotosPicked` + `OnRemovePhoto` (item 2); picker
  pending-selection add/deselect/renumber + commit-on-Done + cap enforcement (item 3).
- Manual smoke test per PR ([[feedback_qa_smoke_tests]]), on device:
  - Item 2: add 4 photos, remove 1, add 2 more (append, not wipe); save; add a title
    via the CTA; edit/remove the title.
  - Item 3: open saved-style picker with 1 already-added; select 2 (badges 1,2); cap
    full grays the rest; deselect one → re-enables; Done commits both; gallery
    multi-select returns 2 and both attach.
