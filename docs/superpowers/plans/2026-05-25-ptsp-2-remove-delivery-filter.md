# PTSP-2 — Remove Delivery Filter from Customer List — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the "All / Pickup / Delivery" filter chip row from the
Customer List screen (PTSP-2). Search-only filtering remains. The
`Customer.deliveryPreference` domain field, the Firestore field, and the
add/edit customer form's `DeliveryPreferenceSelector` all stay
(documented as future-direction in the spec).

**Architecture:** Pure deletion — no new behavior. Removes the
`DeliveryFilterChips` composable, the `deliveryFilter` state field, the
`hasAnyCustomers` state field (orphaned once the chip-gate is gone), the
`OnDeliveryFilterChange` action, the `filterCustomers(..., deliveryFilter)`
parameter, and the `customer_filter_all` string. After this PR, the
Customers tab shows a search field, customer rows, and the locked section
— nothing between search and the list.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI
(State / Action / Event / ViewModel), no new dependencies.

**Spec:** `docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §3.

**Branch:** `feature/ptsp-2-remove-delivery-filter` (already checked out).

---

## File Map

| File                                                                                          | Change                                                                              |
|-----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt` | Delete `DeliveryFilterChips` composable + call site. Drop now-unused imports.       |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt` | Delete `OnDeliveryFilterChange` action. Drop unused `DeliveryPreference` import.    |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt`  | Delete `deliveryFilter` and `hasAnyCustomers` fields. Drop unused imports.          |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt` | Delete `OnDeliveryFilterChange` handler. Update `filterCustomers` signature + call sites. Drop `hasAnyCustomers` write. |
| `composeApp/src/commonMain/composeResources/values/strings.xml`                                | Remove `<string name="customer_filter_all">All</string>`.                            |

**No new files. No tests to write (pure deletion + no existing `CustomerListViewModelTest`).** Verification = `assembleDebug` + `compileKotlinIosSimulatorArm64` + `detekt` + `allTests` + manual smoke test.

> **Note on TDD discipline:** Standard "red → green → refactor" doesn't fit a pure deletion. There's no new behavior to assert. The discipline here is: confirm the symbol's blast radius (already done via the grep audit in the spec), delete in dependency order (UI first → state/action → VM → string), and verify nothing compiled against the deleted bits is still standing. Each task ends with the compiler as the test oracle.

---

### Task 1: Remove `DeliveryFilterChips` and its call site from `CustomerListScreen.kt`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt`

- [ ] **Step 1: Delete the chip-row call site**

Open `CustomerListScreen.kt` and find lines 198–203:

```kotlin
            if (state.hasAnyCustomers) {
                DeliveryFilterChips(
                    selected = state.deliveryFilter,
                    onSelected = { onAction(CustomerListAction.OnDeliveryFilterChange(it)) }
                )
            }
```

Delete the entire block (the `if (state.hasAnyCustomers) { ... }` wrapper and the `DeliveryFilterChips(...)` call inside).

- [ ] **Step 2: Delete the `DeliveryFilterChips` composable**

Find the private `DeliveryFilterChips` composable at line ~389:

```kotlin
@Composable
private fun DeliveryFilterChips(
    selected: DeliveryPreference?,
    onSelected: (DeliveryPreference?) -> Unit
) {
    val allLabel = stringResource(Res.string.customer_filter_all)
    val pickupLabel = stringResource(Res.string.delivery_pickup)
    val deliveryLabel = stringResource(Res.string.delivery_delivery)

    val options: List<Pair<DeliveryPreference?, String>> = listOf(
        null to allLabel,
        DeliveryPreference.PICKUP to pickupLabel,
        DeliveryPreference.DELIVERY to deliveryLabel
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = DesignTokens.space4, end = DesignTokens.space4, bottom = DesignTokens.space2)
    ) {
        options.forEach { (pref, label) ->
            val isSelected = selected == pref
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(pref) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Transparent,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = if (isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                }
            )
        }
    }
}
```

Delete the entire `@Composable private fun DeliveryFilterChips(...) { ... }` block.

- [ ] **Step 3: Remove the now-unused string import**

Find this import line near the top of the file (~line 99):

