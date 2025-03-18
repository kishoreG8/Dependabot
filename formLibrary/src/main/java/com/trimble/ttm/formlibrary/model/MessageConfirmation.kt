package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName

data class MessageConfirmation(
    @SerializedName("asn") val asn: Int=0,
    @SerializedName("dsn") val dsn: Int=0,
    @SerializedName("confirmationDateTime") val confirmationDateTime: String=""
)