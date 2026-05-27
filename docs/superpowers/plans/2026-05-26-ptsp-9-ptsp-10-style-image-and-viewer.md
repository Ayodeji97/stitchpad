# Order Form — Style Image + Fullscreen Viewer Implementation Plan (PTSP-9 + PTSP-10)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tailors attach a style image during order creation (PTSP-9) and tap any fabric/style thumbnail to view it fullscreen (PTSP-10).

**Architecture:** Mirror the existing fabric-photo flow. Add `stylePhotoUrl`/`stylePhotoStoragePath` to `OrderItem` and the DTO/mapper. Extend `OrderRepository` with `uploadStylePhoto` (parallel to `uploadFabricPhoto`). The form's per-item card replaces the existing style dropdown with a unified Style section that has three states (empty/picked/uploaded). A "Save to gallery" toggle (default ON) decides whether to create a new `Style` entity via `StyleRepository.createStyle` or store the image one-off on the `OrderItem`. A new reusable `FullScreenImageViewer` Dialog handles PTSP-10 in both the form and the detail screen.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Coil 3, MVI, GitLive Firebase SDK, Firebase Storage.

**Spec:** `docs/superpowers/specs/2026-05-26-ptsp-9-ptsp-10-style-image-and-viewer-design.md`.

**Branch:** `feature/ptsp-9-style-image-on-new-order` (already checked out off latest `main`).

---

## File Map

| File                                                                                                                                            | Change                                                                                                  |
|-------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`                                                            | Add `stylePhotoUrl` + `stylePhotoStoragePath` to `OrderItem` (Task 1).                                   |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`                                                             | Mirror the 2 new fields on `OrderItemDto` (Task 1).                                                      |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`                                                       | Round-trip the 2 new fields in `toOrderItem` + `toOrderItemDto` (Task 1).                                |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`                                                   | Test the round-trip for the new fields (Task 1).                                                        |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`                                             | Add `uploadStylePhoto(...)` declaration (Task 2).                                                        |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`                                         | Implement `uploadStylePhoto` + `styleStoragePath` helper (Task 2).                                      |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt`                                           | Implement `uploadStylePhoto` (Task 2).                                                                  |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`                                     | Add 5 style fields to `OrderItemFormState` (Task 3).                                                     |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`                                    | Add 5 new actions (Task 3).                                                                              |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`                                 | Handle 5 new actions; extend `save()`; teach `toOrderItemFormState` about the new fields (Task 4).        |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt` *(new)*                | ModalBottomSheet listing customer gallery styles (Task 5).                                              |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`                                    | Replace style dropdown with unified Style section (Task 6); wire viewer on fabric + style thumbnails (Task 8). |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FullScreenImageViewer.kt` *(new)*                                        | Reusable viewer Dialog (Task 7).                                                                         |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`               | Wrap `FabricThumbnail` in clickable + viewer (Task 9).                                                  |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`                         | Wrap style image in clickable + viewer; accept fallback `itemStylePhotoUrl` (Task 9, 10).               |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`                                | Pass `item.stylePhotoUrl` fallback into `OrderHeroCard` (Task 10).                                       |
| `composeApp/src/commonMain/composeResources/values/strings.xml`                                                                                 | 11 new string keys (Task 11).                                                                            |

**Test coverage decision:** The existing `OrderFormViewModel` has no test file. Adding a first test for it requires non-trivial fake setup (5 repository dependencies). Out of scope per the spec ("in-scope-when-easy, not blocking"). We add ONE new test — the mapper round-trip — because it's a pure-function 5-line test.

---

### Task 1: Add style photo fields to domain model + DTO + mapper

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Modify or create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`

- [ ] **Step 1: Add fields to `OrderItem`**

Open `core/domain/model/Order.kt`, find the `OrderItem` data class (around line 27):

```kotlin
data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
)
```

Replace with:

```kotlin
data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
)
```

- [ ] **Step 2: Add fields to `OrderItemDto`**

Open `core/data/dto/OrderDto.kt`, find `OrderItemDto` (around line 39):

```kotlin
@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
)
```

Replace with:

```kotlin
@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
)
```

Null defaults guarantee that old Firestore docs (without these fields) deserialize cleanly.

- [ ] **Step 3: Round-trip the fields in the mapper**

Open `core/data/mapper/OrderMapper.kt`. Update `OrderItemDto.toOrderItem` (around line 117):

OLD:
```kotlin
fun OrderItemDto.toOrderItem(): OrderItem = OrderItem(
    id = id,
    garmentType = parseGarmentType(garmentType),
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath,
    fabricName = fabricName,
)
```

NEW:
```kotlin
fun OrderItemDto.toOrderItem(): OrderItem = OrderItem(
    id = id,
    garmentType = parseGarmentType(garmentType),
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath,
    fabricName = fabricName,
    stylePhotoUrl = stylePhotoUrl,
    stylePhotoStoragePath = stylePhotoStoragePath,
)
```

Then `OrderItem.toOrderItemDto` (around line 135):

OLD:
```kotlin
fun OrderItem.toOrderItemDto(): OrderItemDto = OrderItemDto(
    id = id,
    garmentType = garmentType.name,
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath,
    fabricName = fabricName,
)
```

NEW:
```kotlin
fun OrderItem.toOrderItemDto(): OrderItemDto = OrderItemDto(
    id = id,
    garmentType = garmentType.name,
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath,
    fabricName = fabricName,
    stylePhotoUrl = stylePhotoUrl,
    stylePhotoStoragePath = stylePhotoStoragePath,
)
```

- [ ] **Step 4: Add mapper test for the new fields**

Open `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`. Find a place to add a new test function near the other `OrderItem` tests (look for existing `OrderItem.toOrderItemDto` or `toOrderItem` tests). Add:

```kotlin
@Test
fun `OrderItem round-trips stylePhotoUrl and stylePhotoStoragePath through DTO`() {
    val item = OrderItem(
        id = "item-1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
        stylePhotoUrl = "https://example.com/style.jpg",
        stylePhotoStoragePath = "users/u1/orders/o1/styles/item-1.jpg",
    )

    val roundTripped = item.toOrderItemDto().toOrderItem()

    assertThat(roundTripped.stylePhotoUrl).isEqualTo("https://example.com/style.jpg")
    assertThat(roundTripped.stylePhotoStoragePath).isEqualTo("users/u1/orders/o1/styles/item-1.jpg")
}

