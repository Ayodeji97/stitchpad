# Sync Status Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the tailor when their data is not yet on the server, via a global banner plus a per-row "Not synced" badge.

**Architecture:** Firestore already knows the answer — every snapshot carries `metadata.isFromCache` and `metadata.hasPendingWrites`. Global state is derived by switching the always-on user-doc listener to `includeMetadataChanges = true`, so no new Firestore listeners are added. Per-row state is read off each document's metadata at the existing mapping sites and carried on the domain model. The write path is not touched.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, GitLive firebase-kotlin-sdk 2.4.0, Koin, kotlinx.coroutines Flow, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-08-07-sync-status-indicator-design.md`

## Global Constraints

- **Never hardcode user-facing strings** — use `compose.resources` string resources from `composeApp/src/commonMain/composeResources/values/strings.xml`.
- **No backslash escapes in strings.xml** — CMP iOS renders `\'` literally. Use `&apos;` if an apostrophe is needed.
- **All state in the ViewModel** — never `remember`/`rememberSaveable` for business state.
- **Every Screen composable needs a `@Preview`**, and new surfaces must define **both light and dark** treatments.
- **Colours come from `DesignTokens`** (`ui/theme/DesignTokens.kt`) or `MaterialTheme.colorScheme` — no literal `Color(0x…)` in feature code.
- **Saffron (`DesignTokens.saffron500`) is a rare heritage accent only** — never use it for this feature.
- **Files with many `@Preview`s trip detekt's `TooManyFunctions`** — add `@file:Suppress("TooManyFunctions")` at the top of new component files that carry previews.
- **Test names use underscores**, matching existing `commonTest` convention (e.g. `parses_the_https_universal_link`). Backtick names on JVM tolerate only letters, digits, spaces and hyphens.
- **Run `./gradlew detekt` before each commit.** Tests: `./gradlew :composeApp:testDebugUnitTest`.
- **Staging discipline — this is not optional.** The working tree contains unrelated
  in-flight work by the repo owner (`docs/qa/`, `docs/staff/founding-tailors-*`,
  `functions/scripts/foundingTailors*`, `preview/`, `docs/superpowers/plans/2026-08-04-*`).
  **Never run `git add -A` or `git add .`** — stage only the exact paths named in
  your task. A previous run of this workflow swept the owner's untracked files into
  a task commit this way.
- **Never run `git checkout <sha>`, `git rebase`, `git commit --amend`, or
  `git stash`.** Subagents share one physical worktree and one HEAD; a stray
  checkout detaches HEAD for every task that follows and scatters commits across
  refs. Commit forward only, on the current branch.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SyncStatus.kt` — the three-state enum.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapper.kt` — pure `(isFromCache, hasPendingWrites) -> SyncStatus`. Separated from the observer so it is testable without Firestore.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusObserver.kt` — Firestore-backed flow, including the cold-start debounce.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/SyncStatusViewModel.kt` — exposes status to `MainRoot`.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/SyncStatusBanner.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PendingSyncBadge.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapperTest.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PendingSyncMapperTest.kt`
- `.maestro/offline-sync-indicator.yaml`

**Modify:**
- `core/domain/model/Customer.kt` — add `isPendingSync`.
- `core/domain/model/Order.kt:95-114` — add `isPendingSync`.
- `core/data/mapper/CustomerMapper.kt:44` — `toCustomer` takes the flag.
- `core/data/mapper/OrderMapper.kt:58` — `toOrder` takes the flag.
- `feature/customer/data/FirebaseCustomerRepository.kt:112-127` — `observeCustomers`.
- `feature/order/data/FirebaseOrderRepository.kt:252` and `:264` — `observeOrders`, `observeArchivedOrders`.
- `di/CoreModule.kt` — register `SyncStatusObserver`.
- `di/AuthModule.kt` — register `SyncStatusViewModel`.
- `feature/main/presentation/MainScreen.kt:116` (`MainRoot`) and `:188` (`Scaffold`) — host the banner.
- `feature/customer/presentation/list/CustomerListScreen.kt:719` (`CustomerListItem`) — badge.
- `feature/order/presentation/list/OrderListScreen.kt:724` (`OrderListItem`) — badge.
- `composeApp/src/commonMain/composeResources/values/strings.xml` — three strings.
- `.maestro/README.md` — document the new flow.

---

### Task 1: SyncStatus enum and pure mapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SyncStatus.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapper.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapperTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class SyncStatus { SYNCED, SYNCING, OFFLINE }`, `fun syncStatusOf(isFromCache: Boolean, hasPendingWrites: Boolean): SyncStatus`, and `fun Flow<SyncStatus>.debounceOffline(delayMs: Long): Flow<SyncStatus>`.

