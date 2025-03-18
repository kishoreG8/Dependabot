package com.trimble.ttm.commons.utils

import com.google.firebase.firestore.DocumentReference
import com.trimble.ttm.commons.logger.Log
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume

object FirebaseUtils {

    /**
     * While the device is in offline we wont get success or failure listener .
     * So getting the meta data to send the result.
     */
    fun DocumentReference.sendSuccessResultIfOfflineForDocumentWrite(
        continuation: CancellableContinuation<Boolean>
    ) {
        this.get().addOnSuccessListener {
            if (it.metadata.isFromCache && continuation.isActive) continuation.resume(true)
        }
    }

    fun DocumentReference.sendSuccessResultIfOfflineForDocumentWrite(
        caller: String,
        data: String
    ) {
        this.get().addOnSuccessListener {
            if (it.metadata.isFromCache) {
                Log.i(
                    caller,
                    "Device is offline write for $data on path ${this.path} could have been succeeded please make sure on that"
                )
            }
        }
    }
}