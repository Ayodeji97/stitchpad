package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.smart.data.FunctionsCaller
import com.danzucker.stitchpad.feature.smart.data.GitLiveFunctionsCaller
import com.danzucker.stitchpad.feature.smart.data.InMemorySmartUsageStore
import com.danzucker.stitchpad.feature.smart.data.SmartCustomerSearchAdapter
import com.danzucker.stitchpad.feature.smart.data.SmartFunctionsRepository
import com.danzucker.stitchpad.feature.smart.data.SmartOpenOrdersAdapter
import com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import com.danzucker.stitchpad.feature.smart.presentation.draft.CustomerSearchProvider
import com.danzucker.stitchpad.feature.smart.presentation.draft.DraftMessageViewModel
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val smartDataModule = module {
    single<FirebaseFunctions> { Firebase.functions("europe-west1") }
    single<FunctionsCaller> { GitLiveFunctionsCaller(get()) }
    single<SmartRepository> { SmartFunctionsRepository(get()) }
    single<OpenOrdersProvider> {
        SmartOpenOrdersAdapter(authRepository = get(), orderRepository = get())
    }
    single<CustomerSearchProvider> {
        SmartCustomerSearchAdapter(authRepository = get(), customerRepository = get())
    }
    // V1: always-online stand-in. Replace with a real ConnectivityObserver in V1.5.
    single<StateFlow<Boolean>>(qualifier = named("connectivity")) {
        MutableStateFlow(true)
    }
    // Process-local cache of the last-known free-tier remaining quota.
    // Updated by DraftMessageViewModel on each successful draft; observed
    // by the dashboard so the SmartSectionCard chip stays in sync.
    single<SmartUsageStore> { InMemorySmartUsageStore() }
}

val smartPresentationModule = module {
    viewModel {
        DraftMessageViewModel(
            repository = get(),
            orderProvider = get(),
            customerProvider = get(),
            connectivity = get(named("connectivity")),
            usageStore = get(),
        )
    }
}
