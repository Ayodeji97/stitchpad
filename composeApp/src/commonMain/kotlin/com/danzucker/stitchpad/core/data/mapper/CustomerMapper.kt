package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerContactDto
import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import kotlin.time.Clock

/**
 * Owner-only contact payload for `users/{uid}/customers/{cid}/private/contact`.
 * See [CustomerContactDto]. [ownerId] (plus [Customer.id] as `customerId`) lets
 * the owner read contact for the whole list via one `collectionGroup("private")`
 * query — see [withContact].
 */
fun Customer.toCustomerContactDto(ownerId: String): CustomerContactDto = CustomerContactDto(
    ownerId = ownerId,
    customerId = id,
    phone = phone,
    email = email,
    address = address,
)

/**
 * Slice 8a: fold the owner-only contact sub-doc back onto a base [Customer]. When
 * present it is the source of truth for phone/email/address; a null [contact] — a
 * legacy/seeded customer not yet backfilled — falls back to the base doc's
 * contact during the dual-write window.
 */
fun Customer.withContact(contact: CustomerContactDto?): Customer {
    if (contact == null) return this
    return copy(
        phone = contact.phone,
        email = contact.email,
        address = contact.address,
    )
}

fun CustomerDto.toCustomer(userId: String = ""): Customer = Customer(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    email = email,
    address = address,
    createdAt = createdAt,
    slotState = CustomerSlotState.fromWire(slotState),
    lockedAt = lockedAt,
)

fun Customer.toCustomerDto(): CustomerDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return CustomerDto(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
        slotState = slotState.wireValue,
        lockedAt = lockedAt,
    )
}
