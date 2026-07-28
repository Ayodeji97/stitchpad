# Receipt Image Light Mode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tailors choose a Light or Dark shared receipt/invoice **image** via a Settings preference (default Light), so their clients can read it; the PDF stays light.

**Architecture:** The receipt image is hand-drawn on a native Canvas with a hardcoded dark palette (Android `renderDarkBitmap`, iOS `renderDarkImage`); the PDF is a separate hardcoded-light path. We introduce a `ReceiptPalette` (Light/Dark) in commonMain, parameterize **only** the image renderers by palette, add a local per-device `ReceiptImagePreferences` store (mirroring the existing `ThemePreferences`), read it in `OrderDetailViewModel` when sharing an image, and expose a tap-to-toggle row in Settings. The PDF renderer is untouched.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin DI, native `android.graphics` / UIKit Core Graphics, SharedPreferences (Android) / NSUserDefaults (iOS), kotlin.test + Turbine.

## Global Constraints

- Package root: `com.danzucker.stitchpad`. Follow MVI + Root/Screen + Koin patterns in CLAUDE.md.
- Never hardcode user-facing strings — use compose.resources (`Res.string.*`) added to `composeApp/src/commonMain/composeResources/values/strings.xml`.
- No backslash escapes in strings.xml (`\'` renders literally on CMP iOS) — use `&apos;` or `’`.
- All state in ViewModel, never `remember` (except Compose-internal state).
- Koin: `single { X() } bind Store::class` per platform module; `viewModelOf(::VM)` resolves every constructor param via `get()` — the new store MUST be registered before the VM references it.
- Default `ReceiptImageStyle` when unset = `LIGHT`.
- PDF renderer (`renderLightPdf`) MUST NOT change.
- iOS colors are created inline via the `darkColor(hex)` helper (scattered call sites), not a single color block — replace each in-method literal.
- Verify iOS compiles before "done": `./gradlew :composeApp:compileKotlinIosSimulatorArm64` (JVM-green ≠ iOS-green in this repo).

### Palette values (single source of truth for this plan)

Refines the spec palette: drops `labelHex` (identical `#7D7970` in both, so it stays hardcoded), adds `footerHex` and `watermarkInkHex` so the FREE-tier watermark and footer stay visible on a white background.

| Field | Dark | Light |
|-------|------|-------|
| `backgroundHex` | `#121110` | `#FFFFFF` |
| `bodyTextHex` | `#E5E3DF` | `#1E1C1A` |
| `dividerHex` | `#3A3731` | `#E8E6E3` |
| `accentHex` (saffron/totals) | `#E8A800` | `#C48E00` |
| `footerHex` | `#3A3731` | `#A8A49D` |
| `watermarkInkHex` | `#A8A49D` | `#7D7970` |

Unchanged in both (stay hardcoded in renderers): header band `#2C3E7C`, header text white, green `#2D9E6B`, rush red `#D93B3B`, label `#7D7970`, and the data-driven `statusColorHex`.

---

### Task 1: Domain — `ReceiptImageStyle`, palette, and store interface

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/preferences/ReceiptImageStyle.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/preferences/ReceiptImagePreferencesStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptPalette.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptPaletteTest.kt`

**Interfaces:**
- Produces:
  - `enum class ReceiptImageStyle { LIGHT, DARK }`
  - `interface ReceiptImagePreferencesStore { fun observeStyle(): Flow<ReceiptImageStyle>; suspend fun getStyle(): ReceiptImageStyle; suspend fun setStyle(style: ReceiptImageStyle) }`
  - `data class ReceiptPalette(backgroundHex, bodyTextHex, dividerHex, accentHex, footerHex, watermarkInkHex: String)` with `ReceiptPalette.Light` / `ReceiptPalette.Dark` companion vals
  - `fun ReceiptImageStyle.palette(): ReceiptPalette`

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptPaletteTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptPaletteTest {

    @Test
    fun light_style_maps_to_light_palette_with_white_background() {
        val palette = ReceiptImageStyle.LIGHT.palette()
        assertEquals(ReceiptPalette.Light, palette)
        assertEquals("#FFFFFF", palette.backgroundHex)
        assertEquals("#1E1C1A", palette.bodyTextHex)
        assertEquals("#C48E00", palette.accentHex)
    }

    @Test
    fun dark_style_maps_to_dark_palette_with_ink_background() {
        val palette = ReceiptImageStyle.DARK.palette()
        assertEquals(ReceiptPalette.Dark, palette)
        assertEquals("#121110", palette.backgroundHex)
        assertEquals("#E5E3DF", palette.bodyTextHex)
        assertEquals("#E8A800", palette.accentHex)
    }

    @Test
    fun light_watermark_ink_is_darker_than_dark_for_white_background_visibility() {
        assertEquals("#7D7970", ReceiptPalette.Light.watermarkInkHex)
        assertEquals("#A8A49D", ReceiptPalette.Dark.watermarkInkHex)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.sharing.ReceiptPaletteTest"`
