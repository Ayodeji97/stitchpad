package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.presentation.toUiText
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://stitchpad.app/privacy"
private const val TERMS_URL = "https://stitchpad.app/terms"

private const val TAG = "SettingsVM"

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val customerRepository: CustomerRepository,
    private val entitlementsRepository: EntitlementsRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(SettingsState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                loadInitial()
                observeUserAndPlan()
                observeMeasurementUnit()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnProfileClick -> emit(SettingsEvent.NavigateToEditProfile)
            SettingsAction.OnUpgradeClick,
            SettingsAction.OnComparePlansClick -> emit(SettingsEvent.OpenUrl("https://stitchpad.app/upgrade"))
            SettingsAction.OnMeasurementUnitClick -> toggleMeasurementUnit()
            SettingsAction.OnEmailRowClick -> emit(SettingsEvent.NavigateToChangeEmail)
            SettingsAction.OnChangePasswordClick -> emit(SettingsEvent.NavigateToChangePassword)
            SettingsAction.OnSignOutRowClick -> _state.update { it.copy(showSignOutDialog = true) }
            SettingsAction.OnSignOutDismiss -> _state.update { it.copy(showSignOutDialog = false) }
            SettingsAction.OnSignOutConfirm -> signOut()
            SettingsAction.OnPrivacyClick -> emit(SettingsEvent.OpenUrl(PRIVACY_URL))
            SettingsAction.OnTermsClick -> emit(SettingsEvent.OpenUrl(TERMS_URL))
            SettingsAction.OnDeleteAccountClick -> emit(SettingsEvent.NavigateToDeleteAccount)
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            val provider = authRepository.getSignInProvider()
            _state.update {
                it.copy(
                    isLoading = authUser == null,
                    email = authUser?.email.orEmpty(),
                    phoneNumber = authUser?.phoneNumber,
                    avatarColorIndex = authUser?.avatarColorIndex ?: 0,
                    signInProvider = provider,
                    maskedSignInIdentifier = authUser?.email.orEmpty(),
                )
            }
        }
    }

    private fun observeUserAndPlan() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser() ?: return@launch
            val userId = authUser.id

            val userFlow = userRepository.observeUser(userId)
            val customerCountFlow = customerRepository.observeCustomers(userId)
            val premiumFlow = entitlementsRepository.observeIsPremium()

            combine(userFlow, customerCountFlow, premiumFlow) { user, customersResult, isPremium ->
                Triple(user, customersResult, isPremium)
            }.collect { (firestoreUser, customersResult, isPremium) ->
                val customerCount = when (customersResult) {
                    is Result.Success -> customersResult.data.size
                    is Result.Error -> _state.value.customerCount
                }
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        businessName = firestoreUser?.businessName.orEmpty().ifBlank {
                            authUser.displayName.ifBlank { authUser.email.substringBefore('@') }
                        },
                        phoneNumber = firestoreUser?.phoneNumber ?: authUser.phoneNumber,
                        avatarColorIndex = firestoreUser?.avatarColorIndex
                            ?: authUser.avatarColorIndex,
                        isPremium = isPremium,
                        customerCount = customerCount,
                    )
                }
            }
        }
    }

    private fun observeMeasurementUnit() {
        // The store today is a one-shot suspend reader; refresh after a toggle.
        viewModelScope.launch {
            val unit = measurementPreferencesStore.getUnit()
            _state.update { it.copy(measurementUnit = unit) }
        }
    }

    private fun toggleMeasurementUnit() {
        viewModelScope.launch {
            val current = _state.value.measurementUnit
            val next = if (current == MeasurementUnit.INCHES) MeasurementUnit.CM else MeasurementUnit.INCHES
            measurementPreferencesStore.setUnit(next)
            _state.update { it.copy(measurementUnit = next) }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isSigningOut = true, showSignOutDialog = false) }
            when (val result = authRepository.signOut()) {
                is Result.Success -> emit(SettingsEvent.NavigateToLoginAfterSignOut)
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "signOut failed error=${result.error}" }
                    _state.update { it.copy(isSigningOut = false) }
                    emit(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun emit(event: SettingsEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
