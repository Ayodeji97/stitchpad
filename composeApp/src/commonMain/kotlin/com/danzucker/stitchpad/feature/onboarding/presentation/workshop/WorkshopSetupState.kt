package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.defaultTimeZone
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import org.jetbrains.compose.resources.StringResource

data class WorkshopSetupState(
    val businessName: String = "",
    val whatsappNumber: String = "",
    val whatsappConfirm: WhatsAppConfirmUiState = WhatsAppConfirmUiState(),
    /** The workshop's market: drives the dial-code chip, number validation and the saved currency. */
    val country: Country = Country.DEFAULT,
    /** IANA zone id; always one of [country]'s time zones. */
    val timeZone: String = Country.DEFAULT.defaultTimeZone,
    val isCountryPickerOpen: Boolean = false,
    val isLoading: Boolean = false,
    val businessNameError: StringResource? = null,
    /**
     * Formatted with the [country]'s localized name (`%1$s`), its [Country.nationalLength] (`%2$d`)
     * and its [Country.dialCode] (`%3$s`).
     */
    val whatsappError: StringResource? = null,
    val logo: LogoUploadState = LogoUploadState.Empty,
    /** True between Continue tap and in-flight upload completion. UI shows "Finishing logo upload…". */
    val isAwaitingLogo: Boolean = false,

    // Payment details (PTSP-16) — collapsed group below WhatsApp.
    val isPaymentDetailsExpanded: Boolean = false,
    val bankName: String = "",
    val bankAccountName: String = "",
    val bankAccountNumber: String = "",
    val bankNameError: StringResource? = null,
    val bankAccountNameError: StringResource? = null,
    val bankAccountNumberError: StringResource? = null,
) {
    val hasAnyBankInput: Boolean
        get() = bankName.isNotBlank() ||
            bankAccountName.isNotBlank() ||
            bankAccountNumber.isNotBlank()
}
