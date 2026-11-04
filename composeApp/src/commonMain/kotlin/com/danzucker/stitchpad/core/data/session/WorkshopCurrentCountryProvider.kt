package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.repository.WorkshopMarketRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MARKET_WRITE_WAIT_MS = 3_000L
private const val STAFF_MARKET_REFRESH_MS = 300_000L

/** Owners observe their own profile; active staff receive only authenticated market metadata. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkshopCurrentCountryProvider(
    private val activeWorkshop: ActiveWorkshopProvider,
    private val users: UserRepository,
    private val markets: WorkshopMarketRepository,
    scope: CoroutineScope,
    private val staffRefreshMillis: Long = STAFF_MARKET_REFRESH_MS,
) : CurrentCountryProvider {
    private data class Resolution(val session: WorkshopSession, val country: Country?)
    private val resolved = MutableStateFlow<Resolution?>(null)
    private val _country = MutableStateFlow(Country.DEFAULT)
    override val country: StateFlow<Country> = _country.asStateFlow()
    private val _loadedCountry = MutableStateFlow<Country?>(null)
    override val loadedCountry: StateFlow<Country?> = _loadedCountry.asStateFlow()

    init {
        scope.launch {
            activeWorkshop.awaitHydrated()
            activeWorkshop.flow.onEach { session ->
                if (resolved.value?.session != session) publish(Resolution(session, null))
            }.flatMapLatest { session ->
                flow {
                    emit(Resolution(session, null))
                    when {
                        session.authUid.isBlank() -> Unit
                        session.isOwner -> ownerCountries(session).collect { emit(Resolution(session, it)) }
                        session.isActiveStaff -> {
                            while (currentCoroutineContext().isActive) {
                                emit(Resolution(session, readStaffCountry(session)))
                                delay(staffRefreshMillis)
                            }
                        }
                    }
                }
            }.collect { resolution -> publish(resolution) }
        }
    }

    private fun ownerCountries(session: WorkshopSession) = flow {
        var last: Country? = null
        users.observeUser(session.authUid).catch { }.collect { user ->
            if (user != null) {
                last = user.country
            } else if (last == null) {
                last = when (val read = users.getUser(session.authUid)) {
                    is Result.Success -> read.data?.country ?: Country.DEFAULT
                    is Result.Error -> null
                }
            }
            emit(last)
        }
    }

    private suspend fun readStaffCountry(session: WorkshopSession): Country? =
        when (val result = markets.getCountry(session.workshopUid)) {
            is Result.Success -> result.data
            is Result.Error -> null
        }

    private fun publish(value: Resolution) {
        if (activeWorkshop.current() != value.session) return
        val previousSession = resolved.value?.session
        resolved.value = value
        _country.value = value.country ?: if (previousSession == value.session) _country.value else Country.DEFAULT
        _loadedCountry.value = value.country
    }

    override fun currentCountry(): Country =
        if (resolved.value?.session == activeWorkshop.current()) _country.value else Country.DEFAULT

    /** A staff write requires a fresh server membership check, even when display data is cached. */
    override suspend fun countryForWrite(): Country? = withTimeoutOrNull(MARKET_WRITE_WAIT_MS) {
        val session = activeWorkshop.awaitHydrated()
        if (session.authUid.isBlank()) return@withTimeoutOrNull null
        val country = when {
            session.isOwner -> when (val read = users.getUser(session.authUid)) {
                is Result.Success -> read.data?.country ?: Country.DEFAULT
                is Result.Error -> resolved.first {
                    it != null && it.session == session && it.country != null
                }?.country
            }
            session.isActiveStaff -> readStaffCountry(session)
            else -> null
        }
        if (activeWorkshop.current() != session) return@withTimeoutOrNull null
        publish(Resolution(session, country))
        country
    }
}
