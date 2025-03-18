package com.trimble.ttm.routemanifest.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.getFeatureFlagDataAsLong
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.DelayProvider
import com.trimble.ttm.formlibrary.utils.DelayResolver
import com.trimble.ttm.formlibrary.utils.HOTKEYS_COLLECTION_NAME
import com.trimble.ttm.formlibrary.viewmodel.DELAY_TO_AVOID_FREEZING_ANIMATION
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.isValid
import com.trimble.ttm.routemanifest.repo.isAppLauncherWithMapsPerformanceFixInstalled
import com.trimble.ttm.routemanifest.service.RouteManifestForegroundService
import com.trimble.ttm.routemanifest.usecases.DispatchListUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE
import com.trimble.ttm.routemanifest.utils.INT_MAX
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.setCrashReportIdentifierAfterBackboneDataCache
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class DispatchListViewModel(
    private val application: Application,
    private val dispatchListUseCase: DispatchListUseCase,
    private val backboneUseCase: BackboneUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatchValidationUseCase: DispatchValidationUseCase,
    private val tripStartUseCase: TripStartUseCase,
    private val formLibraryUseCase: FormLibraryUseCase,
    val coroutineDispatcherProvider: DispatcherProvider
) : ViewModel(), KoinComponent {

    private val dispatchListMutableSharedFlow = MutableSharedFlow<List<Dispatch>>()
    internal val dispatchListFlow: SharedFlow<List<Dispatch>> = dispatchListMutableSharedFlow
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    private val _dispatchToShowTripStartPrompt = MutableLiveData<Dispatch>()
    val dispatchToShowTripStartPrompt: LiveData<Dispatch> = _dispatchToShowTripStartPrompt
    private var firstRead = true
    private val _isLastDispatchReached = MutableLiveData<Boolean>()
    val isLastDispatchReached: LiveData<Boolean> = _isLastDispatchReached
    private var _dispatchRemovedFromList = MutableLiveData<String>()
    val dispatchRemovedFromList: LiveData<String> = _dispatchRemovedFromList
    private var _isHotKeysAvailable = MutableLiveData<Boolean>()
    val isHotKeysAvailable : LiveData<Boolean> = _isHotKeysAvailable

    fun removeSelectedDispatchIdFromLocalCache() = viewModelScope.launch {
        dispatchListUseCase.removeSelectedDispatchIdFromLocalCache()
    }

    suspend fun cacheBackboneData() {
        try {
            setCrashReportIdentifierAfterBackboneDataCache(appModuleCommunicator)
        } catch (e: Exception) {
            Log.e("$TRIP_LIST$VIEW_MODEL", "ExceptionSetCrashIdentifier${e.message}")
        }
    }

    private fun clearDispatches() = dispatchListUseCase.clear()

    fun getDispatchList(caller:String) {
        viewModelScope.launch(CoroutineName("$TRIP_LIST$VIEW_MODEL") + coroutineDispatcherProvider.io()) {
            Log.d("$TRIP_LIST$VIEW_MODEL","getTrips$caller")
            val cid = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            if (cid.isEmpty() || truckNumber.isEmpty()) {
                withContext(coroutineDispatcherProvider.mainImmediate()) {
                    _errorData.value =
                        "${application.applicationContext.getString(R.string.err_loading_dispatch_list)}. ${
                            application.applicationContext.getString(
                                R.string.err_invalid_vehicle_customer_id
                            )
                        }."
                    Log.e("$TRIP_LIST$VIEW_MODEL", "CID or Truck# is empty",throwable = null,"Cid" to cid,"Truck#" to truckNumber)
                }
                return@launch
            } else {
                observeForDispatches()
                observeForRemovedDispatchId()
                observeForLastDispatchReachPagination()
                dispatchListUseCase.listenDispatchesForTruck(
                    truckNumber,
                    cid
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun observeForRemovedDispatchId() {
        viewModelScope.launch(coroutineDispatcherProvider.io() + CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            dispatchListUseCase.getRemovedDispatchIDFlow().collect {
                _dispatchRemovedFromList.postValue(it)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun observeForLastDispatchReachPagination() {
        viewModelScope.launch(CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            dispatchListUseCase.getLastDispatchReachedFlow().collect {
                _isLastDispatchReached.value = true
            }
        }
    }

    private suspend fun observeForDispatches() {
        viewModelScope.launch(coroutineDispatcherProvider.io() + CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            dispatchListUseCase.listenDispatchesList().collect {
                if (it is ArrayList<*>) {
                    (it as? ArrayList<*>)?.filterIsInstance<Dispatch>()
                        ?.let { dispatchListFromServer ->
                            Log.d("$TRIP_LIST$VIEW_MODEL","ObserveTrips${dispatchListFromServer.map { disp -> disp.dispid }}")
                            val sortedDispatchList = dispatchListUseCase
                                .checkAndUpdateStopCount(dispatchListFromServer)
                                .let { dispatchListUseCase.sortDispatchListByTripStartTime(it) }

                            val filteredDispatchList = sortedDispatchList.filter { dispatch ->
                                dispatch.stopsCountOfDispatch > 0
                            }

                            updateDispatchesQuantity(filteredDispatchList.size)
                            updateActiveDispatchAndEmitDispatchList(filteredDispatchList)
                            getEligibleTripToShowNextTripToStartPrompt(filteredDispatchList)
                            firstRead = false
                        }

                } else {
                    _errorData.postValue(
                        application.applicationContext.getString(R.string.err_loading_dispatch_list)
                    )
                }
            }
        }
    }

    private suspend fun getEligibleTripToShowNextTripToStartPrompt(sortedDispatchList: List<Dispatch>) {
        withContext(coroutineDispatcherProvider.mainImmediate()) {
            dispatchListUseCase.getDispatchToBeStarted(
                sortedDispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN
            ).let { dispatchEligibleToStart ->
                if (dispatchEligibleToStart.isValid() && (_dispatchToShowTripStartPrompt.value == null || dispatchListUseCase.canShowDispatchStartPopup(
                        _dispatchToShowTripStartPrompt.value,
                        dispatchEligibleToStart
                    ))
                ) {
                    _dispatchToShowTripStartPrompt.postValue(
                        dispatchEligibleToStart
                    )
                }
            }
        }
    }

    private suspend fun updateActiveDispatchAndEmitDispatchList(sortedDispatchList: List<Dispatch>) {
        dispatchListUseCase.updateActiveDispatchDatastoreKeys(sortedDispatchList)
        withContext(coroutineDispatcherProvider.mainImmediate()) {
            Log.d("$TRIP_LIST$VIEW_MODEL", "EmitDispatchList${sortedDispatchList.size}")
            dispatchListMutableSharedFlow.emit(sortedDispatchList)
        }
    }


    suspend fun hasBackboneDataChanged(): Boolean {
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty() || appModuleCommunicator.doGetObcId().isEmpty()
        ) return true
        return try {
            (appModuleCommunicator.doGetCid() != backboneUseCase.getCustomerId()) ||
                    (appModuleCommunicator.doGetTruckNumber() != backboneUseCase.getVehicleId()) ||
                    (appModuleCommunicator.doGetObcId() != backboneUseCase.getOBCId())
        } catch (e: Exception) {
            true
        }
    }

    internal fun dismissTripPanelMessageIfThereIsNoActiveTrip() = viewModelScope.launch(coroutineDispatcherProvider.io()) {
        dispatchListUseCase.dismissTripPanelMessageIfThereIsNoActiveTrip()
    }

    fun addNotifiedDispatchToTheDispatchList(dispatch: Dispatch) {
        viewModelScope.launch(coroutineDispatcherProvider.io() + CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            dispatchListUseCase.addNotifiedDispatchToTheDispatchList(dispatchListUseCase.getDispatches(),dispatch.cid.toString(),dispatch.vehicleNumber.trim(),dispatch.dispid)
        }
    }

    fun hasOnlyOneDispatchOnList() : Flow<Boolean> = flow {
        emit(
            dispatchValidationUseCase.hasOnlyOne()
        )
    }

    fun updateDispatchesQuantity(quantity:Int){
        viewModelScope.launch(coroutineDispatcherProvider.io()+CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            dispatchValidationUseCase.updateQuantity(quantity)
        }
    }

    private fun setHasAnActiveDispatchListener() = dispatchValidationUseCase.hasAnActiveDispatchListener

    fun hasAnActiveDispatch(): Flow<Boolean> = flow {
        emit(
            dispatchValidationUseCase.hasAnActiveDispatch()
        )
    }

    fun restoreSelectedDispatch(
        delayResolver: DelayResolver = DelayProvider(),
        executeAction :() -> Unit
    ) {
        viewModelScope.launch(
            CoroutineName("$TRIP_LIST$VIEW_MODEL")+coroutineDispatcherProvider.io()
        ) {
            appModuleCommunicator.restoreSelectedDispatch()
            delayResolver.callDelay(DELAY_TO_AVOID_FREEZING_ANIMATION)
            executeAction()
        }
    }
    private fun getOneMinuteDelayRemovalFlag(){
        viewModelScope.launch(coroutineDispatcherProvider.io()) {
            dispatchListUseCase.getRemoveOneMinuteDelayFeatureFlag()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun getAppLauncherVersion(packageManager : PackageManager) : Long {
        Utils.getAppLauncherVersion(packageManager).let { appLauncherCode ->
            return appLauncherCode
        }
    }

    internal fun getAppLauncherMapsPerformanceFixVersionFromFireStore() = flow {
        appModuleCommunicator.getFeatureFlags().getFeatureFlagDataAsLong(FeatureGatekeeper.KnownFeatureFlags.LAUNCHER_MAPS_PERFORMANCE_FIX_VERSION).let { version ->
            if(version != -1L) APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE = version
        }
        if(APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE == INT_MAX)
            APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE = dispatchListUseCase.getAppLauncherMapsPerformanceFixVersionFromFireStore(appModuleCommunicator.getFeatureFlags())
        emit(Unit)
    }

    private fun saveAppLauncherVersionInDataStore(appLauncherCode : Long) {
        val invalidVersionCode : Long = -1
        viewModelScope.launch(coroutineDispatcherProvider.io() + CoroutineName("$TRIP_LIST$VIEW_MODEL")) {
            if (appLauncherCode == invalidVersionCode) return@launch
            saveAppLauncherVersionStatusForMapsPerformanceFixInDataStore(appLauncherCode >= APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE)
        }
    }

    private suspend fun saveAppLauncherVersionStatusForMapsPerformanceFixInDataStore(isAppLauncherWithMapsPerfFixInstalled : Boolean) {
        dispatchListUseCase.getLocalDataSourceRepo()
            .setToAppModuleDataStore(DataStoreManager.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED, isAppLauncherWithMapsPerfFixInstalled)
        isAppLauncherWithMapsPerformanceFixInstalled = isAppLauncherWithMapsPerfFixInstalled
        Log.d("$TRIP_LIST$VIEW_MODEL", "isOfALWithMapsPerformanceFix $isAppLauncherWithMapsPerfFixInstalled")
    }

    fun getTripStartEventReasons(oldestDispatch: Dispatch): String {
        return dispatchListUseCase.getTripStartEventReasons(oldestDispatch)
    }

    private fun resetIsDraftView() = viewModelScope.launch(coroutineDispatcherProvider.io()) {
        dispatchListUseCase.getLocalDataSourceRepo().setToFormLibModuleDataStore(
            FormDataStoreManager.IS_DRAFT_VIEW, false)
    }

    internal fun processDispatchNotificationData(dispatchData: Dispatch?, intentAction: String?) {
        dispatchData?.let { dispatch ->
            if (dispatch.dispid.isNotEmpty()) {
                Log.d("$TRIP_LIST$VIEW_MODEL", "TripFromIntent${dispatch.dispid}")
                addNotifiedDispatchToTheDispatchList(dispatch)
            } else Log.w(
                "$TRIP_LIST$VIEW_MODEL", Utils.getIntentDataErrorString(
                    application, "TripId", "String", "empty", intentAction
                )
            )
        } ?: Log.w(
            "$TRIP_LIST$VIEW_MODEL", Utils.getIntentDataErrorString(
                application, "Dispatch", "data class", "null", intentAction
            )
        )
    }

    internal fun performStateAndUiUpdateUponCreatedLifecycle(updateStopMenuItemVisibility:(Boolean) -> Unit, isActiveDispatchAndHasOnlyOneDispatch: (Boolean) -> Unit) {
        resetIsDraftView()
        getOneMinuteDelayRemovalFlag()

        getAppLauncherMapsPerformanceFixVersionFromFireStore().onEach {
            saveAppLauncherVersionInDataStore(
                getAppLauncherVersion(
                    application.packageManager
                )
            )
        }.launchIn(viewModelScope)

        setHasAnActiveDispatchListener().onEach { hasActiveDispatch ->
            updateStopMenuItemVisibility(hasActiveDispatch)
        }.launchIn(viewModelScope)

        dispatchListFlow.onEach { dispatches ->
            Log.d("$TRIP_LIST$VIEW_MODEL", "ObserveTrips${dispatches.map { it.dispid }}")
            isActiveDispatchAndHasOnlyOneDispatch(dispatchValidationUseCase.hasAnActiveDispatch() && dispatches.size == 1)
        }.launchIn(viewModelScope)
    }

    internal fun doAfterAllPermissionsGranted() = viewModelScope.launch(coroutineDispatcherProvider.io()) {
        cacheBackboneData()
        application.startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
        if (hasBackboneDataChanged()) {
            Log.n("$TRIP_LIST$VIEW_MODEL", "BackboneDataChange")
            clearDispatches()
        }
    }

    fun updateDispatchInfoToDataStore(dispatchId:String, dispatchName:String, caller:String){
        viewModelScope.launch(coroutineDispatcherProvider.io()) {
            tripStartUseCase.updateDispatchInfoInDataStore(dispatchId,dispatchName,caller)
        }
    }

    fun canShowHotKeysMenu() {
        viewModelScope.launch(coroutineDispatcherProvider.main()) {
            val obcId = appModuleCommunicator.doGetObcId()
            if(obcId.isNotEmpty()) {
                formLibraryUseCase.getHotKeysWithoutDescription(HOTKEYS_COLLECTION_NAME, obcId).collectLatest {
                    _isHotKeysAvailable.postValue(it.isNotEmpty())
                }
            } else {
                _isHotKeysAvailable.postValue(false)
            }
        }
    }

}