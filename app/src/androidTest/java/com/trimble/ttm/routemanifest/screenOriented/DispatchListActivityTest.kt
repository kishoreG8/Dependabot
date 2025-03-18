package com.trimble.ttm.routemanifest.screenOriented

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.trimble.ttm.routemanifest.ACTIVE_TRIP_TAG_TEXT
import com.trimble.ttm.routemanifest.ACTIVE_TRIP_TAG_TIME
import com.trimble.ttm.routemanifest.ALERT_DIALOG_WAIT_TIME
import com.trimble.ttm.routemanifest.ALERT_NO_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ALERT_YES_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ARRIVE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ETA_TEXT
import com.trimble.ttm.routemanifest.HamburgerMenuTestResources
import com.trimble.ttm.routemanifest.MILES_TEXT
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.ROUTE_CALCULATION_WAIT_TIME
import com.trimble.ttm.routemanifest.SECOND_POSITION
import com.trimble.ttm.routemanifest.STOPS_TEXT
import com.trimble.ttm.routemanifest.STOP_LIST_TEXT
import com.trimble.ttm.routemanifest.WAIT_TIME_TWO_SECONDS
import com.trimble.ttm.routemanifest.YOUR_TRIP_ALERT_TEXT
import com.trimble.ttm.routemanifest.actionCloseDrawer
import com.trimble.ttm.routemanifest.actionOpenDrawer
import com.trimble.ttm.routemanifest.checkDispatchListIsDisplayed
import com.trimble.ttm.routemanifest.checkIfAnyTripIsActiveAndEndTheTrip
import com.trimble.ttm.routemanifest.checkTripInfoIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayedAndClickable
import com.trimble.ttm.routemanifest.checkViewIsNotDisplayed
import com.trimble.ttm.routemanifest.clickElementFromListBasedOnPosition
import com.trimble.ttm.routemanifest.dismissTripAlertDialog
import com.trimble.ttm.routemanifest.endTrip
import com.trimble.ttm.routemanifest.navigateBackToTripList
import com.trimble.ttm.routemanifest.scrollToAndClickElement
import com.trimble.ttm.routemanifest.scrollToLastPosition
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.waitFor
import com.trimble.ttm.routemanifest.waitForAlertDialogUIWithSubstring
import com.trimble.ttm.routemanifest.waitForUIWithText
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
@LargeTest
class DispatchListActivityTest {

    @get:Rule
    var dispatchActivityTestRule = ActivityScenarioRule(DispatchListActivity::class.java)

    private val resources = HamburgerMenuTestResources()

    @Before
    fun setup(){
        checkIfAnyTripIsActiveAndEndTheTrip()
    }

