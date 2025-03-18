package com.trimble.ttm.commons.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.model.FCMTokenData
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.ERROR_TAG
import com.trimble.ttm.commons.utils.FirebaseUtils.sendSuccessResultIfOfflineForDocumentWrite
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import kotlin.coroutines.resume

class FCMDeviceTokenRepositoryImpl(private val appModuleCommunicator: AppModuleCommunicator) : FCMDeviceTokenRepository,
    KoinComponent {
    private val fcmDeviceTokenHandler: FCMDeviceTokenHandler by inject()

    override suspend fun storeFCMTokenInFirestore(path: String, token: String) = suspendCancellableCoroutine<Boolean> { continuation ->
        try {
            val fcmTokenData = FCMTokenData(token, Timestamp(Date()))
            val documentReference = FirebaseFirestore.getInstance().document(path)
            documentReference.set(fcmTokenData)
                .addOnSuccessListener {
                    Log.n("$DEVICE_FCM$REPO", "StoredFCMToken: $path", null, "token" to fcmTokenData.value)
                    if(continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener {
                    Log.e("$DEVICE_FCM$REPO", "FailedStoringFCMToken: $path error ${it.message}")
                    if(continuation.isActive) continuation.resume(false)
                }
            documentReference.sendSuccessResultIfOfflineForDocumentWrite(continuation)
        } catch (e: Exception) {
            Log.e("$DEVICE_FCM$REPO", "Exception while Storing FCM Token at path: $path error: ${e.message}")
            if (continuation.isActive) continuation.resume(false)
        }
    }

    override suspend fun deleteFCMTokenFromFirestore(path: String) =
        suspendCancellableCoroutine<Boolean> { continuation ->
            val firebaseFirestore = FirebaseFirestore.getInstance()
            firebaseFirestore.collection(path).get()
                .addOnSuccessListener { querySnapshot ->
                    Log.n("$DEVICE_FCM$REPO","Fetching documents to delete old FCM tokens: isQuerySnapshotEmpty ${querySnapshot.isEmpty}")
                    if (querySnapshot.documents.isEmpty() && continuation.isActive) continuation.resume(
                        true
                    )
                    for (document in querySnapshot) {
                        // Delete each document
                        deleteFCMTokenDocuments(firebaseFirestore, path, document)
                    }
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(
                        "$DEVICE_FCM$REPO",
                        "Error getting old FCM token documents for collection: ${path}",
                        e
                    )
                    if (continuation.isActive) continuation.resume(false)
                }
        }

    override suspend fun fetchFCMTokenFromFirestore(path: String) =
        suspendCancellableCoroutine<String> { continuation ->
            val firebaseFirestore = FirebaseFirestore.getInstance()
            firebaseFirestore.collection(path).get()
                .addOnSuccessListener { querySnapshot ->
                    Log.i("$DEVICE_FCM$REPO","Fetching FCM document. isQuerySnapshotEmpty : ${querySnapshot.isEmpty}")
                    if (querySnapshot.documents.isEmpty() && continuation.isActive) continuation.resume(EMPTY_STRING)
                    val fcmTokenDocuments = querySnapshot.documents
                    if (isFCMTokenDocumentEmpty(fcmTokenDocuments, continuation)) return@addOnSuccessListener
                    if (continuation.isActive) continuation.resume(fcmTokenDocuments[0].id)
                }
                .addOnFailureListener { e ->
                    Log.e(
                        "$DEVICE_FCM$REPO",
                        "Error getting old FCM token document for collection: $path",
                        e
                    )
                    if (continuation.isActive) continuation.resume(ERROR_TAG)
                }
        }

    override suspend fun fetchFCMToken(): String =
        fcmDeviceTokenHandler.fetchDeviceSpecificFcmTokenFromFirebase()

    override fun getAppModuleCommunicator(): AppModuleCommunicator = appModuleCommunicator

    private fun deleteFCMTokenDocuments(
        firebaseFirestore: FirebaseFirestore,
        path: String,
        document: QueryDocumentSnapshot
    ) {
        firebaseFirestore.collection(path)
            .document(document.id)
            .delete()
            .addOnSuccessListener {
                // Document successfully deleted
                Log.n(
                    "$DEVICE_FCM$REPO",
                    "FCM Document ${document.id} deleted successfully. Collection path: $path",
                    null,
                    "documentId" to document.id
                )
            }
            .addOnFailureListener { e ->
                Log.e(
                    "$DEVICE_FCM$REPO",
                    "Error deleting FCM document ${document.id}, Collection path: $path",
                    e
                )
            }
    }

    private fun isFCMTokenDocumentEmpty(
        fcmTokenDocuments: List<DocumentSnapshot>,
        continuation: CancellableContinuation<String>
    ): Boolean {
        if (fcmTokenDocuments.isEmpty()) {
            Log.e(
                "$DEVICE_FCM$REPO",
                "No FCM token found"
            )
            if (continuation.isActive) continuation.resume(ERROR_TAG)
            return true
        }
        return false
    }
}