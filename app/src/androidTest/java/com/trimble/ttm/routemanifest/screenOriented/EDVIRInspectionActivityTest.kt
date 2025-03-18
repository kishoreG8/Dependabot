package com.trimble.ttm.routemanifest.screenOriented

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.trimble.ttm.formlibrary.ui.activities.EDVIRInspectionsActivity
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.WAIT_TIME_TWO_SECONDS
import com.trimble.ttm.routemanifest.waitFor
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
@LargeTest
class EDVIRInspectionActivityTest {

    @get:Rule
    var activityRule = ActivityScenarioRule(EDVIRInspectionsActivity::class.java)

    @Test
    fun checkForFloatingIconVisibility() {
        waitFor(WAIT_TIME_TWO_SECONDS)
        Espresso.onView(ViewMatchers.withId(R.id.inspectionMenuView))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @After
    fun tearDown() = activityRule.scenario.close()

}