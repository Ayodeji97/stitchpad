package com.danzucker.stitchpad.feature.freemium.presentation.swap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_swap_sheet_subtitle
import stitchpad.composeapp.generated.resources.customer_swap_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(
    lockedCustomer: Customer,
    activeCustomers: List<Customer>,
    onConfirm: (demoteId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SwapSheetContent(
            lockedCustomer = lockedCustomer,
            activeCustomers = activeCustomers,
            onConfirm = onConfirm,
        )
    }
}

/**
 * Inner column extracted so @Preview can render it — `ModalBottomSheet` itself
 * doesn't lay out in preview mode (no host activity / sheet state).
 */
@Composable
private fun SwapSheetContent(
    lockedCustomer: Customer,
    activeCustomers: List<Customer>,
    onConfirm: (demoteId: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = DesignTokens.space4)) {
            Text(
                text = stringResource(
                    Res.string.customer_swap_sheet_title,
                    lockedCustomer.name.substringBefore(" "),
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = stringResource(Res.string.customer_swap_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space3))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(activeCustomers, key = { it.id }) { customer ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfirm(customer.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))
    }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SwapSheetContentPreview() {
    StitchPadTheme {
        SwapSheetContent(
            lockedCustomer = previewCustomer("locked-1", "Tunde Adekunle", "+2348011112222"),
            activeCustomers = listOf(
                previewCustomer("act-1", "Adaeze Okeke", "+2348022223333"),
                previewCustomer("act-2", "Bola Ade", "+2348033334444"),
                previewCustomer("act-3", "Chika Ibe", "+2348044445555"),
            ),
            onConfirm = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SwapSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SwapSheetContent(
            lockedCustomer = previewCustomer("locked-1", "Tunde Adekunle", "+2348011112222"),
            activeCustomers = listOf(
                previewCustomer("act-1", "Adaeze Okeke", "+2348022223333"),
                previewCustomer("act-2", "Bola Ade", "+2348033334444"),
            ),
            onConfirm = {},
        )
    }
}

private fun previewCustomer(id: String, name: String, phone: String) = Customer(
    id = id,
    userId = "preview-uid",
    name = name,
    phone = phone,
    slotState = CustomerSlotState.ACTIVE,
)
