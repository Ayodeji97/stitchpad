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
        } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") _e: Throwable) {
            // Anything that wasn't a FirebaseFunctionsException is almost
            // always a transport-layer failure (no network, DNS, TLS),
            // since the SDK surfaces all app-level errors as Functions
            // exceptions. Treating these as Network lets the UI show the
            // dedicated "no internet" message instead of the generic one.
            // We don't catch IOException specifically — it's platform-
            // different in KMP and would force expect/actual.
            Result.Error(FunctionsCallerError.Network)
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
