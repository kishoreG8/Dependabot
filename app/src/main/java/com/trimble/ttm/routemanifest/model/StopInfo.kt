package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.model.SiteCoordinate
import java.util.Calendar
import java.util.Date

data class StopInfo(
    val description: String = "",
    var dispid: String = "",
    var dispatchName: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var name: String = "",
    var stopId: String = "",
    var note: String = "",
    var completedTime: String = "",
    var isWayPoint: Boolean = false,
    var stopIndex: String = "-1",
    var siteCoordinates: MutableList<SiteCoordinate> = mutableListOf()
) {
    var Address: Address =
        Address()
    var EstimatedArrivalTime: Calendar? = null
    var etaTime: Date? = null
    var leg: Leg? = null
    var plannedDurationMinutes: Int = 0
    var approachRadius: Int? = null
    var arrivedRadius: Int? = null
    var departRadius: Int? = null
    /* We are introducing these action variables for polygon geofence usecase.
       For polygon geofence, we need to register the geofence only on the availability of action instead of radius value.*/
    var hasApproachAction: Boolean = false
    var hasArriveAction: Boolean = false
    var hasDepartAction: Boolean = false
    var isManualArrival: Boolean = false
    var arrivalLatitude: Double = 0.0
    var arrivalLongitude: Double = 0.0
}
