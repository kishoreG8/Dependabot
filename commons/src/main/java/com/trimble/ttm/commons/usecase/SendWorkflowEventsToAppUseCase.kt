package com.trimble.ttm.commons.usecase

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.DISPATCH_BLOB
import com.trimble.ttm.commons.logger.DRIVER_WORKFLOW_EVENTS_COMMUNICATION
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.model.WorkflowEventData
import com.trimble.ttm.commons.model.WorkflowEventDataParameters
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.Utils
import com.trimble.ttm.commons.utils.WORKFLOW_EVENTS_COMMUNICATION_SERVICE_INTENT_ACTION
import com.trimble.ttm.commons.utils.WORKFLOW_EVENT_DATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

class SendWorkflowEventsToAppUseCase(
    val context: Context,
    private val managedConfigurationRepo: ManagedConfigurationRepo,
    private val sendWorkflowEventsScope: CoroutineScope
) {

    // Using default parameters here might result in missing the appropriate parameters being passed from the calling places.
    fun sendWorkflowEvent(workflowEventDataParameters: WorkflowEventDataParameters, caller: String) {
        sendWorkflowEventsScope.launch {
            val appPackageName = getAppPackageNamesForSendingWorkflowEvents(caller)
            if (appPackageName.isNullOrEmpty()) {
                Log.d(
                    DRIVER_WORKFLOW_EVENTS_COMMUNICATION,
                    "appPackageName for Workflow Events Communication from Managed Config is null, caller: $caller"
                )
                return@launch
            }
            //Changing log type to notice for DataDog widget - Custom Workflow Events Processing Insights
            Log.n(
                "$NOTIFICATION$DEVICE_FCM",
                "Third party app notification received D:${workflowEventDataParameters.dispatchId} Event: ${workflowEventDataParameters.eventName}"
            )
            if (workflowEventDataParameters.eventName != WorkFlowEvents.DISPATCH_BLOB_EVENT && workflowEventDataParameters.dispatchId.isEmpty()) {
                Log.e(DRIVER_WORKFLOW_EVENTS_COMMUNICATION, "DispatchId is empty. Cannot send Workflow Event: ${workflowEventDataParameters.eventName.name} to $appPackageName", values = arrayOf("appPackageName" to appPackageName))
                return@launch
            }
            Log.d(
                DRIVER_WORKFLOW_EVENTS_COMMUNICATION,
                "appPackageName for Workflow Events Communication from Managed Config: $appPackageName, dispatchId: ${workflowEventDataParameters.dispatchId}, caller: $caller"
            )
            val workflowEventData = getWorkFlowEventDataToSend(
                workflowEventDataParameters
            )
            Log.d(
                DRIVER_WORKFLOW_EVENTS_COMMUNICATION,
                "Build Bundle with workflowEventData: $workflowEventData, caller: $caller"
            )
            sendWorkflowEventDataToThirdPartyApp(appPackageName, buildBundleForThirdPartyApp(workflowEventData), caller)
        }
    }

    internal fun getAppPackageNamesForSendingWorkflowEvents(caller: String): String? {
        return managedConfigurationRepo.getAppPackageForWorkflowEventsCommunicationFromManageConfiguration(caller)
    }

    fun getPolygonalOptOutDataFromManagedConfig(caller: String): Boolean {
        return managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(caller)
    }

    internal fun generateUniqueEventId(eventName: WorkFlowEvents, dispatchId: String, stopId: String) =
        when (eventName) {
            WorkFlowEvents.TRIP_START_EVENT, WorkFlowEvents.TRIP_END_EVENT, WorkFlowEvents.NEW_TRIP_EVENT -> {
                "${eventName.name}-${dispatchId}"
            }

            WorkFlowEvents.APPROACH_EVENT, WorkFlowEvents.ARRIVE_EVENT, WorkFlowEvents.DEPART_EVENT, WorkFlowEvents.ADD_STOP_EVENT, WorkFlowEvents.REMOVE_STOP_EVENT -> {
                "${eventName.name}-${dispatchId}-${stopId}"
            }

            WorkFlowEvents.DISPATCH_BLOB_EVENT -> {
                "${eventName.name}-${Random.nextInt()}"
            }
        }

    internal fun sendWorkflowEventDataToThirdPartyApp(appPackageName: String?, bundle: Bundle, caller: String) {
        Intent(WORKFLOW_EVENTS_COMMUNICATION_SERVICE_INTENT_ACTION).apply {
            setPackage(appPackageName)
            putExtras(bundle)
            //Changing log type to notice for DataDog widget - Custom Workflow Events Processing Insights
            Log.n(
                DRIVER_WORKFLOW_EVENTS_COMMUNICATION,
                "Launching WorkflowEventsCommunicationService: $appPackageName, caller: $caller",
                throwable = null,
                "appPackageName" to appPackageName
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                context.startService(this)
            else
                context.startForegroundService(this)
        }
    }

    internal fun buildBundleForThirdPartyApp(workflowEventData: WorkflowEventData): Bundle {
        val bundle = Bundle()
        bundle.putString(WORKFLOW_EVENT_DATA, Utils.toJsonString(workflowEventData))
        return bundle
    }

    internal fun getWorkFlowEventDataToSend(
        workflowEventDataParameters: WorkflowEventDataParameters
    ): WorkflowEventData {
        return WorkflowEventData(
            uniqueEventId = generateUniqueEventId(
                workflowEventDataParameters.eventName,
                workflowEventDataParameters.dispatchId,
                workflowEventDataParameters.stopId
            ),
            dispatchId = workflowEventDataParameters.dispatchId,
            dispatchName = workflowEventDataParameters.dispatchName,
            stopId = workflowEventDataParameters.stopId,
            stopName = workflowEventDataParameters.stopName,
            eventName = workflowEventDataParameters.eventName,
            reasonCode = workflowEventDataParameters.reasonCode,
            message = workflowEventDataParameters.message,
            timeStamp = workflowEventDataParameters.timeStamp
        )
    }

    fun sendDispatchBlobEventToThirdPartyApps(
        dispatchBlob: DispatchBlob,
        eventName: WorkFlowEvents,
        timeStamp: Long,
        caller: String
    ) {
        Log.d(
            caller+ DISPATCH_BLOB,
            "Sending DispatchBlob event to third party apps")
        sendWorkflowEvent(
            WorkflowEventDataParameters(
                dispatchId = EMPTY_STRING,
                dispatchName = EMPTY_STRING,
                stopId = EMPTY_STRING,
                stopName = EMPTY_STRING,
                eventName = eventName,
                reasonCode = EMPTY_STRING,
                message = dispatchBlob.blobMessage,
                timeStamp = timeStamp
            ),
            caller
        )
    }
}