# PTSP-15 — Customer Row Actions Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a trailing ⋮ overflow icon to each active row on the Customer List screen. Tap on the row body still opens `CustomerDetailScreen` (unchanged). Tap on the ⋮ opens a `ModalBottomSheet` with header (avatar + name + phone + tappable chevron) and 4 actions: Edit, New measurement, New order, Delete.

**Architecture:** Single new composable (`CustomerActionsSheet`) plus state/action/event/VM plumbing in the customer list feature. Reuses existing form routes by extending `OrderFormRoute` with an optional `customerId` to enable customer pre-selection. Locked customers continue to use `LockedCustomerRow` + the existing Swap sheet — the ⋮ icon is only added to active rows.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 ModalBottomSheet, MVI.

**Spec:** `docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §5.

**Branch:** `feature/ptsp-15-customer-row-actions` (already checked out off latest `main`, which now includes PR #74 (PTSP-2) + PR #76 (PTSP-1) + PR #75 (PTSP-7 "Customer not found" search empty state)).

---

## File Map

| File                                                                                                                       | Change                                                                                          |
|----------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`                                            | Add `customerId: String? = null` to `OrderFormRoute`.                                            |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`            | Read `customerId` from `SavedStateHandle`; seed `pendingCustomerId` so existing resolve logic auto-selects when customers load. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/components/CustomerActionsSheet.kt` | **New.** The ModalBottomSheet — header (avatar + name + phone + chevron → detail) + 4 action rows. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt`         | Add 6 new actions (`OnOverflowClick`, `DismissActionsSheet`, `OnViewCustomerFromSheet`, `OnEditCustomerFromRow`, `OnAddMeasurementFromRow`, `OnNewOrderFromRow`). |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListEvent.kt`          | Add 3 new nav events (`NavigateToEditCustomer`, `NavigateToAddMeasurement`, `NavigateToOrderForm`).                 |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt`          | Add `actionsSheetForId: String? = null`.                                                          |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt`      | Handle the 6 new actions. Apply `~450 ms` post-dismiss delay before emitting nav events (per `feedback_ios_modal_bottom_sheet_timing`). |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt`         | Replace trailing chevron with an ⋮ `IconButton` on `CustomerListItem`. Render `CustomerActionsSheet` when state-flag is set. Update `CustomerListRoot` to expose 3 new nav callbacks. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`                          | Wire the 3 new nav callbacks for `CustomerListRoot` → `CustomerFormRoute(id)`, `MeasurementFormRoute(id)`, `OrderFormRoute(customerId = id)`. |
| `composeApp/src/commonMain/composeResources/values/strings.xml`                                                            | Add 7 new string keys (see Task 7).                                                              |

**Tests:** None required. `CustomerListViewModelTest` doesn't exist yet — adding it is out of scope per PTSP-2's "Out of scope" backlog. The new actions are pure event-emit-then-state-toggle logic with no math.

---

### Task 1: Extend `OrderFormRoute` to accept customerId + wire pre-selection in `OrderFormViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

**Why first:** PTSP-15's "New order" sheet action routes to `OrderFormRoute(customerId = id)`. If this isn't there yet, all subsequent wiring is unresolvable. Doing it first keeps the rest of the plan compile-coherent.

- [ ] **Step 1: Add `customerId` to `OrderFormRoute`**

In `Routes.kt`, find:

```kotlin
@Serializable
data class OrderFormRoute(
    val orderId: String? = null,
    val seedFromOrderId: String? = null,
)
```

Replace with:

```kotlin
@Serializable
data class OrderFormRoute(
    val orderId: String? = null,
    val seedFromOrderId: String? = null,
    val customerId: String? = null,
)
```

Adding a new optional arg at the end is backwards-compatible — every existing `OrderFormRoute()` / `OrderFormRoute(orderId = ...)` / `OrderFormRoute(seedFromOrderId = ...)` call still resolves.

- [ ] **Step 2: Read `customerId` in `OrderFormViewModel` and seed pendingCustomerId**

In `OrderFormViewModel.kt`, find the existing SavedStateHandle reads (around lines 49–50):

