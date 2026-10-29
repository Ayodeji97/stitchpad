package com.danzucker.stitchpad.core.domain.session

import com.danzucker.stitchpad.core.domain.model.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Loaded with [initial] by default; pass `loaded = false` to model a doc that hasn't arrived yet. */
class FakeCurrentCountryProvider(
    initial: Country = Country.DEFAULT,
    loaded: Boolean = true,
) : CurrentCountryProvider {
    private val state = MutableStateFlow(initial)
    private val loadedState = MutableStateFlow(initial.takeIf { loaded })
    override val country: StateFlow<Country> = state
    override val loadedCountry: StateFlow<Country?> = loadedState
    override fun currentCountry(): Country = state.value

    /** Simulates the user doc arriving with [country]. */
    fun load(country: Country) {
        state.value = country
        loadedState.value = country
    }
}
