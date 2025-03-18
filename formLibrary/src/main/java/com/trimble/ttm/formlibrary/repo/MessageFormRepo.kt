package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.User
import kotlinx.coroutines.flow.SharedFlow

interface MessageFormRepo {
    suspend fun getForm(
        customerId: String,
        formID: Int
    ): FormTemplate

    suspend fun saveFormResponse(
        formResponseDataMap: HashMap<String, Any>,
        docSavePath: String,
        cid : String,
        truckNum : String
    ): Boolean

    suspend fun getFreeForm(
        formId: Int,
        customerId: String,
        vehicleId: String
    ): FormTemplate

    suspend fun markMessageAsRead(
        caller: String,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
        callSource: String
    )

    suspend fun isMessageAlreadyRead(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        callSource: String
    ): Boolean

    suspend fun markMessageAsDeleted(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String
    )

    suspend fun markAllTheMessagesAsDeleted(
        customerId: String,
        vehicleId: String,
        buildEnvironment: BuildEnvironment, token: String?, appCheckToken : String
    )

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): HashMap<String, Any>

    suspend fun getRecipientUserName(recipients: MutableList<Recipients>, cid: Long): Set<User>

    fun getMessagesDeleteAllFlow(): SharedFlow<CollectionDeleteResponse>

    fun getDeletedInboxMessageASNFlow(): SharedFlow<String>

    fun getAppModuleCommunicator(): AppModuleCommunicator

    suspend fun getDispatcherFormValuesFromInbox(
        customerId: String,
        vehicleId: String,
        asn: String,
    ): Message

    suspend fun deleteSelectedMessageInTrash(customerId: String, vehicleId: String, asn: String, caller: String)

    suspend fun deleteAllMessageInTrash(customerId: String, vehicleId: String, buildEnvironment: BuildEnvironment, token: String, appCheckToken : String): CollectionDeleteResponse
}