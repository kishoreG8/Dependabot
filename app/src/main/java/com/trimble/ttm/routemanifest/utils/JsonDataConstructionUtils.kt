package com.trimble.ttm.routemanifest.utils

import com.google.gson.JsonObject
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Locale

const val actionId = "actionId"
const val cid = "cid"
const val createdDate = "createdDate"
const val dispId = "dispId"
const val dsn = "dsn"
const val fuel = "fuel"
const val lat = "lat"
const val lon = "lon"
const val mileType = "mileType"
const val negGuf = "negGuf"
const val odom = "odom"
const val odomType = "odomType"
const val quality = "quality"
const val reason = "reason"
const val stopId = "stopId"
const val vid = "vid"

class JsonDataConstructionUtils {
    companion object {

        suspend fun getTripEventJson(
            dispatchId: String,
            fuelLevel : Int,
            odometerReading : Double,
            pfmEventsInfo: PFMEventsInfo.TripEvents,
            currentLocationLatLong : Pair<Double, Double>,
            customerIdObcIdVehicleId: Triple<Int, String, String>
        ): JsonData {
            Mutex().withLock {
                return JsonData(
                    "data",
                    JsonObject().apply {
                        addProperty(cid, customerIdObcIdVehicleId.first)
                        addProperty(
                            createdDate,
                            getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
                        )
                        addProperty(dispId, dispatchId.toInt())
                        addProperty(dsn, customerIdObcIdVehicleId.second)
                        addProperty(fuel, fuelLevel)
                        addProperty(lat, currentLocationLatLong.first)
                        addProperty(lon, currentLocationLatLong.second)
                        addProperty(negGuf, pfmEventsInfo.negativeGuf)
                        addProperty(
                            odom,
                            convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerReading)
                        )
                        addProperty(odomType, "j1708")
                        addProperty(quality, "good")
                        addProperty(reason, pfmEventsInfo.reasonType.lowercase(Locale.ROOT))
                        addProperty(vid, customerIdObcIdVehicleId.third)
                    }.toString()
                )
            }
        }

        suspend fun getStopActionJson(
            action: Action,
            createDate: String,
            fuelLevel : Int,
            odometerReading : Double,
            pfmEventsInfo: PFMEventsInfo.StopActionEvents,
            currentLocationLatLong : Pair<Double, Double>,
            customerIdObcIdVehicleId: Triple<Int, String, String>
        ): JsonData {
            Mutex().withLock {
                return JsonData(
                    "data",
                    JsonObject().apply {
                        addProperty(actionId, action.actionid)
                        addProperty(cid, customerIdObcIdVehicleId.first)
                        addProperty(
                            createdDate,
                            createDate
                        )
                        addProperty(dispId, action.dispid.toInt())
                        addProperty(dsn, customerIdObcIdVehicleId.second)
                        addProperty(fuel, fuelLevel)
                        addProperty(lat, currentLocationLatLong.first)
                        addProperty(lon, currentLocationLatLong.second)
                        addProperty(mileType, pfmEventsInfo.mileType)
                        addProperty(negGuf, pfmEventsInfo.negativeGuf)
                        addProperty(
                            odom,
                            convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerReading)
                        )
                        addProperty(odomType, "j1708")
                        addProperty(quality, "good")
                        addProperty(reason, pfmEventsInfo.reasonType.lowercase(Locale.ROOT))
                        addProperty(stopId, action.stopid)
                        addProperty(vid, customerIdObcIdVehicleId.third)
                    }.toString()
                )
            }
        }
    }
}