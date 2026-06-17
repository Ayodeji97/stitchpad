# Receipt: quantity-row clarity + whole-order discount — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make multi-quantity receipt rows unambiguous (quantity in the name, unit price as a caption, one bold line total) and add an optional whole-order naira discount that renders Subtotal → Discount → Total on the receipt and flows correctly through balances and revenue reporting.

**Architecture:** `totalPrice` keeps its meaning (sum of `price × qty` = subtotal). A new computed `Order.payableTotal = totalPrice − discount` becomes the canonical "amount owed"; `balanceRemaining` is redefined off it. Discount is persisted via two new Order/DTO fields (`discount`, `discountReason`), entered in the order form's Details step, and surfaced on the shared receipt and in-app detail. Calculators that derived "collected" from `totalPrice − balanceRemaining` switch to the unambiguous `depositPaid`, and revenue becomes net-of-discount.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, GitLive Firestore, Koin, kotlinx.serialization. Tests: kotlin.test + Turbine via `:composeApp:testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-06-16-receipt-discount-and-quantity-clarity-design.md`

**Conventions (from CLAUDE.md + project memory):**
- No hardcoded user-facing strings — use `compose.resources`. For apostrophes in `strings.xml` use `&apos;`, never `\'`.
- Run iOS compile before declaring done (JVM-only APIs / Int↔Long skew silently break iOS).
- `./gradlew detekt` must pass; a cohesive screen file over 20 functions uses `@file:Suppress("TooManyFunctions")` (already present on `ReceiptFormatter`).
- Capture gradle exit codes directly (piping to `tail` hides failures).
- Every PR needs manual smoke-test steps (Daniel is QA).

---

## File Structure

**Modified:**
- `core/domain/model/Order.kt` — add `discount`, `discountReason`, `payableTotal`; redefine `balanceRemaining`.
- `core/data/dto/OrderDto.kt` — add `discount`, `discountReason`.
- `core/data/mapper/OrderMapper.kt` — map both fields both directions.
- `core/sharing/ReceiptData.kt` — add `subtotalFormatted`, `discountFormatted`, `discountReason`.
- `core/sharing/ReceiptFormatter.kt` — populate the new fields; `totalFormatted` → `payableTotal`.
- `core/sharing/OrderReceiptSharer.ios.kt` — quantity row B + discount rows (image + PDF paths), height calc.
- `core/sharing/OrderReceiptSharer.android.kt` — same on both Android paths.
- `feature/reports/domain/KpiCalculator.kt`, `feature/reports/domain/CustomerInsightsCalculator.kt`, `feature/dashboard/domain/WeeklyGoalCalculator.kt`, `feature/dashboard/domain/NbaCalculator.kt` — accounting ripple.
- `feature/order/presentation/list/PaymentDisplay.kt`, `PaymentStatusText.kt`, `OrderListScreen.kt` — owed-amount = `payableTotal`.
- `feature/order/presentation/form/OrderFormState.kt`, `OrderFormAction.kt`, `OrderFormViewModel.kt`, `OrderFormScreen.kt` — discount entry.
- `feature/order/presentation/detail/components/OrderPaymentCard.kt`, `detail/OrderDetailScreen.kt`, `detail/components/OrderHeroCard.kt` — discount-aware Total/Balance.
- `feature/dashboard/presentation/DashboardViewModel.kt` — summary amount = `payableTotal`.
- `composeResources/.../values/strings.xml` — new strings.

**Test files (modify/create):**
- `core/domain/model/OrderTest.kt`, `core/data/mapper/OrderMapperTest.kt`, `core/sharing/ReceiptFormatterTest.kt`, `feature/reports/domain/KpiCalculatorTest.kt`, `feature/dashboard/domain/WeeklyGoalCalculatorTest.kt`, `feature/order/presentation/form/OrderFormViewModelTest.kt` (extend if present; create if absent, following `android-testing` skill patterns).

---

## Task 1: Order model — discount, payableTotal, balanceRemaining

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt:76-99`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderTest.kt`

- [ ] **Step 1: Write the failing test**

Create/extend `OrderTest.kt`. Use a small helper to build an Order with one item; only `totalPrice`, `discount`, and `payments` matter here.

