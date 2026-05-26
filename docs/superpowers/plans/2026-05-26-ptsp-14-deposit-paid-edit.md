# PTSP-14 — Editable Deposit Paid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unlock the read-only "Deposit paid" field in the Edit Order flow and reconcile user-edited deposits with the payments audit trail via a confirm-only dialog when prior payments exist.

**Architecture:** Extract the payments-reconciliation math into a pure object (`DepositReconciler`) so create and edit share one rule. ViewModel splits `save()` into a gate (which may set a typed `DepositPrompt` state and return early) and `executeSave()` (which calls the reconciler and persists). UI removes the read-only flag and renders an `AlertDialog` driven from state.

**Tech Stack:** Kotlin Multiplatform · Compose Multiplatform · MVI · Koin · kotlin.test + UnconfinedTestDispatcher · Material3 AlertDialog.

**Spec:** `docs/superpowers/specs/2026-05-26-ptsp-14-deposit-paid-edit-design.md`

---

## File Map

**New files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconciler.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconcilerTest.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`

**Modified files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

**Responsibilities:**
- `DepositReconciler.kt` — Pure functions: compute deposit-only sum, non-deposit total, and build the replacement payments list.
- `OrderFormState.kt` — Add nullable `depositReconciliationPrompt` (typed `DepositPrompt` data class).
- `OrderFormAction.kt` — Add `OnConfirmDepositChange`, `OnDismissDepositPrompt`.
- `OrderFormViewModel.kt` — Fix `loadOrder` to populate the field from deposit-only sum; split `save()` → gate + `executeSave()`; route both Save and ConfirmDepositChange through the reconciler.
- `OrderFormScreen.kt` — Drop `readOnly` + conditional hint; render the dialog.
- `strings.xml` — Add dialog strings, delete the now-unused `order_form_deposit_edit_locked_hint`.

---

## Task 1: `DepositReconciler` pure object + tests (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconciler.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconcilerTest.kt`