**Why the debounce is an operator, not inline:** the spec requires it to be unit-tested. Buried inside the Firestore-dependent observer it would only be reachable end-to-end; as a `Flow` operator it is testable with `runTest` plus a virtual clock.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.sync

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusMapperTest {

    @Test
    fun from_cache_is_offline_regardless_of_pending_writes() {
        assertEquals(SyncStatus.OFFLINE, syncStatusOf(isFromCache = true, hasPendingWrites = false))
        assertEquals(SyncStatus.OFFLINE, syncStatusOf(isFromCache = true, hasPendingWrites = true))
    }

    @Test
    fun server_snapshot_with_pending_writes_is_syncing() {
        assertEquals(SyncStatus.SYNCING, syncStatusOf(isFromCache = false, hasPendingWrites = true))
    }

    @Test
    fun server_snapshot_with_no_pending_writes_is_synced() {
        assertEquals(SyncStatus.SYNCED, syncStatusOf(isFromCache = false, hasPendingWrites = false))
    }

    @Test
    fun a_brief_offline_blip_is_swallowed_by_the_debounce() = runTest {
        // Models a cold start: the first snapshot is always cache-served, then the
        // server responds. The banner must never flash in this case.
        flow {
            emit(SyncStatus.OFFLINE)
            delay(200)
            emit(SyncStatus.SYNCED)
        }.debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.SYNCED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun a_sustained_offline_state_is_emitted_after_the_delay() = runTest {
        flowOf(SyncStatus.OFFLINE).debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.OFFLINE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun non_offline_statuses_are_not_delayed() = runTest {
        flowOf(SyncStatus.SYNCING).debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.SYNCING, awaitItem())
            awaitComplete()
        }
    }
}
```

> `runTest` uses a virtual clock, so the 2s delay costs no real time. If `app.cash.turbine` or `kotlinx-coroutines-test` are not yet on the `commonTest` source set, add them in `composeApp/build.gradle.kts` — both are already declared in `gradle/libs.versions.toml`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SyncStatusMapperTest*"`
Expected: FAIL — unresolved reference `syncStatusOf` and `SyncStatus`.

- [ ] **Step 3: Write minimal implementation**

Create `core/domain/model/SyncStatus.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Whether local data has reached the server yet.
 *
 * The app is offline-first by design ([com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher]
 * deliberately does not await server acknowledgement, so forms never hang in airplane
 * mode). This enum exists so that choice is visible to the user rather than silent.
 */
enum class SyncStatus {
    /** Everything the app has is on the server. */
    SYNCED,

    /** Connected, but at least one local write has not been acknowledged yet. */
    SYNCING,

    /** Not reaching Firestore. Reads are served from cache; writes are queued locally. */
    OFFLINE,
}
```

