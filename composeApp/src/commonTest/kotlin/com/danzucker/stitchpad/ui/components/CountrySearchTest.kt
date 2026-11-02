package com.danzucker.stitchpad.ui.components

import com.danzucker.stitchpad.core.domain.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountrySearchTest {
    private val names = Country.entries.associateWith {
        when (it) {
            Country.NG -> "Nigeria"
            Country.GH -> "Ghana"
            Country.KE -> "Kenya"
            Country.CI -> "Côte d’Ivoire"
            Country.BJ -> "Bénin"
            Country.CG -> "Congo-Brazzaville"
            Country.CD -> "DR Congo"
            else -> it.iso
        }
    }

    @Test
    fun blankQueryRestoresEveryCountryInRegistryOrder() {
        assertEquals(Country.entries.toList(), searchCountries("   ", names))
    }

    @Test
    fun countryNamesIgnoreCaseAccentsAndWordOrder() {
        assertEquals(listOf(Country.CI), searchCountries("cote ivoire", names))
        assertEquals(listOf(Country.CI), searchCountries("IVOIRE COTE", names))
        assertEquals(listOf(Country.BJ), searchCountries("benin", names))
        assertEquals(listOf(Country.BJ), searchCountries("Be\u0301nin", names))
    }

    @Test
    fun commonAliasesFindCountriesWithoutChangingTheirDisplayedNames() {
        assertEquals(listOf(Country.CI), searchCountries("Ivory Coast", names))
        assertEquals(listOf(Country.CD), searchCountries("Kinshasa", names))
        assertEquals(listOf(Country.CG), searchCountries("Brazzaville", names))
    }

    @Test
    fun dialSearchAcceptsInternationalPrefixesAndFormatting() {
        listOf("233", "+233", "00233", "+2 33", "00 233").forEach {
            assertEquals(listOf(Country.GH), searchCountries(it, names), it)
        }
        assertTrue(searchCountries("+23", names).all { it.dialCode.startsWith("23") })
    }

    @Test
    fun isoAndPartialNamesFindCountriesAndUnmatchedQueryStaysEmpty() {
        assertEquals(Country.GH, searchCountries("gh", names).first())
        assertEquals(listOf(Country.KE), searchCountries("ken", names))
        assertEquals(listOf(Country.GH), searchCountries("hana", names))
        assertTrue(searchCountries("sc", names).isEmpty())
        assertTrue(searchCountries("999", names).isEmpty())
    }
}
