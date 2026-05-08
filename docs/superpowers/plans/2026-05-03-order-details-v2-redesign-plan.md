# Order Details V2 Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the V2 redesign of the Order Details screen — illustrated stack, inline notes, six-stage timeline, real payment history, overflow-menu delete — backed by minimal data-layer additions to `Order` (sub-status + payments + archive flag).

**Architecture:** Single `:composeApp` module, package-based MVVM + Clean. Domain → Data → Presentation, MVI on every screen. Compose Multiplatform UI shared between Android + iOS. Firestore via GitLive Firebase SDK. Koin for DI. `Result<T, E>` for all expected failures. New code follows the dashboard V2 card style (`Surface` + `BorderStroke`, not `Card`; `MaterialTheme.colorScheme.outlineVariant` for borders) shipped in PR #31.

**Tech Stack:** Kotlin 2.x · Compose Multiplatform · Material 3 · Koin · GitLive Firebase Kotlin SDK · `kotlin.test` for unit tests · detekt for lint.

**Spec:** `docs/superpowers/specs/2026-05-03-order-details-v2-redesign-design.md`
**Preview:** `preview/order-details-v2.html`
**Branch:** `feature/order-details-redesign` (already cut from main; PR #31 dashboard V2 already merged)

---

## ⛔ Pre-Push Review Gate (READ BEFORE EVERY COMMIT)

**Daniel's standing instruction:** before any `git push`, every change must pass this gate. This is non-negotiable. If a check fails, fix the root cause — never bypass with `--no-verify` or by skipping a step.

The gate runs in this order — do not skip steps:

| # | Check | Command | Pass = |
|---|-------|---------|--------|
| 1 | **Detekt** (lint + style) | `./gradlew detekt` | exit 0, zero violations |
| 2 | **All unit tests** | `./gradlew :composeApp:allTests` | every test green |
| 3 | **Android compile** | `./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL |
| 4 | **iOS compile** (per `feedback_kotlin_native_epoch_days` memory) | `./gradlew :composeApp:compileKotlinIosArm64` (or open `iosApp/iosApp.xcodeproj` and build) | BUILD SUCCESSFUL |
| 5 | **Manual smoke test** — every reference state from spec § Verification | run on emulator + physical Pixel | every step verified |
| 6 | **Self-review checklist** below | walk it line-by-line | every box ticked |

### Self-review checklist (run with fresh eyes, per the QA workflow memory)

Read your diff as if a stranger wrote it. For each new/changed file, ask:

- [ ] **Crash risks**
  - Any `!!` operators? Replace with safe call + early return, or `checkNotNull` with a meaningful message.
  - Any `.first()` / `.last()` / `[0]` on a list that could be empty? Guard with `firstOrNull()` / `getOrNull()`.
  - Any `String.toDouble()` / `.toInt()` on user input or Firestore strings? Use `toDoubleOrNull()` / `toIntOrNull()`.
  - Any new `runCatching {}` that silently swallows? Make sure the `getOrNull()` / `getOrDefault(...)` is the *correct* fallback, not a placeholder.
  - Any new coroutine launch without a scope tied to lifecycle? Should be `viewModelScope.launch` (VM) or `LaunchedEffect` (composable).
- [ ] **Bugs**
  - Off-by-one on lists, ranges, or status transitions?
  - Equality on `Double` (`==`)? Use a tolerance or compare via `compareTo`.
  - Mutable state in `remember { }` that should be `rememberSaveable` or hoisted to VM? (per CLAUDE.md: all state in ViewModel except Compose-internal.)
  - State updates outside the `_state.update { }` lambda? Race risk.
  - Stale closures over `_state.value` inside long-running coroutines?
- [ ] **Code smells**
  - Magic numbers — use `DesignTokens.spaceN` / `radiusN`.
  - Hardcoded strings — use `Res.string.xxx` (per CLAUDE.md rule).
  - Comments that explain *what* the code does — delete (per CLAUDE.md rule). Keep only *why* comments.
  - Dead code, commented-out blocks, stray `println(...)` / `Log.d(...)`.
  - Duplicated branches in a `when` — collapse with shared blocks.
  - Files > ~400 lines doing too much — extract a helper file in the same package.
- [ ] **Conventions**
  - Implementations descriptively named (`FirebaseFooBar`, not `FooBarImpl`) per CLAUDE.md.
  - DTOs separate from domain, mappers as extension functions in `core/data/mapper/`.
  - Errors typed via `Result<T, DataError>` — no thrown exceptions for expected failures.
  - Every new `Screen` composable has a `@Preview`.
  - Every new user-visible string is in `strings.xml`.
- [ ] **Diff hygiene**
  - No accidental file deletions (`git status` should match expectations).
  - No `.idea/`, `.DS_Store`, or other local-config files staged.
  - No secrets (`google-services.json`, `GoogleService-Info.plist`) staged — these are gitignored but check.
  - PR description includes manual smoke test markdown checklist (per `feedback_qa_smoke_tests` memory).

**If anything fails, do not push. Fix it, re-run the gate from step 1.**

---

## File Structure

### Files to create

| Path | Purpose |
|------|---------|
| `core/domain/model/Payment.kt` | `Payment` data class + `PaymentMethod` + `PaymentType` enums |
| `core/data/dto/PaymentDto.kt` | Firestore wire shape for `Payment` |
| `core/data/mapper/PaymentMapper.kt` | `Payment ↔ PaymentDto` extension functions |
| `commonTest/.../core/data/mapper/OrderMapperTest.kt` | New: round-trip + sub-status + payments + legacy synthesis |
| `commonTest/.../core/data/mapper/PaymentMapperTest.kt` | New: round-trip + invalid enum fallback |
| `feature/order/presentation/detail/StatusTransitions.kt` | Pure function: `nextStatusTransitions(currentStatus, currentSubStatus): List<StatusTransition>` |
| `commonTest/.../feature/order/presentation/detail/StatusTransitionsTest.kt` | Test the transition matrix |
| `feature/order/presentation/detail/PrimaryCtaResolver.kt` | Pure function: `resolvePrimaryCta(order, isOverdue): CtaPair` |
| `commonTest/.../feature/order/presentation/detail/PrimaryCtaResolverTest.kt` | Test all status × subStatus × overdue × balance combinations |
| `feature/order/presentation/detail/components/OrderHeroCard.kt` | Hero card composable + previews |
| `feature/order/presentation/detail/components/OrderCustomerCard.kt` | Customer card with chips |
| `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` | Garment details card |
| `feature/order/presentation/detail/components/OrderPaymentCard.kt` | Payment card with collapsible history |
| `feature/order/presentation/detail/components/OrderProductionTimeline.kt` | 6-node timeline composable |
| `feature/order/presentation/detail/components/OrderMeasurementsPreviewCard.kt` | 3-tile measurements card |
| `feature/order/presentation/detail/components/OrderNotesCard.kt` | Inline notes editor |
| `feature/order/presentation/detail/components/OrderFooterCaption.kt` | Order # + date footer |
| `feature/order/presentation/detail/components/OrderArchiveButton.kt` | Tinted bottom Archive button |
| `feature/order/presentation/detail/components/StatusTransitionSheet.kt` | `ModalBottomSheet` for status picker |
| `feature/order/presentation/detail/components/RecordPaymentDialogV2.kt` | Extends record-payment dialog with method + type segmented controls |
| `feature/order/presentation/detail/components/OrderDetailOverflowMenu.kt` | `DropdownMenu` for top-bar `⋮` (Duplicate / Archive / Delete) |

### Files to modify

| Path | Change |
|------|--------|
| `core/domain/model/Order.kt` | Add `OrderSubStatus` enum; add `subStatus`, `payments`, `archivedAt` fields to `Order`. Convert `depositPaid` and `balanceRemaining` to computed properties. |
| `core/data/dto/OrderDto.kt` | Add `subStatus: String?`, `payments: List<PaymentDto>`, `archivedAt: Long?`. Keep `depositPaid` field for legacy migration on read. |
| `core/data/mapper/OrderMapper.kt` | Map new fields; synthesise legacy `Payment` from `depositPaid` if `payments` empty; force `subStatus = null` when `status != IN_PROGRESS`. |
| `core/domain/repository/OrderRepository.kt` | Add `recordPayment`, `updateSubStatus`, `updateNotes`, `archiveOrder` method signatures. |
| `feature/order/data/FirebaseOrderRepository.kt` | Implement the 4 new methods using existing `runTransaction` pattern. Filter `archivedAt == null` from `observeOrders` (NOT `observeOrder` — single-doc lookup must still work for archived orders so the customer detail screen can navigate into them). |
| `feature/order/presentation/detail/OrderDetailState.kt` | Add new fields per spec § ViewModel & State. |
| `feature/order/presentation/detail/OrderDetailAction.kt` | Add new action cases per spec. |
| `feature/order/presentation/detail/OrderDetailEvent.kt` | Add new event cases per spec. |
| `feature/order/presentation/detail/OrderDetailViewModel.kt` | Inject `CustomerRepository` + `MeasurementRepository`; observe customer & measurement; handle every new action; replace `submitPayment` to use `recordPayment(...)`; handle archive; handle inline notes. |
| `feature/order/presentation/detail/OrderDetailScreen.kt` | Replace `OrderDetailContent` with the new card composition. Update top app bar to add `⋮`. Remove the bare bottom Delete button. Wire up the new sheet + dialog overlays. |
| `di/OrderModule.kt` | No change to bindings — Koin auto-resolves `CustomerRepository` + `MeasurementRepository` for the VM constructor (they're already singletons in their own modules). |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Add all new strings from spec § String Resources. |

### Files to delete

None. The current `OrderDetailContent` and helper composables (`SectionHeader`, `FinancialRow`, etc.) inside `OrderDetailScreen.kt` are *replaced inline* — their definitions get removed as part of Task 6.2. No standalone files are deleted.

---

## Task Map

| Phase | Tasks | Independent? |
|-------|-------|--------------|
| 1. Data layer | 1.1 — 1.5 | Yes — internal, no UI |
| 2. Repository | 2.1 — 2.3 | Yes — depends on phase 1 |
| 3. Strings | 3.1 | Yes — independent |
| 4. Composables | 4.1 — 4.10 | Yes per composable — preview-driven |
| 5. VM surface | 5.1 — 5.5 | Depends on phase 1 + 2 |
| 6. Screen rewire | 6.1 — 6.4 | Depends on all previous |
| 7. Pre-push verification | 7.1 — 7.5 | Final gate before PR |

---

## Phase 1: Data Layer Additions

### Task 1.1: Add `OrderSubStatus` enum

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`

- [ ] **Step 1: Add enum to `Order.kt`**

After the existing `OrderPriority` enum (around line 14), add:

```kotlin
/**
 * Sub-stages within IN_PROGRESS that match how tailors narrate work
 * (cutting → sewing → fitting). Only meaningful when [Order.status] is
 * [OrderStatus.IN_PROGRESS]; null otherwise.
 */
enum class OrderSubStatus {
    CUTTING,
    SEWING,
    FITTING,
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt
git commit -m "feat(domain): add OrderSubStatus enum for in-progress sub-stages"
```

---

### Task 1.2: Add `Payment` model + enums

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Payment.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Single payment recorded against an [Order]. The list of payments on an
 * order is the source of truth for what's been paid; [Order.depositPaid]
 * derives from sum of payments.
 */
data class Payment(
    val id: String,
    val amount: Double,
    val method: PaymentMethod,
    val type: PaymentType,
    val recordedAt: Long,
    val note: String? = null,
)

enum class PaymentMethod {
    CASH,
    TRANSFER,
    POS,
    OTHER,
}

enum class PaymentType {
    DEPOSIT,
    PROGRESS,
    FINAL,
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Payment.kt
git commit -m "feat(domain): add Payment model with method + type enums"
```

---

### Task 1.3: Extend `Order` with new fields + computed properties

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt:32-48`

- [ ] **Step 1: Replace the `Order` data class definition**

Replace the existing `Order` class (lines 32-48) with:

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
    val payments: List<Payment> = emptyList(),
    val deadline: Long?,
    val notes: String?,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Sum of all recorded payments. Replaces the prior persisted `depositPaid` field. */
    val depositPaid: Double get() = payments.sumOf { it.amount }

    /** Outstanding balance. Always recomputed from [totalPrice] and [payments]. */
    val balanceRemaining: Double get() = (totalPrice - depositPaid).coerceAtLeast(0.0)
}
```

- [ ] **Step 2: Run the existing tests to surface call-site breakage**

Run: `./gradlew :composeApp:allTests`
Expected: FAIL — call sites that pass `depositPaid` / `balanceRemaining` to the constructor (e.g. tests, mapper, VM) won't compile.

- [ ] **Step 3: Fix the constructor call sites**

For each compile error, replace `depositPaid = X, balanceRemaining = Y` in `Order(...)` constructors with `payments = listOf(Payment(id = "test-deposit", amount = X, method = PaymentMethod.OTHER, type = PaymentType.DEPOSIT, recordedAt = 0L))`. The `balanceRemaining` parameter goes away entirely (it's computed).

Likely call sites (run `grep -rn "depositPaid =" composeApp/src --include="*.kt"`):
- `feature/order/presentation/detail/OrderDetailViewModel.kt:280-283` — the `submitPayment` flow uses `order.copy(depositPaid = ..., balanceRemaining = ...)`. This will be replaced entirely in Task 5.4. For now, replace with `order.copy(payments = order.payments + Payment(...))`.
- Any test fixture builders.

- [ ] **Step 4: Run tests again**

Run: `./gradlew :composeApp:allTests`
Expected: All existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt
git add -u  # picks up the call-site fixes
git commit -m "feat(domain): add subStatus + payments + archivedAt; derive depositPaid from payments"
```

---

### Task 1.4: Update `OrderDto` + `PaymentDto`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/PaymentDto.kt`

- [ ] **Step 1: Create `PaymentDto.kt`**

```kotlin
package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaymentDto(
    val id: String = "",
    val amount: Double = 0.0,
    val method: String = "OTHER",
    val type: String = "DEPOSIT",
    val recordedAt: Long = 0L,
    val note: String? = null,
)
```

- [ ] **Step 2: Modify `OrderDto.kt`**

Replace the existing `OrderDto` class with:

```kotlin
@Serializable
data class OrderDto(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "PENDING",
    val subStatus: String? = null,
    val priority: String = "NORMAL",
    val totalPrice: Double = 0.0,
    /**
     * Legacy field: kept for read-only migration of existing documents that
     * pre-date the [payments] list. New writes do not populate this — the
     * mapper synthesises a single [PaymentDto] from it on read instead.
     */
    val depositPaid: Double = 0.0,
    val payments: List<PaymentDto> = emptyList(),
    val deadline: Long? = null,
    val notes: String? = null,
    val archivedAt: Long? = null,
    val items: List<OrderItemDto> = emptyList(),
    val statusHistory: List<StatusChangeDto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
```

Note: `balanceRemaining` is removed from the DTO entirely — it was always derived and persisting it was a footgun for drift.

- [ ] **Step 3: Verify compile (mapper will fail next, that's expected)**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: FAIL on `OrderMapper.kt` — `balanceRemaining` no longer in DTO. Continue to Task 1.5.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/PaymentDto.kt
git commit -m "feat(data): extend OrderDto with subStatus + payments + archivedAt; add PaymentDto"
```

---

### Task 1.5: Write `PaymentMapper` + update `OrderMapper` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/PaymentMapper.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PaymentMapperTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`

- [ ] **Step 1: Write the failing `PaymentMapperTest`**

Create `commonTest/.../PaymentMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMapperTest {

    @Test
    fun dtoToPayment_mapsAllFields() {
        val dto = PaymentDto(
            id = "p1",
            amount = 40_000.0,
            method = "TRANSFER",
            type = "DEPOSIT",
            recordedAt = 1_700_000_000_000L,
            note = "Bank transfer ref 1234",
        )
        val payment = dto.toPayment()
        assertEquals("p1", payment.id)
        assertEquals(40_000.0, payment.amount)
        assertEquals(PaymentMethod.TRANSFER, payment.method)
        assertEquals(PaymentType.DEPOSIT, payment.type)
        assertEquals(1_700_000_000_000L, payment.recordedAt)
        assertEquals("Bank transfer ref 1234", payment.note)
    }

    @Test
    fun dtoWithUnknownMethod_fallsBackToOther() {
        val dto = PaymentDto(method = "BITCOIN", type = "FINAL")
        val payment = dto.toPayment()
        assertEquals(PaymentMethod.OTHER, payment.method)
        assertEquals(PaymentType.FINAL, payment.type)
    }

    @Test
    fun dtoWithUnknownType_fallsBackToDeposit() {
        val dto = PaymentDto(method = "CASH", type = "GIFT")
        val payment = dto.toPayment()
        assertEquals(PaymentType.DEPOSIT, payment.type)
    }

    @Test
    fun paymentToDto_roundTrips() {
        val payment = Payment(
            id = "p2",
            amount = 25_000.0,
            method = PaymentMethod.CASH,
            type = PaymentType.PROGRESS,
            recordedAt = 1_700_000_100_000L,
            note = null,
        )
        val dto = payment.toPaymentDto()
        assertEquals(payment, dto.toPayment())
    }
}
```

- [ ] **Step 2: Run test — should fail to compile (no mapper yet)**

Run: `./gradlew :composeApp:jvmTest --tests "*PaymentMapperTest*"`
Expected: COMPILATION ERROR — `toPayment()` and `toPaymentDto()` unresolved.

- [ ] **Step 3: Implement `PaymentMapper.kt`**

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType

fun PaymentDto.toPayment(): Payment = Payment(
    id = id,
    amount = amount,
    method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.OTHER),
    type = runCatching { PaymentType.valueOf(type) }.getOrDefault(PaymentType.DEPOSIT),
    recordedAt = recordedAt,
    note = note,
)

fun Payment.toPaymentDto(): PaymentDto = PaymentDto(
    id = id,
    amount = amount,
    method = method.name,
    type = type.name,
    recordedAt = recordedAt,
    note = note,
)
```

- [ ] **Step 4: Re-run test — should pass**

Run: `./gradlew :composeApp:jvmTest --tests "*PaymentMapperTest*"`
Expected: 4 tests passed.

- [ ] **Step 5: Update `OrderMapper.kt` to handle new fields + legacy synthesis**

Replace `toOrder` and `toOrderDto` in `OrderMapper.kt`:

```kotlin
fun OrderDto.toOrder(userId: String): Order {
    val parsedStatus = runCatching { OrderStatus.valueOf(status) }
        .getOrDefault(OrderStatus.PENDING)
    val parsedSubStatus = if (parsedStatus == OrderStatus.IN_PROGRESS) {
        subStatus?.let { runCatching { OrderSubStatus.valueOf(it) }.getOrNull() }
    } else {
        // Force null when not IN_PROGRESS so an inconsistent document can't
        // surface a misleading sub-stage in the UI.
        null
    }
    val mappedPayments = payments.map { it.toPayment() }
    // Legacy migration: synthesise a single deposit Payment from depositPaid
    // when no payment list exists, so existing orders display sensibly.
    val finalPayments = if (mappedPayments.isEmpty() && depositPaid > 0.0) {
        listOf(
            Payment(
                id = "legacy-deposit",
                amount = depositPaid,
                method = PaymentMethod.OTHER,
                type = PaymentType.DEPOSIT,
                recordedAt = createdAt,
                note = null,
            ),
        )
    } else {
        mappedPayments
    }
    return Order(
        id = id,
        userId = userId,
        customerId = customerId,
        customerName = customerName,
        items = items.map { it.toOrderItem() },
        status = parsedStatus,
        subStatus = parsedSubStatus,
        priority = runCatching { OrderPriority.valueOf(priority) }
            .getOrDefault(OrderPriority.NORMAL),
        statusHistory = statusHistory.map { it.toStatusChange() },
        totalPrice = totalPrice,
        payments = finalPayments,
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Order.toOrderDto(): OrderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return OrderDto(
        id = id,
        customerId = customerId,
        customerName = customerName,
        status = status.name,
        subStatus = subStatus?.name,
        priority = priority.name,
        totalPrice = totalPrice,
        // Do NOT write depositPaid back — it's derived from payments now.
        // Older clients reading this document will see depositPaid = 0.0
        // and fall through to the legacy synthesis path on the read side.
        depositPaid = 0.0,
        payments = payments.map { it.toPaymentDto() },
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        items = items.map { it.toOrderItemDto() },
        statusHistory = statusHistory.map { it.toStatusChangeDto() },
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
    )
}
```

Add the new imports at the top of `OrderMapper.kt`:

```kotlin
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
```

- [ ] **Step 6: Write `OrderMapperTest.kt`**

Create `commonTest/.../OrderMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderMapperTest {

    @Test
    fun dtoToOrder_mapsSubStatusWhenInProgress() {
        val dto = OrderDto(status = "IN_PROGRESS", subStatus = "FITTING")
        val order = dto.toOrder("u1")
        assertEquals(OrderSubStatus.FITTING, order.subStatus)
    }

    @Test
    fun dtoToOrder_dropsSubStatusWhenStatusIsNotInProgress() {
        val dto = OrderDto(status = "READY", subStatus = "FITTING")
        val order = dto.toOrder("u1")
        assertNull(order.subStatus, "subStatus should be cleared when status != IN_PROGRESS")
    }

    @Test
    fun dtoToOrder_unknownSubStatusFallsBackToNull() {
        val dto = OrderDto(status = "IN_PROGRESS", subStatus = "QUILTING")
        val order = dto.toOrder("u1")
        assertNull(order.subStatus)
    }

    @Test
    fun dtoToOrder_synthesisesLegacyDepositWhenPaymentsEmpty() {
        val dto = OrderDto(
            depositPaid = 30_000.0,
            payments = emptyList(),
            createdAt = 1_700_000_000_000L,
        )
        val order = dto.toOrder("u1")
        assertEquals(1, order.payments.size)
        val p = order.payments.single()
        assertEquals(30_000.0, p.amount)
        assertEquals(PaymentMethod.OTHER, p.method)
        assertEquals(PaymentType.DEPOSIT, p.type)
        assertEquals(1_700_000_000_000L, p.recordedAt)
        assertEquals("legacy-deposit", p.id)
    }

    @Test
    fun dtoToOrder_doesNotSynthesiseWhenPaymentsListIsPopulated() {
        val dto = OrderDto(
            depositPaid = 30_000.0,
            payments = listOf(
                PaymentDto(id = "p1", amount = 20_000.0, method = "CASH", type = "DEPOSIT"),
            ),
        )
        val order = dto.toOrder("u1")
        // Real payments win — legacy depositPaid is ignored when payments exist.
        assertEquals(1, order.payments.size)
        assertEquals("p1", order.payments.single().id)
        assertEquals(20_000.0, order.depositPaid)
    }

    @Test
    fun dtoToOrder_zeroDepositPaidNoSynthesis() {
        val dto = OrderDto(depositPaid = 0.0, payments = emptyList())
        val order = dto.toOrder("u1")
        assertTrue(order.payments.isEmpty())
    }

    @Test
    fun dtoToOrder_archivedAtRoundTrips() {
        val dto = OrderDto(archivedAt = 1_700_000_500_000L)
        val order = dto.toOrder("u1")
        assertEquals(1_700_000_500_000L, order.archivedAt)
    }

    @Test
    fun orderToDto_doesNotPersistDepositPaid() {
        // Build an order with payments via the round-trip path so we can assert
        // the write-side does not duplicate state into the legacy field.
        val dtoIn = OrderDto(
            payments = listOf(
                PaymentDto(id = "p1", amount = 50_000.0, method = "TRANSFER", type = "DEPOSIT"),
            ),
            totalPrice = 100_000.0,
        )
        val order = dtoIn.toOrder("u1")
        val dtoOut = order.toOrderDto()
        assertEquals(0.0, dtoOut.depositPaid, "depositPaid must NOT be persisted; it's derived")
        assertEquals(1, dtoOut.payments.size)
    }

    @Test
    fun orderToDto_omitsSubStatusWhenNull() {
        val dtoIn = OrderDto(status = "PENDING", subStatus = null)
        val order = dtoIn.toOrder("u1")
        val dtoOut = order.toOrderDto()
        assertNull(dtoOut.subStatus)
    }
}
```

- [ ] **Step 7: Run mapper tests**

Run: `./gradlew :composeApp:jvmTest --tests "*OrderMapperTest*" --tests "*PaymentMapperTest*"`
Expected: All tests pass.

- [ ] **Step 8: Run the full test suite to catch any regressions**

Run: `./gradlew :composeApp:allTests`
Expected: All green.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/PaymentMapper.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PaymentMapperTest.kt
git commit -m "feat(data): map subStatus + payments + archivedAt with legacy depositPaid synthesis"
```

---

## Phase 2: Repository Layer

### Task 2.1: Extend `OrderRepository` interface

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`

- [ ] **Step 1: Add 4 method signatures**

After the existing `deleteOrder` (line 21), insert:

```kotlin
suspend fun recordPayment(
    userId: String,
    orderId: String,
    payment: Payment,
): EmptyResult<DataError.Network>

suspend fun updateSubStatus(
    userId: String,
    orderId: String,
    subStatus: OrderSubStatus?,
): EmptyResult<DataError.Network>

suspend fun updateNotes(
    userId: String,
    orderId: String,
    notes: String?,
): EmptyResult<DataError.Network>

suspend fun archiveOrder(
    userId: String,
    orderId: String,
): EmptyResult<DataError.Network>
```

Add the imports at the top:

```kotlin
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
```

- [ ] **Step 2: Verify compile (FirebaseOrderRepository will fail next)**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: FAIL — `FirebaseOrderRepository` doesn't implement the new methods. Continue to Task 2.2.

---

### Task 2.2: Implement new methods in `FirebaseOrderRepository`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`

- [ ] **Step 1: Add imports**

At the top with the existing imports:

```kotlin
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.mapper.toPaymentDto
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
```

- [ ] **Step 2: Implement `recordPayment` using the existing `runTransaction` pattern**

After `updateOrderStatus` (line 145), insert:

```kotlin
override suspend fun recordPayment(
    userId: String,
    orderId: String,
    payment: Payment,
): EmptyResult<DataError.Network> {
    return try {
        val docRef = ordersCollection(userId).document(orderId)
        val notFound = firestore.runTransaction {
            val snap = get(docRef)
            if (!snap.exists) return@runTransaction true
            val dto = snap.data<OrderDto>()
            val now = Clock.System.now().toEpochMilliseconds()
            val updatedDto = dto.copy(
                payments = dto.payments + payment.toPaymentDto(),
                // Zero out the legacy field on first write — once the new
                // payments list is populated the read-side ignores it anyway.
                depositPaid = 0.0,
                updatedAt = now,
            )
            set(docRef, updatedDto)
            false
        }
        if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "recordPayment failed orderId=$orderId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}

override suspend fun updateSubStatus(
    userId: String,
    orderId: String,
    subStatus: OrderSubStatus?,
): EmptyResult<DataError.Network> {
    return try {
        val docRef = ordersCollection(userId).document(orderId)
        val notFound = firestore.runTransaction {
            val snap = get(docRef)
            if (!snap.exists) return@runTransaction true
            val dto = snap.data<OrderDto>()
            val now = Clock.System.now().toEpochMilliseconds()
            set(docRef, dto.copy(subStatus = subStatus?.name, updatedAt = now))
            false
        }
        if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "updateSubStatus failed orderId=$orderId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}

override suspend fun updateNotes(
    userId: String,
    orderId: String,
    notes: String?,
): EmptyResult<DataError.Network> {
    return try {
        val docRef = ordersCollection(userId).document(orderId)
        val notFound = firestore.runTransaction {
            val snap = get(docRef)
            if (!snap.exists) return@runTransaction true
            val dto = snap.data<OrderDto>()
            val now = Clock.System.now().toEpochMilliseconds()
            set(docRef, dto.copy(notes = notes, updatedAt = now))
            false
        }
        if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "updateNotes failed orderId=$orderId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}

override suspend fun archiveOrder(
    userId: String,
    orderId: String,
): EmptyResult<DataError.Network> {
    return try {
        val docRef = ordersCollection(userId).document(orderId)
        val notFound = firestore.runTransaction {
            val snap = get(docRef)
            if (!snap.exists) return@runTransaction true
            val dto = snap.data<OrderDto>()
            val now = Clock.System.now().toEpochMilliseconds()
            set(docRef, dto.copy(archivedAt = now, updatedAt = now))
            false
        }
        if (notFound) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "archiveOrder failed orderId=$orderId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt
git commit -m "feat(data): add recordPayment, updateSubStatus, updateNotes, archiveOrder"
```

---

### Task 2.3: Filter archived orders from `observeOrders`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt:35-47`

- [ ] **Step 1: Add the filter**

Replace the body of `observeOrders` with:

```kotlin
override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
    ordersCollection(userId)
        .snapshots()
        .map { snapshot ->
            val orders = snapshot.documents
                .mapNotNull { doc ->
                    runCatching { doc.data<OrderDto>().toOrder(userId) }.getOrNull()
                }
                // Filter archived orders client-side. Firestore's GitLive SDK
                // does not currently support `whereEqualTo(field, null)` cleanly
                // across platforms, and the dataset per user is small enough
                // (< 1k orders) that client-side filtering is acceptable.
                .filter { it.archivedAt == null }
            Result.Success(orders) as Result<List<Order>, DataError.Network>
        }
        .catch { throwable ->
            AppLogger.e(tag = TAG, throwable = throwable) { "observeOrders failed" }
            emit(Result.Error(DataError.Network.UNKNOWN))
        }
```

`observeOrder` (single doc) is intentionally NOT filtered — the customer detail screen still needs to navigate into archived orders to view history.

- [ ] **Step 2: Run all tests**

Run: `./gradlew :composeApp:allTests`
Expected: All green (existing tests don't touch archive flag).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt
git commit -m "feat(data): hide archived orders from observeOrders (single-doc lookup unaffected)"
```

---

## Phase 3: String Resources

### Task 3.1: Add all new strings

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Append the strings from spec § String Resources**

Open `strings.xml`, find an appropriate section (preferably grouped near other order-detail strings), and add the entire block from the spec's "String Resources" section verbatim. Reproduced here for convenience:

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

- [ ] **Step 2: Verify the resources compile**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL — generated `Res.string.order_detail_*` accessors.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(strings): add Order Details V2 string resources"
```

---

## Phase 4: New Composables

> **Pattern reference:** every new composable mirrors the dashboard V2 idiom in `feature/dashboard/presentation/components/IllustratedFocusCard.kt` and `TodayWorkCard.kt`:
> - `Surface(shape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier)` — NOT `Card`.
> - All sizes from `DesignTokens.spaceN` / `radiusN` / `iconN`.
> - Each composable file ends with `// region — Previews` containing at least one light + one dark preview.
> - All user-facing strings via `stringResource(Res.string.xxx)`.

### Task 4.1: Pure helper — `nextStatusTransitions` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/StatusTransitions.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/StatusTransitionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTransitionsTest {

    @Test
    fun pendingOffersAllForwardStages() {
        val moves = nextStatusTransitions(OrderStatus.PENDING, null)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
            ),
            moves,
        )
    }

    @Test
    fun cuttingOffersForwardPlusBackToPending() {
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.PENDING, null),
            ),
            moves,
        )
    }

    @Test
    fun fittingOffersReadyDeliveredAndBackToSewing() {
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            ),
            moves,
        )
    }

    @Test
    fun readyOffersDeliveredPlusBackToFitting() {
        val moves = nextStatusTransitions(OrderStatus.READY, null)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
            ),
            moves,
        )
    }

    @Test
    fun deliveredOffersNoMoves() {
        val moves = nextStatusTransitions(OrderStatus.DELIVERED, null)
        assertEquals(emptyList(), moves)
    }

    @Test
    fun inProgressWithNullSubStatusBehavesAsCutting() {
        // Legacy data: status = IN_PROGRESS but no subStatus persisted.
        // Treat as CUTTING for the picker so the user can move forward.
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, null)
        assertEquals(
            nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            moves,
        )
    }
}
```

- [ ] **Step 2: Run test — should fail to compile**

Run: `./gradlew :composeApp:jvmTest --tests "*StatusTransitionsTest*"`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `StatusTransitions.kt`**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

/** A move available from the current (status, subStatus) tuple. */
data class StatusTransition(
    val toStatus: OrderStatus,
    val toSubStatus: OrderSubStatus?,
)

/**
 * Picker contents for moving an order forward (or back) from its current
 * stage. Forward moves come first, back moves at the end. Empty list means
 * no transitions are available (i.e. the order is delivered).
 */
internal fun nextStatusTransitions(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
): List<StatusTransition> {
    val effectiveSub = currentSubStatus
        ?: if (currentStatus == OrderStatus.IN_PROGRESS) OrderSubStatus.CUTTING else null
    return when (currentStatus) {
        OrderStatus.PENDING -> listOf(
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
            StatusTransition(OrderStatus.READY, null),
            StatusTransition(OrderStatus.DELIVERED, null),
        )
        OrderStatus.IN_PROGRESS -> when (effectiveSub) {
            OrderSubStatus.CUTTING -> listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.PENDING, null),
            )
            OrderSubStatus.SEWING -> listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            )
            OrderSubStatus.FITTING -> listOf(
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            )
            null -> emptyList() // unreachable: handled by the elvis above
        }
        OrderStatus.READY -> listOf(
            StatusTransition(OrderStatus.DELIVERED, null),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
        )
        OrderStatus.DELIVERED -> emptyList()
    }
}
```

