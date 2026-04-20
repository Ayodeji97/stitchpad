# Orders Screen Redesign — Design Spec

## Context

The Orders screen (`feature/order/presentation/list/OrderListScreen.kt`) shipped in Sprint 3 with a flat list of orders filtered by status chips. Real-use feedback: a tailor with 20-50 active orders can't answer their daily question — "what do I work on today?" — without reading every row. The current layout treats a pending order due tomorrow identically to one due in three weeks.

This spec covers a presentation-layer redesign only. No domain, repository, or Firestore changes.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Layout model | Triage sections grouped by urgency | Answers "what's next?" before the user taps anything. |
| Within-section sort | Deadline ascending, nulls last | Most urgent items float to the top of each group. |
| Section headers | Sticky (`LazyColumn.stickyHeader`) | Context preserved while scrolling a long list. |
| Empty sections | Hidden entirely | "Overdue (0)" is misleading clutter. |
| Sections in filtered view | Dropped when a status chip is active | Sectioning only adds value in "All" view; redundant when narrowed. |
| DELIVERED orders | Visible only when Delivered chip is selected | Completed work is noise in the default view. |
| Overdue filter chip | Removed | The Overdue section header already surfaces overdue orders. |
| Row identity | Customer initial avatar with deterministic color | Visual identity per customer; spot repeat customers at a glance. |
| Payment status | Shown under the price on every row | Daily question: "did they pay the deposit?" answered without tapping. |
| Search / sort | Out of scope | Triage sections are the implicit sort. Search is a separate future spec. |

---

## Triage Groups

Five groups, computed from `List<Order>` + current timestamp. Display order is fixed.

| Group | Membership rule | Header color |
|-------|-----------------|--------------|
| **Overdue** | `deadline != null && deadline < now && status != DELIVERED` | error500 |
| **Due This Week** | `status == PENDING && deadline != null && daysUntilDeadline in 0..7` (not overdue) | warning500 |
| **In Progress** | `status == IN_PROGRESS` (regardless of deadline, unless already Overdue) | info500 |
| **Ready for Pickup** | `status == READY` (regardless of deadline) | success500 |
| **Pending** | `status == PENDING` and not in Overdue / Due This Week | neutral500 |

**Classification is first-match-wins in this order:** (1) DELIVERED → hidden, (2) READY → Ready for Pickup, (3) deadline past → Overdue, (4) IN_PROGRESS → In Progress, (5) PENDING + deadline ≤ 7 days → Due This Week, (6) otherwise → Pending.

**Display order of sections** when populated: Overdue → Due This Week → In Progress → Ready for Pickup → Pending.

DELIVERED orders are excluded from all groups in the default ("All") view. They appear only when the user taps the Delivered filter chip.

Within each group: sort by `deadline` ascending; orders with `deadline == null` sort last. Stable secondary sort by `createdAt` descending.

---

## Row Anatomy

Three-column `Row`: `[Avatar 36dp] [Body 1f] [Right auto]`.

### Body column

- **Name line** — customer name (`bodyLarge`, SemiBold) + inline `PriorityBadge` when `priority != NORMAL`
- **Garment meta** — `"{count} {garmentDisplayName(firstItem)}"` in lowercase, singular/plural aware. Example: `"1 corset"`, `"3 dresses"`. Use existing `GarmentDisplayName` extension; add lowercase transform at format time, not at the resource level (resources stay capitalized for other surfaces).
- **Deadline line** — plain-English formatted, color-coded (see `formatDeadline` below)

### Right column

- **Price** — `"\u20A6{formatPrice(totalPrice)}"`, right-aligned, `bodyMedium`, SemiBold
- **Payment status** — single line below price (see `formatPaymentStatus` below)

### Removed from the row

- `OrderStatusBadge` — the section header carries the status now
- Free-standing "Overdue" label — replaced by the deadline line and the Overdue section

---

## New Composables

All added as private composables inside `OrderListScreen.kt`. The file grows but stays below ~800 lines; splitting into separate files isn't worth the indirection at this scale.

### `CustomerAvatar(name: String, customerId: String)`