Expected: FAIL — `ReceiptImageStyle` / `ReceiptPalette` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `ReceiptImageStyle.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.preferences

enum class ReceiptImageStyle { LIGHT, DARK }
```

Create `ReceiptImagePreferencesStore.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.preferences

import kotlinx.coroutines.flow.Flow

interface ReceiptImagePreferencesStore {
    fun observeStyle(): Flow<ReceiptImageStyle>
    suspend fun getStyle(): ReceiptImageStyle
    suspend fun setStyle(style: ReceiptImageStyle)
}
```

Create `ReceiptPalette.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle

/**
 * Colors that differ between the Light and Dark shared receipt image. Values
 * that already read correctly on both backgrounds (indigo header #2C3E7C,
 * white header text, green #2D9E6B, rush red #D93B3B, label #7D7970, and the
 * data-driven statusColorHex) stay hardcoded in the renderers.
 *
 * Dark values come from the original renderDarkBitmap/renderDarkImage; Light
 * values from renderLightPdf, with a darker saffron accent (#C48E00) and a
 * darker watermark ink (#7D7970) chosen for contrast on white.
 */
data class ReceiptPalette(
    val backgroundHex: String,
    val bodyTextHex: String,
    val dividerHex: String,
    val accentHex: String,
    val footerHex: String,
    val watermarkInkHex: String,
) {
    companion object {
        val Dark = ReceiptPalette(
            backgroundHex = "#121110",
            bodyTextHex = "#E5E3DF",
            dividerHex = "#3A3731",
            accentHex = "#E8A800",
            footerHex = "#3A3731",
            watermarkInkHex = "#A8A49D",
        )
        val Light = ReceiptPalette(
            backgroundHex = "#FFFFFF",
            bodyTextHex = "#1E1C1A",
            dividerHex = "#E8E6E3",
            accentHex = "#C48E00",
            footerHex = "#A8A49D",
            watermarkInkHex = "#7D7970",
        )
    }
}

fun ReceiptImageStyle.palette(): ReceiptPalette = when (this) {
    ReceiptImageStyle.LIGHT -> ReceiptPalette.Light
    ReceiptImageStyle.DARK -> ReceiptPalette.Dark
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.sharing.ReceiptPaletteTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/preferences/ReceiptImageStyle.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/preferences/ReceiptImagePreferencesStore.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptPalette.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptPaletteTest.kt
git commit -m "feat(receipt): add ReceiptImageStyle + ReceiptPalette (light/dark)"
```

---

### Task 2: Platform preference store (`ReceiptImagePreferences`) + Koin

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.kt` (expect)
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.ios.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt` (near line 52)
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt` (near line 75)

**Interfaces:**
- Consumes: `ReceiptImagePreferencesStore`, `ReceiptImageStyle` (Task 1)
- Produces: `expect class ReceiptImagePreferences : ReceiptImagePreferencesStore`, registered in Koin as `ReceiptImagePreferencesStore`.

This mirrors `ThemePreferences` exactly. No unit test (the real platform stores are untested in this repo, same as `ThemePreferences`); the store is exercised via the SettingsViewModel test in Task 5 through a fake. Verification here is compilation on both platforms.

- [ ] **Step 1: Create the expect declaration**

`ReceiptImagePreferences.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.preferences

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore

expect class ReceiptImagePreferences : ReceiptImagePreferencesStore
```

- [ ] **Step 2: Create the Android actual**

`ReceiptImagePreferences.android.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class ReceiptImagePreferences(context: Context) : ReceiptImagePreferencesStore {
    private val prefs = context.getSharedPreferences("receipt_image_prefs", Context.MODE_PRIVATE)

    override fun observeStyle(): Flow<ReceiptImageStyle> = callbackFlow {
        trySend(readStyle())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STYLE) trySend(readStyle())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun getStyle(): ReceiptImageStyle = readStyle()

    override suspend fun setStyle(style: ReceiptImageStyle) {
        prefs.edit { putString(KEY_STYLE, style.name) }
    }

    private fun readStyle(): ReceiptImageStyle =
        runCatching { ReceiptImageStyle.valueOf(prefs.getString(KEY_STYLE, null) ?: ReceiptImageStyle.LIGHT.name) }
            .getOrDefault(ReceiptImageStyle.LIGHT)

    companion object {
        private const val KEY_STYLE = "receipt_image_style"
    }
}
```

- [ ] **Step 3: Create the iOS actual**

`ReceiptImagePreferences.ios.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.preferences

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

