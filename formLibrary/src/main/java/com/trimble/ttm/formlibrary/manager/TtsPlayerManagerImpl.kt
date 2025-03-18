package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.widget.ITrackStateCallback

class TtsPlayerManagerImpl(
    private val iTtsManager: ITtsManager
) : ITtsPlayerManager,
    ITtsManagerCallback {

    private var trackStateCallback: ITrackStateCallback? = null
    private var TAG: String = TtsPlayerManagerImpl::class.java.canonicalName

    init {
        iTtsManager.setCallback(this)
    }

    override fun playMessage(message: CharSequence) {
        Log.i(TAG,"Message: $message", null, "Feature" to "TTSWidget", "Action" to "playMessage")
        iTtsManager.launchTTS(
            message,
            TtsManagerMode.SPEECH_MODE
        )
    }

    override fun pauseMessage() {
        TODO("Not yet implemented")
    }

    override fun stopMessage() {
        Log.i(TAG,"stopMessage", null, "Feature" to "TTSWidget", "Action" to "stopMessage")
        iTtsManager.stopSpeak()
    }

    override fun setTrackStateCallback(
        trackStateCallback: ITrackStateCallback?
    ) {
        this.trackStateCallback = trackStateCallback
    }

    override fun dispose() {
        iTtsManager.dispose()
    }

    override fun onExecutionFinished() {
        Log.i(TAG,"onExecutionFinished", null, "Feature" to "TTSWidget", "Action" to "onExecutionFinished")
        trackStateCallback?.onTrackFinished()
    }

    override fun onExecutionPaused() {
        trackStateCallback?.onTrackPaused()
    }

    override fun onExecutionError(error: String) {
        Log.e(TAG,"Error: $error", null, "Feature" to "TTSWidget", "Action" to "onExecutionError")
        trackStateCallback?.onTrackError(
            error
        )
    }

}