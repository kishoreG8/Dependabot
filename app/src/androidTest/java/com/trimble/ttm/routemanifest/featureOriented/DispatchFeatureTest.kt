package com.trimble.ttm.routemanifest.featureOriented

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.trimble.ttm.routemanifest.ALERT_CANCEL_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ALERT_DIALOG_WAIT_TIME
import com.trimble.ttm.routemanifest.ALERT_NO_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ALERT_OK_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ALERT_TEXT
import com.trimble.ttm.routemanifest.ALERT_YES_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ARRIVAL_LABEL_TEXT
import com.trimble.ttm.routemanifest.ARRIVE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ARRIVE_COMPLETE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.DEPARTURE_LABEL_TEXT
import com.trimble.ttm.routemanifest.DEPART_BUTTON_TEXT
import com.trimble.ttm.routemanifest.DEPART_COMPLETE_BUTTON_TEXT
import com.trimble.ttm.routemanifest.ETA_LABEL_TEXT
import com.trimble.ttm.routemanifest.ETA_TEXT
import com.trimble.ttm.routemanifest.MILES_TEXT
import com.trimble.ttm.routemanifest.PREVIOUS_ARROW_WAIT_TIME
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.ROUTE_CALCULATION_WAIT_TIME
import com.trimble.ttm.routemanifest.START_BUTTON_TEXT
import com.trimble.ttm.routemanifest.STOPS_TEXT
import com.trimble.ttm.routemanifest.WAIT_TIME_TWO_SECONDS
import com.trimble.ttm.routemanifest.YOUR_TRIP_ALERT_TEXT
import com.trimble.ttm.routemanifest.actionOpenDrawer
import com.trimble.ttm.routemanifest.checkDispatchListIsDisplayed
import com.trimble.ttm.routemanifest.checkIfAnyTripIsActiveAndEndTheTrip
import com.trimble.ttm.routemanifest.checkViewIsDisplayed
import com.trimble.ttm.routemanifest.checkViewIsDisplayedAndClickable
import com.trimble.ttm.routemanifest.clickElementFromListBasedOnPosition
import com.trimble.ttm.routemanifest.dismissTripAlertDialog
import com.trimble.ttm.routemanifest.endTrip
import com.trimble.ttm.routemanifest.getChildFromParentUsingPosition
import com.trimble.ttm.routemanifest.scrollToAndClickElement
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


@RunWith(AndroidJUnit4::class)
@LargeTest
class DispatchFeatureTest {

    @Rule
    @JvmField
    var dispatchActivityTestRule = ActivityScenarioRule(DispatchListActivity::class.java)

