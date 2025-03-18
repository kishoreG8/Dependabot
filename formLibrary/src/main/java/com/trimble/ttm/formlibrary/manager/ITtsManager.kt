package com.trimble.ttm.formlibrary.manager
/**
 * this interface allowed the interaction with the tts library
 * */
interface ITtsManager {

    /**
     * @param msg CharSequence represents the message to speech or convert in a file
     * receives the message to play for the tts.speak(...) method or synthesize by the
     * tts.synthesizeToFile(...) method
     * */
    fun launchTTS(
        msg:CharSequence,
        mode: TtsManagerMode
    )

    /**
     * @param callback ITtsManagerCallback represents the callback used to propagate the events
     * receives the callback to use for the manager to propagate the events
     * */
    fun setCallback(callback: ITtsManagerCallback?)

    /**
     * Only stops the tts.spech(...) execution. When this happened the done method is never called
     * */
    fun stopSpeak()

    /**
     * release the tts library when its necessary
     * */
    fun dispose()

}

enum class TtsManagerMode{
    SPEECH_MODE,
}