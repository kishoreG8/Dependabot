package com.trimble.ttm.routemanifest.repo

interface SendDispatchDataRepo {

    fun sendDispatchStopsForGeofence(
        caller: String,
        uncompletedStops: List<Any>
    )

    fun sendDispatchStopsForRoute(
        uncompletedStops: List<Any>,
        hasFreeFloatingStops : Boolean,
        shouldRedrawCopilotRoute: Boolean
    )

    fun sendDispatchEventForClearRoute()

    fun sendDispatchEventDispatchComplete()

    fun sendRemoveGeoFenceEvent(geoFenceName : String)

    fun sendDispatchEventRemoveGeofences()
}