package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notification.presentation.inbox.components.NotificationRow
import com.danzucker.stitchpad.feature.notification.presentation.inbox.components.relativeTimeLabel
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notifications_back_cd
import stitchpad.composeapp.generated.resources.notifications_empty_subtitle
import stitchpad.composeapp.generated.resources.notifications_empty_title
import stitchpad.composeapp.generated.resources.notifications_load_error
import stitchpad.composeapp.generated.resources.notifications_mark_all_read
import stitchpad.composeapp.generated.resources.notifications_section_earlier
import stitchpad.composeapp.generated.resources.notifications_section_today
import stitchpad.composeapp.generated.resources.notifications_title

private val SectionLetterSpacing = 0.8.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsInboxScreen(
    state: NotificationsInboxState,
    now: Long,
    onAction: (NotificationsInboxAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.notifications_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(NotificationsInboxAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.notifications_back_cd),
                        )
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        TextButton(onClick = { onAction(NotificationsInboxAction.OnMarkAllReadClick) }) {
                            Text(
                                text = stringResource(Res.string.notifications_mark_all_read),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    LoadingDots()
                }
            }
            state.notifications.isEmpty() && state.errorMessage != null -> {
                // Load failed and we have nothing cached — show a distinct error
                // state rather than the empty illustration, which would mislead
                // the user into thinking they genuinely have no notifications.
                NotificationsErrorState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
            state.notifications.isEmpty() -> {
                // Genuinely empty — no notifications at all.
                NotificationsEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
            else -> {
                val tz = remember { TimeZone.currentSystemDefault() }
                val sections = remember(state.notifications, now, tz) {
                    groupNotificationsByDay(state.notifications, now, tz)
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    sections.forEach { section ->
                        item(key = "header_${section.isToday}") {
                            Text(
                                text = stringResource(
                                    if (section.isToday) {
                                        Res.string.notifications_section_today
                                    } else {
                                        Res.string.notifications_section_earlier
                                    },
                                ).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = SectionLetterSpacing,
                                modifier = Modifier.padding(
                                    start = DesignTokens.space4,
                                    end = DesignTokens.space4,
                                    top = DesignTokens.space4,
                                    bottom = DesignTokens.space2,
                                ),
                            )
                        }
                        itemsIndexed(section.items, key = { _, n -> n.id }) { index, n ->
                            NotificationRow(
                                notification = n,
                                relativeTime = relativeTimeLabel(notificationRelativeTime(n.createdAt, now, tz)),
                                onClick = { onAction(NotificationsInboxAction.OnNotificationClick(it)) },
                            )
                            if (index < section.items.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    // Align divider under text column:
                                    // horizontal padding + icon square + spacedBy gap
                                    modifier = Modifier.padding(
                                        start = DesignTokens.space4 + 40.dp + DesignTokens.space3,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsErrorState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = DesignTokens.space8),
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusXl))
                    .background(MaterialTheme.colorScheme.errorContainer),
            ) {
                Icon(
                    imageVector = Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = stringResource(Res.string.notifications_load_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(3f))
    }
}

@Composable
private fun NotificationsEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = DesignTokens.space8),
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusXl))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = stringResource(Res.string.notifications_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.notifications_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(3f))
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun NotificationsInboxScreenErrorPreview() {
    StitchPadTheme {
        NotificationsInboxScreen(
            state = NotificationsInboxState(
                isLoading = false,
                errorMessage = UiText.DynamicString("Couldn't load notifications. Check your connection."),
            ),
            now = 1_780_484_400_000L,
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun NotificationsInboxScreenEmptyPreview() {
    StitchPadTheme {
        NotificationsInboxScreen(
            state = NotificationsInboxState(isLoading = false),
            now = 1_780_484_400_000L,
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun NotificationsInboxScreenFilledPreview() {
    // Two today items + one earlier item so both section headers appear.
    StitchPadTheme {
        NotificationsInboxScreen(
            state = NotificationsInboxState(
                isLoading = false,
                notifications = listOf(
                    Notification(
                        id = "o1__OVERDUE",
                        orderId = "o1",
                        type = NotificationType.OVERDUE,
                        customerName = "Fola Sunday",
                        garmentSummary = "Agbada",
                        isRead = false,
                        createdAt = 1_780_484_400_000L - 2 * 3_600_000L,
                    ),
                    Notification(
                        id = "o2__DUE_SOON",
                        orderId = "o2",
                        type = NotificationType.DUE_SOON,
                        customerName = "Aina Paul",
                        garmentSummary = "Ankara suit",
                        isRead = false,
                        createdAt = 1_780_484_400_000L - 5 * 3_600_000L,
                    ),
                    Notification(
                        id = "o3__TO_COLLECT",
                        orderId = "o3",
                        type = NotificationType.TO_COLLECT,
                        customerName = "Dayyo Au",
                        garmentSummary = "Buba",
                        amount = 15_000.0,
                        isRead = true,
                        createdAt = 1_780_484_400_000L - 3 * 86_400_000L,
                    ),
                ),
            ),
            now = 1_780_484_400_000L,
            onAction = {},
        )
    }
}
