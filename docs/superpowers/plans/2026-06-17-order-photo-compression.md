# Consistent order-photo compression (1600px @ q80) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every order photo (order-form style + fabric, order-detail inline fabric; from camera or gallery) compress to one consistent target — ~1600px, JPEG q80 — via the existing single compression pass.

**Architecture:** Two places already compress order photos. Tune both to the same target: pass an explicit shared `orderPhotoResizeOptions` to the 3 peekaboo gallery pickers, and drop the `rememberImageCaptureLauncher` `MAX_DIM`/`JPEG_QUALITY` constants (used only by order photos) to 1600/80 in both platform files. No new compressor, no double-encoding.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, peekaboo image picker 0.5.2.

**Spec:** `docs/superpowers/specs/2026-06-17-order-photo-compression-design.md`
**Branch:** `feat/order-photo-compression` (stacked on `feat/inline-fabric-photo` / PR #181)

**Verification note:** the resize/encode lives in peekaboo + the platform camera launchers — there is **no `commonMain` unit test** for image bytes (the camera launcher ships untested today for the same reason). This change is verified by build gates + the manual smoke test in the final task.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/media/OrderPhotoCompression.kt` — the single shared `orderPhotoResizeOptions` value.

**Modify:**
- `feature/order/presentation/form/OrderFormScreen.kt` — `resizeOptions` on the style + fabric gallery pickers.
- `feature/order/presentation/detail/OrderDetailScreen.kt` — `resizeOptions` on the inline fabric gallery picker.
- `core/media/ImageCaptureLauncher.android.kt` — camera consts → 1600 / 80.
- `core/media/ImageCaptureLauncher.ios.kt` — camera consts → 1600.0 / 0.80.

---

## Task 1: Shared `orderPhotoResizeOptions`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/media/OrderPhotoCompression.kt`

No unit test (a constant value of a library type). Verified by compile.

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.core.media

import com.preat.peekaboo.image.picker.ResizeOptions

/**
 * Single compression target for ALL order photos (style + fabric, order form + order-detail
 * inline), gallery side. Peekaboo resizes a picked image to fit within [width]x[height] and
 * re-encodes at [ResizeOptions.compressionQuality], but only when the source exceeds its
 * `resizeThresholdBytes` (left at peekaboo's 1 MB default) — so small images pass through
 * untouched and are never needlessly re-encoded. The camera path mirrors this target via the
 * MAX_DIM / JPEG_QUALITY constants in ImageCaptureLauncher.{android,ios}.kt.
 */
val orderPhotoResizeOptions: ResizeOptions = ResizeOptions(
    width = 1600,
    height = 1600,
    compressionQuality = 0.8,
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :composeApp:assembleDebug; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`. (Confirms `ResizeOptions` resolves in commonMain.)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/media/OrderPhotoCompression.kt
git commit -m "feat(media): shared orderPhotoResizeOptions target (1600px q80)"
```

---

## Task 2: Apply `resizeOptions` to the 3 order gallery pickers

**Files:**
- Modify: `feature/order/presentation/form/OrderFormScreen.kt` (style picker ~L1290, fabric picker ~L1544)
- Modify: `feature/order/presentation/detail/OrderDetailScreen.kt` (inline fabric picker ~L284)

No unit test. Verified by compile (Android + iOS).

- [ ] **Step 1: Add the import to both files**

In `OrderFormScreen.kt` and `OrderDetailScreen.kt`, add (alphabetically near the other `com.danzucker.stitchpad.core.media.*` import — e.g. `rememberImageCaptureLauncher` already imported there):

```kotlin
import com.danzucker.stitchpad.core.media.orderPhotoResizeOptions
```

- [ ] **Step 2: Style gallery picker (OrderFormScreen.kt ~L1290)**

Add `resizeOptions = orderPhotoResizeOptions,` after the `scope` argument:

```kotlin
    val styleGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        resizeOptions = orderPhotoResizeOptions,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                onAction(OrderFormAction.OnItemAddStylePhoto(item.id, it))
            }
        },
    )
```

- [ ] **Step 3: Fabric gallery picker (OrderFormScreen.kt ~L1544)**

```kotlin
    val fabricGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        resizeOptions = orderPhotoResizeOptions,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                onAction(OrderFormAction.OnItemAddFabricPhoto(item.id, it))
            }
        },
    )
```

- [ ] **Step 4: Inline fabric gallery picker (OrderDetailScreen.kt ~L284)**

```kotlin
    val fabricGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = fabricPickerScope,
        resizeOptions = orderPhotoResizeOptions,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let { onAction(OrderDetailAction.OnFabricPhotoPicked(it)) }
        },
    )
```

(`resizeOptions` is a named parameter on peekaboo's `rememberImagePickerLauncher` with a default of `ResizeOptions()`; supplying it overrides the 800px/q1.0 default.)

- [ ] **Step 5: Build + iOS compile + detekt**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 detekt; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`. Fix any import-ordering detekt issue you introduced.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(order): apply 1600px/q80 resize to order gallery pickers"
```

---

## Task 3: Lower the camera launcher target to 1600 / 80

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/media/ImageCaptureLauncher.android.kt` (~L111-112)
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/media/ImageCaptureLauncher.ios.kt` (~L100-101)

This launcher is used ONLY by order photos (form style camera, form fabric camera, inline fabric camera). No unit test (platform image code). Verified by compile.

- [ ] **Step 1: Android constants**

In `ImageCaptureLauncher.android.kt`, change:

```kotlin
private const val MAX_DIM = 1920
private const val JPEG_QUALITY = 85
```

to:

```kotlin
private const val MAX_DIM = 1600
private const val JPEG_QUALITY = 80
```

- [ ] **Step 2: iOS constants**

In `ImageCaptureLauncher.ios.kt`, change:

```kotlin
private const val MAX_DIM = 1920.0
private const val JPEG_QUALITY = 0.85
```

to:

```kotlin
private const val MAX_DIM = 1600.0
private const val JPEG_QUALITY = 0.80
```

- [ ] **Step 3: Build + iOS compile**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/media/ImageCaptureLauncher.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/media/ImageCaptureLauncher.ios.kt
git commit -m "feat(media): camera launcher target 1600px/q80 for order photos"
```

---

## Task 4: Verification + PR

- [ ] **Step 1: Full gates**

Run: `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: all green, `exit=0` (no tests changed — confirms no regression).

- [ ] **Step 2: Manual smoke (Daniel is QA) — Android + iOS**
  1. Order form → add a **style** photo from the **gallery** (large, high-res source) → saves/displays fine; the stored image is clearly sharper/larger than the old 800px cap.
  2. Order form → add a **fabric** photo from the **camera** → saves/displays fine; reasonable size.
  3. Order detail → inline **Add fabric photo** from gallery and camera → both attach, look good, consistent with the form.
  4. Eyeball a patterned fabric at 1600/q80 — confirm texture detail is acceptable (the goal).
  5. Pick a raw photo >5 MB → still rejected by the oversize guard (unchanged).

- [ ] **Step 3: Push + open PR (base = feat/inline-fabric-photo)**

```bash
git push -u origin feat/order-photo-compression
gh pr create --base feat/inline-fabric-photo --title "feat(order): consistent order-photo compression (1600px q80)" \
  --body "Implements docs/superpowers/specs/2026-06-17-order-photo-compression-design.md. Unifies order photos (form style + fabric, inline fabric; gallery + camera) to one ~1600px/q80 target by tuning the existing single pass — peekaboo resizeOptions on the 3 gallery pickers + the camera launcher constants. No bolt-on compressor (avoids double-encoding). Stacked on #181; base is feat/inline-fabric-photo. Merge #181 first, then rebase this to main. Includes the manual smoke test above."
```

(Base is `feat/inline-fabric-photo` because the inline fabric picker only exists there. After #181 merges to main, rebase this onto main and retarget the PR.)

Then run the required reviews: Cursor Bugbot (auto) + `codex review`.

---

## Self-Review notes
- **Spec coverage:** shared `orderPhotoResizeOptions` (Task 1), 3 gallery pickers (Task 2), camera consts both platforms (Task 3), build gates + manual smoke (Task 4), branch/PR base (Task 4). All spec sections map to a task.
- **Type consistency:** `orderPhotoResizeOptions` defined in Task 1 (`core.media`) is imported and used by name in Task 2's three pickers; camera consts use the platform-correct literal types (Android Int `1600`/`80`, iOS Double `1600.0`/`0.80`).
- **Out of scope (untouched):** StyleForm library picker, logo pickers, the upload path, the 5 MB oversize guard.