Create `core/data/sync/SyncStatusMapper.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus

/**
 * Pure projection of Firestore snapshot metadata onto [SyncStatus].
 *
 * Kept free of Firestore types so it can be unit-tested directly; the Firestore
 * plumbing lives in [SyncStatusObserver].
 *
 * `isFromCache` wins over `hasPendingWrites`: if we are not reaching the server at
 * all, "Offline" is the more useful thing to say than "Syncing".
 */
fun syncStatusOf(isFromCache: Boolean, hasPendingWrites: Boolean): SyncStatus = when {
    isFromCache -> SyncStatus.OFFLINE
    hasPendingWrites -> SyncStatus.SYNCING
    else -> SyncStatus.SYNCED
}

/**
 * Delays transitions INTO [SyncStatus.OFFLINE] by [delayMs], passing every other
 * status through immediately.
 *
 * The first snapshot after launch is always served from cache while the server
 * round-trip is still in flight, so without this the banner would flash on every
 * cold start. `transformLatest` cancels the pending delay the moment a newer status
 * arrives, so a healthy connection suppresses the blip while a genuine outage still
 * surfaces once the delay elapses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<SyncStatus>.debounceOffline(delayMs: Long): Flow<SyncStatus> =
    transformLatest { status ->
        if (status == SyncStatus.OFFLINE) {
            delay(delayMs)
        }
        emit(status)
    }
```

Imports for that file:

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SyncStatusMapperTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SyncStatus.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapper.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusMapperTest.kt
git commit -m "feat(sync): SyncStatus enum and pure snapshot-metadata mapper"
```

---

### Task 2: SyncStatusObserver

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusObserver.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt`

**Interfaces:**
- Consumes: `syncStatusOf(isFromCache, hasPendingWrites)`, `Flow<SyncStatus>.debounceOffline(delayMs)` and `SyncStatus` from Task 1.
- Produces: `class SyncStatusObserver(firestore: FirebaseFirestore)` with `fun observe(userId: String): Flow<SyncStatus>`.

**Why no unit test here:** after Task 1 this class is a pure Firestore adapter — both pieces of logic it composes are already covered. There is no Firestore fake in `commonTest`; real metadata behaviour is covered end-to-end by the Maestro flow in Task 8. Do not invent a fake Firestore for this.

- [ ] **Step 1: Write the implementation**

Create `core/data/sync/SyncStatusObserver.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val USERS = "users"

/**
 * Emits the workshop's [SyncStatus] by watching snapshot metadata on the user document.
 *
 * Deliberately reuses the user doc rather than opening a dedicated listener: that
 * document is already observed for every signed-in user, so this costs no extra
 * Firestore connection. `includeMetadataChanges = true` is what makes metadata-only
 * transitions (a queued write being acknowledged) arrive at all.
 */
class SyncStatusObserver(
    private val firestore: FirebaseFirestore,
) {

    fun observe(userId: String): Flow<SyncStatus> =
        firestore.collection(USERS).document(userId)
            .snapshots(includeMetadataChanges = true)
            .map { snapshot ->
                syncStatusOf(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites,
                )
            }
            .debounceOffline(OFFLINE_DEBOUNCE_MS)
            .distinctUntilChanged()
            // Fail to invisible. Showing a possibly-false "Offline" is worse than
            // showing nothing, and hiding preserves today's behaviour exactly.
            .catch { emit(SyncStatus.SYNCED) }

    private companion object {
        const val OFFLINE_DEBOUNCE_MS = 2_000L
    }
}
```

- [ ] **Step 2: Register in Koin**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt`. Add the import and a `singleOf` line alongside the other `single` declarations:

```kotlin
import com.danzucker.stitchpad.core.data.sync.SyncStatusObserver
```

```kotlin
    singleOf(::SyncStatusObserver)
```

If `singleOf` is not already imported in that file, add `import org.koin.core.module.dsl.singleOf`.

- [ ] **Step 3: Verify it compiles and Koin resolves**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/sync/SyncStatusObserver.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt
git commit -m "feat(sync): observe global sync status from user-doc snapshot metadata"
```

---