@Test
fun `OrderItem round-trips null style photo fields through DTO`() {
    val item = OrderItem(
        id = "item-1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
    )

    val roundTripped = item.toOrderItemDto().toOrderItem()

    assertThat(roundTripped.stylePhotoUrl).isNull()
    assertThat(roundTripped.stylePhotoStoragePath).isNull()
}
```

If the existing test file uses different imports (e.g., `assertEquals` from JUnit instead of `assertThat` from AssertK), match the existing style. Check the existing tests in the file before writing — pattern-match.

- [ ] **Step 5: Compile + test**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:allTests --tests "*OrderMapperTest*"
```

Both must be BUILD SUCCESSFUL.

- [ ] **Step 6: Commit (after Task 2 also lands — repository changes are coupled)**

Don't commit yet. Task 2 is part of the same logical layer change.

---

### Task 2: Add `uploadStylePhoto` to the repository

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt`

- [ ] **Step 1: Add `uploadStylePhoto` to the interface**

In `OrderRepository.kt`, find `uploadFabricPhoto` (around line 51). Add a parallel declaration right after it:

```kotlin
suspend fun uploadStylePhoto(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytes: ByteArray
): Result<Pair<String, String>, DataError.Network>
```

Match the exact shape of `uploadFabricPhoto`.

- [ ] **Step 2: Implement it on `FirebaseOrderRepository`**

In `FirebaseOrderRepository.kt`, find the private helper `fabricStoragePath` (around line 37):

```kotlin
private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/fabrics/$itemId.jpg"
```

Add a parallel helper right after it:

```kotlin
private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/styles/$itemId.jpg"
```

Then find `uploadFabricPhoto` (around line 292). Add a parallel implementation right after the closing brace of `uploadFabricPhoto`:

```kotlin
override suspend fun uploadStylePhoto(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytes: ByteArray
): Result<Pair<String, String>, DataError.Network> {
    val path = styleStoragePath(userId, orderId, itemId)
    return try {
        storage.reference.child(path).putData(photoBytes.toStorageData())
        val downloadUrl = storage.reference.child(path).getDownloadUrl()
        Result.Success(downloadUrl to path)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.e(tag = TAG, throwable = e) { "uploadStylePhoto failed itemId=$itemId" }
        Result.Error(DataError.Network.UNKNOWN)
    }
}
```

- [ ] **Step 3: Implement on `FakeOrderRepository`**

In `FakeOrderRepository.kt`, find `uploadFabricPhoto` (around line 158). Mirror it:

```kotlin
override suspend fun uploadStylePhoto(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytes: ByteArray
): Result<Pair<String, String>, DataError.Network> {
    return Result.Success(
        "https://fake.example/styles/$orderId/$itemId.jpg" to "users/$userId/orders/$orderId/styles/$itemId.jpg"
    )
}
```

If the existing `uploadFabricPhoto` in the fake takes a different shape (e.g., a flag to simulate failure), match that shape exactly.

- [ ] **Step 4: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Both must be BUILD SUCCESSFUL.

- [ ] **Step 5: Do not commit yet** — Task 3 + 4 build on this.

---

### Task 3: Extend `OrderItemFormState` + actions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`

- [ ] **Step 1: Add fields to `OrderItemFormState`**

In `OrderFormState.kt`, replace the existing `OrderItemFormState` with:

```kotlin
data class OrderItemFormState
@OptIn(ExperimentalUuidApi::class)
constructor(
    val id: String = Uuid.random().toString(),
    val garmentType: GarmentType? = null,
    val description: String = "",
    val price: String = "",
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoBytes: ByteArray? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String = "",
    // PTSP-9 style image
    val stylePhotoBytes: ByteArray? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val styleDescription: String = "",
    /** When true (default), save() creates a new Style entity. When false, the image lives on the OrderItem only. */
    val saveStyleToGallery: Boolean = true,
)
```

5 new fields appended.

- [ ] **Step 2: Add the new actions**

In `OrderFormAction.kt`, find the "Step 2 - Items" block. After `OnItemFabricNameChange`, add:

```kotlin
    // PTSP-9 style image actions
    data class OnItemStylePhotoPicked(val itemId: String, val photoBytes: ByteArray) : OrderFormAction
    data class OnItemStylePhotoRemoved(val itemId: String) : OrderFormAction
    data class OnItemStyleDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemSaveStyleToGalleryToggle(val itemId: String, val value: Boolean) : OrderFormAction
    data object OnDismissStylePickerSheet : OrderFormAction
    data class OnOpenStylePickerSheet(val itemId: String) : OrderFormAction
```

(Note: 6 actions, not 5 — the spec mentioned 5, but we also need `OnDismissStylePickerSheet` and `OnOpenStylePickerSheet` to drive the picker sheet visibility. The existing `OnItemStyleChange` already exists and is reused for "user picked a gallery style".)

Also add to the existing `OrderFormState` (not the item state) one new field. In `OrderFormState.kt`, find the data class declaration (top-level, around line 12). Add:

```kotlin
    /** Item id whose Style picker sheet is currently visible. Null = no sheet. */
    val stylePickerSheetForItemId: String? = null,
```

Place it after the `availableMeasurements: List<Measurement> = emptyList(),` line.

- [ ] **Step 3: Don't compile yet** — VM handlers come in Task 4.

---

### Task 4: VM handlers + save() extension

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

