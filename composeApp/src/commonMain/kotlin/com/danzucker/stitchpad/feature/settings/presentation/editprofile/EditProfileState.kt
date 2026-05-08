package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import org.jetbrains.compose.resources.StringResource

private const val MAX_BUSINESS_NAME = 50
private const val MAX_DISPLAY_NAME = 50
private const val MAX_PHONE_DIGITS = 15

data class EditProfileState(
    val isLoading: Boolean = true,
    val email: String = "",

    // Draft values (what the user is currently editing)
    val businessName: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val whatsappNumber: String = "",
    val avatarColorIndex: Int = 0,

    // Original values (what's on disk; used to compute isDirty)
    val originalBusinessName: String = "",
    val originalDisplayName: String = "",
    val originalPhoneNumber: String = "",
    val originalWhatsappNumber: String = "",
    val originalAvatarColorIndex: Int = 0,

    // Validation
    val businessNameError: StringResource? = null,
    val phoneError: StringResource? = null,
    val whatsappError: StringResource? = null,

    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = businessName != originalBusinessName ||
            displayName != originalDisplayName ||
            phoneNumber != originalPhoneNumber ||
            whatsappNumber != originalWhatsappNumber ||
            avatarColorIndex != originalAvatarColorIndex

    val hasErrors: Boolean
        get() = businessNameError != null || phoneError != null || whatsappError != null

    val businessNameCount: Int get() = businessName.length
    val maxBusinessNameLength: Int get() = MAX_BUSINESS_NAME
    val maxDisplayNameLength: Int get() = MAX_DISPLAY_NAME
    val maxPhoneDigits: Int get() = MAX_PHONE_DIGITS

    /**
     * Save is enabled iff the user has changed something AND no validation
     * errors are currently surfaced AND we're not already mid-save.
     * Required fields (business name, phone) must also be filled.
     */
    val canSave: Boolean
        get() = isDirty &&
            !hasErrors &&
            !isSaving &&
            businessName.trim().isNotEmpty() &&
            phoneNumber.filter { it.isDigit() }.length >= MIN_PHONE_DIGITS
}

private const val MIN_PHONE_DIGITS = 7
