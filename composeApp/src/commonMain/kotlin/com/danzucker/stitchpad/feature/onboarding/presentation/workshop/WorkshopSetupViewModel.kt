package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.validation.BankDetailsValidator
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.WHATSAPP_CONFIRM_CODE_LENGTH
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
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_bank_account_name_required
import stitchpad.composeapp.generated.resources.error_bank_account_number_invalid
import stitchpad.composeapp.generated.resources.error_bank_name_required
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_session_expired
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid
import stitchpad.composeapp.generated.resources.whatsapp_confirm_error_mismatch
import stitchpad.composeapp.generated.resources.workshop_logo_upload_failed
import stitchpad.composeapp.generated.resources.workshop_logo_uploaded

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
    // See BrandLogoCompressor.kt for why this is a function reference rather
    // than the class — JVM unit tests substitute an identity lambda.
    private val compressLogo: suspend (ByteArray) -> ByteArray? = ::defaultCompressLogo,
    private val confirmCodeGenerator: () -> String = ::defaultWhatsAppConfirmCode,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    private var logoUploadJob: Job? = null
    private var continueJob: Job? = null

    // Bumped each time a logo is picked. Job.cancel() is cooperative — an
    // already-running coroutine can land its _state.update synchronously after
    // cancel() but before its next suspension point checks the flag. The local
    // generation snapshot blocks stale-job writes from clobbering the newer pick.
    private var logoUploadGeneration = 0

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: WorkshopSetupAction) {
        when (action) {
            is WorkshopSetupAction.OnBusinessNameChange ->
                _state.update { it.copy(businessName = action.name, businessNameError = null) }
            is WorkshopSetupAction.OnWhatsAppNumberChange ->
                _state.update {
                    it.copy(
                        whatsappNumber = capWhatsAppDigits(action.raw),
                        whatsappError = null,
                        whatsappConfirm = it.whatsappConfirm.copy(
                            confirmed = false,
                            promptVisible = false,
                            code = null,
                            input = "",
                            error = null,
                        ),
                    )
                }
            WorkshopSetupAction.OnBusinessNameBlur ->
                if (_state.value.businessName.isNotBlank()) validateBusinessName()
            WorkshopSetupAction.OnWhatsAppNumberBlur ->
                if (_state.value.whatsappNumber.isNotBlank()) validateWhatsAppNumber()
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> onSkip()
            is WorkshopSetupAction.OnLogoPicked -> onLogoPicked(action.bytes)
            WorkshopSetupAction.OnLogoRetry -> onLogoRetry()
            WorkshopSetupAction.OnConfirmWhatsAppClick -> onConfirmWhatsAppClick()
            is WorkshopSetupAction.OnConfirmCodeChange -> onConfirmCodeChange(action.value)
            WorkshopSetupAction.OnDismissConfirm -> _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        promptVisible = false,
                        input = "",
                        error = null,
                        code = null,
                    )
                )
            }
            WorkshopSetupAction.OnTogglePaymentDetails -> _state.update {
                it.copy(isPaymentDetailsExpanded = !it.isPaymentDetailsExpanded)
            }
            is WorkshopSetupAction.OnBankNameChange -> _state.update {
                it.copy(
                    bankName = action.value.take(BankDetailsValidator.MAX_BANK_NAME_LEN),
                    bankNameError = null,
                )
            }
            is WorkshopSetupAction.OnBankAccountNameChange -> _state.update {
                it.copy(
                    bankAccountName = action.value.take(BankDetailsValidator.MAX_ACCOUNT_NAME_LEN),
                    bankAccountNameError = null,
                )
            }
            is WorkshopSetupAction.OnBankAccountNumberChange -> _state.update {
                val digits = action.value
                    .filter { c -> c.isDigit() }
                    .take(BankDetailsValidator.ACCOUNT_NUMBER_LEN)
                it.copy(bankAccountNumber = digits, bankAccountNumberError = null)
            }
            WorkshopSetupAction.OnBankNameBlur,
            WorkshopSetupAction.OnBankAccountNameBlur,
            WorkshopSetupAction.OnBankAccountNumberBlur -> validateBankFields()
        }
    }

    /**
     * Bank trio validation. Either all blank (group skipped) or all three valid.
     * Returns true when the form may proceed. Rules live in [BankDetailsValidator]
     * so this VM and the edit-profile VM never drift on length / NUBAN regex.
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

    private fun onLogoPicked(bytes: ByteArray) {
        when (val validation = logoValidator.validate(bytes)) {
            is Result.Error -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.ShowSnackbar(validation.error.toUiText()))
                }
                return
            }
            is Result.Success -> Unit
        }
        logoUploadJob?.cancel()
        val myGeneration = ++logoUploadGeneration
        // Show the raw picked preview immediately so the user sees their pick the
        // instant the picker closes. Compression runs in-coroutine on Dispatchers.Default
        // before upload, but the preview shouldn't wait on it.
        _state.update { it.copy(logo = LogoUploadState.Uploading(bytes)) }
        logoUploadJob = viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                if (myGeneration == logoUploadGeneration) {
                    _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                }
                return@launch
            }
            // Downscale + JPEG re-encode before upload. Null means we couldn't decode
            // (the magic-bytes check passed but the payload is corrupt) — surface as
            // an UnsupportedFormat snackbar, same as a non-PNG/JPG header would.
            val compressed = compressLogo(bytes)
            if (compressed == null) {
                if (myGeneration == logoUploadGeneration) {
                    _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                    _events.send(
                        WorkshopSetupEvent.ShowSnackbar(BrandLogoError.UnsupportedFormat.toUiText())
                    )
                }
                return@launch
            }
            val result = userRepository.uploadUserLogo(userId, compressed)
            if (myGeneration == logoUploadGeneration) {
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(logo = LogoUploadState.Uploaded(result.data.first, result.data.second))
                    }
                    is Result.Error -> {
                        _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                        _events.send(WorkshopSetupEvent.ShowSnackbar(BrandLogoError.Network(result.error).toUiText()))
                    }
                }
            }
        }
    }

    private fun onLogoRetry() {
        val failed = _state.value.logo as? LogoUploadState.Failed ?: return
        onLogoPicked(failed.previewBytes)
    }

    private fun onSkip() {
        val current = _state.value.logo
        viewModelScope.launch {
            // Cancel a pending Continue first. Otherwise its pending.join() would
            // resume normally when the upload job is cancelled below, and the rest
            // of Continue (createUserProfile / updateBrandLogo) would run after
            // Skip has already cleaned up — persisting state the user opted out of.
            continueJob?.cancelAndJoin()
            continueJob = null

            // CancelAndJoin (not bare cancel) so any in-flight putData is definitively
            // stopped BEFORE we delete the path. A bare cancel + immediate delete races:
            // the upload can complete after the delete runs, leaving an orphan object
            // even though the user skipped. Repository methods rethrow
            // CancellationException so cancellation propagates cleanly.
            //
            // Awaiting the delete keeps the cleanup tied to the screen exit too —
            // NavigateToHome clears this ViewModel + cancels viewModelScope, so
            // running cleanup in a separate launch would itself get cancelled. The
            // delete returns Result.Success even on a missing object (see
            // FirebaseUserRepository.deleteUserLogo) so a no-op path stays cheap.
            logoUploadJob?.cancelAndJoin()
            logoUploadJob = null
            when (current) {
                is LogoUploadState.Uploaded -> userRepository.deleteUserLogo(current.path)
                is LogoUploadState.Uploading,
                is LogoUploadState.Failed -> {
                    val uid = authRepository.getCurrentUser()?.id
                    if (uid != null) {
                        userRepository.deleteUserLogo("users/$uid/branding/logo.jpg")
                    }
                }
                LogoUploadState.Empty -> Unit
            }
            onboardingPreferences.setWorkshopSetupCompleted()
            _events.send(WorkshopSetupEvent.NavigateToHome)
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        if (name.length < MIN_BUSINESS_NAME_LEN) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun validateWhatsAppNumber(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) return true
        val withCountry = applyImpliedNigerianCountryCode(raw)
        return if (validateNigerianMobileE164(withCountry)) {
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            false
        }
    }

    private fun onConfirmWhatsAppClick() {
        val raw = _state.value.whatsappNumber.trim()
        val withCountry = applyImpliedNigerianCountryCode(raw)
        if (!validateNigerianMobileE164(withCountry)) {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            return
        }
        val code = confirmCodeGenerator()
        val phoneE164 = "+" + normaliseNigerianPhone(withCountry)
        _state.update {
            it.copy(
                whatsappConfirm = it.whatsappConfirm.copy(
                    code = code,
                    input = "",
                    promptVisible = true,
                    error = null,
                )
            )
        }
        viewModelScope.launch {
            _events.send(WorkshopSetupEvent.LaunchWhatsAppConfirm(phoneE164, code))
        }
    }

    private fun onConfirmCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(WHATSAPP_CONFIRM_CODE_LENGTH)
        _state.update {
            it.copy(whatsappConfirm = it.whatsappConfirm.copy(input = digits, error = null))
        }
        if (digits.length == WHATSAPP_CONFIRM_CODE_LENGTH) submitConfirmCode()
    }

    private fun submitConfirmCode() {
        val confirm = _state.value.whatsappConfirm
        if (confirm.code != null && confirm.input == confirm.code) {
            _state.update {
                it.copy(
                    whatsappConfirm = it.whatsappConfirm.copy(
                        confirmed = true,
                        promptVisible = false,
                        code = null,
                        input = "",
                        error = null,
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

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun onContinue() {
        if (!validateBusinessName() || !validateWhatsAppNumber() || !validateBankFields()) return
        // Guard against re-entry from rapid taps. The Screen's button predicate
        // also disables visually on isLoading/isAwaitingLogo, but a slow
        // recomposition window could let multiple taps queue actions; this is
        // the canonical re-entry guard at the VM layer.
        if (_state.value.isLoading || _state.value.isAwaitingLogo) return

        continueJob = viewModelScope.launch {
            try {
                // If a logo upload is still in flight, await it so we can persist the URL.
                // Skip cancels this job via continueJob.cancelAndJoin() — pending.join()
                // is the cancellation suspension point, so cancellation here unwinds via
                // the finally below and the post-await work never runs.
                val pending = logoUploadJob
                if (pending != null && pending.isActive) {
                    _state.update { it.copy(isAwaitingLogo = true) }
                    pending.join()
                    _state.update { it.copy(isAwaitingLogo = false) }
                }

                val s = _state.value
                _state.update { it.copy(isLoading = true) }
                val user = authRepository.getCurrentUser() ?: run {
                    _events.send(
                        WorkshopSetupEvent.ShowError(
                            UiText.StringResourceText(Res.string.error_session_expired)
                        )
                    )
                    _events.send(WorkshopSetupEvent.NavigateToLogin)
                    return@launch
                }
                val whatsappE164 = s.whatsappNumber.trim().takeIf { it.isNotBlank() }
                    ?.let { "+" + normaliseNigerianPhone(applyImpliedNigerianCountryCode(it)) }

                // Group save: any field set → trust validation and pass the trio.
                // Otherwise pass nulls so the user doc doesn't gain blank bank fields.
                val bankNameSave = s.bankName.trim().takeIf { s.hasAnyBankInput }
                val bankAccountNameSave = s.bankAccountName.trim().takeIf { s.hasAnyBankInput }
                val bankAccountNumberSave = s.bankAccountNumber.trim().takeIf { s.hasAnyBankInput }
                val profileResult = userRepository.createUserProfile(
                    userId = user.id,
                    businessName = s.businessName.trim().ifBlank { null },
                    whatsappNumber = whatsappE164,
                    bankName = bankNameSave,
                    bankAccountName = bankAccountNameSave,
                    bankAccountNumber = bankAccountNumberSave,
                    whatsappConfirmed = s.whatsappConfirm.confirmed && whatsappE164 != null,
                )
                if (profileResult is Result.Error) {
                    _events.send(WorkshopSetupEvent.ShowError(UiText.StringResourceText(Res.string.error_unknown)))
                    return@launch
                }

                val logo = s.logo
                if (logo is LogoUploadState.Uploaded) {
                    when (userRepository.updateBrandLogo(user.id, logo.url, logo.path)) {
                        is Result.Success -> _events.send(
                            WorkshopSetupEvent.ShowSnackbar(
                                UiText.StringResourceText(Res.string.workshop_logo_uploaded)
                            )
                        )
                        is Result.Error -> {
                            // Storage upload landed but Firestore never attached the URL —
                            // delete the orphaned object so it doesn't linger forever. The
                            // user has no in-app way to remove it (Edit Profile only sees
                            // originalLogoStoragePath, which is null because the user doc
                            // never got the field). Don't block the home navigation; the
                            // profile is set, just without the logo.
                            _events.send(
                                WorkshopSetupEvent.ShowSnackbar(
                                    UiText.StringResourceText(Res.string.workshop_logo_upload_failed)
                                )
                            )
                            userRepository.deleteUserLogo(logo.path)
                        }
                    }
                }

                onboardingPreferences.setWorkshopSetupCompleted()
                _events.send(WorkshopSetupEvent.NavigateToHome)
            } finally {
                _state.update { it.copy(isLoading = false, isAwaitingLogo = false) }
            }
        }
    }

    private companion object {
        const val MAX_WHATSAPP_DIGITS = 13
        const val MIN_BUSINESS_NAME_LEN = 2

        fun capWhatsAppDigits(raw: String): String = buildString {
            var digits = 0
            raw.forEach { c ->
                val isDigit = c.isDigit()
                val isAcceptedFormatting = c in "+- ()"
                val keep = when {
                    isDigit && digits < MAX_WHATSAPP_DIGITS -> true.also { digits++ }
                    isAcceptedFormatting -> true
                    else -> false
                }
                if (keep) append(c)
            }
        }
    }
}
