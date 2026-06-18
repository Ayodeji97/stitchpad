package com.danzucker.stitchpad.di

import coil3.imageLoader
import com.danzucker.stitchpad.BuildConfig
import com.danzucker.stitchpad.core.data.preferences.ThemePreferences
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.media.AndroidImageCompressor
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadScheduler
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.AndroidSsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsPaymentRepository
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.notification.push.AndroidPushPermissionController
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Expose the Coil singleton ImageLoader so ViewModels can prefetch images
    // (e.g. brand logo bytes for receipt rendering) without a Compose context.
    // PlatformContext is a typealias for android.content.Context on Android, so
    // get<PlatformContext>() resolves via the androidContext(app) binding registered
    // in StitchPadApplication — registering it again here would recurse infinitely.
    single { androidContext().imageLoader }
    single { OnboardingPreferences(androidContext()) } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences(androidContext()) } bind MeasurementPreferencesStore::class
    single { ThemePreferences(androidContext()) } bind ThemePreferencesStore::class
    single { OfflinePhotoStore(androidContext()) }
    single<ImageCompressor> { AndroidImageCompressor() }
    single { OfflineUploadScheduler(androidContext()) }
    single { OrderReceiptSharer(androidContext()) }
    single { WhatsAppLauncher(androidContext()) }
    single { DialerLauncher(androidContext()) }
    single { CurrentActivityHolder() }
    single<PushPermissionController> {
        AndroidPushPermissionController(
            context = androidContext(),
            activityHolder = get(),
        )
    }
    single<SsoCredentialProvider> {
        AndroidSsoCredentialProvider(
            context = androidContext(),
            activityHolder = get(),
            webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
    }
    // Android sells subscriptions via Paystack (Google Play's rules permit it);
    // iOS binds StoreKitPaymentRepository instead (see PlatformModule.ios.kt).
    single<PaymentRepository> { CloudFunctionsPaymentRepository(functions = get()) }
}
