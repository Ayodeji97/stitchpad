package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadButtonVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_view_order
import stitchpad.composeapp.generated.resources.order_saved_sheet_body
import stitchpad.composeapp.generated.resources.order_saved_sheet_done
import stitchpad.composeapp.generated.resources.order_saved_sheet_share_whatsapp
import stitchpad.composeapp.generated.resources.order_saved_sheet_title

/**
 * Shown after a NEW order is created: share the receipt, open the order, or finish.
 * Dismissing the sheet is the same as "Done".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSavedSheet(
    customerName: String?,
    canShareReceipt: Boolean,
    onShareReceipt: () -> Unit,
    onViewOrder: () -> Unit,
    onDone: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        OrderSavedSheetContent(
            customerName = customerName,
            canShareReceipt = canShareReceipt,
            onShareReceipt = onShareReceipt,
            onViewOrder = onViewOrder,
            onDone = onDone,
        )
    }
}

@Composable
private fun OrderSavedSheetContent(
    customerName: String?,
    canShareReceipt: Boolean,
    onShareReceipt: () -> Unit,
    onViewOrder: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space6),
    ) {
        Text(
            text = stringResource(Res.string.order_saved_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!customerName.isNullOrBlank()) {
            Text(
                text = stringResource(Res.string.order_saved_sheet_body, customerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canShareReceipt) {
            StitchPadButton(
                text = stringResource(Res.string.order_saved_sheet_share_whatsapp),
                onClick = onShareReceipt,
                leadingIcon = Icons.Default.Share,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StitchPadButton(
            text = stringResource(Res.string.dashboard_nba_cta_view_order),
            onClick = onViewOrder,
            variant = if (canShareReceipt) StitchPadButtonVariant.Secondary else StitchPadButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.order_saved_sheet_done))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderSavedSheetContentPreview() {
    StitchPadTheme {
        OrderSavedSheetContent(
            customerName = "Adaeze Okafor",
            canShareReceipt = true,
            onShareReceipt = {},
            onViewOrder = {},
            onDone = {},
        )
    }
}
