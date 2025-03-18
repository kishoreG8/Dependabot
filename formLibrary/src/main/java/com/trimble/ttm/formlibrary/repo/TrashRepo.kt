package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import kotlinx.coroutines.flow.Flow

interface TrashRepo: BasePaginationRepo {
    suspend fun getMessages(customerId: String, vehicleId: String,isFirstTimeFetch:Boolean)
    fun getMessageListFlow(): Flow<MutableSet<Message>>
    fun detachListenerRegistration()
    suspend fun deleteAllMessages(customerId: String, vehicleId: String, token: String?, appCheckToken: String): CollectionDeleteResponse
    suspend fun deleteMessage(customerId: String, vehicleId: String, asn: String, caller: String)
}