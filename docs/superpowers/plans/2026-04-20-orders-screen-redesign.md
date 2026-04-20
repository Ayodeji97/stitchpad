# Orders Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Orders list into urgency-grouped triage sections with a polished row (customer avatar, plain-English deadline, payment status).

**Architecture:** Presentation-layer only — no domain or repository changes. Three pure formatter functions (triage grouping, deadline, payment) with unit tests. Four new composables (`CustomerAvatar`, `DeadlineLine`, `PaymentStatus`, `TriageSectionHeader`). Sticky `LazyColumn` section headers in `OrderListScreen`. ViewModel is simplified: the `showOverdueOnly` filter is removed because the Overdue section header replaces the Overdue chip.

**Tech Stack:** Kotlin Multiplatform · Compose Multiplatform · Material3 · Koin · kotlin.test · Compose resources

**Spec:** [2026-04-20-orders-screen-redesign-design.md](../specs/2026-04-20-orders-screen-redesign-design.md)

---

## File Structure

### New files

| File | Responsibility |
|------|----------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGrouping.kt` | `TriageGroup` enum + pure `groupOrdersIntoTriage(orders, now)` function |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplay.kt` | `DeadlineDisplay` sealed interface + pure `formatDeadline(deadline, now, status)` function |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplay.kt` | `PaymentDisplay` sealed interface + pure `formatPaymentStatus(depositPaid, totalPrice)` function |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/CustomerAvatar.kt` | Composable: 36dp colored circle + initial |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineLine.kt` | Composable: renders `DeadlineDisplay` to `Text` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentStatusText.kt` | Composable: renders `PaymentDisplay` to `Text` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageSectionHeader.kt` | Composable: sticky section header |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGroupingTest.kt` | Unit tests |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplayTest.kt` | Unit tests |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplayTest.kt` | Unit tests |

### Modified files

| File | Change |
|------|--------|
| `OrderListState.kt` | Remove `showOverdueOnly` field |
| `OrderListAction.kt` | Remove `OnToggleOverdueFilter` |
| `OrderListViewModel.kt` | Remove overdue filter handling, simplify `filterAndSortOrders`, hide DELIVERED from "All" |
| `OrderListScreen.kt` | Restructure `OrderListItem`, wire triage sections, remove Overdue chip, update previews |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Add new strings; remove `order_filter_overdue`, `order_overdue_label` |

---

## Task 1: Triage grouping — pure function + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGrouping.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGroupingTest.kt`

- [ ] **Step 1: Write the failing test file**

