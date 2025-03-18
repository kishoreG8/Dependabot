package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.usecases.TripStartCaller

data class TripStartInfo(
    val stopDetailList: List<StopDetail>? = null,
    val timeInMillis: Long,
    val pfmEventsInfo: PFMEventsInfo.TripEvents,
    val cid: String,
    val vehicleNumber: String,
    val tripStartCaller: TripStartCaller,
    val caller: String,
    val dispatchId: String = EMPTY_STRING
)