package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsFreemiumRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.freemium.presentation.upgrade.UpgradeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val freemiumModule = module {
    single<FreemiumRepository> {
        CloudFunctionsFreemiumRepository(
            auth = get(),
            firestore = get(),
            functions = get(),
        )
    }
    viewModel { UpgradeViewModel(entitlements = get()) }
}