actual class ReceiptImagePreferences : ReceiptImagePreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val flow = MutableStateFlow(readStyle())

    override fun observeStyle(): Flow<ReceiptImageStyle> = flow.asStateFlow()

    override suspend fun getStyle(): ReceiptImageStyle = readStyle()

    override suspend fun setStyle(style: ReceiptImageStyle) {
        defaults.setObject(style.name, forKey = KEY_STYLE)
        flow.value = style
    }

    private fun readStyle(): ReceiptImageStyle =
        runCatching { ReceiptImageStyle.valueOf(defaults.stringForKey(KEY_STYLE) ?: ReceiptImageStyle.LIGHT.name) }
            .getOrDefault(ReceiptImageStyle.LIGHT)

    companion object {
        private const val KEY_STYLE = "receipt_image_style"
    }
}
```

- [ ] **Step 4: Register in Koin (Android)**

In `PlatformModule.android.kt`, add imports and a `single` next to the `ThemePreferences` line (~line 52):

```kotlin
import com.danzucker.stitchpad.core.data.preferences.ReceiptImagePreferences
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
```

```kotlin
single { ReceiptImagePreferences(androidContext()) } bind ReceiptImagePreferencesStore::class
```

- [ ] **Step 5: Register in Koin (iOS)**

In `PlatformModule.ios.kt`, add imports and a `single` next to the `ThemePreferences` line (~line 75):

```kotlin
import com.danzucker.stitchpad.core.data.preferences.ReceiptImagePreferences
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
```

```kotlin
single { ReceiptImagePreferences() } bind ReceiptImagePreferencesStore::class
```

- [ ] **Step 6: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/data/preferences/ReceiptImagePreferences.ios.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt
git commit -m "feat(receipt): add local ReceiptImagePreferences store (default Light)"
```

---

### Task 3: Palette-parameterize the image renderers (Android + iOS)

Delivers the **default-Light** image end to end. The `shareReceiptAsImage` signature gains a `style` param; the single call site (`OrderDetailViewModel`) passes `ReceiptImageStyle.LIGHT` for now (Task 4 swaps in the real preference). This keeps every task compiling green.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt:228`

**Interfaces:**
- Consumes: `ReceiptImageStyle`, `ReceiptPalette`, `ReceiptImageStyle.palette()` (Task 1)
- Produces: `suspend fun shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle)`

- [ ] **Step 1: Update the `expect` signature**

`OrderReceiptSharer.kt`:

```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle

expect class OrderReceiptSharer {
    suspend fun shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle)
    suspend fun shareReceiptAsPdf(receiptData: ReceiptData)
}
```

- [ ] **Step 2: Android — thread the palette through the image renderer**

In `OrderReceiptSharer.android.kt`:

1. Add imports:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
2. Change `shareReceiptAsImage` (lines ~23-29):
```kotlin
    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle) {
        val file = withContext(Dispatchers.Default) {
            val bitmap = renderBitmap(receiptData, style.palette())
            saveBitmapToCache(bitmap, "img")
        }
        shareFile(file, "image/png")
    }
