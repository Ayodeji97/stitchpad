# QA Cleanup Batch — PTSP-1 / PTSP-2 / PTSP-15

**Date:** 2026-05-25
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** PTSP-1, PTSP-2, PTSP-15 (PM-authored, post-freemium-V1.0 QA pass)

---

## 1. Context

Freemium V1.0 (PR #49) shipped on 2026-05-19. PM ran a QA pass and filed
three tickets covering UX gaps the freemium work didn't touch:

- **PTSP-1** — no quick action for creating a customer from the dashboard
  (the FAB only opens the Order form, so a tailor with no customers yet
  has no path from Dashboard → New Customer).
- **PTSP-2** — the Customer List screen carries a delivery-preference
  filter row ("All / Pickup / Delivery") that adds noise. Per PM/Daniel:
  delivery preference belongs on an **order**, not on a customer.
- **PTSP-15** — tapping a customer row only opens the detail page; PM
  wants a quick chooser (Edit / New Measurement / New Order / Delete)
  matching the pattern competitors use (Tailora).

This spec covers all three under one batch but ships as **three
independent PRs off `main`**.

## 2. Branch & PR shape

Three separate branches off `main`, one PR each, in this order:

| Order | Branch                                       | Why this order                                                  |
|-------|----------------------------------------------|-----------------------------------------------------------------|
| 1     | `feature/ptsp-2-remove-delivery-filter`      | Smallest diff; validates QA-batch CI rhythm first.              |
| 2     | `feature/ptsp-1-dashboard-quick-actions`     | Independent surface (dashboard).                                |
| 3     | `feature/ptsp-15-customer-row-actions`       | Independent surface (customer list).                            |

Each gets Cursor + `codex review` per the saved review-rotation rule. No
rebase conflicts expected — different files.

## 3. PTSP-2 — Remove delivery filter from Customer List

### 3.1 Scope

Remove the **filter chips on the Customer List screen only**. Form
selector, `Customer.deliveryPreference` field, and Firestore field stay.

> **Future direction (out of scope for this PR):** per Daniel's
> principle "delivery should be attached to an order, not a customer,"
> the form field + domain field can be removed in a follow-up once
> downstream code (mapper, DTO, list filtering memory) is audited.

### 3.2 Files touched

| File                                                                                               | Change                                                                                                  |
|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `feature/customer/presentation/list/CustomerListScreen.kt`                                         | Delete `DeliveryFilterChips` composable + its call site. Remove now-unused `customer_filter_all` / `delivery_*` imports. |
| `feature/customer/presentation/list/CustomerListState.kt`                                          | Delete `deliveryFilter: DeliveryPreference?` field.                                                     |
| `feature/customer/presentation/list/CustomerListAction.kt`                                         | Delete `OnDeliveryFilterChange(filter: DeliveryPreference?)` action.                                    |
| `feature/customer/presentation/list/CustomerListViewModel.kt`                                      | Delete the `OnDeliveryFilterChange` handler. Drop the `deliveryFilter` parameter from `filterCustomers(...)`. Search-only filtering remains. |
| `composeApp/src/commonMain/composeResources/values*/strings.xml` *(only if unused after this PR)*  | Remove `customer_filter_all` only if no other surface references it; per-locale variants too.            |

### 3.3 Behavior after

Customer List filtering is **search-only**. Locked-customer section, swap
sheet, swipe-to-delete, and the customer count chip are untouched.

### 3.4 Tests

- `CustomerListViewModelTest` — drop any `deliveryFilter`-specific
  cases; keep search filtering tests.

### 3.5 QA smoke

Open Customers tab → confirm no chip row above the list. Search still
narrows results. Locked customers section still renders. Swipe-to-delete
still works.

## 4. PTSP-1 — Dashboard quick actions (New Customer + New Order)

### 4.1 Two redundant entry points

Per Daniel: surface both `New Customer` and `New Order` via **inline
row** (always-visible discovery) **and** **speed-dial FAB** (thumb-reach
when scrolled). The redundancy is intentional.

### 4.2 Inline "Quick actions" row

New section composable, rendered **immediately after the
`IllustratedFocusCard`** (or after the header if no focus card is
present) and **before** `SetupChecklistCard` / pipeline sections.
Rendered on every dashboard state where the FAB is already shown:

- `FirstCustomer` / `QuietDay` / `PipelineSteady` / `NbaActive` /
  `BusyDay` / `ReadyForPickup`.
- Hidden on `Loading` and `BrandNew` (the existing `OnboardingStepsCard`
  already covers brand-new, including the "Add customer" step).

Placement keeps the focus card as the hero. Quick actions sit below the
hero but above the pipeline, so they're always visible without scrolling
on the FirstCustomer / QuietDay states (the cases where they matter
most).

Two equal-width tappable Surfaces, rounded `radiusMd`:

```
┌─────────────────────────────────────┐
│ Quick actions                       │
│  ┌──────────────┐  ┌──────────────┐ │
│  │  👤  Customer │  │  📋  Order   │ │
│  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────┘
```

Fires `DashboardAction.OnNewCustomerClick` / `OnNewOrderClick`. **Both
actions already exist** (`DashboardAction.kt` lines 16, 21) and are wired
in `DashboardViewModel` (lines 134, 142). **No new actions, no nav
rewire.**

### 4.3 Speed-dial FAB

Replace the single FAB with a Material 3 speed-dial:

- Tap FAB → 50 ms rotation; two mini-FABs fan out with labels.
- Backdrop scrim dims dashboard content; tap-outside-to-dismiss.
- Expansion state lives in `DashboardScreen` (Compose-internal UI state
  — fine per `CLAUDE.md` "All state in ViewModel, never in remember…
  except Compose-internal").

New reusable component: `ui/components/StitchPadSpeedDialFab.kt`. Wraps
existing `StitchPadFab` so the `RoundedCornerShape(16.dp)` from
`feedback_fab_shape` is preserved on the main FAB and each mini.

### 4.4 Files touched

| File                                                            | Change                                                                                      |
|-----------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `feature/dashboard/presentation/DashboardScreen.kt`             | Insert `QuickActionsRow` into `DashboardContent`. Swap single `StitchPadFab` for `StitchPadSpeedDialFab`. |
| `feature/dashboard/presentation/components/QuickActionsRow.kt`  | **New.** Two-chip row composable.                                                            |
| `ui/components/StitchPadSpeedDialFab.kt`                        | **New.** Reusable speed-dial wrapper.                                                        |
| `composeResources/values*/strings.xml`                          | New keys: `dashboard_quick_actions_title`, `dashboard_quick_action_customer`, `dashboard_quick_action_order`, `dashboard_fab_new_customer_cd`, `dashboard_fab_new_order_cd`, `dashboard_fab_close_cd`. |

### 4.5 Tests

No new ViewModel tests required — `OnNewCustomerClick` and
`OnNewOrderClick` paths are already covered in
`DashboardViewModelTest`. Preview-only changes for the new composables.

### 4.6 Considerations

- **Locked state:** speed-dial behaves identically for users with locked
  customers. Routing to New Customer / New Order is unchanged.
- **Accessibility:** each mini-FAB gets a unique `contentDescription`;
  the inline row uses each chip's label as the semantic label.
- **No new analytics events** unless requested — both clicks feed the
  existing nav events.

### 4.7 QA smoke

1. Open dashboard with at least one customer → see Quick actions row
   above the pipeline section.
2. Scroll down → tap FAB → mini-FABs fan out with labels.
3. Tap mini-FAB "+ Customer" → Customer form opens.
4. Tap mini-FAB "+ Order" → Order form opens.
5. Tap scrim → mini-FABs retract.
6. Verify BrandNew state still has no FAB and no Quick actions row.

## 5. PTSP-15 — Customer row actions sheet

### 5.1 Row interaction (Customer List screen)

```
┌────────────────────────────────────────────┐
│ [avatar]  Amina Bello                 ⋮   │  ← ⋮ opens sheet
│           +234 801 234 5678                │  ← rest of row → detail
└────────────────────────────────────────────┘
```

- Tap row body → `CustomerDetailScreen` (unchanged).
- Tap **⋮** icon (44 dp min tap target) → opens `CustomerActionsSheet`.
- ⋮ tap does **not** propagate to row click.
- Existing swipe-to-delete stays (redundant with sheet's Delete, but
  faster for power users).

### 5.2 `CustomerActionsSheet` (new composable)

`feature/customer/presentation/list/components/CustomerActionsSheet.kt`.
Uses `ModalBottomSheet`; per `feedback_ios_modal_bottom_sheet_timing`,
all dismiss-then-navigate flows apply a `~450 ms` delay to avoid the
UIKit `present()` race on iOS.

```
┌──────────────────────────────────┐
│  [○]  Amina Bello           ›    │  ← whole header tappable → detail
│       +234 801 234 5678          │
│  ──────────────────────────────  │
│  ✏️   Edit                       │
│  📏   New measurement            │
│  📋   New order                  │
│  🗑️   Delete                     │  ← error-tinted
└──────────────────────────────────┘
```

- Header = `CustomerAvatar` + name + phone + trailing `KeyboardArrowRight`
  chevron. Entire header is one tappable Row; chevron is the
  affordance.
- Four `ListItem`-style action rows with leading icon + label.
- Delete row uses `MaterialTheme.colorScheme.error` tint per the
  "Dialog for destructive" convention in
  `feedback_notification_patterns` — visual warning, then the existing
  delete-confirm dialog handles the actual destructive step.

### 5.3 Action routing

| Sheet item       | Action                                                            | Destination                                                       |
|------------------|-------------------------------------------------------------------|-------------------------------------------------------------------|
| Header (View)    | `CustomerListAction.OnViewCustomerFromSheet(customerId)` *(new)*  | `CustomerDetailRoute(customerId)`                                  |
| Edit             | `CustomerListAction.OnEditCustomerFromRow(customerId)` *(new)*    | `CustomerFormRoute(customerId)` (edit mode)                        |
| New measurement  | `CustomerListAction.OnAddMeasurementFromRow(customerId)` *(new)*  | `MeasurementFormRoute(customerId)` — existing route                |
| New order        | `CustomerListAction.OnNewOrderFromRow(customerId)` *(new)*        | Existing Order form route — customer pre-selected                  |
| Delete           | Reuse existing `OnDeleteCustomerClick(customer)`                  | Existing delete-confirm dialog (incl. active-order block)          |

Routing **straight to the form** from the sheet (one tap) rather than
through detail (two taps) is the point of the sheet — that's the user
intent the PM expressed.

> **Verify during implementation plan:** confirm `MeasurementFormRoute`,
> `CustomerFormRoute(customerId)`, and the Order form route accept a
> pre-selected `customerId` argument today. If any do not, the
> implementation plan must add the param.

### 5.4 Locked customers

Locked rows use a separate composable (`LockedCustomerRow`) and do not
get the ⋮ icon. The Swap sheet remains the only affordance for locked
customers, matching the V1.0 read-only locked-customer rule in
`project_freemium_v1`.

### 5.5 Files touched

| File                                                                                  | Change                                                                                                                                                                          |
|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `feature/customer/presentation/list/components/CustomerActionsSheet.kt`               | **New.** The sheet composable + tappable header + 4 action rows.                                                                                                                |
| `feature/customer/presentation/list/CustomerListScreen.kt`                            | Add ⋮ icon trailing affordance to active `CustomerListItem`. Render `CustomerActionsSheet` when `state.actionsSheetForId != null`.                                              |
| `feature/customer/presentation/list/CustomerListState.kt`                             | Add `actionsSheetForId: String? = null`.                                                                                                                                        |
| `feature/customer/presentation/list/CustomerListAction.kt`                            | Add: `OnOverflowClick(customer)`, `DismissActionsSheet`, `OnViewCustomerFromSheet(customerId)`, `OnEditCustomerFromRow(customerId)`, `OnAddMeasurementFromRow(customerId)`, `OnNewOrderFromRow(customerId)`. |
| `feature/customer/presentation/list/CustomerListEvent.kt`                             | Add: `NavigateToCustomerDetail(id)`, `NavigateToEditCustomer(id)`, `NavigateToAddMeasurement(id)`, `NavigateToOrderForm(id)`.                                                   |
| `feature/customer/presentation/list/CustomerListViewModel.kt`                         | Handle the new actions; for navigation actions, apply ~450 ms post-dismiss delay before emitting the nav event.                                                                  |
| `navigation/` (route wiring)                                                          | Wire the 4 nav events from `CustomerList` → existing form / detail routes.                                                                                                       |
| `composeResources/values*/strings.xml`                                                | New keys: `customer_actions_edit`, `customer_actions_new_measurement`, `customer_actions_new_order`, `customer_actions_delete`, `cd_customer_overflow`, `cd_customer_actions_sheet`, `cd_customer_actions_view`. Plus per-locale variants. |
| `CustomerListViewModelTest`                                                           | Tests for each new action → expected event (with the `~450 ms` delay covered by `UnconfinedTestDispatcher` per `android-testing`).                                              |

### 5.6 Detail screen — explicitly unchanged

Per Daniel's direction:

- Top-bar **Edit** button stays (now redundant with the sheet's Edit, but
  reachable from different surfaces).
- Measurement **swipe-to-delete** stays.
- Locked-customer **Unlock with Pro** CTA stays.
- Style gallery section stays.

### 5.7 Considerations

- **iOS timing:** `~450 ms` dismiss-delay applied to **all 4 nav events**
  (View, Edit, New Measurement, New Order). Delete uses the existing
  dialog (no sheet → dialog chain to worry about; dialog opens from the
  same coroutine that closes the sheet).
- **Debug-menu hook** (`feedback_debug_menu_per_feature`): no new
  entitlement, time, or onboarding state — no debug entry needed.
- **Accessibility:** ⋮ icon gets `cd_customer_overflow`; the sheet
  surface gets `cd_customer_actions_sheet`; the tappable header gets
  `cd_customer_actions_view`.

### 5.8 QA smoke

1. Open Customers tab → confirm each active row has a trailing ⋮ icon.
2. Tap row body → CustomerDetailScreen opens (unchanged).
3. Tap ⋮ → sheet appears with header (avatar + name + phone + chevron)
   and 4 actions.
4. Tap header → sheet dismisses → CustomerDetailScreen opens.
5. Tap **Edit** → sheet dismisses → CustomerFormScreen (edit) opens.
6. Tap **New measurement** → sheet dismisses → Measurement form opens.
7. Tap **New order** → sheet dismisses → Order form opens with customer
   pre-selected.
8. Tap **Delete** → sheet dismisses → delete-confirm dialog appears;
   confirm-then-delete works; cancel works.
9. Customer with an active order → Delete shows the "blocked" dialog
   variant (existing behavior).
10. Confirm locked customers do **not** show the ⋮ icon and continue to
    use the Swap sheet.
11. On iPhone hardware: verify the `~450 ms` delay holds — no
    silent-fail nav after sheet dismiss.

## 6. Out of scope

- Removal of `Customer.deliveryPreference` from domain model, DTO,
  mapper, or seed fixtures.
- Removal of the delivery selector from the add/edit customer form.
- Any redesign of `CustomerDetailScreen` beyond the locked-customer
  surfaces it already has.
- Analytics events for the new entry points.
- Changes to the Order form's customer pre-selection logic (only verify
  the existing arg works).

## 7. Open questions

- **None at brainstorm close.** Two items flagged for verification
  during implementation planning:
  1. Form routes accept a `customerId` arg today (Customer edit,
     Measurement add, Order form). If any don't, the implementation
     plan extends them.
  2. Whether `customer_filter_all` string is referenced outside the
     Customer List filter — drives whether the string entry can be
     deleted in PR #1 or must be left in place.

## 8. Success criteria

- All three PRs merged to `main` with Cursor + `codex review` passed on
  each.
- Manual QA smoke tests in §3.5, §4.7, and §5.8 all pass on Android
  emulator and iPhone hardware.
- No regression in existing customer-list, dashboard, or
  customer-detail flows.
- No new Crashlytics signals in the 24 h after each PR ships to internal
  TestFlight / Play internal track.
