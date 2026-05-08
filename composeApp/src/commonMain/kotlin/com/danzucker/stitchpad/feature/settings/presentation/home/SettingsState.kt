package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider

private const val FREE_CUSTOMER_LIMIT = 15

data class SettingsState(
    val isLoading: Boolean = true,
    val businessName: String = "",
    val email: String = "",
    val whatsappNumber: String? = null,
    val avatarColorIndex: Int = 0,
    val signInProvider: SignInProvider = SignInProvider.UNKNOWN,
    val maskedSignInIdentifier: String = "",
    val isPremium: Boolean = false,
    val customerCount: Int = 0,
    val customerLimit: Int = FREE_CUSTOMER_LIMIT,
    val measurementUnit: MeasurementUnit = MeasurementUnit.INCHES,
    val showSignOutDialog: Boolean = false,
    val isSigningOut: Boolean = false,
) {
    /**
     * Combined display string for the profile hero subtitle:
     * "+234 803 555 0142 · folake@stitchpad.app", or just email if WhatsApp is
     * blank. The primary contact is the WhatsApp number, not Firestore's
     * reserved `phone` slot.
     */
    val heroSubtitle: String
        get() = listOfNotNull(
            whatsappNumber?.takeIf { it.isNotBlank() },
            email.takeIf { it.isNotBlank() },
        ).joinToString(separator = " · ")

    /** Plan card is hidden for premium users (their plan info lives elsewhere). */
    val showPlanCard: Boolean get() = !isPremium

    /** Change password row only renders for email/password providers. */
    val showChangePasswordRow: Boolean get() = signInProvider == SignInProvider.EMAIL_PASSWORD
}
