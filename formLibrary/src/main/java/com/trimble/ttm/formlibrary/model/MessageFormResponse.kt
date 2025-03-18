package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import java.io.Serializable

data class MessageFormResponse(
    @SerializedName("FormName") val formName: String = EMPTY_STRING,
    @SerializedName("FormResponseType") val formResponseType: String = EMPTY_STRING,
    @SerializedName("RecipientUserNames") val recipientUserNames: String = EMPTY_STRING,
    @SerializedName("RecipientUsers") val recipientUsers: Set<User> = setOf(),
    @SerializedName("Timestamp") val timestamp: String = EMPTY_STRING,
    val createdOn: String = EMPTY_STRING,
    val createdUnixTime: Long = 0L,
    val formData: FormResponse = FormResponse(),
    val messageContentIfCFF: String = EMPTY_STRING,
    val formId: String = EMPTY_STRING,
    val formClass: String = EMPTY_STRING,
    val hasPredefinedRecipients: Boolean = false,
    @SerializedName("UncompletedDispatchFormPath") val uncompletedDispatchFormPath: DispatchFormPath = DispatchFormPath(),
    @SerializedName("DispatchFormSavePath") val dispatchFormSavePath: String = EMPTY_STRING
) : Serializable

data class MessageFormResponseFromDB (
    @SerializedName("FormName") val formName: String = EMPTY_STRING,
    @SerializedName("FormResponseType") val formResponseType: String = EMPTY_STRING,
    @SerializedName("RecipientUserNames") val recipientUserNames: List<HashMap<String, Any>> = listOf(),
    val createdAt: Long = 0L,
    val formClass: String = EMPTY_STRING,
    val formData: FormResponse = FormResponse(),
    val formId: String = EMPTY_STRING,
    @SerializedName("HasPredefinedRecipients") val hasPredefinedRecipients: Boolean = false,
    @SerializedName("UncompletedDispatchFormPath") val uncompletedDispatchFormPath:DispatchFormPath = DispatchFormPath(),
    @SerializedName("DispatchFormSavePath") val dispatchFormSavePath: String = EMPTY_STRING
): Serializable

data class SaveMessageFormResponse(val path: String,
                                   val formResponse: FormResponse,
                                   val formId: String,
                                   val typeOfResponse: String,
                                   val formName: String,
                                   val userNames: Set<User>,
                                   val formClass: Int,
                                   val hasPredefinedRecipients: Boolean,
                                   val uncompletedDispatchFormPath: DispatchFormPath,
                                   val dispatchFormSavePath: String
)