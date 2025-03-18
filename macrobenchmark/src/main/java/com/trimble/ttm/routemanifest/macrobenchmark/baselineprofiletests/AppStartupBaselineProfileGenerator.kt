package com.trimble.ttm.routemanifest.macrobenchmark.baselineprofiletests

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Until.findObject
import com.trimble.ttm.routemanifest.macrobenchmark.utils.LOAD_TIMEOUT
import com.trimble.ttm.routemanifest.macrobenchmark.utils.PACKAGE_NAME
import com.trimble.ttm.routemanifest.macrobenchmark.utils.SCROLL_SPEED
import com.trimble.ttm.routemanifest.macrobenchmark.utils.STOP_LIST_RECYCLER_RES_ID
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TIMELINE_NEXT_STOP_RES_ID
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TIMELINE_PREVIOUS_STOP_RES_ID
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TOOLBAR_HAMBURGER_MENU_DESCRIPTION
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TRIP_LIST_RECYCLERVIEW_RES_ID
import com.trimble.ttm.routemanifest.macrobenchmark.utils.TRIP_LIST_TAB_TEXT
import com.trimble.ttm.routemanifest.macrobenchmark.utils.endTrip
import com.trimble.ttm.routemanifest.macrobenchmark.utils.navigateToNextAction
import com.trimble.ttm.routemanifest.macrobenchmark.utils.navigateToPreviousAction
import com.trimble.ttm.routemanifest.macrobenchmark.utils.navigateToTimelineScreen
import com.trimble.ttm.routemanifest.macrobenchmark.utils.provideDisplayOverlayPermissionAndStartApp
import com.trimble.ttm.routemanifest.macrobenchmark.utils.selectStopFromStopList
import com.trimble.ttm.routemanifest.macrobenchmark.utils.startTrip
import com.trimble.ttm.routemanifest.macrobenchmark.utils.waitForArriveActionVisibility
import com.trimble.ttm.routemanifest.macrobenchmark.utils.waitForRouteCalculationResult
import com.trimble.ttm.routemanifest.macrobenchmark.utils.waitForStopListFetch
import com.trimble.ttm.routemanifest.macrobenchmark.utils.waitForTripListFetchAndPressNoInAlertDialog
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Ignore // We're ignoring this test because we're using this only to generate the baseLineProfile.
    @Test
    fun startup() =
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            startActivityAndWait()
            provideDisplayOverlayPermissionAndStartApp(this)
            waitForTripListFetchAndPressNoInAlertDialog(device)


            val recyclerView = device.findObject(By.res(PACKAGE_NAME, TRIP_LIST_RECYCLERVIEW_RES_ID))
            recyclerView.setGestureMargin(device.displayWidth / 5)
            repeat(1) {
                recyclerView.scroll(Direction.UP, 5f, SCROLL_SPEED)
                device.waitForIdle()
            }

            recyclerView.children[0].click()

            waitForStopListFetch(device)
            waitForRouteCalculationResult(device)
            startTrip(device)

            val stopListRecyclerView = device.findObject(By.res(PACKAGE_NAME, STOP_LIST_RECYCLER_RES_ID))
            stopListRecyclerView.setGestureMargin(device.displayWidth / 5)
            repeat(1) {
                stopListRecyclerView.scroll(Direction.UP, 2f, SCROLL_SPEED)
                device.waitForIdle()
            }

            navigateToTimelineScreen(device)
            device.wait(Until.hasObject(By.textStartsWith("Stop")), LOAD_TIMEOUT)

            device.findObject(By.res(PACKAGE_NAME, TIMELINE_NEXT_STOP_RES_ID)).click()
            device.findObject(By.res(PACKAGE_NAME, TIMELINE_PREVIOUS_STOP_RES_ID)).click()

            device.wait(findObject(By.text(TRIP_LIST_TAB_TEXT)), LOAD_TIMEOUT).click()

            selectStopFromStopList(device,0)

            waitForArriveActionVisibility(device)
            navigateToNextAction(device)
            waitForArriveActionVisibility(device)
            navigateToPreviousAction(device)
            waitForArriveActionVisibility(device)

            device.findObject(By.res(PACKAGE_NAME, TOOLBAR_HAMBURGER_MENU_DESCRIPTION)).click()
            device.wait(Until.hasObject(By.textStartsWith("TRIP")), LOAD_TIMEOUT)
            endTrip(device)
        }
}