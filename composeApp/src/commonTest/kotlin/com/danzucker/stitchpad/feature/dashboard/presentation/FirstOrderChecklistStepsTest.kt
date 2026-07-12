package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepKey
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepStatus
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import kotlin.test.Test
import kotlin.test.assertEquals

class FirstOrderChecklistStepsTest {

    private fun setup(
        hasOrder: Boolean,
        hasDueDate: Boolean,
        hasDeposit: Boolean,
    ) = FirstOrderSetupUi(
        customerName = "Ada",
        orderId = if (hasOrder) "order-1" else null,
        hasOrder = hasOrder,
        hasDueDate = hasDueDate,
        hasDeposit = hasDeposit,
    )

    private fun List<com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStep>.statusOf(
        key: SetupStepKey,
    ) = first { it.key == key }.status

    @Test
    fun `deposit recorded without due date shows deposit Done and due date Active`() {
        val steps = firstOrderChecklistSteps(
            setup(hasOrder = true, hasDueDate = false, hasDeposit = true),
        )
        assertEquals(SetupStepStatus.Done, steps.statusOf(SetupStepKey.RecordDeposit))
        assertEquals(SetupStepStatus.Active, steps.statusOf(SetupStepKey.SetDueDate))
    }

    @Test
    fun `nothing set beyond order keeps due date Active and deposit Pending`() {
        val steps = firstOrderChecklistSteps(
            setup(hasOrder = true, hasDueDate = false, hasDeposit = false),
        )
        assertEquals(SetupStepStatus.Active, steps.statusOf(SetupStepKey.SetDueDate))
        assertEquals(SetupStepStatus.Pending, steps.statusOf(SetupStepKey.RecordDeposit))
    }

    @Test
    fun `due date set without deposit makes deposit the Active step`() {
        val steps = firstOrderChecklistSteps(
            setup(hasOrder = true, hasDueDate = true, hasDeposit = false),
        )
        assertEquals(SetupStepStatus.Done, steps.statusOf(SetupStepKey.SetDueDate))
        assertEquals(SetupStepStatus.Active, steps.statusOf(SetupStepKey.RecordDeposit))
    }

    @Test
    fun `no order keeps both later steps Pending`() {
        val steps = firstOrderChecklistSteps(
            setup(hasOrder = false, hasDueDate = false, hasDeposit = false),
        )
        assertEquals(SetupStepStatus.Active, steps.statusOf(SetupStepKey.AddFirstOrder))
        assertEquals(SetupStepStatus.Pending, steps.statusOf(SetupStepKey.SetDueDate))
        assertEquals(SetupStepStatus.Pending, steps.statusOf(SetupStepKey.RecordDeposit))
    }
}
