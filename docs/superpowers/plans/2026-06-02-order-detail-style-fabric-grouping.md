# Order Detail — Group Style with Fabric Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move style imagery out of the top hero into the Garment details card alongside fabric, turn the top into an image-free order-summary card, and reorder the screen so Garment details sits above Customer.

**Architecture:** This is a Compose-UI refactor inside the existing `feature/order/presentation/detail` package. The only unit-testable logic change is the CTA resolver (`PrimaryCtaResolver`), which becomes the TDD anchor. The composable changes are verified by a successful Android + iOS compile, the `@Preview`s, and the manual smoke test — consistent with this project's test strategy (ViewModel/pure-function unit tests only; no Compose UI test harness).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test, Detekt.

**Spec:** `docs/superpowers/specs/2026-06-02-order-detail-style-fabric-grouping-design.md`

**Working directory:** worktree `.claude/worktrees/feature+ptsp-13-style-fabric-grouping` on branch `worktree-feature+ptsp-13-style-fabric-grouping`.

---

## Baseline (do first)

- [ ] **Step 0: Verify clean baseline**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. If it fails, stop and report — do not start on a red baseline.

---

## Task 1: CTA resolver — make secondary nullable, drop MessageCustomer

The top card's secondary CTA currently falls back to "Message customer" when there's no balance to record. The Customer card already exposes Call + WhatsApp, so a message button up top is redundant. Make the secondary **optional**: `RecordPayment` when a balance is owed, otherwise `null` (the card will render the primary full-width). Remove the `MessageCustomer` value entirely.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolver.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolverTest.kt`

- [ ] **Step 1: Update the failing tests**

Replace the two tests that assert `MessageCustomer` (`ready_offersMarkDeliveredPlusMessage` and `zeroBalance_replacesRecordPaymentWithMessageCustomer`) with these:

```kotlin
    @Test
    fun ready_offersMarkDeliveredNoSecondary() {
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, null),
            cta(OrderStatus.READY, balance = 80_000.0),
        )
    }

    @Test
    fun zeroBalance_dropsSecondary() {
        // With nothing to record and Call/WhatsApp already on the Customer card,
        // the secondary slot is empty — the primary goes full-width.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, null),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.CUTTING, balance = 0.0),
        )
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PrimaryCtaResolverTest*"`
Expected: FAIL — compile error (`MessageCustomer` still referenced) or assertion mismatch.

- [ ] **Step 3: Update the resolver**

In `PrimaryCtaResolver.kt`:

Remove `MessageCustomer` from the enum:

```kotlin
enum class SecondaryCta {
    RecordPayment,
    StartWork,
    UpdateStatus,
    MarkDelivered,
    DuplicateOrder,
}
```

Make the pair's secondary nullable:

```kotlin
data class CtaPair(val primary: PrimaryCta, val secondary: SecondaryCta?)
```

Change the balance fallback and the READY case to `null`:

```kotlin
    val balanceSecondary = if (balanceRemaining > 0.0) {
        SecondaryCta.RecordPayment
    } else {
        null
    }
    return when {
        status == OrderStatus.DELIVERED ->
            CtaPair(PrimaryCta.ShareReceipt, SecondaryCta.DuplicateOrder)
        isOverdue && status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.StartWork)
        isOverdue && status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.UpdateStatus)
        isOverdue && status == OrderStatus.READY ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.MarkDelivered)
        status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.StartWork, balanceSecondary)
        status == OrderStatus.IN_PROGRESS && subStatus == OrderSubStatus.FITTING ->
            CtaPair(PrimaryCta.ConfirmFitting, balanceSecondary)
        status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.UpdateStatus, balanceSecondary)
        status == OrderStatus.READY ->
            CtaPair(PrimaryCta.MarkDelivered, null)
        else -> CtaPair(PrimaryCta.UpdateStatus, balanceSecondary) // unreachable
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PrimaryCtaResolverTest*"`
Expected: PASS (compile of `OrderHeroCard.kt` / `OrderDetailScreen.kt` may still fail — that's fixed in Tasks 2 & 4; run this test module which doesn't pull those in, or proceed and rely on Task 5's full build).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolver.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/PrimaryCtaResolverTest.kt
git commit -m "refactor(ptsp-13): make order CTA secondary optional, drop MessageCustomer"
```

---

## Task 2: OrderHeroCard → image-free summary card

Remove the style image area, swap the headline to the customer name (garment name becomes the subtitle), and render the now-optional secondary CTA (primary goes full-width when secondary is null).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`

- [ ] **Step 1: Remove the `HeroImage` call and the image-viewer state**

In `OrderHeroCard`, delete the `viewerImages`/`viewerStartIndex` state, the `HeroImage(...)` call, and the trailing `FullScreenImageViewer` block. The `Column` should now contain only the padded text/CTA `Column`. Also delete the `styleImageUrls` and `onAddStyleClick` parameters. Result:

```kotlin
@Suppress("LongParameterList", "LongMethod")
@Composable
fun OrderHeroCard(
    garmentTypeIcon: ImageVector,
    garmentName: String,
    customerName: String,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    priority: OrderPriority,
    isOverdue: Boolean,
    overdueDaysAgo: Int,
    dueLabel: UiText?,
    totalPrice: Double,
    balanceRemaining: Double,
    cta: CtaPair,
    onPrimaryCta: () -> Unit,
    onSecondaryCta: () -> Unit,
    onSetDeadlineClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isOverdue) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            HeroDetails(
                garmentTypeIcon = garmentTypeIcon,
                garmentName = garmentName,
                customerName = customerName,
                status = status,
                subStatus = subStatus,
                priority = priority,
                isOverdue = isOverdue,
                dueLabel = dueLabel,
                totalPrice = totalPrice,
                balanceRemaining = balanceRemaining,
                onSetDeadlineClick = onSetDeadlineClick,
            )

            if (isOverdue) {
                OverdueBanner(overdueDaysAgo = overdueDaysAgo)
            }

            CtaRow(
                cta = cta,
                isOverdue = isOverdue,
                onPrimaryCta = onPrimaryCta,
                onSecondaryCta = onSecondaryCta,
            )
        }
    }
}
```

- [ ] **Step 2: Delete the entire `HeroImage` composable**

Remove the whole `private fun HeroImage(...) { ... }` block (the `when` over `styleImageUrls`).

- [ ] **Step 3: Swap title/subtitle in `HeroDetails`**

Add a `garmentTypeIcon: ImageVector` parameter to `HeroDetails` (first param) and replace the first two children of its `Column` so the **customer name is the title** and the **garment name is the subtitle** (with the garment icon as the leading glyph instead of the Person icon):

```kotlin
@Suppress("LongParameterList")
@Composable
private fun HeroDetails(
    garmentTypeIcon: ImageVector,
    garmentName: String,
    customerName: String,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    priority: OrderPriority,
    isOverdue: Boolean,
    dueLabel: UiText?,
    totalPrice: Double,
    balanceRemaining: Double,
    onSetDeadlineClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = customerName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = garmentTypeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = garmentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Status + priority pills (unchanged below) ...
```

Leave everything from the `// Status + priority pills` row downward unchanged.

- [ ] **Step 4: Render the optional secondary in `CtaRow`**

Change `CtaRow` so the `OutlinedButton` only renders when `cta.secondary != null`; otherwise the primary `Button` fills the row. Replace the `OutlinedButton(...) { Text(secondaryCtaLabel(cta.secondary)) }` block with:

```kotlin
        val secondary = cta.secondary
        if (secondary != null) {
            val secondaryContentColor = if (isOverdue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }

            OutlinedButton(
                onClick = onSecondaryCta,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryContentColor),
            ) {
                Text(text = secondaryCtaLabel(secondary))
            }
        }
```

(The primary `Button` already uses `Modifier.weight(1f)`; with the secondary absent it simply expands to fill the row.)

- [ ] **Step 5: Drop the `MessageCustomer` label branch**

In `secondaryCtaLabel`, remove the `SecondaryCta.MessageCustomer -> ...` line. The `when` stays exhaustive over the remaining values.

- [ ] **Step 6: Fix imports and previews**

- Remove now-unused imports: `Icons.Default.Person`, `order_detail_message_customer`, `order_detail_add_style`, `order_detail_style_caption`, and all imports only used by the deleted `HeroImage` (`HorizontalPager`, `rememberPagerState`, `SubcomposeAsyncImage`, `ContentScale`, `LoadingDots`, `FullScreenImageViewer`, `clickable`, `height`, `clip`, `mutableStateOf`/`getValue`/`setValue`/`remember` if unused, `background` if unused, `Color` if unused, `TextButton` if unused). Let the compiler list them.
- In every `@Preview`, delete the `styleImageUrls = ...` and `onAddStyleClick = {}` arguments (the params no longer exist). Leave `garmentTypeIcon` — it now feeds the subtitle.

- [ ] **Step 7: Compile commonMain**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL once `OrderDetailScreen.kt` is updated — if it still references the removed params, that's fixed in Task 4. If you want an isolated check, proceed to Task 4 then build.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt
git commit -m "feat(ptsp-13): turn order hero into image-free summary card (customer headline)"
```

---

## Task 3: OrderGarmentDetailsCard — stacked Style + Fabric strips

Rewrite the card so each garment shows a labeled **Style** strip (order-level, rendered once under the first item) above a labeled **Fabric** strip, replacing the right-column fabric tile. Reuse a single `ReferenceSection` composable for both. This is a full-file replacement.

**Files:**
- Modify (replace contents): `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt`

- [ ] **Step 1: Replace the file with the new layout**

```kotlin
package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_add_fabric
import stitchpad.composeapp.generated.resources.order_detail_add_fabric_name
import stitchpad.composeapp.generated.resources.order_detail_add_style
import stitchpad.composeapp.generated.resources.order_detail_fabric_caption
import stitchpad.composeapp.generated.resources.order_detail_garment_section
import stitchpad.composeapp.generated.resources.order_detail_style_caption
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill

private val REFERENCE_THUMBNAIL_SIZE = 64.dp

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    styleImageUrls: List<String>,
    onAddStyleClick: () -> Unit,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewerImages: List<String> by remember { mutableStateOf(emptyList()) }
    var viewerStartIndex: Int by remember { mutableIntStateOf(0) }
    val openViewer: (List<String>, Int) -> Unit = { urls, startIdx ->
        viewerImages = urls
        viewerStartIndex = startIdx
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space4,
                bottom = DesignTokens.space4,
            ),
        ) {
            SectionHeader()
            Spacer(Modifier.height(DesignTokens.space3))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(DesignTokens.space4))
                }

                GarmentTextBlock(
                    item = item,
                    priority = if (index == 0) priority else OrderPriority.NORMAL,
                )

                // Style is order-level — render it once, under the first garment.
                if (index == 0) {
                    Spacer(Modifier.height(DesignTokens.space3))
                    ReferenceSection(
                        label = stringResource(Res.string.order_detail_style_caption),
                        icon = Icons.Default.Checkroom,
                        urls = styleImageUrls,
                        ctaLabel = if (styleImageUrls.isEmpty()) Res.string.order_detail_add_style else null,
                        onCtaClick = onAddStyleClick,
                        onImageClick = openViewer,
                    )
                }

                Spacer(Modifier.height(DesignTokens.space3))
                FabricSection(
                    item = item,
                    showCta = items.indexOfFirst { needsFabricInfo(it) } == index,
                    onAddFabricPhotoClick = onAddFabricPhotoClick,
                    onAddFabricNameClick = onAddFabricNameClick,
                    onImageClick = openViewer,
                )
            }
        }
    }

    if (viewerImages.isNotEmpty()) {
        FullScreenImageViewer(
            images = viewerImages,
            startIndex = viewerStartIndex,
            contentDescription = null,
            onDismiss = { viewerImages = emptyList() },
        )
    }
}

