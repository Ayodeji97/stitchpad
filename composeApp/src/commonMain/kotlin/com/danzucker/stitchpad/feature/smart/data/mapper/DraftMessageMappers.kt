package com.danzucker.stitchpad.feature.smart.data.mapper

import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult

internal fun DraftMessageRequest.toDto(): DraftMessageRequestDto {
    val notes = customNotes?.trim().takeUnless { it.isNullOrEmpty() }
    return DraftMessageRequestDto(
        intentType = intent.wireName,
        customerId = customerId,
        orderId = orderId,
        language = language.wireName,
        customNotes = notes,
    )
}

internal fun DraftMessageResponseDto.toDomain(): DraftMessageResult =
    DraftMessageResult(
        draftText = draftText,
        remainingFreeQuota = remainingFreeQuota,
    )
