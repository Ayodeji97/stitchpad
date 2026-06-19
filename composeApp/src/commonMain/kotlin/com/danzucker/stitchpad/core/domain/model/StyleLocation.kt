package com.danzucker.stitchpad.core.domain.model

/**
 * Where a style lives. A style is either in a specific customer's closet or in the
 * top-level Inspiration place (not tied to any customer). Drives the Firestore +
 * Storage paths and the gallery/transfer behaviour. See the Inspiration design spec.
 */
sealed interface StyleLocation {
    /** [folderId] null = the customer's flat default folder (existing `styles` collection). */
    data class CustomerCloset(val customerId: String, val folderId: String? = null) : StyleLocation

    /** [folderId] null = the flat default Inspiration folder (existing `inspiration` collection). */
    data class Inspiration(val folderId: String? = null) : StyleLocation
}