### Task 3: Customer.isPendingSync

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Customer.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomerMapper.kt:44`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt:112-127`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PendingSyncMapperTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Customer.isPendingSync: Boolean` and `fun CustomerDto.toCustomer(userId: String = "", isPendingSync: Boolean = false): Customer`.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PendingSyncMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingSyncMapperTest {

    private fun dto() = CustomerDto(
        id = "cust-1",
        name = "Adaeze Okafor",
        phone = "+2348012345601",
    )

    @Test
    fun customer_defaults_to_not_pending() {
        assertFalse(dto().toCustomer(userId = "u1").isPendingSync)
    }

    @Test
    fun customer_carries_the_pending_flag_through_the_mapper() {
        assertTrue(dto().toCustomer(userId = "u1", isPendingSync = true).isPendingSync)
    }

    @Test
    fun with_contact_preserves_the_pending_flag() {
        val customer = dto().toCustomer(userId = "u1", isPendingSync = true)
        assertTrue(customer.withContact(null).isPendingSync)
    }
}
```

> If `CustomerDto` requires more non-default constructor arguments than the three above, open `core/data/dto/CustomerDto.kt` and supply them — do not change the DTO to fit the test.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PendingSyncMapperTest*"`
Expected: FAIL — no parameter `isPendingSync`.

- [ ] **Step 3: Add the field to the domain model**

In `core/domain/model/Customer.kt`, add the last property:

```kotlin
data class Customer(
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val createdAt: Long = 0L,
    val slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    val lockedAt: Long? = null,
    /**
     * True when this record exists locally but Firestore has not acknowledged it yet.
     * Derived from snapshot metadata at read time — never persisted.
     */
    val isPendingSync: Boolean = false,
)
```

- [ ] **Step 4: Thread it through the mapper**

In `core/data/mapper/CustomerMapper.kt`, change `toCustomer` (currently at line 44):

```kotlin
fun CustomerDto.toCustomer(
    userId: String = "",
    isPendingSync: Boolean = false,
): Customer = Customer(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    email = email,
    address = address,
    createdAt = createdAt,
    slotState = CustomerSlotState.fromWire(slotState),
    lockedAt = lockedAt,
    isPendingSync = isPendingSync,
)
```

`withContact` uses `copy()` and therefore preserves the flag — no change needed there.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PendingSyncMapperTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Wire the repository**

In `feature/customer/data/FirebaseCustomerRepository.kt`, replace the body of `observeCustomers` (lines 112-127) with:

```kotlin
    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        combine(
            // includeMetadataChanges is what lets the badge CLEAR: without it, no new
            // snapshot arrives when a queued write is acknowledged, so the row would
            // stay marked "Not synced" until its content next changed.
            firestore.collection("users").document(userId).collection("customers")
                .snapshots(includeMetadataChanges = true),
            contactByCustomerId(userId),
        ) { snapshot, contacts ->
            val decoded = snapshot.documents.mapNotNull { doc ->
                val dto = decodeDocOrLog(tag = TAG, docId = doc.id) { doc.data<CustomerDto>() }
                dto?.let { it to doc.metadata.hasPendingWrites }
            }
            cacheActiveCustomerCount(userId, countActiveCustomers(decoded.map { it.first }))
            val customers = decoded.map { (dto, isPending) ->
                dto.toCustomer(userId, isPendingSync = isPending).withContact(contacts[dto.id])
            }
            Result.Success(customers) as Result<List<Customer>, DataError.Network>
        }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeCustomers failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }
```

- [ ] **Step 7: Prove the flag survives to ViewModel state**

The mapper tests prove the field is threaded; this proves the list state actually
carries it, which is what the badge in Task 7 binds to.

Open `composeApp/src/commonTest/.../FakeCustomerRepository.kt` to learn how it is
seeded, then add to the existing `CustomerListViewModel` test file (or create
`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListPendingSyncTest.kt`
if none exists), following the construction and dispatcher setup of the
neighbouring ViewModel tests exactly:

```kotlin
    @Test
    fun pending_customers_reach_the_list_state_marked() = runTest {
        val pending = Customer(
            id = "c1",
            userId = "u1",
            name = "Offline Ghost",
            phone = "08000000000",
            isPendingSync = true,
        )
        fakeCustomerRepository.emitCustomers(listOf(pending))

        val row = viewModel.state.value.customers.first { it.id == "c1" }
        assertTrue(row.isPendingSync)
    }
```

