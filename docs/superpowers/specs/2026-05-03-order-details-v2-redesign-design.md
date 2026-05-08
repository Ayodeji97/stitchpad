# Order Details V2 — Redesign — Design Spec

## Context

`OrderDetailScreen` (`feature/order/presentation/detail/OrderDetailScreen.kt`) is the screen a tailor opens after tapping any order in the app — from the dashboard, the orders list, search, or a notification. The current implementation is functional but has aged into a generic Material list of stacked sections (Customer · Items · Financial · Status · Deadline · Priority · Notes · Delete) and inverts the action hierarchy: a full-width red **Delete order** button anchors the bottom while frequent actions (Edit, Share) sit as 24dp icons in the top bar that are easy to miss. Notes only render if previously set via `OrderForm` — there is no way to add or edit notes inline. The status timeline shows four enum values (`PENDING / IN_PROGRESS / READY / DELIVERED`) which doesn't match how Nigerian tailors actually narrate work in progress (cutting → sewing → fitting).

Daniel is mid-redesign on the dashboard (Dashboard V2 illustrated stack — spec at `docs/superpowers/specs/2026-04-30-dashboard-v2-illustrated-stack-design.md`, branch `feature/dashboard-illustrated-stack`) and wants Order Details V2 fully spec'd so implementation can start the day the dashboard work merges. Five ChatGPT-generated mockups (states: In progress · Ready for pickup · Fitting today · Overdue · Delivered) form the visual reference. The redesign brings the screen into the same illustrated, GPT-style card language as the dashboard, fixes the action hierarchy, surfaces inline notes editing, and aligns the status timeline with how tailors actually think about production stages.

This spec covers presentation **and** the small data-layer additions needed to support payment history and sub-status. It is an order-of-magnitude larger change than the dashboard refactor — the model and repository touch points are real but minimal.

---

## Scope

**In scope**
- Replace `OrderDetailContent` section list with the eight sections defined below (hero card · customer · garment · payment · production timeline · measurements preview · notes · footer caption).
- New top-bar layout: Back · Share · Edit · `⋮` overflow (Duplicate · Archive · Delete).
- Inline notes editor (replaces the bottom Delete CTA on active states).
- Six-stage visual production timeline backed by a new optional `Order.subStatus` (`CUTTING / SEWING / FITTING`) — only meaningful when `status == IN_PROGRESS`.
- Full payment history: new `Payment` model (amount + method + type + timestamp), embedded as `Order.payments: List<Payment>`. `depositPaid` / `balanceRemaining` become derived values.
- New repository methods: `recordPayment`, `updateSubStatus`, `updateNotes` — partial writes, not full `updateOrder`.
- New ViewModel actions for inline notes save, payment method picker, sub-status picker, overflow menu, duplicate, archive, send-reminder.
- Light + dark mode parity using existing tokens.

**Out of scope**
- Garment-type illustrated icon as a no-photo fallback (could ship later as a polish pass).
- Customer-relationship card ("more from this customer · 3 other orders").
- Activity log beyond the existing `statusHistory`.
- Styles library integration (selecting a saved style for the garment).
- Changes to `OrderForm` — notes editing on the form stays as-is; this redesign only adds inline editing on the detail screen.
- Migrating the existing `depositPaid` field on Firestore — a one-time migration synthesises a single `DEPOSIT` payment from any non-zero `depositPaid` value at read time. No write migration required.
- New domain calculators — every value the screen renders is already derivable from `Order` after the model additions.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bottom CTA on active states | **Notes card replaces the delete button** | Delete is destructive and irreversible — wrong action to anchor the screen. Notes are high-value, missing today, and fill the same vertical space. |
| Bottom CTA on Delivered | **Keep `Archive order` button** (non-destructive) | Archive is a normal cleanup action; surfacing it bottom-anchored gives the Delivered state a clear "done" CTA. Mockup #5 already shows this. |
| Top bar | Back · Share · Edit · `⋮` overflow (Duplicate · Archive · Delete) | Option B from brainstorm: Edit is frequent enough to keep one tap away. Share matters most on Delivered but is harmless elsewhere. Destructive/rare actions go in overflow. |
| Delete affordance | Inside `⋮` overflow, isolated at bottom of menu, error-tinted text, with confirmation dialog | Discoverable but gated. Confirmation dialog already exists (`showDeleteDialog`); reuse it. |
| Production timeline | **6 visual nodes** (Pending · Cutting · Sewing · Fitting · Ready · Delivered) backed by 4 statuses + optional `subStatus` | Matches how tailors narrate work. Backend stays clean (`IN_PROGRESS` is the canonical state; `subStatus` is presentation-flavour). |
| Status update sheet | Bottom sheet showing the **next legal moves** from current state — e.g. from `IN_PROGRESS / Cutting` → Sewing · Fitting · Ready · Delivered | One picker handles both status and sub-status transitions. No separate sub-status picker. |
| Payment history | Build full `Payment` model + recording flow with method picker | Mockups show line items with method (Transfer / Cash) and date — visual placeholder would feel fake. Real model is ~50% extra work but unblocks Reports' future "payment by method" breakdowns too. |
| Hero image | Use existing `OrderItem.fabricPhotoUrl` with a small "Fabric" caption | No model change. If multiple items, show first item's fabric. If no fabric photo, show garment-type icon on cream tile (existing `GarmentType` enum, no new asset). |
| Customer card | Quick-action chips (WhatsApp · Call · Measurements) + name | Tailor's daily workflow is reach-out-then-fit. Chips put it one tap away. Phone number comes from `Customer` lookup, not denormalised on `Order`. |
| Measurements preview | Card showing 3 most-relevant fields (chest/waist/length or garment-specific) + "View all" chevron | Today there's a measurements *button* in customer row + a measurements *section* — duplicate. Drop the button, keep the section, make it preview-style. |
| Priority placement | Inside garment details card, as a coloured pill next to qty | Currently buried in its own section. Pill near qty matches tailor's mental grouping. |
| Hero card layout | Fabric thumbnail (left, 96dp, `radiusMd`) + name · customer · status pill · due date · balance + dual primary CTA row | Matches mockup composition; one card answers "what is this and what do I need to do?" |
| Overdue banner | Red soft-tinted strip *inside* the hero card (above CTA row), with `Send reminder` as the **primary** CTA and `Update status` as the secondary text button | Inverts the current hierarchy because reaching out resolves the issue; updating internal status doesn't. |
| Inline notes editor | Tappable card → expands to multi-line `OutlinedTextField` → save on blur or explicit `Save` button | Light, no separate screen; persists via partial `updateNotes` write. |

