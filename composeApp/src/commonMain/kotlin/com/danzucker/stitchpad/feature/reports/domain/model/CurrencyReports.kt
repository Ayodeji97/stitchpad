package com.danzucker.stitchpad.feature.reports.domain.model

import com.danzucker.stitchpad.core.domain.model.Currency

/** Independent monetary report for one immutable order currency. Never an FX conversion. */
data class CurrencyReports(
    val currency: Currency,
    val kpiSummary: KpiSummary,
    val topCustomers: CappedList<CustomerRanking>,
    val debtors: CappedList<DebtorEntry>,
)
