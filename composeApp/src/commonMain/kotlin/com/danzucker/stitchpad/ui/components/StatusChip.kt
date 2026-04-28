package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens

private val CHIP_FONT_SIZE: TextUnit = 12.sp

@Composable
fun StatusChip(
    text: String,
    textColor: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = background,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = CHIP_FONT_SIZE,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space1
            )
        )
    }
}
