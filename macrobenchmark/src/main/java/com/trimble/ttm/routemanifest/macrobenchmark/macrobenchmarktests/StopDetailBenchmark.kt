package com.trimble.ttm.routemanifest.macrobenchmark.macrobenchmarktests

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StopDetailBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun measureStopDetailScreenNavigationPerformanceWithoutCompilationMode() =
        measureForStopDetailScreenNavigationFor2Stops(CompilationMode.None())

    @Test
    fun measurePerformanceForStopActionNavigationWithoutCompilationMode() =
        measureForStopActionNavigation(CompilationMode.None())

    @Test
    fun measurePerformanceForTripCompletionWithoutCompilationMode() =
        measureForTripCompletion(CompilationMode.None())

    @Test
    fun measurePerformanceForStopDetailNavigationWithoutCompilationMode() =
        measureForStopDetailNavigation(CompilationMode.None())
    @Test
    fun measurePerformanceForStopDetailNavigationWithBaselineProfile() =
        measureForStopDetailNavigation(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureForStopDetailScreenNavigationFor2Stops(compilationMode: CompilationMode) {
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
                    navigateToStopDetailScreenAndSelectFirstStop(this)
                    waitForArriveActionVisibility(this)
                    pressBack()
                    selectStopFromStopList(this, stopId = 1)
                    waitForArriveActionVisibility(this)
                    pressBack()
                    endTrip(this)
                }
            }
        )
    }

    private fun measureForStopActionNavigation(compilationMode: CompilationMode) {
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
                    navigateToStopDetailScreenAndSelectFirstStop(this)
                    waitForArriveActionVisibility(this)
                    navigateToNextAction(this)
                    waitForArriveActionVisibility(this)
                    navigateToPreviousAction(this)
                    waitForArriveActionVisibility(this)
                    pressBack()
                    endTrip(this)
                }
            }
        )
    }

    private fun measureForTripCompletion(compilationMode: CompilationMode) {
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
                    navigateToStopDetailScreenAndSelectFirstStop(this)
                    completeArriveAndDepartActions(this) // For Stop 1
                    navigateToPreviousAction(this)
                    waitForDepartCompleteVisibility(this)
                    navigateToNextAction(this)
                    completeArriveAndDepartActions(this) // For Stop 2
                    waitForDepartCompleteVisibility(this)
                }
            }
        )
    }

    private fun measureForStopDetailNavigation(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric(),TraceSectionMetric("STOPDETAILOADING")),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    navigateToStopDetailScreenAndSelectFirstStop(this)
                    device.wait(Until.hasObject(By.textStartsWith("TRIP")), LOAD_TIMEOUT)
                    device.findObject(By.res(PACKAGE_NAME, TOOLBAR_HAMBURGER_MENU_DESCRIPTION)).click()
                    device.wait(Until.hasObject(By.textStartsWith("TRIP")), LOAD_TIMEOUT)
                    endTrip(device)
                }
            }
        )
    }


}