package com.trimble.ttm.commons.usecase

import com.google.firebase.auth.FirebaseAuth
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.INTERNET_CONNECTIVITY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.logger.USE_CASE
import com.trimble.ttm.commons.model.AuthenticationProcessResult
import com.trimble.ttm.commons.model.DeviceAuthResult
import com.trimble.ttm.commons.model.DeviceFcmToken
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.DeviceAuthRepo
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepository
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
import com.trimble.ttm.commons.utils.AUTH_SERVER_ERROR
import com.trimble.ttm.commons.utils.AUTH_SUCCESS
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.ERROR_TAG
import com.trimble.ttm.commons.utils.FCM_TOKENS_COLLECTION
import com.trimble.ttm.commons.utils.FCM_TOKEN_RECHECK
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.INTENT_CATEGORY_LAUNCHER
import com.trimble.ttm.commons.utils.TRIP_INFO_WIDGET
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This use case class contains functions used to get device access token, firebase custom token
 * and authenticate to firestore database using custom token
 * */

class AuthenticateUseCase(
    private val deviceAuthRepo: DeviceAuthRepo,
    private val firebaseAuthRepo: FirebaseAuthRepo,
    private val fcmDeviceTokenRepository: FCMDeviceTokenRepository,
    private val featureFlagCacheRepo: FeatureFlagCacheRepo,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder,
    private val managedConfigurationRepo: ManagedConfigurationRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun doAuthentication(consumerKey: String): DeviceAuthResult {
        Log.d(
            "$DEVICE_AUTH$USE_CASE",
            "GettingDeviceToken")
        val deviceAccessToken = getDeviceAccessToken(consumerKey)
        if (deviceAccessToken.isEmpty()) {
            Log.e(
                "$DEVICE_AUTH$USE_CASE",
                "EmptyDeviceToken"
            )
            return DeviceAuthResult(
                AUTH_DEVICE_ERROR,
                false
            )
        }

        Log.d(
            "$DEVICE_AUTH$USE_CASE",
            "ExchangeDeviceTokenWithFirebaseToken")
        val firebaseToken = getFireBaseToken(deviceAccessToken)
        if (firebaseToken.isEmpty()) {
            Log.e(
                "$DEVICE_AUTH$USE_CASE",
                "EmptyFirebaseToken"
            )
            return DeviceAuthResult(
                AUTH_SERVER_ERROR,
                false
            )
        }

        Log.d(
            "$DEVICE_AUTH$USE_CASE",
            "CustomSigningInWithFirebaseToken")
        val authResult = authenticateFirestore(firebaseToken)
        if (authResult == null) {
            Log.e(
                "$DEVICE_AUTH$USE_CASE",
                "NullAuthResult"
            )
            return DeviceAuthResult(
                AUTH_SERVER_ERROR,
                false
            )
        }

        return if (authResult.status) {
            val currentUserId= firebaseAuth.currentUser?.uid
            Log.n(
                "$DEVICE_AUTH$USE_CASE",
                "FirebaseCustomSignInSuccess.uid: $currentUserId"
            )
            DeviceAuthResult(
                AUTH_SUCCESS,
                true
            )
        } else {
            Log.e(
                "$DEVICE_AUTH$USE_CASE",
                "FirebaseCustomSignInFailed",
                authResult.exception
            )
            DeviceAuthResult(
                AUTH_SERVER_ERROR,
                false
            )
        }
    }

    suspend fun getDeviceAccessToken(consumerKey: String) =
        deviceAuthRepo.getDeviceToken(consumerKey) ?: ""

    suspend fun getFireBaseToken(deviceToken: String) =
        firebaseAuthRepo.getFireBaseToken(deviceToken) ?: ""

    suspend fun authenticateFirestore(firebaseToken: String) =
        firebaseAuthRepo.authenticateFirestore(firebaseToken)

    suspend fun fetchAndRegisterFcmDeviceSpecificToken(cid: String, truckNumber: String, oldTruckNumber : String = EMPTY_STRING) {
        withContext(dispatcherProvider.io()) {
            if (cid.isEmpty() || truckNumber.isEmpty() || appModuleCommunicator.isFirebaseAuthenticated().not()
            ) {
                Log.n(
                    "$DEVICE_FCM$USE_CASE",
                    "Skipping storage of FcmDeviceToken since, isCidEmpty: ${cid.isEmpty()}, isTruckNumberEmpty: ${truckNumber.isEmpty()}, isFirebaseNotAuthenticated : ${appModuleCommunicator.isFirebaseAuthenticated().not()}"
                )
                return@withContext
            }
            registerDeviceSpecificTokenToFireStore(
                cid = cid,
                truckNumber = truckNumber,
                newFcmToken = fetchFCMDeviceToken(),
                oldTruckNum = oldTruckNumber,
                caller = "AuthUseCase"
            )
        }
    }

    suspend fun fetchFCMDeviceToken(): String = fcmDeviceTokenRepository.fetchFCMToken()

    suspend fun fetchFCMTokenFromFireStore(documentPath: String): String =
        fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(documentPath)

    //This function deletes the existing fcm token of old truck and current truck and stores the new fcm token for current truck in firestore.
    suspend fun registerDeviceSpecificTokenToFireStore(
        cid: String,
        truckNumber: String,
        newFcmToken: String,
        oldTruckNum : String = EMPTY_STRING,
        caller : String
    ) {
        Log.n("$DEVICE_FCM+$USE_CASE", "deleting fcm tokens for the truckNumber: $truckNumber", null, "caller" to caller)
        Log.d(INTERNET_CONNECTIVITY, "Internet Connectivity: ${appModuleCommunicator.getInternetEvents()}")
        unRegisterDeviceSpecificTokenFromFireStore(cid, truckNumber)
        if(oldTruckNum.isNotEmpty()){
            Log.n("$DEVICE_FCM+$USE_CASE", "deleting fcm tokens for the old truckNumber: $oldTruckNum" +"caller : $caller", null, "caller" to caller, "oldTruckNum" to oldTruckNum)
            unRegisterDeviceSpecificTokenFromFireStore(cid, oldTruckNum)
        }
        val path = "${FCM_TOKENS_COLLECTION}/$cid/$truckNumber/$newFcmToken"
        Log.d("$DEVICE_FCM+$USE_CASE", "storing fcm token for truckNumber: $truckNumber")
        fcmDeviceTokenRepository.storeFCMTokenInFirestore(path, newFcmToken).let { isTokenStoredInFirestore ->
            Log.n("$DEVICE_FCM+$USE_CASE", "setting FCMTokenFirestoreStatusInDataStore for vehicle: $truckNumber isTokenStoredInFirestore: $isTokenStoredInFirestore", null, "caller" to caller)
            getAppModuleCommunicator().setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore)
        }
        val deviceFcmToken = DeviceFcmToken(newFcmToken, truckNumber)
        appModuleCommunicator.setLastFetchedFcmTokenToDatastore(deviceFcmToken)
    }

    suspend fun unRegisterDeviceSpecificTokenFromFireStore(
        cid: String,
        vid: String
    ) {
        val path = "${FCM_TOKENS_COLLECTION}/$cid/$vid"
        fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(path)
    }

    suspend fun updateFeatureFlagCache(callback: (Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) -> Unit)  {
        featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap(callback)
    }

    fun getAppModuleCommunicator() = fcmDeviceTokenRepository.getAppModuleCommunicator()

    suspend fun getAuthenticationProcessResult(): AuthenticationProcessResult = withContext(dispatcherProvider.io()) {
        getAppModuleCommunicator().let { appModuleCommunicator ->
            AuthenticationProcessResult(
                isFirestoreAuthenticated = appModuleCommunicator.isFirebaseAuthenticated(),
                isFCMTokenSavedInFireStore = appModuleCommunicator.getFCMTokenFirestoreStatus()
            )
        }
    }

    suspend fun handleAuthenticationProcess(
        caller: String,
        isInternetActive: Boolean,
        onAuthenticationComplete: () -> Unit,
        doAuthentication: () -> Unit,
        onAuthenticationFailed: () -> Unit,
        onNoInternet: () -> Unit
    ) {
        Log.d(DEVICE_AUTH+ USE_CASE, "Handle Authentication Process Called from $caller, isActiveInternetAvailable: $isInternetActive")
        getAuthenticationProcessResult().let { authenticationProcessResult ->
            Log.d(DEVICE_AUTH+ USE_CASE, "authenticationProcessResult: isFirestoreAuthenticated:${authenticationProcessResult.isFirestoreAuthenticated}, isFCMTokenSavedInFireStore:${authenticationProcessResult.isFCMTokenSavedInFireStore}. caller: $caller")
            if (authenticationProcessResult.isAuthenticationComplete()) {
                Log.d(DEVICE_AUTH+ USE_CASE, "Auth completed.  caller: $caller")
                fetchManagedConfigData(caller)
                onAuthenticationComplete()
            } else {
                if (!isInternetActive && !authenticationProcessResult.isFirestoreAuthenticated) {
                    Log.e(DEVICE_AUTH+ USE_CASE, "Auth not completed, no internet available",null, "caller" to caller)
                    onNoInternet()
                    return
                }
                if (isInternetActive && !authenticationProcessResult.isFirestoreAuthenticated) {
                    Log.d(DEVICE_AUTH+ USE_CASE, "Auth not completed",null, "isFCMTokenSavedInFireStore" to authenticationProcessResult.isFCMTokenSavedInFireStore,"caller" to caller)
                    doAuthentication()
                } else if (!authenticationProcessResult.isFCMTokenSavedInFireStore) {
                    Log.d(DEVICE_AUTH+ USE_CASE, "Auth completed, FCM token not saved",null, "isActiveInternetAvailable" to isInternetActive,"caller" to  caller)
                    getAppModuleCommunicator().let { appModuleCommunicator ->
                        fetchAndRegisterFcmDeviceSpecificToken(
                            appModuleCommunicator.doGetCid(),
                            appModuleCommunicator.doGetTruckNumber()
                        )
                    }
                    checkForFCMTokenSavedState(caller, onAuthenticationComplete = {onAuthenticationComplete()}, onAuthenticationFailed = {onAuthenticationFailed()})
                }
            }
        }
    }

    internal suspend fun checkForFCMTokenSavedState(caller: String, onAuthenticationComplete: () -> Unit, onAuthenticationFailed: () -> Unit) {
        if (getAuthenticationProcessResult().isFCMTokenSavedInFireStore) {
            Log.d(DEVICE_AUTH+ USE_CASE, "FCM token saved successfully. caller: $caller")
            onAuthenticationComplete()
        }
        else {
            Log.e(DEVICE_AUTH+ USE_CASE, "Failed saving FCM token. caller: $caller")
            onAuthenticationFailed()
        }
    }

    fun recordShortcutClickEvent(eventName: String, referrer: String, intentCategoriesSet: Set<String>?) {
        if (referrer != TRIP_INFO_WIDGET && intentCategoriesSet?.contains(INTENT_CATEGORY_LAUNCHER) == true) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                eventName
            )
        }
    }

    private fun fetchManagedConfigData(caller: String) {
        val scope = CoroutineScope(dispatcherProvider.io())
        scope.launch {
            managedConfigurationRepo.fetchManagedConfigDataFromServer(caller)
            scope.cancel()
        }
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
    suspend fun getFcmTokenDataFromDatastore(): DeviceFcmToken {
        return appModuleCommunicator.getLastFetchedFcmTokenFromDatastore()
    }

    suspend fun checkFcmInSyncWithFireStore(
        customerId: String,
        truckNumber: String,
        deviceFcmToken: String
    ): Boolean {
        try {
            val fcmTokenFromFirestore = fetchFCMTokenFromFireStore("$FCM_TOKENS_COLLECTION/$customerId/$truckNumber")
            if (fcmTokenFromFirestore != ERROR_TAG && (fcmTokenFromFirestore.isEmpty() || deviceFcmToken != fcmTokenFromFirestore)) {
                Log.n(
                    "$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                    "Updating FCM token, isFcmTokenInSyncWithFireStore : ${deviceFcmToken == fcmTokenFromFirestore} isFcmTokenAvailableInFireStore : ${fcmTokenFromFirestore.isNotEmpty()}" ,
                    null,
                    "deviceFCMToken" to deviceFcmToken,
                    "fcmTokenFromFireStore" to fcmTokenFromFirestore
                )
                fetchAndRegisterFcmDeviceSpecificToken(
                    cid = customerId,
                    truckNumber = truckNumber
                )
            }
        } catch (e: Exception) {
            Log.w(
                "$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK+InFirestore",
                "Exception in checkFcmInSyncWithFireStore ${e.stackTraceToString()}"
            )
            return false
        }
        return true
    }

    suspend fun checkFcmInSyncWithCache(
        customerId: String,
        truckNumber: String,
        deviceFcmToken: String
    ): Boolean {
        try {
            val fcmTokenDataFromDatastore = getFcmTokenDataFromDatastore()
            if ((fcmTokenDataFromDatastore.fcmToken.isNotEmpty() && fcmTokenDataFromDatastore.fcmToken != deviceFcmToken) ||
                (fcmTokenDataFromDatastore.truckNumber.isNotEmpty() && fcmTokenDataFromDatastore.truckNumber != truckNumber)) {
                Log.n(
                    "$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                    "Updating FCM token, isFcmTokenInSyncWithCache : ${deviceFcmToken == fcmTokenDataFromDatastore.fcmToken} isFcmTokenAvailableInCache : ${fcmTokenDataFromDatastore.fcmToken.isNotEmpty()}"
                )
                fetchAndRegisterFcmDeviceSpecificToken(
                    cid = customerId,
                    truckNumber = truckNumber
                )
            }
        } catch (e: Exception) {
            Log.w(
                "$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK+InDatastore",
                "Exception in checkFcmInSyncWithCache ${e.stackTraceToString()}"
            )
            return false
        }
        return true
    }
}