```kotlin
    private val orderId: String? = savedStateHandle["orderId"]
    private val seedFromOrderId: String? = savedStateHandle["seedFromOrderId"]
```

Add a third line right after them:

```kotlin
    private val initialCustomerId: String? = savedStateHandle["customerId"]
```

Then find the existing `pendingCustomerId` declaration (around line 61):

```kotlin
    // On edit, loadOrder may finish before observeCustomers emits. Record the target
    // customer id and resolve it reactively whenever either event wins the race.
    private var pendingCustomerId: String? = null
```

Replace with:

```kotlin
    // On edit (orderId != null), loadOrder may finish before observeCustomers emits.
    // On create-with-pre-selected-customer (initialCustomerId != null, from
    // PTSP-15's "New order" sheet action), we already know the target. Either way
    // we record the target id and resolve it reactively whenever the customer list
    // emits — the existing resolvePendingCustomer() does the matching.
    private var pendingCustomerId: String? = initialCustomerId
```

That's it for the ViewModel. The `resolvePendingCustomer()` function (around line 194) already handles the seed: when the customer list emits a Success result, it looks up the pending id, finds the matching `Customer`, sets `selectedCustomer`, and clears `pendingCustomerId`.

- [ ] **Step 3: Compile + verify**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

Both must be BUILD SUCCESSFUL. The change is additive — no existing call sites break.

- [ ] **Step 4: Do not commit yet**

---

### Task 2: Add state field + actions + events for the sheet

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListEvent.kt`

- [ ] **Step 1: Add state field**

In `CustomerListState.kt`, add `actionsSheetForId: String? = null` to the data class. Place it next to `swapSheetForId` for readability:

OLD field block (lines 7–11 in the current file):
```kotlin
data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val lockedCustomers: List<Customer> = emptyList(),
    val swapSheetForId: String? = null,
    val searchQuery: String = "",
```

NEW:
```kotlin
data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val lockedCustomers: List<Customer> = emptyList(),
    val swapSheetForId: String? = null,
    /** When non-null, the row-overflow actions sheet is open for this customer id. */
    val actionsSheetForId: String? = null,
    val searchQuery: String = "",
```

- [ ] **Step 2: Add the 6 new actions**

In `CustomerListAction.kt`, add to the sealed interface body (after existing entries):

```kotlin
    /** Tapped the ⋮ overflow icon on an active customer row. */
    data class OnOverflowClick(val customer: Customer) : CustomerListAction

    /** Sheet dismissed by swipe-down, backdrop tap, or system back. */
    data object DismissActionsSheet : CustomerListAction

    /** Tapped the sheet header (avatar + name + phone + chevron). Routes to detail. */
    data class OnViewCustomerFromSheet(val customerId: String) : CustomerListAction

    /** Tapped "Edit" in the actions sheet. Routes directly to the customer form (edit mode). */
    data class OnEditCustomerFromRow(val customerId: String) : CustomerListAction

    /** Tapped "New measurement" in the actions sheet. Routes directly to the measurement form. */
    data class OnAddMeasurementFromRow(val customerId: String) : CustomerListAction

    /** Tapped "New order" in the actions sheet. Routes to the order form with customer pre-selected. */
    data class OnNewOrderFromRow(val customerId: String) : CustomerListAction
```

The `Customer` import is already there. No new imports needed.

- [ ] **Step 3: Add the 3 new nav events**

In `CustomerListEvent.kt`, add to the sealed interface body (after existing entries):

```kotlin
    data class NavigateToEditCustomer(val customerId: String) : CustomerListEvent
    data class NavigateToAddMeasurement(val customerId: String) : CustomerListEvent
    data class NavigateToOrderForm(val customerId: String) : CustomerListEvent
