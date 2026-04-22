package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import kotlinx.datetime.LocalDate

enum class Greeting { MORNING, AFTERNOON, EVENING }

data class DashboardOrderRow(
    val orderId: String,
    val customerName: String,
    val primaryLabel: String,
    val secondaryLabel: String? = null
)

data class DashboardState(
    val isLoading: Boolean = true,
    val businessName: String? = null,
    val greeting: Greeting = Greeting.MORNING,
    val todayDate: LocalDate? = null,
    val overdue: List<DashboardOrderRow> = emptyList(),
    val dueToday: List<DashboardOrderRow> = emptyList(),
    val ready: List<DashboardOrderRow> = emptyList(),
    val outstandingAmount: Double = 0.0,
    val outstandingOrderCount: Int = 0,
    val isBrandNew: Boolean = false,
    val isAllClear: Boolean = false,
    val errorMessage: UiText? = null
)
