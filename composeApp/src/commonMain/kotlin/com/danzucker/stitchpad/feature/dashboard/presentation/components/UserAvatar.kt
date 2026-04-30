package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

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
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(gradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = DesignTokens.primary100,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview
@Composable
private fun UserAvatarPreview() {
    StitchPadTheme {
        UserAvatar(name = "Olawale", onClick = {})
    }
}

@Preview
@Composable
private fun UserAvatarLowercasePreview() {
    StitchPadTheme {
        UserAvatar(name = "daniel", onClick = {})
    }
}

@Preview
@Composable
private fun UserAvatarEmptyNamePreview() {
    StitchPadTheme {
        UserAvatar(name = "", onClick = {})
    }
}
