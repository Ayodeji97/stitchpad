package com.danzucker.stitchpad.feature.tutorials

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.tutorials.domain.TutorialUriResolver
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.player.TutorialPlayerAction
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
    fun buffering_starts_true_then_clears_on_player_ready() = runTest {
        val vm = newVm()
        runCurrent()
        // A freshly-resolved uri leaves the overlay up until the player reports its first frame.
        assertTrue(vm.state.value.isBuffering)
        vm.onAction(TutorialPlayerAction.OnBufferingChanged(false))
        assertFalse(vm.state.value.isBuffering)
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

    @Test
    fun playback_failure_surfaces_error_and_detaches_player() = runTest {
        val vm = newVm()
        runCurrent()
        vm.onAction(TutorialPlayerAction.OnPlaybackFailed)
        val state = vm.state.value
        assertTrue(state.hasError)
        // The player composable keys off playableUri; it must drop out so the error UI shows.
        assertNull(state.playableUri)
        assertFalse(state.isBuffering)
    }

    @Test
    fun retry_after_playback_failure_resolves_again() = runTest {
        val vm = newVm()
        runCurrent()
        vm.onAction(TutorialPlayerAction.OnPlaybackFailed)
        vm.onAction(TutorialPlayerAction.OnRetry)
        runCurrent()
        val state = vm.state.value
        assertFalse(state.hasError)
        assertEquals("https://cdn/${TutorialTopic.AddCustomer.id}.mp4", state.playableUri)
    }
}
