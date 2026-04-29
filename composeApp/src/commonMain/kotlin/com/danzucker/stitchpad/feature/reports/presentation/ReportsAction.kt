package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod

sealed interface ReportsAction {
    data class OnPeriodSelected(val period: ReportsPeriod) : ReportsAction
    data class OnTopCustomerClick(val customerId: String) : ReportsAction
    data class OnDebtorClick(val customerId: String) : ReportsAction
    data object OnErrorDismiss : ReportsAction
}
