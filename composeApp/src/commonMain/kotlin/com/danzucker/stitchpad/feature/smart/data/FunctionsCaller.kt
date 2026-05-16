package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Error
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto

/**
 * Test seam over the GitLive Firebase Functions callable. The production
 * implementation in GitLiveFunctionsCaller wraps a real FirebaseFunctions
 * client; tests inject a fake.
 */
internal interface FunctionsCaller {
    suspend fun callDraftMessage(
        request: DraftMessageRequestDto,
    ): Result<DraftMessageResponseDto, FunctionsCallerError>
}

internal sealed interface FunctionsCallerError : Error {
    data class PermissionDenied(val message: String) : FunctionsCallerError
    data class InvalidArgument(val message: String) : FunctionsCallerError
    data object Unavailable : FunctionsCallerError
    data object Network : FunctionsCallerError
    data class Unknown(val message: String) : FunctionsCallerError
}
