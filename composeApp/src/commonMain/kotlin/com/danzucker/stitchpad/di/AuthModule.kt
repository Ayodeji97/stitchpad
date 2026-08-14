package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.auth.data.AuthSessionValidator
import com.danzucker.stitchpad.feature.auth.data.EmailPatternValidator
import com.danzucker.stitchpad.feature.auth.data.FirebaseAuthRepository
import com.danzucker.stitchpad.feature.auth.data.GitLivePasswordResetEmailSender
import com.danzucker.stitchpad.feature.auth.data.GitLiveVerificationEmailSender
import com.danzucker.stitchpad.feature.auth.data.PasswordResetEmailSender
import com.danzucker.stitchpad.feature.auth.data.VerificationEmailSender
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginViewModel
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpViewModel
import com.danzucker.stitchpad.feature.auth.presentation.verifyemail.EmailVerificationViewModel
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.main.presentation.SyncStatusViewModel
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::SignOutUseCase)
    // FirebaseFunctions is provided by smartDataModule (Firebase.functions("europe-west1")).
    singleOf(::GitLiveVerificationEmailSender) bind VerificationEmailSender::class
    singleOf(::GitLivePasswordResetEmailSender) bind PasswordResetEmailSender::class
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

    // App-lifetime scope for the zombie-session watchdog below.
    single<CoroutineScope>(qualifier = named("authSessionAppScope")) {
        appLifetimeScope(tag = "authSessionAppScope")
    }
    // createdAtStart: the whole point is catching a dead cached session at app
    // launch, before any screen tries to use it (2026-08-14 redeem-freeze
    // incident — INVALID_REFRESH_TOKEN with a signed-in user object).
    single(createdAtStart = true) {
        val authRepository = get<AuthRepository>()
        val signOutUseCase = get<SignOutUseCase>()
        AuthSessionValidator(
            userIdSource = get<FirebaseAuth>().authStateChanged.map { it?.uid },
            validateSession = { authRepository.forceRefreshIdToken() },
            currentUserId = { authRepository.getCurrentUser()?.id },
            signOut = { signOutUseCase() },
            scope = get(qualifier = named("authSessionAppScope")),
        ).also { it.start() }
    }
}

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::EmailVerificationViewModel)
    // Explicit factory (not viewModelOf) because the VM takes a
    // `suspend (ByteArray) -> ByteArray?` compressor function with a default —
    // Koin's reflection-based viewModelOf would try to resolve that lambda type
    // from the graph and fail. Omitting it from the call list lets Kotlin's
    // default kick in (see WorkshopSetupViewModel.compressLogo).
    viewModel { WorkshopSetupViewModel(get(), get(), get(), get(), analytics = get(), celebrations = get()) }
    viewModelOf(::SyncStatusViewModel)
}
