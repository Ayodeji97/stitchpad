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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
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
        NotificationType.TO_COLLECT -> if (notification.amount != null) {
            stringResource(
                Res.string.notification_to_collect,
                notification.customerName,
                "₦${formatPrice(notification.amount)}",
            )
        } else {
            "${notification.customerName} · ${notification.garmentSummary}"
        }
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

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowUnreadOverduePreview() {
    StitchPadTheme {
        NotificationRow(
            notification = Notification(
                id = "o1__OVERDUE",
                orderId = "o1",
                type = NotificationType.OVERDUE,
                customerName = "Fola Sunday",
                garmentSummary = "Agbada",
                isRead = false,
                createdAt = 0L,
            ),
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowReadDueSoonPreview() {
    StitchPadTheme {
        NotificationRow(
            notification = Notification(
                id = "o2__DUE_SOON",
                orderId = "o2",
                type = NotificationType.DUE_SOON,
                customerName = "Aina Paul",
                garmentSummary = "Ankara suit",
                isRead = true,
                createdAt = 0L,
            ),
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowToCollectWithAmountPreview() {
    StitchPadTheme {
        NotificationRow(
            notification = Notification(
                id = "o3__TO_COLLECT",
                orderId = "o3",
                type = NotificationType.TO_COLLECT,
                customerName = "Dayyo Au",
                garmentSummary = "Buba",
                amount = 15_000.0,
                isRead = false,
                createdAt = 0L,
            ),
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowToCollectNullAmountPreview() {
    StitchPadTheme {
        NotificationRow(
            notification = Notification(
                id = "o4__TO_COLLECT_NULL",
                orderId = "o4",
                type = NotificationType.TO_COLLECT,
                customerName = "Tunde Bello",
                garmentSummary = "Senator kaftan",
                amount = null,
                isRead = true,
                createdAt = 0L,
            ),
            onClick = {},
        )
    }
}
