package com.trimble.ttm.commons.logger

import android.util.Log
import com.trimble.ttm.commons.BuildConfig
import com.trimble.ttm.mep.log.api.TrimbLog

object Log {

    fun v(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.notice(tag, this, exception = throwable, *values)
            else Log.v(tag, this, throwable)
        }

    fun d(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.debug(tag, this, exception = throwable, *values)
            else Log.d(tag, this, throwable)
        }

    fun i(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.info(tag, this, exception = throwable, *values)
            else Log.i(tag, this, throwable)
        }

    fun w(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.warning(tag, this, exception = throwable, *values)
            else Log.w(tag, this, throwable)
        }

    fun e(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.error(tag, this, exception = throwable, *values)
            else Log.e(tag, this, throwable)
        }

    fun n(tag: String, msg: String?, throwable: Throwable? = null, vararg values: Pair<String, Any?>) =
        msg?.run {
            if (BuildConfig.DEBUG.not()) TrimbLog.notice(tag, this, exception = throwable, *values)
            else Log.i(tag, this, throwable)
        }

    fun logGeoFenceEventFlow(invokerTag: String, logMessage: String, vararg values: Pair<String, Any?>) {
        d(invokerTag,logMessage, values = *values)
    }

    fun logTripRelatedEvents(invokerTag: String, logMessage: String) {
        i(invokerTag,logMessage)
    }

    fun logLifecycle(invokerTag: String, logMessage: String, throwable: Throwable? = null, vararg values: Pair<String, Any?>) {
        i(invokerTag,logMessage, throwable, *values)
    }

    fun logUiInteractionInNoticeLevel(invokerTag: String, logMessage: String, throwable: Throwable? = null, vararg values: Pair<String, Any?>) {
        n(invokerTag,logMessage, throwable, *values)
    }

    fun logUiInteractionInInfoLevel(invokerTag: String, logMessage: String, throwable: Throwable? = null, vararg values: Pair<String, Any?>) {
        i(invokerTag,logMessage, throwable, *values)
    }

}