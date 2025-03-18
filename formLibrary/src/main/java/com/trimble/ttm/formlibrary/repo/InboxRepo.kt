package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.model.Message
import kotlinx.coroutines.flow.Flow

interface InboxRepo : BasePaginationRepo {

    fun getMessages(
        customerId: String,
        vehicleId: String,
        isFirstTimeFetch: Boolean
    ): Flow<MutableSet<Message>>

    fun getMessagesAtOnce(customerId: String, vehicleId: String): Flow<Message>

    fun getAppModuleCommunicator(): AppModuleCommunicator

}