- 36dp circle, first character of `name.trim()` uppercased
- Background + text color picked from `DesignTokens.avatarColors[customerId.hashCode().mod(DesignTokens.avatarColors.size)]` (`Int.mod` returns non-negative, so negative hashes stay in range)
- The palette **already exists** in `DesignTokens.kt` as `List<AvatarColor>` with 6 entries (Saffron, Blue, Green, Orange, Purple, Pink), each with `lightBg` / `lightText` / `darkBg` / `darkText` variants. Avatar uses `lightBg` + `lightText` in light mode and `darkBg` + `darkText` in dark mode — resolved via `isSystemInDarkTheme()` at the call site. No new tokens need to be added.
- Text: white, SemiBold, `bodyMedium`

### `DeadlineLine(deadline: Long?, now: Long, status: OrderStatus)`

Pure formatter returning `(text: String, color: Color)`. Emits a `Text` with the result.

| Condition | Text | Color |
|-----------|------|-------|
| `status == READY` | "Pickup ready" | success500 |
| `deadline == null` | "No deadline" | onSurfaceVariant |
| `deadline < now && status != DELIVERED` | "{N} days late" (singular: "1 day late") | error500 |
| `daysUntil == 0` | "Due today" | warning500 |
| `daysUntil == 1` | "Due tomorrow" | warning500 |
| `daysUntil in 2..3` | "Due in {N} days" | warning500 |
| `daysUntil > 3` | "Due in {N} days" | onSurfaceVariant |

`daysUntil` computed as calendar-day difference, not raw millis / 86_400_000, to avoid "due tomorrow" flipping at 3am.

### `PaymentStatus(depositPaid: Double, totalPrice: Double)`

| Condition | Text | Color |
|-----------|------|-------|
| `depositPaid >= totalPrice` | "Paid" | success500 |
| `depositPaid > 0` | "₦{abbreviated} paid" (e.g. "₦10k paid", "₦500 paid") | warning500 |
| else | "Unpaid" | error500 |

Abbreviation rule: `>= 1000` → "{k}k" with no decimals (₦10k, ₦300k). `< 1000` → exact ("₦500").

### `TriageSectionHeader(label: String, count: Int, color: Color)`

- Sticky header inside `LazyColumn`
- Typography: `labelSmall`, 700 weight, 0.08em letter-spacing, UPPERCASE
- Layout: label left, count right, both in `color`
- Padding: `space4` horizontal, `space3` top, `space1` bottom
- Background: `surface` (so it reads clearly when sticking)

---

## Modified Code

### `OrderListScreen.kt`

- Replace the `items(...)` block with an outer iteration over non-empty triage groups:
  ```kotlin
  triageGroups(state.orders, now).forEach { (group, orders) ->
      stickyHeader(key = "header-${group.name}") {
          TriageSectionHeader(group.label, orders.size, group.color)
      }
      items(orders, key = { it.id }) { order ->
          SwipeableOrderItem(order, onClick = ..., onDelete = ...)
      }
  }
  ```
- `OrderListItem` — restructure to 3-column grid with `CustomerAvatar`, updated body, right stack
- When a status chip is active (`statusFilter != null`), skip the `triageGroups` call and render a flat `items(...)` list sorted by deadline ascending.

### `OrderListState.kt`

- Remove `showOverdueOnly` (field becomes dead with the Overdue chip removal)

### `OrderListAction.kt`

- Remove `OnToggleOverdueFilter`

### `OrderListViewModel.kt`

- Remove handling for the removed action and state field
- No new logic added — grouping happens at render time in the composable (pure function of state + clock)

### `OrderStatusFilterChips`

- Remove the Overdue chip (All / Pending / In Progress / Ready / Delivered remain)

---

## Non-Goals

- Search — deferred to a separate spec
- Custom sort — triage sections are the sort
- Grouping customization — order of sections is fixed
- Section collapse/expand — not needed at current list sizes
- Virtualization beyond what `LazyColumn` already provides
- Date-based headers ("Today", "This Week" as wall-clock dates) — urgency-based grouping is more useful than calendar grouping for this domain

---

## Edge Cases

