package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of the owner-only contact sub-document at
 * `users/{uid}/customers/{cid}/private/contact`.
 *
 * Part of the Owner + Staff feature: a customer's reachable identity (phone,
 * email, address) is the commercially sensitive part staff must never see — a
 * staffer who has a customer's phone number can poach them. The base customer
 * doc keeps only name + slot fields (which staff need to do the work), and this
 * sub-doc holds the contact so Firestore rules can deny staff read access.
 *
 * During the dual-write window the base [CustomerDto] still carries these fields
 * for backward compatibility with older app versions. Slice 8a flipped the owner's
 * read onto this sub-doc (see [ownerId]); the base fields stay until a later slice
 * strips them behind a minimum app-version floor.
 */
@Serializable
data class CustomerContactDto(
    // Slice 8a: [ownerId] (the workshop owner's uid) lets the owner read contact
    // for the whole list in one `collectionGroup("private")` query filtered by
    // `ownerId == uid`; [customerId] carries the parent id so results join back
    // onto each customer without walking the document's parent chain.
    val ownerId: String = "",
    val customerId: String = "",
    val phone: String = "",
    val email: String? = null,
    val address: String? = null,
)
