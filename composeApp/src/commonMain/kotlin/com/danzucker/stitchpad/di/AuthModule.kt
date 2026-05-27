package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.auth.data.EmailPatternValidator
import com.danzucker.stitchpad.feature.auth.data.FirebaseAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginViewModel
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModel
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::EmailPatternValidator) bind PatternValidator::class
    singleOf(::FirebaseUserRepository) bind UserRepository::class
    // BrandLogoValidator must be bound so viewModelOf(::WorkshopSetupViewModel)
    // and viewModelOf(::EditProfileViewModel) can resolve the validator param —
    // Koin's reflection-based viewModelOf does not honour Kotlin default values.
    //
    // Use an explicit zero-arg factory rather than singleOf — the validator's
    // own `maxBytes: Int = MAX_BYTES` constructor param has the SAME problem
    // we're solving for the VMs: singleOf would try to resolve Int from the
    // graph, fail at runtime, and crash both logo-enabled screens.
    single { BrandLogoValidator() }
}

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::WorkshopSetupViewModel)
}
