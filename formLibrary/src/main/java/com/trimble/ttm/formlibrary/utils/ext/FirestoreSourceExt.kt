package com.trimble.ttm.formlibrary.utils.ext

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await

fun Query.getFromCache(): Task<QuerySnapshot> = get(Source.CACHE)

fun DocumentReference.getFromCache(): Task<DocumentSnapshot> = get(Source.CACHE)

fun Query.getFromServer(): Task<QuerySnapshot> = get(Source.SERVER)

fun DocumentReference.getFromServer(): Task<DocumentSnapshot> = get(Source.SERVER)

suspend fun Query.isCacheEmpty(): Boolean {
    return try {
        val querySnapshot = getFromCache().await()
        querySnapshot.isEmpty
    } catch (e: FirebaseFirestoreException) {
        true
    }
}

suspend fun DocumentReference.isCacheEmpty(): Boolean {
    return try {
        val docSnapshot = getFromCache().await()
        docSnapshot.exists().not()
    } catch (e: FirebaseFirestoreException) {
        true
    }
}

fun DocumentSnapshot.dataSource(): String {
    return if (this.metadata.isFromCache)
        "local cache"
    else
        "server"
}