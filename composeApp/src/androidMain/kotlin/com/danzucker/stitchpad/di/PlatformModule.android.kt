package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { OnboardingPreferences(androidContext()) }
}
