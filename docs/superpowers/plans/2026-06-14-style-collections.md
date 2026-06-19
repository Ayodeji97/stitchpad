# Style Collections Implementation Plan (PTSP-38 phase 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tailors organise styles into named, paid-gated **folders** ("collections") on both the customer-closet and Inspiration levels, with tiered caps, building migration-free on the merged flat styles model.

**Architecture:** Extend `StyleLocation` with an optional `folderId` (null = the existing flat collection = the implicit "My styles" default folder). Add a `styleFolders`/`inspirationFolders` Firestore subcollection for named folders. A `StyleCollectionLimits` resolver maps `subscriptionTier` → `{maxFolders, maxImagesPerFolder}` per level, read via the existing `EntitlementsProvider`. Free users keep the flat gallery (capped); paid users see a folders grid → folder detail (the existing gallery scoped to a folder). No data migration — the flat collection *is* the default folder.

**Tech Stack:** KMP, Compose Multiplatform, Koin, GitLive Firebase, kotlin.test + Turbine. Gates: `./gradlew :composeApp:testDebugUnitTest`, `:composeApp:compileKotlinIosSimulatorArm64`, `detekt` (auto-fix `detektFormat`). zsh — capture exit via `${pipestatus[1]}`.

**Spec:** `docs/superpowers/specs/2026-06-14-style-collections-design.md`

**Caps (default "My styles" folder counts toward the folder cap):**

| Tier | Inspiration | Per customer | Img/folder (Insp / Cust) |
|---|---|---|---|
| Free | 10 flat (no folders) | 5 flat | n/a |
| Pro | 10 folders → 50 | 5 folders → 15 | 5 / 3 |
| Atelier | 20 folders → 200 | 5 folders → 25 | 10 / 5 |

---

## File map

**Create**
- `core/domain/model/StyleFolder.kt` — folder domain model.
- `core/data/dto/StyleFolderDto.kt` + `core/data/mapper/StyleFolderMapper.kt`.
- `feature/style/domain/StyleCollectionLimits.kt` — tier → caps resolver (pure, unit-tested).
- `feature/style/presentation/folders/` — `StyleFoldersRoot/Screen/State/Action/Event/ViewModel.kt` (the folders-grid).
- Tests: `StyleCollectionLimitsTest.kt`, `StyleFoldersViewModelTest.kt`.

**Modify**
- `core/domain/model/StyleLocation.kt` — add `folderId`.
- `core/domain/repository/StyleRepository.kt` + `feature/style/data/FirebaseStyleRepository.kt` — folder CRUD + folder-scoped paths + counts.
- `core/data/repository/FakeStyleRepository.kt` (test).
- `core/domain/entitlement/UserEntitlements.kt` (+ `EntitlementsCalculator.kt`) — expose collection limits.
- `navigation/Routes.kt` + `feature/main/presentation/MainScreen.kt` — folders route + scoped gallery route.
- `feature/style/presentation/gallery/StyleGalleryViewModel.kt`/`Screen.kt` — folder-scoped add caps.
- `feature/dashboard/.../DashboardScreen.kt` (Inspiration entry → folders grid for paid).
- `feature/style/presentation/gallery/*` transfer (copy/move folder step).
- `feature/order/presentation/form/OrderFormViewModel.kt` (flatten across folders).
- `composeResources/values/strings.xml`, `firestore.rules`.

---

## Task 1: Extend `StyleLocation` with `folderId`

**Files:** Modify `core/domain/model/StyleLocation.kt`; update all references.

- [ ] **Step 1: Extend the type**

```kotlin
sealed interface StyleLocation {
    /** [folderId] null = the customer's flat default folder (existing `styles` collection). */
    data class CustomerCloset(val customerId: String, val folderId: String? = null) : StyleLocation
    /** [folderId] null = the flat default Inspiration folder (existing `inspiration` collection). */
    data class Inspiration(val folderId: String? = null) : StyleLocation
}
```