```kotlin
package com.danzucker.stitchpad.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderTest {

    private fun order(totalPrice: Double, discount: Double = 0.0, deposit: Double = 0.0) = Order(
        id = "o1", userId = "u1", customerId = "c1", customerName = "Mummy AY",
        items = emptyList(), status = OrderStatus.PENDING, priority = OrderPriority.NORMAL,
        statusHistory = emptyList(), totalPrice = totalPrice, discount = discount,
        payments = if (deposit > 0) listOf(
            Payment("p1", deposit, PaymentMethod.CASH, PaymentType.DEPOSIT, 0L, null)
        ) else emptyList(),
        deadline = null, notes = null, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `no discount keeps payableTotal equal to totalPrice`() {
        assertEquals(32_500.0, order(totalPrice = 32_500.0).payableTotal)
    }

    @Test
    fun `payableTotal subtracts discount`() {
        assertEquals(30_000.0, order(totalPrice = 32_500.0, discount = 2_500.0).payableTotal)
    }

    @Test
    fun `discount larger than subtotal floors payableTotal at zero`() {
        assertEquals(0.0, order(totalPrice = 5_000.0, discount = 9_000.0).payableTotal)
    }

    @Test
    fun `balance is payableTotal minus deposit`() {
        assertEquals(20_000.0, order(totalPrice = 32_500.0, discount = 2_500.0, deposit = 10_000.0).balanceRemaining)
    }

    @Test
    fun `deposit covering discounted total clears balance`() {
        assertEquals(0.0, order(totalPrice = 32_500.0, discount = 2_500.0, deposit = 30_000.0).balanceRemaining)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderTest*"`
Expected: FAIL — `Order` has no `discount`/`payableTotal` parameters/properties (compile error).

- [ ] **Step 3: Implement the model change**

In `Order.kt`, add the two fields (defaults keep all existing constructor call sites compiling) and the computed property; redefine `balanceRemaining`:

```kotlin
data class Order(
    val id: String,
    val userId: String,
    val customerId: String,
    val customerName: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val subStatus: OrderSubStatus? = null,
    val priority: OrderPriority,
    val statusHistory: List<StatusChange>,
    val totalPrice: Double,
    val discount: Double = 0.0,
    val discountReason: String? = null,
    val payments: List<Payment> = emptyList(),
    val deadline: Long?,
    val notes: String?,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Sum of all recorded payments. Replaces the prior persisted `depositPaid` field. */
    val depositPaid: Double get() = payments.sumOf { it.amount }

    /** Subtotal ([totalPrice]) minus the whole-order [discount], floored at 0. The canonical "amount owed". */
    val payableTotal: Double get() = (totalPrice - discount).coerceAtLeast(0.0)

    /** Outstanding balance. Recomputed from [payableTotal] and [payments]. */
    val balanceRemaining: Double get() = (payableTotal - depositPaid).coerceAtLeast(0.0)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderTest.kt
git commit -m "feat(order): add whole-order discount + payableTotal to Order model"
```

---

## Task 2: OrderDto + OrderMapper round-trip

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `OrderMapperTest.kt` (create if absent):

```kotlin
@Test
fun `mapper round-trips discount and reason`() {
    val dto = OrderDto(
        id = "o1", customerId = "c1", customerName = "Mummy AY",
        totalPrice = 32_500.0, discount = 2_500.0, discountReason = "New customer",
    )
    val order = dto.toOrder(userId = "u1")
    assertEquals(2_500.0, order.discount)
    assertEquals("New customer", order.discountReason)
    val back = order.toOrderDto()
    assertEquals(2_500.0, back.discount)
    assertEquals("New customer", back.discountReason)
}

@Test
fun `legacy dto without discount maps to zero and null`() {
    val order = OrderDto(id = "o1", totalPrice = 10_000.0).toOrder(userId = "u1")
    assertEquals(0.0, order.discount)
    assertEquals(null, order.discountReason)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderMapperTest*"`
Expected: FAIL — `OrderDto` has no `discount`/`discountReason`.

- [ ] **Step 3: Implement DTO + mapper changes**

In `OrderDto.kt`, add fields next to `totalPrice` (defaults keep old Firestore docs valid):

```kotlin
    val totalPrice: Double = 0.0,
    val discount: Double = 0.0,
    val discountReason: String? = null,
```

In `OrderMapper.kt`, add the mappings in **both** functions. In `toOrder()` (next to `totalPrice = totalPrice,`):

