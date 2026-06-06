package com.danzucker.stitchpad.feature.notification.presentation.inbox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notification_due_soon
import stitchpad.composeapp.generated.resources.notification_overdue
import stitchpad.composeapp.generated.resources.notification_to_collect

private val UnreadDotSize = 8.dp

@Composable
fun NotificationRow(
    notification: Notification,
    onClick: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (notification.type) {
        NotificationType.OVERDUE -> stringResource(
            Res.string.notification_overdue,
            notification.customerName,
            notification.garmentSummary,
        )
        NotificationType.DUE_SOON -> stringResource(
            Res.string.notification_due_soon,
            notification.customerName,
            notification.garmentSummary,
        )
        NotificationType.TO_COLLECT -> stringResource(
            Res.string.notification_to_collect,
            notification.customerName,
            "₦${formatPrice(notification.amount ?: 0.0)}",
        )
        NotificationType.UNKNOWN -> "${notification.customerName} · ${notification.garmentSummary}"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(notification) }
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
    ) {
        // Unread indicator dot — takes up space even when read to keep text aligned
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(UnreadDotSize),
        ) {
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(UnreadDotSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(DesignTokens.space1))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                color = if (!notification.isRead) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
