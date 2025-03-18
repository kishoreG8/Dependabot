package com.trimble.ttm.routemanifest.application

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.client.wrapper.CommunicationProviderCallBack
import com.trimble.launchercommunicationlib.client.wrapper.HostAppConnectionCallBack
import com.trimble.launchercommunicationlib.client.wrapper.LauncherServiceConnectionCallBack
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.ttm.backbone.api.MultipleEntryQuery
import com.trimble.ttm.backbone.api.data.eld.CurrentUser
import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.di.appContextModule
import com.trimble.ttm.commons.di.commonDataSourceModule
import com.trimble.ttm.commons.di.commonDispatcherProviderModule
import com.trimble.ttm.commons.di.commonFireBaseModule
import com.trimble.ttm.commons.di.commonFirebaseAnalyticsModule
import com.trimble.ttm.commons.di.commonRepoModule
import com.trimble.ttm.commons.di.commonUseCaseModule
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.FeatureLogTags
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MemoryLogger
import com.trimble.ttm.commons.model.DeviceFcmToken
import com.trimble.ttm.commons.model.DriverDeviceInfo
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.BackboneRepository
import com.trimble.ttm.commons.repo.DeviceAuthRepo
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FCM_TOKEN_RECHECK
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.forceEnableAppSystemTracingForPerformanceAnalysis
import com.trimble.ttm.commons.utils.traceBlock
import com.trimble.ttm.formlibrary.di.coroutineScopeModule
import com.trimble.ttm.formlibrary.di.formLibraryDataSourceModule
import com.trimble.ttm.formlibrary.di.formLibraryRepoModule
import com.trimble.ttm.formlibrary.di.formLibraryUseCaseModule
import com.trimble.ttm.formlibrary.di.formLibraryViewModelModule
import com.trimble.ttm.formlibrary.di.networkModule
import com.trimble.ttm.formlibrary.di.roomDataBaseModule
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.workmanager.TtsUpdateWorker
import com.trimble.ttm.formlibrary.manager.workmanager.schedulePeriodicImageUpload
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.InternetConnectionStatus
import com.trimble.ttm.mep.log.api.SetupTrimbLog
import com.trimble.ttm.routemanifest.BuildConfig
import com.trimble.ttm.routemanifest.di.appModule
import com.trimble.ttm.routemanifest.di.dataSourceModule
import com.trimble.ttm.routemanifest.di.repoModule
import com.trimble.ttm.routemanifest.di.useCaseModule
import com.trimble.ttm.routemanifest.di.viewModelModule
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.VID_KEY
import com.trimble.ttm.routemanifest.managers.workmanager.RefreshFCMTokenWorker
import com.trimble.ttm.routemanifest.receiver.AppLauncherMapServiceBoundStatusReceiver
import com.trimble.ttm.routemanifest.receiver.ManagedConfigReceiver
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.service.RouteManifestForegroundService
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.DockModeUseCase
import com.trimble.ttm.routemanifest.usecases.WorkFlowApplicationUseCase
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.FCM_TOKEN_RECHECK_INTERVAL
import com.trimble.ttm.routemanifest.utils.INTENT_ACTION_MAP_SERVICE_STATUS_EVENT
import com.trimble.ttm.routemanifest.utils.REPETITION_TIME
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getAppLauncherVersionAndSaveInMemory
import com.trimble.ttm.routemanifest.utils.ext.getConsumerKey
import com.trimble.ttm.routemanifest.utils.ext.getPanelClientLibBuildEnvironment
import com.trimble.ttm.routemanifest.utils.ext.isServiceRunningInForeground
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import com.trimble.ttm.toolbar.di.initToolbar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class WorkflowApplication : Application(), HostAppConnectionCallBack,
    KoinComponent, LauncherServiceConnectionCallBack, DefaultLifecycleObserver,
    AppModuleCommunicator, CommunicationProviderCallBack {

    //Do not call cancel on this scope. Then application should be force-stopped to reinitialise this scope
    val applicationScope = CoroutineScope(SupervisorJob())
    private val tripPanelServiceConnectionStatusScope = CoroutineScope(SupervisorJob())
    private val localDataSourceRepo: LocalDataSourceRepo by inject()
    private val dispatchValidationUseCase: DispatchValidationUseCase by inject()
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
    private val workFlowApplicationUseCase: WorkFlowApplicationUseCase by inject()
    private val tripPanelEventsRepo: TripPanelEventRepo by inject()

    private val tag = "WorkflowApplication"
    private val dataStoreManager: DataStoreManager by inject()
    private var cid: String = ""
    private var vehicleNumber: String = ""
    private var obcId: String = ""
    private var featureFlagDocumentMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
        emptyMap()
    private val _routeResultBundleFlow = MutableStateFlow<Bundle?>(null)
    val routeResultBundleFlow = _routeResultBundleFlow.asStateFlow()

    private fun emitRouteCalculationBundleResult(bundle: Bundle) {
        _routeResultBundleFlow.tryEmit(bundle)
    }

    override fun onMessageReceived(data: Bundle) {
        emitRouteCalculationBundleResult(data)
    }

    override fun doSetCid(newCid: String) {
        if (newCid.isNotEmpty() && ((cid == newCid).not() || cid.isEmpty())) {
            cid = newCid
        }
    }

    override fun doSetTruckNumber(newTruckNumber: String) {
        if (newTruckNumber.isNotEmpty() && ((vehicleNumber == newTruckNumber).not() || vehicleNumber.isEmpty())) {
            vehicleNumber = newTruckNumber
        }
    }

    override fun doSetObcId(newObcId: String) {
        if (newObcId.isNotEmpty() && ((obcId == newObcId).not() || obcId.isEmpty())) {
            obcId = newObcId
        }
    }

    override suspend fun doGetCid(): String {
        return cid.ifEmpty {
            getCustomerIdFromBackbone(applicationScope)?.let {
                return it
            } ?: EMPTY_STRING
        }
    }

    override suspend fun doGetTruckNumber(): String {
        return vehicleNumber.ifEmpty {
            getTruckNumberFromBackbone(applicationScope)?.let {
                return it
            } ?: EMPTY_STRING
        }
    }

    override suspend fun doGetObcId(): String {
        return obcId.ifEmpty {
            getObcIdFromBackbone(applicationScope)?.let {
                return it
            } ?: EMPTY_STRING
        }
    }

    override suspend fun getCurrentWorkFlowId(caller: String) =
        localDataSourceRepo.getActiveDispatchId(caller)

    override suspend fun getSelectedDispatchId(caller: String) =
        localDataSourceRepo.getSelectedDispatchId(caller)

    override suspend fun setCurrentWorkFlowId(currentWorkFlowId: String) =
        localDataSourceRepo.setActiveDispatchId(currentWorkFlowId)


    override suspend fun setCurrentWorkFlowDispatchName(dispatchName: String) =
        localDataSourceRepo.setCurrentWorkFlowDispatchName(dispatchName)

    override suspend fun isFirebaseAuthenticated(): Boolean =
        FirebaseAuth.getInstance().currentUser != null

    override suspend fun getFCMTokenFirestoreStatus(): Boolean =
        localDataSourceRepo.isFCMTokenSavedInFirestore()

    override suspend fun setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore: Boolean) =
        localDataSourceRepo.setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore)

    override suspend fun setLastFetchedFcmTokenToDatastore(deviceFcmToken: DeviceFcmToken) {
        localDataSourceRepo.setToFormLibModuleDataStore(
            FormDataStoreManager.LAST_FETCHED_FCM_TOKEN_DATA_KEY,
            Utils.toJsonString(deviceFcmToken) ?: run {
                Log.e(tag, "setLastFetchedFcmTokenToDatastore: deviceFcmToken is to json string failed. Returning empty string.")
                EMPTY_STRING
            }
        )
    }

    override suspend fun getLastFetchedFcmTokenFromDatastore(): DeviceFcmToken {
        val fromAppModuleDataStore = localDataSourceRepo.getFromFormLibModuleDataStore(
            FormDataStoreManager.LAST_FETCHED_FCM_TOKEN_DATA_KEY,
            EMPTY_STRING
        )
        return Utils.fromJsonString<DeviceFcmToken>(fromAppModuleDataStore) ?: DeviceFcmToken(EMPTY_STRING, EMPTY_STRING)
    }

    override fun setFeatureFlags(map: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) {
        featureFlagDocumentMap = map
    }

    override fun getFeatureFlags(): Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> = featureFlagDocumentMap

    override suspend fun hasOnlyOneDispatchOnList(): Boolean {
        return dispatchValidationUseCase.hasOnlyOne()
    }

    override suspend fun restoreSelectedDispatch() {
        dispatchValidationUseCase.restoreSelected(
            getCurrentWorkFlowId("restoreSelectedDispatch")
        )
    }

    override fun getConsumerKey(): String = BuildConfig.FLAVOR.getConsumerKey()

    override suspend fun doGetVid(): Long = dataStoreManager.getValue(VID_KEY, 0L)

    private var internetTracker: InternetConnectionStatus? = null

    override fun onHostAppStateChange(newState: HostAppState) {
        workFlowApplicationUseCase.handleTripPanelConnectionStatus(
            newState,
            tripPanelServiceConnectionStatusScope
        )
        Log.i(tag, "Host app state - ${newState.name}")
    }

    override fun onLauncherServiceConnected() {
        workFlowApplicationUseCase.handleTripPanelConnectionStatus(
            HostAppState.SERVICE_CONNECTED,
            tripPanelServiceConnectionStatusScope
        )
        registerListenersOnLauncher()
        Log.i(tag, "Connected to host app service")
    }

    override fun onLauncherServiceDisconnected() {
        workFlowApplicationUseCase.handleTripPanelConnectionStatus(
            HostAppState.SERVICE_DISCONNECTED,
            tripPanelServiceConnectionStatusScope
        )
        Log.i(tag, "Disconnected from host app service")
        // Monitor in TripPanelEventRepo was not triggered,
        // when AppLauncher is updated.
        // So, added retry to establish connection after AppLauncher update.
        tripPanelEventsRepo.retryConnection()
    }

    override fun onLauncherBindingDead() {
        Log.i(tag, "Host binding dead. Unbinding and rebinding again")
        AppLauncherCommunicator.unBindService(context = this)
        AppLauncherCommunicator.bindService(
            context = this,
            buildEnvironment = BuildConfig.FLAVOR.getPanelClientLibBuildEnvironment(),
            serviceConnectionCallBack = this
        )
    }

    override suspend fun getLatestDriverDeviceInfo(): DriverDeviceInfo? {
        val cid = doGetCid()
        val truckNumber = doGetTruckNumber()

        if (cid.isEmpty() || truckNumber.isEmpty()) {
            Log.e(tag, "getLatestDriverInfo() Can not retrieve cid=$cid or truckNumber=$truckNumber")
            return null
        }

        return DriverDeviceInfo(cid, truckNumber)
    }

    override suspend fun getInternetConnectivityStatusByGooglePing(): Boolean =
        internetTracker?.pingGoogleToGetInternetConnectivityStatus() ?: false

    companion object {

        fun isDispatchActivityVisible(): Boolean {
            return dispatchActivityVisible
        }

        fun setDispatchActivityResumed() {
            dispatchActivityVisible = true
        }

        fun setDispatchActivityPaused() {
            dispatchActivityVisible = false
        }

        fun isDispatchListActivityVisible(): Boolean {
            return dispatchListActivityVisible
        }

        fun setDispatchListActivityResumed() {
            dispatchListActivityVisible = true
        }

        fun setDispatchListActivityPaused() {
            dispatchListActivityVisible = false
        }

        fun isInManualInspectionScreen(): Boolean {
            return isInManualInspectionScreen
        }

        internal var dispatchActivityVisible = false
        internal var dispatchListActivityVisible = false

        var isAppBackground = false

        private var isInManualInspectionScreen = false

        private val internetCheckConnectionFlow = MutableSharedFlow<Boolean>(replay = 1)
        val internetConnectionEvents = internetCheckConnectionFlow.asSharedFlow()

        fun getLastValueOfInternetCheck(): Boolean {
            return if (internetConnectionEvents.replayCache.isEmpty()) {
                false
            } else {
                internetConnectionEvents.replayCache.first()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isAppBackground = false
        Log.i(tag, "App in Foreground")
        applicationScope.launch(CoroutineName(tag) + dispatcherProvider.io()) {
            getAppLauncherVersionAndSaveInMemory(dataStoreManager)
        }
        if (getLastValueOfInternetCheck().not()) checkInternetConnectivityStatus()
        applicationScope.launch(dispatcherProvider.io()) {
            RefreshFCMTokenWorker.enqueueCheckFcmTokenWork(
                workManager = WorkManager.getInstance(applicationContext),
                workUniqueName = "$DEVICE_FCM$FCM_TOKEN_RECHECK"
            )
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isAppBackground = true
        Log.i(tag, "App in Background")
    }

    override fun onCreate() {
        forceEnableAppSystemTracingForPerformanceAnalysis()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    //.penaltyDeath()
                    .build()
            )
        }
        super<Application>.onCreate()
        traceBlock(FeatureLogTags.APPLICATION_CLASS_ON_CREATE.name) {
            ApplicationContextProvider.init(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@WorkflowApplication)
                modules(
                    listOf(
                        appContextModule,
                        commonRepoModule,
                        commonUseCaseModule,
                        commonFireBaseModule,
                        commonDispatcherProviderModule,
                        commonDataSourceModule,
                        commonFirebaseAnalyticsModule,
                        formLibraryRepoModule,
                        formLibraryUseCaseModule,
                        formLibraryViewModelModule,
                        formLibraryDataSourceModule,
                        roomDataBaseModule,
                        networkModule,
                        coroutineScopeModule,
                        appModule,
                        repoModule,
                        useCaseModule,
                        viewModelModule,
                        dataSourceModule
                    )
                )
            }
            initToolbar(this)
            tripPanelEventsRepo.unregisterCallbacks()
            tripPanelEventsRepo.registerCallbacks(
                communicationProviderCallBack = this
            )
            // Registering receivers immediately after AppProcessCreation to
            // 1. receive mapReady broadcast from AppLauncher 2. Read the Managed Configuration
            registerReceivers()

            AppLauncherCommunicator.bindService(
                this,
                BuildConfig.FLAVOR.getPanelClientLibBuildEnvironment(), this
            )
            Log.i(tag, "Establishing connection to app launcher communication service")

            internetTracker = InternetConnectionStatus(applicationContext, dispatcherProvider)
            checkInternetConnectivityStatus()

            internetTracker?.let { tracker ->
                applicationScope.launch(dispatcherProvider.io() + SupervisorJob()) {
                    tracker.networkStatus.collectLatest { hasInternetConnection ->
                        internetCheckConnectionFlow.emit(hasInternetConnection)
                    }
                }
            }

            initAppCheck()
            observeRetryStatus()
            SetupTrimbLog
                .writeToULSLogApp(context = applicationContext)
                .writeCrashes()
            if (BuildConfig.DEBUG) SetupTrimbLog.writeToLogCat()
            monitorDeviceChanges()
            cacheCommonData(applicationScope)
            launchTtsWidgetWorker()
            workFlowApplicationUseCase.sendGeofenceServiceIntentToLauncher()
            MemoryLogger.logMemoryAndCpuDetails(applicationContext, MemoryLogger.Scenario.APPLICATION_START)
            applicationScope.launch(dispatcherProvider.io()) {
                RefreshFCMTokenWorker.enqueueCheckFcmTokenPeriodicWork(
                    workManager = WorkManager.getInstance(applicationContext),
                    delay = FCM_TOKEN_RECHECK_INTERVAL + 1
                )
            }
        }
        applicationContext.schedulePeriodicImageUpload()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryLogger.onTrimMemory(level, applicationContext, MemoryLogger.Scenario.APPLICATION_TRIM)
    }

    private fun launchTtsWidgetWorker() {
        val myUploadWork = PeriodicWorkRequestBuilder<TtsUpdateWorker>(
            REPETITION_TIME, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ttsWidgetUpdater",
            ExistingPeriodicWorkPolicy.KEEP,
            myUploadWork
        )
    }

    private fun checkInternetConnectivityStatus(coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO) {
        internetTracker?.let { tracker ->
            applicationScope.launch(coroutineDispatcher) {
                internetCheckConnectionFlow.emit(tracker.pingGoogleToGetInternetConnectivityStatus())
            }
        }
    }

    private fun initAppCheck() {
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }

    private fun cacheCommonData(
        coroutineScope: CoroutineScope,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        coroutineScope.launch(dispatcher) {
            getCustomerIdFromBackbone(this)?.let {
                doSetCid(it)
            }

        }
        coroutineScope.launch(dispatcher) {
            getTruckNumberFromBackbone(this)?.let {
                doSetTruckNumber(it)
            }

        }
        coroutineScope.launch(dispatcher) {
            getObcIdFromBackbone(this)?.let {
                doSetObcId(it)
            }
        }
    }

    private suspend fun getCustomerIdFromBackbone(coroutineScope: CoroutineScope): String? {
        return withContext(coroutineScope.coroutineContext) { get<BackboneRepository>().getCustomerId() }
    }

    private suspend fun getCurrentDriverIdFromBackbone(coroutineScope: CoroutineScope): String {
        return withContext(coroutineScope.coroutineContext) { get<BackboneRepository>().getCurrentUser() }
    }

    private suspend fun getTruckNumberFromBackbone(coroutineScope: CoroutineScope): String? =
        withContext(coroutineScope.coroutineContext) { get<BackboneRepository>().getVehicleId() }

    private suspend fun getObcIdFromBackbone(coroutineScope: CoroutineScope): String? =
        withContext(coroutineScope.coroutineContext)
        { get<BackboneRepository>().getOBCId() }

    private fun observeRetryStatus(coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default) {
        applicationScope.launch(coroutineDispatcher + CoroutineName("$tag Launcher Service Bind retry")) {
            tripPanelEventsRepo.observeRetryStatus().consumeEach {
                Log.i(tag, "Trip panel retry status $it")
                if (it) {
                    AppLauncherCommunicator.bindService(
                        this@WorkflowApplication,
                        BuildConfig.FLAVOR.getPanelClientLibBuildEnvironment(),
                        this@WorkflowApplication
                    )
                }
            }
        }
    }

    private fun registerReceivers() {
        registerReceiverWithFlags(
            receiver = AppLauncherMapServiceBoundStatusReceiver,
            filter = IntentFilter(INTENT_ACTION_MAP_SERVICE_STATUS_EVENT),
            isExported = true
        )
        registerReceiverWithFlags(
            receiver = ManagedConfigReceiver(),
            filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            isExported = false
        )
    }

    private fun registerReceiverWithFlags(
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        isExported: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (isExported) {
                true -> registerReceiver(receiver, filter, RECEIVER_EXPORTED)
                false -> registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            }
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun listenForFeatureFlagDocumentUpdates() {
        workFlowApplicationUseCase.listenForFeatureFlagDocumentUpdates()
    }

    override fun monitorDeviceChanges() {
        workFlowApplicationUseCase.monitorDeviceChanges()
    }

    override suspend fun showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection() {
        workFlowApplicationUseCase.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection()
    }

    override suspend fun doGetCurrentUser(coroutineScope: CoroutineScope) = getCurrentDriverIdFromBackbone(coroutineScope)

    private fun registerListenersOnLauncher() {
        Log.i(tag, "Requesting to register listeners")
        //Listen for trip panel connectivity state change in applauncher
        AppLauncherCommunicator.listenToHostAppChange(hostAppConnectionCallBack = this)
        //Enables the copilot listener in applauncher
        workFlowApplicationUseCase.sendGeofenceServiceIntentToLauncher()
    }

    //Callback functions called by formLibrary module
    override suspend fun getDeviceToken() =
        get<DeviceAuthRepo>().getDeviceToken(BuildConfig.FLAVOR.getConsumerKey())

    override fun startForegroundService() {
        startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
    }

    override fun isForegroundServiceRunning() =
        isServiceRunningInForeground(RouteManifestForegroundService::class.java)

    override fun setDockMode(bundle: Bundle, intentAction: String) {
        get<DockModeUseCase>().setDockMode(bundle, packageName, intentAction)
    }

    override fun resetDockMode() {
        get<DockModeUseCase>().resetDockMode()
    }

    override fun getCurrentUserAndUserNameFromBackbone(): MultipleEntryQuery.Result =
        get<BackboneRepository>().getMultipleData(CurrentUser, UserName)

    override suspend fun getUserEldStatus(): Map<String, UserEldStatus>? =
        get<BackboneRepository>().getUserEldStatus()

    override suspend fun hasActiveDispatch(caller: String, logError: Boolean): Boolean = dataStoreManager.hasActiveDispatch(caller, logError)

    override suspend fun setCrashReportIdentifier() {
        Utils.setCrashReportIdentifierAfterBackboneDataCache(
            get()
        )
    }

    override fun getAppFlavor(): String {
        return (BuildConfig.FLAVOR)
    }

    override fun getCurrentDrivers(): Set<String> = get<BackboneRepository>().getDrivers()
    override fun setIsInManualInspectionScreen(isInManualInspectionScreen: Boolean) {
        WorkflowApplication.isInManualInspectionScreen = isInManualInspectionScreen
    }

    override fun getAppModuleApplicationScope() = applicationScope

    override fun getInternetEvents() = internetConnectionEvents
}

