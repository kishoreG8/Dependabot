package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionDao
import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionMetaData
import com.trimble.ttm.formlibrary.utils.isNull

class InspectionExposeRepoImpl(
    private val mandatoryInspectionDao: MandatoryInspectionDao
    ) : InspectionExposeRepo {
    override suspend fun insert(mandatoryInspectionMetaData: MandatoryInspectionMetaData) {
        mandatoryInspectionDao.insert(mandatoryInspectionMetaData)
    }

    override suspend fun getLatestData(): MandatoryInspectionMetaData {
        var latestData = mandatoryInspectionDao.getLatestData()
        if (latestData.isNull()) latestData = MandatoryInspectionMetaData()
        return  latestData
    }

    override suspend fun delete() {
        mandatoryInspectionDao.delete()
    }

}