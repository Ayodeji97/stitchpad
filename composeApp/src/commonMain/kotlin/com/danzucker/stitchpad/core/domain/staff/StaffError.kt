package com.danzucker.stitchpad.core.domain.staff

import com.danzucker.stitchpad.core.domain.error.Error

/** Typed failures for the staff invite/membership callables. */
enum class StaffError : Error {
    UNAUTHENTICATED,
    SEAT_CAP_REACHED,
    INVITE_NOT_FOUND,
    INVITE_NOT_OPEN,
    INVITE_EXPIRED,
    ALREADY_MEMBER,
    CANNOT_JOIN_OWN_WORKSHOP,
    MEMBERSHIP_NOT_FOUND,
    MEMBERSHIP_REVOKED,
    NETWORK,
    UNKNOWN,
}
