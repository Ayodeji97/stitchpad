package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

/**
 * Canonical Settings list row: icon + label (+optional subtitle) + slot for trailing.
 *
 * The trailing slot is intentionally a [Composable] to keep this primitive flexible:
 * a chevron, a switch, a value-and-chevron pair, or an external-link arrow can all
 * sit in the same shape without duplicating the row layout.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isDanger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val labelColor = if (isDanger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (isDanger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val iconBg = if (isDanger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val baseModifier = modifier
        .heightIn(min = 56.dp)
    val rowModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Row(
        modifier = rowModifier
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(DesignTokens.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(DesignTokens.space2))
            trailing()
        }
    }
}

@Composable
fun SettingsRowChevron(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(20.dp),
    )
}

@Composable
fun SettingsRowExternalIcon(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(16.dp),
    )
}

@Composable
fun SettingsRowValue(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsRowPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = "Email",
                    subtitle = "folake@stitchpad.app",
                    onClick = {},
                    trailing = { SettingsRowChevron() },
                )
                SettingsRow(
                    icon = Icons.Outlined.Lock,
                    label = "Change password",
                    onClick = {},
                    trailing = { SettingsRowChevron() },
                )
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = "Measurement units",
                    onClick = {},
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsRowValue("Inches")
                            Spacer(Modifier.width(DesignTokens.space2))
                            SettingsRowChevron()
                        }
                    },
                )
                SettingsRow(
                    icon = Icons.Outlined.Logout,
                    label = "Sign out",
                    onClick = {},
                )
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = "Delete account",
                    onClick = {},
                    isDanger = true,
                    trailing = { SettingsRowChevron() },
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsRowDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = "Email",
                    subtitle = "folake@stitchpad.app",
                    onClick = {},
                    trailing = { SettingsRowChevron() },
                )
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = "Delete account",
                    onClick = {},
                    isDanger = true,
                    trailing = { SettingsRowChevron() },
                )
            }
        }
    }
}

// Avoids reordered-imports complaints for unused color in some platform stubs.
@Suppress("unused")
private val SettingsRowDividerColorHint: Color = Color.Unspecified
