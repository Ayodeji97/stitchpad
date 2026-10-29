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

    fun currentCountry(): Country
}
