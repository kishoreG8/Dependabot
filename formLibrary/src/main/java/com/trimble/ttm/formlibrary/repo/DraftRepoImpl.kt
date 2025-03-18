package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_DRAFT_FORM_DELETE
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.utils.DISPATCH_FORM_SAVE_PATH
import com.trimble.ttm.commons.utils.DRAFT_KEY
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.MESSAGE_COUNT_PER_PAGE
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.toSafeInt
import kotlinx.coroutines.tasks.await
import java.util.Collections
import java.util.TreeMap

private const val tag = "DraftRepoImpl"
class DraftRepoImpl(private val messageFormUseCase: MessageFormUseCase) : DraftRepo, BaseAbstractMessagingRepoImpl(tag) {
    private val draftMessageResponseFlowPair = getCallbackFlow<MutableSet<MessageFormResponse>>()
    private var didOldestMessageReachedForVehicle = getCallbackFlow<Boolean>()
    private val draftMessageResponseMap = TreeMap<Long, MessageFormResponse>(Collections.reverseOrder())
    private var lastFetchedDoc: DocumentSnapshot? = null
    private var listenerRegistration: ListenerRegistration?=null

    override suspend fun getMessages(
        customerId: String,
        vehicleId: String,
        isFirstTimeFetch: Boolean
    ) {
        if (isFirstTimeFetch) {
            lastFetchedDoc = null
            listenerRegistration?.remove()
        }
        var query = getDraftedResponsePath(customerId, vehicleId)
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(MESSAGE_COUNT_PER_PAGE)
        lastFetchedDoc?.let {
            query = query.startAfter(it)
        }
        listenerRegistration =  query.addSnapshotListener { it, firebaseFireStoreException ->
            when (it) {
                is QuerySnapshot -> {
                    it.documentChanges.forEach { documentChange ->
                        when(documentChange.type ){
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val messageResponsePair = parseFireStoreMessageResponseDocument(documentChange.document, DRAFT_KEY)
                                draftMessageResponseMap[messageResponsePair.second] = messageResponsePair.first
                                val driverFormDef = FormDef(formid = messageResponsePair.first.formId.toSafeInt(), formClass = messageResponsePair.first.formClass.toSafeInt())
                                messageFormUseCase.cacheFormTemplate(messageResponsePair.first.formId, driverFormDef.isFreeForm(), tag)
                            }
                            DocumentChange.Type.REMOVED ->{
                                val messageResponsePair = parseFireStoreMessageResponseDocument(documentChange.document, DRAFT_KEY)
                                draftMessageResponseMap.remove(messageResponsePair.second)
                            }
                        }
                    }
                    if(it.documents.size == 0) didOldestMessageReachedForVehicle.first.notify(true)
                    else lastFetchedDoc = it.documents[it.documents.size - 1]
                    draftMessageResponseFlowPair.first.notify(draftMessageResponseMap.values.toMutableSet())
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
    ) = deleteDraftOrSentMessage(customerId, vehicleId, createdTime, isForDraft = true)

    override suspend fun deleteAllMessage(
        customerId: String, vehicleId: String,
        buildEnvironment: BuildEnvironment,token:String, appCheckToken : String
    ): CollectionDeleteResponse =
        deleteAllMessagesOfDraftOrSent(customerId, vehicleId, isForDraft = true, buildEnvironment,token, appCheckToken)

    override fun detachListenerRegistration() {
        listenerRegistration?.remove()
    }

    override fun didLastItemReached()=didOldestMessageReachedForVehicle.second

    override fun resetPagination() {
        didOldestMessageReachedForVehicle.first.notify(false)
        lastFetchedDoc = null
        draftMessageResponseMap.clear()
    }

    override fun getMessageListFlow() = draftMessageResponseFlowPair.second

    fun getDraftCollectionFromFormPath(path: String) : String {
        val items = Regex("/").split(path)
        // Path must have collection/cid/truckid in order to parse and return
        if(items.size >= 3)
        {
            return  "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/${items[1]}/${items[2]}"
        }
        return EMPTY_STRING
    }

    override suspend fun isDraftSaved(path: String, actionId: String): Boolean {
        val searchCollectionPath = getDraftCollectionFromFormPath(path)
        val documents: MutableList<DocumentSnapshot>
        if(searchCollectionPath != EMPTY_STRING) {
            return try {
                val draftCollectionReference = FirebaseFirestore.getInstance().collection(searchCollectionPath)
                documents = if (draftCollectionReference.isCacheEmpty()) {
                    Log.d(tag, "data not found in cache isFormDrafted(path: ${draftCollectionReference.path})")
                    draftCollectionReference.whereEqualTo(DISPATCH_FORM_SAVE_PATH, path).getFromServer().await().documents
                } else {
                    Log.d(tag, "data cache used isFormDrafted(path: ${draftCollectionReference.path})")
                    draftCollectionReference.whereEqualTo(DISPATCH_FORM_SAVE_PATH, path).getFromCache().await().documents
                }
                for(document in documents) {
                    Log.d(tag, "Found form draft saved = [${document.reference.path}] ")
                }
                return documents.size > 0
            } catch (e: IllegalArgumentException) {
                false
            } catch (e: Exception) {
                Log.e(tag, "Exception fetching in doc path: ${e.message}", e)
                false
            }
        } else {
            Log.i(tag, "No searchCollectionPath defined for drafts given path:${path}")
        }
        return false
    }

    override suspend fun deleteDraftMsgOfDispatchFormSavePath(dispatchFormSavePath: String, customerId: String, vehicleId: String) {
        val formResponsePath = getFormResponseQueryPath(dispatchFormSavePath)
        val query = getDraftedResponsePath(customerId, vehicleId)
            .whereIn(DISPATCH_FORM_SAVE_PATH, mutableListOf(dispatchFormSavePath, formResponsePath))
        query.get().await().documents.forEach { docSnap ->
            docSnap.reference.delete()
            Log.i(TRIP_DRAFT_FORM_DELETE, "deleting draft msg: ${docSnap.reference.path}")
        }
    }

    private fun getFormResponseQueryPath(path: String): String {
        val formResponsePath = path.replace(INBOX_FORM_RESPONSE_COLLECTION, FORM_RESPONSES).substringBeforeLast("/")
        Log.d(tag, "Returned Path for FormResponse collection $formResponsePath")
        return formResponsePath
    }
}