package com.danzucker.stitchpad.feature.customer.presentation.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_customer_actions_view
import stitchpad.composeapp.generated.resources.customer_actions_delete
import stitchpad.composeapp.generated.resources.customer_actions_edit
import stitchpad.composeapp.generated.resources.customer_actions_message_whatsapp
import stitchpad.composeapp.generated.resources.customer_actions_new_measurement
import stitchpad.composeapp.generated.resources.customer_actions_new_order

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerActionsSheet(
    customer: Customer,
    onView: (String) -> Unit,
    onMessageWhatsApp: (Customer) -> Unit,
    onEdit: (String) -> Unit,
    onNewMeasurement: (String) -> Unit,
    onNewOrder: (String) -> Unit,
    onDelete: (Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CustomerActionsSheetContent(
            customer = customer,
            onView = onView,
            onMessageWhatsApp = onMessageWhatsApp,
            onEdit = onEdit,
            onNewMeasurement = onNewMeasurement,
            onNewOrder = onNewOrder,
            onDelete = onDelete,
        )
    }
}

/**
 * Inner column extracted so @Preview can render it — ModalBottomSheet
 * itself doesn't lay out in preview mode (no host activity / sheet state).
 */
@Composable
private fun CustomerActionsSheetContent(
    customer: Customer,
    onView: (String) -> Unit,
    onMessageWhatsApp: (Customer) -> Unit,
    onEdit: (String) -> Unit,
    onNewMeasurement: (String) -> Unit,
    onNewOrder: (String) -> Unit,
    onDelete: (Customer) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space2),
    ) {
        SheetHeader(
            customer = customer,
            onClick = { onView(customer.id) },
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space4),
        )
        Spacer(Modifier.height(DesignTokens.space2))
        // PTSP-32: message the customer on WhatsApp — only when we have a number
        // to send to. Communication action sits first, above the edit/create rows.
        if (customer.phone.isNotBlank()) {
            ActionRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(Res.string.customer_actions_message_whatsapp),
                onClick = { onMessageWhatsApp(customer) },
            )
        }
        ActionRow(
            icon = Icons.Default.Edit,
            label = stringResource(Res.string.customer_actions_edit),
            onClick = { onEdit(customer.id) },
        )
        ActionRow(
            icon = Icons.Default.Straighten,
            label = stringResource(Res.string.customer_actions_new_measurement),
            onClick = { onNewMeasurement(customer.id) },
        )
        ActionRow(
            icon = Icons.AutoMirrored.Filled.Assignment,
            label = stringResource(Res.string.customer_actions_new_order),
            onClick = { onNewOrder(customer.id) },
        )
        ActionRow(
            icon = Icons.Default.Delete,
            label = stringResource(Res.string.customer_actions_delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = { onDelete(customer) },
        )
    }
}

@Composable
private fun SheetHeader(
    customer: Customer,
    onClick: () -> Unit,
) {
    val viewCd = stringResource(Res.string.cd_customer_actions_view, customer.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = viewCd }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
    ) {
        CustomerAvatar(name = customer.name, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (customer.phone.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = customer.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerActionsSheetContentPreview() {
    StitchPadTheme {
        CustomerActionsSheetContent(
            customer = Customer(
                id = "c1",
                userId = "u1",
                name = "Amina Bello",
                phone = "+234 801 234 5678",
            ),
            onView = {},
            onMessageWhatsApp = {},
            onEdit = {},
            onNewMeasurement = {},
            onNewOrder = {},
            onDelete = {},
        )
    }
}
