package com.danzucker.stitchpad.feature.tutorials

import app.cash.turbine.test
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.hint.TutorialHintAction
import com.danzucker.stitchpad.feature.tutorials.presentation.hint.TutorialHintEvent
import com.danzucker.stitchpad.feature.tutorials.presentation.hint.TutorialHintViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialHintViewModelTest {

    private lateinit var repo: FakeTutorialsRepository
    private lateinit var prefs: FakeOnboardingPreferences

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeTutorialsRepository(listOf(tutorial(TutorialTopic.AddCustomer.id)))
        prefs = FakeOnboardingPreferences()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(topic: TutorialTopic = TutorialTopic.AddCustomer) =
        TutorialHintViewModel(topic.id, repo, prefs)

    @Test
    fun first_visit_starts_expanded_with_the_topic_clip() = runTest {
        val vm = newVm()
        runCurrent()
        val state = vm.state.value
        assertTrue(state.resolved)
        assertEquals(TutorialTopic.AddCustomer.id, state.tutorial?.id)
        assertTrue(state.expanded)
    }

    @Test
    fun already_seen_starts_collapsed() = runTest {
        prefs.seenTutorials.add(TutorialTopic.AddCustomer.id)
        val vm = newVm()
        runCurrent()
        assertTrue(vm.state.value.resolved)
        assertFalse(vm.state.value.expanded)
    }

    @Test
    fun watch_marks_seen_collapses_and_navigates() = runTest {
        val vm = newVm()
        runCurrent()
        vm.events.test {
            vm.onAction(TutorialHintAction.OnWatch)
            assertEquals(
                TutorialHintEvent.NavigateToPlayer(TutorialTopic.AddCustomer.id),
                awaitItem(),
            )
        }
        assertFalse(vm.state.value.expanded)
        assertTrue(prefs.seenTutorials.contains(TutorialTopic.AddCustomer.id))
    }

    @Test
    fun dismiss_marks_seen_and_collapses_without_navigating() = runTest {
        val vm = newVm()
        runCurrent()
        vm.onAction(TutorialHintAction.OnDismiss)
        runCurrent()
        assertFalse(vm.state.value.expanded)
        assertTrue(prefs.seenTutorials.contains(TutorialTopic.AddCustomer.id))
    }

    @Test
    fun dismissing_before_a_later_catalog_emission_stays_collapsed() = runTest {
        val vm = newVm()
        runCurrent()
        vm.onAction(TutorialHintAction.OnDismiss)
        runCurrent()
        // Simulate the remote snapshot arriving after the bundled fallback (and after dismiss).
        repo.tutorialsFlow.value = listOf(tutorial(TutorialTopic.AddCustomer.id, durationSec = 55))
        runCurrent()
        assertFalse(vm.state.value.expanded)
    }

    @Test
    fun unknown_topic_resolves_with_no_card() = runTest {
        repo.tutorialsFlow.value = emptyList()
        val vm = newVm()
        runCurrent()
        assertTrue(vm.state.value.resolved)
        assertNull(vm.state.value.tutorial)
    }
}
