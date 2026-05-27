# Multi-image Style & Fabric Implementation Plan (PTSP-11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tailors can attach up to 3 style images + up to 3 fabric images per order item, with a redesigned Style/Fabric section (Variant B+) and a carousel on the order detail screen.

**Architecture:** Replace the single-image fields on `OrderItem` (`stylePhotoUrl`/`styleId`/`fabricPhotoUrl`) with list-based fields (`styleImages: List<StyleImageRef>`, `fabricImages: List<FabricImageRef>`). Legacy single fields stay on the DTO for backward read + forward double-write (12-month window). Add batch repository methods that loop the existing singular methods. Form UI replaces the PTSP-9 Style section composables with a flat inline section (no nested cards) + image strip + chip-style action row. Order detail's hero becomes a `HorizontalPager` carousel when 2+ images; `FullScreenImageViewer` extended to swipe through a list.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 (HorizontalPager from `androidx.compose.foundation.pager`), Coil 3, MVI, GitLive Firebase SDK, Firebase Storage.

**Spec:** `docs/superpowers/specs/2026-05-26-ptsp-11-multi-image-style-fabric-design.md`.

**Branch:** `feature/ptsp-11-multi-image-style-fabric` (already checked out off latest `main`).

---

## File Map

| File | Change |
|---|---|
| `core/domain/model/Order.kt` | Add `styleImages`, `fabricImages` to `OrderItem`; add `StyleImageRef`, `StyleImageSource`, `FabricImageRef`. Keep legacy single fields for write-path double-write. |
| `core/data/dto/OrderDto.kt` | Add `StyleImageRefDto`, `FabricImageRefDto`; extend `OrderItemDto` with the new lists. |
| `core/data/mapper/OrderMapper.kt` | Round-trip + legacy migration + double-write. |
| `core/data/mapper/OrderMapperTest.kt` | Round-trip tests; legacy migration tests. |
| `core/domain/repository/OrderRepository.kt` | Add `uploadFabricPhotos`, `uploadStylePhotos` batch methods. |
| `core/domain/repository/StyleRepository.kt` | Add `createStyles` batch method. |
| `feature/order/data/FirebaseOrderRepository.kt` | Implement batch methods (loop singular). Update `deleteOrder` cleanup for both lists. Storage path includes `-{index}`. |
| `feature/style/data/FirebaseStyleRepository.kt` | Implement `createStyles` (loop). |
| `core/data/repository/FakeOrderRepository.kt` (test) | Implement batch methods. |
| `feature/style/data/FakeStyleRepository.kt` (if exists) | Implement `createStyles`. |
| `feature/order/presentation/form/OrderFormState.kt` | `OrderItemFormState` swaps single fields for list-based fields + bytes lists. |
| `feature/order/presentation/form/OrderFormAction.kt` | Replace single-image actions with list-based: `OnItemAddStylePhoto`, `OnItemRemoveStyleImage(index)`, `OnItemAddFabricPhoto`, `OnItemRemoveFabricImage(index)`. Keep description + toggle + picker sheet actions. |
| `feature/order/presentation/form/OrderFormViewModel.kt` | New handlers; `save()` uses batch resolution; `toOrderItemFormState` loads list fields; `loadOrderForSeed` strips uploaded refs. |
| `feature/order/presentation/form/OrderFormScreen.kt` | Replace existing `StyleSection*` composables with Variant B+ section; replace fabric block with similar pattern. |
| `feature/order/presentation/form/components/StylePickerSheet.kt` | Mark already-selected styles; stay open on pick until capacity. |
| `ui/components/FullScreenImageViewer.kt` | Extend to `images: List<Any>, startIndex: Int = 0` with internal `HorizontalPager`. Update all 3 call sites. |
| `feature/order/presentation/detail/OrderDetailViewModel.kt` | `style: Style?` → `styles: Map<String, Style>`. Observe library styleIds in the first item. Update link-style flow to APPEND. |
| `feature/order/presentation/detail/OrderDetailScreen.kt` | Pass resolved `List<StyleImageDisplay>` into hero card. |
| `feature/order/presentation/detail/components/OrderHeroCard.kt` | `HeroImage` becomes count-aware (0 / 1 / 2+); 2+ uses `HorizontalPager`. |
| `feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` | `FabricThumbnail` → `FabricStrip`. |
| `feature/style/presentation/form/StyleFormViewModel.kt` | `linkToOrderId` path appends `StyleImageRef(LIBRARY, newStyleId)` to `styleImages` instead of replacing `styleId`. |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | ~13 new keys (see Task 14). |

**Verification:** Each task ends in a compile/detekt/tests command. Manual smoke at the end. No new `OrderFormViewModelTest` introduced (test file doesn't exist; adding the first is scope creep per the spec).

---

### Task 1: Add domain model types

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt`

- [ ] **Step 1: Add the new types + extend `OrderItem`**

Replace the existing `OrderItem` data class and add the new types below it. Open `Order.kt` and replace lines 27–39 (the current `OrderItem`) with:

```kotlin
data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11 multi-image
    val styleImages: List<StyleImageRef> = emptyList(),
    val fabricImages: List<FabricImageRef> = emptyList(),
    // Legacy single fields — kept on domain for the double-write path in OrderMapper.
    // Read-time: ignored if `styleImages`/`fabricImages` are non-empty; otherwise
    // the mapper synthesizes a 1-element list from these. Write-time: derived
    // from the lists (first element of each) so older app versions can still
    // render something. Removable in mid-2027.
    val styleId: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
)

enum class StyleImageSource { LIBRARY, UPLOADED }

data class StyleImageRef(
    val source: StyleImageSource,
    val styleId: String? = null,           // set when source == LIBRARY
    val photoUrl: String? = null,          // set when source == UPLOADED
    val photoStoragePath: String? = null,  // set when source == UPLOADED
)

data class FabricImageRef(
    val photoUrl: String,
    val photoStoragePath: String,
)
```

The existing `data class StatusChange(...)` and `data class Order(...)` below stay unchanged.

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD FAILED — many call sites still reference the removed `OrderItem` fields. That's OK; we fix them in subsequent tasks. **Don't try to fix them yet** — the order of tasks below threads the changes through cleanly.

Actually we can compile cleanly since we KEPT the legacy fields (they're still there on `OrderItem` as `styleId`, `stylePhotoUrl`, etc.). The only new requirements are positional — verify with the build.

Run the compile command. Expected: **BUILD SUCCESSFUL** (all old call sites still resolve; the new fields default to empty list).

- [ ] **Step 3: Do not commit yet** — Tasks 1–3 form one logical "data model + mapper" change. Commit at the end of Task 3.

---

### Task 2: Add DTO types

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt`

- [ ] **Step 1: Add new DTO types + extend `OrderItemDto`**

Replace the existing `OrderItemDto` (lines 38–51) with:

```kotlin
@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11 — source of truth going forward
    val styleImages: List<StyleImageRefDto> = emptyList(),
    val fabricImages: List<FabricImageRefDto> = emptyList(),
    // Legacy single fields — kept for backward read (pre-PTSP-11 docs) AND
    // for forward double-write so older app versions can render the first image.
    // Mapper prefers `styleImages` / `fabricImages` if non-empty; falls back to
    // these otherwise. Removable in mid-2027.
    val styleId: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
)

@Serializable
data class StyleImageRefDto(
    val source: String = "UPLOADED",
    val styleId: String? = null,
    val photoUrl: String? = null,
    val photoStoragePath: String? = null,
)

@Serializable
data class FabricImageRefDto(
    val photoUrl: String = "",
    val photoStoragePath: String = "",
)
```

`source` is serialized as a String (e.g. `"LIBRARY"` or `"UPLOADED"`) for resilience against future enum changes.

- [ ] **Step 2: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The DTO file is self-contained.

- [ ] **Step 3: Do not commit yet** — continues into Task 3.

---

### Task 3: Update OrderMapper with legacy migration + double-write + tests

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt`

- [ ] **Step 1: Update `OrderItemDto.toOrderItem()`**

Find `OrderItemDto.toOrderItem` (around line 117). Replace its body with:

```kotlin
fun OrderItemDto.toOrderItem(): OrderItem {
    // Source of truth: the new lists. If empty (pre-PTSP-11 docs), synthesize
    // a 1-element list from the legacy single fields so the rest of the app
    // sees a uniform shape.
    val resolvedStyleImages: List<StyleImageRef> = when {
        styleImages.isNotEmpty() -> styleImages.map { it.toStyleImageRef() }
        !styleId.isNullOrBlank() -> listOf(
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = styleId),
        )
        !stylePhotoUrl.isNullOrBlank() -> listOf(
            StyleImageRef(
                source = StyleImageSource.UPLOADED,
                photoUrl = stylePhotoUrl,
                photoStoragePath = stylePhotoStoragePath,
            ),
        )
        else -> emptyList()
    }
    val resolvedFabricImages: List<FabricImageRef> = when {
        fabricImages.isNotEmpty() -> fabricImages.map { it.toFabricImageRef() }
        !fabricPhotoUrl.isNullOrBlank() -> listOf(
            FabricImageRef(
                photoUrl = fabricPhotoUrl,
                photoStoragePath = fabricPhotoStoragePath.orEmpty(),
            ),
        )
        else -> emptyList()
    }
    return OrderItem(
        id = id,
        garmentType = parseGarmentType(garmentType),
        description = description,
        price = price,
        measurementId = measurementId,
        fabricName = fabricName,
        styleImages = resolvedStyleImages,
        fabricImages = resolvedFabricImages,
        // Carry legacy fields forward verbatim so the domain object can be
        // re-written without losing any data — useful in case the document
        // is round-tripped without modification.
        styleId = styleId,
        stylePhotoUrl = stylePhotoUrl,
        stylePhotoStoragePath = stylePhotoStoragePath,
        fabricPhotoUrl = fabricPhotoUrl,
        fabricPhotoStoragePath = fabricPhotoStoragePath,
    )
}

private fun StyleImageRefDto.toStyleImageRef(): StyleImageRef = StyleImageRef(
    source = runCatching { StyleImageSource.valueOf(source) }
        .getOrDefault(StyleImageSource.UPLOADED),
    styleId = styleId,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
)

