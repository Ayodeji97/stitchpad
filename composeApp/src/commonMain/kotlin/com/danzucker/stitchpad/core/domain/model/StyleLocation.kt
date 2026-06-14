package com.danzucker.stitchpad.core.domain.model

/**
 * Where a style lives. A style is either in a specific customer's closet or in the
 * top-level Inspiration place (not tied to any customer). Drives the Firestore +
 * Storage paths and the gallery/transfer behaviour. See the Inspiration design spec.
 */
sealed interface StyleLocation {
    data class CustomerCloset(val customerId: String) : StyleLocation
    data object Inspiration : StyleLocation
}
