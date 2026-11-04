package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.sharing.formatMoney
import com.danzucker.stitchpad.feature.dashboard.domain.WorkshopEarnings
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_earnings_dismiss
import stitchpad.composeapp.generated.resources.dashboard_earnings_explanation
import stitchpad.composeapp.generated.resources.dashboard_earnings_orders
import stitchpad.composeapp.generated.resources.dashboard_earnings_received
import stitchpad.composeapp.generated.resources.dashboard_earnings_remaining
import stitchpad.composeapp.generated.resources.dashboard_earnings_supporting
import stitchpad.composeapp.generated.resources.dashboard_earnings_title

@Composable
fun WorkshopEarningsCard(
    earnings: List<WorkshopEarnings>,
    onDismiss: () -> Unit,
    onViewOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.dashboard_earnings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.dashboard_earnings_dismiss),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                Text(
                    text = stringResource(Res.string.dashboard_earnings_remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                earnings.forEach { total ->
                    Text(
                        text = formatMoney(total.remaining, total.currency),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_earnings_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                DashboardIllustration(drawable = heroIllustrationFor(FocusVariant.FirstOrder), size = 72.dp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Stack the label above the values so large amounts and larger text never
            // compete for a fixed horizontal slot. Every denomination stays explicit.
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                Text(
                    text = stringResource(Res.string.dashboard_earnings_received),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                earnings.forEach { total ->
                    Text(
                        text = formatMoney(total.collected, total.currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(Res.string.dashboard_earnings_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onViewOrders) {
                Text(stringResource(Res.string.dashboard_earnings_orders))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = DesignTokens.space2).size(DesignTokens.iconInline),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WorkshopEarningsCardLightPreview() {
    StitchPadTheme {
        WorkshopEarningsCard(listOf(WorkshopEarnings(Currency.GHS, 400.0, 3400.0)), {}, {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WorkshopEarningsCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        WorkshopEarningsCard(listOf(WorkshopEarnings(Currency.GHS, 400.0, 3400.0)), {}, {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(widthDp = 320, fontScale = 1.3f)
@Composable
private fun WorkshopEarningsCardMixedPreview() {
    StitchPadTheme {
        WorkshopEarningsCard(
            listOf(WorkshopEarnings(Currency.GHS, 400.25, 3400.50), WorkshopEarnings(Currency.NGN, 18000.0, 2400000.0)),
            {},
            {},
        )
    }
}
