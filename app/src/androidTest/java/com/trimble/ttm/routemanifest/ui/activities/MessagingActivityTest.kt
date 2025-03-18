package com.trimble.ttm.routemanifest.ui.activities


import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.routemanifest.waitForUIWithText
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MessagingActivityTest {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MessagingActivity::class.java)

    @Test
    fun messagingActivityTest() {
        val appCompatImageView = onView(
            allOf(
                withId(R.id.appIcon), withContentDescription("appIcon"),
                childAtPosition(
                    allOf(
                        withId(R.id.toolbar),
                        childAtPosition(
                            withClassName(`is`("androidx.constraintlayout.widget.ConstraintLayout")),
                            0
                        )
                    ),
                    0
                ),
                isDisplayed()
            )
        )
        appCompatImageView.perform(click())

        val tabView = onView(
            allOf(
                withContentDescription("Sent"),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.messageTabLayout),
                        0
                    ),
                    1
                ),
                isDisplayed()
            )
        )
        tabView.perform(click())

        val tabView2 = onView(
            allOf(
                withContentDescription("Draft"),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.messageTabLayout),
                        0
                    ),
                    2
                ),
                isDisplayed()
            )
        )
        tabView2.perform(click())

        val tabView3 = onView(
            allOf(
                withContentDescription("Trash"),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.messageTabLayout),
                        0
                    ),
                    3
                ),
                isDisplayed()
            )
        )
        tabView3.perform(click())

        val tabView4 = onView(
            allOf(
                withContentDescription("Inbox"),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.messageTabLayout),
                        0
                    ),
                    0
                ),
                isDisplayed()
            )
        )
        tabView4.perform(click())
    }

    private fun childAtPosition(
        parentMatcher: Matcher<View>, position: Int
    ): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
}
