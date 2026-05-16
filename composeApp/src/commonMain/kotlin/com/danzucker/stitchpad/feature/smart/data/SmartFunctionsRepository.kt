package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.mapper.toDomain
import com.danzucker.stitchpad.feature.smart.data.mapper.toDto
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository

internal class SmartFunctionsRepository(
    private val caller: FunctionsCaller,
) : SmartRepository {

    override suspend fun draftMessage(
        request: DraftMessageRequest,
    ): Result<DraftMessageResult, SmartError> {
        return when (val raw = caller.callDraftMessage(request.toDto())) {
            is Result.Success -> Result.Success(raw.data.toDomain())
            is Result.Error -> Result.Error(raw.error.toSmartError())
        }
    }

    private fun FunctionsCallerError.toSmartError(): SmartError = when (this) {
        is FunctionsCallerError.PermissionDenied ->
            if (message.contains("free_tier_exhausted")) {
                SmartError.FreeTierExhausted
            } else {
                SmartError.Unknown
            }
        is FunctionsCallerError.InvalidArgument -> SmartError.InvalidInput
        FunctionsCallerError.Unavailable -> SmartError.ServiceUnavailable
        FunctionsCallerError.Network -> SmartError.Network
        is FunctionsCallerError.Unknown -> SmartError.Unknown
    }
}
