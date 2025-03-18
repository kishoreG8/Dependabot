package com.trimble.ttm.commons.model

data class Stop(
    var dispId: String = "",
    var stopId: Int = -1,
    var stopName: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var approachRadius: Int = 0,
    var arrivedRadius: Int = 0,
    var departRadius: Int = 0,
    var appraochFormId: Int = 0,
    var arrivedFormId: Int = 0,
    var arrivedFormClass: Int = -1,
    var departFormId: Int = 0,
    var approachResponseSent: Boolean = false,
    var arrivedResponseSent: Boolean = false,
    var departResponseSent: Boolean = false,
    var hasArriveAction: Boolean = false,
    var siteCoordinates: MutableList<SiteCoordinate> = mutableListOf()
)