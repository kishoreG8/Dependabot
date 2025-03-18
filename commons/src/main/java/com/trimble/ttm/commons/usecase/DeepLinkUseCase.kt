package com.trimble.ttm.commons.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.trimble.ttm.commons.logger.DEEP_LINK
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.USE_CASE
import com.trimble.ttm.commons.model.DeepLinkConfigurationData
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormRelatedConfigurationData
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.utils.ARRIVAL
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FORM_SUBMISSION
import com.trimble.ttm.commons.utils.UUID
import com.trimble.ttm.commons.utils.ZERO
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

// This pattern is specific to Vector application. Other apps may have the same pattern.
private const val VECTOR_URL_PATTERN_TO_ENCODE = "~([^~]*)~"
private const val EQUAL_CHARACTER = '='

class DeepLinkUseCase(private val managedConfigurationRepo: ManagedConfigurationRepo) {

    private var deepLinkURLToEncode: String = EMPTY_STRING
    fun getDeepLinkDataFromManagedConfiguration(caller: String): DeepLinkConfigurationData? {
        return managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(caller)
    }
    /**
     * Check for deepLinkTrigger and launch app
     * If deepLinkTrigger is On Arrival -> Launch deep link URL
     */
    fun checkAndHandleDeepLinkConfigurationForArrival(
        context: Context,
        caller: String
    ){
        val deepLinkConfigurationData = getDeepLinkDataFromManagedConfiguration(caller)
        if(deepLinkConfigurationData?.deepLinkTrigger == ARRIVAL) {
            deepLinkURLToEncode = getDeepLinkOnArrival(deepLinkConfigurationData)
            Log.n("$DEEP_LINK$USE_CASE", "$caller URL to encode: $deepLinkURLToEncode")
            launchAppFromConfiguration(context, deepLinkURLToEncode)
        }
    }

    /**
     * If deepLinkTrigger is On Form Submission -> Launch deep link URL based on form ID and conditions
     */
    fun checkAndHandleDeepLinkConfigurationForFormSubmission(
        context: Context,
        formTemplateData: FormTemplate,
        caller: String
    ) {
        val deepLinkConfigurationData = getDeepLinkDataFromManagedConfiguration(caller)
        if (deepLinkConfigurationData?.deepLinkTrigger == FORM_SUBMISSION) {
            deepLinkURLToEncode = parseDeepLinkUrlOnFormSubmission(
                deepLinkConfigurationData,
                formTemplateData
            ).encodedUrl
            Log.n("$DEEP_LINK$USE_CASE", "$caller URL to encode: $deepLinkURLToEncode")
            launchAppFromConfiguration(context, deepLinkURLToEncode)
        }
    }

    /**
     * On Arrival, get the deep link and launch.
     * For Arrival TriggerValue the config info is given only in Parameter 1
     */
    fun getDeepLinkOnArrival(
        deepLinkConfigurationData: DeepLinkConfigurationData
    ): String {
        if (isTriggeredOnArrival(deepLinkConfigurationData)) {
            deepLinkConfigurationData.formRelatedConfigurationDataList.firstOrNull()
                ?.let { formConfigData ->
                    if (isDeepLinkNotEmpty(formConfigData.deepLinkUrl))
                        return formConfigData.deepLinkUrl
                }
        }
        return EMPTY_STRING
    }

    /**
     * On Form Submission, filter the configuration based on form ID and
     * given conditions(fieldValueExists, fieldValueNotExists and fieldAndValueEquals).
     * If any of the above conditions matches, get the field names between '~' symbol in given deep link URL
     * and replace those fields with values entered by driver in Workflow form.
     */
    fun parseDeepLinkUrlOnFormSubmission(
        deepLinkConfigurationData: DeepLinkConfigurationData,
        formTemplateData: FormTemplate
    ): FormRelatedConfigurationData {
        var deepLinkUrlWithFormField: String = EMPTY_STRING
        val formRelatedConfigurationData = FormRelatedConfigurationData()
        val filteredList = filterConfigurationWithMatchingFormIdAndCriteria(
            deepLinkConfigurationData,
            formTemplateData
        )
        Log.i("$DEEP_LINK$USE_CASE", "filtered List based on FormId and Criteria: $filteredList")
        if (filteredList != null && isDeepLinkNotEmpty(filteredList.deepLinkUrl)) {
            // fetching the field names from the given deep link URL and replacing it with data entered by driver
            getFieldNamesFromUrlToReplace(filteredList)
            deepLinkUrlWithFormField = replaceTheFieldsWithDataEnteredByDriver(filteredList, formTemplateData)

        }
        return formRelatedConfigurationData.copy(encodedUrl = deepLinkUrlWithFormField)
    }

