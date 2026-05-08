package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import org.jetbrains.compose.resources.StringResource

private const val MAX_BUSINESS_NAME = 50
private const val MAX_DISPLAY_NAME = 50
private const val MAX_PHONE_DIGITS = 15
private const val MIN_PHONE_DIGITS = 7

/**
 * Edit-profile draft. The "primary contact" in V1 is the WhatsApp number —
 * tailors run customer comms entirely on WhatsApp, and Firestore's `phone`
 * slot is reserved for a future non-WhatsApp contact field that is NOT in
 * V1's UI. So the form has a single required phone-like input bound to
 * [whatsappNumber].
 */
data class EditProfileState(
    val isLoading: Boolean = true,
    val email: String = "",

    // Draft values
    val businessName: String = "",
    val displayName: String = "",
    val whatsappNumber: String = "",
    val avatarColorIndex: Int = 0,

    // Original values, used to compute isDirty
    val originalBusinessName: String = "",
    val originalDisplayName: String = "",
    val originalWhatsappNumber: String = "",
    val originalAvatarColorIndex: Int = 0,

    // Validation
    val businessNameError: StringResource? = null,
    val whatsappError: StringResource? = null,

    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = businessName != originalBusinessName ||
            displayName != originalDisplayName ||
            whatsappNumber != originalWhatsappNumber ||
            avatarColorIndex != originalAvatarColorIndex

    val hasErrors: Boolean
        get() = businessNameError != null || whatsappError != null

    val businessNameCount: Int get() = businessName.length
    val maxBusinessNameLength: Int get() = MAX_BUSINESS_NAME
    val maxDisplayNameLength: Int get() = MAX_DISPLAY_NAME
    val maxPhoneDigits: Int get() = MAX_PHONE_DIGITS

    val canSave: Boolean
        get() = isDirty &&
            !hasErrors &&
            !isSaving &&
            businessName.trim().isNotEmpty() &&
            whatsappNumber.filter { it.isDigit() }.length >= MIN_PHONE_DIGITS
}