**Imports needed at the top of the file** (verify they're not already present):

```kotlin
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
```

The class already takes a `StyleRepository` parameter (we use it for `observeStyles` already — verify by grep before adding).

Actually — looking at the class:
- `customerRepository: CustomerRepository` ✅
- `orderRepository: OrderRepository` ✅
- `styleRepository: StyleRepository` ✅ (already injected for `observeStyles`)
- `measurementRepository: MeasurementRepository` ✅
- `authRepository: AuthRepository` ✅

So no constructor changes needed; `styleRepository` is available.

- [ ] **Step 1: Handle the 6 new actions in `onAction`**

In `OrderFormViewModel.kt`, find the `onAction` `when` block. After the `OnItemFabricPhotoRemoved` branch, add:

```kotlin
            is OrderFormAction.OnItemStylePhotoPicked -> {
                updateItem(action.itemId) {
                    // Keep stylePhotoUrl/StoragePath intact as upload fallback: if the new
                    // upload fails, uploadStylePhotoIfNeeded() preserves the existing remote.
                    it.copy(stylePhotoBytes = action.photoBytes)
                }
            }
            is OrderFormAction.OnItemStylePhotoRemoved -> {
                updateItem(action.itemId) {
                    it.copy(
                        stylePhotoBytes = null,
                        stylePhotoUrl = null,
                        stylePhotoStoragePath = null,
                        styleDescription = "",
                        saveStyleToGallery = true,
                    )
                }
            }
            is OrderFormAction.OnItemStyleDescriptionChange -> {
                updateItem(action.itemId) { it.copy(styleDescription = action.description) }
            }
            is OrderFormAction.OnItemSaveStyleToGalleryToggle -> {
                updateItem(action.itemId) { it.copy(saveStyleToGallery = action.value) }
            }
            is OrderFormAction.OnOpenStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = action.itemId) }
            }
            OrderFormAction.OnDismissStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = null) }
            }
```

The `updateItem` helper already exists (check around the fabric-photo handlers; if it doesn't, replicate the fabric handler's `_state.update { it.copy(items = ...) }` shape).

- [ ] **Step 2: Update `save()` to handle the style upload + Style creation**

Find the existing block inside `save()` where each `OrderItem` is built (around lines 341–358):

OLD:
```kotlin
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
```

NEW:
```kotlin
            val orderItems = formItems.map { item ->
                val garmentType = item.garmentType!!
                val price = item.price.toDoubleOrNull() ?: 0.0

                val (fabricUrl, fabricPath) = uploadFabricPhotoIfNeeded(uid, actualOrderId, item)
                val styleResolution = resolveStylePhoto(uid, customer.id, actualOrderId, item)

                OrderItem(
                    id = item.id,
                    garmentType = garmentType,
                    description = item.description.trim(),
                    price = price,
                    styleId = styleResolution.styleId,
                    measurementId = item.measurementId,
                    fabricPhotoUrl = fabricUrl,
                    fabricPhotoStoragePath = fabricPath,
                    fabricName = item.fabricName.trim().ifBlank { null },
                    stylePhotoUrl = styleResolution.photoUrl,
                    stylePhotoStoragePath = styleResolution.photoStoragePath,
                )
            }
```

- [ ] **Step 3: Add the `resolveStylePhoto` helper at the bottom of the class**

After `uploadFabricPhotoIfNeeded` (around line 437), add:

```kotlin
    /**
     * Result holder: which of the three style states does this item resolve to after save?
     */
    private data class StyleResolution(
        val styleId: String?,
        val photoUrl: String?,
        val photoStoragePath: String?,
    )

    /**
     * Per-item style image handling at save time:
     *  - No bytes & no toggle change → carry existing values (pre-PTSP-9 orders, or "no style").
     *  - Bytes + saveStyleToGallery=true → create a Style entity; styleId points to it,
     *    photo lives on the Style (not the OrderItem).
     *  - Bytes + saveStyleToGallery=false → upload to Firebase Storage; photoUrl/Path live
     *    on the OrderItem, styleId stays null.
     *
     * On upload/create failure, the existing remote values (if any) are preserved — same
     * resilient-fallback pattern as uploadFabricPhotoIfNeeded.
     */
    private suspend fun resolveStylePhoto(
        userId: String,
        customerId: String,
        orderId: String,
        item: OrderItemFormState,
    ): StyleResolution {
        val bytes = item.stylePhotoBytes
        if (bytes == null) {
            return StyleResolution(
                styleId = item.styleId,
                photoUrl = item.stylePhotoUrl,
                photoStoragePath = item.stylePhotoStoragePath,
            )
        }

        if (item.saveStyleToGallery) {
            val createResult = styleRepository.createStyle(
                userId = userId,
                customerId = customerId,
                description = item.styleDescription.trim(),
                photoBytes = bytes,
            )
            return when (createResult) {
                is Result.Success -> StyleResolution(
                    styleId = createResult.data,
                    photoUrl = null,
                    photoStoragePath = null,
                )
                is Result.Error -> StyleResolution(
                    styleId = item.styleId,
                    photoUrl = item.stylePhotoUrl,
                    photoStoragePath = item.stylePhotoStoragePath,
                )
            }
        }

        val uploadResult = orderRepository.uploadStylePhoto(
            userId = userId,
            orderId = orderId,
            itemId = item.id,
            photoBytes = bytes,
        )
        return when (uploadResult) {
            is Result.Success -> StyleResolution(
                styleId = null,
                photoUrl = uploadResult.data.first,
                photoStoragePath = uploadResult.data.second,
            )
            is Result.Error -> StyleResolution(
                styleId = item.styleId,
                photoUrl = item.stylePhotoUrl,
                photoStoragePath = item.stylePhotoStoragePath,
            )
        }
    }
```

- [ ] **Step 4: Update `OrderItem.toOrderItemFormState()` to include style fields**

Find the existing `toOrderItemFormState` extension (around lines 290–307 in the file). It currently maps from `OrderItem` to `OrderItemFormState` when loading an existing order. Update it:

OLD:
```kotlin
    private fun OrderItem.toOrderItemFormState() = OrderItemFormState(
        id = id,
        garmentType = garmentType,
        description = description,
        price = if (price > 0) price.toLong().toString() else "",
        styleId = styleId,
        measurementId = measurementId,
        fabricPhotoUrl = fabricPhotoUrl,
        fabricPhotoStoragePath = fabricPhotoStoragePath,
        fabricName = fabricName.orEmpty(),
    )
```

NEW:
```kotlin
    private fun OrderItem.toOrderItemFormState() = OrderItemFormState(
        id = id,
        garmentType = garmentType,
        description = description,
        price = if (price > 0) price.toLong().toString() else "",
        styleId = styleId,
        measurementId = measurementId,
        fabricPhotoUrl = fabricPhotoUrl,
        fabricPhotoStoragePath = fabricPhotoStoragePath,
        fabricName = fabricName.orEmpty(),
        stylePhotoUrl = stylePhotoUrl,
        stylePhotoStoragePath = stylePhotoStoragePath,
        // Edit mode: previously-uploaded one-off images can't be edited (no description, no toggle).
        // They render as State C-readonly per the spec. Default fields here mean "no inline editing
        // available" — the screen layer reads `stylePhotoUrl != null && styleId == null` to detect this.
    )
```

- [ ] **Step 5: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Must be BUILD SUCCESSFUL. If `updateItem` doesn't exist as a helper, replicate the fabric-handler shape inline (e.g., `_state.update { state -> state.copy(items = state.items.map { if (it.id == action.itemId) it.copy(...) else it }) }`).

- [ ] **Step 6: Do not commit yet** — UI changes coming.

---

### Task 5: Create `StylePickerSheet`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_style_picker_empty
import stitchpad.composeapp.generated.resources.order_form_style_picker_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    styles: List<Style>,
    onSelect: (Style) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.order_form_style_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space4,
                    vertical = DesignTokens.space3,
                ),
            )
            if (styles.isEmpty()) {
                Text(
                    text = stringResource(Res.string.order_form_style_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space4,
                        vertical = DesignTokens.space4,
                    ),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = styles, key = { it.id }) { style ->
                        StylePickerRow(style = style, onClick = { onSelect(style) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePickerRow(
    style: Style,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space2,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusMd))
                .padding(0.dp),
        ) {
            SubcomposeAsyncImage(
                model = style.photoUrl,
                contentDescription = null,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LoadingDots(dotSize = 4.dp)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.description.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 2: Don't compile yet** — strings come in Task 11. Continue to Task 6.

---

### Task 6: Replace style dropdown with unified Style section in `OrderFormScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`

This is the biggest UI change. Touches the per-item card composable.

- [ ] **Step 1: Add the new imports**

At the top of `OrderFormScreen.kt`, add:

```kotlin
import androidx.compose.material3.Switch
import com.danzucker.stitchpad.feature.order.presentation.form.components.StylePickerSheet
import stitchpad.composeapp.generated.resources.order_form_style_section_title
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_gallery
import stitchpad.composeapp.generated.resources.order_form_style_upload_new
import stitchpad.composeapp.generated.resources.order_form_style_description_label
import stitchpad.composeapp.generated.resources.order_form_style_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_style_save_to_gallery
import stitchpad.composeapp.generated.resources.order_form_style_from_gallery_caption
import stitchpad.composeapp.generated.resources.order_form_style_change
import stitchpad.composeapp.generated.resources.order_form_style_remove
```

(`Switch` for the toggle.)

- [ ] **Step 2: Delete the existing style dropdown block**

Find the block around lines 682–730:

```kotlin
            // Style picker (optional)
            if (availableStyles.isNotEmpty()) {
                Spacer(Modifier.height(DesignTokens.space2))
                var styleExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = styleExpanded,
                    // ... ExposedDropdownMenu body ...
                ) {
                    // ... DropdownMenuItem(...) bodies ...
                }
            }
```

Delete the entire `// Style picker (optional)` block (the conditional `if (availableStyles.isNotEmpty()) { ... }`).

- [ ] **Step 3: Insert the new unified Style section in the same place**

In the spot where the deleted block was, insert:

```kotlin
            // PTSP-9 Style section — replaces the prior dropdown with a unified
            // pick-or-upload UI. Always rendered; the "Pick from gallery" button
            // is disabled when the customer has no gallery styles.
            Spacer(Modifier.height(DesignTokens.space3))
            StyleSection(
                item = item,
                availableStyles = availableStyles,
                onAction = onAction,
            )
```

- [ ] **Step 4: Add the `StyleSection` composable + helpers at the bottom of the file**

After all the existing private composables in `OrderFormScreen.kt` (before the previews), add:

```kotlin
@Composable
private fun StyleSection(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onAction: (OrderFormAction) -> Unit,
) {
    Text(
        text = stringResource(Res.string.order_form_style_section_title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(DesignTokens.space2))

    val pickedStyle = availableStyles.find { it.id == item.styleId }
    val hasUploadBytes = item.stylePhotoBytes != null
    val hasUploadUrl = item.stylePhotoUrl != null && item.styleId == null

    when {
        // State C — new image uploaded inline (bytes still in memory)
        hasUploadBytes -> {
            StyleSectionUploaded(
                item = item,
                isEditable = true,
                onAction = onAction,
            )
        }
        // State C-readonly — order being edited, image was previously uploaded one-off
        hasUploadUrl -> {
            StyleSectionUploaded(
                item = item,
                isEditable = false,
                onAction = onAction,
            )
        }
        // State B — existing gallery style picked
        pickedStyle != null -> {
            StyleSectionExisting(
                style = pickedStyle,
                onChange = { onAction(OrderFormAction.OnOpenStylePickerSheet(item.id)) },
                onRemove = { onAction(OrderFormAction.OnItemStyleChange(item.id, null)) },
            )
        }
        // State A — empty
        else -> {
            StyleSectionEmpty(
                itemId = item.id,
                hasGalleryStyles = availableStyles.isNotEmpty(),
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun StyleSectionEmpty(
    itemId: String,
    hasGalleryStyles: Boolean,
    onAction: (OrderFormAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = { onAction(OrderFormAction.OnOpenStylePickerSheet(itemId)) },
            enabled = hasGalleryStyles,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(Res.string.order_form_style_pick_from_gallery))
        }
        StyleUploadButton(
            itemId = itemId,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StyleSectionExisting(
    style: com.danzucker.stitchpad.core.domain.model.Style,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd)),
                ) {
                    SubcomposeAsyncImage(
                        model = style.photoUrl,
                        contentDescription = null,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots(dotSize = 5.dp)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.description.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.order_form_style_from_gallery_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onChange) {
                    Text(stringResource(Res.string.order_form_style_change))
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(Res.string.order_form_style_remove))
                }
            }
        }
    }
}

@Composable
private fun StyleSectionUploaded(
    item: OrderItemFormState,
    isEditable: Boolean,
    onAction: (OrderFormAction) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                val imageModel: Any? = item.stylePhotoBytes ?: item.stylePhotoUrl
                if (imageModel != null) {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isEditable) {
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = item.styleDescription,
                    onValueChange = {
                        onAction(OrderFormAction.OnItemStyleDescriptionChange(item.id, it))
                    },
                    label = { Text(stringResource(Res.string.order_form_style_description_label)) },
                    placeholder = { Text(stringResource(Res.string.order_form_style_description_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    Switch(
                        checked = item.saveStyleToGallery,
                        onCheckedChange = {
                            onAction(OrderFormAction.OnItemSaveStyleToGalleryToggle(item.id, it))
                        },
                    )
                    Text(
                        text = stringResource(Res.string.order_form_style_save_to_gallery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleUploadButton(
                    itemId = item.id,
                    onAction = onAction,
                    label = stringResource(Res.string.order_form_style_change),
                )
                TextButton(onClick = { onAction(OrderFormAction.OnItemStylePhotoRemoved(item.id)) }) {
                    Text(stringResource(Res.string.order_form_style_remove))
                }
            }
        }
    }
}
```

- [ ] **Step 5: Add the `StyleUploadButton` (mirrors `FabricPhotoPickerButton`)**

Below the StyleSection composables, add:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleUploadButton(
    itemId: String,
    onAction: (OrderFormAction) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val pickerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showSheet by remember { mutableStateOf(false) }
    var pendingSource by remember { mutableStateOf<PhotoSource?>(null) }

    val galleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                onAction(OrderFormAction.OnItemStylePhotoPicked(itemId, it))
            }
        }
    )
    val cameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) {
            onAction(OrderFormAction.OnItemStylePhotoPicked(itemId, bytes))
        }
    }

    LaunchedEffect(showSheet, pendingSource) {
        if (!showSheet && pendingSource != null) {
            when (pendingSource) {
                PhotoSource.Camera -> cameraLauncher.launch()
                PhotoSource.Gallery -> galleryPicker.launch()
                null -> Unit
            }
            pendingSource = null
        }
    }

    OutlinedButton(
        onClick = {
            focusManager.clearFocus()
            showSheet = true
        },
        modifier = modifier,
    ) {
        Text(label ?: stringResource(Res.string.order_form_style_upload_new))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_snap_fabric)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Camera
                        showSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_pick_from_gallery)) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Gallery
                        showSheet = false
                    },
                )
            }
        }
    }
}
```

This mirrors the existing `FabricPhotoPickerButton` shape almost exactly — only the action it dispatches changes (`OnItemStylePhotoPicked` instead of `OnItemFabricPhotoPicked`). Pattern-match the fabric button if any of the imports (`PhotoCamera`, `PhotoLibrary`, `ListItem`, `rememberImagePickerLauncher`, etc.) are named differently in this file — the fabric button is the source of truth.

**Note on the "Snap photo" / "Pick from gallery" string reuse:** the existing fabric button uses `order_form_snap_fabric` and `order_form_pick_from_gallery` — reuse those for the style upload sheet (no new strings for the camera/gallery chooser labels since the action is the same).

- [ ] **Step 6: Render the `StylePickerSheet` from the screen-level Composable**

Inside `OrderFormScreen` (the screen-level composable, not the item card), find a spot where other top-level overlays would render — likely near the bottom of the Scaffold's content lambda, or as a sibling after the main Column. Add:

```kotlin
    state.stylePickerSheetForItemId?.let { itemId ->
        StylePickerSheet(
            styles = state.availableStyles,
            onSelect = { style ->
                onAction(OrderFormAction.OnItemStyleChange(itemId, style.id))
                onAction(OrderFormAction.OnDismissStylePickerSheet)
            },
            onDismiss = { onAction(OrderFormAction.OnDismissStylePickerSheet) },
        )
    }
