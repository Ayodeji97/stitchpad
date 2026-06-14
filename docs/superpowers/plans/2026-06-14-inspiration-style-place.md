# Inspiration Style Place Implementation Plan (PTSP-38 phase 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "Inspiration" place — a styles gallery not tied to any customer, reachable from the Dashboard — and let the tailor copy/move styles both ways between a customer's closet and Inspiration.

**Architecture:** Introduce a `StyleLocation` sealed type (`CustomerCloset(customerId)` | `Inspiration`). The style repository, gallery ViewModel, and transfer flow key on a `StyleLocation` instead of a raw `customerId`; Firestore/storage paths derive from it (`customers/{cid}/styles` vs top-level `inspiration`). The existing `StyleGalleryScreen`/VM and `StyleForm` (multi-pick) serve both places; only the title and the transfer-target list differ. `Style`'s fields are unchanged (its `customerId` is `""` and unused for Inspiration styles).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, GitLive Firebase (Firestore + Storage), kotlin.test + Turbine. Tests run via `./gradlew :composeApp:testDebugUnitTest`. iOS gate: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`. Lint: `./gradlew detekt` (auto-fix with `./gradlew detektFormat`).

**Spec:** `docs/superpowers/specs/2026-06-14-inspiration-style-place-design.md`

---

## File map

**Create**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/StyleLocation.kt` — the sealed location type.
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleLocationTargetsTest.kt` — VM target-list tests (added to the existing gallery test, see Task 6).

**Modify (repository + paths + mapper)**
- `core/domain/repository/StyleRepository.kt` — methods take `StyleLocation`.
- `feature/style/data/FirebaseStyleRepository.kt` — `collectionFor`/`storagePathFor`.
- `core/data/mapper/StyleMapper.kt` — `toStyle(location)`.
- `composeApp/src/commonTest/.../core/data/repository/FakeStyleRepository.kt` — new signatures, records locations.

**Modify (callers — mechanical)**
- `core/debug/DebugSeeder.kt` (2 calls)
- `feature/order/presentation/detail/OrderDetailViewModel.kt` (1)
- `feature/order/presentation/form/OrderFormViewModel.kt` (2)
- `feature/style/presentation/form/StyleFormViewModel.kt` (4) + its `SavedStateHandle` customerId
- `feature/style/presentation/gallery/StyleGalleryViewModel.kt` (transfer + observe + delete)

**Modify (navigation + UI)**
- `navigation/Routes.kt` — `StyleGalleryRoute`/`StyleFormRoute` nullable `customerId`.
- `feature/main/presentation/MainScreen.kt` — Inspiration nav wiring.
- `feature/style/presentation/gallery/StyleGalleryScreen.kt` — title/empty-state per location, "Inspiration" target row.
- `feature/style/presentation/gallery/StyleGalleryState.kt` / `…Action.kt` / `…Event.kt` — `TransferTarget` carries a location.
- `feature/dashboard/presentation/DashboardScreen.kt` + `DashboardEvent.kt` — Inspiration card/entry.
- `composeResources/values/strings.xml` — Inspiration strings.
- `firestore.rules` — `inspiration` collection rule.

---

## Task 1: `StyleLocation` domain type

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/StyleLocation.kt`

- [ ] **Step 1: Create the type**

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Where a style lives. A style is either in a specific customer's closet or in the
 * top-level Inspiration place (not tied to any customer). Drives the Firestore +
 * Storage paths and the gallery/transfer behaviour. See the Inspiration design spec.
 */
sealed interface StyleLocation {
    data class CustomerCloset(val customerId: String) : StyleLocation
    data object Inspiration : StyleLocation
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/StyleLocation.kt
git commit -m "feat(style): add StyleLocation (CustomerCloset | Inspiration)"
```

---

## Task 2: Repository keys on `StyleLocation`

Change every `customerId: String` parameter on `StyleRepository` to `location: StyleLocation`, and generalise transfer to `from`/`to` locations. This is the core refactor; later tasks update callers.

**Files:**
- Modify: `core/domain/repository/StyleRepository.kt`
- Modify: `feature/style/data/FirebaseStyleRepository.kt`
- Modify: `core/data/mapper/StyleMapper.kt`