Pattern follows `feature/order/presentation/detail/PaymentMath.kt` + `PaymentMathTest.kt` — pure object holding math, comprehensive unit tests.

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconcilerTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepositReconcilerTest {

    private fun payment(
        id: String,
        amount: Double,
        type: PaymentType,
        recordedAt: Long = 0L,
    ) = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = type,
        recordedAt = recordedAt,
        note = null,
    )

    @Test
    fun currentDepositSum_filtersToDepositTypeOnly() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        assertEquals(500.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun currentDepositSum_returnsZeroWhenNoDeposits() {
        val loaded = listOf(payment("a", 200.0, PaymentType.PROGRESS))

        assertEquals(0.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun currentDepositSum_returnsZeroWhenEmpty() {
        assertEquals(0.0, DepositReconciler.currentDepositSum(emptyList()))
    }

    @Test
    fun currentDepositSum_sumsMultipleDeposits() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 250.0, PaymentType.DEPOSIT),
        )

        assertEquals(750.0, DepositReconciler.currentDepositSum(loaded))
    }

    @Test
    fun nonDepositTotal_sumsProgressAndFinalOnly() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        assertEquals(500.0, DepositReconciler.nonDepositTotal(loaded))
    }

    @Test
    fun nonDepositTotal_returnsZeroWhenOnlyDeposits() {
        val loaded = listOf(payment("a", 500.0, PaymentType.DEPOSIT))

        assertEquals(0.0, DepositReconciler.nonDepositTotal(loaded))
    }

    @Test
    fun reconcileForDeposit_replacesAllDepositPaymentsAndPreservesOthers() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT, recordedAt = 100L),
            payment("b", 200.0, PaymentType.PROGRESS, recordedAt = 200L),
            payment("c", 100.0, PaymentType.DEPOSIT, recordedAt = 300L),
        )

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = 750.0,
            recordedAt = 999L,
            newPaymentId = "new-id",
        )

        assertEquals(2, result.size)
        val progress = result.single { it.type == PaymentType.PROGRESS }
        assertEquals(200.0, progress.amount)
        assertEquals(200L, progress.recordedAt)
        val deposit = result.single { it.type == PaymentType.DEPOSIT }
        assertEquals(750.0, deposit.amount)
        assertEquals(999L, deposit.recordedAt)
        assertEquals("new-id", deposit.id)
        assertEquals(PaymentMethod.OTHER, deposit.method)
    }

    @Test
    fun reconcileForDeposit_withZeroDepositRemovesAllDepositPaymentsButKeepsOthers() {
        val loaded = listOf(
            payment("a", 500.0, PaymentType.DEPOSIT),
            payment("b", 200.0, PaymentType.PROGRESS),
            payment("c", 300.0, PaymentType.FINAL),
        )

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = 0.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertEquals(2, result.size)
        assertTrue(result.none { it.type == PaymentType.DEPOSIT })
        assertEquals(200.0, result.single { it.type == PaymentType.PROGRESS }.amount)
        assertEquals(300.0, result.single { it.type == PaymentType.FINAL }.amount)
    }

    @Test
    fun reconcileForDeposit_withEmptyLoadedAndPositiveDepositReturnsSingleDeposit() {
        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = emptyList(),
            newDeposit = 1000.0,
            recordedAt = 999L,
            newPaymentId = "fresh",
        )

        assertEquals(1, result.size)
        val deposit = result.single()
        assertEquals(PaymentType.DEPOSIT, deposit.type)
        assertEquals(1000.0, deposit.amount)
        assertEquals(999L, deposit.recordedAt)
        assertEquals("fresh", deposit.id)
    }

    @Test
    fun reconcileForDeposit_withEmptyLoadedAndZeroDepositReturnsEmptyList() {
        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = emptyList(),
            newDeposit = 0.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun reconcileForDeposit_withNegativeDepositTreatedAsZero() {
        val loaded = listOf(payment("a", 500.0, PaymentType.DEPOSIT))

        val result = DepositReconciler.reconcileForDeposit(
            loadedPayments = loaded,
            newDeposit = -50.0,
            recordedAt = 999L,
            newPaymentId = "unused",
        )

        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:compileTestKotlinDesktop`
Expected: FAIL with "Unresolved reference: DepositReconciler"

- [ ] **Step 3: Implement `DepositReconciler`**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconciler.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType

/**
 * Pure reconciliation rules for the "Deposit paid" form field.
 *
 * The form treats deposit as a single editable value, but the underlying
 * model keeps a typed [Payment] list. Editing the form value replaces all
 * DEPOSIT entries with at most one fresh DEPOSIT; PROGRESS and FINAL
 * entries are preserved untouched.
 */
object DepositReconciler {

    /** Sum of just the DEPOSIT-typed payments — what the form field should display. */
    fun currentDepositSum(loadedPayments: List<Payment>): Double =
        loadedPayments.filter { it.type == PaymentType.DEPOSIT }.sumOf { it.amount }

    /** Sum of PROGRESS + FINAL payments — surfaced in the reconciliation dialog. */
    fun nonDepositTotal(loadedPayments: List<Payment>): Double =
        loadedPayments.filter { it.type != PaymentType.DEPOSIT }.sumOf { it.amount }

    /**
     * Returns the payments list to persist after the user has set the form's
     * "Deposit paid" value to [newDeposit].
     *
     * All existing DEPOSIT payments are dropped; if [newDeposit] is positive a
     * single fresh DEPOSIT is appended. Non-DEPOSIT payments pass through unchanged.
     */
    fun reconcileForDeposit(
        loadedPayments: List<Payment>,
        newDeposit: Double,
        recordedAt: Long,
        newPaymentId: String,
    ): List<Payment> {
        val nonDeposit = loadedPayments.filter { it.type != PaymentType.DEPOSIT }
        val freshDeposit = if (newDeposit > 0.0) {
            listOf(
                Payment(
                    id = newPaymentId,
                    amount = newDeposit,
                    method = PaymentMethod.OTHER,
                    type = PaymentType.DEPOSIT,
                    recordedAt = recordedAt,
                    note = null,
                ),
            )
        } else {
            emptyList()
        }
        return nonDeposit + freshDeposit
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.feature.order.domain.DepositReconcilerTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Run detekt**

Run: `./gradlew detekt`
Expected: PASS. (Use `/format` skill if any KtLint issues.)

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconciler.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DepositReconcilerTest.kt
git commit -m "$(cat <<'EOF'
feat(orders): add DepositReconciler for editable deposit (PTSP-14)

Pure-function object that replaces all DEPOSIT-typed payments with one
fresh entry on edit, while preserving PROGRESS/FINAL payments untouched.
Math lives in feature/order/domain so create and edit can share one
rule and the ViewModel stays thin.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: State + Action additions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`

- [ ] **Step 1: Add `DepositPrompt` + state field**

Edit `OrderFormState.kt`. Add the new field to the `OrderFormState` data class right after `notes`, and append the new data class to the bottom of the file:

```kotlin
data class OrderFormState(
    val currentStep: Int = 1,
    val isEditMode: Boolean = false,
    // Step 1 - Customer
    val customers: List<Customer> = emptyList(),
    val customerSearchQuery: String = "",
    val selectedCustomer: Customer? = null,
    // Step 2 - Items
    val items: List<OrderItemFormState> = listOf(OrderItemFormState()),
    val availableStyles: List<Style> = emptyList(),
    val availableMeasurements: List<Measurement> = emptyList(),
    // Step 3 - Details
    val deadline: Long? = null,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val depositPaid: String = "",
    val notes: String = "",
    val depositReconciliationPrompt: DepositPrompt? = null,
    // General
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
)
```

Add the nested data class outside `OrderFormState`, before `OrderItemFormState`:

```kotlin
/**
 * State driving the AlertDialog that gates a deposit-changing save when
 * the order already has at least one recorded payment. Non-null means
 * the dialog is visible; [oldAmount] and [newAmount] are integer-naira
 * doubles surfaced in the dialog body.
 */
data class DepositPrompt(
    val oldAmount: Double,
    val newAmount: Double,
    val nonDepositTotal: Double,
)
```

- [ ] **Step 2: Add new actions**

Edit `OrderFormAction.kt`. Append two cases under the `// Save` section:

```kotlin
    // Save
    data object OnSave : OrderFormAction
    data object OnConfirmDepositChange : OrderFormAction
    data object OnDismissDepositPrompt : OrderFormAction
    data object OnErrorDismiss : OrderFormAction
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :composeApp:compileKotlinDesktop`
Expected: PASS. (The ViewModel `when` block on `OrderFormAction` will fail at runtime if the new cases aren't handled, but won't fail compilation since `OrderFormAction` is a sealed `interface` and `when` is fed via `onAction` — exhaustive checking happens via the `else`-free `when`. We'll add handlers in Task 3.)

> If the compiler instead errors with `'when' expression must be exhaustive`, add temporary stubs in Task 3's step 4 first to compile cleanly.

- [ ] **Step 4: No commit yet** — state and actions without VM/UI wiring are dead code. Commit in Task 3.

---

## Task 3: ViewModel — load-time fix + save() split + reconciler integration + tests (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`

- [ ] **Step 1: Write the failing VM tests**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository

    private val testCustomer = Customer(
        id = "cust-1",
        userId = "user-1",
        name = "Test Customer",
        phone = "08012345678",
    )

    private val testUser = User(
        id = "user-1",
        email = "test@stitchpad.app",
        displayName = "Test",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        styleRepository = FakeStyleRepository()
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository().apply { currentUser = testUser }
        customerRepository.customersList = listOf(testCustomer)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(orderId: String? = null): OrderFormViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            if (orderId != null) set("orderId", orderId)
        }
        val vm = OrderFormViewModel(
            savedStateHandle = savedStateHandle,
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            styleRepository = styleRepository,
            measurementRepository = measurementRepository,
            authRepository = authRepository,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun seedOrder(
        id: String = "order-1",
        payments: List<Payment> = emptyList(),
        priority: OrderPriority = OrderPriority.NORMAL,
    ): Order {
        val order = Order(
            id = id,
            userId = "user-1",
            customerId = testCustomer.id,
            customerName = testCustomer.name,
            items = listOf(
                OrderItem(
                    id = "item-1",
                    garmentType = GarmentType.AGBADA,
                    description = "Demo",
                    price = 5_000.0,
                ),
            ),
            status = OrderStatus.PENDING,
            priority = priority,
            statusHistory = listOf(StatusChange(OrderStatus.PENDING, 0L)),
            totalPrice = 5_000.0,
            payments = payments,
            deadline = null,
            notes = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        orderRepository.ordersList = listOf(order)
        return order
    }

    private fun depositPayment(amount: Double, id: String = "p-dep"): Payment = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = 0L,
        note = null,
    )

    private fun progressPayment(amount: Double, id: String = "p-prog"): Payment = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.PROGRESS,
        recordedAt = 0L,
        note = null,
    )

    @Test
    fun loadOrder_populatesDepositPaidFieldFromDepositPaymentsOnly() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))

        val vm = createViewModel(orderId = "order-1")

        assertEquals("500", vm.state.value.depositPaid)
    }

    @Test
    fun save_inEditMode_withDepositChanged_andPaymentsRecorded_showsPromptAndDoesNotPersist() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)

        val prompt = vm.state.value.depositReconciliationPrompt
        assertNotNull(prompt)
        assertEquals(500.0, prompt.oldAmount)
        assertEquals(750.0, prompt.newAmount)
        assertEquals(200.0, prompt.nonDepositTotal)
        assertNull(orderRepository.lastUpdatedOrder)
    }

    @Test
    fun confirmDepositChange_clearsPromptAndReplacesDepositPayments() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)
        vm.onAction(OrderFormAction.OnConfirmDepositChange)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(2, updated.payments.size)
        assertEquals(750.0, updated.payments.single { it.type == PaymentType.DEPOSIT }.amount)
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun dismissDepositPrompt_clearsPromptAndKeepsTypedDepositValue_noRepoCall() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)
        vm.onAction(OrderFormAction.OnDismissDepositPrompt)

        assertNull(vm.state.value.depositReconciliationPrompt)
        assertEquals("750", vm.state.value.depositPaid)
        assertNull(orderRepository.lastUpdatedOrder)
    }

    @Test
    fun save_inEditMode_withDepositChanged_andNoPayments_persistsSilentlyWithoutPrompt() = runTest {
        seedOrder(payments = emptyList())
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("1000"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(1, updated.payments.size)
        assertEquals(1_000.0, updated.payments.single().amount)
        assertEquals(PaymentType.DEPOSIT, updated.payments.single().type)
    }

    @Test
    fun save_inEditMode_withDepositUnchanged_persistsSilentlyEvenWithPayments() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        // Deposit field already populated to "500" by loadOrder; change priority instead.
        vm.onAction(OrderFormAction.OnPriorityChange(OrderPriority.RUSH))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(OrderPriority.RUSH, updated.priority)
        // Payments are preserved exactly because deposit didn't change.
        assertEquals(500.0, updated.payments.single { it.type == PaymentType.DEPOSIT }.amount)
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun save_inEditMode_withClearedDeposit_promptsThenConfirmRemovesDepositKeepsOthers() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange(""))
        vm.onAction(OrderFormAction.OnSave)
        val prompt = vm.state.value.depositReconciliationPrompt
        assertNotNull(prompt)
        assertEquals(500.0, prompt.oldAmount)
        assertEquals(0.0, prompt.newAmount)

        vm.onAction(OrderFormAction.OnConfirmDepositChange)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertTrue(updated.payments.none { it.type == PaymentType.DEPOSIT })
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun save_inCreateMode_withPositiveDeposit_persistsSingleDepositPaymentAndNoPrompt() = runTest {
        // No orderId on SavedStateHandle → create mode.
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        // The default item id is generated by OrderItemFormState; set fields on the first item.
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnDepositChange("2000"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(1, created.payments.size)
        assertEquals(2_000.0, created.payments.single().amount)
        assertEquals(PaymentType.DEPOSIT, created.payments.single().type)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModelTest"`
Expected: FAIL — multiple tests fail because `OnConfirmDepositChange`/`OnDismissDepositPrompt` aren't handled in the VM `when` and `depositReconciliationPrompt` isn't being set.

- [ ] **Step 3: Patch `loadOrder` to populate field from deposit-only sum**

In `OrderFormViewModel.kt`, add an import for the reconciler at the top:

```kotlin
import com.danzucker.stitchpad.feature.order.domain.DepositReconciler
```

Find this block in `loadOrder` (~lines 240–252):

```kotlin
                    loadedPayments = order.payments
                    pendingCustomerId = order.customerId
                    _state.update {
                        it.copy(
                            items = order.items.map { item -> item.toFormState() },
                            deadline = order.deadline,
                            priority = order.priority,
                            depositPaid = if (order.depositPaid > 0) order.depositPaid.toLong().toString() else "",
                            notes = order.notes ?: "",
                            isLoading = false
                        )
                    }
```

Replace the `depositPaid =` line with:

```kotlin
                            depositPaid = DepositReconciler
                                .currentDepositSum(order.payments)
                                .takeIf { it > 0.0 }
                                ?.toLong()
                                ?.toString()
                                ?: "",
```

- [ ] **Step 4: Add new action handlers and split `save()` into gate + `executeSave()`**

In `onAction` (~line 159), replace this region:

```kotlin
            OrderFormAction.OnSave -> save()
            OrderFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
```

with:

```kotlin
            OrderFormAction.OnSave -> save()
            OrderFormAction.OnConfirmDepositChange -> {
                _state.update { it.copy(depositReconciliationPrompt = null) }
                executeSave()
            }
            OrderFormAction.OnDismissDepositPrompt -> {
                _state.update { it.copy(depositReconciliationPrompt = null) }
            }
            OrderFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
```

Now refactor `save()`. Find the existing `private fun save() { ... }` block (~lines 311–414) and replace the whole function with two functions:

```kotlin
    @Suppress("ReturnCount")
    private fun save() {
        val s = _state.value
        if (userId == null) return

        val customer = s.selectedCustomer
        if (customer == null) {
            setError(Res.string.error_order_customer_required)
            return
        }

        val formItems = s.items.filter { it.garmentType != null }
        if (formItems.isEmpty()) {
            setError(Res.string.error_order_items_required)
            return
        }

        val hasInvalidPrice = formItems.any { (it.price.toDoubleOrNull() ?: 0.0) <= 0.0 }
        if (hasInvalidPrice) {
            setError(Res.string.error_order_item_price_required)
            return
        }

        // Gate: in edit mode, if the user changed the deposit AND any payments
        // already exist on the order, intercept the save with a confirmation
        // prompt so the destructive replace is explicit.
        val isEdit = orderId != null
        if (isEdit && loadedPayments.isNotEmpty()) {
            val typedDeposit = s.depositPaid.toDoubleOrNull() ?: 0.0
            val currentDeposit = DepositReconciler.currentDepositSum(loadedPayments)
            if (typedDeposit != currentDeposit) {
                _state.update {
                    it.copy(
                        depositReconciliationPrompt = DepositPrompt(
                            oldAmount = currentDeposit,
                            newAmount = typedDeposit,
                            nonDepositTotal = DepositReconciler.nonDepositTotal(loadedPayments),
                        ),
                    )
                }
                return
            }
        }

        executeSave()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("LongMethod")
    private fun executeSave() {
        val s = _state.value
        val uid = userId ?: return
        val customer = s.selectedCustomer ?: return
        val formItems = s.items.filter { it.garmentType != null }
        if (formItems.isEmpty()) return

        val actualOrderId = orderId ?: orderRepository.newOrderId(uid)

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }

            val orderItems = formItems.map { item ->
                val garmentType = item.garmentType!!
                val price = item.price.toDoubleOrNull() ?: 0.0
                val (fabricUrl, fabricPath) = uploadFabricPhotoIfNeeded(uid, actualOrderId, item)
                OrderItem(
                    id = item.id,
                    garmentType = garmentType,
                    description = item.description.trim(),
                    price = price,
                    styleId = item.styleId,
                    measurementId = item.measurementId,
                    fabricPhotoUrl = fabricUrl,
                    fabricPhotoStoragePath = fabricPath,
                    fabricName = item.fabricName.trim().ifBlank { null },
                )
            }

            val totalPrice = orderItems.sumOf { it.price }
            val deposit = s.depositPaid.toDoubleOrNull() ?: 0.0
            val now = Clock.System.now().toEpochMilliseconds()
            val isEdit = orderId != null

            val payments = DepositReconciler.reconcileForDeposit(
                loadedPayments = if (isEdit) loadedPayments else emptyList(),
                newDeposit = deposit,
                recordedAt = now,
                newPaymentId = Uuid.random().toString(),
            )

            val order = Order(
                id = actualOrderId,
                userId = uid,
                customerId = customer.id,
                customerName = customer.name,
                items = orderItems,
                status = if (isEdit) loadedStatus else OrderStatus.PENDING,
                priority = s.priority,
                statusHistory = if (isEdit) {
                    loadedStatusHistory
                } else {
                    listOf(StatusChange(OrderStatus.PENDING, now))
                },
                totalPrice = totalPrice,
                payments = payments,
                deadline = s.deadline,
                notes = s.notes.trim().ifBlank { null },
                createdAt = if (isEdit) loadedCreatedAt else 0L,
                updatedAt = 0L,
            )

            val result = if (isEdit) {
                orderRepository.updateOrder(uid, order)
            } else {
                orderRepository.createOrder(uid, order)
            }
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> _events.send(OrderFormEvent.OrderSaved)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }
```

Remove the now-unused imports — specifically the inline `Payment`, `PaymentMethod`, `PaymentType` references inside `save()` are replaced by `DepositReconciler`, but the imports themselves can stay (the test file uses the same models). Don't remove `import com.danzucker.stitchpad.core.domain.model.Payment` etc. unless the file's lint complains.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModelTest"`
Expected: PASS, 7 tests.

If `save_inCreateMode_withPositiveDeposit_...` is flaky because `observeCustomers` hasn't emitted yet when the actions fire, ensure `customerRepository.customersList = listOf(testCustomer)` is set in `@BeforeTest` (it is). The `UnconfinedTestDispatcher` will run the customer-observer emit synchronously.

- [ ] **Step 6: Run full test suite + detekt**

Run: `./gradlew :composeApp:desktopTest detekt`
Expected: PASS. No regressions in other VMs/tests.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(orders): edit deposit in Edit Order with reconciliation gate (PTSP-14)

OrderFormViewModel now allows the deposit field to round-trip through
edit mode. save() gates a destructive replace behind a typed
DepositPrompt state when prior payments exist; executeSave() routes
both create and edit through DepositReconciler. loadOrder populates
the field from the deposit-only sum so the label matches the value.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: UI — unlock field, render dialog, swap strings

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add new strings**

Edit `composeApp/src/commonMain/composeResources/values/strings.xml`. Locate the existing entries (line ~547 area):

```xml
    <string name="order_form_deposit_edit_locked_hint">Use Record Payment on the order details screen to update.</string>
```

Delete that line entirely. Add (in the same general area, before `</resources>` is fine):

```xml
    <string name="order_form_deposit_dialog_title">Update deposit?</string>
    <string name="order_form_deposit_dialog_body">Deposit will change from ₦%1$s to ₦%2$s.</string>
    <string name="order_form_deposit_dialog_body_with_other_payments">Recorded progress/final payments (₦%1$s) won&apos;t change.</string>
    <string name="order_form_deposit_dialog_confirm">Update</string>
    <string name="order_form_deposit_dialog_cancel">Cancel</string>
```

Note: use `&apos;` not `\'` per the project's [[feedback_strings_no_backslash_escape]] convention (Compose iOS renders backslash escapes literally).

- [ ] **Step 2: Unlock the deposit field**

Edit `OrderFormScreen.kt`. Find the deposit `OutlinedTextField` block (~lines 1160–1179) and remove the `readOnly` line and the `supportingText` block. The block should look like this afterwards:

```kotlin
        // Deposit
        OutlinedTextField(
            value = state.depositPaid,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                onAction(OrderFormAction.OnDepositChange(digits))
            },
            visualTransformation = ThousandsSeparatorTransformation,
            label = { Text(stringResource(Res.string.order_form_deposit_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_deposit_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth()
        )
```

- [ ] **Step 3: Add the dialog imports**

At the top of `OrderFormScreen.kt`, add (in alphabetical position among existing material3 imports):

```kotlin
import androidx.compose.material3.AlertDialog
```

Add (in alphabetical position among existing resource imports near the bottom of the import block) the new generated string references:

```kotlin
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_body
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_body_with_other_payments
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_cancel
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_confirm
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_title
```

And remove the now-unused import:

```kotlin
import stitchpad.composeapp.generated.resources.order_form_deposit_edit_locked_hint
```

(If your IDE keeps imports tidy, this happens automatically. Otherwise grep the file with `grep -n order_form_deposit_edit_locked_hint OrderFormScreen.kt` after the change and remove any straggler.)

- [ ] **Step 4: Render the AlertDialog**

The `DetailsStep` composable currently ends with a `CustomDatePickerDialog` `if` block (~line 1197). Add a sibling `if` block right after it (still inside `DetailsStep`'s outer scope — same scope as the date picker dialog):

```kotlin
    state.depositReconciliationPrompt?.let { prompt ->
        DepositReconciliationDialog(
            prompt = prompt,
            onConfirm = { onAction(OrderFormAction.OnConfirmDepositChange) },
            onDismiss = { onAction(OrderFormAction.OnDismissDepositPrompt) },
        )
    }
```

Add the dialog composable as a private composable at the bottom of the file, before the previews (after `garmentGenderLabel`):

```kotlin
@Composable
private fun DepositReconciliationDialog(
    prompt: DepositPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.order_form_deposit_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        Res.string.order_form_deposit_dialog_body,
                        prompt.oldAmount.toLong().toString(),
                        prompt.newAmount.toLong().toString(),
                    ),
                )
                if (prompt.nonDepositTotal > 0.0) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    Text(
                        text = stringResource(
                            Res.string.order_form_deposit_dialog_body_with_other_payments,
                            prompt.nonDepositTotal.toLong().toString(),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.order_form_deposit_dialog_confirm),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.order_form_deposit_dialog_cancel))
            }
        },
    )
}
```

- [ ] **Step 5: Compile Android + iOS**

Run:
```bash
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64
```

Expected: PASS on all four. iOS compile is per [[feedback_kmp_jvm_only_apis]] — always check before declaring done.

- [ ] **Step 6: Run detekt**

Run: `./gradlew detekt`
Expected: PASS. (Apply the `/format` skill if there are KtLint findings.)

- [ ] **Step 7: Manual smoke test (Android sim)**

Boot the Android sim, sign in as Fola (per [[reference_test_environment]]), and walk through:

1. Create order WITHOUT deposit → Order Details → Edit Order → Step 3 → field is empty + editable → type `1000` → Save Changes → no dialog → Order Details shows ₦1,000 paid.
2. Create order WITH deposit `500` → Edit Order → field shows `500` → change to `750` → Save → no dialog (no other payments) → Order Details shows ₦750.
3. Same as 2 → Record Payment on Order Details → PROGRESS ₦200 → Edit Order → field shows `500` (deposit only, not 700) → change to `1000` → Save → dialog appears reading "from ₦500 to ₦1,000" and "(₦200) won't change" → Update → Order Details shows ₦1,000 deposit + ₦200 progress.
4. Repeat 3, but press Cancel on the dialog → no save fires → field still shows `1,000` → Save → Update → persists.
5. Edit Order on an order with deposit ₦500 + PROGRESS ₦200 → clear the field → Save → dialog reads "from ₦500 to ₦0" + "(₦200) won't change" → Update → Order Details shows ₦0 deposit, ₦200 progress preserved.

- [ ] **Step 8: Manual smoke test (iOS sim)**

Per [[reference_test_environment]] iPhone 17 Pro sim — repeat scenarios 3, 4, and 5 to verify dialog renders correctly with `₦` symbol + `&apos;` HTML entity rendering as `'` (not literal backslash-apostrophe).

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(orders): unlock deposit field + render reconciliation dialog (PTSP-14)

OrderFormScreen drops readOnly + locked-hint on the deposit field and
renders an AlertDialog when state.depositReconciliationPrompt is set.
Removes the now-unused order_form_deposit_edit_locked_hint string and
adds the dialog strings (apos-escaped per project rule).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Push + PR

- [ ] **Step 1: Push branch**

```bash
git push -u origin feature/ptsp-14-deposit-paid-edit
```

- [ ] **Step 2: Open PR**

Run via gh:

```bash
gh pr create --title "feat(orders): editable deposit paid in Edit Order (PTSP-14)" --body "$(cat <<'EOF'
## Summary
- Editable Deposit paid field in Edit Order; helper line dropped
- Confirm-only AlertDialog gates the destructive replace when the order already has any recorded payments
- New `DepositReconciler` pure object holds the replace-all-deposits rule; create and edit now share one persistence path
- `loadOrder` populates the field from the deposit-only sum so the label "Deposit paid" matches what's shown

Closes PTSP-14.

## Test plan
- [x] `DepositReconcilerTest` — 10 cases
- [x] `OrderFormViewModelTest` — 7 cases covering load, gate, confirm, dismiss, no-payment fast path, unchanged-deposit fast path, create-mode regression
- [x] Detekt clean
- [x] Android compile + iOS Arm64/SimulatorArm64 compile
- [ ] Manual smoke (Android sim): no-deposit → add via Edit Order
- [ ] Manual smoke (Android sim): edit deposit with no other payments → no dialog
- [ ] Manual smoke (Android sim): edit deposit with PROGRESS recorded → dialog appears with correct old/new/other figures
- [ ] Manual smoke (Android sim): cancel dialog → no persist, value retained
- [ ] Manual smoke (Android sim): clear deposit with PROGRESS recorded → dialog → confirm → only PROGRESS remains
- [ ] Manual smoke (iOS sim): dialog renders apostrophe correctly, ₦ symbol intact, scenarios 3/4/5 repeat

## Review rotation
Per [[feedback_review_rotation]]: kick off Cursor review + `codex review` once CI is green.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Trigger reviews**

After PR is open and CI starts:
- Cursor review (per [[feedback_cursor_review_patterns]]) — anticipate cross-cutting catches around the new dialog's plural grammar and the deposit display vs persist divergence.
- `codex review` — second-opinion bug catcher.

---

## Out of scope (do NOT do)

- Renaming `Order.depositPaid` (still misnamed; sums all payments, not just deposits). Documented in spec under "Naming caveat".
- Capping deposit against `totalPrice`. Separate ticket if desired.
- Editing PROGRESS/FINAL payments inline.
- Refactoring `Clock.System` to injectable `nowMillis: () -> Long` on the VM. The reconciler is already deterministic via its `recordedAt` parameter, which is the only place we need control for tests.
