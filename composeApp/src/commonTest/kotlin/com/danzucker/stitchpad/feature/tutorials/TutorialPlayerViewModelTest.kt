package com.danzucker.stitchpad.feature.tutorials

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.tutorials.domain.TutorialUriResolver
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.player.TutorialPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class TutorialPlayerViewModelTest {

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

    private fun newVm(
        id: String = TutorialTopic.AddCustomer.id,
        resolver: TutorialUriResolver = TutorialUriResolver { "https://cdn/${it.id}.mp4" },
    ) = TutorialPlayerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("tutorialId" to id)),
        tutorialsRepository = repo,
        mediaResolver = resolver,
        onboardingPreferences = prefs,
    )

    @Test
    fun resolves_uri_and_marks_topic_seen() = runTest {
        val vm = newVm()
        runCurrent()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals("https://cdn/${TutorialTopic.AddCustomer.id}.mp4", state.playableUri)
        assertTrue(prefs.seenTutorials.contains(TutorialTopic.AddCustomer.id))
    }

    @Test
    fun resolver_failure_surfaces_error() = runTest {
        val vm = newVm(resolver = { null })
        runCurrent()
        assertTrue(vm.state.value.hasError)
        assertNull(vm.state.value.playableUri)
    }

    @Test
    fun unknown_id_surfaces_error() = runTest {
        val vm = newVm(id = "does_not_exist")
        advanceUntilIdle() // let the catalog-lookup timeout elapse in virtual time
        assertTrue(vm.state.value.hasError)
    }
}
