package com.danzucker.stitchpad.core.config.domain

/**
 * Returns true only when [url] is a well-formed WhatsApp community invite link.
 *
 * Acceptance criteria:
 * - Non-null
 * - Starts with `https://chat.whatsapp.com/` (enforces scheme + exact host prefix)
 *
 * A bare `chat.whatsapp.com/…` URL (no scheme) or any other https host both
 * evaluate to false, so a malformed remote value never reaches the URI handler.
 */
fun isUsableCommunityInviteUrl(url: String?): Boolean =
    url != null && url.startsWith("https://chat.whatsapp.com/")
