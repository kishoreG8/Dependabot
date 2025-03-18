package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.repo.DraftRepo
import com.trimble.ttm.formlibrary.utils.FLAVOR_DEV
import com.trimble.ttm.formlibrary.utils.FLAVOR_PROD
import com.trimble.ttm.formlibrary.utils.FLAVOR_QA
import com.trimble.ttm.formlibrary.utils.isNotNull

class DraftUseCase(
    private val draftRepo: DraftRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val featureFlagGateKeeper: FeatureGatekeeper,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
)  {
    fun getMessageListFlow() = draftRepo.getMessageListFlow()

    suspend fun getMessageOfVehicle(customerId: String, vehicleId: String, isFirstTimeFetch: Boolean) =
        draftRepo.getMessages(customerId, vehicleId, isFirstTimeFetch)

    suspend fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long) =
        draftRepo.deleteMessage(customerId, vehicleId, createdTime)

    suspend fun deleteAllMessage(customerId: String, vehicleId: String, token: String?, appCheckToken : String): CollectionDeleteResponse {
        return if (token != null && appCheckToken.isNotEmpty() && appModuleCommunicator.getAppFlavor().isNotNull()) {
            return draftRepo.deleteAllMessage(
                customerId,
                vehicleId,
                when (appModuleCommunicator.getAppFlavor()) {
                    FLAVOR_DEV -> BuildEnvironment.Dev
                    FLAVOR_QA -> BuildEnvironment.Qa
                    FLAVOR_PROD -> BuildEnvironment.Prod
                    else -> BuildEnvironment.Stg
                },
                token, appCheckToken
            )
        } else {
            CollectionDeleteResponse(false, "Cid $customerId Vehicle $vehicleId either token or app flavor from app module communicator is null")
        }
    }

    fun didLastMessageReached() = draftRepo.didLastItemReached()

    fun clearRegistration() = draftRepo.detachListenerRegistration()

    fun resetPagination() = draftRepo.resetPagination()

    fun shouldSendDraftDataBackToDispatchStopForm(messageFormResponse: MessageFormResponse): Boolean =
        messageFormResponse.uncompletedDispatchFormPath.stopId >= 0

    suspend fun isComposeFormFeatureFlagEnabled(): Boolean {
        val cid = appModuleCommunicator.doGetCid()
        val flags = appModuleCommunicator.getFeatureFlags()
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        return featureFlagGateKeeper.isFeatureTurnedOn(flagName, flags, cid)
    }

    suspend fun isDraftSaved(path: String, actionId: String): Boolean {
        return draftRepo.isDraftSaved(path, actionId)
    }

    suspend fun deleteDraftMsgOfDispatchFormSavePath(dispatchFormSavePath: String, customerId: String, vehicleId: String) =
        draftRepo.deleteDraftMsgOfDispatchFormSavePath(dispatchFormSavePath, customerId, vehicleId)

    fun logScreenViewEvent(screenName: String) =
        firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(screenName)

}
