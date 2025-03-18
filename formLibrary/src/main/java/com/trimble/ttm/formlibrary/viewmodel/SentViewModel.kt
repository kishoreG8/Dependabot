package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.utils.END_REACHED
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class SentViewModel(
    private val application: Application,
    private val sentUseCase: SentUseCase,
    private val firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    internal val dispatchers: DispatcherProvider
) : ViewModel(), KoinComponent {
    private val tag = "SentVM"
    private val _messages = MutableLiveData<MutableSet<MessageFormResponse>>()
    val messages: LiveData<MutableSet<MessageFormResponse>> = _messages
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    var userSelectedItemsOnMessageListAdapter = mutableSetOf<MessageFormResponse>()
    var messageListOnMessageListAdapter = mutableSetOf<MessageFormResponse>()
    var multiSelectOnMessageListAdapter = false
    var originalMessageListCopy: MutableSet<MessageFormResponse> = mutableSetOf()
    var isDeleteImageButtonVisible = false
    private val _isLastItemReceived = MutableLiveData<Boolean>()
    val isLastItemReceived: LiveData<Boolean> = _isLastItemReceived

    private val _collectionDeleteResponse : MutableStateFlow<CollectionDeleteResponse?> = MutableStateFlow(
        null
    )
    val collectionDeleteResponse: StateFlow<CollectionDeleteResponse?> = _collectionDeleteResponse

    init {
        observeLastMessageReached()
    }

    fun getMessages(isFirstTimeFetch: Boolean) {
        viewModelScope.launch(dispatchers.io() + CoroutineName(tag)) {
            if (appModuleCommunicator.doGetCid()
                    .isEmpty() || appModuleCommunicator.doGetTruckNumber()
                    .isEmpty()
            ) {
                withContext(dispatchers.main()){
                    _errorData.value =
                        application.getString(R.string.err_loading_messages)
                    Log.e(
                        tag,
                        "Error while fetching customerId and vehicle for message form response fetch",
                        null,
                        "customer id" to appModuleCommunicator.doGetCid(),
                        "vehicle id" to appModuleCommunicator.doGetTruckNumber()
                    )
                }
                return@launch
            }
            launch(CoroutineName(tag)) {
                sentUseCase.getMessageListFlow()
                    .catch { _errorData.postValue(application.getString(R.string.unable_to_fetch_messages)) }
                    .safeCollect(tag) { messageResponseSet ->
                        if (messageResponseSet.isEmpty() && messages.value.isNullOrEmpty().not()) {
                            _errorData.postValue(END_REACHED)
                        }
                        _messages.postValue(messageResponseSet)
                    }
            }
            sentUseCase.getMessageOfVehicle(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber(), isFirstTimeFetch
            )
        }
    }

    fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long) {
        viewModelScope.launch(dispatchers.io() + CoroutineName(tag)) {
            sentUseCase.deleteMessage(customerId, vehicleId, createdTime)
        }
    }

    fun deleteAllMessage(
        customerId: String,
        vehicleId: String,
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        viewModelScope.launch(
            coroutineDispatcher+CoroutineName(tag)
        ) {
            _collectionDeleteResponse.value = sentUseCase.deleteAllMessage(
                customerId,
                vehicleId,
                firebaseCurrentUserTokenFetchUseCase.getIDTokenOfCurrentUser(),
                firebaseCurrentUserTokenFetchUseCase.getAppCheckToken()
            )
        }
    }

    private fun observeLastMessageReached() {
        viewModelScope.launch {
            sentUseCase.didLastMessageReached().safeCollect(tag) {
                _isLastItemReceived.value = it
            }
        }
    }

    /*
    This is only to be used for unit testing to test the below condition
    1. already messages available in livedata
    2. the current set is empty which means end of the result
     */
    fun postMessagesForOnlyForTest(messages: MutableSet<MessageFormResponse>) {
        _messages.value = messages
    }

    fun logScreenViewEvent(screenName : String) {
        if(screenName.isNotEmpty()) {
            sentUseCase.logScreenViewEvent(screenName)
        }
    }

}