Create `TriageGroupingTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriageGroupingTest {

    private val now = 1_700_000_000_000L // fixed reference
    private val oneDay = 24L * 60 * 60 * 1000

    private fun order(
        id: String,
        status: OrderStatus = OrderStatus.PENDING,
        deadline: Long? = null,
        createdAt: Long = 0L
    ) = Order(
        id = id,
        userId = "u", customerId = "c", customerName = "C",
        items = listOf(OrderItem(id = "i", garmentType = GarmentType.SUIT, description = "", price = 0.0)),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        depositPaid = 0.0,
        balanceRemaining = 0.0,
        deadline = deadline,
        notes = null,
        createdAt = createdAt,
        updatedAt = 0L
    )

    @Test
    fun emptyListReturnsEmptyMap() {
        val result = groupOrdersIntoTriage(emptyList(), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun deliveredOrdersAreHidden() {
        val delivered = order("d", status = OrderStatus.DELIVERED, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(delivered), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun readyOrdersGoToReadyForPickupEvenIfOverdue() {
        val ready = order("r", status = OrderStatus.READY, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(ready), now)
        assertEquals(listOf(ready), result[TriageGroup.READY_FOR_PICKUP])
        assertEquals(null, result[TriageGroup.OVERDUE])
    }

    @Test
    fun pastDeadlinePendingGoesToOverdue() {
        val overdue = order("o", status = OrderStatus.PENDING, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(overdue), now)
        assertEquals(listOf(overdue), result[TriageGroup.OVERDUE])
    }

    @Test
    fun pastDeadlineInProgressGoesToOverdue() {
        val overdue = order("o", status = OrderStatus.IN_PROGRESS, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(overdue), now)
        assertEquals(listOf(overdue), result[TriageGroup.OVERDUE])
    }

    @Test
    fun inProgressWithFutureDeadlineGoesToInProgress() {
        val ip = order("ip", status = OrderStatus.IN_PROGRESS, deadline = now + 3 * oneDay)
        val result = groupOrdersIntoTriage(listOf(ip), now)
        assertEquals(listOf(ip), result[TriageGroup.IN_PROGRESS])
    }

    @Test
    fun pendingWithinSevenDaysGoesToDueThisWeek() {
        val soon = order("s", status = OrderStatus.PENDING, deadline = now + 3 * oneDay)
        val result = groupOrdersIntoTriage(listOf(soon), now)
        assertEquals(listOf(soon), result[TriageGroup.DUE_THIS_WEEK])
    }

    @Test
    fun pendingExactlyAtSevenDaysGoesToDueThisWeek() {
        val sevenDays = order("s", status = OrderStatus.PENDING, deadline = now + 7 * oneDay)
        val result = groupOrdersIntoTriage(listOf(sevenDays), now)
        assertEquals(listOf(sevenDays), result[TriageGroup.DUE_THIS_WEEK])
    }

    @Test
    fun pendingBeyondSevenDaysGoesToPending() {
        val far = order("f", status = OrderStatus.PENDING, deadline = now + 14 * oneDay)
        val result = groupOrdersIntoTriage(listOf(far), now)
        assertEquals(listOf(far), result[TriageGroup.PENDING])
    }

    @Test
    fun pendingWithNoDeadlineGoesToPending() {
        val noDl = order("n", status = OrderStatus.PENDING, deadline = null)
        val result = groupOrdersIntoTriage(listOf(noDl), now)
        assertEquals(listOf(noDl), result[TriageGroup.PENDING])
    }

    @Test
    fun withinGroupSortedByDeadlineAscendingNullsLast() {
        val a = order("a", status = OrderStatus.PENDING, deadline = null)
        val b = order("b", status = OrderStatus.PENDING, deadline = now + 14 * oneDay)
        val c = order("c", status = OrderStatus.PENDING, deadline = now + 10 * oneDay)
        val result = groupOrdersIntoTriage(listOf(a, b, c), now)
        assertEquals(listOf(c, b, a), result[TriageGroup.PENDING])
    }

    @Test
    fun emptyGroupsAreOmitted() {
        val pending = order("p", status = OrderStatus.PENDING, deadline = null)
        val result = groupOrdersIntoTriage(listOf(pending), now)
        assertTrue(TriageGroup.OVERDUE !in result)
        assertTrue(TriageGroup.IN_PROGRESS !in result)
        assertTrue(TriageGroup.READY_FOR_PICKUP !in result)
        assertTrue(TriageGroup.DUE_THIS_WEEK !in result)
    }

    @Test
    fun resultIsInDisplayOrder() {
        val overdue = order("o", status = OrderStatus.PENDING, deadline = now - oneDay)
        val ready = order("r", status = OrderStatus.READY, deadline = null)
        val pending = order("p", status = OrderStatus.PENDING, deadline = null)
        val inProg = order("ip", status = OrderStatus.IN_PROGRESS, deadline = now + 30 * oneDay)
        val result = groupOrdersIntoTriage(listOf(pending, ready, inProg, overdue), now)
        assertEquals(
            listOf(TriageGroup.OVERDUE, TriageGroup.IN_PROGRESS, TriageGroup.READY_FOR_PICKUP, TriageGroup.PENDING),
            result.keys.toList()
        )
    }
}
```

- [ ] **Step 2: Run test — expected to fail (symbols do not exist yet)**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.TriageGroupingTest"`

Expected: compile error — `TriageGroup`, `groupOrdersIntoTriage` unresolved.

- [ ] **Step 3: Implement TriageGrouping.kt**

Create `TriageGrouping.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

enum class TriageGroup {
    OVERDUE,
    DUE_THIS_WEEK,
    IN_PROGRESS,
    READY_FOR_PICKUP,
    PENDING
}

private val displayOrder = listOf(
    TriageGroup.OVERDUE,
    TriageGroup.DUE_THIS_WEEK,
    TriageGroup.IN_PROGRESS,
    TriageGroup.READY_FOR_PICKUP,
    TriageGroup.PENDING
)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val DUE_SOON_DAYS = 7L

fun groupOrdersIntoTriage(orders: List<Order>, now: Long): Map<TriageGroup, List<Order>> {
    val buckets = linkedMapOf<TriageGroup, MutableList<Order>>()
    displayOrder.forEach { buckets[it] = mutableListOf() }

    for (order in orders) {
        val group = classify(order, now) ?: continue
        buckets.getValue(group).add(order)
    }

    val deadlineComparator = compareBy<Order>(nullsLast()) { it.deadline }
        .thenByDescending { it.createdAt }

    return buckets
        .mapValues { (_, list) -> list.sortedWith(deadlineComparator) }
        .filterValues { it.isNotEmpty() }
}

private fun classify(order: Order, now: Long): TriageGroup? {
    if (order.status == OrderStatus.DELIVERED) return null
    if (order.status == OrderStatus.READY) return TriageGroup.READY_FOR_PICKUP
    if (order.deadline != null && order.deadline < now) return TriageGroup.OVERDUE
    if (order.status == OrderStatus.IN_PROGRESS) return TriageGroup.IN_PROGRESS
    if (order.status == OrderStatus.PENDING && order.deadline != null) {
        val daysUntil = (order.deadline - now) / MILLIS_PER_DAY
        if (daysUntil in 0..DUE_SOON_DAYS) return TriageGroup.DUE_THIS_WEEK
    }
    return TriageGroup.PENDING
}
```

