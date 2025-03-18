package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.backbone.api.data.eld.CurrentUser
import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo

class EDVIRInspectionsUseCase(
    private val eDVIRInspectionsRepo: EDVIRInspectionsRepo
) {

    suspend fun getEDVIREnabledSetting(customerId: String, obcId: String) =
        eDVIRInspectionsRepo.getEDVIREnabledSetting(customerId, obcId)

    suspend fun listenToInspectionHistory(
        customerId: String,
        dsn: String,
        thresholdTimeInMillis: Long
    ) = eDVIRInspectionsRepo.listenToInspectionHistory(customerId, dsn, thresholdTimeInMillis)

    fun getInspectionHistoryAsFlow() = eDVIRInspectionsRepo.getInspectionHistoryAsFlow()

    fun getCurrentUser(appModuleCommunicator: AppModuleCommunicator): UserName {
        var userName = UserName("", "", "", "", "")
        val result = appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()
        result[CurrentUser]?.data?.let { currentUser ->
            if (currentUser.isNotEmpty()) {
                result[UserName]?.data?.let { userMap ->
                    if (userMap.isNotEmpty()) {
                        userMap[currentUser]?.let { name ->
                            userName = name
                        }
                    }
                }
            }
        }
        return userName
    }

    suspend fun getUserEldStatus(appModuleCommunicator: AppModuleCommunicator): Map<String, UserEldStatus>? =
        appModuleCommunicator.getUserEldStatus()

    fun canUserPerformManualInspection() = true
}