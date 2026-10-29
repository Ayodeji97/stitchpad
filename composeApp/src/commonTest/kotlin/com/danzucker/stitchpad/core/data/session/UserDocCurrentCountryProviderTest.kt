package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserDocCurrentCountryProviderTest {

    private fun user(country: Country = Country.DEFAULT) = User(
        id = "u1",
        email = "u@x.com",
        displayName = "Ada",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
        country = country,
    )

    @Test
    fun signed_out_is_nigeria() = runTest {
        val provider = UserDocCurrentCountryProvider(
            uidFlow = MutableStateFlow<String?>(null),
            userRepository = FakeUserRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(Country.NG, provider.currentCountry())
    }

    @Test
    fun emits_users_country() = runTest {
        val repo = FakeUserRepository().apply { userFlow.value = user(Country.GH) }
        val provider = UserDocCurrentCountryProvider(MutableStateFlow("u1"), repo, backgroundScope)
        runCurrent()

        assertEquals(Country.GH, provider.currentCountry())
        assertEquals(Country.GH, provider.country.value)
    }

    @Test
    fun country_change_propagates() = runTest {
        val repo = FakeUserRepository().apply { userFlow.value = user(Country.GH) }
        val provider = UserDocCurrentCountryProvider(MutableStateFlow("u1"), repo, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.currentCountry())

        repo.userFlow.value = user(Country.KE)
        runCurrent()

        assertEquals(Country.KE, provider.currentCountry())
    }

    @Test
    fun sign_out_resets_to_nigeria() = runTest {
        val uid = MutableStateFlow<String?>("u1")
        val repo = FakeUserRepository().apply { userFlow.value = user(Country.GH) }
        val provider = UserDocCurrentCountryProvider(uid, repo, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.currentCountry())

        uid.value = null
        runCurrent()

        assertEquals(Country.NG, provider.currentCountry())
    }

    @Test
    fun missing_user_doc_or_country_falls_back_to_nigeria() = runTest {
        // Staff members have no country on their own doc. PR 2's workshop-currency
        // follow-up should resolve staff against the owner's workshop instead.
        val repo = FakeUserRepository().apply { userFlow.value = null }
        val provider = UserDocCurrentCountryProvider(MutableStateFlow("staff-1"), repo, backgroundScope)
        runCurrent()
        assertEquals(Country.NG, provider.currentCountry())

        repo.userFlow.value = user() // mapper default when the doc has no country
        runCurrent()
        assertEquals(Country.NG, provider.currentCountry())
    }

    @Test
    fun null_emission_after_a_doc_keeps_last_known_country() = runTest {
        val repo = FakeUserRepository().apply { userFlow.value = user(Country.GH) }
        val provider = UserDocCurrentCountryProvider(MutableStateFlow("u1"), repo, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.currentCountry())

        repo.userFlow.value = null // listener error fallback
        runCurrent()

        assertEquals(Country.GH, provider.currentCountry())
    }

    @Test
    fun direct_account_switch_resets_to_default_until_new_doc_emits() = runTest {
        val uid = MutableStateFlow<String?>("u1")
        val repo = FakeUserRepository()
        repo.userFlowsByUid["u1"] = MutableStateFlow<User?>(user(Country.GH))
        val u2Doc = MutableSharedFlow<User?>()
        repo.userFlowsByUid["u2"] = u2Doc
        val provider = UserDocCurrentCountryProvider(uid, repo, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.currentCountry())

        uid.value = "u2"
        runCurrent()
        assertEquals(Country.NG, provider.currentCountry())

        u2Doc.emit(user(Country.KE))
        runCurrent()
        assertEquals(Country.KE, provider.currentCountry())
    }

    @Test
    fun sign_out_then_sign_in_with_null_first_emission_does_not_leak_previous_country() = runTest {
        val uid = MutableStateFlow<String?>("u1")
        val repo = FakeUserRepository()
        repo.userFlowsByUid["u1"] = MutableStateFlow<User?>(user(Country.GH))
        repo.userFlowsByUid["u2"] = MutableStateFlow<User?>(null)
        val provider = UserDocCurrentCountryProvider(uid, repo, backgroundScope)
        runCurrent()
        assertEquals(Country.GH, provider.currentCountry())

        uid.value = null
        runCurrent()
        uid.value = "u2"
        runCurrent()

        assertEquals(Country.NG, provider.currentCountry())
    }

    @Test
    fun loaded_country_is_null_until_the_users_doc_arrives_and_resets_on_switch() = runTest {
        val uid = MutableStateFlow<String?>("u1")
        val repo = FakeUserRepository()
        val u1Doc = MutableSharedFlow<User?>()
        repo.userFlowsByUid["u1"] = u1Doc
        repo.userFlowsByUid["u2"] = MutableSharedFlow<User?>()
        val provider = UserDocCurrentCountryProvider(uid, repo, backgroundScope)
        runCurrent()
        assertNull(provider.loadedCountry.value, "no doc yet")
        assertEquals(Country.NG, provider.currentCountry(), "shown as the default meanwhile")

        u1Doc.emit(null) // a failed listener or missing doc is still "not loaded"
        runCurrent()
        assertNull(provider.loadedCountry.value)

        u1Doc.emit(user(Country.GH))
        runCurrent()
        assertEquals(Country.GH, provider.loadedCountry.value)

        uid.value = "u2"
        runCurrent()
        assertNull(provider.loadedCountry.value, "a new account starts unloaded")

        uid.value = null
        runCurrent()
        assertNull(provider.loadedCountry.value, "signed out is unloaded")
    }
}
