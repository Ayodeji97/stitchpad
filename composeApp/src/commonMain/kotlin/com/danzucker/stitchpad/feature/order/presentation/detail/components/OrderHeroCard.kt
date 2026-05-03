package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.order.presentation.detail.CtaPair
import com.danzucker.stitchpad.feature.order.presentation.detail.PrimaryCta
import com.danzucker.stitchpad.feature.order.presentation.detail.SecondaryCta
import com.danzucker.stitchpad.feature.order.presentation.detail.resolvePrimaryCta
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_balance_due
import stitchpad.composeapp.generated.resources.order_detail_confirm_fitting
import stitchpad.composeapp.generated.resources.order_detail_duplicate_order
import stitchpad.composeapp.generated.resources.order_detail_fabric_caption
import stitchpad.composeapp.generated.resources.order_detail_mark_delivered
import stitchpad.composeapp.generated.resources.order_detail_message_customer
import stitchpad.composeapp.generated.resources.order_detail_overdue_banner
import stitchpad.composeapp.generated.resources.order_detail_send_reminder
import stitchpad.composeapp.generated.resources.order_detail_share_receipt
import stitchpad.composeapp.generated.resources.order_detail_start_work
import stitchpad.composeapp.generated.resources.order_detail_update_status
import stitchpad.composeapp.generated.resources.order_overdue_label
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill
import stitchpad.composeapp.generated.resources.order_record_payment_button
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready

@Suppress("LongParameterList", "LongMethod")
@Composable
fun OrderHeroCard(
    fabricPhotoUrl: String?,
    garmentTypeIcon: ImageVector,
    garmentName: String,
    customerName: String,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    priority: OrderPriority,
    isOverdue: Boolean,
    overdueDaysAgo: Int,
    dueLabel: UiText,
    balanceRemaining: Double,
    cta: CtaPair,
    onPrimaryCta: () -> Unit,
    onSecondaryCta: () -> Unit,
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
            // ── Hero row ──────────────────────────────────────────────────────
            HeroRow(
                fabricPhotoUrl = fabricPhotoUrl,
                garmentTypeIcon = garmentTypeIcon,
                garmentName = garmentName,
                customerName = customerName,
                status = status,
                subStatus = subStatus,
                priority = priority,
                isOverdue = isOverdue,
                dueLabel = dueLabel,
                balanceRemaining = balanceRemaining,
            )

            // ── Overdue banner ────────────────────────────────────────────────
            if (isOverdue) {
                OverdueBanner(overdueDaysAgo = overdueDaysAgo)
            }

            // ── Dual CTA row ──────────────────────────────────────────────────
            CtaRow(
                cta = cta,
                isOverdue = isOverdue,
                onPrimaryCta = onPrimaryCta,
                onSecondaryCta = onSecondaryCta,
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun HeroRow(
    fabricPhotoUrl: String?,
    garmentTypeIcon: ImageVector,
    garmentName: String,
    customerName: String,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    priority: OrderPriority,
    isOverdue: Boolean,
    dueLabel: UiText,
    balanceRemaining: Double,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Fabric thumbnail ──────────────────────────────────────────────────
        FabricThumbnail(
            fabricPhotoUrl = fabricPhotoUrl,
            garmentTypeIcon = garmentTypeIcon,
            garmentName = garmentName,
        )

        // ── Text column ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = garmentName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Status + priority pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                StatusPill(status = status, subStatus = subStatus, isOverdue = isOverdue)
                if (priority != OrderPriority.NORMAL) {
                    PriorityPill(priority = priority)
                }
            }

            // Due / balance row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                DueDateSection(dueLabel = dueLabel, isOverdue = isOverdue, status = status)
                BalanceSection(balanceRemaining = balanceRemaining, isOverdue = isOverdue)
            }
        }
    }
}

