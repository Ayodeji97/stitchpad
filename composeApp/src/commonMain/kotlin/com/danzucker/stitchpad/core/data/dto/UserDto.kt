package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile DTO mirroring the `users/{uid}` Firestore document.
 *
 * `phoneNumber` and `whatsappNumber` are distinct slots — they are NOT
 * duplicates of each other. `phoneNumber` (Firestore: `phone`) is the optional
 * voice/SMS line; `whatsappNumber` (Firestore: `whatsapp`) is the required
 * primary customer-contact channel used by receipts, reminders, and the
 * Send-WhatsApp shortcut. Some tailors keep separate numbers for each.
 *
 * `legacyWhatsappNumber` is a read-only migration slot: users created before
 * the rename stored the value under `whatsappNumber`. The mapper falls back
 * to it when `whatsapp` is null so existing accounts keep their number until
 * they next save their profile (which rewrites under `whatsapp`).
 */
@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val businessName: String? = null,
    @SerialName("phone")
    val phoneNumber: String? = null,
    @SerialName("whatsapp")
    val whatsappNumber: String? = null,
    @SerialName("whatsappNumber")
    val legacyWhatsappNumber: String? = null,
    val avatarColorIndex: Int = 0,
    /** Welcome bonus AI coins remaining. See User.bonusCoins for full context. */
    val bonusCoins: Int? = null,
    @SerialName("businessLogoUrl")
    val businessLogoUrl: String? = null,
    @SerialName("businessLogoStoragePath")
    val businessLogoStoragePath: String? = null,
    @SerialName("businessLogoUploadId")
    val businessLogoUploadId: String? = null,
    @SerialName("bankName")
    val bankName: String? = null,
    @SerialName("bankAccountName")
    val bankAccountName: String? = null,
    @SerialName("bankAccountNumber")
    val bankAccountNumber: String? = null,
    @SerialName("whatsappConfirmed")
    val whatsappConfirmed: Boolean = false,
    @SerialName("dailyDigestEmailEnabled")
    val dailyDigestEmailEnabled: Boolean = true,
    @SerialName("dailyPushEnabled")
    val dailyPushEnabled: Boolean = true,
)
