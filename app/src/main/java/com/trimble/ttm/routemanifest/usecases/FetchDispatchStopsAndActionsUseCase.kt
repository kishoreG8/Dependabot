package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo

/**
 * UseCase to fetch stops and actions from firestore cache.
 * Refactor this UseCase in Dispatch refactoring task as required
 */
class FetchDispatchStopsAndActionsUseCase(
    private val dispatchFirestoreRepo: DispatchFirestoreRepo
) {
    private val tag = "FetchDispatchStopsAndActionsUseCase"
    internal val appModuleCommunicator = dispatchFirestoreRepo.getAppModuleCommunicator()

    suspend fun getDispatch(cid: String, vehicleNumber: String, dispatchId: String, isForceFetchFromServer: Boolean = true): Dispatch {
        return dispatchFirestoreRepo.getDispatchPayload(tag, cid, vehicleNumber, dispatchId, isForceFetchFromServer)
    }

    suspend fun getStopsAndActions(cid: String, vehicleNumber: String, dispatchId: String, caller: String, isForceFetchFromServer : Boolean = true): List<StopDetail> {
        return dispatchFirestoreRepo
            .getStopsFromFirestore(caller = caller, vehicleId = vehicleNumber, cid =  cid,
                dispatchId = dispatchId, isForceFetchedFromServer = isForceFetchFromServer)
            .filter { it.deleted == 0 }.getSortedStops()
    }

    internal suspend fun getAllActiveStopsAndActions(caller: String): List<StopDetail> {
        val stopList = getStopsForDispatch(
            appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.getCurrentWorkFlowId(caller)
        ).filter { it.deleted == 0 }.getSortedStops()
        stopList.forEach { stopDetail ->
            getActionsOfStop(
                activeDispatchId = stopDetail.dispid,
                stopId = stopDetail.stopid.toString(),
                caller = caller
            ).let { actionList ->
                stopDetail.Actions.clear()
                stopDetail.Actions.addAll(actionList)
            }
        }
        return stopList
    }

    suspend fun getStopsForDispatch(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): List<StopDetail> = dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
        vehicleId,
        cid,
        dispatchId
    )

    suspend fun getActionsOfStop(
        activeDispatchId: String,
        stopId: String,
        caller: String
    ): List<Action> =
        dispatchFirestoreRepo.getActionsOfStop(activeDispatchId, stopId, caller)

    suspend fun getStopData(stopId: Int): StopDetail? {
        getAllActiveStopsAndActions("getStopData").let { stopList ->
            return stopList.firstOrNull { it.stopid == stopId }
        }
    }

    suspend fun getSortedStopsDataWithoutActions(stopId: Int): StopDetail? {
        getAllActiveSortedStopsWithoutActions("getStopsDataWithoutActions").let { stopList ->
            return stopList.firstOrNull { it.stopid == stopId }
        }
    }

    internal suspend fun getAllActiveSortedStopsWithoutActions(caller: String): List<StopDetail> {
        return getStopsForDispatch(
            appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.getCurrentWorkFlowId(caller)
        ).filter { it.deleted == 0 }.getSortedStops()
    }
}