package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_open_settings

@Composable
fun UserAvatar(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = Brush.linearGradient(
        colors = listOf(
            DesignTokens.primary700,
            DesignTokens.primary900,
        ),
    )
    val isDark = LocalIsDarkTheme.current
    val textColor = if (isDark) DesignTokens.primary200 else DesignTokens.primary100
    val openSettingsDesc = stringResource(Res.string.cd_open_settings)

    // Outer Box anchors the visual circle at 36dp; IconButton provides the 48dp hit area + Role.Button.
    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        // requiredSize lets the 48dp IconButton overflow the 36dp Box without clipping.
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .requiredSize(48.dp)
                .semantics { contentDescription = openSettingsDesc },
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UserAvatarPreview() {
    StitchPadTheme {
        UserAvatar(name = "Olawale", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UserAvatarLowercasePreview() {
    StitchPadTheme {
        UserAvatar(name = "daniel", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UserAvatarEmptyNamePreview() {
    StitchPadTheme {
        UserAvatar(name = "", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UserAvatarDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        UserAvatar(name = "Olawale", onClick = {})
    }
}
