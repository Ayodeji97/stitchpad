package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerContactDto
import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import kotlin.time.Clock

/**
 * Owner-only contact payload for `users/{uid}/customers/{cid}/private/contact`.
 * See [CustomerContactDto].
 */
fun Customer.toCustomerContactDto(): CustomerContactDto = CustomerContactDto(
    phone = phone,
    email = email,
    address = address,
)

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
