package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.collection.presentation.ToCollectViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val toCollectPresentationModule = module {
    // Lambda factory (not viewModelOf) because ToCollectViewModel takes a
    // default-value nowMillis parameter — viewModelOf can't skip defaults.
    viewModel { ToCollectViewModel(get(), get(), get()) }
}
