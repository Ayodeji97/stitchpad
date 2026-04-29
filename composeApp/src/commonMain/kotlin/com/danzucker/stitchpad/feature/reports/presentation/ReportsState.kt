package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.reports.domain.model.AllTimeSummary
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary

data class ReportsState(
    val isLoading: Boolean = true,
    val selectedPeriod: ReportsPeriod = ReportsPeriod.WEEK,
    val hasAnyOrders: Boolean = false,
    val revenueSummary: RevenueSummary? = null,
    val topCustomers: List<CustomerRanking> = emptyList(),
    val debtors: List<DebtorEntry> = emptyList(),
    val allTimeSummary: AllTimeSummary? = null,
    val errorMessage: UiText? = null
)
