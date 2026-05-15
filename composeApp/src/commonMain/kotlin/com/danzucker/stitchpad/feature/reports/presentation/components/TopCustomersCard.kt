package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.CappedList
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerBadge
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_badge_repeat
import stitchpad.composeapp.generated.resources.reports_badge_vip
import stitchpad.composeapp.generated.resources.reports_orders_count
import stitchpad.composeapp.generated.resources.reports_orders_count_one
import stitchpad.composeapp.generated.resources.reports_section_top_customers
import stitchpad.composeapp.generated.resources.reports_top_customers_total_spent
import stitchpad.composeapp.generated.resources.reports_view_all
import stitchpad.composeapp.generated.resources.reports_view_all_with_count

@Composable
fun TopCustomersCard(
    rankings: CappedList<CustomerRanking>,
    onCustomerClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (rankings.items.isEmpty()) return
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
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        CardHeader(
            title = stringResource(Res.string.reports_section_top_customers),
            // Hide the link entirely when nothing is hidden behind it — without
            // overflow "View all" is a lie. When there is overflow, surface the
            // total so the user knows what's waiting on the next screen.
            onViewAllClick = onViewAllClick.takeIf { rankings.hasMore },
            viewAllCount = rankings.totalCount.takeIf { rankings.hasMore }
        )
        rankings.items.forEachIndexed { index, ranking ->
            TopCustomerRow(
                ranking = ranking,
                monoFamily = mono,
                onClick = { onCustomerClick(ranking.customerId) }
            )
            if (index < rankings.items.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun TopCustomerRow(
    ranking: CustomerRanking,
    monoFamily: FontFamily,
    onClick: () -> Unit
) {
    val countLabel = if (ranking.orderCount == 1) {
        stringResource(Res.string.reports_orders_count_one)
    } else {
        stringResource(Res.string.reports_orders_count, ranking.orderCount)
    }
    val totalSpentLabel = stringResource(Res.string.reports_top_customers_total_spent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        CustomerAvatar(name = ranking.customerName, seedId = ranking.customerId)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = ranking.customerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "$countLabel · $totalSpentLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Fixed-width slots so amounts/badges line up across rows regardless
        // of amount length or whether a badge is present (BadgeChip returns
        // nothing for NONE — without a reserved slot the chevron drifts).
        Text(
            modifier = Modifier.width(110.dp),
            text = "₦" + formatPrice(ranking.totalCollected),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = monoFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Box(
            modifier = Modifier.width(72.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgeChip(badge = ranking.badge)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BadgeChip(badge: CustomerBadge) {
    if (badge == CustomerBadge.NONE) return
    val style = when (badge) {
        CustomerBadge.VIP -> BadgeStyle(
            text = stringResource(Res.string.reports_badge_vip),
            icon = Icons.Default.WorkspacePremium,
            fg = LocalStitchPadColors.current.heritageAccent,
            bg = LocalStitchPadColors.current.heritageAccent.copy(alpha = 0.15f)
        )
        CustomerBadge.REPEAT -> BadgeStyle(
            text = stringResource(Res.string.reports_badge_repeat),
            icon = Icons.Default.Loop,
            fg = DesignTokens.success500,
            bg = DesignTokens.success50
        )
        CustomerBadge.NONE -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(style.bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.fg,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = style.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = style.fg
        )
    }
}

private data class BadgeStyle(
    val text: String,
    val icon: ImageVector,
    val fg: Color,
    val bg: Color
)

@Composable
internal fun CardHeader(
    title: String,
    onViewAllClick: (() -> Unit)? = null,
    viewAllCount: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onViewAllClick != null) {
            val label = if (viewAllCount != null) {
                stringResource(Res.string.reports_view_all_with_count, viewAllCount)
            } else {
                stringResource(Res.string.reports_view_all)
            }
            Row(
                modifier = Modifier.clickable(onClick = onViewAllClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