- [ ] **Step 4: Re-run tests**

Run: `./gradlew :composeApp:jvmTest --tests "*StatusTransitionsTest*"`
Expected: 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/StatusTransitions.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/StatusTransitionsTest.kt
git commit -m "feat(order-detail): pure-function status transition resolver"
```

---

### Task 4.2: Pure helper — `resolvePrimaryCta` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolver.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolverTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PrimaryCtaResolverTest {

    private fun cta(
        status: OrderStatus,
        sub: OrderSubStatus? = null,
        overdue: Boolean = false,
        balance: Double = 0.0,
    ): CtaPair = resolvePrimaryCta(
        status = status,
        subStatus = sub,
        isOverdue = overdue,
        balanceRemaining = balance,
    )

    @Test
    fun pending_normal_offersStartWorkPlusRecordPayment() {
        assertEquals(
            CtaPair(PrimaryCta.StartWork, SecondaryCta.RecordPayment),
            cta(OrderStatus.PENDING, balance = 60_000.0),
        )
    }

    @Test
    fun pending_overdue_swapsToReminderPrimary() {
        assertEquals(
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.StartWork),
            cta(OrderStatus.PENDING, overdue = true, balance = 60_000.0),
        )
    }

    @Test
    fun inProgressFitting_offersConfirmFitting() {
        assertEquals(
            CtaPair(PrimaryCta.ConfirmFitting, SecondaryCta.RecordPayment),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.FITTING, balance = 20_000.0),
        )
    }

    @Test
    fun inProgressSewing_offersUpdateStatus() {
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.RecordPayment),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.SEWING, balance = 30_000.0),
        )
    }

    @Test
    fun ready_offersMarkDeliveredPlusMessage() {
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.MessageCustomer),
            cta(OrderStatus.READY, balance = 80_000.0),
        )
    }

    @Test
    fun delivered_offersShareReceiptPlusDuplicate() {
        assertEquals(
            CtaPair(PrimaryCta.ShareReceipt, SecondaryCta.DuplicateOrder),
            cta(OrderStatus.DELIVERED, balance = 0.0),
        )
    }

    @Test
    fun zeroBalance_replacesRecordPaymentWithMessageCustomer() {
        // When balance is 0 the "Record payment" secondary doesn't make sense
        // — fall back to "Message customer" so the secondary is still useful.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.MessageCustomer),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.CUTTING, balance = 0.0),
        )
    }
}
```