```
3. Rename the method and replace the five differing color literals in its color block (lines ~40-48). Change the signature line:
```kotlin
    private fun renderBitmap(data: ReceiptData, palette: ReceiptPalette): Bitmap {
```
and the color block:
```kotlin
        // Colors
        val bgColor = Color.parseColor(palette.backgroundHex)
        val headerBg = Color.parseColor("#2C3E7C") // indigo brand band (was saffron pre-rebrand)
        val headerText = Color.WHITE
        val bodyText = Color.parseColor(palette.bodyTextHex)
        val labelColor = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor(palette.dividerHex)
        val saffron = Color.parseColor(palette.accentHex)
        val green = Color.parseColor("#2D9E6B")
        val rushRed = Color.parseColor("#D93B3B")
```
4. The `footerPaint` uses `dividerColor` today. Change it to use the palette footer so the light footer reads on white:
```kotlin
        val footerPaint = makePaint(Color.parseColor(palette.footerHex), 14f).apply { textAlign = Paint.Align.CENTER }
```
(Keep the existing text size/flags; only the color arg changes.)
5. Find the `drawWatermark(...)` call inside `renderBitmap` and replace its hardcoded `inkHex`/`inkColor` argument with `palette.watermarkInkHex` (parse via `Color.parseColor(palette.watermarkInkHex)` if the Android watermark takes an int color; match the existing param type). Grep within the method: `grep -n "drawWatermark\|watermark" OrderReceiptSharer.android.kt`.

- [ ] **Step 3: iOS — thread the palette through the image renderer**

In `OrderReceiptSharer.ios.kt`:

1. Add import:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
2. Change `shareReceiptAsImage` (line ~47) signature and the render call:
```kotlin
    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData, style: ReceiptImageStyle) {
        val fileUrl = withContext(Dispatchers.Default) {
            val image = renderImage(receiptData, style.palette())
            ...
```
3. Rename `renderDarkImage(data: ReceiptData): UIImage` → `renderImage(data: ReceiptData, palette: ReceiptPalette): UIImage`.
4. iOS uses **inline** `darkColor("#hex")` calls throughout this method. Grep every literal inside `renderImage` and replace per this mapping (the `darkColor` helper itself is unchanged):

   | Current inline literal | Replace with |
   |---|---|
   | `darkColor("#121110")` (background fill) | `darkColor(palette.backgroundHex)` |
   | `darkColor("#E5E3DF")` (body text) | `darkColor(palette.bodyTextHex)` |
   | `darkColor("#3A3731")` (dividers/lines) | `darkColor(palette.dividerHex)` |
   | `darkColor("#E8A800")` (saffron/totals accent) | `darkColor(palette.accentHex)` |
   | footer color literal (grep near "footer"/attribution draw) | `darkColor(palette.footerHex)` |
   | `drawWatermark(..., inkHex = "#A8A49D")` | `inkHex = palette.watermarkInkHex` |

   Leave unchanged: `darkColor("#2C3E7C")` (header band), `darkColor("#2D9E6B")` (green), `darkColor("#D93B3B")` (rush), `darkColor("#7D7970")` (label), white, and `darkColor(data.statusColorHex)`.

   Command to enumerate: `grep -n 'darkColor("#\|inkHex' composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`. Only edit occurrences **inside `renderImage`** — do NOT touch `renderLightPdf`.

- [ ] **Step 4: Update the single call site to compile (temporary LIGHT literal)**

In `OrderDetailViewModel.kt`, add the import and update line ~228:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
```kotlin
            OrderDetailAction.OnShareAsImageClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt(format = "image") { receiptSharer.shareReceiptAsImage(it, ReceiptImageStyle.LIGHT) }
                _state.update { it.copy(documentTypeChoice = null) }
            }
```

- [ ] **Step 5: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (No unit tests — native rendering is verified by manual QA at the end.)

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt
git commit -m "feat(receipt): palette-parameterize image renderer; image defaults to light"
```

---

### Task 4: Read the preference in `OrderDetailViewModel`

Swap the temporary `ReceiptImageStyle.LIGHT` literal for the persisted preference.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt` (constructor ~line 78-90 + call site ~228)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/OrderModule.kt` (only if `OrderDetailViewModel` is NOT `viewModelOf(::...)` — it is, so no change needed; confirm)

**Interfaces:**
- Consumes: `ReceiptImagePreferencesStore` (Task 1), registered in Koin (Task 2), `shareReceiptAsImage(data, style)` (Task 3)

- [ ] **Step 1: Inject the store into the constructor**

Add the import and a constructor parameter (place it right after `receiptSharer`):
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
```
```kotlin
    private val receiptSharer: OrderReceiptSharer,
    private val receiptImagePreferencesStore: ReceiptImagePreferencesStore,
```

Because `OrderModule.kt` uses `viewModelOf(::OrderDetailViewModel)` (constructor reference), Koin resolves the new param via `get()` automatically — the store is registered in Task 2. No `OrderModule.kt` edit required.

Also **remove** the now-unused `import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle` that Task 3 added (Step 2 below no longer references `ReceiptImageStyle.LIGHT` directly), or detekt will flag an unused import.

- [ ] **Step 2: Use the preference at the image-share call site**

Update line ~228 (the lambda is `suspend`, so calling `getStyle()` here is fine):
```kotlin
            OrderDetailAction.OnShareAsImageClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt(format = "image") {
                    receiptSharer.shareReceiptAsImage(it, receiptImagePreferencesStore.getStyle())
                }
                _state.update { it.copy(documentTypeChoice = null) }
            }
