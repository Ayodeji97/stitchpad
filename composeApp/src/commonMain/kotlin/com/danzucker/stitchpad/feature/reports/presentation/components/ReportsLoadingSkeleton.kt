package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

@Composable
fun ReportsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Hero
        SkelBar(widthFraction = 0.4f, height = 10.dp)
        SkelBar(widthFraction = 0.6f, height = 28.dp, topPadding = DesignTokens.space2)
        SkelBar(widthFraction = 0.5f, height = 12.dp, topPadding = DesignTokens.space1)
        SkelBar(widthFraction = 1f, height = 32.dp, topPadding = DesignTokens.space3)

        // Top customers section
        SkelBar(widthFraction = 0.3f, height = 10.dp, topPadding = DesignTokens.space5)
        repeat(3) { SkelRow(topPadding = DesignTokens.space2) }

        // Outstanding section
        SkelBar(widthFraction = 0.4f, height = 10.dp, topPadding = DesignTokens.space5)
        repeat(2) { SkelRow(topPadding = DesignTokens.space2) }
    }
}

@Composable
private fun SkelBar(
    widthFraction: Float,
    height: Dp,
    topPadding: Dp = 0.dp
) {
    Box(
        Modifier
            .padding(top = topPadding)
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(DesignTokens.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun SkelRow(topPadding: Dp = 0.dp) {
    Row(
        modifier = Modifier
            .padding(top = topPadding)
            .fillMaxWidth()
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(Modifier.padding(horizontal = DesignTokens.space2))
        Box(
            Modifier
                .fillMaxWidth(0.25f)
                .height(14.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
