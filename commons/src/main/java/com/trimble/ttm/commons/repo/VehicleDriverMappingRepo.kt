package com.trimble.ttm.commons.repo


interface VehicleDriverMappingRepo {
    suspend fun updateVehicleDriverMap(vehicleId: String, ttcAccountId : String, ttcIdForCurrentUser: String, currentUser: String)
}