package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderBaseDto
import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemBaseDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.domain.model.Order

/**
 * Money-free base-write projection (Slice 8d-1, stop-dual-write). Derives the base
 * doc shape from [toOrderDto] — the single source of truth for the non-money mapping
 * (status parsing, item image legacy double-write, createdAt/updatedAt stamping) —
 * then drops every money field so GitLive stops writing them to the base doc. Money
 * goes to `/private/money` via [toOrderMoneyDto]; see [OrderBaseDto].
 */
fun Order.toOrderBaseDto(): OrderBaseDto = toOrderDto().toBaseDto()

fun OrderDto.toBaseDto(): OrderBaseDto = OrderBaseDto(
    id = id,
    customerId = customerId,
    customerName = customerName,
    status = status,
    subStatus = subStatus,
    priority = priority,
    deadline = deadline,
    notes = notes,
    archivedAt = archivedAt,
    assignedMemberId = assignedMemberId,
    assignedMemberName = assignedMemberName,
    items = items.map { it.toBaseDto() },
    statusHistory = statusHistory,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Drops per-item `price` (relocated to [OrderMoneyDto.itemPrices]); keeps all work data. */
fun OrderItemDto.toBaseDto(): OrderItemBaseDto = OrderItemBaseDto(
    id = id,
    garmentType = garmentType,
    customGarmentName = customGarmentName,
    description = description,
    quantity = quantity,
    measurementId = measurementId,
    fabricName = fabricName,
    styleImages = styleImages,
    fabricImages = fabricImages,
    styleId = styleId,
    stylePhotoUrl = stylePhotoUrl,
    stylePhotoStoragePath = stylePhotoStoragePath,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath,
)
