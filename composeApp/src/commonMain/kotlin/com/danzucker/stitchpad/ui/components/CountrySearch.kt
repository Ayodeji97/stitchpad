package com.danzucker.stitchpad.ui.components

import com.danzucker.stitchpad.core.domain.model.Country

/** Local search only: names, common aliases, ISO codes and formatted international dial codes. */
internal fun searchCountries(query: String, names: Map<Country, String>): List<Country> {
    val normalized = normalizeCountrySearch(query)
    if (normalized.isEmpty()) return Country.entries.toList()
    val dial = query.trim().filter { it.isDigit() }.removePrefix("00")
    val isDialQuery = query.trim().all { it.isDigit() || it in "+ -()" } && dial.isNotEmpty()
    return if (isDialQuery) {
        Country.entries.filter { it.dialCode.startsWith(dial) }
            .sortedBy { if (it.dialCode == dial) 0 else 1 }
    } else {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        Country.entries.mapNotNull { country ->
            val name = normalizeCountrySearch(names[country].orEmpty())
            val searchable = "$name ${country.iso.lowercase()} ${countrySearchAliases(country)}"
            val score = when {
                normalized == country.iso.lowercase() || normalized == name -> 0
                name.startsWith(normalized) -> 1
                tokens.all { token -> searchable.split(' ').any { it.startsWith(token) } } -> 2
                tokens.all { token -> searchable.contains(token) } -> 3
                else -> null
            }
            score?.let { country to it }
        }.sortedBy { it.second }.map { it.first }
    }
}

private fun normalizeCountrySearch(value: String): String = buildString {
    value.lowercase().forEach { character ->
        append(
            when (character) {
                in "àáâäãå" -> 'a'
                in "èéêë" -> 'e'
                in "ìíîï" -> 'i'
                in "òóôöõ" -> 'o'
                in "ùúûü" -> 'u'
                'ç' -> 'c'
                'ñ' -> 'n'
                '\u0300', '\u0301', '\u0302', '\u0308' -> return@forEach
                else -> if (character.isLetterOrDigit()) character else ' '
            },
        )
    }
}.trim().split(Regex("\\s+")).joinToString(" ")

/** Search keywords, never rendered as UI copy. */
private fun countrySearchAliases(country: Country): String = when (country) {
    Country.CI -> "ivory coast cote ivoire"
    Country.CD -> "drc dr congo democratic republic congo kinshasa rdc"
    Country.CG -> "republic congo brazzaville"
    Country.GN -> "guinea guinee"
    Country.CM -> "cameroon cameroun"
    Country.BJ -> "benin"
    else -> ""
}
