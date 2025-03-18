package com.trimble.ttm.routemanifest.repo

import com.trimble.ttm.formlibrary.model.EDVIRPayload
import kotlinx.coroutines.flow.Flow

interface FireStoreCacheRepository {

    fun addSnapshotListenerForEDVIRSetting(
        cid: String,
        dsn: String,
        settingsDocumentId: String
    ): Flow<EDVIRPayload>?

    suspend fun syncFormData(
        cid: String,
        formId: String,
        formClass: Int
    )
}