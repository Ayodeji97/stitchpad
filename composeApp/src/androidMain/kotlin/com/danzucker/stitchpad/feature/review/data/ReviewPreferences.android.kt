package com.danzucker.stitchpad.feature.review.data

import android.content.Context
import android.content.SharedPreferences
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

actual class ReviewPreferences(context: Context) : ReviewPreferencesStore {
    // Same store file OnboardingPreferences uses — distinct key namespace ("review_").
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stitchpad_prefs", Context.MODE_PRIVATE)

    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (prefs.getLong(KEY_INSTALL_MILLIS, 0L) == 0L) {
            prefs.edit().putLong(KEY_INSTALL_MILLIS, nowMillis).apply()
        }
    }

    override suspend fun recordOpenDay(epochDay: Long) {
        val last = prefs.getLong(KEY_LAST_OPEN_DAY, Long.MIN_VALUE)
        if (epochDay != last) {
            prefs.edit()
                .putLong(KEY_LAST_OPEN_DAY, epochDay)
                .putInt(KEY_DISTINCT_OPEN_DAYS, prefs.getInt(KEY_DISTINCT_OPEN_DAYS, 0) + 1)
                .apply()
        }
    }

    override suspend fun incrementOrdersCreated(userId: String) {
        val key = ordersKey(userId)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        prefs.edit()
            .putLong(lastPromptKey(userId), nowMillis)
            .putString(outcomeKey(userId), outcome.name)
            .apply()
    }

    override suspend fun loadSignals(userId: String): ReviewSignals = ReviewSignals(
        installedAtMillis = prefs.getLong(KEY_INSTALL_MILLIS, 0L),
        distinctOpenDays = prefs.getInt(KEY_DISTINCT_OPEN_DAYS, 0),
        ordersCreated = prefs.getInt(ordersKey(userId), 0),
        lastPromptAtMillis = prefs.getLong(lastPromptKey(userId), 0L),
        lastOutcome = prefs.getString(outcomeKey(userId), null)
            ?.let { runCatching { ReviewOutcome.valueOf(it) }.getOrNull() }
            ?: ReviewOutcome.NONE,
    )

    override suspend fun resetForDebug() {
        val editor = prefs.edit()
            .remove(KEY_INSTALL_MILLIS)
            .remove(KEY_LAST_OPEN_DAY)
            .remove(KEY_DISTINCT_OPEN_DAYS)
        prefs.all.keys
            .filter {
                it.startsWith(PREFIX_ORDERS) ||
                    it.startsWith(PREFIX_LAST_PROMPT) ||
                    it.startsWith(PREFIX_OUTCOME)
            }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    private fun ordersKey(userId: String) = "$PREFIX_ORDERS$userId"
    private fun lastPromptKey(userId: String) = "$PREFIX_LAST_PROMPT$userId"
    private fun outcomeKey(userId: String) = "$PREFIX_OUTCOME$userId"

    companion object {
        private const val KEY_INSTALL_MILLIS = "review_install_millis"
        private const val KEY_LAST_OPEN_DAY = "review_last_open_day"
        private const val KEY_DISTINCT_OPEN_DAYS = "review_distinct_open_days"
        private const val PREFIX_ORDERS = "review_orders_created_"
        private const val PREFIX_LAST_PROMPT = "review_last_prompt_millis_"
        private const val PREFIX_OUTCOME = "review_last_outcome_"
    }
}