- [ ] **Step 2: Implement the resolver**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

enum class PrimaryCta {
    StartWork,
    UpdateStatus,
    ConfirmFitting,
    MarkDelivered,
    ShareReceipt,
    SendReminder,
}

enum class SecondaryCta {
    RecordPayment,
    MessageCustomer,
    StartWork,
    UpdateStatus,
    MarkDelivered,
    DuplicateOrder,
}

data class CtaPair(val primary: PrimaryCta, val secondary: SecondaryCta)

internal fun resolvePrimaryCta(
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    isOverdue: Boolean,
    balanceRemaining: Double,
): CtaPair {
    val balanceSecondary = if (balanceRemaining > 0.0) {
        SecondaryCta.RecordPayment
    } else {
        SecondaryCta.MessageCustomer
    }
    return when {
        status == OrderStatus.DELIVERED ->
            CtaPair(PrimaryCta.ShareReceipt, SecondaryCta.DuplicateOrder)
        isOverdue && status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.StartWork)
        isOverdue && status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.UpdateStatus)
        isOverdue && status == OrderStatus.READY ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.MarkDelivered)
        status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.StartWork, balanceSecondary)
        status == OrderStatus.IN_PROGRESS && subStatus == OrderSubStatus.FITTING ->
            CtaPair(PrimaryCta.ConfirmFitting, balanceSecondary)
        status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.UpdateStatus, balanceSecondary)
        status == OrderStatus.READY ->
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.MessageCustomer)
        else -> CtaPair(PrimaryCta.UpdateStatus, balanceSecondary) // unreachable
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :composeApp:jvmTest --tests "*PrimaryCtaResolverTest*"`
Expected: 7 tests passed.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolver.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolverTest.kt
git commit -m "feat(order-detail): pure-function CTA resolver for hero card"
```

