package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange

/**
 * Deterministic fixture data used by [DebugSeeder]. Fixed IDs let smoke tests
 * reference "seed-customer-1" reliably. createdAt is supplied by the caller
 * (typically a now() function from the ViewModel) so fixtures don't bake in
 * stale times when the device clock changes.
 */
internal object SeedFixtures {

    /** Eight customers with Nigerian-style names + phones. */
    fun customers(userId: String, now: Long): List<Customer> = listOf(
        Customer(
            id = "seed-customer-1",
            userId = userId,
            name = "Adaeze Okafor",
            phone = "+2348012345601",
            email = "adaeze@example.ng",
            address = "12 Awolowo Way, Ikeja",
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-2",
            userId = userId,
            name = "Folake Adebayo",
            phone = "+2348012345602",
            email = null,
            address = null,
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = "Prefers WhatsApp",
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-3",
            userId = userId,
            name = "Chinedu Eze",
            phone = "+2348012345603",
            email = null,
            address = "5 Marina, Lagos Island",
            deliveryPreference = DeliveryPreference.DELIVERY,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-4",
            userId = userId,
            name = "Tunde Bakare",
            phone = "+2348012345604",
            email = "tunde@example.ng",
            address = null,
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-5",
            userId = userId,
            name = "Ngozi Iwu",
            phone = "+2348012345605",
            email = null,
            address = null,
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-6",
            userId = userId,
            name = "Oluwaseun Adesina",
            phone = "+2348012345606",
            email = null,
            address = "27 Allen Avenue, Ikeja",
            deliveryPreference = DeliveryPreference.DELIVERY,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-7",
            userId = userId,
            name = "Hauwa Bello",
            phone = "+2348012345607",
            email = null,
            address = null,
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = null,
            createdAt = now,
        ),
        Customer(
            id = "seed-customer-8",
            userId = userId,
            name = "Femi Akinola",
            phone = "+2348012345608",
            email = "femi@example.ng",
            address = null,
            deliveryPreference = DeliveryPreference.PICKUP,
            notes = null,
            createdAt = now,
        ),
    )

    /** Measurement for the first four seeded customers. */
    fun measurementsFor(customer: Customer, now: Long): Measurement = Measurement(
        id = "seed-measurement-${customer.id.substringAfterLast('-')}",
        customerId = customer.id,
        gender = CustomerGender.FEMALE,
        fields = mapOf(
            "Bust" to 36.0,
            "Waist" to 28.0,
            "Hip" to 38.0,
            "Shoulder Width" to 15.0,
            "Arm Length" to 22.5,
        ),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = now,
        createdAt = now,
    )