```

If `OrderFormScreen` uses a `Scaffold` wrapping a content `Column`, place this `let` block AFTER the Column closes, inside the Scaffold's content lambda. (The exact location depends on the file's current structure — find where the `Scaffold` content body ends and place it as a sibling there.)

- [ ] **Step 7: Don't compile yet** — strings still missing.

---

### Task 7: Create `FullScreenImageViewer`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FullScreenImageViewer.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage

@Composable
fun FullScreenImageViewer(
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    if (model == null) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LoadingDots()
                    }
                },
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Should succeed (no external string dependencies in this file). If it fails, the most likely cause is an icon import — every other path is self-contained.

- [ ] **Step 3: Do not commit yet**

---

### Task 8: Wire viewer on form thumbnails (fabric + style)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`

- [ ] **Step 1: Add the import**

```kotlin
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
```

- [ ] **Step 2: Hoist viewer state into the per-item card composable**

Find the per-item card composable in `OrderFormScreen.kt` (the one that renders garment type + price + style section + fabric section per item). At the top of its body, add:

```kotlin
    var fullScreenImage: Any? by remember { mutableStateOf<Any?>(null) }
```

(Use `remember { mutableStateOf<Any?>(null) }` to allow both `ByteArray` and `String` URL.)

- [ ] **Step 3: Wrap the fabric thumbnail in clickable**

