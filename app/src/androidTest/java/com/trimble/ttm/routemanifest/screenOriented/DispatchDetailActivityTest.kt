package com.trimble.ttm.routemanifest.screenOriented

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.trimble.ttm.routemanifest.ALERT_YES_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ARRIVE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ETA_CALCULATION_TIME
import com.trimble.ttm.routemanifest.ETA_TEXT
import com.trimble.ttm.routemanifest.FIRST_STOP_NAME
import com.trimble.ttm.routemanifest.LIST_SECTION_TEXT
import com.trimble.ttm.routemanifest.MILES_TEXT
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.ROUTE_CALCULATION_WAIT_TIME
import com.trimble.ttm.routemanifest.STOPS_TEXT
import com.trimble.ttm.routemanifest.TIMELINE_SECTION_TEXT
import com.trimble.ttm.routemanifest.WAIT_TIME_TWO_SECONDS
import com.trimble.ttm.routemanifest.actionOpenDrawer
import com.trimble.ttm.routemanifest.checkDispatchListIsDisplayed
import com.trimble.ttm.routemanifest.checkIfAnyTripIsActiveAndEndTheTrip
import com.trimble.ttm.routemanifest.checkTripInfoIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayed
import com.trimble.ttm.routemanifest.clickElementFromListBasedOnPosition
import com.trimble.ttm.routemanifest.dismissTripAlertDialog
import com.trimble.ttm.routemanifest.endTrip
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.waitFor
import com.trimble.ttm.routemanifest.waitForUIWithText
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DispatchDetailActivityTest {

    @Rule
    @JvmField
    var activityTestRule = ActivityScenarioRule(DispatchListActivity::class.java)

    @Before
    fun setup(){
        checkIfAnyTripIsActiveAndEndTheTrip()
    }

    @Test
    fun checkRouteCalculationGetsUpdatedWhenTripStartedInDispatchDetailScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        waitForUIWithText(MILES_TEXT, ROUTE_CALCULATION_WAIT_TIME)
        checkViewIsDisplayed(R.id.totalMiles).check(
            ViewAssertions.matches(
                ViewMatchers.withSubstring(
                    MILES_TEXT
                )
            )
        )
        checkViewIsDisplayed(R.id.totalStops).check(
            ViewAssertions.matches(
                ViewMatchers.withSubstring(
                    STOPS_TEXT
                )
            )
        )
        checkViewIsDisplayed(R.id.totalHours).check(
            ViewAssertions.matches(
                ViewMatchers.withSubstring(
                    ETA_TEXT
                )
            )
        )
        endTrip()
    }

    @Test
    fun checkForStopListIsClickableAndScrollable(){
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        waitFor(WAIT_TIME_TWO_SECONDS)
        clickElementFromListBasedOnPosition(checkViewIsDisplayed(R.id.tripInfoRecycler))
        waitFor(WAIT_TIME_TWO_SECONDS)
        checkViewIsDisplayed(R.id.arrived).check(
            ViewAssertions.matches(
                ViewMatchers.withText(
                    ARRIVE_BUTTON_TEXT
                )
            )
        )
        Espresso.pressBack()
        checkViewIsDisplayed(R.id.tripInfoRecycler).perform(RecyclerViewActions.scrollToLastPosition<RecyclerView.ViewHolder>())
        endTrip()
    }

    @Test
    fun checkListTabAndTimeLineTabIsDisplayedInStopListScreen() {
        //Check list and timeline tab and Also check the bottom bar in timeline
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        val viewPager = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(R.id.viewPager),
                ViewMatchers.isDisplayed()
            )
        )
        checkViewIsDisplayed(LIST_SECTION_TEXT)
        checkViewIsDisplayed(TIMELINE_SECTION_TEXT)
        viewPager.perform(ViewActions.swipeLeft())
        if(waitForUIWithText(FIRST_STOP_NAME, ETA_CALCULATION_TIME)) {
            checkViewIsDisplayed(R.id.card_fragment_container).check(
                ViewAssertions.matches(
                    ViewMatchers.withText(
                        Matchers.containsString(
                            FIRST_STOP_NAME
                        )
                    )
                )
            )
        }
        viewPager.perform(ViewActions.swipeRight())
        checkTripInfoIsDisplayed()
        endTrip()
    }

    @Test
    fun checkEndTripButtonFunctionalityInStopListScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        endTrip()
        checkDispatchListIsDisplayed()
    }



    @After
    fun tearDown() = activityTestRule.scenario.close()
}