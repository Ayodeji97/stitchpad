package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.auth.data.EmailPatternValidator
import com.danzucker.stitchpad.feature.auth.data.FirebaseAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginViewModel
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::EmailPatternValidator) bind PatternValidator::class
}

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignUpViewModel)
}
