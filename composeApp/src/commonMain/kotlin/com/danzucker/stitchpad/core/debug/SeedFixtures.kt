package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
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
 *
 * detekt: TooManyFunctions is suppressed because this is a deliberately
 * function-heavy fixture object — each scenario (Active workshop, All-
 * reconnect, Bulk demo) needs its own customer + order + measurement
 * factories. Splitting into three sibling objects would add boilerplate
 * without improving clarity.
 */
@Suppress("TooManyFunctions")
internal object SeedFixtures {

    private const val DAY_MS = 24L * 60 * 60 * 1000L

    // ── Customer fixtures ────────────────────────────────────────────────────

    private data class CustomerFixture(
        val name: String,
        val phone: String,
        val email: String?,
        val address: String?,
    )

    /**
     * Positions 1–4 are all female so measurementsFor (which hardcodes FEMALE)
     * can be called safely on customers.take(4). Positions 5–8 are male.
     */
    private val CUSTOMER_FIXTURES = listOf(
        CustomerFixture("Adaeze Okafor", "+2348012345601", "adaeze@example.ng", "12 Awolowo Way, Ikeja"),
        CustomerFixture("Folake Adebayo", "+2348012345602", null, null),
        CustomerFixture("Ngozi Iwu", "+2348012345603", null, null),
        CustomerFixture("Hauwa Bello", "+2348012345604", null, null),
        CustomerFixture("Chinedu Eze", "+2348012345605", null, "5 Marina, Lagos Island"),
        CustomerFixture("Tunde Bakare", "+2348012345606", "tunde@example.ng", null),
        CustomerFixture("Oluwaseun Adesina", "+2348012345607", null, "27 Allen Avenue, Ikeja"),
        CustomerFixture("Femi Akinola", "+2348012345608", "femi@example.ng", null),
    )

    /** Eight customers with Nigerian-style names + phones. */
    fun customers(userId: String, now: Long): List<Customer> =
        CUSTOMER_FIXTURES.mapIndexed { i, f ->
            Customer(
                id = "seed-customer-${i + 1}",
                userId = userId,
                name = f.name,
                phone = f.phone,
                email = f.email,
                address = f.address,
                createdAt = now,
            )
        }

    // ── Measurement fixture ──────────────────────────────────────────────────

    /**
     * Measurement for the first four seeded customers (all female).
     *
     * Field keys MUST be [com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate]
     * keys ("bust_circumference"), not display labels ("Bust") — the detail and
     * preview screens look values up by key and silently drop anything that
     * matches neither a template field nor a registered custom field. Every
     * FEMALE template field is filled so all three sections render.
     */
    fun measurementsFor(customer: Customer, now: Long): Measurement {
        val n = customer.id.substringAfterLast('-').toIntOrNull() ?: 1
        // Nudge the girths per customer so seeded records aren't four identical
        // copies; lengths stay fixed so the numbers still read as one body type.
        val girth = (n - 1) * 0.5
        return Measurement(
            id = "seed-measurement-$n",
            customerId = customer.id,
            gender = CustomerGender.FEMALE,
            name = "Owambe fitting",
            fields = mapOf(
                // section_upper_body
                "shoulder_width" to 15.5,
                "bust_circumference" to 38.0 + girth,
                "bust_point" to 10.5,
                "shoulder_to_underbust" to 12.0,
                "bust_span" to 7.5,
                "waist" to 30.0 + girth,
                "neck_circumference" to 14.0,
                "underbust_circumference" to 32.0 + girth,
                // section_body_lengths
                "shoulder_to_waist" to 16.0,
                "hip_circumference" to 40.0 + girth,
                "full_length_gown" to 58.0,
                "sleeve_length" to 22.0,
                "wrist_circumference" to 6.5,
                "nape_to_waist" to 16.5,
                "full_front_length" to 24.0,
                "arm_length" to 23.0,
                // section_trouser
                "trouser_waist" to 30.0 + girth,
                "trouser_length" to 40.0,
                "trouser_hip" to 40.0 + girth,
                "thigh_circumference" to 24.0 + girth,
                "inseam" to 30.0,
            ),
            unit = MeasurementUnit.INCHES,
            notes = "Prefers a slightly loose sleeve. Add 1in ease at the bust.",
            dateTaken = now,
            createdAt = now,
        )
    }

    // ── Order fixtures ───────────────────────────────────────────────────────

    /**
     * Order fixtures for the active-workshop seed. Each fixture is built
     * against a customer the seeder has already inserted. The caller supplies
     * `now`; per-order `createdAt` is offset to produce a realistic age
     * distribution (one same-day, one 3-day-old, two 7-day-old).
     */
    fun activeOrders(customers: List<Customer>, now: Long): List<Order> = listOf(
        inProgressOrder(customers[0], now),
        readyOrder(customers[1], now),
        deliveredOrder(customers[2], now),
        pendingOrder(customers[3], now),
    )

    private fun inProgressOrder(customer: Customer, now: Long): Order = Order(
        id = "seed-order-1",
        userId = customer.userId,
        customerId = customer.id,
        customerName = customer.name,
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
        statusHistory = listOf(StatusChange(OrderStatus.IN_PROGRESS, now - 7 * DAY_MS)),
        totalPrice = 25_000.0,
        payments = emptyList(),
        deadline = now + 5 * DAY_MS,
        notes = null,
        createdAt = now - 7 * DAY_MS,
        updatedAt = now - 7 * DAY_MS,
    )

