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
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
class DispatchDetailBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun measureStopListScrollPerformanceWithoutCompilationMode() =
        measureForStopListScroll(CompilationMode.None())

    @Test
    fun measureStopListScrollPerformanceWithBaselineProfile() =
        measureForStopListScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureUntilRouteCalculationResultReceptionWithoutCompilationMode() =
        measureUntilRouteCalculationResultReceptionEvent(CompilationMode.None())

    @Test
    fun measureUntilRouteCalculationResultReceptionWithBaselineProfile() =
        measureUntilRouteCalculationResultReceptionEvent(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureForTripStartAndEndWithoutCompilationMode() =
        measureForTripStartAndEndEvent(CompilationMode.None())

    @Test
    fun measureForTripStartAndEndWithBaselineProfile() =
        measureForTripStartAndEndEvent(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureForTimelineGestureActionWithoutCompilationMode() =
        measureForTimelineGestureAction(CompilationMode.None())

    @Test
    fun measureForTimelineGestureActionWithBaselineProfile() =
        measureForTimelineGestureAction(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureNextAndPreviousStopButtonPressPerformanceWithoutCompilationMode() =
        measureNextAndPreviousStopButtonPressPerformance(CompilationMode.None())

    @Test
    fun measureNextAndPreviousStopButtonPressPerformanceWithBaselineProfile() =
        measureNextAndPreviousStopButtonPressPerformance(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun measureTimelineScreenLoadingTimeWithoutCompilationMode() =
        measureTimelineScreenLoading(CompilationMode.None())

    @Test
    fun measureTimelineScreenLoadingTimeWithBaselineProfile() =
        measureTimelineScreenLoading(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureForStopListScroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(TraceSectionMetric("STOPLISTSCREENLOADINGTIME")),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                    startTrip(this)
                    val stopListRecyclerView = findObject(By.res(PACKAGE_NAME, STOP_LIST_RECYCLER_RES_ID))
                    stopListRecyclerView.setGestureMargin(displayWidth / 5)
                    repeat(OPERATION_REPEAT_COUNT) {
                        stopListRecyclerView.scroll(Direction.UP, 2f, SCROLL_SPEED)
                        waitForIdle()
                    }
                    endTrip(this)
                }
            }
        )
    }

    private fun measureUntilRouteCalculationResultReceptionEvent(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(TraceSectionMetric("ROUTECALCULATIONLOADINGTIME")),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                }
            }
        )
    }

    private fun measureForTripStartAndEndEvent(compilationMode: CompilationMode) {
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
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                    startTrip(this)
                    endTrip(this)
                }
            }
        )
    }

    private fun measureForTimelineGestureAction(compilationMode: CompilationMode) {
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
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                    navigateToTimelineScreen(this)
                    val timelineView : UiObject2? = findObject(By.res(PACKAGE_NAME, TIMELINE_VIEW_RES_ID))
                    timelineView?.setGestureMargin(displayWidth / 5)
                    repeat(OPERATION_REPEAT_COUNT) {
                        timelineView?.scroll(Direction.RIGHT, 2f, SCROLL_SPEED)
                        waitForIdle()
                    }
                }
            }
        )
    }

    private fun measureNextAndPreviousStopButtonPressPerformance(compilationMode: CompilationMode) {
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
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                    navigateToTimelineScreen(this)
                    startTrip(this)
                    findObject(By.res(PACKAGE_NAME, TIMELINE_NEXT_STOP_RES_ID)).click()
                    findObject(By.res(PACKAGE_NAME, TIMELINE_PREVIOUS_STOP_RES_ID)).click()
                    endTrip(this)
                }
            }
        )
    }

    private fun measureTimelineScreenLoading(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric(),TraceSectionMetric("TIMELINESCREENLOADINGTIME")),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = TEST_ITERATION_COUNT,
            setupBlock = {
                provideDisplayOverlayPermissionAndStartApp(this)
            },
            measureBlock = {
                with(device) {
                    navigateToStopListScreen(this)
                    waitForRouteCalculationResult(this)
                    navigateToTimelineScreen(this)
                }
            }
        )
    }


}