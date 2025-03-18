package com.trimble.ttm.routemanifest.model

class LastSentTripPanelMessage(
    var messageId: Int = -1,
    var message: String = "",
    vararg var stopId: Int
)

class LastRespondedTripPanelMessage(var messageId: Int = -1, var message: String = "")



