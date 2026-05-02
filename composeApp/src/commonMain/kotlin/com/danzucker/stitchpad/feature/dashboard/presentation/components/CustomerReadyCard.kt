package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.CustomerReadyUi
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_ready_added_days_ago
import stitchpad.composeapp.generated.resources.customer_ready_added_today
import stitchpad.composeapp.generated.resources.customer_ready_badge_new
import stitchpad.composeapp.generated.resources.customer_ready_badge_no_orders
import stitchpad.composeapp.generated.resources.customer_ready_message_cd
import stitchpad.composeapp.generated.resources.customer_ready_open_cd

private val AVATAR_SIZE = 56.dp
private val ONLINE_DOT_SIZE = 12.dp
private val MESSAGE_BUTTON_SIZE = 44.dp

private val SUCCESS_BG = Color(0xFFDFF3E6)
private val SUCCESS_FG = Color(0xFF2D9E6B)
private val SUCCESS_DARK_BG = Color(0xFF153D2A)
private val SUCCESS_DARK_FG = Color(0xFF5EDBA0)
private val AVATAR_BG = Color(0xFFB07A45)
private val AVATAR_FG = Color(0xFFFCE5D8)
private val AVATAR_DARK_BG = Color(0xFF6D4520)
private val AVATAR_DARK_FG = Color(0xFFF2B07A)

/**
 * "Your customer" card shown on the FirstCustomer state. Surfaces the
 * most recently added customer with a single-tap detail open and a
 * round Message button that launches WhatsApp.
 *
 * Visual: avatar with online-dot indicator, name + "Added today",
 * two pill badges ("New customer" green, "No orders yet" muted),
 * round green Message button, trailing chevron.
 */
@Composable
fun CustomerReadyCard(
    customer: CustomerReadyUi,
    onCardClick: () -> Unit,
    onMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val openLabel = stringResource(Res.string.customer_ready_open_cd, customer.name)

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onCardClick, role = Role.Button),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            AvatarWithDot(initials = initialsOf(customer.name))
            CustomerInfo(customer = customer, modifier = Modifier.weight(1f))
            MessageRoundButton(
                contentDescription = stringResource(
                    Res.string.customer_ready_message_cd,
                    customer.name
                ),
                onClick = onMessageClick,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = openLabel,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AvatarWithDot(initials: String) {
    val isDark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val avatarBg = if (isDark) AVATAR_DARK_BG else AVATAR_BG
    val avatarFg = if (isDark) AVATAR_DARK_FG else AVATAR_FG
    val ringColor = MaterialTheme.colorScheme.surface
    val dotColor = if (isDark) SUCCESS_DARK_FG else SUCCESS_FG
    Box(modifier = Modifier.size(AVATAR_SIZE)) {
        Box(
            modifier = Modifier
                .size(AVATAR_SIZE)
                .background(color = avatarBg, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = avatarFg,
            )
        }
        // Status ring + dot in the bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ONLINE_DOT_SIZE + 4.dp)
                .background(color = ringColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ONLINE_DOT_SIZE)
                    .background(color = dotColor, shape = CircleShape),
            )
        }
    }
}

@Composable
private fun CustomerInfo(customer: CustomerReadyUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = customer.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (customer.daysSinceAdded == 0) {
                stringResource(Res.string.customer_ready_added_today)
            } else {
                stringResource(Res.string.customer_ready_added_days_ago, customer.daysSinceAdded)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space1))
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
            BadgePill(
                icon = Icons.Default.Group,
                label = stringResource(Res.string.customer_ready_badge_new),
                tone = BadgeTone.Success,
            )
            if (!customer.hasOrders) {
                BadgePill(
                    icon = Icons.Default.Sell,
                    label = stringResource(Res.string.customer_ready_badge_no_orders),
                    tone = BadgeTone.Neutral,
                )
            }
        }
    }
}

private enum class BadgeTone { Success, Neutral }

@Composable
private fun BadgePill(icon: ImageVector, label: String, tone: BadgeTone) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminanceIsDark()
    val (bg, fg) = when (tone) {
        BadgeTone.Success -> if (isDark) SUCCESS_DARK_BG to SUCCESS_DARK_FG
            else SUCCESS_BG to SUCCESS_FG
        BadgeTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color = bg)
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
private fun MessageRoundButton(contentDescription: String, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val bg = if (isDark) SUCCESS_DARK_BG else SUCCESS_BG
    val fg = if (isDark) SUCCESS_DARK_FG else SUCCESS_FG
    // IconButton in this Material3 version enforces a 48dp interactive
    // size by default — that's what we want. The visible surface is
    // MESSAGE_BUTTON_SIZE (44dp); the surrounding ~2dp ring is tap-only.
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(),
    ) {
        Box(
            modifier = Modifier
                .size(MESSAGE_BUTTON_SIZE)
                .background(color = bg, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun Color.luminanceIsDark(): Boolean {
    val r = red
    val g = green
    val b = blue
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance < 0.5f
}

internal fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '\t').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun CustomerReadyCardLightPreview() {
    StitchPadTheme {
        CustomerReadyCard(
            customer = CustomerReadyUi(
                customerId = "1",
                name = "Omobolanle Johnson",
                phone = "+2348012345678",
                daysSinceAdded = 0,
                hasOrders = false,
            ),
            onCardClick = {},
            onMessageClick = {},
            modifier = Modifier
                .padding(DesignTokens.space4)
                .offset(),
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun CustomerReadyCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        CustomerReadyCard(
            customer = CustomerReadyUi(
                customerId = "1",
                name = "Omobolanle Johnson",
                phone = "+2348012345678",
                daysSinceAdded = 0,
                hasOrders = false,
            ),
            onCardClick = {},
            onMessageClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}
