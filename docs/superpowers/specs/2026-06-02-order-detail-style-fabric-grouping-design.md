# Order Detail — group Style with Fabric under Garment details

**Ticket:** PTSP-13 — *Remove "Add style" from where it is on the order detail feature.*
**Date:** 2026-06-02
**Status:** Approved design, ready for implementation plan

## Problem

On the Order Detail screen, the style image area is the **first** thing on the screen. When no
style image exists it renders as a large empty placeholder (a 190dp box with a hanger icon and an
"Add style" button) that dominates the screen for no payoff. The reporter (PM) wants style and
fabric to live **next to each other** so a tailor sees the customer's fabric and style reference
in one place, under **Garment details**.

## Decision

Move all style imagery out of the top hero and into the **Garment details** card, directly
alongside fabric. The top of the screen becomes a clean, image-free **order summary card**. We
also reorder the screen so the design brief (Garment details) sits directly under the summary,
above the Customer card — because for a tailor mid-production the style + fabric + garment is the
primary working reference.

This goes one step beyond the PM's literal ask (which was only "move Add style into Garment
details"); the reorder and the summary-card cleanup were reviewed and approved.

## Final layout

### Top summary card (reworked from the current hero card)
- **No image.** The style image area and the empty "Add style" placeholder are removed entirely.
- **Headline = customer name** (e.g. "Success").
- **Subtitle = garment name + status** (e.g. "Kaftan · Pending").
- Deadline row: "Set deadline" CTA (or the resolved due label) on the left, "Total ₦X" on the right.
- Balance-remaining and overdue treatments are unchanged.
- **CTAs:** primary stays status-driven (e.g. "Start work"). The **secondary CTA replaces
  "Message customer" with "Record payment"**, kept status-driven (so a fully-paid order does not
  show a pointless "Record payment"). Rationale: the Customer card already exposes Call + WhatsApp,
  so a message button up top is redundant; record-payment is the more useful default action.

### Garment details card (gains a Style strip)
- Garment type name + description/fabric name as today.
- **Style strip** — a labeled, horizontally-scrollable thumbnail strip (mirrors the existing
  fabric strip). Shows the order's style images; when empty it shows a "＋ Add style" affordance.
- **Fabric strip** — unchanged from today (per garment item).
- Style sits **above** fabric within the card. The two strips are visually distinct (icon + label
  "Style" / "Fabric") but adjacent.

### Section order (top to bottom)
1. Order summary card
2. **Garment details** (Style strip + Fabric strip)  ← moved up
3. Customer
4. Payment
5. Production timeline
6. Measurements preview
7. Notes
8. Archive (DELIVERED only)
9. Footer caption

(Today the order is summary → Customer → Garment details; this swaps Customer and Garment details.)

## Behaviour & data (unchanged where not noted)

- **"Add style" trigger** still opens the existing `StylePickerSheet`. The
  `OrderDetailAction.OnAddStyleClick` action and all style-picker/link/create logic in the
  ViewModel stay the same — only the **trigger location** moves from the hero into the Garment
  details card.
- **Style images remain order-level** (resolved from `firstItem.styleImages` / the `styles` map,
  3-image cap). In the Garment details card the Style strip renders **once** at the card level
  (not per garment item). **Fabric stays per-item** exactly as today.
- **Fabric flow unchanged** — "Add fabric photo" → "Add fabric name" → done, and the fabric-name
  dialog all stay as-is.
- State (`OrderDetailState`), actions (`OrderDetailAction`), and events (`OrderDetailEvent`) need
  no new fields; `styleImageUrls` simply feeds the Garment details card instead of the hero.

## Components affected

| File | Change |
|---|---|
| `feature/order/presentation/detail/components/OrderHeroCard.kt` | Remove `HeroImage` / "Add style" placeholder + the `styleImageUrls` and `onAddStyleClick` params. Swap title/subtitle to customer-name headline + garment·status subtitle. Becomes an image-free summary card (consider renaming to `OrderSummaryCard`). Secondary CTA wiring: "Record payment" replaces "Message customer". |
| `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` | Add a card-level Style strip above the per-item fabric content. Accept `styleImageUrls` + `onAddStyleClick`. Reuse / generalize the existing `FabricStrip` into a shared labeled image strip (e.g. `ReferenceStrip`) used for both style and fabric, or add a sibling `StyleStrip`. |
| `feature/order/presentation/detail/OrderDetailScreen.kt` | Reorder `OrderDetailContent`: Garment details before Customer. Pass `styleImageUrls` + `onAddStyleClick` into `OrderGarmentDetailsCard`; stop passing them into the summary card. Wire the summary secondary CTA to the existing record-payment flow. |
| `OrderDetailState.kt` / `OrderDetailAction.kt` / `OrderDetailEvent.kt` | No new types expected; verify the CtaPair / secondary-CTA resolution surfaces "Record payment" appropriately. |

## Disadvantages accepted

- The screen loses its single large visual (the style hero). Mitigated by grouping style with
  fabric where it is actually used and by keeping the screen image-light rather than image-empty.
- The customer name now appears both as the summary headline and in the Customer card. Accepted as
  fine; the Customer card still adds "customer since" + Call/WhatsApp.
