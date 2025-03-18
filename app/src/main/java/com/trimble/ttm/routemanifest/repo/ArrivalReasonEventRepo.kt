package com.trimble.ttm.routemanifest.repo

import com.trimble.ttm.routemanifest.model.ArrivalReason

interface ArrivalReasonEventRepo {

    suspend fun getArrivalReasonCollectionPath(stopId: Int) : String

    suspend fun getCurrentStopArrivalReason(documentPath: String ): ArrivalReason

    fun setArrivalReasonforStop(
        path: String,
        valueMap: HashMap<String, Any>
    )

    fun updateArrivalReasonforStop(
        path: String,
        valueMap: HashMap<String, Any>
    )

}