package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.AllTimeSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_all_time_lifetime_revenue
import stitchpad.composeapp.generated.resources.reports_all_time_top_customer
import stitchpad.composeapp.generated.resources.reports_section_all_time

@Composable
fun AllTimeSummaryCard(
    summary: AllTimeSummary,
    modifier: Modifier = Modifier
) {
    val mono = JetBrainsMonoFamily()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.reports_section_all_time))
        SummaryRow(
            label = stringResource(Res.string.reports_all_time_lifetime_revenue),
            sublabel = null,
            amount = summary.totalCollected,
            monoFamily = mono
        )
        if (summary.topCustomerName != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            SummaryRow(
                label = stringResource(Res.string.reports_all_time_top_customer),
                sublabel = summary.topCustomerName,
                amount = summary.topCustomerTotal,
                monoFamily = mono
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    sublabel: String?,
    amount: Double,
    monoFamily: FontFamily
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "₦" + formatPrice(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = monoFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
