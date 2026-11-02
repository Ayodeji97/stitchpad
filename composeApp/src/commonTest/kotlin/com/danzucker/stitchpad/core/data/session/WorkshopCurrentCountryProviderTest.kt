package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.repository.WorkshopMarketRepository
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkshopCurrentCountryProviderTest {
    private fun staff(owner: String = "workshop") =
        WorkshopSession("worker", owner, StaffRole.STAFF, MembershipStatus.ACTIVE)
    private fun user(country: Country) = User("owner", "a@b.com", "Ada", null, null, null, 0, country = country)
    private class Markets : WorkshopMarketRepository {
        val requested = mutableListOf<String>()
        var read: suspend (String) -> Result<Country, DataError.Network> = { Result.Success(Country.GH) }
        override suspend fun getCountry(workshopUid: String): Result<Country, DataError.Network> {
            requested += workshopUid
            return read(workshopUid)
        }
    }

    @Test
    fun staffUsesOnlyMarketProjectionAndEveryWriteRevalidatesRatherThanUsingCachedCountry() = runTest {
        val sessions = FakeActiveWorkshopProvider(staff())
        val observed = mutableListOf<String>()
        val fake = FakeUserRepository()
        val users = object : UserRepository by fake {
            override fun observeUser(userId: String): Flow<User?> {
                observed += userId
                return fake.observeUser(userId)
            }
        }
        val markets = Markets()
        val provider = WorkshopCurrentCountryProvider(sessions, users, markets, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.loadedCountry.value)
        assertTrue(observed.isEmpty(), "staff must never observe the owner's private document")
        markets.read = { Result.Success(Country.KE) }
        assertEquals(Country.KE, provider.countryForWrite())
        markets.read = { Result.Error(DataError.Network.FORBIDDEN) }
        assertNull(provider.countryForWrite(), "a revoked/offline fresh check cannot use the displayed country")
        assertEquals(listOf("workshop", "workshop", "workshop"), markets.requested)
    }

    @Test
    fun ownerMissingProfileDefaultsToNigeriaAndReadFailureHasABoundedRecoverableWriteResult() = runTest {
        val sessions = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf("owner"))
        val users = FakeUserRepository()
        val provider = WorkshopCurrentCountryProvider(sessions, users, Markets(), backgroundScope)
        runCurrent()
        assertEquals(Country.NG, provider.countryForWrite())

        val failingUsers = FakeUserRepository().apply { getUserOverride = { Result.Error(DataError.Network.NO_INTERNET) } }
        val failing = WorkshopCurrentCountryProvider(sessions, failingUsers, Markets(), backgroundScope)
        runCurrent()
        val pending = async { failing.countryForWrite() }
        advanceTimeBy(3001)
        runCurrent()
        assertNull(pending.await())
    }

    @Test
    fun switchingWorkshopOrSigningOutCancelsOldStaffMarketAndStopsPolling() = runTest {
        val sessions = FakeActiveWorkshopProvider(staff("old"))
        val old = CompletableDeferred<Country>()
        val markets = Markets().apply {
            read = { uid -> if (uid == "old") Result.Success(old.await()) else Result.Success(Country.KE) }
        }
        val provider = WorkshopCurrentCountryProvider(sessions, FakeUserRepository(), markets, backgroundScope, 1000L)
        runCurrent()
        sessions.setSession(staff("new"))
        runCurrent()
        assertEquals(Country.KE, provider.loadedCountry.value)
        old.complete(Country.GH)
        runCurrent()
        assertEquals(Country.KE, provider.loadedCountry.value)
        sessions.setSession(WorkshopSession.signedOut())
        runCurrent()
        val count = markets.requested.size
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(count, markets.requested.size)
        assertNull(provider.loadedCountry.value)
    }

    @Test
    fun ownerToStaffSwitchDropsOwnerCountryAndStaffToOwnerRestoresOwnProfile() = runTest {
        val sessions = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf("owner"))
        val users = FakeUserRepository().apply { userFlow.value = user(Country.KE) }
        val provider = WorkshopCurrentCountryProvider(sessions, users, Markets(), backgroundScope)
        runCurrent()
        assertEquals(Country.KE, provider.loadedCountry.value)
        sessions.setSession(staff())
        runCurrent()
        assertEquals(Country.GH, provider.loadedCountry.value)
        sessions.setSession(WorkshopSession.ownerOfSelf("owner"))
        runCurrent()
        assertEquals(Country.KE, provider.loadedCountry.value)
    }
}
