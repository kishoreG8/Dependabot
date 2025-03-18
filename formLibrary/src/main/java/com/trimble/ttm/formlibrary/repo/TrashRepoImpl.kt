package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.logger.TRASH_LIST
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.MESSAGE_COUNT_PER_PAGE
import com.trimble.ttm.formlibrary.utils.ROW_DATE
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.toSafeInt
import java.util.Collections
import java.util.TreeMap

private const val tag = "TrashRepoImpl"
class TrashRepoImpl(private val messageFormUseCase: MessageFormUseCase) : TrashRepo, BaseAbstractMessagingRepoImpl(tag) {
    private val messageListFlowPair = getCallbackFlow<MutableSet<Message>>()
    private var didOldestMessageReachedForVehicle = getCallbackFlow<Boolean>()
    internal val trashMessageMap = TreeMap<Long, Message>(Collections.reverseOrder())
    private var lastFetchedDoc: DocumentSnapshot? = null
    private var listenerRegistration:ListenerRegistration?=null

    override suspend fun getMessages(customerId: String, vehicleId: String,isFirstTimeFetch:Boolean) {
        if (isFirstTimeFetch) {
            lastFetchedDoc = null
            listenerRegistration?.remove()
        }
        var query = getTrashPath(customerId, vehicleId).orderBy(ROW_DATE, Query.Direction.DESCENDING).limit(MESSAGE_COUNT_PER_PAGE)
        lastFetchedDoc?.let {
            query = query.startAfter(it)
        }

        listenerRegistration=query.addSnapshotListener{it, firebaseFireStoreException ->
            when (it) {
                is QuerySnapshot -> {
                    it.documentChanges.forEach { documentChange ->
                        val messagePair = parseFireStoreMessageDocument(documentChange.document)
                        when(documentChange.type){
                            DocumentChange.Type.ADDED,DocumentChange.Type.MODIFIED -> {
                                messagePair.first.asn.toLongOrNull()?.let { asn ->
                                    trashMessageMap[asn] =
                                        messagePair.first
                                    val dispatcherFormDef = FormDef(
                                        formid = messagePair.first.formId.toSafeInt(),
                                        formClass = messagePair.first.formClass.toSafeInt()
                                    )
                                    messageFormUseCase.cacheFormTemplate(
                                        messagePair.first.formId,
                                        dispatcherFormDef.isFreeForm(),
                                        tag
                                    )
                                } ?: Log.d(
                                        "$TRASH_LIST$REPO",
                                        "Invalid Message ASN: ${messagePair.first.asn} for Customer: $customerId and Vehicle: $vehicleId"
                                    )
                            }
                            DocumentChange.Type.REMOVED -> {
                                Log.d("$TRASH_LIST$REPO", "Removed ${messagePair.first.asn} ${messagePair.first.date} from Trash")
                                if (trashMessageMap.contains(messagePair.first.asn.toLong())) {
                                    trashMessageMap.remove(messagePair.first.asn.toLong())
                                }
                            }
                        }
                    }
                    if(it.documents.size == 0)  didOldestMessageReachedForVehicle.first.notify(true)
                    else   lastFetchedDoc = it.documents[it.documents.size - 1]
                    messageListFlowPair.first.notify(trashMessageMap.values.toMutableSet())
                }
                else -> {
                    Log.d("$TRASH_LIST$REPO", "$customerId - $vehicleId ${(firebaseFireStoreException as Exception).message}")
                }
            }
        }
    }

    override fun resetPagination() {
        didOldestMessageReachedForVehicle.first.notify(false)
        lastFetchedDoc = null
        trashMessageMap.clear()
    }

    override fun didLastItemReached()=didOldestMessageReachedForVehicle.second

    override fun getMessageListFlow() = messageListFlowPair.second

    override fun detachListenerRegistration() {
        listenerRegistration?.remove()
    }

    override suspend fun deleteAllMessages(
        customerId: String,
        vehicleId: String,
        token: String?,
        appCheckToken: String
    ): CollectionDeleteResponse =
        messageFormUseCase.deleteAllTrashMessages(customerId, vehicleId, token, appCheckToken)


    override suspend fun deleteMessage(
        customerId: String,
        vehicleId: String,
        asn: String,
        caller: String
    ) {
        messageFormUseCase.deleteSelectedTrashMessage(customerId, vehicleId, asn, caller)
    }
}