package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        RecordPaymentSheetContent(
            balanceRemaining = balanceRemaining,
            amountInput = amountInput,
            method = method,
            type = type,
            wasCapped = wasCapped,
            onAmountChange = onAmountChange,
            onMethodSelect = onMethodSelect,
            onTypeSelect = onTypeSelect,
            onMarkPaidInFull = onMarkPaidInFull,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
private fun RecordPaymentSheetContent(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
    ) {
        Text(
            text = stringResource(Res.string.record_payment_dialog_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
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
            // Mark paid in full as a small chip directly beneath the amount field.
            AssistChip(
                onClick = onMarkPaidInFull,
                enabled = balanceRemaining > 0.0,
                label = {
                    Text(
                        text = stringResource(
                            Res.string.record_payment_dialog_mark_paid_full,
                            formatPrice(balanceRemaining),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.record_payment_dialog_type_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                PaymentType.entries.forEach { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = { onTypeSelect(t) },
                        label = {
                            Text(
                                text = paymentTypeLabel(t),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = if (t == type) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.height(DesignTokens.iconInline),
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.record_payment_dialog_method_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                PaymentMethod.entries.forEach { m ->
                    FilterChip(
                        selected = m == method,
                        onClick = { onMethodSelect(m) },
                        label = {
                            Text(
                                text = paymentMethodLabel(m),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = if (m == method) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.height(DesignTokens.iconInline),
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    )
                }
            }
        }

        Spacer(Modifier.height(DesignTokens.space2))

        Button(
            onClick = onConfirm,
            enabled = isConfirmEnabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
        ) {
            Text(
                text = stringResource(Res.string.order_record_payment_confirm),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = DesignTokens.space1),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.common_cancel),
                    style = MaterialTheme.typography.labelLarge,
                )
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

// region — Previews (render content only — ModalBottomSheet needs an Activity for proper preview)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentSheetEmptyLightPreview() {
    StitchPadTheme {
        Surface {
            RecordPaymentSheetContent(
                balanceRemaining = 45000.0,
                amountInput = "",
                method = PaymentMethod.TRANSFER,
                type = PaymentType.DEPOSIT,
                wasCapped = false,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentSheetCappedProgressCashLightPreview() {
    StitchPadTheme {
        Surface {
            RecordPaymentSheetContent(
                balanceRemaining = 20000.0,
                amountInput = "20000",
                method = PaymentMethod.CASH,
                type = PaymentType.PROGRESS,
                wasCapped = true,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RecordPaymentSheetCappedProgressCashDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            RecordPaymentSheetContent(
                balanceRemaining = 20000.0,
                amountInput = "20000",
                method = PaymentMethod.CASH,
                type = PaymentType.PROGRESS,
                wasCapped = true,
                onAmountChange = {},
                onMethodSelect = {},
                onTypeSelect = {},
                onMarkPaidInFull = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

// endregion
