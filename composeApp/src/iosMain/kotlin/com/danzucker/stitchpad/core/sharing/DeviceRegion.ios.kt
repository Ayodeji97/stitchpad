package com.danzucker.stitchpad.core.sharing

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

actual fun deviceRegionIso(): String? = NSLocale.currentLocale.countryCode?.takeIf { it.isNotBlank() }

actual fun deviceLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore("-") ?: "en"
