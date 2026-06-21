package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ordered registry of the platform text fields currently on an auth screen.
 *
 * The iOS `UITextField` bridge uses it to move first responder to the next field
 * when the keyboard's "Next" key is pressed — Compose's focus system doesn't drive
 * native fields, so they can't chain on their own. Fields register in composition
 * (top-to-bottom) order and unregister when released, so only the visible screen's
 * fields are ever present.
 *
 * Field handles are kept as [Any] to stay in common code (a `UITextField` on iOS).
 * Unused on Android, where Compose's `KeyboardActions` already advances focus.
 */
class AuthFocusController {
    private val fields = mutableListOf<Any>()

    fun register(field: Any) {
        if (field !in fields) fields.add(field)
    }

    fun unregister(field: Any) {
        fields.remove(field)
    }

    /** The field registered immediately after [current], or null if it is the last. */
    fun nextAfter(current: Any): Any? {
        val index = fields.indexOf(current)
        return if (index >= 0) fields.getOrNull(index + 1) else null
    }
}

/**
 * Shared across all auth fields. A single instance is fine: fields unregister on
 * release, so only one screen's fields are registered at a time.
 */
private val DefaultAuthFocusController = AuthFocusController()

val LocalAuthFocusController = staticCompositionLocalOf { DefaultAuthFocusController }
