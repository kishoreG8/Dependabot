package com.trimble.ttm.routemanifest.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.text.format.DateFormat
import android.util.Base64
import android.view.TouchDelegate
import android.view.View
import androidx.core.os.ConfigurationCompat
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import com.trimble.ttm.commons.logger.FeatureLogTags
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.CIRCULAR
import com.trimble.ttm.commons.utils.POLYGON
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.UTC_TIME_ZONE_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.KMS_TO_FEET
import com.trimble.ttm.formlibrary.utils.toSafeLong
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ROUTE_DATA_KEY
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.routemanifest.model.*
import com.trimble.ttm.routemanifest.repo.isAppLauncherWithMapsPerformanceFixInstalled
import com.trimble.ttm.routemanifest.utils.ext.getCompletedStopsBasedOnCompletedTime
import com.trimble.ttm.routemanifest.utils.ext.getPackageInfoList
import com.trimble.ttm.routemanifest.utils.ext.getStopInfoList
import com.trimble.ttm.routemanifest.utils.ext.getStopListFromGivenStop
import com.trimble.ttm.routemanifest.utils.ext.isSequentialTrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Utils {
    const val tag = "Utils"
    private const val appLauncherString = "applauncher"

    fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppLauncherVersion(packageManager: PackageManager) : Long{
        packageManager.getPackageInfoList().let { packageInfoList ->
            packageInfoList.forEach {
                if(it.packageName.contains(appLauncherString)){
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.longVersionCode
                    }else{
                        it.versionCode.toSafeLong()
                    }
                }
            }
        }
        return -1
    }

    fun isTablet(context: Context): Boolean =
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE

    fun getFormattedAddress(address: Address, isForTripPanel: Boolean = false): String {
        val addressBuilder = StringBuilder()
        if(address.name == ADDRESS_NOT_AVAILABLE ) return "N/A"
        if (address.address.isEmpty().not() && address.address != " " && isForTripPanel) {
            addressBuilder.append(address.address).append(", ")
        } else if (address.address.isEmpty().not() && !isForTripPanel) {
            addressBuilder.append(address.address).append("\n")
        }
        addressBuilder.append(address.city)
        addressBuilder.append(", ")
        addressBuilder.append(address.state)
        addressBuilder.append(SPACE)
        addressBuilder.append(address.zip)
        return addressBuilder.toString()
    }

    fun getDeviceLocale(context: Context): Locale? {
        ConfigurationCompat.getLocales(context.resources.configuration).let {
            return if (it.isEmpty) null else it.get(0)
        }
    }

    suspend fun getRouteData(stopId: Int, dataStoreManager: DataStoreManager): RouteData? {
        dataStoreManager.getValue(ROUTE_DATA_KEY, EMPTY_STRING).let { routeData ->
            if (routeData.isEmpty()) return null
            Log.i(FeatureLogTags.ROUTE_CALCULATION_RESULT.name, "Current value of RouteDataKey: $routeData")
            return fromJsonString<Map<Int, RouteData>?>(routeData,
                getGsonInstanceWithIsoDateFormatter())?.let { routeDataMap ->
                routeDataMap[stopId]
            }
        }
    }

    fun createRouteDataMapFromRouteCalculationResult(stopList: List<StopDetail>, routeCalculatedStopList: List<StopDetail>) : Map<Int, RouteData> {
        val routeDataMap = mutableMapOf<Int, RouteData>()
        stopList.filter { it.completedTime.isNotEmpty() }.onEach {
            val stopCompletedTimeCalendar = getCalendarFromDate(
                getSystemLocalDateFromUTCDateTime(it.completedTime)!!
            )
            it.EstimatedArrivalTime = stopCompletedTimeCalendar.clone() as Calendar
            it.etaTime = stopCompletedTimeCalendar.time
            routeDataMap[it.stopid] = RouteData(
                etaTime = getSystemLocalDateFromUTCDateTime(it.completedTime)
                    ?: Date(), address = it.Address, leg = it.leg
            )
        }
        if (routeCalculatedStopList.isNotEmpty()) {
            routeCalculatedStopList.forEach {
                routeDataMap[it.stopid] = RouteData(
                    etaTime = it.etaTime ?: Date(),
                    address = it.Address,
                    leg = it.leg
                )
            }
        }
        return routeDataMap
    }

    inline fun <reified T> fromJsonString(json: String, gson: Gson = Gson()): T? =
        try{
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        }catch (e : Exception){
            Log.e(tag,"error in converting string to object for the json $json ${e.stackTraceToString()}")
            null
        }

    fun toJsonString(src: Any, gson: Gson = Gson()): String? = gson.toJson(src)

    fun toPrettyJsonString(src: Any): String =
        GsonBuilder().setPrettyPrinting().create().toJson(src)

    /**
     * function to increase the touch sensitivity.
     */
    fun increaseTouchPortionOfView(view: View) {
        (view.parent as View).let {
            //the view you want to enlarge hit area
            it.post {
                val rect = Rect()
                view.getHitRect(rect)
                rect.top -= 100 // increase top hit area
                rect.left -= 100 // increase left hit area
                rect.bottom += 100 // increase bottom hit area
                rect.right += 100 // increase right hit area
                it.touchDelegate = TouchDelegate(rect, view)
            }
        }
    }

    fun getHoursFromMinutes(totalHours: Float): String {
        return when {
            totalHours <= 0 -> "0:00"
            totalHours < 6000 -> {
                val hour = TimeUnit.MINUTES.toHours(totalHours.toLong())
                String.format(
                    "%02d:%02d",
                    hour,
                    totalHours.toInt() - TimeUnit.HOURS.toMinutes(hour)
                )
            }
            else -> {//trip can greater than 4 days but, surely less than 41 days
                val hour = TimeUnit.MINUTES.toHours(totalHours.toLong())
                String.format(
                    "%03d:%02d",
                    hour,
                    totalHours.toInt() - TimeUnit.HOURS.toMinutes(hour)
                )
            }
        }
    }

    fun systemDateFormat(dateString: String): String {
        val dateFormat = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
        return if (dateString.isNotEmpty()) {
            DateFormat.getDateFormat(ApplicationContextProvider.getApplicationContext())
                .format(dateFormat.parse(dateString)!!)
        } else {
            ""
        }
    }

    fun getLocalDate(utcDateTimeString: String): Date? {
        if (utcDateTimeString.isNotEmpty()) {
            try {
                return SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }.parse(utcDateTimeString)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    fun getCalendarFromDate(date: Date): Calendar {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar
    }

    private fun getFormattedDateTime(date: Date, context: Context): String {
        return DateFormat.getDateFormat(
            context
        ).format(date) + SPACE + DateFormat.getTimeFormat(context).format(
            date
        )
    }

    fun getDiffInHrsAndMinsRemAsPair(startCal: Calendar, endCal: Calendar): Pair<Long, Long> {
        val diffInSeconds = (endCal.timeInMillis - startCal.timeInMillis) / 1000
        val diffInMinutes = diffInSeconds / 60
        val diffInHrs = diffInMinutes / 60
        val minsRem = diffInMinutes % 60

        return Pair(diffInHrs, minsRem)
    }

    fun decodeString(base64String: String): String {
        return String(Base64.decode(base64String, 0))
    }

    fun calendarWithDefaultDateTimeOffset(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 1970)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }

    fun calendarInUTC(): Calendar {
        TimeZone.setDefault(TimeZone.getDefault())
        return Calendar.getInstance(TimeZone.getDefault())
    }

    suspend fun getTripPanelIconUri(iconFileName: String): String = withContext(Dispatchers.IO) {
        "android.resource://${AppBuildConfig.getApplicationId()}/drawable/$iconFileName"
    }

    fun getSystemLocalDateTimeFromUTCDateTime(utcDateTime: String): String {
        return try {
            val isoDateFormatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            isoDateFormatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
            isoDateFormatter.parse(utcDateTime)?.let { utcDate ->
                getFormattedDateTime(utcDate, ApplicationContextProvider.getApplicationContext())
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getSystemLocalDateFromUTCDateTime(utcDateTime: String): Date? {
        return try {
            val isoDateFormatterUTC = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            isoDateFormatterUTC.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
            val utcDate = isoDateFormatterUTC.parse(utcDateTime)

            // Convert to local timezone date time string in ISO format
            val isoDateFormatterLocal = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            isoDateFormatterLocal.timeZone = TimeZone.getDefault()
            val localDateTimeString = isoDateFormatterLocal.format(utcDate!!)
            isoDateFormatterLocal.parse(localDateTimeString)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setCrashReportIdentifierAfterBackboneDataCache(
        appModuleCommunicator: AppModuleCommunicator
    ) {
        val cid = appModuleCommunicator.doGetCid()
        val vehicleNumber = appModuleCommunicator.doGetTruckNumber()
        val obcId = appModuleCommunicator.doGetObcId()
        if (cid.isNotEmpty() && vehicleNumber.isNotEmpty() && obcId.isNotEmpty())
            setCrashReportIdentifier(cid, vehicleNumber, obcId)
    }

    private fun setCrashReportIdentifier(vararg ids: String) {
        try {
            val id = getUniqueIdentifier(*ids)
            if (id.isNotEmpty())
                FirebaseCrashlytics.getInstance().setUserId(id)
            else
                Log.e("Utils", "Crashlytics identifier is empty")
        } catch (e: Exception) {
            Log.e("Utils", e.message, e)
        }
    }

    fun getUniqueIdentifier(vararg ids: String): String {
        if (ids.isEmpty()) return ""
        val idBuilder = StringBuilder()
        ids.forEach { id ->
            idBuilder.append(id).append("-")
        }
        return if (idBuilder.isNotEmpty()) idBuilder.deleteCharAt(idBuilder.length - 1)
            .toString() else ""
    }

    fun Int?.toSafeInt(): Int = this ?: 0

    //If we specify the date time format, then that is used to convert Date object to string and vice versa,
    // otherwise, it takes the device date time format and may run into issue for persisted values if we change the device date time format.
    fun getGsonInstanceWithIsoDateFormatter() : Gson{
        return GsonBuilder().setDateFormat(ISO_DATE_TIME_FORMAT).create()
    }

    fun getIntentDataErrorString(
        context: Context,
        dataName:String,
        dataType:String,
        nullOrEmpty:String,
        actionName:String?
    ):String{
        return context.getString(
            R.string.intent_received_data_error,
            dataName,
            dataType,
            nullOrEmpty,
            actionName
        )
    }

    fun getStopList(stopDetailList: List<StopDetail>, isPolygonalOptOut: Boolean) : List<Any>{
        return stopDetailList.getStopInfoList(isPolygonalOptOut = isPolygonalOptOut)
    }

    fun getSequentialStopList(stopDetailList: List<StopDetail>, currentStopId : Int) : List<StopDetail>{
        if(stopDetailList.isSequentialTrip() && currentStopId != -1){
            stopDetailList.getStopListFromGivenStop(currentStopId).let { stopListAfterGivenStop ->
                if(stopListAfterGivenStop.getCompletedStopsBasedOnCompletedTime().isEmpty()){
                    Log.d(tag,"-----Inside getSequentialStopList - returning next set of sequential stops")
                    return stopListAfterGivenStop
                }else{
                    Log.d(tag,"-----Inside getSequentialStopList - returning current stop")
                    return stopDetailList.filter { it.stopid == currentStopId }
                }
            }
        }else{
            return stopDetailList
        }

    }

    fun getEventTypeKeyForRouteAndGeofence() = ONLY_ROUTE_AND_GEOFENCE

    fun getEventTypeKeyForGeofence() = ADD_GEOFENCE

    fun getEventTypeKeyForRoute() = ONLY_ROUTE

    fun getEventTypeKeyForRouteCalculation() = CPIK_ROUTE_COMPUTATION_RESULT

    fun getKeyForRouteCalculationResponseToClient() = ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY

    suspend fun getAppLauncherVersionAndSaveInMemory(dataStoreManager: DataStoreManager) {
        if (dataStoreManager.containsKey(IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED)) {
            isAppLauncherWithMapsPerformanceFixInstalled =
                dataStoreManager.getValue(IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED, FALSE)
        }
    }

    fun pollElementFromPriorityBlockingQueue(priorityBlockingQueue: PriorityBlockingQueue<LauncherMessageWithPriority>): String {
        return if (priorityBlockingQueue.isNotEmpty()) priorityBlockingQueue.poll()?.message ?: EMPTY_STRING else EMPTY_STRING
    }

    //Use this method where incoming stop details are not available in the method.
    // This method will not give correct result when invoked in background as selected dispatchId might be different in case of geofence events processing.
    fun isActiveDispatchIdSameAsSelectedDispatchId(activeDispatchId : String, selectedDispatchId : String) = activeDispatchId == selectedDispatchId

    //Use this method where incoming stop details are available in the method. This method will give correct details when invoked from background as well.
    fun isIncomingDispatchSameAsActiveDispatch(incomingDispatchId : String, activeDispatchId: String) : Boolean {
        if(activeDispatchId.isNotEmpty() && incomingDispatchId != activeDispatchId){
            Log.d(tag,"putStopsIntoPreferenceForServiceReference - tripId is not same as current tripId. So, ignoring the operation.")
            return false
        }
        return true
    }


    /**
     * This method is used to get the Fourteen days before date in Timestamp
     */
    fun getTheFourteenDaysBeforeDate(): Date =
        Date(System.currentTimeMillis() - FOURTEEN_DAYS_IN_MILLISECONDS)

    fun Long.ensureNonNegative(): Long {
        return if (this < 0) 0 else this
    }

    fun getDistanceBetweenLatLongs(
        location1: Pair<Double, Double>,
        location2: Pair<Double, Double>
    ): Double {
        // Radius of the earth in km
        val earthRadius = 6371
        val distLatitude =
            Math.toRadians(location2.first - location1.first)
        val distLongitude = Math.toRadians(location2.second - location1.second)
        val a =
            sin(distLatitude / 2) * sin(distLatitude / 2) +
                    cos(Math.toRadians(location1.first)) * cos(Math.toRadians(location2.first)) *
                    sin(distLongitude / 2) * sin(distLongitude / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val resultInKM = earthRadius * c
        /* Locale.US is used to ensure that the decimal product has dot as decimal separator */
        return "%.2f".format(Locale.US, resultInKM).toDouble()
    }

    fun Double.toFeet(): Double {
        /* Locale.US is used to ensure that the decimal product has dot as decimal separator */
        return "%.2f".format(Locale.US, this * KMS_TO_FEET).toDouble()
    }

    fun SiteCoordinate.toLatLng(): LatLng {
        return LatLng(this.latitude, this.longitude)
    }

    fun containsLocation(
        location : Pair<Double, Double>,
        stopLocation : Pair<Double, Double>,
        siteCoordinates: List<SiteCoordinate>,
        geodesic: Boolean = false, radius: Int = 0,
        geofenceType: String
    ): Boolean {
        val size = siteCoordinates.size
        if (size == 0) {
            return false
        } else if (geofenceType == CIRCULAR) {
            return if (getDistanceBetweenLatLongs(location, stopLocation).toFeet() < radius) true else false
        } else if (geofenceType == POLYGON){
            return PolyUtil.containsLocation(
                location.first,
                location.second,
                siteCoordinates.map { it.toLatLng() },
                geodesic
            )
        }
        return false
    }

    fun getStopDetailHashMap(stopLocation: SiteCoordinate, isSeq: Int, eta : String = EMPTY_STRING, geofenceType: String, driverID: String): HashMap<String, Any>{
        return HashMap<String, Any>().also{
            it[STOP_LOCATION] = stopLocation
            it[SEQUENCED] = isSeq
            it[ETA] = eta
            it[GEOFENCE_TYPE] = geofenceType
            it[DRIVERID] = driverID
        }
    }

    fun getGeofenceType(siteCoordinates: MutableList<SiteCoordinate>, isPolygonalOptOut: Boolean): String {
        return if (isPolygonalOptOut || siteCoordinates.size <= 1) CIRCULAR else POLYGON
    }

    fun getTripMetrics(stops: List<StopDetail>, isPolygonalOptOut: Boolean) : TripMetrics {
        val tripEndMetrics = TripMetrics()
        tripEndMetrics.completedStopsWithCompletedTimeMissing = stops.filter { stopDetail -> !stopDetail.isArrived() && stopDetail.getArrivedAction()?.responseSent ?: false }
        tripEndMetrics.stopsSortedOnArrivalTriggerReceivedTime = stops.getSortedStopsBasedOnArrivalTriggerReceivedTime()
        tripEndMetrics.isSeqTrip = stops.isSequentialTrip()
        isSeqTripOutOfTrailSequence(tripEndMetrics, stops)
        stops.forEach{
            if(it.deleted == 1) tripEndMetrics.noOfStopsDeleted++
            tripEndMetrics.stopsOverview += "StopId - ${it.stopid}, isSequence ${it.sequenced}, ArrivalTriggerReceivedTime ${it.getArrivalTriggerReceivedTime()}, ArrivalTime ${it.completedTime}, DepartTriggerReceivedTime ${it.getDepartTriggerReceivedTime()}, DepartedTime ${it.departedTime}, ApproachRadius ${it.getApproachRadius()}, ArrivedRadius ${it.getArrivedRadius()}, DepartRadius ${it.getDepartRadius()}"
            val arrivedAction = it.getArrivedAction()
            if (getGeofenceType(it.siteCoordinates ?: mutableListOf(), isPolygonalOptOut) == CIRCULAR) {
                tripEndMetrics.noOfStopsBasedOnGeofenceType.circular++
                if (arrivedAction?.triggerReceived == true) tripEndMetrics.noOfStopsTriggerReceived.circular++
                if (arrivedAction?.responseSent == true) tripEndMetrics.noOfArrivalResponseSent.circular++
            } else {
                tripEndMetrics.noOfStopsBasedOnGeofenceType.polygonal++
                if (arrivedAction?.triggerReceived == true) tripEndMetrics.noOfStopsTriggerReceived.polygonal++
                if (arrivedAction?.responseSent == true) tripEndMetrics.noOfArrivalResponseSent.polygonal++
            }
        }
        return tripEndMetrics
    }

    private fun isSeqTripOutOfTrailSequence(tripMetrics: TripMetrics, stops: List<StopDetail>) {
        val arrivalTriggeredOrderStops = tripMetrics.stopsSortedOnArrivalTriggerReceivedTime.filterNot { it.isStopSoftDeleted() }
        val actualStops = stops.filterNot { it.isStopSoftDeleted() }
        if (tripMetrics.isSeqTrip && arrivalTriggeredOrderStops.size == stops.size) {
            actualStops.forEachIndexed { index, stopDetail ->
                if (arrivalTriggeredOrderStops[index].stopid != stopDetail.stopid) {
                    tripMetrics.isSeqTripOutOfTrailSeq = true
                    return@forEachIndexed
                }
            }
        }
    }

    fun isMessageFromDidYouArrive(messagePriority: Int) =
        (messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY || messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP)

    // The three functions getArrivedRadius(), getApproachRadius() and getDepartRadius() are only used in getTripMetrics(), DO NOT Use it anywhere, unless needed for logging
    private fun StopDetail.getArrivedRadius() : Int =
        this.Actions.find { it.actionType == ActionTypes.ARRIVED.ordinal }?.radius ?: DEFAULT_RADIUS_FOR_ACTION_NOT_FOUND_IN_TRIP_XML

    private fun StopDetail.getApproachRadius() : Int =
        this.Actions.find { it.actionType == ActionTypes.APPROACHING.ordinal }?.radius ?: DEFAULT_RADIUS_FOR_ACTION_NOT_FOUND_IN_TRIP_XML

    private fun StopDetail.getDepartRadius() : Int =
        this.Actions.find { it.actionType == ActionTypes.DEPARTED.ordinal }?.radius ?: DEFAULT_RADIUS_FOR_ACTION_NOT_FOUND_IN_TRIP_XML
}