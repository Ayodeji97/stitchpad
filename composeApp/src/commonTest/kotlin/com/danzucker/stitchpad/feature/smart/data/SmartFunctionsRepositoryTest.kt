package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.smartinfra.data.ai.FunctionsCaller
import com.danzucker.stitchpad.core.smartinfra.data.ai.FunctionsCallerError
import com.danzucker.stitchpad.core.smartinfra.domain.language.DraftLanguage
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmartFunctionsRepositoryTest {

    private val baseRequest = DraftMessageRequest(
        customerId = "c",
        orderId = "o",
        intent = DraftIntent.BalanceReminder,
        language = DraftLanguage.English,
    )

    @Test
    fun success_returns_mapped_DraftMessageResult() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Success(DraftMessageResponseDto("Hi Folake!", remainingFreeQuota = 4)),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)

        assertIs<Result.Success<DraftMessageResult>>(result)
        assertEquals("Hi Folake!", result.data.draftText)
        assertEquals(4, result.data.remainingFreeQuota)
        assertEquals(DraftIntent.BalanceReminder.wireName, fake.lastRequest?.intentType)
    }

    @Test
    fun permission_denied_with_free_tier_exhausted_maps_to_FreeTierExhausted() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.PermissionDenied("free_tier_exhausted")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.FreeTierExhausted, result.error)
    }

    @Test
    fun invalid_argument_maps_to_InvalidInput() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.InvalidArgument("invalid_input: customer not found")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.InvalidInput, result.error)
    }

    @Test
    fun unavailable_maps_to_ServiceUnavailable() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Unavailable))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.ServiceUnavailable, result.error)
    }

    @Test
    fun network_failure_maps_to_Network() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Network))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.Network, result.error)
    }

    @Test
    fun unknown_error_maps_to_Unknown() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Unknown("boom")))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.Unknown, result.error)
    }

    @Test
    fun unknown_error_carrying_free_tier_exhausted_marker_maps_to_FreeTierExhausted() = runTest {
        // Defensive path for iOS GitLive: the wrapper sometimes drops the
        // canonical PERMISSION_DENIED code so a real free-tier rejection
        // arrives as a generic Unknown with the server's message preserved.
        // The repo must still recognise it so the upgrade sheet fires.
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.Unknown("permission-denied: free_tier_exhausted")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.FreeTierExhausted, result.error)
    }

    @Test
    fun unknown_error_carrying_invalid_input_marker_maps_to_InvalidInput() = runTest {
        // Same iOS defensive path — stale/deleted orders are server
        // invalid-argument but arrive as Unknown on iOS.
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.Unknown("invalid_input: order not found")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.InvalidInput, result.error)
    }

    @Test
    fun unknown_error_carrying_service_unavailable_marker_maps_to_ServiceUnavailable() = runTest {
        // Same iOS defensive path — Vertex failures hit the server's
        // unavailable code but arrive as Unknown on iOS.
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.Unknown("service_unavailable")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertIs<Result.Error<SmartError>>(result)
        assertEquals(SmartError.ServiceUnavailable, result.error)
    }

    private class FakeFunctionsCaller(
        private val response: Result<DraftMessageResponseDto, FunctionsCallerError>,
    ) : FunctionsCaller {
        var lastRequest: DraftMessageRequestDto? = null
        override suspend fun callDraftMessage(
            request: DraftMessageRequestDto,
        ): Result<DraftMessageResponseDto, FunctionsCallerError> {
            lastRequest = request
            return response
        }
    }
}