---

## Section Anatomy (top to bottom)

### 0. Top App Bar (modified)

**Before:** Back · `Share` icon · `Edit` pencil icon.

**After:** Back · `Share` icon · `Edit` pencil · `⋮` overflow.

```
┌──────────────────────────────────────────┐
│ ← Order details          🔗  ✏︎  ⋮       │
└──────────────────────────────────────────┘
```

Overflow menu (top-down):
- Duplicate order
- Archive order *(disabled until status = Delivered, hidden in archive view)*
- ─────
- Delete order *(error-tinted, opens existing confirmation dialog)*

Behaviour notes:
- Share opens the existing `ShareReceiptBottomSheet` (no change).
- Edit navigates to the existing `OrderForm` route in edit mode (no change).
- The standalone bottom Delete button is **removed** from active states; it lives only inside the overflow.
- On Delivered, the bottom area gets `Archive order` (see Section 8 below) and the overflow loses the duplicate Archive item.

### 1. Hero Card (new — replaces "Customer" + "Items" + "Status" + "Deadline" + "Priority" + "Financial header" sections at the top of the current screen)

```
┌──────────────────────────────────────────┐
│ ┌──────┐  Vintage Buba                   │
│ │      │  👤 Adewale Paul                │
│ │ FABR │  ●In progress · ⚑ High         │
│ │      │  📅 Due 30 Apr      Balance     │
│ └──────┘                     ₦60,000     │
│                                          │
│ [⚠ Customer waiting · 2 days overdue]    │  ← only on Overdue
│                                          │
│ ┌──────────────────┐  ┌────────────────┐│
│ │ Update status    │  │ Record payment ││
│ └──────────────────┘  └────────────────┘│
└──────────────────────────────────────────┘
```

- Fabric thumbnail: 96dp square, `radiusMd`, "Fabric" caption underneath in `labelSmall`. Falls back to `GarmentType` icon on cream tile if `fabricPhotoUrl == null`.
- Garment name: `headlineSmall`, single line with ellipsis.
- Customer row: small avatar icon (16dp) + customer name (`bodyMedium`).
- Status pill: existing `StatusBadge`, kept as-is.
- Priority pill: only rendered when `priority != NORMAL`. Same colour mapping the screen already uses (`URGENT → warning500`, `RUSH → error500`).
- Due/pickup label adapts: "Due 30 Apr" / "Pickup today" / "Was due 28 Apr" (overdue) / "Delivered 2 May" / "Fitting 2 PM".
- Balance: right-aligned, `headlineMedium`, monospace (JetBrains Mono per existing convention). Color: `error500` when overdue *and* balance > 0, else `onSurface`.
- Overdue banner: only rendered when `isOverdue == true` (existing condition). `error50` background, `error500` icon + text. Spans full hero width.
- Dual CTA row: see **State Matrix** below.

### 2. Customer Card

```
┌──────────────────────────────────────────┐
│ 👤 Customer            [WA] [Call] [Mes] │
│    Adewale Paul                          │
└──────────────────────────────────────────┘
```

