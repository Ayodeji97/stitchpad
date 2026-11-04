package com.danzucker.stitchpad.core.domain.session

import com.danzucker.stitchpad.core.domain.model.Country
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The active workshop country. Owners observe their own profile; staff uses a narrow market
 * projection. [Country.DEFAULT] is the initial/signed-out display fallback; transient failures
 * may retain a last-known display country within the same session. Writes must use
 * [countryForWrite] rather than this display fallback.
 */
interface CurrentCountryProvider {
    val country: StateFlow<Country>

    /**
     * The country read from the active workshop market ([Country.DEFAULT] once the doc is confirmed
     * absent), or null while signed out, before the first answer this session, or while the doc
     * can't be read. Use it where a [Country.DEFAULT] stand-in would be stored
     * permanently (an order's currency), not just shown.
     */
    val loadedCountry: StateFlow<Country?>

    /** Confirm a write denomination; staff implementations must recheck live membership. */
    suspend fun countryForWrite(): Country? = withTimeoutOrNull(3_000L) { loadedCountry.filterNotNull().first() }

    fun currentCountry(): Country
}
