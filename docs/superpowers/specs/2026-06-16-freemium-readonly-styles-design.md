# Freemium: read-only "locked" styles instead of disappearing on downgrade

- **Date:** 2026-06-16
- **Status:** Design — awaiting review
- **Branch:** `feat/freemium-readonly-styles` (off `main`; should land after / rebase onto PR #173, which also touches the style-form area)
- **Related:** `docs/design/freemium-v1.0-design-spec.md` (Decision #2: "Locked = read-only-visible; we never delete your data")

## Problem

When a user downgrades from Pro/Atelier to Free, styles they had in named folders **disappear from the UI**. The data is NOT deleted — it stays in Firestore — but the Free closet redirects to a flat gallery that only reads the *root* location, so folder-contained (and over-cap) styles are silently hidden.

This (a) looks like data loss and erodes trust, and (b) violates the app's own freemium principle, already honored for *customers* (locked = visible, read-only) but not for *styles*.

A second, related case: **Atelier→Pro** keeps folders enabled but lowers the per-folder image cap (5→3), so a folder can hold more images than the new tier allows.

## Goals

- Free never hides or deletes a user's styles. Everything stays viewable.
- Over-cap styles are presented **read-only** ("locked") with an Upgrade path, consistent with locked customers and Decision #2.
- Newest styles remain fully usable up to the tier cap; older over-cap styles lock.
- Non-destructive and reversible: re-subscribing restores full editing; nothing is ever deleted by tier change.

## Non-goals / out of scope (follow-ups)

- **Folder-count over-cap** (Atelier→Pro Inspiration: 20 folders → Pro allows 10). Folders remain browsable; only *creating* a folder past the cap is gated (existing behavior). No data is hidden, so this is not part of this change. Flagged for a separate review.
- Any server-side reconcile / persisted `slotState` for styles. The lock is computed client-side at read time (see below).
- Changing tier caps themselves.

## Decisions (confirmed)

1. **Scope:** customer closets **and** Inspiration.
2. **Locked set:** newest `cap` styles stay active (editable); older ones lock. (Mirrors customers keeping most-recent slots.)
3. **Locked actions:** tap → **read-only view**; any edit / delete / add → existing Upgrade sheet.
4. **Read-only view:** reuse the existing style edit screen in a `readOnly` mode (no new viewer).
5. **Atelier→Pro:** included — the same locking policy applies inside Pro/Atelier folder views (per-folder image over-cap).

## Design

### 1. Data layer — reuse existing aggregation

`CustomerStyleFlattening.kt` already provides (tested, resilient keep-last):
- `StyleRepository.observeAllCustomerStyles(userId, customerId)`
- `StyleRepository.observeAllInspirationStyles(userId)`

On **Free** (`!foldersEnabled`), the flat gallery observes the flattened stream instead of root-only `observeStyles(rootLocation)`. Pro/Atelier folder views keep using `observeStyles(location)` (per-folder).

### 2. Locking policy — new pure function (unit-tested)

```
fun lockStyles(styles: List<Style>, activeCap: Int): List<LockedStyle>
```
- Sort by `createdAt` descending.
- Index `< activeCap` → `isLocked = false`; index `>= activeCap` → `isLocked = true`.
- `LockedStyle(style, isLocked)` (or add `isLocked` to the gallery's UI model).

`activeCap` is the value the gallery already computes:
`if (!foldersEnabled) limits.flatCap else limits.maxImagesPerFolder`. This single primitive covers both the Free flattened view and the Pro/Atelier per-folder view (Atelier→Pro).

### 3. Cap counting fix (Free)

Today the gallery cap check and `StyleFormViewModel.computeMaxPhotoSelection` count styles at a single *location* (root on Free). On Free they must count the **flattened total** so a user can't exceed the real per-closet cap by adding into root while folders hold the rest. Use the flattened stream's size for the cap/at-cap/remaining computations when `!foldersEnabled`.

### 4. Presentation

- **Gallery (`StyleGalleryScreen`/`StyleGalleryViewModel`):**
  - Render a lock badge overlay on locked thumbnails (design-system lock affordance; respects light/dark per spec rule).
  - `OnStyleClick` on a locked style → navigate to the edit screen in `readOnly` mode (active styles behave as today → editable edit screen).
  - Add (`+`) when at cap, or any locked-item edit/delete intent → existing `capSheet` / `NavigateToUpgrade`.
- **Style edit screen (`StyleFormScreen`/`StyleFormViewModel`):** add a `readOnly` input (route arg or derived). In read-only mode: show photo + description, disable inputs and photo replacement, replace the Save button with an "Upgrade to edit" CTA → `NavigateToUpgrade`.
- **Folders redirect:** Free folders→flat redirect stays (`StyleFoldersViewModel`); the destination flat gallery now shows the flattened, lock-marked list.

### 5. Navigation

`NavigateToEditStyle` gains a `readOnly` flag (default false) carried on the style edit route. Active styles pass false; locked styles pass true.

## Affected files (approx.)

- `feature/style/domain/` — new `StyleLockPolicy.kt` (pure `lockStyles`).
- `feature/style/presentation/gallery/StyleGalleryViewModel.kt` — Free uses flattened stream + flattened cap count; mark locked; route locked taps read-only.
- `feature/style/presentation/gallery/StyleGalleryState.kt` / UI model — carry `isLocked`.
- `feature/style/presentation/gallery/StyleGalleryScreen.kt` — lock badge overlay.
- `feature/style/presentation/form/StyleFormViewModel.kt` — `readOnly` mode; flattened cap count on Free in `computeMaxPhotoSelection`.
- `feature/style/presentation/form/StyleFormScreen.kt` — read-only rendering + Upgrade CTA.
- `navigation/` — `readOnly` on the style edit route + `NavigateToEditStyle`.
- (Inspiration gallery path shares the same gallery components.)

## Testing

- **Unit (pure):** `StyleLockPolicy` — under cap → none locked; over cap → newest `cap` active, rest locked; tie/order by `createdAt`; cap 0 / empty list.
- **ViewModel:** gallery on Free emits flattened, lock-marked list with correct active/locked split; at-cap uses flattened count; locked tap emits read-only navigation; Pro/Atelier folder view locks per-folder over-cap (Atelier→Pro).
- **Form VM:** `readOnly` mode disables save and emits Upgrade on edit intent; `computeMaxPhotoSelection` uses flattened count on Free.
- iOS compile + detekt green.

## Manual smoke test

1. Pro account, customer closet with 2 folders × 3 images. Downgrade to Free. Open the customer → all images visible in one flat gallery; newest 5 normal, rest show a lock badge.
2. Tap a locked image → opens read-only (photo + description, no editing, "Upgrade to edit").
3. Tap an active image → edits normally. Try to add a 6th → Upgrade sheet.
4. Re-subscribe to Pro → folders + full editing return; nothing was lost.
5. Atelier account, a folder with 5 images. Downgrade to Pro → that folder shows newest 3 active, 2 locked.
6. Inspiration: repeat 1–4 for the Inspiration collection.

## Rollout / risk

- Purely client-side and non-destructive; no Firestore schema or rules change.
- Touches the style-form area also touched by PR #173 — rebase/land after it to avoid conflicts.
- Behind no flag (it's a correctness/UX fix), but ships within the freemium V1.0 workstream.
