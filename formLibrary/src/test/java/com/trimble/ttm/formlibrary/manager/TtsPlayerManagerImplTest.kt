package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.formlibrary.widget.ITrackStateCallback
import io.mockk.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test

class TtsPlayerManagerImplTest{

    private lateinit var SUT : TtsPlayerManagerImpl
    private val EX_TEXT = "example test"
    private val ERROR = "example error"
    private lateinit var trackStateCallbackMock: ITrackStateCallback
    private lateinit var iTtsManagerTD: ITtsManagerTD

    @Before
    fun setUp() {
        MockKAnnotations.init(this,relaxUnitFun = true)
        trackStateCallbackMock = mockk()
        iTtsManagerTD = ITtsManagerTD()
        SUT = TtsPlayerManagerImpl(
            iTtsManagerTD
        )
        SUT.setTrackStateCallback(trackStateCallbackMock)
        every {
            trackStateCallbackMock.onTrackFinished()
        } returns Unit
        every {
            trackStateCallbackMock.onTrackError(any())
        } returns Unit
        every {
            trackStateCallbackMock.onTrackPaused()
        } returns Unit
    }

    @Test
    fun `play a message from begining to end and recive the finished message callback`() {
        SUT.playMessage(EX_TEXT)
        verify(timeout = 3000) {
            trackStateCallbackMock.onTrackFinished()
        }
    }

    @Test
    fun `play a message and stop it and no recive the finished message callback`() {
        SUT.playMessage(EX_TEXT)
        SUT.stopMessage()
        verify(
            exactly = 0,
            timeout = 3000
        ) {
            trackStateCallbackMock.onTrackFinished()
        }
    }

    @Test
    fun `launch error event on TTS error`() {
        iTtsManagerTD.hasError = true
        every {
            trackStateCallbackMock.onTrackError(any())
        } returns Unit
        SUT.playMessage(EX_TEXT)
        verify(
            exactly = 0,
            timeout = 3000
        ) {
            trackStateCallbackMock.onTrackError(any())
        }
    }

    @Test
    fun `execute playMessage and use TTS in the selected mode`() {
        SUT.playMessage(EX_TEXT)
        assert(
            iTtsManagerTD.mode== TtsManagerMode.SPEECH_MODE
        )
    }

    @Test
    fun `one interaction with trackStateCallbackMock when is call on Execution Finished`() {
        SUT.onExecutionFinished()
        verify(
            exactly = 1
        ) {
            trackStateCallbackMock.onTrackFinished()
        }
    }

    @Test
    fun `one interactions with trackStateCallbackMock when is call on Execution Error`() {
        SUT.onExecutionError(ERROR)
        verify(
            exactly = 1
        ) {
            trackStateCallbackMock.onTrackError(ERROR)
        }
    }

    @Test
    fun `one interactions with trackStateCallbackMock when is call on Execution Pause`() {
        SUT.onExecutionPaused()
        verify(
            exactly = 1
        ) {
            trackStateCallbackMock.onTrackPaused()
        }
    }

    @Test
    fun `no interactions with trackStateCallbackMock when it is null call on Execution Pause`() {
        SUT.setTrackStateCallback(null)
        SUT.onExecutionPaused()
        verify(
            exactly = 0
        ) {
            trackStateCallbackMock.onTrackPaused()
        }
    }

    @Test
    fun `no interactions with trackStateCallbackMock when it is null on Execution Finished`() {
        SUT.setTrackStateCallback(null)
        SUT.onExecutionFinished()
        verify(
            exactly = 0
        ) {
            trackStateCallbackMock.onTrackFinished()
        }
    }

    @Test
    fun `no interactions with trackStateCallbackMock when it is null on Execution Error`() {
        SUT.setTrackStateCallback(null)
        SUT.onExecutionError(ERROR)
        verify(
            exactly = 0
        ) {
            trackStateCallbackMock.onTrackError(any())
        }
    }

    @After
    fun after(){
        SUT.dispose()
        unmockkAll()
    }

    /**
     * We created a TD to simulate the stop behaviour.
     * Every time you stop the TTS the onTrackFinished never executes
     * */
    class ITtsManagerTD : ITtsManager {

        private var callback: ITtsManagerCallback? = null

        var hasError = false

        var mode: TtsManagerMode? = null

        override fun launchTTS(
            msg: CharSequence,
            mode: TtsManagerMode
        ) {
            this.mode = mode
             GlobalScope.launch {
                delay(2000)
                if(!hasError){
                    callback?.onExecutionFinished()
                    return@launch
                }
                callback?.onExecutionError("some error")
            }
        }

        override fun setCallback(callback: ITtsManagerCallback?) {
            this.callback = callback
        }

        override fun stopSpeak() {
            this.callback = null
        }

        override fun dispose() {

        }

    }

}