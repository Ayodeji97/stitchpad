package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.reports.domain.model.AllTimeSummary
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary

data class ReportsState(
    val isLoading: Boolean = true,
    val isPremium: Boolean = true,
    val selectedPeriod: ReportsPeriod = ReportsPeriod.WEEK,
    val customRange: CustomRange? = null,
    val hasAnyOrders: Boolean = false,
    // V1.1 fields kept for the existing hero card; deleted in V2 stage 3.
    val revenueSummary: RevenueSummary? = null,
    val allTimeSummary: AllTimeSummary? = null,
    // V2 fields populated alongside the V1.1 ones during the transition.
    val kpiSummary: KpiSummary? = null,
    val productionCounts: ProductionCounts? = null,
    val topCustomers: List<CustomerRanking> = emptyList(),
    val debtors: List<DebtorEntry> = emptyList(),
    val errorMessage: UiText? = null
)
