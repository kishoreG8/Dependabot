package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.formlibrary.widget.ITrackStateCallback

/**
 * this interface allowed the interaction with the tts library.
 * */
interface ITtsPlayerManager {

    /**
     * @param message CharSequence represents the message to play
     * receives the message to play for the using the media player feature file or the tts feature
     * */
    fun playMessage(message: CharSequence)

    /**
     * Only pauses the message played by the media player feature
     * */
    fun pauseMessage()

    /**
     * Stops the message played by the media player feature or the tts feature
     * */
    fun stopMessage()

    /**
     * @param callback TrackStateCallback represents the callback used to propagate the events
     * receives the callback to use for the manager to propagate the events
     * */
    fun setTrackStateCallback(
        trackStateCallback: ITrackStateCallback?
    )

    /**
     * release the tts feature and the media player feature when its necessary
     * */
    fun dispose()

}