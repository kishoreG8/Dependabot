package com.trimble.ttm.routemanifest.model

import android.content.Context

data class StopActionEventData(
    val stopId: Int,
    val actionType: Int,
    val context: Context,
    val hasDriverAcknowledgedArrivalOrManualArrival: Boolean
)