private fun FabricImageRefDto.toFabricImageRef(): FabricImageRef = FabricImageRef(
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
)
```

- [ ] **Step 2: Update `OrderItem.toOrderItemDto()`**

Replace its body with:

```kotlin
fun OrderItem.toOrderItemDto(): OrderItemDto {
    // Double-write: write the new lists AND derive the legacy fields from the
    // first element of each (so pre-PTSP-11 app versions still see one image).
    val firstLibraryStyle = styleImages.firstOrNull { it.source == StyleImageSource.LIBRARY }
    val firstUploadedStyle = styleImages.firstOrNull { it.source == StyleImageSource.UPLOADED }
    val firstFabric = fabricImages.firstOrNull()
    return OrderItemDto(
        id = id,
        garmentType = garmentType.name,
        description = description,
        price = price,
        measurementId = measurementId,
        fabricName = fabricName,
        styleImages = styleImages.map { it.toStyleImageRefDto() },
        fabricImages = fabricImages.map { it.toFabricImageRefDto() },
        // Legacy double-write
        styleId = firstLibraryStyle?.styleId,
        stylePhotoUrl = firstUploadedStyle?.photoUrl,
        stylePhotoStoragePath = firstUploadedStyle?.photoStoragePath,
        fabricPhotoUrl = firstFabric?.photoUrl,
        fabricPhotoStoragePath = firstFabric?.photoStoragePath,
    )
}

private fun StyleImageRef.toStyleImageRefDto(): StyleImageRefDto = StyleImageRefDto(
    source = source.name,
    styleId = styleId,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
)

private fun FabricImageRef.toFabricImageRefDto(): FabricImageRefDto = FabricImageRefDto(
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
)
```

- [ ] **Step 3: Add missing imports to `OrderMapper.kt`**

```kotlin
import com.danzucker.stitchpad.core.data.dto.FabricImageRefDto
import com.danzucker.stitchpad.core.data.dto.StyleImageRefDto
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
```

- [ ] **Step 4: Add round-trip tests + legacy migration tests**

Open `OrderMapperTest.kt`. Find the existing test for `OrderItem.toOrderItemDto`. Add these test cases near the other `OrderItem` round-trip tests (use `import com.danzucker.stitchpad.core.domain.model.*` if not already imported):

```kotlin
@Test
fun `OrderItem round-trips empty styleImages and fabricImages through DTO`() {
    val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
    )

    val roundTripped = item.toOrderItemDto().toOrderItem()

    assertThat(roundTripped.styleImages).isEmpty()
    assertThat(roundTripped.fabricImages).isEmpty()
}

@Test
fun `OrderItem round-trips multi-image styleImages through DTO`() {
    val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
        styleImages = listOf(
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = "s1"),
            StyleImageRef(
                source = StyleImageSource.UPLOADED,
                photoUrl = "https://example.com/u.jpg",
                photoStoragePath = "users/u1/orders/o1/styles/i1-1.jpg",
            ),
        ),
    )

    val roundTripped = item.toOrderItemDto().toOrderItem()

    assertThat(roundTripped.styleImages).hasSize(2)
    assertThat(roundTripped.styleImages[0].source).isEqualTo(StyleImageSource.LIBRARY)
    assertThat(roundTripped.styleImages[0].styleId).isEqualTo("s1")
    assertThat(roundTripped.styleImages[1].source).isEqualTo(StyleImageSource.UPLOADED)
    assertThat(roundTripped.styleImages[1].photoUrl).isEqualTo("https://example.com/u.jpg")
}

@Test
fun `OrderItem round-trips multi-image fabricImages through DTO`() {
    val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
        fabricImages = listOf(
            FabricImageRef("https://example.com/f1.jpg", "users/u1/orders/o1/fabrics/i1-0.jpg"),
            FabricImageRef("https://example.com/f2.jpg", "users/u1/orders/o1/fabrics/i1-1.jpg"),
        ),
    )

    val roundTripped = item.toOrderItemDto().toOrderItem()

    assertThat(roundTripped.fabricImages).hasSize(2)
    assertThat(roundTripped.fabricImages[0].photoUrl).isEqualTo("https://example.com/f1.jpg")
}

@Test
fun `legacy styleId in DTO migrates to a single LIBRARY StyleImageRef`() {
    // Simulate a pre-PTSP-11 Firestore doc: only legacy single fields populated.
    val dto = OrderItemDto(
        id = "i1",
        garmentType = "SHIRT",
        description = "Test",
        price = 100.0,
        styleId = "legacy-style-1",
    )

    val item = dto.toOrderItem()

    assertThat(item.styleImages).hasSize(1)
    assertThat(item.styleImages[0].source).isEqualTo(StyleImageSource.LIBRARY)
    assertThat(item.styleImages[0].styleId).isEqualTo("legacy-style-1")
}

@Test
fun `legacy stylePhotoUrl in DTO migrates to a single UPLOADED StyleImageRef`() {
    val dto = OrderItemDto(
        id = "i1",
        garmentType = "SHIRT",
        description = "Test",
        price = 100.0,
        stylePhotoUrl = "https://example.com/legacy.jpg",
        stylePhotoStoragePath = "users/u1/orders/o1/styles/i1.jpg",
    )

    val item = dto.toOrderItem()

    assertThat(item.styleImages).hasSize(1)
    assertThat(item.styleImages[0].source).isEqualTo(StyleImageSource.UPLOADED)
    assertThat(item.styleImages[0].photoUrl).isEqualTo("https://example.com/legacy.jpg")
}

@Test
fun `legacy fabricPhotoUrl in DTO migrates to a single FabricImageRef`() {
    val dto = OrderItemDto(
        id = "i1",
        garmentType = "SHIRT",
        description = "Test",
        price = 100.0,
        fabricPhotoUrl = "https://example.com/fabric.jpg",
        fabricPhotoStoragePath = "users/u1/orders/o1/fabrics/i1.jpg",
    )

    val item = dto.toOrderItem()

    assertThat(item.fabricImages).hasSize(1)
    assertThat(item.fabricImages[0].photoUrl).isEqualTo("https://example.com/fabric.jpg")
}

@Test
fun `OrderItem double-writes legacy fields from multi-image lists`() {
    val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "Test",
        price = 100.0,
        styleImages = listOf(
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = "s1"),
            StyleImageRef(
                source = StyleImageSource.UPLOADED,
                photoUrl = "https://example.com/u.jpg",
                photoStoragePath = "p1",
            ),
        ),
        fabricImages = listOf(
            FabricImageRef("https://example.com/f.jpg", "fp1"),
        ),
    )

    val dto = item.toOrderItemDto()

    // Legacy single fields should be derived from the lists for backward read
    assertThat(dto.styleId).isEqualTo("s1")
    assertThat(dto.stylePhotoUrl).isEqualTo("https://example.com/u.jpg")
    assertThat(dto.fabricPhotoUrl).isEqualTo("https://example.com/f.jpg")
}
```

- [ ] **Step 5: Compile + run mapper tests**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:allTests --tests "*OrderMapperTest*"
```

Both must be BUILD SUCCESSFUL. The 7 new test cases above should pass.

- [ ] **Step 6: Commit data layer**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/OrderDto.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapper.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/OrderMapperTest.kt
git commit -m "$(cat <<'EOF'
feat(order): multi-image style/fabric data model + legacy migration (PTSP-11)

OrderItem gains styleImages: List<StyleImageRef> and fabricImages:
List<FabricImageRef>. StyleImageRef carries either a styleId (LIBRARY
source) or a photoUrl+path (UPLOADED source). FabricImageRef is always
uploaded.

Mapper round-trips the new lists AND migrates pre-PTSP-11 Firestore
docs by synthesizing a 1-element list from the legacy single fields.
On write, the legacy fields are double-populated from the first list
entry so older app versions still see one image. Legacy double-write
removable in mid-2027.

7 new round-trip + migration tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Batch upload methods on `OrderRepository`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt`

- [ ] **Step 1: Extend the interface**

Open `OrderRepository.kt`. Add these two methods right after the existing `uploadStylePhoto` declaration:

```kotlin
suspend fun uploadFabricPhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network>

suspend fun uploadStylePhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network>
```

Returns one `Pair<url, storagePath>` per upload, in input order. Empty input → `Result.Success(emptyList())`.

- [ ] **Step 2: Implement on `FirebaseOrderRepository`**

Open `FirebaseOrderRepository.kt`. First, update the storage-path helpers to accept an index. Find `fabricStoragePath` (around line 37):

OLD:
```kotlin
private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/styles/$itemId.jpg"
```

NEW (keep the no-index versions for backward compat with the singular methods, AND add indexed variants):

```kotlin
private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

private fun fabricStoragePath(userId: String, orderId: String, itemId: String, index: Int): String =
    "users/$userId/orders/$orderId/fabrics/$itemId-$index.jpg"

private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
    "users/$userId/orders/$orderId/styles/$itemId.jpg"

private fun styleStoragePath(userId: String, orderId: String, itemId: String, index: Int): String =
    "users/$userId/orders/$orderId/styles/$itemId-$index.jpg"
```

Then implement the two batch methods. Add them right after the existing `uploadStylePhoto` implementation:

```kotlin
override suspend fun uploadFabricPhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val results = mutableListOf<Pair<String, String>>()
    photoBytesList.forEachIndexed { index, bytes ->
        val path = fabricStoragePath(userId, orderId, itemId, index)
        try {
            storage.reference.child(path).putData(bytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            results += downloadUrl to path
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "uploadFabricPhotos failed itemId=$itemId index=$index"
            }
            return Result.Error(DataError.Network.UNKNOWN)
        }
    }
    return Result.Success(results)
}

override suspend fun uploadStylePhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val results = mutableListOf<Pair<String, String>>()
    photoBytesList.forEachIndexed { index, bytes ->
        val path = styleStoragePath(userId, orderId, itemId, index)
        try {
            storage.reference.child(path).putData(bytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            results += downloadUrl to path
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "uploadStylePhotos failed itemId=$itemId index=$index"
            }
            return Result.Error(DataError.Network.UNKNOWN)
        }
    }
    return Result.Success(results)
}
```

- [ ] **Step 3: Update `deleteOrder` to clean up the new list-shape storage paths**

Find the per-item cleanup helper inside `FirebaseOrderRepository` (search for the function that iterates `item.fabricPhotoStoragePath`, likely named `deleteFabricPhotosFor` or inline in `deleteOrder`). The cleanup needs to also iterate the NEW lists. Add inside the existing per-item loop, alongside the existing fabric+style path cleanup:

```kotlin
// PTSP-11 multi-image cleanup
item.fabricImages.forEach { ref ->
    val p = ref.photoStoragePath
    if (p.isNotBlank()) {
        runCatching { storage.reference.child(p).delete() }
    }
}
item.styleImages
    .filter { it.source == StyleImageSource.UPLOADED }
    .forEach { ref ->
        val p = ref.photoStoragePath
        if (!p.isNullOrBlank()) {
            runCatching { storage.reference.child(p).delete() }
        }
    }
```

The existing legacy single-field cleanup (`item.fabricPhotoStoragePath`, `item.stylePhotoStoragePath`) STAYS to handle orders saved during the legacy-only window. Both blocks run; harmless if the path is already deleted.

Add the `StyleImageSource` import at the top of the file:

```kotlin
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
```

- [ ] **Step 4: Implement on `FakeOrderRepository`**

Open `FakeOrderRepository.kt`. Add the two batch methods after the existing `uploadStylePhoto`:

```kotlin
override suspend fun uploadFabricPhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val results = photoBytesList.mapIndexed { index, _ ->
        "https://fake.example/fabrics/$orderId/$itemId-$index.jpg" to
            "users/$userId/orders/$orderId/fabrics/$itemId-$index.jpg"
    }
    return Result.Success(results)
}

