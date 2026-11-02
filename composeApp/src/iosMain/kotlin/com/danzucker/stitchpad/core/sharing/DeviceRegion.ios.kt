package com.danzucker.stitchpad.core.sharing

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun deviceRegionIso(): String? = NSLocale.currentLocale.countryCode?.takeIf { it.isNotBlank() }
