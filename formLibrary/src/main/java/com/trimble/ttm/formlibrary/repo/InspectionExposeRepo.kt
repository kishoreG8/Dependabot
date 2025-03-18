package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionMetaData

interface InspectionExposeRepo {

    suspend fun insert(mandatoryInspectionMetaData: MandatoryInspectionMetaData)

    suspend fun getLatestData(): MandatoryInspectionMetaData

    suspend fun delete()

}