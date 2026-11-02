package com.danzucker.stitchpad.core.sharing

import java.util.Locale

actual fun deviceRegionIso(): String? = Locale.getDefault().country.takeIf { it.isNotBlank() }
