package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.feature.branding.presentation.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import stitchpad.composeapp.generated.resources.edit_profile_logo_removed
import stitchpad.composeapp.generated.resources.edit_profile_logo_updated
import stitchpad.composeapp.generated.resources.edit_profile_save_failed
import stitchpad.composeapp.generated.resources.edit_profile_saved
import stitchpad.composeapp.generated.resources.error_business_name_required
import stitchpad.composeapp.generated.resources.error_unknown
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
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(EditProfileState())
    private var logoUploadJob: Job? = null

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
            is EditProfileAction.OnLogoPicked -> onLogoPicked(action.bytes)
            EditProfileAction.OnLogoRemoveClick -> _state.update { it.copy(showRemoveLogoDialog = true) }
            EditProfileAction.OnLogoRemoveDismiss -> _state.update { it.copy(showRemoveLogoDialog = false) }
            EditProfileAction.OnLogoRemoveConfirm -> onLogoRemoveConfirm()
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

            val logoUrl = firestoreUser?.businessLogoUrl
            val logoPath = firestoreUser?.businessLogoStoragePath
            val logoState = if (logoUrl != null && logoPath != null) {
                LogoUploadState.Uploaded(logoUrl, logoPath)
            } else {
                LogoUploadState.Empty
            }

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
                    logo = logoState,
                    originalLogoUrl = logoUrl,
                    originalLogoStoragePath = logoPath,
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
            when (result) {
                is Result.Success -> {
                    // Mirror displayName onto the Firebase Auth side so the cached
                    // FirebaseUser.displayName matches Firestore. Without this, a
                    // user who clears the field would see the old name reappear
                    // next time Edit Profile loads (which falls back to authUser).
                    val displayName = current.displayName.trim().ifBlank { null }
                    runCatching { authRepository.updateAuthDisplayName(displayName) }
                        .onFailure { AppLogger.e(tag = TAG, throwable = it) { "auth displayName sync failed" } }
                    _state.update { it.copy(isSaving = false) }
                    emit(
                        EditProfileEvent.SaveSucceeded(
                            UiText.StringResourceText(Res.string.edit_profile_saved)
                        )
                    )
                }
                is Result.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    AppLogger.e(tag = TAG) { "updateProfile failed error=${result.error}" }
                    emit(EditProfileEvent.ShowSnackbar(UiText.StringResourceText(Res.string.edit_profile_save_failed)))
                }
            }
        }
    }

    private fun onLogoPicked(bytes: ByteArray) {
        when (val validation = logoValidator.validate(bytes)) {
            is Result.Error -> {
                viewModelScope.launch {
                    _events.send(EditProfileEvent.ShowSnackbar(validation.error.toUiText()))
                }
                return
            }
            is Result.Success -> Unit
        }
        logoUploadJob?.cancel()
        _state.update { it.copy(logo = LogoUploadState.Uploading(bytes)) }
        logoUploadJob = viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                return@launch
            }
            when (val result = userRepository.uploadUserLogo(userId, bytes)) {
                is Result.Success -> {
                    val (url, path) = result.data
                    // Only transition to Uploaded AFTER Firestore is updated. Otherwise
                    // a Firestore failure (offline, rules reject) leaves the user looking
                    // at a "Uploaded" tile that won't survive a reload — and the Storage
                    // object becomes orphaned because nothing references it.
                    when (userRepository.updateBrandLogo(userId, url, path)) {
                        is Result.Success -> {
                            _state.update {
                                it.copy(
                                    logo = LogoUploadState.Uploaded(url, path),
                                    originalLogoUrl = url,
                                    originalLogoStoragePath = path,
                                )
                            }
                            emit(
                                EditProfileEvent.ShowSnackbar(
                                    UiText.StringResourceText(Res.string.edit_profile_logo_updated)
                                )
                            )
                        }
                        is Result.Error -> {
                            // Storage wrote but Firestore didn't. Roll the UI back to
                            // Failed (so the user is prompted to retry) and clean up the
                            // orphaned Storage object. The original logo (if any) stays
                            // on the user doc, so it'll reappear on next snapshot.
                            AppLogger.e(tag = TAG) { "updateBrandLogo after upload failed for userId=$userId" }
                            userRepository.deleteUserLogo(path)
                            _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                            _events.send(
                                EditProfileEvent.ShowSnackbar(
                                    UiText.StringResourceText(Res.string.error_unknown)
                                )
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                    _events.send(
                        EditProfileEvent.ShowSnackbar(BrandLogoError.Network(result.error).toUiText())
                    )
                }
            }
        }
    }

    private fun onLogoRemoveConfirm() {
        _state.update { it.copy(showRemoveLogoDialog = false) }
        viewModelScope.launch {
            // Cancel + await any in-flight upload first. Without this, a user who tapped
            // Change (upload mid-flight) and then confirmed Remove would race: this path
            // clears Firestore + Storage, then the still-running upload coroutine sets
            // LogoUploadState.Uploaded and calls updateBrandLogo(url, path), resurrecting
            // a logo the user just removed (and pointing Firestore at a deleted object).
            // The repository methods rethrow CancellationException, so the upload's
            // failure branch won't run after cancel.
            logoUploadJob?.cancelAndJoin()
            logoUploadJob = null

            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            val pathToDelete = _state.value.let {
                it.originalLogoStoragePath ?: (it.logo as? LogoUploadState.Uploaded)?.path
            }
            // Gate Storage delete + local state clear on the Firestore clear succeeding.
            // If the Firestore write fails (offline, rules reject), we must NOT delete the
            // Storage object — the user doc would still point at a now-missing URL and the
            // next snapshot would resurrect a broken reference.
            when (userRepository.updateBrandLogo(uid, null, null)) {
                is Result.Success -> {
                    pathToDelete?.let { userRepository.deleteUserLogo(it) }
                    _state.update {
                        it.copy(
                            logo = LogoUploadState.Empty,
                            originalLogoUrl = null,
                            originalLogoStoragePath = null,
                        )
                    }
                    _events.send(
                        EditProfileEvent.ShowSnackbar(
                            UiText.StringResourceText(Res.string.edit_profile_logo_removed)
                        )
                    )
                }
                is Result.Error -> {
                    _events.send(
                        EditProfileEvent.ShowSnackbar(
                            UiText.StringResourceText(Res.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    private fun emit(event: EditProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
