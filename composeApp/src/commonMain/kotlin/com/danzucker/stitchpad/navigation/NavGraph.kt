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
import androidx.navigation.toRoute
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.SelfInitiatedLeaveSignal
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.forgotpassword.ForgotPasswordRoot
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginRoot
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpRoot
import com.danzucker.stitchpad.feature.auth.presentation.verifyemail.EmailVerificationRoot
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuRoot
import com.danzucker.stitchpad.feature.main.presentation.MainRoot
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
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
 * Demotion-only workshop-setup check. Deliberately bypasses [ResolveNeedsWorkshopSetup]'s
 * local per-user "completed" fast path and goes straight to the remote profile via
 * [UserRepository.hasWorkshopProfileOrNull]. That fast path is set by Workshop Setup's "Skip
 * for now" action WITHOUT creating the user's `users/{uid}` doc — a staffer who skipped setup
 * while still staff (staff never need a profile) then gets revoked would otherwise read as
 * "already done" here too and land on Home with no doc ever created. Because
 * `grantLaunchFreeOnSignup` only fires on that doc's CREATE, such a user would silently
 * default to FREE forever. Re-checking the remote profile for exactly this transition forces
 * a re-prompt so the doc gets seeded (and, as of the server-side revocation grant, the
 * launch Atelier entitlement lands even if the user never revisits Workshop Setup).
 *
 * The `OrNull` variant is load-bearing: [UserRepository.hasWorkshopProfile] swallows read
 * failures as `false`, which would send a real former owner into Workshop Setup on a flaky
 * network — where they could overwrite their own business details. A null (couldn't tell)
 * therefore routes Home, exactly like a confirmed profile ([demotionNeedsWorkshopSetup]).
 *
 * When the remote read CONFIRMS a profile we also heal the per-user cache flag, mirroring
 * [ResolveNeedsWorkshopSetup] — this path bypasses that resolver, so without the heal the
 * next launch pays for the same Firestore read again.
 */
private suspend fun needsWorkshopSetupForDemotedStaff(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    onboardingPreferences: OnboardingPreferencesStore,
): Boolean {
    val userId = authRepository.getCurrentUser()?.id ?: return false
    val remoteHasProfile = userRepository.hasWorkshopProfileOrNull(userId)
    if (remoteHasProfile == true) {
        onboardingPreferences.setConfirmedRemoteWorkshopProfile(userId)
    }
    return demotionNeedsWorkshopSetup(remoteHasProfile)
}

/**
 * The demotion routing decision, isolated from Firebase so all three cases are testable.
 * Only a CONFIRMED absence of a remote profile (`false`) sends the demoted staffer to
 * Workshop Setup. `true` (profile exists) and `null` (the read failed) both send them Home
 * — the documented fail-safe: a flaky network must never strand a real owner in setup.
 */
internal fun demotionNeedsWorkshopSetup(remoteHasProfile: Boolean?): Boolean =
    remoteHasProfile == false

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
 * Deep-link targets addressed to ONE specific tailor's account, dropped when the app is
 * signed out. Routing them after a fresh sign-in could show whoever logs in next the
 * previous user's inbox, order, or nudge — and a stale ORDER carries a prior user's
 * orderId, which would fail under the new account's permissions.
 *
 * UPGRADE and CLAIM_GIFT are deliberately absent: those survive login because the user
 * must sign in (or sign up) to act on them, which is exactly the gift flow for a brand-new
 * tailor. JOIN_WORKSHOP has its own handling below.
 */
private val ACCOUNT_SCOPED_DEEP_LINKS = setOf(
    DeepLinkTarget.INBOX,
    DeepLinkTarget.ORDER,
    DeepLinkTarget.TO_COLLECT,
    DeepLinkTarget.DASHBOARD,
    DeepLinkTarget.FOUNDING_TAILORS,
)

/**
 * Outer-nav handler for a pending push-tap deep link. The inbox route lives in MainRoot's
 * INNER nav, so if a tap arrives while the user is on a non-Home OUTER route (e.g. the
 * debug menu) MainRoot isn't composed to consume it — bring the app back to Home first
 * (MainRoot then routes to the inbox). Only when Home is ALREADY in the back stack (the
 * user has cleared the splash / email-verification / workshop-setup gates), so a tap can
 * never bypass those gates. When signed out, a push INBOX, ORDER, or TO_COLLECT link is
 * dropped so it can't auto-navigate the next session's (possibly different) user to the
 * prior user's inbox/order without a fresh tap — a stale ORDER carries a prior user's
 * orderId and could route a different account to an order that fails under their
 * permissions. An UPGRADE email-link target is preserved across login (the account owner
 * asked to renew and must sign in to do so) and consumed once Home is reached.
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
            if (pendingDeepLinkTarget in ACCOUNT_SCOPED_DEEP_LINKS) {
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
 * Kill-switch / revocation must bite mid-session, not on next restart (StitchPad
 * exploration, 2026-08-08): [resolvePostAuthDestination] above only resolves the
 * start route ONCE, via `.first { }`. This watches [ActiveWorkshopProvider.flow]
 * for a STAFF<->OWNER role flip AFTER that initial resolution and pops back to
 * [HomeRoute] so every screen re-renders under the freshly-resolved session —
 * every list/dashboard ViewModel already re-subscribes its own listeners on
 * `WorkshopSession.workshopUid` changes (see OrderListViewModel et al.); this is
 * the nav-level half of the same fix.
 *
 * Keyed on `authUid` (not `Unit`) rather than `role` directly: a fresh sign-in
 * must start its OWN `drop(1)` baseline, because the very first session value
 * for a new authUid (e.g. an approved staffer's STAFF/ACTIVE resolution) is not
 * itself a "change" worth re-routing for — resolvePostAuth already routes it
 * correctly the first time. Only a role flip AFTER that baseline — the same
 * authUid resolving differently — is a session-altering event. `role` alone
 * (not workshopUid/membershipStatus) is the right signal: the pending->active
 * approval transition keeps role == STAFF throughout (see
 * `WorkshopSessionResolver`), so it's left to StaffPendingRoot's own approval
 * watcher, and workshopUid-only changes are handled by the VMs directly.
 */
@Composable
private fun StaffRoleChangeRedirectEffect(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferencesStore,
) {
    val activeWorkshopProvider: ActiveWorkshopProvider = koinInject()
    val authRepository: AuthRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val selfInitiatedLeave: SelfInitiatedLeaveSignal = koinInject()
    val session by activeWorkshopProvider.flow.collectAsStateWithLifecycle()
    val authUid = session.authUid
    LaunchedEffect(authUid) {
        if (authUid.isBlank()) return@LaunchedEffect
        var previous: WorkshopSession? = null
        activeWorkshopProvider.flow.collect { current ->
            val prior = previous
            previous = current
            // "Leave this workshop" produces the same ACTIVE-staff -> owner-of-self
            // transition as a revocation, but it owns its own navigation (sign-out ->
            // Welcome). Redirecting here would pop HomeRoute and clear the
            // SettingsViewModel while its sign-out is still running.
            if (selfInitiatedLeave.isLeaving) return@collect
            if (prior != null && shouldRedirectHomeForStaffSessionChange(prior, current)) {
                val destination = staffDemotionDestination(
                    needsWorkshopSetup = needsWorkshopSetupForDemotedStaff(
                        authRepository,
                        userRepository,
                        onboardingPreferences,
                    ),
                )
                // Re-check the session before navigating: the resolve spans a network
                // call, during which the session may have changed (sign-out, or re-activation
                // as staff). Only navigate if the demotion is still current.
                if (demotionStillCurrent(current, activeWorkshopProvider.flow.value)) {
                    navController.navigate(destination) {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

/**
 * The global redirect owns only mid-session loss of an ACTIVE staff session
 * (revocation or the staff kill-switch). Pending onboarding owns its own
 * OWNER -> STAFF/PENDING -> STAFF/ACTIVE navigation, and auth screens own
 * sign-out. Redirecting those transitions races their one-shot events and can
 * strand the redeem screen in loading or send a signed-out user back to Home.
 */
internal fun shouldRedirectHomeForStaffSessionChange(
    previous: WorkshopSession,
    current: WorkshopSession,
): Boolean = previous.isActiveStaff &&
    current.isOwner &&
    current.authUid.isNotBlank() &&
    current.authUid == previous.authUid

/**
 * Where a just-demoted (revoked) staffer lands. A staff-only user has no
 * users/{uid} doc — Workshop Setup is the only path that seeds it, and the
 * server-side launch Atelier grant fires on that doc's creation. Sending a
 * profile-less demoted staffer straight Home would strand them on FREE/15
 * entitlement defaults until their next cold start.
 */
internal fun staffDemotionDestination(needsWorkshopSetup: Boolean): Any =
    if (needsWorkshopSetup) WorkshopSetupRoute else HomeRoute

/**
 * Whether the demotion we're about to navigate for is still current. The redirect
 * branch spans a suspend call (needsWorkshopSetupForCurrentUser → getCurrentUser +
 * Firestore get). During this window, the session may change: sign-out (authUid
 * becomes blank), or re-activation as staff (isOwner flips back to false). Both
 * scenarios make the redirect stale — we must not navigate based on a snapshot
 * from before the async resolve. Only navigate if the latest session still shows
 * the same authUid AND is still an owner (the demotion signature).
 */
internal fun demotionStillCurrent(
    acted: WorkshopSession,
    latest: WorkshopSession,
): Boolean = latest.authUid == acted.authUid && latest.isOwner

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
    val activeWorkshopProvider: ActiveWorkshopProvider = koinInject()
    val pendingDeepLink: PendingDeepLinkHolder = koinInject()
    val resolveNeedsWorkshopSetup: ResolveNeedsWorkshopSetup = koinInject()

    PushDeepLinkRedirectEffect(navController)
    ScreenViewTrackingEffect(navController)
    StaffRoleChangeRedirectEffect(navController, onboardingPreferences)

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
                },
                // Slice 7: "joining as staff" fork -> the invite-code redeem screen.
                onNavigateToJoinWorkshop = { navController.navigate(RedeemInviteRoute()) },
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
                onNavigateToJoinWorkshop = { navController.navigate(RedeemInviteRoute()) },
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
