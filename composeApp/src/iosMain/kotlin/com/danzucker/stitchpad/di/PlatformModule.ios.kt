package com.danzucker.stitchpad.di

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.danzucker.stitchpad.core.data.preferences.ThemePreferences
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadScheduler
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.IosSsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.data.NativeAppleSignInLauncher
import com.danzucker.stitchpad.feature.auth.data.NativeGoogleSignInLauncher
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.notification.push.IosPushPermissionController
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
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

actual val platformModule: Module = module {
    // Expose the Coil singleton ImageLoader and PlatformContext so ViewModels can
    // prefetch images (e.g. brand logo bytes for receipt rendering) without a Compose context.
    single<PlatformContext> { PlatformContext.INSTANCE }
    single { SingletonImageLoader.get(PlatformContext.INSTANCE) }
    single { OnboardingPreferences() } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences() } bind MeasurementPreferencesStore::class
    single { ThemePreferences() } bind ThemePreferencesStore::class
    single { OfflinePhotoStore() }
    single { OfflineUploadScheduler() }
    single { OrderReceiptSharer() }
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
}
