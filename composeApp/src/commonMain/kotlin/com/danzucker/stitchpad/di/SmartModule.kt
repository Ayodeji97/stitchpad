package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.smartinfra.data.ai.FunctionsCaller
import com.danzucker.stitchpad.core.smartinfra.data.ai.GitLiveFunctionsCaller
import com.danzucker.stitchpad.core.smartinfra.data.quota.InMemorySmartUsageStore
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.smart.data.SmartCustomerSearchAdapter
import com.danzucker.stitchpad.feature.smart.data.SmartFunctionsRepository
import com.danzucker.stitchpad.feature.smart.data.SmartOpenOrdersAdapter
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import com.danzucker.stitchpad.feature.smart.presentation.draft.CustomerSearchProvider
import com.danzucker.stitchpad.feature.smart.presentation.draft.DraftMessageViewModel
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    // App-lifetime scope for the SmartUsageStore's auth-state listener.
    // SupervisorJob so a transient failure in one collector doesn't kill
    // the others; private to the smart feature for now (no other consumer).
    single<CoroutineScope>(qualifier = named("smartAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    // Process-local cache of the last-known free-tier remaining quota.
    // Updated by DraftMessageViewModel on each successful draft; observed
    // by the dashboard so the SmartSectionCard chip stays in sync. Wipes
    // itself on auth changes so user A's quota never leaks into user B's
    // session within the same process.
    single<SmartUsageStore> {
        InMemorySmartUsageStore(
            auth = get(),
            scope = get<CoroutineScope>(qualifier = named("smartAppScope")),
        )
    }
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
