package com.trimble.ttm.routemanifest.eventbus

import com.trimble.ttm.routemanifest.model.StopDetail
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.CopyOnWriteArrayList

object WorkflowEventBus {

    private val stopListSharedFlow = MutableSharedFlow<CopyOnWriteArrayList<StopDetail>>(replay = 1)
    val stopListEvents = stopListSharedFlow.asSharedFlow()

    private val stopCountListenerSharedFlow = MutableSharedFlow<String>()
    val stopCountListenerEvents = stopCountListenerSharedFlow.asSharedFlow()

    private val negativeGufTimerSharedFlow = MutableSharedFlow<Int>()
    val negativeGufTimerEvents = negativeGufTimerSharedFlow.asSharedFlow()

    suspend fun postStopList(event: CopyOnWriteArrayList<StopDetail>) {
        stopListSharedFlow.emit(event)
    }

    suspend fun postStopCountListener(event: String) {
        stopCountListenerSharedFlow.emit(event)
    }

    suspend fun postNegativeGufTimerValue(timerValue : Int){
        negativeGufTimerSharedFlow.emit(timerValue)
    }

    fun disposeCacheOnTripEnd(){
        stopListSharedFlow.resetReplayCache()
        stopCountListenerSharedFlow.resetReplayCache()
    }

    fun disposeNegativeGufTimerCacheOnTimerStop(){
        negativeGufTimerSharedFlow.resetReplayCache()
    }

}