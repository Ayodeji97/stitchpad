package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val address: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** "active" | "locked" — see CustomerSlotState. Missing on legacy docs → ACTIVE. */
    val slotState: String = "active",
    /** Epoch millis when slotState was set to "locked", null otherwise. */
    val lockedAt: Long? = null,
)

/**
 * Money-free (contact-free) write shape of the base customer doc (Slice 8d-1,
 * stop-dual-write). `phone`, `email`, and `address` are DELIBERATELY absent so
 * GitLive never writes them to base again — contact now lives only in
 * `users/{uid}/customers/{cid}/private/contact` ([CustomerContactDto]).
 *
 * Reads still decode into the full [CustomerDto] (contact defaults to blank/null
 * when absent, then [com.danzucker.stitchpad.core.data.mapper.withContact] folds
 * `/private` back in), so legacy base docs that still carry contact keep working
 * via the fallback. This is the WRITE model only — never read into.
 */
@Serializable
data class CustomerBaseDto(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val slotState: String = "active",
    val lockedAt: Long? = null,
)
