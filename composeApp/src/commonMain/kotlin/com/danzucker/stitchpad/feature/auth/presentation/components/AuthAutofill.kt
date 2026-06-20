package com.danzucker.stitchpad.feature.auth.presentation.components

/**
 * Autofill role for an [AuthTextField], mapped to a Compose `ContentType` inside
 * the field. Sign-up uses the `New*` roles so the OS offers to SAVE the new
 * credential; login uses the plain roles so the OS offers to FILL a saved one.
 */
enum class AuthAutofill {
    LoginEmail,
    LoginPassword,
    NewEmail,
    NewPassword,
    None,
}
