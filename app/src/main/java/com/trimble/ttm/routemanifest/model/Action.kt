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
 *   Abstract: The data class for trip actions like approach, arrive and depart
 * *
 */
package com.trimble.ttm.routemanifest.model

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName
import com.trimble.ttm.commons.utils.DateUtil.getDeviceLocalTimeInUTC
import com.trimble.ttm.commons.utils.DateUtil.toUTC
import com.trimble.ttm.routemanifest.utils.INVALID_ACTION_ID
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Action(
    @get:PropertyName("ActionType") @set:PropertyName("ActionType")
    @SerializedName("ActionType") var actionType: Int = 0,
    @get:PropertyName("Actionid") @set:PropertyName("Actionid")
    @SerializedName("Actionid") var actionid: Int = 0,
    @get:PropertyName("DataCall") @set:PropertyName("DataCall")
    @SerializedName("DataCall") var dataCall: Int = 0,
    @get:PropertyName("DispEmailCcList") @set:PropertyName("DispEmailCcList")
    @SerializedName("DispEmailCcList") var dispEmailCcList: String = "",
    @get:PropertyName("DisplayEmailNotify") @set:PropertyName("DisplayEmailNotify")
    @SerializedName("DisplayEmailNotify") var displayEmailNotify: Int = 0,
    @get:PropertyName("DispLateEmail") @set:PropertyName("DispLateEmail")
    @SerializedName("DispLateEmail") var dispLateEmail: Int = 0,
    @get:PropertyName("DispMissedEtaNotify") @set:PropertyName("DispMissedEtaNotify")
    @SerializedName("DispMissedEtaNotify") var dispMissedEtaNotify: Int = 0,
    @get:PropertyName("Dispid") @set:PropertyName("Dispid")
    @SerializedName("Dispid") var dispid: String = "",
    @get:PropertyName("DriverAction") @set:PropertyName("DriverAction")
    @SerializedName("DriverAction") var driverAction: Int = 0,
    @get:PropertyName("DriverFormhash") @set:PropertyName("DriverFormhash")
    @SerializedName("DriverFormhash") var driverFormhash: Int = 0,
    @get:PropertyName("DriverFormid") @set:PropertyName("DriverFormid")
    @SerializedName("DriverFormid") var driverFormid: Int = 0,
    @get:PropertyName("Eta") @set:PropertyName("Eta")
    @SerializedName("Eta") var eta: String = "",
    @get:PropertyName("FuelMilliGals") @set:PropertyName("FuelMilliGals")
    @SerializedName("FuelMilliGals") var fuelMilliGals: String = "",
    @get:PropertyName("GpsOdometer") @set:PropertyName("GpsOdometer")
    @SerializedName("GpsOdometer") var gpsOdometer: String = "",
    @get:PropertyName("GpsQuality") @set:PropertyName("GpsQuality")
    @SerializedName("GpsQuality") var gpsQuality: String = "",
    @get:PropertyName("GufAudible") @set:PropertyName("GufAudible")
    @SerializedName("GufAudible") var gufAudible: Int = 0,
    @get:PropertyName("GufType") @set:PropertyName("GufType")
    @SerializedName("GufType") var gufType: Int = 0,
    @get:PropertyName("Latitude") @set:PropertyName("Latitude")
    @SerializedName("Latitude") var latitude: Double = 0.0,
    @get:PropertyName("Longitude") @set:PropertyName("Longitude")
    @SerializedName("Longitude") var longitude: Double = 0.0,
    @get:PropertyName("OccurredAt") @set:PropertyName("OccurredAt")
    @SerializedName("OccurredAt") var occurredAt: String = "",
    @get:PropertyName("Odometer") @set:PropertyName("Odometer")
    @SerializedName("Odometer") var odometer: String = "",
    @get:PropertyName("Radius") @set:PropertyName("Radius")
    @SerializedName("Radius") var radius: Int = 0,
    @get:PropertyName("ReplyButtonSkip") @set:PropertyName("ReplyButtonSkip")
    @SerializedName("ReplyButtonSkip") var replyButtonSkip: Int = 0,
    @get:PropertyName("Stopid") @set:PropertyName("Stopid")
    @SerializedName("Stopid") var stopid: Int = 0,
    @get:PropertyName("WasLate") @set:PropertyName("WasLate")
    @SerializedName("WasLate") var wasLate: Int = 0,
    @get:PropertyName("ResponseSent") @set:PropertyName("ResponseSent")
    @SerializedName("ResponseSent") var responseSent: Boolean = false,
    @get:PropertyName("FreeFormMessage") @set:PropertyName("FreeFormMessage")
    @SerializedName("FreeFormMessage") var freeFormMessage: String = "",
    @get:PropertyName("ForcedFormId") @set:PropertyName("ForcedFormId")
    @SerializedName("ForcedFormId") var forcedFormId: String = "",
    @get:PropertyName("DriverFormClass") @set:PropertyName("DriverFormClass")
    @SerializedName("DriverFormClass") var driverFormClass: Int = 0,
    @get:PropertyName("ForcedFormClass") @set:PropertyName("ForcedFormClass")
    @SerializedName("ForcedFormClass") var forcedFormClass: Int = 0,
    @get:PropertyName("ReplyActionType") @set:PropertyName("ReplyActionType")
    @SerializedName("ReplyActionType") var replyActionType: Int = -1,
    @get:PropertyName("TriggerReceived") @set:PropertyName("TriggerReceived")
    @SerializedName("TriggerReceived") var triggerReceived: Boolean = false,
    @get:PropertyName("TriggerReceivedTime") @set:PropertyName("TriggerReceivedTime")
    @SerializedName("TriggerReceivedTime") var triggerReceivedTime: String = ""

) : Parcelable {
    @IgnoredOnParcel
    val thisAction: ActionTypes
        get() = ActionTypes.values().find { it.ordinal == actionType }!!
}

fun Action.isValidDriverForm() = this.driverFormid > 0 && this.driverFormClass > -1

fun Action.isValidReplyForm() =
    this.forcedFormId.isNotEmpty() && this.forcedFormId.toInt() > 0 && this.forcedFormClass > -1

fun Action.isInValid() = this.actionid == INVALID_ACTION_ID

fun Action.isEtaMissed(caller: String) = if (this.eta.isEmpty()) {
    false
} else {
    this.eta.toUTC(caller) < getDeviceLocalTimeInUTC()
}

fun Action.getEtaDifferenceFromNow(caller: String): Long {
    val etaInUTC = this.eta.toUTC(caller)
    val currentTimeInUTC = getDeviceLocalTimeInUTC()
    return etaInUTC - currentTimeInUTC
}

// leaving this as const instead of enum due to missing information about other guf types
const val DRIVER_NEGATIVE_GUF = 2