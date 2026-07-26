@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.order.presentation.detail.CtaPair
import com.danzucker.stitchpad.feature.order.presentation.detail.PrimaryCta
import com.danzucker.stitchpad.feature.order.presentation.detail.SecondaryCta
import com.danzucker.stitchpad.feature.order.presentation.detail.paymentProgress
import com.danzucker.stitchpad.feature.order.presentation.detail.resolvePrimaryCta
import com.danzucker.stitchpad.ui.components.StrikethroughPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.action_call
import stitchpad.composeapp.generated.resources.action_whatsapp
import stitchpad.composeapp.generated.resources.order_detail_add_phone
import stitchpad.composeapp.generated.resources.order_detail_balance_due
import stitchpad.composeapp.generated.resources.order_detail_edit_deadline
import stitchpad.composeapp.generated.resources.order_detail_mark_delivered
import stitchpad.composeapp.generated.resources.order_detail_overdue_banner
import stitchpad.composeapp.generated.resources.order_detail_overdue_days_one
import stitchpad.composeapp.generated.resources.order_detail_overdue_days_other
import stitchpad.composeapp.generated.resources.order_detail_paid_amount
import stitchpad.composeapp.generated.resources.order_detail_paid_percent
import stitchpad.composeapp.generated.resources.order_detail_send_reminder
import stitchpad.composeapp.generated.resources.order_detail_set_deadline
import stitchpad.composeapp.generated.resources.order_detail_share_receipt
import stitchpad.composeapp.generated.resources.order_detail_start_work
import stitchpad.composeapp.generated.resources.order_detail_total_price
import stitchpad.composeapp.generated.resources.order_detail_update_status
import stitchpad.composeapp.generated.resources.order_overdue_label
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill
import stitchpad.composeapp.generated.resources.order_record_payment_button
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_sewing
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready

private val WHATSAPP_GREEN = Color(0xFF25D366)

/** Minimum fill shown when nothing is paid, so the amber "collect a deposit" sliver is visible. */
private const val ZERO_PAID_SLIVER = 0.04f

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
    discount: Double,
    cta: CtaPair,
    phone: String?,
    onPrimaryCta: () -> Unit,
    onSecondaryCta: () -> Unit,
    onSetDeadlineClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onAddPhoneClick: () -> Unit,
    onCustomerClick: () -> Unit,
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
                discount = discount,
                phone = phone,
                onSetDeadlineClick = onSetDeadlineClick,
                onWhatsAppClick = onWhatsAppClick,
                onCallClick = onCallClick,
                onAddPhoneClick = onAddPhoneClick,
                onCustomerClick = onCustomerClick,
            )

            if (isOverdue) {
                OverdueBanner(overdueDaysAgo = overdueDaysAgo)
            }

            CtaRow(
                cta = cta,
                status = status,
                subStatus = subStatus,
                isOverdue = isOverdue,
                onPrimaryCta = onPrimaryCta,
                onSecondaryCta = onSecondaryCta,
            )
        }
    }
}

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
    discount: Double,
    phone: String?,
    onSetDeadlineClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onAddPhoneClick: () -> Unit,
    onCustomerClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Customer identity — tappable to open the full customer profile
        // (a trailing chevron makes that affordance discoverable). Replaces the
        // separate Customer card, so the name isn't shown twice on this screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                .clickable(onClick = onCustomerClick, role = Role.Button),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = customerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
        }

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

        // Reach-out actions folded in from the old Customer card.
        if (phone.isNullOrBlank()) {
            AddPhoneCta(onClick = onAddPhoneClick)
        } else {
            ReachOutChips(onWhatsAppClick = onWhatsAppClick, onCallClick = onCallClick)
        }

        // Unified payment block — folds the deadline, the balance, the order
        // total and a paid-so-far progress bar into one grouped surface. Replaces
        // the old split "Total" line + Due/Balance row, which showed the same
        // figure twice on unpaid orders and never surfaced how much had been
        // collected. When no deadline is set the left slot becomes a "Set
        // deadline" CTA (the empty-state pattern used elsewhere in the redesign).
        PaymentBlock(
            dueLabel = dueLabel,
            isOverdue = isOverdue,
            status = status,
            totalPrice = totalPrice,
            balanceRemaining = balanceRemaining,
            discount = discount,
            onSetDeadlineClick = onSetDeadlineClick,
        )
    }
}

