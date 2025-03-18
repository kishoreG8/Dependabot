package com.trimble.ttm.routemanifest.model

import com.google.gson.annotations.SerializedName

data class ArrivalReason (
    @SerializedName("stopLocation") var stopLocation: Any? = null,
    @SerializedName("driverId") var driverID: String = "",
    @SerializedName("ETA") var eta : String = "",
    @SerializedName("geofenceType") var geofenceType : String = "",
    @SerializedName("sequenced") var sequenced: Int = 0,
    @SerializedName("arrivalActionStatus") var arrivalActionStatus: String? = null,
    @SerializedName("arrivalActionStatusTime") var arrivalActionStatusTime: String? = null,
    @SerializedName("arrivalActionStatusLocation") var arrivalActionStatusLocation: Any? = null,
    @SerializedName("distanceToArrivalActionStatus") var distanceToArrivalActionStatus: Double = 0.0,
    @SerializedName("insideGeofenceAtArrivalActionStatus") var insideGeofenceAtArrivalActionStatus: Boolean? = null,
    @SerializedName("insideGeofenceAtArrival") var insideGeofenceAtArrival: Boolean? = null,
    @SerializedName("arrivalType") var arrivalType: String? = null,
    @SerializedName("arrivalTime") var arrivalTime: String? = null,
    @SerializedName("arrivalLocation") var arrivalLocation: Any? = null,
    @SerializedName("distanceToArrivalLocation") var distanceToArrivalLocation: Double = 0.0
)

enum class ArrivalActionStatus{
    TRIGGER_RECEIVED,
    TRIGGER_IGNORED_BY_WF,
    DYA_IGNORED_BY_DRIVER,
    DRIVER_CLICKED_NO,
    DRIVER_NOT_IN_STOP_LOCATION,
    TRIGGER_NOT_RECEIVED
}

enum class ArrivalType{
    DRIVER_CLICKED_YES,
    TIMER_EXPIRED,
    MANUAL_ARRIVAL
}