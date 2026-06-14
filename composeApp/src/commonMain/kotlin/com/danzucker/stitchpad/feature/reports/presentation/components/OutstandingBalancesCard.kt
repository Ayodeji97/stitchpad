package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.CappedList
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_aging_due_in_days
import stitchpad.composeapp.generated.resources.reports_aging_due_today
import stitchpad.composeapp.generated.resources.reports_aging_due_tomorrow
import stitchpad.composeapp.generated.resources.reports_aging_overdue_days
import stitchpad.composeapp.generated.resources.reports_aging_overdue_one_day
import stitchpad.composeapp.generated.resources.reports_section_outstanding
import stitchpad.composeapp.generated.resources.reports_send_whatsapp_reminder_cd

private const val DAYS_THIS_WEEK = 7
private const val DAYS_NEXT_WEEK = 14

@Composable
fun OutstandingBalancesCard(
    debtors: CappedList<DebtorEntry>,
    today: LocalDate,
    onDebtorClick: (String) -> Unit,
    onSendReminder: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (debtors.items.isEmpty()) return
    val mono = JetBrainsMonoFamily()
    ReportsCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = DesignTokens.space4,
            vertical = DesignTokens.space3
        )
    ) {
        CardHeader(
            title = stringResource(Res.string.reports_section_outstanding),
            // Hide the link when there's nothing extra hiding behind it; show
            // total when there is, so the user knows it's worth tapping.
            onViewAllClick = onViewAllClick.takeIf { debtors.hasMore },
            viewAllCount = debtors.totalCount.takeIf { debtors.hasMore }
        )
        debtors.items.forEachIndexed { index, debtor ->
            OutstandingRow(
                debtor = debtor,
                today = today,
                monoFamily = mono,
                onRowClick = { onDebtorClick(debtor.customerId) },
                onWhatsAppClick = { onSendReminder(debtor.customerId) }
            )
            if (index < debtors.items.lastIndex) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Right column: amount on top, relative-aging subtitle below
        // ("12 days overdue" / "Due today" / "Due in 3 days"). Both are
        // center-aligned within a fixed 110dp width so the amounts read
        // as a clean centered column across rows.
        Column(
            modifier = Modifier.width(110.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
            if (urgency.agingText != null) {
                Text(
                    text = urgency.agingText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = urgency.agingWeight,
                    color = urgency.agingColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (debtor.canSendWhatsAppReminder) {
            WhatsAppButton(onClick = onWhatsAppClick)
        }
    }
}

@Composable
private fun WhatsAppButton(onClick: () -> Unit) {
    // Quiet indigo ghost action, not a native-WhatsApp-green fill — the brand
    // green was an orphan that fought the palette (PTSP-39).
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = stringResource(
                Res.string.reports_send_whatsapp_reminder_cd
            ),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(15.dp)
        )
    }
}

private data class UrgencyStyle(
    val amountColor: Color,
    val agingText: String?,
    val agingColor: Color,
    val agingWeight: FontWeight
)

@Composable
private fun urgencyOf(deadline: LocalDate?, today: LocalDate): UrgencyStyle {
    // Calm ramp (PTSP-39): colour signals only the two states that need action —
    // overdue (red) and due today (orange). Everything further out renders the
    // amount in plain ink, so the card stops reading as a rainbow.
    val red = DesignTokens.error500
    val orange = DesignTokens.warning500
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    if (deadline == null) {
        return UrgencyStyle(
            amountColor = red,
            agingText = null,
            agingColor = muted,
            agingWeight = FontWeight.Normal
        )
    }
    val daysUntil = today.daysUntil(deadline)
    return when {
        daysUntil < 0 -> UrgencyStyle(
            amountColor = red,
            agingText = if (daysUntil == -1) {
                stringResource(Res.string.reports_aging_overdue_one_day)
            } else {
                stringResource(Res.string.reports_aging_overdue_days, -daysUntil)
            },
            agingColor = red,
            agingWeight = FontWeight.SemiBold
        )
        daysUntil == 0 -> UrgencyStyle(
            amountColor = orange,
            agingText = stringResource(Res.string.reports_aging_due_today),
            agingColor = orange,
            agingWeight = FontWeight.SemiBold
        )
        daysUntil <= DAYS_THIS_WEEK -> UrgencyStyle(
            amountColor = ink,
            agingText = if (daysUntil == 1) {
                stringResource(Res.string.reports_aging_due_tomorrow)
            } else {
                stringResource(Res.string.reports_aging_due_in_days, daysUntil)
            },
            agingColor = muted,
            agingWeight = FontWeight.Normal
        )
        daysUntil <= DAYS_NEXT_WEEK -> UrgencyStyle(
            amountColor = ink,
            agingText = stringResource(Res.string.reports_aging_due_in_days, daysUntil),
            agingColor = muted,
            agingWeight = FontWeight.Normal
        )
        else -> UrgencyStyle(
            amountColor = muted,
            agingText = stringResource(Res.string.reports_aging_due_in_days, daysUntil),
            agingColor = muted,
            agingWeight = FontWeight.Normal
        )
    }
}
