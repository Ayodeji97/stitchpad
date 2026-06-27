package com.danzucker.stitchpad.feature.tutorials

import app.cash.turbine.test
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.library.HelpTutorialsAction
import com.danzucker.stitchpad.feature.tutorials.presentation.library.HelpTutorialsEvent
import com.danzucker.stitchpad.feature.tutorials.presentation.library.HelpTutorialsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HelpTutorialsViewModelTest {

    private lateinit var repo: FakeTutorialsRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeTutorialsRepository(
            listOf(tutorial(TutorialTopic.AddCustomer.id), tutorial(TutorialTopic.CreateOrder.id)),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loads_catalog_into_state() = runTest {
        val vm = HelpTutorialsViewModel(repo)
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertEquals(2, vm.state.value.tutorials.size)
    }

    @Test
    fun tapping_a_tutorial_navigates_to_the_player() = runTest {
        val vm = HelpTutorialsViewModel(repo)
        runCurrent()
        vm.events.test {
            vm.onAction(HelpTutorialsAction.OnTutorialClick(TutorialTopic.CreateOrder.id))
            assertEquals(
                HelpTutorialsEvent.NavigateToPlayer(TutorialTopic.CreateOrder.id),
                awaitItem(),
            )
        }
    }

    @Test
    fun back_emits_navigate_back() = runTest {
        val vm = HelpTutorialsViewModel(repo)
        runCurrent()
        vm.events.test {
            vm.onAction(HelpTutorialsAction.OnBack)
            assertEquals(HelpTutorialsEvent.NavigateBack, awaitItem())
        }
    }
}
