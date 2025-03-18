package com.trimble.ttm.commons.repo

import android.os.Bundle
import com.trimble.ttm.commons.datasource.ManagedConfigurationDataSource
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MANAGED_CONFIG
import com.trimble.ttm.commons.model.DeepLinkConfigurationData
import com.trimble.ttm.commons.model.FormRelatedConfigurationData
import com.trimble.ttm.commons.model.ManagedConfigurationData
import com.trimble.ttm.commons.utils.APP_NAME_KEY
import com.trimble.ttm.commons.utils.DEEP_LINK_CONFIGURATION
import com.trimble.ttm.commons.utils.DEEP_LINK_PARAMETER_ONE
import com.trimble.ttm.commons.utils.DEEP_LINK_PARAMETER_THREE
import com.trimble.ttm.commons.utils.DEEP_LINK_URL
import com.trimble.ttm.commons.utils.FIELD_AND_VALUE_EQUALS
import com.trimble.ttm.commons.utils.FIELD_VALUE_EXISTS
import com.trimble.ttm.commons.utils.FIELD_VALUE_NOT_EXISTS
import com.trimble.ttm.commons.utils.FORM_ID
import com.trimble.ttm.commons.utils.FORM_NAME
import com.trimble.ttm.commons.utils.FORM_RELATED_CONFIGURATION
import com.trimble.ttm.commons.utils.FORM_RELATED_CONFIGURATION_LIST
import com.trimble.ttm.commons.utils.FORM_RELATED_CONFIGURATION_LIST_ARRAY
import com.trimble.ttm.commons.utils.POLYGONAL_OPT_OUT_KEY
import com.trimble.ttm.commons.utils.TRIGGER_VALUE
import com.trimble.ttm.commons.utils.UUID
import com.trimble.ttm.commons.utils.WORKFLOW_EVENTS_COMMUNICATION
import com.trimble.ttm.commons.utils.getBooleanOrDefaultValue
import com.trimble.ttm.commons.utils.getStringOrDefaultValue

class ManagedConfigurationRepoImpl(private val managedConfigurationDataSource: ManagedConfigurationDataSource): ManagedConfigurationRepo {

    internal var managedConfigurationData: ManagedConfigurationData? = null

    override fun fetchManagedConfigDataFromCache(caller: String): ManagedConfigurationData? {
        if(managedConfigurationData == null) {
            fetchManagedConfigDataFromServer(caller)
        }
        return managedConfigurationData
    }

    override fun fetchManagedConfigDataFromServer(caller: String) {
        managedConfigurationDataSource.fetchManagedConfiguration(caller)
            ?.let { appRestrictionsBundle ->
                managedConfigurationData =
                    fetchManagedConfigurationDataFromBundle(appRestrictionsBundle)
            }
    }

    fun fetchManagedConfigurationDataFromBundle(appRestrictionsBundle: Bundle): ManagedConfigurationData {
        val workflowEventsCommunicationData = appRestrictionsBundle.getStringOrDefaultValue(WORKFLOW_EVENTS_COMMUNICATION)
        val deepLinkConfigurationData = fetchDeepLinkDataFromBundle(appRestrictionsBundle)
        val polygonalOptOutData = appRestrictionsBundle.getBooleanOrDefaultValue(POLYGONAL_OPT_OUT_KEY)

        Log.n(
            MANAGED_CONFIG,
            "Workflow Events Communication: $workflowEventsCommunicationData, polygonalOptOutData: $polygonalOptOutData" +
                    "Deep Link Configuration App Name: ${deepLinkConfigurationData.appName}, Trigger: ${deepLinkConfigurationData.deepLinkTrigger}"
        )

        return ManagedConfigurationData(
            appPackageNameForWorkflowEventsCommunication = workflowEventsCommunicationData,
            polygonalOptOut = polygonalOptOutData,
            deepLinkConfigurationData = deepLinkConfigurationData
        )
    }

    fun fetchDeepLinkDataFromBundle(appRestrictionsBundle: Bundle): DeepLinkConfigurationData {
        val deepLinkBundle = appRestrictionsBundle.getBundle(DEEP_LINK_CONFIGURATION)
        return deepLinkBundle?.let { bundle ->
            val deepLinkTrigger = bundle.getStringOrDefaultValue(TRIGGER_VALUE)
            val appName = bundle.getStringOrDefaultValue(APP_NAME_KEY)
            fetchFormRelatedDataFromBundle(bundle, deepLinkTrigger, appName)
        } ?: DeepLinkConfigurationData()
    }

    fun isFormRelatedConfigurationInTheBundle(deepLinkBundle: Bundle): Boolean {
        return (deepLinkBundle.containsKey(FORM_RELATED_CONFIGURATION_LIST))
    }

