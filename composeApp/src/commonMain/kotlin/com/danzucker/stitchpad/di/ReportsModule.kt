package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.reports.presentation.ReportsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reportsPresentationModule = module {
    // Lambda factory (not viewModelOf) because ReportsViewModel takes default-value
    // parameters for nowMillis and timeZone — viewModelOf can't skip those defaults.
    // Same reason the dashboard module uses a lambda factory.
    viewModel {
        ReportsViewModel(
            orderRepository = get(),
            customerRepository = get(),
            authRepository = get(),
            entitlementsRepository = get()
        )
    }
}
