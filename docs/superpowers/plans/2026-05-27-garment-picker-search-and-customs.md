# Garment Picker — Searchable + Custom Values Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the closed-enum garment-type dropdown with a searchable bottom-sheet picker that supports per-tailor custom garment names, persisted in a new Firestore subcollection and sorted by recency.

**Architecture:** Add a new `users/{uid}/customGarmentTypes/{id}` subcollection (matches existing repository pattern for Orders/Customers/Styles/Measurements). `GarmentType` enum gains an `OTHER` variant; `OrderItem` gains a nullable `customGarmentName: String?` (additive, no backfill). A new `GarmentPickerSheet` composable replaces the existing `ExposedDropdownMenuBox`. Search filters both preset enum entries and the tailor's saved customs case-insensitively. The "Custom" badge shows in the form only; every other display site renders the resolved name as plain text via a pure-domain `displayGarmentName` helper.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 (`ModalBottomSheet`), GitLive Firebase SDK, Koin DI, MVI, kotlinx.coroutines.test + kotlin.test for unit tests.

**Spec:** `docs/superpowers/specs/2026-05-27-garment-picker-search-and-custom-values-design.md`.

**Branch:** `feature/garment-picker-search-and-customs` (already checked out; spec already committed at `5b1f797`).

---

## File Map

| File | Change |
|---|---|
| `core/domain/model/CustomGarmentType.kt` | **CREATE** — Domain model |
| `core/data/dto/CustomGarmentTypeDto.kt` | **CREATE** — `@Serializable` DTO |
| `core/data/mapper/CustomGarmentTypeMapper.kt` | **CREATE** — `toCustomGarmentType()` / `toCustomGarmentTypeDto()` |
| `core/domain/repository/CustomGarmentTypeRepository.kt` | **CREATE** — Interface |
| `feature/order/data/FirebaseCustomGarmentTypeRepository.kt` | **CREATE** — Firestore impl |
| `core/data/repository/FakeCustomGarmentTypeRepository.kt` (commonTest) | **CREATE** — Test fake |
| `di/OrderModule.kt` | Register `FirebaseCustomGarmentTypeRepository` + extend `OrderFormViewModel` binding |
| `core/domain/model/GarmentType.kt` | Add `OTHER` enum entry |
| `feature/order/presentation/GarmentDisplayName.kt` | Add `OTHER` branch to `garmentNameResource` |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Add `garment_type_other` + 6 picker keys |
| `core/domain/model/Order.kt` | Add `customGarmentName: String?` to `OrderItem` |
| `core/data/dto/OrderDto.kt` | Add `customGarmentName: String? = null` to `OrderItemDto` |
| `core/data/mapper/OrderMapper.kt` | Round-trip the new field |
| `core/data/mapper/OrderMapperTest.kt` | Round-trip test for `customGarmentName` |
| `core/domain/model/OrderItemDisplay.kt` | **CREATE** — `displayGarmentName { resolver }` pure helper |
| `core/domain/model/OrderItemDisplayTest.kt` (commonTest) | **CREATE** — Unit tests for the helper |
| `feature/order/domain/GarmentPickerFilter.kt` | **CREATE** — `filterGarmentOptions(...)` pure helper |
| `feature/order/domain/GarmentPickerFilterTest.kt` (commonTest) | **CREATE** — Unit tests for the filter |
| `feature/order/presentation/form/OrderFormState.kt` | Add `customGarmentTypes`, `activePickerItemId`, `pickerSearchQuery` to `OrderFormState`; add `customGarmentName: String?` to `OrderItemFormState` |
| `feature/order/presentation/form/OrderFormAction.kt` | Add 5 new actions; keep `OnItemGarmentTypeChange` for the "clear" path |
| `feature/order/presentation/form/OrderFormEvent.kt` | Add `ShowCustomSavedSnackbar(name)` |
| `feature/order/presentation/form/OrderFormViewModel.kt` | Inject repo, subscribe to flow, handle new actions, update `toOrderItem()` save path |
| `feature/order/presentation/form/OrderFormViewModelTest.kt` | Add tests for the 5 new actions |
| `feature/order/presentation/form/components/GarmentPickerSheet.kt` | **CREATE** — The new bottom sheet |
| `feature/order/presentation/form/OrderFormScreen.kt` | Replace `ExposedDropdownMenuBox` with tap-target + sheet host; add "Custom" pill |
| `feature/order/presentation/detail/components/OrderHeroCard.kt` | Use new `displayGarmentName` helper |
| `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` | Use new `displayGarmentName` helper |
| `feature/order/presentation/list/OrderListScreen.kt` (or wherever pipeline row composable lives) | Use new `displayGarmentName` helper |
| Receipt sharing renderer | Use new `displayGarmentName` helper |

**Verification:** Each task ends in a compile/detekt/tests command. Manual smoke at the end. iOS compile required before final smoke (per saved memory `feedback_kmp_jvm_only_apis`).

---

## Task 1: Domain model `CustomGarmentType`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomGarmentType.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * A garment name a tailor has saved for their own use, surfaced in the
 * picker's "My garment types" section. Distinct from the closed [GarmentType]
 * enum — these are user-defined strings (e.g. "Iro and Buba", "Senator cape").
 *
 * The picker UX persists these across orders and sorts by [lastUsedAt] desc.
 */
data class CustomGarmentType(
    val id: String,
    val name: String,         // stored as the tailor typed it (preserves casing)
    val createdAt: Long,      // epoch ms
    val lastUsedAt: Long,     // epoch ms, updated on every pick
)
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success (no output, exit 0).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomGarmentType.kt
git commit -m "feat(garment-picker): add CustomGarmentType domain model"
```

---

## Task 2: DTO + mapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomGarmentTypeDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomGarmentTypeMapper.kt`

- [ ] **Step 1: Create the DTO**

```kotlin
package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomGarmentTypeDto(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
)
```

- [ ] **Step 2: Create the mapper**

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomGarmentTypeDto
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType

fun CustomGarmentTypeDto.toCustomGarmentType(): CustomGarmentType =
    CustomGarmentType(
        id = id,
        name = name,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )

fun CustomGarmentType.toDto(): CustomGarmentTypeDto =
    CustomGarmentTypeDto(
        id = id,
        name = name,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )
```

- [ ] **Step 3: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomGarmentTypeDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomGarmentTypeMapper.kt
git commit -m "feat(garment-picker): add CustomGarmentTypeDto + mapper"
```

---

## Task 3: Repository interface

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/CustomGarmentTypeRepository.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import kotlinx.coroutines.flow.Flow

interface CustomGarmentTypeRepository {

