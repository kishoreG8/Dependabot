package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.*
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.SENT_KEY
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.MESSAGE_COUNT_PER_PAGE
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.toSafeInt
import java.util.*

private const val tag = "SentRepoImpl"

class SentRepoImpl(
    private val messageFormUseCase: MessageFormUseCase,
    private val appModuleCommunicator: AppModuleCommunicator
) : SentRepo, BaseAbstractMessagingRepoImpl(tag) {
    private val sentMessageResponseFlowPair = getCallbackFlow<MutableSet<MessageFormResponse>>()
    private var didOldestMessageReachedForVehicle = getCallbackFlow<Boolean>()
    private val sentMessageResponseMap = TreeMap<Long, MessageFormResponse>(Collections.reverseOrder())
    private var lastFetchedDoc: DocumentSnapshot? = null
    private var listenerRegistration: ListenerRegistration?=null

    override suspend fun getMessages(
        customerId: String,
        vehicleId: String,isFirstTimeFetch:Boolean
    ) {
        if (isFirstTimeFetch) {
            lastFetchedDoc = null
            listenerRegistration?.remove()
        }
        var query = getSentResponsePath(customerId, vehicleId)
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(MESSAGE_COUNT_PER_PAGE)
        lastFetchedDoc?.let {
           query = query.startAfter(it)
        }
        listenerRegistration = query.addSnapshotListener { querySnapshot, firebaseFireStoreException ->
            when (querySnapshot) {
                is QuerySnapshot -> {
                    querySnapshot.documentChanges.forEach { documentChange ->
                        when(documentChange.type ){
                            DocumentChange.Type.ADDED -> {
                                val messageResponsePair = parseFireStoreMessageResponseDocument(documentChange.document, SENT_KEY)
                                sentMessageResponseMap[messageResponsePair.second]= messageResponsePair.first
                                val driverFormDef = FormDef(formid = messageResponsePair.first.formId.toSafeInt(), formClass = messageResponsePair.first.formClass.toSafeInt())
                                messageFormUseCase.cacheFormTemplate(messageResponsePair.first.formId, driverFormDef.isFreeForm(), tag)
                            }
                            DocumentChange.Type.REMOVED ->{
                                val messageResponsePair = parseFireStoreMessageResponseDocument(documentChange.document, SENT_KEY)
                                sentMessageResponseMap.remove(messageResponsePair.second)
                            }
                            else -> {
                                //Ignored
                            }
                        }
                    }
                    if(querySnapshot.documents.size == 0) didOldestMessageReachedForVehicle.first.notify(true)
                    else lastFetchedDoc = querySnapshot.documents[querySnapshot.documents.size - 1]
                    sentMessageResponseFlowPair.first.notify(sentMessageResponseMap.values.toMutableSet())
                }
                else -> {
                    Log.d(tag, "$customerId - $vehicleId ${(firebaseFireStoreException as Exception).message}")
                }
            }
        }
    }

    override suspend fun deleteMessage(
        customerId: String,
        vehicleId: String,
        createdTime: Long
    ) = deleteDraftOrSentMessage(customerId, vehicleId, createdTime, isForDraft = false)

    override suspend fun deleteAllMessage(
        customerId: String, vehicleId: String,
        buildEnvironment: BuildEnvironment,token:String, appCheckToken : String
    ): CollectionDeleteResponse =
        deleteAllMessagesOfDraftOrSent(customerId, vehicleId, isForDraft = false, buildEnvironment,token, appCheckToken)

    override fun detachListenerRegistration() {
        listenerRegistration?.remove()
    }

    override fun didLastItemReached()= didOldestMessageReachedForVehicle.second

    override fun resetPagination() {
        didOldestMessageReachedForVehicle.first.notify(false)
        lastFetchedDoc = null
        sentMessageResponseMap.clear()
    }

    override fun getMessageListFlow() = sentMessageResponseFlowPair.second

    override fun getAppModuleCommunicator() = appModuleCommunicator

}