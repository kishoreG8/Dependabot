package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseRepoData


interface EDVIRFormRepo {
    suspend fun saveEDVIRFormResponse(
        saveEDVIRFormResponseData: EDVIRFormResponseRepoData
    ): Boolean

    suspend fun getEDVIRFormDataResponse(
        customerId: String,
        dsn: String,
        createdAt: String
    ): UIFormResponse

}