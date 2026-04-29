package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.clickable
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
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_orders_count
import stitchpad.composeapp.generated.resources.reports_orders_count_one
import stitchpad.composeapp.generated.resources.reports_section_outstanding

@Composable
fun DebtorsCard(
    debtors: List<DebtorEntry>,
    onDebtorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (debtors.isEmpty()) return
    // Hoist the mono family once per card recompose; the row composable runs N times.
    val mono = JetBrainsMonoFamily()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.reports_section_outstanding))
        debtors.forEachIndexed { index, debtor ->
            DebtorRow(
                debtor = debtor,
                monoFamily = mono,
                onClick = { onDebtorClick(debtor.customerId) }
            )
            if (index < debtors.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun DebtorRow(
    debtor: DebtorEntry,
    monoFamily: FontFamily,
    onClick: () -> Unit
) {
    val countLabel = if (debtor.orderCount == 1) {
        stringResource(Res.string.reports_orders_count_one)
    } else {
        stringResource(Res.string.reports_orders_count, debtor.orderCount)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = debtor.customerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "₦" + formatPrice(debtor.totalOwed),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = monoFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
