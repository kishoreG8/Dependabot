package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRASH
import com.trimble.ttm.commons.logger.TRASH_LIST
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.TrashUseCase
import com.trimble.ttm.formlibrary.utils.END_REACHED
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TrashViewModel(
    private val application: Application,
    private val trashUseCase: TrashUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    internal val dispatcherProvider: DispatcherProvider,
    private val firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase
) : ViewModel() {
    private val tag = "TrashVM"
    private val _messages = MutableLiveData<MutableSet<Message>>()
    val messages: LiveData<MutableSet<Message>> = _messages
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    private val _isLastItemReceived = MutableLiveData<Boolean>()
    val isLastItemReceived: LiveData<Boolean> = _isLastItemReceived
    private val totalMessageSet: MutableSet<Message> = newConcurrentHashSet()
    var deletedMessages: MutableSet<Message> = newConcurrentHashSet()
    var isDeleteImageButtonVisible = false
    private val _trashDeleteAllMessagesResponse = MutableLiveData<CollectionDeleteResponse>()
    val trashDeleteAllMessagesResponse: LiveData<CollectionDeleteResponse> = _trashDeleteAllMessagesResponse
    var selectAllCheckedTimeStamp: Long = 0L
    private val _isNewTrashMessageReceived = MutableLiveData<Boolean>()
    val isNewTrashMessageReceived: LiveData<Boolean> = _isNewTrashMessageReceived
    private val _isPaginationMessageReceived = MutableLiveData<Boolean>()
    val isPaginationMessageReceived = _isPaginationMessageReceived

    //keep track of all selected items in the recyclerview. Keeping in view model survives configuration changes
    private var selectedItems = newConcurrentHashSet<Message>()

    init {
        observeLastMessageReached()
    }

    fun getSelectedItems(): MutableSet<Message> {
        return selectedItems
    }

    fun setSelectedItems(selectedItems: MutableSet<Message>) {
        this.selectedItems = selectedItems
    }

    fun getTotalMessages(): MutableSet<Message> {
        return totalMessageSet
    }

    fun getMessages(isFirstTimeFetch: Boolean) {
        viewModelScope.launch(dispatcherProvider.main() + CoroutineName(tag)) {
            if (appModuleCommunicator.doGetCid()
                    .isEmpty() || appModuleCommunicator.doGetTruckNumber()
                    .isEmpty()
            ) {
                _errorData.value =
                    application.getString(R.string.err_loading_messages)
                Log.e(
                    tag,
                    "Error while fetching customerId and vehicle for messaage fetch",
                    null,
                    "customer id" to appModuleCommunicator.doGetCid(),
                    "vehicle id" to appModuleCommunicator.doGetTruckNumber()
                )
                return@launch
            }
            launch(dispatcherProvider.default() + CoroutineName(tag)) {
                trashUseCase.getMessageListFlow()
                    .catch { _errorData.postValue(application.getString(R.string.unable_to_fetch_messages)) }
                    .safeCollect(tag) { messageSet ->
                        processReceivedMessage(messageSet, isFirstTimeFetch)
                    }
            }
            launch(dispatcherProvider.default() + CoroutineName(tag)) {
                trashUseCase.getMessageOfVehicle(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(), isFirstTimeFetch
                )
            }
        }
    }

    private fun observeLastMessageReached() {
        viewModelScope.launch {
            trashUseCase.didLastMessageReached().safeCollect(tag) {
                _isLastItemReceived.value = it
            }
        }
    }

    fun logScreenViewEvent(screenName : String) {
        if(screenName.isNotEmpty()) {
            trashUseCase.logScreenViewEvent(screenName)
        }
    }

    fun removeDeletedItems(selectedItems: MutableSet<Message>, totalMessages: MutableSet<Message>, clearAllMessages: Boolean) {
        Log.d("$TRASH_LIST$VIEW_MODEL", "removeAll $clearAllMessages")
        if (clearAllMessages) {
            totalMessages.clear()
            deletedMessages.clear()
            _messages.value = newConcurrentHashSet()
            trashUseCase.resetPagination()
        } else {
            totalMessages.removeAll(selectedItems)
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch(Dispatchers.IO + CoroutineName(tag)) {
            val customerId = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            val response = trashUseCase.deleteAllMessages(
                    customerId,
                    truckNumber,
                    firebaseCurrentUserTokenFetchUseCase.getIDTokenOfCurrentUser(),
                    firebaseCurrentUserTokenFetchUseCase.getAppCheckToken()
                )
            _trashDeleteAllMessagesResponse.postValue(response)
        }
    }

    fun deleteSelectedMessages() {
        viewModelScope.launch(dispatcherProvider.io() + CoroutineName(tag)) {
            deletedMessages.reversed().forEach { message ->
                trashUseCase.deleteMessage(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(),
                    message.asn,
                    TRASH
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun processReceivedMessage(messageSet: MutableSet<Message>, isFirstTimeFetch: Boolean) {
        if (messageSet.isEmpty() && messages.value.isNullOrEmpty().not()) {
            _errorData.postValue(END_REACHED)
        }
        viewModelScope.launch(dispatcherProvider.mainImmediate()) {
            if (messageSet.isNotEmpty()) {
                sortMessagesBasedOnDeletionTime(messageSet).let {
                    _messages.postValue(it)
                    totalMessageSet.addAll(it)
                    checkIfNewMessageReceived(it)
                }
                if (isFirstTimeFetch.not()) _isPaginationMessageReceived.postValue(true)
            } else {
                _messages.postValue(newConcurrentHashSet())
                totalMessageSet.clear()
                _errorData.postValue(application.getString(R.string.no_messages_available))
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun sortMessagesBasedOnDeletionTime(messageSet: MutableSet<Message>) =
        messageSet.let { messages ->
            messages.sortedByDescending { it.rowDate }
        }.toMutableSet()


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkIfNewMessageReceived(messageSet: Set<Message>) {
        if (selectAllCheckedTimeStamp > 0L) {
            _isNewTrashMessageReceived.postValue(messageSet.any { it.rowDate > selectAllCheckedTimeStamp })
        }
    }
}