- The Garment details card carries more (garment + two strips + CTAs); keep spacing tight to avoid
  a tall, busy block.

## Out of scope

- No change to the style picker, style creation flow, or the underlying style/fabric data model.
- No change to fabric behaviour beyond sitting below the new Style strip.
- Multi-item style handling beyond "render the order-level Style strip once" is unchanged.

## Manual smoke test (Daniel is QA)

1. Open an order with **no** style and **no** fabric → top card shows customer headline + garment·status, no image box; Garment details shows "＋ Add style" and "＋ Add fabric photo" adjacent.
2. Tap "＋ Add style" → existing StylePickerSheet opens; link/create a style → it appears in the Style strip in Garment details (not at the top).
3. Add a fabric photo → appears in the Fabric strip directly below Style.
4. Confirm section order: summary → Garment details → Customer → Payment → …
5. Confirm secondary CTA reads "Record payment" and opens the record-payment flow; confirm a fully-paid order does not show it.
6. Multi-item order: Style strip shows once; each item keeps its own fabric.
7. Verify on both light and dark mode, and run an iOS build before declaring done.

---

## Amendment — 2026-06-02 (post-implementation polish, same PR)

After building the stacked Style-above-Fabric strips, two issues surfaced in review on device:

### A. Style & Fabric read as wasted space when single-image

The stacked layout pins each 64dp thumbnail to the **left** and stacks Style above Fabric, so a
common order (one style photo + one fabric photo) leaves a wide empty gutter running down the right
of the card. It reads as unbalanced and under-uses the horizontal space.

**Decision — side-by-side columns (was: stacked strips).** Replace the two stacked `ReferenceSection`s
with a single **`Row` of two equal-width columns**: **Style | Fabric**. This kills the gutter, keeps
the two photo-forward, and reinforces the "style + fabric as peers" intent.

- The existing `ReferenceSection` becomes a **`ReferenceColumn`** that fills the width it is given
  (a `Modifier.weight(1f)` cell, or full width).
- **First garment item:** `Row { ReferenceColumn(Style, weight 1f) ; ReferenceColumn(Fabric, weight 1f) }`.
  Style stays **order-level** — rendered once, paired with the first item's fabric.
- **Additional garment items** (rare multi-garment orders): their `ReferenceColumn(Fabric)` renders
  **full-width** below; no Style column for items 2+.
- **Media area, fixed height (~128dp), so both columns always align:**
  - **0 photos** → full-column placeholder tile (icon, primary @10% bg) + the "Add style /
    Add fabric" text CTA below (existing CTA gating preserved: Add fabric **photo** vs **name**).
  - **1 photo** → single tile, `fillMaxWidth`, fixed height, center-crop.
  - **2+ photos** → horizontal scroll of fixed-height tiles, each sized so the next **peeks** at the
    edge; a **count pill ("1/3")** top-right that tracks the scroll offset (derived from
    `scrollState.value`, no pager). Tapping any photo opens the existing `FullScreenImageViewer`.
- Equal fixed heights mean asymmetric states (one filled, one empty) still line up — no jagged edges.
- The old single-image caption-overlay treatment (white "Style"/"Fabric" pill on the photo) is
  dropped in favour of the column's mini-header label above the media.

### B. Overdue banner stretches full-width

The "Customer is waiting · N days overdue" red-tinted banner in the summary card stretches edge to
edge because `OverdueBanner` sets `Modifier.fillMaxWidth()` (`OrderHeroCard.kt`).

**Decision:** drop `Modifier.fillMaxWidth()` from the banner `Surface` so the pill **wraps its
content** — only as wide as the icon + text.

### Non-issue confirmed (no change)

The "Fabric" text that appeared under the garment name is the **fabric name** the tester typed into
the optional Fabric-name field, not a fallback label. `GarmentTextBlock` already renders the fabric
name only when non-blank, so "if no fabric name, keep it empty" is already the behaviour. No change.

### Amendment — components affected

| File | Change |
|---|---|
| `OrderGarmentDetailsCard.kt` | Replace stacked `ReferenceSection`s with a side-by-side `Row` of `ReferenceColumn`s (Style \| Fabric for the first item; Fabric full-width for items 2+). `ReferenceColumn` fills given width, fixed media height; 1 photo fills width, 2+ scroll with peek + "i/n" count pill; placeholder fills column. Update previews to the new states (1+1, multi-style scroll, asymmetric, both-empty, multi-item). |
| `OrderHeroCard.kt` | Remove `Modifier.fillMaxWidth()` from `OverdueBanner`'s `Surface` so it wraps content. |

### Amendment — smoke test additions

8. Order with **one** style photo + **one** fabric photo → two equal columns, no empty right gutter.
9. Order with **2–3** style photos → Style column scrolls, next photo peeks, "1/3" pill updates on scroll, tap opens full-screen viewer; Fabric column unaffected.
10. Asymmetric (style added, fabric empty — and vice-versa) → columns stay aligned at equal height; the empty side shows placeholder + Add CTA.
11. Overdue order → "Customer is waiting · N days overdue" pill wraps its text (not full-width).
12. Re-verify light + dark and run an iOS build before declaring done.
