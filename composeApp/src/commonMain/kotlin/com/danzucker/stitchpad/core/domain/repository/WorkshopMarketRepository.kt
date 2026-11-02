package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country

/** Minimal authenticated workshop market projection, without owner-profile access. */
interface WorkshopMarketRepository {
    suspend fun getCountry(workshopUid: String): Result<Country, DataError.Network>
}
