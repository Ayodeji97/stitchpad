@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.notification.presentation.inbox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.notification.presentation.inbox.RelativeTime
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_notification_from
import stitchpad.composeapp.generated.resources.gift_notification_title_atelier
import stitchpad.composeapp.generated.resources.gift_notification_title_generic
import stitchpad.composeapp.generated.resources.gift_notification_title_pro
import stitchpad.composeapp.generated.resources.month_abbrev_apr
import stitchpad.composeapp.generated.resources.month_abbrev_aug
import stitchpad.composeapp.generated.resources.month_abbrev_dec
import stitchpad.composeapp.generated.resources.month_abbrev_feb
import stitchpad.composeapp.generated.resources.month_abbrev_jan
import stitchpad.composeapp.generated.resources.month_abbrev_jul
import stitchpad.composeapp.generated.resources.month_abbrev_jun
import stitchpad.composeapp.generated.resources.month_abbrev_mar
import stitchpad.composeapp.generated.resources.month_abbrev_may
import stitchpad.composeapp.generated.resources.month_abbrev_nov
import stitchpad.composeapp.generated.resources.month_abbrev_oct
import stitchpad.composeapp.generated.resources.month_abbrev_sep
import stitchpad.composeapp.generated.resources.notification_due_soon
import stitchpad.composeapp.generated.resources.notification_overdue
import stitchpad.composeapp.generated.resources.notification_tag_due_soon
import stitchpad.composeapp.generated.resources.notification_tag_overdue
import stitchpad.composeapp.generated.resources.notification_tag_owes
import stitchpad.composeapp.generated.resources.notification_to_collect
import stitchpad.composeapp.generated.resources.relative_time_date
import stitchpad.composeapp.generated.resources.relative_time_hours
import stitchpad.composeapp.generated.resources.relative_time_minutes
import stitchpad.composeapp.generated.resources.relative_time_now
import stitchpad.composeapp.generated.resources.weekday_abbrev_fri
import stitchpad.composeapp.generated.resources.weekday_abbrev_mon
import stitchpad.composeapp.generated.resources.weekday_abbrev_sat
import stitchpad.composeapp.generated.resources.weekday_abbrev_sun
import stitchpad.composeapp.generated.resources.weekday_abbrev_thu
import stitchpad.composeapp.generated.resources.weekday_abbrev_tue
import stitchpad.composeapp.generated.resources.weekday_abbrev_wed

// ---------------------------------------------------------------------------
// Relative-time UI formatter
// ---------------------------------------------------------------------------

private fun weekdayAbbrevRes(d: DayOfWeek): StringResource = when (d) {
    DayOfWeek.MONDAY -> Res.string.weekday_abbrev_mon
    DayOfWeek.TUESDAY -> Res.string.weekday_abbrev_tue
    DayOfWeek.WEDNESDAY -> Res.string.weekday_abbrev_wed
    DayOfWeek.THURSDAY -> Res.string.weekday_abbrev_thu
    DayOfWeek.FRIDAY -> Res.string.weekday_abbrev_fri
    DayOfWeek.SATURDAY -> Res.string.weekday_abbrev_sat
    DayOfWeek.SUNDAY -> Res.string.weekday_abbrev_sun
}

private fun monthAbbrevRes(m: Month): StringResource = when (m) {
    Month.JANUARY -> Res.string.month_abbrev_jan
    Month.FEBRUARY -> Res.string.month_abbrev_feb
    Month.MARCH -> Res.string.month_abbrev_mar
    Month.APRIL -> Res.string.month_abbrev_apr
    Month.MAY -> Res.string.month_abbrev_may
    Month.JUNE -> Res.string.month_abbrev_jun
    Month.JULY -> Res.string.month_abbrev_jul
    Month.AUGUST -> Res.string.month_abbrev_aug
    Month.SEPTEMBER -> Res.string.month_abbrev_sep
    Month.OCTOBER -> Res.string.month_abbrev_oct
    Month.NOVEMBER -> Res.string.month_abbrev_nov
    Month.DECEMBER -> Res.string.month_abbrev_dec
}

/**
 * Resolves a [RelativeTime] sealed value to a localized display string.
 * Must be called from composable scope.
 */
@Composable
internal fun relativeTimeLabel(rt: RelativeTime): String = when (rt) {
    is RelativeTime.Now -> stringResource(Res.string.relative_time_now)
    is RelativeTime.Minutes -> stringResource(Res.string.relative_time_minutes, rt.value.toString())
    is RelativeTime.Hours -> stringResource(Res.string.relative_time_hours, rt.value.toString())
    is RelativeTime.Weekday -> stringResource(weekdayAbbrevRes(rt.day))
    is RelativeTime.MonthDay -> stringResource(
        Res.string.relative_time_date,
        rt.day.toString(),
        stringResource(monthAbbrevRes(rt.month)),
    )
}

