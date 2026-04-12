package com.danzucker.stitchpad.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordRoot
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginRoot
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpRoot
import com.danzucker.stitchpad.feature.main.presentation.MainRoot
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.presentation.OnboardingScreen
import com.danzucker.stitchpad.feature.onboarding.presentation.SplashScreen
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupRoot
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StitchPadNavHost(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences
) {
    val authRepository: AuthRepository = koinInject()

    NavHost(
        navController = navController,
        startDestination = SplashRoute
    ) {
        composable<SplashRoute> {
            val scope = rememberCoroutineScope()
            SplashScreen(
                onSplashFinished = {
                    scope.launch {
                        val hasSeenOnboarding = onboardingPreferences.hasSeenOnboarding()
                        val hasCompletedWorkshop = onboardingPreferences.hasCompletedWorkshopSetup()
                        val destination = when {
                            !hasSeenOnboarding -> OnboardingRoute
                            authRepository.isLoggedIn && !hasCompletedWorkshop -> WorkshopSetupRoute
                            authRepository.isLoggedIn -> HomeRoute
                            else -> LoginRoute
                        }
                        navController.navigate(destination) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable<OnboardingRoute> {
            val scope = rememberCoroutineScope()
            OnboardingScreen(
                onFinished = {
                    scope.launch {
                        onboardingPreferences.setOnboardingSeen()
                        navController.navigate(LoginRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable<LoginRoute> {
            val scope = rememberCoroutineScope()
            LoginRoot(
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
                onNavigateToForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                onNavigateToHome = {
                    scope.launch {
                        val destination = if (!onboardingPreferences.hasCompletedWorkshopSetup()) {
                            WorkshopSetupRoute
                        } else {
                            HomeRoute
                        }
                        navController.navigate(destination) {
                            popUpTo(LoginRoute) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordRoot(
                onNavigateToLogin = { navController.navigateUp() }
            )
        }
        composable<SignUpRoute> {
            SignUpRoot(
                onNavigateToLogin = { navController.navigateUp() },
                onNavigateToHome = {
                    navController.navigate(WorkshopSetupRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<WorkshopSetupRoute> {
            WorkshopSetupRoot(
                onNavigateToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo(WorkshopSetupRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo<WorkshopSetupRoute> { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> {
            val scope = rememberCoroutineScope()
            MainRoot(
                onSignedOut = {
                    scope.launch {
                        authRepository.signOut()
                        navController.navigate(LoginRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