    fun fetchFormRelatedDataFromBundle(
        deepLinkBundle: Bundle,
        triggerValue: String,
        appName: String
    ): DeepLinkConfigurationData {
        return if (isFormRelatedConfigurationInTheBundle(deepLinkBundle)) {
            val formRelatedConfigurationListBundle = deepLinkBundle.getBundle(
                FORM_RELATED_CONFIGURATION_LIST
            )
            val parcelableArray =
                deepLinkBundle.getParcelableArray(FORM_RELATED_CONFIGURATION_LIST_ARRAY)
            val parcelableList = parcelableArray?.map { it as Bundle } ?: emptyList()
            formRelatedConfigurationListBundle?.let { bundle ->
                DeepLinkConfigurationData(
                    deepLinkTrigger = triggerValue,
                    appName = appName,
                    formRelatedConfigurationDataList = getFormRelatedDataFromBundle(
                        bundle,
                        parcelableList
                    )
                )
            } ?: DeepLinkConfigurationData(deepLinkTrigger = triggerValue, appName = appName)
        } else {
            DeepLinkConfigurationData(deepLinkTrigger = triggerValue, appName = appName)
        }
    }

    fun getFormRelatedDataFromBundle(
        formRelatedConfigurationListBundle: Bundle,
        parcelableList: List<Bundle>
    ): List<FormRelatedConfigurationData> {
        val formRelatedConfigurationList = mutableListOf<FormRelatedConfigurationData>()
        val uniqueConfigurations = mutableSetOf<FormRelatedConfigurationData>()

        fun addConfigurationData(formConfig: Bundle, index: String? = null) {
            val formRelatedConfigurationData = FormRelatedConfigurationData(
                formName = formConfig.getStringOrDefaultValue(if (index != null) FORM_NAME + index else FORM_NAME),
                formId = formConfig.getStringOrDefaultValue(if (index != null) FORM_ID + index else FORM_ID),
                uuid = formConfig.getStringOrDefaultValue(if (index != null) UUID + index else UUID),
                deepLinkUrl = formConfig.getStringOrDefaultValue(if (index != null) DEEP_LINK_URL + index else DEEP_LINK_URL),
                fieldValueExists = formConfig.getStringOrDefaultValue(if (index != null) FIELD_VALUE_EXISTS + index else FIELD_VALUE_EXISTS),
                fieldValueNotExists = formConfig.getStringOrDefaultValue(if (index != null) FIELD_VALUE_NOT_EXISTS + index else FIELD_VALUE_NOT_EXISTS),
                fieldAndValueEquals = formConfig.getStringOrDefaultValue(if (index != null) FIELD_AND_VALUE_EQUALS + index else FIELD_AND_VALUE_EQUALS)
            )

            if (uniqueConfigurations.add(formRelatedConfigurationData)) {
                Log.i(
                    MANAGED_CONFIG,
                    "Values in Form Related Configuration Bundle:" +
                            " FormName:${formRelatedConfigurationData.formName}," +
                            " FormId:${formRelatedConfigurationData.formId}," +
                            " DeepLinkUrl:${formRelatedConfigurationData.deepLinkUrl}," +
                            " UUID:${formRelatedConfigurationData.uuid}," +
                            " FieldValueExists:${formRelatedConfigurationData.fieldValueExists}," +
                            " FieldValueNotExists:${formRelatedConfigurationData.fieldValueNotExists}," +
                            " FieldAndValueEquals:${formRelatedConfigurationData.fieldAndValueEquals}"
                )
                formRelatedConfigurationList.add(formRelatedConfigurationData)
            }
        }

        // Handle static parameters, to ensure older fields work properly with new ones
        for (index in DEEP_LINK_PARAMETER_ONE..DEEP_LINK_PARAMETER_THREE) {
            if (formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + index)) {
                val formRelatedConfigurationBundle =
                    formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + index)
                formRelatedConfigurationBundle?.let { addConfigurationData(it, index.toString()) }
            }
        }
        parcelableList.forEach { formConfig ->
            addConfigurationData(formConfig)
        }
        return formRelatedConfigurationList
    }

    override fun getAppPackageForWorkflowEventsCommunicationFromManageConfiguration(caller: String): String? {
        return fetchManagedConfigDataFromCache(caller)?.appPackageNameForWorkflowEventsCommunication
    }

    override fun getPolygonalOptOutFromManageConfiguration(caller: String): Boolean {
        return fetchManagedConfigDataFromCache(caller)?.polygonalOptOut ?: false
    }

    override fun getDeepLinkDataFromManagedConfiguration(caller: String): DeepLinkConfigurationData? {
        return fetchManagedConfigDataFromCache(caller)?.deepLinkConfigurationData
    }
}