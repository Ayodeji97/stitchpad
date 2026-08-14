package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.feature.foundingtailors.presentation.FoundingTailorsViewModel
import com.danzucker.stitchpad.feature.referral.data.CloudFunctionsReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralAttribution
import com.danzucker.stitchpad.feature.referral.domain.ReferralAttributionCoordinator
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.presentation.entry.ReferralCodeViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val referralModule = module {
    // App-lifetime scope for the coordinator's auth-state collector + fire-and-forget
    // submits, so attribution survives the SignUp screen being torn down by post-signup
    // navigation. SupervisorJob so one failed submit doesn't kill the scope.
    single<CoroutineScope>(qualifier = named("referralAppScope")) {
        appLifetimeScope(tag = "referralAppScope")
    }
    single<ReferralRepository> { CloudFunctionsReferralRepository(functions = get()) }
    single<ReferralAttribution> {
        // ReferralPreferencesStore + InstallReferrerReader are bound per platform
        // (platformModule); PendingDeepLinkHolder is the shared single from
        // notificationDataModule. Bridge GitLive auth into a testable uid flow so the
        // coordinator retries a failed submit on the next authenticated launch.
        val auth: FirebaseAuth = get()
        ReferralAttributionCoordinator(
            referralRepository = get(),
            preferences = get(),
            installReferrerReader = get(),
            clipboardReferralReader = get(),
            pendingDeepLink = get(),
            scope = get(qualifier = named("referralAppScope")),
            uidFlow = auth.authStateChanged.map { it?.uid },
            analytics = get(),
        ).also { it.start() }
    }
    viewModelOf(::ReferralCodeViewModel)
    // Explicit `viewModel { ... }` factory rather than viewModelOf(::FoundingTailorsViewModel)
    // because the VM takes a defaulted shareMessageResolver param — viewModelOf can't skip
    // defaulted params (see feedback_koin_constructor_ref_defaults memory).
    viewModel {
        FoundingTailorsViewModel(
            referralRepository = get(),
            userRepository = get(),
            authRepository = get(),
        )
    }
}
