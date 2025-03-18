package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.perf.metrics.AddTrace
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.usecases.EDVIRInspectionsUseCase
import com.trimble.ttm.formlibrary.utils.GET_INSPECTION_HISTORY_EDVIRINSPECTIONVIEWMODEL
import com.trimble.ttm.formlibrary.utils.Utils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

private const val INSPECTION_HISTORY_THRESHOLD_DAYS = 14

class EDVIRInspectionsViewModel(
    private val application: Application,
    private val eDVIRInspectionsUseCase: EDVIRInspectionsUseCase,
    private val appModuleCommunicator: AppModuleCommunicator
) : NetworkConnectivityListenerViewModel(appModuleCommunicator) {

    private val tag = "EDVIRInspectionsVM"
    private val customerIdLogKey = "customer id"
    private val obcIdLogKey = "obc id"
    private var _canShowInspectionMenu = MutableLiveData<Boolean>()
    val canShowInspectionMenu: LiveData<Boolean> = _canShowInspectionMenu
    private val inspections: MutableList<EDVIRInspection> = mutableListOf()
    private val _inspections = MutableLiveData<List<EDVIRInspection>>()
    val inspectionsList: LiveData<List<EDVIRInspection>> = _inspections
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    private val _errorDataForToast = MutableLiveData<String>()
    val errorDataForToast: LiveData<String> = _errorDataForToast
    lateinit var currentUser: UserName

    fun isCurrentUserInitialised() = ::currentUser.isInitialized

    suspend fun retrieveCurrentUserInfo() {
        try {
            currentUser = eDVIRInspectionsUseCase.getCurrentUser(appModuleCommunicator)
            if (currentUser.userId.isEmpty()) {
                Log.e(
                    tag,
                    "Current user is empty",
                    null,
                    customerIdLogKey to appModuleCommunicator.doGetCid(),
                    obcIdLogKey to appModuleCommunicator.doGetObcId()
                )
                _errorDataForToast.value =
                    application.getString(R.string.err_current_driver_not_available)
            } else {
                val userEldStatus = eDVIRInspectionsUseCase.getUserEldStatus(appModuleCommunicator)
                if (userEldStatus.isNullOrEmpty()) {
                    Log.e(
                        tag,
                        "Current user eld status is null or empty",
                        null,
                        customerIdLogKey to appModuleCommunicator.doGetCid(),
                        obcIdLogKey to appModuleCommunicator.doGetObcId()
                    )
                    _errorDataForToast.value =
                        application.getString(R.string.err_duty_status_not_available)
                } else {
                    _canShowInspectionMenu.value =
                        eDVIRInspectionsUseCase.canUserPerformManualInspection()
                }
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception occurred while fetching CurrentUser or UserEldStatus from Backbone.",
                e,
                customerIdLogKey to appModuleCommunicator.doGetCid(),
                obcIdLogKey to appModuleCommunicator.doGetObcId()
            )
        }
    }

    @AddTrace(name = GET_INSPECTION_HISTORY_EDVIRINSPECTIONVIEWMODEL, enabled = true)
    suspend fun getInspectionsHistory() {
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetObcId()
                .isEmpty()
        ) {
            _errorData.value = application.getString(R.string.err_loading_inspections_list)
            Log.e(
                tag,
                "Error while fetching inspections history",
                null,
                customerIdLogKey to appModuleCommunicator.doGetCid(),
                obcIdLogKey to appModuleCommunicator.doGetObcId()
            )
            return
        }
        viewModelScope.safeLaunch(CoroutineName("$tag Inspection history")) {
            eDVIRInspectionsUseCase.getInspectionHistoryAsFlow()
                .safeCollect("$tag Inspection history") { inspectionsList ->
                    inspections.clear()
                    inspectionsList.forEach {
                        inspections.add(it)
                    }
                    if (inspections.isEmpty()) {
                        _errorData.value = application.getString(R.string.no_inspection_history)
                    } else {
                        inspections.sortWith { inspection1, inspection2 ->
                            inspection1.createdAt.compareTo(
                                inspection2.createdAt
                            )
                        }
                        _inspections.postValue(inspections.reversed())
                    }
                }
        }
        viewModelScope.launch(CoroutineName("$tag listen to Inspection history")) {
            eDVIRInspectionsUseCase.listenToInspectionHistory(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                Utils.getSubtractedTimeInMillis(INSPECTION_HISTORY_THRESHOLD_DAYS)
            )
        }
    }
}