- **Zero orders** — `OrderEmptyState` renders, unchanged
- **All orders in one group** — that single section header still renders (consistency)
- **Empty section** — header + items are both skipped
- **Order exactly at `now`** — NOT overdue. The comparison is strict (`deadline < now`), so an order becomes overdue only once `now` strictly exceeds the deadline (a sub-millisecond edge case in practice — once the clock advances past the deadline by any amount, the next classification pass moves it to Overdue).
- **DELIVERED order with past deadline** — not shown in Overdue; appears only under the Delivered chip filter
- **Overpayment (`depositPaid > totalPrice`)** — displayed as "Paid"
- **`totalPrice == 0`** — displayed as "Paid" (degenerate; no divide-by-zero concern)
- **Customer with empty/whitespace-only name** — avatar shows "?" with `onSurfaceVariant` background
- **Sticky header overlap with snackbar / FAB** — FAB is already constrained by scaffold; snackbar appears above FAB. No conflict.

---

## Testing

### Unit tests (`commonTest`)

**`TriageGroupingTest`**
- empty list → empty map
- only overdue orders → single group
- deadline exactly at `now` → Overdue
- DELIVERED order with past deadline → not grouped into Overdue
- READY status with past deadline → Ready for Pickup (not Overdue)
- `deadline == null` + `status == PENDING` → Pending
- within-group ordering: deadline ascending, nulls last
- multi-group: realistic mix covering all 5 groups

**`FormatDeadlineTest`**
- null + PENDING → "No deadline", onSurfaceVariant
- past + not DELIVERED → "N days late" / "1 day late" with error color
- today → "Due today", warning
- tomorrow → "Due tomorrow", warning
- 3 days out → warning
- 4+ days out → onSurfaceVariant
- READY status short-circuits deadline formatting → "Pickup ready"
- crosses-day-boundary math: 11pm today vs 1am tomorrow both return "Due tomorrow"

**`FormatPaymentStatusTest`**
- depositPaid == totalPrice → "Paid"
- depositPaid > totalPrice → "Paid"
- depositPaid > 0 && < totalPrice → "₦Xk paid" with correct abbreviation
- depositPaid == 0 → "Unpaid"
- totalPrice == 0 → "Paid"
- abbreviation boundary: 999 → "₦999 paid", 1000 → "₦1k paid", 9999 → "₦9k paid", 10000 → "₦10k paid"

### Compose previews (`OrderListScreen.kt`)

Keep existing previews and add:
- `OrderListScreenFullPreview` — all 5 sections populated with realistic mix
- `OrderListScreenFilteredPreview` — status chip selected, flat list shown
- `OrderListScreenDarkPreview` — dark theme variant

### What is NOT tested

- `OrderListViewModel` — no new logic
- Repository / Firestore — no change
- Swipe-to-delete — unchanged behavior

---

## Build Sequence

1. Add grouping + formatter pure functions with unit tests (TDD — tests first)
2. Add `CustomerAvatar`, `DeadlineLine`, `PaymentStatus`, `TriageSectionHeader` composables with previews
3. Restructure `OrderListItem` to use the new composables
4. Wire grouping into `OrderListScreen` with sticky headers
5. Remove `showOverdueOnly` from state, action, ViewModel, and the chip row
6. Add Delivered filter chip
7. Manual QA: smoke-test on Android and iOS against the triage mockup screens

---

## Smoke Test Steps (for PR)

1. Open Orders tab with zero orders → empty state renders
2. Create orders covering: overdue, due today, due in 2 days, due in 10 days, no deadline, READY, IN_PROGRESS, DELIVERED
3. On "All" view — all 5 section headers visible in correct order, DELIVERED order not shown
4. Scroll — section headers stick as expected
5. Tap "Pending" chip — flat list, no section headers, sorted by deadline
6. Tap "Delivered" chip — delivered orders appear
7. Repeat customer (same `customerId`, two orders) → avatar color matches on both rows
8. Swipe to delete on a row → confirmation dialog, delete works
9. Dark mode toggle → all colors remain legible
10. Pull up to find the FAB — still accessible, nav bar still visible
