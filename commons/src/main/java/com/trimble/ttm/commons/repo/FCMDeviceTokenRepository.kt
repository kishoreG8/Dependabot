package com.trimble.ttm.commons.repo

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator

interface FCMDeviceTokenRepository {
    suspend fun fetchFCMToken(): String

    suspend fun fetchFCMTokenFromFirestore(path: String) : String

    suspend fun storeFCMTokenInFirestore(path: String, token: String): Boolean

    suspend fun deleteFCMTokenFromFirestore(path: String) : Boolean

    fun getAppModuleCommunicator(): AppModuleCommunicator
}