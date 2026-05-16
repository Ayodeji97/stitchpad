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
        is FunctionsCallerError.PermissionDenied -> recoverFromMessage(message, fallback = SmartError.Unknown)
        is FunctionsCallerError.InvalidArgument -> SmartError.InvalidInput
        FunctionsCallerError.Unavailable -> SmartError.ServiceUnavailable
        FunctionsCallerError.Network -> SmartError.Network
        // GitLive on iOS surfaces canonical HttpsError codes as generic
        // exceptions on the 2.x Apple wrapper (FunctionsExceptionCode mapping
        // is incomplete for the Firebase Functions error domain). Defensively
        // parse the server message so iOS still routes invalid-input to the
        // dedicated UI, unavailable to "service unavailable", and
        // permission-denied: free_tier_exhausted to the Upgrade sheet —
        // instead of dumping everything into the generic Unknown bucket.
        is FunctionsCallerError.Unknown -> recoverFromMessage(message, fallback = SmartError.Unknown)
    }

    /**
     * Best-effort recovery of the intended SmartError from the server's
     * message string. Used both when canonical code mapping fails (iOS) and
     * when permission-denied could mean either "out of quota" or some other
     * permission issue we'd rather not silently call FreeTierExhausted.
     */
    private fun recoverFromMessage(message: String, fallback: SmartError): SmartError = when {
        message.contains(MARKER_FREE_TIER_EXHAUSTED) -> SmartError.FreeTierExhausted
        message.contains(MARKER_SERVICE_UNAVAILABLE) -> SmartError.ServiceUnavailable
        message.contains(MARKER_INVALID_INPUT) -> SmartError.InvalidInput
        else -> fallback
    }

    private companion object {
        // Markers must stay in sync with functions/src/smart/draftMessage.ts —
        // they are how the iOS GitLive wrapper recovers the intent of the
        // server error when the canonical code is lost.
        const val MARKER_FREE_TIER_EXHAUSTED = "free_tier_exhausted"
        const val MARKER_SERVICE_UNAVAILABLE = "service_unavailable"
        const val MARKER_INVALID_INPUT = "invalid_input"
    }
}
