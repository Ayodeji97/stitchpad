package com.danzucker.stitchpad.di

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.danzucker.stitchpad.core.data.preferences.ThemePreferences
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.core.media.IosImageCompressor
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadScheduler
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.ImageSharer
import com.danzucker.stitchpad.core.sharing.IosMeasurementSharer
import com.danzucker.stitchpad.core.sharing.MeasurementSharer
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.IosSsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.data.NativeAppleSignInLauncher
import com.danzucker.stitchpad.feature.auth.data.NativeGoogleSignInLauncher
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.freemium.data.NativeStoreKitPurchaser
import com.danzucker.stitchpad.feature.freemium.data.StoreKitPaymentRepository
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.notification.push.IosPushPermissionController
import com.danzucker.stitchpad.feature.notification.push.NativePushService
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import com.danzucker.stitchpad.feature.referral.data.IosInstallReferrerReader
import com.danzucker.stitchpad.feature.referral.data.ReferralPreferences
import com.danzucker.stitchpad.feature.referral.domain.InstallReferrerReader
import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import com.danzucker.stitchpad.feature.tutorials.data.TutorialVideoCache
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Set from iosApp's AppDelegate BEFORE doInitKoin is invoked. The Swift launchers
 * hold GIDSignIn and ASAuthorizationController calls that aren't reachable
 * from Kotlin/Native directly.
 */
var iosNativeGoogleSignInLauncher: NativeGoogleSignInLauncher? = null
var iosNativeAppleSignInLauncher: NativeAppleSignInLauncher? = null

/**
 * Set from Swift's AppDelegate before doInitKoin is invoked.
 * Unlike [iosNativeGoogleSignInLauncher] and [iosNativeAppleSignInLauncher], which hard-error
 * if unset, the push subsystem degrades gracefully — [PushTokenProvider] returns null tokens
 * and [IosPushPermissionController] is a no-op — so no crash results from omitting this.
 */
var iosNativePushService: NativePushService? = null

/**
 * Set from Swift's AppDelegate before doInitKoin. StoreKit 2's Product.purchase /
 * AppStore.sync / Transaction APIs are Swift-only, so the purchase flow is bridged
 * through this — mirrors [iosNativeAppleSignInLauncher].
 */
var iosNativeStoreKitPurchaser: NativeStoreKitPurchaser? = null

actual val platformModule: Module = module {
    // Expose the Coil singleton ImageLoader and PlatformContext so ViewModels can
    // prefetch images (e.g. brand logo bytes for receipt rendering) without a Compose context.
    single<PlatformContext> { PlatformContext.INSTANCE }
    single { SingletonImageLoader.get(PlatformContext.INSTANCE) }
    single { OnboardingPreferences() } bind OnboardingPreferencesStore::class
    single { ReferralPreferences() } bind ReferralPreferencesStore::class
    single<InstallReferrerReader> { IosInstallReferrerReader() }
    single { MeasurementPreferences() } bind MeasurementPreferencesStore::class
    single { ThemePreferences() } bind ThemePreferencesStore::class
    single { OfflinePhotoStore() }
    single { TutorialVideoCache() }
    single<ImageCompressor> { IosImageCompressor() }
    single { OfflineUploadScheduler() }
    single { OrderReceiptSharer() }
    single { ImageSharer() }
    single<MeasurementSharer> { IosMeasurementSharer() }
    single { WhatsAppLauncher() }
    single { DialerLauncher() }
    single<PushPermissionController> { IosPushPermissionController() }
    single<SsoCredentialProvider> {
        val google = iosNativeGoogleSignInLauncher
            ?: error(
                "iosNativeGoogleSignInLauncher must be set from Swift before doInitKoin. " +
                    "Check iOSApp.swift's AppDelegate.didFinishLaunchingWithOptions."
            )
        val apple = iosNativeAppleSignInLauncher
            ?: error(
                "iosNativeAppleSignInLauncher must be set from Swift before doInitKoin. " +
                    "Check iOSApp.swift's AppDelegate.didFinishLaunchingWithOptions."
            )
        IosSsoCredentialProvider(googleLauncher = google, appleLauncher = apple)
    }
    // iOS sells subscriptions through Apple IAP (Guideline 3.1.1) — Android binds
    // the Paystack CloudFunctionsPaymentRepository instead (PlatformModule.android.kt).
    single<PaymentRepository> {
        val purchaser = iosNativeStoreKitPurchaser
            ?: error(
                "iosNativeStoreKitPurchaser must be set from Swift before doInitKoin. " +
                    "Check iOSApp.swift's AppDelegate.didFinishLaunchingWithOptions."
            )
        StoreKitPaymentRepository(
            purchaser = purchaser,
            functions = get(),
            authRepository = get(),
        )
    }
}