override suspend fun uploadStylePhotos(
    userId: String,
    orderId: String,
    itemId: String,
    photoBytesList: List<ByteArray>,
): Result<List<Pair<String, String>>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val results = photoBytesList.mapIndexed { index, _ ->
        "https://fake.example/styles/$orderId/$itemId-$index.jpg" to
            "users/$userId/orders/$orderId/styles/$itemId-$index.jpg"
    }
    return Result.Success(results)
}
```

If the existing fake has a `shouldReturnError` mechanism, mirror it here for failure-injection tests.

- [ ] **Step 5: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

Both must be BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/OrderRepository.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeOrderRepository.kt
git commit -m "$(cat <<'EOF'
feat(order): batch upload methods + storage cleanup for multi-image (PTSP-11)

Adds uploadFabricPhotos and uploadStylePhotos to OrderRepository. Each
loops the corresponding singular method with early-error-return — small
implementation surface, easy to test. Storage paths use itemId-{index}
suffix for the batch variants; legacy itemId.jpg path stays for the
singular methods.

FirebaseOrderRepository.deleteOrder cleanup now iterates the new
styleImages/fabricImages lists in addition to the legacy single-path
fields, so orders saved through either codepath get fully cleaned up.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Batch `createStyles` on `StyleRepository`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/StyleRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/data/FirebaseStyleRepository.kt`
- Modify (if exists): `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/data/FakeStyleRepository.kt`

- [ ] **Step 1: Check whether `FakeStyleRepository` exists**

```bash
find /Users/danzucker/Desktop/Project/StitchPad/composeApp/src/commonTest -name "FakeStyleRepository*" 2>/dev/null
```

If empty output: no fake exists; skip the fake update steps below.

- [ ] **Step 2: Extend the interface**

Open `StyleRepository.kt`. Add this method after the existing `createStyle`:

```kotlin
suspend fun createStyles(
    userId: String,
    customerId: String,
    description: String,
    photoBytesList: List<ByteArray>,
): Result<List<String>, DataError.Network>
```

Returns a `List<String>` of newly-created style IDs in input order. Empty input → `Result.Success(emptyList())`.

- [ ] **Step 3: Implement on `FirebaseStyleRepository`**

Open `FirebaseStyleRepository.kt`. Add after the existing `createStyle`:

```kotlin
override suspend fun createStyles(
    userId: String,
    customerId: String,
    description: String,
    photoBytesList: List<ByteArray>,
): Result<List<String>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val createdIds = mutableListOf<String>()
    photoBytesList.forEach { bytes ->
        when (val result = createStyle(userId, customerId, description, bytes)) {
            is Result.Success -> createdIds += result.data
            is Result.Error -> return Result.Error(result.error)
        }
    }
    return Result.Success(createdIds)
}
```

Each loop iteration delegates to `createStyle`, which is responsible for the photo upload + Firestore doc write atomically. On the first failure, returns the error (preceding successful creations stay in the gallery — accepted per spec §9 failure modes).

- [ ] **Step 4: Implement on `FakeStyleRepository`** *(skip if no fake exists)*

If the fake file exists from Step 1, add:

```kotlin
override suspend fun createStyles(
    userId: String,
    customerId: String,
    description: String,
    photoBytesList: List<ByteArray>,
): Result<List<String>, DataError.Network> {
    if (photoBytesList.isEmpty()) return Result.Success(emptyList())
    val ids = photoBytesList.indices.map { "fake-style-$it-${System.currentTimeMillis()}" }
    return Result.Success(ids)
}
```

- [ ] **Step 5: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

Both must be BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/StyleRepository.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/data/FirebaseStyleRepository.kt
# Plus FakeStyleRepository if it exists
git commit -m "$(cat <<'EOF'
feat(style): batch createStyles method (PTSP-11)

Adds StyleRepository.createStyles which loops the existing createStyle
with early-error-return. Each entry creates a new Style in the
customer's gallery sharing the provided description. Returns the list
of new style IDs in input order.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Update `OrderItemFormState` shape

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt`

- [ ] **Step 1: Replace `OrderItemFormState`**

Replace the existing `OrderItemFormState` (lines 36–56) with:

```kotlin
data class OrderItemFormState
@OptIn(ExperimentalUuidApi::class)
constructor(
    val id: String = Uuid.random().toString(),
    val garmentType: GarmentType? = null,
    val description: String = "",
    val price: String = "",
    val measurementId: String? = null,
    val fabricName: String = "",
    // PTSP-11 — multi-image lists
    /** Already-saved style refs loaded from an edit. New picks/uploads append to this. */
    val styleImageRefs: List<StyleImageRef> = emptyList(),
    /** Newly-uploaded style bytes this session, not yet committed. */
    val uploadedStyleBytesList: List<ByteArray> = emptyList(),
    /** Storage paths queued for deletion on next successful save (uploaded refs the user removed). */
    val pendingStyleStorageDeletions: List<String> = emptyList(),
    /** Already-saved fabric refs loaded from an edit. New uploads append to this. */
    val fabricImageRefs: List<FabricImageRef> = emptyList(),
    /** Newly-uploaded fabric bytes this session, not yet committed. */
    val uploadedFabricBytesList: List<ByteArray> = emptyList(),
    /** Storage paths queued for deletion on next successful save. */
    val pendingFabricStorageDeletions: List<String> = emptyList(),
    /** Shared description for ALL newly-uploaded styles this session. Optional. */
    val styleDescription: String = "",
    /** When true, uploaded styles become Style entities in the customer's gallery. */
    val saveStyleToGallery: Boolean = true,
)
```

Add the imports at the top of the file:

```kotlin
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
```

Remove the now-unused `Style` import:

```kotlin
// Remove this line:
// import com.danzucker.stitchpad.core.domain.model.Style
```

Wait — `Style` is still imported by `OrderFormState` (the outer state has `availableStyles: List<Style>`). Keep it. Just verify after the edit.

The total image count for a section in UI = `styleImageRefs.size + uploadedStyleBytesList.size` (and similarly for fabric). The 3-image cap applies to that total.

- [ ] **Step 2: Do not compile yet**

Many VM and screen call sites still reference the OLD shape (`stylePhotoBytes`, `stylePhotoUrl`, `styleId` on the form state, etc.). They get fixed in Tasks 7–10. Build will fail; that's expected.

---

### Task 7: Update `OrderFormAction`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt`

- [ ] **Step 1: Replace the action set**

Replace the entire `OrderFormAction.kt` contents with:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.form

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderPriority

sealed interface OrderFormAction {
    // Navigation
    data object OnNextStep : OrderFormAction
    data object OnPreviousStep : OrderFormAction
    data object OnNavigateBack : OrderFormAction

    // Step 1 - Customer
    data class OnSelectCustomer(val customer: Customer) : OrderFormAction
    data class OnCustomerSearchChange(val query: String) : OrderFormAction

    // Step 2 - Items
    data object OnAddItem : OrderFormAction
    data class OnRemoveItem(val itemId: String) : OrderFormAction
    data class OnItemGarmentTypeChange(val itemId: String, val type: GarmentType?) : OrderFormAction
    data class OnItemDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemPriceChange(val itemId: String, val price: String) : OrderFormAction
    data class OnItemMeasurementChange(val itemId: String, val measurementId: String?) : OrderFormAction
    data class OnItemFabricNameChange(val itemId: String, val fabricName: String) : OrderFormAction

    // PTSP-11 multi-image actions — STYLE
    /** User picked a style from the saved-styles picker sheet. Appends a LIBRARY ref. */
    data class OnItemPickSavedStyle(val itemId: String, val styleId: String) : OrderFormAction
    /** User uploaded a new style photo (camera or gallery). Appends to uploadedStyleBytesList. */
    data class OnItemAddStylePhoto(val itemId: String, val photoBytes: ByteArray) : OrderFormAction
    /** Remove a saved-ref or a session-uploaded style at the combined-list index. */
    data class OnItemRemoveStyleImage(val itemId: String, val index: Int) : OrderFormAction
    data class OnItemStyleDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemSaveStyleToGalleryToggle(val itemId: String, val value: Boolean) : OrderFormAction
    data class OnOpenStylePickerSheet(val itemId: String) : OrderFormAction
    data object OnDismissStylePickerSheet : OrderFormAction

    // PTSP-11 multi-image actions — FABRIC
    /** User uploaded a new fabric photo (camera or gallery). Appends to uploadedFabricBytesList. */
    data class OnItemAddFabricPhoto(val itemId: String, val photoBytes: ByteArray) : OrderFormAction
    /** Remove a saved-ref or a session-uploaded fabric at the combined-list index. */
    data class OnItemRemoveFabricImage(val itemId: String, val index: Int) : OrderFormAction

    // Step 3 - Details
    data class OnDeadlineChange(val deadline: Long?) : OrderFormAction
    data class OnPriorityChange(val priority: OrderPriority) : OrderFormAction
    data class OnDepositChange(val deposit: String) : OrderFormAction
    data class OnNotesChange(val notes: String) : OrderFormAction

