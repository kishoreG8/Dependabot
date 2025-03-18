package com.trimble.ttm.routemanifest.screenOriented

import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.trimble.ttm.routemanifest.ALERT_YES_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ARRIVE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.WAIT_TIME_TWO_SECONDS
import com.trimble.ttm.routemanifest.checkIfAnyTripIsActiveAndEndTheTrip
import com.trimble.ttm.routemanifest.checkTripInfoIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayedAndClickable
import com.trimble.ttm.routemanifest.clickElementFromListBasedOnPosition
import com.trimble.ttm.routemanifest.dismissTripAlertDialog
import com.trimble.ttm.routemanifest.endTrip
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.waitFor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
@LargeTest
class StopDetailActivityTest {

    @get:Rule
    var activityTestRule = ActivityScenarioRule(DispatchListActivity::class.java)


    @Before
    fun setup(){
        checkIfAnyTripIsActiveAndEndTheTrip()
    }

    @Test
    fun checkToolBarBackButtonFunctionalityInStopDetailScreen() {
        dismissTripAlertDialog(ALERT_YES_BUTTON_TEXT)
        checkTripInfoIsDisplayed()
        waitFor(WAIT_TIME_TWO_SECONDS)
        clickElementFromListBasedOnPosition(checkViewIsDisplayed(R.id.tripInfoRecycler))
        waitFor(WAIT_TIME_TWO_SECONDS)
        checkViewIsDisplayed(R.id.arrived).check(ViewAssertions.matches(ViewMatchers.withText(
            ARRIVE_BUTTON_TEXT)))
        checkViewIsDisplayedAndClickable(R.id.appIcon).perform(ViewActions.click())
        checkTripInfoIsDisplayed()
        endTrip()
    }

    @After
    fun tearDown() = activityTestRule.scenario.close()

}