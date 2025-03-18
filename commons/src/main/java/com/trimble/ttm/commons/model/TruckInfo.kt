package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.parcelize.Parcelize

@Parcelize
data class TruckInfo(val truckNumber: String = EMPTY_STRING, val truckNumberUpdated: Timestamp = Timestamp.now()) :
    Parcelable
