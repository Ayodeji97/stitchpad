# Flat Customer Styles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make customer style images a flat gallery on every tier (Free 5 / Pro 15 / Atelier 25 images), while the Inspiration/Lookbook gallery keeps folders for Pro/Atelier.

**Architecture:** A single change to `StyleCollectionLimits.forCustomer(tier)` — return `foldersEnabled = false` with a tiered `flatCap` for all tiers. Every downstream site (gallery, folder screen, form, transfer) already branches on `foldersEnabled`, so customers route through the existing, tested flat path: the folder screen auto-redirects to the flat gallery, reads flatten across all legacy folders, and transfer-to-customer lands flat. No production code other than the factory changes. The rest of the work is updating tests that asserted the now-removed customer-folder behavior.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, kotlin.test + Turbine, JUnit5 (Android unit runner), detekt.

## Global Constraints

- **PR workflow:** Work on branch `feat/flat-customer-styles` (already created). No direct pushes to `main`. Open a PR with CI green.
- **Run the full unit suite** before declaring done: `./gradlew :composeApp:testDebugUnitTest`. (commonTest runs under the Android unit runner; there is no jvmTest.)
- **iOS compile gate (KMP):** A change is not "done" until iOS compiles — run `./gradlew :composeApp:compileKotlinIosSimulatorArm64`. (This change is pure-common Kotlin with no platform APIs, so risk is low, but the gate is mandatory.)
- **Detekt must pass:** `./gradlew detekt`. Capture the exit code directly — piping to `tail` hides gradle's exit status.
- **Every PR includes manual smoke-test steps** (Daniel is QA). Copy the smoke steps from the spec into the PR description.
- **No backslash escapes in `strings.xml`** — N/A here (no new strings).
- **Commit message trailer:** end every commit body with
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- **Spec:** `docs/superpowers/specs/2026-06-24-flat-customer-styles-design.md`

**Note on transient red:** Task 1 changes the production behavior and turns several presentation tests red (they assert customer-folder behavior that no longer exists). That is expected. Tasks 2–4 repair them; the suite returns fully green at Task 5. If running subagent-driven, do not treat the cross-file red after Task 1 as a regression — it is scheduled work.

---

### Task 1: Flatten the customer-style limits factory (domain)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimits.kt:28-32`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimitsTest.kt:23-30`

**Interfaces:**
- Produces: `StyleCollectionLimits.forCustomer(tier)` now returns `StyleCollectionLimits(foldersEnabled = false, maxFolders = 0, maxImagesPerFolder = 0, flatCap = N)` where N = 5 (FREE) / 15 (PRO) / 25 (ATELIER). The data class signature `(foldersEnabled, maxFolders, maxImagesPerFolder, flatCap)` is unchanged. `forInspiration(tier)` is unchanged.

- [ ] **Step 1: Rewrite the two customer-folder unit tests to expect flat caps**

In `StyleCollectionLimitsTest.kt`, replace the two tests at lines 23-30:

```kotlin
    @Test fun pro_customer_flat_cap15() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.PRO)
        assertFalse(l.foldersEnabled); assertEquals(15, l.flatCap)
    }
    @Test fun atelier_customer_flat_cap25() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.ATELIER)
        assertFalse(l.foldersEnabled); assertEquals(25, l.flatCap)
    }
```

(The existing `free_hasNoFolders_flatCaps` test already asserts `forCustomer(FREE).flatCap == 5` — leave it unchanged.)

- [ ] **Step 2: Run the domain test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimitsTest"`
Expected: FAIL — `pro_customer_flat_cap15` asserts `foldersEnabled == false` but production still returns `true` for PRO.

- [ ] **Step 3: Flatten the `forCustomer` factory**

In `StyleCollectionLimits.kt`, replace the `forCustomer` function body (lines 28-32):

```kotlin
        fun forCustomer(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
            SubscriptionTier.FREE -> StyleCollectionLimits(foldersEnabled = false, maxFolders = 0, maxImagesPerFolder = 0, flatCap = 5)
            SubscriptionTier.PRO -> StyleCollectionLimits(foldersEnabled = false, maxFolders = 0, maxImagesPerFolder = 0, flatCap = 15)
            SubscriptionTier.ATELIER -> StyleCollectionLimits(foldersEnabled = false, maxFolders = 0, maxImagesPerFolder = 0, flatCap = 25)
        }
```

