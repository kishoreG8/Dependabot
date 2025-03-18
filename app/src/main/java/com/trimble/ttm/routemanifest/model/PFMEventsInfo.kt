package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.routemanifest.utils.MILE_TYPE_GPS

sealed class PFMEventsInfo {
    data class TripEvents(
        val reasonType: String,
        val negativeGuf: Boolean
    ) : PFMEventsInfo()

    data class StopActionEvents(
        val reasonType: String,
        val negativeGuf: Boolean = false,
        val mileType: String = MILE_TYPE_GPS
    ) : PFMEventsInfo()

}