---

### Task 4.3 — 4.10: Composables

> **Each composable in this group follows the same template.** For each, the steps are:
> 1. Create the file in `feature/order/presentation/detail/components/`.
> 2. Implement following the dashboard V2 pattern: `Surface(shape, color, border, modifier)` — NOT `Card`. Use `MaterialTheme.colorScheme.outlineVariant` for borders. Use `DesignTokens.*` for all spacing/radii/typography.
> 3. Add at least one `@Preview` showing the main render and one `@Preview` with `StitchPadTheme(darkTheme = true)`.
> 4. For composables with multiple visual variants (empty / populated, light / dark, hero pickup / fitting / overdue), add one preview per important variant — see `IllustratedFocusCard.kt` for the exemplar (8 previews covering 6 variants × 2 themes).
> 5. Run `./gradlew :composeApp:compileKotlinJvm` after creating each file; expect BUILD SUCCESSFUL.
> 6. Commit each composable as its own commit: `feat(order-detail): add OrderHeroCard composable + previews` (etc.).
> 7. **Do NOT wire these into `OrderDetailScreen` yet** — that happens in Phase 6. They're standalone, preview-only at this stage.

For each composable: open the relevant section of the spec (`docs/superpowers/specs/2026-05-03-order-details-v2-redesign-design.md` § Section Anatomy) for the exact layout, and the matching state in `preview/order-details-v2.html` for the visual reference.

| # | File | Spec section | Reference state in preview HTML |
|---|------|--------------|---------------------------------|
| 4.3 | `components/OrderHeroCard.kt` | § 1. Hero Card | All 5 main states |
| 4.4 | `components/OrderCustomerCard.kt` | § 2. Customer Card | All 5 main states |
| 4.5 | `components/OrderGarmentDetailsCard.kt` | § 3. Garment Details Card | All 5 main states |
| 4.6 | `components/OrderPaymentCard.kt` | § 4. Payment Card | States 2, 3, 4, 5 (history populated) + State 1 (empty) |
| 4.7 | `components/OrderProductionTimeline.kt` | § 5. Production Timeline Card | All 5 main states (different active node) |
| 4.8 | `components/OrderMeasurementsPreviewCard.kt` | § 6. Measurements Preview Card | All 5 main states |
| 4.9 | `components/OrderNotesCard.kt` | § 7. Notes Card | State 1 (empty), States 2-5 (populated), Dive 3 (editing) |
| 4.10 | `components/OrderFooterCaption.kt` + `OrderArchiveButton.kt` + `OrderDetailOverflowMenu.kt` + `StatusTransitionSheet.kt` + `RecordPaymentDialogV2.kt` | § 8 + § Top App Bar + Dive 1 + Dive 2 | Footer/archive in States 1-5, overflow in State 1, sheet in Dive 1, dialog in Dive 2 |

**Public API per composable** (use these signatures verbatim — Phase 6 wires them up):

