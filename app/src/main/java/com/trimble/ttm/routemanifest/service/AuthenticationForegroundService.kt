package com.trimble.ttm.routemanifest.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.AUTO_TRIP_START_CALLER_FOREGROUND_SERVICE
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MemoryLogger
import com.trimble.ttm.commons.logger.SERVICE
import com.trimble.ttm.commons.logger.TRIP_START_CALL
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.AUTHENTICATION_FOREGROUND_SERVICE_CHANNEL_ID
import com.trimble.ttm.commons.utils.AUTHENTICATION_FOREGROUND_SERVICE_ID
import com.trimble.ttm.commons.utils.AUTH_SUCCESS
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.createNotification
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.usecase.VehicleDriverMappingUseCase
import com.trimble.ttm.formlibrary.manager.workmanager.FormLibraryCacheWorker
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.usecases.DispatchListUseCase
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val AUTH_RETRY_COUNT = 3

class AuthenticationForegroundService : LifecycleService(), KoinComponent {

    private val tag = "AuthenticationForegroundService"
    private val coroutineDispatcherProvider: DispatcherProvider by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val serviceManager: ServiceManager by inject()
    private val dispatchListUseCase: DispatchListUseCase by inject()
    private val vehicleDriverMappingUseCase: VehicleDriverMappingUseCase by inject()
    private val formDataStoreManager: FormDataStoreManager by inject()
    private var authRetryCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.logLifecycle(tag, "$tag onCreate")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startNotificationForForeground()
        appModuleCommunicator.monitorDeviceChanges()
        Log.logLifecycle(tag, "$tag onStartCommand")
        lifecycleScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.io()) {
            if (appModuleCommunicator.isFirebaseAuthenticated()) {
                startRouteManifestForegroundService()
                stopAuthenticationForegroundService()
                return@launch
            }
            // In order to ensure the availability of the internet during Seamless Auth, we sometimes encounter a false negative from the getLastValueOfInternetCheck() function, indicating no internet even when it is actually present. This issue arises because the service code executes before the internet check within the WorkflowApplication class. As a workaround, we perform a secondary internet availability verification by pinging Google.
            val isInternetAvailable =
                WorkflowApplication.getLastValueOfInternetCheck() || appModuleCommunicator.getInternetConnectivityStatusByGooglePing()
            Log.d("$DEVICE_AUTH$SERVICE", "Is Internet Available - $isInternetAvailable")
            serviceManager.handleAuthenticationProcess(
                caller = tag,
                isInternetActive = isInternetAvailable,
                onAuthenticationComplete = {
                    Log.d("$DEVICE_AUTH$SERVICE", "Auth complete")
                    postAuthentication()
                },
                doAuthentication = {
                    Log.d(
                        "$DEVICE_AUTH$SERVICE",
                        "Auth started. attempt: $authRetryCount"
                    )
                    doAuthentication()
                },
                onAuthenticationFailed = {
                    Log.e(
                        "$DEVICE_AUTH$SERVICE",
                        "Auth failed. Failed to get custom firebase token"
                    )
                },
                onNoInternet = {
                    Log.e(
                        "$DEVICE_AUTH$SERVICE",
                        "Auth failed. No Internet Connection."
                    )
                    stopAuthenticationForegroundService()
                }
            )
        }
        return START_NOT_STICKY
    }

    private fun doAuthentication() {
        lifecycleScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.io()) {
            serviceManager.getAuthenticationResult().let { firebaseAuthResult ->
                if (firebaseAuthResult.message == AUTH_SUCCESS) {
                    //set the datastore key to true when the authentication is happens, this is to set the active dispatch if it already there in the firestore
                    formDataStoreManager.setValue(FormDataStoreManager.IS_FIRST_TIME_OPEN, true)
                    authRetryCount = 0
                    serviceManager.fetchAndStoreFCMToken()
                    serviceManager.getEDVIRSettingsAvailabilityStatus()
                    postAuthentication()
                    Log.d(
                        "$DEVICE_AUTH$SERVICE",
                        "FirebaseSignInSuccess"
                    )
                } else {
                    if (authRetryCount < AUTH_RETRY_COUNT) {
                        authRetryCount++
                        delay(1000)
                        doAuthentication()
                    } else {
                        authRetryCount = 0
                        stopAuthenticationForegroundService()
                    }
                    Log.e(
                        "$DEVICE_AUTH$SERVICE",
                        "FirebaseSignInFailed.Attempt: $authRetryCount"
                    )
                }
            }
        }
    }

    private fun postAuthentication() {

        lifecycleScope.launch(coroutineDispatcherProvider.io()) {

            val uniqueWorkName =
                "${appModuleCommunicator.doGetCid() + appModuleCommunicator.doGetTruckNumber()+ appModuleCommunicator.doGetObcId()} "
            FormLibraryCacheWorker.enqueueFormLibraryCacheFromAuthWorkRequest(
                workManager = WorkManager.getInstance(applicationContext),
                uniqueWorkName = uniqueWorkName
            )
            startRouteManifestForegroundService()
        }.invokeOnCompletion {
            stopAuthenticationForegroundService()
        }

        appModuleCommunicator.getAppModuleApplicationScope()
            .launch(CoroutineName(TRIP_START_CALL) + coroutineDispatcherProvider.io()) {
                dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(),AUTO_TRIP_START_CALLER_FOREGROUND_SERVICE
                )
                vehicleDriverMappingUseCase.updateVehicleDriverMapping()
            }

    }

    private fun startNotificationForForeground() {
        startForeground(
            AUTHENTICATION_FOREGROUND_SERVICE_ID,
            this.createNotification(
                AUTHENTICATION_FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.authentication_foreground_service_channel_name),
                getString(R.string.app_name),
                getString(R.string.foreground_notification_content_text),
                R.mipmap.ic_app_icon
            )
        )
    }

    private fun stopAuthenticationForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRouteManifestForegroundService() {
        startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
    }

    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag Service OnDestroy")
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryLogger.onTrimMemory(
            level,
            applicationContext,
            MemoryLogger.Scenario.AUTHENTICATION_SERVICE_TRIM
        )
    }
}