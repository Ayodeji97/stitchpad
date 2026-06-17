# Compress gallery image picks (stop 5 MB rejections)

**Date:** 2026-06-17
**Status:** Approved design, pending spec review → implementation plan

## Problem

Tailors pick a photo from their device gallery to attach to a style or an order
(style/fabric) and get a **"Photo is too large. Please pick one under 5 MB."** rejection.
Modern phone gallery photos are routinely 3–8 MB, so the limit bites constantly.

The root cause is an asymmetry:
- **Camera captures** already run through a downscale pipeline (`ImageCaptureLauncher`,
  both platforms): max edge **1920px**, **JPEG 85%** → typically **~0.5–2 MB**. They almost
  never hit the limit.
- **Gallery picks** (peekaboo `rememberImagePickerLauncher`) are passed through **raw and
  uncompressed**. They hit the 5 MB wall, and when they squeak under it, the full multi-MB
  original is uploaded and stored.

There is **no server-side resize/thumbnailing**, and thumbnails are rendered by Coil with no
`.size()` hint, so full-resolution images are decoded into memory even for small tiles.

## Goal

A tailor can pick **any** gallery photo and have it **just work** — no "too large" rejection —
while keeping the app fast and cheap. The chosen fix must not increase upload time, storage
cost, or memory pressure (the failure modes of simply raising the limit).

## Why not just raise the limit

Raising 5 MB → 10 MB makes the exact things we want to avoid worse:
- **Slower uploads** on ~1 Mbps networks (~80s vs ~40s for the biggest gallery originals).
- **~2× Firebase Storage cost** (Blaze, billed) for those images.
- **Higher OOM risk** on low-end Androids (a 10 MB JPEG decodes to ~30–40 MB in RAM; the
  gallery grid holds several at once).
- No server safety net.

Compressing gallery picks instead is a net win on every axis.

## Decisions

- **Compress gallery picks before the size check**, reusing the existing camera pipeline:
  **max edge 1920px, JPEG quality 85** (identical output to camera captures).
- **Keep the 5 MB guard as a backstop.** Post-downscale images land at ~1–2 MB, so it
  essentially never fires; it stays only to catch pathological inputs.
- **Ceiling stays 1920px** for now. Goal is "stop rejections," not "more detail." If tailors
  later report they can't see fabric weave/embroidery when zooming, bump to 2560px as a
  separate follow-up (different goal, different trade-offs).
- **Logos are out of scope** — they already have a dedicated 1024px compressor
  (`BrandLogoCompressor`).
- **Coil `.size()` thumbnail hints** are a related but separate display optimization — noted as
  a fast-follow, not part of this change.

## Approach

### 1. Extract a shared downscaler
The 1920px/JPEG-85 logic currently lives inline in the camera launchers:
- `composeApp/src/iosMain/.../core/media/ImageCaptureLauncher.ios.kt` (`MAX_DIM=1920.0`,
  `JPEG_QUALITY=0.85`, `toDownscaledJpegBytes()`).
- `composeApp/src/androidMain/.../core/media/ImageCaptureLauncher.android.kt`
  (`MAX_DIM=1920`, `JPEG_QUALITY=85`; decode w/ `inSampleSize`, **EXIF rotate**, scale, encode).

Pull this into a shared utility:

```kotlin
// commonMain: core/media/ImageDownscaler.kt
expect object ImageDownscaler {
    /** Decode, EXIF-orient, downscale to maxEdge, re-encode JPEG. Returns null on decode failure. */
    suspend fun downscale(bytes: ByteArray, maxEdgePx: Int = 1920, jpegQuality: Int = 85): ByteArray?
}
```

- `actual` on Android = the existing bitmap/EXIF/encode pipeline (moved out of the camera file).
- `actual` on iOS = the existing `UIImage` downscale + `UIImageJPEGRepresentation` path.
- The camera launchers are refactored to call `ImageDownscaler` so there is **one** implementation.
- **EXIF orientation must be preserved** — gallery JPEGs carry orientation metadata; decoding
  and re-encoding without applying it would rotate images. The Android camera path already
  handles this; reuse it.