    // Save
    data object OnSave : OrderFormAction
    data object OnErrorDismiss : OrderFormAction
}
```

Removed PTSP-9 actions: `OnItemStyleChange`, `OnItemFabricPhotoPicked`, `OnItemFabricPhotoRemoved`, `OnItemStylePhotoPicked`, `OnItemStylePhotoRemoved`. They're replaced by the multi-image equivalents above.

- [ ] **Step 2: Do not compile yet**

VM still references the old actions. Fixed in Task 8.

---

### Task 8: VM handlers for the new actions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

- [ ] **Step 1: Remove the PTSP-9 single-image handlers**

In the `onAction` `when` block, find and DELETE these branches:
- `is OrderFormAction.OnItemStyleChange -> ...`
- `is OrderFormAction.OnItemFabricPhotoPicked -> ...`
- `is OrderFormAction.OnItemFabricPhotoRemoved -> ...`
- `is OrderFormAction.OnItemStylePhotoPicked -> ...`
- `is OrderFormAction.OnItemStylePhotoRemoved -> ...`

- [ ] **Step 2: Add the new PTSP-11 multi-image handlers**

Add these branches in their place (group with the other Step 2 - Items handlers):

```kotlin
            is OrderFormAction.OnItemPickSavedStyle -> updateItem(action.itemId) {
                // Append a LIBRARY ref. Capacity check: stylePickerSheet already
                // marks already-picked as disabled, so the user shouldn't be able
                // to over-pick — but defend with a guard anyway.
                val total = it.styleImageRefs.size + it.uploadedStyleBytesList.size
                if (total >= 3) return@updateItem it
                if (it.styleImageRefs.any { ref -> ref.source == StyleImageSource.LIBRARY && ref.styleId == action.styleId }) {
                    return@updateItem it  // already picked
                }
                it.copy(
                    styleImageRefs = it.styleImageRefs + StyleImageRef(
                        source = StyleImageSource.LIBRARY,
                        styleId = action.styleId,
                    ),
                )
            }
            is OrderFormAction.OnItemAddStylePhoto -> updateItem(action.itemId) {
                val total = it.styleImageRefs.size + it.uploadedStyleBytesList.size
                if (total >= 3) return@updateItem it
                it.copy(uploadedStyleBytesList = it.uploadedStyleBytesList + action.photoBytes)
            }
            is OrderFormAction.OnItemRemoveStyleImage -> updateItem(action.itemId) {
                // The combined list is: styleImageRefs FIRST, then uploadedStyleBytesList.
                // index addresses that combined position.
                val savedCount = it.styleImageRefs.size
                when {
                    action.index < savedCount -> {
                        val removed = it.styleImageRefs[action.index]
                        val deletionsAdd = if (removed.source == StyleImageSource.UPLOADED &&
                            !removed.photoStoragePath.isNullOrBlank()
                        ) {
                            it.pendingStyleStorageDeletions + removed.photoStoragePath
                        } else {
                            it.pendingStyleStorageDeletions
                        }
                        it.copy(
                            styleImageRefs = it.styleImageRefs.toMutableList()
                                .also { list -> list.removeAt(action.index) },
                            pendingStyleStorageDeletions = deletionsAdd,
                        )
                    }
                    else -> {
                        val byteIndex = action.index - savedCount
                        if (byteIndex !in it.uploadedStyleBytesList.indices) return@updateItem it
                        it.copy(
                            uploadedStyleBytesList = it.uploadedStyleBytesList.toMutableList()
                                .also { list -> list.removeAt(byteIndex) },
                        )
                    }
                }
            }
            is OrderFormAction.OnItemStyleDescriptionChange -> updateItem(action.itemId) {
                it.copy(styleDescription = action.description)
            }
            is OrderFormAction.OnItemSaveStyleToGalleryToggle -> updateItem(action.itemId) {
                it.copy(saveStyleToGallery = action.value)
            }
            is OrderFormAction.OnOpenStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = action.itemId) }
            }
            OrderFormAction.OnDismissStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = null) }
            }
            is OrderFormAction.OnItemAddFabricPhoto -> updateItem(action.itemId) {
                val total = it.fabricImageRefs.size + it.uploadedFabricBytesList.size
                if (total >= 3) return@updateItem it
                it.copy(uploadedFabricBytesList = it.uploadedFabricBytesList + action.photoBytes)
            }
            is OrderFormAction.OnItemRemoveFabricImage -> updateItem(action.itemId) {
                val savedCount = it.fabricImageRefs.size
                when {
                    action.index < savedCount -> {
                        val removed = it.fabricImageRefs[action.index]
                        it.copy(
                            fabricImageRefs = it.fabricImageRefs.toMutableList()
                                .also { list -> list.removeAt(action.index) },
                            pendingFabricStorageDeletions =
                                it.pendingFabricStorageDeletions + removed.photoStoragePath,
                        )
                    }
                    else -> {
                        val byteIndex = action.index - savedCount
                        if (byteIndex !in it.uploadedFabricBytesList.indices) return@updateItem it
                        it.copy(
                            uploadedFabricBytesList = it.uploadedFabricBytesList.toMutableList()
                                .also { list -> list.removeAt(byteIndex) },
                        )
                    }
                }
            }
```

Add imports at the top of the file:
```kotlin
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
```

- [ ] **Step 3: Do not compile yet**

`save()` and `toOrderItemFormState` still reference the old shape. Fixed in Tasks 9 + 10.

---

### Task 9: Replace `save()` body with batch resolution

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

- [ ] **Step 1: Delete the old `resolveStylePhoto` + `StyleResolution`**

Find the private `data class StyleResolution(...)` and `resolveStylePhoto(...)` block at the bottom of the file. Delete the whole block — replaced by a new helper below.

Also find the existing `uploadFabricPhotoIfNeeded(...)` private function. Delete it — same reason.

- [ ] **Step 2: Add the new batch resolvers**

Add these at the bottom of the class (before the closing brace), replacing the deleted helpers:

```kotlin
    /**
     * Result holder for per-item multi-image resolution at save time.
     */
    private data class ItemImageResolution(
        val styleImages: List<StyleImageRef>,
        val fabricImages: List<FabricImageRef>,
    )

    /**
     * Per-item batch resolution at save time:
     *  - Existing saved refs (styleImageRefs / fabricImageRefs) pass through unchanged
     *  - New uploaded bytes:
     *      • Fabric → upload to Firebase Storage as FabricImageRef
     *      • Style + toggle ON  → batch-create Style entities, refs become LIBRARY
     *      • Style + toggle OFF → upload to Firebase Storage, refs become UPLOADED
     *  - On ANY failure → Result.Error so save() aborts (no silent data loss)
     */
    @Suppress("ReturnCount")
    private suspend fun resolveItemImages(
        userId: String,
        customerId: String,
        orderId: String,
        item: OrderItemFormState,
    ): Result<ItemImageResolution, DataError.Network> {
        // 1) Fabric: existing refs + uploaded bytes -> Firebase Storage
        val uploadedFabric = when (val r = orderRepository.uploadFabricPhotos(
            userId = userId,
            orderId = orderId,
            itemId = item.id,
            photoBytesList = item.uploadedFabricBytesList,
        )) {
            is Result.Success -> r.data.map { (url, path) -> FabricImageRef(url, path) }
            is Result.Error -> return Result.Error(r.error)
        }
        val finalFabricImages = item.fabricImageRefs + uploadedFabric

        // 2) Style: existing refs pass through; uploaded bytes branch on toggle
        val uploadedStyleRefs: List<StyleImageRef> = if (item.uploadedStyleBytesList.isEmpty()) {
            emptyList()
        } else if (item.saveStyleToGallery) {
            when (val r = styleRepository.createStyles(
                userId = userId,
                customerId = customerId,
                description = item.styleDescription.trim(),
                photoBytesList = item.uploadedStyleBytesList,
            )) {
                is Result.Success -> r.data.map { styleId ->
                    StyleImageRef(source = StyleImageSource.LIBRARY, styleId = styleId)
                }
                is Result.Error -> return Result.Error(r.error)
            }
        } else {
            when (val r = orderRepository.uploadStylePhotos(
                userId = userId,
                orderId = orderId,
                itemId = item.id,
                photoBytesList = item.uploadedStyleBytesList,
            )) {
                is Result.Success -> r.data.map { (url, path) ->
                    StyleImageRef(
                        source = StyleImageSource.UPLOADED,
                        photoUrl = url,
                        photoStoragePath = path,
                    )
                }
                is Result.Error -> return Result.Error(r.error)
            }
        }
        val finalStyleImages = item.styleImageRefs + uploadedStyleRefs

        return Result.Success(
            ItemImageResolution(
                styleImages = finalStyleImages,
                fabricImages = finalFabricImages,
            ),
        )
    }

    /**
     * Best-effort cleanup of orphaned storage objects the user removed during
     * this edit session. Runs after a successful save. Failures are silent —
     * the order saved fine; the orphan is a Storage-side concern.
     */
    private suspend fun cleanUpPendingStorageDeletions(items: List<OrderItemFormState>) {
        items.forEach { item ->
            (item.pendingStyleStorageDeletions + item.pendingFabricStorageDeletions)
                .filter { it.isNotBlank() }
                .forEach { path ->
                    // FirebaseOrderRepository exposes a public-style `deleteFabricPhoto` we
                    // could call, but to keep this generic we use the storage reference
                    // directly via the existing repository. Since the repository surface
                    // doesn't expose raw storage delete, log this as a no-op for V1 —
                    // the orphan exists for the lifetime of the order. Acceptable per
                    // spec §9 "accepted limitation".
                    // (Implementation note: if we add OrderRepository.deletePhoto(path)
                    // in a follow-up, call it here.)
                }
        }
    }
```

The `cleanUpPendingStorageDeletions` body is intentionally a no-op for V1 — the explicit comment documents why. Removing the in-flight upload from the user's view is what matters; the storage object becomes an orphan that gets cleaned up only when the whole order is deleted.

- [ ] **Step 3: Rewrite the per-item loop inside `save()`**

Find the `viewModelScope.launch { ... }` block in `save()`. Find the `for (item in formItems) { ... }` loop body. Replace it (the per-item resolution + OrderItem construction) with:

```kotlin
            for (item in formItems) {
                val garmentType = item.garmentType!!
                val price = item.price.toDoubleOrNull() ?: 0.0

                val resolution = when (
                    val r = resolveItemImages(uid, customer.id, actualOrderId, item)
                ) {
                    is Result.Success -> r.data
                    is Result.Error -> {
                        _state.update {
                            it.copy(isSaving = false, errorMessage = r.error.toOrderUiText())
                        }
                        return@launch
                    }
                }

                orderItems.add(
                    OrderItem(
                        id = item.id,
                        garmentType = garmentType,
                        description = item.description.trim(),
                        price = price,
                        measurementId = item.measurementId,
                        fabricName = item.fabricName.trim().ifBlank { null },
                        styleImages = resolution.styleImages,
                        fabricImages = resolution.fabricImages,
                        // Legacy single fields — populated by the mapper from the lists on write.
                        // Leave null on the domain object; mapper handles double-write.
                    ),
                )
            }
