package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
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

@Composable
fun ProductionStatusCard(
    counts: ProductionCounts,
    modifier: Modifier = Modifier
) {
    val mono = JetBrainsMonoFamily()
    ReportsCard(modifier = modifier) {
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
                accent = DesignTokens.warning500,
                icon = Icons.Outlined.Schedule,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_in_progress),
                count = counts.inProgress,
                accent = DesignTokens.info500,
                icon = Icons.Outlined.ContentCut,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_ready),
                count = counts.ready,
                accent = DesignTokens.success500,
                icon = Icons.Outlined.TaskAlt,
                monoFamily = mono,
                modifier = Modifier.weight(1f)
            )
            StatusTile(
                label = stringResource(Res.string.reports_status_delivered),
                count = counts.delivered,
                // Delivered is a terminal, neutral state — use the design system's
                // grey status token, not the orphan teal it used to hardcode.
                accent = DesignTokens.statusDelivered,
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
    accent: Color,
    icon: ImageVector,
    monoFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Calm: the icon stays neutral; only the thin accent bar carries the
        // status colour, so the row reads as one quiet group, not four hues.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .background(accent)
        )
    }
}