@Composable
private fun SectionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        SectionIconTile(imageVector = Icons.Default.Checkroom, contentDescription = null)
        Text(
            text = stringResource(Res.string.order_detail_garment_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GarmentTextBlock(
    item: OrderItem,
    priority: OrderPriority,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = garmentDisplayName(item),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (priority != OrderPriority.NORMAL) {
            PriorityPill(priority = priority)
        }
    }
    if (!item.fabricName.isNullOrBlank()) {
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = item.fabricName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (item.description.isNotBlank()) {
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FabricSection(
    item: OrderItem,
    showCta: Boolean,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val urls = when {
        item.fabricImages.isNotEmpty() -> item.fabricImages.map { it.photoUrl }
        !item.fabricPhotoUrl.isNullOrBlank() -> listOf(item.fabricPhotoUrl)
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

    ReferenceSection(
        label = stringResource(Res.string.order_detail_fabric_caption),
        icon = Icons.Default.Texture,
        urls = urls,
        ctaLabel = ctaLabel,
        onCtaClick = onCtaClick,
        onImageClick = onImageClick,
    )
}

/**
 * A labeled reference block: a mini header (icon + label), a horizontal strip of
 * 64dp thumbnails (or a single placeholder tile when empty), and an optional
 * "Add …" text CTA below. Used for both style and fabric so the two read as peers.
 */
@Composable
private fun ReferenceSection(
    label: String,
    icon: ImageVector,
    urls: List<String>,
    ctaLabel: StringResource?,
    onCtaClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
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

    if (urls.isEmpty()) {
        ReferencePlaceholder(onClick = if (ctaLabel != null) onCtaClick else null)
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            urls.forEachIndexed { index, url ->
                ReferenceThumbnail(url = url, onClick = { onImageClick(urls, index) })
            }
        }
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

@Composable
private fun ReferenceThumbnail(
    url: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(REFERENCE_THUMBNAIL_SIZE)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            .clickable { onClick() },
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingDots(dotSize = 6.dp)
                }
            },
            modifier = Modifier
                .size(REFERENCE_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(DesignTokens.radiusMd)),
        )
    }
}

@Composable
private fun ReferencePlaceholder(onClick: (() -> Unit)?) {
    val base = Modifier
        .size(REFERENCE_THUMBNAIL_SIZE)
        .background(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
        )
    Box(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Texture,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(DesignTokens.iconFeature),
        )
    }
}

@Composable
private fun PriorityPill(priority: OrderPriority) {
    val (pillColor, labelRes) = when (priority) {
        OrderPriority.URGENT -> DesignTokens.warning500 to Res.string.order_priority_high_pill
        OrderPriority.RUSH -> DesignTokens.error500 to Res.string.order_priority_rush_pill
        OrderPriority.NORMAL -> return
    }
    Surface(
        shape = CircleShape,
        color = pillColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = pillColor,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space1,
            ),
        )
    }
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Returns true when an item is still missing fabric photo or fabric name. */
private fun needsFabricInfo(item: OrderItem): Boolean {
    val hasPhoto = item.fabricImages.isNotEmpty() || !item.fabricPhotoUrl.isNullOrBlank()
    return !hasPhoto || item.fabricName.isNullOrBlank()
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardEmptyPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                com.danzucker.stitchpad.core.domain.model.OrderItem(
                    id = "i1",
                    garmentType = com.danzucker.stitchpad.core.domain.model.GarmentType.AGBADA,
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
private fun OrderGarmentDetailsCardPopulatedPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                com.danzucker.stitchpad.core.domain.model.OrderItem(
                    id = "i1",
                    garmentType = com.danzucker.stitchpad.core.domain.model.GarmentType.SHIRT,
                    description = "Ankara print",
                    price = 60_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Royal Lace",
                ),
            ),
            priority = OrderPriority.URGENT,
            styleImageUrls = listOf("https://example.com/style1.jpg", "https://example.com/style2.jpg"),
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
                com.danzucker.stitchpad.core.domain.model.OrderItem(
                    id = "i1",
                    garmentType = com.danzucker.stitchpad.core.domain.model.GarmentType.SENATOR,
                    description = "White lace",
                    price = 85_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = null,
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

// endregion
```

> Note: the previews reference `OrderItem`/`GarmentType` by fully-qualified name to keep imports minimal; if you prefer, add the two imports and shorten them. Either compiles.

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderGarmentDetailsCard.kt
git commit -m "feat(ptsp-13): show style + fabric as stacked strips in garment details"
```

---

## Task 4: Wire OrderDetailScreen — reorder sections + pass style into garment card

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt`

- [ ] **Step 1: Update the summary card item (remove style args)**

In `OrderDetailContent`, the first `item { ... }` block: keep the `styleImageUrls` derivation (you'll move it to the garment card), but update the `OrderHeroCard(...)` call to drop `styleImageUrls` and `onAddStyleClick`. Move the `firstItem`/`styleImageUrls` computation up so both items can use it. Replace the first two `item { }` blocks (summary, then customer) **and** the garment block so the order becomes summary → garment → customer:

```kotlin
        item {
            OrderHeroCard(
                garmentTypeIcon = Icons.Default.Checkroom,
                garmentName = garmentName,
                customerName = order.customerName,
                status = order.status,
                subStatus = order.subStatus,
                priority = order.priority,
                isOverdue = isOverdue,
                overdueDaysAgo = overdueDaysAgo,
                dueLabel = dueLabel,
                totalPrice = order.totalPrice,
                balanceRemaining = order.balanceRemaining,
                cta = cta,
                onPrimaryCta = { handlePrimaryCta(cta.primary, onAction) },
                onSecondaryCta = { handleSecondaryCta(cta.secondary, onAction) },
                onSetDeadlineClick = { onAction(OrderDetailAction.OnSetDeadlineClick) },
            )
        }
        item {
            val styleImageUrls: List<String> = firstItem?.styleImages.orEmpty().mapNotNull { ref ->
                when (ref.source) {
                    StyleImageSource.LIBRARY -> state.styles[ref.styleId]?.photoUrl
                    StyleImageSource.UPLOADED -> ref.photoUrl
                }
            }
            OrderGarmentDetailsCard(
                items = order.items,
                priority = order.priority,
                styleImageUrls = styleImageUrls,
                onAddStyleClick = { onAction(OrderDetailAction.OnAddStyleClick) },
                onAddFabricPhotoClick = { onAction(OrderDetailAction.OnAddFabricClick) },
                onAddFabricNameClick = { onAction(OrderDetailAction.OnAddFabricNameClick) },
            )
        }
        item {
            OrderCustomerCard(
                customerName = order.customerName,
                phone = state.customer?.phone,
                customerCreatedAt = state.customer?.createdAt,
                onWhatsAppClick = { onAction(OrderDetailAction.OnWhatsAppClick) },
                onCallClick = { onAction(OrderDetailAction.OnCallClick) },
                onAddPhoneClick = { onAction(OrderDetailAction.OnAddPhoneClick) },
                onCustomerClick = { onAction(OrderDetailAction.OnCustomerClick) },
            )
        }
```

> `firstItem` is already declared above (`val firstItem = order.items.firstOrNull()` at line ~823). Use it; delete the old shadowing `val firstItem = state.order?.items?.firstOrNull()` that lived inside the former hero `item {}`. The old garment-details `item {}` (formerly third) is now removed — it has been merged above. Verify there is exactly one `OrderGarmentDetailsCard` call and one `OrderCustomerCard` call after the edit.

- [ ] **Step 2: Update `handleSecondaryCta` for the nullable secondary**

Change its signature to accept `SecondaryCta?` and no-op on null; remove the `MessageCustomer` branch:

```kotlin
private fun handleSecondaryCta(cta: SecondaryCta?, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        null -> Unit
        SecondaryCta.RecordPayment -> onAction(OrderDetailAction.OnRecordPaymentClick)
        SecondaryCta.StartWork,
        SecondaryCta.UpdateStatus,
        SecondaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        SecondaryCta.DuplicateOrder -> onAction(OrderDetailAction.OnDuplicateClick)
    }
}
```

> Only the `null ->` branch is added and the `MessageCustomer ->` branch removed; every other branch keeps its current action (`OnDuplicateClick` for `DuplicateOrder`).

- [ ] **Step 3: Remove now-unused imports**

If `StyleImageSource` is still used (it is — moved into the garment `item`), keep it. Remove any import that is now unused (e.g. if `Icons.Default.Checkroom` was only used here it stays — it's still used). Let the compiler guide.

- [ ] **Step 4: Build Android**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(ptsp-13): reorder order detail — garment details above customer, style into card"
```

---

## Task 5: Verify everything — tests, detekt, iOS

**Files:** none (verification only).

- [ ] **Step 1: Run the unit tests**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the updated `PrimaryCtaResolverTest`).