- [ ] **Step 1: Update the interface**

Replace the body of `StyleRepository` with these signatures (imports: add `com.danzucker.stitchpad.core.domain.model.StyleLocation`):

```kotlin
interface StyleRepository {
    fun observeStyles(userId: String, location: StyleLocation): Flow<Result<List<Style>, DataError.Network>>

    suspend fun createStyle(
        userId: String, location: StyleLocation, description: String, photoBytes: ByteArray,
    ): Result<String, DataError.Network>

    suspend fun createStyles(
        userId: String, location: StyleLocation, description: String, photoBytesList: List<ByteArray>,
    ): Result<List<String>, DataError.Network>

    suspend fun updateStyle(
        userId: String, location: StyleLocation, style: Style, newPhotoBytes: ByteArray?,
    ): EmptyResult<DataError.Network>

    suspend fun deleteStyle(
        userId: String, location: StyleLocation, style: Style,
    ): EmptyResult<DataError.Network>

    /** Copy [style] from [from] into [to], sharing the source image (no re-upload when
     *  it's already in storage). Source stays; image storage is never eagerly deleted. */
    suspend fun copyStyle(
        userId: String, from: StyleLocation, style: Style, to: StyleLocation,
    ): EmptyResult<DataError.Network>

    /** Move [style] from [from] to [to]: write the target sharing the image, then remove
     *  the source doc only. */
    suspend fun moveStyle(
        userId: String, from: StyleLocation, style: Style, to: StyleLocation,
    ): EmptyResult<DataError.Network>
}
```

- [ ] **Step 2: Update `FirebaseStyleRepository` paths**

Add the location-aware helpers and replace `stylesCollection`/`storagePath`. Add import `com.danzucker.stitchpad.core.domain.model.StyleLocation`.

```kotlin
private fun collectionFor(userId: String, location: StyleLocation) = when (location) {
    is StyleLocation.CustomerCloset ->
        firestore.collection("users").document(userId)
            .collection("customers").document(location.customerId)
            .collection("styles")
    StyleLocation.Inspiration ->
        firestore.collection("users").document(userId).collection("inspiration")
}

private fun storagePathFor(userId: String, location: StyleLocation, styleId: String): String =
    when (location) {
        is StyleLocation.CustomerCloset ->
            "users/$userId/customers/${location.customerId}/styles/$styleId.jpg"
        StyleLocation.Inspiration -> "users/$userId/inspiration/$styleId.jpg"
    }

private fun StyleLocation.customerIdOrEmpty(): String =
    (this as? StyleLocation.CustomerCloset)?.customerId.orEmpty()
```

- [ ] **Step 3: Rewrite the repository method bodies**

Replace every `stylesCollection(userId, customerId)` with `collectionFor(userId, location)`, every `storagePath(userId, customerId, id)` with `storagePathFor(userId, location, id)`, and every `.toStyle(customerId)` with `.toStyle(location)` (Task 2 Step 5). In `createStyle`, the upload job's `customerId` field becomes `location.customerIdOrEmpty()` (it's only used to build the patch path for closets; Inspiration patches by the `styleId`+collection — see Task 2 Step 4). For `copyStyle`/`moveStyle`, the param rename is `from`/`to`: `writeSharedCopy(userId, to, style)`, source ops use `from`. Keep the phase-1 logic (offline enqueue, `writeSharedCopy` share-vs-reupload branch, never-eager-delete in `deleteStyle`) intact — only the path source changes.

- [ ] **Step 4: Make the upload patch Inspiration-aware**

In `OfflineUploadOutbox.patchStyleImage` (`core/offline/OfflineUploadOutbox.kt`), the doc ref is built from `customers/{customerId}/styles/{styleId}`. Add an `inspirationStyle` flag to `OfflineUploadJob` (default `false`) and, when set, target `users/{userId}/inspiration/{styleId}` instead:

```kotlin
// OfflineUploadJob: add
val inspirationStyle: Boolean = false,

// patchStyleImage docRef:
val docRef = if (job.inspirationStyle) {
    firestore.collection("users").document(job.userId)
        .collection("inspiration").document(requireNotNull(job.styleId))
} else {
    firestore.collection("users").document(job.userId)
        .collection("customers").document(requireNotNull(job.customerId))
        .collection("styles").document(requireNotNull(job.styleId))
}
```