@Composable
private fun FabricThumbnail(
    fabricPhotoUrl: String?,
    garmentTypeIcon: ImageVector,
    garmentName: String,
) {
    if (fabricPhotoUrl != null) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ),
        ) {
            SubcomposeAsyncImage(
                model = fabricPhotoUrl,
                contentDescription = garmentName,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LoadingDots()
                    }
                },
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
            )
            // Caption pill at bottom
            val fabricCaption = stringResource(Res.string.order_detail_fabric_caption)
            Text(
                text = fabricCaption,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = garmentTypeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    isOverdue: Boolean,
) {
    val pillData = if (isOverdue) {
        StatusPillData(
            bg = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            fg = MaterialTheme.colorScheme.error,
            icon = Icons.Default.EventBusy,
            label = stringResource(Res.string.order_overdue_label),
        )
    } else {
        when (status) {
            OrderStatus.PENDING -> StatusPillData(
                bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                fg = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.HourglassTop,
                label = stringResource(Res.string.order_status_pending),
            )
            OrderStatus.IN_PROGRESS -> StatusPillData(
                bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                fg = MaterialTheme.colorScheme.primary,
                icon = if (subStatus == OrderSubStatus.FITTING) Icons.Default.Build else Icons.Default.Build,
                label = stringResource(Res.string.order_status_in_progress),
            )
            OrderStatus.READY -> StatusPillData(
                bg = DesignTokens.success500.copy(alpha = 0.15f),
                fg = DesignTokens.success500,
                icon = Icons.Default.Inventory2,
                label = stringResource(Res.string.order_status_ready),
            )
            OrderStatus.DELIVERED -> StatusPillData(
                bg = DesignTokens.success500.copy(alpha = 0.15f),
                fg = DesignTokens.success500,
                icon = Icons.Default.CheckCircle,
                label = stringResource(Res.string.order_status_delivered),
            )
        }
    }

    Surface(
        shape = CircleShape,
        color = pillData.bg,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        ) {
            Icon(
                imageVector = pillData.icon,
                contentDescription = null,
                tint = pillData.fg,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = pillData.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = pillData.fg,
            )
        }
    }
}

private data class StatusPillData(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun PriorityPill(priority: OrderPriority) {
    val (bgColor, fgColor, label) = when (priority) {
        OrderPriority.URGENT -> Triple(
            DesignTokens.warning500.copy(alpha = 0.15f),
            DesignTokens.warning500,
            stringResource(Res.string.order_priority_high_pill),
        )
        OrderPriority.RUSH -> Triple(
            DesignTokens.error500.copy(alpha = 0.15f),
            DesignTokens.error500,
            stringResource(Res.string.order_priority_rush_pill),
        )
        OrderPriority.NORMAL -> return
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        )
    }
}

@Composable
private fun DueDateSection(
    dueLabel: UiText,
    isOverdue: Boolean,
    status: OrderStatus,
) {
    val dateColor = if (isOverdue) DesignTokens.error500 else MaterialTheme.colorScheme.onSurfaceVariant
    val dateIcon = when {
        isOverdue -> Icons.Default.EventBusy
        status == OrderStatus.DELIVERED -> Icons.Default.EventAvailable
        else -> Icons.Default.Event
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = dateIcon,
            contentDescription = null,
            tint = dateColor,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = dueLabel.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = dateColor,
        )
    }
}

@Composable
private fun BalanceSection(
    balanceRemaining: Double,
    isOverdue: Boolean,
) {
    val balanceColor = when {
        isOverdue && balanceRemaining > 0.0 -> DesignTokens.error500
        balanceRemaining == 0.0 -> DesignTokens.success500
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(Res.string.order_detail_balance_due),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "₦${formatPrice(balanceRemaining)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = balanceColor,
        )
    }
}

@Composable
private fun OverdueBanner(overdueDaysAgo: Int) {
    val daysText = if (overdueDaysAgo == 1) "1 day" else "$overdueDaysAgo days"
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = DesignTokens.error500,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(Res.string.order_detail_overdue_banner, daysText),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = DesignTokens.error500,
            )
        }
    }
}

