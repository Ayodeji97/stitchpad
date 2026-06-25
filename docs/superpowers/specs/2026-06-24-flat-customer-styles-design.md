# Flat Customer Styles — Design Spec

**Date:** 2026-06-24
**Status:** Approved design, pending implementation plan
**Area:** `feature/style`

## Problem

Customer styles (images attached to a specific customer) currently use the same
folder model as the Inspiration/Lookbook gallery. In practice a customer holds
only a handful of style images, so the folder layer (folders + images-per-folder
caps) is unnecessary overhead. Inspiration galleries, by contrast, genuinely
benefit from folders and should keep them.

### Current behavior (`StyleCollectionLimits.forCustomer`)

| Tier    | Folders | Max folders | Images/folder | Flat cap |
|---------|---------|-------------|---------------|----------|
| Free    | off     | 0           | 0             | 5        |
| Pro     | on      | 5           | 3             | (3)      |
| Atelier | on      | 5           | 5             | (5)      |

## Goal

Customer styles become a **flat image gallery on every tier** — no folders —
with tiered flat caps. Inspiration galleries are untouched and keep folders for
Pro/Atelier.

### Target behavior (`StyleCollectionLimits.forCustomer`)

| Tier    | Folders | Flat cap |
|---------|---------|----------|
| Free    | off     | 5        |
| Pro     | off     | 15       |
| Atelier | off     | 25       |

Inspiration (`forInspiration`) is **unchanged**: Free flat-10; Pro 10 folders ×5;
Atelier 20 folders ×10.

## Approach

