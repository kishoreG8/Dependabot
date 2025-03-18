package com.trimble.ttm.routemanifest.macrobenchmark.macrobenchmarktests

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import com.trimble.ttm.routemanifest.macrobenchmark.utils.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
Results can be updated and maintained here:
https://confluence.trimble.tools/x/YZHaDg
 */

@LargeTest
@RunWith(AndroidJUnit4::class)
class DispatchListBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun measureTripListScrollPerformanceWithoutCompilationMode() =
        measureForTripListScroll(CompilationMode.None())

    @Test
    fun measureTripListScrollPerformanceWithBaselineProfile() =
        measureForTripListScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureTripSelectionPerformanceWithoutCompilationMode() =
        measureForTripSelection(CompilationMode.None())

    @Test
    fun measureTripSelectionPerformanceWithBaselineProfile() =
        measureForTripSelection(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureHamburgerMenuPressPerformanceWithoutCompilationMode() =
        measureForHamburgerMenuPress(CompilationMode.None())

    @Test
    fun measureHamburgerMenuPressPerformanceWithBaselineProfile() =
        measureForHamburgerMenuPress(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureForTripListScroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    waitForTripListFetchAndPressNoInAlertDialog(this)
                    val recyclerView = findObject(By.res(PACKAGE_NAME, TRIP_LIST_RECYCLERVIEW_RES_ID))
                    recyclerView.setGestureMargin(device.displayWidth / 5)
                    repeat(OPERATION_REPEAT_COUNT) {
                        recyclerView.scroll(Direction.UP, 5f, SCROLL_SPEED)
                        waitForIdle()
                    }
                }
            }
        )
    }

    private fun measureForTripSelection(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    waitForTripListFetchAndPressNoInAlertDialog(this)
                    val recyclerView = findObject(By.res(PACKAGE_NAME, TRIP_LIST_RECYCLERVIEW_RES_ID))
                    recyclerView.children[0].click()
                    waitForStopListFetch(this)
                    pressBack()
                }
            }
        )
    }

    private fun measureForHamburgerMenuPress(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    waitForTripListFetchAndPressNoInAlertDialog(this)
                    findObject(By.desc(TOOLBAR_HAMBURGER_MENU_DESCRIPTION)).click()
                }
            }
        )
    }

}