package com.trimble.ttm.formlibrary.dataLayer

import androidx.room.*
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

@Entity(tableName = "MandatoryInspection")
data class MandatoryInspectionMetaData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "IsPreTripInspectionRequired")
    var isPreTripInspectionRequired: Boolean = true,
    @ColumnInfo(name = "IsPostTripInspectionRequired")
    var isPostTripInspectionRequired: Boolean = true,
    @ColumnInfo(name = "PreviousPreTripAnnotation")
    var previousPreTripAnnotation: String = EMPTY_STRING,
    @ColumnInfo(name = "PreviousPostTripAnnotation")
    var previousPostTripAnnotation: String = EMPTY_STRING
)

@Dao
interface MandatoryInspectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mandatoryInspectionMetaData: MandatoryInspectionMetaData)

    @Query("SELECT * FROM MandatoryInspection LIMIT 1")
    suspend fun getLatestData(): MandatoryInspectionMetaData

    @Query("DELETE FROM MandatoryInspection")
    suspend fun delete()

}

@Database(
    entities = [MandatoryInspectionMetaData::class],
    version = 1
)
abstract class MandatoryInspectionRelatedDatabase : RoomDatabase() {
    abstract val mandatoryInspectionDao: MandatoryInspectionDao
}
