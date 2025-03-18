package com.trimble.ttm.commons.repo

interface DispatcherFormValuesRepo {
    suspend fun getDispatcherFormValues(
        customerId: String,
        vehicleId: String,
        dispatchId: String,
        stopId: String, actionId: String,
    ): HashMap<String, ArrayList<String>>

}