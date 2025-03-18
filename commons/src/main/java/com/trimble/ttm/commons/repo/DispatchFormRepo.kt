package com.trimble.ttm.commons.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SnapshotListenOptions
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_FORM_CRUD
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.ext.doContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar

interface DispatchFormRepo {
    suspend fun saveDispatchFormResponse(
        path: String, data: HashMap<String, Any>, caller: String
    ): Boolean

}

class DispatchFormRepoImpl(private val fireStore: FirebaseFirestore = FirebaseFirestore.getInstance()): DispatchFormRepo {
    override suspend fun saveDispatchFormResponse(
        path: String, data: HashMap<String, Any>, caller: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        var listenerRegistration:ListenerRegistration? = null
        try {
            val options = SnapshotListenOptions.Builder()
                .setMetadataChanges(MetadataChanges.INCLUDE)
                .setSource(ListenSource.CACHE)
                .build()
            val curTimeUtcFormattedStr = DateUtil.getUTCFormattedDate(Calendar.getInstance().time)
            val documentRef = fireStore.collection(path)
                .document(curTimeUtcFormattedStr)
            documentRef.set(data)
            listenerRegistration = documentRef.addSnapshotListener(options){ snapshot, e ->
                if (e != null) {
                    Log.e("$TRIP_FORM_CRUD$caller", "Listen failed.", e)
                    doContinuation(continuation,false,listenerRegistration)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    Log.d("$TRIP_FORM_CRUD$caller", "Written document $path")
                    doContinuation(continuation,true,listenerRegistration)
                } else {
                    Log.d("$TRIP_FORM_CRUD$caller", "Error writing document $path")
                    doContinuation(continuation,false,listenerRegistration)
                }
            }
        } catch (e: Exception) {
            Log.e("$TRIP_FORM_CRUD$caller", "Error writing document:${e.message} path:$path", e)
            doContinuation(continuation,false)
        }
    }
}