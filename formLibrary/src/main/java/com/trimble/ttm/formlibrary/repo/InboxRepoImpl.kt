package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.INBOX_LIST
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.logger.WIDGET
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.ASN_INBOX
import com.trimble.ttm.formlibrary.utils.MESSAGE_COUNT_PER_PAGE
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.toSafeInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

private const val tag = "InboxRepoImpl"

var isNewMessageNotificationReceived = false

class InboxRepoImpl(
    private val messageFormUseCase: MessageFormUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : InboxRepo,
    BaseAbstractMessagingRepoImpl(tag) {
    private val didOldestMessageReachedForVehicle = getCallbackFlow<Boolean>()
    private var lastFetchedDoc: DocumentSnapshot? = null


    override fun getMessages(
        customerId: String,
        vehicleId: String,
        isFirstTimeFetch: Boolean
    ): Flow<MutableSet<Message>> = callbackFlow {
        if (isFirstTimeFetch) {
            lastFetchedDoc = null
        }
        var query =
            getInboxPath(customerId, vehicleId).orderBy(ASN_INBOX, Query.Direction.DESCENDING)
                .limit(MESSAGE_COUNT_PER_PAGE)
        lastFetchedDoc?.let {
            query = query.startAfter(it)
        }
        var inboxMessageSnapshotListener : ListenerRegistration? = null
        try {
            val inboxMessagesHashMap : HashMap<Long, Message> = HashMap()
            inboxMessageSnapshotListener = query.addSnapshotListener{
                    querySnapshot, firebaseFireStoreException ->
                if(firebaseFireStoreException != null){
                    Log.w("$INBOX$REPO", "Exception in getMessages snapshot listener ${firebaseFireStoreException.message}", firebaseFireStoreException)
                    emitInboxMessagesIfChannelNotClosed(isClosedForSend.not(), isActive, channel, HashMap<Long, Message>())
                    return@addSnapshotListener
                }
                when (querySnapshot) {
                    is QuerySnapshot -> {
                        querySnapshot.documentChanges.forEach { documentChange ->
                            processMessageDocumentChanges(documentChange, inboxMessagesHashMap)
                        }
                        checkForOldestMessageReachedForVehicle(querySnapshot)
                        emitInboxMessagesIfChannelNotClosed(isClosedForSend.not(), isActive, channel, inboxMessagesHashMap)
                    }
                }
            }
        } catch (cancellationException: CancellationException) {
            if(cancellationException.cause != null) {
                Log.w(
                    "$INBOX$REPO",
                    "cancelledGetMessages ${cancellationException.message} cause:${cancellationException.cause?.stackTraceToString()}", cancellationException
                )
            }
            emitInboxMessagesIfChannelNotClosed(isClosedForSend.not(), isActive, channel, HashMap<Long, Message>())
        }
        catch (e: Exception) {
            Log.e("$INBOX$REPO", "Exception in getMessages ${e.message}", e)
            emitInboxMessagesIfChannelNotClosed(isClosedForSend.not(), isActive, channel, HashMap<Long, Message>())
        }
        awaitClose {
            inboxMessageSnapshotListener?.remove()
            close()
        }
    }

    private fun emitInboxMessagesIfChannelNotClosed(isChannelActive: Boolean, isJobAlive : Boolean, channel: SendChannel<MutableSet<Message>>, inboxMessageMap: HashMap<Long, Message>) {
        if (isChannelActive && isJobAlive) {
            Log.i("$INBOX$REPO","Message size ${inboxMessageMap.size}")
            channel.trySend(inboxMessageMap.values.toMutableSet())
        }else{
            Log.w("$INBOX$REPO", "emitInboxMessagesIfChannelNotClosed channelActive:$isChannelActive isJobAlive:$isJobAlive")
        }
    }

    //Driver TTS widget needs the latest Inbox messages data when it needs.
    //This method will fetch the latest MESSAGE_COUNT_PER_PAGE number of messages from firestore.
    override fun getMessagesAtOnce(
        customerId: String,
        vehicleId: String,
    ): Flow<Message> = callbackFlow {
        val query = getInboxPath(customerId, vehicleId)
            .orderBy(ASN_INBOX, Query.Direction.DESCENDING)
            .limit(MESSAGE_COUNT_PER_PAGE)
        safeLaunch(CoroutineName(tag) + dispatchers.io()) {
            try {
                val docRef = query.get().await()
                docRef.documents.forEach {
                    val messageTriple = parseFireStoreMessageDocument(it)
                    Log.d("$INBOX$WIDGET$REPO", "widgetMsg:${messageTriple.first.asn}")
                    cacheForms(messageTriple)
                    if (isClosedForSend.not() and isActive) channel.trySend(messageTriple.first)
                }
            } catch (e: CancellationException) {
                //to review
            } catch (e: Exception) {
                Log.e(
                    "$INBOX$WIDGET$REPO",
                    "ExceptionGetMessagesAtOnce ${e.message}"
                )
            } finally {
                channel.close()
            }
        }
        awaitClose {
            close()
        }
    }

    override fun getAppModuleCommunicator(): AppModuleCommunicator = appModuleCommunicator

    private fun processMessageDocumentChanges(
        documentChange: DocumentChange,
        inboxMessageMap: HashMap<Long, Message>,
    ) {
        when (documentChange.type) {
            DocumentChange.Type.ADDED -> {
                val messageTriple = parseFireStoreMessageDocument(documentChange.document)
                inboxMessageMap[messageTriple.first.asn.toLong()] = messageTriple.first
                Log.d("$INBOX_LIST$REPO", "Added ${messageTriple.first.asn} ${messageTriple.first.date} ${messageTriple.first.summaryText}")
                cacheForms(messageTriple)
            }

            DocumentChange.Type.REMOVED -> {
                val messageTriple = parseFireStoreMessageDocument(documentChange.document)
                Log.d("$INBOX_LIST$REPO", "Removed ${messageTriple.first.asn} ${messageTriple.first.date}")
                if(inboxMessageMap.containsKey(messageTriple.first.asn.toLong())){
                    inboxMessageMap.remove(messageTriple.first.asn.toLong())
                }
            }

            DocumentChange.Type.MODIFIED -> {
                val messageTriple = parseFireStoreMessageDocument(documentChange.document)
                inboxMessageMap[messageTriple.first.asn.toLong()] = messageTriple.first
                Log.d("$INBOX_LIST$REPO", "Updated ${messageTriple.first.asn} ${messageTriple.first.date}")
                cacheForms(messageTriple)
            }
        }
    }

    private fun checkForOldestMessageReachedForVehicle(snapShot: QuerySnapshot) {
        if (snapShot.documents.isEmpty()) didOldestMessageReachedForVehicle.first.notify(true)
        else lastFetchedDoc = snapShot.documents[snapShot.documents.size - 1]
    }

    private fun cacheForms(messageTriple: Pair<Message, Long>) {
        val dispatcherFormDef = FormDef(
            formid = messageTriple.first.formId.toSafeInt(),
            formClass = messageTriple.first.formClass.toSafeInt()
        )
        val driverFormDef = FormDef(
            formid = messageTriple.first.replyFormId.toSafeInt(),
            formClass = messageTriple.first.replyFormClass.toSafeInt()
        )
        with(messageFormUseCase) {
            cacheFormTemplate(messageTriple.first.formId, dispatcherFormDef.isFreeForm(), tag)
            cacheFormTemplate(messageTriple.first.replyFormId, driverFormDef.isFreeForm(), tag)
        }
    }

    override fun resetPagination() {
        didOldestMessageReachedForVehicle.first.notify(false)
        lastFetchedDoc = null
    }

    override fun didLastItemReached(): Flow<Boolean> = didOldestMessageReachedForVehicle.second
}

