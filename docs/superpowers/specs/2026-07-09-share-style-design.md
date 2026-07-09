# Share a Style — Design

**Date:** 2026-07-09
**Status:** Approved (brainstorm), pending implementation plan
**Feature:** Share a style/inspiration photo (with its description as caption) to WhatsApp and other apps via the OS share sheet.

## Goal

Let a tailor share a style's photo out of StitchPad — most commonly to WhatsApp, e.g. to confirm a look with a customer or send a reference to a fabric vendor. The photo is sent as-is; the style's description travels along as pre-filled caption text.

## Product decisions (locked in brainstorm)

- **What is shared:** the raw photo **+ the style's description as caption text**. No composed/branded card, no image rendering — we reuse the existing photo bytes and attach the description as the share's text.
- **Trigger:** the **generic OS share sheet** (`ACTION_SEND` on Android, `UIActivityViewController` on iOS), consistent with existing receipt/measurement sharing. WhatsApp is one target among all installed apps. No WhatsApp-specific deep link.
- **Entry points:** both
  - the gallery **long-press action sheet** (alongside Copy / Move / Delete), and
  - the **style view screen** (`StyleFormScreen` top bar).
- **Caption:** the description, plus a `Shared via StitchPad` attribution line **for Free tier only** (Pro/Atelier get a clean caption — same tier rule as receipt attribution). Blank description on a paid tier → no caption (photo only).

## Existing infrastructure this mirrors

- `core/sharing/OrderReceiptSharer.kt` (expect/actual): writes bytes to a cache file (FileProvider on Android), prunes old files, fires the share sheet. The Android `shareFile(file, mimeType)` helper is the template for our sharer.
- `core/sharing/CoilImageBytes.kt` — `expect fun Image.toPngBytes(): ByteArray?`, already used to turn a decoded Coil image into bytes.
- `OrderDetailViewModel` — the reference pattern for obtaining bytes: injects Coil `ImageLoader`, builds an `ImageRequest` for a model, `imageLoader.execute(request).image.toPngBytes()`.
- `MeasurementShareFormatter` — precedent for a pure, testable formatter in `feature/.../presentation/share/`.
- `ReceiptAttribution` — precedent for tier-keyed attribution strings.
- Style model: `Style(photoUrl, photoStoragePath, localPhotoPath, description, ...)`. Screens already resolve the display model as `localPhotoPath ?: photoUrl`.

## Architecture — new units

### 1. `core/sharing/ImageSharer.kt` (expect/actual)

Feature-agnostic sharer for "an image plus optional caption."

```kotlin
expect class ImageSharer {
    suspend fun shareImage(bytes: ByteArray, caption: String?)
}
```

- **Android** (`ImageSharer(context)`): write `bytes` to a pruned cache PNG, get a `FileProvider` URI, build `ACTION_SEND` with `type = "image/png"`, `EXTRA_STREAM = uri`, and `EXTRA_TEXT = caption` when non-null, then launch the chooser. Reuses the cache/prune/FileProvider approach from `OrderReceiptSharer`. WhatsApp reads `EXTRA_TEXT` as the caption for a single-image send.
- **iOS** (`ImageSharer()`): present a `UIActivityViewController` with activity items `[UIImage(bytes), caption]` (caption omitted when null).

Lives in `core/sharing` (like `OrderReceiptSharer`) because it is genuinely reusable, not style-specific.

### 2. `feature/style/presentation/share/StyleShareFormatter.kt` (pure, commonMain)

```kotlin
object StyleShareFormatter {
    fun caption(style: Style, tier: SubscriptionTier): String?
}
```

- Returns `style.description` (trimmed) when present.
- Appends a `Shared via StitchPad` attribution line **only for the Free tier** (exact wording finalized in the plan; reuse the receipt attribution convention).
- Blank description + paid tier → `null`.
- Blank description + Free tier → attribution line only.

Mirrors `MeasurementShareFormatter`; fully unit-testable with no platform deps.

### 3. `StyleImageBytesLoader` (fun interface, commonMain) + Coil impl

```kotlin
fun interface StyleImageBytesLoader {
    suspend fun load(model: String): ByteArray?
}
```

The key testability seam: hides Coil's `ImageLoader` (noted as unconstructible in `commonTest` by `StylePickerLogic`) behind an interface that ViewModels depend on and tests fake.

Real impl (platform DI, e.g. `CoilStyleImageBytesLoader(imageLoader, platformContext)`) performs the `imageLoader.execute(ImageRequest(...)).image.toPngBytes()` sequence from `OrderDetailViewModel`. Returns `null` on any failure (offline with no cache, decode failure, bad URL).

