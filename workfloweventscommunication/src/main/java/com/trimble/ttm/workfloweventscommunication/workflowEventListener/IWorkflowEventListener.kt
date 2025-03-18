package com.trimble.ttm.workfloweventscommunication.workflowEventListener

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
 *   Abstract: The IWorkflowEventListener serves as a means to notify the third-party app that an event has been received from the Workflow app.
 * *
 */

import com.trimble.ttm.workfloweventscommunication.model.WorkflowEventData

/**
 *
 *
 * The IWorkflowEventListener serves as a means to notify the third-party app that an event has been received from the Workflow app. To integrate this functionality, the third-party app must implement the interface method(s) within their application class.
 *
 *
 */
fun interface IWorkflowEventListener {

    /**
     *
     *
     * The onWorkflowEventReceived function is responsible for transmitting the workflowEvent to the third-party app. This function is invoked when Workflow app sends an event.
     *
     *
     * @param[workflowEvent] contains Workflow Event's Information. It is of type WorkflowEventData.
     * @param[acknowledgeEvent] a lambda function which should be invoked after processing the incoming event and the parameter for this lambda function is the same workflowEvent that was processed.
     */
    fun onWorkflowEventReceived(workflowEvent: WorkflowEventData, acknowledgeEvent: (workflowEvent: WorkflowEventData) -> Unit)
}