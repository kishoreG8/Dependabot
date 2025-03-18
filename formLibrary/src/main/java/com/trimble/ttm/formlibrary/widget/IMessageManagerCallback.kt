package com.trimble.ttm.formlibrary.widget

interface IMessageManagerCallback {
    fun onListEnd()
    fun onListStart()
    fun onIncomingMessage(textToShow: String)
}