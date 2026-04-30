package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.danzucker.stitchpad.feature.dashboard.presentation.model.TodayWorkRowUi
import com.danzucker.stitchpad.ui.components.AccentedOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_today_work_title
import stitchpad.composeapp.generated.resources.dashboard_today_work_view_all

private const val MAX_VISIBLE_ROWS = 5

@Composable
fun TodayWorkCard(
    rows: List<TodayWorkRowUi>,
    onRowClick: (orderId: String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val visible = rows.take(MAX_VISIBLE_ROWS)
    val hasMore = rows.size > MAX_VISIBLE_ROWS
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_today_work_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (hasMore) {
                    TextButton(
                        onClick = onViewAllClick,
                        contentPadding = PaddingValues(horizontal = DesignTokens.space2, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_today_work_view_all, rows.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(DesignTokens.space2))
            visible.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(DesignTokens.space2))
                AccentedOrderRow(
                    customerName = row.customerName,
                    primaryLabel = row.primaryLabel,
                    accentColor = row.accentColor,
                    chipText = row.chipText,
                    chipTextColor = row.chipTextColor,
                    chipBackground = row.chipBackground,
                    onClick = { onRowClick(row.orderId) },
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TodayWorkCardPreview() {
    StitchPadTheme {
        TodayWorkCard(
            rows = listOf(
                TodayWorkRowUi.preview(
                    orderId = "1",
                    customerName = "Adaeze Okoro",
                    primaryLabel = "Buba & Skirt · Due 4 PM",
                    accent = DesignTokens.error500,
                    chip = "Due today",
                ),
                TodayWorkRowUi.preview(
                    orderId = "2",
                    customerName = "Kunle Adeyemi",
                    primaryLabel = "Senator Wear · Ready",
                    accent = DesignTokens.success500,
                    chip = "Ready pickup",
                ),
                TodayWorkRowUi.preview(
                    orderId = "3",
                    customerName = "Ifeoma Balogun",
                    primaryLabel = "Bridesmaid Dress · Fitting 2 PM",
                    accent = DesignTokens.warning500,
                    chip = "Fitting today",
                ),
            ),
            onRowClick = {},
            onViewAllClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TodayWorkCardOverflowPreview() {
    StitchPadTheme {
        TodayWorkCard(
            rows = (1..7).map { i ->
                TodayWorkRowUi.preview(
                    orderId = i.toString(),
                    customerName = "Customer $i",
                    primaryLabel = "Garment · today",
                    accent = DesignTokens.warning500,
                    chip = "Due today",
                )
            },
            onRowClick = {},
            onViewAllClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TodayWorkCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        TodayWorkCard(
            rows = listOf(
                TodayWorkRowUi.preview(
                    orderId = "1",
                    customerName = "Adaeze Okoro",
                    primaryLabel = "Buba & Skirt · Due 4 PM",
                    accent = DesignTokens.error500,
                    chip = "Due today",
                ),
                TodayWorkRowUi.preview(
                    orderId = "2",
                    customerName = "Kunle Adeyemi",
                    primaryLabel = "Senator Wear · Ready",
                    accent = DesignTokens.success500,
                    chip = "Ready pickup",
                ),
            ),
            onRowClick = {},
            onViewAllClick = {},
        )
    }
}
