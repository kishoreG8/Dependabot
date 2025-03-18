package com.trimble.ttm.formlibrary.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.R
import java.util.*

class TtsManagerImpl(
    private val context: Context
) : UtteranceProgressListener(),
    TextToSpeech.OnInitListener,
    ITtsManager {

    private var callback: ITtsManagerCallback? = null

    private val ID_TRACK: String = "idTrack"
    private var tts: TextToSpeech? = null
    private var TAG: String = TtsManagerImpl::class.java.canonicalName

    init {
        tts = TextToSpeech(context,this)
        tts?.setOnUtteranceProgressListener(this)
    }

    override fun onStart(idTrack: String?) {
        //not used
        Log.i(TAG,"idTrack: $idTrack", null, "Feature" to "TTSWidget", "Action" to "onStart")
    }

    override fun onDone(idTrack: String?) {
        Log.i(TAG,"idTrack: $idTrack", null, "Feature" to "TTSWidget", "Action" to "onDone")
        if(idTrack==ID_TRACK){
            callback?.onExecutionFinished()
        }
    }

    override fun onError(idTrack: String?) {
        Log.e(TAG,"idTrack: $idTrack", null, "Feature" to "TTSWidget", "Action" to "onError")
        callback?.onExecutionError(
            context.getString(R.string.ttsTryAgainError)
        )
    }

    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS){
            val result = tts?.setLanguage(Locale.US)
            if(
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ){
                Log.i(TAG, "not supported language",null, "Feature" to "TTSWidget", "Action" to "onInit")
            }
        }
    }

    override fun launchTTS(
        msg: CharSequence,
        mode: TtsManagerMode
    ) {
        if(mode == TtsManagerMode.SPEECH_MODE) {
            val result = tts?.speak(
                msg,
                TextToSpeech.QUEUE_FLUSH,
                null,
                ID_TRACK
            )
            Log.i(TAG,"result: $result",null, "Feature" to "TTSWidget", "Action" to "launchTTS")
            return
        }
    }

    override fun setCallback(callback: ITtsManagerCallback?) {
        this.callback = callback
    }

    override fun stopSpeak() {
        //if you call stop on the tts library, the onDone method is never called
        Log.i(TAG, "stopSpeak",null, "Feature" to "TTSWidget", "Action" to "stopSpeak")
        tts?.stop()
    }

    override fun dispose() {
        Log.i(TAG, "dispose",null, "Feature" to "TTSWidget", "Action" to "dispose")
        if(tts!=null){
            tts?.stop()
            tts?.shutdown()
        }
    }
}