// ---------------------------------------------------------------------------

private val IconSquareSize = 40.dp
private val UnreadDotSize = 8.dp
private val IconContentSize = 20.dp

private data class NotificationTypeTokens(
    val accent: Color,
    val iconBg: Color,
    val icon: ImageVector,
)

@Composable
private fun resolveTypeTokens(type: NotificationType, isDark: Boolean): NotificationTypeTokens =
    when (type) {
        NotificationType.OVERDUE -> NotificationTypeTokens(
            accent = MaterialTheme.colorScheme.error,
            iconBg = MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Filled.PriorityHigh,
        )
        NotificationType.DUE_SOON -> NotificationTypeTokens(
            accent = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            iconBg = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50,
            icon = Icons.Outlined.Schedule,
        )
        NotificationType.TO_COLLECT -> NotificationTypeTokens(
            accent = MaterialTheme.colorScheme.tertiary,
            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
            icon = Icons.Outlined.Payments,
        )
        NotificationType.GIFT_RECEIVED -> NotificationTypeTokens(
            accent = MaterialTheme.colorScheme.primary,
            iconBg = MaterialTheme.colorScheme.primaryContainer,
            icon = Icons.Outlined.CardGiftcard,
        )
        NotificationType.UNKNOWN -> NotificationTypeTokens(
            accent = MaterialTheme.colorScheme.onSurfaceVariant,
            iconBg = MaterialTheme.colorScheme.surfaceVariant,
            icon = Icons.Outlined.Notifications,
        )
    }

/** Row title: the gift message for a gift, otherwise the customer's name. */
@Composable
private fun rowTitle(notification: Notification): String = when (notification.type) {
    NotificationType.GIFT_RECEIVED -> when (notification.tier?.lowercase()) {
        "pro" -> stringResource(Res.string.gift_notification_title_pro)
        "atelier" -> stringResource(Res.string.gift_notification_title_atelier)
        else -> stringResource(Res.string.gift_notification_title_generic)
    }
    else -> notification.customerName
}

/** Builds the accessibility sentence mirroring the old row. */
@Composable
private fun buildA11ySentence(notification: Notification, relativeTime: String): String {
    val amountStr = if (notification.amount != null) "₦${formatPrice(notification.amount)}" else ""
    val base = when (notification.type) {
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
            stringResource(Res.string.notification_to_collect, notification.customerName, amountStr)
        } else {
            nameWithGarment(notification)
        }
        NotificationType.GIFT_RECEIVED -> {
            val title = rowTitle(notification)
            if (!notification.gifterName.isNullOrBlank()) {
                "$title · ${stringResource(Res.string.gift_notification_from, notification.gifterName)}"
            } else {
                title
            }
        }
        NotificationType.UNKNOWN -> nameWithGarment(notification)
    }
    return "$base · $relativeTime"
}

/** "Name · Garment", or just the name when the garment summary is empty (avoids a dangling separator). */
private fun nameWithGarment(notification: Notification): String =
    if (notification.garmentSummary.isNotEmpty()) {
        "${notification.customerName} · ${notification.garmentSummary}"
    } else {
        notification.customerName
    }

/** Appends the tinted tag segment for a TO_COLLECT notification. */
private fun AnnotatedString.Builder.appendOwesTag(
    owesFull: String,
    amountStr: String,
    accent: Color,
    monoFamily: FontFamily,
) {
    // Assumes notification_tag_owes ends with the amount placeholder (%1$s),
    // so the prefix ("owes ") is styled plain and only the figure goes mono.
    // If a future locale puts the amount first, switch to a tagged AnnotatedString.
    val prefix = owesFull.removeSuffix(amountStr)
    withStyle(SpanStyle(color = accent)) { append(prefix) }
    withStyle(SpanStyle(color = accent, fontFamily = monoFamily)) { append(amountStr) }
}

private fun showTagForType(type: NotificationType, amount: Double?): Boolean = when (type) {
    NotificationType.OVERDUE, NotificationType.DUE_SOON -> true
    NotificationType.TO_COLLECT -> amount != null
    NotificationType.GIFT_RECEIVED, NotificationType.UNKNOWN -> false
}

private fun AnnotatedString.Builder.appendTag(
    type: NotificationType,
    tagOverdue: String,
    tagDueSoon: String,
    owesFull: String,
    amountStr: String,
    accent: Color,
    monoFamily: FontFamily,
) {
    when (type) {
        NotificationType.OVERDUE -> withStyle(SpanStyle(color = accent)) { append(tagOverdue) }
        NotificationType.DUE_SOON -> withStyle(SpanStyle(color = accent)) { append(tagDueSoon) }
        NotificationType.TO_COLLECT -> appendOwesTag(owesFull, amountStr, accent, monoFamily)
        NotificationType.GIFT_RECEIVED, NotificationType.UNKNOWN -> Unit
    }
}

