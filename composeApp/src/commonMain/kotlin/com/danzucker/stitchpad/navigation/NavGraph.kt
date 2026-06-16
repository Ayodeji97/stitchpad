package com.danzucker.stitchpad.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordRoot
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginRoot
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpRoot
import com.danzucker.stitchpad.feature.auth.presentation.verifyemail.EmailVerificationRoot
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuRoot
import com.danzucker.stitchpad.feature.main.presentation.MainRoot
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.domain.ResolveNeedsWorkshopSetup
import com.danzucker.stitchpad.feature.onboarding.presentation.OnboardingRoot
import com.danzucker.stitchpad.feature.onboarding.presentation.SplashRoot
import com.danzucker.stitchpad.feature.onboarding.presentation.welcome.WelcomeRoot
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

/**
 * Whether to route the signed-in user to workshop setup. Resolves the current user id
 * and delegates to [ResolveNeedsWorkshopSetup], which checks the local "completed" flag
 * first and falls back to the remote profile (the reinstall case). If the user id can't
 * be resolved we fall back to the local flag alone — never blocking entry incorrectly.
 */
private suspend fun needsWorkshopSetupForCurrentUser(
    authRepository: AuthRepository,
    onboardingPreferences: OnboardingPreferences,
    resolveNeedsWorkshopSetup: ResolveNeedsWorkshopSetup,
): Boolean {
    val userId = authRepository.getCurrentUser()?.id
        ?: return !onboardingPreferences.hasCompletedWorkshopSetup()
    return resolveNeedsWorkshopSetup(userId)
}

/**
 * Outer-nav handler for a pending push-tap deep link. The inbox route lives in MainRoot's
 * INNER nav, so if a tap arrives while the user is on a non-Home OUTER route (e.g. the
 * debug menu) MainRoot isn't composed to consume it — bring the app back to Home first
 * (MainRoot then routes to the inbox). Only when Home is ALREADY in the back stack (the
 * user has cleared the splash / email-verification / workshop-setup gates), so a tap can
 * never bypass those gates. When signed out, a push INBOX link is dropped so it can't
 * auto-navigate the next session's user to the inbox without a fresh tap; an UPGRADE
 * email-link target is preserved across login (the account owner asked to renew and
 * must sign in to do so) and consumed once Home is reached.
 */
@Composable
private fun PushDeepLinkRedirectEffect(navController: NavHostController) {
    val authRepository: AuthRepository = koinInject()
    val pendingDeepLink: PendingDeepLinkHolder = koinInject()
    val pendingDeepLinkTarget by pendingDeepLink.target.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDeepLinkTarget, currentEntry) {
        if (pendingDeepLinkTarget == null) return@LaunchedEffect
        if (!authRepository.isLoggedIn) {
            // A push INBOX tap shouldn't auto-route a freshly-signed-in (possibly
            // different) user, so drop it. But an UPGRADE email-link tap is the account
            // owner asking to renew — and they must sign in to upgrade anyway — so
            // preserve it across login. Once Home is reached, MainRoot consumes it.
            if (pendingDeepLinkTarget == DeepLinkTarget.INBOX) {
                pendingDeepLink.clear()
            }
            return@LaunchedEffect
        }
        val onHome = currentEntry?.destination?.hasRoute<HomeRoute>() == true
        val homeInBackStack = navController.currentBackStack.value.any {
            it.destination.hasRoute<HomeRoute>()
        }
        if (!onHome && homeInBackStack) {
            navController.navigate(HomeRoute) {
                launchSingleTop = true
                popUpTo(HomeRoute) { inclusive = false }
            }
        }
    }
}

@Composable
fun StitchPadNavHost(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences
) {
    val authRepository: AuthRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val resolveNeedsWorkshopSetup = remember(onboardingPreferences, userRepository) {
        ResolveNeedsWorkshopSetup(onboardingPreferences, userRepository)
    }

    PushDeepLinkRedirectEffect(navController)

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
                        val destination = when {
                            !hasSeenOnboarding -> OnboardingRoute
                            !authRepository.isLoggedIn -> WelcomeRoute
                            authRepository.needsEmailVerification(onboardingPreferences) ->
                                EmailVerificationRoute
                            needsWorkshopSetupForCurrentUser(
                                authRepository,
                                onboardingPreferences,
                                resolveNeedsWorkshopSetup,
                            ) -> WorkshopSetupRoute
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
                        navController.navigate(WelcomeRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable<WelcomeRoute> {
            WelcomeRoot(
                onSignIn = {
                    navController.navigate(LoginRoute) { launchSingleTop = true }
                },
                onSignUp = {
                    navController.navigate(SignUpRoute) { launchSingleTop = true }
                },
            )
        }
        composable<LoginRoute> {
            val scope = rememberCoroutineScope()
            LoginRoot(
                onNavigateToSignUp = {
                    navController.navigate(SignUpRoute) { launchSingleTop = true }
                },
                onNavigateToForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                onNavigateToHome = {
                    scope.launch {
                        val destination = when {
                            authRepository.needsEmailVerification(onboardingPreferences) ->
                                EmailVerificationRoute
                            needsWorkshopSetupForCurrentUser(
                                authRepository,
                                onboardingPreferences,
                                resolveNeedsWorkshopSetup,
                            ) -> WorkshopSetupRoute
                            else -> HomeRoute
                        }
                        navController.navigate(destination) {
                            // Welcome is the base of the logged-out stack — clear it on success.
                            popUpTo(WelcomeRoute) { inclusive = true }
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
                // "Log in" link: always land on Login above Welcome, whether the user
                // arrived via Welcome -> SignUp or Welcome -> Login -> SignUp.
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        launchSingleTop = true
                        popUpTo(WelcomeRoute) { inclusive = false }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(WorkshopSetupRoute) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                },
                onNavigateToEmailVerification = {
                    navController.navigate(EmailVerificationRoute) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<EmailVerificationRoute> {
            val scope = rememberCoroutineScope()
            EmailVerificationRoot(
                onVerified = {
                    scope.launch {
                        val destination = if (
                            needsWorkshopSetupForCurrentUser(
                                authRepository,
                                onboardingPreferences,
                                resolveNeedsWorkshopSetup,
                            )
                        ) {
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
                    // Sign-out / abandon path -> back to the logged-out video landing.
                    navController.navigate(WelcomeRoute) {
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
                    // Sign-out / abandon path -> back to the logged-out video landing.
                    navController.navigate(WelcomeRoute) {
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
                    navController.navigate(WelcomeRoute) {
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
                    // Route through Welcome (not straight to Login) so Welcome is the
                    // back-stack base for the auth screens here too — otherwise a
                    // successful login's popUpTo(WelcomeRoute) finds nothing and leaves
                    // the logged-out Login screen reachable via Back while signed in.
                    onNavigateToLogin = {
                        navController.navigate(WelcomeRoute) {
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
