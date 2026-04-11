package com.danzucker.stitchpad.feature.onboarding.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.onboarding_status_ready
import stitchpad.composeapp.generated.resources.onboarding_status_sewing

@Composable
fun MeasurementIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(DesignTokens.primary50),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📏", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DesignTokens.primary500)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DesignTokens.primary200)
            )
        }
    }
}

@Composable
fun OrderTrackingIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(DesignTokens.primary50),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "✂\uFE0F", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(text = stringResource(Res.string.onboarding_status_ready), color = DesignTokens.success500)
                StatusBadge(text = stringResource(Res.string.onboarding_status_sewing), color = DesignTokens.warning500)
            }
        }
    }
}

@Composable
fun NotebookIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(DesignTokens.primary50),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📒", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DesignTokens.primary500)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DesignTokens.primary500)
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
