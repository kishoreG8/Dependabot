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
 *   Abstract: Contains all the necessary information about a stop.Eg. actions - (approach,arrived,depart), Address, Coordinates etc.,
 * *
 */
package com.trimble.ttm.routemanifest.model

import com.google.gson.annotations.SerializedName
import com.trimble.ttm.commons.logger.TRIP_STOP_ACTION_LATE_NOTIFICATION
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.commons.utils.DateUtil.getMillisFromUTCDateTime
import com.trimble.ttm.commons.utils.DateUtil.toUTC
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.utils.*
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList


//region data class

data class StopDetail(
    @SerializedName("Asn") val asn: Int = 0,
    @SerializedName("DeleteOnExec") val deleteOnExec: Int = 0,
    @SerializedName("Deleted") val deleted: Int = 0,
    @SerializedName("DeliveryAsn") val deliveryAsn: Int = 0,
    @SerializedName("Description") val description: String = "",
    @SerializedName("DescriptionLong") val descriptionLong: String = "",
    @SerializedName("Dispid") val dispid: String = "",
    @SerializedName("DtwMethod") val dtwMethod: Int = 0,
    @SerializedName("DtwMins") val dtwMins: Int = 0,
    @SerializedName("Expiredate") val expiredate: String = "",
    @SerializedName("Latitude") val latitude: Double = 0.0,
    @SerializedName("LmAutocorrect") val lmAutocorrect: Int = 0,
    @SerializedName("Lmid") val lmid: Int = 0,
    @SerializedName("Longitude") val longitude: Double = 0.0,
    @SerializedName("Name") val name: String = "",
    @SerializedName("Sequenced") val sequenced: Int = 0,
    @SerializedName("Sessionid") val sessionid: String = "",
    @SerializedName("StopUserdata") val stopUserdata: String = "",
    @SerializedName("Stopid") val stopid: Int = -1,
    @SerializedName("CompletedTime") var completedTime: String = "", // For Arrive completion time
    @SerializedName("DepartedTime") var departedTime: String = "",
    @SerializedName("SiteCoordinates") var siteCoordinates: MutableList<SiteCoordinate>? = mutableListOf(),
    @SerializedName("isManualArrival")var isManualArrival : Boolean = false,
    @SerializedName("arrivalLatitude")var arrivalLatitude : Double = 0.0,
    @SerializedName("arrivalLongitude")var arrivalLongitude : Double = 0.0
) {
    val ThisStopType: StopType
        get() {
            return if (completedTime.isEmpty()) StopType.ENTER else StopType.STOP_COMPLETED
        }
    var Address: Address? = null
    var StopCompleted: Boolean = false
    var EstimatedArrivalTime: Calendar? = null
    var etaTime: Date? = null
    var leg: Leg? = null
    var Actions: CopyOnWriteArrayList<Action> = CopyOnWriteArrayList()
    val isEtaAvailable: Boolean
        get() = this.getStopETA().isNotEmpty()

    fun hasETACrossed(): Boolean {
        return if (this.completedTime.isEmpty()) {
            this.getArrivedAction()?.isEtaMissed(TRIP_STOP_ACTION_LATE_NOTIFICATION + "StopDetailViewBinding") ?: false
        } else {
            val arriveEta = this.getArrivedAction()?.eta?.toUTC(TRIP_STOP_ACTION_LATE_NOTIFICATION + "StopDetailViewBinding") ?: 0L
            if (arriveEta == 0L) return false
            val stopCompletionTime = getMillisFromUTCDateTime(utcDateTime = this.completedTime, caller = TRIP_STOP_ACTION_LATE_NOTIFICATION + "StopDetailViewBinding")
            arriveEta < stopCompletionTime
        }
    }

    val prePlannedArrivalTime: String
        get() = Utils.getSystemLocalDateTimeFromUTCDateTime(this.getStopETA())

    fun isArrivalAvailable() = !(hasNoArrivedAction())
    fun getDepatureTime() = Utils.getSystemLocalDateTimeFromUTCDateTime(this.departedTime)
}


