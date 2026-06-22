package com.danzucker.stitchpad.core.config.domain

private const val COMMUNITY_INVITE_PREFIX = "https://chat.whatsapp.com/"

/**
 * Returns true only when [url] is a well-formed WhatsApp community invite link.
 *
 * Acceptance criteria (after trimming surrounding whitespace):
 * - Non-null
 * - Starts with `https://chat.whatsapp.com/` (enforces scheme + exact host prefix)
 * - Has a non-blank invite code after that prefix
 *
 * A bare `https://chat.whatsapp.com/` (no code), a `chat.whatsapp.com/…` URL
 * (no scheme), or any other https host all evaluate to false, so a malformed
 * remote config value never reaches the URI handler as a broken invite.
 */
fun isUsableCommunityInviteUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    return trimmed.startsWith(COMMUNITY_INVITE_PREFIX) &&
        trimmed.removePrefix(COMMUNITY_INVITE_PREFIX).isNotBlank()
}
