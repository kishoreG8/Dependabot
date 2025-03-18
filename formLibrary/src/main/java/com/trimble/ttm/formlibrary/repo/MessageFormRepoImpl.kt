package com.trimble.ttm.formlibrary.repo

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotListenOptions
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.ACKNOWLEDGMENT
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.INBOX_MESSAGE_DEF_VALUES
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MESSAGE_DELETE
import com.trimble.ttm.commons.logger.RECIPIENT
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.logger.TRASH
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ASN
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.INBOX_COLLECTION_READ_DELAY
import com.trimble.ttm.commons.utils.MULTIPLE_CHOICE_FIELD_TYPE
import com.trimble.ttm.commons.utils.ext.doContinuation
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.http.CollectionDeleteApi
import com.trimble.ttm.formlibrary.http.CollectionDeleteApiClient
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.utils.ASN_INBOX
import com.trimble.ttm.formlibrary.utils.CONFIRMATION_DATE_TIME
import com.trimble.ttm.formlibrary.utils.CONFIRMED
import com.trimble.ttm.formlibrary.utils.DELIVERED
import com.trimble.ttm.formlibrary.utils.DSN
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORMS_LIST_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_CHOICES_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_FIELD_COLLECTION
import com.trimble.ttm.formlibrary.utils.FREE_FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FormUtils.getFormRecipients
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_MSG_CONFIRMATION_COLLECTION
import com.trimble.ttm.formlibrary.utils.IS_DELIVERED
import com.trimble.ttm.formlibrary.utils.IS_READ
import com.trimble.ttm.formlibrary.utils.MARK_DELIVERED
import com.trimble.ttm.formlibrary.utils.MARK_READ
import com.trimble.ttm.formlibrary.utils.PAYLOAD
import com.trimble.ttm.formlibrary.utils.PAYLOAD_EMAIL
import com.trimble.ttm.formlibrary.utils.PAYLOAD_UID
import com.trimble.ttm.formlibrary.utils.ROW_DATE
import com.trimble.ttm.formlibrary.utils.STATUS
import com.trimble.ttm.formlibrary.utils.TRASH_COLLECTION
import com.trimble.ttm.formlibrary.utils.USERS_COLLECTION
import com.trimble.ttm.formlibrary.utils.USER_LIST_COLLECTION
import com.trimble.ttm.formlibrary.utils.WHERE_IN_QUERY_CHUNK_MAX_COUNT
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val tag = "MessageFormRepository"

