package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod

sealed interface ReportsAction {
    data class OnPeriodSelected(val period: ReportsPeriod) : ReportsAction
    data class OnCustomRangeSelected(val range: CustomRange) : ReportsAction
    data object OnClearCustomRange : ReportsAction
    data class OnTopCustomerClick(val customerId: String) : ReportsAction
    data class OnDebtorClick(val customerId: String) : ReportsAction
    data class OnSendReminderClick(val customerId: String) : ReportsAction
    data object OnUpgradeClick : ReportsAction
    data object OnErrorDismiss : ReportsAction
}
