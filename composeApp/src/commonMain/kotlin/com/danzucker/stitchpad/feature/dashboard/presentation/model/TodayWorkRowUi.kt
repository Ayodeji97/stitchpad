package com.danzucker.stitchpad.feature.dashboard.presentation.model

import androidx.compose.ui.graphics.Color

/**
 * UI model for a single Today's Work row. Built in DashboardViewModel from
 * BucketCalculator output; consumed by TodayWorkCard. Holds pre-computed
 * styling so the composable stays dumb.
 */
data class TodayWorkRowUi(
    val orderId: String,
    val customerName: String,
    val primaryLabel: String,
    val accentColor: Color,
    val chipText: String,
    val chipTextColor: Color,
    val chipBackground: Color,
) {
    companion object {
        /** Convenience factory used only in @Preview composables. */
        fun preview(
            orderId: String,
            customerName: String,
            primaryLabel: String,
            accent: Color,
            chip: String,
        ) = TodayWorkRowUi(
            orderId = orderId,
            customerName = customerName,
            primaryLabel = primaryLabel,
            accentColor = accent,
            chipText = chip,
            chipTextColor = accent,
            chipBackground = accent.copy(alpha = 0.12f),
        )
    }
}