    private fun readyOrder(customer: Customer, now: Long): Order = Order(
        id = "seed-order-2",
        userId = customer.userId,
        customerId = customer.id,
        customerName = customer.name,
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
            StatusChange(OrderStatus.IN_PROGRESS, now - 7 * DAY_MS),
            StatusChange(OrderStatus.READY, now - 1 * DAY_MS),
        ),
        totalPrice = 18_000.0,
        payments = emptyList(),
        deadline = now - 1 * DAY_MS,
        notes = null,
        createdAt = now - 7 * DAY_MS,
        updatedAt = now - 1 * DAY_MS,
    )

    private fun deliveredOrder(customer: Customer, now: Long): Order = Order(
        id = "seed-order-3",
        userId = customer.userId,
        customerId = customer.id,
        customerName = customer.name,
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
            StatusChange(OrderStatus.IN_PROGRESS, now - 3 * DAY_MS),
            StatusChange(OrderStatus.READY, now - 2 * DAY_MS),
            StatusChange(OrderStatus.DELIVERED, now - 1 * DAY_MS),
        ),
        totalPrice = 8_500.0,
        payments = emptyList(),
        deadline = now - 1 * DAY_MS,
        notes = null,
        createdAt = now - 3 * DAY_MS,
        updatedAt = now - 1 * DAY_MS,
    )

    private fun pendingOrder(customer: Customer, now: Long): Order = Order(
        id = "seed-order-4",
        userId = customer.userId,
        customerId = customer.id,
        customerName = customer.name,
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
        deadline = now + 14 * DAY_MS,
        notes = null,
        createdAt = now,
        updatedAt = now,
    )

    // ── All-reconnect fixtures ───────────────────────────────────────────────

    /** Six all-reconnect customers (100+ days since last order). */
    fun allReconnectCustomers(userId: String, now: Long): List<Customer> {
        return List(6) { i ->
            Customer(
                id = "seed-reconnect-${i + 1}",
                userId = userId,
                name = "Reconnect Tester ${i + 1}",
                phone = "+234801234${(7000 + i).toString().padStart(4, '0')}",
                email = null,
                address = null,
                createdAt = now - (120 + i * 5) * DAY_MS,
            )
        }
    }

    // ── Bulk fixtures ────────────────────────────────────────────────────────

    /**
     * Generate [count] additive demo customers with numbered names so they
     * can be visually counted in the customer list (e.g. when testing the
     * First Month 200-customer cap or the post-First-Month 15-cap). IDs
     * are prefixed `seed-bulk-` so they don't collide with the fixed
     * seedActive/seedReconnect fixtures.
     */
    fun bulkCustomers(userId: String, now: Long, count: Int): List<Customer> {
        return List(count) { i ->
            val n = i + 1
            Customer(
                id = "seed-bulk-$n",
                userId = userId,
                name = "Demo Customer $n",
                phone = "+234801234${(8000 + i).toString().padStart(4, '0')}",
                email = null,
                address = null,
                createdAt = now,
            )
        }
    }

    /**
     * A simple FEMALE measurement attached to a bulk demo customer. Only the
     * essential upper-body/length fields are filled — bulk seeding exists to
     * exercise list volume, not to showcase a full body profile. Keys are
     * BodyProfileTemplate keys; see [measurementsFor] for why that matters.
     */
    fun bulkMeasurementFor(customer: Customer, now: Long): Measurement = Measurement(
        id = "seed-bulk-measurement-${customer.id.substringAfterLast('-')}",
        customerId = customer.id,
        gender = CustomerGender.FEMALE,
        name = "Everyday fit",
        fields = mapOf(
            "shoulder_width" to 14.5,
            "bust_circumference" to 34.0,
            "waist" to 26.0,
            "hip_circumference" to 36.0,
            "full_length_gown" to 55.0,
            "sleeve_length" to 21.0,
        ),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = now,
        createdAt = now,
    )

    /** A simple in-progress order attached to a bulk demo customer. */
    fun bulkOrderFor(customer: Customer, index: Int, now: Long): Order {
        val n = index + 1
        return Order(
            id = "seed-bulk-order-$n",
            userId = customer.userId,
            customerId = customer.id,
            customerName = customer.name,
            items = listOf(
                OrderItem(
                    id = "seed-bulk-item-$n",
                    garmentType = GarmentType.KAFTAN,
                    description = "Demo order #$n",
                    price = 10_000.0,
                ),
            ),
            status = OrderStatus.IN_PROGRESS,
            subStatus = null,
            priority = OrderPriority.NORMAL,
            statusHistory = listOf(StatusChange(OrderStatus.IN_PROGRESS, now)),
            totalPrice = 10_000.0,
            payments = emptyList(),
            deadline = now + 7 * DAY_MS,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** One Delivered order per all-reconnect customer, 100+ days ago. */
    fun allReconnectOrders(customers: List<Customer>, now: Long): List<Order> {
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
                statusHistory = listOf(StatusChange(OrderStatus.DELIVERED, now - daysAgo * DAY_MS)),
                totalPrice = 12_000.0,
                payments = emptyList(),
                deadline = now - daysAgo * DAY_MS,
                notes = null,
                createdAt = now - daysAgo * DAY_MS,
                updatedAt = now - daysAgo * DAY_MS,
            )
        }
    }
}
