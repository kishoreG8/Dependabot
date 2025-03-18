package com.trimble.ttm.routemanifest.model


import android.os.Parcelable
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.parcelize.Parcelize

/**
 * Firebase's Push notification data payload.
 * The data will be used to cache the trip/dispatch related data based on the data's dispatch ID.
 *
 */
@Parcelize
data class FcmData(
    val cid: String = EMPTY_STRING,
    val vid: String = EMPTY_STRING, //truck number
    val asn: Int = 0, //Message serial number
    val dispatchId: String = EMPTY_STRING,
    val dispatchName: String = EMPTY_STRING,
    val startDateTime: String = EMPTY_STRING,
    val stopId: Int = -1,
    val stopName: String = EMPTY_STRING,
    val isStopAdded: Boolean = false,
    val isStopDeleted: Boolean = false,
    val dispatchCreateTime: String = EMPTY_STRING,
    val dispatchReadyTime: String = EMPTY_STRING, //dispatch IsReady write time
    val isDispatchDeleted : Boolean = false,
    val dispatchDeletedTime : String = EMPTY_STRING
) : Parcelable {
    val isStopRemovalNotification: Boolean  //to support old apps which are not consuming isStopDeleted, isStopAdded flag
        get() {
            return stopId != -1
        }
}

/**
 * State of the FCM data.This will be helpful to identify the type of data received from FCM.
 * We needed definite state we expect out of fcm data but fcmData class contains many fields and they are not definitely says which kind of fcm it is
 */
sealed class FcmDataState {
    object NewTrip : FcmDataState()
    object NewMessage : FcmDataState()
    object IsStopAdded : FcmDataState()
    object IsStopDeleted : FcmDataState()
    object Ignore : FcmDataState() // Do not remove this Ignore state to ensure backward compatibility when sending new FCM
}