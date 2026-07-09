# Share a Style — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a tailor share a style's photo (with its description as caption) out of StitchPad via the OS share sheet, reachable from the gallery long-press sheet and the style view screen.

**Architecture:** Reuse the receipt-sharing pattern. A new generic `ImageSharer` expect/actual writes bytes to a cache file and fires the platform share sheet with the caption as `EXTRA_TEXT` (Android) / an activity item (iOS). A Coil-backed `StyleImageBytesLoader` (behind a `fun interface` seam so tests can fake it) turns `localPhotoPath ?: photoUrl` into PNG bytes. A pure `StyleShareFormatter` builds the caption. A `ShareStyle` use case orchestrates the three, reading tier from `EntitlementsProvider`. Two thin entry points call `ShareStyle`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Coil3, Koin, GitLive Firebase, JUnit5 + Turbine + AssertK (commonTest), detekt.

## Global Constraints

- Package root: `com.danzucker.stitchpad`. Single `:composeApp` module, package-based layers (domain → data → presentation).
- MVI: every screen has State/Action/Event + ViewModel; Root/Screen composable split; every Screen has a `@Preview`.
- `Result<T, E>` / `EmptyResult<E>` for expected failures — never throw. `DataError.Local` = `{ DISK_FULL, NOT_FOUND, UNKNOWN }`.
- Koin: `singleOf`/`viewModelOf`/`factoryOf` constructor refs; `koinViewModel()` only in Root composables.
- Name implementations descriptively (e.g. `CoilStyleImageBytesLoader`, not `...Impl`).
- All **UI** user-facing strings via compose.resources (`Res.string.*`) in `composeApp/src/commonMain/composeResources/values/strings.xml`. Non-UI share payload text (the attribution line) is built in Kotlin, matching the existing `MeasurementShareFormatter` precedent which hardcodes `"Sent from StitchPad · getstitchpad.com"`.
- No backslash escapes in strings.xml — use `&apos;` / `’` (CMP iOS renders `\'` literally).
- All state in ViewModel; no business logic in composables.
- Attribution line (exact, Free tier only): `Shared via StitchPad · getstitchpad.com`. Pro/Atelier omit it.
- Design system: indigo `#2C3E7C` primary for the Share action (non-destructive); tokens from `DesignTokens`.
- iOS must compile — no JVM-only APIs. Run the iOS compile gate before claiming done.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatter.kt` — pure caption builder.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleImageBytesLoader.kt` — `fun interface` + Coil impl.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyle.kt` — use case.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.kt` — expect declaration.
- `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.android.kt` — Android actual.
- `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.ios.kt` — iOS actual.
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatterTest.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyleTest.kt`

**Modify:**
- `composeApp/src/androidMain/res/xml/file_paths.xml` — add `shared_images/` cache-path.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/StyleModule.kt` — bind loader + `ShareStyle`.
- `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt` — `single { ImageSharer(androidContext()) }`.
- `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt` — `single { ImageSharer() }`.
- `composeApp/src/commonMain/composeResources/values/strings.xml` — 2 new strings.
- `.../feature/style/presentation/gallery/StyleGalleryAction.kt` + `StyleGalleryViewModel.kt` + `StyleGalleryScreen.kt` — gallery entry point.
- `.../feature/style/presentation/form/StyleFormAction.kt` + `StyleFormViewModel.kt` + `StyleFormScreen.kt` — form entry point.

---

