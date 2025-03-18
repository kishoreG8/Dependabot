package com.trimble.ttm.commons.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.REPO
import com.trimble.ttm.commons.model.AuthResult
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val ACCESS_TOKEN = "accessToken"
private const val CUSTOM_TOKEN = "customtoken"
private const val FIRE_BASE_AUTH_CF_NAME = "DeviceV4FirebaseToken"

@Suppress("UNCHECKED_CAST")
class FirebaseAuthRepoImpl : FirebaseAuthRepo {

    override suspend fun getFireBaseToken(deviceToken: String): String? =
        suspendCoroutine { continuation ->
            val authRequest = HashMap<String, String>()
            authRequest[ACCESS_TOKEN] = deviceToken
            try {
                Log.d("$DEVICE_AUTH$REPO", "GetFireBaseCustomTokenStarted")
                FirebaseFunctions.getInstance().getHttpsCallable(FIRE_BASE_AUTH_CF_NAME)
                    .call(authRequest)
                    .addOnSuccessListener {
                        Log.d("$DEVICE_AUTH$REPO", "SuccessGetFireBaseCustomToken")
                        val responseMap = it.data as HashMap<String, String>
                        responseMap.let {
                            responseMap[CUSTOM_TOKEN]?.let { customToken ->
                                continuation.resume(customToken)
                            } ?: run {
                                Log.e("$DEVICE_AUTH$REPO", "FirebaseCustomTokenNull")
                                continuation.resume(null)
                            }
                        }
                    }.addOnFailureListener {
                        Log.e("$DEVICE_AUTH$REPO", "FailureGetFireBaseToken${it.stackTraceToString()}")
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e("$DEVICE_AUTH$REPO", "ExceptionGetFireBaseToken${e.stackTraceToString()}")
                continuation.resume(null)
            }
        }

    override suspend fun authenticateFirestore(firebaseToken: String): AuthResult? =
        suspendCoroutine { continuation ->
            try {
                FirebaseAuth.getInstance().signInWithCustomToken(firebaseToken)
                    .addOnCompleteListener { authResultTask ->
                        if (authResultTask.isSuccessful) {
                            Log.d("$DEVICE_AUTH$REPO", "FirebaseCustomSignInSuccess")
                            continuation.resume(AuthResult(true, null))
                        }
                        else {
                            Log.e("$DEVICE_AUTH$REPO", "FirebaseCustomSignInFailed${authResultTask.exception?.message}", authResultTask.exception)
                            continuation.resume(AuthResult(false, authResultTask.exception))
                        }
                    }
            } catch (e: Exception) {
                continuation.resume(AuthResult(false, e))
                Log.e("$DEVICE_AUTH$REPO", "ExceptionFirebaseCustomSignIn${e.stackTraceToString()}")
            }
        }
}