package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.logger.FAVOURITES
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORMS
import com.trimble.ttm.formlibrary.utils.FORM_GROUP_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.HOTKEYS
import com.trimble.ttm.formlibrary.utils.HOTKEYS_COLLECTION_NAME
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

data class Forms(val formDef: FormDef = FormDef(), var isFavourite: Boolean = false)

private const val FAVOURITE_COLLECTION_NAME = "FormsFavouriteCollection"

class FormLibraryViewModel(
    private val application: Application,
    private val formLibraryUseCase: FormLibraryUseCase,
    authenticateUseCase: AuthenticateUseCase,
    val dispatchers: DispatcherProvider,
    private val formDataStoreManager: FormDataStoreManager,
    edvirFormUseCase: EDVIRFormUseCase,
    val appModuleCommunicator: AppModuleCommunicator
) : AuthenticationViewModel(
    edvirFormUseCase,
    authenticateUseCase,
    formDataStoreManager,
    application
) {

    private val tag = "FormLibraryVM"
    private val _formList = MutableStateFlow<MutableSet<Forms>>(mutableSetOf())
    val formList: StateFlow<MutableSet<Forms>> = _formList

    private val _hotKeys = MutableStateFlow<MutableSet<HotKeys>>(mutableSetOf())
    val hotKeys: StateFlow<MutableSet<HotKeys>> = _hotKeys

    private val _favouriteForms = MutableStateFlow<MutableSet<Favourite>>(mutableSetOf())
    val favouriteForms: StateFlow<MutableSet<Favourite>> = _favouriteForms

    private var _formsMutableScreenState = MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
    val formsScreenState: StateFlow<ScreenContentState> = _formsMutableScreenState

    private var formListUIState = mutableMapOf<Double, FormDef>()
    private val _isLastFormReceived = MutableLiveData<Boolean>()
    val isLastFormReceived: LiveData<Boolean> = _isLastFormReceived
    internal var expiryTimeForFormFetchFromServer = Date().time
    private val formsAndHotKeysMutableScreenState =
        MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
    internal val formsAndHotKeysScreenContentState = formsAndHotKeysMutableScreenState.asStateFlow()
    private val _selectedTabIndex = MutableStateFlow(EMPTY_STRING)
    val selectedTabIndex: StateFlow<String> = _selectedTabIndex
    private val _isHotKeysAvailable = MutableLiveData<Boolean>()
    val isHotKeysAvailable: LiveData<Boolean> = _isHotKeysAvailable
    private val _hotKeysScreenState = MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
    val hotKeysScreenState: StateFlow<ScreenContentState> = _hotKeysScreenState
    private val _isPaginationLoading = MutableStateFlow(false)
    val isPaginationLoading: StateFlow<Boolean> = _isPaginationLoading
    private val _isListView = MutableStateFlow(true)
    val isListView: StateFlow<Boolean> = _isListView
    private val _canDisplaySearch = MutableStateFlow(false)
    val canDisplaySearch: StateFlow<Boolean> = _canDisplaySearch

    private var currentPage = 0

    init {
        viewModelScope.launch(dispatchers.io()) {
            if (appModuleCommunicator.isFirebaseAuthenticated()) {
                observeInternetConnectivityAndCacheFormsIfRequired()
            }
        }
    }

    fun observeInternetConnectivityAndCacheFormsIfRequired() {
        viewModelScope.launch(dispatchers.io()) {
            listenToNetworkConnectivityChange().collectLatest { isActive ->
                Log.d(tag, "observeInternetConnectivityAndCacheFormsIfRequired isInternetActive: $isActive")
                if (isActive.not()) {
                    withContext(dispatchers.main()) {
                        Utils.noInternetAvailableToast(application)
                    }
                }
                cacheFormIdsAndUserIdsFromServer()
            }
        }
        observeLastFormReached()
    }

    internal fun cacheFormIdsAndUserIdsFromServer() {
        appModuleCommunicator.getAppModuleApplicationScope().launch {
            isActiveInternetAvailable().let { isActiveInternetAvailable ->
                Log.d(tag, "cacheFormIdsAndUserIdsFromServer, From replayCache isActiveInternetAvailable: $isActiveInternetAvailable")
                formLibraryUseCase.listenForFirestoreException(
                    applicationScope = this,
                    tag, isActiveInternetAvailable
                )
            }
            formLibraryUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
                applicationScope = this,
                tag
            )
        }
    }

    internal fun checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime() {
        formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
            Pair(Date().time, expiryTimeForFormFetchFromServer),
            appModuleCommunicator.getAppModuleApplicationScope(),
            isActiveInternetAvailable()
        )
        updateExpiryTimeForFormFetchFromServer(Date().time)
    }

    internal fun updateExpiryTimeForFormFetchFromServer(time: Long) {
        expiryTimeForFormFetchFromServer = time
    }

    suspend fun getDriverOriginatedForms(dispatcher: CoroutineDispatcher = dispatchers.main()) {
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetObcId()
                .isEmpty()
        ) {
            _formsMutableScreenState.value =
                ScreenContentState.Error(errorMessage = application.getString(R.string.err_loading_driver_originated_form_list))
            Log.e(
                tag,
                "Error while fetching customerId/obcId for form fetch",
                null,
                "customer id" to appModuleCommunicator.doGetCid(),
                "obc id" to appModuleCommunicator.doGetObcId()
            )
            return
        }
        viewModelScope.launch(dispatchers.main() + CoroutineName("$tag Get driver forms")) {
            formLibraryUseCase.getFormListFlow()
                .catch {
                    withContext(dispatcher) {
                        _formsMutableScreenState.value =
                            ScreenContentState.Error(errorMessage = application.getString(R.string.unable_to_fetch_forms))
                    }
                }
                .collect { formDefMap ->
                    withContext(dispatcher) {
                        _formsMutableScreenState.value = ScreenContentState.Success()
                    }
                    if (formDefMap.isNotEmpty()) {
                        formListUIState.putAll(formDefMap)
                        formLibraryUseCase.sortFormListByName(formListUIState.values.toMutableSet())
                            .onEach { formDef -> formDef.name = formDef.name.trim() }
                            .also { forms ->
                                _formList.emit(
                                    forms.map { Forms(it) }.toMutableSet()
                                )
                                _isPaginationLoading.value = false
                            }
                    } else {
                        if (formDataStoreManager.getValue(
                                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                                true
                            ) && isActiveInternetAvailable()
                        ) {
                            return@collect
                        }
                        if (currentPage == 0) {
                            _formsMutableScreenState.value =
                                ScreenContentState.Error(errorMessage = application.getString(R.string.no_forms_available))
                        }
                    }
                }
        }
        viewModelScope.launch(dispatchers.main() + CoroutineName("$tag Get customer forms")) {
            formLibraryUseCase.getFormsOfCustomer(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId().toSafeLong()
            )
        }
    }

    suspend fun fetchFormsWithPagination() {
        if (_isPaginationLoading.value) return
        _isPaginationLoading.value = true
        getDriverOriginatedForms()
        currentPage++
    }

    private fun observeLastFormReached() {
        viewModelScope.launch {
            formLibraryUseCase.didLastFormReached().collect {
                _isLastFormReceived.value = it
            }
        }
    }

    fun recordShortCutIconClickEvent(eventName : String, intent: Intent?) {
        if(eventName.isNotEmpty() && intent != null) {
            val selectedIndex = intent.extras?.getString(FORM_GROUP_TAB_INDEX) ?: FORMS
            if(selectedIndex == FORMS) {
                formLibraryUseCase.recordShortCutIconClickEvent(eventName, intent.categories)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentPage = 0
        _formsMutableScreenState.value = ScreenContentState.Success()
        formLibraryUseCase.resetPagination()
    }

    fun updateFavourite(favourite: Favourite) {
        viewModelScope.launch {
            val currentUser = appModuleCommunicator.doGetCurrentUser(appModuleCommunicator.getAppModuleApplicationScope())
            if(currentUser.isNotEmpty()) {
                formLibraryUseCase.addFavourite(favourite, currentUser)
            } else {
                Log.e(FAVOURITES, "Current user is empty while updating favourite forms")
            }
        }
    }

    fun getFavouriteForms() {
        viewModelScope.launch {
            val currentUser = appModuleCommunicator.doGetCurrentUser(appModuleCommunicator.getAppModuleApplicationScope())
            if (currentUser.isNotEmpty()) {
                formLibraryUseCase.getFavouriteForms(
                    FAVOURITE_COLLECTION_NAME,
                    currentUser
                ).collectLatest { favourites ->
                    _favouriteForms.value = favourites
                    _formList.value.forEach {
                        it.isFavourite =
                            it.formDef.formid.toString() in _favouriteForms.value.map { it.formId }
                    }
                }
            } else {
                Log.e(FAVOURITES, "Current user is empty while fetching favourite forms")
            }
        }
    }

    fun removeFavourite(formId: String) {
        viewModelScope.launch {
            val currentUser = appModuleCommunicator.doGetCurrentUser(appModuleCommunicator.getAppModuleApplicationScope())
            if(formId.isNotEmpty() && currentUser.isNotEmpty()) {
                formLibraryUseCase.removeFavourite(formId, currentUser)
            } else {
                Log.e(FAVOURITES, "Current user is empty while removing favourite forms")
            }
        }
    }

    fun getHotkeys() {
        viewModelScope.launch {
            val obcId = appModuleCommunicator.doGetObcId()
            if(obcId.isNotEmpty()) {
                formLibraryUseCase.getHotkeysWithDescription(
                    HOTKEYS_COLLECTION_NAME,
                    obcId,
                    _hotKeys,
                    _isHotKeysAvailable,
                    _selectedTabIndex,
                    _hotKeysScreenState,
                    isActiveInternetAvailable()
                )
            } else {
                _hotKeysScreenState.value = ScreenContentState.Error(application.getString(R.string.unable_to_fetch_hot_keys))
                Log.e(HOTKEYS, "Obc id is empty while fetching hot keys")
            }
        }
    }



    fun getTabListBasedOnHotKeys(isHotKeysAvailable : Boolean) : List<String> {
        val tabList = mutableListOf(HOTKEYS, FORMS)
        if(!isHotKeysAvailable) {
            tabList.remove(HOTKEYS)
        }
        return tabList
    }

    fun filterFormsBasedOnSearchText(items: Set<Forms>, searchText: String): MutableList<Forms> {
        return if (searchText.isBlank()) items.toMutableList() else items.filter {
            it.formDef.name.contains(
                searchText,
                true
            )
        }.toMutableList()
    }

    // Whenever the number of items in the grid is less than or equal to 4, the grid will have 2 columns.
    // Otherwise, the grid will have 4 columns.
    fun getGridCellsCountBasedOnItemsSize(hotKeysItemCount: Int): Int {
        return if (hotKeysItemCount <= 4) 2 else 4
    }

    fun changeSelectedTab(selectedTab : String) {
        _selectedTabIndex.value = selectedTab
    }

    fun canDisplaySearch(selectedTab: String)  {
        _canDisplaySearch.value = selectedTab == FORMS
    }

    fun canShowHotKeysTab() {
        viewModelScope.launch(dispatchers.main()) {
            val obcId = appModuleCommunicator.doGetObcId()
            if (obcId.isNotEmpty()) {
                _isHotKeysAvailable.value = formLibraryUseCase.getHotKeysCount(
                    path = HOTKEYS_COLLECTION_NAME,
                    documentID = obcId,
                    isInternetAvailable = isActiveInternetAvailable()
                ) > 0
                formsAndHotKeysMutableScreenState.value = ScreenContentState.Success()
            } else {
                // If we're not able to retrieve the hot keys availability due to obc id being empty
                // then we need to show forms tab by default so returning success state here
                formsAndHotKeysMutableScreenState.value = ScreenContentState.Success()
                Log.e(HOTKEYS, "Obc Id is empty while fetching hot keys availability")
            }
        }
    }

    fun changeListView(isListView : Boolean) {
        _isListView.value = !isListView
    }

    fun getMaxLinesForHotKeys(hotKeysItemCount: Int) : Int {
        return when {
            hotKeysItemCount > 12 -> 2
            hotKeysItemCount in 9..12 -> 3
            else -> 6
        }
    }
}