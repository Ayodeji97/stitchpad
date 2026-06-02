package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun signIn() {
        authRepository.signUpWithEmail("t@t.com", "pass", "Ade Bello")
    }

    private fun fakeCustomer(
        id: String = "c1",
        name: String = "Ada Lovelace",
        slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    ) = Customer(
        id = id,
        userId = "test-uid",
        name = name,
        phone = "+2348012345678",
        slotState = slotState,
    )

    private fun TestScope.createViewModel(): OrderListViewModel {
        val vm = OrderListViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
        )
        // Subscribe so the state flow's onStart loads orders + customers.
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun onAddOrderClick_withNoCustomers_emitsNavigateToAddCustomerFirst() = runTest {
        // Brand-new user: the Orders-tab FAB must gate to "add a customer first"
        // instead of dropping the user on an empty customer picker.
        signIn()
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToAddCustomerFirst, vm.events.first())
    }

    @Test
    fun onAddOrderClick_withActiveCustomer_emitsNavigateToOrderForm() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToOrderForm, vm.events.first())
    }

    @Test
    fun onAddOrderClick_withOnlyLockedCustomers_emitsNavigateToAddCustomerFirst() = runTest {
        // Locked customers aren't selectable in the order form's picker, so a
        // user whose customers are all locked must be gated rather than dropped
        // on an empty picker dead-end.
        signIn()
        customerRepository.customersList =
            listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToAddCustomerFirst, vm.events.first())
    }

    @Test
    fun onAddOrderClick_whenCustomerQueryErrors_failsOpenToOrderForm() = runTest {
        // If we can't resolve the customer list, route to the form (which surfaces
        // whatever's cached) rather than wrongly gating a customer-owning user.
        signIn()
        customerRepository.shouldReturnError = DataError.Network.NO_INTERNET
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToOrderForm, vm.events.first())
    }
}
