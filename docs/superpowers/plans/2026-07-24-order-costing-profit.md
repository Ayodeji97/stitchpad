# Order Costing & Profit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a tailor record categorized costs against an order and see real profit — per order and (Pro) in Reports — using their own real numbers.

**Architecture:** Add a `costs: List<OrderCost>` field to the `Order` domain model with derived profit helpers; round-trip it through `OrderDto`/`OrderMapper`; persist via a new targeted `updateCosts(...)` repository write (mirrors `updateNotes`/`recordPayment`, stays inside the `serverCreatedAt` Lane-B rules). Surface it in a new private `OrderCostsCard` + `CostsEditorSheet` in order detail, and as a Pro-gated Profit KPI tile in Reports. Costs are never added to any receipt/share path.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, GitLive Firebase (Firestore), kotlin.test + Turbine + AssertK, JUnit5.

Design spec: `docs/superpowers/specs/2026-07-24-order-costing-profit-design.md`.

## Global Constraints

- **Package:** `com.danzucker.stitchpad`. Layers: domain → data → presentation. Name implementations descriptively (no `Impl`).
- **Result<T,E>:** never throw for expected failures; repository methods return `EmptyResult<DataError.Network>`.
- **MVI:** every screen change goes through State/Action/Event + ViewModel; no business logic in composables; all state in ViewModel.
- **Strings:** no hardcoded user-facing text — use `compose.resources` `Res.string.*`. Positional args only (`%1$d`, `%2$d`). No backslash escapes in `strings.xml` — use `&apos;` or `’`.
- **Firestore write rule:** `updateCosts` MUST be a targeted `.update("costs" to …, "updatedAt" to now)` wrapped in `offlineWrites.enqueue { … }`. It MUST NOT write `serverCreatedAt` or `createdAt`. Firestore-map keys MUST match `OrderCostDto` `@Serializable` field names so snapshot reads round-trip.
- **Privacy:** costs/profit must never be added to `core/sharing/ReceiptData.kt`, `ReceiptFormatter.kt`, or any share/WhatsApp path.
- **iOS parity:** no JVM-only APIs (e.g. `String.format`). Compile iOS before declaring done: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
- **Test names:** backtick names use letters/digits/spaces/hyphens ONLY. Gate compile on `:compileTestKotlinIosSimulatorArm64`.
- **Every `@Composable` Screen/card gets a `@Preview`** in light AND dark. Detekt: previews may need `@file:Suppress("TooManyFunctions")`.
- **Category set (fixed, 6):** `FABRIC`, `MATERIALS_TRIMS`, `EMBELLISHMENT`, `LABOUR`, `LOGISTICS`, `OTHER`.

---

### Task 1: Domain model — `CostCategory`, `OrderCost`, `Order` profit helpers

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderCostingTest.kt`

**Interfaces:**
- Produces: `enum class CostCategory { FABRIC, MATERIALS_TRIMS, EMBELLISHMENT, LABOUR, LOGISTICS, OTHER }`; `data class OrderCost(category: CostCategory, amount: Double, note: String? = null)`; on `Order`: `costs: List<OrderCost>`, `val totalCost: Double`, `val profit: Double`, `val profitMargin: Double?`, `val hasCosts: Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.isFalse
import kotlin.test.Test

class OrderCostingTest {