> Adjust `emitCustomers` and `state.value.customers` to the fake's and the state
> class's real names — read both before writing. If `CustomerListViewModel` maps
> to a UI model rather than exposing `Customer` directly, add `isPendingSync` to
> that UI model and to its mapping function as part of this step.

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomerList*"`
Expected: PASS.

- [ ] **Step 8: Verify the build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Customer.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomerMapper.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt \
        composeApp/src/commonTest/
git commit -m "feat(sync): carry per-customer pending-write state from snapshot metadata"
```

---

### Task 4: Order.isPendingSync

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt:95-114`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt:58`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt:252` and `:264`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PendingSyncMapperTest.kt` (extend)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Order.isPendingSync: Boolean` and `fun OrderDto.toOrder(userId: String, isPendingSync: Boolean = false): Order`.

- [ ] **Step 1: Write the failing test**

Append to `PendingSyncMapperTest.kt` (add whatever `OrderDto` imports and required constructor arguments the DTO needs — read `core/data/dto/OrderDto.kt`):

```kotlin
    @Test
    fun order_defaults_to_not_pending() {
        assertFalse(orderDto().toOrder(userId = "u1").isPendingSync)
    }

    @Test
    fun order_carries_the_pending_flag_through_the_mapper() {
        assertTrue(orderDto().toOrder(userId = "u1", isPendingSync = true).isPendingSync)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PendingSyncMapperTest*"`
Expected: FAIL — no parameter `isPendingSync` on `toOrder`.

- [ ] **Step 3: Add the field to the domain model**

In `core/domain/model/Order.kt`, add as the last constructor property of `data class Order` (after `updatedAt` on line 114, before the closing `)` and the `{` body):

```kotlin
    /**
     * True when this record exists locally but Firestore has not acknowledged it yet.
     * Derived from snapshot metadata at read time — never persisted.
     */
    val isPendingSync: Boolean = false,
```

- [ ] **Step 4: Thread it through the mapper**

In `core/data/mapper/OrderMapper.kt`, change the signature at line 58 to
`fun OrderDto.toOrder(userId: String, isPendingSync: Boolean = false): Order {`
and add `isPendingSync = isPendingSync,` to the `Order(...)` construction it returns.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PendingSyncMapperTest*"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Wire both repository call sites**

In `feature/order/data/FirebaseOrderRepository.kt`, **both** `observeOrders` (line 252) and `observeArchivedOrders` (line 264) currently start:

```kotlin
        combine(ordersCollection(userId).snapshots(), moneyByOrderId(userId)) { snapshot, money ->
```

Change each to:

```kotlin
        combine(
            ordersCollection(userId).snapshots(includeMetadataChanges = true),
            moneyByOrderId(userId),
        ) { snapshot, money ->
```

Then, in each lambda body, find where documents are decoded and `toOrder(userId)` is called. Keep the document in scope and pass its metadata, mirroring Task 3:

```kotlin
            val decoded = snapshot.documents.mapNotNull { doc ->
                val dto = decodeDocOrLog(tag = TAG, docId = doc.id) { doc.data<OrderDto>() }
                dto?.let { it to doc.metadata.hasPendingWrites }
            }
            val orders = decoded.map { (dto, isPending) ->
                dto.toOrder(userId, isPendingSync = isPending).withMoney(money[dto.id])
            }
```

> Read the existing bodies before editing — the exact decode helper and the `withMoney` fold must be preserved as they are. Only the pending flag is new. `withMoney` uses `copy()` so it preserves the flag.