- [ ] **Step 2: Run detekt (format first if needed)**

Run: `./gradlew detekt`
Expected: no violations. If formatting fails, invoke the `format` skill (detekt ktlint formatter) and re-run.

- [ ] **Step 3: Compile iOS (KMP guard)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (Per project memory, always compile iOS before declaring done — JVM-only APIs and Native quirks won't surface on Android.)

- [ ] **Step 4: Manual smoke test (Daniel is QA)**

Walk the spec's smoke-test checklist on a simulator/device:
1. Order with no style + no fabric → top card shows **customer headline** + garment·status subtitle, **no image box**; Garment details shows a **Style** strip ("Add style") above a **Fabric** strip ("Add fabric photo").
2. Tap "Add style" → existing StylePickerSheet opens; link/create a style → appears in the **Style** strip in Garment details (not at the top).
3. Add a fabric photo → appears in the **Fabric** strip directly below Style; then "Add fabric name" CTA appears.
4. Section order: summary → **Garment details** → Customer → Payment → Timeline → Measurements → Notes.
5. Secondary CTA reads **Record payment** when a balance is owed; a fully-paid / READY order shows a **single full-width primary** button (no Message button).
6. Multi-item order: Style strip shows **once**; each item keeps its own Fabric strip.
7. Verify in **light and dark** mode.

- [ ] **Step 5: Final commit (if smoke test prompted tweaks)**

```bash
git add -A
git commit -m "chore(ptsp-13): smoke-test fixes + cleanup"
```

---

## Self-Review notes

- **Spec coverage:** summary-card rework (Task 2), style+fabric grouping (Task 3), section reorder (Task 4), Record-payment secondary / no Message (Tasks 1+2+4), smoke test (Task 5). All spec sections mapped.
- **Type consistency:** `CtaPair.secondary: SecondaryCta?` is introduced in Task 1 and consumed consistently in Task 2 (`CtaRow`/`secondaryCtaLabel`) and Task 4 (`handleSecondaryCta`). `ReferenceSection`/`FabricSection`/`GarmentTextBlock` are defined and called within Task 3 only. `OrderGarmentDetailsCard`'s new params (`styleImageUrls`, `onAddStyleClick`) are defined in Task 3 and supplied in Task 4.
- **No new strings:** reuses `order_detail_style_caption` ("Style"), `order_detail_fabric_caption` ("Fabric"), `order_detail_add_style`, `order_detail_add_fabric`, `order_detail_add_fabric_name`.
