package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.config.data.FirebaseAppConfigRepository
import com.danzucker.stitchpad.core.config.data.FirebaseCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.config.presentation.AppGateViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val configDataModule = module {
    singleOf(::FirebaseAppConfigRepository) bind AppConfigRepository::class
    // Lambda form (not singleOf): FirebaseCommunityJoinTracker has a `nowMillis: () -> Long`
    // default param. singleOf's constructor reflection ignores Kotlin defaults and tries to
    // resolve the Function0 from Koin → NoDefinitionFoundException crash at graph creation.
    single<CommunityJoinTracker> { FirebaseCommunityJoinTracker(get(), get()) }
    singleOf(::CommunityBannerDismissal)
}

val configPresentationModule = module {
    // Lambda factory (not viewModelOf): AppGateViewModel has default-value params
    // (isIos, currentBuild) sourced from platform globals — viewModelOf resolves every
    // constructor param through Koin and can't skip defaults.
    viewModel { AppGateViewModel(appConfigRepository = get()) }
}
