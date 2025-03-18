package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormActivityIntentActionData
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.utils.END_REACHED
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class DraftViewModel(
    private val application: Application,
    private val draftUseCase: DraftUseCase,
    private val firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel(), KoinComponent {
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val tag = "DraftVM"
    private val _messages = MutableLiveData<MutableSet<MessageFormResponse>>()
    val messages: LiveData<MutableSet<MessageFormResponse>> = _messages
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    var userSelectedItemsOnMessageListAdapter = mutableSetOf<MessageFormResponse>()
    var messageListOnMessageListAdapter = mutableSetOf<MessageFormResponse>()
    var multiSelectOnMessageListAdapter = false
    var originalMessageListCopy: MutableSet<MessageFormResponse> = mutableSetOf()
    var isDeleteImageButtonVisible = false
    private val _isLastDraftReceived = MutableLiveData<Boolean>()
    val isLastDraftReceived: LiveData<Boolean> = _isLastDraftReceived
    private var _isComposeEnabled : Boolean = false

    init {
        observeLastDraftReached()
        setComposeFormFeatureFlag()
    }

    fun getMessages(isFirstTimeFetch: Boolean) {
        viewModelScope.launch(ioDispatcher + CoroutineName("${tag}getMessages")) {
            if (
                appModuleCommunicator.doGetCid().isEmpty() ||
                appModuleCommunicator.doGetTruckNumber().isEmpty()
            ) {
                _errorData.value = application.getString(R.string.err_loading_messages)

                Log.e(
                    tag,
                    "Error while fetching customerId and vehicle for message form response fetch",
                    null,
                    "customer id" to appModuleCommunicator.doGetCid(),
                    "vehicle id" to appModuleCommunicator.doGetTruckNumber()
                )

                return@launch
            }

            draftUseCase.getMessageListFlow()
                .catch {
                    _errorData.postValue(
                        application.getString(R.string.unable_to_fetch_messages)
                    )
                }.onEach { messageResponseSet ->
                    if (messageResponseSet.isEmpty() && messages.value.isNullOrEmpty().not()) {
                        _errorData.postValue(END_REACHED)
                        _messages.postValue(mutableSetOf())
                    } else {
                        _messages.postValue(messageResponseSet)
                    }
                }.launchIn(viewModelScope)

            launch(defaultDispatcher + CoroutineName("${tag}getMessages")) {
                draftUseCase.getMessageOfVehicle(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(),
                    isFirstTimeFetch
                )
            }
        }
    }

    fun deleteMessage(customerId: String, vehicleId: String, createdTime: Long) {
        viewModelScope.launch(
            ioDispatcher + CoroutineName(::deleteMessage.name)
        ) {
            draftUseCase.deleteMessage(customerId, vehicleId, createdTime)

        }
    }

    fun deleteAllMessagesAsync(
        customerId: String,
        vehicleId: String
    ): Deferred<CollectionDeleteResponse> =
        viewModelScope.async(
            ioDispatcher + CoroutineName(::deleteAllMessagesAsync.name)
        ) {
            return@async draftUseCase.deleteAllMessage(
                customerId,
                vehicleId,
                firebaseCurrentUserTokenFetchUseCase.getIDTokenOfCurrentUser(),
                firebaseCurrentUserTokenFetchUseCase.getAppCheckToken()
            )
        }

    private fun observeLastDraftReached() {
        viewModelScope.launch {
            draftUseCase.didLastMessageReached().safeCollect(tag) {
                _isLastDraftReceived.value = it
            }
        }
    }

    private fun setComposeFormFeatureFlag() {
        viewModelScope.launch(defaultDispatcher + CoroutineName("setFeatureFlags")) {
            _isComposeEnabled = draftUseCase.isComposeFormFeatureFlagEnabled()
        }
    }

    fun provideFormActivityIntent(messageFormResponse: MessageFormResponse, customerId: Int): Intent {
        return  FormActivityIntentActionData(
            isComposeEnabled = _isComposeEnabled,
            customerId = customerId,
            formId = messageFormResponse.formId.toInt(),
            dispatchFormSavePath = messageFormResponse.dispatchFormSavePath,
            formResponse = messageFormResponse.formData,
            driverFormPath = messageFormResponse.uncompletedDispatchFormPath,
            isFormFromTripPanel = false,
            isSecondForm = true,
            containsStopData = true,
            isFromDraft = messageFormResponse.dispatchFormSavePath.isNotEmpty()
        ).buildIntent(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun shouldSendDraftDataBackToDispatchStopForm(messageFormResponse: MessageFormResponse): Boolean {
        return draftUseCase.shouldSendDraftDataBackToDispatchStopForm(messageFormResponse)
    }

    fun isDraftedFormOfTheActiveDispatch(dispatchFormSavePath: String): Deferred<Boolean> =
        viewModelScope.async {
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("DraftListItemClick")
            if (activeDispatchId.isNotEmpty()) {
                dispatchFormSavePath.split("/").let { splittedDispatchFormSavePath ->
                    if (splittedDispatchFormSavePath.size == 6) {
                        val dispatchIdOfDraftedForm = splittedDispatchFormSavePath[3]
                        return@async dispatchIdOfDraftedForm == activeDispatchId
                    } else {
                        Log.w(tag, "invalid dispatch form save path found in drafted form. $dispatchFormSavePath")
                    }
                }
            }
            return@async false
        }


    fun logScreenViewEvent(screenName : String) {
        if(screenName.isNotEmpty()) {
            draftUseCase.logScreenViewEvent(screenName)
        }
    }

}
