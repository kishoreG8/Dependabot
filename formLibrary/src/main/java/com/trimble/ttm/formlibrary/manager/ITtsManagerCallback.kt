package com.trimble.ttm.formlibrary.manager
/**
 * Callback uses for the TtsManager class to propagate the generated events
 * during the file creation or the speech execution
 * */
interface ITtsManagerCallback {

    /**
     * when the File creation or the speech execution finished this
     * callback is called
     * */
    fun onExecutionFinished()

    /**
     * when the media player change from play to stop this
     * callback is called
     * */
    fun onExecutionPaused()

    /**
     * @param error String with the error of the execution
     * when the track error or the speech error happened this
     * callback is called with the string error
     * */
    fun onExecutionError(error:String)

}