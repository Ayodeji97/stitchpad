# PTSP-14 — Editable Deposit Paid in Edit Order

**Ticket:** PTSP-14 · "Work on the deposit paid aspect"
**Branch:** `feature/ptsp-14-deposit-paid-edit`
**Date:** 2026-05-26

## Problem

In the Edit Order flow (Step 3 of 3 — Details), the "Deposit paid (₦)" field is locked read-only with a helper line telling users to "Use Record Payment on the order details screen to update." This blocks two legitimate user actions:

1. A user who saved a new order without entering a deposit cannot add one later via Edit Order.
2. A user who entered the wrong figure during new-order intake cannot correct it via Edit Order.

The product requirement (from the ticket) is that the user should be able to edit the deposit paid figure from the Edit Order screen.

## Why it works this way today

`Order.depositPaid` is a computed property — `payments.sumOf { it.amount }` (Order.kt:64–68) — derived from a list of `Payment` entities, each timestamped and typed (`DEPOSIT` / `PROGRESS` / `FINAL`). The "Record Payment" flow on the Order Details screen creates new `Payment` rows, preserving a full audit trail.

> **Naming caveat:** the domain property is called `depositPaid` but in practice sums *all* payment types. Renaming is out of scope for this ticket. The form-field treatment below filters by `PaymentType.DEPOSIT` explicitly so the label "Deposit paid" matches what's actually shown.

The original Edit Order form respected that model by refusing to let the user mutate the deposit through a simple text field. Two enforcement points keep it read-only:

- `OrderFormScreen.kt:1155–1174` — the OutlinedTextField sets `readOnly = state.isEditMode` and shows the "Use Record Payment…" supporting text only when editing.
- `OrderFormViewModel.kt:473–488` — even if the field were editable, the save logic in edit mode preserves `loadedPayments` unchanged and silently discards any typed input.

The original design intent was correct for progress/final payments (which have legitimate workflow semantics), but overshoots for the deposit case — users genuinely need to correct the original number.

## Design

### Behaviour

Editing the **Deposit paid** field in Step 3 of Edit Order is allowed. On Save Changes:

- If the field's value matches the order's current `depositPaid`, save proceeds silently (no-op for the deposit, other field edits still go through).
- If the value differs **and** the order already has at least one recorded payment, an AlertDialog ("Update deposit?") appears before persistence.
  - **Update** → proceeds with the replace and saves.
  - **Cancel** → dismisses the dialog, leaves the typed value in the field, no repository call.
- If the value differs **and** the order has no payments yet (first-time deposit entry), save proceeds silently.

The misleading supporting line *"Use Record Payment on the order details screen to update."* is removed; Record Payment remains the right tool for `PROGRESS` / `FINAL`, but the form no longer redirects users away from a valid action.

No changes to: the New Order flow, the Record Payment flow, Order Details display, or domain models.

### Data semantics

On save, payments are reconciled with a single rule (applies identically to create and edit):

```
nonDepositPayments = loadedPayments.filter { it.type != DEPOSIT }
newDeposit         = if (typedDeposit > 0)
                       listOf(Payment(type=DEPOSIT, amount=typedDeposit,
                                      method=OTHER, recordedAt=now, note=null))
                     else
                       emptyList()
order.payments     = nonDepositPayments + newDeposit
```

Consequences:

- All prior `DEPOSIT` payments are replaced by exactly one fresh `DEPOSIT` entry. Edit semantics match the user's "fix the figure I typed" mental model.
- `PROGRESS` and `FINAL` entries are preserved. Balance math continues to work.
- Clearing the field (typed value = 0) removes the deposit entirely.
- The new `Payment` uses `PaymentMethod.OTHER` and `recordedAt = now` — identical to the create-mode defaults today (`OrderFormViewModel.kt:473–483`). No new patterns introduced.

### Reconciliation dialog

A confirm-only AlertDialog gates destructive save in edit mode.

**Trigger** (both must hold):
1. `loadedPayments.isNotEmpty()`
2. `typedDeposit != currentDepositSum` where `currentDepositSum = loadedPayments.filter { it.type == DEPOSIT }.sumOf { it.amount }`

Comparison is done on integer naira (digits-only string) to avoid floating-point ambiguity.

Note: the dialog's "(₦{nonDepositTotal}) won't change" line is the sum of `PROGRESS` + `FINAL` payments — computed independently from `loadedPayments.filter { it.type != DEPOSIT }.sumOf { it.amount }`.

**Content:**

```
Update deposit?

Deposit will change from ₦{old} to ₦{new}.

Recorded progress/final payments (₦{nonDepositTotal}) won't change.
```

If `nonDepositTotal == 0`, the second sentence is omitted.

**Buttons:** `Cancel` (text) and `Update` (filled).

Strings live in `compose.resources` per project rules. No backslash escapes ([[feedback_strings_no_backslash_escape]]).

### State / Action plumbing

**`OrderFormState`** gains:

```kotlin
val depositReconciliationPrompt: DepositPrompt? = null

data class DepositPrompt(
    val oldAmount: Double,
    val newAmount: Double,
    val nonDepositTotal: Double,
)
```

Nullable single source of truth — non-null means dialog is visible, null means not.

**`OrderFormAction`** gains two cases:

```kotlin
data object ConfirmDepositChange : OrderFormAction
data object DismissDepositPrompt : OrderFormAction
```