## Task 1: `StyleShareFormatter` — pure caption builder

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatter.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatterTest.kt`

**Interfaces:**
- Produces: `object StyleShareFormatter { fun caption(style: Style, tier: SubscriptionTier): String? }`
  - Returns trimmed `style.description` when non-blank.
  - Appends `"\n\nShared via StitchPad · getstitchpad.com"` **only** when `tier == SubscriptionTier.FREE`.
  - Blank description + paid tier → `null`. Blank description + FREE → the attribution line alone (no leading newlines).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StyleShareFormatterTest {

    private fun style(description: String) = Style(
        id = "s1",
        customerId = "c1",
        description = description,
        photoUrl = "https://example.com/p.jpg",
        photoStoragePath = "styles/s1.jpg",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val attribution = "Shared via StitchPad · getstitchpad.com"

    @Test
    fun free_tier_with_description_appends_attribution() {
        val result = StyleShareFormatter.caption(style("Blue agbada"), SubscriptionTier.FREE)
        assertEquals("Blue agbada\n\n$attribution", result)
    }

    @Test
    fun paid_tier_with_description_has_no_attribution() {
        val result = StyleShareFormatter.caption(style("Blue agbada"), SubscriptionTier.PRO)
        assertEquals("Blue agbada", result)
    }

    @Test
    fun paid_tier_blank_description_is_null() {
        assertNull(StyleShareFormatter.caption(style("   "), SubscriptionTier.ATELIER))
    }

    @Test
    fun free_tier_blank_description_is_attribution_only() {
        val result = StyleShareFormatter.caption(style(""), SubscriptionTier.FREE)
        assertEquals(attribution, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.share.StyleShareFormatterTest"`
Expected: FAIL — unresolved reference `StyleShareFormatter`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * Builds the WhatsApp/share caption for a style. Pure and unit-testable.
 * Free tier carries a StitchPad attribution line (free distribution); paid
 * tiers get a clean caption. Mirrors MeasurementShareFormatter's role.
 */
object StyleShareFormatter {

    private const val ATTRIBUTION = "Shared via StitchPad · getstitchpad.com"

