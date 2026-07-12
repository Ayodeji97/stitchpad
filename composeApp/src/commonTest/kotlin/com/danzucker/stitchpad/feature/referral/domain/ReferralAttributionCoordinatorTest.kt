package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeReferralRepository(
    var result: Result<AttributionOutcome, ReferralError> =
        Result.Success(AttributionOutcome(alreadyAttributed = false, marketerId = "m1")),
) : ReferralRepository {
    val calls = mutableListOf<Call>()
    data class Call(val code: String, val deviceHash: String, val source: ReferralSource)

    override suspend fun recordAttribution(
        code: String,
        deviceHash: String,
        source: ReferralSource,
    ): Result<AttributionOutcome, ReferralError> {
        calls += Call(code, deviceHash, source)
        return result
    }
}

private class FakeReferralPreferences(
    private var attributed: Boolean = false,
    private var checkedReferrer: Boolean = false,
    private val deviceId: String = "device-uuid",
) : ReferralPreferencesStore {
    override suspend fun getOrCreateDeviceId(): String = deviceId
    override suspend fun hasAttributed(): Boolean = attributed
    override suspend fun setAttributed() { attributed = true }
    override suspend fun hasCheckedReferrer(): Boolean = checkedReferrer
    override suspend fun setReferrerChecked() { checkedReferrer = true }
    override suspend fun resetForDebug() { attributed = false; checkedReferrer = false }

    fun attributedNow() = attributed
    fun checkedNow() = checkedReferrer
}

private class FakeInstallReferrerReader(private val referrer: String?) : InstallReferrerReader {
    var reads = 0
    override suspend fun readReferrer(): String? { reads++; return referrer }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReferralAttributionCoordinatorTest {

    private fun coordinator(
        scope: CoroutineScope,
        repo: FakeReferralRepository = FakeReferralRepository(),
        prefs: FakeReferralPreferences = FakeReferralPreferences(),
        reader: FakeInstallReferrerReader = FakeInstallReferrerReader(null),
        holder: PendingDeepLinkHolder = PendingDeepLinkHolder(),
        uidFlow: Flow<String?> = flowOf(null),
    ) = ReferralAttributionCoordinator(repo, prefs, reader, holder, scope, uidFlow)

    // ── attributeOnce: code resolution priority ──────────────────────────────

    @Test
    fun manualCode_wins_overPending_andTagsManual() = runTest {
        val repo = FakeReferralRepository()
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, repo = repo, holder = holder)

        c.attributeOnce(manualCode = " manual-01 ")

        assertEquals(1, repo.calls.size)
        assertEquals("MANUAL01", repo.calls[0].code)
        assertEquals(ReferralSource.MANUAL, repo.calls[0].source)
    }

