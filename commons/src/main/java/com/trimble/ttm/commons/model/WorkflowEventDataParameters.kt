package com.trimble.ttm.commons.model

import com.trimble.ttm.commons.utils.EMPTY_STRING

/**
 * The fun sendWorkflowEvent() from SendWorkflowEventsToAppUseCase
 * requires more than 7 parameters so using data class WorkflowEventDataParameters
 */

data class WorkflowEventDataParameters(
    val dispatchId: String,
    val dispatchName: String,
    val stopId: String,
    val stopName: String,
    val eventName: WorkFlowEvents,
    val reasonCode: String = EMPTY_STRING,
    val message : String = EMPTY_STRING,
    val timeStamp: Long
)