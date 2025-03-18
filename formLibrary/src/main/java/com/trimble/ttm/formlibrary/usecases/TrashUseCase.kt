package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.formlibrary.repo.TrashRepo

class TrashUseCase(
    private val trashRepo: TrashRepo,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
) {
    fun getMessageListFlow() = trashRepo.getMessageListFlow()

    suspend fun getMessageOfVehicle(
        customerId: String,
        vehicleId: String,
        isFirstTimeFetch: Boolean
    ) =
        trashRepo.getMessages(customerId, vehicleId, isFirstTimeFetch)

    fun didLastMessageReached() = trashRepo.didLastItemReached()

    fun resetPagination() = trashRepo.resetPagination()

    fun clearRegistration() = trashRepo.detachListenerRegistration()

    fun logScreenViewEvent(screenName: String) =
        firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(screenName)

    suspend fun deleteAllMessages(
        customerId: String,
        vehicleId: String,
        token: String?,
        appCheckToken: String
    ) =
        trashRepo.deleteAllMessages(customerId, vehicleId, token, appCheckToken)

    suspend fun deleteMessage(customerId: String, vehicleId: String, asn: String, caller: String) {
        trashRepo.deleteMessage(customerId, vehicleId, asn, caller)
    }
}