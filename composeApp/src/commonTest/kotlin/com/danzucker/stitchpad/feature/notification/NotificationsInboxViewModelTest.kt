package com.danzucker.stitchpad.feature.notification

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxAction
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxEvent
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeNotificationRepository : NotificationRepository {
    val flow = MutableStateFlow<Result<List<Notification>, DataError.Network>>(Result.Success(emptyList()))
    var lastMarkedRead: String? = null
    var markAllReadCalledWithIds: List<String>? = null

    override fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>> = flow
    override fun observeUnreadCount(userId: String): Flow<Int> = MutableStateFlow(0)

    override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> {
        lastMarkedRead = notificationId
        return Result.Success(Unit)
    }

    override suspend fun markAllRead(userId: String, notificationIds: List<String>): EmptyResult<DataError.Network> {
        markAllReadCalledWithIds = notificationIds
        return Result.Success(Unit)
    }
}

private fun notif(id: String, read: Boolean = false) = Notification(
    id = id,
    orderId = "ord-$id",
    type = NotificationType.OVERDUE,
    customerName = "Ada",
    garmentSummary = "Agbada",
    isRead = read,
    createdAt = 1L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsInboxViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun fakeAuth(): FakeAuthRepository {
        val auth = FakeAuthRepository()
        auth.currentUser = User(
            id = "u1",
            email = "test@example.com",
            displayName = "Test User",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        return auth
    }

    @Test
    fun emitsNotificationsFromRepo() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, fakeAuth())
        vm.state.test {
            awaitItem() // initial loading state
            repo.flow.value = Result.Success(listOf(notif("a"), notif("b", read = true)))
            val s = awaitItem()
            assertEquals(2, s.notifications.size)
            assertEquals(1, s.unreadCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tapMarksReadAndNavigates() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, fakeAuth())
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() } // trigger onStart
        vm.events.test {
            vm.onAction(NotificationsInboxAction.OnNotificationClick(notif("a")))
            val e = awaitItem()
            assertTrue(e is NotificationsInboxEvent.NavigateToOrderDetail && e.orderId == "ord-a")
            assertEquals("a", repo.lastMarkedRead)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markAllReadPassesUnreadIdsToRepo() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, fakeAuth())
        // Push two notifications: one unread, one read.
        vm.state.test {
            awaitItem()
            repo.flow.value = Result.Success(listOf(notif("a"), notif("b", read = true)))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.onAction(NotificationsInboxAction.OnMarkAllReadClick)
        assertEquals(listOf("a"), repo.markAllReadCalledWithIds)
    }

    @Test
    fun networkErrorSetsErrorMessageAndStopsLoading() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, fakeAuth())
        vm.state.test {
            awaitItem() // initial loading state
            repo.flow.value = Result.Error(DataError.Network.UNKNOWN)
            val s = awaitItem()
            assertNotNull(s.errorMessage)
            assertTrue(!s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onBackClickEmitsNavigateBack() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, fakeAuth())
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() } // trigger onStart
        vm.events.test {
            vm.onAction(NotificationsInboxAction.OnBackClick)
            val e = awaitItem()
            assertTrue(e is NotificationsInboxEvent.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
