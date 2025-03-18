package com.trimble.ttm.routemanifest.utils.ext

import android.content.Context
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.utils.UTC_TIME_ZONE_ID
import com.trimble.ttm.formlibrary.utils.*
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.model.*
import com.trimble.ttm.routemanifest.utils.DEFAULT_DEPART_RADIUS_IN_FEET
import com.trimble.ttm.routemanifest.utils.ISO_DATE_TIME_FORMAT
import com.trimble.ttm.routemanifest.utils.Utils.calendarWithDefaultDateTimeOffset
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

fun Double.getTimeStringFromMinutes(context: Context): String = String.format(
    context.getString(R.string.estimatedDriveTimeFormat),
    this.div(60).toInt(),
    this.rem(60).toInt()
)

fun Double.getDaysFromMinutes(): Double = this / 1440

fun List<StopDetail>.hasFreeFloatingStops(): Boolean = this.any { it.sequenced == 0 }

fun List<StopDetail>.isSequentialTrip(): Boolean = this.all { it.sequenced == 1 }

fun List<StopDetail>.isFreeFloatingTrip(): Boolean = this.all { it.sequenced == 0 }

fun List<StopDetail>.getStopListBeforeGivenStop(stopId: Int): List<StopDetail> {
    return this.filter { it.stopid < stopId }
}

fun List<StopDetail>.getStopListFromGivenStop(stopId: Int): List<StopDetail> {
    return this.filter { it.stopid >= stopId }
}

fun List<StopDetail>.getCompletedStopsBasedOnCompletedTime() =
    this.filter { it.deleted == 0 && it.completedTime.isNotEmpty() }

fun List<StopDetail>.getUncompletedStopsBasedOnActions() =
    this.filter { stopDetail ->
        (stopDetail.Actions.isEmpty()) || stopDetail.Actions.filter {
            it.responseSent.not()
        }.isNullOrEmpty().not()
    }

fun List<StopDetail>.getUncompletedStopsBasedOnCompletedTime() =
    this.filter { it.deleted == 0 && it.completedTime.isEmpty() }

fun List<StopDetail>.getUncompletedStops(): List<StopDetail> {
    return this.getUncompletedStopsBasedOnCompletedTime()
}

fun List<StopDetail>.getNonDeletedAndUncompletedStopsBasedOnActions() = this.filter { stopDetail ->
    (stopDetail.deleted == 0) && (stopDetail.Actions.any { it.responseSent.not() })
}

fun List<StopDetail>.areAllStopsComplete(): Boolean {
    if (this.isEmpty()) {
        return true
    }
    return this.none { it.completedTime.isEmpty() }
}

fun Int?.isGreaterThan(other: Int?) =
    this != null && other != null && this > other

fun Int?.isLessThan(other: Int?) =
    this != null && other != null && this < other

fun Int?.isGreaterThanAndEqualTo(other: Int?) =
    this != null && other != null && this >= other

fun Int?.isLessThanAndEqualTo(other: Int?) =
    this != null && other != null && this <= other

fun Int?.isEqualTo(other: Int?) =
    this != null && other != null && this == other

fun Int?.isNotEqualTo(other: Int?) =
    this != null && other != null && this != other

fun String.toCalendarInUTC(): Calendar {
    if (this.isEmpty()) return calendarWithDefaultDateTimeOffset()
    return try {
        val date = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
        }.parse(this)
        val calendar = Calendar.getInstance()
        calendar.time = date!!
        calendar
    } catch (e: Exception) {
        calendarWithDefaultDateTimeOffset()
    }
}

fun Float.toMilesText(): String {
    return try {
        DecimalFormat("#.#").format(this)
    } catch (e: Exception) {
        "0"
    }
}

fun List<StopDetail>.getStopInfoList( dispatchName:String = EMPTY_STRING, isPolygonalOptOut: Boolean): List<StopInfo> = this.map { stopDetail ->
    StopInfo().apply {
        dispid = stopDetail.dispid
        if (dispatchName.isNotEmpty()){
            this.dispatchName = dispatchName
        }
        stopId = stopDetail.stopid.toString()
        name = stopDetail.name
        latitude = stopDetail.latitude
        longitude = stopDetail.longitude
        completedTime = stopDetail.completedTime
        isManualArrival = stopDetail.isManualArrival
        arrivalLatitude = stopDetail.arrivalLatitude
        arrivalLongitude = stopDetail.arrivalLongitude
        if(stopDetail.siteCoordinates.isNotNull() && isPolygonalOptOut.not()) siteCoordinates = stopDetail.siteCoordinates!!
        stopDetail.Actions.forEach {
            if (it.responseSent.not()) {
                when (it.actionType) {
                    0 -> {
                        approachRadius = it.radius
                        hasApproachAction = true
                    }
                    1 -> {
                        arrivedRadius = it.radius
                        hasArriveAction = true
                    }
                    2 -> {
                        departRadius = it.radius
                        hasDepartAction = true
                    }
                }
            }
        }
        Address.name = stopDetail.name
        // Address is sent with empty(" ") value to make the address invisible in CPIK Hamburger(Trip -> Plan) -> MAPP-7533
        Address.address = " "
        if (stopDetail.hasNoArrivedAction()) stopDetail.getDefaultArrivedRadius()
            ?.let { arrivedRadius = it }
        if (stopDetail.hasNoDepartedAction())
            departRadius = DEFAULT_DEPART_RADIUS_IN_FEET
    }
}

fun ArrayList<StopInfo>.getStopDetailList(): ArrayList<StopDetail> {
    val stopDetailList = ArrayList<StopDetail>()
    this.forEach { stopInfo ->
        stopDetailList.add(stopInfo.getStopAndActions())
    }
    return stopDetailList
}

fun StopInfo.getStopAndActions() : StopDetail =
    StopDetail(dispid = this.dispid, stopid = this.stopId.toSafeInt(), latitude = this.latitude, longitude = this.longitude, description = this.description, name = this.name, completedTime = this.completedTime).also {
        it.etaTime = this.etaTime
        it.leg = this.leg
        it.EstimatedArrivalTime = this.EstimatedArrivalTime
        it.Address = this.Address
    }

fun Int.getWorkflowEventName() =
    when (this) {
        ActionTypes.APPROACHING.ordinal -> WorkFlowEvents.APPROACH_EVENT
        ActionTypes.ARRIVED.ordinal -> WorkFlowEvents.ARRIVE_EVENT
        else -> WorkFlowEvents.DEPART_EVENT
    }