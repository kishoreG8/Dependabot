package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.utils.INTENT_CATEGORY_LAUNCHER
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.repo.InboxRepo
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.flow.Flow

class InboxUseCase(
    private val inboxRepo: InboxRepo,
    private val messageConfirmationUseCase : MessageConfirmationUseCase,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
) {

    fun getMessageOfVehicle(
        customerId: String,
        vehicleId: String,
        isFirstTimeFetch: Boolean
    ): Flow<MutableSet<Message>> {
        return inboxRepo.getMessages(customerId, vehicleId, isFirstTimeFetch)
    }

    private fun getMessageOfVehicleAtOnce(customerId: String, vehicleId: String): Flow<Message> {
        return inboxRepo.getMessagesAtOnce(customerId, vehicleId)
    }

    fun getMessageWithConfirmationOfVehicleAtOnce(customerId: String, vehicleId: String): Flow<Message> {
        val message = getMessageOfVehicleAtOnce(customerId, vehicleId)
        messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessageFetchedViaTtsWidget(message)
        return message
    }

    fun didLastMessageReached() = inboxRepo.didLastItemReached()

    fun resetPagination() = inboxRepo.resetPagination()

    internal fun processReceivedMessages(
        totalMessageSet: MutableSet<Message>,
        receivedMessageChunk: MutableSet<Message>
    ): MutableSet<Message> =
        sortMessagesByAsn(updateMessagesIfAny(totalMessageSet, receivedMessageChunk))


    internal fun updateMessagesIfAny(
        totalMessageSet: MutableSet<Message>,
        receivedMessageChunk: MutableSet<Message>
    ): MutableSet<Message> {
        //To handle message update. The old message will be removed and added by referring asn. Eg., Message Read
        val messagesToRemove= totalMessageSet.filter {totalMessages->
            receivedMessageChunk.any { receivedMessages->
            totalMessages.asn == receivedMessages.asn
        } }
        totalMessageSet.removeAll(messagesToRemove.toSet())
        totalMessageSet.addAll(receivedMessageChunk)
        return totalMessageSet
    }

    internal fun sortMessagesByAsn(totalMessageSet: MutableSet<Message>): MutableSet<Message> =
        totalMessageSet.sortedByDescending { message -> message.asn.toSafeLong() }
            .toMutableSet()

    fun getAppModuleCommunicator() = inboxRepo.getAppModuleCommunicator()

    fun logScreenViewEvent(screenName: String) =
        firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(screenName)

    fun logNewEventWithDefaultParameters(eventName: String) =
        firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(eventName)

    fun recordShortCutIconClickEvent(eventName: String, intentCategoriesSet: Set<String>?) {
        if (intentCategoriesSet?.contains(INTENT_CATEGORY_LAUNCHER) == true) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(eventName)
        }
    }

}