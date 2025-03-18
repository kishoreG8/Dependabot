package com.trimble.ttm.routemanifest.usecases

import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ARRIVED_TRIGGERS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.emptyArrivedGeoFenceTriggerList
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArriveTriggerDataStoreKeyManipulationUseCase(
    private val localDataSourceRepo: LocalDataSourceRepo
) {

    private val arrivedPreferenceMutexLock = Mutex()

    internal suspend fun getArrivedTriggerDataFromPreference(): String =
        localDataSourceRepo.getFromAppModuleDataStore(ARRIVED_TRIGGERS_KEY, emptyArrivedGeoFenceTriggerList)

    internal suspend fun setArrivedTriggerDataInPreference(arrivedTriggerList: ArrayList<ArrivedGeoFenceTriggerData>) {
        Gson().toJson(arrivedTriggerList)?.let {
            localDataSourceRepo.setToAppModuleDataStore(ARRIVED_TRIGGERS_KEY, it)
        }
    }

    suspend fun getArrivedTriggerData(): ArrayList<ArrivedGeoFenceTriggerData> {
        try {
            val arrivedGeoFenceTriggerDataString = getArrivedTriggerDataFromPreference()
            if (arrivedGeoFenceTriggerDataString.isEmpty() || arrivedGeoFenceTriggerDataString.trim()
                    .contentEquals("[]")
            ) {
                return ArrayList()
            }
            Log.d(
                DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION,
                "Arrived Trigger Data From Preference: $arrivedGeoFenceTriggerDataString"
            )
            return Gson().fromJson(
                arrivedGeoFenceTriggerDataString,
                object : TypeToken<ArrayList<ArrivedGeoFenceTriggerData>>() {}.type
            )
        } catch (e: Exception) {
            Log.e(
                DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION,
                "Exception while parsing arrivedGeoFenceTriggerDataString. ${e.stackTraceToString()}"
            )
            return ArrayList()
        }
    }

    suspend fun removeTriggerFromPreference(
        messageId: Int,
        removeMessageFromPriorityQueueAndUpdateTripPanelFlags: (Int) -> Unit
    ): ArrayList<ArrivedGeoFenceTriggerData> {
        arrivedPreferenceMutexLock.withLock {
            getArrivedTriggerData()
                .let { listOfTriggersFromPreference ->
                    val listOfTriggersFromPreferenceIterator =
                        listOfTriggersFromPreference.iterator()
                    logStopListBeforeAndAfterRemove(listOfTriggersFromPreference, "before")
                    while (listOfTriggersFromPreferenceIterator.hasNext()) {
                        val arrivedTriggerData = listOfTriggersFromPreferenceIterator.next()
                        if (arrivedTriggerData.messageId == messageId) {
                            listOfTriggersFromPreferenceIterator.remove()
                            removeMessageFromPriorityQueueAndUpdateTripPanelFlags(messageId)
                        }
                    }
                    setArrivedTriggerDataInPreference(listOfTriggersFromPreference)
                    logStopListBeforeAndAfterRemove(listOfTriggersFromPreference, "after")
                    return listOfTriggersFromPreference
                }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun logStopListBeforeAndAfterRemove(listOfTriggersFromPreference: ArrayList<ArrivedGeoFenceTriggerData>, tag : String) {
        listOfTriggersFromPreference.map { it.messageId }.let { stopTriggerList ->
            if (stopTriggerList.isNotEmpty()) {
                Log.d(
                    DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION,
                    "removeTriggerFromPreference, $tag remove, trigger from preference:$stopTriggerList"
                )
            }
        }
    }

    suspend fun removeArrivedTriggersFromPreferenceIfRespondedByUser(
        messageId: Int,
        removeMessageFromPriorityQueueAndUpdateTripPanelFlags: (Int) -> Unit
    ) {
        // Check only Did you arrive.
        if (messageId != SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID && messageId != FILL_FORMS_MESSAGE_ID) {
            removeTriggerFromPreference(messageId) { stopId -> removeMessageFromPriorityQueueAndUpdateTripPanelFlags(stopId) }
        }
    }

    internal suspend fun putArrivedTriggerDataIntoPreference(
        triggeredGeoFenceStopId: Int
    ) {
        arrivedPreferenceMutexLock.withLock {
            getArrivedTriggerData().let { arrivedTriggerDataFromPreference ->
                if (checkArrivedGeofenceTriggerDataExistInThePreference(
                        arrivedTriggerDataFromPreference,
                        triggeredGeoFenceStopId
                    ).not()
                ) {
                    val arrivedGeoFenceTriggerData =
                        ArrivedGeoFenceTriggerData(
                            triggeredGeoFenceStopId
                        )
                    val newTriggerListData: ArrayList<ArrivedGeoFenceTriggerData> =
                        arrivedTriggerDataFromPreference
                    Log.d(DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION, "putArrivedTriggerDataIntoPreference, adding arrive to queue, messageId: ${arrivedGeoFenceTriggerData.messageId}")
                    newTriggerListData.add(
                        arrivedGeoFenceTriggerData
                    )
                    setArrivedTriggerDataInPreference(newTriggerListData)
                    Log.d(DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION, "putArrivedTriggerDataIntoPreference, list of arrive triggers in queue: ${newTriggerListData.map { it.messageId }}")
                }
            }
        }
    }

    fun checkArrivedGeofenceTriggerDataExistInThePreference(
        arrivedTriggerDataFromPreference: ArrayList<ArrivedGeoFenceTriggerData>,
        triggeredGeoFenceStopId: Int
    ): Boolean {
        var isArrivedTriggerAlreadyExist = false
        arrivedTriggerDataFromPreference.filter { stop -> stop.messageId == triggeredGeoFenceStopId }
            .let {
                if (it.isNotEmpty()) isArrivedTriggerAlreadyExist = true
            }
        Log.d(DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION, "isArrivedTriggerAlreadyExist for stopId $triggeredGeoFenceStopId: $isArrivedTriggerAlreadyExist")
        return isArrivedTriggerAlreadyExist
    }

    suspend fun checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
        currentStopId: Int?
    ) =
        arrivedPreferenceMutexLock.withLock {
            getArrivedTriggerData().any { it.messageId == currentStopId }
        }
}