    @Test
    fun manualPath_doesNotConsumePendingCode() = runTest {
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, holder = holder)

        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals("PENDING1", holder.consumeReferralCode())
    }

    @Test
    fun pendingCode_used_whenNoManual_tagsInstallReferrer() = runTest {
        val repo = FakeReferralRepository()
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, repo = repo, holder = holder)

        c.attributeOnce(manualCode = null)

        assertEquals(1, repo.calls.size)
        assertEquals("PENDING1", repo.calls[0].code)
        assertEquals(ReferralSource.INSTALL_REFERRER, repo.calls[0].source)
        assertEquals("device-uuid", repo.calls[0].deviceHash)
    }

    @Test
    fun concurrentTriggers_serialize_andManualAlwaysWins() = runTest {
        // Both triggers fire — the auth-state collector AND a signup submit — while a
        // captured code sits in the holder. The Mutex must serialize them to a single
        // attribution, and the manually-typed code must win regardless of which runs first.
        val repo = FakeReferralRepository()
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, repo = repo, holder = holder, uidFlow = flowOf("uid-1"))

        c.submitPendingAttribution(" manual-01 ") // stashes the manual code synchronously
        c.start() // auth-state collector → attributeOnce(null)
        advanceUntilIdle()

        assertEquals(1, repo.calls.size) // serialized: exactly one submit
        assertEquals("MANUAL01", repo.calls[0].code) // manual won over the captured code
        assertEquals(ReferralSource.MANUAL, repo.calls[0].source)
        assertEquals("PENDING1", holder.consumeReferralCode()) // captured code never consumed
    }

    @Test
    fun installReferrer_readWhenNoManualOrPending() = runTest {
        val repo = FakeReferralRepository()
        val prefs = FakeReferralPreferences()
        val c = coordinator(this, repo = repo, prefs = prefs, reader = FakeInstallReferrerReader("ref=abcd1234"))

        c.attributeOnce(manualCode = null)

        assertEquals(1, repo.calls.size)
        assertEquals("ABCD1234", repo.calls[0].code)
        assertEquals(ReferralSource.INSTALL_REFERRER, repo.calls[0].source)
        assertTrue(prefs.attributedNow())
    }

    @Test
    fun noCodeAnywhere_noOps_andMarksReferrerChecked() = runTest {
        val repo = FakeReferralRepository()
        val prefs = FakeReferralPreferences()
        val c = coordinator(this, repo = repo, prefs = prefs, reader = FakeInstallReferrerReader(null))

        c.attributeOnce(manualCode = null)

        assertTrue(repo.calls.isEmpty())
        // Organic: the read yielded nothing, so we stop re-reading Play on later launches.
        assertTrue(prefs.checkedNow())
    }

    @Test
    fun installReferrer_notReReadOnceChecked() = runTest {
        val reader = FakeInstallReferrerReader("ref=ABCD1234")
        val prefs = FakeReferralPreferences(checkedReferrer = true)
        val repo = FakeReferralRepository()
        val c = coordinator(this, repo = repo, prefs = prefs, reader = reader)

        c.attributeOnce(manualCode = null)

        assertEquals(0, reader.reads)
        assertTrue(repo.calls.isEmpty())
    }

    // ── attributeOnce: guards + retry semantics ──────────────────────────────

    @Test
    fun alreadyAttributed_noOps() = runTest {
        val repo = FakeReferralRepository()
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, repo = repo, prefs = FakeReferralPreferences(attributed = true), holder = holder)

        c.attributeOnce(manualCode = "MANUAL01")

        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun success_setsAttributed_soASecondCallNoOps() = runTest {
        val repo = FakeReferralRepository()
        val c = coordinator(this, repo = repo)

        c.attributeOnce(manualCode = "MANUAL01")
        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals(1, repo.calls.size)
    }

    @Test
    fun manualFailure_leavesUnattributed_soARetryCanRun() = runTest {
        val repo = FakeReferralRepository(result = Result.Error(ReferralError.NETWORK))
        val c = coordinator(this, repo = repo)

        c.attributeOnce(manualCode = "MANUAL01")
        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals(2, repo.calls.size)
    }

    @Test
    fun pendingFailure_reStashesCode_forNextAttempt() = runTest {
        val repo = FakeReferralRepository(result = Result.Error(ReferralError.NETWORK))
        val holder = PendingDeepLinkHolder().apply { setReferralCode("PENDING1") }
        val c = coordinator(this, repo = repo, holder = holder)

        c.attributeOnce(manualCode = null)

        // The consumed deep-link code was put back so a later trigger can retry it.
        assertEquals("PENDING1", holder.consumeReferralCode())
    }

    @Test
    fun installReferrerFailure_leavesCheckedUnset_forRetry() = runTest {
        val repo = FakeReferralRepository(result = Result.Error(ReferralError.NETWORK))
        val prefs = FakeReferralPreferences()
        val c = coordinator(this, repo = repo, prefs = prefs, reader = FakeInstallReferrerReader("ref=ABCD1234"))

        c.attributeOnce(manualCode = null)

        assertFalse(prefs.attributedNow())
        // A genuine code that failed to submit must be re-readable next launch.
        assertFalse(prefs.checkedNow())
    }

    // ── start(): auth-state trigger ──────────────────────────────────────────

    @Test
    fun start_attributesWhenSignedIn() = runTest {
        val repo = FakeReferralRepository()
        val c = coordinator(this, repo = repo, reader = FakeInstallReferrerReader("ref=ABCD1234"), uidFlow = flowOf("uid-1"))

        c.start()
        advanceUntilIdle()

        assertEquals(1, repo.calls.size)
        assertEquals("ABCD1234", repo.calls[0].code)
    }

    @Test
    fun start_signedOut_doesNotAttribute() = runTest {
        val repo = FakeReferralRepository()
        val c = coordinator(this, repo = repo, reader = FakeInstallReferrerReader("ref=ABCD1234"), uidFlow = flowOf(null))

        c.start()
        advanceUntilIdle()

        assertTrue(repo.calls.isEmpty())
    }
}