```

After the loop, call the cleanup helper (placed after the existing `createOrder`/`updateOrder` success branch):

```kotlin
            when (result) {
                is Result.Success -> {
                    cleanUpPendingStorageDeletions(formItems)
                    _events.send(OrderFormEvent.OrderSaved)
                }
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
```

- [ ] **Step 4: Do not compile yet**

`toOrderItemFormState` and `loadOrderForSeed` still need updating. Task 10.

---

### Task 10: Update `toOrderItemFormState` + `loadOrderForSeed`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt`

- [ ] **Step 1: Update `OrderItem.toOrderItemFormState()`**

Find the private extension `OrderItem.toOrderItemFormState()`. Replace its body with:

```kotlin
    private fun OrderItem.toOrderItemFormState() = OrderItemFormState(
        id = id,
        garmentType = garmentType,
        description = description,
        price = if (price > 0) price.toLong().toString() else "",
        measurementId = measurementId,
        fabricName = fabricName.orEmpty(),
        // PTSP-11 — load the lists; uploadedBytesList stays empty until the user
        // uploads new this session. Description + toggle reset to defaults.
        styleImageRefs = styleImages,
        fabricImageRefs = fabricImages,
        uploadedStyleBytesList = emptyList(),
        uploadedFabricBytesList = emptyList(),
        pendingStyleStorageDeletions = emptyList(),
        pendingFabricStorageDeletions = emptyList(),
        styleDescription = "",
        saveStyleToGallery = true,
    )
```

- [ ] **Step 2: Update `loadOrderForSeed`**

Find the `loadOrderForSeed` function. Find the `items = source.items.map { item -> item.copy(...).toFormState() }` block. Replace with:

```kotlin
                    _state.update {
                        it.copy(
                            // Seeded order is brand new: each item gets a fresh id, AND we
                            // strip the one-off uploaded storage paths (both style + fabric)
                            // so the new order doesn't point at the source order's Storage
                            // objects. Without this, deleting either order would break the
                            // other's image via FirebaseOrderRepository.deleteOrder's
                            // cleanup. Library-source style refs are preserved
                            // (customer-gallery Styles are shared across orders by design).
                            items = source.items.map { item ->
                                item.copy(
                                    id = Uuid.random().toString(),
                                    // Drop ALL fabric refs (always uploaded → all point at source order)
                                    fabricImages = emptyList(),
                                    fabricPhotoUrl = null,
                                    fabricPhotoStoragePath = null,
                                    // Keep LIBRARY style refs; drop UPLOADED style refs
                                    styleImages = item.styleImages.filter {
                                        it.source == StyleImageSource.LIBRARY
                                    },
                                    stylePhotoUrl = null,
                                    stylePhotoStoragePath = null,
                                ).toOrderItemFormState()
                            },
                            deadline = source.deadline,
                            priority = source.priority,
                            depositPaid = "",
                            notes = source.notes ?: "",
                            isLoading = false,
                        )
                    }
```

- [ ] **Step 3: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD FAILED — the screen-side composables (`OrderFormScreen.kt`) still reference the old state shape (`item.stylePhotoBytes`, `item.fabricPhotoUrl`, etc.). That's fixed in Tasks 11–12. Don't try to fix anything else yet.

If the compile fails on OTHER files (not `OrderFormScreen.kt`), check whether the call site is using legacy `OrderItem` single fields and update it to the lists, OR confirm the legacy fields are still accessible on the domain object (they are — we kept them).

- [ ] **Step 4: Do not commit yet**

---

### Task 11: Rewrite Style + Fabric sections in `OrderFormScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt`

This is the big UI change. The existing `StyleSection`, `StyleSectionEmpty`, `StyleSectionExisting`, `StyleSectionUploaded`, and `StyleUploadButton` composables (PTSP-9) all get deleted. They're replaced with ONE new composable per category (`StyleImageSection`, `FabricImageSection`) plus a shared `ImageThumbnailStrip` helper.

The total redesign is ~250 lines of new composables in this file. Replacing piecemeal is risky; this task replaces the whole block.

- [ ] **Step 1: Locate and DELETE the existing Style section composables**

In `OrderFormScreen.kt`, find and remove:
- `private fun StyleSection(...)` (currently around line 1221)
- `private fun StyleSectionEmpty(...)`
- `private fun StyleSectionExisting(...)`
- `private fun StyleSectionUploaded(...)`
- `private fun StyleUploadButton(...)`

Also find the call site in the per-item card composable (search for `StyleSection(` — should be one call site around line 712). Note its location; we'll replace it in Step 4.

- [ ] **Step 2: Locate and prepare to update the FABRIC block in the per-item card**

Find the fabric-photo block in the per-item card composable (around lines 783–862 — search for `fabricPhotoBytes`). This block uses the OLD single-image state (`item.fabricPhotoBytes`, `item.fabricPhotoUrl`, `OrderFormAction.OnItemFabricPhotoPicked`). We'll delete this block and replace it with `FabricImageSection(item, onAction, onPreview)` call in Step 4.

Also find the existing fabric "name" `OutlinedTextField` (uses `Res.string.order_form_fabric_name_label`). It stays — moves into `FabricImageSection` below the strip per the spec layout.

- [ ] **Step 3: Add the new composables**

At the bottom of `OrderFormScreen.kt` (before the previews), add:

```kotlin
// ────────────────────────────────────────────────────────────────────────
// PTSP-11 — Style section (Variant B+ inline)
// ────────────────────────────────────────────────────────────────────────

private const val MAX_IMAGES_PER_CATEGORY = 3

@Composable
private fun StyleImageSection(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (Any) -> Unit,
) {
    val savedCount = item.styleImageRefs.size
    val newCount = item.uploadedStyleBytesList.size
    val total = savedCount + newCount
    val capacityRemaining = MAX_IMAGES_PER_CATEGORY - total
    val hasUploaded = newCount > 0
    val hasGalleryStyles = availableStyles.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_form_style_section_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.12.em,
            )
            Text(
                text = stringResource(
                    Res.string.order_form_image_count_fmt,
                    total,
                    MAX_IMAGES_PER_CATEGORY,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))

        // Image strip — saved refs first, then session uploads, then +tile if room
        StyleImageStrip(
            item = item,
            availableStyles = availableStyles,
            onRemove = { index -> onAction(OrderFormAction.OnItemRemoveStyleImage(item.id, index)) },
            onPreview = onPreview,
            onAddClick = if (capacityRemaining > 0) {
                { onAction(OrderFormAction.OnOpenStylePickerSheet(item.id)) }
            } else null,
        )

        // Action chips — hidden when at max capacity
        if (capacityRemaining > 0) {
            Spacer(Modifier.height(DesignTokens.space3))
            StyleActionChips(
                itemId = item.id,
                showSavedChip = hasGalleryStyles,
                onAction = onAction,
            )
        }

        // Description + Save-to-gallery toggle — only shown when there's at least
        // one session-uploaded image still pending save.
        if (hasUploaded) {
            Spacer(Modifier.height(DesignTokens.space3))
            OutlinedTextField(
                value = item.styleDescription,
                onValueChange = { onAction(OrderFormAction.OnItemStyleDescriptionChange(item.id, it)) },
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
    }
}

@Composable
private fun StyleImageStrip(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onRemove: (Int) -> Unit,
    onPreview: (Any) -> Unit,
    onAddClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        // Saved refs first
        item.styleImageRefs.forEachIndexed { index, ref ->
            val (imageModel, badge) = when (ref.source) {
                StyleImageSource.LIBRARY -> {
                    val style = availableStyles.find { it.id == ref.styleId }
                    (style?.photoUrl as Any? to stringResource(Res.string.order_form_image_badge_library))
                }
                StyleImageSource.UPLOADED -> (ref.photoUrl as Any? to null)
            }
            if (imageModel != null) {
                ImageThumbnail(
                    model = imageModel,
                    badge = badge,
                    onRemove = { onRemove(index) },
                    onTap = { onPreview(imageModel) },
                )
            }
        }
        // Session uploads next
        item.uploadedStyleBytesList.forEachIndexed { byteIndex, bytes ->
            val combinedIndex = item.styleImageRefs.size + byteIndex
            ImageThumbnail(
                model = bytes,
                badge = stringResource(Res.string.order_form_image_badge_new),
                onRemove = { onRemove(combinedIndex) },
                onTap = { onPreview(bytes) },
            )
        }
        // Add tile if capacity
        if (onAddClick != null) {
            AddImageTile(onClick = onAddClick)
        }
    }
}

@Composable
private fun StyleActionChips(
    itemId: String,
    showSavedChip: Boolean,
    onAction: (OrderFormAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        if (showSavedChip) {
            OutlinedButton(
                onClick = { onAction(OrderFormAction.OnOpenStylePickerSheet(itemId)) },
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.CollectionsBookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(DesignTokens.space1))
                Text(stringResource(Res.string.order_form_style_pick_from_saved))
            }
        }
        StyleUploadChip(
            itemId = itemId,
            onAction = onAction,
            modifier = if (showSavedChip) Modifier.weight(1f) else Modifier.fillMaxWidth(),
        )
    }
}

// ────────────────────────────────────────────────────────────────────────
// PTSP-11 — Fabric section (Variant B+ inline, mirrors style minus library + toggle)
// ────────────────────────────────────────────────────────────────────────

@Composable
private fun FabricImageSection(
    item: OrderItemFormState,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (Any) -> Unit,
) {
    val total = item.fabricImageRefs.size + item.uploadedFabricBytesList.size
    val capacityRemaining = MAX_IMAGES_PER_CATEGORY - total

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_form_fabric_section_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.12.em,
            )
            Text(
                text = stringResource(
                    Res.string.order_form_image_count_fmt,
                    total,
                    MAX_IMAGES_PER_CATEGORY,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))

        // Fabric image strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            item.fabricImageRefs.forEachIndexed { index, ref ->
                ImageThumbnail(
                    model = ref.photoUrl,
                    badge = null,
                    onRemove = { onAction(OrderFormAction.OnItemRemoveFabricImage(item.id, index)) },
                    onTap = { onPreview(ref.photoUrl) },
                )
            }
            item.uploadedFabricBytesList.forEachIndexed { byteIndex, bytes ->
                val combinedIndex = item.fabricImageRefs.size + byteIndex
                ImageThumbnail(
                    model = bytes,
                    badge = stringResource(Res.string.order_form_image_badge_new),
                    onRemove = { onAction(OrderFormAction.OnItemRemoveFabricImage(item.id, combinedIndex)) },
                    onTap = { onPreview(bytes) },
                )
            }
            if (capacityRemaining > 0) {
                FabricAddTile(itemId = item.id, onAction = onAction)
            }
        }

        // Single upload chip below the strip (no "Choose from saved" — no fabric gallery)
        if (capacityRemaining > 0) {
            Spacer(Modifier.height(DesignTokens.space3))
            FabricUploadChip(
                itemId = item.id,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(DesignTokens.space3))
        OutlinedTextField(
            value = item.fabricName,
            onValueChange = { onAction(OrderFormAction.OnItemFabricNameChange(item.id, it)) },
            label = { Text(stringResource(Res.string.order_form_fabric_name_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_fabric_name_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ────────────────────────────────────────────────────────────────────────
// Shared image thumbnail + tile composables
// ────────────────────────────────────────────────────────────────────────

@Composable
private fun ImageThumbnail(
    model: Any,
    badge: String?,
    onRemove: () -> Unit,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 82.dp, height = 100.dp)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap),
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) { LoadingDots() }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
        // Source badge
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AddImageTile(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 82.dp, height = 100.dp)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(Res.string.order_form_image_add_tile),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
            )
        }
    }
}
```

- [ ] **Step 4: Add the chip + sheet trigger composables**

Append to the bottom of the file (mirrors the existing PTSP-9 `StyleUploadButton` pattern, but adapted for the new actions):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleUploadChip(
    itemId: String,
    onAction: (OrderFormAction) -> Unit,
    modifier: Modifier = Modifier,
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
                onAction(OrderFormAction.OnItemAddStylePhoto(itemId, it))
            }
        },
    )
    val cameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) onAction(OrderFormAction.OnItemAddStylePhoto(itemId, bytes))
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
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(DesignTokens.space1))
        Text(stringResource(Res.string.order_form_style_upload_new))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Camera
                        showSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabricUploadChip(
    itemId: String,
    onAction: (OrderFormAction) -> Unit,
    modifier: Modifier = Modifier,
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
                onAction(OrderFormAction.OnItemAddFabricPhoto(itemId, it))
            }
        },
    )
    val cameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) onAction(OrderFormAction.OnItemAddFabricPhoto(itemId, bytes))
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
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(DesignTokens.space1))
        Text(stringResource(Res.string.order_form_fabric_upload_new))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Camera
                        showSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
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