In `FirebaseStyleRepository.createStyle`/`updateStyle`/`reuploadIndependentCopy`, set `inspirationStyle = location is StyleLocation.Inspiration` on the enqueued `OfflineUploadJob`.

- [ ] **Step 5: Update the mapper**

In `core/data/mapper/StyleMapper.kt`, change `toStyle` to take a `StyleLocation` and fill `customerId` from it (import `StyleLocation`):

```kotlin
fun StyleDto.toStyle(location: StyleLocation): Style = Style(
    id = id,
    customerId = (location as? StyleLocation.CustomerCloset)?.customerId.orEmpty(),
    description = description,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = runCatching { ImageSyncState.valueOf(syncState) }.getOrDefault(ImageSyncState.SYNCED),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
```

- [ ] **Step 6: Verify it compiles (callers still broken — expected)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: FAIL — call sites in DebugSeeder / OrderForm / OrderDetail / StyleForm / StyleGallery still pass `customerId`. Fixed in Tasks 3–4. (Do not commit yet.)

---

## Task 3: Update the Fake + all repository callers

**Files:**
- Modify: `composeApp/src/commonTest/.../core/data/repository/FakeStyleRepository.kt`
- Modify: `core/debug/DebugSeeder.kt`, `feature/order/.../OrderDetailViewModel.kt`, `feature/order/.../OrderFormViewModel.kt`, `feature/style/.../form/StyleFormViewModel.kt`

- [ ] **Step 1: Update `FakeStyleRepository`**

Change all override signatures to match Task 2. `observeStyles(userId, location)` returns `stylesList` regardless of location (tests set `stylesList` directly). Record transfers as locations:

```kotlin
var lastCopied: Triple<StyleLocation, String, StyleLocation>? = null  // (from, styleId, to)
var lastMoved: Triple<StyleLocation, String, StyleLocation>? = null

override suspend fun copyStyle(userId: String, from: StyleLocation, style: Style, to: StyleLocation): EmptyResult<DataError.Network> {
    operationError?.let { return Result.Error(it) }
    lastCopied = Triple(from, style.id, to)
    return Result.Success(Unit)
}
override suspend fun moveStyle(userId: String, from: StyleLocation, style: Style, to: StyleLocation): EmptyResult<DataError.Network> {
    operationError?.let { return Result.Error(it) }
    lastMoved = Triple(from, style.id, to)
    return Result.Success(Unit)
}
```
(Update `createStyle`/`createStyles`/`updateStyle`/`deleteStyle`/`observeStyles` signatures to take `location: StyleLocation`; bodies are otherwise unchanged. Add `import …model.StyleLocation`.)

- [ ] **Step 2: Update the customer-only callers (mechanical)**

Wrap each existing `customerId` in `StyleLocation.CustomerCloset(...)` (add the import to each file):
- `DebugSeeder.kt:163` → `observeStyles(userId, StyleLocation.CustomerCloset(customer.id))`
- `DebugSeeder.kt:168` → `deleteStyle(userId, StyleLocation.CustomerCloset(customer.id), it)`
- `OrderDetailViewModel.kt:604` → `observeStyles(userId, StyleLocation.CustomerCloset(customerId))`
- `OrderFormViewModel.kt:422` → `observeStyles(uid, StyleLocation.CustomerCloset(customerId))`
- `OrderFormViewModel.kt:830` (`createStyles`) → pass `StyleLocation.CustomerCloset(customerId)` as the location arg
- `StyleFormViewModel.kt:108/151/170/186` → wrap the `customerId` local in `StyleLocation.CustomerCloset(customerId)` at each call.

- [ ] **Step 3: Commit (after Task 4 compiles)** — see Task 4 Step 4.

---

## Task 4: Style form + gallery VMs derive location from the route

`StyleFormRoute`/`StyleGalleryRoute` already carry `customerId`; make it nullable (`null` = Inspiration) and derive a `StyleLocation`.

