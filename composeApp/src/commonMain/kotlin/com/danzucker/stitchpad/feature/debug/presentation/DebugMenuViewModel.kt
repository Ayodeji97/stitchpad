package com.danzucker.stitchpad.feature.debug.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.debug.AnalyticsDebugActions
import com.danzucker.stitchpad.core.debug.DebugActionResult
import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DebugTestAccounts
import com.danzucker.stitchpad.core.debug.DigestDebugActions
import com.danzucker.stitchpad.core.debug.DigestSendResult
import com.danzucker.stitchpad.core.debug.FreemiumDebugActions
import com.danzucker.stitchpad.core.debug.ReferralAdminDebugActions
import com.danzucker.stitchpad.core.debug.ReferralDebugActions
import com.danzucker.stitchpad.core.debug.ReminderDebugActions
import com.danzucker.stitchpad.core.debug.ReminderSendResult
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.core.debug.SessionActionResult
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A cohesive debug-only menu VM: one runX() per debug action. Splitting it would
// just scatter trivial one-liner handlers across files for no readability gain.
@Suppress("TooManyFunctions", "LongParameterList")
class DebugMenuViewModel(
    private val seeder: DebugSeeder,
    private val sessionActions: DebugSessionActions,
    private val freemiumActions: FreemiumDebugActions,
    private val digestActions: DigestDebugActions,
    private val reminderActions: ReminderDebugActions,
    private val analyticsActions: AnalyticsDebugActions,
    private val referralActions: ReferralDebugActions,
    private val referralAdminActions: ReferralAdminDebugActions,
    private val now: () -> Long,
    private val testAccountsConfigured: Boolean = DebugTestAccounts.isConfigured,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DebugMenuState(testAccountsConfigured = testAccountsConfigured)
    )
    val state = _state.asStateFlow()

    private val _events = Channel<DebugMenuEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: DebugMenuAction) {
        if (handleFreemiumAction(action)) return
        if (handleReferralAction(action)) return
        when (action) {
            DebugMenuAction.OnBackClick -> emit(DebugMenuEvent.NavigateBack)
            DebugMenuAction.OnSeedBrandNewClick -> runSeed(DebugScenario.BrandNew) { seeder.seedBrandNew() }
            DebugMenuAction.OnSeedActiveWorkshopClick -> runSeed(
                DebugScenario.ActiveWorkshop
            ) { seeder.seedActiveWorkshop() }
            DebugMenuAction.OnSeedAllReconnectClick -> runSeed(DebugScenario.AllReconnect) { seeder.seedAllReconnect() }
            DebugMenuAction.OnBulkSeedClick -> _state.update { it.copy(bulkSeed = BulkSeedDialogState()) }
            DebugMenuAction.OnBulkSeedDismiss -> _state.update { it.copy(bulkSeed = null) }
            is DebugMenuAction.OnBulkSeedTotalChange -> _state.update {
                it.copy(bulkSeed = it.bulkSeed?.copy(totalInput = action.value.filter(Char::isDigit)))
            }
            is DebugMenuAction.OnBulkSeedMeasurementsChange -> _state.update {
                it.copy(bulkSeed = it.bulkSeed?.copy(measurementsInput = action.value.filter(Char::isDigit)))
            }
            is DebugMenuAction.OnBulkSeedOrdersChange -> _state.update {
                it.copy(bulkSeed = it.bulkSeed?.copy(ordersInput = action.value.filter(Char::isDigit)))
            }
            DebugMenuAction.OnBulkSeedConfirm -> runBulkSeed()
            DebugMenuAction.OnClearActiveScenarioClick -> {
                _state.update { it.copy(activeScenario = null) }
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Active state cleared")))
            }
            DebugMenuAction.OnResetOnboardingClick -> runJob {
                sessionActions.resetOnboardingFlags()
                emit(DebugMenuEvent.NavigateToSplash)
            }
            DebugMenuAction.OnResetCommunityBannerClick -> runJob {
                sessionActions.clearCommunityBannerDismissed()
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Community banner reset")))
            }
            DebugMenuAction.OnResetCelebrationsClick -> runJob {
                sessionActions.resetCelebrations()
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Celebrations reset")))
            }
            DebugMenuAction.OnSignOutClick -> runSignOut()
            DebugMenuAction.OnSwitchToFolaClick -> runJob {
                handleSwitch(
                    sessionActions.switchAccount(
                        DebugTestAccounts.FOLA_EMAIL,
                        DebugTestAccounts.FOLA_PASSWORD,
                    )
                )
            }
            DebugMenuAction.OnSwitchToGabbyClick -> runJob {
                handleSwitch(
                    sessionActions.switchAccount(
                        DebugTestAccounts.GABBY_EMAIL,
                        DebugTestAccounts.GABBY_PASSWORD,
                    )
                )
            }
            DebugMenuAction.OnWipeDataClick -> runWipe()
            DebugMenuAction.OnSendDailyDigestClick -> runSendDigest()
            DebugMenuAction.OnSendTestPushClick -> runSendTestPush()
            DebugMenuAction.OnSendRenewalReminderClick -> runSendReminder()
            is DebugMenuAction.ToggleAnalyticsCollection -> {
                analyticsActions.setAnalyticsEnabled(action.enabled)
                _state.update { it.copy(analyticsCollectionEnabled = action.enabled) }
            }
            else -> Unit // freemium branch handled above
        }
    }

    private fun runSignOut() = runJob {
        val r = sessionActions.signOut()
        if (r is SessionActionResult.Success) {
            emit(DebugMenuEvent.NavigateToLogin)
        } else {
            emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Sign-out failed")))
        }
    }

    @Suppress("ReturnCount")
    private fun runSetSmartUsage() {
        val dialog = _state.value.smartUsage ?: return
        if (!dialog.isValid) return
        val count = dialog.count ?: return
        val bonusUsed = dialog.bonusUsed ?: return
        _state.update { it.copy(smartUsage = null) }
        // Server's truth field is REMAINING balance, not used. Convert here so
        // the dialog can keep its tester-friendly "drafts used" semantic
        // (matches the count field's semantic).
        val bonusBalance = (FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT - bonusUsed)
            .coerceIn(0, FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT)
        runJob {
            val r = freemiumActions.setSmartUsage(
                monthlyCount = count,
                bonusBalance = bonusBalance,
                nowMs = now(),
            )
            val message = when (r) {
                DebugActionResult.Success ->
                    UiText.DynamicString("Smart usage: count=$count, bonusUsed=$bonusUsed")
                is DebugActionResult.Failure ->
                    UiText.DynamicString("Set Smart usage failed: ${r.reason}")
            }
            emit(DebugMenuEvent.ShowSnackbar(message))
        }
    }

    @Suppress("ReturnCount")
    private fun runSetWelcomeDaysLeft() {
        val dialog = _state.value.welcomeDaysLeft ?: return
        if (!dialog.isValid) return
        val days = dialog.days ?: return
        _state.update { it.copy(welcomeDaysLeft = null) }
        runJob {
            val r = freemiumActions.setWelcomeDaysLeft(daysLeft = days, nowMs = now())
            val message = when (r) {
                DebugActionResult.Success -> UiText.DynamicString("Welcome window: $days days left")
                is DebugActionResult.Failure -> UiText.DynamicString("Set days left failed: ${r.reason}")
            }
            emit(DebugMenuEvent.ShowSnackbar(message))
        }
    }

    @Suppress("ReturnCount")
    private fun runBulkSeed() {
        val dialog = _state.value.bulkSeed ?: return
        if (!dialog.isValid) return
        val total = dialog.total ?: return
        val measurements = dialog.measurements ?: return
        val orders = dialog.orders ?: return
        _state.update { it.copy(bulkSeed = null) }
        runJob {
            val r = seeder.seedBulkCustomers(total, measurements, orders)
            val message = when (r) {
                SeedResult.Success -> UiText.DynamicString("Seeded $total customers")
                is SeedResult.Failure -> UiText.DynamicString("Bulk seed failed: ${r.reason}")
            }
            emit(DebugMenuEvent.ShowSnackbar(message))
        }
    }

    private fun runWipe() = runJob {
        val message = when (val r = seeder.wipeAllData()) {
            SeedResult.Success -> {
                _state.update { it.copy(activeScenario = null) }
                UiText.DynamicString("Data wiped")
            }
            is SeedResult.Failure -> UiText.DynamicString("Wipe failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    private fun runSendDigest() = runJob {
        val message = when (val r = digestActions.sendNow()) {
            is DigestSendResult.Sent -> {
                val channels = buildList {
                    if (r.emailSent) add("email")
                    if (r.pushSent) add("push")
                }
                UiText.DynamicString("Digest sent — ${channels.joinToString(" + ")}")
            }
            DigestSendResult.Empty -> UiText.DynamicString("Nothing actionable — digest suppressed")
            DigestSendResult.Disabled ->
                UiText.DynamicString("Nothing sent — email/push may be off, no token, or no order. Check Settings.")
            is DigestSendResult.Failure -> UiText.DynamicString("Digest failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    private fun runSendReminder() = runJob {
        val message = when (val r = reminderActions.sendNow()) {
            is ReminderSendResult.Sent -> UiText.DynamicString("Renewal reminder sent to ${r.to}")
            is ReminderSendResult.Failure -> UiText.DynamicString("Reminder failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    private fun runSendTestPush() = runJob {
        val message = when (val r = digestActions.sendNow()) {
            is DigestSendResult.Sent ->
                if (r.pushSent) {
                    UiText.DynamicString("Test push sent")
                } else {
                    UiText.DynamicString("No push sent (push off or no token) — email sent instead")
                }
            DigestSendResult.Empty -> UiText.DynamicString("Nothing actionable — push suppressed (no eligible orders)")
            DigestSendResult.Disabled ->
                UiText.DynamicString("No push sent (check permission / opt-out / actionable order)")
            is DigestSendResult.Failure -> UiText.DynamicString("Test push failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleFreemiumAction(action: DebugMenuAction): Boolean {
        when (action) {
            DebugMenuAction.OnSetTierFreeClick -> runFreemium("Tier: Free") {
                freemiumActions.setTier(SubscriptionTier.FREE)
            }
            DebugMenuAction.OnSetTierProClick -> runFreemium("Tier: Pro") {
                freemiumActions.setTier(SubscriptionTier.PRO)
            }
            DebugMenuAction.OnSetTierAtelierClick -> runFreemium("Tier: Atelier") {
                freemiumActions.setTier(SubscriptionTier.ATELIER)
            }
            DebugMenuAction.OnExpireWelcomeWindowClick -> runFreemium("Welcome window expired") {
                freemiumActions.expireWelcomeWindow(nowMs = now())
            }
            DebugMenuAction.OnResetWelcomeWindowClick -> runFreemium("Welcome window reset") {
                freemiumActions.resetWelcomeWindow()
            }
            DebugMenuAction.OnSetWelcomeDaysLeftClick -> _state.update {
                it.copy(welcomeDaysLeft = WelcomeDaysLeftDialogState())
            }
            DebugMenuAction.OnSetWelcomeDaysLeftDismiss -> _state.update {
                it.copy(welcomeDaysLeft = null)
            }
            is DebugMenuAction.OnSetWelcomeDaysLeftChange -> _state.update {
                it.copy(welcomeDaysLeft = it.welcomeDaysLeft?.copy(daysInput = action.value.filter(Char::isDigit)))
            }
            DebugMenuAction.OnSetWelcomeDaysLeftConfirm -> runSetWelcomeDaysLeft()
            DebugMenuAction.OnDrainBonusCoinsClick -> runFreemium("Bonus coins drained") {
                freemiumActions.setBonusCoins(0)
            }
            DebugMenuAction.OnRefillBonusCoinsClick -> runFreemium("Bonus coins refilled") {
                freemiumActions.setBonusCoins(FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT)
            }
            DebugMenuAction.OnResetSmartUsageClick -> runFreemium("Smart usage reset") {
                freemiumActions.resetSmartUsage()
            }
            DebugMenuAction.OnSetSmartUsageClick -> _state.update {
                it.copy(smartUsage = SmartUsageDialogState())
            }
            DebugMenuAction.OnSetSmartUsageDismiss -> _state.update {
                it.copy(smartUsage = null)
            }
            is DebugMenuAction.OnSetSmartUsageCountChange -> _state.update {
                it.copy(smartUsage = it.smartUsage?.copy(countInput = action.value.filter(Char::isDigit)))
            }
            is DebugMenuAction.OnSetSmartUsageBonusUsedChange -> _state.update {
                it.copy(smartUsage = it.smartUsage?.copy(bonusUsedInput = action.value.filter(Char::isDigit)))
            }
            DebugMenuAction.OnSetSmartUsageConfirm -> runSetSmartUsage()
            DebugMenuAction.OnReconcileSlotsClick -> runFreemium("Slots reconciled") {
                freemiumActions.reconcileSlots()
            }
            else -> return false
        }
        return true
    }

    private fun handleReferralAction(action: DebugMenuAction): Boolean {
        when (action) {
            DebugMenuAction.OnReferralAttributeClick -> _state.update {
                it.copy(referralAttribute = ReferralAttributeDialogState())
            }
            DebugMenuAction.OnReferralAttributeDismiss -> _state.update {
                it.copy(referralAttribute = null)
            }
            is DebugMenuAction.OnReferralAttributeCodeChange -> _state.update {
                it.copy(referralAttribute = it.referralAttribute?.copy(codeInput = action.value))
            }
            DebugMenuAction.OnReferralAttributeConfirm -> runAttributeReferral()
            DebugMenuAction.OnReferralSeedQualificationClick -> runResultAction(
                "Seeded workshop + 4 days — now run the grader (or wait for the nightly run)"
            ) { referralActions.seedQualification(now()) }
            DebugMenuAction.OnReferralResetCaptureClick -> runResultAction("Referral capture state reset") {
                referralActions.resetCaptureState()
            }
            DebugMenuAction.OnReferralRunGraderClick -> runResultAction("Grader ran — check the referral doc") {
                referralAdminActions.runGrader()
            }
            DebugMenuAction.OnReferralRunConfirmClick -> runResultAction("Confirm payouts ran") {
                referralAdminActions.runConfirmPayouts()
            }
            DebugMenuAction.OnReferralRunSweepClick -> runResultAction("Deleted-user sweep ran") {
                referralAdminActions.runSweep()
            }
            else -> return false
        }
        return true
    }

    @Suppress("ReturnCount")
    private fun runAttributeReferral() {
        val dialog = _state.value.referralAttribute ?: return
        if (!dialog.isValid) return
        val code = dialog.code
        _state.update { it.copy(referralAttribute = null) }
        runResultAction("Attributed with code $code") { referralActions.attributeWithCode(code) }
    }

    private fun runResultAction(successMessage: String, block: suspend () -> DebugActionResult) =
        runJob {
            val message = when (val r = block()) {
                DebugActionResult.Success -> UiText.DynamicString(successMessage)
                is DebugActionResult.Failure -> UiText.DynamicString("Failed: ${r.reason}")
            }
            emit(DebugMenuEvent.ShowSnackbar(message))
        }

    private fun runFreemium(successMessage: String, block: suspend () -> DebugActionResult) =
        runResultAction(successMessage, block)

    private fun runSeed(scenario: DebugScenario, block: suspend () -> SeedResult) = runJob {
        val r = block()
        val message = when (r) {
            SeedResult.Success -> {
                _state.update { it.copy(activeScenario = scenario) }
                UiText.DynamicString("Seed complete")
            }
            is SeedResult.Failure -> UiText.DynamicString("Seed failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    private fun runJob(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun handleSwitch(r: SessionActionResult) {
        when (r) {
            SessionActionResult.Success -> emit(DebugMenuEvent.NavigateToSplash)
            SessionActionResult.ConfigurationMissing ->
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Test creds not configured")))
            is SessionActionResult.Failure ->
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Switch failed: ${r.reason}")))
        }
    }

    private fun emit(event: DebugMenuEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