- Section icon (28dp) + label + customer name.
- Three chip buttons (right-aligned): WhatsApp (green) · Call · Measurements.
  - WhatsApp tap: opens `wa.me/<phone>` deep link with prefilled message ("Hi Adewale, your Vintage Buba is …" — derived from status).
  - Call tap: standard `tel:` intent.
  - Measurements tap: scrolls to the Measurements Preview card (no separate sheet — measurements live inline on this screen).
- Tapping anywhere on the card body (outside chips) navigates to customer detail (existing behaviour, kept).
- Phone number resolution: ViewModel reads `customer.phone` via `CustomerRepository.observeCustomer(customerId)`; if customer has no phone, WhatsApp/Call chips render disabled with `onSurface3` tint and a tooltip "No phone number saved".

### 3. Garment Details Card

```
┌──────────────────────────────────────────┐
│ 👕 Garment details          [garment img]│
│                                          │
│ Vintage Buba                             │
│ ⚏ Ankara · Qty 1                         │
│                                          │
│ Priority [ HIGH ]                        │
└──────────────────────────────────────────┘
```

- Garment hero image (right, 96dp, same fabric photo as hero card). Repetition is intentional — when scrolled past the hero, the section needs its own visual anchor.
- Name: `bodyLarge` SemiBold.
- Fabric · Qty row: `bodyMedium`, `onSurface2`. "Fabric" comes from `OrderItem.description` parsed for fabric mention OR from a future fabric field; for V2 we render `description` as-is (matches mockup labels).
- Priority pill: only when not NORMAL.
- For multi-item orders: render one row per item (name + fabric + qty), no repeating priority/garment hero.

### 4. Payment Card

```
┌──────────────────────────────────────────┐
│ 💳 Payment                               │
│ Total      Paid       Balance due        │
│ ₦120,000   ₦40,000    ₦80,000           │
│ ──────────────────────────────────────── │
│ ⏱ Payment history                     ⌄ │
│   ✓ Deposit  ₦40,000  Transfer  15 Apr  │
│   ✓ Final    ₦40,000  Cash      28 Apr  │
└──────────────────────────────────────────┘
```

- Three financial values in a row: Total · Paid · Balance due (monospace, `headlineSmall`). Balance is `error500` if > 0, `success500` if 0.
- Payment history toggle: chevron-style row that expands/collapses the list. Default state:
  - Empty (no payments): hidden entirely. Section ends after the financial row.
  - 1 payment: collapsed by default, shows "1 payment recorded · expand →".
  - 2+ payments: expanded by default.
- Each payment row: status icon (✓ green) · payment type label · amount · method · date.
- Tapping the chevron toggles expansion; chevron rotates with `durationQuick` animation (existing token).
- Below the list (when expanded), small "Record payment" text button right-aligned — the same action as the dual-CTA on the hero card. Removed when balance == 0.

### 5. Production Timeline Card

```
┌──────────────────────────────────────────┐
│ 🪡 Production timeline                   │
│  ✓     ✓     ◉     ○     ○     ○         │
│ Pend  Cut  Sew  Fit  Rdy  Del            │
└──────────────────────────────────────────┘
```

- Six nodes, equal spacing, connected by a thin line.
- Node states: completed (✓ on saffron filled), current (filled saffron, larger, with sewing-machine icon), upcoming (outlined neutral).
- Tap on the card → opens "Update status" bottom sheet showing next legal moves.

**Visual node ↔ backend state mapping**

| Visual node | Backend `status` | Backend `subStatus` |
|---|---|---|
| Pending | `PENDING` | — |
| Cutting | `IN_PROGRESS` | `CUTTING` |
| Sewing | `IN_PROGRESS` | `SEWING` |
| Fitting | `IN_PROGRESS` | `FITTING` |
| Ready | `READY` | — |
| Delivered | `DELIVERED` | — |

When `status == IN_PROGRESS` and `subStatus == null` (legacy data), default the visual node to **Cutting** (first in-progress stage).

**Status sheet — next legal moves**

The picker shows transitions from the current state, ordered by typical progression:

| Current state | Sheet options |
|---|---|
| `PENDING` | Cutting · Sewing · Fitting · Ready · Delivered |
| `IN_PROGRESS / CUTTING` | Sewing · Fitting · Ready · Delivered · Back to Pending |
| `IN_PROGRESS / SEWING` | Fitting · Ready · Delivered · Back to Cutting |
| `IN_PROGRESS / FITTING` | Ready · Delivered · Back to Sewing |
| `READY` | Delivered · Back to Fitting |
| `DELIVERED` | (no sheet — status section is read-only) |

Selecting an option calls `OrderRepository.updateOrderStatus(...)` (existing, signature unchanged) plus a follow-up `updateSubStatus(...)` when needed. Status history (existing `statusHistory`) gets one entry per change; sub-status changes are not history-tracked in V2 (would clutter the timeline; future enhancement).