@Composable
private fun PaymentBlock(
    dueLabel: UiText?,
    isOverdue: Boolean,
    status: OrderStatus,
    totalPrice: Double,
    balanceRemaining: Double,
    discount: Double,
    onSetDeadlineClick: () -> Unit,
) {
    val progress = paymentProgress(totalPrice, balanceRemaining, discount)
    // Nothing owed reads as settled — including free / fully-discounted / unpriced
    // (₦0 net) orders, where there is no deposit to collect. Matches the green ₦0
    // balance figure below; without the netTotal guard those orders wrongly showed
    // the amber "collect a deposit" sliver.
    val fullyPaid = balanceRemaining <= 0.0
    val percent = if (fullyPaid) 100 else (progress.fraction * 100).toInt()

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space1),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (dueLabel != null) {
                    DueDateSection(
                        dueLabel = dueLabel,
                        isOverdue = isOverdue,
                        status = status,
                        onEditClick = onSetDeadlineClick,
                    )
                } else {
                    SetDeadlineCta(onClick = onSetDeadlineClick)
                }
                BalanceSection(
                    balanceRemaining = balanceRemaining,
                    netTotal = progress.netTotal,
                    grossTotal = totalPrice,
                    discount = discount,
                    isOverdue = isOverdue,
                )
            }

            PaymentTrack(fraction = progress.fraction, fullyPaid = fullyPaid)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        Res.string.order_detail_paid_amount,
                        "₦${formatPrice(progress.paid)}",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Bake the "%" into the arg — Compose's resource formatter doesn't
                    // collapse a literal "%%" in the string, so it renders "40%%".
                    text = stringResource(
                        Res.string.order_detail_paid_percent,
                        "$percent%",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentTrack(fraction: Float, fullyPaid: Boolean) {
    // Progress fill tells the payment story at a glance: green when settled, indigo
    // while partly paid, and a thin amber sliver at zero to nudge collecting a deposit.
    val fillColor = when {
        fullyPaid -> DesignTokens.success500
        fraction > 0f -> MaterialTheme.colorScheme.primary
        else -> DesignTokens.warning500
    }
    val fillFraction = when {
        fullyPaid -> 1f
        fraction <= 0f -> ZERO_PAID_SLIVER
        else -> fraction
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .background(fillColor),
        )
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
                icon = if (subStatus == OrderSubStatus.FITTING) Icons.Default.Accessibility else Icons.Default.Build,
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
    onEditClick: () -> Unit,
) {
    val dateColor = if (isOverdue) DesignTokens.error500 else MaterialTheme.colorScheme.onSurfaceVariant
    val dateIcon = when {
        isOverdue -> Icons.Default.EventBusy
        status == OrderStatus.DELIVERED -> Icons.Default.EventAvailable
        else -> Icons.Default.Event
    }
    val editLabel = stringResource(Res.string.order_detail_edit_deadline)

    // The whole due-date row is tappable so a deadline can be changed after it's
    // been set (previously the date was read-only and only the empty-state CTA
    // opened the picker). A trailing pencil makes the affordance discoverable.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .clickable(onClick = onEditClick, onClickLabel = editLabel, role = Role.Button)
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
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
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun SetDeadlineCta(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(Res.string.order_detail_set_deadline),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BalanceSection(
    balanceRemaining: Double,
    netTotal: Double,
    grossTotal: Double,
    discount: Double,
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
        // Order total caption. StrikethroughPrice keeps the discount visible on the
        // hero (struck gross next to the net) when one applies; with no discount it
        // renders a single "Total ₦x".
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_detail_total_price),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StrikethroughPrice(
                grossPrice = grossTotal,
                netPrice = netTotal,
                discount = discount,
                netStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                netColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReachOutChips(
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        AssistChip(
            onClick = onWhatsAppClick,
            label = {
                Text(
                    text = stringResource(Res.string.action_whatsapp),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(DesignTokens.iconInline),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = WHATSAPP_GREEN,
                leadingIconContentColor = WHATSAPP_GREEN,
            ),
        )
        AssistChip(
            onClick = onCallClick,
            label = {
                Text(
                    text = stringResource(Res.string.action_call),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(DesignTokens.iconInline),
                )
            },
        )
    }
}

@Composable
private fun AddPhoneCta(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text(
            text = stringResource(Res.string.order_detail_add_phone),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OverdueBanner(overdueDaysAgo: Int) {
    val daysText = if (overdueDaysAgo == 1) {
        stringResource(Res.string.order_detail_overdue_days_one)
    } else {
        stringResource(Res.string.order_detail_overdue_days_other, overdueDaysAgo)
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
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
    status: OrderStatus,
    subStatus: OrderSubStatus?,
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
            val label = primaryCtaLabel(cta.primary, status, subStatus)
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
                if (secondary == SecondaryCta.ShareReceipt) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(text = secondaryCtaLabel(secondary))
            }
        }
    }
}

@Composable
private fun primaryCtaLabel(
    cta: PrimaryCta,
    status: OrderStatus,
    subStatus: OrderSubStatus?,
): String = when (cta) {
    PrimaryCta.StartWork -> stringResource(Res.string.order_detail_start_work)
    // PTSP-28: while in progress, the button reflects the CURRENT stage
    // (Cutting / Sewing / Fitting) instead of a generic "Update Status" —
    // testers couldn't tell which stage they'd moved to. Tapping still opens
    // the same status picker. ConfirmFitting collapses into this too: at the
    // fitting stage the button reads "Fitting".
    PrimaryCta.UpdateStatus,
    PrimaryCta.ConfirmFitting ->
        if (status == OrderStatus.IN_PROGRESS) {
            inProgressStageLabel(subStatus)
        } else {
            stringResource(Res.string.order_detail_update_status)
        }
    PrimaryCta.MarkDelivered -> stringResource(Res.string.order_detail_mark_delivered)
    PrimaryCta.ShareReceipt -> stringResource(Res.string.order_detail_share_receipt)
    PrimaryCta.SendReminder -> stringResource(Res.string.order_detail_send_reminder)
}

@Composable
private fun inProgressStageLabel(subStatus: OrderSubStatus?): String = when (subStatus) {
    OrderSubStatus.SEWING -> stringResource(Res.string.order_stage_sewing)
    OrderSubStatus.FITTING -> stringResource(Res.string.order_stage_fitting)
    // CUTTING is the entry sub-stage; a null sub-status on an in-progress order
    // means work just started, which is the cutting stage.
    OrderSubStatus.CUTTING, null -> stringResource(Res.string.order_stage_cutting)
}

@Composable
private fun secondaryCtaLabel(cta: SecondaryCta): String = when (cta) {
    SecondaryCta.RecordPayment -> stringResource(Res.string.order_record_payment_button)
    SecondaryCta.StartWork -> stringResource(Res.string.order_detail_start_work)
    SecondaryCta.UpdateStatus -> stringResource(Res.string.order_detail_update_status)
    SecondaryCta.MarkDelivered -> stringResource(Res.string.order_detail_mark_delivered)
    SecondaryCta.ShareReceipt -> stringResource(Res.string.order_detail_share_receipt)
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardInProgressLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Vintage Buba",
            customerName = "Adewale Paul",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.URGENT,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 30 Apr"),
            totalPrice = 75000.0,
            balanceRemaining = 60000.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = false,
                balanceRemaining = 60000.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardDiscountedLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Vintage Buba",
            customerName = "Adewale Paul",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.URGENT,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 30 Apr"),
            totalPrice = 380_000.0,
            balanceRemaining = 310_000.0,
            discount = 30_000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = false,
                balanceRemaining = 310_000.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardReadyLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Inventory2,
            garmentName = "Senator Outfit",
            customerName = "Chukwuemeka Nwosu",
            status = OrderStatus.READY,
            subStatus = null,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 30 Apr"),
            totalPrice = 30000.0,
            balanceRemaining = 25000.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.READY,
                subStatus = null,
                isOverdue = false,
                balanceRemaining = 25000.0,
            ),
            phone = null,
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardFittingLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Agbada Set",
            customerName = "Tunde Bakare",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.FITTING,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Fitting today"),
            totalPrice = 40000.0,
            balanceRemaining = 40000.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.FITTING,
                isOverdue = false,
                balanceRemaining = 40000.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardOverdueLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Kaftan",
            customerName = "Blessing Okafor",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.RUSH,
            isOverdue = true,
            overdueDaysAgo = 3,
            dueLabel = UiText.DynamicString("Was due 27 Apr"),
            totalPrice = 35000.0,
            balanceRemaining = 18000.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = true,
                balanceRemaining = 18000.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardDeliveredDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.CheckCircle,
            garmentName = "Bridal Gown",
            customerName = "Amaka Eze",
            status = OrderStatus.DELIVERED,
            subStatus = null,
            priority = OrderPriority.NORMAL,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Delivered 28 Apr"),
            totalPrice = 80000.0,
            balanceRemaining = 0.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.DELIVERED,
                subStatus = null,
                isOverdue = false,
                balanceRemaining = 0.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardNullSecondaryPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Inventory2,
            garmentName = "Agbada",
            customerName = "Gose Wale",
            status = OrderStatus.PENDING,
            subStatus = null,
            priority = OrderPriority.URGENT,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 29 May"),
            totalPrice = 40000.0,
            balanceRemaining = 0.0,
            discount = 0.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.PENDING,
                subStatus = null,
                isOverdue = false,
                balanceRemaining = 0.0,
            ),
            phone = "+2348012345678",
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

// endregion
