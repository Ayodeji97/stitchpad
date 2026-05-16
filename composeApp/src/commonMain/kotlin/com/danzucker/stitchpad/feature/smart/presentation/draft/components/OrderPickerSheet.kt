package com.danzucker.stitchpad.feature.smart.presentation.draft.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_no_open_orders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderPickerSheet(
    orders: List<OrderSummary>,
    onSelect: (OrderSummary) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (orders.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space8),
            ) {
                Text(
                    text = stringResource(Res.string.draft_message_no_open_orders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = orders, key = { it.id }) { order ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(order)
                                onDismissRequest()
                            }
                            .padding(
                                horizontal = DesignTokens.space4,
                                vertical = DesignTokens.space3,
                            ),
                    ) {
                        Text(
                            text = order.garmentLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${order.balanceFormatted} • ${order.deadlineFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
