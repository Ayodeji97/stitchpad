package com.danzucker.stitchpad.core.domain.model

import com.danzucker.stitchpad.core.sharing.isValidMobile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryTest {

    private val xof = setOf(
        Country.CI, Country.SN, Country.BJ, Country.TG, Country.ML, Country.BF, Country.NE,
    )
    private val xaf = setOf(Country.CM, Country.GA, Country.CG)

    @Test
    fun `every country has a unique iso and dial code`() {
        assertEquals(Country.entries.size, Country.entries.map { it.iso }.toSet().size)
        assertEquals(Country.entries.size, Country.entries.map { it.dialCode }.toSet().size)
    }

    @Test
    fun `fromIso is case insensitive and rejects unknown`() {
        assertEquals(Country.GH, Country.fromIso("gh"))
        assertNull(Country.fromIso("ZZ"))
        assertNull(Country.fromIso(null))
        assertEquals(Country.NG, Country.DEFAULT)
    }

    @Test
    fun `currency fromCode is case insensitive and rejects unknown`() {
        assertEquals(Currency.XOF, Currency.fromCode("xof"))
        assertNull(Currency.fromCode("USD"))
        assertNull(Currency.fromCode(null))
    }

    @Test
    fun `nigeria keeps paystack and everyone else uses play`() {
        assertEquals(AndroidBilling.PAYSTACK, Country.NG.androidBilling)
        Country.entries.filter { it != Country.NG }.forEach {
            assertEquals(AndroidBilling.PLAY, it.androidBilling, it.iso)
        }
    }

    @Test
    fun currencies() {
        assertEquals(Currency.NGN, Country.NG.currency)
        assertEquals(2, Currency.NGN.decimals)
        assertEquals(Currency.GHS, Country.GH.currency)
        assertEquals(Currency.KES, Country.KE.currency)
        xof.forEach { assertEquals(Currency.XOF, it.currency, it.iso) }
        xaf.forEach { assertEquals(Currency.XAF, it.currency, it.iso) }
        assertEquals(0, Currency.XOF.decimals)
        assertEquals(0, Currency.XAF.decimals)
        assertEquals(Currency.GNF, Country.GN.currency)
        assertEquals(0, Currency.GNF.decimals)
        assertEquals(Currency.CDF, Country.CD.currency)
        assertEquals(2, Currency.CDF.decimals)
    }

    @Test
    fun languages() {
        val english = setOf(Country.NG, Country.GH, Country.KE)
        Country.entries.forEach {
            assertEquals(if (it in english) "en" else "fr", it.defaultLanguage, it.iso)
        }
    }

    @Test
    fun `time zones`() {
        assertEquals("Africa/Lagos", Country.NG.defaultTimeZone)
        assertEquals("Africa/Accra", Country.GH.defaultTimeZone)
        assertEquals("Africa/Nairobi", Country.KE.defaultTimeZone)
        assertEquals(listOf("Africa/Kinshasa", "Africa/Lubumbashi"), Country.CD.timeZones)
        Country.entries.filter { it != Country.CD }.forEach {
            assertEquals(1, it.timeZones.size, it.iso)
        }
    }

    @Test
    fun `trunk prefix only where the national plan has one`() {
        val withTrunk = setOf(Country.NG, Country.GH, Country.KE, Country.CD)
        Country.entries.forEach {
            assertEquals(if (it in withTrunk) "0" else null, it.trunkPrefix, it.iso)
        }
    }

    // Mobile examples from libphonenumber PhoneNumberMetadata.xml (plus the plan's samples).
    private val mobileSamples = mapOf(
        Country.NG to listOf("8031234567", "8021234567", "7201234567"),
        Country.GH to listOf("244123456", "231234567"),
        Country.KE to listOf("712345678", "712123456", "110123456"),
        Country.CI to listOf("0701234567", "0123456789"),
        Country.SN to listOf("771234567", "701234567"),
        Country.BJ to listOf("0190123456", "0195123456"),
        Country.TG to listOf("90123456", "90112345"),
        Country.ML to listOf("76123456", "65012345"),
        Country.BF to listOf("70123456"),
        Country.NE to listOf("93123456"),
        Country.CM to listOf("671234567"),
        Country.GA to listOf("06123456", "06031234"),
        Country.CG to listOf("061234567"),
        Country.GN to listOf("621234567", "601123456"),
        Country.CD to listOf("812345678", "991234567"),
    )

    // Landline examples from libphonenumber: must never validate as mobile.
    private val landlineSamples = mapOf(
        Country.NG to "2033123456",
        Country.GH to "302345678",
        Country.KE to "202012345",
        Country.CI to "2123456789",
        Country.SN to "301012345",
        Country.BJ to "0120211234",
        Country.TG to "22212345",
        Country.ML to "20212345",
        Country.BF to "20491234",
        Country.NE to "20201234",
        Country.CM to "222123456",
        Country.GA to "01441234",
        Country.CG to "222123456",
        Country.GN to "30241234",
        Country.CD to "1234567",
    )

    @Test
    fun `mobile patterns match a sample number`() {
        assertEquals(Country.entries.toSet(), mobileSamples.keys)
        mobileSamples.forEach { (country, samples) ->
            samples.forEach {
                assertTrue(country.mobilePattern.matches(it), "${country.iso} $it")
                assertEquals(country.nationalLength, it.length, "${country.iso} $it length")
            }
        }
    }

    @Test
    fun `mobile patterns reject landline examples`() {
        assertEquals(Country.entries.toSet(), landlineSamples.keys)
        landlineSamples.forEach { (country, sample) ->
            assertFalse(country.mobilePattern.matches(sample), "${country.iso} $sample")
        }
    }

    @Test
    fun `example national number is a valid mobile for its country`() {
        Country.entries.forEach {
            val digits = it.exampleNational.filter { c -> c.isDigit() }
            assertEquals(it.nationalLength, digits.length, "${it.iso} ${it.exampleNational} length")
            assertTrue(isValidMobile(digits, it), "${it.iso} ${it.exampleNational}")
        }
    }
}
