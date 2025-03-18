package com.trimble.ttm.routemanifest.repo

import com.google.firebase.firestore.CollectionReference
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.StopDetail
import kotlinx.coroutines.flow.Flow

interface DispatchFirestoreRepo {

    fun getDispatchCollectionPath(cid: String, vehicleId: String) : CollectionReference

    suspend fun listenDispatchesForTruck(
        vehicleId: String,
        cid: String)

    fun listenDispatchListFlow(): Flow<Any>

    fun getStopsForDispatch(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): Flow<Set<StopDetail>>

    suspend fun getAllStopsForDispatchIncludingDeletedStops(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): List<StopDetail>

    suspend fun getActionsOfStop(activeDispatchId:String, stopId: String, caller: String): List<Action>

    suspend fun getStop(cid: String, truckNum: String, dispatchId: String, stopId: String):StopDetail

    fun listenToStopActions(
        vehicleId: String,
        cid: String,
        dispatchId: String, stopId: String
    ): Flow<Set<Action>>

    suspend fun isDispatchCompleted(dispatchId: String, cid: String, vehicleId: String): Boolean

    suspend fun getDispatchPayload(
        caller:String,
        cid: String,
        vehicleId: String,
        dispatchId: String,
        isForceFetchedFromServer: Boolean = true
    ): Dispatch

    suspend fun getStopCountOfDispatch(cid: String,truckNum: String,dispatchId:String):Int?

    suspend fun scheduleDispatchToDisplay(
        dispatchId: String,
        cid: String,
        vehicleNumber: String,
        created: String
    )

    fun unRegisterFirestoreLiveListeners()

    fun getAppModuleCommunicator(): AppModuleCommunicator

    suspend fun getCurrentWorkFlowId(caller: String):String

    suspend fun setCurrentWorkFlowId(currentWorkFlowId: String)

    suspend fun setCurrentWorkFlowDispatchName(dispatchName: String)

    suspend fun getStopsFromFirestore(
        caller: String,
        vehicleId: String,
        cid: String,
        dispatchId: String,
        isForceFetchedFromServer: Boolean = false
    ): List<StopDetail>

    suspend fun getDispatchBlobDataByBlobId(cid: String, vehicleId: String, blobId: String): DispatchBlob

    suspend fun getAllDispatchBlobDataForVehicle(cid: String, vehicleId: String): ArrayList<DispatchBlob>

    suspend fun deleteDispatchBlobByBlobId(cid: String, vehicleId: String, blobId: String)

    suspend fun deleteAllDispatchBlobDataForVehicle(cid: String, vehicleId: String, dispatchBlobIdList : List<String>)

    suspend fun setActiveDispatchFlagInFirestore(
        cid: String,
        truckNumber: String,
        activeDispatchId: String
    )

    suspend fun getDispatchesList(
        vehicleId: String,
        cid: String,
        caller: String
    ):Set<Dispatch>
}