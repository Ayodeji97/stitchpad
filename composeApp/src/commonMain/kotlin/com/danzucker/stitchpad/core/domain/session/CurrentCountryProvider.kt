package com.danzucker.stitchpad.core.domain.session

import com.danzucker.stitchpad.core.domain.model.Country
import kotlinx.coroutines.flow.StateFlow

/**
 * The signed-in user's country, kept live from their user doc. Falls back to
 * [Country.DEFAULT] when signed out or while the doc has not loaded, so callers
 * never have to handle "unknown".
 */
interface CurrentCountryProvider {
    val country: StateFlow<Country>

    /**
     * The country read from the signed-in user's doc, or null while signed out or before that
     * doc has been seen this session. Use it where a [Country.DEFAULT] stand-in would be stored
     * permanently (an order's currency), not just shown.
     */
    val loadedCountry: StateFlow<Country?>

    fun currentCountry(): Country
}
