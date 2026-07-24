@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.order.presentation.detail.costCategoryOrder
import com.danzucker.stitchpad.feature.order.presentation.detail.label
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_costs_add_button
import stitchpad.composeapp.generated.resources.order_costs_add_cost
import stitchpad.composeapp.generated.resources.order_costs_empty_body
import stitchpad.composeapp.generated.resources.order_costs_loss
import stitchpad.composeapp.generated.resources.order_costs_private_caption
import stitchpad.composeapp.generated.resources.order_costs_profit
import stitchpad.composeapp.generated.resources.order_costs_section
import stitchpad.composeapp.generated.resources.order_costs_total
import kotlin.math.abs
import kotlin.math.roundToInt

private val INDENT_START = 28.dp + 8.dp // icon width + space2 gap

@Composable
fun OrderCostsCard(
    costs: List<OrderCost>,
    totalCost: Double,
    profit: Double,
    profitMargin: Double?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                CostsSectionIconTile(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.order_costs_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            if (costs.isEmpty()) {
                OrderCostsEmptyState(onEditClick = onEditClick)
            } else {
                OrderCostsFilledContent(
                    costs = costs,
                    totalCost = totalCost,
                    profit = profit,
                    profitMargin = profitMargin,
                    onEditClick = onEditClick,
                )
            }
        }
    }
}

@Composable
private fun OrderCostsEmptyState(onEditClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = INDENT_START),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Text(
            text = stringResource(Res.string.order_costs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.Start,
        ) {
            Button(onClick = onEditClick) {
                Text(
                    text = stringResource(Res.string.order_costs_add_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        PrivateCaption()
    }
}

@Composable
private fun OrderCostsFilledContent(
    costs: List<OrderCost>,
    totalCost: Double,
    profit: Double,
    profitMargin: Double?,
    onEditClick: () -> Unit,
) {
    val costsByCategory = costs.associateBy { it.category }

    Column(
        modifier = Modifier.padding(start = INDENT_START),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        costCategoryOrder.forEach { category ->
            val cost = costsByCategory[category]
            if (cost != null) {
                CostRow(category = category, cost = cost)
            }
        }
    }

    Spacer(Modifier.height(DesignTokens.space2))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
            .padding(start = INDENT_START),
    ) {
        Text(
            text = stringResource(Res.string.order_costs_add_cost),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = DesignTokens.space2),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = INDENT_START),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.order_costs_total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "₦${formatPrice(totalCost)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    Spacer(Modifier.height(DesignTokens.space2))

    ProfitBand(profit = profit, profitMargin = profitMargin)

    Spacer(Modifier.height(DesignTokens.space3))

    PrivateCaption()
}

@Composable
private fun CostRow(category: CostCategory, cost: OrderCost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            cost.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "₦${formatPrice(cost.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProfitBand(profit: Double, profitMargin: Double?) {
    val isLoss = profit < 0.0
    val dark = isSystemInDarkTheme()
    val containerColor = if (isLoss) {
        if (dark) DesignTokens.errorDarkBg else DesignTokens.error50
    } else {
        if (dark) DesignTokens.successDarkBg else DesignTokens.success50
    }
    val textColor = if (isLoss) {
        if (dark) DesignTokens.errorDarkText else DesignTokens.error500
    } else {
        if (dark) DesignTokens.successDarkText else DesignTokens.success500
    }

    val amountText = buildString {
        if (isLoss) append('−')
        append('₦')
        append(formatPrice(abs(profit)))
        profitMargin?.let { margin ->
            append(" · ")
            append((margin * 100).roundToInt())
            append('%')
        }
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLoss) {
                    stringResource(Res.string.order_costs_loss)
                } else {
                    stringResource(Res.string.order_costs_profit)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor,
            )
        }
    }
}

@Composable
private fun PrivateCaption() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DesignTokens.iconInline),
        )
        Text(
            text = stringResource(Res.string.order_costs_private_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CostsSectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = DesignTokens.sienna500.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = DesignTokens.sienna500,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews

private val PREVIEW_COSTS_PROFIT = listOf(
    OrderCost(category = CostCategory.FABRIC, amount = 18_000.0, note = null),
    OrderCost(category = CostCategory.MATERIALS_TRIMS, amount = 4_500.0, note = "thread, zips"),
    OrderCost(category = CostCategory.LABOUR, amount = 12_000.0, note = null),
)

private val PREVIEW_COSTS_LOSS = listOf(
    OrderCost(category = CostCategory.FABRIC, amount = 40_000.0, note = null),
    OrderCost(category = CostCategory.EMBELLISHMENT, amount = 25_000.0, note = "beading"),
    OrderCost(category = CostCategory.LABOUR, amount = 20_000.0, note = null),
    OrderCost(category = CostCategory.LOGISTICS, amount = 5_000.0, note = null),
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardFilledProfitLightPreview() {
    StitchPadTheme {
        OrderCostsCard(
            costs = PREVIEW_COSTS_PROFIT,
            totalCost = PREVIEW_COSTS_PROFIT.sumOf { it.amount },
            profit = 45_500.0,
            profitMargin = 0.325,
            onEditClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardFilledProfitDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderCostsCard(
            costs = PREVIEW_COSTS_PROFIT,
            totalCost = PREVIEW_COSTS_PROFIT.sumOf { it.amount },
            profit = 45_500.0,
            profitMargin = 0.325,
            onEditClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardLossLightPreview() {
    StitchPadTheme {
        OrderCostsCard(
            costs = PREVIEW_COSTS_LOSS,
            totalCost = PREVIEW_COSTS_LOSS.sumOf { it.amount },
            profit = -10_000.0,
            profitMargin = -0.1136,
            onEditClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardLossDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderCostsCard(
            costs = PREVIEW_COSTS_LOSS,
            totalCost = PREVIEW_COSTS_LOSS.sumOf { it.amount },
            profit = -10_000.0,
            profitMargin = -0.1136,
            onEditClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardEmptyLightPreview() {
    StitchPadTheme {
        OrderCostsCard(
            costs = emptyList(),
            totalCost = 0.0,
            profit = 88_000.0,
            profitMargin = 1.0,
            onEditClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCostsCardEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderCostsCard(
            costs = emptyList(),
            totalCost = 0.0,
            profit = 88_000.0,
            profitMargin = 1.0,
            onEditClick = {},
        )
    }
}

// endregion
