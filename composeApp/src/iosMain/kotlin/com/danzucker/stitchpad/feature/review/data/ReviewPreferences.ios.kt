package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals
import platform.Foundation.NSUserDefaults

actual class ReviewPreferences : ReviewPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (defaults.integerForKey(KEY_INSTALL_MILLIS) == 0L) {
            defaults.setInteger(nowMillis, forKey = KEY_INSTALL_MILLIS)
        }
    }

    override suspend fun recordOpenDay(epochDay: Long) {
        // Absent key reads back as 0; guard with an explicit "seen" flag so epochDay 0 counts once.
        val seen = defaults.boolForKey(KEY_HAS_OPEN_DAY)
        val last = defaults.integerForKey(KEY_LAST_OPEN_DAY)
        if (!seen || epochDay != last) {
            defaults.setBool(true, forKey = KEY_HAS_OPEN_DAY)
            defaults.setInteger(epochDay, forKey = KEY_LAST_OPEN_DAY)
            defaults.setInteger(defaults.integerForKey(KEY_DISTINCT_OPEN_DAYS) + 1, forKey = KEY_DISTINCT_OPEN_DAYS)
        }
    }

    override suspend fun incrementOrdersCreated(userId: String) {
        val key = ordersKey(userId)
        defaults.setInteger(defaults.integerForKey(key) + 1, forKey = key)
    }

    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        defaults.setInteger(nowMillis, forKey = lastPromptKey(userId))
        defaults.setObject(outcome.name, forKey = outcomeKey(userId))
    }

    override suspend fun loadSignals(userId: String): ReviewSignals = ReviewSignals(
        installedAtMillis = defaults.integerForKey(KEY_INSTALL_MILLIS),
        distinctOpenDays = defaults.integerForKey(KEY_DISTINCT_OPEN_DAYS).toInt(),
        ordersCreated = defaults.integerForKey(ordersKey(userId)).toInt(),
        lastPromptAtMillis = defaults.integerForKey(lastPromptKey(userId)),
        lastOutcome = (defaults.stringForKey(outcomeKey(userId)))
            ?.let { runCatching { ReviewOutcome.valueOf(it) }.getOrNull() }
            ?: ReviewOutcome.NONE,
    )

    override suspend fun resetForDebug() {
        listOf(KEY_INSTALL_MILLIS, KEY_HAS_OPEN_DAY, KEY_LAST_OPEN_DAY, KEY_DISTINCT_OPEN_DAYS)
            .forEach { defaults.removeObjectForKey(it) }
        val perUserPrefixes = listOf(PREFIX_ORDERS, PREFIX_LAST_PROMPT, PREFIX_OUTCOME)
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { key -> perUserPrefixes.any { key.startsWith(it) } }
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun ordersKey(userId: String) = "$PREFIX_ORDERS$userId"
    private fun lastPromptKey(userId: String) = "$PREFIX_LAST_PROMPT$userId"
    private fun outcomeKey(userId: String) = "$PREFIX_OUTCOME$userId"

    companion object {
        private const val KEY_INSTALL_MILLIS = "review_install_millis"
        private const val KEY_HAS_OPEN_DAY = "review_has_open_day"
        private const val KEY_LAST_OPEN_DAY = "review_last_open_day"
        private const val KEY_DISTINCT_OPEN_DAYS = "review_distinct_open_days"
        private const val PREFIX_ORDERS = "review_orders_created_"
        private const val PREFIX_LAST_PROMPT = "review_last_prompt_millis_"
        private const val PREFIX_OUTCOME = "review_last_outcome_"
    }
}
