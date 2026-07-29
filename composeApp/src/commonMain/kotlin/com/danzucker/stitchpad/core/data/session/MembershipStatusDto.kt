package com.danzucker.stitchpad.core.data.session

import kotlinx.serialization.Serializable

/**
 * Wire shape of the slice of `users/{workshopUid}/memberships/{staffAuthUid}` the
 * session provider reads to drive the pending window. Only [status] matters here;
 * the doc carries more (invited/approved timestamps) that the provider ignores.
 *
 * Typed (not `Map<String, Any?>`) because kotlinx.serialization on Kotlin/Native
 * has no runtime serializer for `Any?` — a raw-map `snap.data()` crashes the
 * first time the snapshot listener fires on iOS. See
 * [com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider].
 */
@Serializable
internal data class MembershipStatusDto(
    val status: String = "pending",
)