The entire downstream codebase already branches on `StyleCollectionLimits.foldersEnabled`.
Setting it to `false` for customers routes them through the existing, tested flat
path (today's Free experience). This makes the change a single-factory edit plus
test updates — no new UI, no navigation change, no data migration.

### The one code change

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimits.kt`

```kotlin
fun forCustomer(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
    SubscriptionTier.FREE    -> StyleCollectionLimits(foldersEnabled = false, 0, 0, flatCap = 5)
    SubscriptionTier.PRO     -> StyleCollectionLimits(foldersEnabled = false, 0, 0, flatCap = 15)
    SubscriptionTier.ATELIER -> StyleCollectionLimits(foldersEnabled = false, 0, 0, flatCap = 25)
}
```

## Why downstream needs no changes (verified)

- **Folder screen auto-redirects.** `StyleFoldersViewModel.onStart()` (line ~117)
  sends `RedirectToFlatGallery(customerId)` whenever `!foldersEnabled`. With the
  flag off, customer-style entry skips the folder list and lands on the flat
  gallery on every tier.
- **Read-time flatten across folders.** `StyleGalleryViewModel.observeFlattened`
  (taken when `!foldersEnabled`) calls `foldersWithStylesFlow` →
  `observeFoldersWithStyles(CustomerCloset)` and `flatMap`s every folder's styles
  into one list sorted by `createdAt`. Legacy customer styles that were filed in
  named folders therefore still surface in the flat gallery. No Firestore
  migration or query change.
- **Legacy writes stay correct.** `observeFlattened` populates `entryLocations`
  via `locationFor(folderId)`, preserving each style's original `folderId`. Edit,
  delete, and transfer of legacy folder-filed styles continue to target the right
  location.
- **Cap gating reuses the flat path.** `OnAddClick` (line ~90), `StyleCapInfo`,
  and `StyleLockPolicy` all read `flatCap` when `!foldersEnabled`. Raising
  Pro→15 / Atelier→25 means existing paid users see *fewer* locks than before,
  never more.
- **Transfer to a customer** targets the flat closet (`folderId = null`); the
  folder-destination branch is gated off by the flag. The destination-folder
  picker (`onTargetSelected` → `destinationFolders`) is reached only when the
  target's `foldersEnabled` is true. With customer folders off, copying/moving a
  style **to a customer** takes the flat path (`onTargetSelected` lines ~221-240:
  flat-cap check, then direct transfer). The picker survives **only for the
  Inspiration target** (transferring a customer's style into the Pro/Atelier
  lookbook), which is still offered from a customer closet (`openTransfer`
  line ~186-187). No transfer code changes — this is all driven by the flag.

## Scope boundaries (YAGNI)

- **No** Firestore migration or data write.
- **No** navigation graph change.
- **No** change to `forInspiration` or any inspiration code path.
- Customer-style folder creation / folder navigation simply stop being reachable
  (gated off by `foldersEnabled = false`).

## Risks

- **Low.** The flat path is the existing Free experience, already shipped and
  tested. The only behavioral shift for Pro/Atelier customer styles is: folders
  disappear from the UI and all images show in one flat list with a higher cap.
  Raising caps cannot newly lock anyone.

## Testing

The production change is a single factory, but it deliberately removes
customer-folder behavior, so several existing tests that asserted that behavior
must be updated. Enumerated surface (verified against current test files):

**`StyleCollectionLimitsTest`** (domain) — TDD anchor:
- Rewrite `pro_customer_5folders_3each` → `pro_customer_flat_15`: assert
  `foldersEnabled == false`, `flatCap == 15`.
- Rewrite `atelier_customer_5folders_5each` → `atelier_customer_flat_25`: assert
  `foldersEnabled == false`, `flatCap == 25`.
- `free_hasNoFolders_flatCaps` already asserts Free `forCustomer` flatCap == 5 —
  keep unchanged.

**`StyleFoldersViewModelTest`** (presentation):
- `createBlockedAtCap_customerFoldersPro_offersNoUpgrade` tests a customer folder
  cap that no longer exists. Replace with a test that a **Pro/Atelier customer**
  closet immediately emits `RedirectToFlatGallery(customerId)` (folders off on
  every tier now), mirroring the existing Free redirect test.

**`StyleGalleryViewModelTest`** (presentation):
- `proTier_closet_perFolder_locksOldestOverFolderCap` and
  `proTier_closet_perFolder_scrambledDates_sortsNewestFirst_andLocksOldest`:
  Pro customer now flat with cap 15. Update to seed >15 styles to exercise
  locking (or assert 0 locked under cap) and keep the newest-first sort check.
- `transferToPaidTarget_showsDestinationFolders`,
  `onDestinationFolderSelected_named_copiesToThatFolder`,
  `onDestinationFolderSelected_fullFolder_setsCapSheet_noCopy`,
  `transfer_paidTier_whenDestinationCountReadErrors_noCopy_errorSurfaced`:
  these exercise the paid folder-picker via a **Customer** destination, which no
  longer has folders. Move them to the **Inspiration** target (still folder-
  enabled on Pro) to keep covering the picker path.
- `observeError` test (line ~180) and `onErrorDismiss_clearsErrorMessage`
  (line ~757) use a PRO **customer** gallery to hit the per-folder error-
  propagation path (`observePerFolder`). With customer folders off, a PRO
  customer now uses `observeFlattened` (keep-last, silently degrades) and would
  not surface the error. Switch these to an **Inspiration** PRO gallery
  (`customerId = null`) to keep exercising `observePerFolder`.

**`StyleFormViewModelTest`** (presentation):
- `save_batchOverCap_setsCapSheet_stylesPro`, `onDismissCapSheet`,
  `onUpgradeFromCap`, `maxPhotoSelection_proFolderWithExisting_clampsToRemaining`:
  these assume Pro customer `maxImagesPerFolder = 3`. Pro customer is now flat
  with cap 15. Drop the `folderId = "f1"` arg and update seed counts to the flat
  cap (e.g. seed 15 existing to hit the cap; seed 2 → expect 13 remaining).
- `maxPhotoSelection_emptyFreeCustomer_isFlatCap` (Free flatCap = 5) — unchanged.

Note: after the Task-1 production change, the gallery/form/folders tests above
go red until their tasks land. This is expected; the suite returns green at the
final task.

- Confirm `StyleLockPolicy` behavior at the new caps (oldest-beyond-cap lock) for
  customer context via the updated gallery lock tests.

### Manual smoke test (Daniel = QA)

1. As an **Atelier** account with a customer that has styles in multiple folders:
   open that customer's styles → confirm it opens the **flat gallery** (no folder
   list) and **all** images from every old folder appear, newest first.
2. Add a new style to the flat customer gallery → confirm it saves and appears.
3. Edit and delete one **legacy folder-filed** style → confirm both succeed.
4. Add styles until the cap (25 Atelier / 15 Pro / 5 Free) → confirm the cap
   sheet appears at the right number.
5. As **Free**, confirm customer styles still cap at 5 (unchanged).
6. Open an **Inspiration** gallery on Pro/Atelier → confirm folders still work
   (regression check — must be untouched).
7. Transfer a style **to a customer** → confirm it lands in the flat closet.