/**
 * Builds the single-line meta AnnotatedString:
 * garment · [type tag] · relativeTime
 */
@Composable
private fun buildMetaString(
    notification: Notification,
    relativeTime: String,
    accent: Color,
    monoFamily: FontFamily,
    muted: Color,
): AnnotatedString {
    val amountStr = if (notification.amount != null) "₦${formatPrice(notification.amount)}" else ""
    val tagOverdue = stringResource(Res.string.notification_tag_overdue)
    val tagDueSoon = stringResource(Res.string.notification_tag_due_soon)
    val owesFull = if (notification.type == NotificationType.TO_COLLECT && notification.amount != null) {
        stringResource(Res.string.notification_tag_owes, amountStr)
    } else {
        ""
    }
    val showTag = showTagForType(notification.type, notification.amount)
    val giftFrom = if (
        notification.type == NotificationType.GIFT_RECEIVED && !notification.gifterName.isNullOrBlank()
    ) {
        stringResource(Res.string.gift_notification_from, notification.gifterName)
    } else {
        ""
    }

    return buildAnnotatedString {
        var hasPrev = false

        if (giftFrom.isNotEmpty()) {
            withStyle(SpanStyle(color = muted)) { append(giftFrom) }
            hasPrev = true
        }

        if (notification.garmentSummary.isNotEmpty()) {
            if (hasPrev) append(" · ")
            withStyle(SpanStyle(color = muted)) { append(notification.garmentSummary) }
            hasPrev = true
        }

        if (showTag) {
            if (hasPrev) append(" · ")
            appendTag(notification.type, tagOverdue, tagDueSoon, owesFull, amountStr, accent, monoFamily)
            hasPrev = true
        }

        if (hasPrev) append(" · ")
        withStyle(SpanStyle(color = muted)) { append(relativeTime) }
    }
}

@Composable
fun NotificationRow(
    notification: Notification,
    relativeTime: String,
    onClick: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val monoFamily: FontFamily = JetBrainsMonoFamily()
    val tokens = resolveTypeTokens(notification.type, isDark)
    val a11ySentence = buildA11ySentence(notification, relativeTime)

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    val metaString = buildMetaString(
        notification = notification,
        relativeTime = relativeTime,
        accent = tokens.accent,
        monoFamily = monoFamily,
        muted = onSurfaceVariant,
    )

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = a11ySentence }
            .clickable(onClick = { onClick(notification) }, role = Role.Button)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4),
    ) {
        // 1. Tinted icon square
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(IconSquareSize)
                .background(tokens.iconBg, RoundedCornerShape(DesignTokens.radiusMd)),
        ) {
            Icon(
                imageVector = tokens.icon,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(IconContentSize),
            )
        }

        // 2. Text column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Text(
                text = rowTitle(notification),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                color = if (!notification.isRead) onSurface else onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaString,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 3. Unread dot — reserves space always, fills only when unread
        Box(modifier = Modifier.size(UnreadDotSize)) {
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(UnreadDotSize)
                        .background(primary, CircleShape),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowLightPreview() {
    StitchPadTheme {
        Surface {
            Column {
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
                    relativeTime = "2h",
                    onClick = {},
                )
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
                    relativeTime = "Tue",
                    onClick = {},
                )
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
                    relativeTime = "5h",
                    onClick = {},
                )
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
                    relativeTime = "Mon",
                    onClick = {},
                )
                NotificationRow(
                    notification = Notification(
                        id = "gift1__GIFT_RECEIVED",
                        orderId = "",
                        type = NotificationType.GIFT_RECEIVED,
                        customerName = "",
                        garmentSummary = "",
                        tier = "pro",
                        gifterName = "Bola",
                        isRead = false,
                        createdAt = 0L,
                    ),
                    relativeTime = "1h",
                    onClick = {},
                )
                NotificationRow(
                    notification = Notification(
                        id = "o5__UNKNOWN",
                        orderId = "o5",
                        type = NotificationType.UNKNOWN,
                        customerName = "Bisi Adeyemi",
                        garmentSummary = "Iro and Buba",
                        isRead = true,
                        createdAt = 0L,
                    ),
                    relativeTime = "3 May",
                    onClick = {},
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotificationRowDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            Column {
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
                    relativeTime = "2h",
                    onClick = {},
                )
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
                    relativeTime = "Tue",
                    onClick = {},
                )
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
                    relativeTime = "5h",
                    onClick = {},
                )
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
                    relativeTime = "Mon",
                    onClick = {},
                )
                NotificationRow(
                    notification = Notification(
                        id = "o5__UNKNOWN",
                        orderId = "o5",
                        type = NotificationType.UNKNOWN,
                        customerName = "Bisi Adeyemi",
                        garmentSummary = "Iro and Buba",
                        isRead = true,
                        createdAt = 0L,
                    ),
                    relativeTime = "3 May",
                    onClick = {},
                )
            }
        }
    }
}