### 4. `ShareStyle` use case (injectable, commonMain)

The shared orchestration both entry points call — no duplication across the two ViewModels.

```kotlin
class ShareStyle(
    private val loader: StyleImageBytesLoader,
    private val sharer: ImageSharer,
    private val entitlements: EntitlementsProvider,
) {
    suspend operator fun invoke(style: Style): EmptyResult<DataError> {
        val model = style.localPhotoPath ?: style.photoUrl
        val bytes = loader.load(model) ?: return Result.Error(DataError.Local.UNKNOWN)
        val tier = entitlements.awaitHydrated().tier
        sharer.shareImage(bytes, StyleShareFormatter.caption(style, tier))
        return Result.Success(Unit)
    }
}
```

(Exact `DataError` variant confirmed against the enum during implementation.)

## Entry points

### A. Gallery long-press sheet — `StyleGalleryScreen` + `StyleGalleryViewModel`

- Add `data class OnShareClick(val style: Style)` to `StyleGalleryAction`.
- Add a **Share** row to the existing action sheet (leading `Icons.Default.Share`, indigo — non-destructive), above/among Copy / Move / Delete.
- VM handler: launch `ShareStyle(action.style)`, dismiss the sheet, and `onFailure` surface the error via the existing error channel.
- `StyleGalleryViewModel` already injects `entitlements`; add `ShareStyle`.
- Locked styles keep today's behavior (long-press → cap sheet), so Share is simply unreachable for locked cards — no special-casing needed.

### B. Style view screen — `StyleFormScreen` + `StyleFormViewModel`

- Add a **share icon** to the `TopAppBar` actions, shown only when viewing an existing style (`styleId != null`); visible in both edit and read-only mode.
- Add `StyleFormAction.OnShareClick`; VM calls `ShareStyle(currentStyle)`.
- `StyleFormViewModel` already injects `entitlements`; add `ShareStyle`.

Both paths: success needs no confirmation (the OS sheet appearing is the feedback); failure shows one new string, e.g. *"Couldn't prepare this photo to share."*

## DI wiring

- `PlatformModule.android.kt`: `single { ImageSharer(androidContext()) }`; `single<StyleImageBytesLoader> { CoilStyleImageBytesLoader(get(), get()) }` (ImageLoader + PlatformContext already provided for receipts).
- `PlatformModule.ios.kt`: `single { ImageSharer() }`; the same loader binding.
- `StyleModule.kt` (commonMain): `factoryOf(::ShareStyle)`; add `ShareStyle` to `StyleGalleryViewModel` and `StyleFormViewModel` constructors.

## Byte-source strategy

Model = `style.localPhotoPath ?: style.photoUrl`:

- Works **offline / pre-upload** — a just-captured, not-yet-uploaded style shares its local file.
- Otherwise falls back to the remote URL, served from Coil's disk cache when the image is already on screen (no re-download).

Tradeoff: `toPngBytes()` re-encodes a JPEG to PNG, inflating the shared file somewhat. Acceptable for V1 (WhatsApp recompresses); a JPEG encoder is an easy later refinement, explicitly out of scope now.

## Error handling

- `StyleImageBytesLoader.load` returns `null` on any failure; `ShareStyle` maps that to `Result.Error(DataError.Local...)`.
- Each ViewModel surfaces the error through its existing error state/event with the new "couldn't prepare photo" string.
- No crash surfaces to the user; the share sheet either opens or an error snackbar shows.

## Testing

- **`StyleShareFormatterTest`** (commonTest, pure): description-only; blank description; Free vs Pro/Atelier attribution; blank + Free → attribution only.
- **`ShareStyleTest`** (commonTest): fake `StyleImageBytesLoader`, fake `ImageSharer`, fake `EntitlementsProvider` → assert model resolution (localPhotoPath vs photoUrl), caption passed to the sharer, and `Error` returned when bytes are `null`.
- **ViewModel tests** (both): `OnShareClick` invokes `ShareStyle`; error path surfaces the error state, using the fakes.

## Out of scope (YAGNI)

- WhatsApp-specific deep link / direct-to-WhatsApp button.
- Composed/branded share card with rendered text + watermark.
- Sharing multiple styles at once.
- JPEG (vs PNG) re-encoding to shrink the shared file.

## New strings

- Share action label (gallery sheet + form top-bar content description).
- Share failure message: "Couldn't prepare this photo to share."
- Free-tier attribution line (finalize wording in plan).
