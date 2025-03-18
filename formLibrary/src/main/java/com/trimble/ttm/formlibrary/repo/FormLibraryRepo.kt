package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import kotlinx.coroutines.flow.Flow

interface FormLibraryRepo: BasePaginationRepo {
    suspend fun getForms(customerId: String, obcId: Long, isServerSync: Boolean)
    suspend fun getFormDefListFlow(): Flow<Map<Double, FormDef>>
    fun addHotkeysSnapshotFlow(path: String, documentId: String) : Flow<Set<HotKeys>>
    suspend fun addFavourite(favourite: Favourite, driverId: String)
    suspend fun removeFavourite(formid: String, driverId: String)
    fun getFavouriteForms(path: String, driverId: String) : Flow<MutableSet<Favourite>>
    suspend fun getHotKeysDescription(hotKeysSet : Set<HotKeys>, isInternetAvailable: Boolean) : MutableSet<HotKeys>
    suspend fun getHotKeysCount(path: String, documentId: String, isInternetAvailable : Boolean) : Int
}