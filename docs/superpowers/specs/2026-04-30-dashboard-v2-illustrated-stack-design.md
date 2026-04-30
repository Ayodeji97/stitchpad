# Dashboard V2 — Illustrated Stack — Design Spec

## Context

The current `DashboardScreen` (`feature/dashboard/presentation/DashboardScreen.kt`) renders nine vertically-stacked sections — header → focus card → weekly goals → reconnect → quick start → KPI tiles → today's work → NBAs → pipeline. Even in sparse states, the screen exceeds the viewport: the FAB overlaps content and the second pipeline row is cut off at the fold (verified in screenshots taken 2026-04-30 of the `ReadyForPickup` state). The dashboard reads as a feed instead of a desk.

This redesign follows the recently-shipped Reports V2 premium layout (PR #29) and the working principle in memory — *"Reports = template; dashboard refactor follows."* It also incorporates direction the user established after reviewing illustrated mockups generated externally: drop the four-tile KPI grid (redundant with Reports' production-status block), keep the revenue goal / Today's Work / NBA / Pipeline / Reconnect, and add an illustrated focus card with empty-state illustrations to give the dashboard the same craft-app personality the rest of the V2 work is heading toward.

This spec covers presentation only — no domain, repository, or Firestore changes. Every value rendered already has an extracted calculator in `feature/dashboard/domain/`.

---

## Scope

**In scope**
- Replace `DashboardContent` section list with the seven sections defined below.
- New composables: `IllustratedFocusCard`, `TodayWorkCard`, `PipelineDualCard`, `ReconnectChipStrip`.
- Lightly modified composables: `DashboardHeader` (add bell + avatar slots), `WeeklyGoalsCard` (no structural change; trophy/celebration variant already exists).
- State-driven hero variants for all eight `DashboardUiState` / `FocusVariant` cases.
- Illustrated empty states for: empty pipeline, empty NBA, brand-new (no customers).
- Light + dark mode parity using existing tokens.

**Out of scope**
- Animation / shared-element transitions between states.
- Settings screen for VIP thresholds (already tracked as a separate backlog memory).
- Bottom-nav redesign.
- Reconnect promotion to a *full section* on `QuietDay` — the chip-row covers all eight states for V1; full-section reconnect is a v2.1 follow-up.
- New domain calculators — every value flows from the five calculators already extracted in `feature/dashboard/domain/`.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Layout model | Vertical stack with illustrated hero | User explicitly chose Option A after side-by-side comparison of A (stack), B (split), C (compressed). Stack gives every section room to breathe. |
| KPI tile grid | **Removed** | User confirmed redundancy with Reports' production-status block. |
| Weekly goal card | **Kept** as-is, with trophy "ahead of target" variant | Already exists, motivating, no change needed. |
| Today's Work | New `TodayWorkCard` (avatar · name+garment · time · chip) | GPT-style row layout reads cleaner than current `AccentedOrderRow` siblings. Reuses `AccentedOrderRow` internally — only the wrapper changes. |
| NBA layout | 2-up grid of mini cards | Saves vertical space vs current carousel; each card stays tappable. |
| Pipeline | New `PipelineDualCard` — In-progress / Not-started in two columns inside one card | Denser than current two-section layout (`pipelineInProgress` + `pipelinePending` rendered separately). One card = one cognitive unit. |
| Reconnect | New `ReconnectChipStrip` — pill-shaped footer | Subtle when Reconnect isn't the main story; never gets cut off below the fold. |
| FocusTodayCard | **Replaced** by `IllustratedFocusCard` | Adds 88dp illustration slot; same eight FocusVariant accents. |
| QuickStartTiles | **Removed** | Folded into the BrandNew variant of the new focus card (CTA + supporting copy is enough). |
| Illustrations | 7 hero + 3 empty-state PNGs, generated externally | Cannot be authored in code. User generates via DALL·E with locked style prompt (below). HTML preview ships with hand-authored SVG placeholders. |
| Header | Greeting + bell (with red unread dot) + circular avatar | Matches GPT mockups; bell + avatar are conventional Android patterns. Bell tap → notifications screen (out of scope; placeholder no-op). |
| FAB | Unchanged — `RoundedCornerShape(16.dp)` saffron square | Already correct per memory. |
| Settings entry | Stays on the avatar tap | Replaces the standalone settings icon currently shown in `DashboardHeader`. |

---

## Section Anatomy (top to bottom)

### 1. `DashboardHeader` (modified)

**Before:** greeting + business name + settings icon (top-right).

**After:** greeting + date (no business name — moved into avatar tap menu) + bell with notification dot + circular avatar (initial of `firstName`, gradient background).

```
┌──────────────────────────────────────────┐
│ Good afternoon, Olawale       🔔  (O)    │
│ Thursday, 30 Apr                         │
└──────────────────────────────────────────┘
```

- `firstName` and `greeting` already in `DashboardState`.
- `todayDate` already in `DashboardState`.
- Bell unread dot: visible when notifications feature exists (currently always hidden — hide via `if (false)` for now, swap when notifications ship).
- Avatar: gradient `linear-gradient(135deg, primary700, primary900)` with `firstName.first()` in cream.

### 2. `IllustratedFocusCard` (new — replaces `FocusTodayCard`)

Single horizontal layout: text on left (title + supporting + CTA), illustration on right (88dp square, transparent PNG fading into card background).

```
┌──────────────────────────────────────────┐
│ Title text (one line)            [ART]   │
│ Supporting copy (1–2 lines)              │
│                                          │
│ CTA →                                    │
└──────────────────────────────────────────┘
```

Variants → see **State Matrix** below.

### 3. `WeeklyGoalsCard` (kept)

No structural change. Trophy "AHEAD OF TARGET" variant already exists. Remove the empty `gh-edit` chevron — "Raise your goal →" CTA stays.

### 4. `TodayWorkCard` (new)

Card with header strip ("Today's Work · View all") and 3 rows. Each row:

```
[Avatar 28dp] [Name + Garment]  [Time + chip]  [chev]
```

- `BucketCalculator` provides three lists — overdue, dueToday, ready. Card shows up to 5 rows total, grouped by bucket in priority order (overdue → due → ready). When > 5, "View all (n)" chevron.
- Status chip colors: red (Due today / Overdue), amber (Fitting today), green (Ready pickup).
- Empty state: hidden entirely. The illustrated focus card already covers "nothing today" via the QuietDay variant — no second redundant empty card.

### 5. `NextBestActionsRow` (modified — was `NextBestActionsSection`)

2-up grid (instead of horizontal carousel). Each `NbaMini`:

```
[Icon 28dp] [Action / Customer + amount]  [chev]
```

- `NbaCalculator` outputs up to 5 NBAs ranked by priority. Show up to 4 in the 2×2 grid; when 5+ exist, the bottom-right cell becomes a "+N more" navigation tile (not an action card) that opens the full NBA list.
- When `nextBestActions.isEmpty()`: show illustrated empty state (see Empty States below). Not a lightbulb card.

### 6. `PipelineDualCard` (new)

Single card, two columns:

```
┌──────────────────┬──────────────────┐
│ ▣ In progress · 4│ ✕ Not started · 6│
│ AP Adeyinka P.   │ PP Pooja Paul    │
│ BT Blessing T.   │ TO Tolu Ojo      │
│ View all →       │ View more →      │
└──────────────────┴──────────────────┘
```

- Each side shows up to 2 rows + an "View all/more" link footer.
- Counts come from `BucketCalculator.pipelineInProgressTotal` / `pipelinePendingTotal`.
- When pipeline is empty: replace card with illustrated empty state ("No work in flight yet" — the mockup the user explicitly liked).

### 7. `ReconnectChipStrip` (new — replaces current `ReconnectStrip`)

Single horizontally-scrollable pill row at the bottom of the screen, above the bottom nav:

```
[👤 Reconnect]  [TA Tolu A. · 45d]  [FA Funmi A. · 2mo]  [chev]
```

- `ReconnectCalculator` provides up to 5 candidates. Strip shows up to 3 chips inline; tap chevron → full reconnect list.
- When `reconnectCandidates.isEmpty()`: hide the strip entirely (don't render an empty pill).

---

## State Matrix — `FocusVariant` → `IllustratedFocusCard`

The eight `DashboardUiState` cases reduce to seven hero variants (Loading is its own screen). `FocusResolver` already picks the variant; this spec only adds the illustration slug.

| `DashboardUiState` | `FocusVariant` | Illustration slug | Tone | Title (existing in state) |
|---|---|---|---|---|
| `BrandNew` | `BrandNew` | `welcome.png` | Saffron · sparkly | *"Let's create your first order"* |
| `FirstCustomer` | `FirstOrder` | `first-order.png` | Saffron | *"Turn your customer into your first order"* |
| `QuietDay` | `Quiet` | `quiet.png` | Warm | *"Quiet day — bring in new work"* |
| `PipelineSteady` | `Steady` | `steady.png` | Green | *"Workshop is steady"* |
| `BusyDay` | `FocusBusy` or `EarnNow` | `busy.png` | Red | *"X orders need attention"* |
| `ReadyForPickup` | `Pickup` (new) | `pickup.png` | Green | *"X ready for pickup"* |
| `NbaActive` (money-leaning) | `EarnNow` | `money.png` | Saffron | *"₦Xk ready to collect"* |

`FocusResolver` already produces six of these. **One small addition:** add a `Pickup` `FocusVariant` so `ReadyForPickup` doesn't reuse the generic `Steady` variant — this keeps illustration mapping clean and is a one-enum-case change in `FocusResolver`.

---

## Illustrations

### Files (delivered separately, dropped into `composeApp/src/commonMain/composeResources/drawable/dashboard/`)

| Slug | Use | Subject | Palette |
|---|---|---|---|
| `welcome.png` | BrandNew hero | Mannequin + scissors + sparkles | Saffron |
| `first-order.png` | FirstCustomer hero | Mannequin + measuring tape | Saffron |
| `quiet.png` | QuietDay hero | Calm mannequin + warm fabric | Warm cream |
| `steady.png` | PipelineSteady hero | Mannequin + green drape | Green |
| `busy.png` | BusyDay hero | Mannequin + red fabric, slight urgency | Red |
| `pickup.png` | ReadyForPickup hero | Shopping bag + folded garment | Green |
| `money.png` | EarnNow hero | Open wallet + Naira coins | Saffron |
| `empty-pipeline.png` | Pipeline empty state | Empty mannequin + clothing rack + plant | Cream |
| `empty-nba.png` | NBA empty state | Open notebook + pen + leaf | Cream |
| `empty-customers.png` | Brand-new flow accent | Two stylized people + thread spool | Saffron |

### Locked style prompt (paste into ChatGPT / DALL·E for every generation)

> Warm flat illustration, soft hand-drawn feel with light pencil texture, centered subject on transparent background. Palette only: saffron `#E8A800`, cream `#F4E4D4`, deep brown `#5C4A3D`, accent red `#D44C5C`, accent green `#2D9E6B`. Subtle drop shadow under subject. No text, no people's faces, no logos. 1024×1024 PNG with transparent background. Subject: **{insert subject from table above}**.

Generate one at a time, keep the prompt prefix identical, change only the subject. Reject and regenerate any image that drifts in palette or style.

### Placeholder strategy

Until the PNGs arrive, `IllustratedFocusCard` renders an inline SVG silhouette (the same one used in `preview/dashboard-v2-direction-options.html`) so Compose previews aren't blank. Replacing the SVG with the generated PNG is a one-line change per variant.

---

## Composable Inventory

### Reused
- `DashboardHeader` — modify in place: drop business-name line, drop standalone settings icon, add `BellButton` and `UserAvatar` composables.
- `WeeklyGoalsCard` — no change.
- `AccentedOrderRow` — used inside `TodayWorkCard` (one minor change: chip text accepts a `Today / Ready / Fitting today` variant).
- `LoadingDots` — no change.
- `StitchPadFab` — no change.

### New (in `feature/dashboard/presentation/components/`)
- `IllustratedFocusCard.kt` — composable + variant enum + illustration drawable resolver. Takes `FocusVariant`, `title`, `supporting`, `ctaLabel`, `onCtaClick`. Internal `Illustration` slot accepts a `@DrawableRes` ID, falling back to a placeholder until PNGs ship.
- `TodayWorkCard.kt` — composable wrapping `AccentedOrderRow` × ≤5, with header strip and "View all" navigation callback.
- `PipelineDualCard.kt` — single card with two `Column`s side by side. Takes `inProgress: List<OrderRow>`, `pending: List<OrderRow>`, totals, and two click callbacks. Uses `Modifier.weight(1f)` for equal columns; allows one column to be empty (renders header + "Empty" inline).
- `ReconnectChipStrip.kt` — `LazyRow` of chips. Takes `candidates: List<ReconnectCandidate>` and an `onMore` callback.
- `BellButton.kt` — small composable, 36dp tappable icon with conditional red dot; tap is a no-op for now.
- `UserAvatar.kt` — 36dp gradient circle with first letter; tap → settings screen (existing route).
- `EmptyIllustrationCard.kt` — generic illustrated empty state, used by NBA and Pipeline. Takes a drawable, title, supporting copy, optional CTA.

### Removed
- `TileGrid` and `Tile` composables that produce the four KPI tiles. (`feature/dashboard/presentation/components/Tile.kt` — delete or relocate to Reports if reused there.)
- `QuickStartTiles` composable — folded into the BrandNew variant of `IllustratedFocusCard`.
- `FocusTodayCard` — replaced by `IllustratedFocusCard` (separate file; deletion is independent).
- `ReconnectStrip` — replaced by `ReconnectChipStrip`.
- `WelcomeHero` — replaced by the BrandNew variant of `IllustratedFocusCard` (one card handles the entire BrandNew screen, no separate fullscreen hero needed).

---

## Data Flow

```
DashboardViewModel
  ├─ FocusResolver → uiState + focusVariant + hero copy
  ├─ BucketCalculator → overdue/dueToday/ready → TodayWorkCard
  │                    inProgress/pending → PipelineDualCard
  │                    outstanding → focusVariant=EarnNow signal
  ├─ NbaCalculator → top 5 actions → NextBestActionsRow
  ├─ WeeklyGoalCalculator → revenue progress → WeeklyGoalsCard
  └─ ReconnectCalculator → candidates → ReconnectChipStrip
```

No new domain logic. The only domain change is adding `Pickup` to the `FocusVariant` enum and a single branch in `FocusResolver` for the `ReadyForPickup` state.

---

## Empty States

Three illustrated empty states ship with V1. Each replaces the section's normal card — same height as the active card so the layout doesn't jump when data arrives.

| Section | When empty | Illustration | Copy | CTA |
|---|---|---|---|---|
| Pipeline | `pipelineInProgress.isEmpty() && pipelinePending.isEmpty()` | `empty-pipeline.png` | "No work in flight yet" / "When you create orders, they'll appear here." | "Create order →" |
| NBA | `nextBestActions.isEmpty()` | `empty-nba.png` | "No suggested moves" / "Show up when balances or deadlines need follow-up." | hidden |
| Today's Work | All three buckets empty | section hidden | — | — |
| Reconnect | `reconnectCandidates.isEmpty()` | strip hidden | — | — |

NB: when `Today's Work` and `Pipeline` are *both* empty, the focus card adapts to the QuietDay or BrandNew variant — the illustrated hero carries the "nothing to do" message, so the empty Pipeline card is the only one visible.

---

## Migration & Branch Strategy

The current branch `feature/dashboard-calculators-refactor` contains the calculator extraction (BucketCalculator, NbaCalculator, ReconnectCalculator, FocusResolver, WeeklyGoalCalculator) which is mergeable now. Mixing it with the V2 redesign would muddy the diff.

**Plan:**
1. **Open PR for `feature/dashboard-calculators-refactor`** with the calculator extraction + the two preview HTML files (`dashboard-v2-explorations.html`, `dashboard-v2-direction-options.html`) + this spec. Merge to main once CI green.
2. **Cut a new branch `feature/dashboard-illustrated-stack` from main** for the V2 implementation.
3. Implementation order on the new branch (each its own commit, optional sub-PRs):
   1. Add `Pickup` to `FocusVariant`; update `FocusResolver`.
   2. New composables (`IllustratedFocusCard`, `TodayWorkCard`, `PipelineDualCard`, `ReconnectChipStrip`, `BellButton`, `UserAvatar`, `EmptyIllustrationCard`) — with placeholder SVG for hero and material-icons placeholders for empty states.
   3. Rewire `DashboardScreen` to use the new sections; delete `TileGrid`, `QuickStartTiles`, `ReconnectStrip`, `FocusTodayCard`, `WelcomeHero` once unreferenced.
   4. Drop generated PNGs into `composeResources/drawable/dashboard/` and swap each placeholder slot.
   5. Manual smoke-test pass per state (`BrandNew`, `FirstCustomer`, `QuietDay`, `PipelineSteady`, `ReadyForPickup`, `BusyDay` with overdue, `BusyDay` clean) — Daniel runs the PR through these states.

---

## Verification

- **HTML preview** — already approved (`preview/dashboard-v2-direction-options.html`, Option A).
- **Unit tests** — new tests only for `FocusResolver` Pickup branch and any small calc edges that pop up. Calculators themselves already covered.
- **Compose previews** — each new composable ships with `@Preview` covering its main render and at least one edge case (empty / overflow).
- **State pass** — manual verification of all seven hero variants in light + dark on Android emulator and a physical Pixel; iOS later when the Compose Multiplatform side is wired up. Capture screenshots for the PR description.
- **Smoke test (per memory: every PR includes manual smoke steps)** —
  1. Sign in as a tailor with zero orders → verify BrandNew hero + illustrated empty pipeline.
  2. Create one customer → verify FirstCustomer hero, QuickStartTiles gone.
  3. Create one order due today → verify BusyDay hero + Today's Work shows the row.
  4. Mark order as Ready → verify ReadyForPickup hero appears.
  5. Toggle dark mode at every step.
- **Comparison gate** — open the live app and the running shipped main side-by-side on the same data. The V2 must reduce vertical scroll for the same content set in at least 6 of the 7 states.

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Generated illustrations drift in style across batches. | Medium | Locked style prompt; reject any that drift on the first review pass. Re-generate, don't tweak post-hoc. |
| Pipeline dual-column gets too narrow on small phones (< 360dp). | Low | At < 360dp, fall back to stacked rows (single column). Detect via `BoxWithConstraints` in `PipelineDualCard`. |
| Adding `Pickup` to `FocusVariant` breaks existing `when` expressions. | Low | Kotlin `when` exhaustiveness will surface every site at compile time. Update each in the same commit. |
| Bell button looks meaningless without a notifications screen. | Medium | Hide the bell entirely (`if (false)`) for V1. Surface only when notifications feature lands. |
| Avatar tap → settings is hidden discoverability. | Low | Add a subtle popover hint on first run (out of scope for V1, tracked in feedback). |

---

## Definition of Done

- All seven hero variants render with placeholder SVG and route copy correctly.
- All four new composables have previews.
- TileGrid, QuickStartTiles, FocusTodayCard, ReconnectStrip deleted (no dead code).
- Smoke test from above passes end-to-end.
- Light and dark mode parity verified on Android.
- PR description includes screenshots of each state in light + dark.
- Spec linked from PR description.