### 6. Measurements Preview Card

```
┌──────────────────────────────────────────┐
│ 📏 Measurements (preview)                │
│ ┌───────┐ ┌───────┐ ┌───────┐            │
│ │ Chest │ │ Waist │ │Length │            │
│ │ 42 in │ │ 34 in │ │ 38 in │            │
│ └───────┘ └───────┘ └───────┘            │
└──────────────────────────────────────────┘
```

- Three tile chips, monospace numerals.
- Field selection: take the first item's `garmentType` and look up `GarmentType.fieldLabels` for the canonical fields. Show the first three. If fewer than three exist, render only what's defined.
- Source: `OrderItem.measurementId` → `MeasurementRepository.observeMeasurement(...)`. If no measurement linked, render an empty-state version: "No measurements linked" + "Link measurements →" CTA navigating to the customer's measurements list.
- Card itself is tappable → opens full measurements sheet (existing behaviour, kept).

### 7. Notes Card (NEW affordance — replaces bottom Delete button on active states)

```
Collapsed (no notes yet):
┌──────────────────────────────────────────┐
│ 📝 Notes                                 │
│   Tap to add a note about this order →  │
└──────────────────────────────────────────┘

Collapsed (notes set):
┌──────────────────────────────────────────┐
│ 📝 Notes                              ✏︎ │
│   Customer wants longer sleeves; fabric  │
│   arrives 5 May. Charge mama's account.  │
└──────────────────────────────────────────┘

Expanded (editing):
┌──────────────────────────────────────────┐
│ 📝 Notes                                 │
│ ┌──────────────────────────────────────┐ │
│ │ Customer wants longer sleeves; fab…  │ │
│ │ rics arrive 5 May. Charge mama's a…  │ │
│ │ ccount.                              │ │
│ └──────────────────────────────────────┘ │
│                          [Cancel] [Save] │
└──────────────────────────────────────────┘
```

- Card tap (anywhere) toggles edit mode.
- Edit mode renders `OutlinedTextField` (multi-line, max 8 lines, scrolls beyond), seeded with current `notes` value.
- Save: calls new `OrderRepository.updateNotes(userId, orderId, notes)` (partial write — does not require `updateOrder`). On success, snackbar "Notes saved". On failure, snackbar with `DataError.toUiText()` and the editor stays open.
- Cancel: discards edit, restores prior value.
- Save-on-blur is **out** for V2 — explicit Save avoids accidental writes when the tailor scrolls or taps elsewhere mid-edit. Reconsider after watching real users.

### 8. Footer Caption (active states) / Archive Button (Delivered)

**Active states:**
```
                Order #ST-2451 · Created 12 Apr 2026
```
- `labelSmall`, `onSurface3`, centered, 24dp top padding.
- Order # = first 4-6 chars of `Order.id` (Firestore auto-id), prefixed `ST-` and uppercased.
- Created date: `Order.createdAt` formatted "DD MMM YYYY".

**Delivered state:**
```
┌──────────────────────────────────────────┐
│ 🗄  Archive order                        │
└──────────────────────────────────────────┘
                Order #ST-2451 · Delivered 2 May 2026
```
- Tinted neutral button (`surface2` background, `onSurface2` text), full width, `radiusMd`.
- Tap → `OnArchiveClick` → confirmation dialog → `OrderRepository.archiveOrder(...)` (new method).
- Caption shows "Delivered" date instead of "Created".

---

## State Matrix — Primary CTA per status

The hero card's dual CTA row is the single most important contextual element. It changes based on `status`, `subStatus`, and overdue/balance flags.

| Status | Sub | Overdue? | Primary (filled) | Secondary (text) |
|---|---|---|---|---|
| `PENDING` | — | no | Start work | Record payment |
| `PENDING` | — | yes | Send reminder | Start work |
| `IN_PROGRESS` | `CUTTING` / `SEWING` | no | Update status | Record payment |
| `IN_PROGRESS` | `FITTING` | no | Confirm fitting | Record payment |
| `IN_PROGRESS` | any | yes | Send reminder | Update status |
| `READY` | — | no | Mark delivered | Message customer |
| `READY` | — | yes | Send reminder | Mark delivered |
| `DELIVERED` | — | n/a | Share receipt | Duplicate order |

When `balanceRemaining == 0`, "Record payment" is replaced by the next-best secondary action: "Message customer" for active states, "Duplicate order" for `DELIVERED`.

---

## Model Additions

All additions live in `core/domain/model/Order.kt` (and corresponding DTO + mapper).

### `OrderSubStatus` enum (new)

```kotlin
enum class OrderSubStatus { CUTTING, SEWING, FITTING }
```

### `Order` — new fields