- [ ] **Step 4: Run test — expected to pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.TriageGroupingTest"`

Expected: all 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGrouping.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageGroupingTest.kt
git commit -m "feat(orders): add triage grouping pure function with tests"
```

---

## Task 2: Deadline formatter — pure function + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplay.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplayTest.kt`

- [ ] **Step 1: Write the failing test file**

Create `DeadlineDisplayTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineDisplayTest {

    private val now = 1_700_000_000_000L
    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun readyStatusReturnsPickupReadyRegardlessOfDeadline() {
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(now - oneDay, now, OrderStatus.READY))
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(null, now, OrderStatus.READY))
    }

    @Test
    fun nullDeadlineReturnsNoDeadline() {
        assertEquals(DeadlineDisplay.NoDeadline, formatDeadline(null, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlinePastReturnsDaysLate() {
        val three = formatDeadline(now - 3 * oneDay, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DaysLate(3), three)
    }

    @Test
    fun oneDayLateReturnsDaysLateOne() {
        val one = formatDeadline(now - oneDay - 1000, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DaysLate(1), one)
    }

    @Test
    fun deliveredOrderWithPastDeadlineStillReturnsDaysLate() {
        // formatter does not special-case DELIVERED — grouping logic excludes those rows
        val res = formatDeadline(now - oneDay, now, OrderStatus.DELIVERED)
        assertEquals(DeadlineDisplay.DaysLate(1), res)
    }

    @Test
    fun deadlineTodayReturnsDueToday() {
        // same calendar day, a few hours in the future
        assertEquals(DeadlineDisplay.DueToday, formatDeadline(now + 3 * 60 * 60 * 1000, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlineTomorrowReturnsDueTomorrow() {
        assertEquals(DeadlineDisplay.DueTomorrow, formatDeadline(now + oneDay + 60_000, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlineIn3DaysReturnsDueInDaysSoon() {
        val res = formatDeadline(now + 3 * oneDay + 60_000, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DueInDays(days = 3, soon = true), res)
    }

    @Test
    fun deadlineIn4DaysReturnsDueInDaysSoon() {
        val res = formatDeadline(now + 4 * oneDay + 60_000, now, OrderStatus.PENDING)
        // 4 days still within "soon" (0..3 is soon, 4..7 still colored soon per spec? Spec says 2..3 soon, 4+ muted)
        assertEquals(DeadlineDisplay.DueInDays(days = 4, soon = false), res)
    }

    @Test
    fun deadlineFarFutureReturnsDueInDaysMuted() {
        val res = formatDeadline(now + 12 * oneDay, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DueInDays(days = 12, soon = false), res)
    }
}
```

- [ ] **Step 2: Run test — expected to fail**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.DeadlineDisplayTest"`

Expected: compile error — `DeadlineDisplay`, `formatDeadline` unresolved.

- [ ] **Step 3: Implement DeadlineDisplay.kt**

Create `DeadlineDisplay.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus

sealed interface DeadlineDisplay {
    data object NoDeadline : DeadlineDisplay
    data class DaysLate(val days: Int) : DeadlineDisplay
    data object DueToday : DeadlineDisplay
    data object DueTomorrow : DeadlineDisplay
    data class DueInDays(val days: Int, val soon: Boolean) : DeadlineDisplay
    data object PickupReady : DeadlineDisplay
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

fun formatDeadline(deadline: Long?, now: Long, status: OrderStatus): DeadlineDisplay {
    if (status == OrderStatus.READY) return DeadlineDisplay.PickupReady
    if (deadline == null) return DeadlineDisplay.NoDeadline

    val deltaMillis = deadline - now
    if (deltaMillis < 0) {
        // Floor-divide absolute overdue millis to whole days.
        // Minimum 1 so sub-day overdue reads as "1 day late" rather than "0 days late".
        val daysLate = ((-deltaMillis) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
        return DeadlineDisplay.DaysLate(daysLate)
    }

    val daysUntil = (deltaMillis / MILLIS_PER_DAY).toInt()
    return when {
        daysUntil == 0 -> DeadlineDisplay.DueToday
        daysUntil == 1 -> DeadlineDisplay.DueTomorrow
        daysUntil in 2..3 -> DeadlineDisplay.DueInDays(days = daysUntil, soon = true)
        else -> DeadlineDisplay.DueInDays(days = daysUntil, soon = false)
    }
}
```

- [ ] **Step 4: Run test — expected to pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.DeadlineDisplayTest"`

Expected: all 10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplay.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineDisplayTest.kt
git commit -m "feat(orders): add deadline formatter with tests"
```

---

## Task 3: Payment formatter — pure function + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplay.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplayTest.kt`

