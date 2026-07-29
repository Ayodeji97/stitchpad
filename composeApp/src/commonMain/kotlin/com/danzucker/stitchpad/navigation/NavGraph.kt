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
import androidx.navigation.toRoute
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
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
import com.danzucker.stitchpad.feature.staff.presentation.pending.StaffPendingRoot
import com.danzucker.stitchpad.feature.staff.presentation.redeem.RedeemInviteRoot
import kotlinx.coroutines.flow.first
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
 * Whether to route the signed-in user to workshop setup. Resolves the current user id and
 * delegates to [ResolveNeedsWorkshopSetup], which checks the per-user "completed" flag first
 * and falls back to the remote profile (the reinstall case). If the user id can't be
 * resolved (no signed-in user) we return false — there's no per-user flag to check and a
 * logged-out user is routed elsewhere; never force setup incorrectly.
 */
private suspend fun needsWorkshopSetupForCurrentUser(
    authRepository: AuthRepository,
    resolveNeedsWorkshopSetup: ResolveNeedsWorkshopSetup,
): Boolean {
    val userId = authRepository.getCurrentUser()?.id ?: return false
    return resolveNeedsWorkshopSetup(userId)
}

/**
 * The single post-authentication destination ladder, shared by the Splash, Login
 * and Email-verification gates so the staff branches can't drift between them.
 * Precedence:
 *  1. Email verification (applies to everyone).
 *  2. Approved staff  → Home (they skip workshop setup — the tree isn't theirs).
 *  3. Pending staff   → the waiting screen.
 *  4. A JOIN_WORKSHOP deep link → the redeem screen (code prefilled).
 *  5. Owner needing workshop setup → setup; otherwise Home.
 * Returns a route object for [NavHostController.navigate].
 */
