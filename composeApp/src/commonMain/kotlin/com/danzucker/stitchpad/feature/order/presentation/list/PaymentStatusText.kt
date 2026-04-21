package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.payment_paid
import stitchpad.composeapp.generated.resources.payment_partial
import stitchpad.composeapp.generated.resources.payment_unpaid

@Composable
fun PaymentStatusText(
    depositPaid: Double,
    totalPrice: Double,
    modifier: Modifier = Modifier
) {
    val display = formatPaymentStatus(depositPaid, totalPrice)
    val (text, color) = when (display) {
        PaymentDisplay.Paid -> stringResource(Res.string.payment_paid) to DesignTokens.success500
        is PaymentDisplay.Partial -> {
            stringResource(Res.string.payment_partial, display.formatAbbreviated()) to DesignTokens.warning500
        }
        PaymentDisplay.Unpaid -> stringResource(Res.string.payment_unpaid) to DesignTokens.error500
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}
