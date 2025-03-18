package com.trimble.ttm.routemanifest

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.CheckResult
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.AmbiguousViewMatcherException
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf
import org.junit.Assert
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val resources = HamburgerMenuTestResources()
fun waitForAlertDialogUIWithSubstring(text: String, timeOutInMillis: Long): Boolean {
    var element: ViewInteraction?
    var cancelViewVisibilityCheck = false
    Handler(Looper.getMainLooper()).postDelayed({
        cancelViewVisibilityCheck = true
    }, timeOutInMillis)
    do {
        waitFor(500)
        element = onView(ViewMatchers.withSubstring(text)).inRoot(isDialog())
        if (MatcherExtension.exists(element))
            return true
        if (cancelViewVisibilityCheck) break
    } while (MatcherExtension.exists(element).not())
    return false
}

fun waitForUIWithText(text: String, timeOutInMillis: Long): Boolean {
    var element: ViewInteraction?
    var cancelViewVisibilityCheck = false
    Handler(Looper.getMainLooper()).postDelayed({
        cancelViewVisibilityCheck = true
    }, timeOutInMillis)
    do {
        waitFor(500)
        element = onView(withText(text))
        if (MatcherExtension.exists(element))
            return true
        if (cancelViewVisibilityCheck) break
    } while (MatcherExtension.exists(element).not())
    return false
}


fun waitFor(millis: Long) {
    val signal = CountDownLatch(1)
    try {
        signal.await(millis, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Assert.fail(e.message)
    }
}

private object MatcherExtension {
    @CheckResult
    fun exists(interaction: ViewInteraction?): Boolean {
        return try {
            interaction?.perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> {
                    return CoreMatchers.any(View::class.java)
                }

                override fun getDescription(): String {
                    return "check for existence"
                }

                override fun perform(uiController: UiController, view: View) {
                    // no op, if this is run, then the execution will continue after .perform(...)
                }
            })
            true
        } catch (ex: AmbiguousViewMatcherException) {
            // if there's any interaction later with the same matcher, that'll fail anyway
            true // we found more than one
        } catch (ex: NoMatchingViewException) {
            false
        } catch (ex: NoMatchingRootException) {
            // optional depending on what you think "exists" means
            false
        }
    }
}

fun actionOpenDrawer(): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isAssignableFrom(DrawerLayout::class.java)
        }

        override fun getDescription(): String {
            return "open drawer"
        }

        override fun perform(uiController: UiController?, view: View) {
            (view as DrawerLayout).openDrawer(GravityCompat.START)
        }
    }
}

fun actionCloseDrawer(): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isAssignableFrom(DrawerLayout::class.java)
        }

        override fun getDescription(): String {
            return "close drawer"
        }

        override fun perform(uiController: UiController, view: View) {
            (view as DrawerLayout).closeDrawer(GravityCompat.START)
        }
    }

}

fun dismissTripAlertDialog(buttonToClick: String = ALERT_NO_BUTTON_TEXT) {
    if (waitForAlertDialogUIWithSubstring(YOUR_TRIP_ALERT_TEXT, ALERT_DIALOG_WAIT_TIME))
        onView(withText(buttonToClick)).perform(click())
}

fun dismissEndTripDialog() {
    if (waitForAlertDialogUIWithSubstring(ALERT_TEXT, ALERT_DIALOG_WAIT_TIME))
        onView(withText(ALERT_YES_BUTTON_TEXT)).perform(click())
}

fun getChildFromParentUsingPosition(parentViewId: Int, position: Int = FIRST_POSITION): Matcher<View> {
    return allOf(
        ViewMatchers.withParent(withId(parentViewId)),
        ViewMatchers.withParentIndex(position)
    )
}

fun checkViewIsDisplayedAndClickable(viewId: Int): ViewInteraction = onView(
    allOf(
        withId(viewId),
        isDisplayed(), ViewMatchers.isClickable()
    )
)

fun checkViewIsDisplayedAndClickable(viewText: String): ViewInteraction = onView(
    allOf(
        withText(Matchers.containsString(viewText)),
        isDisplayed(),
        ViewMatchers.isClickable()
    )
)

fun checkViewIsDisplayed(viewId: Int): ViewInteraction = onView(
    allOf(
        withId(
            viewId
        ), isDisplayed()
    )
)

fun checkViewIsDisplayedWithSubString(viewText: String): ViewInteraction = onView(
    allOf(
        withSubstring(
            viewText
        ),
        isDisplayed()
    )
)


fun checkViewIsDisplayed(viewText: String): ViewInteraction = onView(
    allOf(
        withText(
            viewText
        ), isDisplayed()
    )
)

fun checkViewIsNotDisplayed(viewId: Int): ViewInteraction = onView(
    allOf(
        withId(
            viewId
        ),
        Matchers.not(isDisplayed())
    )
)

fun clickElementFromListBasedOnPosition(viewInteraction: ViewInteraction, position: Int = FIRST_POSITION) {
    viewInteraction.perform(
        RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
            position,
            click()
        )
    )
}

fun scrollToAndClickElement(viewInteraction: ViewInteraction, position: Int = FIRST_POSITION) {
    viewInteraction.perform(
        RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
            position,
            ViewActions.scrollTo()
        )
    )
    viewInteraction.perform(
        RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
            position,
            click()
        )
    )
}

fun scrollToLastPosition(viewInteraction: ViewInteraction) {
    viewInteraction.perform(
        RecyclerViewActions.scrollToLastPosition<ViewHolder>()
    )
}

fun navigateBackToTripList() {
    checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
    waitFor(WAIT_TIME_TWO_SECONDS)
    checkViewIsDisplayed(resources.getString(R.string.menu_trip_list)).perform(click())
}

//Sometimes after the end trip, MAPP-8545 will occur. So Added this info for debugging purposes.
fun endTrip() {
    checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
    waitFor(WAIT_TIME_TWO_SECONDS)
    onView(allOf(withText(resources.getString(R.string.menu_end_trip)), isDisplayed())).perform(click())
    dismissEndTripDialog()
}

fun endTripViaStopListOption(){
    checkViewIsDisplayed(R.id.drawer_layout).perform(actionOpenDrawer())
    waitFor(WAIT_TIME_TWO_SECONDS)
    onView(allOf(withText(resources.getString(R.string.menu_stop_list)), isDisplayed())).perform(
        click()
    )
    waitFor(WAIT_TIME_TWO_SECONDS)
    endTrip()
}

fun checkTripInfoIsDisplayed() = checkViewIsDisplayed(R.id.tripInfoRecycler)

fun checkDispatchListIsDisplayed() = checkViewIsDisplayed(R.id.dispatchList)

internal fun checkIfAnyTripIsActiveAndEndTheTrip(){
    if(!waitForAlertDialogUIWithSubstring(YOUR_TRIP_ALERT_TEXT, ALERT_DIALOG_WAIT_TIME)){
        endTripViaStopListOption()
    }
}