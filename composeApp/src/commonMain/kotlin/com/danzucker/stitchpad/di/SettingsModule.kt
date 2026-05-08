package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.settings.data.FirestoreDeletionFeedbackRepository
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import com.danzucker.stitchpad.feature.settings.presentation.changeemail.ChangeEmailViewModel
import com.danzucker.stitchpad.feature.settings.presentation.changepassword.ChangePasswordViewModel
import com.danzucker.stitchpad.feature.settings.presentation.editprofile.EditProfileViewModel
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsDataModule = module {
    singleOf(::FirestoreDeletionFeedbackRepository) bind DeletionFeedbackRepository::class
}

val settingsPresentationModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::ChangeEmailViewModel)
    viewModelOf(::ChangePasswordViewModel)
    // Delete account ViewModel lands in the next commit:
    // viewModelOf(::DeleteAccountViewModel)
}
