package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { OnboardingPreferences() } bind OnboardingPreferencesStore::class
}
