package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderBaseDto
import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemBaseDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem

/**
 * Money-free base-write projection (Slice 8d-1, stop-dual-write). Derives the base
 * doc shape from [toOrderDto] — the single source of truth for the non-money mapping
 * (status parsing, item image legacy double-write, createdAt/updatedAt stamping) —
 * then drops every money field so GitLive stops writing them to the base doc. Money
 * goes to `/private/money` via [toOrderMoneyDto]; see [OrderBaseDto].
 *
 * Items are re-derived from [Order.items] via [toOrderItemBaseDto] rather than kept
 * from [OrderDto.toBaseDto]'s own item mapping, so the full-order write and the
 * items-only write (Phase 2b `updateItems`, see
 * [com.danzucker.stitchpad.feature.order.data.orderItemsWriteFields]) share one
 * item -> base-dto mapper. Same result either way — [toOrderItemBaseDto] delegates to
 * the same [toOrderItemDto]-then-[toBaseDto] pipeline this used to inline.
 */
fun Order.toOrderBaseDto(): OrderBaseDto = toOrderDto().toBaseDto().copy(
    items = items.map { it.toOrderItemBaseDto() },
)

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

/**
 * Direct domain -> money-free base DTO projection for a single item (Phase 2b, Task
 * 3 — staff garment-item edits). Delegates to [toOrderItemDto] (the single source of
 * truth for the legacy single-field double-write: deriving `styleId`/`stylePhotoUrl`/
 * `fabricPhotoUrl` from the first matching entry in `styleImages`/`fabricImages`) then
 * [OrderItemDto.toBaseDto] to drop `price`. Reused by both [Order.toOrderBaseDto]
 * (full order writes) and
 * [com.danzucker.stitchpad.feature.order.data.orderItemsWriteFields] (items-only
 * writes) so the base-doc item shape has exactly one mapper.
 */
internal fun OrderItem.toOrderItemBaseDto(): OrderItemBaseDto = toOrderItemDto().toBaseDto()
