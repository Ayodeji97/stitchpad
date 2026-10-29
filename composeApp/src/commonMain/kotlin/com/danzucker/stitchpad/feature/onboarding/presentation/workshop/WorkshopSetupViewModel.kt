package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.defaultTimeZone
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.validation.BankDetailsValidator
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.core.sharing.WHATSAPP_CONFIRM_CODE_LENGTH
import com.danzucker.stitchpad.core.sharing.applyImpliedCountryCode
import com.danzucker.stitchpad.core.sharing.defaultWhatsAppConfirmCode
import com.danzucker.stitchpad.core.sharing.deviceRegionIso
import com.danzucker.stitchpad.core.sharing.isValidMobile
import com.danzucker.stitchpad.core.sharing.normalisePhone
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
import kotlinx.coroutines.withTimeoutOrNull
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_bank_account_name_required
import stitchpad.composeapp.generated.resources.error_bank_account_number_invalid
import stitchpad.composeapp.generated.resources.error_bank_name_required
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_session_expired
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid_for_country
import stitchpad.composeapp.generated.resources.whatsapp_confirm_error_mismatch
import stitchpad.composeapp.generated.resources.workshop_logo_upload_failed
import stitchpad.composeapp.generated.resources.workshop_logo_uploaded

// Each collaborator is injected so JVM tests can fake it (logo codec, confirm code, device locale).
// TooManyFunctions: one private handler per setup step; the Skip market write took it past 15.
@Suppress("LongParameterList", "TooManyFunctions")
class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
    // See BrandLogoCompressor.kt for why this is a function reference rather
    // than the class — JVM unit tests substitute an identity lambda.
    private val compressLogo: suspend (ByteArray) -> ByteArray? = ::defaultCompressLogo,
    private val confirmCodeGenerator: () -> String = ::defaultWhatsAppConfirmCode,
    private val analytics: Analytics,
    private val celebrations: CelebrationController,
    // Lambdas (not values) so JVM tests can fake the device locale.
    private val deviceRegion: () -> String? = ::deviceRegionIso,
    private val deviceLanguage: () -> String = { Locale.current.language },
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(deviceRegion()))
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    private var logoUploadJob: Job? = null
    private var continueJob: Job? = null
    private var skipJob: Job? = null

    // Bumped each time a logo is picked. Job.cancel() is cooperative — an
    // already-running coroutine can land its _state.update synchronously after
    // cancel() but before its next suspension point checks the flag. The local
    // generation snapshot blocks stale-job writes from clobbering the newer pick.
    private var logoUploadGeneration = 0

    // Set once the user taps a country or zone, so a late existing-doc read never overrides it.
    private var countryChosenByUser = false

    // Locale already stored on an existing user doc; kept instead of the device language.
    private var existingLocale: String? = null

    // Outcome of the one-shot existing-doc read; FAILED until the read proves otherwise.
    private var existingProfileRead = ExistingProfileRead.FAILED

    // Workshop Setup can re-run on an existing user doc (skip-then-complete, reinstall, demoted
    // staff). Pre-fill from that doc so re-entering setup never relabels a non-NG tailor; the
    // device region only applies once the read CONFIRMS there is no doc. A failed or timed-out
    // read leaves the stored country untouched (see onContinue). Bounded so Continue's wait on
    // this job can never hang setup.
    private val existingProfileJob: Job = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.id ?: return@launch
        val result = withTimeoutOrNull(EXISTING_PROFILE_WAIT_MS) { userRepository.getUser(userId) }
        if (result !is Result.Success) return@launch
        val existing = result.data
        if (existing == null) {
            existingProfileRead = ExistingProfileRead.ABSENT
            return@launch
        }
        existingProfileRead = ExistingProfileRead.PRESENT
        existingLocale = existing.locale
        if (countryChosenByUser) return@launch
        val zone = existing.timeZone.takeIf { it in existing.country.timeZones }
            ?: existing.country.defaultTimeZone
        _state.update { it.copy(country = existing.country, timeZone = zone) }
        revalidateEnteredNumber()
    }

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
            WorkshopSetupAction.OnCountryChipClick -> _state.update { it.copy(isCountryPickerOpen = true) }
            WorkshopSetupAction.OnDismissCountryPicker -> _state.update { it.copy(isCountryPickerOpen = false) }
            is WorkshopSetupAction.OnCountrySelected -> onCountrySelected(action.country)
            is WorkshopSetupAction.OnTimeZoneSelected -> onTimeZoneSelected(action.timeZone)
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> onSkip()
            WorkshopSetupAction.OnJoinWorkshopClick ->
                viewModelScope.launch { _events.send(WorkshopSetupEvent.NavigateToJoinWorkshop) }
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
        // Skip can wait up to EXISTING_PROFILE_WAIT_MS for the doc read; a second tap in that
        // window must not run a second cleanup, write or NavigateToHome.
        if (skipJob?.isActive == true) return
        val current = _state.value.logo
        skipJob = viewModelScope.launch {
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
            persistMarketOnSkip()
            // Per-user so skipping on one account never suppresses setup for another on
            // this device. The user is signed in during setup, so id is present.
            authRepository.getCurrentUser()?.id?.let {
                onboardingPreferences.setWorkshopSetupCompleted(it)
                celebrations.trigger(it, Milestone.WorkshopReady)
            }
            _events.send(WorkshopSetupEvent.NavigateToHome)
        }
    }

    /**
     * Skip still keeps a non-Nigerian market, so a tailor who picked Ghana is not silently
     * relabelled NG. Written only when the effective selection differs from the default
     * (country or zone), and only when it is safe: the user chose the country/zone, or the
     * existing-doc read CONFIRMED there is no doc (so the device-region default overwrites
     * nothing). A Nigerian skipper (untouched picker or Nigeria re-selected) gets no write, so
     * Skip creates no `users/{uid}` doc for them, exactly as before the country picker: no
     * early launch grant, billing seed or engagement-push audience entry. A present doc the user
     * didn't change already holds its market, and after a failed read the stored market is left
     * untouched. Every profile field is null (and whatsappConfirmed null) so the merge write
     * never clears a stored business name, number or confirmation. A market-only doc does not
     * count as a workshop profile: [UserRepository.hasWorkshopProfileOrNull] keys off
     * businessName, so routing is unchanged. Best effort: a rejected write never blocks Skip.
     */
    private suspend fun persistMarketOnSkip() {
        // Bounded by EXISTING_PROFILE_WAIT_MS, so Skip can never hang on it.
        if (!countryChosenByUser && existingProfileJob.isActive) existingProfileJob.join()
        val s = _state.value
        val nonDefaultMarket = s.country != Country.DEFAULT || s.timeZone != Country.DEFAULT.defaultTimeZone
        val safeToWrite = countryChosenByUser || existingProfileRead == ExistingProfileRead.ABSENT
        if (!nonDefaultMarket || !safeToWrite) return
        val userId = authRepository.getCurrentUser()?.id ?: return
        userRepository.createUserProfile(
            userId = userId,
            businessName = null,
            whatsappNumber = null,
            whatsappConfirmed = null,
            country = s.country,
            timeZone = s.timeZone,
            locale = marketLocale,
        )
    }

    /** The stored locale when the doc has one, else the device language. */
    private val marketLocale: String?
        get() = existingLocale ?: deviceLanguage().takeIf { it.isNotBlank() }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        if (name.length < MIN_BUSINESS_NAME_LEN) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun onCountrySelected(country: Country) {
        countryChosenByUser = true
        val changed = country != _state.value.country
        _state.update {
            it.copy(
                country = country,
                timeZone = if (changed) country.defaultTimeZone else it.timeZone,
                isCountryPickerOpen = false,
            )
        }
        if (!changed) return
        // A new country means a different E.164 number, so the old confirmation no longer holds.
        _state.update {
            it.copy(
                whatsappConfirm = it.whatsappConfirm.copy(
                    confirmed = false,
                    promptVisible = false,
                    code = null,
                    input = "",
                    error = null,
                ),
            )
        }
        revalidateEnteredNumber()
    }

    private fun onTimeZoneSelected(timeZone: String) {
        if (timeZone !in _state.value.country.timeZones) return
        countryChosenByUser = true
        _state.update { it.copy(timeZone = timeZone) }
    }

    /** Re-checks an already-entered number against the current country, clearing a stale error. */
    private fun revalidateEnteredNumber() {
        _state.update { it.copy(whatsappError = null) }
        if (_state.value.whatsappNumber.isNotBlank()) validateWhatsAppNumber()
    }

    private fun validateWhatsAppNumber(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) return true
        val country = _state.value.country
        return if (isValidMobile(applyImpliedCountryCode(raw, country), country)) {
            true
        } else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid_for_country) }
            false
        }
    }

    private fun onConfirmWhatsAppClick() {
        val raw = _state.value.whatsappNumber.trim()
        val country = _state.value.country
        val withCountry = applyImpliedCountryCode(raw, country)
        if (!isValidMobile(withCountry, country)) {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid_for_country) }
            return
        }
        val code = confirmCodeGenerator()
        val phoneE164 = "+" + normalisePhone(withCountry, country)
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
        // Guard against re-entry from rapid taps. The Screen's button predicate
        // also disables visually on isLoading/isAwaitingLogo, but a slow
        // recomposition window could let multiple taps queue actions; this is
        // the canonical re-entry guard at the VM layer. continueJob covers the
        // existing-doc wait below, before either flag is set. A running Skip is already leaving
        // the screen, so a Continue tapped during its wait must not write or navigate again.
        if (continueJob?.isActive == true || skipJob?.isActive == true) return
        if (_state.value.isLoading || _state.value.isAwaitingLogo) return

        continueJob = viewModelScope.launch {
            try {
                // A Continue tapped before the existing-doc read lands must not save the device
                // region over a stored country. The read job is itself time-bounded. Show the
                // spinner meanwhile so the button reads as busy.
                if (existingProfileJob.isActive) {
                    _state.update { it.copy(isLoading = true) }
                    existingProfileJob.join()
                    _state.update { it.copy(isLoading = false) }
                }
                // Validate after the wait: the pre-fill can change the country the number is
                // checked against.
                if (!validateBusinessName() || !validateWhatsAppNumber() || !validateBankFields()) return@launch
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
                val country = s.country
                // Write the market only when we know what is stored (or the user chose one).
                // After a failed read the stored country/currency/zone/locale stay as they are.
                val writeMarket = countryChosenByUser || existingProfileRead != ExistingProfileRead.FAILED
                val whatsappE164 = s.whatsappNumber.trim().takeIf { it.isNotBlank() }
                    ?.let { "+" + normalisePhone(applyImpliedCountryCode(it, country), country) }

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
                    country = country.takeIf { writeMarket },
                    timeZone = s.timeZone.takeIf { writeMarket },
                    locale = marketLocale.takeIf { writeMarket },
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

                onboardingPreferences.setWorkshopSetupCompleted(user.id)
                analytics.logEvent(AnalyticsEvent.WorkshopSetupCompleted)
                celebrations.trigger(user.id, Milestone.WorkshopReady)
                _events.send(WorkshopSetupEvent.NavigateToHome)
            } finally {
                _state.update { it.copy(isLoading = false, isAwaitingLogo = false) }
            }
        }
    }

    private enum class ExistingProfileRead { ABSENT, PRESENT, FAILED }

    private companion object {
        const val MAX_WHATSAPP_DIGITS = 13
        const val MIN_BUSINESS_NAME_LEN = 2
        const val EXISTING_PROFILE_WAIT_MS = 3_000L

        /** Device region when it is a supported market, otherwise Nigeria (blank and unknown codes included). */
        fun initialState(regionIso: String?): WorkshopSetupState {
            val country = Country.fromIso(regionIso?.trim()) ?: Country.DEFAULT
            return WorkshopSetupState(country = country, timeZone = country.defaultTimeZone)
        }

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