```kotlin
    totalPrice = totalPrice,
    discount = discount,
    discountReason = discountReason,
```

In `toOrderDto()` (next to `totalPrice = totalPrice,`):

```kotlin
    totalPrice = totalPrice,
    discount = discount,
    discountReason = discountReason,
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderMapperTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt
git commit -m "feat(order): persist discount + reason through OrderDto/mapper"
```

---

## Task 3: Accounting ripple — collected = depositPaid, revenue = payableTotal

This is the silent-corruption fix. After Task 1, `totalPrice − balanceRemaining` no longer equals "collected" (it now equals `discount + depositPaid`). Replace every such idiom with `order.depositPaid`, and make revenue net-of-discount.

**Files:**
- Modify: `feature/reports/domain/KpiCalculator.kt:53-54,73-82`
- Modify: `feature/dashboard/domain/WeeklyGoalCalculator.kt:62`
- Modify: `feature/reports/domain/CustomerInsightsCalculator.kt:57,64`
- Modify: `feature/dashboard/domain/NbaCalculator.kt:102,108`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculatorTest.kt` (+ `WeeklyGoalCalculatorTest.kt` if present)

- [ ] **Step 1: Write the failing test**

Add a discount-aware case to `KpiCalculatorTest.kt`. Build one order with `totalPrice = 10_000`, `discount = 2_000`, one `2_000` deposit, `updatedAt` inside the current window. Expected: revenue counts the **net** `8_000`; collected counts the `2_000` deposit; outstanding = `6_000`.

```kotlin
@Test
fun `discount reduces revenue and never inflates collected`() {
    val order = order( // use the test file's existing order factory
        totalPrice = 10_000.0, discount = 2_000.0,
        payments = listOf(Payment("p", 2_000.0, PaymentMethod.CASH, PaymentType.DEPOSIT, nowMillis, null)),
        updatedAt = nowMillis,
    )
    val summary = KpiCalculator.compute(orders = listOf(order), /* same args the other tests pass */)
    assertEquals(8_000.0, summary.revenue.current)      // net of discount
    assertEquals(2_000.0, summary.collected.current)    // deposits only, NOT discount+deposit
    assertEquals(6_000.0, summary.outstanding.current)
}
```

(Match the exact `KpiCalculator.compute` signature and `KpiSummary` accessor names used by the sibling tests in the file — read them first and mirror them.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*KpiCalculatorTest*"`
Expected: FAIL — `collected.current` is `4_000.0` (discount + deposit) and `revenue.current` is `10_000.0`.

- [ ] **Step 3: Implement the calculator fixes**

`KpiCalculator.kt` — in the spark loop (≈L53-54):

```kotlin
                revenueSpark[i] += order.payableTotal
                collectedSpark[i] += order.depositPaid
```

and in the current/previous loop (≈L73-82) replace the local and the revenue lines:

```kotlin
            val collected = order.depositPaid
            val outstanding = order.balanceRemaining.coerceAtLeast(0.0)
            if (order.updatedAt in currentStart until currentEnd) {
                currentRevenue += order.payableTotal
                currentCollected += collected
                ...
            }
            if (order.updatedAt in previousStart until previousEnd) {
                previousRevenue += order.payableTotal
                ...
            }
```

Update the file's header doc comment: `Revenue: Σ payableTotal (net of discount)`, `Collected: Σ depositPaid (cash paid)`.

`WeeklyGoalCalculator.kt:62`:

```kotlin
            .sumOf { it.depositPaid }
```

(and update the KDoc at L26 from `Σ (totalPrice − balanceRemaining)` to `Σ depositPaid`.)

`CustomerInsightsCalculator.kt:57` and `:64` — both occurrences:

```kotlin
            l.collected += order.depositPaid
```
```kotlin
            val collected = order.depositPaid
```

`NbaCalculator.kt:102,108` — make the CollectDeposit branch discount-aware:

```kotlin
                order.status == OrderStatus.PENDING &&
                    order.depositPaid == 0.0 &&
                    order.payableTotal > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectDeposit,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.payableTotal,
                        days = daysSinceCreation(order, today, timeZone)
                    )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*KpiCalculatorTest*" --tests "*WeeklyGoalCalculatorTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculator.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/domain/CustomerInsightsCalculator.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/WeeklyGoalCalculator.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/NbaCalculator.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/reports/domain/KpiCalculatorTest.kt
git commit -m "fix(reports): collected=deposits, revenue=net-of-discount after discount change"
```

