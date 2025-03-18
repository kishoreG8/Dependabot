package com.trimble.ttm.formlibrary.utils.ext

import com.google.firebase.firestore.*
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.utils.isNotNull
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val localCache = "local cache"
private const val server = "server"
private const val nullSnapshot = "Snapshot is null."
private const val TAG = "FireStoreListenerExt"

fun Query.toFlow(): Flow<Any?> = callbackFlow {
    addSnapshotListener(this).also {
        awaitClose { it.remove() }
    }
}

fun DocumentReference.toFlow(): Flow<Any?> = callbackFlow {
    addSnapshotListener(this).also {
        awaitClose { it.remove() }
    }
}

fun DocumentReference.toDocSnapshotFlow(): Flow<Any?> = callbackFlow {
    addSnapshotListener(this).also {
        awaitClose { it.remove() }
    }
}

fun CollectionReference.toQuerySnapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    addSnapshotListener(this).also {
        awaitClose { it.remove() }
    }
}

fun Query.toQuerySnapshotFlow(): Flow<Any?> = callbackFlow {
    addSnapshotListener(this).also {
        awaitClose { it.remove() }
    }
}

private fun <T> Query.addSnapshotListener(producerScope: ProducerScope<T>) : ListenerRegistration {
    return addSnapshotListener { querySnapshot, firebaseFireStoreException ->
        when {
            firebaseFireStoreException.isNotNull() ->
                firebaseFireStoreException?.sendToConsumer(producerScope)
            querySnapshot.isNotNull() ->
            querySnapshot?.sendToConsumer(producerScope)
            else -> sendToConsumer(producerScope)
        }
    }
}

fun <T> DocumentReference.addSnapshotListener(producerScope: ProducerScope<T>) : ListenerRegistration {
    return addSnapshotListener { documentSnapshot, firebaseFireStoreException ->
        when {
            firebaseFireStoreException.isNotNull() ->
                firebaseFireStoreException?.sendToConsumer(producerScope)
            documentSnapshot.isNotNull() ->
            documentSnapshot?.sendToConsumer(producerScope)
            else -> sendToConsumer(producerScope)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.sendToConsumer(producerScope: ProducerScope<T>) {
    when(this) {
        is QuerySnapshot ->
            (producerScope as ProducerScope<QuerySnapshot>).trySend(this).also {
                logPath(it.isSuccess)
            }
        is DocumentSnapshot ->
            (producerScope as ProducerScope<DocumentSnapshot>).trySend(this).also {
                logPath(it.isSuccess)
            }
        is FirebaseFirestoreException ->
            (producerScope as ProducerScope<FirebaseFirestoreException>).trySend(this)
        else ->
            (producerScope as ProducerScope<Exception>).trySend(Exception(nullSnapshot))
    }
}

private fun QuerySnapshot.logPath(isSentSuccess: Boolean) {
    val source = if (metadata.isFromCache) localCache else server
    try {
        if (isSentSuccess.not()) Log.e(TAG, "Failed to Send to the consumer. Path: ${documents[0].reference.path}")
        else Log.i(TAG, "Collection Data fetched from $source . Path: ${documents[0].reference.path}")
    } catch (e: Exception) {
        Log.i(TAG, "Collection Data fetched from $source")
    }
}

private fun DocumentSnapshot.logPath(isSentSuccess: Boolean) {
    if (isSentSuccess.not()) Log.e(TAG, "Failed to Send to the consumer. Path: ${reference.path}")
    else Log.i(TAG, "Document Data fetched from ${if (metadata.isFromCache) localCache else server} Path: ${reference.path}")
}