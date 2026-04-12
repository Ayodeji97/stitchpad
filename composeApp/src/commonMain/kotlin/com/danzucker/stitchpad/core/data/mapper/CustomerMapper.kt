package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import kotlinx.datetime.Clock

fun CustomerDto.toCustomer(userId: String = ""): Customer = Customer(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    email = email,
    address = address,
    deliveryPreference = runCatching { DeliveryPreference.valueOf(deliveryPreference) }
        .getOrDefault(DeliveryPreference.PICKUP),
    notes = notes,
    createdAt = createdAt
)

fun Customer.toCustomerDto(): CustomerDto = CustomerDto(
    id = id,
    name = name,
    phone = phone,
    email = email,
    address = address,
    deliveryPreference = deliveryPreference.name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = Clock.System.now().toEpochMilliseconds()
)
