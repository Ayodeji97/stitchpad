package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.components.ThousandsSeparatorTransformation
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.order_record_payment_confirm
import stitchpad.composeapp.generated.resources.payment_capped_at_balance
import stitchpad.composeapp.generated.resources.payment_method_cash
import stitchpad.composeapp.generated.resources.payment_method_other
import stitchpad.composeapp.generated.resources.payment_method_pos
import stitchpad.composeapp.generated.resources.payment_method_transfer
import stitchpad.composeapp.generated.resources.payment_type_deposit
import stitchpad.composeapp.generated.resources.payment_type_final
import stitchpad.composeapp.generated.resources.payment_type_progress
import stitchpad.composeapp.generated.resources.record_payment_dialog_amount_label
import stitchpad.composeapp.generated.resources.record_payment_dialog_mark_paid_full
import stitchpad.composeapp.generated.resources.record_payment_dialog_method_label
import stitchpad.composeapp.generated.resources.record_payment_dialog_title
import stitchpad.composeapp.generated.resources.record_payment_dialog_type_label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialogV2(
    balanceRemaining: Double,
    amountInput: String,
    method: PaymentMethod,
    type: PaymentType,
    wasCapped: Boolean,
    onAmountChange: (String) -> Unit,
    onMethodSelect: (PaymentMethod) -> Unit,
    onTypeSelect: (PaymentType) -> Unit,
    onMarkPaidInFull: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isConfirmEnabled = amountInput.isNotBlank() && (amountInput.toDoubleOrNull() ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.record_payment_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isConfirmEnabled,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ) {
                Text(
                    text = stringResource(Res.string.order_record_payment_confirm),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.common_cancel))
            }
        },
        text = {
            RecordPaymentDialogV2Content(
                balanceRemaining = balanceRemaining,
                amountInput = amountInput,
                method = method,
                type = type,
                wasCapped = wasCapped,
                onAmountChange = onAmountChange,
                onMethodSelect = onMethodSelect,
                onTypeSelect = onTypeSelect,
                onMarkPaidInFull = onMarkPaidInFull,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialogV2Content(
    balanceRemaining: Double,
    amountInput: String,
    method: PaymentMethod,
    type: PaymentType,
    wasCapped: Boolean,
    onAmountChange: (String) -> Unit,
    onMethodSelect: (PaymentMethod) -> Unit,
    onTypeSelect: (PaymentType) -> Unit,
    onMarkPaidInFull: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        // ── Amount field ──────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Text(
                text = stringResource(Res.string.record_payment_dialog_amount_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                prefix = { Text(text = "₦") },
                visualTransformation = ThousandsSeparatorTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                supportingText = if (wasCapped) {
                    {
                        Text(
                            text = stringResource(
                                Res.string.payment_capped_at_balance,
                                "₦${formatPrice(balanceRemaining)}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
            )
            // ── Mark paid in full shortcut (inline, under the amount field) ──
            TextButton(
                onClick = onMarkPaidInFull,
                enabled = balanceRemaining > 0.0,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(
                        Res.string.record_payment_dialog_mark_paid_full,
                        formatPrice(balanceRemaining),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // ── Type segmented control ────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Text(
                text = stringResource(Res.string.record_payment_dialog_type_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PaymentType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = t == type,
                        onClick = { onTypeSelect(t) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PaymentType.entries.size,
                        ),
                    ) {
                        Text(
                            text = paymentTypeLabel(t),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        // ── Method segmented control ──────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Text(
                text = stringResource(Res.string.record_payment_dialog_method_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PaymentMethod.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = m == method,
                        onClick = { onMethodSelect(m) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PaymentMethod.entries.size,
                        ),
                    ) {
                        Text(
                            text = paymentMethodLabel(m),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
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

// region — Previews (render dialog content only — AlertDialog needs an Activity for consistent preview)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentDialogV2EmptyLightPreview() {
    StitchPadTheme {
        Surface {
            RecordPaymentDialogV2Content(
                balanceRemaining = 45000.0,
                amountInput = "",
                method = PaymentMethod.TRANSFER,
                type = PaymentType.DEPOSIT,
                wasCapped = false,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentDialogV2CappedProgressCashLightPreview() {
    StitchPadTheme {
        Surface {
            RecordPaymentDialogV2Content(
                balanceRemaining = 20000.0,
                amountInput = "20000",
                method = PaymentMethod.CASH,
                type = PaymentType.PROGRESS,
                wasCapped = true,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentDialogV2CappedProgressCashDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            RecordPaymentDialogV2Content(
                balanceRemaining = 20000.0,
                amountInput = "20000",
                method = PaymentMethod.CASH,
                type = PaymentType.PROGRESS,
                wasCapped = true,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
            )
        }
    }
}

// endregion
