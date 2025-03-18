/*
 * *
 *  * Copyright Trimble Inc., 2019 - 2020 All rights reserved.
 *  *
 *  * Licensed Software Confidential and Proprietary Information of Trimble Inc.,
 *   made available under Non-Disclosure Agreement OR License as applicable.
 *
 *   Product Name: TTM - Route Manifest
 *
 *   Author: Vignesh Elangovan
 *
 *   Created On: 12-08-2020
 *
 *   Abstract: Contains necessary information about a trip.
 * *
 */
package com.trimble.ttm.routemanifest.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Dispatch(
    @SerializedName("Cid") val cid: Int = 0,
    @SerializedName("Created") val created: String = "",
    @SerializedName("DeliveryAsn") val deliveryAsn: Int = 0,
    @SerializedName("Description") val description: String = "",
    @SerializedName("Dispid") val dispid: String = "",
    @SerializedName("IsCompleted") var isCompleted: Boolean = false,
    @SerializedName("IsReady") var isReady: Boolean = false,
    @SerializedName("Name") val name: String = "",
    @SerializedName("Sessionid") val sessionid: Int = 0,
    @SerializedName("TripEndCall") val tripEndCall: Int = 0,
    @SerializedName("TripOi") val tripOi: Int = 0,
    @SerializedName("TripPfm") val tripPfm: Int = 0,
    @SerializedName("TripStartCall") val tripStartCall: Int = 0,
    @SerializedName("TripStartDisableAuto") val tripStartDisableAuto: Int = 0,
    @SerializedName("TripStartDisableManual") val tripStartDisableManual: Int = 0,
    @SerializedName("TripStartNegGuf") val tripStartNegGuf: Int = 0,
    @SerializedName("TripStartTimed") val tripStartTimed: Int = 0,
    @SerializedName("TripStarttime") val tripStarttime: String = "",
    @SerializedName("UID") val uID: Int = 0,
    @SerializedName("Userdata1") val userdata1: String = "",
    @SerializedName("Userdata2") val userdata2: String = "",
    @SerializedName("Username") val username: String = "",
    @SerializedName("VehicleNumber") val vehicleNumber: String = "",
    @SerializedName("Vid") val vid: Long = 0L,
    @SerializedName("StopsCountOfDispatch") var stopsCountOfDispatch:Int=0
) : Parcelable {
    @IgnoredOnParcel
    val stopDetails: MutableList<StopDetail> = mutableListOf()
    @IgnoredOnParcel
    var isActive = false
}

fun Dispatch.isValid() = this.dispid.isNotEmpty()

enum class TripTypes {
    FREE_FLOATING,
    SEQUENTIAL,
    MIXED
}