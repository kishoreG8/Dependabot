package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DraftingRepoImpl(
    private val formDataStoreManager: FormDataStoreManager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : DraftingRepo {

    private val TAG = "DraftingRepoImpl"

    private val _draftProcessFinished = MutableSharedFlow<Boolean>(0)

    override val draftProcessFinished: SharedFlow<Boolean>
        get() = _draftProcessFinished

    private val _initDraftProcessing =  MutableSharedFlow<Boolean>(0)

    override val initDraftProcessing: SharedFlow<Boolean>
        get() = _initDraftProcessing

    override suspend fun setInitDraftProcessing(isStarted: Boolean) {
        _initDraftProcessing.emit(isStarted)
    }

    override suspend fun setDraftProcessFinished(isFinished: Boolean) {
        _draftProcessFinished.emit(isFinished)
    }

    init {
        setObserverCloseEvent()
    }

    private fun setObserverCloseEvent(){
        scope.launch (
            dispatcher + CoroutineName(TAG)
        ) {
            formDataStoreManager.fieldObserver(
                FormDataStoreManager.CLOSE_FIRST
            ).onEach {
                it?.let {
                    _initDraftProcessing.emit(it)
                }
            }.launchIn(this)
        }
    }

    override suspend fun restoreDraftProcessFinished(){
        _draftProcessFinished.emit(false)
    }

    override suspend fun restoreInitDraftProcessing(){
        _initDraftProcessing.emit( false)
        formDataStoreManager.setValue(
            FormDataStoreManager.CLOSE_FIRST,
            false
        )
    }

}