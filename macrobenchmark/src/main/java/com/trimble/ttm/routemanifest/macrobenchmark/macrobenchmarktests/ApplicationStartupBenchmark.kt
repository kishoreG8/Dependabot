package com.trimble.ttm.routemanifest.macrobenchmark.macrobenchmarktests

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.trimble.ttm.routemanifest.macrobenchmark.utils.PACKAGE_NAME
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TEST_ITERATION_COUNT
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
Results can be updated and maintained here:
https://confluence.trimble.tools/x/YZHaDg
 */

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ApplicationStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun appStartUpWithoutCompilationMode() = startup(CompilationMode.None())

    @Test
    fun appStartUpWithBaselineProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), TraceSectionMetric("APPLICATION_CLASS_ON_CREATE")),
        compilationMode = compilationMode,
        iterations = TEST_ITERATION_COUNT,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() }
    )
}