@Suppress("UNCHECKED_CAST")
class MessageFormRepoImpl(
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatcherProvider: DispatcherProvider,
    private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : MessageFormRepo,BaseAbstractMessagingRepoImpl(tag) {
    private val gson = Gson()

    private val _deletedInboxMessageASNFlow:MutableSharedFlow<String> = MutableSharedFlow()

    private var _inboxDeleteAllMessagesResponse :MutableSharedFlow<CollectionDeleteResponse> = MutableSharedFlow()

    override suspend fun getForm(customerId: String, formID: Int): FormTemplate = try {
        val formTemplate: FormTemplate
        val documentReference =
            getFormListCollectionReference(customerId).document(formID.toString())
        if (documentReference.isCacheEmpty()) {
            Log.d(tag, "data not found in cache getForm - ${documentReference.path}")
            val formDefDocument = documentReference.getFromServer().await()
            formTemplate = getFormTemplate(formDefDocument, customerId)
            Log.d(
                tag,
                "FormTemplate in getForm from server(path:${documentReference.path})"
            )
            formTemplate
        } else fetchFormTemplateFromCache(documentReference, customerId)
    } catch (e: FirebaseFirestoreException) {
        fetchFormTemplateFromCache(
            getFormListCollectionReference(customerId).document(formID.toString()),
            customerId
        )
    } catch (e: Exception) {
        Log.e(tag, "exception in getForm of customer: $customerId formId: $formID ${e.message}")
        FormTemplate()
    }

    private suspend fun fetchFormTemplateFromCache(
        documentReference: DocumentReference,
        customerId: String
    ): FormTemplate = try {
        val formDefDocument = documentReference.getFromCache().await()
        val formTemplate = getFormTemplate(formDefDocument, customerId)
        Log.d(
            tag,
            "FormTemplate in getForm from cache(path:${documentReference.path})"
        )
        formTemplate
    } catch (e: Exception) {
        Log.e(tag, "exception in getForm from cache customer: $customerId  ${e.message}")
        FormTemplate()
    }


    override suspend fun saveFormResponse(
        formResponseDataMap: HashMap<String, Any>,
        docSavePath: String,
        cid: String,
        truckNum: String
    ) = suspendCancellableCoroutine { continuation ->
        try {
            var listenerRegistration: ListenerRegistration? = null
            val options = SnapshotListenOptions.Builder()
                .setMetadataChanges(MetadataChanges.INCLUDE)
                .setSource(ListenSource.CACHE)
                .build()
            val curTimeUtcFormattedStr = getUTCFormattedDate(Calendar.getInstance().time)
            val documentReference = firebaseFirestore.collection(docSavePath)
                .document(curTimeUtcFormattedStr)
            documentReference.set(formResponseDataMap)
            listenerRegistration = documentReference.addSnapshotListener(options) { snapshot, err ->
                if (snapshot.isNull()) {
                    Log.e(
                        tag,
                        "Error while saving form response. Path: $docSavePath. Error: DocumentSnapshot is null $err"
                    )
                    doContinuation(continuation, false,listenerRegistration)
                } else {
                    Log.d(
                        tag,
                        "Form response saved successfully. Path: $docSavePath"
                    )
                   doContinuation(continuation, true,listenerRegistration)
                }
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error while saving form response. Path: $docSavePath",
                e
            )
            doContinuation(continuation, false)
        }
    }


    override suspend fun getRecipientUserName(
        recipients: MutableList<Recipients>,
        cid: Long
    ): Set<User> {
        val users = mutableSetOf<User>()
        val userCollectionRef =
            firebaseFirestore.collection(USERS_COLLECTION).document(cid.toString())
                .collection(USER_LIST_COLLECTION)
        val pfmUserRecipient =
            recipients.filter { recipient -> recipient.recipientPfmUser.isNotNull() }
        val pfmEmailRecipient =
            recipients.filter { recipient -> recipient.recipientEmailUser.isNotNull() }
        pfmUserRecipient.chunked(WHERE_IN_QUERY_CHUNK_MAX_COUNT).forEach { pfmUserRecipientChunk ->
            val pfmUserRecipientQuery = userCollectionRef.whereIn(
                PAYLOAD_UID,
                pfmUserRecipientChunk.map { it.recipientPfmUser })
            parseUserDocument(pfmUserRecipientQuery.get().await(), users)
        }
        pfmEmailRecipient.chunked(WHERE_IN_QUERY_CHUNK_MAX_COUNT)
            .forEach { pfmEmailRecipientChunk ->
                val pfmEmailRecipientQuery = userCollectionRef.whereIn(
                    PAYLOAD_EMAIL,
                    pfmEmailRecipientChunk.map { it.recipientEmailUser })
                parseUserDocument(pfmEmailRecipientQuery.get().await(), users)
            }
        return users
    }

    private fun parseUserDocument(querySnapshot: QuerySnapshot, users: MutableSet<User>) {
        querySnapshot.documents.forEach { userDocSnapshot ->
            if (userDocSnapshot.isNull() || userDocSnapshot.exists().not()) return@forEach
            parseUser(gson, userDocSnapshot[PAYLOAD], users)
        }
    }

    internal fun parseUser(gson: Gson, payload: Any?, users: MutableSet<User>) {
        gson.fromJson(gson.toJson(payload), User::class.java)?.let { user -> users.add(user) }
    }

    private suspend fun getFormTemplate(
        formDefDocument: DocumentSnapshot,
        customerId: String
    ): FormTemplate = try {
        //Fetching Form Definition
        val formDef = gson.fromJson(
            gson.toJson(formDefDocument.data?.getValue(PAYLOAD)),
            FormDef::class.java
        )
        // Form recipients
        formDefDocument.data?.let {
            formDef?.let { def ->
                def.recipients = it.getFormRecipients()
            }
        }
        //Fetching FormFields
        val collRef = getFormsFieldCollectionReference(
            customerId,
            formDef.formid.toString()
        )
        val formFieldCollection: QuerySnapshot = try {
            if (collRef.isCacheEmpty()) collRef.getFromServer().await()
            else collRef.getFromCache().await()
        } catch (e: FirebaseFirestoreException) {
            collRef.getFromCache().await()
        }
        val formFieldList = ArrayList<FormField>()
        processFormFieldDocuments(
            customerId,
            formFieldCollection,
            formFieldList
        )
        FormTemplate(formDef, formFieldList)
    } catch (e: Exception) {
        Log.e(
            tag,
            "exception in getFormTemplate of customer: $customerId Document Path: ${formDefDocument.reference.path}",null,"error" to e.stackTraceToString()
        )
        FormTemplate()
    }

    private suspend fun processFormFieldDocuments(
        customerId: String,
        formFieldCollection: QuerySnapshot,
        formFieldList: ArrayList<FormField>
    ) {
        formFieldCollection.forEach {
            it?.let {
                val formField =
                    gson.fromJson(gson.toJson(it.data.getValue(PAYLOAD)), FormField::class.java)

                //Actual Loop Count of the field
                formField.actualLoopCount = formField.loopcount

                if (formField.qtype != MULTIPLE_CHOICE_FIELD_TYPE)
                    formFieldList.add(formField)
                else {
                    //Fetching Form Choices of a FormField
                    fetchFormChoices(customerId, formField).let { formChoice ->
                        formFieldList.add(formChoice)
                    }
                }
            }
        }
    }

    private suspend fun fetchFormChoices(
        customerId: String,
        formField: FormField
    ): FormField = try {
        val formChoicesList = ArrayList<FormChoice>()
        val documentReference =
            getFormFieldFormChoiceCollectionReference(customerId, formField)
        if (documentReference.isCacheEmpty()) {
            Log.d(
                tag,
                "data not found in cache fetchFormChoices - ${documentReference.path}"
            )
            val formChoicesCollection = documentReference.getFromServer().await()
            formField.formChoiceList =
                getFormChoiceList(formChoicesCollection, formChoicesList)
            formField
        } else {
            val formChoicesCollection = documentReference.getFromCache().await()
            formField.formChoiceList =
                getFormChoiceList(formChoicesCollection, formChoicesList)
            formField
        }
    } catch (e: FirebaseFirestoreException) {
        val formChoicesList = ArrayList<FormChoice>()
        val formChoicesCollection =
            getFormFieldFormChoiceCollectionReference(customerId, formField).getFromCache().await()
        formField.formChoiceList =
            getFormChoiceList(formChoicesCollection, formChoicesList)
        formField
    } catch (e: Exception) {
        Log.e(tag, "exception in fetchFormChoices of customer: $customerId error ${e.message}")
        formField
    }

    private fun getFormChoiceList(
        formChoicesCollection: QuerySnapshot,
        formChoicesList: ArrayList<FormChoice>
    ): ArrayList<FormChoice> {
        formChoicesCollection.documents
            .asSequence()
            .map {
                gson.toJson(
                    it?.data?.getValue(
                        PAYLOAD
                    )
                )
            }
            .mapTo(formChoicesList) { gson.fromJson(it, FormChoice::class.java) }
        return formChoicesList
    }

    override suspend fun getFreeForm(
        formId: Int,
        customerId: String,
        vehicleId: String
    ): FormTemplate = try {
        val freeFormFormDefDocumentData = getFreeFormFormDefDocumentData(formId)
        freeFormFormDefDocumentData.first.let { docSnapshot ->
            if (docSnapshot.exists()) {
                var formDef = FormDef()
                docSnapshot.data?.let {
                    formDef = gson.fromJson(
                        gson.toJson(it.getValue(PAYLOAD)),
                        FormDef::class.java
                    )
                }
                val freeFormFormFieldQuerySnapshot = getFreeFormFormFieldSubCollection(
                    formId
                )
                val formFieldList = ArrayList<FormField>()
                freeFormFormFieldQuerySnapshot.forEach { queryDocSnapshot ->
                    queryDocSnapshot?.let {
                        val formField =
                            gson.fromJson(
                                gson.toJson(it.data.getValue(PAYLOAD)),
                                FormField::class.java
                            )
                        if (formField.qtype != MULTIPLE_CHOICE_FIELD_TYPE)
                            formFieldList.add(formField)
                    }
                }
                FormTemplate(formDef, formFieldList)
            } else {
                // Form does not exists in FreeForms collection
                FormTemplate()
            }
        }
    } catch (e: Exception) {
        Log.e(
            tag,
            "error retrieving free form id $formId ${e.stackTraceToString()}"
        )
        FormTemplate()
    }

    override suspend fun markMessageAsRead(
        caller: String,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
        callSource: String
    ) = suspendCancellableCoroutine { continuation ->
        if (customerId.isEmpty() or vehicleId.isEmpty() or obcId.isEmpty() or asn.isEmpty() || asn == "0") {
            Log.d(callSource, "invalid path for data access. cid:$customerId truckNum:$vehicleId obcId:$obcId asn:$asn")
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val scope = CoroutineScope(dispatcherProvider.io())
        scope.launch(CoroutineName(tag)) {
            when (callSource) {
                INBOX_COLLECTION -> {
                    markInboxMessageAsRead(callSource + caller, customerId, vehicleId, obcId, asn, operationType)
                }
                TRASH_COLLECTION -> {
                    markTrashMessageAsRead(callSource + caller, customerId, vehicleId, asn)
                }
            }
            if (continuation.isActive) continuation.resume(Unit)
            scope.cancel()
        }
    }

    internal suspend fun markInboxMessageAsRead(caller: String, customerId: String, vehicleId: String, obcId: String, asn: String, operationType: String) {
        try {
            val documentReference = getInboxPathDocument(customerId, vehicleId, asn)
            val documentSnapshot = if (documentReference.isCacheEmpty()) {
                Log.d(tag, "data not found in cache markMessageAsRead - ${documentReference.path}")
                documentReference.getFromServer().await()
            } else {
                documentReference.getFromCache().await()
            }
            if (documentSnapshot.exists()) handleMessageReadAndDelivery(
                caller,
                documentSnapshot,
                customerId,
                vehicleId,
                obcId,
                asn,
                operationType
            )
        }catch (e: FirebaseFirestoreException) {
            Log.e(
                tag,
                "markMessageAsRead INBOX_COLLECTION exception ${e.stackTraceToString()}"
            )
        }
    }

    internal suspend fun markTrashMessageAsRead(caller:String, customerId: String, vehicleId: String, asn: String){
        try {
            val documentPath = getTrashPathDocument(customerId, vehicleId, asn).path
            val collectionPath = getTrashPathCollection(customerId, vehicleId).path
            val documentReference = getTrashPathDocument(customerId, vehicleId, asn)
            val documentSnapshot = if (documentReference.isCacheEmpty()) {
                Log.d(tag, "data not found in cache markMessageAsRead - ${documentReference.path}")
                documentReference.getFromServer().await()
            } else {
                documentReference.getFromCache().await()
            }
            if (documentSnapshot.exists()) documentSnapshot.let {
                val payload = (it.get(PAYLOAD) as HashMap<String, Any>)
                updateReadOrDelivered(caller, payload, IS_READ, documentPath, collectionPath, asn)
            }
        }catch (e: FirebaseFirestoreException) {
            Log.e(
                tag,
                "markMessageAsRead TRASH_COLLECTION exception ${e.stackTraceToString()}"
            )
        }
    }

    private suspend fun handleMessageReadAndDelivery(
        caller: String,
        documentSnapshot: DocumentSnapshot,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
    ) {
        val payload = (documentSnapshot.get(PAYLOAD) as HashMap<String, Any>)
        if (!payload.containsKey(IS_READ) && payload.containsKey(IS_DELIVERED)) {
            // Only read has to marked
            markMessageAsReadAndSendInboxMessageConfirmation(
                caller,
                customerId,
                vehicleId,
                obcId,
                asn,
                operationType,
                payload
            )
            return
        }
        if (!payload.containsKey(IS_DELIVERED) && !payload.containsKey(IS_READ)) {
            // Delivery confirmation has to be sent, Delivered and read status has to be marked
            sendFallbackInboxDeliveryConfirmationToPFMAndMarkInboxMessageAsReadAndDelivered(
                caller,
                customerId,
                vehicleId,
                asn,
                obcId,
                operationType,
                payload
            )
        }
    }

    private suspend fun sendFallbackInboxDeliveryConfirmationToPFMAndMarkInboxMessageAsReadAndDelivered(
        caller: String,
        customerId: String,
        vehicleId: String,
        asn: String,
        obcId: String,
        operationType: String,
        payload: HashMap<String, Any>
    ) {
        // FALLBACK: The opened message has not been previously sent Delivery confirmation to PFM and not been marked Inbox delivered/read,
        // We will be sending these 4 events, in sequence as a fallback
        // i) Delivered confirmation to InboxMessage Confirmation
        // ii) Inbox Acknowledgement IsDelivered to Inbox Collection
        // iii) Read Confirmation to InboxMessage Confirmation
        // iv) Inbox Acknowledgement IsRead to Inbox Collection

        Log.d("$INBOX$ACKNOWLEDGMENT", "FALLBACK : Marking message as Delivered asn : $asn")
        val documentPath = getInboxPathDocument(customerId, vehicleId, asn).path
        val collectionPath = getInboxPathCollection(customerId, vehicleId).path
        val messageDeliveryAndReadConfirmationScope =
            CoroutineScope(dispatcherProvider.io())
        val markMessageDeliveryJob =
            messageDeliveryAndReadConfirmationScope.launch(CoroutineName(tag)) {
                sendInboxMessageConfirmation(
                    caller,
                    "$INBOX_MSG_CONFIRMATION_COLLECTION/$customerId/$vehicleId/$asn",
                    asn,
                    obcId,
                    MARK_DELIVERED
                )
                updateReadOrDelivered(
                    caller,
                    payload,
                    IS_DELIVERED,
                    documentPath,
                    collectionPath,
                    asn
                )
            }
        markMessageDeliveryJob.invokeOnCompletion { throwable ->
            if (throwable == null) {
                Log.d(
                    "$INBOX$ACKNOWLEDGMENT",
                    "markMessageDeliveryJob is done for $asn, caller : $caller"
                )
                val markMessageReadJob = messageDeliveryAndReadConfirmationScope.launch {
                    // This explicit delay in Fallback is used for marking "Sent On" status "Report Center" in PFM.
                    // Without this explicit delay, only the "Read On" status will be marked on PFM, leaving "Sent On" status blank.
                    delay(INBOX_COLLECTION_READ_DELAY)
                    markMessageAsReadAndSendInboxMessageConfirmation(
                        caller,
                        customerId,
                        vehicleId,
                        obcId,
                        asn,
                        operationType,
                        payload
                    )
                }
                markMessageReadJob.invokeOnCompletion {
                    Log.d(
                        "$INBOX$ACKNOWLEDGMENT",
                        "markMessageReadJob is completed  for $asn, caller : $caller"
                    )
                    messageDeliveryAndReadConfirmationScope.cancel()
                }
            } else {
                Log.d(
                    "$INBOX$ACKNOWLEDGMENT",
                    "markMessageDeliveryJob was cancelled or failed due to throwable $throwable for $asn, caller: $caller"
                )
                messageDeliveryAndReadConfirmationScope.cancel()
            }
        }
    }

    internal fun markMessageAsReadAndSendInboxMessageConfirmation(
        caller: String,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
        payload: HashMap<String, Any>
    ) {
        val documentPath = getInboxPathDocument(customerId, vehicleId, asn).path
        val collectionPath = getInboxPathCollection(customerId, vehicleId).path
        sendInboxMessageConfirmation(
            caller,
            "$INBOX_MSG_CONFIRMATION_COLLECTION/$customerId/$vehicleId/$asn",
            asn,
            obcId,
            operationType
        )
        updateReadOrDelivered(caller, payload, IS_READ, documentPath, collectionPath, asn)
    }

    override suspend fun isMessageAlreadyRead(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        callSource: String): Boolean {
        if (customerId.isEmpty() or vehicleId.isEmpty() or obcId.isEmpty() or asn.isEmpty() || asn == "0") {
            Log.d(callSource, "invalid path for data access. cid:$customerId truckNum:$vehicleId obcId:$obcId asn:$asn")
            return false
        }
        return when (callSource) {
            INBOX_COLLECTION -> {
                try {
                    val documentReference = getInboxPathDocument(customerId, vehicleId, asn)
                    val documentSnapshot = if (documentReference.isCacheEmpty()) {
                        Log.d(tag, "data not found in cache isMessageAlreadyRead - ${documentReference.path}")
                        documentReference.getFromServer().await()
                    } else {
                        documentReference.getFromCache().await()
                    }
                    if (documentSnapshot.exists()) {
                        val payload = (documentSnapshot.get(PAYLOAD) as HashMap<String, Any>)
                        payload.containsKey(IS_READ)
                    }
                }catch (e: FirebaseFirestoreException){
                    Log.e(tag, "isMessageAlreadyRead inside INBOX_COLLECTION exception ${e.stackTraceToString()}")
                }
                false
            }
            TRASH_COLLECTION -> {
                try {
                    val trashDocumentReference = getTrashPathDocument(customerId, vehicleId, asn)
                    val trashDocumentSnapshot = if (trashDocumentReference.isCacheEmpty()) {
                        Log.d(tag, "data not found in cache isMessageAlreadyRead - ${trashDocumentReference.path}")
                        trashDocumentReference.getFromServer().await()
                    } else {
                        trashDocumentReference.getFromCache().await()
                    }
                    if (trashDocumentSnapshot.exists()) {
                        val payload = (trashDocumentSnapshot.get(PAYLOAD) as HashMap<String, Any>)
                        payload.containsKey(IS_READ)
                    }
                }catch (e: FirebaseFirestoreException){
                    Log.e(tag, "isMessageAlreadyRead inside INBOX_COLLECTION exception ${e.stackTraceToString()}")
                }
                false

            }
            else -> false
        }
    }

    override suspend fun markMessageAsDeleted(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String
    ) {
        return updateDeletedMessage(
            getInboxPathCollection(customerId, vehicleId).path,
            getTrashPathCollection(customerId, vehicleId).path,
            asn,operationType
        )
    }

    private suspend fun updateDeletedMessage(
        documentPath: String,
        trashCollectionPath: String,
        asn: String,operationType: String
    ) {
        Log.d("$INBOX$MESSAGE_DELETE$operationType", asn)
        val trashRef = firebaseFirestore.collection(trashCollectionPath).document(asn)
        val inboxRef = firebaseFirestore.collection(documentPath).document(asn)
        val batch = firebaseFirestore.batch()
        try {
            inboxRef.get().await().let {
                if (it.data.isNullOrEmpty().not()) {
                    val payload = it.data as HashMap<String, Any>
                    payload[ROW_DATE] = Timestamp(Date())
                    batch[trashRef] = payload
                    batch.delete(inboxRef)
                    batch.commit().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("$INBOX$MESSAGE_DELETE$REPO", asn)
                            emitDeletedMessageASN(payload[ASN_INBOX].toString())
                        } else {
                            Log.d("$INBOX$MESSAGE_DELETE$REPO", "${task.exception}")
                            emitDeletedMessageASN(EMPTY_STRING)
                        }
                    }
                    emitDeletedMessageASN(asn) //In offline there will be no complete or failure callback from firestore, so returning asn from here since it would've deleted and registered in SDK
                } else {
                    emitDeletedMessageASN(EMPTY_STRING)
                }
            }
        } catch (e: Exception) {
            Log.e(
                "$INBOX$MESSAGE_DELETE",
                "ExceptionDeletedMessage $documentPath - $trashCollectionPath - $asn ${e.message}"
            )
            emitDeletedMessageASN(EMPTY_STRING)
        }
    }

    private fun emitDeletedMessageASN(asn: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _deletedInboxMessageASNFlow.emit(asn)
            if(this.isActive) this.cancel()
        }
    }

    internal fun sendInboxMessageConfirmation(
        caller: String,
        messageConfirmationDocumentPath: String,
        asn: String,
        obcId: String,
        status: String
    ) {
        Log.d("$INBOX$ACKNOWLEDGMENT","Setting Message Confirmation for $asn to $status Caller : $caller")
        HashMap<String, Any>().apply {
            this[ASN] = asn
            this[DSN] = obcId.toInt()
            this[CONFIRMATION_DATE_TIME] =
                getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
            if (status == MARK_READ) this[STATUS] = CONFIRMED
            else if (status == MARK_DELIVERED) this[STATUS] = DELIVERED
            val documentReference =
                firebaseFirestore.document(messageConfirmationDocumentPath)
            documentReference.set(this)
                .addOnSuccessListener {
                    Log.n(
                        "$INBOX$ACKNOWLEDGMENT",
                        "inbox msg confirmed",
                        throwable = null,
                        "asn" to asn,
                        "status" to status
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(
                        "$INBOX$ACKNOWLEDGMENT",
                        "Error writing document to $messageConfirmationDocumentPath Error:${e.message}"
                    )
                }
        }
    }

    override suspend fun markAllTheMessagesAsDeleted(
        customerId: String,
        vehicleId: String,
        buildEnvironment: BuildEnvironment, token: String?, appCheckToken : String
    )  {
        if (customerId.isEmpty() || vehicleId.isEmpty() || token == null || appCheckToken.isEmpty() || appModuleCommunicator.getAppFlavor()
                .isNull()
        ) {
            _inboxDeleteAllMessagesResponse.emit(
                CollectionDeleteResponse(
                    false,
                    "Cid $customerId Vehicle $vehicleId either token ${token.isNull()} or appCheckToken ${appCheckToken.isNull()} or app flavor is null ${
                        appModuleCommunicator.getAppFlavor().isNull()
                    }"
                )
            )
            return
        }

        try {
            val deleteResponse =
                CollectionDeleteApiClient.createApi<CollectionDeleteApi>(buildEnvironment, token, appCheckToken)
                    .deleteCollection(getInboxPathCollection(customerId, vehicleId).path)
            Log.d(
                "$INBOX$MESSAGE_DELETE",
                "DeletingAllMessages $deleteResponse"
            )
            _inboxDeleteAllMessagesResponse.emit(deleteResponse)
        } catch (e: Exception) {
            Log.e(
                "$INBOX$MESSAGE_DELETE",
                "exceptionDeletingAllMessages ${e.message}"
            )
            _inboxDeleteAllMessagesResponse.emit(CollectionDeleteResponse(false, e.message?:"exceptionDeleteAllMessages"))
        }
    }


    internal fun updateReadOrDelivered(
        caller: String,
        payload: HashMap<String, Any>,
        operationFlag: String,
        documentPath: String,
        collectionPath: String,
        asn: String
    ) {
        if(payload[operationFlag].isNull() || payload[operationFlag] == false) {
            Log.d("$INBOX$ACKNOWLEDGMENT", "Setting $operationFlag to true for $asn Caller: $caller ")
            val markMessageAsDeliveredOrReadTask = firebaseFirestore.collection(collectionPath).document(asn).let {
                if (operationFlag == IS_DELIVERED) {
                    it.update(FieldPath.of(PAYLOAD, IS_DELIVERED), true)
                } else {
                    it.update(FieldPath.of(PAYLOAD, IS_READ), true)
                }
            }
            markMessageAsDeliveredOrReadTask.addOnSuccessListener {
                Log.d(
                    "$INBOX$ACKNOWLEDGMENT",
                    "WrittenMessageACK: $documentPath $operationFlag"
                )
            }.addOnFailureListener { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    return@addOnFailureListener
                }
                Log.e(
                    "$INBOX$ACKNOWLEDGMENT",
                    "FailedWritingMessageConfirmation: $documentPath $operationFlag error:${e.message}"
                )
            }
        }
    }

    //Gets the latest recipient from server 1st, if server not available then fetches data from the cache
    override suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): HashMap<String, Any> =
        try {
            val recipientMap: HashMap<String, Any> = LinkedHashMap()
            val documentReference =
                getFormListCollectionReference(customerId.toString()).document(formID.toString())
            
            val formDefDocument = documentReference.get().await()

            formDefDocument.data?.let {
                recipientMap.putAll(it.getFormRecipients())
            }
            recipientMap
        } catch (firestoreException: FirebaseFirestoreException) {
            Log.e(
                "$INBOX$RECIPIENT",
                "FirebaseFirestoreExceptionGetLatestFormRecipients", firestoreException,
                "form id" to formID
            )
            HashMap()
        } catch (e: Exception) {
                Log.e(
                    "$INBOX$RECIPIENT",
                    "ExceptionGetLatestFormRecipients", e,
                    "form id" to formID
                )
                HashMap()
            }

    private suspend fun getFreeFormFormDefDocumentData(formId: Int): Pair<DocumentSnapshot, Boolean> {
        val documentReference = getFreeFormsCollectionReference().document(formId.toString())
        val documentSnapshotTask: Task<DocumentSnapshot>
        val isCacheEmpty = documentReference.isCacheEmpty()
        documentSnapshotTask = try {
            if (isCacheEmpty) {
                documentReference.getFromServer()
            } else {
                documentReference.getFromCache()
            }
        } catch (e: FirebaseFirestoreException) {
            documentReference.getFromCache()
        }
        val documentSnapshot = documentSnapshotTask.await()
        return Pair(documentSnapshot, isCacheEmpty)
    }

    private suspend fun getFreeFormFormFieldSubCollection(
        formId: Int
    ): QuerySnapshot {
        val collRef = getFreeFormsFormFieldCollectionReference(formId)
        val formFieldCollectionQuerySnapshotTask: Task<QuerySnapshot> = try {
            if (collRef.isCacheEmpty()) collRef.getFromServer()
            else collRef.getFromCache()
        } catch (e: FirebaseFirestoreException) {
            collRef.getFromCache()
        }
        return formFieldCollectionQuerySnapshotTask.await()
    }

    private fun getFreeFormsFormFieldCollectionReference(formId: Int): CollectionReference =
        firebaseFirestore.collection(FREE_FORMS_COLLECTION)
            .document(formId.toString())
            .collection(FORM_FIELD_COLLECTION)

    private fun getFreeFormsCollectionReference(): CollectionReference =
        firebaseFirestore.collection(FREE_FORMS_COLLECTION)

    private fun getFormListCollectionReference(customerId: String): CollectionReference =
        firebaseFirestore.collection(FORMS_COLLECTION).document(customerId)
            .collection(FORMS_LIST_COLLECTION)

    private fun getFormsFieldCollectionReference(customerId: String, formId: String) =
        getFormListCollectionReference(customerId)
            .document(formId).collection(FORM_FIELD_COLLECTION)

    private fun getFormFieldFormChoiceCollectionReference(
        customerId: String,
        formField: FormField
    ) = getFormListCollectionReference(customerId)
        .document(formField.formid.toString()).collection(FORM_FIELD_COLLECTION).document(
            formField.qnum.toString()
        ).collection(
            FORM_CHOICES_COLLECTION
        )

    private fun getInboxPathDocument(customerId: String, vehicleId: String, asn: String) : DocumentReference =
        firebaseFirestore
            .collection(INBOX_COLLECTION).document(customerId)
            .collection(vehicleId).document(asn)

    private fun getInboxPathCollection(customerId: String, vehicleId: String) : CollectionReference =
        firebaseFirestore
            .collection(INBOX_COLLECTION).document(customerId)
            .collection(vehicleId)

    private fun getTrashPathCollection(customerId: String, vehicleId: String) =
        firebaseFirestore.collection(TRASH_COLLECTION).document(customerId)
            .collection(vehicleId)

    private fun getTrashPathDocument(customerId: String, vehicleId: String, asn: String) =
        firebaseFirestore.collection(TRASH_COLLECTION).document(customerId)
            .collection(vehicleId).document(asn)

    override fun getMessagesDeleteAllFlow(): SharedFlow<CollectionDeleteResponse> = _inboxDeleteAllMessagesResponse

    override fun getDeletedInboxMessageASNFlow(): SharedFlow<String> = _deletedInboxMessageASNFlow

    override fun getAppModuleCommunicator(): AppModuleCommunicator = appModuleCommunicator
    override suspend fun getDispatcherFormValuesFromInbox(
        customerId: String,
        vehicleId: String,
        asn: String
    ) : Message {
        try {
            val documentReference =
                getInboxPathDocument(customerId = customerId, vehicleId = vehicleId, asn = asn)
            val inboxDocReference =
                if (documentReference.isCacheEmpty()) {
                    documentReference.getFromServer().await()
                }else {
                    documentReference.getFromCache().await()
                }
            Log.d(
                INBOX_MESSAGE_DEF_VALUES,
                "data not found in cache getFormDefaultValues(path: ${documentReference.path})"
            )
            inboxDocReference.let { inboxDocReferenceData ->
                val messagePair = parseFireStoreMessageDocument(inboxDocReferenceData)
                return messagePair.first
            }
        } catch (firestoreException: FirebaseFirestoreException) {
            Log.e(INBOX_MESSAGE_DEF_VALUES, "FirebaseFirestoreException in getDispatcherFormValuesFromInbox")
        }catch (e: CancellationException) {
            //Ignored

        } catch (e: Exception) {
            Log.e(INBOX_MESSAGE_DEF_VALUES, "exception in getFormDefaultValues ${e.stackTraceToString()}")
        }
        return Message()
    }

    override suspend fun deleteSelectedMessageInTrash(
        customerId: String,
        vehicleId: String,
        asn: String,
        caller: String
    ) {
        try {
            Log.d("$TRASH$MESSAGE_DELETE$caller", asn)
            getTrashPathDocument(customerId, vehicleId, asn)
                .get()
                .await().let { docSnapshot ->
                    val deleteTask = docSnapshot.reference.delete()
                    deleteTask.addOnFailureListener { error ->
                        Log.e(
                            "$TRASH$MESSAGE_DELETE",
                            "deleteTrashMessage from $caller failed. Document Path:${docSnapshot.reference.path}",
                            error
                        )
                    }
                    deleteTask.addOnSuccessListener { _ ->
                        Log.i(
                            "$TRASH$MESSAGE_DELETE",
                            "deleteTrashMessage from $caller success. Document Path:${docSnapshot.reference.path}"
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e("$TRASH$MESSAGE_DELETE", "error deleting the message in trash from $caller. ${e.message}")
        }
    }

    override suspend fun deleteAllMessageInTrash(
        customerId: String,
        vehicleId: String,
        buildEnvironment: BuildEnvironment,
        token: String,
        appCheckToken: String
    ): CollectionDeleteResponse =
        try {
            CollectionDeleteApiClient.createApi<CollectionDeleteApi>(
                buildEnvironment,
                token,
                appCheckToken
            )
                .deleteCollection(getTrashPathCollection(customerId, vehicleId).path)
        } catch (e: Exception) {
            Log.e(
                "$TRASH$MESSAGE_DELETE",
                "error response from collection delete http cloud function for Trash: ${e.message}"
            )
            CollectionDeleteResponse(false, e.message.orEmpty())
        }
}