```kotlin
data class Order(
    // … existing fields unchanged …
    val subStatus: OrderSubStatus? = null,   // NEW — only meaningful when status == IN_PROGRESS
    val payments: List<Payment> = emptyList(), // NEW — replaces flat depositPaid as source of truth
    val archivedAt: Long? = null,            // NEW — set when archive action runs; filtered out of normal queries
)
```

`depositPaid` and `balanceRemaining` stay on the model **as computed properties** for backwards compatibility with existing dashboard calculators / Reports / pipeline logic:

```kotlin
val depositPaid: Double get() = payments.sumOf { it.amount }
val balanceRemaining: Double get() = totalPrice - depositPaid
```

### `Payment` data class (new)

```kotlin
data class Payment(
    val id: String,
    val amount: Double,
    val method: PaymentMethod,
    val type: PaymentType,
    val recordedAt: Long,
    val note: String? = null,
)
```

### `PaymentMethod` enum (new)

```kotlin
enum class PaymentMethod { CASH, TRANSFER, POS, OTHER }
```

### `PaymentType` enum (new)

```kotlin
enum class PaymentType { DEPOSIT, PROGRESS, FINAL }
```

### DTO additions (`core/data/dto/OrderDto.kt`)

```kotlin
data class OrderDto(
    // … existing …
    val subStatus: String? = null,
    val payments: List<PaymentDto> = emptyList(),
    val archivedAt: Long? = null,
    // depositPaid kept on the DTO for one release as a write-only field for legacy migration;
    // mapper synthesises a single Payment from it on read if payments is empty.
)

data class PaymentDto(
    val id: String,
    val amount: Double,
    val method: String,
    val type: String,
    val recordedAt: Long,
    val note: String? = null,
)
```

### Mapper migration (`core/data/mapper/OrderMapper.kt`)

`toOrder()`:
- If `payments.isEmpty() && depositPaid > 0`: synthesise one `Payment(id = "legacy-deposit", amount = depositPaid, method = OTHER, type = DEPOSIT, recordedAt = createdAt)` so existing orders display sensibly.
- Parse `subStatus` string back to enum, fall back to `null` on invalid value.