- [ ] **Step 4: Run the domain test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimitsTest"`
Expected: PASS (all tests in the class).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimits.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/domain/StyleCollectionLimitsTest.kt
git commit -m "$(cat <<'EOF'
feat(style): flat customer styles on all tiers (Free 5 / Pro 15 / Atelier 25)

forCustomer now returns foldersEnabled=false with a tiered flat cap.
Downstream already branches on foldersEnabled, so customers route through
the existing flat path. Inspiration folders unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Customer folder screen redirects to flat gallery on all tiers (folders VM test)

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/folders/StyleFoldersViewModelTest.kt:230-244`

**Interfaces:**
- Consumes: `StyleFoldersViewModel.onStart()` emits `StyleFoldersEvent.RedirectToFlatGallery(customerId)` when `limits.foldersEnabled == false`. The test helper `createViewModel(customerId: String? = null, tier: SubscriptionTier = SubscriptionTier.PRO)` and `StyleFoldersEvent.RedirectToFlatGallery(val customerId: String?)` already exist.

**Why:** `createBlockedAtCap_customerFoldersPro_offersNoUpgrade` tested a customer folder cap that no longer exists (a Pro customer now has no folders, so the create-folder action is unreachable — the VM redirects). Replace it with a redirect assertion for a paid-tier customer, mirroring the existing Free redirect test at lines 126-134.

- [ ] **Step 1: Replace the obsolete customer-folder-cap test**

In `StyleFoldersViewModelTest.kt`, replace the whole test at lines 230-244 (`createBlockedAtCap_customerFoldersPro_offersNoUpgrade`) with:

```kotlin
    @Test
    fun paidCustomer_immediatelyRedirectsToFlatGallery() = runTest {
        // Customer styles are flat on every tier now (foldersEnabled=false), so a
        // Pro/Atelier customer closet must redirect straight to the flat gallery,
        // exactly like Free — never show a folder list.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(customerId = "cust-1", tier = SubscriptionTier.ATELIER)

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.RedirectToFlatGallery>(event)
        assertEquals("cust-1", event.customerId)
    }
```

Ensure these imports exist in the file (add any that are missing): `kotlin.test.assertEquals`, `kotlin.test.assertIs`, and `kotlinx.coroutines.flow.first`. (The Free redirect test already uses `assertIs` and `first`, so only `assertEquals` may be new — it is almost certainly already imported.)

- [ ] **Step 2: Run the folders VM test class to verify green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.folders.StyleFoldersViewModelTest"`
Expected: PASS (the new test plus all pre-existing inspiration tests, which are unaffected).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/folders/StyleFoldersViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(style): paid customer closet redirects to flat gallery

Replaces the obsolete customer-folder-cap test — customer folders no
longer exist on any tier, so the folder screen redirects to the flat
gallery for Pro/Atelier just as it already did for Free.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Update gallery VM tests for flat customer + Inspiration-target picker

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModelTest.kt`

**Interfaces:**
- Consumes: `StyleGalleryViewModel` flat path (`observeFlattened`) for customers; the paid folder-picker path (`onTargetSelected`/`onDestinationFolderSelected`) reachable via `TransferTarget.Inspiration` (`id = "inspiration"`, `location = StyleLocation.Inspiration()`). `forInspiration(PRO)` = folders on, `maxImagesPerFolder = 5`. The transfer flow offers the Inspiration target only when the source `location is StyleLocation.CustomerCloset` (so keep `customerId = "customer-1"` as the source in transfer tests). `fakeStyle(id, customerId = "customer-1", createdAt = …)` helper exists; default test `createViewModel` `customerId` is `"customer-1"`.

**Why:** Two lock tests assumed Pro customer per-folder cap 3; four transfer tests + two error-path tests reached the paid per-folder path through a *customer* destination/context, which is now flat. Pro customer locking is now flat at 15; the paid picker and per-folder error propagation survive via the Inspiration target.

- [ ] **Step 1: Rewrite the two customer lock tests to flat semantics**

Replace `proTier_closet_perFolder_locksOldestOverFolderCap` (lines 224-245) with:

```kotlin
    @Test
    fun proTier_closet_flat_locksOldestOverFlatCap() = runTest {
        // PRO forCustomer is now flat: flatCap = 15, foldersEnabled = false.
        // Seed 16 styles in the root closet → newest 15 active, oldest 1 locked.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        val styles = List(16) { i ->
            fakeStyle(id = "s$i", createdAt = (16 - i).toLong()) // s0 newest (16) … s15 oldest (1)
        }
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1")] = styles

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)

        assertEquals(16, vm.state.value.styles.size)
        assertEquals("s0", vm.state.value.styles.first().id)
        // flatCap=15 → only the single oldest (s15) is locked
        assertEquals(setOf("s15"), vm.state.value.lockedStyleIds)
        assertFalse(vm.state.value.isLoading)
    }
