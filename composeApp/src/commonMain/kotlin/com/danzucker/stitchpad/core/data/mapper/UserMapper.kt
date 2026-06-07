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
    dailyPushEnabled = dailyPushEnabled,
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
)
