package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
