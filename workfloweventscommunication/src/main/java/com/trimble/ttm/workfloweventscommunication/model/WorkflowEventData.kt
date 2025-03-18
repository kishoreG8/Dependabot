package com.trimble.ttm.workfloweventscommunication.model

import com.trimble.ttm.workfloweventscommunication.utils.EMPTY_STRING

/*
 * *
 *  * Copyright Trimble Inc., 2023 All rights reserved.
 *  *
 *  * Licensed Software Confidential and Proprietary Information of Trimble Inc.,
 *   made available under Non-Disclosure Agreement OR License as applicable.
 *
 *   Product Name: TTM - Driver Workflow
 *
 *   Author: Koushik Kumar V
 *
 *   Created On: 29-11-2023
 *
 *   Abstract: The data class for getting Workflow events information from Workflow app
 * *
 */


/**
 *
 *
 * data class contains event specific information of Workflow app
 *
 *
 * @param[uniqueEventId] unique id to uniquely identify the event
 * @param[dispatchId] refers to the unique id of a dispatch for the received event.
 * @param[dispatchName] refers to the name of a dispatch for the received event.
 * @param[stopId] refers to the stop id of the dispatch for the received event
 * @param[stopName] refers to the stop name of the dispatch for the received event
 * @param[eventName] It is of type WorkflowEvents. WorkflowEvents is an enum class which contains the list of events that will be sent from the Workflow App.
 * @param[reasonCode] refers to the PFM reason code(NORMAL, MANUAL, AUTO, TIMEOUT) of stop events Approach, Arrive and Depart
 * @param[message] refers to any message that needs to be sent along with the event.
 * @param[timeStamp] string holding the time of when the event took place in Workflow app
 */
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

/**
 *
 *
 * WorkflowEvents is an enum class which contains the list of events that will be sent from the Workflow app.
 *
 *
 */
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