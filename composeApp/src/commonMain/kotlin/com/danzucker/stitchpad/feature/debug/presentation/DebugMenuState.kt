package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator

enum class DebugScenario {
    BrandNew,
    ActiveWorkshop,
    AllReconnect,
}

data class DebugMenuState(
    val isWorking: Boolean = false,
    val testAccountsConfigured: Boolean = false,
    val activeScenario: DebugScenario? = null,
    val bulkSeed: BulkSeedDialogState? = null,
    val smartUsage: SmartUsageDialogState? = null,
    val welcomeDaysLeft: WelcomeDaysLeftDialogState? = null,
    // Optimistic / session-scoped: the GitLive SDK persists the real value internally
    // but exposes no getter, so we default to true (enabled) on every cold launch and
    // track what the user toggled this session. SDK persistence governs actual behavior.
    val analyticsCollectionEnabled: Boolean = true,
)

data class WelcomeDaysLeftDialogState(
    val daysInput: String = EntitlementsCalculator.WELCOME_WINDOW_DAYS.toString(),
) {
    val days: Int? get() = daysInput.toIntOrNull()
    val isValid: Boolean
        get() = days?.let { it in 0..EntitlementsCalculator.WELCOME_WINDOW_DAYS } ?: false
}

data class SmartUsageDialogState(
    val countInput: String = "5",
    // "Drafts USED out of the 30-coin welcome bonus" — matches the count field's
    // used-flavored semantic so testers don't have to flip-flop interpretations.
    // The ViewModel converts this to bonusBalance = (30 - bonusUsed) before
    // writing to Firestore (the server's truth field is "remaining", not "used").
    val bonusUsedInput: String = "0",
) {
    val count: Int? get() = countInput.toIntOrNull()
    val bonusUsed: Int? get() = bonusUsedInput.toIntOrNull()

    val isValid: Boolean
        get() {
            val c = count ?: return false
            val b = bonusUsed ?: return false
            return c >= 0 && b >= 0
        }
}

data class BulkSeedDialogState(
    val totalInput: String = "30",
    val measurementsInput: String = "10",
    val ordersInput: String = "10",
) {
    val total: Int? get() = totalInput.toIntOrNull()
    val measurements: Int? get() = measurementsInput.toIntOrNull()
    val orders: Int? get() = ordersInput.toIntOrNull()

    val isValid: Boolean
        get() {
            val t = total ?: return false
            val m = measurements ?: return false
            val o = orders ?: return false
            return t > 0 && t <= MAX_BULK_CUSTOMERS && m in 0..t && o in 0..t
        }

    companion object {
        const val MAX_BULK_CUSTOMERS: Int = 200
    }
}
