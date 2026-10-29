package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tracks the signed-in user's [Country] from their user doc. [uidFlow] is the auth-state uid
 * stream (null when signed out), wired from Firebase auth in DI like the other session providers.
 *
 * Resets to [Country.DEFAULT] on sign-out so one user's country never leaks into the next
 * session. A doc with no country field also resolves to [Country.DEFAULT] (the user mapper
 * applies that fallback). That covers staff, whose own doc carries no country, so they see
 * Nigerian phone handling until the workshop-currency follow-up in PR 2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UserDocCurrentCountryProvider(
    uidFlow: Flow<String?>,
    userRepository: UserRepository,
    scope: CoroutineScope,
) : CurrentCountryProvider {

    private val _country = MutableStateFlow(Country.DEFAULT)
    override val country: StateFlow<Country> = _country.asStateFlow()

    init {
        scope.launch {
            uidFlow
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(Country.DEFAULT)
                    } else {
                        // observeUser already retries internally; keep the last known value on
                        // a terminal failure rather than crashing the app-lifetime collector.
                        userRepository.observeUser(uid)
                            .map { it?.country ?: Country.DEFAULT }
                            .catch { }
                    }
                }
                .collect { _country.value = it }
        }
    }

    override fun currentCountry(): Country = _country.value
}
