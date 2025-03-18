package com.trimble.ttm.commons.usecase

import com.trimble.ttm.commons.repo.DispatcherFormValuesRepo

class DispatcherFormValuesUseCase( private val dispatcherFormValuesRepo: DispatcherFormValuesRepo) {

    suspend fun getDispatcherFormValues(customerId :String,
    vehicleId: String,
    dispatchId: String,
    stopId: String,
    actionId: String) : HashMap<String, ArrayList<String>> =
        dispatcherFormValuesRepo.getDispatcherFormValues(customerId,
            vehicleId,
            dispatchId,
            stopId,
            actionId)
}