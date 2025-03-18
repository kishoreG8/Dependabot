package com.trimble.ttm.commons.repo

import com.trimble.ttm.backbone.api.Backbone
import com.trimble.ttm.backbone.api.MultipleEntryQuery
import com.trimble.ttm.backbone.api.data.WorkflowCurrentTrip
import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.backbone.api.data.user.UserLogInStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BackboneRepository {
    suspend fun getCustomerId(): String?
    suspend fun getVehicleId(): String
    suspend fun getOBCId(): String? // or DSN
    fun getMultipleData(
        retriever: Backbone.Retriever<*>,
        vararg retrievers: Backbone.Retriever<*>
    ): MultipleEntryQuery.Result

    suspend fun getUserEldStatus(): Map<String, UserEldStatus>?
    suspend fun getLoggedInUsersStatus():List<UserLogInStatus>
    suspend fun getCurrentUser(): String
    fun monitorTrailersData(): Flow<List<String>>
    fun monitorShipmentsData(): Flow<List<String>>
    fun monitorCustomerId(): Flow<String>
    fun monitorVehicleId(): Flow<String>
    fun monitorMotion(): StateFlow<Boolean?>
    fun monitorOBCId():Flow<String>
    suspend fun fetchEngineMotion(caller: String): Boolean?
    fun getDrivers(): Set<String>
    suspend fun setWorkflowStartAction(dispatchId: Int)
    suspend fun setWorkflowEndAction(dispatchId: Int)
    suspend fun getCurrentWorkFlowId(): WorkflowCurrentTrip
    suspend fun getCurrentLocation():Pair<Double,Double>
    suspend fun getFuelLevel(): Int
    suspend fun getOdometerReading(shouldFetchConfigOdometer: Boolean): Double
    suspend fun getTTCAccountId(): String
    suspend fun getTTCIdForCurrentUser(currentUser: String): String

}