```kotlin
import stitchpad.composeapp.generated.resources.customer_filter_all
```

Delete it.

- [ ] **Step 4: Let the Kotlin compiler flag remaining unused imports**

Run:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | grep -E "warning|unused"
```

Expected: warnings (or detekt findings) for unused imports from the deleted `DeliveryFilterChips` body. Candidates to remove from `CustomerListScreen.kt` (verify in your IDE that each is genuinely unused elsewhere in the file before deleting):

- `androidx.compose.foundation.BorderStroke`
- `androidx.compose.foundation.horizontalScroll`
- `androidx.compose.foundation.rememberScrollState`
- `androidx.compose.material3.FilterChip`
- `androidx.compose.material3.FilterChipDefaults`
- `androidx.compose.ui.graphics.Color`

⚠️ **Keep these even though they appear in the deleted block** — they are used elsewhere in this file (verify by IDE "Find Usages" before deleting):

- `androidx.compose.foundation.layout.Arrangement` — used in other rows
- `androidx.compose.ui.text.font.FontWeight` — used in row labels
- `com.danzucker.stitchpad.core.domain.model.DeliveryPreference` — used in preview literal at the bottom of the file (was `deliveryPreference = DeliveryPreference.DELIVERY`)

- [ ] **Step 5: Compile to confirm no broken references**

Run:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. If the build fails with `unresolved reference: hasAnyCustomers` or `unresolved reference: deliveryFilter`, that means the call site at Step 1 wasn't fully removed — go back and re-check.

⚠️ **Do NOT commit yet** — `CustomerListAction.OnDeliveryFilterChange` is still referenced because we haven't touched the VM/Action yet. The screen now reads/calls fields/actions we're about to delete in Tasks 2–4. Tasks 1–4 form one logically-coherent change; commit after Task 4.

---

### Task 2: Delete the `OnDeliveryFilterChange` action

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt`

- [ ] **Step 1: Delete the action declaration**

The current file contents:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference

sealed interface CustomerListAction {
    data class OnSearchQueryChange(val query: String) : CustomerListAction
    data class OnDeliveryFilterChange(val filter: DeliveryPreference?) : CustomerListAction
    data class OnCustomerClick(val customer: Customer) : CustomerListAction
    data class OnDeleteCustomerClick(val customer: Customer) : CustomerListAction
    data object OnAddCustomerClick : CustomerListAction
    data object OnConfirmDelete : CustomerListAction
    data object OnDismissDeleteDialog : CustomerListAction
    data object OnErrorDismiss : CustomerListAction
    data class OpenSwapSheetFor(val lockedCustomerId: String) : CustomerListAction
    data object DismissSwapSheet : CustomerListAction
    data class ConfirmSwap(val lockedCustomerId: String, val activeCustomerIdToDemote: String) : CustomerListAction
}
```

Edit to:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer

sealed interface CustomerListAction {
    data class OnSearchQueryChange(val query: String) : CustomerListAction
    data class OnCustomerClick(val customer: Customer) : CustomerListAction
    data class OnDeleteCustomerClick(val customer: Customer) : CustomerListAction
    data object OnAddCustomerClick : CustomerListAction
    data object OnConfirmDelete : CustomerListAction
    data object OnDismissDeleteDialog : CustomerListAction
    data object OnErrorDismiss : CustomerListAction
    data class OpenSwapSheetFor(val lockedCustomerId: String) : CustomerListAction
    data object DismissSwapSheet : CustomerListAction
    data class ConfirmSwap(val lockedCustomerId: String, val activeCustomerIdToDemote: String) : CustomerListAction
}
```

(Two changes: drop the `OnDeliveryFilterChange` line; drop the now-unused `DeliveryPreference` import.)

- [ ] **Step 2: Do not compile yet**

The VM still references `OnDeliveryFilterChange` in its `when` branch. That gets fixed in Task 4. Move on.

---

### Task 3: Remove `deliveryFilter` and `hasAnyCustomers` from `CustomerListState`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt`

- [ ] **Step 1: Delete the two fields and their comments**

