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
import stitchpad.composeapp.generated.resources.error_whatsapp_format

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

    @Suppress("CyclomaticComplexMethod")
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
            is EditProfileAction.OnPhoneChange -> _state.update {
                val filtered = action.value.filter { c -> c.isDigit() || c in "+- ()" }
                    .take(MAX_PHONE_DIGITS + 5)
                it.copy(phoneNumber = filtered, phoneError = null)
            }
            is EditProfileAction.OnWhatsappChange -> _state.update {
                val filtered = action.value.filter { c -> c.isDigit() || c in "+- ()" }
                    .take(MAX_PHONE_DIGITS + 5)
                it.copy(whatsappNumber = filtered, whatsappError = null)
            }
            is EditProfileAction.OnAvatarColorSelect -> _state.update {
                it.copy(avatarColorIndex = action.index.coerceIn(0, 5))
            }
            EditProfileAction.OnBusinessNameBlur -> validateBusinessName()
            EditProfileAction.OnPhoneBlur -> validatePhone()
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
            // Snapshot read — using the live flow would shift draft values out
            // from under the user mid-edit.
            val firestoreUser = runCatching {
                userRepository.observeUser(authUser.id).first()
            }.getOrNull()

            val business = firestoreUser?.businessName.orEmpty()
            // Existing onboarded users have their value in Firestore `phone`,
            // which now lands in the optional Phone field. Their WhatsApp slot
            // starts empty until they fill it in here. New users coming through
            // a future onboarding update will write directly to `whatsapp`.
            val phone = firestoreUser?.phoneNumber.orEmpty()
            val whatsapp = firestoreUser?.whatsappNumber.orEmpty()
            val color = firestoreUser?.avatarColorIndex ?: authUser.avatarColorIndex
            // Prefer the Firestore value once the user has saved a display
            // name through this screen; fall back to Firebase Auth's
            // displayName for users who haven't edited it yet.
            val displayName = firestoreUser?.displayName?.takeIf { it.isNotBlank() }
                ?: authUser.displayName

            _state.update {
                it.copy(
                    isLoading = false,
                    email = authUser.email,
                    businessName = business,
                    displayName = displayName,
                    phoneNumber = phone,
                    whatsappNumber = whatsapp,
                    avatarColorIndex = color,
                    originalBusinessName = business,
                    originalDisplayName = displayName,
                    originalPhoneNumber = phone,
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

    /** Phone is optional. Empty passes; a partial value (some digits but
     *  outside the 7..15 window) fails. */
    private fun validatePhone(): Boolean {
        val raw = _state.value.phoneNumber
        if (raw.isBlank()) {
            _state.update { it.copy(phoneError = null) }
            return true
        }
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS -> {
                _state.update { it.copy(phoneError = Res.string.error_phone_format) }
                false
            }
            else -> {
                _state.update { it.copy(phoneError = null) }
                true
            }
        }
    }

    /** WhatsApp is optional — blank is allowed; validate only when filled. */
    private fun validateWhatsapp(): Boolean {
        val digits = _state.value.whatsappNumber.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> {
                _state.update { it.copy(whatsappError = null) }
                true
            }
            digits.length !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS -> {
                _state.update { it.copy(whatsappError = Res.string.error_whatsapp_format) }
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
        val phoneOk = validatePhone()
        val whatsappOk = validateWhatsapp()
        if (!nameOk || !phoneOk || !whatsappOk) return

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
                phoneNumber = current.phoneNumber.trim().ifBlank { null },
                whatsappNumber = current.whatsappNumber.trim().ifBlank { null },
                avatarColorIndex = current.avatarColorIndex,
            )
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> emit(
                    EditProfileEvent.SaveSucceeded(
                        UiText.StringResourceText(Res.string.edit_profile_saved)
                    )
                )
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
