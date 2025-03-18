package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.parcelize.Parcelize

@Parcelize
data class DispatchFormPath(
    val stopName: String = EMPTY_STRING,
    val stopId: Int = -1,
    val actionId: Int = -1,
    val formId: Int = -1,
    val formClass: Int = -1,
    var dispatchName: String = EMPTY_STRING
) : Parcelable