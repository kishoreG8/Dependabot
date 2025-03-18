package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.trimble.ttm.commons.logger.ACKNOWLEDGMENT
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ASN
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.FirebaseUtils.sendSuccessResultIfOfflineForDocumentWrite
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageConfirmation
import com.trimble.ttm.formlibrary.utils.CONFIRMATION_DATE_TIME
import com.trimble.ttm.formlibrary.utils.DELIVERED
import com.trimble.ttm.formlibrary.utils.DSN
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_MSG_CONFIRMATION_COLLECTION
import com.trimble.ttm.formlibrary.utils.IS_DELIVERED
import com.trimble.ttm.formlibrary.utils.MARK_DELIVERED
import com.trimble.ttm.formlibrary.utils.PAYLOAD
import com.trimble.ttm.formlibrary.utils.STATUS
import com.trimble.ttm.formlibrary.utils.isNull
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

class MessageConfirmationRepoImpl(
    private val appModuleCommunicator: AppModuleCommunicator,
    private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) :
    MessageConfirmationRepo {

    private val tag = "MessageConfirmationRepo"

    override suspend fun markMessageAsDelivered(
        caller: String,
        asn: String,
        message: Message?
    ) {
        val customerId = appModuleCommunicator.doGetCid()
        val vehicleId = appModuleCommunicator.doGetTruckNumber()
        val obcId = appModuleCommunicator.doGetObcId()
        try {
            val documentPath = getInboxPathDocument(customerId, vehicleId, asn).path
            val collectionPath = getInboxPathCollection(customerId, vehicleId).path
            sendInboxMessageConfirmation(
                caller,
                "$INBOX_MSG_CONFIRMATION_COLLECTION/$customerId/$vehicleId/$asn",
                asn,
                obcId
            )
            if (message == null) {
                // Delivery is marked from workflowAppNotification
                getInboxPathDocument(customerId, vehicleId, asn).get().await()?.let {
                    val payload = (it.get(PAYLOAD) as HashMap<String, Any>)
                    updateMessageAsDelivered(
                        caller,
                        payload,
                        documentPath,
                        collectionPath,
                        asn
                    )
                }
            } else {
                // Delivery is marked from fallback in Inbox Fragment
                updateMessageAsDelivered(
                    caller,
                    hashMapOf(IS_DELIVERED to false), // message.isDelivered will always be false
                    documentPath,
                    collectionPath,
                    asn
                )
            }
        } catch (e: Exception) {
            /* FirebaseFirestore Exception may occur, since we do a Firestore read before Firestore write
            * Those undelivered message will be marked as delivered when driver navigates to Inbox list screen | TTS widget */
            if (e !is FirebaseFirestoreException) {
                Log.e(
                    "$INBOX$ACKNOWLEDGMENT",
                    "Exception While Marking Message as Delivered: ${e.message}", e, "asn" to asn
                )
            }
        }
    }

    override fun sendEdvirMessageViewedConfirmation(
        messageConfirmationDocPath: String,
        messageConfirmation: MessageConfirmation, inspectionTypeForLogging: String
    ) {
        val documentReference = firebaseFirestore.document(messageConfirmationDocPath)
        documentReference.set(messageConfirmation).addOnCompleteListener { confirmationWriteTask ->
            if (confirmationWriteTask.isSuccessful) {
                Log.d(
                    tag,
                    "EDVIR settings acknowledged for ${inspectionTypeForLogging}.DSN ${messageConfirmation.dsn}."
                )
            } else {
                Log.e(
                    tag,
                    "Failed to acknowledge EDVIR settings of ${inspectionTypeForLogging}.DSN ${messageConfirmation.dsn}. error ${confirmationWriteTask.exception?.stackTraceToString()}."
                )
            }
        }
        documentReference.sendSuccessResultIfOfflineForDocumentWrite(
            tag,
            "messageConfirmationDocPath : $messageConfirmationDocPath"
        )
    }

    private fun getInboxPathDocument(customerId: String, vehicleId: String, asn: String) =
        firebaseFirestore
            .collection(INBOX_COLLECTION).document(customerId)
            .collection(vehicleId).document(asn)

    private fun getInboxPathCollection(customerId: String, vehicleId: String) =
        firebaseFirestore
            .collection(INBOX_COLLECTION).document(customerId)
            .collection(vehicleId)

    private fun sendInboxMessageConfirmation(
        caller: String,
        messageConfirmationDocumentPath: String,
        asn: String,
        obcId: String
    ) {
        Log.d(
            "$INBOX$ACKNOWLEDGMENT",
            "Setting Message Confirmation for $asn to $MARK_DELIVERED Caller : $caller"
        )
        HashMap<String, Any>().apply {
            this[ASN] = asn
            this[DSN] = obcId.toInt()
            this[CONFIRMATION_DATE_TIME] =
                getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
            this[STATUS] = DELIVERED
            val documentReference =
                firebaseFirestore.document(messageConfirmationDocumentPath)
            documentReference.set(this)
                .addOnSuccessListener {
                    Log.d(
                        "$INBOX$ACKNOWLEDGMENT",
                        "Inbox Message Confirmed",
                        throwable = null,
                        "asn" to asn,
                        "status" to MARK_DELIVERED
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

    private fun updateMessageAsDelivered(
        caller: String,
        payload: HashMap<String, Any>,
        documentPath: String,
        collectionPath: String,
        asn: String
    ) {
        if (payload[IS_DELIVERED] == false || payload[IS_DELIVERED].isNull()) {
            Log.d("$INBOX$ACKNOWLEDGMENT", "Setting IS_DELIVERED to true for $asn Caller: $caller ")
            firebaseFirestore.collection(collectionPath).document(asn).let {
                it.update(FieldPath.of(PAYLOAD, IS_DELIVERED), true).addOnSuccessListener {
                    Log.d(
                        "$INBOX$ACKNOWLEDGMENT",
                        "WrittenMessageACK: $documentPath IS_DELIVERED"
                    )
                }.addOnFailureListener { e ->
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        return@addOnFailureListener
                    }
                    Log.e(
                        "$INBOX$ACKNOWLEDGMENT",
                        "FailedWritingMessageConfirmation: $documentPath IS_DELIVERED error:${e.message}"
                    )
                }
            }
        }
    }
}