---

## Task 4: ReceiptData + ReceiptFormatter — subtotal/discount fields

**Files:**
- Modify: `core/sharing/ReceiptData.kt:99-135`
- Modify: `core/sharing/ReceiptFormatter.kt:76-104`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `discount populates subtotal and signed discount lines`() {
    val order = sampleOrder(totalPrice = 32_500.0, discount = 2_500.0, discountReason = "New customer")
    val data = ReceiptFormatter.format(order, sampleUser, garmentNames = emptyMap())
    assertEquals("₦32,500", data.subtotalFormatted)
    assertEquals("−₦2,500", data.discountFormatted)
    assertEquals("New customer", data.discountReason)
    assertEquals("₦30,000", data.totalFormatted)   // net total, not subtotal
}

@Test
fun `no discount yields null discount line and total equals subtotal`() {
    val order = sampleOrder(totalPrice = 32_500.0, discount = 0.0)
    val data = ReceiptFormatter.format(order, sampleUser, garmentNames = emptyMap())
    assertEquals(null, data.discountFormatted)
    assertEquals("₦32,500", data.totalFormatted)
}
```

(Reuse the file's existing `sampleOrder`/`sampleUser` builders; add `discount`/`discountReason` params to the order builder.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReceiptFormatterTest*"`
Expected: FAIL — `ReceiptData` has no `subtotalFormatted`/`discountFormatted`.

- [ ] **Step 3: Implement the data + formatter changes**

In `ReceiptData.kt`, add three fields to `ReceiptData` (place near `totalFormatted`):

```kotlin
    val items: List<ReceiptItem>,
    /** Sum of item line totals before discount, pre-formatted (e.g. "₦32,500"). */
    val subtotalFormatted: String,
    /** Signed discount line (e.g. "−₦2,500"), or null when there is no discount. */
    val discountFormatted: String?,
    /** Optional free-text reason shown under the discount line. Null when unset. */
    val discountReason: String?,
    val totalFormatted: String,
```

In `ReceiptFormatter.format()`, change the totals block (≈L86-88) to:

```kotlin
            subtotalFormatted = "₦${formatPrice(order.totalPrice)}",
            discountFormatted = if (order.discount > 0.0) "−₦${formatPrice(order.discount)}" else null,
            discountReason = order.discountReason,
            totalFormatted = "₦${formatPrice(order.payableTotal)}",
            depositFormatted = "₦${formatPrice(order.depositPaid)}",
            balanceFormatted = "₦${formatPrice(order.balanceRemaining)}",
```

