package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionMetaData
import com.trimble.ttm.formlibrary.repo.InspectionExposeRepo
import com.trimble.ttm.formlibrary.repo.LocalRepo
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay

class UpdateInspectionInformationUseCase(
    private val localRepo: LocalRepo,
    private val inspectionExposeRepo: InspectionExposeRepo,
    private val appModuleCommunicator: AppModuleCommunicator
) {

    private val tag = "UpdateInspectionInformationUC"

    suspend fun updatePreTripInspectionRequire(isRequired: Boolean) {
        localRepo.updatePreTripInspectionRequire(isRequired)
        updateInspectionRequired(isRequired, true)
    }

    suspend fun updatePostTripInspectionRequire(isRequired: Boolean) {
        localRepo.updatePostTripInspectionRequire(isRequired)
        updateInspectionRequired(isRequired, false)
    }

    suspend fun updateInspectionRequire(isRequired: Boolean) {
        localRepo.updateInspectionRequire(isRequired)
        updateInspectionRequired(isRequired, true)
        updateInspectionRequired(isRequired, false)
    }

    suspend fun updatePreviousPreTripAnnotation(annotation: String) {
        localRepo.updatePreviousPreTripAnnotation(annotation)
        updatePreviousAnnotationData(annotation, true)
    }

    suspend fun updatePreviousPostTripAnnotation(annotation: String) {
        localRepo.updatePreviousPostTripAnnotation(annotation)
        updatePreviousAnnotationData(annotation, false)
    }

    suspend fun isPreTripInspectionRequired(): Boolean =
        localRepo.isPreTripInspectionRequired()

    suspend fun isPostTripInspectionRequired(): Boolean =
        localRepo.isPostTripInspectionRequired()

    suspend fun getPreviousPreTripAnnotation(): String =
        localRepo.getPreviousPreTripAnnotation()

    suspend fun getPreviousPostTripAnnotation(): String =
        localRepo.getPreviousPostTripAnnotation()

    suspend fun clearPreviousAnnotations() {
        localRepo.clearPreviousAnnotations()
        updatePreviousAnnotationData(EMPTY_STRING, true)
        updatePreviousAnnotationData(EMPTY_STRING, false)
    }

    suspend fun setLastSignedInDriversCount(driverCount: Int) =
        localRepo.setLastSignedInDriversCount(driverCount)

    suspend fun getLastSignedInDriversCount(): Int =
        localRepo.getLastSignedInDriversCount()

    private suspend fun updateInspectionRequired(isRequired: Boolean, isForPreTrip: Boolean) {
        with(inspectionExposeRepo) {
            getLatestData().also { latestData ->
                if (latestData.id == 0) {
                    if (isForPreTrip) insert(MandatoryInspectionMetaData(isPreTripInspectionRequired = isRequired))
                    else insert(MandatoryInspectionMetaData(isPostTripInspectionRequired = isRequired))
                } else {
                    if (isForPreTrip) latestData.isPreTripInspectionRequired = isRequired
                    else latestData.isPostTripInspectionRequired = isRequired
                    delete()
                    insert(latestData)
                }
            }
        }
    }

    private fun updatePreviousAnnotationData(annotation: String, isForPreTrip: Boolean) {
        appModuleCommunicator.getAppModuleApplicationScope().safeLaunch(CoroutineName("$tag Update previous annotaion")) {
            delay(500)
            with(inspectionExposeRepo) {
                getLatestData().also { latestData ->
                    if (latestData.id == 0) {
                        if (isForPreTrip) insert(MandatoryInspectionMetaData(previousPreTripAnnotation = annotation))
                        else insert(MandatoryInspectionMetaData(previousPostTripAnnotation = annotation))
                    } else {
                        if (isForPreTrip) latestData.previousPreTripAnnotation = annotation
                        else latestData.previousPostTripAnnotation = annotation
                        delete()
                        insert(latestData)
                    }
                }
            }
        }
    }

}