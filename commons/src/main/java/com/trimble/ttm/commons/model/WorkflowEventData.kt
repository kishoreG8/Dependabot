package com.trimble.ttm.commons.model

import com.trimble.ttm.commons.utils.EMPTY_STRING

// workfloweventscommunication library also contains the same data and enum classes.
// If changes are made here, we must do in lib as well.
data class WorkflowEventData(
    val uniqueEventId: String,
    val dispatchId: String = EMPTY_STRING,
    val dispatchName: String = EMPTY_STRING,
    val stopId: String = EMPTY_STRING,
    val stopName: String = EMPTY_STRING,
    val eventName: WorkFlowEvents,
    val reasonCode: String = EMPTY_STRING,
    val message : String,
    val timeStamp: Long = 0
)

enum class WorkFlowEvents{
    TRIP_START_EVENT,
    TRIP_END_EVENT,
    APPROACH_EVENT,
    ARRIVE_EVENT,
    DEPART_EVENT,
    NEW_TRIP_EVENT,
    ADD_STOP_EVENT,
    REMOVE_STOP_EVENT,
    DISPATCH_BLOB_EVENT
}