- [ ] **Step 2: Fix references (Inspiration is now a class)**

`StyleLocation.Inspiration` → `StyleLocation.Inspiration()` everywhere. Find them: `grep -rn "StyleLocation.Inspiration\b" composeApp/src --include=*.kt | grep -v build`. Update `FirebaseStyleRepository.collectionFor`/`storagePathFor`, the gallery/form VMs' `location` derivation, `FakeStyleRepository`, `StyleGalleryViewModelTest`, `StyleMapper` callers. `is StyleLocation.Inspiration` matches still compile.

- [ ] **Step 3: Verify + commit**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -2 ; echo "IOS=${pipestatus[1]}"` → IOS=0.
Run: `./gradlew :composeApp:testDebugUnitTest 2>&1 | tail -2 ; echo "TEST=${pipestatus[1]}"` → TEST=0.
```bash
git add -A && git commit -m "refactor(style): StyleLocation carries an optional folderId"
```

---

## Task 2: `StyleCollectionLimits` (tier → caps)

**Files:** Create `feature/style/domain/StyleCollectionLimits.kt` + test.

- [ ] **Step 1: Failing test**

`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimitsTest.kt`:
```kotlin
package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyleCollectionLimitsTest {
    @Test fun free_hasNoFolders_flatCaps() {
        val insp = StyleCollectionLimits.forInspiration(SubscriptionTier.FREE)
        assertFalse(insp.foldersEnabled)
        assertEquals(10, insp.flatCap)
        val cust = StyleCollectionLimits.forCustomer(SubscriptionTier.FREE)
        assertEquals(5, cust.flatCap)
    }
    @Test fun pro_inspiration_10folders_5each() {
        val l = StyleCollectionLimits.forInspiration(SubscriptionTier.PRO)
        assertTrue(l.foldersEnabled)
        assertEquals(10, l.maxFolders)
        assertEquals(5, l.maxImagesPerFolder)
    }
    @Test fun atelier_inspiration_20folders_10each() {
        val l = StyleCollectionLimits.forInspiration(SubscriptionTier.ATELIER)
        assertEquals(20, l.maxFolders); assertEquals(10, l.maxImagesPerFolder)
    }
    @Test fun pro_customer_5folders_3each() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.PRO)
        assertEquals(5, l.maxFolders); assertEquals(3, l.maxImagesPerFolder)
    }
    @Test fun atelier_customer_5folders_5each() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.ATELIER)
        assertEquals(5, l.maxFolders); assertEquals(5, l.maxImagesPerFolder)
    }
}
```
Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleCollectionLimitsTest" 2>&1 | tail -3 ; echo "EXIT=${pipestatus[1]}"` → FAIL (unresolved).

- [ ] **Step 2: Implement**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimits.kt`:
```kotlin
package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * Caps for the Style Collections feature. [foldersEnabled] false = Free (flat
 * gallery capped at [flatCap]). When enabled, the default "My styles" folder
 * counts as one of [maxFolders].
 */