    /**
     * Order fixtures for the active-workshop seed. Each fixture is built
     * against a customer the seeder has already inserted. The caller supplies
     * `now`; per-order `createdAt` is offset to produce a realistic age
     * distribution (one same-day, one 3-day-old, two 7-day-old).
     */
    fun activeOrders(customers: List<Customer>, now: Long): List<Order> {
        val dayMs = 24L * 60 * 60 * 1000
        return listOf(
            // 7-day-old IN_PROGRESS
            Order(
                id = "seed-order-1",
                userId = customers[0].userId,
                customerId = customers[0].id,
                customerName = customers[0].name,
                items = listOf(
                    OrderItem(
                        id = "seed-item-1",
                        garmentType = GarmentType.AGBADA,
                        description = "Cream agbada with embroidery",
                        price = 25_000.0,
                    ),
                ),
                status = OrderStatus.IN_PROGRESS,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.IN_PROGRESS, now - 7 * dayMs)),
                totalPrice = 25_000.0,
                payments = emptyList(),
                deadline = now + 5 * dayMs,
                notes = null,
                createdAt = now - 7 * dayMs,
                updatedAt = now - 7 * dayMs,
            ),
            // 7-day-old READY
            Order(
                id = "seed-order-2",
                userId = customers[1].userId,
                customerId = customers[1].id,
                customerName = customers[1].name,
                items = listOf(
                    OrderItem(
                        id = "seed-item-2",
                        garmentType = GarmentType.ASOEBI,
                        description = "Navy ankara asoebi",
                        price = 18_000.0,
                    ),
                ),
                status = OrderStatus.READY,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(
                    StatusChange(OrderStatus.IN_PROGRESS, now - 7 * dayMs),
                    StatusChange(OrderStatus.READY, now - 1 * dayMs),
                ),
                totalPrice = 18_000.0,
                payments = emptyList(),
                deadline = now - 1 * dayMs,
                notes = null,
                createdAt = now - 7 * dayMs,
                updatedAt = now - 1 * dayMs,
            ),
            // 3-day-old DELIVERED
            Order(
                id = "seed-order-3",
                userId = customers[2].userId,
                customerId = customers[2].id,
                customerName = customers[2].name,
                items = listOf(
                    OrderItem(
                        id = "seed-item-3",
                        garmentType = GarmentType.SHIRT,
                        description = "White cotton dress shirt",
                        price = 8_500.0,
                    ),
                ),
                status = OrderStatus.DELIVERED,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(
                    StatusChange(OrderStatus.IN_PROGRESS, now - 3 * dayMs),
                    StatusChange(OrderStatus.READY, now - 2 * dayMs),
                    StatusChange(OrderStatus.DELIVERED, now - 1 * dayMs),
                ),
                totalPrice = 8_500.0,
                payments = emptyList(),
                deadline = now - 1 * dayMs,
                notes = null,
                createdAt = now - 3 * dayMs,
                updatedAt = now - 1 * dayMs,
            ),
            // Same-day PENDING (deadline 14d out)
            Order(
                id = "seed-order-4",
                userId = customers[3].userId,
                customerId = customers[3].id,
                customerName = customers[3].name,
                items = listOf(
                    OrderItem(
                        id = "seed-item-4",
                        garmentType = GarmentType.KAFTAN,
                        description = "Sky-blue brocade kaftan",
                        price = 15_000.0,
                    ),
                ),
                status = OrderStatus.PENDING,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.PENDING, now)),
                totalPrice = 15_000.0,
                payments = emptyList(),
                deadline = now + 14 * dayMs,
                notes = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Six all-reconnect customers (100+ days since last order). */
    fun allReconnectCustomers(userId: String, now: Long): List<Customer> {
        val dayMs = 24L * 60 * 60 * 1000
        return List(6) { i ->
            Customer(
                id = "seed-reconnect-${i + 1}",
                userId = userId,
                name = "Reconnect Tester ${i + 1}",
                phone = "+234801234${(7000 + i).toString().padStart(4, '0')}",
                email = null,
                address = null,
                deliveryPreference = DeliveryPreference.PICKUP,
                notes = null,
                createdAt = now - (120 + i * 5) * dayMs,
            )
        }
    }

    /** One Delivered order per all-reconnect customer, 100+ days ago. */
    fun allReconnectOrders(customers: List<Customer>, now: Long): List<Order> {
        val dayMs = 24L * 60 * 60 * 1000
        return customers.mapIndexed { i, c ->
            val daysAgo = 100L + i * 10
            Order(
                id = "seed-reconnect-order-${i + 1}",
                userId = c.userId,
                customerId = c.id,
                customerName = c.name,
                items = listOf(
                    OrderItem(
                        id = "seed-reconnect-item-${i + 1}",
                        garmentType = GarmentType.DRESS,
                        description = "Old order — needs reconnect",
                        price = 12_000.0,
                    ),
                ),
                status = OrderStatus.DELIVERED,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.DELIVERED, now - daysAgo * dayMs)),
                totalPrice = 12_000.0,
                payments = emptyList(),
                deadline = now - daysAgo * dayMs,
                notes = null,
                createdAt = now - daysAgo * dayMs,
                updatedAt = now - daysAgo * dayMs,
            )
        }
    }
}