@Composable
private fun FabricAddTile(itemId: String, onAction: (OrderFormAction) -> Unit) {
    // Reuses the upload chip's logic but renders as the small +tile in the strip.
    // For simplicity, this just opens the same upload chooser as the chip would —
    // implementation can either: (a) hoist the sheet state up, or (b) duplicate
    // the picker setup here. For V1 we duplicate (b) — same pattern as PTSP-9.
    FabricUploadChip(
        itemId = itemId,
        onAction = onAction,
        modifier = Modifier
            .size(width = 82.dp, height = 100.dp),  // The OutlinedButton renders inside this size
    )
    // Note: visually this won't perfectly match the "+ ADD" tile from the mockup.
    // For V1 acceptable; can refine in a follow-up to a true tile-only widget.
}
```

**Implementation note on `FabricAddTile`:** the simplest V1 is to just have the user tap the upload chip below the strip. If you want the literal +tile from the mockup as a separate compact entry point, lift the sheet state into the parent `FabricImageSection` and pass an `onAddTileClick` callback to a real `AddImageTile`. The plan ships the simpler version; the visual polish can land as a follow-up.

- [ ] **Step 5: Wire the new sections into the per-item card**

Find the per-item card composable (the one currently containing garment chips, garment type dropdown, description, price, the OLD StyleSection call, Measurement, fabric block, etc.).

Find the `StyleSection(...)` call from PTSP-9 (around line 712). Replace with:

```kotlin
            Spacer(Modifier.height(DesignTokens.space3))
            StyleImageSection(
                item = item,
                availableStyles = availableStyles,
                onAction = onAction,
                onPreview = { fullScreenImage = it },
            )
```

Find the OLD fabric block (the `fabricPhotoLabel` text, the `if (hasFabricPhoto) ... else FabricPhotoPickerButton(...)` block, AND the fabric name field — they all need to come out as one). Replace with:

```kotlin
            Spacer(Modifier.height(DesignTokens.space4))
            FabricImageSection(
                item = item,
                onAction = onAction,
                onPreview = { fullScreenImage = it },
            )
```

The fabric name field is now INSIDE `FabricImageSection`, so any standalone fabric name field outside that block should be removed.

- [ ] **Step 6: Clean up unused imports + the old `FabricPhotoPickerButton`**

The PTSP-9 `FabricPhotoPickerButton` composable is unused now (replaced by `FabricUploadChip`). Find and delete it.

Now compile and let the IDE / detekt tell you which imports are unused. Likely cleanup candidates in `OrderFormScreen.kt`:
- `androidx.compose.material.icons.filled.AddAPhoto` (if only `FabricPhotoPickerButton` used it)
- Other unused PTSP-9 imports

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD FAILED due to missing string resources (added in Task 14). Continue.

- [ ] **Step 7: Do not commit yet** — Tasks 11–14 form one logical "UI redesign" change.

---

### Task 12: Wire the `StylePickerSheet` to append-with-marking

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt`

- [ ] **Step 1: Update the sheet to accept already-selected ids + capacity**

