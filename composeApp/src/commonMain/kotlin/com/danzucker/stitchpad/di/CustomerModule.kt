package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.customer.data.FirebaseCustomerRepository
import com.danzucker.stitchpad.feature.customer.presentation.detail.CustomerDetailViewModel
import com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModel
import com.danzucker.stitchpad.feature.customer.presentation.list.CustomerListViewModel
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val customerDataModule = module {
    singleOf(::FirebaseCustomerRepository) bind CustomerRepository::class
}

val customerPresentationModule = module {
    viewModel {
        CustomerListViewModel(
            customerRepository = get(),
            orderRepository = get(),
            authRepository = get(),
            freemiumRepository = get<FreemiumRepository>(),
        )
    }
    viewModelOf(::CustomerFormViewModel)
    viewModelOf(::CustomerDetailViewModel)
}