Find the existing fabric-photo preview block (around lines 806–855 — the `Box` with the fabric thumbnail). Add `.clickable { ... }` to the Box modifier:

OLD (the Box wrapping the fabric thumbnail):
```kotlin
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (item.fabricPhotoBytes != null) {
                        AsyncImage(/* ... */)
                    } else {
                        SubcomposeAsyncImage(/* ... */)
                    }
                    // ... remove IconButton
                }
```

NEW (add a `.clickable` between background and contents):
```kotlin
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            fullScreenImage = item.fabricPhotoBytes ?: item.fabricPhotoUrl
                        }
                ) {
                    // body unchanged
                }
```

- [ ] **Step 4: Wrap the style thumbnail in clickable**

In `StyleSectionUploaded` (added in Task 6), find the image Box. Add the same `.clickable`:

```kotlin
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        fullScreenImage = item.stylePhotoBytes ?: item.stylePhotoUrl
                    },
            ) {
```

In `StyleSectionExisting`, the avatar-sized 64dp box for the gallery thumbnail. Add `.clickable { fullScreenImage = style.photoUrl }`:

```kotlin
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .clickable { fullScreenImage = style.photoUrl },
                ) {
```

**Note:** `fullScreenImage` is hoisted to the parent (per-item card). `StyleSection`, `StyleSectionUploaded`, `StyleSectionExisting` are children. Either pass `fullScreenImage` setter through (cleaner) or inline the viewer state into each section (more duplication). Decision: **pass the setter through**. Update the section composables to accept `onPreview: (Any?) -> Unit`:

```kotlin
@Composable
private fun StyleSection(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (Any?) -> Unit,
) { /* ... */ }
```

And so on for `StyleSectionExisting(..., onPreview)` and `StyleSectionUploaded(..., onPreview)`. The call from the per-item card becomes:

```kotlin
            StyleSection(
                item = item,
                availableStyles = availableStyles,
                onAction = onAction,
                onPreview = { fullScreenImage = it },
            )
```

- [ ] **Step 5: Render the viewer in the per-item card**

After the per-item card content (but inside the same composable scope), add:

```kotlin
        FullScreenImageViewer(
            model = fullScreenImage,
            contentDescription = null,
            onDismiss = { fullScreenImage = null },
        )
```

The viewer is a `Dialog` so it overlays the entire screen; placement inside the per-item card is fine.

- [ ] **Step 6: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Will FAIL on unresolved string refs from Task 6. That's expected — strings come in Task 11. Don't fix; continue.

---

### Task 9: Wire viewer on order-detail thumbnails

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`

- [ ] **Step 1: Add the import + hoist viewer state in `OrderGarmentDetailsCard`**

Add the import:

```kotlin
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
```

In the `OrderGarmentDetailsCard` composable, near the top of its body, add:

```kotlin
    var fullScreenImage: String? by remember { mutableStateOf<String?>(null) }
