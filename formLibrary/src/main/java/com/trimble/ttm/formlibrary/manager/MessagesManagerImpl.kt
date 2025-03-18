package com.trimble.ttm.formlibrary.manager

import android.content.Context
import android.view.View
import androidx.annotation.VisibleForTesting
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TTS
import com.trimble.ttm.commons.logger.WIDGET
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.repo.isNewMessageNotificationReceived
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FREE_FORM_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_MESSAGE_CONFIRMATION_FIRESTORE_READ_TIME_INTERVAL
import com.trimble.ttm.formlibrary.utils.MARK_READ
import com.trimble.ttm.formlibrary.utils.TTS_DATE
import com.trimble.ttm.formlibrary.utils.TTS_FORMNAME
import com.trimble.ttm.formlibrary.utils.TTS_FROM
import com.trimble.ttm.formlibrary.utils.isNotNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Stack

class MessagesManagerImpl(
    private val appModuleCommunicator: AppModuleCommunicator,
    private val messageFormUseCase: MessageFormUseCase,
    private val inboxUseCase: InboxUseCase,
    private val coroutineDispatcherProvider: DefaultDispatcherProvider = DefaultDispatcherProvider(),
    private val context: Context
) : IMessagesManager {

    internal val totalMessageSet: MutableSet<Message> = mutableSetOf()
    internal var index: Int = 0
    private var callback: IMessageManagerCallback? = null
    private var latestInboxMessageJob: Job? = null
    private var lastInboxMessageReadTimeInMillis: Long = 0
    private val tag = "MessagesManagerImpl"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun isFreeForm(formId: String, formClass: String): Boolean {
        val isValidForm = formClass.isNotEmpty() && formId.isNotEmpty()
        if (!isValidForm)
            return false
        return formClass.toInt() == FREE_FORM_FORM_CLASS
    }

    fun getCurrentTimeMillis(): Long{
        return System.currentTimeMillis()
    }

    fun getLastInboxMessageReadTimeInMillis(): Long{
        return lastInboxMessageReadTimeInMillis
    }

    fun getMessages() {
        //isNewMessageNotificationReceived will be true if we receive a new message push notification.
        // We can initiate a call to get latest inbox messages only when we receive a new message push notification.
        if (isNewMessageNotificationReceived || (getCurrentTimeMillis() - getLastInboxMessageReadTimeInMillis() > INBOX_MESSAGE_CONFIRMATION_FIRESTORE_READ_TIME_INTERVAL)
            || totalMessageSet.isEmpty()
        ) {
            getLatestInboxMessages()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getLatestInboxMessages() {
        latestInboxMessageJob?.let { oldListenerJob ->
            if (oldListenerJob.isActive) oldListenerJob.cancel()
        }
        latestInboxMessageJob = appModuleCommunicator.getAppModuleApplicationScope()
            .launch(coroutineDispatcherProvider.io() + CoroutineName("$INBOX$WIDGET")) {
                if (appModuleCommunicator.doGetCid().isEmpty() ||
                    appModuleCommunicator.doGetTruckNumber().isEmpty() ||
                    appModuleCommunicator.isFirebaseAuthenticated().not()
                ) {
                    Log.d("$INBOX$WIDGET",
                        "Truck information is not yet available CID:${appModuleCommunicator.doGetCid()} " +
                                "Vehicle ${appModuleCommunicator.doGetObcId()} " +
                                "ValidateAuthentication ${appModuleCommunicator.isFirebaseAuthenticated()}"
                    )
                    return@launch
                }
                inboxUseCase.getMessageWithConfirmationOfVehicleAtOnce(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber()
                ).let {
                    val filteredMsg = newConcurrentHashSet<Message>()
                    lastInboxMessageReadTimeInMillis = System.currentTimeMillis()
                    it.catch { throwable ->
                        isNewMessageNotificationReceived = false
                        Log.e(
                            "$INBOX$WIDGET",
                            "exceptionGetLatestInboxMessages : ${throwable.stackTraceToString()}",
                        )
                        callback?.onError(context.getString(R.string.unable_to_fetch_messages))
                    }.onEach { message ->
                        isNewMessageNotificationReceived = false
                        Log.d(
                            "$INBOX$WIDGET",
                            "WidgetMsg:${message.asn}"
                        )
                        if (!message.isRead ){
                            filteredMsg.add(message)
                        }
                    }.onCompletion {
                        updateUiWithNewMessages(filteredMsg)
                    }.collect()
                }
            }
    }

    private suspend fun updateUiWithNewMessages(
        filteredMsg: MutableSet<Message>
    ) {
        Log.d("$INBOX$WIDGET", "isEmptyMsgWidget? ${filteredMsg.isEmpty()}")
        if (filteredMsg.isNotEmpty()) {
            val newMessages = inboxUseCase.processReceivedMessages(
                totalMessageSet,
                filteredMsg
            )
            totalMessageSet.clear()
            totalMessageSet.addAll(
                newMessages
            )
            //this was put for not update the UI very fast and avoid UI problems during screen rotation
            delay(500)
            callback?.onMessagesUpdated()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getFilteredMsgs(messageSet: MutableSet<Message>) =
        messageSet.filterNot { msg ->
            isAReadOrNonFreeFormMsg(msg)
        }.toMutableSet()

    private fun isAReadOrNonFreeFormMsg(msg: Message): Boolean {
        return msg.isRead || isFreeForm(formId = msg.formId, formClass = msg.formClass).not()
    }


    override fun navigatePrevious() {
        if (totalMessageSet.isEmpty()) return
        if (index > 0) {
            index--
        }
    }

    override fun navigateNext() {
        if (totalMessageSet.isEmpty()) return
        if (index < totalMessageSet.indexOfLast { true })
            index++
    }

    fun getMessageCountTitle() = if (totalMessageSet.isNotEmpty()) context.getString(
        R.string.tts_widget_left_messages,
        index + 1,
        totalMessageSet.size
    ) else context.getString(
        R.string.tts_widget_no_new_messages
    )

    fun hideOrShowNext() =
        if (totalMessageSet.isEmpty() || index == totalMessageSet.indexOfLast { true }) View.VISIBLE else View.GONE

    fun hideOrShowPrevious() =
        if (totalMessageSet.isEmpty() || index == 0) View.VISIBLE else View.GONE

    fun hideOrShowPlay() = if (totalMessageSet.isNotEmpty()) View.GONE else View.VISIBLE

    fun getTTSFormHeader(): String {
        if( totalMessageSet.isNotEmpty()) {
            return "${TTS_FROM} ${totalMessageSet.elementAt(index).userName}" + " ${
                TTS_DATE
            } ${totalMessageSet.elementAt(index).date}" + " ${TTS_FORMNAME} ${
                totalMessageSet.elementAt(
                    index
                ).formName
            }"
        }
        return EMPTY_STRING
    }

    // Creates the TTS string for normal/DTF forms. If DTF form, when they mcq's values is "Yes/"Y" will pop out the duplicate fieldID
    fun getTtsforForm(): String{
        var ttsString = EMPTY_STRING
        var formFieldStack:Stack<MessageFormField> = Stack()
        val formFieldList = totalMessageSet.elementAt(index).formFieldList
        formFieldList.forEach { data->
            val lastFormField=if(formFieldStack.isNotEmpty()) formFieldStack.lastElement() else null
            if(lastFormField.isNotNull() && lastFormField?.fieldType == "2" && lastFormField.text in setOf("Y","Yes")) { // fieldType=2 indicates mcq and if its yes the stack with duplicate qnum be popped out
                while(formFieldStack.any { it.qNum == data.qNum }){
                    formFieldStack.pop()
                }
            }

            if(!formFieldStack.any { it.qNum == data.qNum })
            {
                formFieldStack.push(data)
                ttsString += data.fqText + data.text+" "
            }
        }
        return ttsString
    }
    override fun getCurrentMessage(): String {
        if (totalMessageSet.isNotEmpty()) {
            markAsRead(totalMessageSet.elementAt(index))
            var ttsString = getTTSFormHeader()
            if (isFreeForm(
                    totalMessageSet.elementAt(index).formId,
                    totalMessageSet.elementAt(index).formClass
                )
            ) {
                ttsString += totalMessageSet.elementAt(index).summaryText
            } else {
                ttsString += getTtsforForm()
            }
            Log.d("$TTS$WIDGET", "tts: ${ttsString}")
            return ttsString
        }
        return EMPTY_STRING
    }

    fun getContext() = context

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun markAsRead(msg: Message) {
        if (msg.isRead.not()) {
            appModuleCommunicator.getAppModuleApplicationScope().safeLaunch(
                Dispatchers.Default +
                        CoroutineName(
                            "$tag Mark as read"
                        )
            ) {
                messageFormUseCase.markMessageAsRead(
                    tag,
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(),
                    appModuleCommunicator.doGetObcId(),
                    msg.asn,
                    MARK_READ,
                    INBOX_COLLECTION
                )
            }
        }
    }

    override fun setCallback(callback: IMessageManagerCallback?) {
        this.callback = callback
    }

    override fun clearMessages() {
        if (totalMessageSet.isNotEmpty()) {
            totalMessageSet.clear()
            index = 0
        }
    }
}