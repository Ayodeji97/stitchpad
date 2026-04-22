# Order Receipt Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild order receipt sharing with a professional dual-theme design (dark PNG for WhatsApp, light PDF for printing), a bottom sheet format picker, and consistent cross-platform rendering.

**Architecture:** Common `ReceiptData` model + `ReceiptFormatter` in shared code handles all data preparation. Platform `expect/actual` `OrderReceiptSharer` handles rendering (Android Canvas/PdfDocument, iOS UIGraphicsImageRenderer/UIGraphicsPDFRenderer) and sharing. The existing single `shareReceipt(order)` method is replaced with `shareReceiptAsImage(receiptData)` and `shareReceiptAsPdf(receiptData)`.

**Tech Stack:** KMP, Compose Multiplatform, Android Canvas + PdfDocument, iOS UIGraphicsImageRenderer + UIGraphicsPDFRenderer, Koin DI, kotlin.test

**Design Spec:** `docs/superpowers/specs/2026-04-21-order-receipt-generator-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/sharing/ReceiptData.kt` | Display-ready receipt data model |
| Create | `core/sharing/ReceiptFormatter.kt` | Builds ReceiptData from Order + User |
| Create | `core/sharing/PriceFormatter.kt` | Shared price formatting (extracted from 4 duplicates) |
| Modify | `core/sharing/OrderReceiptSharer.kt` | Updated expect class with new methods |
| Rewrite | `androidMain/.../OrderReceiptSharer.android.kt` | Dark PNG + light PDF rendering + sharing |
| Rewrite | `iosMain/.../OrderReceiptSharer.ios.kt` | Dark PNG + light PDF rendering + sharing |
| Modify | `feature/order/presentation/detail/OrderDetailState.kt` | Add `showShareSheet`, `user` fields |
| Modify | `feature/order/presentation/detail/OrderDetailAction.kt` | Add share sheet actions |
| Modify | `feature/order/presentation/detail/OrderDetailViewModel.kt` | Wire share flow with ReceiptFormatter |
| Modify | `feature/order/presentation/detail/OrderDetailScreen.kt` | Add ShareReceiptBottomSheet |
| Modify | `commonMain/composeResources/values/strings.xml` | New receipt string resources |
| Modify | `di/OrderModule.kt` | Register ReceiptFormatter |
| Create | `commonTest/.../core/sharing/ReceiptFormatterTest.kt` | Unit tests for ReceiptFormatter |
| Create | `commonTest/.../core/sharing/PriceFormatterTest.kt` | Unit tests for PriceFormatter |

All paths are relative to `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/` unless prefixed with `androidMain`, `iosMain`, or `commonTest`.

---

### Task 1: Extract shared PriceFormatter

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatter.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatterTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatterTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import kotlin.test.Test
import kotlin.test.assertEquals

class PriceFormatterTest {

    @Test
    fun wholeNumberFormatsWithoutDecimals() {
        assertEquals("50,000", formatPrice(50_000.0))
    }

    @Test
    fun decimalFormatsToTwoPlaces() {
        assertEquals("1,234.56", formatPrice(1_234.56))
    }

    @Test
    fun zeroFormatsCorrectly() {
        assertEquals("0", formatPrice(0.0))
    }

    @Test
    fun smallNumberNoSeparator() {
        assertEquals("500", formatPrice(500.0))
    }

    @Test
    fun negativeNumber() {
        assertEquals("-1,000", formatPrice(-1_000.0))
    }

    @Test
    fun singleDecimalPadded() {
        assertEquals("100.50", formatPrice(100.5))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.core.sharing.PriceFormatterTest" 2>&1 | tail -20`
Expected: Compilation failure — `formatPrice` not found.

- [ ] **Step 3: Write the implementation**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatter.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

fun formatPrice(price: Double): String {
    val long = price.toLong()
    if (price == long.toDouble()) return addThousandsSeparator(long.toString())
    val parts = price.toString().split(".")
    val decimal = (parts.getOrElse(1) { "00" } + "00").take(2)
    return addThousandsSeparator(parts[0]) + "." + decimal
}

private fun addThousandsSeparator(intPart: String): String {
    val negative = intPart.startsWith("-")
    val digits = if (negative) intPart.drop(1) else intPart
    val result = buildString {
        digits.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
    return if (negative) "-$result" else result
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.core.sharing.PriceFormatterTest" 2>&1 | tail -20`
Expected: All 6 tests PASS.

- [ ] **Step 5: Replace duplicate formatPrice usages**

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`, remove the private `formatPrice` and `addThousandsSeparator` functions (lines 784-802). Add import `com.danzucker.stitchpad.core.sharing.formatPrice`.

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt`, remove the private `formatPrice` and `addThousandsSeparator` functions. Add import `com.danzucker.stitchpad.core.sharing.formatPrice`.

The Android and iOS `OrderReceiptSharer` files will be fully rewritten in later tasks, so their `formatPrice` will be removed then.

- [ ] **Step 6: Run full tests to verify no regressions**

Run: `./gradlew :composeApp:desktopTest 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatter.kt \
       composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/PriceFormatterTest.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt
git commit -m "refactor: extract shared PriceFormatter from duplicate implementations"
```

---

### Task 2: Create ReceiptData model and ReceiptFormatter

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatter.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatterTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatterTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ReceiptFormatterTest {

    private val testUser = User(
        id = "user1",
        email = "ade@test.com",
        displayName = "Ade",
        businessName = "Ade's Tailoring",
        phoneNumber = "08012345678",
        avatarColorIndex = 0
    )

    private val testOrder = Order(
        id = "abc123def456",
        userId = "user1",
        customerId = "cust1",
        customerName = "Chief Okafor",
        items = listOf(
            OrderItem(
                id = "item1",
                garmentType = GarmentType.AGBADA,
                description = "Blue fabric",
                price = 45_000.0
            ),
            OrderItem(
                id = "item2",
                garmentType = GarmentType.SENATOR,
                description = "",
                price = 25_000.0
            )
        ),
        status = OrderStatus.IN_PROGRESS,
        priority = OrderPriority.RUSH,
        statusHistory = emptyList(),
        totalPrice = 70_000.0,
        depositPaid = 30_000.0,
        balanceRemaining = 40_000.0,
        deadline = 1745798400000L, // 28 Apr 2025 00:00:00 UTC
        notes = "Needs by Friday",
        createdAt = 1745193600000L, // 21 Apr 2025 00:00:00 UTC
        updatedAt = 1745193600000L
    )

    private val garmentNames = mapOf(
        GarmentType.AGBADA to "Agbada",
        GarmentType.SENATOR to "Senator"
    )

    @Test
    fun formatsBusinessNameWithScissorsEmoji() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("\u2702\uFE0F Ade's Tailoring", result.businessName)
    }

    @Test
    fun formatsPhoneWithEmoji() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("\uD83D\uDCDE 08012345678", result.businessPhone)
    }

    @Test
    fun nullBusinessNameFallsBackToStitchPad() {
        val userNoName = testUser.copy(businessName = null)
        val result = ReceiptFormatter.format(testOrder, userNoName, garmentNames)
        assertEquals("\u2702\uFE0F StitchPad", result.businessName)
    }

    @Test
    fun nullPhoneReturnsNull() {
        val userNoPhone = testUser.copy(phoneNumber = null)
        val result = ReceiptFormatter.format(testOrder, userNoPhone, garmentNames)
        assertNull(result.businessPhone)
    }

    @Test
    fun customerNamePassedThrough() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("Chief Okafor", result.customerName)
    }

    @Test
    fun itemsGroupedByGarmentType() {
        val orderWithDuplicates = testOrder.copy(
            items = listOf(
                OrderItem("i1", GarmentType.TROUSER, "", 8_000.0),
                OrderItem("i2", GarmentType.TROUSER, "", 8_000.0),
                OrderItem("i3", GarmentType.AGBADA, "", 45_000.0)
            )
        )
        val names = mapOf(GarmentType.TROUSER to "Trouser", GarmentType.AGBADA to "Agbada")
        val result = ReceiptFormatter.format(orderWithDuplicates, testUser, names)
        assertEquals(2, result.items.size)
        val trouserItem = result.items.first { it.garmentName == "Trouser" }
        assertEquals(2, trouserItem.quantity)
        assertEquals("\u20A616,000", trouserItem.formattedPrice)
    }

    @Test
    fun paymentFormattedWithNaira() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("\u20A670,000", result.totalFormatted)
        assertEquals("\u20A630,000", result.depositFormatted)
        assertEquals("\u20A640,000", result.balanceFormatted)
    }

