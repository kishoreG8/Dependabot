package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.logger.TRIP_VALIDATION
import com.trimble.ttm.commons.logger.USE_CASE
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

interface DispatchValidation {

    val hasAnActiveDispatchListener: SharedFlow<Boolean>

    suspend fun hasAnActiveDispatch() : Boolean

    suspend fun restoreSelected(dispatchId:String)

    suspend fun updateNameOnSelected(dispatchName:String)

    suspend fun hasOnlyOne() : Boolean

    suspend fun updateQuantity(quantity:Int)

}

class DispatchValidationUseCase(
    private val dataStoreManager: DataStoreManager,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : DispatchValidation {

    private val VALIDATION_LOG_TAG="validation"

    private val _hasAnActiveDispatch = MutableSharedFlow<Boolean>(0)
    private val tag = "DispatchValidationUC"
    override val hasAnActiveDispatchListener : SharedFlow<Boolean> = _hasAnActiveDispatch

    init {
        setHasDispatchObserver()
    }

    private fun setHasDispatchObserver() {
        scope.launch(
            dispatcher + CoroutineName("$tag Has dispatch observer")
        ) {
            dataStoreManager.fieldObserver(
                DataStoreManager.ACTIVE_DISPATCH_KEY
            ).onEach {
                if (it!=null){
                    _hasAnActiveDispatch.emit(
                        it.isNotEmpty()
                    )
                }else{
                    _hasAnActiveDispatch.emit(
                        false
                    )
                }
            }.launchIn(this)
        }
    }

    override suspend fun hasAnActiveDispatch() : Boolean {
        return dataStoreManager.hasActiveDispatch(
            caller = TRIP_VALIDATION,
            logError = false
        )
    }

    override suspend fun restoreSelected(dispatchId:String){
        Log.d("$TRIP_LIST$VALIDATION_LOG_TAG$USE_CASE","restoreActiveTrip$dispatchId")
        dataStoreManager.setValue(DataStoreManager.SELECTED_DISPATCH_KEY, dispatchId)
        dataStoreManager.setValue(
            DataStoreManager.DISPATCH_NAME_KEY,
            dataStoreManager.getValue(
                DataStoreManager.CURRENT_DISPATCH_NAME_KEY,
                EMPTY_STRING
            )
        )
    }

    override suspend fun updateNameOnSelected(dispatchName:String){
        dataStoreManager.setValue(
            DataStoreManager.CURRENT_DISPATCH_NAME_KEY,
            dispatchName
        )
    }

    override suspend fun hasOnlyOne() = dataStoreManager.getValue(DataStoreManager.DISPATCHES_QUANTITY,0) == 1

    override suspend fun updateQuantity(quantity:Int){
        dataStoreManager.setValue(DataStoreManager.DISPATCHES_QUANTITY,quantity)
    }

}