    /**
     * Subscribe to the tailor's saved customs.
     * Emits a list sorted by lastUsedAt desc, with an alphabetical tiebreak.
     */
    fun observe(userId: String): Flow<Result<List<CustomGarmentType>, DataError.Network>>

    /**
     * Create a new custom OR return the existing one if a case-insensitive
     * name match already exists. Always updates lastUsedAt = now on the
     * resolved doc so the resulting entry sorts to the top of the picker.
     */
    suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network>

    /**
     * Bump lastUsedAt on an existing custom. Called fire-and-forget from
     * the form when the user picks an already-saved custom value.
     */
    suspend fun touch(userId: String, id: String): EmptyResult<DataError.Network>
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/CustomGarmentTypeRepository.kt
git commit -m "feat(garment-picker): add CustomGarmentTypeRepository interface"
```

---

## Task 4: Firebase repository implementation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseCustomGarmentTypeRepository.kt`

Pattern reference: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/data/FirebaseStyleRepository.kt` (observe + mutation pattern).

- [ ] **Step 1: Create the implementation**

```kotlin
package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.CustomGarmentTypeDto
import com.danzucker.stitchpad.core.data.mapper.toCustomGarmentType
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomGarmentTypeRepo"

class FirebaseCustomGarmentTypeRepository(
    private val firestore: FirebaseFirestore,
) : CustomGarmentTypeRepository {

    private fun collection(userId: String) =
        firestore.collection("users").document(userId).collection("customGarmentTypes")

    override fun observe(
        userId: String
    ): Flow<Result<List<CustomGarmentType>, DataError.Network>> =
        collection(userId)
            .snapshots()
            .map { snapshot ->
                val customs = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<CustomGarmentTypeDto>().toCustomGarmentType() }.getOrNull()
                    }
                    .sortedWith(
                        compareByDescending<CustomGarmentType> { it.lastUsedAt }
                            .thenBy { it.name.lowercase() }
                    )
                Result.Success(customs) as Result<List<CustomGarmentType>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observe failed userId=$userId" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error(DataError.Network.UNKNOWN)
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = collection(userId).get().documents
                .mapNotNull {
                    runCatching { it.data<CustomGarmentTypeDto>() }.getOrNull()
                }
                .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }

            val resolved = if (existing != null) {
                val updated = existing.copy(lastUsedAt = now)
                collection(userId).document(existing.id).set(updated)
                updated.toCustomGarmentType()
            } else {
                val docRef = collection(userId).document
                val newDoc = CustomGarmentTypeDto(
                    id = docRef.id,
                    name = trimmed,
                    createdAt = now,
                    lastUsedAt = now,
                )
                docRef.set(newDoc)
                newDoc.toCustomGarmentType()
            }
            Result.Success(resolved)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "upsert failed userId=$userId name=$trimmed" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun touch(
        userId: String,
        id: String
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            collection(userId).document(id).update("lastUsedAt" to now)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "touch failed userId=$userId id=$id" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
```

- [ ] **Step 2: Compile (Android + iOS)**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Both must pass — per saved memory `feedback_kmp_jvm_only_apis`, `String.format` and similar JVM-only APIs compile on Android but fail iOS link. None used here but verify both targets.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseCustomGarmentTypeRepository.kt
git commit -m "feat(garment-picker): add FirebaseCustomGarmentTypeRepository"
```

---

## Task 5: Wire repository in Koin

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/OrderModule.kt`

- [ ] **Step 1: Add the binding**

Open `OrderModule.kt`. The existing `orderDataModule` block currently reads:

```kotlin
val orderDataModule = module {
    singleOf(::FirebaseOrderRepository) bind OrderRepository::class
}
```

Replace it with:

```kotlin
val orderDataModule = module {
    singleOf(::FirebaseOrderRepository) bind OrderRepository::class
    singleOf(::FirebaseCustomGarmentTypeRepository) bind CustomGarmentTypeRepository::class
}
```

Add the imports at the top of the file:

```kotlin
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import com.danzucker.stitchpad.feature.order.data.FirebaseCustomGarmentTypeRepository
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/OrderModule.kt
git commit -m "feat(garment-picker): wire CustomGarmentTypeRepository in Koin"
```

---

## Task 6: Fake repository for tests

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeCustomGarmentTypeRepository.kt`

Pattern reference: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt`.

- [ ] **Step 1: Create the fake**

```kotlin
package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCustomGarmentTypeRepository : CustomGarmentTypeRepository {

    var shouldReturnError: DataError.Network? = null

    private val customsFlow = MutableStateFlow<Map<String, List<CustomGarmentType>>>(emptyMap())

    var upsertCalls: MutableList<Pair<String, String>> = mutableListOf()
        private set

    var touchCalls: MutableList<Pair<String, String>> = mutableListOf()
        private set

    fun seed(userId: String, customs: List<CustomGarmentType>) {
        customsFlow.value = customsFlow.value + (userId to customs)
    }

    override fun observe(
        userId: String
    ): Flow<Result<List<CustomGarmentType>, DataError.Network>> =
        customsFlow.map { byUser ->
            shouldReturnError?.let { Result.Error(it) }
                ?: Result.Success(
                    byUser[userId].orEmpty().sortedWith(
                        compareByDescending<CustomGarmentType> { it.lastUsedAt }
                            .thenBy { it.name.lowercase() }
                    )
                ) as Result<List<CustomGarmentType>, DataError.Network>
        }

    override suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network> {
        upsertCalls += userId to name
        shouldReturnError?.let { return Result.Error(it) }
        val trimmed = name.trim()
        val now = 1_000L * (upsertCalls.size)  // monotonic deterministic clock
        val existing = customsFlow.value[userId].orEmpty()
            .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        return if (existing != null) {
            val updated = existing.copy(lastUsedAt = now)
            customsFlow.value = customsFlow.value + (userId to
                (customsFlow.value[userId].orEmpty().map { if (it.id == existing.id) updated else it })
            )
            Result.Success(updated)
        } else {
            val created = CustomGarmentType(
                id = "fake-id-${upsertCalls.size}",
                name = trimmed,
                createdAt = now,
                lastUsedAt = now,
            )
            customsFlow.value = customsFlow.value + (userId to
                (customsFlow.value[userId].orEmpty() + created)
            )
            Result.Success(created)
        }
    }

    override suspend fun touch(
        userId: String,
        id: String
    ): EmptyResult<DataError.Network> {
        touchCalls += userId to id
        return shouldReturnError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
}
```

- [ ] **Step 2: Compile tests**

```bash
./gradlew :composeApp:compileTestKotlinJvm --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeCustomGarmentTypeRepository.kt
git commit -m "test(garment-picker): add FakeCustomGarmentTypeRepository"
```

---

## Task 7: Add `GarmentType.OTHER` + string + display mapping

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/GarmentType.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/GarmentDisplayName.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the enum entry**

In `GarmentType.kt`, after the last existing enum entry (`CORPORATE_TROUSER`), add:

```kotlin
    OTHER(
        fieldLabels = emptyList(),
        gender = GarmentGender.UNISEX,
    ),
```

`fieldLabels = emptyList()` is correct — custom garments don't have a fixed measurement field set. The measurement-form code that consumes `fieldLabels` should already handle empty (defensive); if not, the call site will surface during smoke and we patch it.

- [ ] **Step 2: Add the string resource**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, locate the `<!-- Garment type names -->` block (around line 258). After the existing 17 `garment_type_*` entries, add:

```xml
    <string name="garment_type_other">Other</string>
```

This is the fallback label used when an OrderItem has `garmentType = OTHER` but no `customGarmentName` (defensive). It should never display in normal use.

- [ ] **Step 3: Add the display mapping**

In `GarmentDisplayName.kt`, the `garmentNameResource` `when` block currently has 17 cases. Add the `OTHER` case at the bottom of the when block (before the closing `}`):

```kotlin
    GarmentType.OTHER -> Res.string.garment_type_other
```

- [ ] **Step 4: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/GarmentType.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/GarmentDisplayName.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(garment-picker): add GarmentType.OTHER + display fallback"
```

---

## Task 8: Add `customGarmentName` to OrderItem + DTO + mapper

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`

- [ ] **Step 1: Write a failing round-trip test**

In `OrderMapperTest.kt`, find the section that tests `OrderItem` round-trip and add a new test method:

```kotlin
    @Test
    fun `OrderItem round-trips customGarmentName when garmentType is OTHER`() {
        val item = OrderItem(
            id = "item-1",
            garmentType = GarmentType.OTHER,
            customGarmentName = "Iro and Buba",
            description = "",
            price = 5000.0,
        )
        val order = Order(
            id = "order-1",
            customerId = "cust-1",
            createdAt = 1000L,
            updatedAt = 1000L,
            items = listOf(item),
            // other defaults
        )

        val dto = order.toOrderDto()
        val roundTripped = dto.toOrder(userId = "user-1")

        assertEquals("Iro and Buba", roundTripped.items.first().customGarmentName)
        assertEquals(GarmentType.OTHER, roundTripped.items.first().garmentType)
    }

    @Test
    fun `OrderItem customGarmentName is null when garmentType is a preset`() {
        val item = OrderItem(
            id = "item-1",
            garmentType = GarmentType.AGBADA,
            description = "",
            price = 5000.0,
        )

        val dto = OrderItemDto(
            id = "item-1",
            garmentType = "AGBADA",
            description = "",
            price = 5000.0,
            customGarmentName = null,
        )

        assertNull(dto.toOrderItem().customGarmentName)
    }
```

(The exact `Order` defaults depend on the existing model — fill in whichever fields are required from the existing test patterns in this file.)

- [ ] **Step 2: Run test, confirm it fails**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.core.data.mapper.OrderMapperTest" --quiet
```

Expected: FAIL with "Unresolved reference: customGarmentName".

- [ ] **Step 3: Add field to domain `OrderItem`**

In `Order.kt`, modify the existing `OrderItem` to include `customGarmentName: String? = null` (insert it after `garmentType`):

```kotlin
data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val customGarmentName: String? = null,   // NEW — set only when garmentType == OTHER
    val description: String,
    val price: Double,
    val measurementId: String? = null,
    val fabricName: String? = null,
    val styleImages: List<StyleImageRef> = emptyList(),
    val fabricImages: List<FabricImageRef> = emptyList(),
    // Legacy fields below unchanged…
)
```

- [ ] **Step 4: Add field to DTO**

In `OrderDto.kt`, modify the existing `OrderItemDto` to include `customGarmentName: String? = null` (insert it after `garmentType`):

```kotlin
@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val customGarmentName: String? = null,   // NEW
    val description: String = "",
    val price: Double = 0.0,
    val measurementId: String? = null,
    val fabricName: String? = null,
    val styleImages: List<StyleImageRefDto> = emptyList(),
    val fabricImages: List<FabricImageRefDto> = emptyList(),
    // Legacy single fields below unchanged…
)
```

- [ ] **Step 5: Update mapper round-trip**

In `OrderMapper.kt`, find the `toOrderItem()` function. It currently sets `garmentType = parseGarmentType(garmentType)`. After that line, add:

```kotlin
        customGarmentName = customGarmentName,
```

Then find the reverse direction (the function building `OrderItemDto` from `OrderItem`, likely `toOrderItemDto()` or inline within `toOrderDto()`). Where it sets `garmentType = garmentType.name`, add immediately after:

```kotlin
        customGarmentName = customGarmentName,
```

- [ ] **Step 6: Run the tests, confirm they pass**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.core.data.mapper.OrderMapperTest" --quiet
```

Expected: PASS — all tests including the two new round-trip tests.

- [ ] **Step 7: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt
git commit -m "feat(garment-picker): add customGarmentName to OrderItem + round-trip"
```

---

## Task 9: Pure-domain `displayGarmentName` helper (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/OrderItemDisplay.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderItemDisplayTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderItemDisplayTest {

    private fun resolver(type: GarmentType): String = "label-${type.name}"

    @Test
    fun `returns custom name when garmentType is OTHER and customGarmentName is non-blank`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = "Iro and Buba",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("Iro and Buba", result)
    }

    @Test
    fun `returns resolved enum label for preset garmentType`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.AGBADA,
            customGarmentName = null,
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-AGBADA", result)
    }

    @Test
    fun `falls back to resolver when garmentType is OTHER but customGarmentName is null`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = null,
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-OTHER", result)
    }

    @Test
    fun `falls back to resolver when garmentType is OTHER but customGarmentName is blank`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = "   ",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-OTHER", result)
    }

    @Test
    fun `ignores customGarmentName when garmentType is a preset`() {
        // Defensive — should never happen, but proves the OR-guard.
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.AGBADA,
            customGarmentName = "Iro and Buba",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-AGBADA", result)
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.core.domain.model.OrderItemDisplayTest" --quiet
```

Expected: FAIL with "Unresolved reference: displayGarmentName".

- [ ] **Step 3: Implement the helper**

Create `OrderItemDisplay.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Resolves the user-visible name of an order item's garment.
 *
 * Custom values (`garmentType == OTHER && customGarmentName != null`) are
 * stored as-typed and returned verbatim. Preset garments fall through to
 * the caller's [resolveLabel] — pass a Compose `stringResource(...)` resolver
 * from a `@Composable` context, or a pre-resolved map lookup from background
 * text builders (receipt sharing).
 */
fun OrderItem.displayGarmentName(resolveLabel: (GarmentType) -> String): String =
    if (garmentType == GarmentType.OTHER && !customGarmentName.isNullOrBlank()) {
        customGarmentName
    } else {
        resolveLabel(garmentType)
    }
```

- [ ] **Step 4: Run tests, confirm they pass**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.core.domain.model.OrderItemDisplayTest" --quiet
```

Expected: PASS (5/5).

- [ ] **Step 5: Compile iOS**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: success.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/OrderItemDisplay.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/model/OrderItemDisplayTest.kt
git commit -m "feat(garment-picker): displayGarmentName pure helper + 5 tests"
```

---

## Task 10: `filterGarmentOptions` pure helper (TDD)

The picker's search algorithm extracted as a pure function so the ViewModel + UI both consume the same filter results and they're unit-testable without Compose.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/GarmentPickerFilter.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/GarmentPickerFilterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GarmentPickerFilterTest {

    private val customs = listOf(
        CustomGarmentType("c1", "Iro and Buba", 1L, 1L),
        CustomGarmentType("c2", "Senator cape", 2L, 2L),
    )
    private val presets = listOf(GarmentType.AGBADA, GarmentType.SENATOR, GarmentType.KAFTAN)
    private fun resolvePreset(type: GarmentType): String = type.name.lowercase()

    @Test
    fun `empty query returns all customs and all presets, addCta hidden`() {
        val result = filterGarmentOptions(
            query = "",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matches custom case-insensitively, addCta hidden`() {
        val result = filterGarmentOptions(
            query = "IRO",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertTrue(result.matchingPresets.isEmpty())
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matches preset substring case-insensitively`() {
        val result = filterGarmentOptions(
            query = "kaf",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.KAFTAN), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query with no matches shows add CTA`() {
        val result = filterGarmentOptions(
            query = "Kente cape",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertTrue(result.matchingPresets.isEmpty())
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `query that exactly matches an existing custom case-insensitively hides add CTA`() {
        val result = filterGarmentOptions(
            query = "iro and buba",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matching a preset hides add CTA even if no exact custom match`() {
        val result = filterGarmentOptions(
            query = "agbada",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.AGBADA), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `blank query (whitespace only) treated as empty`() {
        val result = filterGarmentOptions(
            query = "   ",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.domain.GarmentPickerFilterTest" --quiet
```

Expected: FAIL with "Unresolved reference: filterGarmentOptions".

- [ ] **Step 3: Implement the helper**

Create `GarmentPickerFilter.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType

/**
 * Result of running the picker's search query against the available garment options.
 *
 * @property matchingCustoms tailor-defined customs whose name contains the query (case-insensitive)
 * @property matchingPresets preset enum entries whose label contains the query (case-insensitive)
 * @property showAddCustomCta whether the green "Add '<query>' as a new garment type" affordance
 *   should appear at the top. True only when the query is non-blank AND nothing matches
 *   in either list (preset or custom).
 */
data class GarmentPickerFilterResult(
    val matchingCustoms: List<CustomGarmentType>,
    val matchingPresets: List<GarmentType>,
    val showAddCustomCta: Boolean,
)

/**
 * Pure-domain filter for the garment picker. Used by both the ViewModel
 * (to drive `OrderFormState.pickerSearchQuery` projections) and the UI
 * (to render section visibility) — keeping the algorithm here keeps both
 * consistent and unit-testable.
 *
 * Behavior:
 * - Empty or blank query: return all customs + all presets, no Add CTA.
 * - Non-blank query: case-insensitive substring filter against each list.
 *   Add CTA appears only if BOTH lists are empty after filtering.
 */
fun filterGarmentOptions(
    query: String,
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    resolvePresetLabel: (GarmentType) -> String,
): GarmentPickerFilterResult {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) {
        return GarmentPickerFilterResult(customs, presets, showAddCustomCta = false)
    }
    val matchingCustoms = customs.filter { it.name.lowercase().contains(normalized) }
    val matchingPresets = presets.filter { resolvePresetLabel(it).lowercase().contains(normalized) }
    val showAddCustomCta = matchingCustoms.isEmpty() && matchingPresets.isEmpty()
    return GarmentPickerFilterResult(matchingCustoms, matchingPresets, showAddCustomCta)
}
```

- [ ] **Step 4: Run tests, confirm all pass**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.domain.GarmentPickerFilterTest" --quiet
```

Expected: PASS (7/7).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/GarmentPickerFilter.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/GarmentPickerFilterTest.kt
git commit -m "feat(garment-picker): filterGarmentOptions pure helper + 7 tests"
```

---

## Task 11: Form state — picker state + per-item customGarmentName

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`

- [ ] **Step 1: Add fields to `OrderFormState` and `OrderItemFormState`**

The existing `OrderFormState` data class has fields ending with `errorMessage`. Add four new fields:

```kotlin
data class OrderFormState(
    val currentStep: Int = 1,
    val isEditMode: Boolean = false,
    val customers: List<Customer> = emptyList(),
    val customerSearchQuery: String = "",
    val selectedCustomer: Customer? = null,
    val items: List<OrderItemFormState> = listOf(OrderItemFormState()),
    val availableStyles: List<Style> = emptyList(),
    val availableMeasurements: List<Measurement> = emptyList(),
    val stylePickerSheetForItemId: String? = null,
    // NEW — picker state
    val customGarmentTypes: List<CustomGarmentType> = emptyList(),
    val activePickerItemId: String? = null,
    val pickerSearchQuery: String = "",
    // (rest unchanged: deadline, priority, depositPaid, notes, depositReconciliationPrompt,
    //  isLoading, isSaving, errorMessage)
    val deadline: Long? = null,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val depositPaid: String = "",
    val notes: String = "",
    val depositReconciliationPrompt: DepositPrompt? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
)
```

Add the import at the top:

```kotlin
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
```

In the same file, modify `OrderItemFormState` to add `customGarmentName`:

```kotlin
data class OrderItemFormState(
    val id: String = randomUuid(),
    val garmentType: GarmentType? = null,
    val customGarmentName: String? = null,   // NEW — set only when garmentType == OTHER
    val description: String = "",
    val price: String = "",
    // ... rest unchanged
)
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt
git commit -m "feat(garment-picker): add picker state + customGarmentName to form state"
```

---

## Task 12: Form actions — new picker actions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`

- [ ] **Step 1: Add the new actions**

In `OrderFormAction.kt`, after the existing `OnItemGarmentTypeChange` action, add:

```kotlin
    /** Open the garment picker for a specific item row. */
    data class OnOpenGarmentPicker(val itemId: String) : OrderFormAction

    /**
     * Pick a garment value (preset OR existing custom) from the picker.
     *
     * @param customName Non-null only when [garmentType] is [GarmentType.OTHER].
     *   Stored on the OrderItem; drives display everywhere.
     */
    data class OnPickGarmentType(
        val itemId: String,
        val garmentType: GarmentType,
        val customName: String? = null,
    ) : OrderFormAction

    /**
     * Add a brand-new custom garment value AND pick it for the current item.
     * The ViewModel calls [CustomGarmentTypeRepository.upsert] then internally
     * dispatches [OnPickGarmentType] with the resolved name.
     */
    data class OnAddCustomGarmentType(val itemId: String, val name: String) : OrderFormAction

    /** Update the search query in the open picker. */
    data class OnPickerSearchChange(val query: String) : OrderFormAction

    /** Dismiss the picker without selecting anything. */
    data object OnDismissPicker : OrderFormAction
```

Keep `OnItemGarmentTypeChange` — it's still useful for the "clear garment" path (e.g., the future Settings → delete-custom flow that nulls all referencing items). The picker doesn't use it for normal selection.

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt
git commit -m "feat(garment-picker): add 5 picker actions to OrderFormAction"
```

---

## Task 13: Form event — `ShowCustomSavedSnackbar`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormEvent.kt`

- [ ] **Step 1: Add the event**

Replace the entire `OrderFormEvent.kt` file contents with:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.form

sealed interface OrderFormEvent {
    data object NavigateBack : OrderFormEvent
    data object OrderSaved : OrderFormEvent
    data class ShowCustomSavedSnackbar(val name: String) : OrderFormEvent
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormEvent.kt
git commit -m "feat(garment-picker): add ShowCustomSavedSnackbar event"
```

---

## Task 14: ViewModel — inject repo, subscribe to flow, handle picker actions (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `OrderFormViewModelTest.kt`. Reuse the existing test setup (`@BeforeTest`, `runTest`, etc.) and add these test methods:

```kotlin
    @Test
    fun `subscribes to custom garment types on init`() = runTest {
        val custom = CustomGarmentType("c1", "Iro and Buba", 1L, 1L)
        customGarmentTypeRepository.seed("test-user", listOf(custom))

        viewModel.state.test {
            // skip initial empty emission
            val emitted = awaitItem()  // collected state once flow is wired
            assertEquals(listOf(custom), emitted.customGarmentTypes)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `OnOpenGarmentPicker sets activePickerItemId`() = runTest {
        viewModel.onAction(OrderFormAction.OnOpenGarmentPicker("item-1"))

        assertEquals("item-1", viewModel.state.value.activePickerItemId)
    }

    @Test
    fun `OnDismissPicker clears activePickerItemId and search query`() = runTest {
        viewModel.onAction(OrderFormAction.OnOpenGarmentPicker("item-1"))
        viewModel.onAction(OrderFormAction.OnPickerSearchChange("iro"))

        viewModel.onAction(OrderFormAction.OnDismissPicker)

        assertEquals(null, viewModel.state.value.activePickerItemId)
        assertEquals("", viewModel.state.value.pickerSearchQuery)
    }

    @Test
    fun `OnPickGarmentType preset updates item and closes picker, no touch`() = runTest {
        val itemId = viewModel.state.value.items.first().id
        viewModel.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        viewModel.onAction(OrderFormAction.OnPickGarmentType(itemId, GarmentType.AGBADA))

        val item = viewModel.state.value.items.first()
        assertEquals(GarmentType.AGBADA, item.garmentType)
        assertEquals(null, item.customGarmentName)
        assertEquals(null, viewModel.state.value.activePickerItemId)
        assertTrue(customGarmentTypeRepository.touchCalls.isEmpty())
    }

    @Test
    fun `OnPickGarmentType existing custom calls touch on matching doc`() = runTest {
        val existing = CustomGarmentType("c1", "Iro and Buba", 1L, 1L)
        customGarmentTypeRepository.seed("test-user", listOf(existing))
        // wait for the flow to propagate
        runCurrent()

        val itemId = viewModel.state.value.items.first().id
        viewModel.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        viewModel.onAction(
            OrderFormAction.OnPickGarmentType(itemId, GarmentType.OTHER, "Iro and Buba")
        )

        val item = viewModel.state.value.items.first()
        assertEquals(GarmentType.OTHER, item.garmentType)
        assertEquals("Iro and Buba", item.customGarmentName)
        assertEquals(listOf("test-user" to "c1"), customGarmentTypeRepository.touchCalls)
    }

    @Test
    fun `OnAddCustomGarmentType upserts then updates item and emits snackbar`() = runTest {
        val itemId = viewModel.state.value.items.first().id
        viewModel.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        val events = mutableListOf<OrderFormEvent>()
        val job = launch { viewModel.events.toList(events) }

        viewModel.onAction(OrderFormAction.OnAddCustomGarmentType(itemId, "Kente cape"))
        runCurrent()

        val item = viewModel.state.value.items.first()
        assertEquals(GarmentType.OTHER, item.garmentType)
        assertEquals("Kente cape", item.customGarmentName)
        assertEquals(listOf("test-user" to "Kente cape"), customGarmentTypeRepository.upsertCalls)
        assertTrue(events.contains(OrderFormEvent.ShowCustomSavedSnackbar("Kente cape")))
        job.cancel()
    }
```

You'll need to add a `lateinit var customGarmentTypeRepository: FakeCustomGarmentTypeRepository` field to the test class and instantiate it in `@BeforeTest`, then pass it to the `OrderFormViewModel` constructor.

- [ ] **Step 2: Run tests, confirm they fail**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModelTest" --quiet
```

Expected: FAIL — the ViewModel doesn't have the new constructor param, doesn't handle the new actions.

- [ ] **Step 3: Add the constructor param**

In `OrderFormViewModel.kt`, modify the constructor:

```kotlin
@Suppress("LongParameterList")
class OrderFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val styleRepository: StyleRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val customGarmentTypeRepository: CustomGarmentTypeRepository,   // NEW
) : ViewModel()
```

Add the import at the top:

```kotlin
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
```

- [ ] **Step 4: Subscribe to the flow in `loadInitialData`**

In `loadInitialData()` (or wherever the existing repository subscriptions are launched after `userId` is resolved), add:

```kotlin
        viewModelScope.launch {
            customGarmentTypeRepository.observe(userId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(customGarmentTypes = result.data) }
                    is Result.Error -> Unit  // silent: picker just shows zero customs
                }
            }
        }
```

- [ ] **Step 5: Handle the new actions in `onAction`**

In the existing `when (action)` block, add these branches (immediately after the `OnItemGarmentTypeChange` handler):

```kotlin
            is OrderFormAction.OnOpenGarmentPicker -> {
                _state.update { it.copy(activePickerItemId = action.itemId, pickerSearchQuery = "") }
            }
            is OrderFormAction.OnPickerSearchChange -> {
                _state.update { it.copy(pickerSearchQuery = action.query) }
            }
            OrderFormAction.OnDismissPicker -> {
                _state.update { it.copy(activePickerItemId = null, pickerSearchQuery = "") }
            }
            is OrderFormAction.OnPickGarmentType -> {
                updateItem(action.itemId) {
                    it.copy(
                        garmentType = action.garmentType,
                        customGarmentName = action.customName,
                    )
                }
                _state.update { it.copy(activePickerItemId = null, pickerSearchQuery = "") }

                // Fire-and-forget touch on existing customs (sort-order maintenance).
                if (action.garmentType == GarmentType.OTHER && action.customName != null) {
                    val uid = userId ?: return@launch
                    val match = _state.value.customGarmentTypes
                        .firstOrNull { it.name.equals(action.customName, ignoreCase = true) }
                    if (match != null) {
                        viewModelScope.launch {
                            customGarmentTypeRepository.touch(uid, match.id)
                        }
                    }
                }
            }
            is OrderFormAction.OnAddCustomGarmentType -> {
                val uid = userId ?: return
                viewModelScope.launch {
                    when (val result = customGarmentTypeRepository.upsert(uid, action.name)) {
                        is Result.Success -> {
                            onAction(
                                OrderFormAction.OnPickGarmentType(
                                    itemId = action.itemId,
                                    garmentType = GarmentType.OTHER,
                                    customName = result.data.name,
                                )
                            )
                            _events.send(OrderFormEvent.ShowCustomSavedSnackbar(result.data.name))
                        }
                        is Result.Error -> {
                            // Silent V1: keep picker open, do nothing. Future: emit error.
                        }
                    }
                }
            }
```

Note: the original `return@launch` was inside `onAction`'s caller scope — adjust to match the surrounding function's control flow if needed (most likely you can drop the `?: return@launch` and use `?: return` since `onAction` is not a launch).

- [ ] **Step 6: Update the save path**

Find the `toOrderItem()` function (or wherever `OrderItemFormState` → `OrderItem` conversion happens for save). Add `customGarmentName = customGarmentName,` to the conversion:

```kotlin
private fun OrderItemFormState.toOrderItem(): OrderItem =
    OrderItem(
        id = id,
        garmentType = garmentType ?: GarmentType.SHIRT,  // existing fallback
        customGarmentName = customGarmentName,
        description = description,
        price = price.toDoubleOrNull() ?: 0.0,
        measurementId = measurementId,
        fabricName = fabricName,
        styleImages = styleImageRefs,
        fabricImages = fabricImageRefs,
        // legacy fields…
    )
```

Also update the save-validation filter. The existing logic filters items by `garmentType != null`. Add the OTHER-needs-customGarmentName guard:

```kotlin
val validItems = s.items.filter { item ->
    item.garmentType != null &&
        (item.garmentType != GarmentType.OTHER || !item.customGarmentName.isNullOrBlank())
}
```

- [ ] **Step 7: Run tests, confirm they pass**

```bash
./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModelTest" --quiet
```

Expected: PASS — all existing tests + all 6 new picker tests.

- [ ] **Step 8: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModelTest.kt
git commit -m "feat(garment-picker): handle picker actions in OrderFormViewModel"
```

---

## Task 15: Add picker string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the new strings**

In `strings.xml`, after the existing `order_form_garment_type_label` entry (around line 471), add the new keys. Group them under a new comment block:

```xml
    <!-- Garment picker sheet -->
    <string name="garment_picker_title">What are you making?</string>
    <string name="garment_picker_subtitle">Pick from the list — or type to add your own.</string>
    <string name="garment_picker_search_placeholder">Search or add new…</string>
    <string name="garment_picker_section_my_types">My garment types</string>
    <string name="garment_picker_section_presets">Preset types</string>
    <string name="garment_picker_add_custom_format">Add &quot;%1$s&quot; as a new garment type</string>
    <string name="garment_picker_add_custom_subtext">Saved for next time</string>
    <string name="garment_picker_no_matches_format">No preset matches &quot;%1$s&quot; — that&apos;s OK, add it as your own.</string>
    <string name="garment_picker_custom_pill">Custom</string>
    <string name="garment_picker_saved_snackbar_format">Saved &quot;%1$s&quot; to your garment types.</string>
```

Note: `&quot;` for double quotes inside XML attributes, `&apos;` for apostrophes per saved memory `feedback_strings_no_backslash_escape`.

- [ ] **Step 2: Compile (regenerates the `Res.string.*` references)**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(garment-picker): add 10 picker string resources"
```

---

## Task 16: `GarmentPickerSheet` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/GarmentPickerSheet.kt`

Pattern reference: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/swap/SwapSheet.kt` (`ModalBottomSheet` shell). Visual reference: `preview/garment-picker-redesign.html`.

- [ ] **Step 1: Create the sheet**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.feature.order.domain.filterGarmentOptions
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_picker_add_custom_format
import stitchpad.composeapp.generated.resources.garment_picker_add_custom_subtext
import stitchpad.composeapp.generated.resources.garment_picker_no_matches_format
import stitchpad.composeapp.generated.resources.garment_picker_search_placeholder
import stitchpad.composeapp.generated.resources.garment_picker_section_my_types
import stitchpad.composeapp.generated.resources.garment_picker_section_presets
import stitchpad.composeapp.generated.resources.garment_picker_subtitle
import stitchpad.composeapp.generated.resources.garment_picker_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentPickerSheet(
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPickPreset: (GarmentType) -> Unit,
    onPickCustom: (CustomGarmentType) -> Unit,
    onAddCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        GarmentPickerSheetContent(
            customs = customs,
            presets = presets,
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            onPickPreset = onPickPreset,
            onPickCustom = onPickCustom,
            onAddCustom = onAddCustom,
        )
    }
}

@Composable
private fun GarmentPickerSheetContent(
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPickPreset: (GarmentType) -> Unit,
    onPickCustom: (CustomGarmentType) -> Unit,
    onAddCustom: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.garment_picker_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(Res.string.garment_picker_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(Res.string.garment_picker_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        val resolver: (GarmentType) -> String = { type -> garmentDisplayName(type) }
        val filterResult = filterGarmentOptions(
            query = searchQuery,
            customs = customs,
            presets = presets,
            resolvePresetLabel = resolver,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            if (filterResult.showAddCustomCta) {
                item {
                    AddCustomRow(
                        typedText = searchQuery.trim(),
                        onClick = { onAddCustom(searchQuery.trim()) },
                    )
                }
            }
            if (filterResult.matchingCustoms.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(Res.string.garment_picker_section_my_types),
                        count = filterResult.matchingCustoms.size,
                    )
                }
                items(filterResult.matchingCustoms, key = { it.id }) { custom ->
                    PickerRow(
                        label = custom.name,
                        onClick = { onPickCustom(custom) },
                    )
                }
            }
            item {
                SectionHeader(
                    title = stringResource(Res.string.garment_picker_section_presets),
                    count = filterResult.matchingPresets.size,
                )
            }
            if (filterResult.matchingPresets.isEmpty() && searchQuery.trim().isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            Res.string.garment_picker_no_matches_format,
                            searchQuery.trim()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            } else {
                items(filterResult.matchingPresets, key = { it.name }) { preset ->
                    PickerRow(
                        label = garmentDisplayName(preset),
                        onClick = { onPickPreset(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AddCustomRow(typedText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.garment_picker_add_custom_format, typedText),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.garment_picker_add_custom_subtext),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

- [ ] **Step 2: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/GarmentPickerSheet.kt
git commit -m "feat(garment-picker): GarmentPickerSheet composable"
```

---

## Task 17: Wire picker into `OrderFormScreen` — remove dropdown, add tap target + sheet host

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`

- [ ] **Step 1: Replace `ExposedDropdownMenuBox` with tap target**

In `OrderFormScreen.kt`, locate the existing garment-type dropdown (around line 685-717, the `ExposedDropdownMenuBox` block). Replace the entire block with:

```kotlin
            // Garment-type tap target — opens GarmentPickerSheet
            val filteredGarmentTypes = remember(selectedGenderFilter) {
                GarmentType.entries.filter {
                    it.gender == selectedGenderFilter && it != GarmentType.OTHER
                }
            }
            val displayValue = when {
                item.garmentType == GarmentType.OTHER && !item.customGarmentName.isNullOrBlank() ->
                    item.customGarmentName
                item.garmentType != null -> garmentDisplayName(item.garmentType)
                else -> ""
            }
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.order_form_garment_type_label)) },
                trailingIcon = {
                    if (item.garmentType == GarmentType.OTHER && !item.customGarmentName.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.garment_picker_custom_pill)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(OrderFormAction.OnOpenGarmentPicker(item.id)) },
            )
```

Remove the now-orphaned `var garmentExpanded by remember { mutableStateOf(false) }` if it's still in this block, and any leftover `ExposedDropdownMenu` block.

Update imports — add:

```kotlin
import androidx.compose.material3.AssistChip
import com.danzucker.stitchpad.feature.order.presentation.form.components.GarmentPickerSheet
import stitchpad.composeapp.generated.resources.garment_picker_custom_pill
import stitchpad.composeapp.generated.resources.garment_picker_saved_snackbar_format
```

- [ ] **Step 2: Add the sheet host at the end of the screen**

Find the screen's outermost `Box` or `Scaffold` content lambda. After the existing content (or after any existing sheets like `StylePickerSheet`), add:

```kotlin
            if (state.activePickerItemId != null) {
                GarmentPickerSheet(
                    customs = state.customGarmentTypes,
                    presets = remember(state) {
                        GarmentType.entries.filter {
                            (state.selectedCustomer == null ||
                                it.gender == state.selectedCustomer.preferredGender) &&
                                it != GarmentType.OTHER
                        }
                    },
                    searchQuery = state.pickerSearchQuery,
                    onSearchChange = { onAction(OrderFormAction.OnPickerSearchChange(it)) },
                    onPickPreset = { type ->
                        onAction(
                            OrderFormAction.OnPickGarmentType(
                                itemId = state.activePickerItemId!!,
                                garmentType = type,
                                customName = null,
                            )
                        )
                    },
                    onPickCustom = { custom ->
                        onAction(
                            OrderFormAction.OnPickGarmentType(
                                itemId = state.activePickerItemId!!,
                                garmentType = GarmentType.OTHER,
                                customName = custom.name,
                            )
                        )
                    },
                    onAddCustom = { typed ->
                        onAction(
                            OrderFormAction.OnAddCustomGarmentType(
                                itemId = state.activePickerItemId!!,
                                name = typed,
                            )
                        )
                    },
                    onDismiss = { onAction(OrderFormAction.OnDismissPicker) },
                )
            }
```

Note: the `state.selectedCustomer.preferredGender` access depends on the existing Customer model — replicate whatever filter the old `ExposedDropdownMenuBox` used. If the existing code uses a different gender filter variable, mirror it.

- [ ] **Step 3: Wire the snackbar event**

In the `ObserveAsEvents(viewModel.events)` block, add the new event branch:

```kotlin
            is OrderFormEvent.ShowCustomSavedSnackbar -> {
                snackbarHostState.showSnackbar(
                    message = stringResolver.getString(
                        Res.string.garment_picker_saved_snackbar_format,
                        event.name
                    )
                )
            }
```

If the existing `ObserveAsEvents` collector isn't `suspend` or doesn't have access to `stringResolver`, use the existing snackbar pattern in this file (look for any other `showSnackbar(...)` call as a template).

- [ ] **Step 4: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt
git commit -m "feat(garment-picker): wire GarmentPickerSheet into OrderFormScreen"
```

---

## Task 18: Update all display call sites to use `displayGarmentName`

The order detail screen, pipeline rows, dashboard previews, and receipt all currently call `garmentDisplayName(item.garmentType)` directly. They need to route through the new `displayGarmentName { resolver }` helper so custom names render correctly.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`
- Modify: any pipeline-row composable that currently calls `garmentDisplayName(...)` on an OrderItem
- Modify: any receipt/share renderer that currently calls `garmentDisplayName(...)` on an OrderItem

- [ ] **Step 1: Find all call sites**

```bash
grep -rn "garmentDisplayName(" /Users/danzucker/Desktop/Project/StitchPad/composeApp/src/commonMain/kotlin/ | grep -v "GarmentDisplayName.kt"
```

For each call site that takes a `GarmentType` directly off an `OrderItem`, replace it with the new helper.

- [ ] **Step 2: Update each call site**

Pattern — replace:

```kotlin
val name = garmentDisplayName(item.garmentType)
```

with:

```kotlin
val name = item.displayGarmentName { garmentDisplayName(it) }
```

Add this import where needed:

```kotlin
import com.danzucker.stitchpad.core.domain.model.displayGarmentName
```

For call sites that pass `GarmentType` (not `OrderItem`) — e.g. inside the picker sheet itself — leave them as-is. The new helper is only for `OrderItem` contexts.

- [ ] **Step 3: Compile both platforms**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --quiet
./gradlew :composeApp:compileKotlinIosSimulatorArm64 --quiet
```

Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(garment-picker): route all OrderItem display sites through displayGarmentName"
```

---

## Task 19: Run all tests + detekt

- [ ] **Step 1: Full test suite**

```bash
./gradlew :composeApp:allTests --quiet
```

Expected: success — all existing tests + the new ones added in Tasks 6, 8, 9, 10, 14.

- [ ] **Step 2: Detekt**

```bash
./gradlew detekt --quiet
```

Expected: success (zero warnings, zero errors).

- [ ] **Step 3: If anything fails, fix it; commit fixes if needed**

If detekt flags issues (long parameter lists, unused imports, etc.), fix and commit:

```bash
git add -A
git commit -m "fix(garment-picker): address detekt findings"
```

---

## Task 20: Final iOS link check + manual smoke test

- [ ] **Step 1: Build iOS framework end-to-end**

```bash
./gradlew :composeApp:linkPodDebugFrameworkIosSimulatorArm64 --quiet
```

Expected: success. This is a stricter test than the per-task compileKotlinIosSimulatorArm64 — it also links and surfaces issues like missing iOS-side stdlib calls (per saved memory `feedback_kmp_jvm_only_apis`).

- [ ] **Step 2: Run the app on a simulator (Daniel does this, not the agent)**

Smoke checklist (Daniel runs this manually before opening the PR):

1. Open the app, sign in as Fola.
2. Tap the FAB → **New order**.
3. Step 1: pick a customer → Next.
4. Step 2 (Items): tap **What are you making?** → **the picker sheet should open** with the search field at top and "Preset types" section visible.
5. **First-time tailor:** "My garment types" section is HIDDEN.
6. Type "kaf" → "Preset types" filters to Kaftan only. No "Add" row.
7. Clear search, type "Kente cape" → green "Add 'Kente cape' as a new garment type" row appears. Tap it.
8. Sheet dismisses. Field shows **"Kente cape"** with a **Custom** chip. Snackbar reads `Saved "Kente cape" to your garment types.`
9. Tap the field again. Picker opens. **"My garment types"** section now visible with "Kente cape". Type "KENTE" — case-insensitive match → existing row highlighted, NO "Add" CTA.
10. Pick existing "Kente cape" → sheet dismisses, field still shows "Kente cape" with chip.
11. Enter a price, complete the order. Verify the detail screen shows "Kente cape" as plain text (no chip on detail screen — chip is form-only).
12. Open the receipt share — verify it shows "Kente cape" not "Other".
13. Open the order in edit mode — verify the picker preserves the custom value selection.
14. **Negative case:** create an order, leave the garment unset, try to save → snackbar reads `Pick what you're making to save this order.`

- [ ] **Step 3: Commit the smoke-test record (if any UI tweaks were needed)**

If smoke surfaced any UI polish needs, fix and commit:

```bash
git add -A
git commit -m "polish(garment-picker): smoke-test feedback"
```

---

## Task 21: Push branch and open PR

- [ ] **Step 1: Push**

```bash
git push -u origin feature/garment-picker-search-and-customs
```

The pre-push hook will run `codex review` — read its output and fix any P1/P2 findings before continuing.

- [ ] **Step 2: Open PR**

```bash
gh pr create --base main --title "feat(order-form): searchable garment picker with custom values" --body "$(cat <<'EOF'
## Summary

Second slice of the order-form garment field redesign (after PR #84 shipped the label rename).

Replaces the closed-enum `ExposedDropdownMenuBox` with a searchable bottom-sheet picker that supports:
- **Live search** across both preset enum entries and the tailor's saved customs
- **Per-tailor custom garment names** (e.g. "Iro and Buba", "Kente cape") persisted in a new Firestore subcollection
- **Recency sort** — most-recently-used customs surface first
- **Case-insensitive dedupe** — typing "iro and buba" when "Iro and Buba" exists highlights the existing entry instead of creating a duplicate
- **Form-only Custom badge** — receipt and order detail stay clean of internal-app concepts

## Design references

- Spec: `docs/superpowers/specs/2026-05-27-garment-picker-search-and-custom-values-design.md`
- Mockup: `preview/garment-picker-redesign.html` (5 phone states)
- Plan: `docs/superpowers/plans/2026-05-27-garment-picker-search-and-customs.md`

## Data model

Strictly additive — no migration script.

- New subcollection `users/{uid}/customGarmentTypes/{id}` with `{ name, createdAt, lastUsedAt }`
- New `GarmentType.OTHER` enum entry
- New nullable `OrderItem.customGarmentName: String?` (Firestore reads existing docs as `null`)

## Smoke test

See the implementation plan (Task 20) for the 14-step manual smoke. Highlights:
- First-time tailor: "My garment types" section is hidden
- Type a new garment name → green "Add" row → tap → custom pill + snackbar
- Reopen picker → custom appears in "My garment types"
- Case-insensitive dedupe verified
- Order detail / receipt show the custom name as plain text (no pill)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Wait for CI + Cursor BugBot**

CI runs `detekt`, `secrets-scan`, `functions-tests`, `build-android`, `build-ios`. Cursor BugBot reviews on PR open. Address any findings before requesting merge.

---

## Self-Review

(Confirmed before plan handoff:)

1. **Spec coverage** — every section of the spec maps to a task:
   - Spec §4 Data model → Tasks 1, 2, 7, 8
   - Spec §5 Repository → Tasks 3, 4, 5, 6
   - Spec §6 Picker UX → Tasks 10, 16
   - Spec §7 Form-side → Tasks 11, 12, 13, 14, 17
   - Spec §8 Display rules → Tasks 9, 18
   - Spec §9 Receipt grouping → falls out of Task 18 (no new task needed; receipt uses `displayGarmentName`)
   - Spec §10 Migration → covered by additive field defaults (Tasks 7, 8)
   - Spec §11 Tests → Tasks 6, 8, 9, 10, 14
   - Spec §13 Risks → Task 4 (upsert dedupe + offline behavior), Task 14 (touch fire-and-forget)

2. **Placeholder scan** — no TBDs, no "handle edge cases" without specifics, every code step has complete code.

3. **Type consistency** — `displayGarmentName(resolveLabel)` signature consistent across Tasks 9, 18. `CustomGarmentType` fields consistent across Tasks 1, 2, 4, 6. `OrderFormAction.OnPickGarmentType` signature consistent across Tasks 12, 14, 17. `customGarmentName` field name consistent everywhere.