```

(Detail screen images are always URLs, never bytes — so `String?` is enough.)

- [ ] **Step 2: Make `FabricThumbnail` accept a click**

In the same file, find `FabricThumbnail` (around line 212). Add `onClick: () -> Unit` parameter:

```kotlin
@Composable
private fun FabricThumbnail(
    photoUrl: String,
    onClick: () -> Unit,
) {
```

Add `.clickable(onClick = onClick)` to the outer Box modifier (the one with `.size(FABRIC_THUMBNAIL_SIZE)`).

Update the call site (line 204):

OLD:
```kotlin
            FabricThumbnail(photoUrl = item.fabricPhotoUrl)
```

NEW:
```kotlin
            FabricThumbnail(
                photoUrl = item.fabricPhotoUrl,
                onClick = { fullScreenImage = item.fabricPhotoUrl },
            )
```

- [ ] **Step 3: Render the viewer in `OrderGarmentDetailsCard`**

At the bottom of the card's content (still inside the composable), add:

```kotlin
    FullScreenImageViewer(
        model = fullScreenImage,
        contentDescription = null,
        onDismiss = { fullScreenImage = null },
    )
```

- [ ] **Step 4: Wire `OrderHeroCard` the same way**

Add the import:

```kotlin
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
```

In `OrderHeroCard` body, hoist state:

```kotlin
    var fullScreenImage: String? by remember { mutableStateOf<String?>(null) }
```

In `HeroImage` (private composable, around line 169), wrap the `SubcomposeAsyncImage` in a Box with `.clickable`:

OLD (inside `HeroImage`, the `if (photoUrl != null) { SubcomposeAsyncImage(...) }` block):
```kotlin
        if (photoUrl != null) {
            SubcomposeAsyncImage(
                model = photoUrl,
                /* ... */
            )
```

NEW: lift the click-to-preview behavior to `HeroImage` via a callback. Update `HeroImage` signature:

```kotlin
@Composable
private fun HeroImage(
    stylePhotoUrl: String?,
    garmentTypeIcon: ImageVector,
    garmentName: String,
    onAddStyleClick: () -> Unit,
    onPhotoClick: (String) -> Unit,
) {
```

And the call site (around line 126):

```kotlin
            HeroImage(
                stylePhotoUrl = stylePhotoUrl,
                garmentTypeIcon = garmentTypeIcon,
                garmentName = garmentName,
                onAddStyleClick = onAddStyleClick,
                onPhotoClick = { fullScreenImage = it },
            )
```

Inside `HeroImage`, modify the `SubcomposeAsyncImage` modifier to add `.clickable { onPhotoClick(photoUrl) }`:

```kotlin
        if (photoUrl != null) {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = garmentName,
                contentScale = ContentScale.Crop,
                loading = { /* existing loading content */ },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick(photoUrl) },
            )
```

(If the existing SubcomposeAsyncImage doesn't have a `modifier =` parameter, find what wraps it and add `.clickable` there.)

- [ ] **Step 5: Render the viewer in `OrderHeroCard`**

At the bottom of the card body (after the outer `Surface(...) { Column { ... } }`), add:

```kotlin
    FullScreenImageViewer(
        model = fullScreenImage,
        contentDescription = null,
        onDismiss = { fullScreenImage = null },
    )
```

- [ ] **Step 6: Do not compile yet** — Task 10 finishes the detail screen wiring.

---

### Task 10: Fall back to `item.stylePhotoUrl` in `OrderHeroCard` wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`

- [ ] **Step 1: Update the `OrderHeroCard` call site**

Find the call to `OrderHeroCard` (around line 739 where `stylePhotoUrl = state.style?.photoUrl`):

OLD:
```kotlin
                stylePhotoUrl = state.style?.photoUrl,
```

NEW:
```kotlin
                stylePhotoUrl = state.style?.photoUrl ?: state.order?.items?.firstOrNull()?.stylePhotoUrl,
```

Read: if there's a gallery Style (via `styleId`), prefer its photo URL; otherwise, fall back to the first item's one-off `stylePhotoUrl`. This is spec §4.3.

If `state.order` isn't directly accessible at the call site (because it lives nested in state), the appropriate adjustment is to read whatever the existing item-list state is. Search the surrounding code for how items are referenced; mirror that pattern.

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Will still FAIL on missing strings (Task 11). Continue.

---

### Task 11: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add 11 new keys**

Find a sensible spot in `strings.xml` — near other `order_form_*` strings. Add:

```xml
    <!-- Order form style section (PTSP-9) -->
    <string name="order_form_style_section_title">STYLE</string>
    <string name="order_form_style_pick_from_gallery">Pick from gallery</string>
    <string name="order_form_style_upload_new">Upload new</string>
    <string name="order_form_style_description_label">Style description</string>
    <string name="order_form_style_description_placeholder">e.g. Ankara wedding suit</string>
    <string name="order_form_style_save_to_gallery">Save to style gallery</string>
    <string name="order_form_style_from_gallery_caption">From your style gallery</string>
    <string name="order_form_style_change">Change</string>
    <string name="order_form_style_remove">Remove</string>
    <string name="order_form_style_picker_title">Pick a style</string>
    <string name="order_form_style_picker_empty">No saved styles yet</string>
```

**Apostrophe check:** none of these strings need apostrophes; verify by re-reading. If you add or modify any to include `'`, use the typographic `’` (Unicode U+2019), NEVER `\'` (per `feedback_strings_no_backslash_escape`).

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Must be BUILD SUCCESSFUL.

- [ ] **Step 3: Do not commit yet** — full verification first.

---

### Task 12: Full verification

- [ ] **Step 1: Android compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Must be BUILD SUCCESSFUL.

- [ ] **Step 2: iOS compile** (per `feedback_kmp_jvm_only_apis`)

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Must be BUILD SUCCESSFUL.

- [ ] **Step 3: detekt**

```bash
./gradlew detekt
```

Must be BUILD SUCCESSFUL. If `LongMethod` fires on `OrderFormScreen` or `OrderFormViewModel` body, the existing `@Suppress` annotations should cover it — but inspect any new finding.

- [ ] **Step 4: Tests**

```bash
./gradlew :composeApp:allTests
```

The new mapper round-trip test from Task 1 should pass. Android tests overall must pass. iOS `linkDebugTestIosSimulatorArm64` may fail on pre-existing `FirebaseCore` framework — not introduced by this PR.

---

### Task 13: Manual smoke test (Daniel)

**Pre-req:** Android emulator or device running the debug build.

- [ ] **Step 1: Install**

```bash
./gradlew :composeApp:installDebug
```

- [ ] **Step 2: Open New Order form, customer with no gallery styles**

Sign in with Fola (per `reference_test_environment`). Start New Order for a customer with no saved styles. Reach the per-item step:
- Style section visible
- "Pick from gallery" button **disabled**
- "Upload new" button **enabled**

- [ ] **Step 3: Open New Order form, customer with gallery styles**

For a customer that has gallery styles:
- "Pick from gallery" button enabled
- Tap → ModalBottomSheet with the customer's gallery styles (each row = thumbnail + description)
- Tap a row → sheet dismisses, section flips to State B with the picked style's preview + description + "From your style gallery" caption + Change/Remove

- [ ] **Step 4: Upload new image, toggle ON, with description**

- Tap "Upload new" → camera/gallery chooser sheet → pick an image
- Section flips to State C: 120dp image preview + description text field + "Save to style gallery" toggle (default ON) + Change/Remove
- Type a description ("Wedding suit test 1")
- Complete the order form and tap Save
- After save, open the customer's Style Gallery → the new style appears with the description and image

- [ ] **Step 5: Upload new image, toggle OFF**

- Same flow as Step 4, but toggle OFF
- Save the order
- Open the customer's Style Gallery → the new image does **NOT** appear (it lives on the OrderItem only)
- Open the saved order's detail → the hero image shows the uploaded style image (via the `item.stylePhotoUrl` fallback from Task 10)

- [ ] **Step 6: Upload new image, toggle ON, blank description**

- Same as Step 4 but leave the description empty
- Save → order saves successfully
- Open Style Gallery → the new style appears (description is empty space; this is the accepted behavior per spec §3.3)

- [ ] **Step 7: Tap fabric thumbnail on the form → fullscreen viewer**

- After uploading a fabric image, tap the thumbnail
- FullScreenImageViewer opens with the image filling the screen
- Close via X (top-right) → viewer dismisses
- Reopen → close via scrim tap → dismisses
- Reopen → close via Android system back → dismisses

- [ ] **Step 8: Tap style thumbnail on the form → fullscreen viewer**

Repeat Step 7 for the style image (test in all three states: State B existing-gallery, State C with bytes, State C with URL).

- [ ] **Step 9: Tap fabric thumbnail on order detail → viewer**

Open a saved order's detail screen. Tap the fabric thumbnail in `OrderGarmentDetailsCard`. Viewer opens. Close works.

- [ ] **Step 10: Tap style thumbnail on order detail → viewer**

Same as Step 9 for the style image on `OrderHeroCard`.

- [ ] **Step 11: Edit a pre-PTSP-9 order**

Open an order created before this PR shipped. Edit it:
- Style section starts as State A (empty) since old orders have null style fields
- Adding a style works (any state)
- Save updates the order

- [ ] **Step 12: iOS hardware smoke**

On a real iPhone (per `feedback_gitlive_ios_nonnull_tokens` — sims sometimes mask Firebase upload behavior):
- Upload a style image with toggle ON → confirm Style Gallery updates
- Upload a style image with toggle OFF → confirm order detail renders the one-off image

---

### Task 14: Commit + push + open PR

- [ ] **Step 1: Commit the plan doc separately**

```bash
git add docs/superpowers/plans/2026-05-26-ptsp-9-ptsp-10-style-image-and-viewer.md
git commit -m "$(cat <<'EOF'
docs(plans): PTSP-9 + PTSP-10 implementation plan

Task-by-task plan for the bundled order-form work: inline style image
upload during order creation (PTSP-9) and tap-to-fullscreen on fabric
and style thumbnails (PTSP-10).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 2: Stage the data-layer commit (Tasks 1 + 2)**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt
```

```bash
git commit -m "$(cat <<'EOF'
feat(order): add stylePhotoUrl/StoragePath fields + uploadStylePhoto repository method (PTSP-9)

OrderItem (domain + DTO + mapper) gains two new optional fields for
one-off style images attached directly to an order item, parallel to
the existing fabric photo fields. OrderRepository.uploadStylePhoto
implements the Firebase Storage upload mirroring uploadFabricPhoto.

Backward-compatible: pre-PTSP-9 orders read with both new fields null.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Stage the form commit (Tasks 3 + 4 + 5 + 6 + 8)**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml
```

```bash
git commit -m "$(cat <<'EOF'
feat(order-form): inline style image upload with save-to-gallery toggle (PTSP-9)

Replaces the existing style dropdown with a unified Style section that
has three states: empty (Pick-from-gallery + Upload-new buttons), picked
(gallery style preview + Change/Remove), or uploaded (image + optional
description + Save-to-gallery toggle defaulting to ON).

When the toggle is ON at save time, the form creates a new Style entity
via StyleRepository.createStyle and points the OrderItem.styleId at it.
When OFF, the image uploads to Firebase Storage and lives on
OrderItem.stylePhotoUrl/StoragePath as a one-off.

Per feedback_ios_modal_bottom_sheet_timing none of these flows trigger
UIKit nav, so no sheet-dismiss delays are needed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Stage the viewer commit (Tasks 7 + 8 + 9 + 10)**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FullScreenImageViewer.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
```

Note: the form's tap-to-viewer wiring (Task 8) lands in the form commit above; the viewer itself + detail wiring lands here.

```bash
git commit -m "$(cat <<'EOF'
feat(order): tap-to-fullscreen on fabric and style thumbnails (PTSP-10)

Adds a reusable FullScreenImageViewer Dialog (Coil-backed, accepts
URL or ByteArray) and wires it into every fabric/style thumbnail in
the order form, the OrderGarmentDetailsCard fabric thumbnail, and the
OrderHeroCard style image.

Also adds the item.stylePhotoUrl fallback in OrderHeroCard's wiring so
one-off uploaded styles (toggle OFF at save) render correctly on the
detail screen.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: Verify branch state and push**

```bash
git log --oneline main..HEAD
```

Expected 4 commits: docs(plans) + 3 feat commits.

```bash
git push -u origin feature/ptsp-9-style-image-on-new-order
```

Pre-push `codex review` will run automatically.

- [ ] **Step 6: Open the PR — pause for Daniel's approval first**

When approved, run:

```bash
gh pr create --title "feat(order): style image upload + fullscreen viewer (PTSP-9 + PTSP-10)" --body "$(cat <<'EOF'
## Summary

- Adds inline style image upload during order creation. Tailors can either pick from the customer's Style Gallery OR upload a new image. A "Save to style gallery" toggle (default ON) decides whether the uploaded image becomes a reusable Style entity (creating a new gallery item) or lives one-off on the OrderItem (\`stylePhotoUrl\` / \`stylePhotoStoragePath\`).
- Makes fabric and style thumbnails tap-to-fullscreen everywhere they appear: order form item card, OrderGarmentDetailsCard fabric thumbnail, OrderHeroCard style image. Uses a new reusable \`FullScreenImageViewer\` Dialog.
- \`OrderFormRoute\` not touched. \`OrderItem\` gains two optional fields with null defaults — fully backward-compatible.

Spec: \`docs/superpowers/specs/2026-05-26-ptsp-9-ptsp-10-style-image-and-viewer-design.md\`.

## Test plan

- [x] \`./gradlew :composeApp:compileDebugKotlinAndroid\` ✅
- [x] \`./gradlew :composeApp:compileKotlinIosSimulatorArm64\` ✅
- [x] \`./gradlew detekt\` ✅
- [x] \`./gradlew :composeApp:allTests\` — Android passes (new \`OrderMapperTest\` cases for the new fields included); iOS link fails on pre-existing FirebaseCore framework (not a regression).
- [x] Manual smoke (Android, Fola, customer with gallery styles): picker sheet, gallery selection, upload with both toggle states, save creates Style or stores one-off as expected.
- [x] Manual smoke (Android, fresh customer with no gallery): empty Style section, picker disabled, upload still works.
- [x] Manual smoke: tap fabric + style thumbnails on form and detail → viewer opens; close via X / scrim / back all work.
- [x] iPhone hardware smoke: both upload paths complete successfully via Firebase Storage.
- [ ] Pre-push \`codex review\` (automatic on push)
- [ ] Cursor review

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of scope (reaffirming spec §7)

- Pinch-to-zoom / pan / swipe-between / save-to-device / share in the viewer (V1 = view + close).
- Firebase Storage orphan cleanup on partial save failure.
- Adding a `description` field directly to `OrderItem` (description is a Style attribute).
- Multiple style images per item.
- Promoting a previously-one-off-uploaded image to the gallery from edit mode (the moment for that decision has passed — user can re-upload via the standalone Style Gallery form).
- Tappable thumbnails on Style Gallery, Customer Detail, or other surfaces.
- Adding a first `OrderFormViewModelTest` — the new save-paths are covered by manual smoke.
