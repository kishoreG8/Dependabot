package com.trimble.ttm.routemanifest.screenOriented

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.MESSAGES_MENU_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.SCREEN
import com.trimble.ttm.formlibrary.utils.Screen
import com.trimble.ttm.formlibrary.utils.TRASH_INDEX
import com.trimble.ttm.routemanifest.DRAFT_TEXT
import com.trimble.ttm.routemanifest.FIRST_POSITION
import com.trimble.ttm.routemanifest.FORMS_TEXT
import com.trimble.ttm.routemanifest.FOURTH_POSITION
import com.trimble.ttm.routemanifest.HamburgerMenuTestResources
import com.trimble.ttm.routemanifest.INBOX_TEXT
import com.trimble.ttm.routemanifest.INSPECTIONS_TEXT
import com.trimble.ttm.routemanifest.MESSAGING_TEXT
import com.trimble.ttm.routemanifest.SECOND_POSITION
import com.trimble.ttm.routemanifest.SENT_TEXT
import com.trimble.ttm.routemanifest.THIRD_POSITION
import com.trimble.ttm.routemanifest.TRASH_TEXT
import com.trimble.ttm.routemanifest.TRIP_LIST_TEXT
import com.trimble.ttm.routemanifest.actionCloseDrawer
import com.trimble.ttm.routemanifest.actionOpenDrawer
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MessagingActivityTest {

    private val messagingActivityIntent = Intent(
        ApplicationProvider.getApplicationContext(),
        MessagingActivity::class.java
    ).apply {
        putExtra(MESSAGES_MENU_TAB_INDEX, TRASH_INDEX)
        putExtra(SCREEN, Screen.DISPATCH_LIST.ordinal)
    }

    @Rule
    @JvmField
    var grantPermissionRuleForOverlayPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.SYSTEM_ALERT_WINDOW)

    @Rule
    @JvmField
    var grantPermissionRuleForNotificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)


    private val resources = HamburgerMenuTestResources()

    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule<MessagingActivity>(messagingActivityIntent)

    @Test
    fun messagingActivityTest() {
        val appCompatImageView = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.appIcon),
                ViewMatchers.withContentDescription("appIcon"),
                childAtPosition(
                    Matchers.allOf(
                        ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.toolbar),
                        childAtPosition(
                            ViewMatchers.withClassName(Matchers.`is`("androidx.constraintlayout.widget.ConstraintLayout")),
                            FIRST_POSITION
                        )
                    ),
                    FIRST_POSITION
                ),
                isDisplayed()
            )
        )
        appCompatImageView.perform(ViewActions.click())

        val tabView = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withContentDescription(SENT_TEXT),
                childAtPosition(
                    childAtPosition(
                        ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.messageTabLayout),
                        FIRST_POSITION
                    ),
                    SECOND_POSITION
                ),
                isDisplayed()
            )
        )
        tabView.perform(ViewActions.click())

        val tabView2 = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withContentDescription(DRAFT_TEXT),
                childAtPosition(
                    childAtPosition(
                        ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.messageTabLayout),
                        FIRST_POSITION
                    ),
                    THIRD_POSITION
                ),
                isDisplayed()
            )
        )
        tabView2.perform(ViewActions.click())

        val tabView3 = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withContentDescription(TRASH_TEXT),
                childAtPosition(
                    childAtPosition(
                        ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.messageTabLayout),
                        FIRST_POSITION
                    ),
                    FOURTH_POSITION
                ),
                isDisplayed()
            )
        )
        tabView3.perform(ViewActions.click())

        val tabView4 = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withContentDescription(INBOX_TEXT),
                childAtPosition(
                    childAtPosition(
                        ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.messageTabLayout),
                        FIRST_POSITION
                    ),
                    FIRST_POSITION
                ),
                isDisplayed()
            )
        )
        tabView4.perform(ViewActions.click())
    }

    @Test
    fun verifyTripListMenuInHamburgerIfOpenedFromTripListScreen() {
        Espresso.onView(ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.drawer_layout))
            .perform(actionOpenDrawer())
        Espresso.onView(ViewMatchers.withText(resources.getString(com.trimble.ttm.formlibrary.R.string.menu_trip_list)))
            .check(ViewAssertions.matches(ViewMatchers.withText(TRIP_LIST_TEXT)))
        Espresso.onView(ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.drawer_layout))
            .perform(actionCloseDrawer())
    }

    @Test
    fun checkHamburgerMenuTexts() {
        Espresso.onView(ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.drawer_layout))
            .perform(actionOpenDrawer())
        Espresso.onView(ViewMatchers.withText(resources.getString(com.trimble.ttm.formlibrary.R.string.menu_messaging)))
            .check(ViewAssertions.matches(ViewMatchers.withText(MESSAGING_TEXT)))
        Espresso.onView(ViewMatchers.withText(resources.getString(com.trimble.ttm.formlibrary.R.string.menu_form_library)))
            .check(ViewAssertions.matches(ViewMatchers.withText(FORMS_TEXT)))
        Espresso.onView(ViewMatchers.withText(resources.getString(com.trimble.ttm.formlibrary.R.string.menu_inspections)))
            .check(ViewAssertions.matches(ViewMatchers.withText(INSPECTIONS_TEXT)))
        Espresso.onView(ViewMatchers.withText(resources.getString( com.trimble.ttm.formlibrary.R.string.menu_trip_list)))
            .check(ViewAssertions.matches(ViewMatchers.withText(TRIP_LIST_TEXT)))
        Espresso.onView(ViewMatchers.withId(com.trimble.ttm.formlibrary.R.id.drawer_layout))
            .perform(actionCloseDrawer())
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