@Composable
private fun CtaRow(
    cta: CtaPair,
    isOverdue: Boolean,
    onPrimaryCta: () -> Unit,
    onSecondaryCta: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        val primaryContainerColor = if (isOverdue) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

        Button(
            onClick = onPrimaryCta,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryContainerColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            val label = primaryCtaLabel(cta.primary)
            when (cta.primary) {
                PrimaryCta.ShareReceipt -> {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                PrimaryCta.SendReminder -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                else -> Unit
            }
            Text(text = label)
        }

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
            Text(text = secondaryCtaLabel(cta.secondary))
        }
    }
}

@Composable
private fun primaryCtaLabel(cta: PrimaryCta): String = when (cta) {
    PrimaryCta.StartWork -> stringResource(Res.string.order_detail_start_work)
    PrimaryCta.UpdateStatus -> stringResource(Res.string.order_detail_update_status)
    PrimaryCta.ConfirmFitting -> stringResource(Res.string.order_detail_confirm_fitting)
    PrimaryCta.MarkDelivered -> stringResource(Res.string.order_detail_mark_delivered)
    PrimaryCta.ShareReceipt -> stringResource(Res.string.order_detail_share_receipt)
    PrimaryCta.SendReminder -> stringResource(Res.string.order_detail_send_reminder)
}

@Composable
private fun secondaryCtaLabel(cta: SecondaryCta): String = when (cta) {
    SecondaryCta.RecordPayment -> stringResource(Res.string.order_record_payment_button)
    SecondaryCta.MessageCustomer -> stringResource(Res.string.order_detail_message_customer)
    SecondaryCta.StartWork -> stringResource(Res.string.order_detail_start_work)
    SecondaryCta.UpdateStatus -> stringResource(Res.string.order_detail_update_status)
    SecondaryCta.MarkDelivered -> stringResource(Res.string.order_detail_mark_delivered)
    SecondaryCta.DuplicateOrder -> stringResource(Res.string.order_detail_duplicate_order)
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardInProgressLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            fabricPhotoUrl = null,
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Vintage Buba",
            customerName = "Adewale Paul",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.URGENT,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 30 Apr"),
            balanceRemaining = 60000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = false,
                balanceRemaining = 60000.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardReadyLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            fabricPhotoUrl = null,
            garmentTypeIcon = Icons.Default.Inventory2,
            garmentName = "Senator Outfit",
            customerName = "Chukwuemeka Nwosu",
            status = OrderStatus.READY,
            subStatus = null,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 30 Apr"),
            balanceRemaining = 25000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.READY,
                subStatus = null,
                isOverdue = false,
                balanceRemaining = 25000.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardFittingLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            fabricPhotoUrl = null,
            garmentTypeIcon = Icons.Default.Person,
            garmentName = "Agbada Set",
            customerName = "Tunde Bakare",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.FITTING,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Fitting today"),
            balanceRemaining = 40000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.FITTING,
                isOverdue = false,
                balanceRemaining = 40000.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardOverdueLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            fabricPhotoUrl = null,
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Kaftan",
            customerName = "Blessing Okafor",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.RUSH,
            isOverdue = true,
            overdueDaysAgo = 3,
            dueLabel = UiText.DynamicString("Was due 27 Apr"),
            balanceRemaining = 18000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = true,
                balanceRemaining = 18000.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardDeliveredDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderHeroCard(
            fabricPhotoUrl = null,
            garmentTypeIcon = Icons.Default.CheckCircle,
            garmentName = "Bridal Gown",
            customerName = "Amaka Eze",
            status = OrderStatus.DELIVERED,
            subStatus = null,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Delivered 28 Apr"),
            balanceRemaining = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.DELIVERED,
                subStatus = null,
                isOverdue = false,
                balanceRemaining = 0.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
        )
    }
}

// endregion
