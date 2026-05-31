package com.danzucker.stitchpad.core.sharing

import kotlin.random.Random

/** Number of digits in a WhatsApp confirmation code. */
const val WHATSAPP_CONFIRM_CODE_LENGTH = 4

/**
 * Generates a [WHATSAPP_CONFIRM_CODE_LENGTH]-digit code shown to the user inside
 * WhatsApp during the "Confirm on WhatsApp" round-trip. Always zero-padded to
 * [WHATSAPP_CONFIRM_CODE_LENGTH] chars.
 *
 * Uses padStart (NOT String.format — JVM-only, breaks iOS native). Injected into
 * the ViewModels as `() -> String` so tests can make it deterministic.
 */
fun defaultWhatsAppConfirmCode(): String {
    var bound = 1
    repeat(WHATSAPP_CONFIRM_CODE_LENGTH) { bound *= 10 }
    return Random.nextInt(0, bound).toString().padStart(WHATSAPP_CONFIRM_CODE_LENGTH, '0')
}
