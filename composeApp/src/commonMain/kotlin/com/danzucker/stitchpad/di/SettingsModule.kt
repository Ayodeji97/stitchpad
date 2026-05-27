package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.settings.data.FirestoreDeletionFeedbackRepository
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import com.danzucker.stitchpad.feature.settings.presentation.changeemail.ChangeEmailViewModel
import com.danzucker.stitchpad.feature.settings.presentation.changepassword.ChangePasswordViewModel
import com.danzucker.stitchpad.feature.settings.presentation.deleteaccount.DeleteAccountViewModel
import com.danzucker.stitchpad.feature.settings.presentation.editprofile.EditProfileViewModel
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsDataModule = module {
    // Explicit lambda binding: FirestoreDeletionFeedbackRepository's constructor
    // has a defaulted `nowEpochMs: () -> Long`, which singleOf() would otherwise
    // try (and fail) to resolve as a Koin binding. The default Clock.System
    // value is what we want at runtime, so just don't pass it through Koin.
    single<DeletionFeedbackRepository> { FirestoreDeletionFeedbackRepository(get()) }
}

val settingsPresentationModule = module {
    viewModelOf(::SettingsViewModel)
    // Explicit factory (not viewModelOf) because the VM takes a
    // `suspend (ByteArray) -> ByteArray?` compressor function with a default —
    // viewModelOf would try to resolve that lambda type from the graph. Letting
    // Kotlin's default kick in routes to BrandLogoCompressor (production) or
    // an identity lambda (tests pass it explicitly).
    viewModel { EditProfileViewModel(get(), get(), get(), get()) }
    viewModelOf(::ChangeEmailViewModel)
    viewModelOf(::ChangePasswordViewModel)
    viewModelOf(::DeleteAccountViewModel)
}
