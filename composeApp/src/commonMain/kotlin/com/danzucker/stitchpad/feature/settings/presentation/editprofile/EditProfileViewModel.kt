package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.validation.BankDetailsValidator
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
import com.danzucker.stitchpad.core.sharing.applyImpliedNigerianCountryCode
import com.danzucker.stitchpad.core.sharing.defaultWhatsAppConfirmCode
import com.danzucker.stitchpad.core.sharing.normaliseNigerianPhone
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.branding.domain.defaultCompressLogo
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
import stitchpad.composeapp.generated.resources.error_bank_account_name_required
import stitchpad.composeapp.generated.resources.error_bank_account_number_invalid
import stitchpad.composeapp.generated.resources.error_bank_name_required
import stitchpad.composeapp.generated.resources.error_business_name_required
import stitchpad.composeapp.generated.resources.error_phone_format
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_whatsapp_format
import stitchpad.composeapp.generated.resources.whatsapp_confirm_error_mismatch

private const val TAG = "EditProfileVM"
private const val MIN_PHONE_DIGITS = 7
private const val MAX_PHONE_DIGITS = 15
private const val MIN_BUSINESS_NAME_LEN = 2
private const val CONFIRM_CODE_LENGTH = 4

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    @Suppress("UnusedPrivateMember") private val savedStateHandle: SavedStateHandle,
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
    // See BrandLogoCompressor.kt for why this is a function reference rather
    // than the class — JVM unit tests substitute an identity lambda.
    private val compressLogo: suspend (ByteArray) -> ByteArray? = ::defaultCompressLogo,
    private val confirmCodeGenerator: () -> String = ::defaultWhatsAppConfirmCode,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(EditProfileState())
    private var logoUploadJob: Job? = null

    // Bumped each time a logo is picked. Job.cancel() is cooperative — an
    // already-running coroutine can land its _state.update synchronously after
    // cancel() but before its next suspension point checks the flag. The local
    // generation snapshot blocks stale-job writes from clobbering the newer pick.
    private var logoUploadGeneration = 0

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
                it.copy(
                    whatsappNumber = filtered,
                    whatsappError = null,
                    whatsappConfirm = it.whatsappConfirm.copy(
                        confirmed = false, promptVisible = false,
                        code = null, input = "", error = null,
                    ),
                )
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
            is EditProfileAction.OnBankNameChange -> _state.update {
                it.copy(
                    bankName = action.value.take(it.maxBankNameLength),
                    bankNameError = null,
                )
            }
            is EditProfileAction.OnBankAccountNameChange -> _state.update {
                it.copy(
                    bankAccountName = action.value.take(it.maxBankAccountNameLength),
                    bankAccountNameError = null,
                )
            }
            is EditProfileAction.OnBankAccountNumberChange -> _state.update {
                val digitsOnly = action.value.filter { c -> c.isDigit() }
                    .take(it.bankAccountNumberLength)
                it.copy(bankAccountNumber = digitsOnly, bankAccountNumberError = null)
            }
            EditProfileAction.OnBankNameBlur -> validateBankFields()
            EditProfileAction.OnBankAccountNameBlur -> validateBankFields()
            EditProfileAction.OnBankAccountNumberBlur -> validateBankFields()
            EditProfileAction.OnConfirmWhatsAppClick -> onConfirmWhatsAppClick()
            is EditProfileAction.OnConfirmCodeChange -> onConfirmCodeChange(action.value)
            EditProfileAction.OnDismissConfirm -> _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        promptVisible = false, input = "", error = null, code = null,
                    )
                )
            }
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
            val whatsappConfirmed = firestoreUser?.whatsappConfirmed ?: false
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

            val bankName = firestoreUser?.bankName.orEmpty()
            val bankAccountName = firestoreUser?.bankAccountName.orEmpty()
            val bankAccountNumber = firestoreUser?.bankAccountNumber.orEmpty()

            _state.update {
                it.copy(
                    isLoading = false,
                    email = authUser.email,
                    businessName = business,
                    displayName = displayName,
                    phoneNumber = phone,
                    whatsappNumber = whatsapp,
                    avatarColorIndex = color,
                    bankName = bankName,
                    bankAccountName = bankAccountName,
                    bankAccountNumber = bankAccountNumber,
                    originalBusinessName = business,
                    originalDisplayName = displayName,
                    originalPhoneNumber = phone,
                    originalWhatsappNumber = whatsapp,
                    originalAvatarColorIndex = color,
                    originalBankName = bankName,
                    originalBankAccountName = bankAccountName,
                    originalBankAccountNumber = bankAccountNumber,
                    logo = logoState,
                    originalLogoUrl = logoUrl,
                    originalLogoStoragePath = logoPath,
                    whatsappConfirm = WhatsAppConfirmUiState(confirmed = whatsappConfirmed),
                    originalWhatsappConfirmed = whatsappConfirmed,
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

    private fun onConfirmWhatsAppClick() {
        val raw = _state.value.whatsappNumber.trim()
        val withCountry = applyImpliedNigerianCountryCode(raw)
        if (!validateNigerianMobileE164(withCountry)) {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_format) }
            return
        }
        val code = confirmCodeGenerator()
        val phoneE164 = "+" + normaliseNigerianPhone(withCountry)
        _state.update {
            it.copy(
                whatsappConfirm = it.whatsappConfirm.copy(
                    code = code, input = "", promptVisible = true, error = null,
                )
            )
        }
        emit(EditProfileEvent.LaunchWhatsAppConfirm(phoneE164, code))
    }

    private fun onConfirmCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(CONFIRM_CODE_LENGTH)
        _state.update {
            it.copy(whatsappConfirm = it.whatsappConfirm.copy(input = digits, error = null))
        }
        if (digits.length == CONFIRM_CODE_LENGTH) submitConfirmCode()
    }

    private fun submitConfirmCode() {
        val confirm = _state.value.whatsappConfirm
        if (confirm.code != null && confirm.input == confirm.code) {
            _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        confirmed = true, promptVisible = false,
                        code = null, input = "", error = null,
                    )
                )
            }
        } else {
            _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        error = Res.string.whatsapp_confirm_error_mismatch,
                    )
                )
            }
        }
    }

    /**
     * Bank fields are a logical group. Either all three are blank (no bank
     * details on the user doc) or all three must be valid. Validation surfaces
     * an error against any partially-filled field so the form points the user
     * at what's missing. Rules live in [BankDetailsValidator] so this VM and
     * the workshop setup VM never drift on length / NUBAN regex.
     */
    private fun validateBankFields(): Boolean {
        val s = _state.value
        if (!s.hasAnyBankInput) {
            _state.update {
                it.copy(
                    bankNameError = null,
                    bankAccountNameError = null,
                    bankAccountNumberError = null,
                )
            }
            return true
        }
        val r = BankDetailsValidator.validate(s.bankName, s.bankAccountName, s.bankAccountNumber)
        val nameErr = if (r.isBankNameValid) null else Res.string.error_bank_name_required
        val accountNameErr = if (r.isAccountNameValid) null else Res.string.error_bank_account_name_required
        val accountNumberErr = if (r.isAccountNumberValid) null else Res.string.error_bank_account_number_invalid
        _state.update {
            it.copy(
                bankNameError = nameErr,
                bankAccountNameError = accountNameErr,
                bankAccountNumberError = accountNumberErr,
            )
        }
        return r.isValid
    }

    /** WhatsApp is optional — blank is allowed; validate only when filled. */
    private fun validateWhatsapp(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) {
            _state.update { it.copy(whatsappError = null) }
            return true
        }
        val withCountry = applyImpliedNigerianCountryCode(raw)
        return if (validateNigerianMobileE164(withCountry)) {
            _state.update { it.copy(whatsappError = null) }
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_format) }
            false
        }
    }

    private fun save() {
        val allValid = listOf(
            validateBusinessName(),
            validatePhone(),
            validateWhatsapp(),
            validateBankFields(),
        ).all { it }
        if (!allValid) return

        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                emit(EditProfileEvent.NavigateBack)
                return@launch
            }
            _state.update { it.copy(isSaving = true) }
            val current = _state.value
            // Group save: all blank → all clear; any set → all three set (validation above
            // guarantees validity when hasAnyBankInput is true). Null on the repository call
            // maps to FieldValue.delete so the Firestore document drops cleared keys.
            val bankNameSave = current.bankName.trim().takeIf { current.hasAnyBankInput }
            val bankAccountNameSave =
                current.bankAccountName.trim().takeIf { current.hasAnyBankInput }
            val bankAccountNumberSave =
                current.bankAccountNumber.trim().takeIf { current.hasAnyBankInput }
            val result = userRepository.updateProfile(
                userId = authUser.id,
                businessName = current.businessName.trim(),
                displayName = current.displayName.trim().ifBlank { null },
                phoneNumber = current.phoneNumber.trim().ifBlank { null },
                whatsappNumber = current.whatsappNumber.trim().ifBlank { null },
                avatarColorIndex = current.avatarColorIndex,
                bankName = bankNameSave,
                bankAccountName = bankAccountNameSave,
                bankAccountNumber = bankAccountNumberSave,
                whatsappConfirmed = current.whatsappConfirm.confirmed,
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

    // Long because the upload path now has 3 stages (validate, compress,
    // upload-then-Firestore-then-rollback) each with their own error branch.
    // Splitting into helpers would scatter the state-transition contract.
    @Suppress("LongMethod")
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
        val myGeneration = ++logoUploadGeneration
        // Show the raw picked preview immediately; compress before the upload runs.
        _state.update { it.copy(logo = LogoUploadState.Uploading(bytes)) }
        logoUploadJob = viewModelScope.launch { runLogoUpload(bytes, myGeneration) }
    }

    private suspend fun runLogoUpload(bytes: ByteArray, myGeneration: Int) {
        val userId = authRepository.getCurrentUser()?.id ?: run {
            if (myGeneration == logoUploadGeneration) {
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
            }
            return
        }
        // Downscale + JPEG re-encode before upload. Null = decode failed despite
        // the magic-bytes check passing → surface as UnsupportedFormat.
        val compressed = compressLogo(bytes)
        if (compressed == null) {
            if (myGeneration == logoUploadGeneration) {
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                _events.send(
                    EditProfileEvent.ShowSnackbar(BrandLogoError.UnsupportedFormat.toUiText())
                )
            }
            return
        }
        val result = userRepository.uploadUserLogo(userId, compressed)
        if (myGeneration == logoUploadGeneration) {
            when (result) {
                is Result.Success -> {
                    val (url, path) = result.data
                    applyUploadedLogo(userId, url, path, bytes, myGeneration)
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

    // Only transition to Uploaded AFTER Firestore is updated. Otherwise a Firestore
    // failure (offline, rules reject) leaves the user looking at an "Uploaded" tile
    // that won't survive a reload — and the Storage object becomes orphaned because
    // nothing references it.
    private suspend fun applyUploadedLogo(
        userId: String,
        url: String,
        path: String,
        bytes: ByteArray,
        myGeneration: Int,
    ) {
        val updateResult = userRepository.updateBrandLogo(userId, url, path)
        if (myGeneration != logoUploadGeneration) return
        when (updateResult) {
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
                // Storage wrote but Firestore didn't. Storage path is deterministic
                // (users/{uid}/branding/logo.jpg), so the upload has OVERWRITTEN any
                // prior logo's bytes already.
                //
                // - First-time upload (no previous logo): the Storage object is
                //   orphaned because the user doc has no reference. Delete it to
                //   avoid permanent waste.
                // - Replacement (prior logo existed): the previous logo's bytes are
                //   already gone — overwritten by this upload. We must NOT delete
                //   now, or the path would be empty while the user doc still points
                //   at the (token-mismatched, but stable) old URL. A subsequent
                //   retry will overwrite the path again and reconcile.
                AppLogger.e(tag = TAG) { "updateBrandLogo after upload failed for userId=$userId" }
                val hadPreviousLogo = _state.value.originalLogoStoragePath != null
                if (!hadPreviousLogo) {
                    userRepository.deleteUserLogo(path)
                }
                if (myGeneration != logoUploadGeneration) return
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                _events.send(
                    EditProfileEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.error_unknown)
                    )
                )
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
