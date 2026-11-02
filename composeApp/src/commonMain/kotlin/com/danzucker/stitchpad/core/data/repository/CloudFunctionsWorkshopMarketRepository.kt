package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.repository.WorkshopMarketRepository
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

private const val MARKET_CALL_TIMEOUT_MS = 10_000L

class CloudFunctionsWorkshopMarketRepository(private val functions: FirebaseFunctions) : WorkshopMarketRepository {
    override suspend fun getCountry(workshopUid: String): Result<Country, DataError.Network> =
        withTimeoutOrNull(MARKET_CALL_TIMEOUT_MS) {
            try {
                val result = functions.httpsCallable("getWorkshopMarket")
                    .invoke(WorkshopMarketRequest(workshopUid)).data<WorkshopMarketResponse>()
                val country = Country.fromIso(result.country)
                when {
                    country == null -> Result.Error(DataError.Network.SERIALIZATION)
                    result.workshopUid != workshopUid -> Result.Error(DataError.Network.SERIALIZATION)
                    country.currency.code != result.currency || result.timeZone !in country.timeZones ->
                        Result.Error(DataError.Network.SERIALIZATION)
                    else -> Result.Success(country)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.Error(DataError.Network.UNKNOWN)
            }
        } ?: Result.Error(DataError.Network.REQUEST_TIMEOUT)
}

@Serializable
private data class WorkshopMarketRequest(val workshopUid: String)

@Serializable
private data class WorkshopMarketResponse(
    val workshopUid: String,
    val country: String,
    val currency: String,
    val timeZone: String,
)
