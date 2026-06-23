package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.debug.AnalyticsDebugActions
import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DefaultAnalyticsDebugActions
import com.danzucker.stitchpad.core.debug.DefaultDebugSeeder
import com.danzucker.stitchpad.core.debug.DefaultDigestDebugActions
import com.danzucker.stitchpad.core.debug.DefaultFreemiumDebugActions
import com.danzucker.stitchpad.core.debug.DefaultReminderDebugActions
import com.danzucker.stitchpad.core.debug.DigestDebugActions
import com.danzucker.stitchpad.core.debug.FreemiumDebugActions
import com.danzucker.stitchpad.core.debug.ReminderDebugActions
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
    single {
        DebugSessionActions(
            authRepository = get(),
            onboardingPreferences = get(),
            signOutUseCase = get(),
            communityBannerDismissal = get(),
        )
    }
    single<FreemiumDebugActions> {
        DefaultFreemiumDebugActions(
            firestore = get(),
            authRepository = get(),
            freemiumRepository = get(),
        )
    }
    single<DigestDebugActions> { DefaultDigestDebugActions(functions = get()) }
    single<ReminderDebugActions> { DefaultReminderDebugActions(functions = get()) }
    single<AnalyticsDebugActions> { DefaultAnalyticsDebugActions() }
    // Explicit `viewModel { ... }` factory rather than viewModelOf(::DebugMenuViewModel) because
    // the VM takes a defaulted Boolean param (testAccountsConfigured) — viewModelOf can't skip
    // defaulted params (see feedback_ios_clock_injection memory).
    viewModel {
        DebugMenuViewModel(
            seeder = get(),
            sessionActions = get(),
            freemiumActions = get(),
            digestActions = get(),
            reminderActions = get(),
            analyticsActions = get(),
            now = ::nowEpochMs,
        )
    }
}
