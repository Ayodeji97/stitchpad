package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.sharing.normalisePhone

object DuplicateMatcher {
    private val titles = setOf("mr", "mrs", "miss", "madam", "mama", "alhaji", "chief")
    private const val MIN_PHONE_SUFFIX = 7

    /** Base letter to the precomposed accented letters that fold to it (combining marks are dropped separately). */
    private val accentGroups = mapOf(
        'a' to "àáâãäåāăą",
        'c' to "çćĉċč",
        'd' to "ďđ",
        'e' to "èéêëēĕėęěẹẻẽếềểễệ",
        'g' to "ĝğġģ",
        'h' to "ĥħ",
        'i' to "ìíîïĩīĭįıỉị",
        'j' to "ĵ",
        'k' to "ķ",
        'l' to "ĺļľł",
        'n' to "ñńņň",
        'o' to "òóôõöøōŏőọỏốồổỗộớờởỡợ",
        'r' to "ŕŗř",
        's' to "śŝşšṣ",
        't' to "ţťŧ",
        'u' to "ùúûüũūŭůűųụủứừửữự",
        'w' to "ŵ",
        'y' to "ýÿŷỳỵỷỹ",
        'z' to "źżž",
    )

    private val foldMap: Map<Char, Char> = buildMap {
        accentGroups.forEach { (base, accented) -> accented.forEach { put(it, base) } }
    }

    /** Lowercase, strip accents/punctuation, collapse spaces, drop titles (mr/mrs/miss/madam/mama/alhaji/chief). */
    fun normaliseName(name: String): String {
        val folded = buildString {
            for (c in name.lowercase()) {
                val code = c.code
                when {
                    code in 0x0300..0x036F -> Unit // combining marks
                    else -> {
                        val base = foldMap[c] ?: c
                        append(if (base.isLetterOrDigit()) base else ' ')
                    }
                }
            }
        }
        return folded.split(' ').filter { it.isNotEmpty() && it !in titles }.joinToString(" ")
    }

    fun findExisting(card: ReviewCard, customers: List<Customer>, country: Country): Customer? {
        val cardPhone = card.phone?.let { normalisePhone(it, country) }.orEmpty()
        val byPhone = if (cardPhone.isBlank()) {
            null
        } else {
            customers.firstOrNull { normalisePhone(it.phone, country) == cardPhone }
        }
        if (byPhone != null) return byPhone
        val name = normaliseName(card.name)
        return if (name.isBlank()) null else customers.firstOrNull { normaliseName(it.name) == name }
    }

    /**
     * Groups of card ids (size >= 2) that look like the same person: equal non-blank normalised
     * name and no conflicting phone. Singletons are omitted. Phones are compared digits-only by
     * suffix (no country available here), so "0803..." and "234803..." do not conflict.
     */
    fun groupWithinImport(cards: List<ReviewCard>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        cards.filter { normaliseName(it.name).isNotBlank() }
            .groupBy { normaliseName(it.name) }
            .values
            .forEach { sameName ->
                val clusters = mutableListOf<MutableList<ReviewCard>>()
                for (card in sameName) {
                    val home = clusters.firstOrNull { cluster -> cluster.none { phonesConflict(it.phone, card.phone) } }
                    if (home != null) home.add(card) else clusters.add(mutableListOf(card))
                }
                clusters.filter { it.size > 1 }.forEach { result.add(it.map { c -> c.id }) }
            }
        return result
    }

    private fun phonesConflict(a: String?, b: String?): Boolean {
        val da = a.orEmpty().filter { it.isDigit() }
        val db = b.orEmpty().filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        val (short, long) = if (da.length <= db.length) da to db else db to da
        return !(short.length >= MIN_PHONE_SUFFIX && long.endsWith(short))
    }
}
