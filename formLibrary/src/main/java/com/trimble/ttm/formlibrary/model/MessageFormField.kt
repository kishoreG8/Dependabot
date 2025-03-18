package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MessageFormField(
    @SerializedName("FieldID") val fieldID: String = "",
    @SerializedName("FieldType") val fieldType: String = "",
    @SerializedName("FqText") val fqText: String = "",
    @SerializedName("QNum") val qNum: String = "",
    @SerializedName("Text") val text: String = "",
    @SerializedName("UmmEmptyAtStart") val ummEmptyAtStart: String = "",
    @SerializedName("UmmModified") val ummModified: String = ""
): Serializable