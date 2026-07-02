package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.referral.data.CloudFunctionsReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralAttribution
import com.danzucker.stitchpad.feature.referral.domain.ReferralAttributionCoordinator
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val referralModule = module {
    // App-lifetime scope for the coordinator's fire-and-forget capture + submit, so
    // the attribution call survives the SignUp screen being torn down by post-signup
    // navigation. SupervisorJob so one failed submit doesn't kill the scope.
    single<CoroutineScope>(qualifier = named("referralAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<ReferralRepository> { CloudFunctionsReferralRepository(functions = get()) }
    single<ReferralAttribution> {
        // ReferralPreferencesStore + InstallReferrerReader are bound per platform
        // (platformModule); PendingDeepLinkHolder is the shared single from
        // notificationDataModule.
        ReferralAttributionCoordinator(
            referralRepository = get(),
            preferences = get(),
            installReferrerReader = get(),
            pendingDeepLink = get(),
            scope = get(qualifier = named("referralAppScope")),
        )
    }
}