```

Replace `proTier_closet_perFolder_scrambledDates_sortsNewestFirst_andLocksOldest` (lines 247-273) with:

```kotlin
    @Test
    fun proTier_closet_flat_scrambledDates_sortsNewestFirst() = runTest {
        // PRO forCustomer is now flat: flatCap = 15, foldersEnabled = false.
        // 5 styles in scrambled createdAt order → all under cap (0 locked), sorted newest-first.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        val scrambled = listOf(
            fakeStyle(id = "s2", createdAt = 400L),
            fakeStyle(id = "s5", createdAt = 100L),
            fakeStyle(id = "s1", createdAt = 500L),
            fakeStyle(id = "s3", createdAt = 300L),
            fakeStyle(id = "s4", createdAt = 200L),
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1")] = scrambled

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)

        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), vm.state.value.styles.map { it.id })
        assertTrue(vm.state.value.lockedStyleIds.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }
```

- [ ] **Step 2: Point the two error-propagation tests at an Inspiration gallery**

The per-folder error path (`observePerFolder`) now runs only when folders are enabled — i.e. an Inspiration gallery on a paid tier. In both tests below, change the source from the default customer closet to inspiration by passing `customerId = null`.

In the test whose comment reads `// On paid tiers the VM uses observePerFolder which propagates errors directly.` (around line 180), change:

```kotlin
        val vm = createViewModel(tier = SubscriptionTier.PRO)
```
to:
```kotlin
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)
```

In `onErrorDismiss_clearsErrorMessage` (around line 757), make the same change:

```kotlin
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)
```

- [ ] **Step 3: Move the four transfer-picker tests to the Inspiration target**

Replace `transferToPaidTarget_showsDestinationFolders` (lines 623-661) with:

```kotlin
    @Test
    fun transferToInspirationTarget_showsDestinationFolders() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // Transfer FROM a customer closet TO the shared Inspiration library
        // (still folder-enabled on Pro). Customer destinations are flat now.
        customerRepository.customersList = listOf(fakeCustomer(id = "customer-1"))
        styleRepository.foldersByLocation[StyleLocation.Inspiration()] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.Inspiration()] = listOf(fakeStyle(id = "dest-default"))
        styleRepository.stylesByLocation[StyleLocation.Inspiration("f1")] = emptyList()

        // PRO tier — forInspiration(PRO): foldersEnabled=true, maxImagesPerFolder=5
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("inspiration"))

        val transfer = vm.state.value.transfer
        assertNotNull(transfer)
        val folders = transfer.destinationFolders
        assertNotNull(folders)
        assertEquals(2, folders.size)
        // First option = default (folderId null)
        assertNull(folders[0].folderId)
        assertEquals(1, folders[0].count)
        assertEquals(5, folders[0].cap)
        // Second option = named "Wedding"
        assertEquals("f1", folders[1].folderId)
        assertEquals("Wedding", folders[1].name)
        assertEquals(0, folders[1].count)
        assertNull(styleRepository.lastCopied)
    }
```

Replace `onDestinationFolderSelected_named_copiesToThatFolder` (lines 663-691) with:

```kotlin
    @Test
    fun onDestinationFolderSelected_named_copiesToInspirationFolder() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(fakeCustomer(id = "customer-1"))
        styleRepository.foldersByLocation[StyleLocation.Inspiration()] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.Inspiration()] = emptyList()
        styleRepository.stylesByLocation[StyleLocation.Inspiration("f1")] = emptyList()

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("inspiration"))

        vm.onAction(StyleGalleryAction.OnDestinationFolderSelected("f1"))

        assertEquals(
            Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.Inspiration("f1")),
            styleRepository.lastCopied,
        )
        val event = vm.events.first()
        assertIs<StyleGalleryEvent.StyleTransferred>(event)
        assertEquals("f1", event.destinationFolderId)
        assertNull(vm.state.value.transfer)
    }
```

