package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.ARRIVAL_REASON
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.ArrivalType
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ArrivalActionStatus
import com.trimble.ttm.routemanifest.utils.ARRIVAL_ACTION_STATUS
import com.trimble.ttm.routemanifest.utils.ARRIVAL_ACTION_STATUS_LOCATION
import com.trimble.ttm.routemanifest.utils.ARRIVAL_ACTION_STATUS_TIME
import com.trimble.ttm.routemanifest.utils.ARRIVAL_LOCATION
import com.trimble.ttm.routemanifest.utils.ARRIVAL_TIME
import com.trimble.ttm.routemanifest.utils.ARRIVAL_TYPE
import com.trimble.ttm.routemanifest.utils.DISTANCE_TO_ARRIVAL_ACTION_STATUS_LOCATION
import com.trimble.ttm.routemanifest.utils.DISTANCE_TO_ARRIVAL_LOCATION
import com.trimble.ttm.routemanifest.utils.DRIVERID
import com.trimble.ttm.routemanifest.utils.ETA
import com.trimble.ttm.routemanifest.utils.GEOFENCE_TYPE
import com.trimble.ttm.routemanifest.utils.INSIDE_GEOFENCE_AT_ARRIVAL
import com.trimble.ttm.routemanifest.utils.INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS
import com.trimble.ttm.routemanifest.utils.SEQUENCED
import com.trimble.ttm.routemanifest.utils.STOP_LOCATION
import com.trimble.ttm.routemanifest.utils.Utils.containsLocation
import com.trimble.ttm.routemanifest.utils.Utils.getDistanceBetweenLatLongs
import com.trimble.ttm.routemanifest.utils.Utils.getGeofenceType
import com.trimble.ttm.routemanifest.utils.Utils.toFeet
import java.util.Calendar
import java.util.Locale

