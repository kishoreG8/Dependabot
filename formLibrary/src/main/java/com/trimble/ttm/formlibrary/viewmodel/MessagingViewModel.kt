package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Intent
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.usecases.TrashUseCase
import com.trimble.ttm.formlibrary.utils.DelayProvider
import com.trimble.ttm.formlibrary.utils.DelayResolver
import com.trimble.ttm.formlibrary.utils.HOTKEYS_COLLECTION_NAME
import com.trimble.ttm.formlibrary.utils.Utils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


const val DELAY_TO_AVOID_FREEZING_ANIMATION = 270L

class MessagingViewModel(
    private val application: Application,
    private val sentUseCase: SentUseCase,
    private val draftUseCase: DraftUseCase,
    private val inboxUseCase: InboxUseCase,
    private val trashUseCase: TrashUseCase,
    private val authenticateUseCase: AuthenticateUseCase,
    private val formLibraryUseCase: FormLibraryUseCase,
    edvirFormUseCase: EDVIRFormUseCase,
    formDataStoreManager: FormDataStoreManager,
    private val appModuleCommunicator: AppModuleCommunicator
) : AuthenticationViewModel(
    edvirFormUseCase,
    authenticateUseCase,
    formDataStoreManager,
    application
) {
    init {
        observeForNetworkConnectivityChange()
    }
    private val tag = "MessagingVM"
    var mapFieldIdsToViews = mutableMapOf<Int, View>()

    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable
    internal var isMessagingSectionSelectedFromDispatchScreenNavMenu: Boolean = false

    private val _tabPosition = MutableLiveData<Int>()
    val tabPosition: LiveData<Int> = _tabPosition

    private val _enableTabs = MutableLiveData<Boolean>()
    val enabledTabs: LiveData<Boolean> = _enableTabs

    private val _shouldFinishFragment = MutableLiveData<Boolean>()
    val shouldFinishFragment: LiveData<Boolean> = _shouldFinishFragment

    private val _shouldGoToListStart = MutableLiveData<Boolean>()
    val shouldGoToListStart: LiveData<Boolean> = _shouldGoToListStart
    private var _isHotKeysAvailable = MutableLiveData<Boolean>()
    val isHotKeysAvailable : LiveData<Boolean> = _isHotKeysAvailable
    private var _isAuthenticationCompleted = MutableLiveData<Boolean>()
    val isAuthenticationCompleted : LiveData<Boolean> = _isAuthenticationCompleted

    fun changeNetworkAvailabilityStatus(isNetworkAvailable: Boolean) {
        _isNetworkAvailable.value = isNetworkAvailable
    }

    fun setCurrentTabPosition(tabPosition: Int?) {
        _tabPosition.value = tabPosition!!
    }

    fun setEnableTabs() {
        _enableTabs.value = true
    }

    fun setShouldFinishFragment(value: Boolean) {
        _shouldFinishFragment.value = value
    }

    fun setShouldGoToListStart(value: Boolean) {
        _shouldGoToListStart.value = value
    }

    suspend fun hasActiveDispatch(): Boolean = appModuleCommunicator.hasActiveDispatch("ToUpdateSideMenu",false)

    override fun onCleared() {
        sentUseCase.resetPagination()
        draftUseCase.resetPagination()
        inboxUseCase.resetPagination()
        trashUseCase.resetPagination()
        sentUseCase.clearRegistration()
        draftUseCase.clearRegistration()
        trashUseCase.clearRegistration()
        isMessagingSectionSelectedFromDispatchScreenNavMenu = false
        super.onCleared()
    }

    private fun observeForNetworkConnectivityChange() {
        viewModelScope.launch(defaultDispatcherProvider.io()) {
            listenToNetworkConnectivityChange()
                .collectLatest { isAvailable ->
                    if(!isAvailable){
                        withContext(defaultDispatcherProvider.main()) {
                            Utils.noInternetAvailableToast(application)
                        }
                    }
                }
        }
    }

    fun hasOnlyOneTripOnList() : Flow<Boolean> = flow {
        emit(
            appModuleCommunicator.hasOnlyOneDispatchOnList()
        )
    }

    fun restoreSelectedDispatch(
        delayResolver: DelayResolver = DelayProvider(),
        executeActionInDispatch :() -> Unit,
        executeActionWithoutDispatch :() -> Unit
    )  {
        viewModelScope.launch(
            CoroutineName(tag)+defaultDispatcherProvider.io()
        ) {
            if( appModuleCommunicator.getCurrentWorkFlowId("MessagingViewModel.restoreSelectedDispatch").isNotEmpty()) {
                appModuleCommunicator.restoreSelectedDispatch()
                delayResolver.callDelay(DELAY_TO_AVOID_FREEZING_ANIMATION)
                executeActionInDispatch()
            } else {
                executeActionWithoutDispatch()
            }
        }
    }

    suspend fun getAuthenticationProcessResult() = authenticateUseCase.getAuthenticationProcessResult()

    fun recordShortCutIconClickEvent(eventName : String, intent: Intent?) {
        if(eventName.isNotEmpty() && intent != null) {
            inboxUseCase.recordShortCutIconClickEvent(eventName,intent.categories)
        }
    }

    fun changeAuthenticationCompletedStatus(value: Boolean) {
        _isAuthenticationCompleted.value = value
    }

    fun canShowHotKeysMenu() {
        viewModelScope.launch(defaultDispatcherProvider.main()) {
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