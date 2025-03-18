package com.trimble.ttm.routemanifest.repo

import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.model.JsonData

interface TripMobileOriginatedEventsRepo {

    fun saveTripActionResponse(collectionName: String, documentPath: String, response: JsonData)

    suspend fun isTripActionResponseSaved(collectionName: String, documentPath: String): Boolean

    fun saveStopActionResponse(collectionName: String, documentPath: String, response: HashMap<String, Any>, arrivalReason: ArrivalReason, triggerReceivedTime: String)

    fun updateActionPayload(path: String, actionData: Action)

    fun setCompletedTimeForStop(path: String, stopActionCompletionTime: String, actionFieldName: String)

    fun setDepartedTimeForStop(path: String, stopActionDepartedTime: String, actionFieldName: String)

    fun setTriggerReceivedForActions(path:String, valueMap: HashMap<String, Any>)

    fun setIsCompleteFlagForTrip(
        caller:String,
        path: String
    )

    suspend fun isLateNotificationExists(
        collectionName: String, documentPath: String
    ): Boolean

    suspend fun updateStopDetailWithManualArrivalLocation(path: String, valueMap : HashMap<String, Any>)

    suspend fun setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(
        customerId: String,
        vehicleId: String,
        dispatchId: String,
        dispatchDeletedTime: String
    )
}