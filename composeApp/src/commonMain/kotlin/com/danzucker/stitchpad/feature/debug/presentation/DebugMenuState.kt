package com.danzucker.stitchpad.feature.debug.presentation

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
)

data class SmartUsageDialogState(
    val countInput: String = "5",
    val bonusInput: String = "0",
) {
    val count: Int? get() = countInput.toIntOrNull()
    val bonus: Int? get() = bonusInput.toIntOrNull()

    val isValid: Boolean
        get() {
            val c = count ?: return false
            val b = bonus ?: return false
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
