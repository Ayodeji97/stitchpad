package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DefaultDebugSeeder
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

val debugModule = module {
    single<DebugSeeder> {
        DefaultDebugSeeder(
            customerRepository = get(),
            orderRepository = get(),
            measurementRepository = get(),
            styleRepository = get(),
            authRepository = get(),
            now = ::nowEpochMs,
        )
    }
    single { DebugSessionActions(authRepository = get(), onboardingPreferences = get()) }
    // Explicit `viewModel { ... }` factory rather than viewModelOf(::DebugMenuViewModel) because
    // the VM takes a defaulted Boolean param (testAccountsConfigured) — viewModelOf can't skip
    // defaulted params (see feedback_ios_clock_injection memory).
    viewModel { DebugMenuViewModel(seeder = get(), sessionActions = get()) }
}
