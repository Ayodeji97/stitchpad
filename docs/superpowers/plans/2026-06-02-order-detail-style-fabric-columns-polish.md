# Order Detail — Style|Fabric side-by-side columns + overdue pill wrap (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Garment details card render Style and Fabric as two balanced side-by-side columns (killing the empty right gutter when there's one photo each), and make the overdue banner wrap its text instead of spanning full width.

**Architecture:** Pure-presentation refactor of two existing composables. `OrderGarmentDetailsCard.kt`: the stacked `ReferenceSection` becomes a width-filling `ReferenceColumn` placed in a `Row` of two weighted cells (Style | Fabric) for the first item; additional items render their fabric column full-width. A tiny pure function derives the count-pill index from scroll offset (TDD'd). `OrderHeroCard.kt`: drop one modifier. No domain/data/ViewModel/state/action changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Coil3 (`SubcomposeAsyncImage`), kotlin.test + Turbine (commonTest), detekt.

**Spec:** `docs/superpowers/specs/2026-06-02-order-detail-style-fabric-grouping-design.md` (see "Amendment — 2026-06-02").

---

## File structure

- **Modify** `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt` — overdue banner wraps content.
- **Modify** `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt` — side-by-side columns, fixed-height media, single/multi/empty tile rendering, count pill, previews. Adds one `internal` pure function `referenceScrollIndex(...)`.
- **Create** `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/ReferenceScrollIndexTest.kt` — unit test for the pure index function.

---

## Task 1: Overdue banner wraps its content

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt` (the `OverdueBanner` composable, ~line 434)

- [ ] **Step 1: Remove `fillMaxWidth()` from the banner Surface**

In `OverdueBanner`, the `Surface` currently reads:

```kotlin
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
```

Change it to drop the modifier so the surface wraps its `Row` content:

```kotlin
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
    ) {
```

- [ ] **Step 2: Check `fillMaxWidth` import is still used**

Run: `grep -n "fillMaxWidth" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`
Expected: still referenced elsewhere (e.g. the card `Surface` and `CtaRow`), so the import stays. If it returns no other hits, also remove the `import androidx.compose.foundation.layout.fillMaxWidth` line. (It is used by `CtaRow`, so it should remain.)

- [ ] **Step 3: Verify detekt passes**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt
git commit -m "fix(ptsp-13): overdue banner wraps content instead of full width"
```

---

## Task 2: Pure function for the count-pill index (TDD)

The multi-photo strip shows a "current/total" pill. With a plain `horizontalScroll` we derive the current index from the scroll offset. Extract that math into a pure, testable function so the rounding/clamping is correct.

**Files:**
- Modify: `OrderGarmentDetailsCard.kt` (add an `internal` top-level function)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/ReferenceScrollIndexTest.kt`

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/ReferenceScrollIndexTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.feature.order.presentation.detail.components.referenceScrollIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceScrollIndexTest {

    private val stride = 108f // tile width (100) + gap (8), in px for the test

    @Test
    fun atRest_isFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 0, strideePx = stride, count = 3))
    }

    @Test
    fun justPastHalf_roundsToSecond() {
        // 60px > half of 108 → rounds up to index 1
        assertEquals(1, referenceScrollIndex(scrollPx = 60, strideePx = stride, count = 3))
    }

    @Test
    fun justUnderHalf_staysFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 40, strideePx = stride, count = 3))
    }

    @Test
    fun scrolledToEnd_clampsToLast() {
        assertEquals(2, referenceScrollIndex(scrollPx = 10_000, strideePx = stride, count = 3))
    }

    @Test
    fun singleImage_isAlwaysFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 500, strideePx = stride, count = 1))
    }

    @Test
    fun zeroStride_doesNotCrash() {
        assertEquals(0, referenceScrollIndex(scrollPx = 500, strideePx = 0f, count = 3))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferenceScrollIndexTest*"`
Expected: FAIL — `referenceScrollIndex` is unresolved.

- [ ] **Step 3: Add the function**

In `OrderGarmentDetailsCard.kt`, add this `internal` top-level function (near the bottom of the file, beside `needsFabricInfo`). Add `import kotlin.math.roundToInt` to the import block.

```kotlin
/**
 * Maps a horizontal scroll offset (px) to the index of the photo currently centred-ish under
 * the count pill. Pure so the rounding/clamping is unit-tested. `strideePx` is one tile width
 * plus the inter-tile gap, in px.
 */
internal fun referenceScrollIndex(scrollPx: Int, strideePx: Float, count: Int): Int {
    if (count <= 1 || strideePx <= 0f) return 0
    return (scrollPx / strideePx).roundToInt().coerceIn(0, count - 1)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferenceScrollIndexTest*"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/ReferenceScrollIndexTest.kt
git commit -m "feat(ptsp-13): pure referenceScrollIndex for count-pill position"
```

---

## Task 3: Side-by-side Style | Fabric columns

Replace the stacked `ReferenceSection` rendering with a width-filling `ReferenceColumn`, lay Style and Fabric out in a weighted `Row` for the first item, and render additional items' fabric full-width. Update the placeholder and thumbnail rendering for the new fixed-height media, and refresh previews.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`

- [ ] **Step 1: Update size constants**

Replace the existing line:

```kotlin
private val REFERENCE_THUMBNAIL_SIZE = 64.dp
```

with:

```kotlin
private val REFERENCE_TILE_HEIGHT = 128.dp
private val REFERENCE_MULTI_TILE_WIDTH = 100.dp
```

- [ ] **Step 2: Update imports**

Ensure the import block contains these (add any missing; `width`, `derivedStateOf`, `LocalDensity`, `Color`, and `roundToInt` are the new ones — `roundToInt` was already added in Task 2):

```kotlin
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
```

Keep existing imports (`horizontalScroll`, `rememberScrollState`, `fillMaxWidth`, `fillMaxSize`, `height`, `size`, `clip`, `Box`, `Row`, `Column`, `Spacer`, `Arrangement`, `Alignment`, `RoundedCornerShape`, `SubcomposeAsyncImage`, `LoadingDots`, `TextButton`, `PaddingValues`, etc.).

- [ ] **Step 3: Restructure the per-item loop into side-by-side columns**

In `OrderGarmentDetailsCard`, replace the body of the `items.forEachIndexed { index, item -> ... }` block (the part that today calls `GarmentTextBlock`, the `if (index == 0) ReferenceSection(...)` style block, and `FabricSection(...)`) with:

```kotlin
            val firstNeedsFabricIndex = items.indexOfFirst { needsFabricInfo(it) }
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(DesignTokens.space4))
                }

                GarmentTextBlock(
                    item = item,
                    priority = if (index == 0) priority else OrderPriority.NORMAL,
                )
                Spacer(Modifier.height(DesignTokens.space3))

                if (index == 0) {
                    // Style is order-level — pair it with the first item's fabric.
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                        ReferenceColumn(
                            label = stringResource(Res.string.order_detail_style_caption),
                            icon = Icons.Default.Checkroom,
                            urls = styleImageUrls,
                            ctaLabel = if (styleImageUrls.isEmpty()) {
                                Res.string.order_detail_add_style
                            } else {
                                null
                            },
                            onCtaClick = onAddStyleClick,
                            onImageClick = openViewer,
                            modifier = Modifier.weight(1f),
                        )
                        FabricColumn(
                            item = item,
                            showCta = firstNeedsFabricIndex == index,
                            onAddFabricPhotoClick = onAddFabricPhotoClick,
                            onAddFabricNameClick = onAddFabricNameClick,
                            onImageClick = openViewer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // Additional garment items: fabric only, full width (no style column).
                    FabricColumn(
                        item = item,
                        showCta = firstNeedsFabricIndex == index,
                        onAddFabricPhotoClick = onAddFabricPhotoClick,
                        onAddFabricNameClick = onAddFabricNameClick,
                        onImageClick = openViewer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
```

(The `SectionHeader()` + `Spacer` above this block stay unchanged.)

- [ ] **Step 4: Replace `FabricSection` with `FabricColumn`**

Replace the whole `FabricSection` composable with `FabricColumn` (same fabric URL derivation + CTA gating, now forwarding a `modifier` and delegating to `ReferenceColumn`):

```kotlin
@Composable
private fun FabricColumn(
    item: OrderItem,
    showCta: Boolean,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val legacyUrl = item.fabricPhotoUrl
    val urls = when {
        item.fabricImages.isNotEmpty() -> item.fabricImages.map { it.localPhotoPath ?: it.photoUrl }
        !legacyUrl.isNullOrBlank() -> listOf(legacyUrl)
        else -> emptyList()
    }
    val needsPhoto = urls.isEmpty()
    val needsName = !needsPhoto && item.fabricName.isNullOrBlank()
    val ctaLabel: StringResource? = when {
        !showCta -> null
        needsPhoto -> Res.string.order_detail_add_fabric
        needsName -> Res.string.order_detail_add_fabric_name
        else -> null
    }
    val onCtaClick: () -> Unit = if (needsPhoto) onAddFabricPhotoClick else onAddFabricNameClick

    ReferenceColumn(
        label = stringResource(Res.string.order_detail_fabric_caption),
        icon = Icons.Default.Texture,
        urls = urls,
        ctaLabel = ctaLabel,
        onCtaClick = onCtaClick,
        onImageClick = onImageClick,
        modifier = modifier,
    )
}
```

- [ ] **Step 5: Replace `ReferenceSection` with `ReferenceColumn` + tile composables**

Replace the `ReferenceSection`, `ReferenceThumbnail`, and `ReferencePlaceholder` composables with the following four composables (`ReferenceColumn`, `SingleReferenceTile`, `MultiReferenceStrip`, `ReferencePlaceholder`):

```kotlin
/**
 * A labeled reference block that fills the width it is given: a mini header (icon + label),
 * a fixed-height media area, and an optional "Add ..." CTA below. Used for both style and
 * fabric so the two read as side-by-side peers. Media: a single photo fills the column; 2+
 * photos scroll horizontally with the next one peeking and a "i/n" count pill; empty shows a
 * tappable placeholder.
 */
@Composable
private fun ReferenceColumn(
    label: String,
    icon: ImageVector,
    urls: List<String>,
    ctaLabel: StringResource?,
    onCtaClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))

        when {
            urls.isEmpty() -> ReferencePlaceholder(
                icon = icon,
                onClick = if (ctaLabel != null) onCtaClick else null,
            )
            urls.size == 1 -> SingleReferenceTile(
                url = urls[0],
                contentDescription = label,
                onClick = { onImageClick(urls, 0) },
            )
            else -> MultiReferenceStrip(
                urls = urls,
                contentDescription = label,
                onImageClick = onImageClick,
            )
        }

        if (ctaLabel != null) {
            Spacer(Modifier.height(DesignTokens.space1))
            TextButton(onClick = onCtaClick, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = stringResource(ctaLabel),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SingleReferenceTile(
    url: String,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(REFERENCE_TILE_HEIGHT)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingDots(dotSize = 6.dp)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MultiReferenceStrip(
    urls: List<String>,
    contentDescription: String?,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val strideePx = with(LocalDensity.current) {
        (REFERENCE_MULTI_TILE_WIDTH + DesignTokens.space2).toPx()
    }
    val currentIndex by remember(urls.size, strideePx) {
        derivedStateOf { referenceScrollIndex(scrollState.value, strideePx, urls.size) }
    }
    Box(modifier = Modifier.height(REFERENCE_TILE_HEIGHT)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.horizontalScroll(scrollState),
        ) {
            urls.forEachIndexed { index, url ->
                Box(
                    modifier = Modifier
                        .width(REFERENCE_MULTI_TILE_WIDTH)
                        .height(REFERENCE_TILE_HEIGHT)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick(urls, index) },
                ) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots(dotSize = 6.dp)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Text(
            text = "${currentIndex + 1}/${urls.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DesignTokens.space2)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(DesignTokens.radiusFull),
                )
                .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReferencePlaceholder(icon: ImageVector, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .height(REFERENCE_TILE_HEIGHT)
        .clip(RoundedCornerShape(DesignTokens.radiusMd))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
    Box(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(DesignTokens.iconFeature),
        )
    }
}
```

- [ ] **Step 6: Refresh previews to the new states**

Replace the four existing preview composables (`OrderGarmentDetailsCardEmptyPreview`, `OrderGarmentDetailsCardPopulatedPreview`, `OrderGarmentDetailsCardDarkPreview`, `OrderGarmentDetailsCardMultiItemPreview`) with these — covering 1+1, multi-style scroll, asymmetric, both-empty, dark, and multi-item:

```kotlin
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardOneEachPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SHIRT,
                    description = "Ankara print",
                    price = 60_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Royal Lace",
                ),
            ),
            priority = OrderPriority.URGENT,
            styleImageUrls = listOf("https://example.com/style1.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardMultiStylePreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask",
                    price = 100_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Damask",
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = listOf(
                "https://example.com/style1.jpg",
                "https://example.com/style2.jpg",
                "https://example.com/style3.jpg",
            ),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardAsymmetricPreview() {
    // Style added, fabric empty — columns must stay aligned at equal height.
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SENATOR,
                    description = "White lace",
                    price = 85_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = listOf("https://example.com/style1.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardEmptyPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask",
                    price = 60_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = emptyList(),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SENATOR,
                    description = "White lace",
                    price = 85_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Lace",
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = listOf("https://example.com/style1.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardMultiItemPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask with embroidery",
                    price = 100_000.0,
                    fabricPhotoUrl = "https://example.com/fabric1.jpg",
                    fabricName = "Damask",
                ),
                OrderItem(
                    id = "i2",
                    garmentType = GarmentType.SHIRT,
                    description = "Matching cotton inner",
                    price = 25_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = listOf("https://example.com/style1.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}
```

- [ ] **Step 7: Verify unit tests pass + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL (incl. `ReferenceScrollIndexTest` + the existing `PrimaryCtaResolverTest`).

If detekt flags unused imports (e.g. it no longer finds `REFERENCE_THUMBNAIL_SIZE` references — that constant is removed in Step 1), remove the offending import/constant it names.

- [ ] **Step 8: Verify the iOS build compiles**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (KMP guard — Compose-only change, but always compile iOS before declaring done.)

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt
git commit -m "feat(ptsp-13): render Style and Fabric as side-by-side columns"
```

---

## Task 4: Final verification + push

- [ ] **Step 1: Full verification sweep**

Run: `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Push (codex pre-push hook bypassed for the known infra issue)**

```bash
SKIP_CODEX_REVIEW=1 git push origin HEAD:feature/ptsp-13-style-fabric-grouping
```

Expected: branch updated; PR #113 picks up the new commits and re-runs CI (Cursor Bugbot, build-android, build-ios, detekt).

- [ ] **Step 3: Re-run reviews on the new diff**

Run codex review against main and re-check Cursor Bugbot on the PR (per the review-rotation requirement). Address findings before merge.

- [ ] **Step 4: Manual smoke test (Daniel is QA)**

Per the spec amendment, verify on a real device/sim: steps 8–12 (one-each balanced columns, multi-style scroll + peek + "1/3" pill + tap-to-viewer, asymmetric alignment, overdue pill wraps, light + dark).

---

## Self-review notes

- **Spec coverage:** Amendment item A (side-by-side columns, all states, multi-style scroll+peek+pill) → Tasks 2 & 3. Item B (overdue pill wrap) → Task 1. Non-issue (fabric name) → no task, by design. ✅
- **No new state/action/event** — matches spec ("no new fields"). ✅
- **Type consistency:** `referenceScrollIndex(scrollPx: Int, strideePx: Float, count: Int)` used identically in the test (Task 2) and `MultiReferenceStrip` (Task 3). `ReferenceColumn` / `FabricColumn` / `SingleReferenceTile` / `MultiReferenceStrip` / `ReferencePlaceholder` signatures are each defined once and called consistently. `REFERENCE_TILE_HEIGHT` / `REFERENCE_MULTI_TILE_WIDTH` defined in Step 1, used in Steps 5. ✅
- **Preserved:** offline `localPhotoPath ?: photoUrl` (FabricColumn + the screen-level style mapping in `OrderDetailScreen.kt`, untouched), fabric CTA gating, `FullScreenImageViewer`, all string resources, dark mode. ✅
