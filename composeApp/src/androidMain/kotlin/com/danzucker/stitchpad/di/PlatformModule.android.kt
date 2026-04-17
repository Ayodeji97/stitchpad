package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { OnboardingPreferences(androidContext()) } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences(androidContext()) } bind MeasurementPreferencesStore::class
}