    @Rule
    @JvmField
    var grantPermissionRuleForOverlayPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.SYSTEM_ALERT_WINDOW)

    @Rule
    @JvmField
    var grantPermissionRuleForNotificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun setup(){
        checkIfAnyTripIsActiveAndEndTheTrip()
    }

    @Test
    fun checkAlertForNextTripIsDisplayed(){
        if(waitForAlertDialogUIWithSubstring(YOUR_TRIP_ALERT_TEXT, ALERT_DIALOG_WAIT_TIME)){
            checkViewIsDisplayedAndClickable(ALERT_YES_BUTTON_TEXT)
            checkViewIsDisplayedAndClickable(ALERT_NO_BUTTON_TEXT).perform(click())
        }
    }

    @Test
    fun checkTheAppropriateButtonsAreDisplayedBasedOnTheActionsOfDipatchInStopDetailScreen() {
        //Check each stop actions are in correct order as they are in the dispatch xml (Like if a stop has arrive action then arrive button needs to displayed in the stop detail screen)
        dismissTripAlertDialog()
        clickElementFromListBasedOnPosition(
            checkViewIsDisplayed(R.id.dispatchList)
        )
        checkViewIsDisplayed(R.id.startTripButton).check(
            ViewAssertions.matches(
                withText(
                    START_BUTTON_TEXT
                )
            )
        ).perform(
            click()
        )
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
        for (i in 0..6) {
            scrollToAndClickElement(Espresso.onView(ViewMatchers.withId(R.id.tripInfoRecycler)), i)
            waitFor(WAIT_TIME_TWO_SECONDS)
            when (i) {
                0, 1, 3 -> {
                    checkViewIsDisplayedAndClickable(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(ARRIVE_BUTTON_TEXT)
                        )
                    )
                    Espresso.onView(
                        Matchers.allOf(
                            ViewMatchers.withId(R.id.departed),
                            Matchers.not(ViewMatchers.isDisplayed())
                        )
                    ).check(
                        ViewAssertions.matches(
                            Matchers.not(
                                withText(
                                    DEPART_BUTTON_TEXT
                                )
                            )
                        )
                    )
                }

                2, 4 -> {
                    Espresso.onView(
                        Matchers.allOf(
                            ViewMatchers.withId(R.id.arrived),
                            Matchers.not(ViewMatchers.isDisplayed())
                        )
                    )
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_BUTTON_TEXT)
                        )
                    )
                }

                5, 6 -> {
                    checkViewIsDisplayedAndClickable(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(ARRIVE_BUTTON_TEXT)
                        )
                    )
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_BUTTON_TEXT)
                        )
                    )
                }
            }
            Espresso.pressBack()
        }
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        endTrip()
        dismissTripAlertDialog()
        checkViewIsDisplayed(R.id.dispatchList)
    }

    // This test will be included once MAPP-8547 is done.
    @Ignore
    @Test
    fun verifyForArrivalAndDepartureFunctionalityInStopDetailAndStopListScreen() {
        /*
            Check each stop actions are in correct order
            Check each stop actions text are changed when it is clicked
            Check Stop detail gets updated with appropriate labels when a action is performed (Like when arrive button is clicked check arrival label is updated in the stop detail screen)
            Check appropriate labels are updated in the stop list screen when a action is performed (Like when arrive button is clicked check arrival label appears in the appropriate stop list item)
         */
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
        for (i in 0..6) {
            scrollToAndClickElement(
                Espresso.onView(ViewMatchers.withId(R.id.tripInfoRecycler)),
                i
            )
            when (i) {
                0 -> {
                    Espresso.onView(
                        Matchers.allOf(
                            ViewMatchers.withId(R.id.departed),
                            Matchers.not(ViewMatchers.isDisplayed())
                        )
                    )
                    checkViewIsDisplayed(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(
                                ARRIVE_BUTTON_TEXT
                            )
                        )
                    )
                        .perform(click())
                    waitFor(PREVIOUS_ARROW_WAIT_TIME)
                    checkViewIsDisplayedAndClickable(R.id.previousArrow).perform(click())
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    checkViewIsDisplayed(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(
                                ARRIVE_COMPLETE_BUTTON_TEXT
                            )
                        )
                    )
                    Espresso.pressBack()
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    Espresso.onView(
                        Matchers.allOf(
                            ViewMatchers.withId(R.id.stopArrivedOn),
                            Matchers.not(ViewMatchers.isDisplayed())
                        )
                    )
                }

                1, 3 -> {
                    checkViewIsDisplayed(R.id.etaOrArrivedTimeLabel).check(
                        ViewAssertions.matches(
                            withText(ETA_LABEL_TEXT)
                        )
                    )
                    Espresso.onView(
                        Matchers.allOf(
                            ViewMatchers.withId(R.id.departed),
                            Matchers.not(ViewMatchers.isDisplayed())
                        )
                    )
                    checkViewIsDisplayed(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(
                                ARRIVE_BUTTON_TEXT
                            )
                        )
                    )
                        .perform(click())
                    waitFor(PREVIOUS_ARROW_WAIT_TIME)
                    checkViewIsDisplayedAndClickable(R.id.previousArrow).perform(click())
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    checkViewIsDisplayed(R.id.etaOrArrivedTimeLabel).check(
                        ViewAssertions.matches(
                            withText(ARRIVAL_LABEL_TEXT)
                        )
                    )
                    checkViewIsDisplayed(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(
                                ARRIVE_COMPLETE_BUTTON_TEXT
                            )
                        )
                    )
                    Espresso.pressBack()
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    Espresso.onView(
                        Matchers.allOf(
                            getChildFromParentUsingPosition(R.id.tripInfoRecycler, i),
                            ViewMatchers.withId(R.id.stopArrivedOn),
                            ViewMatchers.withSubstring(ARRIVAL_LABEL_TEXT),
                            ViewMatchers.isDisplayed()
                        )
                    )
                }

                2, 4 -> {
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_BUTTON_TEXT)
                        )
                    )
                        .perform(click())
                    waitFor(PREVIOUS_ARROW_WAIT_TIME)
                    checkViewIsDisplayedAndClickable(R.id.previousArrow).perform(click())
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    checkViewIsDisplayed(R.id.depatureLabel).check(
                        ViewAssertions.matches(
                            withText(DEPARTURE_LABEL_TEXT)
                        )
                    )
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_COMPLETE_BUTTON_TEXT)
                        )
                    )
                    Espresso.pressBack()
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    Espresso.onView(
                        Matchers.allOf(
                            getChildFromParentUsingPosition(R.id.tripInfoRecycler, i),
                            ViewMatchers.withId(R.id.stopDepartedOn),
                            ViewMatchers.withSubstring(DEPARTURE_LABEL_TEXT),
                            ViewMatchers.isDisplayed()
                        )
                    )
                }

                5, 6 -> {
                    checkViewIsDisplayed(R.id.etaOrArrivedTimeLabel).check(
                        ViewAssertions.matches(
                            withText(ETA_LABEL_TEXT)
                        )
                    )
                    checkViewIsDisplayed(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(
                                ARRIVE_BUTTON_TEXT
                            )
                        )
                    )
                        .perform(click())
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_BUTTON_TEXT)
                        )
                    )
                        .perform(click())
                    waitFor(PREVIOUS_ARROW_WAIT_TIME)
                    checkViewIsDisplayedAndClickable(R.id.previousArrow).perform(click())
                    waitFor(WAIT_TIME_TWO_SECONDS)
                    checkViewIsDisplayed(R.id.etaOrArrivedTimeLabel).check(
                        ViewAssertions.matches(
                            withText(ARRIVAL_LABEL_TEXT)
                        )
                    )
                    checkViewIsDisplayed(R.id.depatureLabel).check(
                        ViewAssertions.matches(
                            withText(DEPARTURE_LABEL_TEXT)
                        )
                    )
                    checkViewIsDisplayedAndClickable(R.id.arrived).check(
                        ViewAssertions.matches(
                            withText(ARRIVE_COMPLETE_BUTTON_TEXT)
                        )
                    )
                    checkViewIsDisplayedAndClickable(R.id.departed).check(
                        ViewAssertions.matches(
                            withText(DEPART_COMPLETE_BUTTON_TEXT)
                        )
                    )
                    if (i == 6) {
                        if (waitForAlertDialogUIWithSubstring(
                                ALERT_TEXT,
                                ALERT_DIALOG_WAIT_TIME
                            )
                        ) {
                            checkViewIsDisplayedAndClickable(ALERT_OK_BUTTON_TEXT)
                            checkViewIsDisplayedAndClickable(ALERT_CANCEL_BUTTON_TEXT).perform(
                                click()
                            )
                        }
                        Espresso.pressBack()
                        waitFor(WAIT_TIME_TWO_SECONDS)
                        Espresso.onView(
                            Matchers.allOf(
                                getChildFromParentUsingPosition(R.id.tripInfoRecycler, i),
                                ViewMatchers.withId(R.id.stopArrivedOn),
                                ViewMatchers.withSubstring(ARRIVAL_LABEL_TEXT),
                                ViewMatchers.isDisplayed(),
                                ViewMatchers.withId(R.id.stopDepartedOn),
                                ViewMatchers.withSubstring(DEPARTURE_LABEL_TEXT),
                                ViewMatchers.isDisplayed()
                            )
                        )
                    } else {
                        Espresso.pressBack()
                        waitFor(WAIT_TIME_TWO_SECONDS)
                        Espresso.onView(
                            Matchers.allOf(
                                getChildFromParentUsingPosition(R.id.tripInfoRecycler, i),
                                ViewMatchers.withId(R.id.stopArrivedOn),
                                ViewMatchers.withSubstring(ARRIVAL_LABEL_TEXT),
                                ViewMatchers.isDisplayed(),
                                ViewMatchers.withId(R.id.stopDepartedOn),
                                ViewMatchers.withSubstring(DEPARTURE_LABEL_TEXT),
                                ViewMatchers.isDisplayed()
                            )
                        )
                    }
                }
            }
        }
        checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
        endTrip()
        dismissTripAlertDialog()
        checkDispatchListIsDisplayed()
    }

    @After
    fun tearDown() = dispatchActivityTestRule.scenario.close()

}