**`OrderFormViewModel`:**

- In `loadOrder` (~line 296), change the `depositPaid` form-field initial value from `order.depositPaid.toLong().toString()` to `loadedPayments.filter { it.type == DEPOSIT }.sumOf { it.amount }.toLong().toString()`. This is the first-class fix for the naming caveat — the field now shows the deposit-only sum, matching the label.
- Split current `save()` into:
  - `save()` (entry point bound to `Action.Save`) — performs the dialog gate, sets the prompt and returns early when triggered; otherwise delegates to `executeSave()`.
  - `executeSave()` (private suspend) — contains the existing persistence work, calling the new unified payments-reconciliation rule.
- `onAction(ConfirmDepositChange)` clears the prompt and calls `executeSave()`.
- `onAction(DismissDepositPrompt)` clears the prompt; no save fires.

Replace the existing `payments = if (!isEdit && deposit > 0.0) {...} else if (isEdit) { loadedPayments } else { ... }` block in `executeSave()` with the unified rule above. Create and edit now share one path.

### UI plumbing

`OrderFormScreen.kt`:

- Remove `readOnly = state.isEditMode` and the conditional `supportingText` block on the deposit `OutlinedTextField` (~lines 1155–1174).
- Render an `AlertDialog` driven by `state.depositReconciliationPrompt != null`. `confirmButton` dispatches `Action.ConfirmDepositChange`; `dismissButton` dispatches `Action.DismissDepositPrompt`.
- The dialog composable should be small enough to live inline in `OrderFormScreen.kt` (or a sibling private composable in the same file) — no new file is warranted.

### Validation / edge cases

- **Deposit parsing:** `state.depositPaid` is already digits-only (the `onValueChange` filter strips non-digits, ~line 1158). Parse via `.toDoubleOrNull() ?: 0.0`.
- **Save gate:** Step 3's `canSave` predicate is unchanged. Empty deposit is and remains valid.
- **Cap against totalPrice:** explicitly out of scope. Today's create path accepts deposit > totalPrice silently; if that's a problem it warrants a separate ticket and would apply symmetrically to create + edit.
- **Offline:** `updateOrder` follows the Firestore set() fire-and-forget pattern ([[feedback_gitlive_firestore_set_awaits_server_ack]]). No new offline behaviour.
- **Dialog vs other changes:** if the user changed deposit AND deadline/notes/priority/etc., the dialog still gates only on the deposit change. On Cancel, the other changes remain in state — the user can press Save again, choose Update, and everything saves together.

## Testing

### Unit tests (`OrderFormViewModelTest`)

1. `save_inEditMode_withDepositChanged_andPaymentsRecorded_showsPromptAndDoesNotPersist`
2. `confirmDepositChange_replacesDepositPaymentsAndPersists`
3. `dismissDepositPrompt_clearsPromptAndKeepsTypedDepositValue_noRepoCall`
4. `save_inEditMode_withDepositChanged_andNoPayments_persistsSilently`
5. `save_inEditMode_withDepositUnchanged_persistsSilentlyEvenWithPayments`
6. `save_inEditMode_replacesAllDepositPayments_preservesProgressAndFinal`
7. `save_inEditMode_withClearedDeposit_removesAllDepositPayments_keepsOthers`
8. `save_inCreateMode_unchangedBehaviour_singleDepositPaymentForPositiveValue` (regression guard)
9. `loadOrder_populatesDepositPaidFieldFromDepositPaymentsOnly` (regression guard for the naming-caveat fix — order with DEPOSIT ₦500 + PROGRESS ₦200 loads the field as "500", not "700")

Patterns per [[android-testing]] — JUnit5, Turbine, AssertK, `UnconfinedTestDispatcher`, fake repository.

### Smoke tests (manual; iOS + Android sims)

Per [[feedback_qa_smoke_tests]] each PR ships a smoke checklist.

1. Create order without deposit → Edit Order → type `1000` → Save → Order Details shows ₦1,000 deposit and ₦(total − 1000) balance.
2. Create order with deposit `500` → Edit Order → field shows `500`, change to `750` → Save → no dialog (no other payments) → Order Details shows ₦750.
3. Same as 2 → Record Payment on Order Details (PROGRESS ₦200) → Edit Order → change deposit `750` → `1000` → Save → dialog appears with "from ₦750 to ₦1,000" and "(₦200) won't change" → Update → Order Details shows deposit ₦1,000, balance reduced by total ₦1,200 paid.
4. Same scenario as 3 but press Cancel on the dialog → no save fired → reopen Edit Order if needed, the typed value `1,000` is still there → press Save again → Update → persists.
5. Edit Order → clear the deposit field on an order with PROGRESS ₦200 already recorded → Save → dialog says "from ₦750 to ₦0" → Update → Order Details shows deposit ₦0, PROGRESS ₦200 still listed.
6. iOS parity: repeat 3 + 4 + 5 on iPhone 17 sim.

## Out of scope

- Capping deposit against totalPrice.
- Editing PROGRESS / FINAL payments inline (Record Payment still owns those).
- Showing per-payment history on the Edit Order screen.
- Migration of any legacy `depositPaid` field — already done previously per Order.kt:64–68 comments.

## Touched files

- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml` (new strings for dialog title/body/buttons)
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`
