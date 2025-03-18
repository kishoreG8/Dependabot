package com.trimble.ttm.formlibrary.eventbus

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EventBus {
    private val flow = MutableSharedFlow<Intent>()
    val events = flow.asSharedFlow()

    suspend fun postEvent(event: Intent) {
        flow.emit(event)
    }

    private val resetRouteCalcRetryFlow = MutableSharedFlow<Unit>()
    val resetRouteCalcRetryEvents = resetRouteCalcRetryFlow.asSharedFlow()

    suspend fun resetRouteCalculationRetry() {
        resetRouteCalcRetryFlow.emit(Unit)
    }
}

