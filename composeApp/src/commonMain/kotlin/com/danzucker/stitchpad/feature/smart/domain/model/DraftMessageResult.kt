package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Domain result returned by the repository on a successful draft.
 *
 * @param draftText the generated message body
 * @param remainingFreeQuota null if the user is on the premium tier; an
 *                           integer >= 0 for free-tier users
 */
data class DraftMessageResult(
    val draftText: String,
    val remainingFreeQuota: Int?,
)
