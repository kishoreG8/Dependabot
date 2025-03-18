package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

data class MessagePayload(
    @SerializedName("Asn") val asn: String = EMPTY_STRING,
    @SerializedName("EmailAddr") val emailAddr: String = EMPTY_STRING,
    @SerializedName("Message") val message: Map<String, Any> = mutableMapOf(),
    @SerializedName("MessageType") val messageType: String = EMPTY_STRING,
    @SerializedName("Subject") val subject: String = EMPTY_STRING,
    @SerializedName("TimeCreated") val timeCreated: String = EMPTY_STRING,
    @SerializedName("UserName") val userName: String = EMPTY_STRING,
    @SerializedName("UID") val uID: Long = 0L,
    @SerializedName("IsDelivered") val isDelivered: Boolean = false,
    @SerializedName("IsRead") val isRead: Boolean = false,
)