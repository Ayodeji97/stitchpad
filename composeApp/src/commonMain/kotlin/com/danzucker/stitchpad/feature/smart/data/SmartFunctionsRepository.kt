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
            if (message.containsExhaustedMarker()) {
                SmartError.FreeTierExhausted
            } else {
                SmartError.Unknown
            }
        is FunctionsCallerError.InvalidArgument -> SmartError.InvalidInput
        FunctionsCallerError.Unavailable -> SmartError.ServiceUnavailable
        FunctionsCallerError.Network -> SmartError.Network
        // GitLive on iOS sometimes surfaces canonical HttpsError codes as
        // generic exceptions (the FunctionsExceptionCode mapping isn't always
        // complete in the Apple wrapper). Defensively check the message
        // substring so the upgrade sheet still fires when the server
        // legitimately rejected with permission-denied: free_tier_exhausted.
        is FunctionsCallerError.Unknown ->
            if (message.containsExhaustedMarker()) {
                SmartError.FreeTierExhausted
            } else {
                SmartError.Unknown
            }
    }

    private fun String.containsExhaustedMarker(): Boolean =
        contains("free_tier_exhausted")
}
