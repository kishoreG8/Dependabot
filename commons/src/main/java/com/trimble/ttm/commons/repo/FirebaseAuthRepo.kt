package com.trimble.ttm.commons.repo

import com.trimble.ttm.commons.model.AuthResult


interface FirebaseAuthRepo {
    suspend fun getFireBaseToken(deviceToken: String): String?
    suspend fun authenticateFirestore(firebaseToken: String): AuthResult?
}