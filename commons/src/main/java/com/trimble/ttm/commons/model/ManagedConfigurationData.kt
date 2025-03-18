package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.parcelize.Parcelize


@Parcelize
data class ManagedConfigurationData(
    val appPackageNameForWorkflowEventsCommunication: String = EMPTY_STRING,
    val polygonalOptOut: Boolean = false,
    val deepLinkConfigurationData: DeepLinkConfigurationData = DeepLinkConfigurationData()
) : Parcelable

@Parcelize
data class DeepLinkConfigurationData(
    val appName : String = EMPTY_STRING,
    val deepLinkTrigger: String = EMPTY_STRING,
    val formRelatedConfigurationDataList: List<FormRelatedConfigurationData> = listOf()
) : Parcelable

@Parcelize
data class FormRelatedConfigurationData(
    val formName: String = EMPTY_STRING,
    val formId: String = EMPTY_STRING,
    val uuid: String = EMPTY_STRING,
    val deepLinkUrl: String = EMPTY_STRING,
    val fieldValueExists: String = EMPTY_STRING, // "Trigger Off Field - Any Value" - If field exists in driver form and has any non-empty value
    val fieldValueNotExists: String = EMPTY_STRING, // "Trigger Off Field - Empty Value" - If field exists in driver form and has empty value
    val fieldAndValueEquals: String = EMPTY_STRING, // "Trigger Off Field - Specific Value" - Both field and value should be matching with the given query
    val encodedUrl: String = EMPTY_STRING,
    val fieldsListToBeReplacedInUrl : MutableList<String> = mutableListOf()
) : Parcelable