package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests for the DraftMessageScreen state contract. These verify that
 * the state presented to the screen correctly drives the displayed content:
 * idle state shows placeholder text, generate CTA is disabled until all
 * required fields are set, and Success state exposes the draft text + action
 * buttons. Full Compose rendering tests live in the instrumented test suite.
 */
class DraftMessageScreenTest {

    private val testCustomer = CustomerSummary(
        id = "c",
        firstName = "Folake",
        whatsappNumber = "+234",
    )
    private val testOrder = OrderSummary(
        id = "o",
        customerId = "c",
        garmentLabel = "Boubou",
        balanceFormatted = "₦5,000",
        deadlineFormatted = "Fri",
    )

    @Test
    fun idle_state_has_no_customer_and_cannot_generate() {
        val state = DraftMessageState()
        // "Pick a customer" placeholder: customer is null
        assertNull(state.customer)
        // Generate CTA is disabled (no customer, order, or intent)
        assertFalse(state.canGenerate)
    }

    @Test
    fun generate_button_is_disabled_until_all_required_fields_are_set() {
        // Missing intent → cannot generate
        val partial = DraftMessageState(
            customer = testCustomer,
            order = testOrder,
            intent = null,
        )
        assertFalse(partial.canGenerate)

        // All fields set → can generate
        val ready = DraftMessageState(
            customer = testCustomer,
            order = testOrder,
            intent = DraftIntent.BalanceReminder,
        )
        assertTrue(ready.canGenerate)
    }

    @Test
    fun success_state_exposes_draft_text_and_whatsapp_send_is_available() {
        val successState = DraftMessageState(
            customer = testCustomer,
            order = testOrder,
            generationState = GenerationState.Success("Hi Folake!"),
        )
        // Draft preview should be visible (generationState is Success)
        val gen = successState.generationState
        assertIs<GenerationState.Success>(gen)
        assertEquals("Hi Folake!", gen.draftText)
        // WhatsApp send is available when customer has a number
        assertNotNull(successState.customer?.whatsappNumber)
    }
}