    @Test
    fun checkDrawerOpenInStopListScreen() {
        dismissTripAlertDialog()
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionCloseDrawer())
    }


    @Test
    fun checkForFormLibraryOptionInDrawerInDispatchListScreen() {
        dismissTripAlertDialog()
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        onView(withText(R.string.menu_form_library))
            .perform(click())
    }

    @Test
    fun checkTripListIsClickableAndScrollableInDispatchListScreen() {
        dismissTripAlertDialog()
        clickElementFromListBasedOnPosition(
            checkViewIsDisplayed(R.id.dispatchList)
        )
        pressBack()
        scrollToLastPosition(checkViewIsDisplayed(R.id.dispatchList))
    }

    @Test
    fun checkYourNextTripPopupIsDisplayedAndButtonsAreClickableInDispatchListScreen() {
        if (waitForAlertDialogUIWithSubstring(YOUR_TRIP_ALERT_TEXT, ALERT_DIALOG_WAIT_TIME)) {
            checkViewIsDisplayedAndClickable(ALERT_YES_BUTTON_TEXT)
            checkViewIsDisplayedAndClickable(ALERT_NO_BUTTON_TEXT).perform(click())
        }
    }

    @Test
    fun checkIfTripListCameWhenTripStartedViaYourNextTripPopupInDispatchDetailScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        onView(
            Matchers.allOf(
                withId(R.id.startTripButton),
                Matchers.not(ViewMatchers.isDisplayed())
            )
        )
        clickElementFromListBasedOnPosition(checkViewIsDisplayed(R.id.tripInfoRecycler))
        waitFor(WAIT_TIME_TWO_SECONDS)
        checkViewIsDisplayed(R.id.arrived).check(
            matches(
                withText(
            ARRIVE_BUTTON_TEXT)
            )
        )
        pressBack()
        endTrip()
        checkDispatchListIsDisplayed()
    }

    @Test
    fun checkStopListIsScrollableAndClickableInStopListScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        waitFor(WAIT_TIME_TWO_SECONDS)
        clickElementFromListBasedOnPosition(checkViewIsDisplayed(R.id.tripInfoRecycler))
        pressBack()
        scrollToLastPosition(checkViewIsDisplayed(R.id.tripInfoRecycler))
        endTrip()
    }

    // This test won't fail everytime but sometimes it will fail due to MAPP-8546.
    @Ignore
    @Test
    fun checkIfActiveTripIsDisplayedWhenBackIsPressedFromTheActiveTripInDispatchListScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        checkTripInfoIsDisplayed()
        waitForUIWithText(MILES_TEXT, ROUTE_CALCULATION_WAIT_TIME)
        checkViewIsDisplayed(R.id.totalMiles).check(
            matches(
                withSubstring(
                    MILES_TEXT
                )
            )
        )
        checkViewIsDisplayed(R.id.totalStops).check(
            matches(
                withSubstring(
                    STOPS_TEXT
                )
            )
        )
        checkViewIsDisplayed(R.id.totalHours).check(
            matches(
                withSubstring(
                    ETA_TEXT
                )
            )
        )
        pressBack()
        if(waitForUIWithText(ACTIVE_TRIP_TAG_TEXT, ACTIVE_TRIP_TAG_TIME)) {
            onView(
                Matchers.allOf(
                    withId(R.id.active_trip_chip),
                    ViewMatchers.isDisplayed()
                )
            ).check(matches(withSubstring(ACTIVE_TRIP_TAG_TEXT)))
            clickElementFromListBasedOnPosition(checkViewIsDisplayed(R.id.dispatchList))
            checkTripInfoIsDisplayed()
            endTrip()
        }
    }

    // This test won't fail everytime but sometimes it will fail due to MAPP-8638
    @Test
    fun checkTripPreviewIsDisplayedWithoutStartButtonWhenNotActiveDispatchItemIsClickedInDispatchListScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        checkTripInfoIsDisplayed()
        pressBack()
        scrollToAndClickElement(onView(withId(R.id.dispatchList)), SECOND_POSITION)
        checkViewIsNotDisplayed(R.id.startTripButton)
        checkViewIsDisplayed(R.id.previewOnlyTextView)
        waitFor(WAIT_TIME_TWO_SECONDS)
        navigateBackToTripList()
        scrollToAndClickElement(checkViewIsDisplayed(R.id.dispatchList))
        waitFor(WAIT_TIME_TWO_SECONDS)
        checkTripInfoIsDisplayed()
        endTrip()
    }


    @Test
    fun checkForInspectionOptionInDrawerInDispatchListScreen() {
        dismissTripAlertDialog()
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        onView(withText(R.string.menu_inspections))
            .perform(click())
    }

    @Test
    fun verifyStopListMenuInHamburgerIfOpenedFromTripDetailScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        pressBack()
        onView(withId(com.trimble.ttm.formlibrary.R.id.drawer_layout))
            .perform(actionOpenDrawer())
        waitFor(WAIT_TIME_TWO_SECONDS)
        onView(withText(resources.getString(R.string.menu_stop_list)))
            .check(matches(withText(STOP_LIST_TEXT))).perform(click())
        endTrip()
    }


    @After
    fun tearDown() = dispatchActivityTestRule.scenario.close()

}