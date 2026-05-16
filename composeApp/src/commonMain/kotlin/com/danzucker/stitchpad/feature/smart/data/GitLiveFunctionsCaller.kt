package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode

internal class GitLiveFunctionsCaller(
    private val functions: FirebaseFunctions,
) : FunctionsCaller {

    override suspend fun callDraftMessage(
        request: DraftMessageRequestDto,
    ): Result<DraftMessageResponseDto, FunctionsCallerError> {
        return try {
            val response = functions
                .httpsCallable("smartDraftMessage")
                .invoke(request)
                .data<DraftMessageResponseDto>()
            Result.Success(response)
        } catch (e: FirebaseFunctionsException) {
            Result.Error(mapFunctionsException(e))
        } catch (e: Throwable) {
            // Network/IO failures bubble up as platform exceptions on Android/iOS.
            // Treat anything that is not a FirebaseFunctionsException as Unknown.
            // We do not catch a specific IOException here — it is platform-different
            // in KMP and would force expect/actual.
            Result.Error(FunctionsCallerError.Unknown(e.message ?: "unknown"))
        }
    }

    private fun mapFunctionsException(e: FirebaseFunctionsException): FunctionsCallerError =
        when (e.code) {
            FunctionsExceptionCode.PERMISSION_DENIED ->
                FunctionsCallerError.PermissionDenied(e.message ?: "")
            FunctionsExceptionCode.INVALID_ARGUMENT ->
                FunctionsCallerError.InvalidArgument(e.message ?: "")
            FunctionsExceptionCode.UNAVAILABLE ->
                FunctionsCallerError.Unavailable
            FunctionsExceptionCode.UNAUTHENTICATED ->
                FunctionsCallerError.Unknown("unauthenticated")
            else ->
                FunctionsCallerError.Unknown(e.message ?: e.code.toString())
        }
}
