package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://getstitchpad.com/privacy"
private const val TERMS_URL = "https://getstitchpad.com/terms"
private const val UPGRADE_URL = "https://getstitchpad.com/upgrade"
private const val FREE_CUSTOMER_LIMIT = 15
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
    val showThemeSheet: Boolean = false,
    val showSignOutDialog: Boolean = false,
    val isSigningOut: Boolean = false,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val customerRepository: CustomerRepository,
    private val entitlementsRepository: EntitlementsRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val themePreferencesStore: ThemePreferencesStore,
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
            SettingsAction.OnUpgradeClick,
            SettingsAction.OnComparePlansClick -> emit(SettingsEvent.OpenUrl(UPGRADE_URL))
            SettingsAction.OnMeasurementUnitClick -> toggleMeasurementUnit()
            SettingsAction.OnAppearanceClick -> uiState.update { it.copy(showThemeSheet = true) }
            SettingsAction.OnThemeSheetDismiss -> uiState.update { it.copy(showThemeSheet = false) }
            is SettingsAction.OnThemeSelect -> selectTheme(action.theme)
            SettingsAction.OnEmailRowClick -> emit(SettingsEvent.NavigateToChangeEmail)
            SettingsAction.OnChangePasswordClick -> emit(SettingsEvent.NavigateToChangePassword)
            SettingsAction.OnSignOutRowClick -> uiState.update { it.copy(showSignOutDialog = true) }
            SettingsAction.OnSignOutDismiss -> uiState.update { it.copy(showSignOutDialog = false) }
            SettingsAction.OnSignOutConfirm -> signOut()
            SettingsAction.OnPrivacyClick -> emit(SettingsEvent.OpenUrl(PRIVACY_URL))
            SettingsAction.OnTermsClick -> emit(SettingsEvent.OpenUrl(TERMS_URL))
            SettingsAction.OnDeleteAccountClick -> emit(SettingsEvent.NavigateToDeleteAccount)
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

        val combined = combine(
            userRepository.observeUser(authUser.id),
            customerRepository.observeCustomers(authUser.id),
            entitlementsRepository.observeIsPremium(),
            uiState,
        ) { firestoreUser, customersResult, isPremium, ui ->
            buildState(authUser, provider, firestoreUser, customersResult, isPremium, ui)
        }
        combined.collect { emit(it) }
    }

    private fun buildState(
        authUser: com.danzucker.stitchpad.core.domain.model.User,
        provider: SignInProvider,
        firestoreUser: com.danzucker.stitchpad.core.domain.model.User?,
        customersResult: Result<List<com.danzucker.stitchpad.core.domain.model.Customer>, *>,
        isPremium: Boolean,
        ui: LocalUiState,
    ): SettingsState {
        val customerCount = when (customersResult) {
            is Result.Success -> customersResult.data.size
            is Result.Error -> 0
        }
        val business = firestoreUser?.businessName.orEmpty().ifBlank {
            authUser.displayName.ifBlank { authUser.email.substringBefore('@') }
        }
        return SettingsState(
            isLoading = false,
            businessName = business,
            email = authUser.email,
            whatsappNumber = firestoreUser?.whatsappNumber,
            avatarColorIndex = firestoreUser?.avatarColorIndex ?: authUser.avatarColorIndex,
            signInProvider = provider,
            maskedSignInIdentifier = authUser.email,
            isPremium = isPremium,
            customerCount = customerCount,
            customerLimit = FREE_CUSTOMER_LIMIT,
            measurementUnit = ui.measurementUnit,
            themePreference = ui.themePreference,
            showThemeSheet = ui.showThemeSheet,
            showSignOutDialog = ui.showSignOutDialog,
            isSigningOut = ui.isSigningOut,
        )
    }

    private fun toggleMeasurementUnit() {
        viewModelScope.launch {
            val current = uiState.value.measurementUnit
            val next = if (current == MeasurementUnit.INCHES) MeasurementUnit.CM else MeasurementUnit.INCHES
            measurementPreferencesStore.setUnit(next)
            uiState.update { it.copy(measurementUnit = next) }
        }
    }

    private fun selectTheme(theme: ThemePreference) {
        viewModelScope.launch {
            themePreferencesStore.setTheme(theme)
            uiState.update { it.copy(themePreference = theme, showThemeSheet = false) }
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
