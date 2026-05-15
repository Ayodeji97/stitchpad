package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_tab_custom
import stitchpad.composeapp.generated.resources.reports_tab_month
import stitchpad.composeapp.generated.resources.reports_tab_week

@Composable
fun ReportsTabRow(
    selected: ReportsPeriod,
    onSelect: (ReportsPeriod) -> Unit,
    onCustomTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        TabPill(
            label = stringResource(Res.string.reports_tab_week),
            isActive = selected == ReportsPeriod.WEEK,
            onClick = { onSelect(ReportsPeriod.WEEK) },
            modifier = Modifier.weight(1f)
        )
        TabPill(
            label = stringResource(Res.string.reports_tab_month),
            isActive = selected == ReportsPeriod.MONTH,
            onClick = { onSelect(ReportsPeriod.MONTH) },
            modifier = Modifier.weight(1f)
        )
        // Custom always opens the picker — even if already selected, so users
        // can re-pick a range without first switching tabs.
        TabPill(
            label = stringResource(Res.string.reports_tab_custom),
            isActive = selected == ReportsPeriod.CUSTOM,
            onClick = onCustomTap,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isActive) {
        DesignTokens.primary500
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}
