package com.trimble.ttm.formlibrary.usecases

import androidx.lifecycle.MutableLiveData
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.INTENT_CATEGORY_LAUNCHER
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.repo.FormLibraryRepo
import com.trimble.ttm.formlibrary.utils.FAILED_TO_FETCH_HOTKEYS
import com.trimble.ttm.formlibrary.utils.FORMS
import com.trimble.ttm.formlibrary.utils.FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FormLibraryUseCase(
    private val formLibraryRepo: FormLibraryRepo,
    private val cacheGroupsUseCase: CacheGroupsUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) {
    private val tag = "FormLibraryUC"
    private lateinit var applicationScopeJob: Job
    private lateinit var firestoreExceptionCollectorJob: Job

    fun checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
        timePair: Pair<Long, Long>,
        applicationScope: CoroutineScope,
        hasActiveInternet: Boolean
    ): Boolean {
        if (timePair.first - timePair.second > FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL && hasActiveInternet) {
            cacheGroupIdsFormIdsAndUserIdsFromServer(
                applicationScope,
                tag
            )
            return true
        }
        return false
    }

    fun listenForFirestoreException(applicationScope: CoroutineScope, tag: String, hasActiveInternet: Boolean) {
        firestoreExceptionCollectorJob = applicationScope.launch(dispatcherProvider.io() + SupervisorJob()) {
            cacheGroupsUseCase.getFirestoreExceptionNotifier().collect {
                Log.d(tag, "listenForFirestoreException hasActiveInternet: $hasActiveInternet")
                if (hasActiveInternet) cacheGroupIdsFormIdsAndUserIdsFromServer(applicationScope, tag)
            }
        }
    }

    fun cacheGroupIdsFormIdsAndUserIdsFromServer(
        applicationScope: CoroutineScope,
        tag: String
    ) {
        if (::applicationScopeJob.isInitialized && applicationScopeJob.isActive) applicationScopeJob.cancel()
        applicationScopeJob = applicationScope.launch(dispatcherProvider.io() + SupervisorJob()) {
            val cid = appModuleCommunicator.doGetCid()
            val obcId = appModuleCommunicator.doGetObcId()
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                cid,
                obcId,
                applicationScope = applicationScope,
                tag
            ).also { isSyncSuccess ->
                if (isSyncSuccess) getFormsOfCustomer(cid, obcId.toLong(), isSyncSuccess)
            }
        }
    }

    suspend fun getHotkeysWithDescription(
        path: String,
        documentId: String,
        hotKeys: MutableStateFlow<MutableSet<HotKeys>>,
        isHotKeysAvailable: MutableLiveData<Boolean>,
        selectedTabIndex: MutableStateFlow<String>,
        hotKeysScreenState: MutableStateFlow<ScreenContentState>,
        isInternetAvailable: Boolean
    ) {
        formLibraryRepo.addHotkeysSnapshotFlow(path, documentId).collectLatest { hotKeysSet ->
            if(hotKeysSet.isNotEmpty()) {
                isHotKeysAvailable.postValue(true)
                val description = formLibraryRepo.getHotKeysDescription(hotKeysSet, isInternetAvailable)
                if(description.isNotEmpty()) {
                    hotKeys.emit(
                        description.sortedBy { it.hkId }
                            .toMutableSet()
                    )
                    hotKeysScreenState.emit(ScreenContentState.Success())
                } else {
                    hotKeys.emit(mutableSetOf())
                    hotKeysScreenState.emit(ScreenContentState.Error(FAILED_TO_FETCH_HOTKEYS))
                }
            } else {
                isHotKeysAvailable.postValue(false)
                selectedTabIndex.value = FORMS
                hotKeys.emit(mutableSetOf())
            }
        }
    }

    suspend fun getHotKeysCount(path: String, documentID: String, isInternetAvailable : Boolean) = formLibraryRepo.getHotKeysCount(path, documentID, isInternetAvailable)

    fun getHotKeysWithoutDescription(path: String, documentId: String) = formLibraryRepo.addHotkeysSnapshotFlow(path, documentId)

    suspend fun getFormListFlow() = formLibraryRepo.getFormDefListFlow()

    suspend fun getFormsOfCustomer(customerId: String, obcId: Long, isServerSync: Boolean = false) =
        formLibraryRepo.getForms(customerId, obcId, isServerSync)

    fun didLastFormReached() = formLibraryRepo.didLastItemReached()

    fun resetPagination() {
        if (::applicationScopeJob.isInitialized) {
            applicationScopeJob.cancel()
        }
        if (::firestoreExceptionCollectorJob.isInitialized) {
            firestoreExceptionCollectorJob.cancel()
        }
        formLibraryRepo.resetPagination()
    }

    fun getDriverOriginatedFormWithRecipients(forms: List<FormDef>): List<FormDef> = forms
        .filter { it.driverOriginate == 1 && it.recipients.isNotEmpty() }

    fun sortFormListByName(forms: MutableSet<FormDef>) = cacheGroupsUseCase.sortFormsAlphabetically(forms)

    fun recordShortCutIconClickEvent(eventName: String, intentCategoriesSet: Set<String>?) {
        if(intentCategoriesSet?.contains(INTENT_CATEGORY_LAUNCHER) == true) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(eventName)
        }
    }

    suspend fun addFavourite(favourite: Favourite, driverId: String) = formLibraryRepo.addFavourite(favourite, driverId )

    fun getFavouriteForms(path: String, driverID: String) = formLibraryRepo.getFavouriteForms(path, driverID)
    suspend fun removeFavourite(formid: String, driverId: String)  = formLibraryRepo.removeFavourite(formid, driverId)

}