**Files:**
- Modify: `navigation/Routes.kt`, `feature/main/presentation/MainScreen.kt`
- Modify: `feature/style/presentation/form/StyleFormViewModel.kt`, `feature/style/presentation/gallery/StyleGalleryViewModel.kt`

- [ ] **Step 1: Nullable route args**

In `navigation/Routes.kt`:
```kotlin
@Serializable data class StyleGalleryRoute(val customerId: String? = null)
@Serializable data class StyleFormRoute(
    val customerId: String? = null,
    val styleId: String? = null,
    val linkToOrderId: String? = null,
)
```

- [ ] **Step 2: Derive `StyleLocation` in the form VM**

In `StyleFormViewModel`, replace the `customerId` guard. The VM currently reads `savedStateHandle["customerId"]` and bails if null. Now a null customerId means Inspiration — valid. Add:
```kotlin
private val customerId: String? = savedStateHandle["customerId"]
private val location: StyleLocation =
    customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration
```
Remove the "customerId == null → NavigateBack" early return (Inspiration is now legitimate). Replace the `styleRepository.*(userId, customerId, …)` calls with `…(userId, location, …)`. `allowMultiPhoto` already gates on `styleId == null && linkToOrderId == null`; Inspiration (no linkToOrderId) keeps multi-pick. Keep the `linkToOrderId` order-attach path unchanged (only reachable for closets).

- [ ] **Step 3: Derive `StyleLocation` in the gallery VM**

In `StyleGalleryViewModel`:
```kotlin
private val customerId: String? = savedStateHandle["customerId"]
private val location: StyleLocation =
    customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration
```
Remove the `customerId == null → NavigateBack` early return. Replace `observeStyles(userId, customerId)` / `deleteStyle(userId, customerId, style)` with `location`. `OnAddClick` navigates to the form for the same location (pass `customerId` which may be null). Transfer wiring is updated in Task 6.

- [ ] **Step 4: Compile + commit Tasks 2–4**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: PASS.
Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleGalleryViewModelTest" --tests "*StyleFormViewModelTest" --tests "*OrderFormViewModelTest"`
Expected: PASS (after fixing test call sites — the gallery/form tests construct `StyleGalleryViewModel`/`StyleFormViewModel` and assert `lastCopied`/`lastMoved`; update those assertions to the `StyleLocation` triples and pass nullable customerIds. See Task 6 for the gallery test rewrite.)

```bash
git add -A
git commit -m "refactor(style): key StyleRepository on StyleLocation; routes carry nullable customerId"
```

---

## Task 5: Strings + Firestore rule

**Files:**
- Modify: `composeResources/values/strings.xml`, `firestore.rules`

- [ ] **Step 1: Strings**

Add (apostrophes via `&apos;`, never `\'`):
```xml
<string name="style_inspiration_title">Inspiration</string>
<string name="style_inspiration_empty_title">No saved styles yet</string>
<string name="style_inspiration_empty_subtitle">Save looks you love here, then use them for any customer.</string>
<string name="style_transfer_to_inspiration">Inspiration</string>
<string name="dashboard_inspiration_card_title">Inspiration</string>
<string name="dashboard_inspiration_card_subtitle">Looks you&apos;ve saved</string>
```

- [ ] **Step 2: Firestore rule**