data class StyleCollectionLimits(
    val foldersEnabled: Boolean,
    val maxFolders: Int,
    val maxImagesPerFolder: Int,
    val flatCap: Int,
) {
    companion object {
        fun forInspiration(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
            SubscriptionTier.FREE -> StyleCollectionLimits(false, 0, 0, flatCap = 10)
            SubscriptionTier.PRO -> StyleCollectionLimits(true, maxFolders = 10, maxImagesPerFolder = 5, flatCap = 5)
            SubscriptionTier.ATELIER -> StyleCollectionLimits(true, maxFolders = 20, maxImagesPerFolder = 10, flatCap = 10)
        }
        fun forCustomer(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
            SubscriptionTier.FREE -> StyleCollectionLimits(false, 0, 0, flatCap = 5)
            SubscriptionTier.PRO -> StyleCollectionLimits(true, maxFolders = 5, maxImagesPerFolder = 3, flatCap = 3)
            SubscriptionTier.ATELIER -> StyleCollectionLimits(true, maxFolders = 5, maxImagesPerFolder = 5, flatCap = 5)
        }
    }
}
```
(Confirm `SubscriptionTier` enum values are exactly `FREE`, `PRO`, `ATELIER` — `grep -n "enum class SubscriptionTier" -A5 composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/*.kt`. Adjust `flatCap` for paid tiers if you prefer a different default-folder size — it's only used when foldersEnabled but rendering the default folder; the per-folder cap governs.)

- [ ] **Step 3: Pass + commit**

Run the test → PASS. `git add -A && git commit -m "feat(style): StyleCollectionLimits tier→caps resolver"`

---

## Task 3: `StyleFolder` model + DTO + mapper

**Files:** Create `core/domain/model/StyleFolder.kt`, `core/data/dto/StyleFolderDto.kt`, `core/data/mapper/StyleFolderMapper.kt`.

- [ ] **Step 1: Model**

```kotlin
// StyleFolder.kt
package com.danzucker.stitchpad.core.domain.model

data class StyleFolder(
    val id: String,
    val name: String,
    val coverStyleId: String? = null,
    val styleCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 2: DTO + mapper**

```kotlin
// StyleFolderDto.kt
package com.danzucker.stitchpad.core.data.dto
import kotlinx.serialization.Serializable
@Serializable
data class StyleFolderDto(
    val id: String = "",
    val name: String = "",
    val coverStyleId: String? = null,
    val styleCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
```
```kotlin
// StyleFolderMapper.kt
package com.danzucker.stitchpad.core.data.mapper
import com.danzucker.stitchpad.core.data.dto.StyleFolderDto
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import kotlin.time.Clock
fun StyleFolderDto.toStyleFolder(): StyleFolder =
    StyleFolder(id, name, coverStyleId, styleCount, createdAt, updatedAt)
fun StyleFolder.toDto(): StyleFolderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return StyleFolderDto(id, name, coverStyleId, styleCount,
        createdAt = if (createdAt == 0L) now else createdAt, updatedAt = now)
}
```

- [ ] **Step 3: Compile + commit**

`./gradlew :composeApp:compileKotlinIosSimulatorArm64` → green. `git add -A && git commit -m "feat(style): StyleFolder model + DTO + mapper"`

---

## Task 4: Repository — folder CRUD + folder-scoped styles

**Files:** Modify `core/domain/repository/StyleRepository.kt`, `feature/style/data/FirebaseStyleRepository.kt`, `core/data/repository/FakeStyleRepository.kt`.

- [ ] **Step 1: Interface additions**

```kotlin
fun observeFolders(userId: String, location: StyleLocation): Flow<Result<List<StyleFolder>, DataError.Network>>
suspend fun createFolder(userId: String, location: StyleLocation, name: String): Result<String, DataError.Network>
suspend fun renameFolder(userId: String, location: StyleLocation, folderId: String, name: String): EmptyResult<DataError.Network>
suspend fun deleteFolder(userId: String, location: StyleLocation, folderId: String): EmptyResult<DataError.Network>
```
`location` here is the *parent* place (CustomerCloset(cid) / Inspiration()), folderId ignored for folder-list ops.

- [ ] **Step 2: Firebase impl**

In `FirebaseStyleRepository`, add a `foldersCollectionFor(userId, location)`:
```kotlin
private fun foldersCollectionFor(userId: String, location: StyleLocation) = when (location) {
    is StyleLocation.CustomerCloset ->
        firestore.collection("users").document(userId)
            .collection("customers").document(location.customerId).collection("styleFolders")
    is StyleLocation.Inspiration ->
        firestore.collection("users").document(userId).collection("inspirationFolders")
}
```
- `observeFolders`: `.snapshots().map { it.documents.mapNotNull { d -> runCatching { d.data<StyleFolderDto>().toStyleFolder() }.getOrNull() }.sortedByDescending { it.createdAt } }` wrapped in `Result.Success`, with the same `.catch { emit(Error) }` as `observeStyles`.
- `createFolder`: `val ref = foldersCollectionFor(...).document; offlineWrites.enqueue { ref.set(StyleFolder(id=ref.id, name=name, createdAt=0, updatedAt=0).toDto()) }; Result.Success(ref.id)`.
- `renameFolder`: `offlineWrites.enqueue { foldersCollectionFor(...).document(folderId).update("name" to name, "updatedAt" to now) }`.
- `deleteFolder`: enqueue delete of the folder doc AND its `styles` subcollection docs (read them first, doc-only delete each — never eager storage delete, per phase-1). The folder's styles live at `foldersCollectionFor(...).document(folderId).collection("styles")`.
- Update `collectionFor(userId, location)` so a `folderId != null` reads the folder's `styles` subcollection: for `CustomerCloset(cid, fid)` → `customers/{cid}/styleFolders/{fid}/styles`; for `Inspiration(fid)` → `inspirationFolders/{fid}/styles`. `folderId == null` keeps today's flat `customers/{cid}/styles` / `inspiration`. Mirror in `storagePathFor` (folder images at `users/{uid}/.../styleFolders/{fid}/{styleId}.jpg` etc.) and set `inspirationStyle`/customerId on the upload job as today.

- [ ] **Step 3: Fake**

`FakeStyleRepository`: add `var folders: List<StyleFolder> = emptyList()` returned by `observeFolders`; `var lastCreatedFolderName/lastRenamed/lastDeletedFolderId` trackers; implement the 4 methods recording inputs and returning Success (or `operationError`).

- [ ] **Step 4: Compile + commit**

`./gradlew :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew :composeApp:testDebugUnitTest` → green. `git add -A && git commit -m "feat(style): folder CRUD + folder-scoped styles in StyleRepository"`

---

## Task 5: Expose limits via entitlements

**Files:** Modify `core/domain/entitlement/UserEntitlements.kt`, `core/domain/entitlement/EntitlementsCalculator.kt`.

- [ ] **Step 1:** Read both files. `UserEntitlements` already carries `customerCap` derived from the tier. Add the tier itself if not present (or a `styleCollectionsEnabled`/`tier` field). The cleanest: keep `StyleCollectionLimits.forInspiration/forCustomer(tier)` as the source of truth and just ensure VMs can get the current `SubscriptionTier`. Confirm `EntitlementsProvider.current()` exposes the tier (read `UserEntitlements`); if it only exposes derived caps, add `val tier: SubscriptionTier` to `UserEntitlements` and set it in `EntitlementsCalculator`.

- [ ] **Step 2:** Compile + commit. `git commit -m "feat(entitlement): expose subscription tier for style collection caps"`

---

## Task 6: Folders-grid screen (paid) + nav

**Files:** Create `feature/style/presentation/folders/StyleFolders{Root,Screen,State,Action,Event,ViewModel}.kt`; modify `navigation/Routes.kt`, `feature/main/presentation/MainScreen.kt`; test `StyleFoldersViewModelTest.kt`.

- [ ] **Step 1: Routes** — `data class StyleFoldersRoute(val customerId: String? = null)` (null = Inspiration). Add `folderId` to `StyleGalleryRoute`/`StyleFormRoute`: `data class StyleGalleryRoute(val customerId: String? = null, val folderId: String? = null)`, same for the form.

- [ ] **Step 2: State/Action/Event** —
State: `folders: List<StyleFolder>`, `isLoading`, `limits: StyleCollectionLimits`, `showCreateSheet`, `renameTarget: StyleFolder?`, `errorMessage`.
Action: `OnFolderClick(folderId)`, `OnCreateFolderClick`, `OnConfirmCreate(name)`, `OnRenameClick(folder)`, `OnConfirmRename(name)`, `OnDeleteFolderClick(folder)`, `OnConfirmDelete`, `OnDismissSheet`, `OnUpgradeClick`, `OnNavigateBack`, `OnErrorDismiss`.
Event: `NavigateBack`, `NavigateToFolder(customerId?, folderId)`, `NavigateToUpgrade`.

- [ ] **Step 3: VM (failing test first)** — `StyleFoldersViewModel(savedStateHandle, styleRepository, authRepository, entitlements)`. Derives `location` from nullable customerId. `observeFolders(userId, location)` → state. `limits = StyleCollectionLimits.forInspiration/forCustomer(entitlements.current().tier)`. `OnCreateFolderClick`: if `folders.size >= limits.maxFolders` (default folder counts — see note) → `_events.send(NavigateToUpgrade)`; else `showCreateSheet=true`. `OnConfirmCreate` → `createFolder`. Note: the default "My styles" folder is rendered client-side as a pinned card and counts as 1 toward `maxFolders`, so the create-blocked threshold is `folders.size + 1 >= maxFolders` (named folders capacity = maxFolders − 1).

Test `StyleFoldersViewModelTest.kt` (mirror `StyleGalleryViewModelTest` setup with `FakeStyleRepository` + a fake entitlements returning a tier):
```kotlin
@Test fun createBlockedAtCap_emitsUpgrade() = runTest {
    authRepository.signUpWithEmail("t@t.com","p","T")
    entitlements.tier = SubscriptionTier.PRO            // inspiration maxFolders=10 (incl default)
    styleRepository.folders = List(9) { fakeFolder("f$it") } // 9 named + default = 10
    val vm = createViewModel(customerId = null)
    vm.onAction(StyleFoldersAction.OnCreateFolderClick)
    assertIs<StyleFoldersEvent.NavigateToUpgrade>(vm.events.first())
}
@Test fun createUnderCap_opensSheet() = runTest {
    authRepository.signUpWithEmail("t@t.com","p","T")
    entitlements.tier = SubscriptionTier.PRO
    styleRepository.folders = List(3) { fakeFolder("f$it") }
    val vm = createViewModel(customerId = null)
    vm.onAction(StyleFoldersAction.OnCreateFolderClick)
    assertTrue(vm.state.value.showCreateSheet)
}
```
Implement to pass.

- [ ] **Step 4: Screen** — `StyleFoldersScreen`: a `LazyVerticalGrid(2)` of folder cards (cover `AsyncImage` with `LoadingDots`, name, count) — a pinned "My styles" default card first (navigates to `StyleGalleryRoute(customerId, folderId=null)`), then named folders (→ `folderId=folder.id`). A FAB / "+" → `OnCreateFolderClick`. Create/rename via a `ModalBottomSheet` with a `TextField`; delete via the destructive `AlertDialog` (per notification-patterns). Long-press a folder → rename/delete sheet. Title: "INSPIRATION" / "{Name}'s Closet" per location. Empty (no named folders) still shows the default card. Every Screen needs a `@Preview`.

- [ ] **Step 5: Nav wiring** — `MainScreen`: `composable<StyleFoldersRoute>{ StyleFoldersRoot(onNavigateBack, onNavigateToFolder = { cid, fid -> navController.navigate(StyleGalleryRoute(customerId=cid, folderId=fid)) }, onNavigateToUpgrade = { navController.navigate(UpgradeRoute) }) }`. The Inspiration entry (Dashboard) and the customer closet row now route to `StyleFoldersRoute` **when the tier has folders enabled**, else to the flat `StyleGalleryRoute` (Free). Gate at the call site using `entitlements` (or always route to folders and let the folders screen show only the default card for Free — simpler; pick the always-folders-grid route only for paid, flat for Free to honour "Free unchanged").

- [ ] **Step 6:** iOS compile + tests + detekt → green. `git commit -m "feat(style): folders grid + create/rename/delete + nav"`

---

## Task 7: Folder-scoped gallery add caps

**Files:** Modify `feature/style/presentation/gallery/StyleGalleryViewModel.kt`, `StyleFormViewModel.kt`.

- [ ] **Step 1:** The gallery/form VMs derive `location` including `folderId` from the route. Inject `entitlements`. Compute `limits` per location level. On add (multi-pick) / save, block when the folder's current style count + new ≥ the per-folder cap (or the Free `flatCap` for `folderId==null` on Free) → emit an upgrade/cap event + Snackbar; otherwise proceed. For Free flat gallery, the cap is `limits.flatCap`. Mirror the existing `CustomerCapReachedSheet` pattern for the UI.

- [ ] **Step 2:** Tests: add-at-cap blocked (no `createStyles` call, upgrade event); under cap proceeds. iOS + detekt. `git commit -m "feat(style): enforce per-folder + free flat style caps"`

---

## Task 8: Copy/move folder step + order picker flatten

**Files:** Modify `feature/style/presentation/gallery/*` (transfer), `feature/order/presentation/form/OrderFormViewModel.kt`.

- [ ] **Step 1: Transfer folder step** — when copy/move targets a paid location, after picking the customer/Inspiration, present its folders to choose the destination folder (reuse `observeFolders`). `copyStyle/moveStyle` `to = CustomerCloset(cid, folderId)` / `Inspiration(folderId)`. Destination per-folder cap applies (block + nudge). For a Free destination, drop into the flat default (folderId null).

- [ ] **Step 2: Order picker flatten** — `OrderFormViewModel` currently reads `observeStyles(uid, CustomerCloset(cid))` (flat default). To include foldered styles, also observe the customer's folders and their styles, and flatten into `availableStyles` (so the picker shows all the customer's styles regardless of folder). Keep the `StyleImageRef(LIBRARY, styleId)` shape unchanged; resolution by styleId still works since ids are unique. Test: a style inside a named folder appears in `availableStyles`.

- [ ] **Step 3:** iOS + tests + detekt. `git commit -m "feat(style): copy/move folder step + order picker flattens folders"`

---

## Task 9: Strings, rules, final verify, PR

- [ ] **Step 1: Strings** (`strings.xml`, `&apos;` for apostrophes): folder grid title/empty, `style_folder_create`/`_rename`/`_delete` titles, `style_folder_name_placeholder`, default folder name "My styles", cap-reached + upgrade copy for folders and images, content descriptions.

- [ ] **Step 2: Firestore rules** (`firestore.rules`) — add under the user/customer docs:
```
match /inspirationFolders/{folderId} {
  allow read, write: if isOwner(uid);
  match /styles/{styleId} { allow read, write: if isOwner(uid); }
}
```
and under `customers/{customerId}`:
```
match /styleFolders/{folderId} {
  allow read, write: if isOwner(uid);
  match /styles/{styleId} { allow read, write: if isOwner(uid); }
}
```
Comment that these (and the Storage paths under `users/{uid}/...`) must be **deployed** (`firebase deploy --only firestore:rules`) before testing — same ops gate as #164.

- [ ] **Step 3: Full gate** — `./gradlew :composeApp:testDebugUnitTest && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew detekt` → all green.

- [ ] **Step 4: Manual smoke (PR body)** — Pro: create folders to the cap, confirm the next is blocked with an upgrade nudge; add 5 images to an Inspiration folder, 6th blocked; copy a style into a named folder; rename/delete a folder; order picker still lists the customer's styles across folders. Atelier: confirm 20×10 / 5×5. Free: flat 10/5 caps + read-only-visible over cap. Deploy firestore rules first. Repeat on iOS.

- [ ] **Step 5: PR** — branch `feat/style-collections-ptsp-38-p3`; Cursor + `codex review` before merge.

---

## Deferred / follow-up
- Server-side cap hardening (a Cloud Function or rules-based count) — V1 is client-side.
- "Inspiration as a source in the order picker" (from #164) remains a separate ticket.
