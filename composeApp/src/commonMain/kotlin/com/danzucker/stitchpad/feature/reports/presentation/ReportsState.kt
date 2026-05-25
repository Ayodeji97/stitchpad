package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.reports.domain.model.CappedList
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate

data class ReportsState(
    val isLoading: Boolean = true,
    // Default to free-tier UX so the screen never flashes premium content while
    // EntitlementsProvider's first snapshot is in flight. The real value lands
    // on the first combine emission.
    val isPremium: Boolean = false,
    val selectedPeriod: ReportsPeriod = ReportsPeriod.WEEK,
    val customRange: CustomRange? = null,
    val hasAnyOrders: Boolean = false,
    val kpiSummary: KpiSummary? = null,
    val productionCounts: ProductionCounts? = null,
    val topCustomers: CappedList<CustomerRanking> = CappedList.empty(),
    val debtors: CappedList<DebtorEntry> = CappedList.empty(),
    // Recomputed every time the VM rebuilds state (whenever orders change,
    // period flips, etc.) — sourced from nowMillis there. Carrying it in
    // state instead of computing in the screen avoids the stale-overnight
    // case where 'remember(timeZone) { today }' in the composable kept the
    // pre-midnight date for as long as the user left Reports open.
    val today: LocalDate? = null,
    val errorMessage: UiText? = null
)
