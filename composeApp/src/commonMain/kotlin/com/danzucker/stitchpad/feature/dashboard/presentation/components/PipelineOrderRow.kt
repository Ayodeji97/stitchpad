package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.dashboard.domain.PipelinePaymentStatus
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_due_in_days
import stitchpad.composeapp.generated.resources.dashboard_pipeline_deposit_due
import stitchpad.composeapp.generated.resources.dashboard_pipeline_deposit_paid
import stitchpad.composeapp.generated.resources.dashboard_pipeline_paid

private val AVATAR_SIZE = 56.dp
private val DUE_PILL_ICON_SIZE = 14.dp
private val META_ICON_SIZE = 12.dp

/**
 * V2 pipeline order row. Replaces the AccentedOrderRow used previously in
 * the Work Pipeline section. Visually brand-aligned: solid CustomerAvatar,
 * garment label with hanger icon, due-date pill on the right, and a payment
 * status + order-value footer.
 */
@Composable
fun PipelineOrderRow(
    row: DashboardOrderRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                IndigoAvatar(name = row.customerName)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = row.customerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GarmentRow(label = row.primaryLabel)
                }
                if (row.daysUntilDeadline != null) {
                    DueInPill(days = row.daysUntilDeadline)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (row.paymentStatus != null || row.orderValue != null) {
                Spacer(Modifier.height(DesignTokens.space3))
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(DesignTokens.space3))
                MetadataFooter(row = row)
            }
        }
    }
}

@Composable
private fun IndigoAvatar(name: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(scheme.primary.copy(alpha = 0.16f)),
    ) {
        Text(
            text = pipelineInitialsOf(name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

private fun pipelineInitialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotEmpty() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("").ifEmpty { "?" }

@Composable
private fun GarmentRow(label: String) {
    if (label.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Checkroom,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(META_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DueInPill(days: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(color = scheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(DUE_PILL_ICON_SIZE),
        )
        Text(
            text = stringResource(Res.string.dashboard_due_in_days, days),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
    }
}

@Composable
private fun HorizontalDivider(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
private fun MetadataFooter(row: DashboardOrderRow) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        row.paymentStatus?.let { status ->
            val (labelRes, color) = when (status) {
                // Sienna (tertiary) = brand warmth accent: a soft "attention", not an
                // alarming error and NOT saffron. Other states stay calm.
                PipelinePaymentStatus.DepositDue ->
                    Res.string.dashboard_pipeline_deposit_due to scheme.tertiary
                PipelinePaymentStatus.DepositPaid ->
                    Res.string.dashboard_pipeline_deposit_paid to scheme.onSurfaceVariant
                PipelinePaymentStatus.Paid ->
                    Res.string.dashboard_pipeline_paid to scheme.onSurfaceVariant
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        if (row.paymentStatus != null && row.orderValue != null) MetaSeparator()
        row.orderValue?.let { value ->
            Text(
                text = formatPrice(value),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineOrderRowPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineOrderRow(
            row = DashboardOrderRow(
                orderId = "1",
                customerName = "Omobolanle Johnson",
                primaryLabel = "Kaftan",
                daysUntilDeadline = 17,
                createdAtEpochMillis = 1746144000000L,
                orderValue = 40000.0,
                paymentStatus = PipelinePaymentStatus.DepositDue,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}
