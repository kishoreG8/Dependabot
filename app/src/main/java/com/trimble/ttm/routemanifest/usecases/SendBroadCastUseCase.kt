package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trimble.ttm.commons.logger.Log

class SendBroadCastUseCase(private val context: Context) {

    /**
     * Sends system level broadcast
     */
    fun sendBroadCast(intent: Intent, caller: String) {
        context.sendBroadcast(intent)
        Log.i(caller, "Broadcast sent. $intent")
    }

    /**
     * Sends app level broadcast
     */
    fun sendLocalBroadCast(intent: Intent, caller: String) {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(intent)
        Log.i(caller, "LocalBroadcast sent. $intent")
    }

}