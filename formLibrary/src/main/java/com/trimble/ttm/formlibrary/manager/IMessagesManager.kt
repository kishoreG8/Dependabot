package com.trimble.ttm.formlibrary.manager

interface IMessagesManager {
    fun navigatePrevious()
    fun navigateNext()
    fun getCurrentMessage(): String
    fun setCallback(
        callback: IMessageManagerCallback?
    )
    fun clearMessages()
}