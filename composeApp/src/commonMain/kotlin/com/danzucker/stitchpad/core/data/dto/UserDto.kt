package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile DTO mirroring the `users/{uid}` Firestore document.
 *
 * `phoneNumber` and `whatsappNumber` are intentionally distinct slots — `phone`
 * (Firestore) is reserved for a future Settings non-WhatsApp contact field and
 * is NOT a backward-compat alias for the WhatsApp number. The active V1
 * primary-contact field is `whatsappNumber` → Firestore `whatsapp`.
 */
@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val businessName: String? = null,
    val phoneNumber: String? = null,
    @SerialName("whatsapp")
    val whatsappNumber: String? = null,
    val avatarColorIndex: Int = 0
)
