package com.danzucker.stitchpad.feature.notification

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
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

private class FakeAuthRepositoryReturning(private val uid: String) : AuthRepository {
    private val user = User(
        id = uid,
        email = "test@example.com",
        displayName = "Test User",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
    )

    override suspend fun getCurrentUser(): User = user
    override val isLoggedIn: Boolean get() = true

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User, AuthError> = Result.Success(user)
    override suspend fun signInWithEmail(email: String, password: String): Result<User, AuthError> = Result.Success(user)
    override suspend fun signInWithGoogle(): Result<User, AuthError> = Result.Success(user)
    override suspend fun signInWithApple(): Result<User, AuthError> = Result.Success(user)
    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun sendEmailVerification(): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun reloadUser(): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun isEmailVerified(): Boolean = true
    override suspend fun signOut(): Result<Unit, AuthError> = Result.Success(Unit)
    override suspend fun getSignInProvider(): SignInProvider = SignInProvider.EMAIL_PASSWORD
    override suspend fun reauthenticateWithPassword(password: String): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun reauthenticateWithApple(): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun reauthenticateWithGoogle(): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun updateEmail(newEmail: String): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun updatePassword(newPassword: String): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun updateAuthDisplayName(name: String?): EmptyResult<AuthError> = Result.Success(Unit)
    override suspend fun deleteAccount(): EmptyResult<AuthError> = Result.Success(Unit)
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

    @Test
    fun emitsNotificationsFromRepo() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
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
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
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
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
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
}