    /**
     * filtering the config with the matching driver submitted formID
     * and criteria for fieldValueExists, fieldValueNotExists and fieldAndValueEquals
     */
    fun filterConfigurationWithMatchingFormIdAndCriteria(
        deepLinkConfigurationData: DeepLinkConfigurationData,
        formTemplateData: FormTemplate
    ): FormRelatedConfigurationData? {
        return deepLinkConfigurationData.formRelatedConfigurationDataList.firstOrNull {
            isDriverFormMatchingWithConfig(it, formTemplateData).let { configMatchWithDriverForm ->
                Log.i(
                    "$DEEP_LINK$USE_CASE",
                    "Are Conditions met to launch vector $configMatchWithDriverForm"
                )
                configMatchWithDriverForm
            }
        }
    }

    fun isDriverFormMatchingWithConfig(
        formRelatedConfigurationData: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): Boolean {
        return formRelatedConfigurationData.formId == formTemplateData.formDef.formid.toString()
                && checkFieldValuesBasedOnCondition(formRelatedConfigurationData, formTemplateData)
    }

    /** Field name or query will be given in any of the following:
     * fieldValueExists -> If field exists in driver form and has any non-empty value
     * fieldValueNotExists -> If field exists in driver form and has empty value
     * fieldAnValueEquals -> Both field and value should be matching with the given query
     */
    fun checkFieldValuesBasedOnCondition(
        formRelatedConfigurationData: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): Boolean {
        return when {
            formRelatedConfigurationData.fieldValueExists.isNotEmpty() -> {
                val fieldValue =
                    checkFieldValueExists(formRelatedConfigurationData, formTemplateData)
                Log.i("$DEEP_LINK$USE_CASE", "fieldValueExists: $fieldValue")
                fieldValue.isNotEmpty()
            }
            formRelatedConfigurationData.fieldValueNotExists.isNotEmpty() -> {
                val fieldValue =
                    checkFieldValueNotExists(formRelatedConfigurationData, formTemplateData)
                Log.i("$DEEP_LINK$USE_CASE", "fieldValueNotExists: $fieldValue")
                fieldValue.isEmpty()
            }
            else -> {
                val fieldValue =
                    checkFieldAndValueEquals(formRelatedConfigurationData, formTemplateData)
                Log.i("$DEEP_LINK$USE_CASE", "fieldAnValueEquals: $fieldValue")
                fieldValue.isNotEmpty()
            }
        }
    }

    fun checkFieldValueExists(
        formRelatedConfigurationData: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): String {
        // Example: fieldValueExists -> WEIGHT
        // Look for the field name "WEIGHT", if is not empty then launch the Vector Application
        return formTemplateData.formFieldsList.find { formField ->
            formField.qtext.equals(
                formRelatedConfigurationData.fieldValueExists,
                ignoreCase = true
            )
        }?.uiData ?: EMPTY_STRING
    }

    fun checkFieldValueNotExists(
        formRelatedConfigurationData: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): String {
        // Example: fieldValueExists -> WEIGHT
        // Look for the field name "WEIGHT", if is empty then launch the Vector Application
        return formTemplateData.formFieldsList.find { formField ->
            formField.qtext.equals(
                formRelatedConfigurationData.fieldValueNotExists,
                ignoreCase = true
            )
        }?.uiData ?: "-1"
    }

    fun checkFieldAndValueEquals(
        formRelatedConfigurationData: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): String {
        /* Example query for fieldAndValueEquals "LUMPER USED?=Yes"
            the condition satisfies only if field name is "LUMPER USED?" and value entered in form is "Yes"
            so splitting the given query with '=' symbol
            */
        val fieldName = formRelatedConfigurationData.fieldAndValueEquals.split(EQUAL_CHARACTER)
        if (fieldName.size != 2) return EMPTY_STRING
        return formTemplateData.formFieldsList.find { formField ->
            formField.qtext.equals(fieldName.firstOrNull(), ignoreCase = true)
                    && formField.uiData.equals(fieldName[1], ignoreCase = true)
        }?.uiData ?: EMPTY_STRING
    }

