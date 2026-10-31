package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.model.defaultTimeZone

fun UserDto.toUser(): User {
    // Legacy docs (and unknown codes) are Nigerian; currency and zone derive from the country.
    val resolvedCountry = Country.fromIso(country?.uppercase()) ?: Country.DEFAULT
    return User(
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
        country = resolvedCountry,
        currency = Currency.fromCode(currency?.uppercase()) ?: resolvedCountry.currency,
        timeZone = timeZone ?: resolvedCountry.defaultTimeZone,
        locale = locale,
    )
}

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
    country = country.iso,
    currency = currency.code,
    timeZone = timeZone,
    locale = locale,
)
