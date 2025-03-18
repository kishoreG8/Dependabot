package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_POST_TRIP_INSPECTION_REQUIRED
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_PRE_TRIP_INSPECTION_REQUIRED
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.LAST_SIGNED_IN_DRIVERS_COUNT
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.PREVIOUS_POST_TRIP_INSPECTION_ANNOTATION
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.PREVIOUS_PRE_TRIP_INSPECTION_ANNOTATION
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO

class LocalDataSourceImpl(private val dataStoreManager: FormDataStoreManager): LocalDataSource {

    override suspend fun updatePreTripInspectionRequire(isRequired: Boolean) =
        dataStoreManager.setValue(IS_PRE_TRIP_INSPECTION_REQUIRED, isRequired)

    override suspend fun updatePostTripInspectionRequire(isRequired: Boolean) =
        dataStoreManager.setValue(IS_POST_TRIP_INSPECTION_REQUIRED, isRequired)

    override suspend fun updateInspectionRequire(isRequired: Boolean) {
        updatePreTripInspectionRequire(isRequired)
        updatePostTripInspectionRequire(isRequired)
    }

    override suspend fun updatePreviousPreTripAnnotation(annotation: String) =
        dataStoreManager.setValue(PREVIOUS_PRE_TRIP_INSPECTION_ANNOTATION, annotation)

    override suspend fun updatePreviousPostTripAnnotation(annotation: String) =
        dataStoreManager.setValue(PREVIOUS_POST_TRIP_INSPECTION_ANNOTATION, annotation)

    override suspend fun isPreTripInspectionRequired(): Boolean =
        dataStoreManager.getValue(IS_PRE_TRIP_INSPECTION_REQUIRED, true)

    override suspend fun isPostTripInspectionRequired(): Boolean =
        dataStoreManager.getValue(IS_POST_TRIP_INSPECTION_REQUIRED, true)

    override suspend fun getPreviousPreTripAnnotation(): String =
        dataStoreManager.getValue(PREVIOUS_PRE_TRIP_INSPECTION_ANNOTATION, EMPTY_STRING)

    override suspend fun getPreviousPostTripAnnotation(): String =
        dataStoreManager.getValue(PREVIOUS_POST_TRIP_INSPECTION_ANNOTATION, EMPTY_STRING)

    override suspend fun clearPreviousAnnotations() {
        dataStoreManager.setValue(PREVIOUS_PRE_TRIP_INSPECTION_ANNOTATION, EMPTY_STRING)
        dataStoreManager.setValue(PREVIOUS_POST_TRIP_INSPECTION_ANNOTATION, EMPTY_STRING)
    }

    override suspend fun setLastSignedInDriversCount(driverCount: Int) =
        dataStoreManager.setValue(LAST_SIGNED_IN_DRIVERS_COUNT, driverCount)

    override suspend fun getLastSignedInDriversCount() =
        dataStoreManager.getValue(LAST_SIGNED_IN_DRIVERS_COUNT, ZERO)

}