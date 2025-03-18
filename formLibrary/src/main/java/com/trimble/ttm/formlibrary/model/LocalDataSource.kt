package com.trimble.ttm.formlibrary.model

interface LocalDataSource {

    suspend fun updatePreTripInspectionRequire(isRequired: Boolean)

    suspend fun updatePostTripInspectionRequire(isRequired: Boolean)

    suspend fun updateInspectionRequire(isRequired: Boolean)

    suspend fun updatePreviousPreTripAnnotation(annotation: String)

    suspend fun updatePreviousPostTripAnnotation(annotation: String)

    suspend fun isPreTripInspectionRequired(): Boolean

    suspend fun isPostTripInspectionRequired(): Boolean

    suspend fun getPreviousPreTripAnnotation(): String

    suspend fun getPreviousPostTripAnnotation(): String

    suspend fun clearPreviousAnnotations()

    suspend fun setLastSignedInDriversCount(driverCount: Int)

    suspend fun getLastSignedInDriversCount(): Int

}