    /**
     * fetching the field names from Url prefixed and suffixed by '~' symbol Example: ~UUID~
     * and add to fieldsListToBeReplacedInUrl list.
     * Used a pattern and grouping it with matching fields from URL.
     * groupValues[0] will have matching fields in URL, example - [~UUID~, ~Order#~]
     * groupValues[1] have the actual fields - [UUID, ORDER#]
     */
    fun getFieldNamesFromUrlToReplace(filteredList: FormRelatedConfigurationData) {
        val pattern = Regex(pattern = VECTOR_URL_PATTERN_TO_ENCODE)
        val matches = pattern.findAll(filteredList.deepLinkUrl)
        filteredList.fieldsListToBeReplacedInUrl.addAll(
            matches.filter { it.groupValues.size == 2 }.map { it.groupValues[1] }
        )
        Log.i(
            "$DEEP_LINK$USE_CASE",
            "List of field names to be replaced: ${filteredList.fieldsListToBeReplacedInUrl}"
        )
    }

    /**
     * Replace the fields in fieldsListToBeReplacedInUrl list with driver entered value from form.
     * Note: if field is UUID, replace it with UUID value given in the config itself
     */
    fun replaceTheFieldsWithDataEnteredByDriver(
        filteredList: FormRelatedConfigurationData,
        formTemplateData: FormTemplate
    ): String {
        var deepLinkUrlWithFormField: String = filteredList.deepLinkUrl
        filteredList.fieldsListToBeReplacedInUrl.forEach { fieldFromURL ->
            var tempUrl: String = deepLinkUrlWithFormField
            if (fieldFromURL.equals(UUID, ignoreCase = true)) {
                tempUrl =
                    tempUrl.replace(
                        oldValue = "~$fieldFromURL~",
                        newValue = filteredList.uuid
                    )
            } else {
                val field: FormField? = formTemplateData.formFieldsList.find { formField ->
                    formField.qtext.equals(fieldFromURL, ignoreCase = true)
                }
                tempUrl =
                    tempUrl.replace(
                        oldValue = "~$fieldFromURL~",
                        newValue = field?.uiData ?: EMPTY_STRING
                    )
            }
            deepLinkUrlWithFormField = tempUrl
        }
        Log.i("$DEEP_LINK$USE_CASE", "Deep link URL parsing result: $deepLinkUrlWithFormField")
        return deepLinkUrlWithFormField
    }


    fun isTriggeredOnArrival(deepLinkConfigurationData: DeepLinkConfigurationData?): Boolean {
        return deepLinkConfigurationData?.deepLinkTrigger == ARRIVAL
                && deepLinkConfigurationData.formRelatedConfigurationDataList.isNotEmpty()
    }

    fun isDeepLinkNotEmpty(deepLinkUrl: String): Boolean {
        return deepLinkUrl.isNotEmpty()
    }

    fun launchAppFromConfiguration(context: Context, deepLinkUrl: String) {
        val deepLinkUrlEncoded = encodeURLBeforeLaunching(deepLinkUrl)
        Log.n("$DEEP_LINK$USE_CASE", "EncodedURL: $deepLinkUrlEncoded")
        if (deepLinkUrlEncoded.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLinkUrlEncoded)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Encode the Deep link URL.
     * Example: "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''~UUID~''], ''order'':''~ORDER#~''}"
     * Split the URL to get the content in jsonProps, encode those and finally merge the URL
     */
    fun encodeURLBeforeLaunching(deepLinkUrl: String): String {
        var deepLinkUrlEncoded: String = EMPTY_STRING
        try {
            // If no query parameter in URL, return the URL as it is , identified by '=' character
            if (!deepLinkUrl.contains(EQUAL_CHARACTER)) {
                return deepLinkUrl
            }
            // Deep link contains single quote for all fields so replacing it with double quotes
            val replacedURL = deepLinkUrl.replace("''", "\"")

            // Split the URL to get the content in jsonProps and encode it , split the URL based on '=' character
            val subStringToEncode =
                replacedURL.substring(replacedURL.indexOf(EQUAL_CHARACTER) + 1, replacedURL.length)
            val query: String = URLEncoder.encode(subStringToEncode, Charsets.UTF_8.name())
            deepLinkUrlEncoded =
                replacedURL.substring(ZERO, replacedURL.indexOf(EQUAL_CHARACTER) + 1) + query
                    .replace(
                        "+",
                        "%20"
                    ) //URLs may contain spaces. URL encoding replaces a space with a plus (+) sign so replacing it with %20
        } catch (e: UnsupportedEncodingException) {
            Log.e("$DEEP_LINK$USE_CASE", "unsupported encoding deep link URL", throwable = e)
        }
        return deepLinkUrlEncoded
    }
}

