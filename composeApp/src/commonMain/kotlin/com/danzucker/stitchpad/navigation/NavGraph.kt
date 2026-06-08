package com.danzucker.stitchpad.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordRoot
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginRoot
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpRoot
import com.danzucker.stitchpad.feature.auth.presentation.verifyemail.EmailVerificationRoot
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuRoot
import com.danzucker.stitchpad.feature.main.presentation.MainRoot
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.presentation.OnboardingRoot
import com.danzucker.stitchpad.feature.onboarding.presentation.SplashRoot
import com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupRoot
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Whether the signed-in user must verify their email before entering the app.
 * Only email/password users are gated; SSO providers supply pre-verified emails.
 * The bypass flag is honoured ONLY in debug builds — a persisted flag (e.g. from
 * a prior debug install or restored backup) must never let a release build skip
 * the gate. Store reviewers use a pre-verified account instead. Reloads from the
 * server first so a freshly tapped link is reflected.
 */
private suspend fun AuthRepository.needsEmailVerification(
    onboardingPreferences: OnboardingPreferences,
): Boolean {
    val bypassed = isDebugBuild && onboardingPreferences.hasBypassedEmailVerification()
    val gated = getSignInProvider() == SignInProvider.EMAIL_PASSWORD && !bypassed
    if (!gated) return false
    reloadUser()
    return !isEmailVerified()
}

@Composable
fun StitchPadNavHost(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences
) {
    val authRepository: AuthRepository = koinInject()

    // A push-tap inbox deep-link is consumed inside MainRoot's inner nav (the inbox
    // route). If the tap arrives while the user is on a non-Home OUTER route (e.g. the
    // debug menu), MainRoot isn't composed to consume it — so bring the app back to
    // Home first, then MainRoot's own effect routes to the inbox. Gated on Home being
    // ALREADY in the back stack (not merely isLoggedIn): the user must have cleared the
    // splash / email-verification / workshop-setup gates. Otherwise the pending link
    // waits until they reach Home naturally, so a tap (e.g. from a stale token during a
    // new account's setup) can never bypass those gates into MainRoot.
    val pendingDeepLink: PendingDeepLinkHolder = koinInject()
    val pendingDeepLinkTarget by pendingDeepLink.target.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDeepLinkTarget, currentEntry) {
        val onHome = currentEntry?.destination?.hasRoute<HomeRoute>() == true
        val homeInBackStack = navController.currentBackStack.value.any {
            it.destination.hasRoute<HomeRoute>()
        }
        if (pendingDeepLinkTarget != null && !onHome && homeInBackStack) {
            navController.navigate(HomeRoute) {
                launchSingleTop = true
                popUpTo(HomeRoute) { inclusive = false }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = SplashRoute
    ) {
        composable<SplashRoute> {
            val scope = rememberCoroutineScope()
            SplashRoot(
                onSplashFinished = {
                    scope.launch {
                        val hasSeenOnboarding = onboardingPreferences.hasSeenOnboarding()
                        val hasCompletedWorkshop = onboardingPreferences.hasCompletedWorkshopSetup()
                        val destination = when {
                            !hasSeenOnboarding -> OnboardingRoute
                            !authRepository.isLoggedIn -> LoginRoute
                            authRepository.needsEmailVerification(onboardingPreferences) ->
                                EmailVerificationRoute
                            !hasCompletedWorkshop -> WorkshopSetupRoute
                            else -> HomeRoute
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
            OnboardingRoot(
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
                        val destination = when {
                            authRepository.needsEmailVerification(onboardingPreferences) ->
                                EmailVerificationRoute
                            !onboardingPreferences.hasCompletedWorkshopSetup() -> WorkshopSetupRoute
                            else -> HomeRoute
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
                },
                onNavigateToEmailVerification = {
                    navController.navigate(EmailVerificationRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<EmailVerificationRoute> {
            val scope = rememberCoroutineScope()
            EmailVerificationRoot(
                onVerified = {
                    scope.launch {
                        val destination = if (!onboardingPreferences.hasCompletedWorkshopSetup()) {
                            WorkshopSetupRoute
                        } else {
                            HomeRoute
                        }
                        navController.navigate(destination) {
                            popUpTo(EmailVerificationRoute) { inclusive = true }
                        }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(EmailVerificationRoute) { inclusive = true }
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
            MainRoot(
                // Sign-out (and push-token revocation) is owned by SettingsViewModel /
                // DeleteAccountViewModel via SignOutUseCase. By the time this callback
                // fires, the session is already cleared — navigate only.
                onSignedOut = {
                    navController.navigate(LoginRoute) {
                        popUpTo(HomeRoute) { inclusive = true }
                    }
                },
                onNavigateToDebugMenu = { navController.navigate(DebugMenuRoute) },
            )
        }
        if (isDebugBuild) {
            composable<DebugMenuRoute> {
                DebugMenuRoot(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToLogin = {
                        navController.navigate(LoginRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                        }
                    },
                    onNavigateToSplash = {
                        navController.navigate(SplashRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
