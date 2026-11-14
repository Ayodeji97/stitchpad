package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SmartOpenOrdersAdapterTest {
    @Test
    fun draftsKeepHistoricalOrderCurrenciesAfterAccountCountryChanges() = runTest {
        val auth = FakeAuthRepository().apply {
            currentUser = User("owner", "test@example.com", "Test", null, null, null, 0, country = Country.KE)
        }
        val orders = FakeOrderRepository().apply {
            ordersList = listOf(
                order("ghana", Currency.GHS, 1234.50),
                order("nigeria", Currency.NGN, 100.25),
                order("cfa", Currency.XOF, 2500.0),
                order("delivered", Currency.GHS, 12.0).copy(status = OrderStatus.DELIVERED),
                order("another-customer", Currency.GHS, 20.0).copy(customerId = "other"),
            )
        }
        val summaries = SmartOpenOrdersAdapter(auth, orders, englishLabels).openOrdersFor("customer")
        assertEquals(listOf("ghana", "nigeria", "cfa"), summaries.map { it.id })
        assertEquals(listOf("GH₵1,234.50", "₦100.25", "F CFA 2,500"), summaries.map { it.balanceFormatted })
    }

    @Test
    fun englishDefaultsKeepFallbackCopy() = runTest {
        val summaries = adapterWith(OpenOrderSummaryLabels()).openOrdersFor("customer")
        assertEquals("Garment", summaries.single().garmentLabel)
        assertEquals("No deadline set", summaries.single().deadlineFormatted)
    }

    @Test
    fun localizedLabelsReplaceFallbackCopy() = runTest {
        val french = OpenOrderSummaryLabels(
            noDeadline = "Aucune date limite",
            garmentFallback = "Vêtement",
            garmentNames = mapOf(GarmentType.SHIRT to "Chemise"),
        )
        val noItems = adapterWith(french).openOrdersFor("customer").single()
        assertEquals("Vêtement", noItems.garmentLabel)
        assertEquals("Aucune date limite", noItems.deadlineFormatted)

        val shirt = adapterWith(
            french,
            items = listOf(OrderItem(id = "i1", garmentType = GarmentType.SHIRT, description = "", price = 0.0)),
        ).openOrdersFor("customer").single()
        assertEquals("Chemise", shirt.garmentLabel)
    }

    private fun adapterWith(
        labels: OpenOrderSummaryLabels,
        items: List<OrderItem> = emptyList(),
    ): SmartOpenOrdersAdapter {
        val auth = FakeAuthRepository().apply {
            currentUser = User("owner", "test@example.com", "Test", null, null, null, 0, country = Country.NG)
        }
        val orders = FakeOrderRepository().apply {
            ordersList = listOf(order("o1", Currency.NGN, 10.0).copy(items = items))
        }
        return SmartOpenOrdersAdapter(auth, orders) { labels }
    }

    private val englishLabels: suspend () -> OpenOrderSummaryLabels = { OpenOrderSummaryLabels() }

    private fun order(id: String, currency: Currency, amount: Double) = Order(
        id = id,
        userId = "owner",
        customerId = "customer",
        customerName = "Customer",
        items = emptyList(),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = amount,
        deadline = null,
        notes = null,
        currency = currency,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