In `firestore.rules`, add under the user document (sibling of `customers`/`orders`):
```
match /inspiration/{styleId} {
  allow read, write: if isOwner(uid);
}
```
Add a code comment noting the **Storage** rule for `users/{uid}/inspiration/` is console-managed and must be added before testing uploads.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml firestore.rules
git commit -m "feat(style): Inspiration strings + Firestore rule"
```

---

## Task 6: Gallery — title, empty state, and both-way transfer targets

**Files:**
- Modify: `feature/style/presentation/gallery/StyleGalleryState.kt`, `…Action.kt`, `…Event.kt`
- Modify: `feature/style/presentation/gallery/StyleGalleryViewModel.kt`, `StyleGalleryScreen.kt`
- Modify: `composeApp/src/commonTest/.../gallery/StyleGalleryViewModelTest.kt`

- [ ] **Step 1: `TransferTarget` carries a location**

In `StyleGalleryState.kt`, change `TransferTarget`:
```kotlin
data class TransferTarget(
    val location: StyleLocation,
    val name: String,            // customer name, or the Inspiration label
    val isInspiration: Boolean = false,
)
```
Add `val isInspirationGallery: Boolean = false` to `StyleGalleryState` (drives the title) and set it from the VM's `location`.

- [ ] **Step 2: Build the target list per location (failing test first)**

Add to `StyleGalleryViewModelTest.kt`:
```kotlin
@Test
fun fromCloset_targetsAreOtherCustomersPlusInspiration() = runTest {
    authRepository.signUpWithEmail("t@t.com", "p", "T")
    customerRepository.customersList = listOf(
        fakeCustomer(id = "customer-1"),               // source — excluded
        fakeCustomer(id = "customer-2", name = "Bisi"),
    )
    val vm = createViewModel(customerId = "customer-1")
    vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
    vm.onAction(StyleGalleryAction.OnCopyClick)
    val t = vm.state.value.transfer!!
    assertEquals(StyleLocation.CustomerCloset("customer-2"), t.targets[0].location)
    assertTrue(t.targets.last().isInspiration)
}

@Test
fun fromInspiration_targetsAreCustomersOnly() = runTest {
    authRepository.signUpWithEmail("t@t.com", "p", "T")
    customerRepository.customersList = listOf(fakeCustomer(id = "customer-2", name = "Bisi"))
    val vm = createViewModel(customerId = null) // Inspiration
    vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
    vm.onAction(StyleGalleryAction.OnMoveClick)
    val t = vm.state.value.transfer!!
    assertEquals(listOf(StyleLocation.CustomerCloset("customer-2")), t.targets.map { it.location })
    assertTrue(t.targets.none { it.isInspiration })
}
```
Make `createViewModel` accept `customerId: String?` and put it in the `SavedStateHandle` only when non-null.

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleGalleryViewModelTest"`
Expected: FAIL (targets don't include Inspiration / location type mismatch).

- [ ] **Step 3: Implement `openTransfer`**

In `StyleGalleryViewModel.openTransfer`, build targets from active customers, exclude the source customer (when in a closet), and append an Inspiration target when the current location is a closet:
```kotlin
val customerTargets = result.data
    .filter { it.slotState == CustomerSlotState.ACTIVE && location != StyleLocation.CustomerCloset(it.id) }
    .map { TransferTarget(StyleLocation.CustomerCloset(it.id), it.name) }
val inspirationLabel = getString(Res.string.style_transfer_to_inspiration)
val targets = if (location is StyleLocation.CustomerCloset) {
    customerTargets + TransferTarget(StyleLocation.Inspiration, inspirationLabel, isInspiration = true)
} else {
    customerTargets
}
```
(`getString` is suspend — `openTransfer` already runs in `viewModelScope.launch`.) Update `transferTo(target: TransferTarget)` to call `copyStyle/moveStyle(userId, from = location, transfer.style, to = target.location)` and emit `StyleTransferred(mode, target.location, target.name)`.

- [ ] **Step 4: Update the action + event + screen wiring**

- `StyleGalleryAction.OnTargetCustomerSelected(val customerId: String)` → `OnTargetSelected(val target: TransferTarget)` (the screen already holds the `TransferTarget`).
- `StyleGalleryEvent.StyleTransferred(mode, targetCustomerId, targetName)` → `(mode, target: StyleLocation, targetName)`. In `StyleGalleryScreen` Root, the "View" action navigates: `when (target) { is CustomerCloset -> onNavigateToCustomerCloset(target.customerId); Inspiration -> onNavigateToInspiration() }` (add `onNavigateToInspiration: () -> Unit`).
- In the customer-picker sheet, render `transfer.targets` (each a `TransferTarget`); use the Person icon for customers and a distinct icon (e.g. `Icons.Default.Collections`) for the Inspiration row.
- Title: `if (state.isInspirationGallery) stringResource(style_inspiration_title) else <existing closet title>`. Empty state likewise uses the Inspiration strings when `isInspirationGallery`.

- [ ] **Step 5: Update existing gallery transfer tests**

Update `onTargetCustomerSelected_copy_*` / `_move_*` to call `OnTargetSelected(TransferTarget(StyleLocation.CustomerCloset("customer-2"), "Bisi"))` and assert `styleRepository.lastCopied == Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.CustomerCloset("customer-2"))`, and `event.target == StyleLocation.CustomerCloset("customer-2")`.

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleGalleryViewModelTest"`
Expected: PASS.

- [ ] **Step 6: iOS compile + detekt + commit**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew detektFormat && ./gradlew detekt
git add -A
git commit -m "feat(style): Inspiration gallery title + both-way transfer targets"
```

---

## Task 7: Navigation wiring

**Files:**
- Modify: `feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: Wire the Inspiration destinations**

In `MainScreen`, `StyleGalleryRoot(...)` gains `onNavigateToInspiration = { navController.navigate(StyleGalleryRoute(customerId = null)) }` (alongside the existing `onNavigateToCustomerCloset`). `onNavigateToAddStyle` for Inspiration passes `customerId = null`: the gallery's `OnAddClick` should navigate to `StyleFormRoute(customerId = <this gallery's customerId, may be null>)`. Confirm `StyleFormRoot` already only needs `onNavigateBack` (it does).

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(style): navigate to Inspiration gallery"
```