`toOrderDto()`:
- Serialise `payments` as `List<PaymentDto>`.
- Serialise `subStatus` as enum name string (or null).
- Drop `depositPaid` write (it's derived on read; keep the read field for legacy compatibility).

---

## Repository Additions

### `OrderRepository` interface (`core/domain/repository/OrderRepository.kt`)

```kotlin
suspend fun recordPayment(userId: String, orderId: String, payment: Payment): EmptyResult<DataError.Network>
suspend fun updateSubStatus(userId: String, orderId: String, subStatus: OrderSubStatus?): EmptyResult<DataError.Network>
suspend fun updateNotes(userId: String, orderId: String, notes: String?): EmptyResult<DataError.Network>
suspend fun archiveOrder(userId: String, orderId: String): EmptyResult<DataError.Network>
```

### `FirebaseOrderRepository` impl (`feature/order/data/FirebaseOrderRepository.kt`)

Each new method does a **partial Firestore update** (single field write or array union), not a full document overwrite. This avoids the race conditions that full `updateOrder` is prone to when the tailor edits notes while a status change is in flight.

- `recordPayment`: `firestore.collection("orders/$userId/orders").document(orderId).update("payments", FieldValue.arrayUnion(paymentDto))`.
- `updateSubStatus`: `update("subStatus", subStatus?.name)`.
- `updateNotes`: `update("notes", notes)`.
- `archiveOrder`: `update("archivedAt", System.currentTimeMillis())`.

`observeOrders` adds `.whereEqualTo("archivedAt", null)` so archived orders disappear from the dashboard / list without deletion.

---

## ViewModel & State Surface Changes

### `OrderDetailState` (`OrderDetailState.kt`) — new fields

```kotlin
data class OrderDetailState(
    // … existing fields …
    val customer: Customer? = null,             // NEW — for WhatsApp/Call phone resolution
    val measurement: Measurement? = null,       // NEW — for measurements preview
    val isEditingNotes: Boolean = false,        // NEW
    val notesDraft: String = "",                // NEW
    val isPaymentHistoryExpanded: Boolean = true, // NEW (default expanded when 2+ payments)
    val showOverflowMenu: Boolean = false,      // NEW
    val showStatusSheet: Boolean = false,       // NEW (replaces showStatusUpdateDialog for V2 sheet UI)
    val showArchiveDialog: Boolean = false,     // NEW
    val paymentMethodSelection: PaymentMethod = PaymentMethod.TRANSFER, // NEW
    val paymentTypeSelection: PaymentType = PaymentType.DEPOSIT,        // NEW
)
```

`showStatusUpdateDialog` is kept during the migration cycle and removed after the new sheet replaces it.

### `OrderDetailAction` (`OrderDetailAction.kt`) — new cases

```kotlin
// Notes
data object OnNotesEditClick : OrderDetailAction
data class OnNotesDraftChange(val text: String) : OrderDetailAction
data object OnNotesSaveClick : OrderDetailAction
data object OnNotesCancelClick : OrderDetailAction

// Payment
data class OnPaymentMethodSelect(val method: PaymentMethod) : OrderDetailAction
data class OnPaymentTypeSelect(val type: PaymentType) : OrderDetailAction
data object OnPaymentHistoryToggle : OrderDetailAction

// Status / sub-status
data class OnSelectStatusTransition(val toStatus: OrderStatus, val toSubStatus: OrderSubStatus?) : OrderDetailAction
data object OnDismissStatusSheet : OrderDetailAction

// Top-bar overflow
data object OnOverflowMenuToggle : OrderDetailAction
data object OnDuplicateClick : OrderDetailAction
data object OnArchiveClick : OrderDetailAction
data object OnConfirmArchive : OrderDetailAction
data object OnDismissArchiveDialog : OrderDetailAction

// Customer reach-out
data object OnWhatsAppClick : OrderDetailAction
data object OnCallClick : OrderDetailAction
data object OnSendReminderClick : OrderDetailAction

// Measurements
data object OnMeasurementsScrollClick : OrderDetailAction  // scroll to measurements card from customer chip
data object OnLinkMeasurementsClick : OrderDetailAction    // empty-state CTA
```

### `OrderDetailEvent` (`OrderDetailEvent.kt`) — new cases

```kotlin
data class LaunchWhatsApp(val phone: String, val message: String) : OrderDetailEvent
data class LaunchDialer(val phone: String) : OrderDetailEvent
data class NavigateToMeasurementsList(val customerId: String) : OrderDetailEvent
data class NavigateToCreateOrder(val seedFromOrderId: String) : OrderDetailEvent  // for Duplicate
data object OrderArchived : OrderDetailEvent
data object NotesSaved : OrderDetailEvent
data object PaymentRecorded : OrderDetailEvent  // (existing, no change)
```

---

## String Resources (additions to `composeApp/src/commonMain/composeResources/values/strings.xml`)

```xml
<!-- Order Detail V2 -->
<string name="order_detail_garment_section">Garment details</string>
<string name="order_detail_payment_section">Payment</string>
<string name="order_detail_production_timeline">Production timeline</string>
<string name="order_detail_measurements_preview">Measurements (preview)</string>
<string name="order_detail_notes_empty_hint">Tap to add a note about this order</string>
<string name="order_detail_notes_save">Save</string>
<string name="order_detail_notes_cancel">Cancel</string>
<string name="order_detail_notes_saved_toast">Notes saved</string>
<string name="order_detail_fabric_caption">Fabric</string>
<string name="order_detail_payment_history_label">Payment history</string>
<string name="order_detail_payment_history_count">%1$d payment recorded · expand</string>
<string name="order_detail_payment_history_count_plural">%1$d payments recorded</string>
<string name="order_detail_no_payments">No payments recorded yet</string>
<string name="order_detail_overflow_duplicate">Duplicate order</string>
<string name="order_detail_overflow_archive">Archive order</string>
<string name="order_detail_overflow_delete">Delete order</string>
<string name="order_detail_archive_confirm_title">Archive this order?</string>
<string name="order_detail_archive_confirm_body">It will be hidden from the dashboard and orders list. You can restore it from Reports.</string>
<string name="order_detail_archive_confirm_cta">Archive</string>
<string name="order_detail_send_reminder">Send reminder</string>
<string name="order_detail_message_customer">Message customer</string>
<string name="order_detail_mark_delivered">Mark delivered</string>
<string name="order_detail_confirm_fitting">Confirm fitting</string>
<string name="order_detail_start_work">Start work</string>
<string name="order_detail_share_receipt">Share receipt</string>
<string name="order_detail_duplicate_order">Duplicate order</string>
<string name="order_detail_due_label">Due %1$s</string>
<string name="order_detail_was_due_label">Was due %1$s</string>
<string name="order_detail_pickup_today">Pickup today</string>
<string name="order_detail_fitting_at_label">Fitting %1$s</string>
<string name="order_detail_delivered_label">Delivered %1$s</string>
<string name="order_detail_overdue_banner">Customer is waiting · %1$s overdue</string>
<string name="order_detail_no_phone">No phone number saved</string>
<string name="order_detail_link_measurements">Link measurements</string>
<string name="order_detail_no_measurements">No measurements linked</string>
<string name="order_detail_footer_caption">Order #%1$s · Created %2$s</string>
<string name="order_detail_footer_caption_delivered">Order #%1$s · Delivered %2$s</string>

<!-- Production stages -->
<string name="order_stage_pending">Pending</string>
<string name="order_stage_cutting">Cutting</string>
<string name="order_stage_sewing">Sewing</string>
<string name="order_stage_fitting">Fitting</string>
<string name="order_stage_ready">Ready</string>
<string name="order_stage_delivered">Delivered</string>

<!-- Payment dialog -->
<string name="payment_method_cash">Cash</string>
<string name="payment_method_transfer">Transfer</string>
<string name="payment_method_pos">POS</string>
<string name="payment_method_other">Other</string>
<string name="payment_type_deposit">Deposit</string>
<string name="payment_type_progress">Progress payment</string>
<string name="payment_type_final">Final payment</string>
```

---

## Composable Inventory

### Reused (no change)
- `StatusBadge` — used in hero card.
- `LoadingDots` — reused for fabric image loading.
- `BalanceOwedWarningDialog` — kept as-is for status transitions with outstanding balance.
- `RecordPaymentDialog` — extended (not replaced) to include method + type pickers.

### New (in `feature/order/presentation/detail/components/`)
- `OrderHeroCard.kt` — fabric thumbnail + title block + status/priority pills + due/balance + dual CTA row + conditional overdue banner.
- `OrderCustomerCard.kt` — customer label + name + WhatsApp/Call/Measurements chips. Stateless, takes `phone: String?` (null disables chips).
- `OrderGarmentDetailsCard.kt` — name + fabric/qty + priority pill + garment hero image (right-aligned).
- `OrderPaymentCard.kt` — financial row (Total/Paid/Balance) + collapsible payment history list.
- `OrderProductionTimeline.kt` — six-node timeline with current-state highlighting; clickable opens status sheet.
- `OrderMeasurementsPreviewCard.kt` — 3-tile measurements preview; empty-state variant.
- `OrderNotesCard.kt` — collapsed/expanded states, inline `OutlinedTextField`, Save/Cancel.
- `OrderFooterCaption.kt` — small grey order # + date caption.
- `OrderArchiveButton.kt` — full-width tinted neutral button (Delivered state only).
- `StatusTransitionSheet.kt` — `ModalBottomSheet` showing next legal status moves + optional sub-status.
- `RecordPaymentDialogV2.kt` — extends existing dialog with `PaymentMethod` and `PaymentType` selectors. (May overwrite original after migration is complete.)

### Removed / replaced
- `SectionHeader` (existing) — replaced by per-card icon-tile + label pattern (more visual, matches dashboard V2 cards).
- The bare `Button` "Delete order" at the bottom of `OrderDetailContent` (lines 985-999) — removed entirely; lives in overflow menu now.
- The standalone `Status section` Card with embedded "Update Status" button — replaced by hero CTA + production timeline card + status sheet.
- The standalone "Priority section" Text — folded into Garment Details card as a pill.
- The standalone "Deadline section" Text — folded into hero card as the due/pickup label.

---

## Empty States

| Section | When empty | Treatment |
|---|---|---|
| Payment history | `payments.isEmpty()` | Section ends after financial row; no expandable list. |
| Measurements preview | No `measurementId` linked | Card shows "No measurements linked" + "Link measurements →" CTA. |
| Notes | `notes == null \|\| notes.isBlank()` | Collapsed card with "Tap to add a note about this order →" hint, low-contrast text. |
| Overdue banner | `!isOverdue` | Banner not rendered. |
| Customer phone | `customer.phone == null` | WhatsApp/Call chips render disabled with tooltip. |

---

## Migration & Branch Strategy

This redesign is sequenced **after** the dashboard V2 work (`feature/dashboard-illustrated-stack`) merges to main. Two reasons: (1) dashboard V2 establishes the V2 card style that Order Details inherits — reviewing both diffs simultaneously would be noisy; (2) dashboard V2 is the bigger user-visible change and should ship first.

**Plan:**
1. **Wait for dashboard V2 merge.**
2. **Cut `feature/order-details-v2` from main.**
3. Implementation order on the new branch (each its own commit, sub-PRs welcome):
   1. **Model + DTO + mapper additions** — `OrderSubStatus`, `Payment`, `PaymentMethod`, `PaymentType`, `Order.subStatus`, `Order.payments`, `Order.archivedAt`. Includes the `depositPaid` legacy synthesis path. Tests for mapper round-trip including legacy migration.
   2. **Repository additions** — `recordPayment`, `updateSubStatus`, `updateNotes`, `archiveOrder` + the `archivedAt == null` filter on `observeOrders`. Tests use the existing fake repository pattern.
   3. **String resources** — add the strings listed above.
   4. **New composables** — built and previewed in isolation, one PR per composable cluster (hero/customer/garment, payment/timeline, measurements/notes, footer/archive, sheet).
   5. **Rewire `OrderDetailScreen`** — swap `OrderDetailContent` to use the new composables; delete dead code (bare bottom Delete button, standalone status/priority/deadline sections).
   6. **State + actions + events** — extend ViewModel to handle every new action.
   7. **Manual smoke test pass** per state (Daniel runs through the five reference states + edge cases).

---

## Verification

- **HTML preview** at `preview/order-details-v2.html` — five reference states (In progress · Ready for pickup · Fitting today · Overdue · Delivered) rendered side-by-side, light + dark mode toggle. Approved before implementation begins.
- **Unit tests:**
  - Mapper round-trip: `OrderDto ↔ Order` including `payments`, `subStatus`, `archivedAt`, and the legacy `depositPaid → synthetic Payment` synthesis.
  - `Payment.computedBalance` derivation correctness (zero, partial, overpaid, multi-payment).
  - Status transition picker — given each `(status, subStatus)` tuple, returns the correct list of next moves.
- **Compose previews** — each new composable ships with a `@Preview` covering its main render and at least one edge case (empty / overflow). Notes card preview covers collapsed/expanded/empty.
- **Manual smoke test (per CLAUDE.md and the QA workflow memory — every PR includes manual smoke steps):**
  1. Open an order in `PENDING` → tap "Start work" → expect status sheet opens with Cutting first → select Cutting → expect status pill, timeline node, and CTAs all update.
  2. Open an order in `IN_PROGRESS / FITTING` → expect "Confirm fitting" as primary CTA → tap → expect transition to Ready.
  3. Open an order with `balanceRemaining > 0` → tap "Record payment" → select Cash + Final → expect new payment in history and balance recalculates to zero.
  4. Open an overdue `IN_PROGRESS` order → expect red banner inside hero card and "Send reminder" as primary CTA.
  5. Open a `DELIVERED` order → expect "Share receipt" + "Duplicate order" CTAs and `Archive order` button at the bottom (no Delete) → tap Archive → confirm dialog → expect order leaves the orders list.
  6. Open any order → top-right `⋮` → expect Duplicate / Archive (or disabled if not Delivered) / Delete → tap Delete → confirmation dialog → confirm → expect navigation back.
  7. Open any order → tap notes card → type → Save → expect toast and persisted text on reload.
  8. Toggle dark mode at every step.
- **Comparison gate** — open the V2 screen and current main side-by-side on the same order. V2 must render the same primary information above the fold (status, balance, due date, primary CTA) on a Pixel 6.
- **Final commit message:** `docs(order-details): add V2 redesign spec + preview mockups` (matches dashboard V2 commit pattern).

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Existing orders' `depositPaid` displays inconsistently with new payment model. | Medium | Mapper synthesises a `legacy-deposit` `Payment` on read; UI labels it "Deposit · Method unknown · (date created)". One-time visual artefact, no data loss. |
| Adding fields to `Order` breaks dashboard / Reports calculators. | Low | All new fields default to safe values (`null`, `emptyList()`, `0L`). Existing calculators don't reference them. Compile-time check via Kotlin's exhaustiveness. |
| `subStatus` only meaningful when `status == IN_PROGRESS` — invalid combinations possible. | Low | Always set together via `updateOrderStatus(...)` + `updateSubStatus(...)` call pair. Guard in mapper: if `status != IN_PROGRESS`, force `subStatus = null` on read. |
| WhatsApp deep-link silently fails on iOS if WhatsApp not installed. | Medium | Detect via `UIApplication.canOpenURL(...)` (existing iOS pattern in app); fall back to copying number to clipboard with snackbar "WhatsApp not installed — number copied". |
| Inline notes editor loses focus when keyboard appears. | Low | Use `bringIntoViewRequester` (Compose) so the field scrolls into view above the IME. Test on a small phone. |
| `archivedAt` filter breaks customer-detail "all orders" view. | Low | Customer detail screen uses `observeOrdersByCustomer(...)` which we keep unfiltered (shows archived). Only dashboard/orders-list filter. |
| Overflow menu Archive item duplicated with bottom Archive button on Delivered. | Low | Hide the overflow Archive item when bottom button is present (Delivered state only). |
| Six-node timeline crowds on small phones. | Low | Use `Modifier.weight(1f)` so nodes distribute evenly; fallback to scrolling row at < 360dp screen width. |

---

## Definition of Done

- All eight sections render correctly in light + dark on Android emulator and a physical Pixel.
- All new composables have `@Preview`s covering main + at least one edge case.
- Smoke test from above passes end-to-end on a real device.
- The bare bottom Delete button in `OrderDetailContent` is removed; Delete lives only in `⋮` overflow.
- Notes can be added and edited inline with persistence verified after app restart.
- Payment history shows all payments with correct method/date and survives restart.
- Status transitions through the new sheet work end-to-end including sub-status changes.
- Archived orders disappear from dashboard and orders list but remain visible on customer detail.
- Spec linked from PR description; PR includes screenshots of all five reference states in light + dark.
- iOS compile passes (per the kotlinx.datetime epoch-days memory — run iOS compile before declaring done).