```

Note: we DO NOT add a `NavigateToCustomerDetail`-from-sheet variant — `OnViewCustomerFromSheet` reuses the existing `NavigateToCustomerDetail(id)` event (already wired). Just route the new action to the existing event.

- [ ] **Step 4: Do not compile yet**

The ViewModel doesn't handle the new actions yet — will be unresolved-when-branch until Task 3.

---

### Task 3: Handle new actions in `CustomerListViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt`

**Design note (iOS timing):** Per `feedback_ios_modal_bottom_sheet_timing`, after a Compose `ModalBottomSheet` dismisses, launching a UIKit-backed nav transition immediately can silently fail on iOS. The dashboard's existing pattern is a `~450 ms` `delay()` between sheet dismiss and nav-event emit. We apply the same here: each navigation action first clears `actionsSheetForId`, then delays, then emits the nav event.

- [ ] **Step 1: Add the handlers**

In the `onAction` `when` block (around lines 65–150), add these branches near the existing `OpenSwapSheetFor` / `DismissSwapSheet` handlers for locality:

```kotlin
            is CustomerListAction.OnOverflowClick -> {
                _state.update { it.copy(actionsSheetForId = action.customer.id) }
            }
            CustomerListAction.DismissActionsSheet -> {
                _state.update { it.copy(actionsSheetForId = null) }
            }
            is CustomerListAction.OnViewCustomerFromSheet -> {
                navigateFromSheet { CustomerListEvent.NavigateToCustomerDetail(action.customerId) }
            }
            is CustomerListAction.OnEditCustomerFromRow -> {
                navigateFromSheet { CustomerListEvent.NavigateToEditCustomer(action.customerId) }
            }
            is CustomerListAction.OnAddMeasurementFromRow -> {
                navigateFromSheet { CustomerListEvent.NavigateToAddMeasurement(action.customerId) }
            }
            is CustomerListAction.OnNewOrderFromRow -> {
                navigateFromSheet { CustomerListEvent.NavigateToOrderForm(action.customerId) }
            }
```

- [ ] **Step 2: Add the `navigateFromSheet` helper**

At the bottom of the class (before the closing brace, after `filterCustomers`), add:

```kotlin
    /**
     * Common path for the four "from row" nav actions: close the sheet, wait
     * ~450ms for the Compose dismissal to fully settle (per
     * `feedback_ios_modal_bottom_sheet_timing` — UIKit-backed nav after a
     * Compose sheet dismiss can silently no-op on iOS), then emit the nav
     * event.
     */
    private fun navigateFromSheet(event: () -> CustomerListEvent) {
        _state.update { it.copy(actionsSheetForId = null) }
        viewModelScope.launch {
            delay(SHEET_DISMISS_DELAY_MS)
            _events.send(event())
        }
    }
```

- [ ] **Step 3: Add the constant**

Above the class declaration (or as a top-level `private const`):

```kotlin
private const val SHEET_DISMISS_DELAY_MS = 450L
```

- [ ] **Step 4: Add the `delay` import**

Add to the import block:

```kotlin
import kotlinx.coroutines.delay
```

- [ ] **Step 5: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Must be BUILD SUCCESSFUL.

The `when` block lost its exhaustive cover before Task 4 wires the screen, but the action sealed interface is now larger and the ViewModel handles every new variant — there should be no unresolved-when warning.

- [ ] **Step 6: Do not commit yet**

---

### Task 4: Create `CustomerActionsSheet` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/components/CustomerActionsSheet.kt`

