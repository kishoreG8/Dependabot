package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.INBOX_LIST
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.utils.END_REACHED
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InboxViewModel(
    private val application: Application,
    private val inboxUseCase: InboxUseCase,
    private val messageConfirmationUseCase: MessageConfirmationUseCase,
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {
    private val tag = "InboxVM"
    private val _messages = MutableLiveData<MutableSet<Message>>()
    val messages: LiveData<MutableSet<Message>> = _messages
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    var isDeleteImageButtonVisible = false
    // Used to keep track of timestamp of the select all checkbox checked by the user
    var selectAllCheckedTimeStamp = 0L
    private val totalMessageSet: MutableSet<Message> = newConcurrentHashSet()
    private val _isLastMessageReceived = MutableLiveData<Boolean>()
    val isLastMessageReceived: LiveData<Boolean> = _isLastMessageReceived
    var deletedMessages : MutableSet<Message> = newConcurrentHashSet()
    private val _isNewInboxMessageReceived = MutableLiveData<Boolean>()
    val isNewInboxMessageReceived: LiveData<Boolean> = _isNewInboxMessageReceived

    //keep track of all selected items in the recyclerview. Keeping in view model survives configuration changes
    private var selectedItems = newConcurrentHashSet<Message>()

    init {
        observeLastMessageReachedFlow()
    }

    fun getSelectedItems() :MutableSet<Message> {
        return selectedItems
    }

    fun setSelectedItems(selectedItems: MutableSet<Message>){
        this.selectedItems=selectedItems
    }

    fun getTotalMessages(): MutableSet<Message> {
        return totalMessageSet
    }

    fun getMessages(isFirstTimeFetch: Boolean) {
        viewModelScope.launch(dispatchers.mainImmediate()) {
            val cid = inboxUseCase.getAppModuleCommunicator().doGetCid()
            val truckNumber = inboxUseCase.getAppModuleCommunicator().doGetTruckNumber()
            if (cid.isEmpty() || truckNumber.isEmpty()) {
                _errorData.value =
                    application.getString(R.string.err_loading_messages)
                Log.e(
                    "$INBOX_LIST$VIEW_MODEL",
                    "ErrorGetMessages $cid $truckNumber"
                )
                return@launch
            }
            withContext(dispatchers.io() + CoroutineName(tag)) {
                inboxUseCase.getMessageOfVehicle(
                    cid,
                    truckNumber, isFirstTimeFetch
                ).catch {
                    Log.d("$INBOX_LIST$VIEW_MODEL", "ExceptionGetMessagesFlow: ${it.message}")
                    _errorData.postValue(application.getString(R.string.unable_to_fetch_messages))
                }.collect { messageSet ->
                    if (messageSet.isEmpty() && messages.value.isNullOrEmpty().not()) {
                        _errorData.postValue(END_REACHED)
                    }
                    messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessagesFetchedViaInboxScreen(
                        messageSet
                    )
                    withContext(dispatchers.mainImmediate()) {
                        inboxUseCase.processReceivedMessages(
                            totalMessageSet,
                            messageSet
                        ).let { messageSet ->
                            _messages.value = messageSet
                            checkIfNewMessageReceived(messageSet)
                        }
                    }
                }
            }
        }
    }

    /**
     * This method is used to check if new message is received in the inbox
     * We're maintaining a timestamp of the select all checkbox checked by the user
     * Whenever a new message is received in the inbox
     * We're checking if the timestamp of the new message is greater than the select all checkbox checked timestamp
     * If it is greater, then we're setting the value of isNewInboxMessageReceived to true, which is being observed in the fragment and it will update the view accordingly
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkIfNewMessageReceived(messageSet: Set<Message>) {
        if (selectAllCheckedTimeStamp > 0L) {
            _isNewInboxMessageReceived.value =
                messageSet.any { it.timestamp > selectAllCheckedTimeStamp }
        }
    }

    private fun observeLastMessageReachedFlow() {
        viewModelScope.launch {
            inboxUseCase.didLastMessageReached().collect {
                _isLastMessageReceived.value = it
            }
        }
    }

    fun removeDeletedItems(selectedItems: MutableSet<Message>, totalMessages: MutableSet<Message>, clearAllMessages: Boolean) {
        Log.d("$INBOX_LIST$VIEW_MODEL", "removeAll $clearAllMessages")
        if (clearAllMessages) {
            totalMessages.clear()
        } else {
            totalMessages.removeAll(selectedItems)
        }
        Log.d("$INBOX_LIST$VIEW_MODEL", "message size ${totalMessages.size}")
    }

    // if replyFormClass is equals to "-1" we have a reply_with_same, then we need to show
    // the reply view otherwise shows the detail view first
    fun showReply(replyFormClass:String,formId:String,replyFormId:String):Boolean = (replyFormClass != "1" && formId == replyFormId) || replyFormClass == "-1"


    internal fun removeDeletedMessageFromTotalMessages(
        deletedAsn: String,
        totalMessages: MutableSet<Message>
    ) {
        val messagesToRemove = totalMessages.filter {
            it.asn == deletedAsn
        }
        totalMessages.removeAll(messagesToRemove.toSet())
        _messages.value = inboxUseCase.sortMessagesByAsn(totalMessages)
    }

    fun getAppModuleCommunicator(): AppModuleCommunicator {
        return inboxUseCase.getAppModuleCommunicator()
    }

    fun logNewEventWithDefaultParameters(eventName : String) {
        if(eventName.isNotEmpty()) {
            inboxUseCase.logNewEventWithDefaultParameters(eventName)
        }
    }

    fun logScreenViewEvent(screenName : String) {
        if(screenName.isNotEmpty()) {
            inboxUseCase.logScreenViewEvent(screenName)
        }
    }
}