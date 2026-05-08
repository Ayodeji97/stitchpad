package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.settings.data.FirestoreDeletionFeedbackRepository
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsDataModule = module {
    singleOf(::FirestoreDeletionFeedbackRepository) bind DeletionFeedbackRepository::class
}

val settingsPresentationModule = module {
    // ViewModels added as Settings screens land:
    // viewModelOf(::SettingsViewModel)
    // viewModelOf(::EditProfileViewModel)
    // viewModelOf(::ChangeEmailViewModel)
    // viewModelOf(::ChangePasswordViewModel)
    // viewModelOf(::DeleteAccountViewModel)
}