Replace `onDestinationFolderSelected_fullFolder_setsCapSheet_noCopy` (lines 693-719) with:

```kotlin
    @Test
    fun onDestinationFolderSelected_fullFolder_setsCapSheet_noCopy() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(fakeCustomer(id = "customer-1"))
        styleRepository.foldersByLocation[StyleLocation.Inspiration()] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.Inspiration()] = emptyList()
        // PRO inspiration: maxImagesPerFolder = 5 — fill the named folder to cap.
        styleRepository.stylesByLocation[StyleLocation.Inspiration("f1")] =
            List(5) { fakeStyle(id = "dst-$it") }

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("inspiration"))

        vm.onAction(StyleGalleryAction.OnDestinationFolderSelected("f1"))

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertNull(styleRepository.lastCopied)
    }
```

Replace `transfer_paidTier_whenDestinationCountReadErrors_noCopy_errorSurfaced` (lines 794-821) with:

```kotlin
    @Test
    fun transfer_paidTier_whenDestinationCountReadErrors_noCopy_errorSurfaced() = runTest {
        // Paid path: the live re-read in performTransfer hard-fails on error.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(fakeCustomer(id = "customer-1"))
        styleRepository.foldersByLocation[StyleLocation.Inspiration()] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.Inspiration()] = emptyList()
        styleRepository.stylesByLocation[StyleLocation.Inspiration("f1")] = emptyList()
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("inspiration"))
        // Inject the read error BEFORE the folder is selected so performTransfer's re-read fails.
        styleRepository.observeError = DataError.Network.UNKNOWN

        vm.onAction(StyleGalleryAction.OnDestinationFolderSelected("f1"))

        assertNull(styleRepository.lastCopied)
        assertNotNull(vm.state.value.errorMessage)
    }
```

- [ ] **Step 4: Run the gallery VM test class to verify green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModelTest"`
Expected: PASS (all tests). If a transfer test fails on a `StyleLocation` equality assertion, confirm `StyleLocation.CustomerCloset("customer-1")` equals `CustomerCloset("customer-1", folderId = null)` (it does — `folderId` defaults to null) and that the source `location` used `folderId = null` (no `folderId` arg passed to `createViewModel`).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(style): gallery tests for flat customer + inspiration-target picker

Customer locking is now flat at 15 (Pro). The paid folder-picker and
per-folder error propagation survive via the Inspiration transfer target,
so those tests now transfer FROM a customer closet TO inspiration.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Update form VM cap tests for the flat customer cap

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt`

**Interfaces:**
- Consumes: `StyleFormViewModel` flat cap path. With `foldersEnabled = false`, the cap = `flatCap` and the current count = `countStylesAcrossFolders(userId, CustomerCloset(customerId))`. In the fake repo this resolves to `stylesList.size` when `stylesByLocation`/`foldersByLocation`/`folders` are unset (default empty). So for these tests, seed `styleRepository.stylesList = List(N)` and DROP the `folderId = "f1"` argument. Pro customer `flatCap = 15`. `STYLE_MULTI_PICK_CEILING` ≥ 2.

**Why:** Four tests assumed Pro customer `maxImagesPerFolder = 3` with a `folderId`. Pro customer is now flat with cap 15 and no folder.

- [ ] **Step 1: Update the over-cap test**

Replace `save_batchOverCap_setsCapSheet_stylesPro` (lines 452-474) with:

```kotlin
    @Test
    fun save_batchOverCap_setsCapSheet_customerFlat() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer is now flat: flatCap = 15. 15 existing (at cap) + picking 1 = 16 > 15 → blocked.
        styleRepository.stylesList = List(15) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertEquals(SubscriptionTier.PRO, capSheet.currentTier)
        assertNull(styleRepository.lastCreatedDescription)
        assertNull(styleRepository.lastBatchCreatedDescription)
        assertFalse(vm.state.value.isSaving)
    }