class ArrivalReasonUsecase(
    private val backboneUseCase: BackboneUseCase,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val arrivalReasonEventRepo: ArrivalReasonEventRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val managedConfigurationRepo: ManagedConfigurationRepo,
) {

    suspend fun getArrivalReasonMap(
        arrivalReason: String,
        stopId: Int,
        isArrival: Boolean
    ): HashMap<String, Any> {
        val cid = appModuleCommunicator.doGetCid()
        val truckNum = appModuleCommunicator.doGetTruckNumber()
        val dispatchId = localDataSourceRepo.getActiveDispatchId("getArrivalReasonMap")
        if (cid.isEmpty() || truckNum.isEmpty() || dispatchId.isEmpty()) {
            Log.i(
                ARRIVAL_REASON,
                "invalid data request to access stop and its actions. cid:$cid trucknum:$truckNum dispId:$dispatchId"
            )
            return HashMap()
        }
        val currentStop =
            dispatchFirestoreRepo.getStop(cid, truckNum, dispatchId, stopId.toString())
        val arriveAction : Action? = dispatchFirestoreRepo.getActionsOfStop(dispatchId, stopId.toString(),"getArrivalReasonMap").firstOrNull { it.actionType == ActionTypes.ARRIVED.ordinal }

        if (arriveAction == null) {
            Log.d(
                ARRIVAL_REASON,
                "There is no arrived actions present for this stop", null, "dispatch Id" to dispatchId, "stop Id" to stopId
            )
            return HashMap()
        }
        val isPolygonalOptOut = managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(ARRIVAL_REASON)

        val currentLocation = backboneUseCase.getCurrentLocation()
        val arrivalReasonHashMap = HashMap<String, Any>().also {
            it[STOP_LOCATION] = SiteCoordinate(currentStop.latitude, currentStop.longitude)
            it[SEQUENCED] = currentStop.sequenced
            it[ETA] = currentStop.etaTime.toString()
            it[GEOFENCE_TYPE] = getGeofenceType(currentStop.siteCoordinates ?: mutableListOf(), isPolygonalOptOut)
            it[DRIVERID] = backboneUseCase.getCurrentUser()
        }

        if (isArrival) {
            arrivalReasonHashMap.also { data ->
                data[ARRIVAL_TYPE] = arrivalReason
                data[ARRIVAL_TIME] =
                    getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
                data[ARRIVAL_LOCATION] =
                    SiteCoordinate(currentLocation.first, currentLocation.second)
                if (currentStop.siteCoordinates != null) {
                    data[INSIDE_GEOFENCE_AT_ARRIVAL] = containsLocation(
                        Pair(currentLocation.first, currentLocation.second),
                        Pair(currentStop.latitude, currentStop.longitude),
                        currentStop.siteCoordinates!!.toList(),
                        radius = arriveAction.radius,
                        geofenceType = data[GEOFENCE_TYPE] as String,
                    )
                    data[DISTANCE_TO_ARRIVAL_LOCATION] =
                        getDistanceBetweenLatLongs(
                            currentLocation,
                            Pair(currentStop.latitude, currentStop.longitude)
                        ).toFeet()
                }
            }
        } else {
            arrivalReasonHashMap.also { data ->
                data[ARRIVAL_ACTION_STATUS] = arrivalReason
                data[ARRIVAL_ACTION_STATUS_TIME] =
                    getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
                data[ARRIVAL_ACTION_STATUS_LOCATION] =
                    SiteCoordinate(currentLocation.first, currentLocation.second)
                if (currentStop.siteCoordinates != null) {
                    data[INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS] = containsLocation(
                        Pair(currentLocation.first, currentLocation.second),
                        Pair(currentStop.latitude, currentStop.longitude),
                        currentStop.siteCoordinates!!.toList(),
                        radius = arriveAction.radius,
                        geofenceType = data[GEOFENCE_TYPE] as String
                    )
                    data[DISTANCE_TO_ARRIVAL_ACTION_STATUS_LOCATION] = getDistanceBetweenLatLongs(
                        currentLocation,
                        Pair(currentStop.latitude, currentStop.longitude)
                    ).toFeet()
                }
            }
        }
        return arrivalReasonHashMap
    }


    suspend fun setArrivalReasonForCurrentStop(stopId: Int, valueMap: HashMap<String, Any>) {
        if (appModuleCommunicator.doGetCid() == EMPTY_STRING || appModuleCommunicator.doGetTruckNumber() == EMPTY_STRING || appModuleCommunicator.getCurrentWorkFlowId(
                "setArrivalReasonforCurrentStop"
            ) == EMPTY_STRING
        ) {
            Log.w(ARRIVAL_REASON, "CID/TruckID/WorkflowID is Empty")
            return
        }
        if (valueMap[ARRIVAL_TYPE] == ArrivalType.MANUAL_ARRIVAL.name && arrivalReasonEventRepo.getCurrentStopArrivalReason(arrivalReasonEventRepo.getArrivalReasonCollectionPath(stopId)).arrivalActionStatus.isNull())
        {
            if(valueMap[INSIDE_GEOFENCE_AT_ARRIVAL] == false) valueMap[ARRIVAL_ACTION_STATUS] = ArrivalActionStatus.DRIVER_NOT_IN_STOP_LOCATION.name
            else valueMap[ARRIVAL_ACTION_STATUS] = ArrivalActionStatus.TRIGGER_NOT_RECEIVED.name
        }
        arrivalReasonEventRepo.setArrivalReasonforStop(
            path = arrivalReasonEventRepo.getArrivalReasonCollectionPath(stopId),
            valueMap = valueMap
        )
    }

    suspend fun updateArrivalReasonForCurrentStop(stopId: Int, valueMap: HashMap<String, Any>) {
        if (appModuleCommunicator.doGetCid() == EMPTY_STRING || appModuleCommunicator.doGetTruckNumber() == EMPTY_STRING || appModuleCommunicator.getCurrentWorkFlowId(
                "updateArrivalReasonforCurrentStop"
            ) == EMPTY_STRING
        ) {
            Log.w(ARRIVAL_REASON, "CID/TruckID/WorkflowID is Empty")
            return
        }
        arrivalReasonEventRepo.updateArrivalReasonforStop(
            path = arrivalReasonEventRepo.getArrivalReasonCollectionPath(stopId),
            valueMap = valueMap
        )
    }


}