- [ ] **Step 7: Verify the build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/PendingSyncMapperTest.kt
git commit -m "feat(sync): carry per-order pending-write state from snapshot metadata"
```

---

### Task 5: Presentational components

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/SyncStatusBanner.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PendingSyncBadge.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: `SyncStatus` from Task 1.
- Produces: `@Composable fun SyncStatusBanner(status: SyncStatus, modifier: Modifier = Modifier)` and `@Composable fun PendingSyncBadge(modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add the strings**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add:

```xml
    <string name="sync_status_offline">Offline — saved on this phone, will sync later</string>
    <string name="sync_status_syncing">Syncing…</string>
    <string name="sync_not_synced">Not synced</string>
```

No backslash escapes. The em dash and ellipsis are literal characters and render correctly on both platforms.

- [ ] **Step 2: Write the badge**

Create `ui/components/PendingSyncBadge.kt`:

```kotlin
@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.sync_not_synced

/**
 * Marks a record that exists locally but has not been acknowledged by the server.
 *
 * An outline dot rather than a filled one: this is informational, not an error. The
 * record is safe on the device and will sync on its own.
 */
@Composable
fun PendingSyncBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
        Text(
            text = stringResource(Res.string.sync_not_synced),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Add `import androidx.compose.foundation.layout.Box`. Then add previews in both colour modes, following whatever preview wrapper the existing components in `ui/components/` use (open `StatusChip.kt` and copy its preview pattern exactly — including how it wraps in `StitchPadTheme` and how it sets dark mode).

- [ ] **Step 3: Write the banner**

Create `ui/components/SyncStatusBanner.kt`. It renders nothing when `SYNCED`:

```kotlin
@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val message = when (status) {
        SyncStatus.SYNCED -> null
        SyncStatus.SYNCING -> stringResource(Res.string.sync_status_syncing)
        SyncStatus.OFFLINE -> stringResource(Res.string.sync_status_offline)
    }
    AnimatedVisibility(visible = message != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DesignTokens.space2, horizontal = DesignTokens.space4),
            )
        }
    }
}
```

> `stringResource` cannot be called inside a `when` branch that may not execute in some Compose versions — if the compiler objects, hoist each string into its own `stringResource` val above the `when` and select between them.

Add previews for all three states in both colour modes.

- [ ] **Step 4: Verify the build and previews compile**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Confirm both new files' previews render in Android Studio.

- [ ] **Step 5: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/SyncStatusBanner.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/PendingSyncBadge.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(sync): banner and pending-sync badge components"
```

---

### Task 6: Host the banner in MainScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/SyncStatusViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`

**Interfaces:**
- Consumes: `SyncStatusObserver.observe(userId)` (Task 2), `SyncStatusBanner(status, modifier)` (Task 5).
- Produces: `class SyncStatusViewModel` with `val state: StateFlow<SyncStatus>`.

**Known limitation to verify, not fix:** the observer watches `users/{workshopUid}`. For a **staff** account that document belongs to the owner, and staff read rules may deny it — in which case the flow errors and `catch` hides the banner. Staff therefore may get no banner. Confirm the behaviour during Task 8 verification and note it in the PR description; do not attempt to solve it in this plan.

- [ ] **Step 1: Write the ViewModel**

Create `feature/main/presentation/SyncStatusViewModel.kt`:

```kotlin
package com.danzucker.stitchpad.feature.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.data.sync.SyncStatusObserver
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncStatusViewModel(
    private val syncStatusObserver: SyncStatusObserver,
    private val activeWorkshopProvider: ActiveWorkshopProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncStatus.SYNCED)
    val state: StateFlow<SyncStatus> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: return@launch
            syncStatusObserver.observe(userId).collect { _state.value = it }
        }
    }
}
```

> `ActiveWorkshopProvider` is at
> `core/domain/session/ActiveWorkshopProvider.kt:15`. Confirm `workshopUidOrNull()`
> is still its accessor — `CustomerListViewModel.kt:211` uses it the same way.

- [ ] **Step 2: Register in Koin**

In `di/AuthModule.kt`, alongside the existing declarations:

```kotlin
import com.danzucker.stitchpad.feature.main.presentation.SyncStatusViewModel
import org.koin.core.module.dsl.viewModelOf
```

```kotlin
    viewModelOf(::SyncStatusViewModel)
```

- [ ] **Step 3: Render the banner**

In `feature/main/presentation/MainScreen.kt`, inside `MainRoot` (line 116) obtain the ViewModel and collect its state:

```kotlin
    val syncStatusViewModel: SyncStatusViewModel = koinViewModel()
    val syncStatus by syncStatusViewModel.state.collectAsStateWithLifecycle()
```

Pass `syncStatus` down to `MainScreen`, add it as a parameter there, and render it inside the `Scaffold` content lambda (line ~242) above the nav graph:

```kotlin
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            SyncStatusBanner(status = syncStatus)
            MainNavGraph(
                navController = innerNavController,
                onSignedOut = onSignedOut,
                onNavigateToDebugMenu = onNavigateToDebugMenu,
                modifier = Modifier.weight(1f)
            )
        }
    }
```

Note the padding moved from `MainNavGraph` onto the `Column`, and `MainNavGraph` now takes `Modifier.weight(1f)` so the nav graph fills the remaining height. Update the existing `@Preview`s for `MainScreen` to pass a `SyncStatus`.

- [ ] **Step 4: Verify on device**

Run: `./gradlew :composeApp:assembleDebug` — expect BUILD SUCCESSFUL.

Then confirm the banner does **not** appear during a normal online launch (this proves the debounce works):

```bash
./scripts/e2e-ios.sh <SIM_UDID> .maestro/login.yaml
```

Expected: flow passes. If it fails on an unexpected banner, the debounce is too short.

- [ ] **Step 5: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/SyncStatusViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(sync): surface the sync banner across all main tabs"
```

---

### Task 7: Badge the customer and order rows

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt:719`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt:724`

**Interfaces:**
- Consumes: `PendingSyncBadge()` (Task 5), `Customer.isPendingSync` (Task 3), `Order.isPendingSync` (Task 4).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Badge the customer row**

In `CustomerListItem` (line 719), inside the `Column(modifier = Modifier.weight(1f))` that already renders the name and phone, append after the existing phone `Text`:

```kotlin
            if (customer.isPendingSync) {
                PendingSyncBadge(modifier = Modifier.padding(top = DesignTokens.space1))
            }
```

Add `import com.danzucker.stitchpad.ui.components.PendingSyncBadge`.

- [ ] **Step 2: Badge the order row**

Open `OrderListItem` (line 724) and read it before editing. Place `PendingSyncBadge()` in the same relative position — under the secondary text line, inside the weighted `Column` — guarded by `if (order.isPendingSync)`.

> If `OrderListItem` delegates to `AccentedOrderRow` (`ui/components/AccentedOrderRow.kt`), do **not** add a parameter to that shared component: it is used by dashboard surfaces that are explicitly out of scope. Render the badge in `OrderListItem` around the row instead.

- [ ] **Step 3: Add previews**

Add a `@Preview` to each list screen showing one pending and one synced row, in both colour modes, following the existing preview pattern in those files.

- [ ] **Step 4: Verify the build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run detekt and commit**

```bash
./gradlew detekt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt
git commit -m "feat(sync): mark unsynced rows in the customers and orders lists"
```

---

### Task 8: Maestro offline regression flow

**Files:**
- Create: `.maestro/manual/offline-sync-indicator.yaml`
- Modify: `.maestro/README.md`

**Why `manual/`:** `scripts/e2e-ios.sh` runs the whole `.maestro` directory and keeps
the emulator alive throughout, so a flow that requires a dead backend cannot live in
the default suite. Maestro's directory runner does not descend into subdirectories —
`.maestro/subflows/` is already excluded this way, which is why the suite reports 2
flows and not 3. Step 4 verifies that assumption still holds rather than trusting it.

**Interfaces:**
- Consumes: the shipped UI from Tasks 5-7, and `.maestro/subflows/ensure-signed-in.yaml`.
- Produces: nothing.

This is the payoff: the exact defect that prompted the feature becomes a guarded regression.

- [ ] **Step 1: Write the flow**

Create `.maestro/manual/offline-sync-indicator.yaml`:

```yaml
# StitchPad — E2E regression: the app must SAY when data is not on the server.
#
# This guards the defect that prompted the sync indicator: with Firestore
# unreachable the app accepted a save, navigated forward, and listed the record
# identically to a synced one, with no signal of any kind.
#
# Run this one MANUALLY — it needs the Firestore backend killed mid-flow, which
# scripts/e2e-ios.sh does not do. See .maestro/README.md.
#
#   ./scripts/e2e-ios.sh <UDID> .maestro/login.yaml   # get signed in first
#   pkill -f "firebase emulators:start"               # kill the backend
#   maestro --device <UDID> test .maestro/manual/offline-sync-indicator.yaml