```kotlin
@Composable
fun OrderHeroCard(
    fabricPhotoUrl: String?,
    garmentTypeIcon: ImageVector, // fallback when fabricPhotoUrl == null
    garmentName: String,
    customerName: String,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    priority: OrderPriority,
    isOverdue: Boolean,
    overdueDaysAgo: Int,
    dueLabel: UiText,
    balanceRemaining: Double,
    cta: CtaPair,
    onPrimaryCta: () -> Unit,
    onSecondaryCta: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderCustomerCard(
    customerName: String,
    phone: String?,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onMeasurementsClick: () -> Unit,
    onCustomerClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>, // multi-item: render one row per item
    priority: OrderPriority,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderPaymentCard(
    totalPrice: Double,
    payments: List<Payment>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderProductionTimeline(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
    isOverdue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderMeasurementsPreviewCard(
    measurement: Measurement?, // null → empty state
    primaryFieldLabels: List<String>, // first 3 from GarmentType.fieldLabels
    onCardClick: () -> Unit,
    onLinkMeasurementsClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderNotesCard(
    notes: String?,
    isEditing: Boolean,
    draft: String,
    onCardClick: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderFooterCaption(
    orderId: String,
    referenceTimestamp: Long, // createdAt OR delivered timestamp
    isDelivered: Boolean,
    modifier: Modifier = Modifier,
)

@Composable
fun OrderArchiveButton(onClick: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun OrderDetailOverflowMenu(
    expanded: Boolean,
    showArchive: Boolean, // false on Delivered (already at the bottom) and on archived orders
    onDismiss: () -> Unit,
    onDuplicateClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
)

@Composable
fun StatusTransitionSheet(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
    onTransitionSelected: (StatusTransition) -> Unit,
    onDismiss: () -> Unit,
)

@Composable
fun RecordPaymentDialogV2(
    balanceRemaining: Double,
    amountInput: String,
    method: PaymentMethod,
    type: PaymentType,
    wasCapped: Boolean,
    onAmountChange: (String) -> Unit,
    onMethodSelect: (PaymentMethod) -> Unit,
    onTypeSelect: (PaymentType) -> Unit,
    onMarkPaidInFull: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

**Implementation guidance for tricky composables:**

- **OrderHeroCard:** `Row` with thumbnail (`Modifier.size(96.dp).clip(RoundedCornerShape(DesignTokens.radiusMd))`) and text column (`Modifier.weight(1f)`). Use `Coil` `SubcomposeAsyncImage` for the fabric photo (mirror the existing usage in `OrderDetailScreen.kt:790`). Below the row: optional overdue banner (`Surface` with `error50` background) then the dual CTA row (`Row` with two equal-weight `Button` / `TextButton`).
- **OrderProductionTimeline:** `Row` with `horizontalArrangement = Arrangement.SpaceBetween`. Each step is a `Column { node; label }`. Connecting line via `Box` with `Modifier.background(...)` positioned absolutely between the nodes (use `BoxWithConstraints` if needed). Active node is 32dp, inactive 28dp; current node has elevated colour (`primary` for in-progress, `success500` for ready/delivered, `error500` if overdue).
- **OrderNotesCard:** Conditional rendering inside the Surface — when `!isEditing`, render the static text (or empty hint); when `isEditing`, render an `OutlinedTextField` with `maxLines = 8` and a `Row` of Cancel/Save buttons. Hoist all editing state to the VM (per CLAUDE.md rule).
- **StatusTransitionSheet:** `ModalBottomSheet` (`androidx.compose.material3.ModalBottomSheet`). On iOS, dismiss with `delay(450)` before invoking `onDismiss` (per `feedback_ios_modal_bottom_sheet_timing` memory) — use the existing pattern from elsewhere in the app.
- **RecordPaymentDialogV2:** `AlertDialog` with custom `text` slot containing the `OutlinedTextField` (existing) plus two `SegmentedButtonRow` rows for Type and Method. Look at how the existing `RecordPaymentDialog` lays out its content in `OrderDetailScreen.kt:567` as the starting point, then append the two segmented controls.

After all composables in this phase compile and have working previews:

- [ ] **Pre-push gate (Phase 4 checkpoint):** Run the full Pre-Push Review Gate checklist at the top of this document. Push the branch.

---

## Phase 5: ViewModel Surface Extension

### Task 5.1: Extend `OrderDetailState`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt`

- [ ] **Step 1: Replace the data class with the V2 shape**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderDetailState(
    val order: Order? = null,
    val user: User? = null,
    val customer: Customer? = null,
    val measurement: Measurement? = null,
    val isLoading: Boolean = true,

    // Dialogs / sheets
    val showDeleteDialog: Boolean = false,
    val showStatusSheet: Boolean = false,
    val showBalanceWarningDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val showRecordPaymentDialog: Boolean = false,
    val showArchiveDialog: Boolean = false,
    val showOverflowMenu: Boolean = false,

    // Payment dialog state
    val paymentAmountInput: String = "",
    val wasPaymentCapped: Boolean = false,
    val paymentMethodSelection: PaymentMethod = PaymentMethod.TRANSFER,
    val paymentTypeSelection: PaymentType = PaymentType.DEPOSIT,
    val isPaymentHistoryExpanded: Boolean = true,

    // Notes editor
    val isEditingNotes: Boolean = false,
    val notesDraft: String = "",

    // Status sheet
    val pendingStatusTransition: StatusTransition? = null,
    val selectedNewStatus: OrderStatus? = null, // kept for the existing balance-warning flow
    val selectedNewSubStatus: OrderSubStatus? = null,

    val errorMessage: UiText? = null,
)
```

- [ ] **Step 2: Verify compile (VM will fail next, expected)**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: FAIL on `OrderDetailViewModel` referencing the old field names. Continue to Task 5.4.

---

### Task 5.2: Extend `OrderDetailAction`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt`

- [ ] **Step 1: Replace the sealed interface with the V2 shape**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType

sealed interface OrderDetailAction {
    // Navigation
    data object OnBackClick : OrderDetailAction
    data object OnEditClick : OrderDetailAction
    data object OnCustomerClick : OrderDetailAction

    // Top-bar overflow
    data object OnOverflowMenuToggle : OrderDetailAction
    data object OnDuplicateClick : OrderDetailAction

    // Delete
    data object OnDeleteClick : OrderDetailAction
    data object OnConfirmDelete : OrderDetailAction
    data object OnDismissDeleteDialog : OrderDetailAction

    // Archive
    data object OnArchiveClick : OrderDetailAction
    data object OnConfirmArchive : OrderDetailAction
    data object OnDismissArchiveDialog : OrderDetailAction

    // Status sheet
    data object OnUpdateStatusClick : OrderDetailAction
    data class OnSelectStatusTransition(val transition: StatusTransition) : OrderDetailAction
    data object OnDismissStatusSheet : OrderDetailAction
    // Kept for existing balance-warning flow:
    data class OnSelectNewStatus(val status: OrderStatus) : OrderDetailAction
    data object OnConfirmStatusUpdate : OrderDetailAction
    data object OnDismissStatusUpdate : OrderDetailAction
    data object OnBalanceWarningRecordPayment : OrderDetailAction
    data object OnBalanceWarningProceed : OrderDetailAction
    data object OnBalanceWarningDismiss : OrderDetailAction

    // Sharing
    data object OnShareClick : OrderDetailAction
    data object OnShareAsImageClick : OrderDetailAction
    data object OnShareAsPdfClick : OrderDetailAction
    data object OnDismissShareSheet : OrderDetailAction

    // Record payment
    data object OnRecordPaymentClick : OrderDetailAction
    data class OnPaymentAmountChange(val digits: String) : OrderDetailAction
    data class OnPaymentMethodSelect(val method: PaymentMethod) : OrderDetailAction
    data class OnPaymentTypeSelect(val type: PaymentType) : OrderDetailAction
    data object OnMarkPaidInFull : OrderDetailAction
    data object OnConfirmRecordPayment : OrderDetailAction
    data object OnDismissRecordPayment : OrderDetailAction
    data object OnPaymentHistoryToggle : OrderDetailAction

    // Notes
    data object OnNotesEditClick : OrderDetailAction
    data class OnNotesDraftChange(val text: String) : OrderDetailAction
    data object OnNotesSaveClick : OrderDetailAction
    data object OnNotesCancelClick : OrderDetailAction

    // Customer reach-out
    data object OnWhatsAppClick : OrderDetailAction
    data object OnCallClick : OrderDetailAction
    data object OnSendReminderClick : OrderDetailAction

    // Measurements
    data object OnMeasurementsScrollClick : OrderDetailAction
    data object OnLinkMeasurementsClick : OrderDetailAction

    // Misc
    data object OnErrorDismiss : OrderDetailAction
}
```

---

### Task 5.3: Extend `OrderDetailEvent`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailEvent.kt`

- [ ] **Step 1: Replace with the V2 shape**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