Current file:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val lockedCustomers: List<Customer> = emptyList(),
    val swapSheetForId: String? = null,
    val searchQuery: String = "",
    val deliveryFilter: DeliveryPreference? = null,
    /**
     * True once the user has at least one customer in the unfiltered list.
     * Drives whether the delivery-filter chip row is shown — chips would
     * just be visual noise on a brand-new account's empty Customers tab.
     */
    val hasAnyCustomers: Boolean = false,
    val isLoading: Boolean = true,
    ...
```

Edit to remove **both** `deliveryFilter` and `hasAnyCustomers` (and the multi-line KDoc comment block above `hasAnyCustomers`). The other fields stay verbatim.

Also remove the now-unused import:

```kotlin
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
```

Result:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val lockedCustomers: List<Customer> = emptyList(),
    val swapSheetForId: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    /**
     * True once the orders flow has emitted [com.danzucker.stitchpad.core.domain.error.Result.Success]
     * at least once, so [customerToDeleteActiveOrderCount] and the in-VM
     * `activeOrderCountByCustomerId` cache are trustworthy. Until then,
     * [CustomerListAction.OnConfirmDelete] is treated as a no-op with a snackbar to avoid
     * orphaning a customer's non-delivered orders by deleting on a stale (empty) count.
     */
    val ordersLoaded: Boolean = false,
    /**
     * True when the orders flow has emitted [com.danzucker.stitchpad.core.domain.error.Result.Error]
     * before any successful snapshot — typically offline / permission / transient failure. Distinct
     * from [ordersLoaded] so the delete dialog can surface a specific "couldn't load order data"
     * snackbar rather than the generic "still loading" one. Cleared on the next successful emission.
     */
    val ordersLoadFailed: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val customerToDelete: Customer? = null,
    /** Active (non-delivered) order count for [customerToDelete]. > 0 blocks deletion. */
    val customerToDeleteActiveOrderCount: Int = 0,
    val errorMessage: UiText? = null
)
```

- [ ] **Step 2: Do not compile yet**

The VM still writes to these fields and the screen still reads them via the previously-deleted code paths. Compile pass after Task 4.

---

### Task 4: Simplify `CustomerListViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt`

- [ ] **Step 1: Drop the `DeliveryPreference` import**

At the top of the file, delete:

```kotlin
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
```

- [ ] **Step 2: Update `OnSearchQueryChange` handler**

Find (around lines 65–72):

```kotlin
            is CustomerListAction.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = action.query,
                        customers = filterCustomers(allCustomers, action.query, it.deliveryFilter),
                        lockedCustomers = filterCustomers(allLockedCustomers, action.query, it.deliveryFilter),
                    )
                }
            }
```

Replace with:

```kotlin
            is CustomerListAction.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = action.query,
                        customers = filterCustomers(allCustomers, action.query),
                        lockedCustomers = filterCustomers(allLockedCustomers, action.query),
                    )
                }
            }
```

- [ ] **Step 3: Delete the `OnDeliveryFilterChange` branch**

Find (around lines 74–82):

```kotlin
            is CustomerListAction.OnDeliveryFilterChange -> {
                _state.update {
                    it.copy(
                        deliveryFilter = action.filter,
                        customers = filterCustomers(allCustomers, it.searchQuery, action.filter),
                        lockedCustomers = filterCustomers(allLockedCustomers, it.searchQuery, action.filter),
                    )
                }
            }
```

Delete the entire branch.

- [ ] **Step 4: Update `observeCustomers` collect block**

Find (around lines 159–166):

```kotlin
                        _state.update { state ->
                            state.copy(
                                customers = filterCustomers(active, state.searchQuery, state.deliveryFilter),
                                lockedCustomers = filterCustomers(locked, state.searchQuery, state.deliveryFilter),
                                hasAnyCustomers = result.data.isNotEmpty(),
                                isLoading = false
                            )
                        }
```

Replace with:

```kotlin
                        _state.update { state ->
                            state.copy(
                                customers = filterCustomers(active, state.searchQuery),
                                lockedCustomers = filterCustomers(locked, state.searchQuery),
                                isLoading = false
                            )
                        }
```

(Two changes: drop the `state.deliveryFilter` arg from both `filterCustomers` calls; drop the `hasAnyCustomers = result.data.isNotEmpty()` line.)

