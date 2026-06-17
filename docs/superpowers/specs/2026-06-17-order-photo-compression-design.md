# Consistent order-photo compression (1600px @ q80)

**Date:** 2026-06-17
**Status:** Approved design, pending implementation plan
**Branch:** `feat/order-photo-compression` (stacked on `feat/inline-fabric-photo` / PR #181)

## Problem

Order photos (style + fabric) are already compressed, but **inconsistently by source**:

| Source | Today |
|---|---|
| Gallery (peekaboo default `ResizeOptions()`) | resized to **800×800**, `compressionQuality = 1.0` |
| Camera (`rememberImageCaptureLauncher`) | downscaled to **1920px**, **JPEG q85** |

So the same fabric photo is 800px from the gallery (low detail for texture) but 1920px from the
camera — unpredictable storage/bandwidth and an inconsistent result. (A separate, stronger
`BrandLogoCompressor` exists but is used **only** for the brand logo, not order photos.)

## Goal

Every order photo — order-form **style**, order-form **fabric**, and order-detail **inline
fabric**, from **either** camera or gallery — lands at a single consistent target: **~1600px,
JPEG q80**. One compression pass per photo (no double re-encoding).

## Decision: tune the existing single pass (no bolt-on compressor)

A dedicated post-pick compressor (BrandLogoCompressor-style) was rejected: it would re-encode an
already-JPEG'd image (double compression → quality loss for little gain). Instead we tune the two
places compression already happens.

### 1. Gallery (peekaboo `rememberImagePickerLauncher`)
Define one shared value:
```kotlin
val orderPhotoResizeOptions = ResizeOptions(
    width = 1600,
    height = 1600,
    compressionQuality = 0.8,
)
```
(`ResizeOptions` is `com.preat.peekaboo.image.picker.ResizeOptions`; its `resizeThresholdBytes`
field is left at peekaboo's **1 MB default** — images already under 1 MB pass through untouched,
so we never needlessly re-encode a small image.)

Pass `resizeOptions = orderPhotoResizeOptions` to the **3** order gallery pickers:
- `feature/order/presentation/form/OrderFormScreen.kt` — `styleGalleryPicker` (~L1290)
- `feature/order/presentation/form/OrderFormScreen.kt` — `fabricGalleryPicker` (~L1544)
- `feature/order/presentation/detail/OrderDetailScreen.kt` — `fabricGalleryPicker` (~L284)

Home for the shared value: a new tiny file `core/media/OrderPhotoCompression.kt` (commonMain) so
all three pickers DRY-reference one definition.

### 2. Camera (`rememberImageCaptureLauncher`)
This launcher is used **only** by order photos (form style camera, form fabric camera, inline
fabric camera — confirmed: 3 call sites, all order photos; logo capture uses the gallery picker,
not this). Change its two private constants in **both** platform files to the same target:
- `composeApp/src/androidMain/.../core/media/ImageCaptureLauncher.android.kt`:
  `MAX_DIM = 1920 → 1600`, `JPEG_QUALITY = 85 → 80`.
- `composeApp/src/iosMain/.../core/media/ImageCaptureLauncher.ios.kt`:
  `MAX_DIM = 1920.0 → 1600.0`, `JPEG_QUALITY = 0.85 → 0.80`.

(If a non-order caller of this launcher ever appears, it should be parameterized then — YAGNI now,
since it's order-only today.)

## The tradeoff (stated honestly)

This is a size-vs-detail dial, not a free win:
- **Gallery** detail goes **up** (800 → 1600px), so gallery uploads get **bigger** — more data
  for tailors on slow/metered networks, but better fabric texture.
- **Camera** goes **slightly down** (1920 → 1600, q85 → q80), so camera uploads get **smaller**.
- Net: every order photo is a predictable ~1600px / q80 (~300–500 KB typical) instead of the
  current 800-vs-1920 split.

## Scope

In scope: the 3 order gallery pickers + the shared camera launcher constants + the one new shared
`orderPhotoResizeOptions` value.

Out of scope (unchanged):
- The StyleForm *library* picker (`StyleFormScreen.kt`) — separate concern; could adopt the same
  value later for consistency, but not in this change.
- The brand-logo pickers (EditProfile / Workshop) — they have their own `BrandLogoCompressor`.
- The upload path (`uploadFabricPhotos` / `OfflinePhotoStore`) — no change; it stores the already
  compressed bytes.
- The 5 MB oversize guard — kept as a backstop.

## Verification

The resize/encode lives in peekaboo + the platform camera launchers — there is **no commonMain
unit test** for image bytes (same reason the camera launcher ships untested today). Verified by:
- **Build gates:** `./gradlew detekt`, `:composeApp:assembleDebug`,
  `:composeApp:compileKotlinIosSimulatorArm64` all green.
- **Manual smoke (Daniel is QA) — Android + iOS:**
  1. Order form → add a **style** photo from the **gallery** (use a large, high-res source) →
     saves/displays fine; the stored image is ~1600px (clearly larger/sharper than the old 800px).
  2. Order form → add a **fabric** photo from the **camera** → saves/displays fine; reasonable size.
  3. Order detail → inline **Add fabric photo** from gallery and from camera → both attach, look
     good, consistent with the form.
  4. Confirm fabric texture detail is acceptable at 1600/q80 (the whole point — eyeball a patterned
     fabric).
  5. Pick a very large photo (>5 MB raw) → still rejected by the oversize guard (unchanged).

## Branching

Based on `feat/inline-fabric-photo` (so all 3 pickers exist in one tree); shipped as its own
focused PR with base = `feat/inline-fabric-photo`. Merge order: PR #181 first, then this. After
#181 merges, rebase onto main and retarget the PR to main.