sealed interface OrderDetailEvent {
    data class NavigateToOrderForm(val orderId: String) : OrderDetailEvent
    data class NavigateToCustomerDetail(val customerId: String) : OrderDetailEvent
    data class NavigateToCreateOrder(val seedFromOrderId: String) : OrderDetailEvent
    data class NavigateToMeasurementsList(val customerId: String) : OrderDetailEvent
    data class LaunchWhatsApp(val phone: String, val message: String) : OrderDetailEvent
    data class LaunchDialer(val phone: String) : OrderDetailEvent
    data object NavigateBack : OrderDetailEvent
    data object OrderDeleted : OrderDetailEvent
    data object OrderArchived : OrderDetailEvent
    data object PaymentRecorded : OrderDetailEvent
    data object NotesSaved : OrderDetailEvent
    data object ScrollToMeasurements : OrderDetailEvent
}
```

---

### Task 5.4: Rewire `OrderDetailViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`

This is the largest single edit in the plan. Doing it in one task because the changes are deeply interleaved — splitting would leave the VM in a non-compiling intermediate state.

- [ ] **Step 1: Update the constructor + injected deps**

Replace the constructor signature:

```kotlin
class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val receiptSharer: OrderReceiptSharer,
) : ViewModel() {
```

Add imports:

```kotlin
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder // create if missing — see step 6
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
```

- [ ] **Step 2: Observe customer + measurement when order loads**

Inside `observeOrder()`, after the `_state.update { it.copy(order = result.data, isLoading = false) }`, add:

```kotlin
loadCustomerIfNeeded(result.data.customerId, userId)
val measurementId = result.data.items.firstOrNull()?.measurementId
if (measurementId != null) {
    loadMeasurementIfNeeded(result.data.customerId, measurementId, userId)
}
```

Add the two helpers:

```kotlin
private fun loadCustomerIfNeeded(customerId: String, userId: String) {
    if (_state.value.customer?.id == customerId) return
    viewModelScope.launch {
        when (val res = customerRepository.getCustomer(userId, customerId)) {
            is Result.Success -> _state.update { it.copy(customer = res.data) }
            is Result.Error -> { /* phone chips will render disabled; no toast for this background load */ }
        }
    }
}

private fun loadMeasurementIfNeeded(customerId: String, measurementId: String, userId: String) {
    if (_state.value.measurement?.id == measurementId) return
    viewModelScope.launch {
        measurementRepository.observeMeasurements(userId, customerId).collect { res ->
            if (res is Result.Success) {
                val match = res.data.firstOrNull { it.id == measurementId }
                if (match != null) _state.update { it.copy(measurement = match) }
            }
        }
    }
}
```

- [ ] **Step 3: Replace the `onAction` `when` body with the V2 surface**

Replace the entire `onAction` body. The new handler uses the new state field names and routes new actions; preserve the existing balance-warning flow as-is for status transitions that go straight to READY/DELIVERED.

```kotlin
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun onAction(action: OrderDetailAction) {
    when (action) {
        // Navigation
        OrderDetailAction.OnBackClick ->
            viewModelScope.launch { _events.send(OrderDetailEvent.NavigateBack) }
        OrderDetailAction.OnEditClick ->
            viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToOrderForm(orderId)) }
        OrderDetailAction.OnCustomerClick -> {
            val customerId = _state.value.order?.customerId ?: return
            viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToCustomerDetail(customerId)) }
        }

        // Top-bar overflow
        OrderDetailAction.OnOverflowMenuToggle ->
            _state.update { it.copy(showOverflowMenu = !it.showOverflowMenu) }
        OrderDetailAction.OnDuplicateClick -> {
            _state.update { it.copy(showOverflowMenu = false) }
            viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToCreateOrder(orderId)) }
        }

        // Delete
        OrderDetailAction.OnDeleteClick ->
            _state.update { it.copy(showOverflowMenu = false, showDeleteDialog = true) }
        OrderDetailAction.OnConfirmDelete -> deleteOrder()
        OrderDetailAction.OnDismissDeleteDialog ->
            _state.update { it.copy(showDeleteDialog = false) }

        // Archive
        OrderDetailAction.OnArchiveClick ->
            _state.update { it.copy(showOverflowMenu = false, showArchiveDialog = true) }
        OrderDetailAction.OnConfirmArchive -> archiveOrder()
        OrderDetailAction.OnDismissArchiveDialog ->
            _state.update { it.copy(showArchiveDialog = false) }

        // Status sheet
        OrderDetailAction.OnUpdateStatusClick ->
            _state.update { it.copy(showStatusSheet = true) }
        is OrderDetailAction.OnSelectStatusTransition ->
            handleStatusTransition(action.transition)
        OrderDetailAction.OnDismissStatusSheet ->
            _state.update { it.copy(showStatusSheet = false) }

        // Existing balance-warning flow (preserve for backwards compat)
        is OrderDetailAction.OnSelectNewStatus ->
            _state.update { it.copy(selectedNewStatus = action.status) }
        OrderDetailAction.OnConfirmStatusUpdate -> updateStatusViaWarningFlow()
        OrderDetailAction.OnDismissStatusUpdate ->
            _state.update { it.copy(selectedNewStatus = null) }
        OrderDetailAction.OnBalanceWarningRecordPayment -> {
            _state.update {
                it.copy(
                    showBalanceWarningDialog = false,
                    selectedNewStatus = null,
                    selectedNewSubStatus = null,
                    showRecordPaymentDialog = true,
                    paymentAmountInput = "",
                    wasPaymentCapped = false,
                )
            }
        }
        OrderDetailAction.OnBalanceWarningProceed -> {
            val pending = _state.value.selectedNewStatus
            val pendingSub = _state.value.selectedNewSubStatus
            _state.update {
                it.copy(
                    showBalanceWarningDialog = false,
                    selectedNewStatus = null,
                    selectedNewSubStatus = null,
                )
            }
            if (pending != null) performStatusUpdate(pending, pendingSub)
        }
        OrderDetailAction.OnBalanceWarningDismiss ->
            _state.update {
                it.copy(
                    showBalanceWarningDialog = false,
                    selectedNewStatus = null,
                    selectedNewSubStatus = null,
                )
            }

        // Sharing
        OrderDetailAction.OnShareClick ->
            _state.update { it.copy(showShareSheet = true) }
        OrderDetailAction.OnShareAsImageClick -> {
            _state.update { it.copy(showShareSheet = false) }
            shareReceipt { receiptSharer.shareReceiptAsImage(it) }
        }
        OrderDetailAction.OnShareAsPdfClick -> {
            _state.update { it.copy(showShareSheet = false) }
            shareReceipt { receiptSharer.shareReceiptAsPdf(it) }
        }
        OrderDetailAction.OnDismissShareSheet ->
            _state.update { it.copy(showShareSheet = false) }

        // Record payment
        OrderDetailAction.OnRecordPaymentClick -> {
            val isFirst = _state.value.order?.payments?.isEmpty() == true
            _state.update {
                it.copy(
                    showRecordPaymentDialog = true,
                    paymentAmountInput = "",
                    wasPaymentCapped = false,
                    paymentTypeSelection = if (isFirst) PaymentType.DEPOSIT else PaymentType.PROGRESS,
                    paymentMethodSelection = PaymentMethod.TRANSFER,
                )
            }
        }
        is OrderDetailAction.OnPaymentAmountChange -> {
            val rawDigits = action.digits.filter { it.isDigit() }.trimStart('0')
            val capped = capPaymentAmountDigits(action.digits)
            val didCap = rawDigits.isNotEmpty() && rawDigits != capped
            _state.update { it.copy(paymentAmountInput = capped, wasPaymentCapped = didCap) }
        }
        is OrderDetailAction.OnPaymentMethodSelect ->
            _state.update { it.copy(paymentMethodSelection = action.method) }
        is OrderDetailAction.OnPaymentTypeSelect ->
            _state.update { it.copy(paymentTypeSelection = action.type) }
        OrderDetailAction.OnMarkPaidInFull -> markPaidInFull()
        OrderDetailAction.OnConfirmRecordPayment -> recordPayment()
        OrderDetailAction.OnDismissRecordPayment -> {
            _state.update {
                it.copy(
                    showRecordPaymentDialog = false,
                    paymentAmountInput = "",
                    wasPaymentCapped = false,
                )
            }
        }
        OrderDetailAction.OnPaymentHistoryToggle ->
            _state.update { it.copy(isPaymentHistoryExpanded = !it.isPaymentHistoryExpanded) }

        // Notes
        OrderDetailAction.OnNotesEditClick ->
            _state.update {
                it.copy(isEditingNotes = true, notesDraft = it.order?.notes.orEmpty())
            }
        is OrderDetailAction.OnNotesDraftChange ->
            _state.update { it.copy(notesDraft = action.text) }
        OrderDetailAction.OnNotesSaveClick -> saveNotes()
        OrderDetailAction.OnNotesCancelClick ->
            _state.update { it.copy(isEditingNotes = false, notesDraft = "") }

        // Customer reach-out
        OrderDetailAction.OnWhatsAppClick -> launchWhatsApp()
        OrderDetailAction.OnCallClick -> launchDialer()
        OrderDetailAction.OnSendReminderClick -> launchWhatsApp() // same channel for V2

        // Measurements
        OrderDetailAction.OnMeasurementsScrollClick ->
            viewModelScope.launch { _events.send(OrderDetailEvent.ScrollToMeasurements) }
        OrderDetailAction.OnLinkMeasurementsClick -> {
            val customerId = _state.value.order?.customerId ?: return
            viewModelScope.launch {
                _events.send(OrderDetailEvent.NavigateToMeasurementsList(customerId))
            }
        }

        // Misc
        OrderDetailAction.OnErrorDismiss ->
            _state.update { it.copy(errorMessage = null) }
    }
}
```

- [ ] **Step 4: Add the new private handlers**

Below the existing private helpers, add:

```kotlin
private fun handleStatusTransition(transition: StatusTransition) {
    val order = _state.value.order ?: return
    val needsBalanceWarning = order.balanceRemaining > 0.0 &&
        (transition.toStatus == OrderStatus.READY || transition.toStatus == OrderStatus.DELIVERED)
    if (needsBalanceWarning) {
        _state.update {
            it.copy(
                showStatusSheet = false,
                selectedNewStatus = transition.toStatus,
                selectedNewSubStatus = transition.toSubStatus,
                showBalanceWarningDialog = true,
            )
        }
        return
    }
    _state.update { it.copy(showStatusSheet = false) }
    performStatusUpdate(transition.toStatus, transition.toSubStatus)
}

private fun updateStatusViaWarningFlow() {
    // Legacy entry-point used by the old dialog flow; kept for safety net.
    val target = _state.value.selectedNewStatus ?: return
    _state.update { it.copy(selectedNewStatus = null) }
    performStatusUpdate(target, _state.value.selectedNewSubStatus)
}

private fun performStatusUpdate(newStatus: OrderStatus, newSubStatus: OrderSubStatus?) {
    viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.id ?: return@launch
        // Status update first; sub-status second so a partial failure leaves the
        // status correct (the more important field) even if subStatus write fails.
        val statusResult = orderRepository.updateOrderStatus(userId, orderId, newStatus)
        if (statusResult is Result.Error) {
            _state.update { it.copy(errorMessage = statusResult.error.toOrderUiText()) }
            return@launch
        }
        // Always normalise subStatus: only IN_PROGRESS keeps it; other states clear.
        val effectiveSub = if (newStatus == OrderStatus.IN_PROGRESS) newSubStatus else null
        val subResult = orderRepository.updateSubStatus(userId, orderId, effectiveSub)
        if (subResult is Result.Error) {
            _state.update { it.copy(errorMessage = subResult.error.toOrderUiText()) }
        }
    }
}

private fun saveNotes() {
    val draft = _state.value.notesDraft
    viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.id ?: return@launch
        val toSave = draft.takeIf { it.isNotBlank() }
        when (val res = orderRepository.updateNotes(userId, orderId, toSave)) {
            is Result.Success -> {
                _state.update { it.copy(isEditingNotes = false, notesDraft = "") }
                _events.send(OrderDetailEvent.NotesSaved)
            }
            is Result.Error ->
                _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
        }
    }
}

private fun archiveOrder() {
    _state.update { it.copy(showArchiveDialog = false) }
    viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.id ?: return@launch
        when (val res = orderRepository.archiveOrder(userId, orderId)) {
            is Result.Success -> _events.send(OrderDetailEvent.OrderArchived)
            is Result.Error ->
                _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
        }
    }
}

private fun launchWhatsApp() {
    val customer = _state.value.customer ?: return
    val order = _state.value.order ?: return
    if (customer.phone.isBlank()) return
    val message = WhatsAppMessageBuilder.buildForOrder(order, customer)
    viewModelScope.launch {
        _events.send(OrderDetailEvent.LaunchWhatsApp(customer.phone, message))
    }
}

private fun launchDialer() {
    val phone = _state.value.customer?.phone ?: return
    if (phone.isBlank()) return
    viewModelScope.launch { _events.send(OrderDetailEvent.LaunchDialer(phone)) }
}
```

- [ ] **Step 5: Replace `submitPayment` to use `recordPayment`**

Replace the entire `submitPayment` function (currently lines 272-300) with:

```kotlin
@OptIn(ExperimentalUuidApi::class)
private fun submitPayment(amountJustPaid: Double) {
    val state = _state.value
    val order = state.order ?: return
    if (amountJustPaid <= 0.0) return
    val now = Clock.System.now().toEpochMilliseconds()
    val payment = Payment(
        id = Uuid.random().toString(),
        amount = amountJustPaid.coerceAtMost(order.balanceRemaining),
        method = state.paymentMethodSelection,
        type = state.paymentTypeSelection,
        recordedAt = now,
        note = null,
    )
    _state.update {
        it.copy(
            showRecordPaymentDialog = false,
            paymentAmountInput = "",
            wasPaymentCapped = false,
        )
    }
    viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.id ?: return@launch
        when (val res = orderRepository.recordPayment(userId, orderId, payment)) {
            is Result.Success -> _events.send(OrderDetailEvent.PaymentRecorded)
            is Result.Error ->
                _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
        }
    }
}
```

- [ ] **Step 6: Create `WhatsAppMessageBuilder` helper**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/util/WhatsAppMessageBuilder.kt`:

```kotlin
package com.danzucker.stitchpad.core.util

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

object WhatsAppMessageBuilder {
    fun buildForOrder(order: Order, customer: Customer): String {
        val firstName = customer.name.substringBefore(' ').ifBlank { customer.name }
        return when (order.status) {
            OrderStatus.PENDING -> "Hi $firstName, just confirming your order details — I'll be starting work on it soon."
            OrderStatus.IN_PROGRESS -> "Hi $firstName, quick update on your outfit — work is in progress and on track."
            OrderStatus.READY -> "Hi $firstName, your outfit is ready for pickup — let me know what time works for you."
            OrderStatus.DELIVERED -> "Hi $firstName, hope the outfit suits you well — would love a photo when you wear it!"
        }
    }
}
```

- [ ] **Step 7: Update Koin module** (`di/OrderModule.kt`)

The constructor now takes 6 dependencies. Koin's `viewModelOf(::OrderDetailViewModel)` resolves them all by type — but only if `CustomerRepository` and `MeasurementRepository` are registered. Verify they are:

Run: `grep -rn "CustomerRepository\|MeasurementRepository" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/`
Expected: Both bound in their feature modules (`CustomerModule.kt`, `MeasurementModule.kt`). If not, add bindings — but per the existing module structure they should already be there.

- [ ] **Step 8: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run tests**

Run: `./gradlew :composeApp:allTests`
Expected: All green.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailAction.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailEvent.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/util/WhatsAppMessageBuilder.kt
git commit -m "feat(order-detail): extend VM/State/Action/Event for V2 surface (notes, archive, sub-status, real payments)"
```

---

## Phase 6: Screen Rewire

### Task 6.1: Update top app bar — add overflow menu

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt:193-230`

- [ ] **Step 1: Replace the `actions` block of the top app bar**

Find the existing `TopAppBar` actions (around line 211-228). Replace the existing Share + Edit icon buttons with:

```kotlin
actions = {
    if (state.order != null) {
        IconButton(onClick = { onAction(OrderDetailAction.OnShareClick) }) {
            Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.order_detail_share))
        }
        IconButton(onClick = { onAction(OrderDetailAction.OnEditClick) }) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.order_detail_edit))
        }
        Box {
            IconButton(onClick = { onAction(OrderDetailAction.OnOverflowMenuToggle) }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.order_detail_more))
            }
            OrderDetailOverflowMenu(
                expanded = state.showOverflowMenu,
                showArchive = state.order.status != OrderStatus.DELIVERED,
                onDismiss = { onAction(OrderDetailAction.OnOverflowMenuToggle) },
                onDuplicateClick = { onAction(OrderDetailAction.OnDuplicateClick) },
                onArchiveClick = { onAction(OrderDetailAction.OnArchiveClick) },
                onDeleteClick = { onAction(OrderDetailAction.OnDeleteClick) },
            )
        }
    }
}
```

- [ ] **Step 2: Add missing strings**

If `Res.string.order_detail_more` doesn't exist, add to `strings.xml`:

```xml
<string name="order_detail_more">More options</string>
<string name="order_detail_share">Share</string>
<string name="order_detail_edit">Edit</string>
```

(check first — `order_detail_share` and `order_detail_edit` may already exist; only add what's missing.)

- [ ] **Step 3: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "feat(order-detail): add overflow menu (Duplicate / Archive / Delete) to top bar"
```

---

### Task 6.2: Replace `OrderDetailContent` with the V2 card composition

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt:710-1003`

- [ ] **Step 1: Replace the entire `OrderDetailContent` function**

Delete the existing `OrderDetailContent` (lines 710-1003) and the helpers `SectionHeader`, `FinancialRow`, `StatusHistoryItem`, `BalanceOwedWarningDialog`, `StatusBadge`, `OrderDetailNotFound` *only if* they're no longer referenced (`StatusBadge` is — it's used in `OrderHeroCard`; keep it. `BalanceOwedWarningDialog` is — keep it. `FinancialRow`, `SectionHeader`, `StatusHistoryItem` are obsolete — delete).

Replace `OrderDetailContent` with:

```kotlin
@Composable
private fun OrderDetailContent(
    state: OrderDetailState,
    order: Order,
    onAction: (OrderDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val isOverdue = order.deadline != null &&
        order.deadline < now &&
        order.status != OrderStatus.READY &&
        order.status != OrderStatus.DELIVERED
    val overdueDaysAgo = if (isOverdue && order.deadline != null) {
        ((now - order.deadline) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
    } else {
        0
    }
    val cta = remember(order.status, order.subStatus, isOverdue, order.balanceRemaining) {
        resolvePrimaryCta(order.status, order.subStatus, isOverdue, order.balanceRemaining)
    }

    val measurementsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = measurementsListState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item {
            OrderHeroCard(
                fabricPhotoUrl = order.items.firstOrNull()?.fabricPhotoUrl,
                garmentTypeIcon = garmentTypeIconFor(order.items.firstOrNull()?.garmentType),
                garmentName = order.items.firstOrNull()?.let { garmentDisplayName(it.garmentType) }.orEmpty(),
                customerName = order.customerName,
                status = order.status,
                subStatus = order.subStatus,
                priority = order.priority,
                isOverdue = isOverdue,
                overdueDaysAgo = overdueDaysAgo,
                dueLabel = formatDueLabel(order, isOverdue),
                balanceRemaining = order.balanceRemaining,
                cta = cta,
                onPrimaryCta = { handlePrimaryCta(cta.primary, onAction) },
                onSecondaryCta = { handleSecondaryCta(cta.secondary, onAction) },
            )
        }
        item {
            OrderCustomerCard(
                customerName = order.customerName,
                phone = state.customer?.phone,
                onWhatsAppClick = { onAction(OrderDetailAction.OnWhatsAppClick) },
                onCallClick = { onAction(OrderDetailAction.OnCallClick) },
                onMeasurementsClick = { onAction(OrderDetailAction.OnMeasurementsScrollClick) },
                onCustomerClick = { onAction(OrderDetailAction.OnCustomerClick) },
            )
        }
        item {
            OrderGarmentDetailsCard(items = order.items, priority = order.priority)
        }
        item {
            OrderPaymentCard(
                totalPrice = order.totalPrice,
                payments = order.payments,
                isExpanded = state.isPaymentHistoryExpanded,
                onToggleExpanded = { onAction(OrderDetailAction.OnPaymentHistoryToggle) },
                onRecordPaymentClick = { onAction(OrderDetailAction.OnRecordPaymentClick) },
            )
        }
        item {
            OrderProductionTimeline(
                currentStatus = order.status,
                currentSubStatus = order.subStatus,
                isOverdue = isOverdue,
                onClick = { onAction(OrderDetailAction.OnUpdateStatusClick) },
            )
        }
        item {
            OrderMeasurementsPreviewCard(
                measurement = state.measurement,
                primaryFieldLabels = order.items.firstOrNull()
                    ?.let { primaryMeasurementFieldsFor(it.garmentType) }
                    .orEmpty(),
                onCardClick = { /* opens full sheet — wired later */ },
                onLinkMeasurementsClick = { onAction(OrderDetailAction.OnLinkMeasurementsClick) },
            )
        }
        item {
            OrderNotesCard(
                notes = order.notes,
                isEditing = state.isEditingNotes,
                draft = state.notesDraft,
                onCardClick = { onAction(OrderDetailAction.OnNotesEditClick) },
                onDraftChange = { onAction(OrderDetailAction.OnNotesDraftChange(it)) },
                onSaveClick = { onAction(OrderDetailAction.OnNotesSaveClick) },
                onCancelClick = { onAction(OrderDetailAction.OnNotesCancelClick) },
            )
        }
        if (order.status == OrderStatus.DELIVERED) {
            item {
                OrderArchiveButton(onClick = { onAction(OrderDetailAction.OnArchiveClick) })
            }
        }
        item {
            OrderFooterCaption(
                orderId = order.id,
                referenceTimestamp = if (order.status == OrderStatus.DELIVERED) {
                    order.statusHistory
                        .lastOrNull { it.status == OrderStatus.DELIVERED }?.changedAt
                        ?: order.updatedAt
                } else {
                    order.createdAt
                },
                isDelivered = order.status == OrderStatus.DELIVERED,
            )
        }
    }
}

private const val MILLIS_PER_DAY: Long = 86_400_000L

private fun handlePrimaryCta(cta: PrimaryCta, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        PrimaryCta.StartWork,
        PrimaryCta.UpdateStatus,
        PrimaryCta.ConfirmFitting,
        PrimaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        PrimaryCta.ShareReceipt -> onAction(OrderDetailAction.OnShareClick)
        PrimaryCta.SendReminder -> onAction(OrderDetailAction.OnSendReminderClick)
    }
}

private fun handleSecondaryCta(cta: SecondaryCta, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        SecondaryCta.RecordPayment -> onAction(OrderDetailAction.OnRecordPaymentClick)
        SecondaryCta.MessageCustomer -> onAction(OrderDetailAction.OnWhatsAppClick)
        SecondaryCta.StartWork,
        SecondaryCta.UpdateStatus,
        SecondaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        SecondaryCta.DuplicateOrder -> onAction(OrderDetailAction.OnDuplicateClick)
    }
}

@Composable
private fun formatDueLabel(order: Order, isOverdue: Boolean): UiText {
    // Implementation: returns the appropriate UiText based on status + overdue.
    // Placeholder — fill with real formatting using existing date helpers in the project.
    val deadlineDate = order.deadline?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val readableDeadline = deadlineDate?.let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}" }
        ?: ""
    return when {
        order.status == OrderStatus.DELIVERED -> UiText.StringResourceText(
            Res.string.order_detail_delivered_label,
            arrayOf(readableDeadline),
        )
        isOverdue -> UiText.StringResourceText(
            Res.string.order_detail_was_due_label,
            arrayOf(readableDeadline),
        )
        order.status == OrderStatus.READY -> UiText.StringResourceText(Res.string.order_detail_pickup_today)
        else -> UiText.StringResourceText(Res.string.order_detail_due_label, arrayOf(readableDeadline))
    }
}

private fun garmentTypeIconFor(type: GarmentType?): ImageVector =
    Icons.Default.Checkroom // placeholder; replace with per-type mapping if you build one

private fun primaryMeasurementFieldsFor(type: GarmentType?): List<String> =
    type?.fieldLabels?.take(3).orEmpty()
```

