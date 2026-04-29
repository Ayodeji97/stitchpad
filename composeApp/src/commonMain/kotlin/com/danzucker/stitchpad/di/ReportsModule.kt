package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.reports.presentation.ReportsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reportsPresentationModule = module {
    viewModel {
        ReportsViewModel(
            orderRepository = get(),
            customerRepository = get(),
            authRepository = get()
        )
    }
}
