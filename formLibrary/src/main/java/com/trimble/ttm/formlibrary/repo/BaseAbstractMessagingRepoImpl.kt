package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FreeText
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.SENT_KEY
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.Utils
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.http.CollectionDeleteApi
import com.trimble.ttm.formlibrary.http.CollectionDeleteApiClient
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.Message.Companion.getWithDateRemovedAndFormatted
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponseFromDB
import com.trimble.ttm.formlibrary.model.MessagePayload
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.utils.ACTIVE
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.DISPATCH_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.EMAIL
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FORM_FIELD
import com.trimble.ttm.formlibrary.utils.FORM_ID
import com.trimble.ttm.formlibrary.utils.FORM_NAME
import com.trimble.ttm.formlibrary.utils.FQUESTION
import com.trimble.ttm.formlibrary.utils.FREE_FORM_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FULL_DATE_TIME_FORMAT
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.MESSAGE_CONTENT
import com.trimble.ttm.formlibrary.utils.NEWLINE
import com.trimble.ttm.formlibrary.utils.NO_CONTACTS
import com.trimble.ttm.formlibrary.utils.PAYLOAD
import com.trimble.ttm.formlibrary.utils.PFM_MESSAGE_TIMESTAMP_FORMAT
import com.trimble.ttm.formlibrary.utils.PFM_TIMESTAMP_LENGTH
import com.trimble.ttm.formlibrary.utils.PFM_TIMESTAMP_SETTING_REGEX
import com.trimble.ttm.formlibrary.utils.PRE_DEFINED_RECIPS
import com.trimble.ttm.formlibrary.utils.READABLE_DATE_FORMAT
import com.trimble.ttm.formlibrary.utils.READABLE_DATE_TIME_FORMAT
import com.trimble.ttm.formlibrary.utils.REPLY_ACTION_TYPE
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_ID
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_NAME
import com.trimble.ttm.formlibrary.utils.ROW_DATE
import com.trimble.ttm.formlibrary.utils.TRASH_COLLECTION
import com.trimble.ttm.formlibrary.utils.UID_FIELD_KEY
import com.trimble.ttm.formlibrary.utils.USERNAME
import com.trimble.ttm.formlibrary.utils.getDateFormatted
import com.trimble.ttm.formlibrary.utils.toSafeInt
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.AbstractMap
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Suppress("UNCHECKED_CAST")
abstract class BaseAbstractMessagingRepoImpl(private val tag: String) {
    internal val gsonInstance = Gson()

    internal fun parseFireStoreMessageDocument(doc: DocumentSnapshot): Pair<Message, Long> =
        try {
            val messagePayload = Gson().fromJson(
                Gson().toJson(doc[PAYLOAD]),
                MessagePayload::class.java
            )
            val rowDate = doc.getTimestamp(ROW_DATE)?.toDate()?.time ?: 0L
            val message = parseMessagePayload(messagePayload, doc.reference.path)
            message.rowDate = rowDate
            Pair(
                message,
                rowDate
            )
        } catch (e: Exception) {
            Log.e(
                tag,
                "error parsing the message document for inbox/trash. Document path: ${doc.reference.path} ${e.message}"
            )
            Pair(Message(), 0L)
        }

    internal fun parseFireStoreMessageResponseDocument(doc: DocumentSnapshot, caller: String): Pair<MessageFormResponse, Long> =
        try {
            doc.data?.let { dataMap ->
                Utils.toJsonString(dataMap)
                    ?.let {
                        val messageResponsePayload = Utils.fromJsonString<MessageFormResponseFromDB>(it) ?: MessageFormResponseFromDB()
                        val messageResponse = parseMessageResponsePayload(messageResponsePayload, caller)
                        Pair(messageResponse, messageResponsePayload.createdAt)
                    } ?: run {
                    Log.e(tag,"Invalid DataMap while parsing")
                    Pair(MessageFormResponse(), -1L)
                }
            } ?: run {
                Log.e(tag,"Empty Firestore document")
                Pair(MessageFormResponse(), -1L)
            }

        } catch (e: Exception) {
            Log.e(
                tag,
                "error parsing the message document for draft/sent. Document path: ${doc.reference.path} ${e.message}"
            )
            Pair(MessageFormResponse(), -1L)
        }

