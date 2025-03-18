package com.trimble.ttm.commons.repo

interface DeviceAuthRepo {
    suspend fun getDeviceToken(consumerKey:String): String?
}