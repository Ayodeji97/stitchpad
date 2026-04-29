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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_section_outstanding
import stitchpad.composeapp.generated.resources.reports_status_due_today
import stitchpad.composeapp.generated.resources.reports_status_next_week
import stitchpad.composeapp.generated.resources.reports_status_overdue
import stitchpad.composeapp.generated.resources.reports_status_this_week

private const val DAYS_THIS_WEEK = 7
private const val DAYS_NEXT_WEEK = 14

@Composable
fun OutstandingBalancesCard(
    debtors: List<DebtorEntry>,
    today: LocalDate,
    onDebtorClick: (String) -> Unit,
    onSendReminder: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (debtors.isEmpty()) return
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
            title = stringResource(Res.string.reports_section_outstanding),
            onViewAllClick = onViewAllClick
        )
        debtors.forEachIndexed { index, debtor ->
            OutstandingRow(
                debtor = debtor,
                today = today,
                monoFamily = mono,
                onRowClick = { onDebtorClick(debtor.customerId) },
                onWhatsAppClick = { onSendReminder(debtor.customerId) }
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
private fun OutstandingRow(
    debtor: DebtorEntry,
    today: LocalDate,
    monoFamily: FontFamily,
    onRowClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val urgency = urgencyOf(debtor.oldestDeadline, today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(vertical = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CustomerAvatar(name = debtor.customerName, seedId = debtor.customerId, size = 36.dp)
        Text(
            modifier = Modifier.weight(1f),
            text = debtor.customerName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        // Fixed-width right column so amounts/pills line up across rows even
        // when amount widths differ. Centered horizontally so the narrower
        // pill gets balanced spacing on either side. Due date dropped — the
        // pill already says "Overdue / Due Today / This Week / Next Week".
        Column(
            modifier = Modifier.width(110.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "₦" + formatPrice(debtor.totalOwed),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = monoFamily,
                fontWeight = FontWeight.Bold,
                color = urgency.amountColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            StatusPill(label = urgency.label, fg = urgency.pillFg, bg = urgency.pillBg)
        }
        WhatsAppButton(onClick = onWhatsAppClick)
    }
}

@Composable
private fun StatusPill(label: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun WhatsAppButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF25D366))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

private data class UrgencyStyle(
    val label: String,
    val amountColor: Color,
    val pillFg: Color,
    val pillBg: Color
)

@Composable
private fun urgencyOf(deadline: LocalDate?, today: LocalDate): UrgencyStyle {
    val saffron = DesignTokens.primary500
    val saffronTint = DesignTokens.primary100
    val red = DesignTokens.error500
    val redTint = DesignTokens.error50
    val orange = DesignTokens.warning500
    val orangeTint = DesignTokens.warning50
    val green = DesignTokens.success500
    val greenTint = DesignTokens.success50
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    if (deadline == null) {
        return UrgencyStyle(
            label = stringResource(Res.string.reports_status_overdue),
            amountColor = red,
            pillFg = red,
            pillBg = redTint
        )
    }
    val daysUntil = today.daysUntil(deadline)
    return when {
        daysUntil < 0 -> UrgencyStyle(
            label = stringResource(Res.string.reports_status_overdue),
            amountColor = red,
            pillFg = red,
            pillBg = redTint
        )
        daysUntil == 0 -> UrgencyStyle(
            label = stringResource(Res.string.reports_status_due_today),
            amountColor = orange,
            pillFg = orange,
            pillBg = orangeTint
        )
        daysUntil <= DAYS_THIS_WEEK -> UrgencyStyle(
            label = stringResource(Res.string.reports_status_this_week),
            amountColor = saffron,
            pillFg = saffron,
            pillBg = saffronTint
        )
        daysUntil <= DAYS_NEXT_WEEK -> UrgencyStyle(
            label = stringResource(Res.string.reports_status_next_week),
            amountColor = green,
            pillFg = green,
            pillBg = greenTint
        )
        else -> UrgencyStyle(
            label = stringResource(Res.string.reports_status_next_week),
            amountColor = muted,
            pillFg = green,
            pillBg = greenTint
        )
    }
}
