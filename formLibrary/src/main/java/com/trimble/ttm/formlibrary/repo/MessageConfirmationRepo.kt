package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageConfirmation

interface MessageConfirmationRepo {
    suspend fun markMessageAsDelivered(
        caller: String,
        asn: String,
        message: Message?
    )

    fun sendEdvirMessageViewedConfirmation(
        messageConfirmationDocPath: String,
        messageConfirmation: MessageConfirmation, inspectionTypeForLogging: String
    )
}