    @Test
    fun balanceRemainingMeansNotFullyPaid() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertFalse(result.isFullyPaid)
    }

    @Test
    fun zeroBalanceMeansFullyPaid() {
        val paidOrder = testOrder.copy(depositPaid = 70_000.0, balanceRemaining = 0.0)
        val result = ReceiptFormatter.format(paidOrder, testUser, garmentNames)
        assertTrue(result.isFullyPaid)
    }

    @Test
    fun rushPriorityShowsLabel() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("RUSH", result.priorityLabel)
    }

    @Test
    fun urgentPriorityShowsLabel() {
        val urgentOrder = testOrder.copy(priority = OrderPriority.URGENT)
        val result = ReceiptFormatter.format(urgentOrder, testUser, garmentNames)
        assertEquals("URGENT", result.priorityLabel)
    }

    @Test
    fun normalPriorityReturnsNull() {
        val normalOrder = testOrder.copy(priority = OrderPriority.NORMAL)
        val result = ReceiptFormatter.format(normalOrder, testUser, garmentNames)
        assertNull(result.priorityLabel)
    }

    @Test
    fun deadlineFormattedCorrectly() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        // Deadline is 28 Apr 2025 — exact format depends on timezone but should contain "28" and "Apr" and "2025"
        assertTrue(result.deadlineFormatted!!.contains("28"))
        assertTrue(result.deadlineFormatted!!.contains("2025"))
    }

    @Test
    fun nullDeadlineReturnsNull() {
        val noDeadline = testOrder.copy(deadline = null)
        val result = ReceiptFormatter.format(noDeadline, testUser, garmentNames)
        assertNull(result.deadlineFormatted)
    }

    @Test
    fun orderIdTruncatedToShortForm() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("ORD-ABC1", result.orderIdShort)
    }

    @Test
    fun statusLabelMapped() {
        val result = ReceiptFormatter.format(testOrder, testUser, garmentNames)
        assertEquals("In Progress", result.statusLabel)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.core.sharing.ReceiptFormatterTest" 2>&1 | tail -20`
Expected: Compilation failure — `ReceiptData` and `ReceiptFormatter` not found.

- [ ] **Step 3: Create ReceiptData model**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

data class ReceiptItem(
    val quantity: Int,
    val garmentName: String,
    val formattedPrice: String
)

data class ReceiptData(
    val businessName: String,
    val businessPhone: String?,
    val customerName: String,
    val dateFormatted: String,
    val items: List<ReceiptItem>,
    val totalFormatted: String,
    val depositFormatted: String,
    val balanceFormatted: String,
    val isFullyPaid: Boolean,
    val statusLabel: String,
    val statusColorHex: String,
    val deadlineFormatted: String?,
    val priorityLabel: String?,
    val orderIdShort: String
)
```

- [ ] **Step 4: Create ReceiptFormatter**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatter.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object ReceiptFormatter {

    private const val FALLBACK_BUSINESS_NAME = "StitchPad"
    private const val ORDER_ID_PREFIX_LENGTH = 4

    fun format(
        order: Order,
        user: User,
        garmentNames: Map<GarmentType, String>
    ): ReceiptData {
        val createdDate = Instant.fromEpochMilliseconds(order.createdAt)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dateFormatted = "${createdDate.dayOfMonth} ${createdDate.month.name.lowercase()
            .replaceFirstChar { it.uppercase() }.take(3)} ${createdDate.year}"

        val deadlineFormatted = order.deadline?.let { millis ->
            val d = Instant.fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            "${d.dayOfMonth} ${d.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }.take(3)} ${d.year}"
        }

        val groupedItems = order.items
            .groupBy { it.garmentType }
            .map { (type, items) ->
                ReceiptItem(
                    quantity = items.size,
                    garmentName = garmentNames[type] ?: type.name,
                    formattedPrice = "\u20A6${formatPrice(items.sumOf { it.price })}"
                )
            }

        val shortId = order.id
            .take(ORDER_ID_PREFIX_LENGTH)
            .uppercase()

        return ReceiptData(
            businessName = "\u2702\uFE0F ${user.businessName ?: FALLBACK_BUSINESS_NAME}",
            businessPhone = user.phoneNumber?.let { "\uD83D\uDCDE $it" },
            customerName = order.customerName,
            dateFormatted = dateFormatted,
            items = groupedItems,
            totalFormatted = "\u20A6${formatPrice(order.totalPrice)}",
            depositFormatted = "\u20A6${formatPrice(order.depositPaid)}",
            balanceFormatted = "\u20A6${formatPrice(order.balanceRemaining)}",
            isFullyPaid = order.balanceRemaining <= 0.0,
            statusLabel = statusToLabel(order.status),
            statusColorHex = statusToColorHex(order.status),
            deadlineFormatted = deadlineFormatted,
            priorityLabel = when (order.priority) {
                OrderPriority.NORMAL -> null
                OrderPriority.URGENT -> "URGENT"
                OrderPriority.RUSH -> "RUSH"
            },
            orderIdShort = "ORD-$shortId"
        )
    }

    private fun statusToLabel(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "Pending"
        OrderStatus.IN_PROGRESS -> "In Progress"
        OrderStatus.READY -> "Ready"
        OrderStatus.DELIVERED -> "Delivered"
    }

    private fun statusToColorHex(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "#2B7FD4"
        OrderStatus.IN_PROGRESS -> "#E8A800"
        OrderStatus.READY -> "#2D9E6B"
        OrderStatus.DELIVERED -> "#7D7970"
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.danzucker.stitchpad.core.sharing.ReceiptFormatterTest" 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatter.kt \
       composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatterTest.kt
git commit -m "feat: add ReceiptData model and ReceiptFormatter"
```

---

### Task 3: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add new receipt string resources**

Add the following entries inside the `<resources>` tag in `composeApp/src/commonMain/composeResources/values/strings.xml`:

```xml
    <!-- Receipt sharing -->
    <string name="share_receipt_title">Share Receipt</string>
    <string name="share_as_image_title">Share as Image</string>
    <string name="share_as_image_description">Best for WhatsApp &amp; social media</string>
    <string name="share_as_pdf_title">Share as PDF</string>
    <string name="share_as_pdf_description">Best for printing &amp; email</string>
    <string name="receipt_balance_due">DUE</string>
    <string name="receipt_paid_in_full">PAID IN FULL</string>
    <string name="receipt_order_id_prefix">Order #</string>
    <string name="receipt_share_error">Could not share receipt. Please try again.</string>
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat: add receipt sharing string resources"
```

---

### Task 4: Update OrderReceiptSharer expect class and MVI contracts

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt`

- [ ] **Step 1: Update OrderReceiptSharer expect class**

Replace the contents of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

expect class OrderReceiptSharer {
    suspend fun shareReceiptAsImage(receiptData: ReceiptData)
    suspend fun shareReceiptAsPdf(receiptData: ReceiptData)
}
```

- [ ] **Step 2: Update OrderDetailState**

Replace the contents of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderDetailState(
    val order: Order? = null,
    val user: User? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val showStatusUpdateDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val selectedNewStatus: OrderStatus? = null,
    val errorMessage: UiText? = null
)
```

- [ ] **Step 3: Update OrderDetailAction**

Replace the contents of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus

sealed interface OrderDetailAction {
    data object OnEditClick : OrderDetailAction
    data object OnDeleteClick : OrderDetailAction
    data object OnConfirmDelete : OrderDetailAction
    data object OnDismissDeleteDialog : OrderDetailAction
    data object OnUpdateStatusClick : OrderDetailAction
    data class OnSelectNewStatus(val status: OrderStatus) : OrderDetailAction
    data object OnConfirmStatusUpdate : OrderDetailAction
    data object OnDismissStatusUpdate : OrderDetailAction
    data object OnCustomerClick : OrderDetailAction
    data object OnShareClick : OrderDetailAction
    data object OnShareAsImageClick : OrderDetailAction
    data object OnShareAsPdfClick : OrderDetailAction
    data object OnDismissShareSheet : OrderDetailAction
    data object OnBackClick : OrderDetailAction
    data object OnErrorDismiss : OrderDetailAction
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt
git commit -m "feat: update OrderReceiptSharer API and MVI contracts for share sheet"
```

Note: The project will NOT compile at this point because the `actual` classes on Android and iOS still implement the old `shareReceipt(order)` method. This is expected — Tasks 6 and 7 will fix the platform implementations.

---

### Task 5: Update OrderDetailViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`

- [ ] **Step 1: Update OrderDetailViewModel**

Replace the contents of `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.ReceiptFormatter
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayNameAsync
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val receiptSharer: OrderReceiptSharer
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private var hasStartedObserving = false
    private val _state = MutableStateFlow(OrderDetailState())

    private val _events = Channel<OrderDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasStartedObserving) {
                hasStartedObserving = true
                observeOrder()
                loadUser()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderDetailState()
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.OnEditClick -> {
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToOrderForm(orderId))
                }
            }
            OrderDetailAction.OnDeleteClick -> {
                _state.update { it.copy(showDeleteDialog = true) }
            }
            OrderDetailAction.OnConfirmDelete -> deleteOrder()
            OrderDetailAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false) }
            }
            OrderDetailAction.OnUpdateStatusClick -> {
                _state.update { it.copy(showStatusUpdateDialog = true) }
            }
            is OrderDetailAction.OnSelectNewStatus -> {
                _state.update { it.copy(selectedNewStatus = action.status) }
            }
            OrderDetailAction.OnConfirmStatusUpdate -> updateStatus()
            OrderDetailAction.OnDismissStatusUpdate -> {
                _state.update { it.copy(showStatusUpdateDialog = false, selectedNewStatus = null) }
            }
            OrderDetailAction.OnCustomerClick -> {
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToCustomerDetail(customerId))
                }
            }
            OrderDetailAction.OnShareClick -> {
                _state.update { it.copy(showShareSheet = true) }
            }
            OrderDetailAction.OnShareAsImageClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt { receiptData -> receiptSharer.shareReceiptAsImage(receiptData) }
            }
            OrderDetailAction.OnShareAsPdfClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt { receiptData -> receiptSharer.shareReceiptAsPdf(receiptData) }
            }
            OrderDetailAction.OnDismissShareSheet -> {
                _state.update { it.copy(showShareSheet = false) }
            }
            OrderDetailAction.OnBackClick -> {
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateBack) }
            }
            OrderDetailAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun shareReceipt(share: suspend (com.danzucker.stitchpad.core.sharing.ReceiptData) -> Unit) {
        val order = _state.value.order ?: return
        val user = _state.value.user ?: return
        viewModelScope.launch {
            try {
                val garmentNames = order.items
                    .map { it.garmentType }
                    .distinct()
                    .associate { it to garmentDisplayNameAsync(it) }
                val receiptData = ReceiptFormatter.format(order, user, garmentNames)
                share(receiptData)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = com.danzucker.stitchpad.core.presentation.UiText.DynamicString(
                            e.message ?: "Could not share receipt"
                        )
                    )
                }
            }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _state.update { it.copy(user = user) }
        }
    }

    private fun observeOrder() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            orderRepository.observeOrder(userId, orderId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _state.update { it.copy(order = result.data, isLoading = false) }
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                        }
                    }
                }
            }
        }
    }

    private fun deleteOrder() {
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val result = orderRepository.deleteOrder(userId, orderId)) {
                is Result.Success -> _events.send(OrderDetailEvent.OrderDeleted)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }

    private fun updateStatus() {
        val newStatus = _state.value.selectedNewStatus ?: return
        _state.update { it.copy(showStatusUpdateDialog = false, selectedNewStatus = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val result = orderRepository.updateOrderStatus(userId, orderId, newStatus)) {
                is Result.Success -> { /* Snapshot observer will auto-update the state */ }
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt
git commit -m "feat: wire OrderDetailViewModel with ReceiptFormatter and share sheet flow"
```

---

### Task 6: Rewrite Android OrderReceiptSharer

**Files:**
- Rewrite: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt`

- [ ] **Step 1: Rewrite Android implementation**

Replace the full contents of `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class OrderReceiptSharer(private val context: Context) {

    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData) {
        val file = withContext(Dispatchers.Default) {
            val bitmap = renderDarkBitmap(receiptData)
            saveBitmapToCache(bitmap, "img")
        }
        shareFile(file, "image/png")
    }

    actual suspend fun shareReceiptAsPdf(receiptData: ReceiptData) {
        val file = withContext(Dispatchers.Default) {
            renderLightPdf(receiptData)
        }
        shareFile(file, "application/pdf")
    }

    // region Dark Theme Bitmap Rendering

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderDarkBitmap(data: ReceiptData): Bitmap {
        val width = 800
        val padding = 40f
        val contentWidth = width - 2 * padding

        // Colors
        val bgColor = Color.parseColor("#121110")
        val headerBg = Color.parseColor("#E8A800")
        val headerText = Color.parseColor("#121110")
        val bodyText = Color.parseColor("#E5E3DF")
        val labelColor = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#3A3731")
        val saffron = Color.parseColor("#E8A800")
        val green = Color.parseColor("#2D9E6B")
        val rushRed = Color.parseColor("#D93B3B")

        // Paints
        val headerTitlePaint = makePaint(headerText, 28f, bold = true)
        val headerPhonePaint = makePaint(headerText, 16f).apply { alpha = 190 }
        val labelPaint = makePaint(labelColor, 14f, bold = true)
        val bodyPaint = makePaint(bodyText, 18f)
        val bodyBoldPaint = makePaint(bodyText, 18f, bold = true)
        val priceRightPaint = makePaint(bodyText, 18f).apply { textAlign = Paint.Align.RIGHT }
        val totalLabelPaint = makePaint(bodyText, 20f, bold = true)
        val totalPaint = makePaint(saffron, 20f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val depositPaint = makePaint(green, 18f).apply { textAlign = Paint.Align.RIGHT }
        val balancePaint = makePaint(saffron, 18f, bold = true)
        val statusPaint = makePaint(Color.parseColor(data.statusColorHex), 17f, bold = true)
        val footerPaint = makePaint(dividerColor, 14f).apply { textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply { color = dividerColor; strokeWidth = 1f; style = Paint.Style.STROKE }
        val rushBadgePaint = makePaint(Color.WHITE, 13f, bold = true)
        val rushBgPaint = Paint().apply { color = rushRed; style = Paint.Style.FILL }
        val balanceBgPaint = Paint().apply {
            color = saffron; alpha = 30; style = Paint.Style.FILL
        }
        val paidBgPaint = Paint().apply {
            color = green; alpha = 30; style = Paint.Style.FILL
        }

        // Estimate height
        val headerHeight = if (data.businessPhone != null) 90f else 70f
        var estimatedHeight = headerHeight + padding
        estimatedHeight += 60f // customer + date row
        estimatedHeight += 20f // divider gap
        estimatedHeight += 30f // Items label
        estimatedHeight += data.items.size * 30f // items
        estimatedHeight += 20f // gap
        estimatedHeight += 30f // divider gap
        estimatedHeight += 30f * 3 // total/deposit/balance
        estimatedHeight += 30f // gap
        estimatedHeight += 20f // divider
        estimatedHeight += 50f // status + deadline row
        if (data.priorityLabel != null) estimatedHeight += 30f
        estimatedHeight += 50f // footer
        estimatedHeight += padding * 2

        val height = estimatedHeight.toInt().coerceAtLeast(500)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        var y = 0f

        // Header band
        val headerBgPaint = Paint().apply { color = headerBg; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerBgPaint)
        val headerCenterY = if (data.businessPhone != null) headerHeight / 2f - 10f else headerHeight / 2f
        canvas.drawText(data.businessName, width / 2f - headerTitlePaint.measureText(data.businessName) / 2f,
            headerCenterY + 10f, headerTitlePaint)
        if (data.businessPhone != null) {
            canvas.drawText(data.businessPhone, width / 2f - headerPhonePaint.measureText(data.businessPhone) / 2f,
                headerCenterY + 32f, headerPhonePaint)
        }
        y = headerHeight + padding

        // Customer & Date row
        canvas.drawText("CUSTOMER", padding, y, labelPaint)
        canvas.drawText("DATE", width - padding - labelPaint.measureText("DATE"), y, labelPaint)
        y += 22f
        canvas.drawText(data.customerName, padding, y, bodyBoldPaint)
        val dateWidth = bodyPaint.measureText(data.dateFormatted)
        canvas.drawText(data.dateFormatted, width - padding - dateWidth, y, bodyPaint)
        y += 24f

        // Dashed divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 20f

        // Items
        canvas.drawText("ITEMS", padding, y, labelPaint)
        y += 24f
        data.items.forEach { item ->
            val itemText = "${item.quantity} \u00D7 ${item.garmentName}"
            canvas.drawText(itemText, padding, y, bodyPaint)
            canvas.drawText(item.formattedPrice, width - padding, y, priceRightPaint)
            y += 28f
        }
        y += 8f

        // Payment divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 22f

        // Total
        canvas.drawText("Total", padding, y, totalLabelPaint)
        canvas.drawText(data.totalFormatted, width - padding, y, totalPaint)
        y += 26f

        // Deposit
        canvas.drawText("Deposit Paid", padding, y, bodyPaint)
        canvas.drawText(data.depositFormatted, width - padding, y, depositPaint)
        y += 26f

        // Balance
        canvas.drawText("Balance", padding, y, bodyPaint)
        if (data.isFullyPaid) {
            val paidText = "\u2713 PAID IN FULL"
            val paidPaint = makePaint(green, 17f, bold = true)
            val tw = paidPaint.measureText(paidText)
            val rx = width - padding - tw - 14f
            val ry = y - 16f
            canvas.drawRoundRect(RectF(rx, ry, rx + tw + 14f, ry + 24f), 8f, 8f, paidBgPaint)
            canvas.drawText(paidText, rx + 7f, y, paidPaint)
        } else {
            val dueText = "${data.balanceFormatted} DUE"
            val tw = balancePaint.measureText(dueText)
            val rx = width - padding - tw - 14f
            val ry = y - 16f
            canvas.drawRoundRect(RectF(rx, ry, rx + tw + 14f, ry + 24f), 8f, 8f, balanceBgPaint)
            canvas.drawText(dueText, rx + 7f, y, balancePaint)
        }
        y += 28f

        // Status & Deadline divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 22f

        // Status
        canvas.drawText("STATUS", padding, y, labelPaint)
        if (data.deadlineFormatted != null) {
            canvas.drawText("DEADLINE", width - padding - labelPaint.measureText("DEADLINE"), y, labelPaint)
        }
        y += 22f
        canvas.drawText("\u25CF ${data.statusLabel}", padding, y, statusPaint)
        if (data.deadlineFormatted != null) {
            val dlWidth = bodyPaint.measureText(data.deadlineFormatted)
            canvas.drawText(data.deadlineFormatted, width - padding - dlWidth, y, bodyPaint)
        }
        y += 6f

        // Priority badge
        if (data.priorityLabel != null) {
            y += 18f
            val badgeX = if (data.deadlineFormatted != null) {
                width - padding - rushBadgePaint.measureText(data.priorityLabel) - 16f
            } else {
                padding
            }
            val badgeRect = RectF(
                badgeX, y - 14f,
                badgeX + rushBadgePaint.measureText(data.priorityLabel) + 16f, y + 8f
            )
            canvas.drawRoundRect(badgeRect, 6f, 6f, rushBgPaint)
            canvas.drawText(data.priorityLabel, badgeX + 8f, y + 4f, rushBadgePaint)
            y += 14f
        }

        // Footer
        y += 24f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 20f
        canvas.drawText("Order #${data.orderIdShort}", width / 2f, y, footerPaint)

        // Crop to actual content height
        val finalHeight = (y + padding).toInt().coerceAtMost(height)
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight)
    }

    // endregion

    // region Light Theme PDF Rendering

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderLightPdf(data: ReceiptData): File {
        // A5 size in PostScript points: 420 x 595
        val pageWidth = 420
        val pageHeight = 595
        val padding = 30f

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Colors
        val bodyText = Color.parseColor("#1E1C1A")
        val labelColor = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#E8E6E3")
        val saffron = Color.parseColor("#C48E00")
        val green = Color.parseColor("#2D9E6B")
        val rushRed = Color.parseColor("#D93B3B")
        val headerBorderColor = Color.parseColor("#E8A800")

        // Paints
        val headerTitlePaint = makePaint(bodyText, 22f, bold = true)
        val headerPhonePaint = makePaint(labelColor, 12f)
        val labelPaintPdf = makePaint(labelColor, 10f, bold = true)
        val bodyPaintPdf = makePaint(bodyText, 14f)
        val bodyBoldPdf = makePaint(bodyText, 14f, bold = true)
        val priceRightPdf = makePaint(bodyText, 14f).apply { textAlign = Paint.Align.RIGHT }
        val totalLabelPdf = makePaint(bodyText, 16f, bold = true)
        val totalPricePdf = makePaint(saffron, 16f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val depositPricePdf = makePaint(green, 14f).apply { textAlign = Paint.Align.RIGHT }
        val balancePdf = makePaint(saffron, 14f, bold = true)
        val statusPdf = makePaint(Color.parseColor(data.statusColorHex), 13f, bold = true)
        val footerPdf = makePaint(Color.parseColor("#A8A49D"), 10f).apply { textAlign = Paint.Align.CENTER }
        val linePdf = Paint().apply { color = dividerColor; strokeWidth = 1f }
        val rushBadgePdf = makePaint(Color.WHITE, 10f, bold = true)
        val rushBgPdf = Paint().apply { color = rushRed; style = Paint.Style.FILL }

        // White background
        canvas.drawColor(Color.WHITE)

        var y = padding

        // Header (centered, with saffron bottom border)
        val headerBottomY = if (data.businessPhone != null) y + 50f else y + 40f
        canvas.drawText(data.businessName,
            pageWidth / 2f - headerTitlePaint.measureText(data.businessName) / 2f, y + 22f, headerTitlePaint)
        if (data.businessPhone != null) {
            canvas.drawText(data.businessPhone,
                pageWidth / 2f - headerPhonePaint.measureText(data.businessPhone) / 2f, y + 38f, headerPhonePaint)
        }
        y = headerBottomY + 4f
        val borderPaint = Paint().apply { color = headerBorderColor; strokeWidth = 3f }
        canvas.drawLine(padding, y, pageWidth - padding, y, borderPaint)
        y += 18f

        // Customer & Date
        canvas.drawText("CUSTOMER", padding, y, labelPaintPdf)
        canvas.drawText("DATE", pageWidth - padding - labelPaintPdf.measureText("DATE"), y, labelPaintPdf)
        y += 16f
        canvas.drawText(data.customerName, padding, y, bodyBoldPdf)
        canvas.drawText(data.dateFormatted, pageWidth - padding - bodyPaintPdf.measureText(data.dateFormatted), y, bodyPaintPdf)
        y += 18f

        // Divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f

        // Items
        canvas.drawText("ITEMS", padding, y, labelPaintPdf)
        y += 18f
        data.items.forEach { item ->
            val text = "${item.quantity} \u00D7 ${item.garmentName}"
            canvas.drawText(text, padding, y, bodyPaintPdf)
            canvas.drawText(item.formattedPrice, pageWidth - padding, y, priceRightPdf)
            y += 20f
        }
        y += 6f

        // Payment divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 18f

        // Total
        canvas.drawText("Total", padding, y, totalLabelPdf)
        canvas.drawText(data.totalFormatted, pageWidth - padding, y, totalPricePdf)
        y += 20f

        // Deposit
        canvas.drawText("Deposit Paid", padding, y, bodyPaintPdf)
        canvas.drawText(data.depositFormatted, pageWidth - padding, y, depositPricePdf)
        y += 20f

        // Balance
        canvas.drawText("Balance", padding, y, bodyPaintPdf)
        if (data.isFullyPaid) {
            val paidText = "\u2713 PAID IN FULL"
            val pp = makePaint(green, 13f, bold = true)
            val tw = pp.measureText(paidText)
            val rx = pageWidth - padding - tw - 12f
            val bgp = Paint().apply { color = green; alpha = 25; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(rx, y - 12f, rx + tw + 12f, y + 6f), 6f, 6f, bgp)
            canvas.drawText(paidText, rx + 6f, y, pp)
        } else {
            val dueText = "${data.balanceFormatted} DUE"
            val tw = balancePdf.measureText(dueText)
            val rx = pageWidth - padding - tw - 12f
            val bgp = Paint().apply { color = saffron; alpha = 25; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(rx, y - 12f, rx + tw + 12f, y + 6f), 6f, 6f, bgp)
            canvas.drawText(dueText, rx + 6f, y, balancePdf)
        }
        y += 22f

        // Status divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f

        // Status & Deadline
        canvas.drawText("STATUS", padding, y, labelPaintPdf)
        if (data.deadlineFormatted != null) {
            canvas.drawText("DEADLINE", pageWidth - padding - labelPaintPdf.measureText("DEADLINE"), y, labelPaintPdf)
        }
        y += 16f
        canvas.drawText("\u25CF ${data.statusLabel}", padding, y, statusPdf)
        if (data.deadlineFormatted != null) {
            canvas.drawText(data.deadlineFormatted,
                pageWidth - padding - bodyPaintPdf.measureText(data.deadlineFormatted), y, bodyPaintPdf)
        }
        y += 6f

        // Priority badge
        if (data.priorityLabel != null) {
            y += 14f
            val bx = if (data.deadlineFormatted != null) {
                pageWidth - padding - rushBadgePdf.measureText(data.priorityLabel) - 12f
            } else {
                padding
            }
            val rect = RectF(bx, y - 10f, bx + rushBadgePdf.measureText(data.priorityLabel) + 12f, y + 6f)
            canvas.drawRoundRect(rect, 4f, 4f, rushBgPdf)
            canvas.drawText(data.priorityLabel, bx + 6f, y + 2f, rushBadgePdf)
        }

        // Footer
        y += 30f
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f
        canvas.drawText("Order #${data.orderIdShort}", pageWidth / 2f, y, footerPdf)

        doc.finishPage(page)

        val file = cacheFile("pdf", "pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        pruneOldReceipts()
        return file
    }

    // endregion

    // region Helpers

    private fun makePaint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun saveBitmapToCache(bitmap: Bitmap, prefix: String): File {
        val file = cacheFile(prefix, "png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        pruneOldReceipts()
        return file
    }

    private fun cacheFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        return File(dir, "receipt_${prefix}_${System.currentTimeMillis()}.$extension")
    }

    private fun pruneOldReceipts() {
        val dir = File(context.cacheDir, "receipts")
        val files = dir.listFiles().orEmpty()
        if (files.size <= CACHE_LIMIT) return
        files.sortedByDescending { it.lastModified() }
            .drop(CACHE_LIMIT)
            .forEach { it.delete() }
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // endregion

    private companion object {
        const val CACHE_LIMIT = 10
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt
git commit -m "feat: rewrite Android receipt sharer with dual-theme PNG and PDF rendering"
```

---

### Task 7: Rewrite iOS OrderReceiptSharer

**Files:**
- Rewrite: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`

- [ ] **Step 1: Rewrite iOS implementation**

Replace the full contents of `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class)
actual class OrderReceiptSharer {

    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData) {
        val image = renderDarkImage(receiptData)
        val pngData = UIImagePNGRepresentation(image) ?: return
        val fileUrl = tempFileUrl("png")
        pngData.writeToURL(fileUrl, atomically = true)
        shareUrl(fileUrl)
    }

    actual suspend fun shareReceiptAsPdf(receiptData: ReceiptData) {
        val fileUrl = renderLightPdf(receiptData)
        shareUrl(fileUrl)
    }

    private fun renderDarkImage(data: ReceiptData): UIImage {
        val width = 800.0
        val padding = 40.0
        val headerHeight = if (data.businessPhone != null) 90.0 else 70.0
        val lineSpacing = 28.0
        var estimatedHeight = headerHeight + padding * 2
        estimatedHeight += 60.0 + 20.0 // customer row
        estimatedHeight += 30.0 + data.items.size * lineSpacing + 20.0 // items
        estimatedHeight += lineSpacing * 3 + 30.0 // payment
        estimatedHeight += 60.0 // status
        if (data.priorityLabel != null) estimatedHeight += 30.0
        estimatedHeight += 50.0 // footer

        val size = CGSizeMake(width, estimatedHeight)
        val format = UIGraphicsImageRendererFormat().apply { opaque = true }
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        return renderer.imageWithActions { context ->
            val ctx = context ?: return@imageWithActions

            // Background
            darkColor("#121110").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, estimatedHeight))

            // Header band
            darkColor("#E8A800").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, headerHeight))

            drawCentered(data.businessName, y = headerHeight / 2.0 - 14.0, width = width,
                font = boldFont(22.0), color = darkColor("#121110"))
            if (data.businessPhone != null) {
                drawCentered(data.businessPhone, y = headerHeight / 2.0 + 8.0, width = width,
                    font = regularFont(13.0), color = darkColor("#121110"))
            }

            var y = headerHeight + padding

            // Customer & Date
            drawText("CUSTOMER", padding, y, labelFont(), darkColor("#7D7970"))
            drawTextRight("DATE", width - padding, y, labelFont(), darkColor("#7D7970"))
            y += 18.0
            drawText(data.customerName, padding, y, boldFont(15.0), darkColor("#E5E3DF"))
            drawTextRight(data.dateFormatted, width - padding, y, regularFont(14.0), darkColor("#E5E3DF"))
            y += 22.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Items
            drawText("ITEMS", padding, y, labelFont(), darkColor("#7D7970"))
            y += 20.0
            data.items.forEach { item ->
                drawText("${item.quantity} \u00D7 ${item.garmentName}", padding, y,
                    regularFont(14.0), darkColor("#E5E3DF"))
                drawTextRight(item.formattedPrice, width - padding, y,
                    regularFont(14.0), darkColor("#E5E3DF"))
                y += lineSpacing
            }
            y += 8.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Payment
            drawText("Total", padding, y, boldFont(16.0), darkColor("#E5E3DF"))
            drawTextRight(data.totalFormatted, width - padding, y, boldFont(16.0), darkColor("#E8A800"))
            y += 24.0
            drawText("Deposit Paid", padding, y, regularFont(13.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, width - padding, y, regularFont(13.0), darkColor("#2D9E6B"))
            y += 24.0
            drawText("Balance", padding, y, regularFont(13.0), darkColor("#7D7970"))
            if (data.isFullyPaid) {
                drawTextRight("\u2713 PAID IN FULL", width - padding, y, boldFont(14.0), darkColor("#2D9E6B"))
            } else {
                drawTextRight("${data.balanceFormatted} DUE", width - padding, y, boldFont(14.0), darkColor("#E8A800"))
            }
            y += 26.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Status & Deadline
            drawText("STATUS", padding, y, labelFont(), darkColor("#7D7970"))
            if (data.deadlineFormatted != null) {
                drawTextRight("DEADLINE", width - padding, y, labelFont(), darkColor("#7D7970"))
            }
            y += 18.0
            drawText("\u25CF ${data.statusLabel}", padding, y, boldFont(13.0),
                darkColor(data.statusColorHex))
            if (data.deadlineFormatted != null) {
                drawTextRight(data.deadlineFormatted, width - padding, y,
                    regularFont(13.0), darkColor("#E5E3DF"))
            }
            y += 8.0

            if (data.priorityLabel != null) {
                y += 14.0
                drawBadge(data.priorityLabel, width - padding, y,
                    darkColor("#D93B3B"), UIColor.whiteColor, boldFont(11.0))
            }

            y += 24.0
            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 16.0
            drawCentered("Order #${data.orderIdShort}", y = y, width = width,
                font = regularFont(11.0), color = darkColor("#3A3731"))
        }
    }

    private fun renderLightPdf(data: ReceiptData): NSURL {
        val pageWidth = 420.0
        val pageHeight = 595.0
        val padding = 30.0
        val fileUrl = tempFileUrl("pdf")

        val format = UIGraphicsPDFRendererFormat()
        val bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val pdfData = renderer.PDFDataWithActions { context ->
            val ctx = context ?: return@PDFDataWithActions
            ctx.beginPage()

            var y = padding

            // Header
            drawCentered(data.businessName, y = y, width = pageWidth,
                font = boldFont(18.0), color = darkColor("#1E1C1A"))
            y += 20.0
            if (data.businessPhone != null) {
                drawCentered(data.businessPhone, y = y, width = pageWidth,
                    font = regularFont(10.0), color = darkColor("#7D7970"))
                y += 16.0
            }
            y += 4.0
            // Saffron border
            val borderPaint = darkColor("#E8A800")
            borderPaint.setFill()
            platform.UIKit.UIRectFill(CGRectMake(padding, y, pageWidth - 2 * padding, 3.0))
            y += 18.0

            // Customer & Date
            drawText("CUSTOMER", padding, y, labelFont(8.0), darkColor("#7D7970"))
            drawTextRight("DATE", pageWidth - padding, y, labelFont(8.0), darkColor("#7D7970"))
            y += 14.0
            drawText(data.customerName, padding, y, boldFont(12.0), darkColor("#1E1C1A"))
            drawTextRight(data.dateFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#1E1C1A"))
            y += 16.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Items
            drawText("ITEMS", padding, y, labelFont(8.0), darkColor("#7D7970"))
            y += 16.0
            data.items.forEach { item ->
                drawText("${item.quantity} \u00D7 ${item.garmentName}", padding, y,
                    regularFont(11.0), darkColor("#1E1C1A"))
                drawTextRight(item.formattedPrice, pageWidth - padding, y,
                    regularFont(11.0), darkColor("#1E1C1A"))
                y += 18.0
            }
            y += 6.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Payment
            drawText("Total", padding, y, boldFont(13.0), darkColor("#1E1C1A"))
            drawTextRight(data.totalFormatted, pageWidth - padding, y, boldFont(13.0), darkColor("#C48E00"))
            y += 18.0
            drawText("Deposit Paid", padding, y, regularFont(11.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#2D9E6B"))
            y += 18.0
            drawText("Balance", padding, y, regularFont(11.0), darkColor("#7D7970"))
            if (data.isFullyPaid) {
                drawTextRight("\u2713 PAID IN FULL", pageWidth - padding, y, boldFont(11.0), darkColor("#2D9E6B"))
            } else {
                drawTextRight("${data.balanceFormatted} DUE", pageWidth - padding, y, boldFont(11.0), darkColor("#C48E00"))
            }
            y += 20.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Status & Deadline
            drawText("STATUS", padding, y, labelFont(8.0), darkColor("#7D7970"))
            if (data.deadlineFormatted != null) {
                drawTextRight("DEADLINE", pageWidth - padding, y, labelFont(8.0), darkColor("#7D7970"))
            }
            y += 14.0
            drawText("\u25CF ${data.statusLabel}", padding, y, boldFont(11.0),
                darkColor(data.statusColorHex))
            if (data.deadlineFormatted != null) {
                drawTextRight(data.deadlineFormatted, pageWidth - padding, y,
                    regularFont(11.0), darkColor("#1E1C1A"))
            }
            y += 6.0

            if (data.priorityLabel != null) {
                y += 12.0
                drawBadge(data.priorityLabel, pageWidth - padding, y,
                    darkColor("#D93B3B"), UIColor.whiteColor, boldFont(9.0))
            }

            y += 24.0
            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0
            drawCentered("Order #${data.orderIdShort}", y = y, width = pageWidth,
                font = regularFont(9.0), color = darkColor("#A8A49D"))
        }

        pdfData.writeToURL(fileUrl, atomically = true)
        return fileUrl
    }

    // region Drawing Helpers

    private fun drawText(text: String, x: Double, y: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        nsText.drawAtPoint(CGPointMake(x, y), withAttributes = attrs)
    }

    private fun drawTextRight(text: String, rightX: Double, y: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake(rightX - this.width, y), withAttributes = attrs)
        }
    }

    private fun drawCentered(text: String, y: Double, width: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake((width - this.width) / 2.0, y), withAttributes = attrs)
        }
    }

    private fun drawDivider(x1: Double, y: Double, x2: Double, color: UIColor) {
        color.setFill()
        platform.UIKit.UIRectFill(CGRectMake(x1, y, x2 - x1, 1.0))
    }

    private fun drawBadge(text: String, rightX: Double, y: Double, bg: UIColor, fg: UIColor, font: UIFont) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to fg
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            val badgeWidth = this.width + 12.0
            val badgeHeight = this.height + 6.0
            val bx = rightX - badgeWidth
            bg.setFill()
            val path = platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(
                CGRectMake(bx, y - 3.0, badgeWidth, badgeHeight), cornerRadius = 4.0
            )
            path.fill()
            nsText.drawAtPoint(CGPointMake(bx + 6.0, y), withAttributes = attrs)
        }
    }

    private fun darkColor(hex: String): UIColor {
        val cleaned = hex.removePrefix("#")
        val r = cleaned.substring(0, 2).toInt(16) / 255.0
        val g = cleaned.substring(2, 4).toInt(16) / 255.0
        val b = cleaned.substring(4, 6).toInt(16) / 255.0
        return UIColor.colorWithRed(r, green = g, blue = b, alpha = 1.0)
    }

    private fun regularFont(size: Double) = UIFont.systemFontOfSize(size)
    private fun boldFont(size: Double) = UIFont.boldSystemFontOfSize(size)
    private fun labelFont(size: Double = 10.0) = UIFont.boldSystemFontOfSize(size)

    // endregion

    // region File & Share

    private fun tempFileUrl(extension: String): NSURL {
        val dir = NSTemporaryDirectory()
        val name = "receipt_${NSUUID().UUIDString}.$extension"
        return NSURL.fileURLWithPath("$dir$name")
    }

    private suspend fun shareUrl(url: NSURL) {
        withContext(Dispatchers.Main) {
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = rootVC.presentedViewController ?: rootVC
            val activityVC = UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null
            )
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    // endregion
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt
git commit -m "feat: rewrite iOS receipt sharer with dual-theme PNG and PDF rendering"
```

---

### Task 8: Add ShareReceiptBottomSheet and update OrderDetailScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`

- [ ] **Step 1: Add bottom sheet imports and composable**

At the top of `OrderDetailScreen.kt`, add these imports (alongside existing ones):

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import stitchpad.composeapp.generated.resources.share_receipt_title
import stitchpad.composeapp.generated.resources.share_as_image_title
import stitchpad.composeapp.generated.resources.share_as_image_description
import stitchpad.composeapp.generated.resources.share_as_pdf_title
import stitchpad.composeapp.generated.resources.share_as_pdf_description
```

Add the `ShareReceiptBottomSheet` composable at the end of the file (before the existing private helper functions):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiptBottomSheet(
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = DesignTokens.radiusXl, topEnd = DesignTokens.radiusXl)
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                bottom = DesignTokens.space8
            )
        ) {
            Text(
                text = stringResource(Res.string.share_receipt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = DesignTokens.space4)
            )

            // Share as Image option
            ShareOption(
                icon = "\uD83D\uDDBC\uFE0F",
                title = stringResource(Res.string.share_as_image_title),
                description = stringResource(Res.string.share_as_image_description),
                onClick = onShareAsImage
            )

            Spacer(Modifier.height(DesignTokens.space2))

            // Share as PDF option
            ShareOption(
                icon = "\uD83D\uDCC4",
                title = stringResource(Res.string.share_as_pdf_title),
                description = stringResource(Res.string.share_as_pdf_description),
                onClick = onShareAsPdf
            )
        }
    }
}

@Composable
private fun ShareOption(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(DesignTokens.space3)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = DesignTokens.space3)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Wire bottom sheet into OrderDetailScreen**

In the `OrderDetailScreen` composable, add the share bottom sheet right after the status update dialog block (after `if (state.showStatusUpdateDialog && state.order != null) { ... }`):

```kotlin
    // Share receipt bottom sheet
    if (state.showShareSheet) {
        ShareReceiptBottomSheet(
            onShareAsImage = { onAction(OrderDetailAction.OnShareAsImageClick) },
            onShareAsPdf = { onAction(OrderDetailAction.OnShareAsPdfClick) },
            onDismiss = { onAction(OrderDetailAction.OnDismissShareSheet) }
        )
    }
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat: add ShareReceiptBottomSheet to order detail screen"
```

---

### Task 9: Run detekt and fix any issues

- [ ] **Step 1: Run detekt**

Run: `./gradlew detekt 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL (or fixable warnings).

- [ ] **Step 2: Fix any issues detekt reports**

Common issues: line length, complexity suppression, import ordering. Fix as needed.

- [ ] **Step 3: Commit fixes if any**

```bash
git add -A
git commit -m "fix: resolve detekt findings"
```

---

### Task 10: Build and verify compilation

- [ ] **Step 1: Run Android build**

Run: `./gradlew :composeApp:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all tests**

Run: `./gradlew :composeApp:desktopTest 2>&1 | tail -20`
Expected: All tests PASS.

- [ ] **Step 3: Commit if any final fixes needed**

---

## Verification

After implementation, manually test:

1. Open order detail → tap Share icon → bottom sheet appears with "Share as Image" and "Share as PDF"
2. Tap "Share as Image" → dark theme PNG receipt appears in platform share sheet
3. Tap "Share as PDF" → light theme PDF receipt opens correctly
4. Verify receipt shows: business name, phone, customer, items grouped by type, total/deposit/balance
5. Test with balance > 0 → "₦X DUE" badge in saffron
6. Test with balance = 0 → "PAID IN FULL" badge in green
7. Test RUSH order → red RUSH badge next to deadline
8. Test NORMAL order → no priority badge
9. Test order with no deadline → deadline section omitted
10. Test with no business name set → falls back to "StitchPad"
11. Test on both Android and iOS
