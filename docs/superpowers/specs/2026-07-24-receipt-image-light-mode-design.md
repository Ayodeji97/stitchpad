# Receipt/Invoice Image — Light Mode Option

**Date:** 2026-07-24
**Branch:** `feat/receipt-image-light-mode`
**Status:** Design — approved, pending spec review

## Problem

Tailors share the receipt/invoice with their clients in two formats. The **PDF**
renders on a white background (readable, printable). The shared **image**
renders on a dark background (`#121110`). Some tailors report their clients
cannot read the dark image on their phones.

There is currently no way to get a light-background image.

## Root cause

The receipt is **not** a screenshot of a Compose surface, so the app's
light/dark theme has no effect. It is hand-drawn twice on native Canvas/Paint
with two hardcoded palettes:

- `renderDarkBitmap` (Android) / `renderDarkImage` (iOS) → hardcoded dark palette → the image.
- `renderLightPdf` (Android + iOS) → hardcoded white palette → the PDF.

File: `composeApp/src/{androidMain,iosMain}/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.{android,ios}.kt`

The two paths share only the `ReceiptData` payload and small helpers; layout and
colors are duplicated per format and per platform.

## Decisions (from brainstorming)

1. Add a **Settings preference** for receipt **image** style: Light or Dark.
2. The preference controls **only the shared image**. The **PDF stays light,
   always** (it is a printable document; a dark PDF wastes ink).
3. **Default is Light** — so every tailor, including those who never open the
   setting, gets a client-readable image out of the box. Tailors who prefer the
   dark card can switch back.
4. Renderer approach: **palette-parameterize the image renderer only**
   (Approach A). Do not touch the PDF renderer. Do not attempt to unify the
   image and PDF drawing routines (rejected: high regression risk on a
   customer-facing document across two platforms).

## Design

### 1. Domain (commonMain)

`core/domain/preferences/ReceiptImageStyle.kt`
```kotlin
enum class ReceiptImageStyle { LIGHT, DARK }
```

`core/domain/preferences/ReceiptImagePreferencesStore.kt` — mirrors
`ThemePreferencesStore` exactly:
```kotlin
interface ReceiptImagePreferencesStore {
    fun observeStyle(): Flow<ReceiptImageStyle>
    suspend fun getStyle(): ReceiptImageStyle
    suspend fun setStyle(style: ReceiptImageStyle)
}
```
Default when unset: `LIGHT`.

### 2. Preference persistence (expect/actual) — mirrors `ThemePreferences`

`core/data/preferences/ReceiptImagePreferences.kt` (expect) +
`.android.kt` (SharedPreferences `"receipt_image_prefs"`, key
`"receipt_image_style"`) + `.ios.kt` (NSUserDefaults). Local per-device
preference, exactly like theme. No Firestore, no cross-device sync (consistent
with how `ThemePreferences` already works).

### 3. Palette abstraction (commonMain)

`core/sharing/ReceiptPalette.kt` — holds only the values that differ between
light and dark. Colors that already read correctly on both backgrounds
(indigo header band `#2C3E7C`, white header text, green `#2D9E6B`, rush red
`#D93B3B`, and the data-driven `statusColorHex`) are **not** in the palette;
renderers keep drawing them as today.

```kotlin
data class ReceiptPalette(
    val backgroundHex: String,
    val bodyTextHex: String,
    val labelHex: String,
    val dividerHex: String,
    val accentHex: String,   // "saffron" totals accent
    val footerHex: String,
) {
    companion object {
        val Dark = ReceiptPalette(
            backgroundHex = "#121110",
            bodyTextHex   = "#E5E3DF",
            labelHex      = "#7D7970",
            dividerHex    = "#3A3731",
            accentHex     = "#E8A800",
            footerHex     = "#3A3731",
        )
        val Light = ReceiptPalette(
            backgroundHex = "#FFFFFF",
            bodyTextHex   = "#1E1C1A",
            labelHex      = "#7D7970",
            dividerHex    = "#E8E6E3",
            accentHex     = "#C48E00", // darker saffron for contrast on white
            footerHex     = "#A8A49D",
        )
    }
}

fun ReceiptImageStyle.palette(): ReceiptPalette =
    if (this == ReceiptImageStyle.LIGHT) ReceiptPalette.Light else ReceiptPalette.Dark
```

Palette values are drawn from the existing hardcoded literals: `Dark` from the
current `renderDarkBitmap`, `Light` from the current `renderLightPdf` body
colors. The image keeps its own bitmap layout (filled indigo header band); only
the six palette values change.

### 4. Renderer refactor

- Android `renderDarkBitmap(data): Bitmap` → `renderBitmap(data, palette): Bitmap`.
  Replace the six hardcoded dark hex literals with `palette.*` lookups
  (parsed via `Color.parseColor`, as today). No layout/sizing changes.
- iOS `renderDarkImage(data): UIImage` → `renderImage(data, palette): UIImage`,
  with the existing `darkColor(hex)` helper fed by `palette.*`.
- `renderLightPdf` (both platforms): **unchanged.**

### 5. Wiring

- `OrderReceiptSharer` expect/actual: change
  `shareReceiptAsImage(receiptData: ReceiptData)` →
  `shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle)`.
  `shareReceiptAsPdf` unchanged. The actual maps `style.palette()` into the
  renderer.
- `OrderDetailViewModel` (`shareReceiptAsImage` at ~line 228): read the current
  style from `ReceiptImagePreferencesStore` (injected) before calling the
  sharer, and pass it.
- Koin: provide `ReceiptImagePreferences` as `ReceiptImagePreferencesStore`
  (singleton), following the `ThemePreferences` registration. Inject into
  `OrderDetailViewModel`.

### 6. Settings UI

Add a "Receipt image style" row to Settings home (`feature/settings/presentation/home`):

- A `SettingsRow` showing the current value ("Light" / "Dark").
- Tapping opens a **bottom sheet** with the two choices (per project convention:
  Sheet = choices), selecting persists via `setStyle` and dismisses.
- New string resources for the row label, the two option labels, and an optional
  one-line helper ("Choose how shared receipt images look").
- `SettingsViewModel` observes `observeStyle()` into `SettingsState`; a new
  `SettingsAction` sets the style; state drives the sheet.

## Testing

- `ReceiptImagePreferences` round-trip + default-is-`LIGHT` when unset
  (commonTest with the existing preferences test approach).
- `ReceiptImageStyle.palette()` returns `Light`/`Dark` correctly; palette hex
  values match the constants (guards accidental edits).
- `SettingsViewModel`: observing style populates state; set action calls
  `setStyle` and updates state (fake store).
- `OrderDetailViewModel`: sharing image reads the preference and forwards the
  resolved style (fake store + fake sharer capturing the arg).

## Out of scope / non-goals

- No change to the PDF (stays light).
- No unification of image + PDF drawing routines.
- No per-share toggle (decided against; setting only).
- No cross-device sync of the preference (local, like theme).
- No new dark-PDF renderer.

## QA smoke test (manual, Daniel)

1. Fresh install (no preference set) → share order as image → image is **light**.
2. Settings → Receipt image style → **Dark** → share image → image is **dark**.
3. Switch back to **Light** → share image → light again.
4. Share as **PDF** in both preference states → PDF is **white** both times.
5. Repeat 1–4 on iOS.
6. Verify watermark (Free tier), logo, bank block, and status color still render
   correctly in the new **light** image.
