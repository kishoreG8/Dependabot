package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.repo.SentRepo
import com.trimble.ttm.formlibrary.utils.FLAVOR_DEV
import com.trimble.ttm.formlibrary.utils.FLAVOR_PROD
import com.trimble.ttm.formlibrary.utils.FLAVOR_QA
import com.trimble.ttm.formlibrary.utils.isNotNull

class SentUseCase(
    private val sentRepo: SentRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
) {

    fun getMessageListFlow() = sentRepo.getMessageListFlow()

    suspend fun getMessageOfVehicle(customerId: String, vehicleId: String,isFirstTimeFetch:Boolean) =
        sentRepo.getMessages(customerId, vehicleId,isFirstTimeFetch)

    suspend fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long) =
        sentRepo.deleteMessage(customerId, vehicleId, createdTime)

    suspend fun deleteAllMessage(customerId: String, vehicleId: String,token:String?,appCheckToken : String): CollectionDeleteResponse {
        return if (token!=null && appCheckToken.isNotEmpty() && appModuleCommunicator.getAppFlavor().isNotNull()) {
            return sentRepo.deleteAllMessage(customerId, vehicleId,when (appModuleCommunicator.getAppFlavor()) {
                FLAVOR_DEV -> BuildEnvironment.Dev
                FLAVOR_QA -> BuildEnvironment.Qa
                FLAVOR_PROD -> BuildEnvironment.Prod
                else -> BuildEnvironment.Stg
            },token, appCheckToken)
        } else CollectionDeleteResponse(false, "Cid $customerId Vehicle $vehicleId either token or app flavor from app module communicator is null")
    }

    fun didLastMessageReached() = sentRepo.didLastItemReached()

    fun resetPagination() = sentRepo.resetPagination()

    fun clearRegistration()=sentRepo.detachListenerRegistration()

    fun logScreenViewEvent(screenName: String) =
        firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(screenName)

    fun getAppModuleCommunicator() = sentRepo.getAppModuleCommunicator()

}