```

- [ ] **Step 2: Update the within-cap test**

Replace `save_batchWithinCap_creates` (lines 476-495) with:

```kotlin
    @Test
    fun save_batchWithinCap_creates() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer flat: flatCap = 15. 1 existing + picking 2 = 3 ≤ 15 → allowed.
        styleRepository.stylesList = List(1) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnPhotosPicked(List(2) { ByteArray(10) }))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertIs<StyleFormEvent.NavigateBack>(event)
        assertEquals(2, styleRepository.lastBatchCreatedCount)
        assertFalse(vm.state.value.isSaving)
    }
```

- [ ] **Step 3: Update the two cap-sheet action tests**

In `onDismissCapSheet` (lines 499-515) and `onUpgradeFromCap` (lines 517-534), change the seed and drop the `folderId` arg so the cap is actually hit on the flat path. In each, replace:

```kotlin
        styleRepository.stylesList = List(3) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
```
with:
```kotlin
        styleRepository.stylesList = List(15) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            tier = SubscriptionTier.PRO,
        )
```

- [ ] **Step 4: Update the multi-pick clamp test**

Replace `maxPhotoSelection_proFolderWithExisting_clampsToRemaining` (lines 538-550) with:

```kotlin
    @Test
    fun maxPhotoSelection_proCustomerFlat_clampsToRemaining() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer flat: flatCap = 15. 13 already in the closet → can pick 2 more.
        styleRepository.stylesList = List(13) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)

        assertEquals(2, vm.state.value.maxPhotoSelection)
    }
```

(Leave `maxPhotoSelection_emptyFreeCustomer_isFlatCap` at lines 552-560 unchanged — Free flatCap is still 5.)

- [ ] **Step 5: Run the form VM test class to verify green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModelTest"`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(style): form cap tests use flat customer cap (15) instead of folder cap

Pro customer is flat now; cap math counts the whole closet across folders.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Full verification, iOS compile, detekt, and PR

**Files:** none (verification + PR).

**Interfaces:** none.

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass. If a style test outside the four files above fails, search it for `forCustomer`/`maxImagesPerFolder = 3`/customer `folderId` assumptions and bring it onto the flat model or the Inspiration target the same way.

- [ ] **Step 2: Run detekt (capture exit code directly)**

Run: `./gradlew detekt; echo "detekt exit: $?"`
Expected: `detekt exit: 0`. Fix any style violations introduced (e.g. unused imports left from removed assertions).

- [ ] **Step 3: Verify iOS compiles (KMP gate)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (No platform APIs touched, but this gate is mandatory before "done".)

- [ ] **Step 4: Push and open the PR**

```bash
git push -u origin feat/flat-customer-styles
gh pr create --title "feat(style): flat customer styles on all tiers (drop folders)" --body "$(cat <<'EOF'
## What
Customer style images become a flat gallery on every tier — no folders.
Caps: Free 5 / Pro 15 / Atelier 25. Inspiration/Lookbook keeps folders for
Pro/Atelier (unchanged).

## How
Single production change: `StyleCollectionLimits.forCustomer(tier)` now
returns `foldersEnabled = false` with a tiered `flatCap`. Everything
downstream already branches on `foldersEnabled`:
- the customer folder screen auto-redirects to the flat gallery,
- the gallery flattens styles across any legacy folders (no migration),
- transfer-to-a-customer lands flat (the folder picker survives only for
  the Inspiration target).

Legacy customer styles filed in folders still surface (read-time flatten)
and remain editable/deletable (their original folderId is preserved).

## Manual smoke test (QA: Daniel)
1. Atelier account, a customer with styles across multiple old folders:
   open that customer's styles → opens the flat gallery (no folder list),
   all images from every old folder appear, newest first.
2. Add a new style to the flat customer gallery → saves and appears.
3. Edit and delete a legacy folder-filed style → both succeed.
4. Add styles to the cap (25 Atelier / 15 Pro / 5 Free) → cap sheet at the
   right number.
5. Free account → customer styles still cap at 5.
6. Inspiration gallery on Pro/Atelier → folders still work (regression).
7. Copy a style to a customer → lands in the flat closet (no folder picker);
   copy a style to Inspiration → folder picker still shown.

Spec: docs/superpowers/specs/2026-06-24-flat-customer-styles-design.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Confirm CI is green and request review**

Wait for the required checks (crash-check / build-android / build-ios / detekt) to pass, then route through the standard review rotation (Cursor Bugbot auto-runs; `codex review` via the pre-push hook).