```

- [ ] **Step 3: Verify compile + existing order tests still pass**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.order.*"`
Expected: BUILD SUCCESSFUL; existing order tests PASS. If any `OrderDetailViewModel` test constructs the VM directly, add `receiptImagePreferencesStore = <fake or a simple inline store returning LIGHT>` to that construction (search: `grep -rn "OrderDetailViewModel(" composeApp/src/commonTest`).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt
git commit -m "feat(receipt): share image using persisted ReceiptImageStyle preference"
```

---

### Task 5: Settings UI — tap-to-toggle "Receipt image style" row

Adds a row to the Settings **Preferences** section (right after Appearance), mirroring the measurement-unit tap-to-cycle pattern. Toggles Light ↔ Dark.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsDigestToggleTest.kt` (add fake + one test; extend shared VM builder)

**Interfaces:**
- Consumes: `ReceiptImageStyle`, `ReceiptImagePreferencesStore` (Task 1)

- [ ] **Step 1: Write the failing test**

In `SettingsDigestToggleTest.kt`:

1. Add imports:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
2. Add a recording fake near the other fakes (bottom of file):
```kotlin
private class FakeReceiptImagePreferencesStore(
    initial: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
) : ReceiptImagePreferencesStore {
    private val _flow = MutableStateFlow(initial)
    var lastSet: ReceiptImageStyle? = null
        private set
    override fun observeStyle(): Flow<ReceiptImageStyle> = _flow
    override suspend fun getStyle(): ReceiptImageStyle = _flow.value
    override suspend fun setStyle(style: ReceiptImageStyle) {
        lastSet = style
        _flow.value = style
    }
}
```
3. Add the param to the shared VM builder (the `SettingsViewModel(...)` construction ~line 180). Because it needs to be asserted, change the builder to accept and return the fake. Add near the top of the builder function a `val receiptStore = FakeReceiptImagePreferencesStore()`, pass `receiptImagePreferencesStore = receiptStore,` in the constructor, and return it too (e.g. change the return to `Triple(vm, userRepo, receiptStore)` and update existing call sites, OR expose the fake via a default-arg builder). Simplest: add an overload-free change — have the builder take `receiptStore: FakeReceiptImagePreferencesStore = FakeReceiptImagePreferencesStore()` as a parameter and include it in the returned pair as needed by the new test only. Keep existing tests compiling by keeping their return usage unchanged.
4. Add the test:
```kotlin
@Test
fun toggling_receipt_image_style_from_light_flips_to_dark_and_persists() = runTest {
    val receiptStore = FakeReceiptImagePreferencesStore(ReceiptImageStyle.LIGHT)
    val (vm, _) = buildViewModel(receiptStore = receiptStore)
    vm.state.test {
        awaitItem() // initial
        // let the seeding flow settle to LIGHT
        skipItems(0)
        vm.onAction(SettingsAction.OnReceiptImageStyleClick)
        // state eventually reflects DARK
        val dark = awaitItem()
        assertEquals(ReceiptImageStyle.DARK, dark.receiptImageStyle)
        cancelAndIgnoreRemainingEvents()
    }
    assertEquals(ReceiptImageStyle.DARK, receiptStore.lastSet)
}
```
(Match the existing tests' Turbine idiom in this file — mirror how `toggling_daily_digest...` awaits items; adjust `awaitItem()`/`skipItems` counts to the file's established pattern.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.settings.SettingsDigestToggleTest"`
Expected: FAIL — `OnReceiptImageStyleClick` / `receiptImageStyle` unresolved.

- [ ] **Step 3: Add state field**

`SettingsState.kt` — add import and field:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
```kotlin
    val receiptImageStyle: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
```
(place next to `themePreference`).

- [ ] **Step 4: Add action**

`SettingsAction.kt`:
```kotlin
    data object OnReceiptImageStyleClick : SettingsAction
```

- [ ] **Step 5: Wire the ViewModel**

`SettingsViewModel.kt`:
1. Imports:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
2. Constructor param (next to `themePreferencesStore`):
```kotlin
    private val receiptImagePreferencesStore: ReceiptImagePreferencesStore,
```
3. `LocalUiState` — add field:
```kotlin
    val receiptImageStyle: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
```
4. Seed it in `settingsStateFlow()` alongside theme:
```kotlin
        uiState.update {
            it.copy(
                measurementUnit = measurementPreferencesStore.getUnit(),
                themePreference = themePreferencesStore.getTheme(),
                receiptImageStyle = receiptImagePreferencesStore.getStyle(),
            )
        }
```
5. Map it in `buildState(...)` — add to the returned `SettingsState(...)`:
```kotlin
            receiptImageStyle = ui.receiptImageStyle,
```
6. Handle the action in `onAction`:
```kotlin
            SettingsAction.OnReceiptImageStyleClick -> toggleReceiptImageStyle()
```
7. Add the toggle function (mirrors `toggleMeasurementUnit`):
```kotlin
    private fun toggleReceiptImageStyle() {
        viewModelScope.launch {
            var next: ReceiptImageStyle = ReceiptImageStyle.LIGHT
            uiState.update { current ->
                next = if (current.receiptImageStyle == ReceiptImageStyle.LIGHT) {
                    ReceiptImageStyle.DARK
                } else {
                    ReceiptImageStyle.LIGHT
                }
                current.copy(receiptImageStyle = next)
            }
            receiptImagePreferencesStore.setStyle(next)
        }
    }
```

- [ ] **Step 6: Add strings**

In `strings.xml`, next to the appearance strings (~line 1352):
```xml
    <string name="settings_row_receipt_image">Receipt image style</string>
    <string name="settings_receipt_image_light">Light</string>
    <string name="settings_receipt_image_dark">Dark</string>
```

- [ ] **Step 7: Add the Settings row**

`SettingsScreen.kt` — add the import for the icon and a row after the Appearance `SettingsRow` + a `SettingsRowDivider()` (in the Preferences `SettingsSectionCard`, after line ~260):

```kotlin
import androidx.compose.material.icons.outlined.Image
```

```kotlin
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Image,
                    label = stringResource(Res.string.settings_row_receipt_image),
                    onClick = { onAction(SettingsAction.OnReceiptImageStyleClick) },
                    trailing = {
                        SettingsRowValue(
                            text = stringResource(
                                when (state.receiptImageStyle) {
                                    ReceiptImageStyle.LIGHT -> Res.string.settings_receipt_image_light
                                    ReceiptImageStyle.DARK -> Res.string.settings_receipt_image_dark
                                }
                            ),
                        )
                    },
                )
```
Also add the state import at the top of `SettingsScreen.kt`:
```kotlin
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
```
Ensure the new `Res.string.*` symbols are imported (the file imports each `Res.string.x` explicitly — add the three new ones, or rely on the existing `stitchpad.composeapp.generated.resources.Res` wildcard if present; match the file's convention).

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.settings.SettingsDigestToggleTest"`
Expected: PASS (existing digest tests + new receipt-image test).

- [ ] **Step 9: Verify iOS compiles + detekt**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL. (Previews already exist on `SettingsScreen`; if detekt flags `TooManyFunctions` from an added preview, use `@file:Suppress` per repo convention — not applicable here since no new preview is added.)

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/SettingsDigestToggleTest.kt
git commit -m "feat(settings): add Receipt image style toggle (Light/Dark)"
```

---

## Final verification

- [ ] Full unit tests: `./gradlew :composeApp:testDebugUnitTest`
- [ ] iOS compile gate: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
- [ ] Detekt: `./gradlew detekt`

## QA smoke test (manual — Daniel)

1. Fresh install (no preference) → open an order → Share → **Image** → image is **light** (white background, dark text, readable).
2. Settings → Preferences → **Receipt image style** shows "Light". Tap → flips to "Dark".
3. Share the same order as **Image** → image is now **dark**.
4. Tap the row again → "Light" → share image → light again.
5. Share as **PDF** in both preference states → PDF is **white** both times (unchanged).
6. On a **Free-tier** account, confirm the "STITCHPAD" watermark is visible on the **light** image (darker ink), and the logo, bank block, discount line, totals accent, and status color all render correctly.
7. Repeat 1–6 on **iOS**.

## Notes / non-goals

- PDF unchanged; no dark-PDF renderer.
- Preference is local per device (like theme); no Firestore sync.
- No per-share toggle; the Settings preference is the only control.
- Renderers keep their own bitmap/PDF layouts — only the image palette is parameterized (no image+PDF unification).
