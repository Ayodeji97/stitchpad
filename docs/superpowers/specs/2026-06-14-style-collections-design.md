# Style Collections — named folders for styles (PTSP-38 phase 3)

## Context

Tester feedback (BWStitches): let tailors organise styles into **named folders/collections** by type — e.g. "Corset", "Blouse", "Agbada" — so that when a client asks for a corset, the tailor opens the Corset folder and picks. Requested on both the **Inspiration** level (phase 2, #164) and the **customer-closet** level.

Daniel's product guardrail: keep this from turning the app into an image dump (the app's core is measurements). So **collections are a paid feature** with hard caps, and they double as an upgrade lever.

This is **phase 3**, building on the flat styles model shipped in #159/#162/#164. #164 (flat Inspiration) merges first.

### Decisions (confirmed with Daniel)
- **Packaging:** feature-gated by `subscriptionTier` (no new pricing SKU).
- **Model A — everything lives in a folder** (for paid users). No separate flat overflow; hitting the cap = upgrade nudge.
- **Free stays flat** (today's single gallery), with a cap.
- **Caps:**

| Tier | Inspiration | Per customer |
|---|---|---|
| **Free** | 10 styles (flat, no folders) | 5 styles (flat, no folders) |
| **Pro** | 10 folders × 5 = **50** | 5 folders × 3 = **15** |
| **Atelier** | 20 folders × 10 = **200** | 5 folders × 5 = **25** |

  Per-folder image caps: Inspiration 5 (Pro) / 10 (Atelier); customer 3 (Pro) / 5 (Atelier). The default **"My styles"** folder **counts as one** of the tier's folders (so Pro = the default + up to 9 named = 10 folders × cap = 50; Atelier = default + 19 named = 200). This makes the totals match exactly.
- **Existing over-cap Free users:** grandfathered **read-only-visible** (see everything saved; can't add until under the cap or upgraded) — reuses the freemium V1.0 "locked = read-only-visible" pattern.
- **Caps enforced client-side** via the entitlement + live counts (block the over-cap folder/image with an upgrade prompt). Server-side count hardening is a later follow-up, not V1.

## Model & data — the default folder trick (migration-free)

Reuse the phase-2 `StyleLocation` abstraction and extend it with an optional folder:

```kotlin
sealed interface StyleLocation {
    data class CustomerCloset(val customerId: String, val folderId: String? = null) : StyleLocation
    data class Inspiration(val folderId: String? = null) : StyleLocation
}
```

- **`folderId == null`** → the existing flat collection (`customers/{cid}/styles`, `users/{uid}/inspiration`). This is the **default "My styles" folder** — no data migration; existing styles already live here.
- **`folderId != null`** → a named folder's styles subcollection (below).

Note: phase-2's `StyleLocation.Inspiration` was a `data object`; it becomes a `data class Inspiration(folderId)`. All existing `StyleLocation.Inspiration` references (phase 2) update to `StyleLocation.Inspiration()`.

Named folders are new docs:
- `users/{uid}/customers/{cid}/styleFolders/{folderId}` — `{ name, coverStyleId?, createdAt, updatedAt }`, with a `styles/{styleId}` subcollection.
- `users/{uid}/inspirationFolders/{folderId}` — same shape + `styles` subcollection.

So **the flat collection IS the default folder**; named folders are additive. "Everything in a folder" (Model A) holds because the default counts as a folder in the UI. No migration of existing data.

## UX

- **Free:** unchanged — the current flat `StyleGalleryScreen`, now capped (10 Inspiration / 5 per customer). No folder UI.
- **Paid:** the closet/Inspiration entry opens a **folders grid** — each folder a card (cover image, name, count), matching the tester's mockup. A pinned **"My styles"** card represents the default (flat) folder. Tap a folder → today's styles grid scoped to that folder. A **"+ New folder"** affordance opens a name sheet; blocked at the folder cap with an **upgrade nudge** (Pro→Atelier or Free→Pro). Adding photos happens **inside** a folder (multi-pick), blocked at the per-folder image cap.
- Folder **rename / delete** via long-press (delete moves nothing's-orphaned: per phase-1, style deletes are doc-only; deleting a folder deletes its style docs but never eagerly deletes shared storage). Default "My styles" can't be renamed/deleted.
- Empty-folder and cap-reached states have their own copy + upgrade CTA.

## Interactions with existing features
- **Copy/move (#162):** the transfer target picker gains a **folder step** — pick customer/Inspiration, then which folder to drop into (and move between folders within a place). The cap applies to the destination folder.
- **Order style picker (#164-area):** when picking saved styles for an order, **flatten across the customer's folders** (show all the customer's styles regardless of folder) so the picker stays simple. The deferred "Inspiration source in the order picker" follow-up is unchanged/separate.
- **Dashboard Inspiration entry (#164):** opens the folders grid for paid users, the flat gallery for Free.

## Enforcement
A small `StyleCollectionLimits` resolver maps `subscriptionTier` → `{ maxFolders, maxImagesPerFolder }` per level (Inspiration vs customer). ViewModels read it via the existing `EntitlementsProvider` and gate: creating a folder checks the live folder count; adding photos checks the live count in that folder; Free's flat-gallery add checks the flat cap. Over-cap actions surface an upgrade Snackbar/sheet (per notification-patterns). Reads are never blocked (read-only-visible).

## Sequencing
1. **#164 merges first** (flat Inspiration — done/reviewed).
2. **Phase 3 (this spec)** on a new branch off the updated `main`.

## Testing
- `StyleCollectionLimits` per tier × level (folder + image caps).
- ViewModel: folder list per location; create-folder blocked at cap (emits upgrade event); add-photo blocked at per-folder cap; Free flat-gallery add blocked at flat cap; over-cap reads still succeed.
- Repository: folder CRUD paths (`styleFolders` / `inspirationFolders`), styles scoped to a folder (`folderId`), default-folder = flat collection.
- `FakeStyleRepository` records folder ops.

## Verification
- Unit tests green (`:composeApp:testDebugUnitTest`), iOS compile, `detekt` clean.
- Manual: as a Pro user, create folders up to 10 (Inspiration) and confirm the 11th is blocked with an upgrade nudge; add 5 images to a folder and confirm the 6th is blocked; as Atelier confirm 20×10; as Free confirm the flat 10/5 cap + read-only-visible when over; copy a style into a named folder; order picker still lists the customer's styles across folders. Repeat on iOS. **Ops:** the Storage rule for the inspiration path (already noted in #164) covers folder images too since paths stay under `users/{uid}/...`.
