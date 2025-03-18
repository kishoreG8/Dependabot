package com.trimble.ttm.formlibrary.utils

import com.trimble.ttm.commons.utils.ext.safeLaunch
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive

interface InternalNotifier<T> {
    fun notify(t: T)
}

interface ExternalNotifier<T> {
    fun notify(t: T)
}

fun <T> getCallbackFlow(): Pair<ExternalNotifier<T>, Flow<T>> {
    var internalNotifier: InternalNotifier<T>? = null
    val externalNotifier: ExternalNotifier<T> = object : ExternalNotifier<T> {
        override fun notify(t: T) {
            internalNotifier?.notify(t)
        }
    }
    val flow: Flow<T> = callbackFlow {
        internalNotifier = object : InternalNotifier<T> {
            override fun notify(t: T) {
                safeLaunch(CoroutineName("Call back flow")) {
                    if (isClosedForSend.not() and isActive) channel.send(t)
                }
            }
        }
        awaitClose { internalNotifier = null }
    }
    return Pair(externalNotifier, flow)
}