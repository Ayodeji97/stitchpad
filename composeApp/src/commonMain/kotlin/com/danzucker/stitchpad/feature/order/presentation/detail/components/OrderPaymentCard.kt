package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.balance_warning_record_payment
import stitchpad.composeapp.generated.resources.order_detail_payment_balance
import stitchpad.composeapp.generated.resources.order_detail_payment_discount
import stitchpad.composeapp.generated.resources.order_detail_payment_history_count
import stitchpad.composeapp.generated.resources.order_detail_payment_history_count_plural
import stitchpad.composeapp.generated.resources.order_detail_payment_history_label
import stitchpad.composeapp.generated.resources.order_detail_payment_paid
import stitchpad.composeapp.generated.resources.order_detail_payment_section
import stitchpad.composeapp.generated.resources.order_detail_payment_subtotal
import stitchpad.composeapp.generated.resources.order_detail_payment_total
import stitchpad.composeapp.generated.resources.payment_method_cash
import stitchpad.composeapp.generated.resources.payment_method_other
import stitchpad.composeapp.generated.resources.payment_method_pos
import stitchpad.composeapp.generated.resources.payment_method_transfer
import stitchpad.composeapp.generated.resources.payment_type_deposit
import stitchpad.composeapp.generated.resources.payment_type_final
import stitchpad.composeapp.generated.resources.payment_type_progress

private val INDENT_START = 28.dp + 8.dp // icon width + space2 gap

@Composable
fun OrderPaymentCard(
    totalPrice: Double,
    payments: List<Payment>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    modifier: Modifier = Modifier,
    discount: Double = 0.0,
) {
    // payable = subtotal minus the whole-order discount; the Total column and the
    // balance both come off this, never the raw subtotal.
    val payable = (totalPrice - discount).coerceAtLeast(0.0)
    val paid = payments.sumOf { it.amount }
    val balance = (payable - paid).coerceAtLeast(0.0)
    val hasPayments = payments.isNotEmpty()
    val showDivider = hasPayments || balance > 0.0

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                SectionIconTile(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.order_detail_payment_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            // ── Discount breakdown (only when a discount is applied) ─────────
            if (discount > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = INDENT_START),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                ) {
                    Text(
                        text = stringResource(
                            Res.string.order_detail_payment_subtotal,
                            "₦${formatPrice(totalPrice)}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            Res.string.order_detail_payment_discount,
                            "−₦${formatPrice(discount)}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DesignTokens.success500,
                    )
                }
                Spacer(Modifier.height(DesignTokens.space2))
            }

            // ── Financial row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = INDENT_START),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                // Total
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.order_detail_payment_total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "₦${formatPrice(payable)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Paid
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.order_detail_payment_paid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "₦${formatPrice(paid)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Balance
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.order_detail_payment_balance),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "₦${formatPrice(balance)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (balance > 0.0) {
                            DesignTokens.error500
                        } else {
                            DesignTokens.success500
                        },
                    )
                }
            }

            // ── Divider ──────────────────────────────────────────────────────
            if (showDivider) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = DesignTokens.space2),
                )
            }

            // ── Payment history toggle ────────────────────────────────────────
            if (hasPayments) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpanded)
                        .padding(start = INDENT_START),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DesignTokens.iconInline),
                    )
                    Text(
                        text = stringResource(Res.string.order_detail_payment_history_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val countText = if (payments.size == 1) {
                        stringResource(Res.string.order_detail_payment_history_count, 1)
                    } else {
                        stringResource(
                            Res.string.order_detail_payment_history_count_plural,
                            payments.size,
                        )
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DesignTokens.iconInline),
                    )
                }

                // ── Expanded payment list ──────────────────────────────────────
                if (isExpanded) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    Column(
                        modifier = Modifier.padding(start = INDENT_START + DesignTokens.space2),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        payments.sortedBy { it.recordedAt }.forEach { payment ->
                            PaymentRow(payment = payment)
                        }
                    }
                }
            }

            // ── Record payment button ─────────────────────────────────────────
            if (balance > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRecordPaymentClick) {
                        Text(
                            text = stringResource(Res.string.balance_warning_record_payment),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = DesignTokens.success500,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = paymentTypeLabel(payment.type),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "₦${formatPrice(payment.amount)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val methodLabel = paymentMethodLabel(payment.method)
        val dateLabel = formatPaymentDate(payment.recordedAt)
        Text(
            text = "$methodLabel · $dateLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun paymentTypeLabel(type: PaymentType): String = when (type) {
    PaymentType.DEPOSIT -> stringResource(Res.string.payment_type_deposit)
    PaymentType.PROGRESS -> stringResource(Res.string.payment_type_progress)
    PaymentType.FINAL -> stringResource(Res.string.payment_type_final)
}

@Composable
private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(Res.string.payment_method_cash)
    PaymentMethod.TRANSFER -> stringResource(Res.string.payment_method_transfer)
    PaymentMethod.POS -> stringResource(Res.string.payment_method_pos)
    PaymentMethod.OTHER -> stringResource(Res.string.payment_method_other)
}

private fun formatPaymentDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month"
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

// region — Previews

private val PREVIEW_PAYMENT_1 = Payment(
    id = "p1",
    amount = 40_000.0,
    method = PaymentMethod.TRANSFER,
    type = PaymentType.DEPOSIT,
    recordedAt = 1_713_182_400_000L, // 15 Apr 2024
    note = null,
)

private val PREVIEW_PAYMENT_2 = Payment(
    id = "p2",
    amount = 30_000.0,
    method = PaymentMethod.CASH,
    type = PaymentType.PROGRESS,
    recordedAt = 1_715_860_800_000L, // 16 May 2024
    note = null,
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderPaymentCardEmptyStateLightPreview() {
    StitchPadTheme {
        OrderPaymentCard(
            totalPrice = 60_000.0,
            payments = emptyList(),
            isExpanded = false,
            onToggleExpanded = {},
            onRecordPaymentClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderPaymentCardOnePaymentCollapsedLightPreview() {
    StitchPadTheme {
        OrderPaymentCard(
            totalPrice = 60_000.0,
            payments = listOf(PREVIEW_PAYMENT_1),
            isExpanded = false,
            onToggleExpanded = {},
            onRecordPaymentClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderPaymentCardMultiPaymentExpandedLightPreview() {
    StitchPadTheme {
        OrderPaymentCard(
            totalPrice = 100_000.0,
            payments = listOf(PREVIEW_PAYMENT_1, PREVIEW_PAYMENT_2),
            isExpanded = true,
            onToggleExpanded = {},
            onRecordPaymentClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderPaymentCardMultiPaymentExpandedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderPaymentCard(
            totalPrice = 100_000.0,
            payments = listOf(PREVIEW_PAYMENT_1, PREVIEW_PAYMENT_2),
            isExpanded = true,
            onToggleExpanded = {},
            onRecordPaymentClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderPaymentCardWithDiscountLightPreview() {
    StitchPadTheme {
        OrderPaymentCard(
            totalPrice = 60_000.0,
            discount = 5_000.0,
            payments = listOf(PREVIEW_PAYMENT_1),
            isExpanded = false,
            onToggleExpanded = {},
            onRecordPaymentClick = {},
        )
    }
}

// endregion
