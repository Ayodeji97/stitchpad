package com.danzucker.stitchpad.core.config.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.domain.AppGateDecision
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppGateViewModelTest {

    private lateinit var repository: FakeAppConfigRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeAppConfigRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun disabledConfig_isAllowed() = runTest {
        val viewModel = AppGateViewModel(repository, isIos = false, currentBuild = 100)

        viewModel.state.test {
            assertEquals(AppGateDecision.Allowed, awaitItem().decision)
        }
    }

    @Test
    fun belowFloorConfig_emitsForceUpdate() = runTest {
        repository = FakeAppConfigRepository(
            AppConfig.Disabled.copy(
                minSupportedBuildAndroid = 200,
                updateUrlAndroid = "market://android",
            ),
        )
        val viewModel = AppGateViewModel(repository, isIos = false, currentBuild = 100)

        viewModel.state.test {
            val decision = assertIs<AppGateDecision.ForceUpdate>(awaitItem().decision)
            assertEquals("market://android", decision.updateUrl)
        }
    }

    @Test
    fun maintenanceConfig_emitsMaintenance() = runTest {
        repository = FakeAppConfigRepository(AppConfig.Disabled.copy(maintenanceMode = true))
        val viewModel = AppGateViewModel(repository, isIos = false, currentBuild = 100)

        viewModel.state.test {
            assertIs<AppGateDecision.Maintenance>(awaitItem().decision)
        }
    }

    @Test
    fun configFlippingToForceUpdate_updatesDecision() = runTest {
        val viewModel = AppGateViewModel(repository, isIos = true, currentBuild = 100)

        viewModel.state.test {
            assertEquals(AppGateDecision.Allowed, awaitItem().decision)

            repository.emit(AppConfig.Disabled.copy(minSupportedBuildIos = 200))

            assertIs<AppGateDecision.ForceUpdate>(awaitItem().decision)
        }
    }

    @Test
    fun nullBuild_failsOpenDespiteFloor() = runTest {
        repository = FakeAppConfigRepository(AppConfig.Disabled.copy(minSupportedBuildAndroid = 999))
        val viewModel = AppGateViewModel(repository, isIos = false, currentBuild = null)

        viewModel.state.test {
            assertEquals(AppGateDecision.Allowed, awaitItem().decision)
        }
    }
}
