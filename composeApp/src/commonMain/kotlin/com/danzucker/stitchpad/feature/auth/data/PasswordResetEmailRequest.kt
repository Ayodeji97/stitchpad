package com.danzucker.stitchpad.feature.auth.data

import kotlinx.serialization.Serializable

/**
 * Wire shape for the `sendPasswordResetEmail` callable function request body.
 * A typed DTO rather than a raw Map<String, Any?> — the latter has no serializer
 * on Kotlin/Native and crashes the iOS Functions call.
 */
@Serializable
data class PasswordResetEmailRequest(
    val email: String,
)
