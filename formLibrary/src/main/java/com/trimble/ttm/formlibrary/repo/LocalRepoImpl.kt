package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.model.LocalDataSource

class LocalRepoImpl(private val localDataSource: LocalDataSource): LocalRepo {

    override suspend fun updatePreTripInspectionRequire(isRequired: Boolean) =
        localDataSource.updatePreTripInspectionRequire(isRequired)

    override suspend fun updatePostTripInspectionRequire(isRequired: Boolean) =
        localDataSource.updatePostTripInspectionRequire(isRequired)

    override suspend fun updateInspectionRequire(isRequired: Boolean) =
        localDataSource.updateInspectionRequire(isRequired)

    override suspend fun updatePreviousPreTripAnnotation(annotation: String) =
        localDataSource.updatePreviousPreTripAnnotation(annotation)

    override suspend fun updatePreviousPostTripAnnotation(annotation: String) =
        localDataSource.updatePreviousPostTripAnnotation(annotation)

    override suspend fun isPreTripInspectionRequired(): Boolean =
        localDataSource.isPreTripInspectionRequired()

    override suspend fun isPostTripInspectionRequired(): Boolean =
        localDataSource.isPostTripInspectionRequired()

    override suspend fun getPreviousPreTripAnnotation(): String =
        localDataSource.getPreviousPreTripAnnotation()

    override suspend fun getPreviousPostTripAnnotation(): String =
        localDataSource.getPreviousPostTripAnnotation()

    override suspend fun clearPreviousAnnotations() =
        localDataSource.clearPreviousAnnotations()

    override suspend fun setLastSignedInDriversCount(driverCount: Int) =
        localDataSource.setLastSignedInDriversCount(driverCount)

    override suspend fun getLastSignedInDriversCount(): Int =
        localDataSource.getLastSignedInDriversCount()

}