private suspend fun resolvePostAuthDestination(
    authRepository: AuthRepository,
    onboardingPreferences: OnboardingPreferences,
    resolveNeedsWorkshopSetup: ResolveNeedsWorkshopSetup,
    activeWorkshopProvider: ActiveWorkshopProvider,
    pendingDeepLink: PendingDeepLinkHolder,
): Any {
    if (authRepository.needsEmailVerification(onboardingPreferences)) return EmailVerificationRoute

    // Wait for a session that belongs to the just-signed-in user. awaitHydrated()
    // alone can return a stale signed-out/owner-of-self session right after login
    // (the provider stays "hydrated" across auth changes), which would misroute an
    // approved/pending staffer into owner setup. Matching on authUid closes that race.
    val authUid = authRepository.getCurrentUser()?.id
    val session = if (authUid == null) {
        activeWorkshopProvider.awaitHydrated()
    } else {
        activeWorkshopProvider.flow.first { it.authUid == authUid }
    }
    val staffPending = session.role == StaffRole.STAFF && session.membershipStatus == MembershipStatus.PENDING

    // An already-staff (active or pending) user's pending invite link is stale —
    // they're already in a workshop. Drop the target AND code so it can't linger
    // and bounce them from Home back to redeem via PushDeepLinkRedirectEffect.
    if ((session.isActiveStaff || staffPending) &&
        pendingDeepLink.target.value == DeepLinkTarget.JOIN_WORKSHOP
    ) {
        pendingDeepLink.clear()
        pendingDeepLink.consumeJoinWorkshopCode()
    }

    return when {
        session.isActiveStaff -> HomeRoute
        staffPending -> StaffPendingRoute()
        pendingDeepLink.target.value == DeepLinkTarget.JOIN_WORKSHOP -> {
            // Clear the target (the code survives for RedeemInviteViewModel to consume)
            // so it can't linger and bounce the user back to redeem via
            // PushDeepLinkRedirectEffect once they later reach Home.
            pendingDeepLink.clear()
            RedeemInviteRoute()
        }
        needsWorkshopSetupForCurrentUser(authRepository, resolveNeedsWorkshopSetup) -> WorkshopSetupRoute
        else -> HomeRoute
    }
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
    val activeWorkshopProvider: ActiveWorkshopProvider = koinInject()
    val pendingDeepLinkTarget by pendingDeepLink.target.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDeepLinkTarget, currentEntry) {
        if (pendingDeepLinkTarget == null) return@LaunchedEffect
        if (!authRepository.isLoggedIn) {
            // A push INBOX tap shouldn't auto-route a freshly-signed-in (possibly
            // different) user, so drop it. But an UPGRADE renewal link or a CLAIM_GIFT
            // link is preserved across login: the user must sign in (or sign up) to
            // upgrade / claim, which is exactly the gift flow for a brand-new tailor.
            // Once Home is reached, MainRoot consumes it.
            if (pendingDeepLinkTarget == DeepLinkTarget.INBOX) {
                pendingDeepLink.clear()
            }
            return@LaunchedEffect
        }
        // A staff invite normally routes via the post-auth gate during the
        // Login/Splash/Verify transition (landing directly on RedeemInviteRoute).
        // This is the backstop for an already-signed-in user who taps an invite
        // while settled past the auth gates (e.g. on WorkshopSetup, or with Home
        // in the back stack) — the gate won't re-run for them. We only act past
        // the auth gates so an unverified user is never routed around email
        // verification.
        if (pendingDeepLinkTarget == DeepLinkTarget.JOIN_WORKSHOP) {
            // An already-staff user (active or pending) can't join another workshop —
            // drop the stale invite instead of routing them to redeem (where it would
            // create a second pending request). Mirrors the post-auth gate's guard.
            if (activeWorkshopProvider.current().role == StaffRole.STAFF) {
                pendingDeepLink.clear()
                pendingDeepLink.consumeJoinWorkshopCode()
                return@LaunchedEffect
            }
            val onRedeem = currentEntry?.destination?.hasRoute<RedeemInviteRoute>() == true
            val pastAuthGate = currentEntry?.destination?.hasRoute<WorkshopSetupRoute>() == true ||
                navController.currentBackStack.value.any { it.destination.hasRoute<HomeRoute>() }
            if (onRedeem || pastAuthGate) {
                pendingDeepLink.clear()
                // Replace any existing redeem entry so a FRESH RedeemInviteViewModel
                // consumes the newly-tapped code (the VM reads the holder only on
                // init) — otherwise a tap while already on the blank redeem screen
                // would be dropped. Mirrors the RedeemGift deep-link handling.
                navController.navigate(RedeemInviteRoute()) {
                    launchSingleTop = true
                    popUpTo<RedeemInviteRoute> { inclusive = true }
                }
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

/**
 * Logs a screen_view for every destination the user lands on. One hook covers every
 * route — no per-screen code. Keys on the route string so re-landing the same screen
 * (e.g. tab reselects to the same destination) does not spam duplicates.
 *
 * Attach to EVERY NavController that owns destinations: the root host below AND
 * MainScreen's inner tab host — the inner controller is a separate back stack the
 * root observer never sees (pre-1.2 the app was analytically blind past "Main").
 */
@Composable
internal fun ScreenViewTrackingEffect(navController: NavHostController) {
    val analytics: Analytics = koinInject()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    LaunchedEffect(route) {
        if (route != null) analytics.logScreenView(screenNameFor(route))
    }
}

@Composable
fun StitchPadNavHost(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences
) {
    val authRepository: AuthRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val activeWorkshopProvider: ActiveWorkshopProvider = koinInject()
    val pendingDeepLink: PendingDeepLinkHolder = koinInject()
    val resolveNeedsWorkshopSetup = remember(onboardingPreferences, userRepository) {
        ResolveNeedsWorkshopSetup(onboardingPreferences, userRepository)
    }

    PushDeepLinkRedirectEffect(navController)
    ScreenViewTrackingEffect(navController)

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
                            else -> resolvePostAuthDestination(
                                authRepository,
                                onboardingPreferences,
                                resolveNeedsWorkshopSetup,
                                activeWorkshopProvider,
                                pendingDeepLink,
                            )
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
                        val destination = resolvePostAuthDestination(
                            authRepository,
                            onboardingPreferences,
                            resolveNeedsWorkshopSetup,
                            activeWorkshopProvider,
                            pendingDeepLink,
                        )
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
            val scope = rememberCoroutineScope()
            SignUpRoot(
                // "Log in" link: always land on Login above Welcome, whether the user
                // arrived via Welcome -> SignUp or Welcome -> Login -> SignUp.
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        launchSingleTop = true
                        popUpTo(WelcomeRoute) { inclusive = false }
                    }
                },
                // SSO success (Google/Apple — no email-verification step). Route
                // through the shared resolver so an invite recipient lands on the
                // redeem screen instead of owner workshop setup.
                onNavigateToHome = {
                    scope.launch {
                        val destination = resolvePostAuthDestination(
                            authRepository,
                            onboardingPreferences,
                            resolveNeedsWorkshopSetup,
                            activeWorkshopProvider,
                            pendingDeepLink,
                        )
                        navController.navigate(destination) {
                            popUpTo(WelcomeRoute) { inclusive = true }
                        }
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
                        val destination = resolvePostAuthDestination(
                            authRepository,
                            onboardingPreferences,
                            resolveNeedsWorkshopSetup,
                            activeWorkshopProvider,
                            pendingDeepLink,
                        )
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
        composable<RedeemInviteRoute> { entry ->
            RedeemInviteRoot(
                onNavigateToPending = { workshopName ->
                    navController.navigate(StaffPendingRoute(workshopName, fromRedeem = true)) {
                        // Clear the WHOLE back stack (including any Home underneath from an
                        // in-app invite tap) so a pending staffer can't system-back out of
                        // the waiting screen into the app while still STAFF/PENDING.
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.navigateUp() },
                onSignedOut = {
                    navController.navigate(WelcomeRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                declined = entry.toRoute<RedeemInviteRoute>().declined,
            )
        }
        composable<StaffPendingRoute> { entry ->
            val route = entry.toRoute<StaffPendingRoute>()
            StaffPendingRoot(
                workshopName = route.workshopName,
                fromRedeem = route.fromRedeem,
                onNavigateToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo<StaffPendingRoute> { inclusive = true }
                    }
                },
                onNavigateToRedeem = { declined ->
                    navController.navigate(RedeemInviteRoute(declined)) {
                        popUpTo<StaffPendingRoute> { inclusive = true }
                    }
                },
                onSignedOut = {
                    navController.navigate(WelcomeRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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
