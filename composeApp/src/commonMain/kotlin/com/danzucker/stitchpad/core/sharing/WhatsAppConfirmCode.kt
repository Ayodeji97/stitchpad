package com.danzucker.stitchpad.core.sharing

import kotlin.random.Random

/**
 * Generates the 4-digit code shown to the user inside WhatsApp during the
 * "Confirm on WhatsApp" round-trip. Always zero-padded to 4 chars.
 *
 * padStart (NOT String.format — JVM-only, breaks iOS native). Injected into
 * the ViewModels as `() -> String` so tests can make it deterministic.
 */
fun defaultWhatsAppConfirmCode(): String =
    Random.nextInt(0, 10_000).toString().padStart(4, '0')