### 2. Apply to gallery picks before the size check
Run `ImageDownscaler.downscale(...)` on each picked photo inside the ViewModel, **before** the
`MAX_*_SIZE` guard, on a background dispatcher (CPU work, ~100–500ms/image). Show the existing
loading/saving state while it runs.

Touch points:
- **Style form** — `StyleFormViewModel` `OnPhotosPicked` handler (around the guard at
  `StyleFormViewModel.kt:113–127`). Compress each photo in `selectedPhotos`.
- **Order form** — `OrderFormViewModel` style picks (`:185–191`) and fabric picks (`:253–259`),
  before `rejectOversizedPhoto`.
- **Inline Add-fabric-photo** (separate branch `feat/inline-fabric-photo`) will call the same
  utility when it lands — explicitly reused, not reimplemented.

### 3. Failure / edge handling
- **Decode failure** (`downscale` returns null — corrupt/unsupported input): fall back to the
  original bytes and let the existing 5 MB guard + error string handle it (no crash, no silent
  drop).
- **Non-JPEG inputs** (PNG/HEIC from gallery): the pipeline decodes then re-encodes to JPEG, so
  output is always JPEG (consistent with storage paths that already use `.jpg`).
- **Multi-select** (style form): compress each picked image; failures isolated per image.
- **Already-small images**: downscale is a no-op on dimensions below maxEdge but still
  re-encodes at quality 85 — acceptable; keeps output uniform.

## Affected files (summary)

| File | Change |
|------|--------|
| `core/media/ImageDownscaler.kt` (new, commonMain) | `expect object` API |
| `core/media/ImageDownscaler.android.kt` (new) | Android actual (moved from camera launcher) |
| `core/media/ImageDownscaler.ios.kt` (new) | iOS actual (moved from camera launcher) |
| `core/media/ImageCaptureLauncher.android.kt` | Refactor to call `ImageDownscaler` |
| `core/media/ImageCaptureLauncher.ios.kt` | Refactor to call `ImageDownscaler` |
| `feature/style/.../StyleFormViewModel.kt` | Compress picks before size guard |
| `feature/order/.../OrderFormViewModel.kt` | Compress style + fabric picks before guards |

## Tests

- `ImageDownscaler` (per platform, instrumented/unit as feasible): a large image downscales to
  ≤1920px max edge and output bytes < input bytes; an already-small image stays ≤ its size; a
  corrupt input returns null.
- `StyleFormViewModel`: a picked photo that would exceed 5 MB raw is **accepted** after
  compression (no `PHOTO_TOO_LARGE`); the stored/selected bytes are the compressed ones.
- `OrderFormViewModel`: style and fabric picks are compressed before the `rejectOversizedPhoto`
  guard; oversized originals now pass.
- Decode-failure path: original bytes retained, existing guard still applies.

## Verification gates

- Unit suite green (`:composeApp:testDebugUnitTest`).
- `detekt` green; `npm run lint` n/a (no functions change).
- **iOS compile** (`compileKotlinIosSimulatorArm64`) green — `expect/actual` + the iOS
  `UIImage` path must build (KMP iOS-only-break risk).
- Manual: pick a known >5 MB gallery photo on **both** platforms → it attaches with no "too
  large" error; verify orientation is correct (no sideways/upside-down images); verify upload
  completes and the thumbnail renders.

## Follow-ups (out of scope)

- Coil `.size()` hints on style/order thumbnails to cut decode memory further.
- Optional higher-detail ceiling (2560px) if tailors report losing fabric/stitch detail.
- Server-side resize Cloud Function as a defense-in-depth safety net.

## Relationship to other work

- `feat/inline-fabric-photo` (spec `2026-06-16-inline-fabric-photo-order-detail-design.md`) will
  reuse `ImageDownscaler` for its inline upload. This branch ships **first** so that work builds
  on a ready compressor.