    private fun order(
        totalPrice: Double = 50_000.0,
        discount: Double = 0.0,
        costs: List<OrderCost> = emptyList(),
    ) = Order(
        id = "o1", userId = "u1", customerId = "c1", customerName = "Amaka",
        items = emptyList(), status = OrderStatus.PENDING, priority = OrderPriority.NORMAL,
        statusHistory = emptyList(), totalPrice = totalPrice, discount = discount,
        costs = costs, deadline = null, notes = null, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `totalCost sums all cost amounts`() {
        val o = order(costs = listOf(
            OrderCost(CostCategory.FABRIC, 25_000.0),
            OrderCost(CostCategory.LABOUR, 7_000.0),
        ))
        assertThat(o.totalCost).isEqualTo(32_000.0)
    }

    @Test
    fun `profit is payableTotal minus totalCost`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)))
        assertThat(o.profit).isEqualTo(6_000.0)
    }

    @Test
    fun `profit uses payableTotal net of discount`() {
        val o = order(totalPrice = 50_000.0, discount = 10_000.0,
            costs = listOf(OrderCost(CostCategory.FABRIC, 30_000.0)))
        assertThat(o.profit).isEqualTo(10_000.0) // (50k-10k) - 30k
    }

    @Test
    fun `profit is negative when costs exceed payable`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 58_000.0)))
        assertThat(o.profit).isEqualTo(-8_000.0)
    }

    @Test
    fun `profitMargin is profit over payableTotal`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)))
        assertThat(o.profitMargin).isEqualTo(6_000.0 / 50_000.0)
    }

    @Test
    fun `profitMargin is null when payableTotal is zero`() {
        val o = order(totalPrice = 0.0, costs = listOf(OrderCost(CostCategory.FABRIC, 1_000.0)))
        assertThat(o.profitMargin).isNull()
    }

    @Test
    fun `hasCosts reflects presence of cost lines`() {
        assertThat(order().hasCosts).isFalse()
        assertThat(order(costs = listOf(OrderCost(CostCategory.OTHER, 500.0))).hasCosts).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderCostingTest*"`
Expected: FAIL — `costs`, `CostCategory`, `OrderCost`, `totalCost`, `profit`, `profitMargin`, `hasCosts` unresolved.

- [ ] **Step 3: Implement the model**

In `Order.kt`, add above `data class Order`:

```kotlin
enum class CostCategory {
    FABRIC,
    MATERIALS_TRIMS,
    EMBELLISHMENT,
    LABOUR,
    LOGISTICS,
    OTHER,
}

/**
 * One recorded cost line on an [Order]. At most one per [CostCategory]
 * (the editor enforces this); modelled as a list for stable serialization.
 */
data class OrderCost(
    val category: CostCategory,
    val amount: Double,
    val note: String? = null,
)
```

Add `val costs: List<OrderCost> = emptyList(),` to the `Order` constructor (place it after `payments`). In the `Order` body, alongside `depositPaid`/`payableTotal`/`balanceRemaining`, add:

```kotlin
    /** Sum of all recorded cost lines. Private business data — never on receipts. */
    val totalCost: Double get() = costs.sumOf { it.amount }

    /** Real profit on the full order value: [payableTotal] minus [totalCost]. Can be negative (a loss). */
    val profit: Double get() = payableTotal - totalCost

    /** [profit] as a fraction of [payableTotal]; null when payableTotal is 0 (no meaningful %). */
    val profitMargin: Double? get() = if (payableTotal > 0.0) profit / payableTotal else null

    /** True when at least one cost line is recorded. */
    val hasCosts: Boolean get() = costs.isNotEmpty()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderCostingTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderCostingTest.kt
git commit -m "feat(costing): add OrderCost model and Order profit helpers"
```

---

### Task 2: Persistence — `OrderCostDto` + mapper round-trip

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt` (add cases)

**Interfaces:**
- Consumes: `CostCategory`, `OrderCost` (Task 1).
- Produces: `@Serializable data class OrderCostDto(category: String = "OTHER", amount: Double = 0.0, note: String? = null)`; `OrderDto.costs: List<OrderCostDto>`; `fun OrderCostDto.toOrderCost(): OrderCost`; `fun OrderCost.toOrderCostDto(): OrderCostDto`.

- [ ] **Step 1: Write the failing test** (append to `OrderMapperTest`)

```kotlin
@Test
fun `costs round-trip through dto and back`() {
    val order = sampleOrder().copy(  // use whatever order factory the test file already has
        costs = listOf(
            OrderCost(CostCategory.FABRIC, 25_000.0),
            OrderCost(CostCategory.EMBELLISHMENT, 4_000.0, note = "beading — outsourced"),
        ),
    )
    val restored = order.toOrderDto().toOrder(userId = "u1")
    assertThat(restored.costs).isEqualTo(order.costs)
}

@Test
fun `legacy order with no costs field maps to empty list`() {
    val dto = OrderDto(id = "o1", totalPrice = 50_000.0) // no costs
    assertThat(dto.toOrder(userId = "u1").costs).isEqualTo(emptyList())
}

@Test
fun `unknown cost category string falls back to OTHER`() {
    val dto = OrderDto(
        id = "o1",
        costs = listOf(OrderCostDto(category = "MYSTERY", amount = 10.0)),
    )
    assertThat(dto.toOrder(userId = "u1").costs.single().category).isEqualTo(CostCategory.OTHER)
}
```

(Add imports for `CostCategory`, `OrderCost`, `OrderCostDto`. If the file lacks a `sampleOrder()` helper, construct an `Order` inline as in Task 1.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderMapperTest*"`
Expected: FAIL — `costs`, `OrderCostDto` unresolved.

- [ ] **Step 3: Implement DTO + mapper**

In `OrderDto.kt`, add `val costs: List<OrderCostDto> = emptyList(),` to `OrderDto` (after `payments`), and add:

```kotlin
@Serializable
data class OrderCostDto(
    val category: String = "OTHER",
    val amount: Double = 0.0,
    val note: String? = null,
)
```

In `OrderMapper.kt`: in `OrderDto.toOrder`, add `costs = costs.map { it.toOrderCost() },` to the `Order(...)` construction. In `Order.toOrderDto`, add `costs = costs.map { it.toOrderCostDto() },`. Add the two mapper functions (mirroring `toPayment`/`toPaymentDto`):

```kotlin
fun OrderCostDto.toOrderCost(): OrderCost = OrderCost(
    category = runCatching { CostCategory.valueOf(category) }.getOrDefault(CostCategory.OTHER),
    amount = amount,
    note = note,
)

fun OrderCost.toOrderCostDto(): OrderCostDto = OrderCostDto(
    category = category.name,
    amount = amount,
    note = note,
)
```

Add imports: `OrderCostDto`, `CostCategory`, `OrderCost`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderMapperTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt
git commit -m "feat(costing): persist order costs via dto + mapper round-trip"
```

---

### Task 3: Repository — `updateCosts(...)`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt`

**Interfaces:**
- Consumes: `OrderCost` (Task 1), `OrderCostDto.toFirestoreMap()` (defined here), `OrderCost.toOrderCostDto()` (Task 2).
- Produces: `suspend fun OrderRepository.updateCosts(userId: String, orderId: String, costs: List<OrderCost>): EmptyResult<DataError.Network>`. On `FakeOrderRepository`: `var lastCostsUpdate: Pair<String, List<OrderCost>>?`.

- [ ] **Step 1: Add to the interface**

In `OrderRepository.kt`, after `updateNotes(...)`:

```kotlin
    suspend fun updateCosts(
        userId: String,
        orderId: String,
        costs: List<OrderCost>,
    ): EmptyResult<DataError.Network>
```

Add import `com.danzucker.stitchpad.core.domain.model.OrderCost`.

- [ ] **Step 2: Implement in `FakeOrderRepository`**

Add field near the other `last*` vars: `var lastCostsUpdate: Pair<String, List<OrderCost>>? = null`. Add the override (mirrors `updateNotes`):

```kotlin
override suspend fun updateCosts(
    userId: String,
    orderId: String,
    costs: List<OrderCost>,
): EmptyResult<DataError.Network> {
    shouldReturnError?.let { return Result.Error(it) }
    lastCostsUpdate = orderId to costs
    ordersFlow.value = ordersFlow.value.map { existing ->
        if (existing.id == orderId) existing.copy(costs = costs) else existing
    }
    return Result.Success(Unit)
}
```

Add import `com.danzucker.stitchpad.core.domain.model.OrderCost`.

- [ ] **Step 3: Implement in `FirebaseOrderRepository`**

Add a Firestore-map helper next to `PaymentDto.toFirestoreMap` (keys MUST match `OrderCostDto` fields):

```kotlin
internal fun OrderCostDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "category" to category,
    "amount" to amount,
    "note" to note,
)
```

Add the override next to `updateNotes` (targeted write, full-list replacement, offline-enqueued — NO serverCreatedAt/createdAt):

```kotlin
override suspend fun updateCosts(
    userId: String,
    orderId: String,
    costs: List<OrderCost>,
): EmptyResult<DataError.Network> {
    val now = Clock.System.now().toEpochMilliseconds()
    val costMaps = costs.map { it.toOrderCostDto().toFirestoreMap() }
    val accepted = offlineWrites.enqueue("updateCosts orderId=$orderId") {
        ordersCollection(userId).document(orderId).update(
            "costs" to costMaps,
            "updatedAt" to now,
        )
    }
    if (!accepted) {
        return Result.Error(DataError.Network.UNKNOWN)
    }
    return Result.Success(Unit)
}
```

Add imports: `com.danzucker.stitchpad.core.domain.model.OrderCost`, `com.danzucker.stitchpad.core.data.dto.OrderCostDto`, `com.danzucker.stitchpad.core.data.mapper.toOrderCostDto`.

- [ ] **Step 4: Verify it compiles (both targets)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (No unit test here — behavior is exercised through the ViewModel in Task 4.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt
git commit -m "feat(costing): add targeted updateCosts repository write"
```

---

### Task 4: Order-detail MVI — state, actions, ViewModel save

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModelCostsTest.kt`

**Interfaces:**
- Consumes: `updateCosts(...)` (Task 3), `OrderCost`, `CostCategory`.
- Produces on `OrderDetailState`: `costsEditorVisible: Boolean`, `costsDraft: Map<CostCategory, String>` (raw digit strings keyed by category; empty string = unset). New actions: `OnEditCostsClick`, `OnCostDraftChange(category, digits)`, `OnSaveCosts`, `OnDismissCostsEditor`.

> Read the existing `OrderDetailViewModel` before editing to match its `_state`/`onAction` conventions, how it reads `userId`, and how it emits snackbars/events (mirror `OnNotesSaveClick` → `updateNotes`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.containsExactlyInAnyOrder
import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.OrderCost
// … project test imports for dispatcher rule / VM construction, mirroring existing OrderDetailViewModel tests

class OrderDetailViewModelCostsTest {

    @Test
    fun `saving costs calls repository and hides editor`() = runTest {
        val repo = FakeOrderRepository().apply { ordersList = listOf(sampleOrderWith(id = "o1")) }
        val vm = buildOrderDetailViewModel(repo, orderId = "o1") // use existing test builder pattern

        vm.state.test {
            awaitInitialLoad() // skip to loaded state per existing helper
            vm.onAction(OrderDetailAction.OnEditCostsClick)
            vm.onAction(OrderDetailAction.OnCostDraftChange(CostCategory.FABRIC, "25000"))
            vm.onAction(OrderDetailAction.OnCostDraftChange(CostCategory.LABOUR, "7000"))
            vm.onAction(OrderDetailAction.OnSaveCosts)

            val saved = repo.lastCostsUpdate
            assertThat(saved?.first).isEqualTo("o1")
            assertThat(saved?.second.orEmpty()).containsExactlyInAnyOrder(
                OrderCost(CostCategory.FABRIC, 25_000.0),
                OrderCost(CostCategory.LABOUR, 7_000.0),
            )
            // editor closed after save
            assertThat(expectMostRecentItem().costsEditorVisible).isFalse()
        }
    }

    @Test
    fun `blank category drafts are dropped and zero amounts excluded`() = runTest {
        val repo = FakeOrderRepository().apply { ordersList = listOf(sampleOrderWith(id = "o1")) }
        val vm = buildOrderDetailViewModel(repo, orderId = "o1")
        vm.onAction(OrderDetailAction.OnEditCostsClick)
        vm.onAction(OrderDetailAction.OnCostDraftChange(CostCategory.FABRIC, "25000"))
        vm.onAction(OrderDetailAction.OnCostDraftChange(CostCategory.OTHER, "")) // unset
        vm.onAction(OrderDetailAction.OnSaveCosts)
        assertThat(repo.lastCostsUpdate?.second.orEmpty()).containsExactlyInAnyOrder(
            OrderCost(CostCategory.FABRIC, 25_000.0),
        )
    }
}
```

(Match the exact VM-construction / dispatcher-rule helpers used by the existing `OrderDetailViewModel` tests in the same package — copy their setup verbatim rather than inventing new harness code.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderDetailViewModelCostsTest*"`
Expected: FAIL — new actions/state fields unresolved.

- [ ] **Step 3: Implement state + actions + VM handling**

In `OrderDetailState.kt` add fields:
```kotlin
val costsEditorVisible: Boolean = false,
val costsDraft: Map<CostCategory, String> = emptyMap(),
```
(import `com.danzucker.stitchpad.core.domain.model.CostCategory`).

In `OrderDetailAction.kt` add a `// Costs` group:
```kotlin
data object OnEditCostsClick : OrderDetailAction
data class OnCostDraftChange(val category: CostCategory, val digits: String) : OrderDetailAction
data object OnSaveCosts : OrderDetailAction
data object OnDismissCostsEditor : OrderDetailAction
```
(import `CostCategory`).

In `OrderDetailViewModel.onAction`, add branches. Seed the draft from the loaded order's existing costs when opening:
```kotlin
OrderDetailAction.OnEditCostsClick -> {
    val existing = _state.value.order?.costs.orEmpty()
        .associate { it.category to it.amount.toLong().toString() }
    _state.update { it.copy(costsEditorVisible = true, costsDraft = existing) }
}
is OrderDetailAction.OnCostDraftChange ->
    _state.update {
        it.copy(costsDraft = it.costsDraft + (action.category to action.digits.filter(Char::isDigit)))
    }
OrderDetailAction.OnDismissCostsEditor ->
    _state.update { it.copy(costsEditorVisible = false) }
OrderDetailAction.OnSaveCosts -> saveCosts()
```
Add the private helper (mirror how `OnNotesSaveClick`/`updateNotes` resolves `userId`, launches in `viewModelScope`, and emits the snackbar/error event):
```kotlin
private fun saveCosts() {
    val orderId = _state.value.order?.id ?: return
    val costs = _state.value.costsDraft.mapNotNull { (category, digits) ->
        val amount = digits.toDoubleOrNull() ?: return@mapNotNull null
        if (amount <= 0.0) null else OrderCost(category, amount)
    }
    viewModelScope.launch {
        _state.update { it.copy(costsEditorVisible = false) }
        when (orderRepository.updateCosts(userId, orderId, costs)) {
            is Result.Error -> _events.send(OrderDetailEvent.ShowError(/* UiText per existing error mapping */))
            is Result.Success -> _events.send(OrderDetailEvent.ShowMessage(/* costs-saved UiText */))
        }
    }
}
```
(Use the VM's existing `userId`, `_events`, `OrderDetailEvent` variants, and `UiText` error-mapping — match the `updateNotes` handler exactly. Imports: `OrderCost`, `Result`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderDetailViewModelCostsTest*"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModelCostsTest.kt
git commit -m "feat(costing): order-detail MVI for editing and saving costs"
```

---

### Task 5: Strings + category display helper

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (path: confirm actual location with `git ls-files '*strings.xml'`)
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/CostCategoryUi.kt`

**Interfaces:**
- Produces: `@Composable fun CostCategory.label(): String`, `@Composable fun CostCategory.hint(): String`, and `val CostCategory.Companion` display order list `costCategoryOrder: List<CostCategory>` (FABRIC, MATERIALS_TRIMS, EMBELLISHMENT, LABOUR, LOGISTICS, OTHER).

- [ ] **Step 1: Add string resources**

Add to `strings.xml` (use `&apos;`/`’`, never `\'`):
```xml
<string name="order_costs_section">Costs &amp; Profit</string>
<string name="order_costs_total">Total cost</string>
<string name="order_costs_profit">Profit</string>
<string name="order_costs_loss">Loss</string>
<string name="order_costs_add_cost">Add cost</string>
<string name="order_costs_add_button">Add costs</string>
<string name="order_costs_empty_body">Add what this order cost you — fabric, trims, labour — to see your real profit.</string>
<string name="order_costs_private_caption">Private — never shown on receipts</string>
<string name="costs_editor_title">Record costs</string>
<string name="costs_editor_subtitle">What did this order cost you? Leave blank what does not apply.</string>
<string name="costs_editor_save">Save costs</string>
<string name="costs_saved_message">Costs saved</string>
<string name="cost_category_fabric">Fabric</string>
<string name="cost_category_materials_trims">Materials &amp; trims</string>
<string name="cost_category_embellishment">Embellishment</string>
<string name="cost_category_labour">Labour</string>
<string name="cost_category_logistics">Logistics</string>
<string name="cost_category_other">Other</string>
<string name="cost_hint_materials_trims">thread, lining, zips, buttons</string>
<string name="cost_hint_embellishment">embroidery, beading, stoning, print</string>
<string name="cost_hint_labour">sewing / handwork</string>
<string name="cost_hint_logistics">transport, delivery, market runs</string>
<string name="cost_hint_other">packaging, POS fees, anything else</string>
<string name="reports_kpi_profit">Profit</string>
<string name="reports_profit_coverage">On %1$d of %2$d orders with costs recorded</string>
<string name="reports_profit_empty">Add costs to an order to see profit</string>
```

- [ ] **Step 2: Create the UI helper**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CostCategory
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.*

val costCategoryOrder: List<CostCategory> = listOf(
    CostCategory.FABRIC,
    CostCategory.MATERIALS_TRIMS,
    CostCategory.EMBELLISHMENT,
    CostCategory.LABOUR,
    CostCategory.LOGISTICS,
    CostCategory.OTHER,
)

@Composable
fun CostCategory.label(): String = when (this) {
    CostCategory.FABRIC -> stringResource(Res.string.cost_category_fabric)
    CostCategory.MATERIALS_TRIMS -> stringResource(Res.string.cost_category_materials_trims)
    CostCategory.EMBELLISHMENT -> stringResource(Res.string.cost_category_embellishment)
    CostCategory.LABOUR -> stringResource(Res.string.cost_category_labour)
    CostCategory.LOGISTICS -> stringResource(Res.string.cost_category_logistics)
    CostCategory.OTHER -> stringResource(Res.string.cost_category_other)
}

@Composable
fun CostCategory.hint(): String? = when (this) {
    CostCategory.FABRIC -> null
    CostCategory.MATERIALS_TRIMS -> stringResource(Res.string.cost_hint_materials_trims)
    CostCategory.EMBELLISHMENT -> stringResource(Res.string.cost_hint_embellishment)
    CostCategory.LABOUR -> stringResource(Res.string.cost_hint_labour)
    CostCategory.LOGISTICS -> stringResource(Res.string.cost_hint_logistics)
    CostCategory.OTHER -> stringResource(Res.string.cost_hint_other)
}
```

- [ ] **Step 3: Verify generation + compile**

Run: `./gradlew :composeApp:generateComposeResClass :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (generated `Res.string.*` resolve).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/CostCategoryUi.kt
git commit -m "feat(costing): strings and category display helpers"
```

---

### Task 6: `OrderCostsCard` composable + previews

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderCostsCard.kt`

**Interfaces:**
- Consumes: `Order.costs`, `Order.totalCost`, `Order.profit`, `Order.profitMargin`, `Order.hasCosts`, `CostCategory.label()`/`hint()`, `costCategoryOrder`, `formatPrice` from `core.sharing`.
- Produces: `@Composable fun OrderCostsCard(costs: List<OrderCost>, totalCost: Double, profit: Double, profitMargin: Double?, onEditClick: () -> Unit, modifier: Modifier = Modifier)`.

> Model construction on `OrderPaymentCard.kt` — same `Surface(shape=radiusLg, border=outlineVariant)`, `SectionIconTile` header (use `Icons.Default.QueryStats` or `Insights`, tinted with `DesignTokens.sienna500`), `HorizontalDivider`, monospace amounts via `formatPrice`.

- [ ] **Step 1: Implement the card**

Key structure (fill in styling to match `OrderPaymentCard`):
- Header: `SectionIconTile` (sienna tint) + `stringResource(Res.string.order_costs_section)`.
- If `costs.isEmpty()`: empty state — body text `order_costs_empty_body`, a filled button `order_costs_add_button` → `onEditClick`, and the private caption row.
- Else: for each `category in costCategoryOrder` where a cost exists, a row `category.label()` + optional small `note` + monospace `₦${formatPrice(amount)}`; then a text affordance `order_costs_add_cost` → `onEditClick`; `HorizontalDivider`; a Total row (`order_costs_total` + `₦${formatPrice(totalCost)}`); then the profit band:
  - `val isLoss = profit < 0.0`
  - container color `DesignTokens.successDarkBg`/`success50` (or loss `errorDarkBg`/`error50`) — resolve via theme like `OrderPaymentCard` does for balance;
  - label `order_costs_loss` if `isLoss` else `order_costs_profit`;
  - value `"${if (isLoss) "−" else ""}₦${formatPrice(abs(profit))}"` + `profitMargin?.let { " · ${(it*100).roundToInt()}%" }`;
  - text color `successDarkText`/`success500` vs `errorDarkText`/`error500`.
- Footer: lock glyph + `order_costs_private_caption` in `onSurfaceVariant`.

Reuse the private `SectionIconTile` pattern (either copy it locally as `OrderPaymentCard` does, or extract a shared one — copying is acceptable to match the existing file-local convention).

- [ ] **Step 2: Add previews** (light + dark, three states)

Add `@Preview` composables wrapped in `StitchPadTheme` / `StitchPadTheme(darkTheme = true)` for: filled (profit), loss (costs > payable), and empty (`costs = emptyList()`). Add `@file:Suppress("TooManyFunctions")` at the top if detekt flags it.

- [ ] **Step 3: Verify compile (both targets)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderCostsCard.kt
git commit -m "feat(costing): OrderCostsCard with filled, loss, and empty states"
```

---

### Task 7: `CostsEditorSheet` + wire card into `OrderDetailScreen`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/CostsEditorSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`

**Interfaces:**
- Consumes: `costsDraft: Map<CostCategory, String>`, `costCategoryOrder`, `label()`/`hint()`, actions from Task 4.
- Produces: `@Composable fun CostsEditorSheet(draft: Map<CostCategory, String>, onAmountChange: (CostCategory, String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit)`.

> Mirror `RecordPaymentDialogV2.kt` for the `ModalBottomSheet` scaffolding, and apply the iOS present timing note: gate re-presentation with the ~450ms delay pattern already used for sheets in this screen. Bind each amount field to a local `TextFieldValue` (cursor-stability pattern) rather than raw String from state.

- [ ] **Step 1: Implement the sheet**

`ModalBottomSheet` with title `costs_editor_title`, subtitle `costs_editor_subtitle`, then a column of 6 rows over `costCategoryOrder`: each row shows `category.label()` (+ `hint()` subtitle) and a numeric `OutlinedTextField`/styled field bound to `draft[category].orEmpty()`, `onValueChange` → digits-only → `onAmountChange(category, digits)`, prefixed `₦`, `KeyboardType.Number`. A full-width primary button `costs_editor_save` → `onSave`. Dismiss via sheet scrim → `onDismiss`.

- [ ] **Step 2: Wire into `OrderDetailScreen`**

- Render `OrderCostsCard(...)` in the detail scroll immediately after `OrderPaymentCard(...)`, passing `state.order.costs`, `state.order.totalCost`, `state.order.profit`, `state.order.profitMargin`, and `onEditClick = { onAction(OrderDetailAction.OnEditCostsClick) }`.
- When `state.costsEditorVisible`, show `CostsEditorSheet(draft = state.costsDraft, onAmountChange = { c, d -> onAction(OrderDetailAction.OnCostDraftChange(c, d)) }, onSave = { onAction(OrderDetailAction.OnSaveCosts) }, onDismiss = { onAction(OrderDetailAction.OnDismissCostsEditor) })`.
- Add previews for the screen state with costs present if the file already has screen previews.

- [ ] **Step 3: Verify compile (both targets) + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL, detekt clean.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/CostsEditorSheet.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(costing): costs editor sheet wired into order detail"
```

---

### Task 8: Reports — profit + coverage in `KpiCalculator`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/model/Kpi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculator.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculatorTest.kt` (add cases)

**Interfaces:**
- Consumes: `Order.hasCosts`, `Order.profit`, `Order.payableTotal`.
- Produces on `KpiSummary`: `profit: Kpi`, `ordersWithCosts: Int`, `ordersInWindow: Int`, `profitMarginPercent: Double?`.

- [ ] **Step 1: Write the failing test** (append to `KpiCalculatorTest`; reuse the file's existing order/window helpers)

```kotlin
@Test
fun `profit sums only orders with costs in window`() {
    val today = LocalDate(2026, 7, 24)
    val tz = TimeZone.UTC
    val inWindow = updatedAtFor(today, tz) // use existing helper that lands in current window
    val orders = listOf(
        orderInWindow(id = "a", payable = 50_000.0, updatedAt = inWindow,
            costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0))), // profit 6k
        orderInWindow(id = "b", payable = 30_000.0, updatedAt = inWindow,
            costs = emptyList()), // excluded from profit + coverage numerator
    )
    val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
    assertThat(summary.profit.current).isEqualTo(6_000.0)
    assertThat(summary.ordersWithCosts).isEqualTo(1)
    assertThat(summary.ordersInWindow).isEqualTo(2)
    assertThat(summary.profitMarginPercent).isEqualTo(6_000.0 / 50_000.0 * 100.0)
}

@Test
fun `profit margin is null when no costed orders in window`() {
    val today = LocalDate(2026, 7, 24); val tz = TimeZone.UTC
    val orders = listOf(orderInWindow(id = "a", payable = 30_000.0,
        updatedAt = updatedAtFor(today, tz), costs = emptyList()))
    val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
    assertThat(summary.profit.current).isEqualTo(0.0)
    assertThat(summary.ordersWithCosts).isEqualTo(0)
    assertThat(summary.profitMarginPercent).isNull()
}
```

(Adapt `orderInWindow`/`updatedAtFor` to the helpers the existing test uses; add `costs` to whatever order factory it has.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*KpiCalculatorTest*"`
Expected: FAIL — `summary.profit`, `ordersWithCosts`, `ordersInWindow`, `profitMarginPercent` unresolved.

- [ ] **Step 3: Implement**

In `Kpi.kt`, extend `KpiSummary`:
```kotlin
data class KpiSummary(
    val revenue: Kpi,
    val collected: Kpi,
    val outstanding: Kpi,
    val orders: Kpi,
    val profit: Kpi,
    val ordersWithCosts: Int,
    val ordersInWindow: Int,
    val profitMarginPercent: Double?,
)
```

In `KpiCalculator.computeSummary`: add a `profitSpark` MutableList alongside the others, and inside the per-bucket loop add `if (order.hasCosts) profitSpark[i] += order.profit`. In the current/previous single-window loop, accumulate `currentProfit`/`previousProfit` (only when `order.hasCosts`), plus `ordersWithCosts`, `ordersInWindow`, and `currentCostedRevenue` (sum `payableTotal` of costed orders in current window). Then:
```kotlin
val marginPercent = if (currentCostedRevenue > 0.0) currentProfit / currentCostedRevenue * 100.0 else null
```
Add to the returned `KpiSummary`: `profit = kpi(currentProfit, previousProfit, profitSpark)`, `ordersWithCosts = ordersWithCosts`, `ordersInWindow = ordersInWindow`, `profitMarginPercent = marginPercent`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*KpiCalculatorTest*"`
Expected: PASS. (Fix any other `KpiSummary(...)` construction sites the compiler flags — e.g. fakes/previews — by supplying the new fields.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/model/Kpi.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculator.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculatorTest.kt
git commit -m "feat(costing): compute profit and coverage in KpiCalculator"
```

---

### Task 9: Reports UI — Profit KPI tile

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/KpiGrid.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/KpiTile.kt` (only if a new variant/caption slot is needed)

**Interfaces:**
- Consumes: `KpiSummary.profit`, `.ordersWithCosts`, `.ordersInWindow`, `.profitMarginPercent`.

> The Profit tile lives inside the existing Reports grid, which is already wrapped by the Pro paywall (`ReportsPaywallCard`) upstream in `ReportsScreen` — **do not add gating here**. Read `KpiGrid`/`KpiTile` first to match how the four existing tiles are built (value formatting, sparkline, delta).

- [ ] **Step 1: Render the Profit tile**

In `KpiGrid`, after the four existing tiles, add a Profit tile (may span full width per the approved mock):
- Title `reports_kpi_profit` + margin: `summary.profitMarginPercent?.let { "· margin ${it.roundToInt()}%" }`.
- Value `₦${formatPrice(summary.profit.current)}` (success-tinted), sparkline from `summary.profit.sparkline`.
- Coverage caption: `stringResource(Res.string.reports_profit_coverage, summary.ordersWithCosts, summary.ordersInWindow)`.
- When `summary.ordersWithCosts == 0`: show `reports_profit_empty` instead of `₦0` and hide the sparkline/margin.

- [ ] **Step 2: Add/adjust previews**

Update or add `KpiGrid`/`KpiTile` previews to include a `KpiSummary` with the new fields, covering: profit present, and zero-coverage empty. Light + dark.

- [ ] **Step 3: Verify compile (both targets) + detekt + full unit tests**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL, all tests pass, detekt clean.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/KpiGrid.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/KpiTile.kt
git commit -m "feat(costing): Pro Profit tile with honest coverage in Reports"
```

---

### Task 10: Privacy regression test — receipts never expose costs

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptCostPrivacyTest.kt`

**Interfaces:**
- Consumes: `ReceiptFormatter` (existing), `Order` with costs.

> Read `ReceiptFormatter.kt` + `ReceiptFormatterTest.kt` first to reuse its exact formatter entry point and input shape.

- [ ] **Step 1: Write the test**

Format a receipt for an order that has costs, then assert none of the cost amounts or profit appear in any `ReceiptData` string field. Example shape (adapt to the real formatter signature):
```kotlin
@Test
fun `receipt never contains cost or profit figures`() {
    val order = sampleReceiptOrder().copy(
        totalPrice = 50_000.0,
        costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)), // profit 6,000
    )
    val receipt = ReceiptFormatter.format(order, /* same args as ReceiptFormatterTest */)
    val haystack = listOfNotNull(
        receipt.subtotalFormatted, receipt.totalFormatted, receipt.depositFormatted,
        receipt.balanceFormatted, receipt.discountFormatted,
    ) + receipt.items.flatMap { listOf(it.formattedPrice, it.formattedUnitPrice) } +
        receipt.paymentRows.map { it.formattedAmount }
    // "44,000" (a cost) and "6,000" (profit) must appear nowhere on the receipt
    assertThat(haystack.none { it.contains("44,000") }).isTrue()
    assertThat(haystack.none { it.contains("6,000") }).isTrue()
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReceiptCostPrivacyTest*"`
Expected: PASS immediately (costs are already excluded by construction — this is a guardrail against future regressions). If it FAILS, a cost/profit field leaked into `ReceiptData` — remove it.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptCostPrivacyTest.kt
git commit -m "test(costing): assert receipts never expose costs or profit"
```

---

### Task 11: Full verification pass

- [ ] **Step 1: iOS compile gate**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: All unit tests (JVM + iOS test compile)**

Run: `./gradlew :composeApp:testDebugUnitTest :composeApp:compileTestKotlinIosSimulatorArm64`
Expected: all pass, iOS test target compiles.

- [ ] **Step 3: Detekt**

Run: `./gradlew detekt`
Expected: clean (or only pre-existing baseline items).

- [ ] **Step 4: Manual smoke (Android + iOS sim), for the PR body per QA convention**

1. Open an order → **Costs & Profit** card shows empty CTA. Tap **Add costs** → enter Fabric ₦25,000, Materials ₦5,000, Embellishment ₦4,000, Labour ₦7,000, Logistics ₦3,000 → Save. Card shows the five rows, Total ₦44,000, green **Profit ₦6,000 · 12%**.
2. Edit costs so total > order value → band flips to red **Loss −₦x**.
3. Toggle device dark mode → both states legible.
4. Share a receipt for that order → confirm **no** cost/profit figures anywhere.
5. As a Pro/Atelier account, open Reports → **Profit** tile shows amount, margin %, and "On N of M orders with costs recorded". As a Free account → Reports paywall still shown.

- [ ] **Step 5: Finish the branch**

Use superpowers:finishing-a-development-branch to open the PR (include the manual smoke steps above, run Cursor + `codex review` per project review rotation).

---

## Self-Review

**Spec coverage:** model (T1) · persistence dto/mapper (T2) · updateCosts repo write staying in Lane-B rules (T3) · order-detail MVI (T4) · strings/category helpers (T5) · OrderCostsCard filled/empty/loss (T6) · CostsEditorSheet + screen wiring (T7) · KpiCalculator profit+coverage (T8) · Pro Profit tile (T9) · privacy guardrail test (T10) · verification incl. iOS + receipt-leak (T11). All spec sections mapped.

**Type consistency:** `updateCosts(userId, orderId, costs)` identical across interface/fake/firebase/VM/tests. `OrderCost(category, amount, note)`, `CostCategory` 6-value set, and `KpiSummary` new fields (`profit`, `ordersWithCosts`, `ordersInWindow`, `profitMarginPercent`) used consistently in T8/T9. `costsDraft: Map<CostCategory, String>` and the four new actions match between T4 and T7.

**Open reviewer notes (confirmed in spec):** profit basis is `payableTotal` (not collected); Reports margin denominator is costed-orders' revenue only.
