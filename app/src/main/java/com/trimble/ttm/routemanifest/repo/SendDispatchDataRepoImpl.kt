package com.trimble.ttm.routemanifest.repo

import android.os.Bundle
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.EVENT_TYPE_KEY
import com.trimble.launchercommunicationlib.commons.MESSAGE_RESPONSE_ACTION_KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_MAP_REQUEST
import com.trimble.ttm.routemanifest.utils.CLEAR_ROUTE
import com.trimble.ttm.routemanifest.utils.DEFAULT_ROUTE_CALC_REQ_DEBOUNCE_THRESHOLD
import com.trimble.ttm.routemanifest.utils.DISPATCH_COMPLETE
import com.trimble.ttm.routemanifest.utils.GEOFENCE_NAME
import com.trimble.ttm.routemanifest.utils.KEY_DISPATCH_STOPS_GEO_COORDINATES
import com.trimble.ttm.routemanifest.utils.KEY_REDRAW_COPILOT_ROUTE
import com.trimble.ttm.routemanifest.utils.KEY_STOP_INFO_LIST
import com.trimble.ttm.routemanifest.utils.REMOVE_ALL_GEOFENCE
import com.trimble.ttm.routemanifest.utils.REMOVE_GEOFENCE
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getEventTypeKeyForGeofence
import com.trimble.ttm.routemanifest.utils.Utils.getEventTypeKeyForRoute
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//This variable is set to true if App Launcher with maps code refactoring is installed. This check is done when the DispatchListActivity is loaded.
var isAppLauncherWithMapsPerformanceFixInstalled = false
class SendDispatchDataRepoImpl : SendDispatchDataRepo {
    private var sendingDispatchStopsForRouteJob: Job? = null
    private var coroutineScope = CoroutineScope(Dispatchers.IO)


    override fun sendDispatchStopsForGeofence(
        caller: String,
        uncompletedStops: List<Any>
    ) {
        Log.d(TRIP_MAP_REQUEST,"-----Inside SendDispatchDataRepoImpl sendDispatchStopsForGeofence()")
        val unCompletedStopList = ArrayList<String>()
        uncompletedStops.forEach { dispatchStop ->
            Utils.toJsonString(dispatchStop)?.let { unCompletedStopList.add(it) }
        }
        Bundle().let {
            it.putStringArrayList(KEY_DISPATCH_STOPS_GEO_COORDINATES, unCompletedStopList)
            it.putStringArrayList(KEY_STOP_INFO_LIST, unCompletedStopList)
            it.putString(EVENT_TYPE_KEY, getEventTypeKeyForGeofence())
            AppLauncherCommunicator.sendMessage(
                messageId = MESSAGE_RESPONSE_ACTION_KEY,
                data = it,
                communicationProviderCallBack = null
            )
            Log.i(TRIP_MAP_REQUEST, "-----Geofence Only Caller : $caller--> $unCompletedStopList")
        }
    }

    override fun sendDispatchStopsForRoute(
        uncompletedStops: List<Any>,
        hasFreeFloatingStops: Boolean,
        shouldRedrawCopilotRoute: Boolean
    ) {
        sendingDispatchStopsForRouteJob?.cancel()
        sendingDispatchStopsForRouteJob = coroutineScope.launch(CoroutineName(TRIP_MAP_REQUEST)) {
            Log.d(TRIP_MAP_REQUEST, "ONLY_ROUTE - only route request received..")
            delay(DEFAULT_ROUTE_CALC_REQ_DEBOUNCE_THRESHOLD)
            val unCompletedStopList = ArrayList<String>()
            uncompletedStops.forEach { dispatchStop ->
                Utils.toJsonString(dispatchStop)?.let { unCompletedStopList.add(it) }
            }
            var shouldRedrawRoute = shouldRedrawCopilotRoute
            if(hasFreeFloatingStops){
                Log.d(TRIP_MAP_REQUEST,"ONLY_ROUTE - Inside sendDispatchStopsForRoute setting shouldRedrawRoute")
                shouldRedrawRoute = true
            }
            Bundle().let {
                it.putStringArrayList(KEY_DISPATCH_STOPS_GEO_COORDINATES, unCompletedStopList)
                it.putString(EVENT_TYPE_KEY, getEventTypeKeyForRoute())
                it.putBoolean(KEY_REDRAW_COPILOT_ROUTE, shouldRedrawRoute)
                AppLauncherCommunicator.sendMessage(
                    messageId = MESSAGE_RESPONSE_ACTION_KEY,
                    data = it,
                    communicationProviderCallBack = null
                )
                Log.i(TRIP_MAP_REQUEST, "ONLY_ROUTE - Route Only request sent and unCompletedStopList ${unCompletedStopList}}")
            }
        }
    }

    override fun sendDispatchEventForClearRoute() {
        Bundle().let {
            it.putString(EVENT_TYPE_KEY, CLEAR_ROUTE)
            AppLauncherCommunicator.sendMessage(
                messageId = MESSAGE_RESPONSE_ACTION_KEY,
                data = it,
                communicationProviderCallBack = null
            )
            Log.d(TRIP_MAP_REQUEST, "-----Clear Route Called")
        }
    }

    override fun sendDispatchEventDispatchComplete() {
        Bundle().let {
            it.putString(EVENT_TYPE_KEY, DISPATCH_COMPLETE)
            AppLauncherCommunicator.sendMessage(
                messageId = MESSAGE_RESPONSE_ACTION_KEY,
                data = it,
                communicationProviderCallBack = null
            )
            Log.d(TRIP_MAP_REQUEST, "-----Dispatch Complete Called")
        }
    }

    override fun sendRemoveGeoFenceEvent(geoFenceName: String) {
        Bundle().let {
            it.putString(EVENT_TYPE_KEY, REMOVE_GEOFENCE)
            it.putString(GEOFENCE_NAME, geoFenceName)
            AppLauncherCommunicator.sendMessage(
                messageId = MESSAGE_RESPONSE_ACTION_KEY,
                data = it,
                communicationProviderCallBack = null
            )

            Log.i(TRIP_MAP_REQUEST, "------Remove Geofence event Called for $geoFenceName")
        }
    }

    override fun sendDispatchEventRemoveGeofences() {
        Bundle().let {
            it.putString(EVENT_TYPE_KEY, REMOVE_ALL_GEOFENCE)
            AppLauncherCommunicator.sendMessage(
                messageId = MESSAGE_RESPONSE_ACTION_KEY,
                data = it,
                communicationProviderCallBack = null
            )
            Log.i(TRIP_MAP_REQUEST, "-----Remove All Geofence called")
        }
    }
}