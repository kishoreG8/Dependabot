package com.trimble.ttm.routemanifest.model

data class TripMetrics (
    var completedStopsWithCompletedTimeMissing : List<StopDetail> = listOf(),
    var stopsSortedOnArrivalTriggerReceivedTime : List<StopDetail> = listOf(),
    var noOfStopsTriggerReceived : GeofenceType = GeofenceType(0, 0),
    var noOfStopsBasedOnGeofenceType : GeofenceType = GeofenceType(0, 0),
    var noOfStopsDeleted : Int = 0,
    var noOfArrivalResponseSent : GeofenceType = GeofenceType(0, 0),
    var stopsOverview : List<String> = listOf(),
    var isSeqTrip: Boolean = false,
    var isSeqTripOutOfTrailSeq: Boolean = false
)

data class GeofenceType(var circular: Int, var polygonal: Int)