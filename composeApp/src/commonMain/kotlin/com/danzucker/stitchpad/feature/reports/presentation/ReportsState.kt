package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod

data class ReportsState(
    val isLoading: Boolean = true,
    val isPremium: Boolean = true,
    val selectedPeriod: ReportsPeriod = ReportsPeriod.WEEK,
    val customRange: CustomRange? = null,
    val hasAnyOrders: Boolean = false,
    val kpiSummary: KpiSummary? = null,
    val productionCounts: ProductionCounts? = null,
    val topCustomers: List<CustomerRanking> = emptyList(),
    val debtors: List<DebtorEntry> = emptyList(),
    val errorMessage: UiText? = null
)