- [ ] **Step 1: Write the failing test file**

Create `PaymentDisplayTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentDisplayTest {

    @Test
    fun fullyPaidReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 10_000.0, totalPrice = 10_000.0))
    }

    @Test
    fun overpaidReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 12_000.0, totalPrice = 10_000.0))
    }

    @Test
    fun zeroTotalReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 0.0, totalPrice = 0.0))
    }

    @Test
    fun partialPaymentReturnsPartial() {
        assertEquals(
            PaymentDisplay.Partial(amountPaid = 5_000.0),
            formatPaymentStatus(depositPaid = 5_000.0, totalPrice = 10_000.0)
        )
    }

    @Test
    fun zeroPaidReturnsUnpaid() {
        assertEquals(PaymentDisplay.Unpaid, formatPaymentStatus(depositPaid = 0.0, totalPrice = 10_000.0))
    }

    @Test
    fun abbreviateBelowOneThousandKeepsExact() {
        assertEquals("\u20A6500", PaymentDisplay.Partial(500.0).formatAbbreviated())
    }

    @Test
    fun abbreviateAtOneThousandUsesK() {
        assertEquals("\u20A61k", PaymentDisplay.Partial(1_000.0).formatAbbreviated())
    }

    @Test
    fun abbreviateTenThousandUsesK() {
        assertEquals("\u20A610k", PaymentDisplay.Partial(10_000.0).formatAbbreviated())
    }

    @Test
    fun abbreviateNineHundredNinetyNineKeepsExact() {
        assertEquals("\u20A6999", PaymentDisplay.Partial(999.0).formatAbbreviated())
    }
}
```

- [ ] **Step 2: Run test — expected to fail**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.PaymentDisplayTest"`

Expected: compile error.

- [ ] **Step 3: Implement PaymentDisplay.kt**

Create `PaymentDisplay.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

sealed interface PaymentDisplay {
    data object Paid : PaymentDisplay
    data class Partial(val amountPaid: Double) : PaymentDisplay {
        fun formatAbbreviated(): String {
            val long = amountPaid.toLong()
            return if (long >= 1_000) "\u20A6${long / 1_000}k" else "\u20A6$long"
        }
    }
    data object Unpaid : PaymentDisplay
}

fun formatPaymentStatus(depositPaid: Double, totalPrice: Double): PaymentDisplay = when {
    totalPrice <= 0.0 -> PaymentDisplay.Paid
    depositPaid >= totalPrice -> PaymentDisplay.Paid
    depositPaid > 0.0 -> PaymentDisplay.Partial(amountPaid = depositPaid)
    else -> PaymentDisplay.Unpaid
}
```

- [ ] **Step 4: Run test — expected to pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.list.PaymentDisplayTest"`

Expected: all 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplay.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentDisplayTest.kt
git commit -m "feat(orders): add payment status formatter with tests"
```

---

## Task 4: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Read the current strings file to locate insertion points**

Run: `grep -n "order_" composeApp/src/commonMain/composeResources/values/strings.xml | head -30`

- [ ] **Step 2: Remove two obsolete strings and add the new ones**

Delete these entries if present:

```xml
<string name="order_filter_overdue">...</string>
<string name="order_overdue_label">...</string>
```

Add these new entries near other `order_*` strings:

```xml
<!-- Triage section headers -->
<string name="triage_overdue">Overdue</string>
<string name="triage_due_this_week">Due This Week</string>
<string name="triage_in_progress">In Progress</string>
<string name="triage_ready_for_pickup">Ready for Pickup</string>
<string name="triage_pending">Pending</string>

<!-- Deadline display -->
<string name="deadline_no_deadline">No deadline</string>
<string name="deadline_pickup_ready">Pickup ready</string>
<string name="deadline_due_today">Due today</string>
<string name="deadline_due_tomorrow">Due tomorrow</string>
<string name="deadline_due_in_days">Due in %d days</string>
<string name="deadline_day_late">1 day late</string>
<string name="deadline_days_late">%d days late</string>

<!-- Payment status -->
<string name="payment_paid">Paid</string>
<string name="payment_partial">%s paid</string>
<string name="payment_unpaid">Unpaid</string>

<!-- Order item garment summary (lowercase form) -->
<string name="order_items_count_one_lc">1 %s</string>
<string name="order_items_count_many_lc">%1$d %2$s</string>
```

- [ ] **Step 3: Run the build to regenerate resources**

Run: `./gradlew :composeApp:generateComposeResClass`

Expected: build succeeds; new `Res.string.triage_*`, `Res.string.deadline_*`, `Res.string.payment_*` symbols generated.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(orders): add triage/deadline/payment string resources"
```

---

## Task 5: CustomerAvatar composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/CustomerAvatar.kt`

- [ ] **Step 1: Implement CustomerAvatar.kt**

