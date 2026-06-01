package com.danzucker.stitchpad.core.domain.model

/**
 * A garment name a tailor has saved for their own use, surfaced in the
 * picker's "My garment types" section. Distinct from the closed [GarmentType]
 * enum — these are user-defined strings (e.g. "Iro and Buba", "Senator cape").
 *
 * The picker UX persists these across orders and sorts by [lastUsedAt] desc.
 */
data class CustomGarmentType(
    val id: String,
    val name: String, // stored as the tailor typed it (preserves casing)
    val createdAt: Long, // epoch ms
    val lastUsedAt: Long, // epoch ms, updated on every pick
)