- [ ] **Step 5: Update `filterCustomers` signature and body**

Find (around lines 256–273):

```kotlin
    private fun filterCustomers(
        customers: List<Customer>,
        query: String,
        deliveryFilter: DeliveryPreference?
    ): List<Customer> {
        var result = customers
        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            result = result.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        }
        if (deliveryFilter != null) {
            result = result.filter {
                it.deliveryPreference == deliveryFilter ||
                    it.deliveryPreference == DeliveryPreference.EITHER
            }
        }
        return result
    }
```

Replace with:

```kotlin
    private fun filterCustomers(
        customers: List<Customer>,
        query: String,
    ): List<Customer> {
        if (query.isBlank()) return customers
        val q = query.lowercase().trim()
        return customers.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }
```

(Three changes: drop the `deliveryFilter` parameter; drop the entire `if (deliveryFilter != null) { ... }` block; simplify the early-return on blank query.)

- [ ] **Step 6: Check whether `@Suppress("CyclomaticComplexMethod", "LongMethod")` on `onAction` is still warranted**

The `onAction` method just lost one branch. If detekt still complains in Step 8, leave the suppression. If detekt no longer complains, remove the `@Suppress(...)` annotation above `fun onAction(...)` to keep the codebase honest.

To check: after Step 7's compile passes, run `./gradlew detekt` and see whether `CyclomaticComplexMethod` / `LongMethod` would fire on `onAction` if the suppression were removed. If unsure, leave the suppression in place — removing it is purely cosmetic, not blocking.

- [ ] **Step 7: Compile Android**

Run:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL with no errors. If `unresolved reference: deliveryFilter` or `unresolved reference: hasAnyCustomers` appears, retrace Tasks 2/3/4 — something was missed.

- [ ] **Step 8: Compile iOS (per `feedback_kmp_jvm_only_apis`)**

Run:

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL. The change is pure Kotlin / Compose Multiplatform code with no JVM-only API surface — iOS compile is a defensive check per saved memory feedback.

- [ ] **Step 9: Run detekt**

Run:

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If any new findings appear (e.g. unused imports the compiler tolerated), fix them inline and re-run.

- [ ] **Step 10: Run full test suite**

Run:

```bash
./gradlew :composeApp:allTests
```

Expected: BUILD SUCCESSFUL. No test touches `deliveryFilter` / `hasAnyCustomers` / `OnDeliveryFilterChange` (verified during plan recon — there is no `CustomerListViewModelTest` in `commonTest`). The suite should pass unchanged.

- [ ] **Step 11: Do not commit yet**

The `customer_filter_all` string entry in `strings.xml` is now orphaned. Clean it up in Task 5, then commit Tasks 1–5 together.

---

### Task 5: Remove the orphan string resource

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Delete the line**

Find line 219:

```xml
    <string name="customer_filter_all">All</string>
```

Delete it.

(No per-locale variants exist — verified during recon. Only `values/strings.xml` carries this key.)

- [ ] **Step 2: Compile Android to regenerate the string accessor**

Run:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The generated `String0.commonMain.kt` will lose its `customer_filter_all` reference. If anything still imports `stitchpad.composeapp.generated.resources.customer_filter_all`, the build will fail with `unresolved reference` — retrace Task 1 Step 3.

- [ ] **Step 3: Final verification**