Add the necessary imports (`androidx.compose.foundation.lazy.LazyColumn`, `androidx.compose.foundation.lazy.rememberLazyListState`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.material.icons.filled.Checkroom`, etc.).

- [ ] **Step 2: Update the call site — pass `state` to `OrderDetailContent`**

Find the existing call to `OrderDetailContent(order, onAction, ...)` higher up in `OrderDetailScreen.kt` and update to pass the full `state`:

```kotlin
OrderDetailContent(
    state = state,
    order = order,
    onAction = onAction,
    modifier = Modifier.fillMaxSize().padding(paddingValues),
)
```

- [ ] **Step 3: Wire the StatusTransitionSheet + Archive dialog at the screen level**

Inside the `Scaffold` content (after the existing dialogs), add:

```kotlin
if (state.showStatusSheet && order != null) {
    StatusTransitionSheet(
        currentStatus = order.status,
        currentSubStatus = order.subStatus,
        onTransitionSelected = { onAction(OrderDetailAction.OnSelectStatusTransition(it)) },
        onDismiss = { onAction(OrderDetailAction.OnDismissStatusSheet) },
    )
}

if (state.showArchiveDialog) {
    AlertDialog(
        onDismissRequest = { onAction(OrderDetailAction.OnDismissArchiveDialog) },
        title = { Text(stringResource(Res.string.order_detail_archive_confirm_title)) },
        text = { Text(stringResource(Res.string.order_detail_archive_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { onAction(OrderDetailAction.OnConfirmArchive) }) {
                Text(stringResource(Res.string.order_detail_archive_confirm_cta))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(OrderDetailAction.OnDismissArchiveDialog) }) {
                Text(stringResource(Res.string.cancel)) // verify this string exists; if not, add
            }
        },
    )
}
```

- [ ] **Step 4: Replace the existing `RecordPaymentDialog` invocation with `RecordPaymentDialogV2`**

Find where `RecordPaymentDialog` is rendered (existing) and swap to `RecordPaymentDialogV2` with the additional method/type props pulled from state.

- [ ] **Step 5: Wire event handlers in `OrderDetailRoot`**

In `OrderDetailRoot` (around line 146), find the `LaunchedEffect` collecting events. Add cases for:
- `OrderDetailEvent.OrderArchived` → `navController.popBackStack()` + show snackbar.
- `OrderDetailEvent.NotesSaved` → show snackbar with `Res.string.order_detail_notes_saved_toast`.
- `OrderDetailEvent.LaunchWhatsApp` → existing platform helper (look for existing usage in the project; if missing, defer to Task 6.4).
- `OrderDetailEvent.LaunchDialer` → existing dialer launcher.
- `OrderDetailEvent.NavigateToCreateOrder` → `navController.navigate(OrderFormRoute(seedFromOrderId = ...))` — if the form supports seeding, use that; otherwise, defer to a follow-up.
- `OrderDetailEvent.NavigateToMeasurementsList` → existing measurements route.
- `OrderDetailEvent.ScrollToMeasurements` → invoke `measurementsListState.animateScrollToItem(...)` — index of the measurements card.

- [ ] **Step 6: Verify compile**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run all tests**

Run: `./gradlew :composeApp:allTests`
Expected: All green.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(order-detail): rewire screen to V2 card composition"
```

---

### Task 6.3: Wire WhatsApp + Dialer platform launchers (if missing)

**Files:**
- Check: `feature/order/presentation/detail/OrderDetailScreen.kt` event handler block

- [ ] **Step 1: Look for existing usage**

Run: `grep -rn "wa\.me\|tel:" composeApp/src --include="*.kt"`

If a helper exists (`UriHandler` / `LocalUriHandler` calls), reuse it. If not, add inline in the event handler:

```kotlin
val uriHandler = LocalUriHandler.current
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is OrderDetailEvent.LaunchWhatsApp -> {
                val sanitized = event.phone.filter { it.isDigit() || it == '+' }.removePrefix("+")
                val encoded = event.message.encodeURLQueryComponent()
                uriHandler.openUri("https://wa.me/$sanitized?text=$encoded")
            }
            is OrderDetailEvent.LaunchDialer ->
                uriHandler.openUri("tel:${event.phone}")
            // … other cases …
        }
    }
}
```

`encodeURLQueryComponent` from `io.ktor.http` — already on the classpath via Firebase Storage. If not, write a small helper.

- [ ] **Step 2: Verify on Android emulator**

Build and install. Open an order → tap WhatsApp chip → verify intent launches WhatsApp (or a chooser if not installed).

- [ ] **Step 3: Commit**

```bash
git add -u
git commit -m "feat(order-detail): wire WhatsApp + dialer launchers"
```

---

### Task 6.4: Final cleanup pass

**Files:**
- `feature/order/presentation/detail/OrderDetailScreen.kt`
- `feature/order/presentation/detail/OrderDetailViewModel.kt`

- [ ] **Step 1: Delete dead code**

Search the screen file for any reference to removed composables (`SectionHeader`, `FinancialRow`, `StatusHistoryItem` if unused). Delete.

Run: `grep -n "SectionHeader\|FinancialRow\|StatusHistoryItem" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`
Expected: empty output (or only definitions, never usages).

- [ ] **Step 2: Confirm no `Suppress("LongMethod")` left where the function is now short**

`OrderDetailContent` is much smaller after the rewrite. Drop unnecessary `@Suppress` annotations.

- [ ] **Step 3: Run detekt**

Run: `./gradlew detekt`
Expected: zero violations. Fix anything reported (likely `MagicNumber`, `LongMethod`).

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "chore(order-detail): clean up dead helpers and stale suppressions"
```

---

## Phase 7: Pre-Push Verification (the gate from the top of this doc, executed in full)

### Task 7.1: Run the gate end-to-end

- [ ] **Step 1: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL, zero violations.

- [ ] **Step 2: All tests**

Run: `./gradlew :composeApp:allTests`
Expected: every test green.

- [ ] **Step 3: Android compile**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: iOS compile** (per `feedback_kotlin_native_epoch_days` memory)

Run: `./gradlew :composeApp:compileKotlinIosArm64` (or open `iosApp/iosApp.xcodeproj` in Xcode and build)
Expected: BUILD SUCCESSFUL.

If iOS fails on `Long` vs `Int` for `LocalDate.toEpochDays()`: add `.toLong()` calls explicitly per memory.
If iOS fails on `kotlinx.datetime.Clock.System` unresolved: switch to `kotlin.time.Clock.System` (the codebase already standardised on this in the mapper — keep consistency).

---

### Task 7.2: Manual smoke test (the spec's verification list, executed)

- [ ] **Step 1: Build to a Pixel emulator and install**

Run: `./gradlew :composeApp:installDebug`

- [ ] **Step 2: Run every step in spec § Verification → Manual smoke test**

For each of the eight numbered steps in the spec, perform the action and verify the expected behaviour. Take a screenshot at each step for the PR description.

- [ ] **Step 3: Toggle dark mode at every step**

System settings → dark mode → return to app → verify hero card, payment card, timeline, notes card, and overflow menu all render correctly. Spot-check overdue banner contrast in dark mode (text must be readable on the `error50` background).

---

### Task 7.3: Self-review checklist

- [ ] **Step 1: Tick every box in the Self-review checklist at the top of this document.** No exceptions. If a box can't be ticked, fix the issue.

---

### Task 7.4: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/order-details-redesign
```

- [ ] **Step 2: Open PR**

```bash
gh pr create --title "feat(order-details): V2 redesign — illustrated stack, inline notes, real payments" --body "$(cat <<'EOF'
## Summary

Order Details V2 — matches the dashboard V2 card language. Delete moves out of the bottom CTA into the `⋮` overflow; Notes card takes its place with inline editing. Six-stage production timeline backed by new optional `Order.subStatus`. Real `Payment` model replaces the flat `depositPaid` field (legacy synthesis on read keeps existing orders working).

Spec: `docs/superpowers/specs/2026-05-03-order-details-v2-redesign-design.md`
Plan: `docs/superpowers/plans/2026-05-03-order-details-v2-redesign-plan.md`
Preview: `preview/order-details-v2.html`

## Test plan (manual smoke — every box must be ticked before merge)

- [ ] Open an order in `PENDING` → tap "Start work" → status sheet opens with Cutting first → select Cutting → status pill, timeline node, and CTAs all update.
- [ ] Open an order in `IN_PROGRESS / FITTING` → "Confirm fitting" is the primary CTA → tap → transitions to Ready.
- [ ] Open an order with `balanceRemaining > 0` → tap "Record payment" → select Cash + Final → new payment in history, balance recalculates to zero.
- [ ] Open an overdue `IN_PROGRESS` order → red banner inside hero card, "Send reminder" is the primary CTA.
- [ ] Open a `DELIVERED` order → "Share receipt" + "Duplicate order" CTAs, `Archive order` button at the bottom (no Delete) → tap Archive → confirm dialog → order leaves the orders list.
- [ ] Open any order → top-right `⋮` → Duplicate / Archive / Delete present (Archive disabled if not Delivered) → tap Delete → confirmation dialog → confirm → navigation back.
- [ ] Open any order → tap notes card → type → Save → toast appears, persisted text on app restart.
- [ ] Toggle dark mode at every step.
- [ ] Pre-push gate ran clean: detekt, all tests, Android assembleDebug, iOS compile.

## Screenshots

Five reference states in light + dark (10 total). Drag-drop into the PR.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Drag-drop the screenshots into the PR description.**

---

## Verification (plan-level)

The plan is verified end-to-end when:

- All 7 phases are complete.
- The Pre-Push Review Gate at the top has passed.
- The PR is open with screenshots and the smoke-test checklist ticked.
- Spec §Definition of Done items are all true.
- Daniel (the QA per the memory) has personally walked the smoke-test on a real Pixel device.

---

## Out of scope (explicitly deferred to follow-up specs)

- Garment-type illustrated icon as a no-fabric fallback.
- Customer-relationship "more from this customer" card.
- Activity log beyond the existing `statusHistory`.
- Styles library integration on the hero card.
- Restoring archived orders from a Reports surface (the data is preserved; the UI to restore comes later).
- Migrating the Firestore documents server-side to drop the legacy `depositPaid` field — the read-side mapper handles synthesis indefinitely; a clean-up migration can run later if drift becomes a problem.
- Sub-status changes appearing in `statusHistory` (currently sub-status changes are silent — would clutter the history; revisit if tailors complain).
