package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import kotlinx.coroutines.flow.Flow

interface SentRepo: BasePaginationRepo {
    suspend fun getMessages(customerId: String, vehicleId: String,isFirstTimeFetch:Boolean)
    suspend fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long)
    suspend fun deleteAllMessage(customerId: String, vehicleId: String,buildEnvironment: BuildEnvironment,token:String, appCheckToken : String): CollectionDeleteResponse
    fun detachListenerRegistration()
    fun getMessageListFlow(): Flow<MutableSet<MessageFormResponse>>
    fun getAppModuleCommunicator() : AppModuleCommunicator
}