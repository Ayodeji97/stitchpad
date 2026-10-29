package com.danzucker.stitchpad.core.domain.session

import com.danzucker.stitchpad.core.domain.model.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeCurrentCountryProvider(initial: Country = Country.DEFAULT) : CurrentCountryProvider {
    private val state = MutableStateFlow(initial)
    override val country: StateFlow<Country> = state
    override fun currentCountry(): Country = state.value
}
