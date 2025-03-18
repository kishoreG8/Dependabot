package com.trimble.ttm.workfloweventscommunication.manager

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
 *   Abstract: WorkflowEventListenerManager helps in managing the workflowEventListener of third party app
 * *
 */

import android.util.Log
import com.trimble.ttm.workfloweventscommunication.utils.WORKFLOW_EVENTS_COMMUNICATION
import com.trimble.ttm.workfloweventscommunication.workflowEventListener.IWorkflowEventListener

object WorkflowEventListenerManager {

    private var iWorkflowEventListener: IWorkflowEventListener? = null

    /**
     *
     *
     * This function initializes and assigns the provided instance of a class that implements the IWorkflowEventListener interface for handling workflow events.
     *
     *
     * @param[workflowEventListener] The class reference implementing the IWorkflowEventListener interface.
     *
     */
    fun initializeWorkflowEventListener(workflowEventListener: IWorkflowEventListener) {
        iWorkflowEventListener = workflowEventListener
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "Initialized WorkflowEventListener")
    }

    fun getWorkflowEventListener(): IWorkflowEventListener? {
        return iWorkflowEventListener
    }
}