    internal fun parseMessagePayload(
        messagePayload: MessagePayload,
        documentPath: String
    ): Message =
        try {
            //Reads only the messages which are not deleted
            messagePayload.message.let {
                val messageContent = it[MESSAGE_CONTENT]?.toString() ?: ""
                val formId = it[FORM_ID]?.toString() ?: ""
                val formClass = it[FORM_CLASS]?.toString() ?: ""
                val formName = it[FORM_NAME]?.toString() ?: ""
                val replyFormId = it[REPLY_FORM_ID]?.toString() ?: ""
                val replyFormClass = it[REPLY_FORM_CLASS]?.toString() ?: ""
                val replyFormName = it[REPLY_FORM_NAME]?.toString() ?: ""
                val replyActionType = it[REPLY_ACTION_TYPE]?.toString() ?: ""
                val subject = messagePayload.subject.run {
                    this.ifEmpty { formName }
                }
                val message = Message(
                    userName = messagePayload.userName.ifEmpty { messagePayload.emailAddr },
                    subject = subject,
                    formId = formId,
                    formClass = formClass,
                    formName = formName,
                    replyFormId = if(replyFormId.toSafeInt() == 0) formId else replyFormId,
                    replyFormClass = replyFormClass,
                    replyFormName = replyFormName,
                    replyActionType = replyActionType,
                    isDelivered = messagePayload.isDelivered,
                    isRead = messagePayload.isRead,
                    asn = messagePayload.asn,
                    uid = messagePayload.uID
                )
                if (messagePayload.timeCreated.isNotEmpty()) {
                    SimpleDateFormat(FULL_DATE_TIME_FORMAT, Locale.getDefault()).parse(
                        messagePayload.timeCreated
                    )?.let { date ->
                        message.date =
                            SimpleDateFormat(READABLE_DATE_FORMAT, Locale.getDefault()).format(
                                date
                            )
                        message.dateTime =
                            SimpleDateFormat(READABLE_DATE_TIME_FORMAT, Locale.getDefault()).format(
                                date
                            )

                        message.summary = getMessageBody(messageContent, date)
                        message.summaryText = message.summary.getWithDateRemovedAndFormatted()
                        message.timestamp = date.time
                    }
                }
                it[FORM_FIELD]?.let { formFields ->
                    val formFieldMap = formFields as Map<String, Any>
                    formFieldMap[FQUESTION]?.let { formFieldList ->
                        val messageFormFieldList = arrayListOf<MessageFormField>()
                        (formFieldList as ArrayList<LinkedTreeMap<String, String>>).forEach { formFieldMap ->
                            messageFormFieldList.add(
                                Gson().fromJson(
                                    Gson().toJson(formFieldMap),
                                    MessageFormField::class.java
                                )
                            )
                        }
                        message.formFieldList = messageFormFieldList
                    }
                }
                message
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "error parsing the message payload for inbox/trash. Document path: $documentPath ${e.message}"
            )
            Message()
        }

    fun getMessageBody(messageText: String, date: Date): String {
        return if (messageText.isNotEmpty() &&
            PFM_TIMESTAMP_SETTING_REGEX.toRegex().containsMatchIn(messageText)
        ) {
            getTimeStampLocaleFormattedMessage(messageText, date)
        } else messageText
    }


