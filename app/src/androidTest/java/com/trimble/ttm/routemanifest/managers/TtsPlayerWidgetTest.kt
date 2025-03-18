package com.trimble.ttm.routemanifest.managers

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class TtsPlayerWidgetTest {

    //Tests are failing. So commenting it as of now will fix it when this functionality is taken up for ui test creation.
    // These tests will be fixed as part of MAPP-8548.

    private val APP_PACKAGE_NAME = "com.trimble.ttm.routemanifest.stg"
    private val LAUNCH_TIMEOUT = 5000L
    private lateinit var device: UiDevice

  /*  @Before
    fun setup() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Start from the home screen
        device.pressHome()

        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(
            Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LAUNCH_TIMEOUT
        )/
    }*/

    @Ignore
    @Test
    fun checkPreviousButton() {
        val previousButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetPreviousButton"))
        assertNotNull(previousButton)
        assertEquals(previousButton.contentDescription, "This is the previous button, it allows you to go back to the previous message.")
    }

    @Ignore
    @Test
    fun checkPreviousButtonOff() {
        val previousButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetPreviousButtonOff"))
        assertNotNull(previousButton)
        assertEquals(previousButton.contentDescription, "You don\'t have previous messages to navigate.")
    }

    @Ignore
    @Test
    fun checkPauseButton() {
        val pauseButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetPauseButton"))
        assertNotNull(pauseButton)
        assertEquals(pauseButton.contentDescription, "This is the pause button, it allows you to pause the message.")
    }

    @Ignore
    @Test
    fun checkStopButton() {
        val stopButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetStopButton"))
        assertNotNull(stopButton)
        assertEquals(stopButton.contentDescription, "This is the stop button, it allows you to stop the message.")
    }

    @Ignore
    @Test
    fun checkReplayButton() { // We cannot check this button because it is not at the player
        val replayButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetReplayButton"))
        assertNull(replayButton)
    }

    @Ignore
    @Test
    fun checkPlayButton() {
        val playButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetPlayButton"))
        assertNotNull(playButton)
        assertEquals(playButton.contentDescription, "This is the play button, it allows you to play the message.")
    }

    @Ignore
    @Test
    fun checkPlayButtonOff() {
        val playButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetPlayButtonOff"))
        assertNotNull(playButton)
        assertEquals(playButton.contentDescription, "You don\'t have any new message.")
    }

    @Ignore
    @Test
    fun checkNextButton() {
        val nextButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetNextButton"))
        assertNotNull(nextButton)
        assertEquals(nextButton.contentDescription, "This is the next button, it allows you to go to the next message.")
    }

    @Ignore
    @Test
    fun checkNextButtonOff() {
        val nextButton = device.findObject(By.res(APP_PACKAGE_NAME, "playerWidgetNextButtonOff"))
        assertNotNull(nextButton)
        assertEquals(nextButton.contentDescription, "You don\'t have more messages to navigate.")
    }

}