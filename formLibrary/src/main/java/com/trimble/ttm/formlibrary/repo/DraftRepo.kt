package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import kotlinx.coroutines.flow.Flow

interface DraftRepo: BasePaginationRepo {
    suspend fun getMessages(customerId: String, vehicleId: String,isFirstTimeFetch:Boolean)
    fun getMessageListFlow(): Flow<MutableSet<MessageFormResponse>>
    suspend fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long)
    suspend fun deleteAllMessage(customerId: String, vehicleId: String,buildEnvironment: BuildEnvironment,token:String, appCheckToken : String): CollectionDeleteResponse
    fun detachListenerRegistration()
    suspend fun isDraftSaved(path: String, actionId: String): Boolean
    suspend fun deleteDraftMsgOfDispatchFormSavePath(dispatchFormSavePath: String, customerId: String, vehicleId: String)
}