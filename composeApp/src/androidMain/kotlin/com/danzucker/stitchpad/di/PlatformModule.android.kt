package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.BuildConfig
import com.danzucker.stitchpad.core.data.preferences.ThemePreferences
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.AndroidSsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
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
    single { ThemePreferences(androidContext()) } bind ThemePreferencesStore::class
    single { OrderReceiptSharer(androidContext()) }
    single { WhatsAppLauncher(androidContext()) }
    single { DialerLauncher(androidContext()) }
    single { CurrentActivityHolder() }
    single<SsoCredentialProvider> {
        AndroidSsoCredentialProvider(
            context = androidContext(),
            activityHolder = get(),
            webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
    }
}