    private fun getTimeStampLocaleFormattedMessage(messageText: String, date: Date): String {
        return if (messageText.length == PFM_TIMESTAMP_LENGTH) {
            SimpleDateFormat(PFM_MESSAGE_TIMESTAMP_FORMAT, Locale.getDefault()).format(date)
                .toString()
                .plus(SPACE)
                .plus(TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT))
                .plus(NEWLINE)
                .plus(messageText.substring(11))
        } else {
            SimpleDateFormat(PFM_MESSAGE_TIMESTAMP_FORMAT, Locale.getDefault()).format(date)
                .toString()
                .plus(SPACE)
                .plus(TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT))
                .plus(NEWLINE)
                .plus(messageText.substring(12))
        }
    }

    internal fun parseMessageResponsePayload(
        messageFormResponsePayload: MessageFormResponseFromDB,
        caller: String
    ): MessageFormResponse = try {
        val users: List<HashMap<String, Any>> = messageFormResponsePayload.recipientUserNames
        val userSet = mutableSetOf<User>()
        users.forEach {
            userSet.add(
                User(
                    uID = (it[UID_FIELD_KEY] as? Double)?.toLong() ?: -1L,
                    username = it[USERNAME]?.toString() ?: EMPTY_STRING,
                    email = it[EMAIL]?.toString() ?: EMPTY_STRING,
                    active = (it[ACTIVE] as? Double)?.toLong() ?: 0L
                )
            )
        }
        MessageFormResponse(
            formName = messageFormResponsePayload.formName,
            formResponseType = messageFormResponsePayload.formResponseType,
            recipientUserNames = userSet.run {
                if (messageFormResponsePayload.hasPredefinedRecipients || (this.isEmpty() && messageFormResponsePayload.formResponseType == DISPATCH_FORM_RESPONSE_TYPE && caller == SENT_KEY)) PRE_DEFINED_RECIPS
                else {
                    return@run if (this.isEmpty()) NO_CONTACTS
                    else this.elementAt(0).username
                }
            },
            recipientUsers = userSet,
            createdOn = messageFormResponsePayload.createdAt.let {
                SimpleDateFormat(READABLE_DATE_FORMAT, Locale.getDefault()).format(
                    Date(it)
                )
            } ?: EMPTY_STRING,
            createdUnixTime = messageFormResponsePayload.createdAt,
            formData = messageFormResponsePayload.formData,
            messageContentIfCFF = getFreeFormText(
                messageFormResponsePayload.formData,
                messageFormResponsePayload.formClass.toSafeInt()
            ),
            formId = messageFormResponsePayload.formId,
            formClass = messageFormResponsePayload.formClass,
            hasPredefinedRecipients = messageFormResponsePayload.hasPredefinedRecipients,
            timestamp = messageFormResponsePayload.createdAt.getDateFormatted(READABLE_DATE_TIME_FORMAT),
            uncompletedDispatchFormPath = messageFormResponsePayload.uncompletedDispatchFormPath,
            dispatchFormSavePath = messageFormResponsePayload.dispatchFormSavePath
        )
    } catch (e: Exception) {
        Log.e(
            tag,
            "error parsing the messageResponsePayload for draft/sent.${e.message}"
        )
        MessageFormResponse()
    }

    internal fun getFreeFormText(formResponse: FormResponse, formClass: Int): String {
        var freeFormText = EMPTY_STRING
        formResponse.fieldData.forEach { fieldData ->
            if (formResponse.fieldData.size == 1 &&
                formClass == FREE_FORM_FORM_CLASS
            ) {
                val field = fieldData as AbstractMap<String, Any>
                for ((key, value) in field) {
                    when (key) {
                        FREETEXT_KEY -> {
                            val data = gsonInstance.fromJson(value as String, FreeText::class.java)
                            freeFormText = data.text
                        }
                    }
                }
            }
        }
        return freeFormText
    }


    internal suspend fun deleteDraftOrSentMessage(
        customerId: String,
        vehicleId: String,
        createdTime: Long,
        isForDraft: Boolean
    ) {
        fun deleteTaskRunner (docSnapshot: DocumentSnapshot) {
            val deleteTask = docSnapshot.reference.delete()
            deleteTask.addOnFailureListener { error ->
                Log.e(tag, "deleteDraftOrSentMessage failed. Document Path:${docSnapshot.reference.path}", error)
            }
            deleteTask.addOnSuccessListener { _ ->
                Log.i(tag, "deleteDraftOrSentMessage success. Document Path:${docSnapshot.reference.path}")
            }
        }

        if (isForDraft) {
            getDraftedResponsePath(customerId, vehicleId).whereEqualTo(CREATED_AT, createdTime)
                .get()
                .await().documents.forEach {
                    deleteTaskRunner(it)
                }
        } else {
            getSentResponsePath(customerId, vehicleId).whereEqualTo(CREATED_AT, createdTime)
                .get()
                .await().documents.forEach {
                    deleteTaskRunner(it)
                }
        }
    }

    internal suspend fun deleteAllMessagesOfDraftOrSent(
        customerId: String,
        vehicleId: String, isForDraft: Boolean,
        buildEnvironment: BuildEnvironment, token: String, appCheckToken : String
    ): CollectionDeleteResponse = try {
        if (isForDraft)
            CollectionDeleteApiClient.createApi<CollectionDeleteApi>(buildEnvironment, token, appCheckToken)
                .deleteCollection(getDraftedResponsePath(customerId, vehicleId).path)
        else
            CollectionDeleteApiClient.createApi<CollectionDeleteApi>(buildEnvironment, token, appCheckToken)
                .deleteCollection(getSentResponsePath(customerId, vehicleId).path)
    } catch (e: Exception) {
        Log.e(
            tag,
            "error response from collection delete http cloud function for draft/sent: ${e.message}"
        )
        CollectionDeleteResponse(false, e.message.orEmpty())
    }

    internal fun getInboxPath(customerId: String, vehicleId: String) =
        FirebaseFirestore.getInstance()
            .collection(INBOX_COLLECTION).document(customerId)
            .collection(vehicleId)

    internal fun getTrashPath(customerId: String, vehicleId: String) =
        FirebaseFirestore.getInstance()
            .collection(TRASH_COLLECTION).document(customerId)
            .collection(vehicleId)

    internal fun getDraftedResponsePath(customerId: String, vehicleId: String) =
        FirebaseFirestore.getInstance()
            .collection(INBOX_FORM_DRAFT_RESPONSE_COLLECTION).document(customerId)
            .collection(vehicleId)

    internal fun getSentResponsePath(customerId: String, vehicleId: String) =
        FirebaseFirestore.getInstance()
            .collection(INBOX_FORM_RESPONSE_COLLECTION).document(customerId)
            .collection(vehicleId)

}