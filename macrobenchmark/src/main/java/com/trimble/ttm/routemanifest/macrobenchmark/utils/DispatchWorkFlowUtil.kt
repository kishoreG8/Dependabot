package com.trimble.ttm.routemanifest.macrobenchmark.utils

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until

internal fun waitForTripListFetchAndPressNoInAlertDialog(device: UiDevice) {
    waitForTripListFetch(device)
    device.findObject(UiSelector().textStartsWith(NEGATIVE_BUTTON_TEXT))
        .clickAndWaitForNewWindow()
}

internal fun waitForTripListFetch(device: UiDevice) {
    val isAnyTripAvailable =
        device.wait(
            Until.hasObject(By.textStartsWith(TRIP_START_SUGGESTION_ALERT_DIALOG_START_TEXT)),
            AUTHENTICATION_AND_TRIP_LIST_LOAD_TIMEOUT
        )
    check(isAnyTripAvailable)
}

internal fun waitForStopListFetch(device: UiDevice) {
    val isStopListLoadingComplete =
        device.wait(
            Until.hasObject(By.res(PACKAGE_NAME, STOP_LIST_RIGHT_ARROW_ICON_DESCRIPTION)),
            LOAD_TIMEOUT
        )
    check(isStopListLoadingComplete)
}

internal fun endTrip(device: UiDevice) {
    with(device) {
        findObject(By.desc(TOOLBAR_HAMBURGER_MENU_DESCRIPTION)).click()
        wait(Until.findObject(By.textStartsWith(END_TRIP_HAMBURGER_MENU_TEXT)), LOAD_TIMEOUT).click()
        wait(Until.findObject(By.text(POSITIVE_BUTTON_TEXT)), LOAD_TIMEOUT).click()
    }
}

internal fun startTrip(device: UiDevice) =
    device.findObject(By.text(TRIP_START_TEXT)).click()

internal fun selectFirstVisibleTrip(device: UiDevice) {
    val tripListRecyclerView =
        device.findObject(By.res(PACKAGE_NAME, TRIP_LIST_RECYCLERVIEW_RES_ID))
    val tripList = tripListRecyclerView.children
    if (tripList.size == 0) return
    tripList[0].click()
}

internal fun selectStopFromStopList(device: UiDevice, stopId: Int) {
    with(device) {
        wait(Until.hasObject(By.hasChild(By.res(PACKAGE_NAME, STOP_LIST_RECYCLER_RES_ID))), LOAD_TIMEOUT)
        findObject(By.res(PACKAGE_NAME, STOP_LIST_RECYCLER_RES_ID)).children[stopId].click()
    }
}

internal fun navigateToStopListScreen(device: UiDevice) {
    with(device) {
        waitForTripListFetchAndPressNoInAlertDialog(this)
        selectFirstVisibleTrip(this)
        wait(Until.findObject(By.text(TRIP_LIST_TAB_TEXT)), LOAD_TIMEOUT).click()
        waitForStopListFetch(this)
    }
}

internal fun navigateToTimelineScreen(device: UiDevice) =
    device.wait(Until.findObject(By.text(TIMELINE_TAB_TEXT)), LOAD_TIMEOUT).click()

internal fun navigateToStopDetailScreenAndSelectFirstStop(device: UiDevice) =
    with(device) {
        navigateToStopListScreen(this)
        waitForRouteCalculationResult(this)
        startTrip(this)
        selectStopFromStopList(this, stopId = 0)
    }

internal fun navigateToNextAction(device: UiDevice) =
    device.wait(Until.findObject(By.res(PACKAGE_NAME, NEXT_ACTION_RES_ID)), LOAD_TIMEOUT).click()

internal fun navigateToPreviousAction(device: UiDevice) =
    device.wait(Until.findObject(By.res(PACKAGE_NAME, PREVIOUS_ACTION_RES_ID)), LOAD_TIMEOUT).click()

internal fun waitForRouteCalculationResult(device: UiDevice) {
    val isRouteCalculationComplete =
        device.wait(Until.gone(By.textStartsWith(ROUTE_CALCULATION_PROGRESS_TEXT)), ROUTE_CALCULATION_TIMEOUT)
    check(isRouteCalculationComplete)
}

internal fun waitForArriveActionVisibility(device: UiDevice) =
    device.wait(Until.hasObject(By.textStartsWith(ARRIVE_TEXT)), LOAD_TIMEOUT)

internal fun waitForArriveCompleteVisibility(device: UiDevice) =
    device.wait(Until.hasObject(By.textStartsWith(ARRIVE_COMPLETE_TEXT)), LOAD_TIMEOUT)

internal fun waitForDepartCompleteVisibility(device: UiDevice) =
    device.wait(Until.hasObject(By.textStartsWith(DEPART_COMPLETE_TEXT)), LOAD_TIMEOUT)

internal fun completeArriveAndDepartActions(device: UiDevice) =
    with(device) {
        waitForArriveActionVisibility(this)
        findObject(By.textStartsWith(ARRIVE_TEXT)).click()
        waitForArriveCompleteVisibility(this)
        findObject(By.textStartsWith(DEPART_TEXT)).also { uiObject ->
            uiObject.wait(Until.enabled(true), LOAD_TIMEOUT)
        }
        findObject(By.textStartsWith(DEPART_TEXT)).click()
    }