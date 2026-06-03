package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageDocSource
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageSnapshot
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_invite_share_message
import stitchpad.composeapp.generated.resources.settings_support_intro_message

private const val PRIVACY_URL = "https://getstitchpad.com/privacy"
private const val TERMS_URL = "https://getstitchpad.com/terms"
private const val SUPPORT_WHATSAPP_NUMBER = "+2348064816696"

private const val TAG = "SettingsVM"

/**
 * Slice of state that the user (or one-shot reads) drives directly. Lives in a
 * MutableStateFlow that's part of the cold combine, so toggles + dialog-flips
 * still propagate to the screen but the upstream Firestore listeners genuinely
 * cancel when no one's collecting.
 */
private data class LocalUiState(
    val measurementUnit: MeasurementUnit = MeasurementUnit.INCHES,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val showSignOutDialog: Boolean = false,
    val isSigningOut: Boolean = false,
    val dailyDigestEnabledOverride: Boolean? = null,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val entitlementsProvider: EntitlementsProvider,
    private val customerRepository: CustomerRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val themePreferencesStore: ThemePreferencesStore,
    private val smartUsageStore: SmartUsageStore,
    private val smartUsageDocSource: SmartUsageDocSource,
) : ViewModel() {

    private val uiState = MutableStateFlow(LocalUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * The state flow IS the cold upstream chain (Firestore listeners + UI state)
     * passed through stateIn. WhileSubscribed(5_000L) actually triggers
     * cancellation of the listeners when the screen is gone for >5s and restarts
     * them on return — the previous topology launched the listeners as a
     * sibling coroutine on viewModelScope, which kept them running for the VM's
     * full lifetime.
     */
    val state = settingsStateFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsState(),
    )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnProfileClick -> emit(SettingsEvent.NavigateToEditProfile)
            SettingsAction.OnMeasurementUnitClick -> toggleMeasurementUnit()
            SettingsAction.OnAppearanceClick -> cycleTheme()
            SettingsAction.OnEmailRowClick -> emit(SettingsEvent.NavigateToChangeEmail)
            SettingsAction.OnChangePasswordClick -> emit(SettingsEvent.NavigateToChangePassword)
            SettingsAction.OnSignOutRowClick -> uiState.update { it.copy(showSignOutDialog = true) }
            SettingsAction.OnSignOutDismiss -> uiState.update { it.copy(showSignOutDialog = false) }
            SettingsAction.OnSignOutConfirm -> signOut()
            SettingsAction.OnPrivacyClick -> emit(SettingsEvent.OpenUrl(PRIVACY_URL))
            SettingsAction.OnTermsClick -> emit(SettingsEvent.OpenUrl(TERMS_URL))
            SettingsAction.OnDeleteAccountClick -> emit(SettingsEvent.NavigateToDeleteAccount)
            SettingsAction.OnInviteClick -> {
                // Empty phone → WhatsApp opens its universal share picker so the
                // user can pick any chat, instead of a direct conversation.
                emit(SettingsEvent.OpenWhatsApp("", Res.string.settings_invite_share_message))
            }
            SettingsAction.OnContactClick -> {
                emit(
                    SettingsEvent.OpenWhatsApp(
                        SUPPORT_WHATSAPP_NUMBER,
                        Res.string.settings_support_intro_message,
                    )
                )
            }
            SettingsAction.OnDebugMenuClick -> emit(SettingsEvent.NavigateToDebugMenu)
            SettingsAction.OnUpgradeClick -> emit(SettingsEvent.NavigateToUpgrade)
            SettingsAction.OnFoundersNoteClick -> emit(SettingsEvent.NavigateToFoundersNote)
            is SettingsAction.OnDailyDigestToggle -> setDailyDigest(action.enabled)
        }
    }

    private fun settingsStateFlow(): Flow<SettingsState> = flow {
        val authUser = authRepository.getCurrentUser()
        if (authUser == null) {
            emit(SettingsState(isLoading = false))
            return@flow
        }
        val provider = authRepository.getSignInProvider()
        // Seed the persisted measurement unit and theme once; toggles update uiState below.
        uiState.update {
            it.copy(
                measurementUnit = measurementPreferencesStore.getUnit(),
                themePreference = themePreferencesStore.getTheme(),
            )
        }

        val customerCountFlow = customerRepository.observeCustomers(authUser.id)
            .map { result ->
                when (result) {
                    is Result.Success -> result.data.count { it.slotState == CustomerSlotState.ACTIVE }
                    is Result.Error -> 0
                }
            }

        // Pair the user-doc and usage-doc streams up front so the outer combine
        // stays at 5 args (kotlinx-coroutines has no 6-arg overload). The usage
        // snapshot's bonusBalance drives the First Month chip and monthlyCount
        // drives the post-First-Month chip; both null = doc absent or fields
        // missing, in which case the consumer falls through to other sources.
        val userWithUsageFlow = combine(
            userRepository.observeUser(authUser.id),
            smartUsageDocSource.observeSnapshot(authUser.id),
        ) { firestoreUser, usage -> firestoreUser to usage }

        val combined = combine(
            userWithUsageFlow,
            entitlementsProvider.flow,
            customerCountFlow,
            smartUsageStore.remainingFreeQuota,
            uiState,
        ) { userBundle, entitlements, customerCount, remainingAi, ui ->
            val (firestoreUser, usage) = userBundle
            buildState(
                authUser = authUser,
                provider = provider,
                firestoreUser = firestoreUser,
                usageSnapshot = usage,
                entitlements = entitlements,
                customerCount = customerCount,
                remainingAiQuota = remainingAi,
                ui = ui,
            )
        }
        combined.collect { emit(it) }
    }

    @Suppress("LongParameterList")
    private fun buildState(
        authUser: com.danzucker.stitchpad.core.domain.model.User,
        provider: SignInProvider,
        firestoreUser: com.danzucker.stitchpad.core.domain.model.User?,
        usageSnapshot: SmartUsageSnapshot,
        entitlements: UserEntitlements,
        customerCount: Int,
        remainingAiQuota: Int?,
        ui: LocalUiState,
    ): SettingsState {
        val business = firestoreUser?.businessName.orEmpty().ifBlank {
            authUser.displayName.ifBlank { authUser.email.substringBefore('@') }
        }
        val aiDisplay = computeAiDisplay(
            tier = entitlements.tier,
            isInWelcomeWindow = entitlements.isInWelcomeWindow,
            smartCoinAllowance = entitlements.smartCoinAllowance,
            bonusCoinsRemaining = firestoreUser?.bonusCoins,
            usageBonusBalance = usageSnapshot.bonusBalance,
            usageMonthlyCount = usageSnapshot.monthlyCount,
            remainingMonthlyQuota = remainingAiQuota,
        )
        return SettingsState(
            isLoading = false,
            businessName = business,
            businessLogoUrl = firestoreUser?.businessLogoUrl,
            email = authUser.email,
            whatsappNumber = firestoreUser?.whatsappNumber,
            avatarColorIndex = firestoreUser?.avatarColorIndex ?: authUser.avatarColorIndex,
            signInProvider = provider,
            maskedSignInIdentifier = authUser.email,
            subscriptionTier = entitlements.tier,
            customerCount = customerCount,
            customerLimit = if (entitlements.customerCap == Int.MAX_VALUE) null else entitlements.customerCap,
            aiDraftsUsed = aiDisplay.used,
            aiDraftLimit = aiDisplay.limit,
            isFirstMonth = entitlements.isInWelcomeWindow,
            welcomeDaysLeft = entitlements.welcomeDaysLeft,
            measurementUnit = ui.measurementUnit,
            themePreference = ui.themePreference,
            dailyDigestEmailEnabled = ui.dailyDigestEnabledOverride
                ?: firestoreUser?.dailyDigestEmailEnabled ?: true,
            showSignOutDialog = ui.showSignOutDialog,
            isSigningOut = ui.isSigningOut,
        )
    }

    private fun toggleMeasurementUnit() {
        viewModelScope.launch {
            // Compute `next` inside the atomic `update` so rapid double-taps
            // advance two steps instead of landing on the same value (snapshot
            // read + update is not atomic end-to-end).
            var nextUnit: MeasurementUnit = MeasurementUnit.INCHES
            uiState.update { current ->
                nextUnit = if (current.measurementUnit == MeasurementUnit.INCHES) {
                    MeasurementUnit.CM
                } else {
                    MeasurementUnit.INCHES
                }
                current.copy(measurementUnit = nextUnit)
            }
            measurementPreferencesStore.setUnit(nextUnit)
        }
    }

    private fun cycleTheme() {
        viewModelScope.launch {
            // Cycle System → Light → Dark → System, mirroring how
            // toggleMeasurementUnit cycles Inches ↔ Cm. Compute `next` inside
            // the atomic `update` so rapid taps advance through every state
            // instead of collapsing onto a single transition.
            var nextTheme: ThemePreference = ThemePreference.SYSTEM
            uiState.update { current ->
                nextTheme = when (current.themePreference) {
                    ThemePreference.SYSTEM -> ThemePreference.LIGHT
                    ThemePreference.LIGHT -> ThemePreference.DARK
                    ThemePreference.DARK -> ThemePreference.SYSTEM
                }
                current.copy(themePreference = nextTheme)
            }
            themePreferencesStore.setTheme(nextTheme)
        }
    }

    private fun setDailyDigest(enabled: Boolean) {
        // Optimistic override (flips the switch immediately) + fire-and-forget
        // persist, mirroring toggleMeasurementUnit/cycleTheme. The user id is
        // resolved live from the auth repo inside the coroutine — not from a
        // captured field — so a toggle tapped before the cold state flow has run
        // still updates the UI and persists. getCurrentUser() is null only when
        // signed out (impossible on Settings); guarded so we never write users/"".
        uiState.update { it.copy(dailyDigestEnabledOverride = enabled) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            userRepository.setDailyDigestEmailEnabled(userId, enabled)
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            uiState.update { it.copy(isSigningOut = true, showSignOutDialog = false) }
            when (val result = authRepository.signOut()) {
                is Result.Success -> emit(SettingsEvent.NavigateToLoginAfterSignOut)
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "signOut failed error=${result.error}" }
                    uiState.update { it.copy(isSigningOut = false) }
                    emit(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun emit(event: SettingsEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

/**
 * What the PlanCard renders for AI usage: a tier-derived limit + the count consumed
 * against it. Atelier maps to (null, 0) — null limit means "unlimited" to PlanCard
 * which short-circuits to PlanCardPaid.
 */
internal data class AiDisplay(val limit: Int?, val used: Int)

/**
 * Pure mapping from entitlements + raw quota mirrors → what to show on PlanCard.
 *
 * Three branches by spec decision #6:
 * - **Atelier** → (null, 0): unlimited, no fraction shown
 * - **First Month** → (30, 30 - effective bonus remaining)
 * - **Post-First-Month (Free / Pro)** → (smartCoinAllowance, effective monthly used)
 *
 * **First Month bonus precedence:** `usageBonusBalance` from the server-decremented
 * `users/{uid}/usage/smart_drafts.bonusBalance` is the truth and wins whenever it's
 * non-null. Falls back to user-doc `bonusCoinsRemaining` (the signup-time seed) when
 * the usage doc doesn't exist yet — i.e. before the first Smart call lifts the bonus
 * onto the server's gating doc. If both are null (pre-V1.0 account), defaults to a
 * full balance so first-time render shows "0 of 30 used", not "30 of 30 used".
 *
 * **Post-First-Month count precedence:** `usageMonthlyCount` from the usage doc's
 * `count` field is the server truth and wins when non-null. Falls back to the
 * in-process `SmartUsageStore.remainingFreeQuota` cache (`smartCoinAllowance - cache`)
 * for sessions where the usage doc hasn't materialized yet, then to 0 when neither
 * source is hydrated. Clamped at `smartCoinAllowance` to defend against the rare
 * post-tier-downgrade case where doc count > new allowance.
 */
@Suppress("LongParameterList")
internal fun computeAiDisplay(
    tier: com.danzucker.stitchpad.core.domain.model.SubscriptionTier,
    isInWelcomeWindow: Boolean,
    smartCoinAllowance: Int,
    bonusCoinsRemaining: Int?,
    usageBonusBalance: Int?,
    usageMonthlyCount: Int?,
    remainingMonthlyQuota: Int?,
): AiDisplay = when {
    tier == com.danzucker.stitchpad.core.domain.model.SubscriptionTier.ATELIER ->
        AiDisplay(limit = null, used = 0)
    isInWelcomeWindow -> {
        val limit = FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT
        val remaining = usageBonusBalance ?: bonusCoinsRemaining ?: limit
        AiDisplay(limit = limit, used = (limit - remaining).coerceAtLeast(0))
    }
    else -> {
        val used = usageMonthlyCount
            ?: remainingMonthlyQuota?.let { (smartCoinAllowance - it).coerceAtLeast(0) }
            ?: 0
        AiDisplay(limit = smartCoinAllowance, used = used.coerceIn(0, smartCoinAllowance))
    }
}
