package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.domain.model.User

fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    whatsappNumber = whatsappNumber ?: legacyWhatsappNumber,
    avatarColorIndex = avatarColorIndex,
    bonusCoins = bonusCoins,
    businessLogoUrl = businessLogoUrl,
    businessLogoStoragePath = businessLogoStoragePath,
    bankName = bankName,
    bankAccountName = bankAccountName,
    bankAccountNumber = bankAccountNumber,
    whatsappConfirmed = whatsappConfirmed,
    dailyDigestEmailEnabled = dailyDigestEmailEnabled,
    // Absent push flag inherits the digest opt-in/out — same resolution as the backend
    // productionDigestIO. Explicit push value always wins over the digest default.
    dailyPushEnabled = dailyPushEnabled ?: dailyDigestEmailEnabled,
    // Falls back through both older preferences, matching resolveAnnouncementsEnabled
    // in functions/src/notifications/engagementPush.ts: a tailor who already opted out
    // of daily push must not be silently opted into tips.
    announcementsPushEnabled = announcementsPushEnabled ?: dailyPushEnabled ?: dailyDigestEmailEnabled,
    referralCode = referralCode,
)

fun User.toUserDto(): UserDto = UserDto(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    whatsappNumber = whatsappNumber,
    avatarColorIndex = avatarColorIndex,
    bonusCoins = bonusCoins,
    businessLogoUrl = businessLogoUrl,
    businessLogoStoragePath = businessLogoStoragePath,
    bankName = bankName,
    bankAccountName = bankAccountName,
    bankAccountNumber = bankAccountNumber,
    whatsappConfirmed = whatsappConfirmed,
    dailyDigestEmailEnabled = dailyDigestEmailEnabled,
    dailyPushEnabled = dailyPushEnabled,
    announcementsPushEnabled = announcementsPushEnabled,
)
