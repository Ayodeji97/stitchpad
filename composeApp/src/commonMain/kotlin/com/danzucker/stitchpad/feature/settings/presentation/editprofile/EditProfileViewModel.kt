package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.edit_profile_save_failed
import stitchpad.composeapp.generated.resources.edit_profile_saved
import stitchpad.composeapp.generated.resources.error_business_name_required
import stitchpad.composeapp.generated.resources.error_phone_format
import stitchpad.composeapp.generated.resources.error_phone_required

private const val TAG = "EditProfileVM"
private const val MIN_PHONE_DIGITS = 7
private const val MAX_PHONE_DIGITS = 15
private const val MIN_BUSINESS_NAME_LEN = 2

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    @Suppress("UnusedPrivateMember") private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(EditProfileState())

    private val _events = Channel<EditProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                loadCurrentProfile()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = EditProfileState(),
        )

    fun onAction(action: EditProfileAction) {
        when (action) {
            is EditProfileAction.OnBusinessNameChange -> _state.update {
                it.copy(
                    businessName = action.value.take(it.maxBusinessNameLength),
                    businessNameError = null,
                )
            }
            is EditProfileAction.OnDisplayNameChange -> _state.update {
                it.copy(displayName = action.value.take(it.maxDisplayNameLength))
            }
            is EditProfileAction.OnWhatsappChange -> _state.update {
                val filtered = action.value.filter { c -> c.isDigit() || c in "+- ()" }
                    .take(MAX_PHONE_DIGITS + 5) // +5 covers prefix punctuation users might type
                it.copy(whatsappNumber = filtered, whatsappError = null)
            }
            is EditProfileAction.OnAvatarColorSelect -> _state.update {
                it.copy(avatarColorIndex = action.index.coerceIn(0, 5))
            }
            EditProfileAction.OnBusinessNameBlur -> validateBusinessName()
            EditProfileAction.OnWhatsappBlur -> validateWhatsapp()
            EditProfileAction.OnSaveClick -> save()
            EditProfileAction.OnBackClick -> emit(EditProfileEvent.NavigateBack)
        }
    }

    private fun loadCurrentProfile() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(EditProfileEvent.NavigateBack)
                return@launch
            }
            // observeUser().first() snapshot — using the live flow would shift draft
            // values out from under the user mid-edit.
            val firestoreUser = runCatching {
                userRepository.observeUser(authUser.id).first()
            }.getOrNull()

            val business = firestoreUser?.businessName.orEmpty()
            // Per memory `feedback_user_phone_vs_whatsapp`: the V1 primary contact
            // lives in `whatsappNumber`. Existing onboarded users with their
            // number in Firestore `phone` will see a blank field here until they
            // re-enter — `phone` is reserved for a future non-WhatsApp slot.
            val whatsapp = firestoreUser?.whatsappNumber.orEmpty()
            val color = firestoreUser?.avatarColorIndex ?: authUser.avatarColorIndex

            _state.update {
                it.copy(
                    isLoading = false,
                    email = authUser.email,
                    businessName = business,
                    displayName = authUser.displayName,
                    whatsappNumber = whatsapp,
                    avatarColorIndex = color,
                    originalBusinessName = business,
                    originalDisplayName = authUser.displayName,
                    originalWhatsappNumber = whatsapp,
                    originalAvatarColorIndex = color,
                )
            }
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        return when {
            name.isEmpty() -> {
                _state.update { it.copy(businessNameError = Res.string.error_business_name_required) }
                false
            }
            name.length < MIN_BUSINESS_NAME_LEN -> {
                _state.update { it.copy(businessNameError = Res.string.error_business_name_required) }
                false
            }
            else -> {
                _state.update { it.copy(businessNameError = null) }
                true
            }
        }
    }

    private fun validateWhatsapp(): Boolean {
        val digits = _state.value.whatsappNumber.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> {
                _state.update { it.copy(whatsappError = Res.string.error_phone_required) }
                false
            }
            digits.length !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS -> {
                _state.update { it.copy(whatsappError = Res.string.error_phone_format) }
                false
            }
            else -> {
                _state.update { it.copy(whatsappError = null) }
                true
            }
        }
    }

    private fun save() {
        val nameOk = validateBusinessName()
        val whatsappOk = validateWhatsapp()
        if (!nameOk || !whatsappOk) return

        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(EditProfileEvent.NavigateBack)
                return@launch
            }
            _state.update { it.copy(isSaving = true) }
            val current = _state.value
            val result = userRepository.updateProfile(
                userId = authUser.id,
                businessName = current.businessName.trim(),
                displayName = current.displayName.trim().ifBlank { null },
                phoneNumber = null, // V1 does not surface the non-WhatsApp slot
                whatsappNumber = current.whatsappNumber.trim(),
                avatarColorIndex = current.avatarColorIndex,
            )
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    emit(EditProfileEvent.ShowSnackbar(UiText.StringResourceText(Res.string.edit_profile_saved)))
                    emit(EditProfileEvent.NavigateBack)
                }
                is Result.Error -> {
                    AppLogger.e(tag = TAG) { "updateProfile failed error=${result.error}" }
                    emit(EditProfileEvent.ShowSnackbar(UiText.StringResourceText(Res.string.edit_profile_save_failed)))
                }
            }
        }
    }

    private fun emit(event: EditProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
