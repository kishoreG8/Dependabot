package com.trimble.ttm.workfloweventscommunication.service

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
 *   Abstract: The WorkflowEventsCommunicationService is a foreground service of type shortService. It's initiated by the Workflow application when specific events occur (for instance, a TripStart event).
 * *
 */

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.trimble.ttm.workfloweventscommunication.R
import com.trimble.ttm.workfloweventscommunication.model.WorkflowEventData
import com.trimble.ttm.workfloweventscommunication.utils.WORKFLOW_EVENTS_COMMUNICATION
import com.trimble.ttm.workfloweventscommunication.utils.WORKFLOW_EVENT_DATA
import com.trimble.ttm.workfloweventscommunication.utils.createNotification
import com.trimble.ttm.workfloweventscommunication.utils.fromJsonString
import com.trimble.ttm.workfloweventscommunication.utils.getAppInfo
import com.trimble.ttm.workfloweventscommunication.utils.getAppLabel
import com.trimble.ttm.workfloweventscommunication.manager.WorkflowEventListenerManager
import java.util.concurrent.ConcurrentHashMap

private const val WORKFLOW_EVENTS_COMMUNICATION_CHANNEL_ID = "0003"
private const val WORKFLOW_EVENTS_COMMUNICATION_SERVICE_ID = 114

private val workflowEventsHashMap = ConcurrentHashMap<String, Boolean>()

class WorkflowEventsCommunicationService: LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService onCreate")
        startNotificationForForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService onStartCommand")
        intent?.extras?.run {
            val workflowEventData = getString(WORKFLOW_EVENT_DATA)
            if (workflowEventData == null) {
                Log.w(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventInfo is empty.")
                checkEventsMapAndStopService()
            } else {
                fromJsonString<WorkflowEventData>(workflowEventData)?.let { workflowEvent ->
                    Log.d(
                        WORKFLOW_EVENTS_COMMUNICATION,
                        "WorkflowEventsCommunicationService WorkflowEventInfo: $workflowEvent"
                    )
                    if (!workflowEventsHashMap.containsKey(workflowEvent.uniqueEventId)) {
                        workflowEventsHashMap[workflowEvent.uniqueEventId] = true
                        sendWorkflowEvent(workflowEvent)
                    } else checkEventsMapAndStopService()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun sendWorkflowEvent(workflowEvent: WorkflowEventData) {
        val eventListener = WorkflowEventListenerManager.getWorkflowEventListener()
        if (eventListener == null) {
            Log.e(WORKFLOW_EVENTS_COMMUNICATION, "IWorkflowEventListener is null. Removing workflowEventData: $workflowEvent from workflowEventsHashMap")
            workflowEventsHashMap.remove(workflowEvent.uniqueEventId)
            checkEventsMapAndStopService()
            return
        }
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService Sending WorkflowEvent: $workflowEvent to $packageName")
        eventListener.onWorkflowEventReceived(workflowEvent) { acknowlegedWorkflowevent ->
            Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService acknowledgement received, WorkflowEvent: $acknowlegedWorkflowevent")
            processEventAcknowledgement(acknowlegedWorkflowevent)
        }
    }

    private fun processEventAcknowledgement(workflowEvent: WorkflowEventData) {
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService removing WorkflowEventData from workflowEventsHashMap having uniqueEventId: ${workflowEvent.uniqueEventId}")
        workflowEventsHashMap.remove(workflowEvent.uniqueEventId)
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService workflowEventsHashMap: ${workflowEventsHashMap.map { it.key }}")
        checkEventsMapAndStopService()
    }

    private fun checkEventsMapAndStopService() {
        if (workflowEventsHashMap.isEmpty()) {
            Log.d(WORKFLOW_EVENTS_COMMUNICATION, "workflowEventsHashMap is empty. Hence stopping WorkflowEventsCommunicationService")
            stopSelf()
        } else {
            Log.d(WORKFLOW_EVENTS_COMMUNICATION, "workflowEventsHashMap is not empty, workflowEventsHashMap: ${workflowEventsHashMap.map { it.key }}, Hence not stopping WorkflowEventsCommunicationService")        }
    }

    private fun startNotificationForForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(WORKFLOW_EVENTS_COMMUNICATION, "AppPackageName = $packageName")
            startForeground(
                WORKFLOW_EVENTS_COMMUNICATION_SERVICE_ID,
                this.createNotification(
                    WORKFLOW_EVENTS_COMMUNICATION_CHANNEL_ID,
                    getString(R.string.workflow_events_communication_foreground_service_channel_name),
                    packageManager.getAppLabel(packageName, PackageManager.GET_META_DATA),
                    getString(R.string.workflow_events_communication_notification_content_text),
                    packageManager.getAppInfo(packageName, PackageManager.GET_META_DATA).icon,
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(WORKFLOW_EVENTS_COMMUNICATION, "WorkflowEventsCommunicationService onDestroy")
    }
}