Open `StylePickerSheet.kt`. Replace its `StylePickerSheet` composable signature with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    styles: List<Style>,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    onSelect: (Style) -> Unit,
    onDismiss: () -> Unit,
)
```

Inside, gray out / disable already-selected styles AND disable all when `remainingCapacity == 0`. Replace the `StylePickerRow` rendering with:

```kotlin
items(items = styles, key = { it.id }) { style ->
    val alreadyPicked = style.id in alreadySelectedStyleIds
    val outOfCapacity = remainingCapacity <= 0
    val disabled = alreadyPicked || outOfCapacity
    StylePickerRow(
        style = style,
        disabled = disabled,
        statusLabel = when {
            alreadyPicked -> stringResource(Res.string.style_picker_already_added)
            else -> null
        },
        onClick = { if (!disabled) onSelect(style) },
    )
}
```

Update `StylePickerRow` to accept the new params:

```kotlin
@Composable
private fun StylePickerRow(
    style: Style,
    disabled: Boolean,
    statusLabel: String?,
    onClick: () -> Unit,
) {
    val alpha = if (disabled) 0.5f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(role = Role.Button, enabled = !disabled, onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusMd)),
        ) {
            SubcomposeAsyncImage(
                model = style.photoUrl,
                contentDescription = null,
                loading = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
            if (statusLabel != null) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

Required imports:
```kotlin
import androidx.compose.ui.draw.alpha
```

- [ ] **Step 2: Update the call site in `OrderFormScreen.kt`**

Find the `StylePickerSheet(...)` call in `OrderFormScreen`. Update it with the new params:

```kotlin
state.stylePickerSheetForItemId?.let { itemId ->
    val targetItem = state.items.find { it.id == itemId } ?: return@let
    val alreadyPickedIds = targetItem.styleImageRefs
        .filter { it.source == StyleImageSource.LIBRARY }
        .mapNotNull { it.styleId }
        .toSet()
    val remaining = MAX_IMAGES_PER_CATEGORY - (
        targetItem.styleImageRefs.size + targetItem.uploadedStyleBytesList.size
    )
    StylePickerSheet(
        styles = state.availableStyles,
        alreadySelectedStyleIds = alreadyPickedIds,
        remainingCapacity = remaining,
        onSelect = { style ->
            onAction(OrderFormAction.OnItemPickSavedStyle(itemId, style.id))
            // Sheet auto-closes only when capacity reaches 0; otherwise stays open
            // so the user can pick another. The action handler in the VM applies
            // the capacity check defensively.
            val nextRemaining = remaining - 1
            if (nextRemaining <= 0) {
                onAction(OrderFormAction.OnDismissStylePickerSheet)
            }
        },
        onDismiss = { onAction(OrderFormAction.OnDismissStylePickerSheet) },
    )
}
```

- [ ] **Step 3: Do not compile yet** — string resources added in Task 14.

---

### Task 13: Extend `FullScreenImageViewer` to a list + startIndex

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FullScreenImageViewer.kt`

- [ ] **Step 1: Rewrite the viewer signature + body**

Replace the entire `FullScreenImageViewer.kt` with:

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_close_image_viewer

@Composable
fun FullScreenImageViewer(
    images: List<Any>,
    startIndex: Int = 0,
    contentDescription: String? = null,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val closeCd = stringResource(Res.string.cd_close_image_viewer)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
                .semantics {
                    role = Role.Button
                    this.contentDescription = closeCd
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) { page ->
                SubcomposeAsyncImage(
                    model = images[page],
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) { LoadingDots() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Counter pill (top-left, doesn't overlap with close button)
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Dots (bottom-center)
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    repeat(images.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (active) 22.dp else 7.dp,
                                    height = 7.dp,
                                )
                                .background(
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeCd,
                    tint = Color.White,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Update all 3 call sites to pass a list**

Find each call site and wrap the old single `model` in `listOf(...)`. From the grep earlier, the sites are:

**`OrderFormScreen.kt`** (around line 854):
```kotlin
// OLD:
FullScreenImageViewer(
    model = fullScreenImage,
    contentDescription = null,
    onDismiss = { fullScreenImage = null },
)
// NEW:
fullScreenImage?.let { img ->
    FullScreenImageViewer(
        images = listOf(img),
        contentDescription = null,
        onDismiss = { fullScreenImage = null },
    )
}
```

For the form, since the user can preview ANY image in a section (style or fabric), V1 ships with single-image preview from the form (tap = open viewer with just that one image). Multi-image-swipe in the viewer applies to the **detail screen** call sites (where the strips are stable). Form-side strips already let the user scroll through them inline; the viewer there just shows the one they tapped.

**`OrderHeroCard.kt`** (around line 173) and **`OrderGarmentDetailsCard.kt`** (around line 107): Tasks 15 + 16 update these with proper lists.

For now, do the minimal wrap (1-element list) on these two as well so the call sites compile:

```kotlin
fullScreenImage?.let { img ->
    FullScreenImageViewer(
        images = listOf(img),
        contentDescription = null,
        onDismiss = { fullScreenImage = null },
    )
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: build still fails on missing string resources from Task 11/12 (`order_form_style_pick_from_saved`, `order_form_image_count_fmt`, etc.). That's expected; fixed in Task 14.

---

### Task 14: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add new keys + rename/retire stale ones**

Open `strings.xml`. Find the existing PTSP-9 style strings (search for `order_form_style_`) and update / extend the block:

```xml
    <!-- Order form image sections (PTSP-11) -->
    <string name="order_form_style_section_title">Style references</string>
    <string name="order_form_fabric_section_title">Fabric references</string>
    <string name="order_form_image_count_fmt">%1$d of %2$d</string>
    <string name="order_form_image_badge_library">LIBRARY</string>
    <string name="order_form_image_badge_new">NEW</string>
    <string name="order_form_image_add_tile">ADD</string>

    <!-- Renamed from "order_form_style_pick_from_gallery" to remove ambiguity with the phone's photo gallery -->
    <string name="order_form_style_pick_from_saved">Choose from saved</string>
    <string name="order_form_style_upload_new">Upload new</string>
    <string name="order_form_fabric_upload_new">Upload fabric photo</string>

    <string name="order_form_style_description_label">Style description (optional)</string>
    <string name="order_form_style_description_placeholder">e.g. Ankara wedding suit</string>
    <string name="order_form_style_save_to_gallery">Save uploaded styles to my library</string>

    <string name="style_picker_already_added">Already added</string>
```

Replace any existing duplicates. Delete the orphan PTSP-9 keys that are no longer used:
- `order_form_style_pick_from_gallery` — replaced by `order_form_style_pick_from_saved`
- `order_form_style_from_gallery_caption` — no longer rendered
- `order_form_style_change` — no longer rendered (replaced by add/remove pattern)
- `order_form_style_remove` — no longer rendered

(Verify each is genuinely unused before deletion with `grep -rn "order_form_style_pick_from_gallery" composeApp/src --include='*.kt'`.)

- [ ] **Step 2: Compile + verify**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew detekt
```

All three must be BUILD SUCCESSFUL. If detekt fires `LongMethod` on any new composable, add `@Suppress("LongMethod")` only if the function is genuinely cohesive (don't fragment to satisfy detekt).

- [ ] **Step 3: Commit UI redesign + strings**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormState.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormAction.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/components/StylePickerSheet.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FullScreenImageViewer.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(order-form): Variant B+ multi-image redesign (PTSP-11)

Replaces PTSP-9 single-image Style/Fabric blocks with the Variant B+
design: flat inline structure (no nested cards), section header with
count, horizontal image strip with source badges (LIBRARY/NEW) and
remove buttons, action chips below (Choose from saved + Upload new
for style; Upload only for fabric), and description + Save-to-library
toggle when any session-uploaded styles are pending save.

Hidden affordances per the spec:
- "Choose from saved" chip hides when the customer has zero saved
  styles (Daniel's explicit ask vs. the disabled state from PTSP-9)
- Action chips hide when the 3-image cap is reached
- Description + toggle only show when session-uploaded styles exist

StylePickerSheet now marks already-added styles and stays open for
multi-pick until capacity is reached.

FullScreenImageViewer extended to a list + startIndex with internal
HorizontalPager; existing single-image call sites wrap in 1-element
lists. Detail-screen call sites update to pass real lists in Tasks
15+16.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: `OrderDetailViewModel` — observe many styles, append on link

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModel.kt`

- [ ] **Step 1: Change state shape from `style: Style?` to `styles: Map<String, Style>`**

Open `OrderDetailState.kt`. Find the existing `style: Style?` field. Replace with:

```kotlin
val styles: Map<String, Style> = emptyMap(),
```

- [ ] **Step 2: Observe all library-source styles in the first item**

Open `OrderDetailViewModel.kt`. Find the existing `observeStyle` (or similar) that observes a single `Style` based on `item.styleId`. Replace with:

```kotlin
private fun observeStyles() {
    viewModelScope.launch {
        // Observe customer's full style list and keep a lookup map. The hero
        // image resolver in OrderDetailScreen picks the relevant styles per
        // styleImages[].styleId at render time. Cheaper than per-style
        // subscriptions; the gallery list is small for any tailor.
        val order = _state.value.order ?: return@launch
        styleRepository.observeStyles(userId = order.userId, customerId = order.customerId)
            .collect { result ->
                if (result is Result.Success) {
                    val map = result.data.associateBy { it.id }
                    _state.update { it.copy(styles = map) }
                }
            }
    }
}
```

The existing call site (probably inside `loadOrder` after order resolves) needs updating to call `observeStyles()` (no args) instead of the old `observeStyle(styleId)`.

- [ ] **Step 3: Update the StyleForm.linkToOrderId path to APPEND a LIBRARY ref**

Open `StyleFormViewModel.kt`. Find the linkToOrderId block (around line 165):

OLD:
```kotlin
if (linkOrderId != null) {
    when (val orderResult = orderRepository.getOrder(userId, linkOrderId)) {
        is Result.Success -> {
            val order = orderResult.data
            val firstItem = order.items.firstOrNull()
            if (firstItem != null) {
                val updatedItems = listOf(firstItem.copy(styleId = newStyleId)) +
                    order.items.drop(1)
                orderRepository.updateOrder(userId, order.copy(items = updatedItems))
            }
        }
        is Result.Error -> Unit
    }
}
```

NEW:
```kotlin
if (linkOrderId != null) {
    when (val orderResult = orderRepository.getOrder(userId, linkOrderId)) {
        is Result.Success -> {
            val order = orderResult.data
            val firstItem = order.items.firstOrNull()
            if (firstItem != null) {
                // PTSP-11 — APPEND a LIBRARY ref to the first item's styleImages
                // list. Guard against duplicates and the 3-cap.
                val alreadyHas = firstItem.styleImages.any {
                    it.source == StyleImageSource.LIBRARY && it.styleId == newStyleId
                }
                val atCap = firstItem.styleImages.size >= 3
                if (!alreadyHas && !atCap) {
                    val newRef = StyleImageRef(
                        source = StyleImageSource.LIBRARY,
                        styleId = newStyleId,
                    )
                    val updatedItem = firstItem.copy(
                        styleImages = firstItem.styleImages + newRef,
                    )
                    val updatedItems = listOf(updatedItem) + order.items.drop(1)
                    orderRepository.updateOrder(userId, order.copy(items = updatedItems))
                }
            }
        }
        is Result.Error -> Unit
    }
}
```

Add imports:
```kotlin
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
```

- [ ] **Step 4: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD FAILED — `OrderDetailScreen.kt` still passes `state.style?.photoUrl` to `OrderHeroCard`. Fixed in Task 16.

---

### Task 16: `OrderHeroCard` — count-aware hero with carousel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`

- [ ] **Step 1: Update `OrderHeroCard` signature**

Replace the existing `stylePhotoUrl: String?` parameter with:

```kotlin
fun OrderHeroCard(
    styleImageUrls: List<String>,
    // ... rest of params unchanged
)
```

Update `HeroImage`'s signature similarly:
```kotlin
@Composable
private fun HeroImage(
    styleImageUrls: List<String>,
    garmentTypeIcon: ImageVector,
    garmentName: String,
    onAddStyleClick: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,  // (urls, startIndex)
)
```

- [ ] **Step 2: Replace `HeroImage` body with count-aware branches**

Find `HeroImage`'s body. Replace with:

```kotlin
@Composable
private fun HeroImage(
    styleImageUrls: List<String>,
    garmentTypeIcon: ImageVector,
    garmentName: String,
    onAddStyleClick: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
        .clip(
            RoundedCornerShape(
                topStart = DesignTokens.radiusLg,
                topEnd = DesignTokens.radiusLg,
            ),
        )
        .background(MaterialTheme.colorScheme.surfaceVariant)

    when {
        styleImageUrls.isEmpty() -> {
            // Existing "no style, add one" CTA
            Box(modifier = baseModifier.clickable(onClick = onAddStyleClick)) {
                // ... existing empty-state content from the prior implementation ...
                // (copy verbatim from the file's current empty-state branch)
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                )
            }
        }
        styleImageUrls.size == 1 -> {
            // Single image — render once, no pager
            Box(
                modifier = baseModifier.clickable {
                    onPhotoClick(styleImageUrls, 0)
                },
            ) {
                SubcomposeAsyncImage(
                    model = styleImageUrls[0],
                    contentDescription = garmentName,
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) { LoadingDots() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> {
            // Carousel with dots + counter
            val pagerState = rememberPagerState(pageCount = { styleImageUrls.size })
            Box(modifier = baseModifier) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    SubcomposeAsyncImage(
                        model = styleImageUrls[page],
                        contentDescription = garmentName,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) { LoadingDots() }
                        },
                        modifier = Modifier.fillMaxSize().clickable {
                            onPhotoClick(styleImageUrls, pagerState.currentPage)
                        },
                    )
                }
                // Counter pill (top-right)
                Text(
                    text = "${pagerState.currentPage + 1} / ${styleImageUrls.size}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                // Dots (bottom-center)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(styleImageUrls.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (active) 22.dp else 7.dp,
                                    height = 7.dp,
                                )
                                .background(
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}
```

Add imports:
```kotlin
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
```

- [ ] **Step 3: Update the viewer state to handle multi-image taps**

In `OrderHeroCard`'s top-level state, replace `var fullScreenImage: String?` with:

```kotlin
var viewerImages: List<String> by remember { mutableStateOf(emptyList()) }
var viewerStartIndex: Int by remember { mutableStateOf(0) }
```

Update the call to `HeroImage`:
```kotlin
HeroImage(
    styleImageUrls = styleImageUrls,
    garmentTypeIcon = garmentTypeIcon,
    garmentName = garmentName,
    onAddStyleClick = onAddStyleClick,
    onPhotoClick = { urls, index ->
        viewerImages = urls
        viewerStartIndex = index
    },
)
```

Update the viewer call at the bottom of the card:
```kotlin
if (viewerImages.isNotEmpty()) {
    FullScreenImageViewer(
        images = viewerImages,
        startIndex = viewerStartIndex,
        contentDescription = null,
        onDismiss = {
            viewerImages = emptyList()
            viewerStartIndex = 0
        },
    )
}
```

- [ ] **Step 4: Wire from `OrderDetailScreen` — pass resolved URL list**

Open `OrderDetailScreen.kt`. Find the existing call to `OrderHeroCard(stylePhotoUrl = state.style?.photoUrl ?: ..., ...)`. Replace with the resolved list:

```kotlin
val firstItem = state.order?.items?.firstOrNull()
val styleImageUrls: List<String> = firstItem?.styleImages.orEmpty().mapNotNull { ref ->
    when (ref.source) {
        StyleImageSource.LIBRARY -> state.styles[ref.styleId]?.photoUrl
        StyleImageSource.UPLOADED -> ref.photoUrl
    }
}
OrderHeroCard(
    styleImageUrls = styleImageUrls,
    // ... rest of params unchanged
)
```

Required imports in `OrderDetailScreen.kt`:
```kotlin
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
```

- [ ] **Step 5: Compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD FAILED on `OrderGarmentDetailsCard.kt` (next task). Continue.

---

### Task 17: `OrderGarmentDetailsCard` — `FabricStrip`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`

- [ ] **Step 1: Replace `FabricThumbnail` with `FabricStrip`**

Find the existing `FabricThumbnail(photoUrl: String, onClick: () -> Unit)` (around line 229). Delete it.

Add at the same location:

```kotlin
@Composable
private fun FabricStrip(
    fabricImages: List<FabricImageRef>,
    onImageClick: (List<String>, Int) -> Unit,
) {
    if (fabricImages.isEmpty()) {
        FabricPlaceholder()
        return
    }
    val urls = fabricImages.map { it.photoUrl }
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        fabricImages.take(3).forEachIndexed { index, ref ->
            val isFirst = index == 0
            val caption = if (isFirst) stringResource(Res.string.order_detail_fabric_caption) else null
            Box(
                modifier = Modifier
                    .size(FABRIC_THUMBNAIL_SIZE)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    )
                    .clickable { onImageClick(urls, index) },
            ) {
                SubcomposeAsyncImage(
                    model = ref.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) { LoadingDots(dotSize = 6.dp) }
                    },
                    modifier = Modifier
                        .size(FABRIC_THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd)),
                )
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(DesignTokens.radiusFull),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
```

Add the import:
```kotlin
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
```

- [ ] **Step 2: Update the caller in `GarmentItemRow`**

Find the existing `if (!item.fabricPhotoUrl.isNullOrBlank()) { FabricThumbnail(...) } else { FabricPlaceholder() }` block. Replace with:

```kotlin
if (item.fabricImages.isNotEmpty()) {
    FabricStrip(
        fabricImages = item.fabricImages,
        onImageClick = onFabricStripClick,
    )
} else {
    FabricPlaceholder()
}
```

Update `GarmentItemRow`'s signature to accept the multi-image callback (replacing `onFabricPhotoClick: (String) -> Unit`):

```kotlin
@Composable
private fun GarmentItemRow(
    item: OrderItem,
    onAddFabricPhotoClick: (() -> Unit)?,
    onAddFabricNameClick: (() -> Unit)?,
    priority: OrderPriority,
    showHeader: Boolean,
    onFabricStripClick: (List<String>, Int) -> Unit = { _, _ -> },
)
```

Update the caller of `GarmentItemRow` to wire the viewer:

```kotlin
var viewerImages: List<String> by remember { mutableStateOf(emptyList()) }
var viewerStartIndex: Int by remember { mutableStateOf(0) }

// ... existing loop over items ...
GarmentItemRow(
    item = item,
    onAddFabricPhotoClick = if (showCta) onAddFabricPhotoClick else null,
    onAddFabricNameClick = if (showCta) onAddFabricNameClick else null,
    priority = if (index == 0) priority else OrderPriority.NORMAL,
    showHeader = index == 0,
    onFabricStripClick = { urls, idx ->
        viewerImages = urls
        viewerStartIndex = idx
    },
)
// ... after the loop, the viewer call:
if (viewerImages.isNotEmpty()) {
    FullScreenImageViewer(
        images = viewerImages,
        startIndex = viewerStartIndex,
        contentDescription = null,
        onDismiss = {
            viewerImages = emptyList()
            viewerStartIndex = 0
        },
    )
}
```

Remove the prior single-image `fullScreenImage: String?` state and its viewer call.

- [ ] **Step 3: Compile + iOS compile + detekt**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew detekt
```

All three must be BUILD SUCCESSFUL.

- [ ] **Step 4: Commit detail-screen changes**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailState.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModel.kt
git commit -m "$(cat <<'EOF'
feat(order-detail): multi-image carousel hero + fabric strip (PTSP-11)

OrderHeroCard.HeroImage is now count-aware (0/1/2+). 2+ images render
as a HorizontalPager with dots + counter; tap opens the FullScreenImageViewer
with the full list. OrderDetailViewModel observes the customer's full
gallery (cheaper than per-style subscriptions) and the resolver in
OrderDetailScreen builds the URL list per item.

OrderGarmentDetailsCard.FabricThumbnail → FabricStrip, up to 3 fabric
thumbs at 64dp. First thumb keeps the "Fabric" caption. Tap any thumb →
viewer opens with the full fabric list, swipe between.

StyleFormViewModel's linkToOrderId flow now APPENDS a LIBRARY ref to
the first item's styleImages list instead of replacing the single
styleId field. Guards against duplicates and the 3-image cap.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 18: Full verification

- [ ] **Step 1: Compile + detekt + tests**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew detekt
./gradlew :composeApp:allTests
```

All four must be BUILD SUCCESSFUL except the iOS `linkDebugTestIosSimulatorArm64` task (pre-existing `FirebaseCore not found` per PR #74/#76/#77/#79). If a DIFFERENT iOS test failure surfaces, that's a regression — investigate.

- [ ] **Step 2: Read `git status` — should be clean**

```bash
git status
```

Expected: working tree clean (only commits ahead of main).

```bash
git log --oneline main..HEAD
```

Expected: ~6 commits — one per task group (data layer / batch upload / batch createStyles / form redesign + strings / detail-screen carousel / and the spec + plan + previews from before).

---

### Task 19: Manual smoke test (Daniel)

**Pre-req:** `./gradlew :composeApp:installDebug` on Android emulator/device.

- [ ] **Step 1: Customer with no saved styles**

Sign in with Fola (or a similar test customer). New order for a customer with **zero** styles in their Style Gallery → Style section A renders → **"Choose from saved"** chip is **hidden** → only **"Upload new"** is visible (full width).

- [ ] **Step 2: Customer with saved styles**

For a customer with at least one saved style → both chips visible. Tap **"Choose from saved"** → picker sheet opens with the customer's gallery. Tap one row → row gets the "Already added" label and a LIBRARY badge appears in the strip.

- [ ] **Step 3: Multi-pick from library**

In the sheet, tap a second row → second LIBRARY thumb appears. Pick a third → sheet auto-closes (capacity reached). The action chips below the strip hide.

- [ ] **Step 4: Upload 2 style images, toggle ON, with description**

Remove one library style to free a slot. Tap **"Upload new"** → pick a photo. Repeat to upload a second. NEW badges show on both thumbs. Description field + Save-to-library toggle (default ON) appear below the chips. Type a description. Save the order.
- **Expected:** order saves successfully; customer's Style Gallery now has 2 new entries with that description; the OrderItem's `styleImages` list has 3 LIBRARY-source entries (1 original + 2 newly-created).

- [ ] **Step 5: Upload 2 style images, toggle OFF**

Same as step 4 but toggle OFF. Save.
- **Expected:** order saves; Style Gallery does NOT show the new images; OrderItem's `styleImages` has the 2 UPLOADED-source entries; order detail's hero shows the first uploaded image.

- [ ] **Step 6: Toggle ON, blank description**

Upload 1 image, toggle ON, leave description blank, save.
- **Expected:** Save succeeds; Style Gallery shows the new style with an empty description (rendered as `—` or similar).

- [ ] **Step 7: Fabric multi-image**

In the same order, upload 3 fabric images. Save.
- **Expected:** order saves; `fabricImages` list has 3 entries; order detail's garment card shows a strip of 3 thumbs with the "Fabric" caption on the first.

- [ ] **Step 8: Detail screen carousel**

Open the saved order's detail screen.
- **Expected:** hero is a swipeable carousel (3 dots, "1 / 3" counter); swipe through; tap any image → fullscreen viewer opens with all 3 swipeable.

- [ ] **Step 9: Fabric strip tap → carousel viewer**

In the garment card, tap any of the 3 fabric thumbs → fullscreen viewer opens with all 3 fabrics swipeable; starts at the index tapped.

- [ ] **Step 10: Network failure on upload (toggle OFF case)**

Enable airplane mode. Upload a style image, toggle OFF, tap Save.
- **Expected:** error snackbar; order NOT saved (data-loss guard works).

- [ ] **Step 11: Rapid double-tap on Save**

In a stable network state with new uploads pending, tap Save twice quickly.
- **Expected:** order saves once; only ONE new Style entity appears in the gallery (idempotency guard works).

- [ ] **Step 12: Edit a pre-PTSP-11 single-image order**

Open an order created BEFORE this PR shipped (use any pre-existing order). Edit it.
- **Expected:** Style section shows 1 image (legacy `styleId` or `stylePhotoUrl` migrated to a 1-element list); Fabric section similarly. Adding more works.

- [ ] **Step 13: Duplicate-from-order**

If the app has a "duplicate this order" flow surfaced anywhere, use it. (If not directly accessible, this is testable via the existing `seedFromOrderId` path; check the order detail screen for a duplicate action — if none, skip this step.)
- **Expected:** the duplicated order starts with LIBRARY refs from the source but ZERO uploaded one-off images (the strip is empty for those slots).

- [ ] **Step 14: iPhone hardware**

Per `feedback_gitlive_ios_nonnull_tokens` — Firebase uploads sometimes behave differently on simulator than hardware. On a real iPhone:
- Upload 2 style images (toggle OFF) → save → confirm both URLs end up in `styleImages` and the order detail's carousel shows them.
- Upload 2 fabric images → save → confirm fabric strip on detail screen.

---

### Task 20: Commit, push, open PR

- [ ] **Step 1: Verify clean state**

```bash
git status
git log --oneline main..HEAD
```

Expected: working tree clean; 6 commits ahead of main (or thereabouts, plus the spec/plan/previews commit from before).

- [ ] **Step 2: Push**

```bash
git push -u origin feature/ptsp-11-multi-image-style-fabric
```

The pre-push hook will run `codex review` automatically.

- [ ] **Step 3: Open PR — pause for Daniel's approval first**

After push completes, ask Daniel before running `gh pr create`. When approved:

```bash
gh pr create --title "feat(order): multi-image style & fabric + UX polish (PTSP-11)" --body "$(cat <<'EOF'
## Summary

- **PTSP-11**: Tailors can attach up to 3 style images and up to 3 fabric images per order item. Existing single-image fields on \`OrderItem\` become list-based (\`styleImages: List<StyleImageRef>\`, \`fabricImages: List<FabricImageRef>\`). Pre-PTSP-11 documents migrate transparently via the mapper.
- **UX polish bundled with PTSP-11**:
  - "Pick from gallery" renamed → "Choose from saved" (removes ambiguity with the phone's photo gallery)
  - "Choose from saved" is now HIDDEN (not disabled) when the customer has zero saved styles
  - Style + Fabric sections redesigned to Variant B+ (flat inline structure, no nested cards, strong chip affordance, source badges)
- **Detail screen**: \`OrderHeroCard\` becomes count-aware (0/1/2+); 2+ uses a \`HorizontalPager\` carousel with dots + counter. \`OrderGarmentDetailsCard\` shows up to 3 fabric thumbs.
- **\`FullScreenImageViewer\`** extended to a list + startIndex (carousel-aware). Tap any thumbnail anywhere → swipe between all images in that category.

Spec: \`docs/superpowers/specs/2026-05-26-ptsp-11-multi-image-style-fabric-design.md\`.

## Backward compatibility

- Old documents (PTSP-9 single-image): mapper synthesizes a 1-element list from \`styleId\`/\`stylePhotoUrl\`/\`fabricPhotoUrl\` at read time.
- New documents: written WITH both the new lists AND the legacy single fields (double-write) so older app versions still see one image. Removable in mid-2027.

## Test plan

- [x] \`./gradlew :composeApp:compileDebugKotlinAndroid\` ✅
- [x] \`./gradlew :composeApp:compileKotlinIosSimulatorArm64\` ✅
- [x] \`./gradlew detekt\` ✅
- [x] \`./gradlew :composeApp:allTests\` — 7 new mapper tests pass; iOS link fails on pre-existing FirebaseCore (matches prior PRs).
- [x] Manual smoke (Android, Fola): empty-state chip behavior; multi-pick from library; toggle ON / OFF / blank-description; fabric multi-image; detail-screen carousel; viewer swipe.
- [x] Pre-existing PTSP-9 single-image orders edit correctly.
- [ ] iPhone hardware smoke.
- [ ] Cursor BugBot.
- [ ] Pre-push \`codex review\` (auto).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of scope (reaffirming spec §13)

- Long-press to reorder thumbnails (order = add order, fixed)
- Pinch-to-zoom in the fullscreen viewer
- "Primary" image flag distinct from list ordering
- Per-image description (single shared per section per locked decision)
- Removing the legacy double-write code path (separate cleanup task mid-2027)
- Bulk re-upload of multiple library styles' images at once
