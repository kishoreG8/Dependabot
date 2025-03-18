package com.trimble.ttm.commons.repo

import android.content.Context
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.utils.APP_NAME
import com.trimble.ttm.mep.certificates.api.tpaas.TokenRequest
import kotlinx.coroutines.CancellationException

class DeviceAuthRepoImpl(private val context: Context) : DeviceAuthRepo {
    override suspend fun getDeviceToken(consumerKey: String): String? {
        try {
            Log.d("$DEVICE_AUTH$REPO", "RequestingDeviceTokenFromCertService")
            return TokenRequest(context, consumerKey, APP_NAME).execute()
        } catch (cancellationException: CancellationException) {
            //Ignored
        }  catch (e: java.lang.Exception) {
            Log.e("$DEVICE_AUTH$REPO", "FailedDeviceTokenRequest ${e.message}", e)
        }
        Log.e("$DEVICE_AUTH$REPO", "ReturningNullDeviceToken")
        return null
    }

}