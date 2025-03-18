package com.trimble.ttm.commons.usecase

import com.trimble.ttm.commons.logger.CHANGE_USER
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.repo.VehicleDriverMappingRepo

class VehicleDriverMappingUseCase(private val backboneUseCase: BackboneUseCase, private val vehicleDriverMappingRepository: VehicleDriverMappingRepo) {

    suspend fun updateVehicleDriverMapping() {
        // Fetch required data from BackboneUseCase
        val vehicleId = backboneUseCase.getVehicleId()
        val currentUser = backboneUseCase.getCurrentUser()
        val ttcAccountId = backboneUseCase.getTTCAccountId()
        val ttcIdForCurrentUser = backboneUseCase.getTTCIdForCurrentUser(currentUser)

        // Check if all required fields are valid
        if (areRequiredFieldsValid(vehicleId, currentUser, ttcAccountId, ttcIdForCurrentUser)) {
            Log.d(
                CHANGE_USER,
                "VehicleId: $vehicleId, CurrentUser: $currentUser, TTCAccountId: $ttcAccountId, TTCIdForCurrentUser: $ttcIdForCurrentUser"
            )
            // Execute the use case to update vehicle-driver mapping
            vehicleDriverMappingRepository.updateVehicleDriverMap(vehicleId, ttcAccountId, ttcIdForCurrentUser, currentUser)
        } else {
            Log.w(CHANGE_USER, "One or more required fields are empty or null, skipping update. VehicleId: $vehicleId, CurrentUser: $currentUser, TTCAccountId: $ttcAccountId, TTCIdForCurrentUser: $ttcIdForCurrentUser")
        }
    }

    private fun areRequiredFieldsValid(
        vehicleId: String,
        currentUser: String,
        ttcAccountId: String,
        ttcIdForCurrentUser: String
    ): Boolean {
        return vehicleId.isNotEmpty() && currentUser.isNotEmpty() && ttcAccountId.isNotEmpty() && ttcIdForCurrentUser.isNotEmpty()
    }
}