    fun caption(style: Style, tier: SubscriptionTier): String? {
        val description = style.description.trim().takeIf { it.isNotBlank() }
        val attribution = ATTRIBUTION.takeIf { tier == SubscriptionTier.FREE }
        return when {
            description != null && attribution != null -> "$description\n\n$attribution"
            description != null -> description
            else -> attribution
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.share.StyleShareFormatterTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatter.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleShareFormatterTest.kt
git commit -m "feat(style): add StyleShareFormatter caption builder"
```

---

## Task 2: `ImageSharer` — generic image + caption share sheet (expect/actual)

No unit test — platform side-effect (fires the OS share sheet). Verified by the manual smoke test in Task 8. Steps produce the expect/actual trio and extend the FileProvider paths.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.kt`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.ios.kt`
- Modify: `composeApp/src/androidMain/res/xml/file_paths.xml`

**Interfaces:**
- Produces: `expect class ImageSharer { suspend fun shareImage(bytes: ByteArray, caption: String?) }`
  - Android actual ctor: `ImageSharer(context: Context)`. iOS actual ctor: `ImageSharer()`.

- [ ] **Step 1: Write the expect declaration**

`ImageSharer.kt`:
```kotlin
package com.danzucker.stitchpad.core.sharing

/**
 * Shares a single image (raw bytes) plus an optional caption via the platform
 * share sheet (Android ACTION_SEND chooser / iOS UIActivityViewController).
 * Feature-agnostic; used by style sharing and reusable elsewhere.
 */
expect class ImageSharer {
    suspend fun shareImage(bytes: ByteArray, caption: String?)
}
```

- [ ] **Step 2: Add the `shared_images/` cache-path**

Modify `composeApp/src/androidMain/res/xml/file_paths.xml` to:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_receipts" path="receipts/" />
    <cache-path name="camera_captures" path="camera/" />
    <cache-path name="shared_images" path="shared_images/" />
</paths>
```

- [ ] **Step 3: Write the Android actual**

`ImageSharer.android.kt`:
```kotlin
package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class ImageSharer(private val context: Context) {

    actual suspend fun shareImage(bytes: ByteArray, caption: String?) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val f = File(dir, "style_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { it.write(bytes) }
            pruneOld(dir)
            f
        }
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }

    private fun pruneOld(dir: File) {
        val files = dir.listFiles().orEmpty()
        if (files.size <= CACHE_LIMIT) return
        files.sortedByDescending { it.lastModified() }.drop(CACHE_LIMIT).forEach { it.delete() }
    }

    private companion object {
        const val CACHE_LIMIT = 10
    }
}
```

- [ ] **Step 4: Write the iOS actual**

`ImageSharer.ios.kt`:
```kotlin
package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class ImageSharer {

    actual suspend fun shareImage(bytes: ByteArray, caption: String?) {
        if (bytes.isEmpty()) return
        // Let any dismissing Compose ModalBottomSheet finish before presenting a
        // UIKit modal — UIKit silently refuses to present mid-transition.
        delay(SHARE_PRESENT_DELAY_MS)
        withContext(Dispatchers.Main) {
            val image = UIImage.imageWithData(bytes.toNSData()) ?: return@withContext
            val items: List<Any> = buildList {
                add(image)
                if (!caption.isNullOrBlank()) add(caption)
            }
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = topmostPresenter(rootVC)
            val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
            // iPad: a popover source is required or the sheet fails to present.
            activityVC.popoverPresentationController?.apply {
                sourceView = presenter.view
                presenter.view.bounds.useContents {
                    sourceRect = CGRectMake(
                        origin.x + size.width / 2.0,
                        origin.y + size.height / 2.0,
                        0.0,
                        0.0,
                    )
                }
            }
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    private fun topmostPresenter(root: UIViewController): UIViewController {
        var vc: UIViewController = root
        while (true) {
            val next = vc.presentedViewController ?: return vc
            if (next.isBeingDismissed()) return vc
            vc = next
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private companion object {
        const val SHARE_PRESENT_DELAY_MS = 450L
    }
}
```

- [ ] **Step 5: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (no `expect`/`actual` mismatch, no unresolved iOS symbols).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/ImageSharer.ios.kt \
        composeApp/src/androidMain/res/xml/file_paths.xml
git commit -m "feat(sharing): add ImageSharer expect/actual for image + caption"
```

---

## Task 3: `StyleImageBytesLoader` — Coil bytes seam

No unit test — the impl wraps Coil's `ImageLoader`, unconstructible in `commonTest` (per `StylePickerLogic`). The `fun interface` exists precisely so Task 4's use case is testable with a fake. The impl is exercised by Task 8's smoke test.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleImageBytesLoader.kt`

**Interfaces:**
- Consumes: `Image.toPngBytes(): ByteArray?` from `core.sharing.CoilImageBytes` (existing).
- Produces:
  - `fun interface StyleImageBytesLoader { suspend fun load(model: String): ByteArray? }`
  - `class CoilStyleImageBytesLoader(imageLoader: ImageLoader, platformContext: PlatformContext) : StyleImageBytesLoader`

- [ ] **Step 1: Write the interface + Coil impl**

```kotlin
package com.danzucker.stitchpad.feature.style.presentation.share

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.danzucker.stitchpad.core.sharing.toPngBytes

/**
 * Resolves an image model (a local file path or remote URL) to PNG-encoded
 * bytes for sharing. Behind a fun interface so ShareStyle stays unit-testable
 * without Coil (which is unconstructible in commonTest).
 */
fun interface StyleImageBytesLoader {
    suspend fun load(model: String): ByteArray?
}

/**
 * Real loader: runs the model through Coil (served from disk cache when the
 * image is already on screen — no re-download) and PNG-encodes the result.
 * Returns null on blank input, load failure, or unsupported image type.
 */
class CoilStyleImageBytesLoader(
    private val imageLoader: ImageLoader,
    private val platformContext: PlatformContext,
) : StyleImageBytesLoader {

    override suspend fun load(model: String): ByteArray? {
        if (model.isBlank()) return null
        val request = ImageRequest.Builder(platformContext).data(model).build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        return result.image.toPngBytes()
    }
}
```

- [ ] **Step 2: Verify it compiles (both platforms)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/StyleImageBytesLoader.kt
git commit -m "feat(style): add StyleImageBytesLoader Coil bytes seam"
```

---

## Task 4: `ShareStyle` use case

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyle.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyleTest.kt`

**Interfaces:**
- Consumes: `StyleImageBytesLoader` (Task 3), `ImageSharer` (Task 2), `StyleShareFormatter` (Task 1), `EntitlementsProvider.awaitHydrated().tier`.
- Produces: `class ShareStyle(loader, sharer, entitlements) { suspend operator fun invoke(style: Style): EmptyResult<DataError> }`
  - Resolves model as `style.localPhotoPath ?: style.photoUrl`.
  - Returns `Result.Error(DataError.Local.UNKNOWN)` when the model is blank or bytes are null; otherwise calls `sharer.shareImage(bytes, caption)` and returns `Result.Success(Unit)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareStyleTest {

    private class FakeSharer {
        var sharedBytes: ByteArray? = null
        var sharedCaption: String? = null
        var callCount = 0
    }

    // ShareStyle's primary constructor takes a fun-interface loader + a suspend
    // `share` lambda, so tests need no platform ImageSharer. See Step 3.
    private fun entitlements(tier: SubscriptionTier) = object : EntitlementsProvider {
        private val flowState = MutableStateFlow(
            UserEntitlements(tier = tier, styleImageCap = 15, customerCap = 15)
        )
        override val flow: StateFlow<UserEntitlements> = flowState
        override fun current() = flowState.value
        override suspend fun awaitHydrated() = flowState.value
    }

    private val style = Style(
        id = "s1", customerId = "c1", description = "Blue agbada",
        photoUrl = "https://example.com/p.jpg", photoStoragePath = "styles/s1.jpg",
        createdAt = 0L, updatedAt = 0L, localPhotoPath = "/local/s1.jpg",
    )

    @Test
    fun shares_local_path_bytes_and_caption() = runTest {
        val fake = FakeSharer()
        val shareStyle = ShareStyle(
            loader = { model -> if (model == "/local/s1.jpg") byteArrayOf(1, 2, 3) else null },
            share = { bytes, caption -> fake.callCount++; fake.sharedBytes = bytes; fake.sharedCaption = caption },
            entitlements = entitlements(SubscriptionTier.FREE),
        )

        val result = shareStyle(style)

        assertTrue(result is Result.Success)
        assertEquals(1, fake.callCount)
        assertEquals("Blue agbada\n\nShared via StitchPad · getstitchpad.com", fake.sharedCaption)
    }

    @Test
    fun falls_back_to_photoUrl_when_no_local_path() = runTest {
        var seenModel: String? = null
        val shareStyle = ShareStyle(
            loader = { model -> seenModel = model; byteArrayOf(9) },
            share = { _, _ -> },
            entitlements = entitlements(SubscriptionTier.PRO),
        )

        shareStyle(style.copy(localPhotoPath = null))

        assertEquals("https://example.com/p.jpg", seenModel)
    }

    @Test
    fun returns_error_and_does_not_share_when_bytes_null() = runTest {
        val fake = FakeSharer()
        val shareStyle = ShareStyle(
            loader = { null },
            share = { bytes, caption -> fake.callCount++; fake.sharedBytes = bytes; fake.sharedCaption = caption },
            entitlements = entitlements(SubscriptionTier.FREE),
        )

        val result = shareStyle(style)

        assertTrue(result is Result.Error)
        assertEquals(DataError.Local.UNKNOWN, (result as Result.Error).error)
        assertEquals(0, fake.callCount)
        assertNull(fake.sharedBytes)
    }
}
```

> **Note on `UserEntitlements` construction:** the constructor arguments above (`styleImageCap`, `customerCap`) are placeholders — open `core/domain/entitlement/UserEntitlements.kt` and copy the real required parameters, matching how `FakeEntitlementsProvider` in `StyleGalleryViewModelTest.kt` builds one via its `entitlementsFor(tier)` helper. Reuse that helper's field set.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.share.ShareStyleTest"`
Expected: FAIL — unresolved reference `ShareStyle`.

- [ ] **Step 3: Write minimal implementation**

`ShareStyle.kt` — the production class depends on the real `ImageSharer`, but exposes a `share` lambda seam defaulting to it so tests inject a capturing lambda:
```kotlin
package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.sharing.ImageSharer

/**
 * Orchestrates sharing a style's photo + caption. Resolves the image model
 * (local file first, remote URL fallback), loads bytes, builds the tier-keyed
 * caption, and fires the share sheet. Returns Error when no shareable bytes
 * are available (offline with no cache, decode failure, or blank model).
 */
class ShareStyle(
    private val loader: StyleImageBytesLoader,
    private val entitlements: EntitlementsProvider,
    private val share: suspend (ByteArray, String?) -> Unit,
) {
    constructor(
        loader: StyleImageBytesLoader,
        entitlements: EntitlementsProvider,
        sharer: ImageSharer,
    ) : this(loader, entitlements, share = { bytes, caption -> sharer.shareImage(bytes, caption) })

    suspend operator fun invoke(style: Style): EmptyResult<DataError> {
        val model = style.localPhotoPath ?: style.photoUrl
        val bytes = loader.load(model) ?: return Result.Error(DataError.Local.UNKNOWN)
        val tier = entitlements.awaitHydrated().tier
        share(bytes, StyleShareFormatter.caption(style, tier))
        return Result.Success(Unit)
    }
}
```

> The test's `ShareStyle(loader = ..., share = ..., entitlements = ...)` call uses the primary constructor (named args, any order). DI (Task 5) uses the secondary `sharer:` constructor.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.style.presentation.share.ShareStyleTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyle.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/share/ShareStyleTest.kt
git commit -m "feat(style): add ShareStyle use case"
```

---

## Task 5: DI wiring

No new unit test — resolution is verified by the app building and the smoke test in Task 8.

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/StyleModule.kt`

**Interfaces:**
- Consumes: `ImageSharer` (Task 2), `StyleImageBytesLoader`/`CoilStyleImageBytesLoader` (Task 3), `ShareStyle` (Task 4). Registered `ImageLoader`, `PlatformContext`, `EntitlementsProvider` singles already exist.
- Produces: DI-resolvable `ImageSharer`, `StyleImageBytesLoader`, `ShareStyle`.

- [ ] **Step 1: Register `ImageSharer` on Android**

In `PlatformModule.android.kt`, add `import com.danzucker.stitchpad.core.sharing.ImageSharer` and, alongside the existing `single { OrderReceiptSharer(androidContext()) }`:
```kotlin
    single { ImageSharer(androidContext()) }
```

- [ ] **Step 2: Register `ImageSharer` on iOS**

In `PlatformModule.ios.kt`, add `import com.danzucker.stitchpad.core.sharing.ImageSharer` and, alongside `single { OrderReceiptSharer() }`:
```kotlin
    single { ImageSharer() }
```

- [ ] **Step 3: Bind the loader + `ShareStyle` in `StyleModule.kt`**

Replace the module bodies with:
```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.style.data.FirebaseStyleRepository
import com.danzucker.stitchpad.feature.style.presentation.folders.StyleFoldersViewModel
import com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModel
import com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModel
import com.danzucker.stitchpad.feature.style.presentation.share.CoilStyleImageBytesLoader
import com.danzucker.stitchpad.feature.style.presentation.share.ShareStyle
import com.danzucker.stitchpad.feature.style.presentation.share.StyleImageBytesLoader
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val styleDataModule = module {
    singleOf(::FirebaseStyleRepository) bind StyleRepository::class
    singleOf(::CoilStyleImageBytesLoader) bind StyleImageBytesLoader::class
}

val stylePresentationModule = module {
    factory { ShareStyle(get(), get(), get<ImageSharer>()) }
    viewModelOf(::StyleFoldersViewModel)
    viewModelOf(::StyleGalleryViewModel)
    viewModelOf(::StyleFormViewModel)
}
```

> `factoryOf(::ShareStyle)` does **not** compile here: `ShareStyle` has two 3-arg constructors, so the `::ShareStyle` reference is ambiguous at Kotlin compile time (before Koin's graph resolution runs). Use the explicit lambda `factory { ShareStyle(get(), get(), get<ImageSharer>()) }` — `get<ImageSharer>()` forces the secondary `(loader, entitlements, sharer)` constructor. Add `import com.danzucker.stitchpad.core.sharing.ImageSharer` and `org.koin.dsl.module`'s `factory`; keep `singleOf` for the loader bind. `CoilStyleImageBytesLoader`'s `(imageLoader, platformContext)` resolve from the existing `ImageLoader` + `PlatformContext` singles in the platform modules.

- [ ] **Step 4: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/di/PlatformModule.ios.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/StyleModule.kt
git commit -m "feat(di): wire ImageSharer, StyleImageBytesLoader, ShareStyle"
```

---

## Task 6: Gallery long-press sheet entry point

**Files:**
- Modify: `.../feature/style/presentation/gallery/StyleGalleryAction.kt`
- Modify: `.../feature/style/presentation/gallery/StyleGalleryViewModel.kt`
- Modify: `.../feature/style/presentation/gallery/StyleGalleryScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `.../feature/style/presentation/gallery/StyleGalleryViewModelTest.kt`

**Interfaces:**
- Consumes: `ShareStyle` (Task 4). `StyleGalleryViewModel` gains a `shareStyle: ShareStyle` constructor param (resolved via `viewModelOf`).
- Produces: `StyleGalleryAction.OnShareClick(style: Style)`; a Share row in `StyleActionsSheet`.

- [ ] **Step 1: Add the two new strings**

In `strings.xml`, next to `style_action_delete` (line ~493):
```xml
    <string name="style_action_share">Share style</string>
    <string name="style_share_failed">Couldn&apos;t prepare this photo to share.</string>
```

- [ ] **Step 2: Add the action**

In `StyleGalleryAction.kt`, add to the sealed interface:
```kotlin
    data class OnShareClick(val style: Style) : StyleGalleryAction
```

- [ ] **Step 3: Write the failing ViewModel test**

Add to `StyleGalleryViewModelTest.kt`. Use a recording fake `ShareStyle` built via its **primary** constructor (`loader`, `entitlements`, `share`) so no platform `ImageSharer` is needed. Follow the existing test's `FakeEntitlementsProvider` and VM-construction helpers.
```kotlin
    @Test
    fun onShareClick_invokes_shareStyle_and_dismisses_sheet() = runTest {
        var sharedId: String? = null
        val recordingShare = ShareStyle(
            loader = { byteArrayOf(1) },
            entitlements = FakeEntitlementsProvider(),
            share = { _, _ -> },
        )
        // If the existing helper builds the VM, thread this ShareStyle through it.
        val viewModel = buildViewModel(shareStyle = recordingShare) // adapt to the test's factory
        val style = sampleStyle(id = "s1") // reuse the test's existing style builder

        viewModel.onAction(StyleGalleryAction.OnStyleLongPress(style))
        viewModel.onAction(StyleGalleryAction.OnShareClick(style))
        advanceUntilIdle()

        assertThat(viewModel.state.value.actionSheetStyle).isNull()
    }
```
> Adapt names (`buildViewModel`, `sampleStyle`, imports for `assertThat`/`isNull`, `advanceUntilIdle`) to the conventions already in this test file. If the file builds the VM inline rather than via a helper, add an overload/param for `shareStyle` mirroring how it passes the other collaborators. To assert `ShareStyle` actually ran, capture into a `var` inside the injected `share` lambda and assert it was set.

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleGalleryViewModelTest*"`
Expected: FAIL — `OnShareClick` unhandled / `shareStyle` param missing.

- [ ] **Step 5: Wire the ViewModel**

In `StyleGalleryViewModel.kt`, add the constructor param (after `entitlements`):
```kotlin
    private val shareStyle: ShareStyle,
```
Add imports:
```kotlin
import com.danzucker.stitchpad.feature.style.presentation.share.ShareStyle
import com.danzucker.stitchpad.core.domain.error.onError
```
> If the codebase's Result helper is named differently (e.g. `onFailure`), use that — check `core/domain/error/Result.kt`.

Add the handler in the `onAction` `when` (near `OnDeleteClick`):
```kotlin
            is StyleGalleryAction.OnShareClick -> {
                _state.update { it.copy(actionSheetStyle = null) }
                viewModelScope.launch {
                    shareStyle(action.style).onError {
                        _state.update {
                            it.copy(errorMessage = UiText.StringResourceText(Res.string.style_share_failed))
                        }
                    }
                }
            }
```
> `UiText`, `Res`, and `viewModelScope`/`launch` are already imported in this file (used by neighboring handlers).

- [ ] **Step 6: Add the Share row to the sheet**

In `StyleGalleryScreen.kt`: extend `StyleActionsSheet` with an `onShare: () -> Unit` param and a `SheetActionRow` at the top of the `Column` (before Copy). Icon: `Icons.Default.Share` (add `import androidx.compose.material.icons.filled.Share`). Default tint (indigo `onSurface`), not error.
```kotlin
            SheetActionRow(
                icon = Icons.Default.Share,
                label = stringResource(Res.string.style_action_share),
                onClick = onShare
            )
```
At the call site (line ~334), add:
```kotlin
            onShare = { onAction(StyleGalleryAction.OnShareClick(style)) },
```

- [ ] **Step 7: Run test to verify it passes + full module tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleGalleryViewModelTest*"`
Expected: PASS.

- [ ] **Step 8: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/gallery/StyleGalleryViewModelTest.kt
git commit -m "feat(style): share action in gallery long-press sheet"
```

---

## Task 7: Style view screen (form) entry point

**Files:**
- Modify: `.../feature/style/presentation/form/StyleFormAction.kt`
- Modify: `.../feature/style/presentation/form/StyleFormViewModel.kt`
- Modify: `.../feature/style/presentation/form/StyleFormScreen.kt`
- Test: `.../feature/style/presentation/form/StyleFormViewModelTest.kt`

**Interfaces:**
- Consumes: `ShareStyle` (Task 4); `Res.string.style_action_share` / `style_share_failed` (Task 6). `StyleFormViewModel` gains `shareStyle: ShareStyle`.
- Produces: `StyleFormAction.OnShareClick`; a share `IconButton` in the form `TopAppBar`, shown only for an existing style.

- [ ] **Step 1: Add the action**

In `StyleFormAction.kt`:
```kotlin
    data object OnShareClick : StyleFormAction
```

- [ ] **Step 2: Write the failing ViewModel test**

In `StyleFormViewModelTest.kt`, add a test that constructs the VM in **edit mode** (existing `styleId` in `SavedStateHandle` — mirror how the file already sets up edit-mode tests), injects a recording `ShareStyle` (primary constructor with a capturing `share` lambda), dispatches `OnShareClick`, and asserts the capture fired. On the null-bytes path (loader returns null), assert `state.errorMessage` is set.
```kotlin
    @Test
    fun onShareClick_shares_current_style() = runTest {
        var shared = false
        val shareStyle = ShareStyle(
            loader = { byteArrayOf(1) },
            entitlements = FakeEntitlementsProvider(),
            share = { _, _ -> shared = true },
        )
        val viewModel = buildEditModeViewModel(shareStyle = shareStyle) // adapt to this file's factory + seeded style

        viewModel.onAction(StyleFormAction.OnShareClick)
        advanceUntilIdle()

        assertThat(shared).isTrue()
    }
```
> Adapt `buildEditModeViewModel`, `FakeEntitlementsProvider`, and the seeded-style setup to this test file's existing conventions. The VM must have a loaded current style for `OnShareClick` to share (edit mode loads it).

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleFormViewModelTest*"`
Expected: FAIL — `OnShareClick` unhandled / `shareStyle` param missing.

- [ ] **Step 4: Wire the ViewModel**

In `StyleFormViewModel.kt`, add constructor param (after `entitlements`):
```kotlin
    private val shareStyle: ShareStyle,
```
Add imports (`ShareStyle`, and the Result error helper matching Task 6). Add the handler in `onAction`:
```kotlin
            StyleFormAction.OnShareClick -> {
                val current = buildCurrentStyle() ?: return
                viewModelScope.launch {
                    shareStyle(current).onError {
                        _state.update {
                            it.copy(errorMessage = UiText.StringResourceText(Res.string.style_share_failed))
                        }
                    }
                }
            }
```
> `buildCurrentStyle()` is a placeholder: the VM must hand `ShareStyle` a `Style` domain object. Use the VM's already-loaded style (the field it populates in edit mode — find where it stores the loaded `Style`/state photo + description) and construct/reuse a `Style` with the current `id`, `customerId`, `description`, `photoUrl`, `photoStoragePath`, and `localPhotoPath`. If the VM keeps the loaded `Style` in a private field, share that directly and drop the helper.

- [ ] **Step 5: Add the share `IconButton` to the top bar**

In `StyleFormScreen.kt`, in the `TopAppBar` `actions = { }` slot, add — guarded so it only shows for an existing style (use the state flag the screen already uses to distinguish add vs edit, e.g. `state.isEditing` / `state.styleId != null`; match the actual field):
```kotlin
                if (state.isExistingStyle) {
                    IconButton(onClick = { onAction(StyleFormAction.OnShareClick) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.style_action_share),
                        )
                    }
                }
```
Add `import androidx.compose.material.icons.filled.Share` (and `IconButton`/`Icon` if not already imported). Replace `state.isExistingStyle` with the real state predicate for "viewing an existing style" — confirm against `StyleFormState`.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StyleFormViewModelTest*"`
Expected: PASS.

- [ ] **Step 7: Verify both platforms compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/style/presentation/form/StyleFormViewModelTest.kt
git commit -m "feat(style): share action on the style view screen"
```

---

## Task 8: Full verification + manual smoke

**Files:** none (verification only).

- [ ] **Step 1: Full test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass (new + existing).

- [ ] **Step 2: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If `StyleGalleryViewModel`/`StyleFormViewModel` now trips `TooManyFunctions` or a long-method rule, prefer a targeted `@Suppress` with a one-line note over restructuring (per project convention), or extract the handler body to a private function.

- [ ] **Step 3: iOS compile gate**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (no JVM-only API leak, no unresolved iOS symbols).

- [ ] **Step 4: Android smoke test** (manual — Daniel is QA)

1. Open a customer's styles (or Inspiration) gallery with at least one style that has a description.
2. Long-press a style → tap **Share style** → confirm the Android chooser opens with WhatsApp listed; pick WhatsApp → the photo attaches and the description + `Shared via StitchPad · getstitchpad.com` line (Free tier) appears as the message. On a Pro/Atelier test account, confirm no attribution line.
3. Open a style (tap it) → tap the top-bar **share icon** → same result.
4. Turn on airplane mode, open a style whose image is already cached, share → still works (uses cached/local bytes). Share a never-loaded style offline → the "Couldn&apos;t prepare this photo to share." snackbar shows, no crash.

- [ ] **Step 5: iOS smoke test** (manual, iPhone 17 Pro sim)

Repeat 1–3 on iOS: confirm the `UIActivityViewController` presents (note the ~450ms delay after the long-press sheet dismisses is intentional) and WhatsApp receives image + caption.

- [ ] **Step 6: Final commit (if any suppress/cleanup was needed)**

```bash
git add -A
git commit -m "chore(style): detekt + verification cleanup for share-style"
```

---

## Self-Review notes (addressed)

- **Spec coverage:** ImageSharer (§1) → T2; StyleShareFormatter (§2) → T1; StyleImageBytesLoader (§3) → T3; ShareStyle (§4) → T4; DI (§DI) → T5; gallery entry (§A) → T6; form entry (§B) → T7; byte-source `localPhotoPath ?: photoUrl` → T3/T4; error handling + strings → T6/T7; tests → T1/T4/T6/T7; free-tier `getstitchpad.com` attribution → T1. All covered.
- **Type consistency:** `ShareStyle(loader, entitlements, share)` primary + `(loader, entitlements, sharer)` secondary used consistently in T4 tests, T5 DI, T6/T7 tests. `StyleImageBytesLoader.load(model): ByteArray?`, `ImageSharer.shareImage(bytes, caption)`, `caption(style, tier): String?`, `DataError.Local.UNKNOWN` consistent throughout.
- **Known adaptation points (flagged inline, not placeholders):** exact `UserEntitlements` constructor fields (copy from `UserEntitlements.kt`/`FakeEntitlementsProvider`); the Result error helper name (`onError` vs `onFailure` — confirm in `Result.kt`); the VM's loaded-`Style` accessor and the `StyleFormState` "existing style" predicate. These are lookups against existing code, with the source named in each step.