**Design notes:**
- Header: avatar + name + phone + trailing chevron (`Icons.AutoMirrored.Filled.KeyboardArrowRight`). Entire header is one tappable Row.
- 4 action rows below the header divider. Each row: leading icon + label.
- Delete row uses `error`-tinted icon and label to match the "Dialog for destructive" convention.
- All clickable surfaces use `role = Role.Button` for accessibility (per `feedback_review_rotation` lesson from PTSP-1 nit #2).
- Use the same `SwapSheet` pattern: `@Composable fun CustomerActionsSheet(...)` wraps `ModalBottomSheet`; inner `CustomerActionsSheetContent` is preview-friendly.

- [ ] **Step 1: Write the file**

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_customer_actions_sheet
import stitchpad.composeapp.generated.resources.cd_customer_actions_view
import stitchpad.composeapp.generated.resources.customer_actions_delete
import stitchpad.composeapp.generated.resources.customer_actions_edit
import stitchpad.composeapp.generated.resources.customer_actions_new_measurement
import stitchpad.composeapp.generated.resources.customer_actions_new_order

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerActionsSheet(
    customer: Customer,
    onView: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNewMeasurement: (String) -> Unit,
    onNewOrder: (String) -> Unit,
    onDelete: (Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CustomerActionsSheetContent(
            customer = customer,
            onView = onView,
            onEdit = onEdit,
            onNewMeasurement = onNewMeasurement,
            onNewOrder = onNewOrder,
            onDelete = onDelete,
        )
    }
}

/**
 * Inner column extracted so @Preview can render it — ModalBottomSheet
 * itself doesn't lay out in preview mode (no host activity / sheet state).
 */
@Composable
private fun CustomerActionsSheetContent(
    customer: Customer,
    onView: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNewMeasurement: (String) -> Unit,
    onNewOrder: (String) -> Unit,
    onDelete: (Customer) -> Unit,
) {
    val sheetCd = stringResource(Res.string.cd_customer_actions_sheet, customer.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space2),
    ) {
        SheetHeader(
            customer = customer,
            onClick = { onView(customer.id) },
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space4),
        )
        Spacer(Modifier.height(DesignTokens.space2))
        ActionRow(
            icon = Icons.Default.Edit,
            label = stringResource(Res.string.customer_actions_edit),
            onClick = { onEdit(customer.id) },
        )
        ActionRow(
            icon = Icons.Default.Straighten,
            label = stringResource(Res.string.customer_actions_new_measurement),
            onClick = { onNewMeasurement(customer.id) },
        )
        ActionRow(
            icon = Icons.AutoMirrored.Filled.Assignment,
            label = stringResource(Res.string.customer_actions_new_order),
            onClick = { onNewOrder(customer.id) },
        )
        ActionRow(
            icon = Icons.Default.Delete,
            label = stringResource(Res.string.customer_actions_delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = { onDelete(customer) },
        )
    }
}

