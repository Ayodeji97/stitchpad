package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerOnlyListenerTest {
    @Test
    fun waitsForHydrationAndNeverOpensForStaff() = runTest {
        val provider = SessionProvider()
        var opened = 0
        val values = mutableListOf<Int>()
        backgroundScope.launch {
            provider.ownerOnlyListener("owner", -1) {
                flow { opened++; emit(7) }
            }.collect { values += it }
        }
        runCurrent()
        assertEquals(0, opened)
        provider.flow.value = staff()
        provider.ready.complete(Unit)
        runCurrent()
        assertEquals(listOf(-1), values)
        assertEquals(0, opened)
    }

    @Test
    fun cancelsOwnerListenerOnRoleChangeAndReopensOnReturn() = runTest {
        val provider = SessionProvider()
        provider.ready.complete(Unit)
        var opened = 0
        var closed = 0
        val values = mutableListOf<Int>()
        backgroundScope.launch {
            provider.ownerOnlyListener("owner", -1) {
                flow {
                    opened++
                    try {
                        emit(7)
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        closed++
                    }
                }
            }.collect { values += it }
        }
        runCurrent()
        provider.flow.value = staff()
        runCurrent()
        assertEquals(1, closed)
        assertEquals(listOf(7, -1), values)
        provider.flow.value = WorkshopSession.ownerOfSelf("owner")
        runCurrent()
        assertEquals(2, opened)
        provider.flow.value = WorkshopSession.ownerOfSelf("someoneElse")
        runCurrent()
        assertEquals(2, closed)
        assertEquals(2, opened)
    }

    private fun staff() = WorkshopSession("staff", "owner", StaffRole.STAFF, MembershipStatus.ACTIVE)

    private class SessionProvider : ActiveWorkshopProvider {
        val ready = CompletableDeferred<Unit>()
        override val flow = MutableStateFlow(WorkshopSession.ownerOfSelf("owner"))
        override fun current() = flow.value
        override suspend fun awaitHydrated(): WorkshopSession {
            ready.await()
            return current()
        }
    }
}
