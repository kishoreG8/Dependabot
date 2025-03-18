package com.trimble.ttm.commons.utils.ext

import androidx.lifecycle.*
import com.google.firebase.firestore.ListenerRegistration
import com.trimble.ttm.commons.BuildConfig
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

//Refer this method from common module for all the places in the app. https://jira.trimble.tools/browse/MAPP-8285
val coRoutineTag:String = "Coroutines"

private fun logCoRoutines(cs: CoroutineScope, caller: String) {
    if (BuildConfig.DEBUG) {
        Log.d(coRoutineTag, "[${Thread.currentThread().name}] Job #${cs.coroutineContext.job} from $caller")
    }
}

private fun findCaller(): String {
    if (BuildConfig.DEBUG) {
        val stackTrace = Thread.currentThread().stackTrace
        if (stackTrace.size > 6) {
            return Thread.currentThread().stackTrace[5].toString()
        }
        return "Undefined source"
    } else {
        return EMPTY_STRING
    }
}

fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val caller = findCaller()
    return this.launch(context = context, start = start) {
        logCoRoutines(this, caller)
        try {
            block()
        }  catch (e: Exception) {
            val theTag: String = context[CoroutineName]?.name ?: coRoutineTag
            when (e) {
                is CancellationException -> {
                    // Ignore
                }
                else -> {
                    Log.e(theTag, "${e.message}, $caller", e)
                }
            }
        }
    }
}

fun LifecycleCoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val caller = findCaller()
    return this.launch(context = context, start = start) {
        logCoRoutines(this, caller)
        try {
            block()
        }  catch (e: Exception) {
            val theTag: String = context[CoroutineName]?.name ?: coRoutineTag
            when (e) {
                is CancellationException -> {
                    // Ignore
                }
                else -> {
                    Log.e(theTag, "${e.message}, $caller", e)
                }
            }
        }
    }
}

fun doContinuation(continuation: CancellableContinuation<Boolean>, doContinue:Boolean, listenerRegistration: ListenerRegistration?=null) {
    listenerRegistration?.remove()
    if (continuation.isActive) continuation.resume(doContinue)
}
