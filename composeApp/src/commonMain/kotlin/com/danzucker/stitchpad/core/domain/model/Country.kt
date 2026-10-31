package com.danzucker.stitchpad.core.domain.model

enum class AndroidBilling { PAYSTACK, PLAY }

/**
 * Wave-1 markets. [mobilePattern] matches the national number only (no dial code, no trunk
 * prefix), checked against libphonenumber's PhoneNumberMetadata.xml mobile patterns.
 * [trunkPrefix] is the national trunk prefix dropped when dialling internationally; it is null
 * where the leading 0 (CI, BJ, GA, CG) is part of the number itself. [exampleNational] reuses the
 * CountryTest mobile samples (NG keeps its existing placeholder), spaced as usually written locally.
 */
// One flat row per market keeps the registry auditable at a glance; a builder would hide it.
@Suppress("LongParameterList")
enum class Country(
    val iso: String,
    val dialCode: String,
    val trunkPrefix: String?,
    val nationalLength: Int,
    val mobilePattern: Regex,
    val currency: Currency,
    val timeZones: List<String>,
    val defaultLanguage: String,
    val androidBilling: AndroidBilling,
    /** A formatted local mobile example (no dial code), shown as the phone-field placeholder. */
    val exampleNational: String,
) {
    NG(
        "NG",
        "234",
        "0",
        10,
        Regex("^(?:7[0-2]|[89][01])\\d{8}$"),
        Currency.NGN,
        listOf("Africa/Lagos"),
        "en",
        AndroidBilling.PAYSTACK,
        "801 234 5678",
    ),
    GH(
        "GH",
        "233",
        "0",
        9,
        Regex("^[25]\\d{8}$"),
        Currency.GHS,
        listOf("Africa/Accra"),
        "en",
        AndroidBilling.PLAY,
        "24 412 3456",
    ),
    KE(
        "KE",
        "254",
        "0",
        9,
        Regex("^(?:7\\d|1[0-4])\\d{7}$"),
        Currency.KES,
        listOf("Africa/Nairobi"),
        "en",
        AndroidBilling.PLAY,
        "712 345 678",
    ),
    CI(
        "CI",
        "225",
        null,
        10,
        Regex("^0[157]\\d{8}$"),
        Currency.XOF,
        listOf("Africa/Abidjan"),
        "fr",
        AndroidBilling.PLAY,
        "07 01 23 45 67",
    ),
    SN(
        "SN",
        "221",
        null,
        9,
        Regex("^7[0-25-9]\\d{7}$"),
        Currency.XOF,
        listOf("Africa/Dakar"),
        "fr",
        AndroidBilling.PLAY,
        "77 123 45 67",
    ),
    BJ(
        "BJ",
        "229",
        null,
        10,
        Regex("^01(?:2[5-9]|[4-69]\\d)\\d{6}$"),
        Currency.XOF,
        listOf("Africa/Porto-Novo"),
        "fr",
        AndroidBilling.PLAY,
        "01 90 12 34 56",
    ),
    TG(
        "TG",
        "228",
        null,
        8,
        Regex("^(?:7[0-389]|9[0-36-9])\\d{6}$"),
        Currency.XOF,
        listOf("Africa/Lome"),
        "fr",
        AndroidBilling.PLAY,
        "90 12 34 56",
    ),
    ML(
        "ML",
        "223",
        null,
        8,
        // Deliberately omits libphonenumber's ML four-digit-prefix 2xxx mobiles,
        // 2(?:0(?:01|79)|17\d)\d{4}: they overlap ML's landline-style 2xxx numbers.
        Regex("^(?:5[0-3]|[679]\\d|8[2-59])\\d{6}$"),
        Currency.XOF,
        listOf("Africa/Bamako"),
        "fr",
        AndroidBilling.PLAY,
        "76 12 34 56",
    ),
    BF(
        "BF",
        "226",
        null,
        8,
        Regex("^(?:0[1-7]|4[4-6]|5[0-8]|[67]\\d)\\d{6}$"),
        Currency.XOF,
        listOf("Africa/Ouagadougou"),
        "fr",
        AndroidBilling.PLAY,
        "70 12 34 56",
    ),
    NE(
        "NE",
        "227",
        null,
        8,
        Regex("^(?:23|7[0467]|[89]\\d)\\d{6}$"),
        Currency.XOF,
        listOf("Africa/Niamey"),
        "fr",
        AndroidBilling.PLAY,
        "93 12 34 56",
    ),
    CM(
        "CM",
        "237",
        null,
        9,
        Regex("^(?:24[23]|6(?:[25-9]\\d|4[0-2]))\\d{6}$"),
        Currency.XAF,
        listOf("Africa/Douala"),
        "fr",
        AndroidBilling.PLAY,
        "6 71 23 45 67",
    ),
    GA(
        "GA",
        "241",
        null,
        8,
        Regex("^(?:0[2-7]|6\\d|7[467])\\d{6}$"),
        Currency.XAF,
        listOf("Africa/Libreville"),
        "fr",
        AndroidBilling.PLAY,
        "06 12 34 56",
    ),
    CG(
        "CG",
        "242",
        null,
        9,
        Regex("^(?:026(?:1[0-5]|6[6-9])\\d{4}|0(?:[14-6]\\d\\d|2(?:40|5[5-8]|6[07-9]))\\d{5})$"),
        Currency.XAF,
        listOf("Africa/Brazzaville"),
        "fr",
        AndroidBilling.PLAY,
        "06 123 4567",
    ),
    GN(
        "GN",
        "224",
        null,
        9,
        Regex("^6[0-356]\\d{7}$"),
        Currency.GNF,
        listOf("Africa/Conakry"),
        "fr",
        AndroidBilling.PLAY,
        "621 23 45 67",
    ),
    CD(
        "CD",
        "243",
        "0",
        9,
        // Deliberately omits libphonenumber's CD 88\d{5} mobile range: 7-digit numbers that
        // don't fit the 9-digit nationalLength used here.
        Regex("^(?:8[0-79]|9[016-9])\\d{7}$"),
        Currency.CDF,
        listOf("Africa/Kinshasa", "Africa/Lubumbashi"),
        "fr",
        AndroidBilling.PLAY,
        "812 345 678",
    ),
    ;

    companion object {
        val DEFAULT = NG

        fun fromIso(iso: String?): Country? =
            entries.firstOrNull { it.iso.equals(iso, ignoreCase = true) }
    }
}

val Country.defaultTimeZone: String get() = timeZones.first()
