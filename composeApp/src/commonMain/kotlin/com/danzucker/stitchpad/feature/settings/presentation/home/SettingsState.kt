package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider

// QA-only: lowered from 15 → 3, see SettingsViewModel for context.
private const val FREE_CUSTOMER_LIMIT = 3

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
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val showThemeSheet: Boolean = false,
    val showSignOutDialog: Boolean = false,
    val isSigningOut: Boolean = false,
) {
    /**
     * Combined display string for the profile hero subtitle.
     * The separator uses a Unicode escape (U+2022 BULLET) rather than a literal
     * glyph so any downstream encoding mismatch can't mangle it to mojibake.
     */
    val heroSubtitle: String
        get() = listOfNotNull(
            whatsappNumber?.takeIf { it.isNotBlank() },
            email.takeIf { it.isNotBlank() },
        ).joinToString(separator = " • ")

    /** Plan card is hidden for premium users (their plan info lives elsewhere). */
    val showPlanCard: Boolean get() = !isPremium

    /** Change password row only renders for email/password providers. */
    val showChangePasswordRow: Boolean get() = signInProvider == SignInProvider.EMAIL_PASSWORD

    /**
     * Change-email row is hidden for SSO users — their email is owned by the
     * identity provider (Google/Apple), Firebase rejects
     * `verifyBeforeUpdateEmail` for federated accounts, and the Sign-in method
     * row already shows the same email above.
     */
    val showChangeEmailRow: Boolean get() = signInProvider == SignInProvider.EMAIL_PASSWORD
}
