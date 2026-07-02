package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private val deviceId: String = "device-uuid",
) : ReferralPreferencesStore {
    override suspend fun getOrCreateDeviceId(): String = deviceId
    override suspend fun hasAttributed(): Boolean = attributed
    override suspend fun setAttributed() { attributed = true }
    override suspend fun resetForDebug() { attributed = false }
}

private class FakeInstallReferrerReader(private val referrer: String?) : InstallReferrerReader {
    override suspend fun readReferrer(): String? = referrer
}

class ReferralAttributionCoordinatorTest {

    private fun coordinator(
        scope: kotlinx.coroutines.CoroutineScope,
        repo: FakeReferralRepository = FakeReferralRepository(),
        prefs: FakeReferralPreferences = FakeReferralPreferences(),
        reader: FakeInstallReferrerReader = FakeInstallReferrerReader(null),
        holder: PendingDeepLinkHolder = PendingDeepLinkHolder(),
    ) = ReferralAttributionCoordinator(repo, prefs, reader, holder, scope)

    // ── attributeOnce ────────────────────────────────────────────────────────

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

        // Manual won, so the captured code must still be available for later.
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
    fun noManualAndNoPending_noOps() = runTest {
        val repo = FakeReferralRepository()
        val c = coordinator(this, repo = repo)

        c.attributeOnce(manualCode = null)
        c.attributeOnce(manualCode = "   ")

        assertTrue(repo.calls.isEmpty())
    }

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
        val prefs = FakeReferralPreferences()
        val c = coordinator(this, repo = repo, prefs = prefs)

        c.attributeOnce(manualCode = "MANUAL01")
        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals(1, repo.calls.size)
    }

    @Test
    fun error_leavesUnattributed_soARetryCanRun() = runTest {
        val repo = FakeReferralRepository(result = Result.Error(ReferralError.NETWORK))
        val prefs = FakeReferralPreferences()
        val c = coordinator(this, repo = repo, prefs = prefs)

        c.attributeOnce(manualCode = "MANUAL01")
        c.attributeOnce(manualCode = "MANUAL01")

        assertEquals(2, repo.calls.size)
    }

    // ── captureInstallReferrer ───────────────────────────────────────────────

    @Test
    fun captureInstallReferrer_storesParsedCode() = runTest {
        val holder = PendingDeepLinkHolder()
        val c = coordinator(this, reader = FakeInstallReferrerReader("ref=abcd1234"), holder = holder)

        c.captureInstallReferrer()

        advanceUntilIdle()

        assertEquals("ABCD1234", holder.consumeReferralCode())
    }

    @Test
    fun captureInstallReferrer_organicReferrer_storesNothing() = runTest {
        val holder = PendingDeepLinkHolder()
        val c = coordinator(
            this,
            reader = FakeInstallReferrerReader("utm_source=google-play&utm_medium=organic"),
            holder = holder,
        )

        c.captureInstallReferrer()

        advanceUntilIdle()

        assertNull(holder.consumeReferralCode())
    }

    @Test
    fun captureInstallReferrer_whenAlreadyAttributed_skips() = runTest {
        val holder = PendingDeepLinkHolder()
        val c = coordinator(
            this,
            prefs = FakeReferralPreferences(attributed = true),
            reader = FakeInstallReferrerReader("ref=ABCD1234"),
            holder = holder,
        )

        c.captureInstallReferrer()

        advanceUntilIdle()

        assertNull(holder.consumeReferralCode())
    }
}