@Composable
private fun SheetHeader(
    customer: Customer,
    onClick: () -> Unit,
) {
    val viewCd = stringResource(Res.string.cd_customer_actions_view, customer.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
    ) {
        CustomerAvatar(name = customer.name, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (customer.phone.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = customer.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = viewCd,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerActionsSheetContentPreview() {
    StitchPadTheme {
        CustomerActionsSheetContent(
            customer = Customer(
                id = "c1",
                userId = "u1",
                name = "Amina Bello",
                phone = "+234 801 234 5678",
            ),
            onView = {},
            onEdit = {},
            onNewMeasurement = {},
            onNewOrder = {},
            onDelete = {},
        )
    }
}
```

**Icon fallback policy:** if `Icons.AutoMirrored.Filled.Assignment` or `Icons.Default.Straighten` aren't available in the bundled icon pack, fall back to `Icons.Default.Add` (for Assignment) or `Icons.Default.Edit` (for Straighten). Run the compile to find out.

- [ ] **Step 2: This file references 6 string resources that don't exist yet — DON'T compile yet**

Compile will fail with `unresolved reference: customer_actions_edit` etc. until Task 7 adds the strings. Continue.

---

### Task 5: Update `CustomerListScreen` — ⋮ icon + sheet rendering + Root callbacks

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt`

- [ ] **Step 1: Update imports**

Add to the import block (alphabetical placement):

```kotlin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import com.danzucker.stitchpad.feature.customer.presentation.list.components.CustomerActionsSheet
import stitchpad.composeapp.generated.resources.cd_customer_overflow
```

Verify (do not duplicate; the IDE auto-import will likely handle this).

- [ ] **Step 2: Update `CustomerListRoot` signature**

Find (around lines 107–110):

```kotlin
@Composable
fun CustomerListRoot(
    onNavigateToAddCustomer: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit
) {
```

Replace with:

```kotlin
@Composable
fun CustomerListRoot(
    onNavigateToAddCustomer: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToEditCustomer: (String) -> Unit,
    onNavigateToAddMeasurement: (String) -> Unit,
    onNavigateToOrderForm: (String) -> Unit,
) {
```

- [ ] **Step 3: Update the event-handling `when` block inside `CustomerListRoot`**

Find (around lines 117–121):

```kotlin
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerListEvent.NavigateToAddCustomer -> onNavigateToAddCustomer()
            is CustomerListEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            // ... existing SwapSucceeded / SwapFailed branches ...
        }
    }
```

Add branches for the 3 new events. The full updated `when` should be:

```kotlin
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerListEvent.NavigateToAddCustomer -> onNavigateToAddCustomer()
            is CustomerListEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            is CustomerListEvent.NavigateToEditCustomer -> onNavigateToEditCustomer(event.customerId)
            is CustomerListEvent.NavigateToAddMeasurement -> onNavigateToAddMeasurement(event.customerId)
            is CustomerListEvent.NavigateToOrderForm -> onNavigateToOrderForm(event.customerId)
            // ... keep any existing SwapSucceeded / SwapFailed branches as-is ...
        }
    }
```

If the existing `when` has Swap branches (it does, per current code), keep them.

- [ ] **Step 4: Update `CustomerListItem` — replace chevron with ⋮ IconButton**

Find the existing `CustomerListItem` private composable (around lines 622–658). The current trailing element is:

```kotlin
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
    }
```

Replace the trailing `Icon(...)` with an `IconButton` containing the ⋮ icon. The `IconButton` takes its own click and DOES NOT propagate to the parent Row's `clickable`.

Also update `CustomerListItem`'s signature to accept an overflow callback:

OLD:
```kotlin
@Composable
private fun CustomerListItem(customer: Customer, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        CustomerAvatar(name = customer.name, size = 44.dp)

        Column(modifier = Modifier.weight(1f)) {
            // ... name + phone ...
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
```

NEW:
```kotlin
@Composable
private fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = DesignTokens.space4, end = DesignTokens.space2, top = DesignTokens.space3, bottom = DesignTokens.space3)
    ) {
        CustomerAvatar(name = customer.name, size = 44.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = customer.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onOverflowClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.cd_customer_overflow, customer.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Note the reduced `end` padding (`space2` not `space4`) to compensate for the IconButton's built-in 12dp internal padding — keeps the trailing edge visually consistent with the old chevron.

- [ ] **Step 5: Update the call site of `CustomerListItem`**

Find where `CustomerListItem` is called (inside `SwipeableCustomerItem`, around line 616):

```kotlin
            CustomerListItem(customer = customer, onClick = onClick)
```

This is inside `SwipeableCustomerItem`. We need to plumb the overflow callback through. First update `SwipeableCustomerItem`:

OLD:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCustomerItem(
    customer: Customer,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // ... swipe state ...
    SwipeToDismissBox(...) {
        Surface(...) {
            CustomerListItem(customer = customer, onClick = onClick)
        }
    }
}
```

NEW:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCustomerItem(
    customer: Customer,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    // ... existing swipe state — unchanged ...
    SwipeToDismissBox(...) {
        Surface(...) {
            CustomerListItem(customer = customer, onClick = onClick, onOverflowClick = onOverflowClick)
        }
    }
}
```

Then update the call site of `SwipeableCustomerItem` (around line 220 in the `LazyColumn`):

OLD:
```kotlin
                        items(items = state.customers, key = { it.id }) { customer ->
                            SwipeableCustomerItem(
                                customer = customer,
                                onClick = { onAction(CustomerListAction.OnCustomerClick(customer)) },
                                onDelete = { onAction(CustomerListAction.OnDeleteCustomerClick(customer)) }
                            )
                            // ... divider ...
                        }
```

NEW:
```kotlin
                        items(items = state.customers, key = { it.id }) { customer ->
                            SwipeableCustomerItem(
                                customer = customer,
                                onClick = { onAction(CustomerListAction.OnCustomerClick(customer)) },
                                onDelete = { onAction(CustomerListAction.OnDeleteCustomerClick(customer)) },
                                onOverflowClick = { onAction(CustomerListAction.OnOverflowClick(customer)) },
                            )
                            // ... divider unchanged ...
                        }
```

- [ ] **Step 6: Render `CustomerActionsSheet` when `actionsSheetForId` is set**

Inside `CustomerListScreen` (the screen-level composable, NOT the Root), find the existing `state.swapSheetForId?.let { ... }` block (around lines 293–303). Add a sibling block right after it (or wherever ergonomic, but co-located with the swap-sheet block keeps the two sheet renderings together):

```kotlin
    state.actionsSheetForId?.let { customerId ->
        val customer = state.customers.firstOrNull { it.id == customerId }
        if (customer != null) {
            CustomerActionsSheet(
                customer = customer,
                onView = { id -> onAction(CustomerListAction.OnViewCustomerFromSheet(id)) },
                onEdit = { id -> onAction(CustomerListAction.OnEditCustomerFromRow(id)) },
                onNewMeasurement = { id -> onAction(CustomerListAction.OnAddMeasurementFromRow(id)) },
                onNewOrder = { id -> onAction(CustomerListAction.OnNewOrderFromRow(id)) },
                onDelete = { c -> onAction(CustomerListAction.OnDeleteCustomerClick(c)) },
                onDismiss = { onAction(CustomerListAction.DismissActionsSheet) },
            )
        }
    }
```

The `onDelete` reuses the existing `OnDeleteCustomerClick` action — that one already opens the delete-confirm dialog (or the active-orders blocked variant). No new event/dialog logic needed.

**Important — `onDelete` and the sheet:** When the user taps Delete inside the sheet, we fire `OnDeleteCustomerClick(customer)` which sets `showDeleteDialog = true`. The sheet stays open visually until the user dismisses or confirms the dialog. That's acceptable UX (the dialog is modal so the sheet is hidden behind it). If you want the sheet to close BEFORE the dialog appears, the `onDelete` lambda can also dispatch `DismissActionsSheet` first — but the existing delete dialog renders OVER the sheet without issue, so no change needed.

- [ ] **Step 7: Do not compile yet — strings still missing**

Strings come in Task 7.

---

### Task 6: Wire `CustomerListRoot` in `MainScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: Update the `composable<CustomerListRoute>` block**

Find (around lines 171–180):

```kotlin
        composable<CustomerListRoute> {
            CustomerListRoot(
                onNavigateToAddCustomer = {
                    navController.navigate(CustomerFormRoute())
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                }
            )
        }
```

Replace with:

```kotlin
        composable<CustomerListRoute> {
            CustomerListRoot(
                onNavigateToAddCustomer = {
                    navController.navigate(CustomerFormRoute())
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                },
                onNavigateToEditCustomer = { customerId ->
                    navController.navigate(CustomerFormRoute(customerId = customerId))
                },
                onNavigateToAddMeasurement = { customerId ->
                    navController.navigate(MeasurementFormRoute(customerId = customerId))
                },
                onNavigateToOrderForm = { customerId ->
                    navController.navigate(OrderFormRoute(customerId = customerId))
                },
            )
        }
```

The `OrderFormRoute(customerId = customerId)` call works because Task 1 added the `customerId` parameter.

- [ ] **Step 2: Do not compile yet — strings still missing**

---

### Task 7: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the 7 keys**

Place under a new section comment (find a sensible spot — near other customer-related strings, e.g. after the existing `customer_swap_*` block):

```xml
    <!-- Customer row actions sheet (PTSP-15) -->
    <string name="customer_actions_edit">Edit</string>
    <string name="customer_actions_new_measurement">New measurement</string>
    <string name="customer_actions_new_order">New order</string>
    <string name="customer_actions_delete">Delete</string>
    <string name="cd_customer_overflow">More actions for %1$s</string>
    <string name="cd_customer_actions_sheet">Actions for %1$s</string>
    <string name="cd_customer_actions_view">View %1$s\'s details</string>
```

**Wait — `\'` is forbidden per `feedback_strings_no_backslash_escape`**. Use the typographic apostrophe instead:

```xml
    <string name="cd_customer_actions_view">View %1$s’s details</string>
```

The `%1$s` placeholders accept the customer name at runtime — that's how `stringResource(Res.string.cd_customer_overflow, customer.name)` works in the screen / sheet code.

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Must be BUILD SUCCESSFUL. If any unresolved-reference errors fire, retrace Tasks 4–6 — something didn't pick up the right string key name.

---

### Task 8: Full verification

- [ ] **Step 1: Android compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: iOS compile (per `feedback_kmp_jvm_only_apis` + `feedback_strings_no_backslash_escape` — iOS renders `\'` literally so the typographic apostrophe matters)**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If `LongMethod` fires on `CustomerListViewModel.onAction`, that's expected — keep the existing `@Suppress` (or add it if not present).

- [ ] **Step 4: Tests**

```bash
./gradlew :composeApp:allTests
```

Expected: Android tests pass; iOS `linkDebugTestIosSimulatorArm64` may fail on pre-existing `FirebaseCore` framework — not introduced by this PR (documented on PR #74 and PR #76).

---

### Task 9: Manual smoke test (Daniel)

- [ ] **Step 1: Install**

```bash
./gradlew :composeApp:installDebug
```

- [ ] **Step 2: Active row — tap row body still opens detail**

Sign in with Fola → Customers tab. Tap on a customer name → `CustomerDetailScreen` opens (unchanged behavior).

- [ ] **Step 3: Active row — tap ⋮ opens sheet**

Back to Customers list. Tap the ⋮ icon at the right of any active row. Bottom sheet appears with:
- Header: avatar + name + phone + chevron `›`
- Edit row (✏️)
- New measurement row (📏)
- New order row (📋)
- Delete row (🗑️, error-tinted)

- [ ] **Step 4: Tap sheet header → opens detail**

In the open sheet, tap anywhere on the header row (avatar / name / phone / chevron). Sheet dismisses, ~450 ms later detail screen opens.

- [ ] **Step 5: Tap Edit → opens edit form**

Open sheet again. Tap Edit. Sheet dismisses, customer form opens in edit mode with the existing data pre-populated.

- [ ] **Step 6: Tap New measurement → opens measurement form**

Open sheet. Tap "New measurement". Sheet dismisses, measurement form opens with the customer pre-selected.

- [ ] **Step 7: Tap New order → opens order form with customer pre-selected**

Open sheet. Tap "New order". Sheet dismisses, order form opens with the customer field already populated (no manual customer-pick step).

- [ ] **Step 8: Tap Delete → opens existing delete dialog**

Open sheet. Tap Delete. Existing delete-confirm dialog appears. Confirm → row deletes (or the active-orders blocked variant if customer has non-delivered orders).

- [ ] **Step 9: Swipe-to-delete still works**

On the customer list, swipe a row left → existing delete dialog appears. Verify the swipe gesture wasn't broken by the ⋮ IconButton.

- [ ] **Step 10: Sheet dismissal**

Tap outside the sheet (backdrop), swipe down on the sheet, and press the Android system back button — all three should dismiss the sheet cleanly without nav.

- [ ] **Step 11: Locked customers — no ⋮ icon**

Sign in with an account that has locked customers (or use the debug menu to enter the locked-cap state). Verify locked rows still use `LockedCustomerRow` (no ⋮ icon, the existing Swap-sheet flow is the only affordance).

- [ ] **Step 12: iOS hardware verification (per `feedback_ios_modal_bottom_sheet_timing`)**

On a real iPhone, confirm the 450 ms delay holds — no silent-fail nav after the sheet dismisses. Tap each of: View / Edit / New measurement / New order. All four should navigate within ~half a second of the sheet collapsing.

---

### Task 10: Commit + push + open PR

- [ ] **Step 1: Stage and commit the plan doc first** (matches PR #74 / PR #76 pattern)

```bash
git add docs/superpowers/plans/2026-05-25-ptsp-15-customer-row-actions.md
git commit -m "$(cat <<'EOF'
docs(plans): PTSP-15 implementation plan (customer row actions sheet)

Task-by-task plan for the third QA-batch PR. Includes a customerId
extension to OrderFormRoute so the sheet's "New order" action can
pre-select the customer in the order form.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 2: Stage the implementation files**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/components/CustomerActionsSheet.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListEvent.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml

git diff --cached --stat
```

- [ ] **Step 3: Feat commit**

```bash
git commit -m "$(cat <<'EOF'
feat(customers): row actions sheet — Edit / New measurement / New order / Delete (PTSP-15)

Adds a trailing ⋮ overflow icon to each active customer row. Tap on
the row body still opens CustomerDetailScreen (unchanged). Tap on ⋮
opens a ModalBottomSheet with a tappable header (avatar + name +
phone + chevron → detail) and four actions: Edit, New measurement,
New order, Delete.

The "New order" action routes to the order form with the selected
customer pre-populated — added an optional customerId parameter to
OrderFormRoute and seeded pendingCustomerId in OrderFormViewModel so
the existing resolve-pending-customer logic auto-selects when the
customer list loads.

Locked customers continue to use LockedCustomerRow + the existing
Swap sheet — the ⋮ icon is only added to active rows.

Per feedback_ios_modal_bottom_sheet_timing, all four nav actions
apply a ~450ms delay between sheet dismiss and event emit so
UIKit-backed nav transitions don't silently no-op after a Compose
sheet dismiss on iOS.

Spec: docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md §5.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Push** (pre-push `codex review` runs automatically)

```bash
git push -u origin feature/ptsp-15-customer-row-actions
```

- [ ] **Step 5: Open PR — pause for Daniel's approval first**

After push succeeds, ask Daniel before running `gh pr create`. Use this body:

```bash
gh pr create --title "feat(customers): row actions sheet (PTSP-15)" --body "$(cat <<'EOF'
## Summary

- Adds a trailing ⋮ overflow icon to each active customer row. Tap-on-row-body still opens \`CustomerDetailScreen\` (unchanged); tap-on-⋮ opens a ModalBottomSheet with header → detail + 4 actions (Edit / New measurement / New order / Delete).
- Sheet header is itself tappable (avatar + name + phone + trailing chevron) → routes to detail.
- "New order" pre-selects the customer in the order form — added an optional \`customerId\` arg to \`OrderFormRoute\` and seeded \`pendingCustomerId\` in the ViewModel so the existing resolve-pending-customer logic auto-selects.
- Locked customers untouched — still use \`LockedCustomerRow\` + Swap sheet.
- All four nav actions apply a ~450ms post-dismiss delay (per \`feedback_ios_modal_bottom_sheet_timing\`).

Spec: \`docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md\` §5.

This is the third (and final) QA-batch PR after PR #74 (PTSP-2) and PR #76 (PTSP-1).

## Test plan

- [x] \`./gradlew :composeApp:compileDebugKotlinAndroid\` ✅
- [x] \`./gradlew :composeApp:compileKotlinIosSimulatorArm64\` ✅
- [x] \`./gradlew detekt\` ✅
- [x] \`./gradlew :composeApp:allTests\` — Android passes; iOS link fails on pre-existing FirebaseCore (same as PR #74 / PR #76).
- [x] Manual smoke (Android, Fola): row tap → detail; ⋮ → sheet; each of 4 actions routes correctly; header → detail; swipe-delete still works; locked rows have no ⋮.
- [x] Manual smoke (iPhone hardware): 450 ms post-dismiss delay confirmed; no silent nav-fail on iOS.
- [ ] Pre-push \`codex review\` (automatic on push)
- [ ] Cursor review

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of scope (already noted in spec §5)

- Removing the top-bar Edit button on `CustomerDetailScreen` (now redundant with the sheet's Edit, but reachable from a different surface; intentionally kept).
- Removing swipe-to-delete on customer list (redundant with sheet's Delete, but faster for power users; intentionally kept).
- Writing `CustomerListViewModelTest` — same scope-creep call as PTSP-2.
- Adding analytics events.
- Restructuring the `CustomerDetailScreen` measurements list or styles section.