appId: com.danzucker.stitchpad
---
- launchApp
- runFlow: subflows/ensure-signed-in.yaml

# The banner must appear once Firestore is unreachable. Allow for the ~2s
# cold-start debounce plus Firestore's own connection timeout.
- extendedWaitUntil:
    visible: "Offline"
    timeout: 30000

- tapOn: "Customers"
- tapOn:
    text: "Add customer"
    index: 0
- assertVisible: "Add Customer"

- tapOn: "e.g. Amina Bello"
- inputText: "Offline Ghost"
- tapOn: "+234 801 234 5678"
- inputText: "08000000000"
- tapOn: "EMAIL (OPTIONAL)"
- tapOn: "Save & Add Measurements"
- assertVisible: "Add Measurements"
- tapOn: "Skip for now"

# The record is listed, AND it is marked. Before this feature the first
# assertion passed and the second had nothing to match.
- tapOn: "Customers"
- assertVisible: "Offline Ghost"
- assertVisible: "Not synced"
```

- [ ] **Step 2: Run it and watch it pass**

```bash
./scripts/e2e-ios.sh <SIM_UDID> .maestro/login.yaml
pkill -f "firebase emulators:start"
export PATH="$PATH:$HOME/.maestro/bin"
maestro --device <SIM_UDID> test .maestro/manual/offline-sync-indicator.yaml
```

Expected: PASS.

- [ ] **Step 3: Prove it would have caught the bug**

Temporarily comment out the `- assertVisible: "Not synced"` line and re-run: it should still pass. Restore the line, then temporarily change `PendingSyncBadge` to render nothing and re-run: it must **FAIL**. Restore the component.

A regression test you have never seen fail is not yet trusted.

- [ ] **Step 4: Verify the default suite still runs exactly two flows**

Run: `./scripts/e2e-ios.sh <SIM_UDID>`

Expected: **2/2 flows passed** — `login` and `smoke-add-customer` only. If the
runner reports 3 flows, Maestro descended into `manual/` after all; in that case
add an `--include-tags`/`--exclude-tags` filter or move the file outside
`.maestro/` entirely, and say which you did in your report.

- [ ] **Step 5: Document it**

In `.maestro/README.md`, under "Running", add:

```markdown
`manual/offline-sync-indicator.yaml` is excluded from the default suite because it
needs the Firestore backend killed mid-flow, which `e2e-ios.sh` never does. Run it
by hand — the header of that file has the three commands.
```

- [ ] **Step 6: Commit**

```bash
git add .maestro/
git commit -m "test(e2e): regression flow for the offline sync indicator"
```

---

## Verification before opening the PR

- [ ] `./gradlew :composeApp:testDebugUnitTest` — all green.
- [ ] `./gradlew detekt` — clean.
- [ ] `./gradlew :composeApp:assembleDebug` — succeeds.
- [ ] `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` — succeeds (catches KMP-only breakage that the Android build misses).
- [ ] `./scripts/e2e-ios.sh <SIM_UDID>` — 2/2 pass.
- [ ] The manual offline flow passes, and was seen to fail with the badge removed.
- [ ] Banner does not flash on a normal online cold start.
- [ ] Both components checked in light **and** dark mode.
- [ ] Scroll the customers and orders lists on a device — `includeMetadataChanges = true` increases emission frequency and this is the one perf risk in the change.
- [ ] Note the staff-account limitation from Task 6 in the PR description.