Note: the `−` is U+2212 MINUS SIGN (matches the design); keep it consistent in the renderers. `balanceRemaining` is already discount-aware after Task 1, and `resolveDocumentType` keeps working unchanged.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReceiptFormatterTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatter.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptFormatterTest.kt
git commit -m "feat(receipt): add subtotal/discount lines to ReceiptData + formatter"
```

---

## Task 5: iOS renderer — quantity row B + discount rows

No unit tests (CoreGraphics drawing); verified visually in the final smoke test. Apply to **both** render paths in `core/sharing/OrderReceiptSharer.ios.kt`: the image renderer (items ≈L176-198, totals ≈L205-215, height ≈L78) and the PDF renderer (items ≈L426-451, totals ≈L461-, height ≈L320-325).

- [ ] **Step 1: Replace the quantity>1 item block (image path, ≈L177-198)**

Replace the `if (item.quantity == 1) { … } else { … }` body. Keep the `== 1` branch as-is. Replace the `else` branch with Option B (name carries `×N`, line total on the right, unit price as a grey caption):

```kotlin
            data.items.forEach { item ->
                if (item.quantity == 1) {
                    drawText(item.garmentName, padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    drawTextRight(item.formattedPrice, width - padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    y += 30.0
                } else {
                    // Row 1: "<name> ×N" (left, bold) + line total (right, bold).
                    drawText("${item.garmentName} ×${item.quantity}", padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    drawTextRight(item.formattedPrice, width - padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    y += 22.0
                    // Row 2 (caption): "<unit price> each", muted, no right-column figure.
                    drawText("${item.formattedUnitPrice} each", padding, y, regularFont(12.0), darkColor("#7D7970"))
                    y += 26.0
                }
            }
```

(The two-line height total of 48.0 is unchanged from today's `22+26`, so `itemsHeight` at L78 — `if (item.quantity == 1) 30.0 else 74.0` — should be reduced to `else 48.0`. Verify the rendered block no longer overlaps the divider; adjust the constant if needed.)

- [ ] **Step 2: Insert Subtotal + Discount rows before Total (image path, ≈L205)**

Immediately before the existing `drawText("Total", …)` line, add:

```kotlin
            data.discountFormatted?.let { discount ->
                drawText("Subtotal", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawTextRight(data.subtotalFormatted, width - padding, y, regularFont(13.0), darkColor("#E5E3DF"))
                y += 22.0
                drawText("Discount", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawTextRight(discount, width - padding, y, boldFont(13.0), darkColor("#2D9E6B"))
                y += 20.0
                data.discountReason?.let { reason ->
                    drawText(reason, padding, y, regularFont(11.0), darkColor("#7D7970"))
                    y += 18.0
                }
            }
```

Then in the height estimate (≈L322-325) add headroom when a discount is present:

```kotlin
        if (data.discountFormatted != null) {
            estimatedHeight += 22.0 + 20.0 // Subtotal + Discount rows
            if (data.discountReason != null) estimatedHeight += 18.0
        }
```

- [ ] **Step 3: Mirror Steps 1–2 on the PDF path (≈L426-451 items, ≈L461 totals)**

Apply the identical structure using that path's font sizes (`boldFont(11.0)` / `regularFont(11.0)` / muted color `#7D7970`, light-document text `#1E1C1A`). Quantity-B else-branch:

```kotlin
                } else {
                    drawText("${item.garmentName} ×${item.quantity}", padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    drawTextRight(item.formattedPrice, pageWidth - padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    y += 16.0
                    drawText("${item.formattedUnitPrice} each", padding, y, regularFont(10.0), darkColor("#7D7970"))
                    y += 20.0
                }
```

Discount block before the PDF Total line (mirror Step 2 with `pageWidth - padding`, `regularFont(10.0)` for the reason, `#1E1C1A`/`#7D7970` colors). Update the PDF per-item height estimate accordingly (the `else 52f`-equivalent and the totals headroom).

- [ ] **Step 4: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt
git commit -m "feat(receipt-ios): quantity-row B + subtotal/discount rows on shared document"
```

---

## Task 6: Android renderer — quantity row B + discount rows

Apply the same two changes to `core/sharing/OrderReceiptSharer.android.kt`, both paths: image renderer (items ≈L209-, totals ≈L239-249, height ≈L103 `if (item.quantity == 1) 26f else 76f`) and PDF renderer (items ≈L363, totals below). Use the existing `Paint` objects (`bodyBoldPaint`, `bodyPaint`, the muted `unitLabelPaint` at ≈L60, and the `totalLabelPaint`). `canvas.drawText(text, x, y, paint)` draws left-aligned; right-aligned figures use the existing right-align helper/paint pattern already in the file — mirror how `formattedPrice` is currently drawn on the right.

- [ ] **Step 1: Replace the quantity>1 item block (image path)**

```kotlin
        data.items.forEach { item ->
            if (item.quantity == 1) {
                // unchanged
            } else {
                // Row 1: "<name> ×N" left + line total right (bold).
                canvas.drawText("${item.garmentName} ×${item.quantity}", padding, y, bodyBoldPaint)
                drawRight(canvas, item.formattedPrice, width - padding, y, bodyBoldPaint) // same right-draw used by qty==1
                y += 24f
                // Row 2 (caption): "<unit price> each", muted.
                canvas.drawText("${item.formattedUnitPrice} each", padding, y, unitLabelPaint)
                y += 28f
            }
        }
```

(Use whatever right-draw mechanism the qty==1 branch uses — replicate it, don't invent `drawRight` if the file uses a `Paint.Align.RIGHT` paint instead.) Update the height estimate at ≈L103 from `else 76f` to `else 52f` (24+28), and verify spacing.

- [ ] **Step 2: Insert Subtotal + Discount rows before Total (image path, ≈L239)**

Before `canvas.drawText("Total", padding, y, totalLabelPaint)`:

```kotlin
        data.discountFormatted?.let { discount ->
            canvas.drawText("Subtotal", padding, y, bodyPaint)
            drawRight(canvas, data.subtotalFormatted, width - padding, y, bodyPaint)
            y += 26f
            canvas.drawText("Discount", padding, y, bodyPaint)
            drawRight(canvas, discount, width - padding, y, bodyBoldPaint)
            y += 24f
            data.discountReason?.let { reason ->
                canvas.drawText(reason, padding, y, unitLabelPaint)
                y += 22f
            }
        }
```

Then bump the totals height (≈L107 `estimatedHeight += 30f * 3`): add `if (data.discountFormatted != null) estimatedHeight += 26f + 24f + (if (data.discountReason != null) 22f else 0f)`.

- [ ] **Step 3: Mirror Steps 1–2 on the PDF path**

Same structure with the PDF path's paints/sizes; update its per-item estimate (`else 52f`-equivalent at ≈L363) and totals headroom.

- [ ] **Step 4: Build Android**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt
git commit -m "feat(receipt-android): quantity-row B + subtotal/discount rows on shared document"
```

---

## Task 7: Order form — discount entry

**Files:**
- Modify: `feature/order/presentation/form/OrderFormState.kt` (add fields)
- Modify: `feature/order/presentation/form/OrderFormAction.kt` (add actions)
- Modify: `feature/order/presentation/form/OrderFormViewModel.kt` (handle actions + save)
- Modify: `feature/order/presentation/form/OrderFormScreen.kt` (Details step UI)
- Modify: `composeResources/.../values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Extend the existing `OrderFormViewModelTest` (mirror how it asserts a saved order via the fake repository). Drive the new actions, save, and assert the persisted `Order.discount` / `discountReason`:

```kotlin
@Test
fun `entered discount and reason are saved on the order`() = runTest {
    val vm = createViewModel() // existing helper; seeds a customer + one priced item
    vm.onAction(OrderFormAction.OnDiscountChange("2500"))
    vm.onAction(OrderFormAction.OnDiscountReasonChange("New customer"))
    vm.onAction(OrderFormAction.OnSaveClick)

    val saved = fakeOrderRepository.lastSavedOrder()
    assertEquals(2_500.0, saved.discount)
    assertEquals("New customer", saved.discountReason)
}

@Test
fun `blank discount saves zero and null reason`() = runTest {
    val vm = createViewModel()
    vm.onAction(OrderFormAction.OnSaveClick)
    val saved = fakeOrderRepository.lastSavedOrder()
    assertEquals(0.0, saved.discount)
    assertEquals(null, saved.discountReason)
}
```

(Match the test file's existing helper/fake names; if `lastSavedOrder()` doesn't exist, use whatever capture the sibling save-tests use.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderFormViewModelTest*"`
Expected: FAIL — no `OnDiscountChange` action / no `discount` state.

- [ ] **Step 3: Implement state, actions, VM, screen**

`OrderFormState.kt` — add next to `notes`/`depositPaid`:

```kotlin
    val discount: String = "",
    val discountReason: String = "",
```

`OrderFormAction.kt` — add next to `OnNotesChange`:

```kotlin
    data class OnDiscountChange(val discount: String) : OrderFormAction
    data class OnDiscountReasonChange(val reason: String) : OrderFormAction
```

`OrderFormViewModel.kt` — handle them in the `when` (mirror `OnNotesChange`; digit-filter the discount like the price field does):

```kotlin
            is OrderFormAction.OnDiscountChange -> {
                val digits = action.discount.filter { it.isDigit() }
                _state.update { it.copy(discount = digits) }
            }
            is OrderFormAction.OnDiscountReasonChange -> {
                _state.update { it.copy(discountReason = action.reason) }
            }
```

In `executeSave()` (≈L748), parse and clamp the discount to the subtotal, then pass both into the `Order(...)` constructor (next to `notes = …`):

```kotlin
            val totalPrice = orderItems.sumOf { it.price * it.quantity }
            val discount = (s.discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalPrice)
```
```kotlin
                totalPrice = totalPrice,
                discount = discount,
                discountReason = s.discountReason.trim().ifBlank { null },
```

If the form has deposit-vs-balance validation, ensure it compares the typed deposit against `totalPrice - discount` (the payable amount), not raw `totalPrice`.

`OrderFormScreen.kt` — in the Details step, after the Deposit field and before/with Notes, add two fields mirroring the deposit (`ThousandsSeparatorTransformation`, numeric keyboard) and notes patterns:

```kotlin
        OutlinedTextField(
            value = state.discount,
            onValueChange = { raw -> onAction(OrderFormAction.OnDiscountChange(raw.filter { it.isDigit() })) },
            label = { Text(stringResource(Res.string.order_form_discount_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ThousandsSeparatorTransformation,
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DesignTokens.space3))
        OutlinedTextField(
            value = state.discountReason,
            onValueChange = { onAction(OrderFormAction.OnDiscountReasonChange(it)) },
            label = { Text(stringResource(Res.string.order_form_discount_reason_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_discount_reason_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
```

`strings.xml` — add (no `\'`; use `&apos;` if any apostrophe needed):

```xml
    <string name="order_form_discount_label">Discount (₦)</string>
    <string name="order_form_discount_reason_label">Discount reason (optional)</string>
    <string name="order_form_discount_reason_placeholder">e.g. New customer</string>
```

When editing an existing order, prefill `discount`/`discountReason` from the loaded order in the same place the loader fills `notes`/`depositPaid` (format the discount as an integer string with no separator; the visual transformation adds separators).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OrderFormViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/ \
        composeApp/src/commonMain/composeResources/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt
git commit -m "feat(order-form): enter whole-order discount + reason in Details step"
```

---

## Task 8: Order detail — discount-aware Total/Balance

**Files:**
- Modify: `feature/order/presentation/detail/components/OrderPaymentCard.kt:66-160`
- Modify: `feature/order/presentation/detail/OrderDetailScreen.kt:532,1025,1056`
- Modify: `feature/order/presentation/detail/components/OrderHeroCard.kt:93,158,217`

No new unit test (Compose UI); covered by the existing previews + manual smoke. Each `@Composable` touched must keep its `@Preview`.

- [ ] **Step 1: Make OrderPaymentCard discount-aware**

Change the signature to take the order's `discount` (keep `totalPrice` as the subtotal so the card can show both):

```kotlin
@Composable
fun OrderPaymentCard(
    totalPrice: Double,
    discount: Double,
    payments: List<Payment>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payable = (totalPrice - discount).coerceAtLeast(0.0)
    val paid = payments.sumOf { it.amount }
    val balance = (payable - paid).coerceAtLeast(0.0)
```

In the financial row, the "Total" column shows `payable` (`"₦${formatPrice(payable)}"`). When `discount > 0`, render a small line above/below the row showing `Subtotal ₦… · Discount −₦…` using `stringResource(Res.string.order_detail_payment_subtotal)` / `order_detail_payment_discount` (add these strings). Update the preview(s) at the bottom to pass `discount = 0.0` and add one preview with `discount = 2_500.0`.

- [ ] **Step 2: Update call sites to pass discount + payable**

`OrderDetailScreen.kt:1025,1056` — pass `discount = order.discount` to `OrderPaymentCard`. At L532 (`totalFormatted = "₦${formatPrice(it.totalPrice)}"` for the share/summary), use `it.payableTotal`. `OrderHeroCard` (L93/158 params, L217 display) — the hero "amount" should read the payable total: pass `totalPrice = order.payableTotal` from its call site, or add a `payableTotal` param; keep preview defaults compiling.

- [ ] **Step 3: Build Android + compile iOS**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/ \
        composeApp/src/commonMain/composeResources/
git commit -m "feat(order-detail): show discount + payable total on payment card and hero"
```

---

## Task 9: Order list + dashboard summary use payableTotal

**Files:**
- Modify: `feature/order/presentation/list/PaymentDisplay.kt:18`
- Modify: `feature/order/presentation/list/PaymentStatusText.kt:18-21`
- Modify: `feature/order/presentation/list/OrderListScreen.kt:690,696`
- Modify: `feature/dashboard/presentation/DashboardViewModel.kt:464`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplayTest.kt` (create if absent)

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `deposit covering discounted total reads as Paid`() {
    // amountOwed is the payable (post-discount) total
    assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 30_000.0, amountOwed = 30_000.0))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PaymentDisplayTest*"`
Expected: FAIL — parameter is named `totalPrice`, not `amountOwed`.

- [ ] **Step 3: Implement**

`PaymentDisplay.kt` — rename the parameter for clarity (semantics: amount owed, now post-discount):

```kotlin
fun formatPaymentStatus(depositPaid: Double, amountOwed: Double): PaymentDisplay = when {
    amountOwed <= 0.0 -> PaymentDisplay.Paid
    depositPaid >= amountOwed -> PaymentDisplay.Paid
    depositPaid > 0.0 -> PaymentDisplay.Partial(amountPaid = depositPaid)
    else -> PaymentDisplay.Unpaid
}
```

`PaymentStatusText.kt` — rename its `totalPrice` param to `amountOwed` and forward it. `OrderListScreen.kt` — at L690 display `order.payableTotal` (`"₦${formatPrice(order.payableTotal)}"`); at L696 pass `amountOwed = order.payableTotal`. `DashboardViewModel.kt:464` — `totalAmount = firstOrder?.payableTotal ?: 0.0`.

- [ ] **Step 4: Run the test + build**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PaymentDisplayTest*" && ./gradlew :composeApp:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/ \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplayTest.kt
git commit -m "fix(order-list,dashboard): show payable (post-discount) amount + status"
```

---

## Task 10: Full verification + PR

- [ ] **Step 1: Sweep for missed totalPrice/balanceRemaining call sites**

Run: `grep -rn "totalPrice\|balanceRemaining" composeApp/src/commonMain/kotlin/` and confirm each remaining raw `totalPrice` genuinely means "subtotal before discount" (item-sum, seed fixtures, the form's own sum). Anything meaning "amount owed" must be `payableTotal`; anything meaning "collected" must be `depositPaid`.

- [ ] **Step 2: Full test suite**

Run: `./gradlew :composeApp:testDebugUnitTest; echo "exit=$?"`
Expected: all green, `exit=0` (capture the code directly — piping hides failures).

- [ ] **Step 3: Detekt**

Run: `./gradlew detekt; echo "exit=$?"`
Expected: `exit=0`. If a touched screen file trips `TooManyFunctions`, add `@file:Suppress("TooManyFunctions")` rather than splitting a cohesive file.

- [ ] **Step 4: iOS compile (mandatory gate)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: `exit=0`. Then do a clean Xcode build of `iosApp` if the renderer drawing changed materially (build-ios CI compiles the framework, not the Swift target).

- [ ] **Step 5: Manual smoke test (Daniel is QA)** — Android + iOS:
  1. New order, qty-2 item → receipt row reads `Blouse ×2` / `₦4,000 each` / `₦8,000` on the right; no "Unit price / Price for 2" stack.
  2. Add ₦2,500 discount + reason "New customer" → detail Total = ₦30,000, Balance off the discounted total; payment card shows Subtotal + Discount.
  3. Share as **image** and **PDF** on both platforms → `Subtotal / Discount / Total` align and don't overlap the divider; reason shows in grey.
  4. Edit order, clear discount → receipt reverts to a single Total line (byte-for-byte today's layout); detail Total = ₦32,500.
  5. Record full payment of the discounted balance → order list/detail read "Paid"; Reports → Revenue reflects the **net** amount and Collected = cash received.

- [ ] **Step 6: Open the PR**

```bash
git push -u origin feat/receipt-discount
gh pr create --base main --title "feat(receipt): quantity-row clarity + whole-order discount" \
  --body "Implements docs/superpowers/specs/2026-06-16-receipt-discount-and-quantity-clarity-design.md. Quantity rows now read '<name> ×N' + '<unit> each' + bold line total; adds an optional whole-order naira discount (Subtotal/Discount/Total on the receipt) wired through balances and net-of-discount revenue. Includes the manual smoke test above."
```

Then run the required reviews before merge: Cursor Bugbot (auto) **and** `codex review` (pre-push hook). Pay attention to the accounting-ripple area — client/server constant drift and collected/revenue semantics are exactly what Bugbot catches.

---

## Self-Review notes
- **Spec coverage:** quantity-row B (Tasks 5–6), discount data (1–2), form entry (7), receipt display (4–6), detail/list/dashboard (8–9), revenue ripple (3,9), free-for-all (no entitlement gate anywhere), tests + smoke (1–10). All spec sections map to a task.
- **Type consistency:** `payableTotal`/`discount`/`discountReason` defined in Task 1 are referenced identically downstream; `formatPaymentStatus` param renamed to `amountOwed` in Task 9 and all call sites updated there.
- **Out of scope (unchanged):** per-item discounts, percentages, stacked discounts, debug-menu entry, tier gating.