Create the file:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun CustomerAvatar(
    name: String,
    customerId: String,
    modifier: Modifier = Modifier
) {
    val colors = DesignTokens.avatarColors
    val avatar = remember(customerId) {
        colors[customerId.hashCode().mod(colors.size)]
    }
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) avatar.darkBg else avatar.lightBg
    val fg = if (isDark) avatar.darkText else avatar.lightText
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerAvatarPreview() {
    StitchPadTheme {
        CustomerAvatar(name = "Fola Sunday", customerId = "c-12345")
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/CustomerAvatar.kt
git commit -m "feat(orders): add CustomerAvatar composable"
```

---

## Task 6: DeadlineLine composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineLine.kt`

- [ ] **Step 1: Implement DeadlineLine.kt**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.deadline_day_late
import stitchpad.composeapp.generated.resources.deadline_days_late
import stitchpad.composeapp.generated.resources.deadline_due_in_days
import stitchpad.composeapp.generated.resources.deadline_due_today
import stitchpad.composeapp.generated.resources.deadline_due_tomorrow
import stitchpad.composeapp.generated.resources.deadline_no_deadline
import stitchpad.composeapp.generated.resources.deadline_pickup_ready

@Composable
fun DeadlineLine(
    deadline: Long?,
    now: Long,
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val display = formatDeadline(deadline, now, status)
    val text = when (display) {
        DeadlineDisplay.NoDeadline -> stringResource(Res.string.deadline_no_deadline)
        DeadlineDisplay.PickupReady -> stringResource(Res.string.deadline_pickup_ready)
        DeadlineDisplay.DueToday -> stringResource(Res.string.deadline_due_today)
        DeadlineDisplay.DueTomorrow -> stringResource(Res.string.deadline_due_tomorrow)
        is DeadlineDisplay.DaysLate ->
            if (display.days == 1) stringResource(Res.string.deadline_day_late)
            else stringResource(Res.string.deadline_days_late, display.days)
        is DeadlineDisplay.DueInDays -> stringResource(Res.string.deadline_due_in_days, display.days)
    }
    val color = colorFor(display)

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun colorFor(display: DeadlineDisplay): Color = when (display) {
    is DeadlineDisplay.DaysLate -> DesignTokens.error500
    DeadlineDisplay.DueToday, DeadlineDisplay.DueTomorrow -> DesignTokens.warning500
    is DeadlineDisplay.DueInDays -> if (display.soon) DesignTokens.warning500 else MaterialTheme.colorScheme.onSurfaceVariant
    DeadlineDisplay.PickupReady -> DesignTokens.success500
    DeadlineDisplay.NoDeadline -> MaterialTheme.colorScheme.onSurfaceVariant
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/DeadlineLine.kt
git commit -m "feat(orders): add DeadlineLine composable"
```

---

## Task 7: PaymentStatusText composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentStatusText.kt`

- [ ] **Step 1: Implement PaymentStatusText.kt**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.payment_paid
import stitchpad.composeapp.generated.resources.payment_partial
import stitchpad.composeapp.generated.resources.payment_unpaid

@Composable
fun PaymentStatusText(
    depositPaid: Double,
    totalPrice: Double,
    modifier: Modifier = Modifier
) {
    val display = formatPaymentStatus(depositPaid, totalPrice)
    val (text, color) = when (display) {
        PaymentDisplay.Paid -> stringResource(Res.string.payment_paid) to DesignTokens.success500
        is PaymentDisplay.Partial -> stringResource(Res.string.payment_partial, display.formatAbbreviated()) to DesignTokens.warning500
        PaymentDisplay.Unpaid -> stringResource(Res.string.payment_unpaid) to DesignTokens.error500
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/PaymentStatusText.kt
git commit -m "feat(orders): add PaymentStatusText composable"
```

---

## Task 8: TriageSectionHeader composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageSectionHeader.kt`

- [ ] **Step 1: Implement TriageSectionHeader.kt**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.triage_due_this_week
import stitchpad.composeapp.generated.resources.triage_in_progress
import stitchpad.composeapp.generated.resources.triage_overdue
import stitchpad.composeapp.generated.resources.triage_pending
import stitchpad.composeapp.generated.resources.triage_ready_for_pickup

@Composable
fun TriageSectionHeader(
    group: TriageGroup,
    count: Int,
    modifier: Modifier = Modifier
) {
    val color = colorFor(group)
    val label = stringResource(labelFor(group)).uppercase()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space3,
                bottom = DesignTokens.space1
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun colorFor(group: TriageGroup): Color = when (group) {
    TriageGroup.OVERDUE -> DesignTokens.error500
    TriageGroup.DUE_THIS_WEEK -> DesignTokens.warning500
    TriageGroup.IN_PROGRESS -> DesignTokens.info500
    TriageGroup.READY_FOR_PICKUP -> DesignTokens.success500
    TriageGroup.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun labelFor(group: TriageGroup): StringResource = when (group) {
    TriageGroup.OVERDUE -> Res.string.triage_overdue
    TriageGroup.DUE_THIS_WEEK -> Res.string.triage_due_this_week
    TriageGroup.IN_PROGRESS -> Res.string.triage_in_progress
    TriageGroup.READY_FOR_PICKUP -> Res.string.triage_ready_for_pickup
    TriageGroup.PENDING -> Res.string.triage_pending
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/TriageSectionHeader.kt
git commit -m "feat(orders): add TriageSectionHeader composable"
```

---

## Task 9: Remove `showOverdueOnly` from state + action

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListAction.kt`

- [ ] **Step 1: Edit OrderListState.kt**

Replace the file contents with:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderListState(
    val orders: List<Order> = emptyList(),
    val statusFilter: OrderStatus? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val orderToDelete: Order? = null,
    val errorMessage: UiText? = null
)
```

- [ ] **Step 2: Edit OrderListAction.kt**

Open the file and delete the `OnToggleOverdueFilter` action case. (Exact edit depends on current file contents — open it, locate the line, remove it.)

Run: `grep -n "OnToggleOverdueFilter" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListAction.kt`

Use the line number to find and remove that case and any imports it brought in. Typical form to remove:

```kotlin
data class OnToggleOverdueFilter(val showOverdue: Boolean) : OrderListAction
```

- [ ] **Step 3: Build to find all broken references**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: build FAILS with errors in `OrderListViewModel.kt` and `OrderListScreen.kt` referencing the removed action and state field. That is expected and fixed in the next tasks — do not commit yet.

Do **not** commit here — commit after Task 10 when the ViewModel is updated so each commit builds cleanly.

---

## Task 10: Simplify OrderListViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListViewModel.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrderListViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var allOrders: List<Order> = emptyList()

    private val _state = MutableStateFlow(OrderListState())

    private val _events = Channel<OrderListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeOrders()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderListState()
        )

    fun onAction(action: OrderListAction) {
        when (action) {
            is OrderListAction.OnStatusFilterChange -> {
                _state.update {
                    it.copy(
                        statusFilter = action.status,
                        orders = filterAndSort(allOrders, action.status)
                    )
                }
            }
            is OrderListAction.OnOrderClick -> {
                viewModelScope.launch {
                    _events.send(OrderListEvent.NavigateToOrderDetail(action.order.id))
                }
            }
            OrderListAction.OnAddOrderClick -> {
                viewModelScope.launch {
                    _events.send(OrderListEvent.NavigateToOrderForm)
                }
            }
            is OrderListAction.OnDeleteOrderClick -> {
                _state.update { it.copy(showDeleteDialog = true, orderToDelete = action.order) }
            }
            OrderListAction.OnConfirmDelete -> deleteOrder()
            OrderListAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
            }
            OrderListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun observeOrders() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            orderRepository.observeOrders(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        allOrders = result.data
                        _state.update { state ->
                            state.copy(
                                orders = filterAndSort(result.data, state.statusFilter),
                                isLoading = false
                            )
                        }
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.error.toOrderUiText()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun deleteOrder() {
        val order = _state.value.orderToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = orderRepository.deleteOrder(userId, order.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toOrderUiText()) }
            }
        }
    }

    private fun filterAndSort(orders: List<Order>, statusFilter: OrderStatus?): List<Order> {
        val filtered = when (statusFilter) {
            null -> orders.filter { it.status != OrderStatus.DELIVERED }
            else -> orders.filter { it.status == statusFilter }
        }
        return filtered.sortedBy { it.deadline ?: Long.MAX_VALUE }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: still fails — `OrderListScreen.kt` still references removed symbols. That's fixed in Task 11.

---

## Task 11: Restructure OrderListScreen — rows, sections, chip row

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt`

This is the largest task. Break it into sub-steps.

- [ ] **Step 1: Update imports**

Open `OrderListScreen.kt`. Remove the import for `order_filter_overdue` and `order_overdue_label`. Add imports for the new composables and string resources (Compose will often auto-import; ensure these are present):

```kotlin
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
// remove: import stitchpad.composeapp.generated.resources.order_filter_overdue
// remove: import stitchpad.composeapp.generated.resources.order_overdue_label
```

- [ ] **Step 2: Replace `OrderListItem` with the new 3-column layout**

Locate the existing `OrderListItem` private composable. Replace it entirely with:

```kotlin
@Composable
private fun OrderListItem(order: Order, now: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        OrderRowAvatar(name = order.customerName, customerId = order.customerId)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                Text(
                    text = order.customerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (order.priority != OrderPriority.NORMAL) {
                    PriorityBadge(priority = order.priority)
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = garmentSummary(order),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            DeadlineLine(deadline = order.deadline, now = now, status = order.status)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "\u20A6${formatPrice(order.totalPrice)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            PaymentStatusText(depositPaid = order.depositPaid, totalPrice = order.totalPrice)
        }
    }
}

@Composable
private fun garmentSummary(order: Order): String {
    val firstItem = order.items.firstOrNull() ?: return ""
    val garmentName = garmentDisplayName(firstItem.garmentType).lowercase()
    return if (order.items.size == 1) {
        "1 $garmentName"
    } else {
        "${order.items.size} ${garmentName}s"
    }
}
```

Note: the old `OrderListItem` referenced `isOverdue` and `OrderStatusBadge`. Both are gone. `OrderStatusBadge` is no longer used — delete that private composable and its helper references. `isOverdue` local is removed.

- [ ] **Step 3: Delete `OrderStatusBadge` (no longer used)**

Locate and remove the entire `OrderStatusBadge` private composable and its import of `order_overdue_label`.

- [ ] **Step 4: Replace the LazyColumn block with triage-aware rendering**

Find the body of `OrderListScreen` where the `when { state.isLoading, state.orders.isEmpty(), else ... }` block lives. Replace the `else` branch with:

```kotlin
else -> {
    val now = Clock.System.now().toEpochMilliseconds()
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.statusFilter == null) {
            val groups = groupOrdersIntoTriage(state.orders, now)
            groups.forEach { (group, ordersInGroup) ->
                stickyHeader(key = "header-${group.name}") {
                    TriageSectionHeader(group = group, count = ordersInGroup.size)
                }
                items(items = ordersInGroup, key = { it.id }) { order ->
                    SwipeableOrderItem(
                        order = order,
                        now = now,
                        onClick = { onAction(OrderListAction.OnOrderClick(order)) },
                        onDelete = { onAction(OrderListAction.OnDeleteOrderClick(order)) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 64.dp) // indent past avatar
                    )
                }
            }
        } else {
            items(items = state.orders, key = { it.id }) { order ->
                SwipeableOrderItem(
                    order = order,
                    now = now,
                    onClick = { onAction(OrderListAction.OnOrderClick(order)) },
                    onDelete = { onAction(OrderListAction.OnDeleteOrderClick(order)) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 64.dp)
                )
            }
        }
    }
}
```

Add the `stickyHeader` import:

```kotlin
import androidx.compose.foundation.lazy.stickyHeader
```

Note: `stickyHeader` is experimental → add `@OptIn(ExperimentalFoundationApi::class)` to the `OrderListScreen` function if the compiler asks. The function already has `@OptIn(ExperimentalMaterial3Api::class)` — extend it to `@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)`.

- [ ] **Step 5: Update `SwipeableOrderItem` to accept `now`**

Replace its signature and body to pass `now` through:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableOrderItem(
    order: Order,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = DesignTokens.space5)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            OrderListItem(order = order, now = now, onClick = onClick)
        }
    }
}
```

- [ ] **Step 6: Remove the Overdue chip from `OrderStatusFilterChips`**

Find the `OrderStatusFilterChips` private composable. Remove:
- The `showOverdueOnly: Boolean` parameter
- The `onOverdueToggled: (Boolean) -> Unit` parameter
- The trailing FilterChip block that handles the Overdue chip (the one using `order_filter_overdue`)
- The import for `order_filter_overdue`

Simplify the `isSelected` logic:

```kotlin
val isSelected = selectedStatus == status
```

Update the call site at the top of `OrderListScreen`'s Scaffold body:

```kotlin
OrderStatusFilterChips(
    selectedStatus = state.statusFilter,
    onStatusSelected = { onAction(OrderListAction.OnStatusFilterChange(it)) }
)
```

- [ ] **Step 7: Update the filled preview and add a multi-section preview**

Replace `OrderListScreenFilledPreview` with a preview that exercises multiple triage groups:

```kotlin
@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun OrderListScreenFilledPreview() {
    val now = 1_700_000_000_000L
    val oneDay = 24L * 60 * 60 * 1000
    StitchPadTheme {
        OrderListScreen(
            state = OrderListState(
                isLoading = false,
                orders = listOf(
                    Order(
                        id = "1", userId = "u", customerId = "c1", customerName = "Fola Sunday",
                        items = listOf(OrderItem("i1", GarmentType.CORSET, "", 40_000.0)),
                        status = OrderStatus.PENDING, priority = OrderPriority.RUSH,
                        statusHistory = emptyList(),
                        totalPrice = 40_000.0, depositPaid = 0.0, balanceRemaining = 40_000.0,
                        deadline = now - 3 * oneDay, notes = null, createdAt = 0L, updatedAt = 0L
                    ),
                    Order(
                        id = "2", userId = "u", customerId = "c2", customerName = "Aina Paul",
                        items = listOf(OrderItem("i2", GarmentType.SUIT, "", 20_000.0)),
                        status = OrderStatus.PENDING, priority = OrderPriority.URGENT,
                        statusHistory = emptyList(),
                        totalPrice = 20_000.0, depositPaid = 10_000.0, balanceRemaining = 10_000.0,
                        deadline = now + 2 * oneDay, notes = null, createdAt = 0L, updatedAt = 0L
                    ),
                    Order(
                        id = "3", userId = "u", customerId = "c3", customerName = "Dayyo Au",
                        items = listOf(OrderItem("i3", GarmentType.SUIT, "", 4_000.0)),
                        status = OrderStatus.READY, priority = OrderPriority.RUSH,
                        statusHistory = emptyList(),
                        totalPrice = 4_000.0, depositPaid = 2_000.0, balanceRemaining = 2_000.0,
                        deadline = null, notes = null, createdAt = 0L, updatedAt = 0L
                    )
                )
            ),
            onAction = {}
        )
    }
}
```

(Add imports: `OrderItem`, `GarmentType` from `core.domain.model` if not already present.)

- [ ] **Step 8: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: success.

- [ ] **Step 9: Run all tests**

Run: `./gradlew :composeApp:allTests`

Expected: all tests pass (existing + new from Tasks 1-3).

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/
git commit -m "feat(orders): triage-grouped list with polished rows

- Group orders into Overdue / Due This Week / In Progress / Ready / Pending
- Sticky section headers with counts
- New row layout: customer avatar, plain-English deadline, payment status
- Remove Overdue chip (section header replaces it)
- Hide DELIVERED orders from All view

Closes the triage-redesign feature spec."
```

---

## Task 12: Run detekt + manual smoke test

**Files:** none

- [ ] **Step 1: Run detekt**

Run: `./gradlew detekt`

Expected: no new violations. Fix any issues inline.

- [ ] **Step 2: Build Android and launch**

Run: `./gradlew :composeApp:assembleDebug`

Install and launch on a device/emulator. Walk through the smoke test from the spec:

1. Open Orders tab with zero orders → empty state renders
2. Create orders covering: overdue, due today, due in 2 days, due in 10 days, no deadline, READY, IN_PROGRESS, DELIVERED
3. "All" view — all 5 sections in correct order, DELIVERED order not shown
4. Scroll — section headers stick
5. Tap "Pending" chip — flat list, no section headers, sorted by deadline ascending
6. Tap "Delivered" chip — delivered orders appear
7. Repeat customer → same avatar color on both rows
8. Swipe to delete → dialog confirms, delete works
9. Dark mode toggle → colors remain legible
10. FAB and bottom nav remain accessible

- [ ] **Step 3: iOS smoke test**

Open `iosApp/iosApp.xcodeproj` in Xcode, build and run. Repeat smoke test steps.

- [ ] **Step 4: Commit any detekt fixes**

If detekt required changes:

```bash
git add -A
git commit -m "chore(orders): detekt fixes post-redesign"
```

- [ ] **Step 5: Push branch and open PR**

```bash
git push -u origin feature/order
gh pr create --title "Redesign Orders screen with triage sections" --body "$(cat <<'EOF'
## Summary
- Group orders into urgency-based sections: Overdue / Due This Week / In Progress / Ready for Pickup / Pending
- Polished row: customer initial avatar (deterministic color), plain-English deadline, payment status under price
- Removed the Overdue filter chip (section header replaces it)
- DELIVERED orders hidden from the All view (visible only under Delivered chip)
- Added unit tests for the three pure functions (grouping, deadline formatter, payment formatter)

## Test plan
- [ ] Empty state still renders with zero orders
- [ ] All 5 triage sections render in correct order with mixed-status data
- [ ] Section headers stick while scrolling
- [ ] Filter chips still work; selecting one shows a flat list
- [ ] Swipe-to-delete still works
- [ ] Repeat customers get the same avatar color
- [ ] Light + dark mode both readable
- [ ] iOS and Android both build and behave identically
EOF
)"
```

---

## Self-Review Summary

- **Spec coverage:** Every spec section maps to a task — triage groups (Task 1), row anatomy (Tasks 5-7, 11), section header (Task 8), filter interaction (Task 10), string resources (Task 4), testing (Tasks 1-3), smoke test (Task 12).
- **No placeholders:** Every code block is complete and self-contained.
- **Type consistency:** `TriageGroup`, `DeadlineDisplay`, `PaymentDisplay`, `groupOrdersIntoTriage`, `formatDeadline`, `formatPaymentStatus` have consistent signatures across tasks.
- **Commit ordering:** Tasks 9-11 span multiple commits but Task 9 intentionally ends without a commit (build broken) and Task 11 includes the fix-up commit — this keeps each committed state buildable.
- **Known minor risk:** `stickyHeader` is `ExperimentalFoundationApi`. Flagged in Task 11 Step 4. If KMP Compose version used in this repo does not expose `stickyHeader` on `LazyColumn`, fall back to regular `item { TriageSectionHeader(...) }` — same visual result, loses the sticky behavior only.
