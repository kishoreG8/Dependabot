package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.StopInfo
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.repo.SendDispatchDataRepo
import com.trimble.ttm.routemanifest.repo.isAppLauncherWithMapsPerformanceFixInstalled
import com.trimble.ttm.routemanifest.utils.APPROACH
import com.trimble.ttm.routemanifest.utils.ARRIVED
import com.trimble.ttm.routemanifest.utils.COPILOT_ROUTE_CLEARING_TIME_SECONDS
import com.trimble.ttm.routemanifest.utils.DEPART
import com.trimble.ttm.routemanifest.utils.Utils.getStopList
import com.trimble.ttm.routemanifest.utils.ext.getNonDeletedAndUncompletedStopsBasedOnActions
import com.trimble.ttm.routemanifest.utils.ext.hasFreeFloatingStops
import kotlinx.coroutines.delay

class SendDispatchDataUseCase(
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val sendDispatchDataRepo: SendDispatchDataRepo,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val sendWorkflowEventsToAppUseCase : SendWorkflowEventsToAppUseCase
) {

    private val tag = "SendDispDataUC"

    fun sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(
        stopDetailList: List<StopDetail>,
        dispatchState: DispatchActiveState,
        caller: String
    ) {
        if(dispatchState == DispatchActiveState.ACTIVE){
            sendDispatchDataRepo.sendDispatchStopsForGeofence(
                caller,
                getStopList(stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions(), getManagedConfigDataForPolygonalOptOut())
            )
        }
    }

    private fun getManagedConfigDataForPolygonalOptOut() : Boolean {
        return sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig(tag)
    }

    suspend fun sendDispatchDataToMapsForSelectedFreeFloatStop(stopDetailList: List<StopDetail>) {
        sendDispatchDataRepo.sendDispatchStopsForRoute(
            getStopList(stopDetailList,  getManagedConfigDataForPolygonalOptOut()), hasFreeFloatingStops = stopDetailList.hasFreeFloatingStops(),
            shouldRedrawCopilotRoute = false
        )
        sendDispatchDataRepo.sendDispatchStopsForGeofence(
            "Navigate Icon - FreeFloat",
            getStopList(
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("sendDispatchDataToMapsForSelectedFreeFloatStop")
                    .getNonDeletedAndUncompletedStopsBasedOnActions(), getManagedConfigDataForPolygonalOptOut()
            )
        )
    }

    //sendCurrentDispatchDataToMaps - this method is invoked when a dispatch is modified, on clicking No in geofence prompt, on device reboot.
    //On responding No in Did you arrive prompt, we should not register the geofence again because if we do so, copilot fires the geofence prompt again.
    suspend fun sendCurrentDispatchDataToMaps(stopDetailList: List<StopDetail>? = null, shouldRegisterGeofence : Boolean = true, shouldRedrawCopilotRoute: Boolean, caller : String ) {
        Log.d(tag,"-----Inside sendCurrentDispatchDataToMaps")
        val cachedStops = stopDetailList?.getSortedStops() ?: fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("sendCurrentDispatchDataToMaps").getSortedStops()
        if (localDataSourceRepo.hasActiveDispatch() && localDataSourceRepo.getActiveDispatchId(tag)
                .isNotEmpty() && cachedStops.isNotEmpty()
        ) {
            if (cachedStops.hasFreeFloatingStops()) {
                // Draw route if current stop available
                localDataSourceRepo.getCurrentStop()?.let { currentStop ->
                    ArrayList<Stop>().let { stopList ->
                        stopList.add(currentStop)
                        stopList
                    }.map { stop ->
                        StopInfo().apply {
                            stopId = stop.stopId.toString()
                            latitude = stop.latitude
                            longitude = stop.longitude
                            approachRadius = stop.approachRadius
                            arrivedRadius = stop.arrivedRadius
                            departRadius = stop.departRadius
                        }
                    }.also { dispatchStopList ->
                        sendDispatchDataRepo.sendDispatchStopsForRoute(
                            dispatchStopList, hasFreeFloatingStops = true,
                            shouldRedrawCopilotRoute = shouldRedrawCopilotRoute
                        )
                        Log.i(
                            tag,
                            "Broadcast sent when current stop is available in preference for a free floating trip after device restart or app launcher app update. Stop(s) count ${dispatchStopList.size}"
                        )
                    }
                }
                if(shouldRegisterGeofence){
                    sendDispatchDataRepo.sendDispatchStopsForGeofence(
                        caller,
                        getStopList(cachedStops.getNonDeletedAndUncompletedStopsBasedOnActions(),  getManagedConfigDataForPolygonalOptOut())
                    )
                }
                Log.i(
                    tag,
                    "Broadcast sent for free floating trip after device restart or app launcher app update."
                )
            } else {
                drawRouteForCurrentStop(shouldRedrawCopilotRoute = shouldRedrawCopilotRoute)
                if(shouldRegisterGeofence){
                    sendDispatchDataRepo.sendDispatchStopsForGeofence(
                        caller,
                        getStopList(cachedStops.getNonDeletedAndUncompletedStopsBasedOnActions(), getManagedConfigDataForPolygonalOptOut()))
                }
                Log.i(
                    tag,
                    "Broadcast sent for sequenced trip after device restart or app launcher app update."
                )
            }
        } else {
            Log.i(
                tag,
                "Could not send broadcast on map service bound.No active dispatch or stop list is empty"
            )
        }
    }

    fun sendDispatchCompleteEvent() {
        Log.d(tag,"Inside sendDispatchCompleteEvent")
        sendDispatchDataRepo.sendDispatchEventDispatchComplete()
    }

    fun sendDispatchEventForClearRoute() {
        sendDispatchDataRepo.sendDispatchEventForClearRoute()
    }

    @Deprecated("This will be removed after sometime. This is added to handle backward compatibility with OLD AL")
    suspend fun sendDispatchEventForClearRouteWithDelay() {
        if (isAppLauncherWithMapsPerformanceFixInstalled) return
        sendDispatchDataRepo.sendDispatchEventForClearRoute()
        delay(COPILOT_ROUTE_CLEARING_TIME_SECONDS)
    }

    fun sendRemoveGeoFenceEvent(action: Action) {
        Log.logGeoFenceEventFlow(tag,"sendRemoveGeoFenceEvent: D${action.dispid} S${action.stopid} A${action.actionid}")
        val geofenceName = StringBuilder()
        if(action.actionType == ActionTypes.ARRIVED.ordinal){
            geofenceName.append(APPROACH)
            geofenceName.append(action.stopid)
            Log.d(tag,"geofence name to be removed: $geofenceName")
            sendDispatchDataRepo.sendRemoveGeoFenceEvent(geofenceName.toString())
        }
        geofenceName.clear()
        when(action.actionType){
            ActionTypes.APPROACHING.ordinal -> geofenceName.append(APPROACH)
            ActionTypes.ARRIVED.ordinal -> geofenceName.append(ARRIVED)
            ActionTypes.DEPARTED.ordinal -> geofenceName.append(DEPART)
            else -> geofenceName.append("")
        }
        if(geofenceName.toString().isNotEmpty()){
            geofenceName.append(action.stopid)
            Log.d(tag,"geofence name to be removed: $geofenceName")
            sendDispatchDataRepo.sendRemoveGeoFenceEvent(geofenceName.toString())
        }
    }

    internal fun removeAllGeofences() = sendDispatchDataRepo.sendDispatchEventRemoveGeofences()

    suspend fun drawRouteForCurrentStop(shouldRedrawCopilotRoute: Boolean) {
        val stops = fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("drawRouteForCurrentStop")
        getStopList(stops.filter {
            it.stopid == localDataSourceRepo.getCurrentStop()?.stopId
        },  getManagedConfigDataForPolygonalOptOut()).let {
            Log.i(tag, "drawRouteForCurrentStop. currentStop: $it shouldRedrawCopilotRoute: $shouldRedrawCopilotRoute")
            if (it.isNotEmpty()) {
                sendDispatchDataRepo.sendDispatchStopsForRoute(
                    it,
                    hasFreeFloatingStops = stops.hasFreeFloatingStops(),
                    shouldRedrawCopilotRoute = shouldRedrawCopilotRoute
                )
            } else {
                if (shouldRedrawCopilotRoute) sendDispatchDataRepo.sendDispatchEventForClearRoute()
                else {
                    //Ignored
                }
            }
        }
    }

    suspend fun setGeofenceForCachedStops(activeState: DispatchActiveState) {
        if (activeState == DispatchActiveState.ACTIVE) {
            sendDispatchDataRepo.sendDispatchStopsForGeofence(
                "ETAResult - Setting Geofence for cached stops",
                getStopList(
                    fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("setGeofenceForCachedStops")
                        .getNonDeletedAndUncompletedStopsBasedOnActions(),  getManagedConfigDataForPolygonalOptOut()
                )
            )
        }
    }

}