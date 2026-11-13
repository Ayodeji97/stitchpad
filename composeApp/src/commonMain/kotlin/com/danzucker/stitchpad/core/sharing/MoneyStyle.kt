package com.danzucker.stitchpad.core.sharing

/**
 * Display-only number conventions for money, picked from the device / app
 * language (the same signal Compose resources use to pick `values-fr`).
 * Symbol placement is NOT part of the style — it stays per-currency.
 *
 * Never use this for amounts a user will edit: input fields keep their own
 * parsing/formatting so a French grouping space can never reach a parser.
 */
enum class MoneyStyle(
    internal val groupSeparator: Char,
    internal val decimalSeparator: Char,
    internal val thousandSuffix: String,
    internal val millionSuffix: String,
) {
    /** "₦100,000.50", compact "₦310K" / "₦1.2M". */
    ENGLISH(',', '.', "K", "M"),

    /**
     * "₦100 000,50" (U+202F narrow no-break space grouping, decimal comma),
     * compact "₦310 k" / "₦1,2 M" (U+00A0 before the suffix) — CLDR fr.
     */
    FRENCH('\u202F', ',', "\u00A0k", "\u00A0M"),
    ;

    companion object {
        fun forLanguage(languageCode: String): MoneyStyle =
            if (languageCode.startsWith("fr", ignoreCase = true)) FRENCH else ENGLISH

        /** Style for the current device / app language. */
        fun current(): MoneyStyle = forLanguage(deviceLanguageCode())
    }
}
