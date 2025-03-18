package com.trimble.ttm.routemanifest.repo

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MANUAL_ARRIVAL_STOP
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.logger.TRIP_COMPLETE
import com.trimble.ttm.commons.logger.TRIP_PFM_EVENT
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EXPIRE_AT
import com.trimble.ttm.commons.utils.VALUE
import com.trimble.ttm.commons.utils.ext.getFromCache
import com.trimble.ttm.commons.utils.ext.getFromServer
import com.trimble.ttm.commons.utils.ext.isCacheEmpty
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.utils.*
import com.trimble.ttm.routemanifest.viewmodel.PAYLOAD
import kotlinx.coroutines.tasks.await

const val PAYLOAD_COMPLETED_TIME = "Payload.CompletedTime"
const val LATE_EVENT = "late"
const val NORMAL_EVENT = "normal"

class TripMobileOriginatedEventsRepoImpl(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : TripMobileOriginatedEventsRepo {
    private val tag = "MobileOriginatedRepo"

    override fun updateActionPayload(path: String, actionData: Action) {
        firestore.document(path).update(PAYLOAD, actionData)
            .addOnSuccessListener {
                Log.d(tag, "Updated action payload $path${actionData.thisAction}")
            }
            .addOnFailureListener {
                Log.e(
                    tag,
                    "Error updating action payload at $path",
                    throwable = null,
                    "stack" to it.stackTraceToString()
                )
            }
    }

    override fun setCompletedTimeForStop(
        path: String,
        stopActionCompletionTime: String,
        actionFieldName: String
    ) {
            val updates = hashMapOf<String, Any>(
                actionFieldName to stopActionCompletionTime,
                PAYLOAD_COMPLETED_TIME to stopActionCompletionTime
            )
            firestore.document(path)
                .update(updates)
                .addOnSuccessListener {
                    Log.d(tag, "Updated $actionFieldName for : $path")
                }
                .addOnFailureListener {
                    Log.e(
                        tag, "Error updating $actionFieldName at: $path",
                        throwable = null,
                        "stack" to it.stackTraceToString()
                    )
                }
    }

    override fun setDepartedTimeForStop(
        path: String,
        stopActionDepartedTime: String,
        actionFieldName: String
    ) {
            firestore.document(path)
                .update(actionFieldName, stopActionDepartedTime)
                .addOnSuccessListener {
                    Log.d(tag, "Updated $actionFieldName for : $path")
                }
                .addOnFailureListener {
                    Log.e(
                        tag, "Error updating $actionFieldName at: $path",
                        throwable = null,
                        "stack" to it.stackTraceToString()
                    )
                }
    }

    override fun saveTripActionResponse(
        collectionName: String,
        documentPath: String,
        response: JsonData
    ) {
        val extractedEventValue = extractResponseValuesForLogging(response.value)

        // Create a data map with the fields and their values
        val data = hashMapOf(
            VALUE to response.value,
            EXPIRE_AT to DateUtil.getExpireAtDateTimeForTTLInUTC()
        )

        firestore.collection(collectionName).document(documentPath)
            .set(data)
            .addOnSuccessListener {
               Log.n(
                    TRIP_PFM_EVENT,
                    "trip event sent",
                     throwable = null,
                   "action" to collectionName,
                    "reason" to extractedEventValue.first,
                    "dispatchId" to extractedEventValue.second
                )
            }
            .addOnFailureListener { e ->
                Log.e(
                    TRIP_PFM_EVENT,
                    "Error adding trip event at: $collectionName $documentPath",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
            }
    }

    override suspend fun isTripActionResponseSaved(
        collectionName: String,
        documentPath: String
    ): Boolean {
        var isDocumentExist = false

        // Function to check cache and handle exceptions
        suspend fun checkDocumentExistence(
            docReference: DocumentReference,
            isServer: Boolean = false
        ) {
            try {
                isDocumentExist = if (isServer) {
                    docReference.getFromServer().await().exists()
                } else {
                    docReference.getFromCache().await().exists()
                }
            } catch (e: FirebaseFirestoreException) {
                Log.d(
                    tag,
                    e.message,
                    null,
                    "Exception in getting the doc with path" to docReference.path
                )
            }
        }

        // Check new Path Cache
        checkDocumentExistence(firestore.collection(collectionName).document(documentPath))
        if (isDocumentExist) return true

        // Check new Path Server
        checkDocumentExistence(firestore.collection(collectionName).document(documentPath), true)

        return isDocumentExist
    }

    override fun saveStopActionResponse(
        collectionName: String,
        documentPath: String,
        response: HashMap<String, Any>,
        arrivalReason: ArrivalReason,
        triggerReceivedTime: String
    ) {
        val extractedEventValues = extractResponseValuesForLogging(response[VALUE]as String)
        //if save doc path string contains timeout it is late event otherwise normal event arrive/5688/vehicles/India40/stopEvents/538131798_2_0_timeout
        val actionEventKind = if (documentPath.contains("timeout")) LATE_EVENT else NORMAL_EVENT

        firestore.collection(collectionName).document(documentPath)
            .set(response)
            .addOnSuccessListener {
                if (actionEventKind == NORMAL_EVENT) {
                    Log.n(
                        TRIP_PFM_EVENT,
                        "action event sent",
                        throwable = null,
                        "action" to collectionName,
                        "eventType" to actionEventKind,
                        "reason" to extractedEventValues.first,
                        "dispatchId" to extractedEventValues.second,
                        "stopId" to extractedEventValues.third,
                        DRIVERID to arrivalReason.driverID,
                        ARRIVAL_ACTION_STATUS to arrivalReason.arrivalActionStatus,
                        ARRIVAL_TYPE to arrivalReason.arrivalType,
                        "triggerReceivedTime" to triggerReceivedTime,
                        INSIDE_GEOFENCE_AT_ARRIVAL to arrivalReason.insideGeofenceAtArrival,
                        INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS to arrivalReason.insideGeofenceAtArrivalActionStatus,
                        GEOFENCE_TYPE to arrivalReason.geofenceType,
                        SEQUENCED to arrivalReason.sequenced,
                        "distanceToArrivalLocation" to arrivalReason.distanceToArrivalLocation,
                        "stopLocation" to arrivalReason.stopLocation.toString(),
                        KEY to DISPATCH_LIFECYCLE
                    )
                }else{
                    Log.d(
                        TRIP_PFM_EVENT,
                        "action event sent - timer expired",
                        throwable = null,
                        "action" to collectionName,
                        "eventType" to actionEventKind,
                        "reason" to extractedEventValues.first,
                        "dispatchId" to extractedEventValues.second,
                        "stopId" to extractedEventValues.third,
                        ARRIVAL_REASON_DETAILS to arrivalReason,
                        DRIVERID to arrivalReason.driverID,
                        ARRIVAL_ACTION_STATUS to arrivalReason.arrivalActionStatus,
                        ARRIVAL_TYPE to arrivalReason.arrivalType,
                        "triggerReceivedTime" to triggerReceivedTime,
                        INSIDE_GEOFENCE_AT_ARRIVAL to arrivalReason.insideGeofenceAtArrival,
                        INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS to arrivalReason.insideGeofenceAtArrivalActionStatus,
                        GEOFENCE_TYPE to arrivalReason.geofenceType,
                        SEQUENCED to arrivalReason.sequenced,
                        "distanceToArrivalLocation" to arrivalReason.distanceToArrivalLocation,
                        "stopLocation" to arrivalReason.stopLocation.toString(),
                        KEY to DISPATCH_LIFECYCLE
                    )
                }
            }
            .addOnFailureListener {
                Log.e(
                    tag,
                    "action event sent failed $collectionName/$documentPath",
                    throwable = null,
                    "stack" to it.stackTraceToString()
                )
            }
    }

    fun extractResponseValuesForLogging(response: String): Triple<String, Double, Double> {
        // Parse the JSON string to a Map
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val responseMap: Map<String, Any> = gson.fromJson(response, type)

       return Triple(
            responseMap[reason] as String,
            responseMap[dispId] as Double,
           responseMap[stopId]  as? Double?:0.0, //Do not change the return order
        )
    }

    override fun setTriggerReceivedForActions(path: String, valueMap: HashMap<String, Any>) {
        firestore.document(path).update(valueMap)
            .addOnSuccessListener {
                Log.d(
                    tag,
                    "Updated trigger received for actions $path",
                    throwable = null
                )
            }
            .addOnFailureListener {
                Log.e(
                    tag,
                    "Error updating trigger received for actions $path",
                    throwable = null,
                    "stack" to it.stackTraceToString()
                )
            }
    }

    override fun setIsCompleteFlagForTrip(caller: String, path: String) {
        val data = hashMapOf<String, Any>(
            ISCOMPLETED to true,
            IS_ACTIVE_DISPATCH to false
        )
        firestore.document(path).update(data)
            .addOnSuccessListener {
                Log.d(
                    TRIP_COMPLETE,
                    "UpdatedCompletedFlag P$path",
                    throwable = null,
                    "caller" to caller
                )
            }
            .addOnFailureListener {
                Log.e(
                    TRIP_COMPLETE,
                    "ErrorUpdatingCompletedFlag P${path}",
                    throwable = null,
                    "caller" to caller,
                    "stack" to it.stackTraceToString()
                )
            }
    }

    override suspend fun isLateNotificationExists(
        collectionName: String,
        documentPath: String,
    ): Boolean {
        val documentReference =
            firestore.collection(collectionName)
                .document(documentPath)
        return documentReference.isCacheEmpty().not()
    }

    override suspend fun updateStopDetailWithManualArrivalLocation(path: String, valueMap: HashMap<String, Any>) {
        try {
            firestore.document(path)
                .update(valueMap)
                .addOnSuccessListener {
                    Log.d("$MANUAL_ARRIVAL_STOP$REPO", "Updated stopDetail valueMap:$valueMap for : $path")
                }
                .addOnFailureListener {
                    Log.e(
                        "$MANUAL_ARRIVAL_STOP$REPO", "Error updating stopDetail $valueMap at: $path",
                        throwable = null,
                        "stack" to it.stackTraceToString()
                    )
                }
        }catch (e: Exception) {
            Log.e(
                "$MANUAL_ARRIVAL_STOP$REPO", "Exception on updating stopDetail $valueMap at: $path",
                throwable = null,
                "stack" to e
            )
        }
    }

    override suspend fun setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(
        customerId: String,
        vehicleId: String,
        dispatchId: String,
        dispatchDeletedTime: String
    ) {
        val path = "$DISPATCH_COLLECTION/${customerId}/${vehicleId}/${dispatchId}"
        val documentReference = firestore.document(path)
        val data: HashMap<String, Any> = hashMapOf(
            IS_DISPATCH_DELETED to true,
            DISPATCH_DELETED_TIME to dispatchDeletedTime
        )
        documentReference.update(data).addOnSuccessListener {
            Log.d(TRIP_COMPLETE, "Updated Data for path $path , data : $data")
        }.addOnFailureListener {
            Log.e(TRIP_COMPLETE, "Failure on Updating Data for path $path , data : $data")
        }
    }
}