---

## Task 8: Dashboard entry (design-first)

**Files:**
- Create: `preview/inspiration-dashboard-entry.html` (design exploration)
- Modify: `feature/dashboard/presentation/DashboardScreen.kt`, `DashboardEvent.kt`, `MainScreen.kt`

- [ ] **Step 1: Build HTML variants under `Preview/`**

Create `preview/inspiration-dashboard-entry.html` with 2–3 placements/treatments of an "Inspiration" entry among the existing dashboard cards (light + dark), following the conventions in the existing `preview/*.html` (Adire tokens, phone shell, Fraunces/Manrope/JetBrains Mono). Open it (`open preview/inspiration-dashboard-entry.html`) and get Daniel's pick before writing Compose.

- [ ] **Step 2: Add the entry + event**

Add `data object NavigateToInspiration : DashboardEvent`. Add an Inspiration card/row to `DashboardScreen` (per the chosen variant) that calls `onAction(DashboardAction.OnInspirationClick)` → VM sends `NavigateToInspiration`. Wire in `MainScreen`'s `DashboardRoot(... onNavigateToInspiration = { navController.navigate(StyleGalleryRoute(customerId = null)) })`.

- [ ] **Step 3: Compile + detekt + commit**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew detekt
git add -A
git commit -m "feat(dashboard): Inspiration entry → Inspiration styles place"
```

---

## Task 9: Final verification + PR

- [ ] **Step 1: Full gate**

```bash
./gradlew :composeApp:testDebugUnitTest && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew detekt
```
Expected: all green.

- [ ] **Step 2: Manual smoke test (Daniel is QA — put these in the PR body)**

1. Dashboard → **Inspiration** opens an empty place; add 2+ photos at once (multi-pick) → all appear.
2. From a customer's closet, long-press a style → **Copy to → Inspiration** → "View" opens Inspiration showing it; the original stays in the closet; **both images render**.
3. From Inspiration, long-press → **Move to → <customer>** → it leaves Inspiration and appears in that closet, image renders.
4. Picker correctness: from a closet the target list shows other active customers **+ Inspiration**; from Inspiration it shows customers only.
5. Order flow unchanged: creating an order still picks from the customer's closet only (copy-bridge confirmed).
6. Offline: copy with airplane mode on → reconnect → syncs. Repeat 1–4 on **iOS**.
7. **Ops:** confirm the Firebase **Storage** rule for `users/{uid}/inspiration/` is deployed (else uploads fail).

- [ ] **Step 3: Open the PR**

Branch `feat/inspiration-style-place-ptsp-38-p2`; both Cursor Bugbot and `codex review` before merge.

---

## Deferred follow-up (separate ticket/PR)
Add **Inspiration as a source in the order-creation style picker** (a "Closet | Inspiration" toggle). Requires the order's `StyleImageRef` resolution (`OrderFormViewModel.availableStyles`, `OrderForm`/`OrderDetail` rendering) to also resolve styleIds against the Inspiration collection. Out of scope here.
