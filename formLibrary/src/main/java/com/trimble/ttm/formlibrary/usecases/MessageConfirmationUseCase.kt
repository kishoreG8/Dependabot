package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageConfirmation
import com.trimble.ttm.formlibrary.repo.MessageConfirmationRepo
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.TTS_WIDGET
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class MessageConfirmationUseCase(
    private val messageConfirmationRepo: MessageConfirmationRepo,
    private val coroutineScope: CoroutineScope,
    private val coroutineDispatcherProvider: DefaultDispatcherProvider = DefaultDispatcherProvider()
) : KoinComponent {

    fun sendEdvirMessageViewedConfirmation(
        path: String,
        data: MessageConfirmation,
        inspectionTypeForLogging: String
    ) {
        messageConfirmationRepo.sendEdvirMessageViewedConfirmation(
            path,
            data,
            inspectionTypeForLogging
        )
    }

    fun sendUnDeliveredMessageConfirmationForMessagesFetchedViaInboxScreen(
        messages: MutableSet<Message>
    ) {
        coroutineScope.launch(coroutineDispatcherProvider.io()) {
            messages.forEach {
                verifyDeliveredAndReadAndSendMessageConfirmation(INBOX_COLLECTION, it)
            }
        }
    }

    fun sendUnDeliveredMessageConfirmationForMessageFetchedViaTtsWidget(
        messages: Flow<Message>,
    ) {
        coroutineScope.launch(coroutineDispatcherProvider.io()) {
            messages.firstOrNull()?.let {
                verifyDeliveredAndReadAndSendMessageConfirmation(TTS_WIDGET, it)
            }
        }
    }

    suspend fun verifyDeliveredAndReadAndSendMessageConfirmation(messageFetchedFrom: String, message: Message){
        if (!message.isRead && !message.isDelivered) {
            sendInboxMessageDeliveryConfirmation(
                messageFetchedFrom,
                message.asn,
                message
            )
        }
    }

    suspend fun sendInboxMessageDeliveryConfirmation(
        caller: String,
        asn: String,
        message: Message?
    ) {
        messageConfirmationRepo.markMessageAsDelivered(caller, asn, message)
    }
}