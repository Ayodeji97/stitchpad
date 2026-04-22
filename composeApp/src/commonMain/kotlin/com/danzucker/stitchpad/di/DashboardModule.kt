package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardPresentationModule = module {
    viewModel {
        DashboardViewModel(
            orderRepository = get(),
            customerRepository = get(),
            authRepository = get()
        )
    }
}