//region enum

enum class StopType {
    PRE_TRIP,
    ENTER,
    EXIT,
    GAS_STATION,
    POST_TRIP,
    STOP_COMPLETED
}

enum class ActionTypes(val action: String) {
    APPROACHING("approach"),
    ARRIVED("arrive"),
    DEPARTED("depart")
}

enum class DetentionWarningMethod(val value: Int) {
    NONE(0),
    START_AT_ARRIVAL(1),
    NO_PENALTY(2)
}

fun StopDetail.logAction(action: Action): String =
    "dispatchId: ${this.dispid} StopId: ${this.stopid} ActionId: ${action.actionid} " +
            "${if (action.actionType >= 0) "ActionType: ${action.thisAction.name}" else ""} " +
            "DriverFormId: ${action.driverFormid} ForcedFormId: ${action.forcedFormId}"

fun List<StopDetail>.getSortedStops(): List<StopDetail> = this.sortedBy { it.stopid }

fun CopyOnWriteArrayList<StopDetail>.getSortedStops(): CopyOnWriteArrayList<StopDetail> =
    CopyOnWriteArrayList(this.sortedBy { it.stopid })

fun StopDetail.hasArrivingActionOnly() =
    this.Actions.size == 1 && this.Actions[0].actionType == ActionTypes.APPROACHING.ordinal

fun StopDetail.hasDepartActionOnly() =
    this.Actions.size == 1 && this.Actions[0].actionType == ActionTypes.DEPARTED.ordinal

fun StopDetail.hasArrivingAndDepartActionOnly() =
    this.Actions.size == 2 && this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }
        .isNull()

fun StopDetail.hasNoDepartedAction() =
    this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal }.isNull()

fun StopDetail.hasNoArrivedAction() =
    this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }.isNull()

fun StopDetail.getArrivingAction() =
    this.Actions.find { it.actionType == ActionTypes.APPROACHING.ordinal }

fun StopDetail.getArrivedAction() =
    this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }

fun StopDetail.hasNoPendingAction() =
    this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal && !it.responseSent }.isNull()

fun StopDetail.hasArrivedActionOnly() =
    this.Actions.size == 1 && this.Actions[0].actionType == ActionTypes.ARRIVED.ordinal

fun StopDetail.hasArrivingAndArrivedActionOnly() =
    this.Actions.size == 2 && this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal }
        .isNull()

fun StopDetail.getStopETA(): String =
    this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }?.eta ?: EMPTY_STRING

fun StopDetail.getArrivalTriggerReceivedTime(): String =
    this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }?.triggerReceivedTime ?: EMPTY_STRING

fun StopDetail.getDepartTriggerReceivedTime(): String =
    this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal }?.triggerReceivedTime ?: EMPTY_STRING

fun StopDetail.getArriveRadius(): Int =
    this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }?.radius ?: 0

fun StopDetail.isArrived() = this.completedTime.isNotEmpty()

fun StopDetail.isDeparted() = this.departedTime.isNotEmpty()

fun StopDetail.isSequencedStop() = this.sequenced == 1

fun StopDetail.isStopSoftDeleted() = this.deleted == 1

fun List<StopDetail>.getArrivedActionForGivenStop(stopId: Int): Action? = firstOrNull { it.stopid == stopId }?.Actions?.firstOrNull { it.actionType == ActionTypes.ARRIVED.ordinal }

fun List<StopDetail>.getSortedStopsBasedOnArrivalTriggerReceivedTime(): List<StopDetail> =
    this.sortedBy { it.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }?.triggerReceivedTime }

fun StopDetail.getDefaultArrivedRadius(): Int? {
    val departData = this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal }
    val isStopHasDepartAction = departData.isNotNull()
    if (isStopHasDepartAction) {
        val departRadius =
            ((departData!!.radius) * ARRIVED_RADIUS_RELATIVE_TO_DEPART_RADIUS_IN_PERCENTAGE).toInt()
        return departRadius
    }
    return null
}