package com.trimble.ttm.formlibrary.usecases

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.trimble.ttm.commons.logger.APP_CHECK
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.coroutines.tasks.await

class FirebaseCurrentUserTokenFetchUseCase {
    suspend fun getIDTokenOfCurrentUser() =
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token

    suspend fun getAppCheckToken() : String {
        val appCheckToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).await()?.token
        appCheckToken?.let {
            if(it.isEmpty()){
                Log.e(APP_CHECK, "App check token is empty")
            }
            return it
        }
        return EMPTY_STRING
    }
}

