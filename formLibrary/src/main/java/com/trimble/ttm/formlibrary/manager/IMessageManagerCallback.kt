package com.trimble.ttm.formlibrary.manager

interface IMessageManagerCallback {
    fun onMessagesUpdated()
    fun onError(msg:String)
}