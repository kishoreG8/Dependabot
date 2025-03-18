package com.trimble.ttm.commons.usecase

import com.trimble.ttm.backbone.api.data.WorkflowCurrentTrip
import com.trimble.ttm.backbone.api.data.user.UserLogInStatus
import com.trimble.ttm.commons.repo.BackboneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class BackboneUseCase(private val backboneRepository: BackboneRepository) {

    suspend fun getCustomerId(): String? =
        backboneRepository.getCustomerId()

    suspend fun getVehicleId(): String =
        backboneRepository.getVehicleId()

    suspend fun getOBCId(): String? =
        backboneRepository.getOBCId()

    fun monitorCustomerId(): Flow<String> = backboneRepository.monitorCustomerId()

    fun monitorVehicleId(): Flow<String> = backboneRepository.monitorVehicleId()

    fun monitorOBCId(): Flow<String> = backboneRepository.monitorOBCId()

    fun monitorTrailersData(): Flow<List<String>> = backboneRepository.monitorTrailersData()

    fun monitorShipmentsData(): Flow<List<String>> = backboneRepository.monitorShipmentsData()

    fun monitorMotion(): StateFlow<Boolean?> = backboneRepository.monitorMotion()

    suspend fun fetchEngineMotion(caller: String): Boolean? =
        backboneRepository.fetchEngineMotion(caller)

    suspend fun setWorkflowStartAction(dispatchId: Int) =
        backboneRepository.setWorkflowStartAction(dispatchId)

    suspend fun setWorkflowEndAction(dispatchId: Int) =
        backboneRepository.setWorkflowEndAction(dispatchId)

    suspend fun getCurrentWorkFlowId(): WorkflowCurrentTrip =
        backboneRepository.getCurrentWorkFlowId()

    suspend fun getCurrentUser(): String = backboneRepository.getCurrentUser()

    suspend fun getLoggedInUsersStatus(): List<UserLogInStatus> =
        backboneRepository.getLoggedInUsersStatus()

    suspend fun getCurrentLocation(): Pair<Double, Double> = backboneRepository.getCurrentLocation()

    suspend fun getFuelLevel(): Int = backboneRepository.getFuelLevel()

    suspend fun getOdometerReading(shouldFetchConfigOdometer: Boolean): Double =
        backboneRepository.getOdometerReading(shouldFetchConfigOdometer)

    suspend fun getTTCAccountId(): String = backboneRepository.getTTCAccountId()

    suspend fun getTTCIdForCurrentUser(currentUser: String): String = backboneRepository.getTTCIdForCurrentUser(currentUser)

}