Run all three checks one more time:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt
```

Expected: BUILD SUCCESSFUL for all three.

Then run the full test suite:

```bash
./gradlew :composeApp:allTests
```

Expected: BUILD SUCCESSFUL.

---

### Task 6: Manual smoke test (per `feedback_qa_smoke_tests`)

**Pre-req:** Android emulator or device running the debug build. iOS sim verification is a nice-to-have but not blocking for a deletion-only PR (compile is the iOS guarantee).

- [ ] **Step 1: Build and install debug APK**

Run:

```bash
./gradlew :composeApp:installDebug
```

Or launch from Android Studio.

- [ ] **Step 2: Open the Customers tab**

Sign in with the Fola test account (per `reference_test_environment`).

**Expected:**
- Search field at top of the screen.
- No chip row between search and the customer list. (Pre-PR: 3 chips "All / Pickup / Delivery". Post-PR: nothing.)
- Customer rows render unchanged.
- Locked-customer section renders unchanged (if account is over the cap).

- [ ] **Step 3: Type a customer name in search**

Expected: list narrows to matching customers. Search by name and by phone both work.

- [ ] **Step 4: Clear search**

Expected: full list returns.

- [ ] **Step 5: Swipe a customer row left**

Expected: delete confirmation works as before. Active-order block dialog still appears for customers with non-delivered orders.

- [ ] **Step 6: Open a customer's detail page**

Expected: tap row → detail. Unchanged.

- [ ] **Step 7: Sign out and sign in as a test account with zero customers**

Either use the Gabby account from `reference_test_environment` if it has no customers, or sign up a throwaway account, or wipe app storage on Fola first.

Expected: Customers tab opens to an empty state. No chip row visible. (Pre-PR: chip row hidden behind `hasAnyCustomers`. Post-PR: same observable result, achieved by deletion.)

- [ ] **Step 8: Confirm no unexpected layout shift**

Compare the spacing between the search field and the first customer row before/after. The chip row used to add a ~48dp band of vertical space. After deletion, the first customer row is closer to the search field — this is the intended visual change.

---

### Task 7: Commit and open PR

- [ ] **Step 1: Stage all the changes**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListState.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml
```

- [ ] **Step 2: Verify staged diff**

```bash
git diff --cached --stat
git diff --cached
```

Expected: 5 files changed, deletions outweigh insertions. No surprise touches outside these 5 files.

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(customers): remove All/Pickup/Delivery filter from Customer List (PTSP-2)

Drops the DeliveryFilterChips row, the deliveryFilter and
hasAnyCustomers state fields, the OnDeliveryFilterChange action,
the deliveryFilter parameter on filterCustomers, and the orphaned
customer_filter_all string. Search-only filtering remains. The
Customer.deliveryPreference domain field, Firestore field, and
add/edit form selector are unchanged.

Spec: docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md §3.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feature/ptsp-2-remove-delivery-filter
```

- [ ] **Step 5: Open the PR**

```bash
gh pr create --title "feat(customers): remove delivery filter from Customer List (PTSP-2)" --body "$(cat <<'EOF'
## Summary

- Removes the All/Pickup/Delivery filter chip row from the Customer List screen (PTSP-2 in PM Jira).
- Removes the orphaned `hasAnyCustomers` state field (only consumer was the chip-row visibility gate).
- Search-only filtering remains. `Customer.deliveryPreference`, the Firestore field, and the add/edit form selector are unchanged — see future-direction note in the spec for fuller removal.

Spec: `docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §3.

This is the first of three QA-batch PRs from the post-freemium-V1.0 PM pass. PTSP-1 and PTSP-15 follow on their own branches.

## Test plan

- [x] `./gradlew :composeApp:compileDebugKotlinAndroid` ✅
- [x] `./gradlew :composeApp:compileKotlinIosSimulatorArm64` ✅
- [x] `./gradlew detekt` ✅
- [x] `./gradlew :composeApp:allTests` ✅
- [x] Manual smoke (Android, Fola account): search filters, swipe-delete, empty-state, no chip row visible.
- [x] Manual smoke (Android, Gabby brand-new account): empty Customers tab renders with no chip row.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: Run the dual-review rotation (`feedback_review_rotation`)**

After CI passes:

1. Trigger Cursor review on the PR.
2. Run `codex review` locally.
3. Address findings; commit fixes as new commits (no force-pushes per `feedback_pr_workflow`).
4. Merge only when both reviews are green.

---

## Out of scope (already noted in spec)

- Removing `Customer.deliveryPreference` from the domain model.
- Removing the field from `CustomerDto` / `CustomerMapper` / Firestore.
- Removing `DeliveryPreferenceSelector` from the add/edit customer form.
- Removing the `delivery_pickup` / `delivery_delivery` / `delivery_either` strings (still used by the form).
- Writing a `CustomerListViewModelTest` (genuine gap, but adding it under PTSP-2 is scope creep — track as a follow-up backlog item if you want coverage).
