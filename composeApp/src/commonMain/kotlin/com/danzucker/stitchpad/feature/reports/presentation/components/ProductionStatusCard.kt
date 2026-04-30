package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_section_production
import stitchpad.composeapp.generated.resources.reports_status_delivered
import stitchpad.composeapp.generated.resources.reports_status_in_progress
import stitchpad.composeapp.generated.resources.reports_status_pending
import stitchpad.composeapp.generated.resources.reports_status_ready

private val DELIVERED_COLOR = Color(0xFF0D9488)

@Composable
fun ProductionStatusCard(
    counts: ProductionCounts,
    modifier: Modifier = Modifier
) {
    val mono = JetBrainsMonoFamily()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(DesignTokens.space4)
    ) {
        Text(
            text = stringResource(Res.string.reports_section_production),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(DesignTokens.space3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
        ) {
            StatusTile(
                label = stringResource(Res.string.reports_status_pending),
                count = counts.pending,
                color = DesignTokens.warning500,
                icon = Icons.Outlined.Schedule,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_in_progress),
                count = counts.inProgress,
                color = DesignTokens.info500,
                icon = Icons.Outlined.ContentCut,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_ready),
                count = counts.ready,
                color = DesignTokens.success500,
                icon = Icons.Outlined.TaskAlt,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_delivered),
                count = counts.delivered,
                color = DELIVERED_COLOR,
                icon = Icons.Outlined.LocalShipping,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusTile(
    label: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    monoFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            fontFamily = monoFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}
