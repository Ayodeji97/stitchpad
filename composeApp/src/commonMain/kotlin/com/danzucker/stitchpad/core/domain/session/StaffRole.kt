package com.danzucker.stitchpad.core.domain.session

/**
 * Who the signed-in user is, relative to a workshop.
 *
 * OWNER — the tailor whose data tree this is; sees everything.
 * STAFF — a worker the owner invited in; operates on the owner's tree with a
 *         restricted view (no money, no customer contact). See [WorkshopSession].
 */
enum class StaffRole {
    OWNER,
    STAFF,
}
