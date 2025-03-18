package com.trimble.ttm.formlibrary.widget

interface ITrackStateCallback {
    fun onTrackFinished()
    fun onTrackPaused()
    fun onTrackError(error: String)
}