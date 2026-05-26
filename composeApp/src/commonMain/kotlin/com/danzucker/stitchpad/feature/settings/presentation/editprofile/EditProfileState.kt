package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import org.jetbrains.compose.resources.StringResource

private const val MAX_BUSINESS_NAME = 50
private const val MAX_DISPLAY_NAME = 50
private const val MAX_PHONE_DIGITS = 15

/**
 * Edit-profile draft. The form has two distinct phone-like inputs:
 * [phoneNumber] (optional voice line, maps to Firestore `phone`) and
 * [whatsappNumber] (required primary contact, maps to Firestore `whatsapp`).
 * They are independent — a user may have one, both, or the same value in each.
 */
data class EditProfileState(
    val isLoading: Boolean = true,
    val email: String = "",

    // Draft values
    val businessName: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val whatsappNumber: String = "",
    val avatarColorIndex: Int = 0,

    // Original values, used to compute isDirty
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

    // Logo
    val logo: LogoUploadState = LogoUploadState.Empty,
    val originalLogoUrl: String? = null,
    val originalLogoStoragePath: String? = null,
    val showRemoveLogoDialog: Boolean = false,
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
     * Save enables only with: dirty form, no validation errors, business name
     * filled. Phone and WhatsApp are both optional — their validators run when
     * the field has content but blank does not block save (existing users who
     * never added either number can still update other fields).
     */
    val canSave: Boolean
        get() = isDirty &&
            !hasErrors &&
            !isSaving &&
            businessName.trim().isNotEmpty()
}
