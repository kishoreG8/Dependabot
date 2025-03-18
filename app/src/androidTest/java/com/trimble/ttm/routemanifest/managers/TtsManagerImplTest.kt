package com.trimble.ttm.routemanifest.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trimble.ttm.formlibrary.manager.ITtsManagerCallback
import com.trimble.ttm.formlibrary.manager.TtsManagerImpl
import com.trimble.ttm.formlibrary.manager.TtsManagerMode
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class TtsManagerImplTest{

    private lateinit var SUT : TtsManagerImpl
    private lateinit var iTtsManagerCallbackMock : ITtsManagerCallbackTD
    private val EX_TEXT = "example test"
    private val MODE = TtsManagerMode.SPEECH_MODE

    @Before
    fun setUp() {
        //this was commented for a mockk problem with the most recent gradle version
        //MockKAnnotations.init(this,relaxUnitFun = true)
        //iTtsManagerCallbackMock = mockk()
        iTtsManagerCallbackMock = ITtsManagerCallbackTD()
        val context = ApplicationProvider.getApplicationContext<Context>()
        SUT = TtsManagerImpl(
            context
        )
        SUT.setCallback(iTtsManagerCallbackMock)
        /*
        this was commented for a mockk problem with the most recent gradle version
        every {
            iTtsManagerCallbackMock.onExecutionFinished()
        } returns Unit*/
    }

    @Test
    fun playMessageAndStopAndNoReceiveTheFinishedMessageCallback() {
        runTest {
            SUT.launchTTS(EX_TEXT,MODE)
            SUT.stopSpeak()
            /*
            this was commented for a mockk problem with the most recent gradle version
            verify(
                exactly = 0,
                timeout = 5000
            ) {
                iTtsManagerCallbackMock.onExecutionFinished()
            }*/
            delay(2000)
            assertEquals(0,iTtsManagerCallbackMock.called)
            iTtsManagerCallbackMock.called = 0
        }
    }

    @Test
    fun playMessageFromBeginningToEndAndReceiveTheFinishedMessageCallback() = runTest {
        //this delay has to be added for the QUEUE_FLUSH behaviour. We need to way some moment to
        // lunch the next speech text session.
        delay(2000)
        SUT.launchTTS(EX_TEXT,MODE)
        /*
        this was commented for a mockk problem with the most recent gradle version
        verify(timeout = 5000) {
            iTtsManagerCallbackMock.onExecutionFinished()
        }*/
        delay(2000)
        assertEquals(1,iTtsManagerCallbackMock.called)
        iTtsManagerCallbackMock.called = 0
    }

    @After
    fun after(){
        SUT.dispose()
        //unmockkAll()
    }

    class ITtsManagerCallbackTD : ITtsManagerCallback{

        var called = 0

        override fun onExecutionFinished() {
            called = 1
        }

        override fun onExecutionPaused() {
            called = 2
        }

        override fun onExecutionError(error: String) {
            called = 3
        }

    }

}