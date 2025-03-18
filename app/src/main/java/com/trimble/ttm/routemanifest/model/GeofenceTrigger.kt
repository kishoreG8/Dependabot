package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import java.util.Date

data class GeofenceTrigger(
    val dispatchId: String = EMPTY_STRING,
    val stopId: String = EMPTY_STRING,
    val geofenceEvent: String = EMPTY_STRING,
    val geofenceName: String = EMPTY_STRING,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val geofenceTime: Date? = null
)