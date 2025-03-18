package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.ACTIVITY
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.AUTH_SUCCESS
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.workmanager.FormLibraryCacheWorker
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

open class AuthenticationViewModel(
    private val eDVIRFormUseCase: EDVIRFormUseCase,
    private val authenticateUseCase: AuthenticateUseCase,
    private val formDataStoreManager: FormDataStoreManager,
    private val application: Application
) : NetworkConnectivityListenerViewModel(authenticateUseCase.getAppModuleCommunicator()) {
    private val tag = "Authentication VM"

    private val _authenticationState: MutableLiveData<AuthenticationState> = MutableLiveData()
    val authenticationState: LiveData<AuthenticationState> = _authenticationState
    private val _composeAuthenticationState : MutableLiveData<AuthenticationState> = MutableLiveData()
    val composeAuthenticationState : LiveData<AuthenticationState> = _composeAuthenticationState

    private val _isEDVIREnabled = MutableLiveData<Boolean>()
    val isEDVIREnabled: LiveData<Boolean> = _isEDVIREnabled

    private val appModuleCommunicator = authenticateUseCase.getAppModuleCommunicator()

    fun checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility() {
        viewModelScope.launch(CoroutineName("$tag${"getEDVIRAvl"}") + defaultDispatcherProvider.io()){
            if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetObcId().isEmpty()) {
                Log.e(
                    "$DEVICE_AUTH$VIEW_MODEL",
                    "ErrorGetEDVIREnabledDocument.Cid ${appModuleCommunicator.doGetCid()}, obc ${appModuleCommunicator.doGetObcId()}"
                )
                return@launch
            }
            var isEDVIRDocExist = formDataStoreManager.getValue(
                FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU,
                false
            )
            if(!isEDVIRDocExist){
                isEDVIRDocExist =
                    eDVIRFormUseCase.isEDVIREnabled(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetObcId()
                    )
                formDataStoreManager.setValue(
                    FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU,
                    isEDVIRDocExist
                )
            }
            _isEDVIREnabled.postValue(isEDVIRDocExist)
        }
    }

    fun startForegroundService() = eDVIRFormUseCase.startForegroundService()

    suspend fun fetchAndRegisterFcmDeviceSpecificToken() {
        authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber()
        )
    }

    fun cacheBackboneData() {
        try {
            viewModelScope.launch(CoroutineName(tag) + defaultDispatcherProvider.io()) {
                eDVIRFormUseCase.setCrashReportIdentifier()
            }
        } catch (e: Exception) {
            Log.e(tag, "exception caching backbone data", null,"exception" to e.stackTraceToString())
        }
    }

    suspend fun getFeatureFlagUpdates() = suspendCancellableCoroutine { continuation ->
        viewModelScope.launch(CoroutineName(tag) + defaultDispatcherProvider.io()) {
            authenticateUseCase.updateFeatureFlagCache { appModuleCommunicator.setFeatureFlags(it) }
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    fun handleAuthenticationProcess(
        caller: String,
        onAuthenticationComplete: () -> Unit,
        doAuthentication: () -> Unit,
        onAuthenticationFailed: () -> Unit,
        onNoInternet: () -> Unit
    ) {
        viewModelScope.launch(CoroutineName(caller)) {
            authenticateUseCase.handleAuthenticationProcess(
                caller = caller,
                isInternetActive = isActiveInternetAvailable(),
                onAuthenticationComplete = {
                    onAuthenticationComplete()
                },
                doAuthentication = {
                    doAuthentication()
                },
                onAuthenticationFailed = {
                    onAuthenticationFailed()
                },
                onNoInternet = {
                    onNoInternet()
                })
        }
    }

    fun doAuthentication(caller:String) {
        Log.d("$DEVICE_AUTH$VIEW_MODEL$caller","startAuth")
        _authenticationState.postValue(AuthenticationState.Loading)
        _composeAuthenticationState.postValue(AuthenticationState.Loading)
        viewModelScope.launch(CoroutineName(tag) + defaultDispatcherProvider.io()) {
            authenticateUseCase.doAuthentication(appModuleCommunicator.getConsumerKey()).let { firebaseAuthResult ->
                if (firebaseAuthResult.message == AUTH_SUCCESS) {
                    //set the datastore key to true when the authentication is happens, this is to set the active dispatch if it already there in the firestore
                    formDataStoreManager.setValue(FormDataStoreManager.IS_FIRST_TIME_OPEN, true)
                    _authenticationState.postValue(AuthenticationState.FirestoreAuthenticationSuccess)
                    _composeAuthenticationState.postValue(AuthenticationState.FirestoreAuthenticationSuccess)
                    cacheFormLibraryData()
                } else {
                    _composeAuthenticationState.postValue(AuthenticationState.Error(firebaseAuthResult.message))
                    _authenticationState.postValue(AuthenticationState.Error(firebaseAuthResult.message))
                }
            }
        }
    }

    fun handleAuthenticationProcessForComposable(
        caller: String
    ) {
        viewModelScope.launch(CoroutineName(caller)) {
            authenticateUseCase.handleAuthenticationProcess(
                caller = caller,
                isInternetActive = isActiveInternetAvailable(),
                onAuthenticationComplete = {
                    Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication Complete")
                    _composeAuthenticationState.postValue(AuthenticationState.FirestoreAuthenticationSuccess)
                },
                doAuthentication = {
                    Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication is not completed. Calling doAuthentication.")
                    doAuthentication(caller)
                },
                onAuthenticationFailed = {
                    val authFailureMessage = application.getString(R.string.firestore_authentication_failure)
                    Log.e("$DEVICE_AUTH$ACTIVITY", authFailureMessage)
                    _composeAuthenticationState.postValue(AuthenticationState.Error(authFailureMessage))
                },
                onNoInternet = {
                    val noInternetMessage = application.getString(R.string.no_internet_authentication_failed)
                    Log.e("$DEVICE_AUTH$ACTIVITY", noInternetMessage)
                    _composeAuthenticationState.postValue(AuthenticationState.Error(noInternetMessage))
                })
        }
    }


    private fun cacheFormLibraryData() {
        viewModelScope.launch(defaultDispatcherProvider.io()) {
            val uniqueWorkName =
                "Cid = ${appModuleCommunicator.doGetCid()} VehicleId = ${appModuleCommunicator.doGetTruckNumber()} ObcId = ${appModuleCommunicator.doGetObcId()} "
            FormLibraryCacheWorker.enqueueFormLibraryCacheFromAuthWorkRequest(
                workManager = WorkManager.getInstance(application.applicationContext),
                uniqueWorkName = uniqueWorkName
            )
        }
    }

    fun recordShortcutClickEvent(eventName: String, referrer: String, intent: Intent?) {
        if (eventName.isNotEmpty() && intent != null) {
            authenticateUseCase.recordShortcutClickEvent(eventName, referrer, intent.categories)
        }
    }

    fun fetchAuthenticationPreRequisites(): Deferred<Unit> = viewModelScope.async(defaultDispatcherProvider.io()) {
        //Used separate async job to get their work done concurrently
        val cacheBackboneDataJob = async { cacheBackboneData() }
        val fetchAndRegisterFcmDeviceSpecificTokenJob = async { fetchAndRegisterFcmDeviceSpecificToken() }
        val getEDVIRSettingsAvailabilityStatusJob = async { checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility() }
        val getFeatureFlagUpdatesJob = async { getFeatureFlagUpdates() }
        listOf(cacheBackboneDataJob,fetchAndRegisterFcmDeviceSpecificTokenJob, getEDVIRSettingsAvailabilityStatusJob,getFeatureFlagUpdatesJob).awaitAll()
    }


}