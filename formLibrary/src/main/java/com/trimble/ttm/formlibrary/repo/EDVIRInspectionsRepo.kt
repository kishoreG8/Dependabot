package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import kotlinx.coroutines.flow.Flow

interface EDVIRInspectionsRepo {
    suspend fun getEDVIREnabledSetting(customerId: String, dsn: String): EDVIRPayload
    suspend fun getEDVIRInspectionSetting(
        customerId: String,
        dsn: String,
        inspectionDocumentId: String
    ): EDVIRPayload

    suspend fun listenToInspectionHistory(
        customerId: String,
        dsn: String,
        thresholdTimeInMillis: Long
    )

    fun getInspectionHistoryAsFlow(): Flow<List<EDVIRInspection>>

    suspend fun listenToEDVIRSetting(customerId: String, dsn: String): Flow<EDVIRPayload>

    suspend fun isEDVIRSettingsExist(customerId: String, dsn: String): Boolean
}