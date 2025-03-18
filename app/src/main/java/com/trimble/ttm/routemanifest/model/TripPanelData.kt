package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.utils.EMPTY_STRING

data class TripPanelData(
    val message: String = EMPTY_STRING,
    val priority: Int = -1,
    val messageId: Int = -1,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
