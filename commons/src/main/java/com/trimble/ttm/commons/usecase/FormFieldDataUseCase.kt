package com.trimble.ttm.commons.usecase

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Barcode
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldAttribute
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.FreeText
import com.trimble.ttm.commons.model.ImageRef
import com.trimble.ttm.commons.model.LatLong
import com.trimble.ttm.commons.model.MultipleChoice
import com.trimble.ttm.commons.model.Odometer
import com.trimble.ttm.commons.model.Signature
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.BARCODE_KEY
import com.trimble.ttm.commons.utils.DEFAULT_VALUES_INDEX
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE
import com.trimble.ttm.commons.utils.FORM_RESPONSE_INBOX_VALUE
import com.trimble.ttm.commons.utils.FORM_TEMPLATE_INDEX
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.FeatureFlagGateKeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.commons.utils.FormUtils.getRecipient
import com.trimble.ttm.commons.utils.FormUtils.shouldDisplayDefaultValues
import com.trimble.ttm.commons.utils.IMAGE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.IMG_REFERENCE_KEY
import com.trimble.ttm.commons.utils.LATLNG_KEY
import com.trimble.ttm.commons.utils.LOCATION_KEY
import com.trimble.ttm.commons.utils.MULTIPLECHOICE_KEY
import com.trimble.ttm.commons.utils.ODOMETER_KEY
import com.trimble.ttm.commons.utils.SIGNATURE_KEY
import com.trimble.ttm.commons.utils.UI_FORM_RESPONSE_INDEX
import kotlinx.coroutines.coroutineScope
import java.util.Date

private const val SIGNATURE_COMPRESSION_TYPE = "gzip"
private const val SIGNATURE_IMAGE_TYPE = "jpeg"
private const val TAG = "FormFieldDataUseCase"

class FormFieldDataUseCase(private val encodedImageRefUseCase: EncodedImageRefUseCase,
                           private val appModuleCommunicator: AppModuleCommunicator,
                           private val backboneUseCase: BackboneUseCase,
                           private val gson: Gson = Gson()
) {

    private var barcode = StringBuilder()
    private var prefix = EMPTY_STRING
    private val tag = "FormFieldDataUseCase"


    fun getFormFieldAttributeFromFieldDatum(field : String) : FormFieldAttribute {
        // There will be only one item in the json object
        JsonParser.parseString(field).asJsonObject.entrySet().forEach {
            return FormFieldAttribute(
                uniqueTag = it.value.asJsonObject.get("uniqueTag").asString,
                fieldType = it.key,
                formFieldData = it.value.toString()
            )
        }
        return FormFieldAttribute()
    }

    suspend fun buildFormData(
        formTemplate: FormTemplate,
        formResponse: FormResponse,
        obcId:String,
        context : Context
    ): FormResponse {
        formResponse.baseSerialNumber = formTemplate.asn
        formResponse.mailbox = FORM_RESPONSE_INBOX_VALUE
        formResponse.dsn = obcId.toLong()
        formResponse.uniqueTemplateHash = formTemplate.formDef.formHash
        formResponse.uniqueTemplateTag = formTemplate.formDef.formid.toLong()
        addRecipientToFormResponse(formTemplate, formResponse)
        formTemplate.formFieldsList.forEach { formField ->
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal,
                FormFieldType.NUMERIC.ordinal,
                FormFieldType.NUMERIC_ENHANCED.ordinal,
                FormFieldType.DISPLAY_TEXT.ordinal,
                FormFieldType.PASSWORD.ordinal ->
                    processEditTextRelatedFields(formField, formResponse)
                FormFieldType.AUTO_VEHICLE_FUEL.ordinal ->
                    processAutoVehicleFuel(formField, formResponse)
                FormFieldType.AUTO_VEHICLE_LATLONG.ordinal ->
                    processAutoVehicleLatLongField(formField, formResponse)
                FormFieldType.AUTO_VEHICLE_LOCATION.ordinal ->
                    processAutoVehicleLocationField(formField, formResponse)
                FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal ->
                    processAutoVehicleOdometerField(formField, formResponse)
                FormFieldType.DATE.ordinal ->
                    processDateField(formField, formResponse, context)
                FormFieldType.TIME.ordinal ->
                    processTimeField(formField, formResponse, context)
                FormFieldType.DATE_TIME.ordinal ->
                    processDateTimeField(formField, formResponse, context)
                FormFieldType.MULTIPLE_CHOICE.ordinal ->
                    processMultiChoiceField(formField, formResponse)
                FormFieldType.BARCODE_SCAN.ordinal ->
                    processBarcodeField(formField, formResponse)
                FormFieldType.SIGNATURE_CAPTURE.ordinal ->
                    processSignatureField(formField, formResponse)
                FormFieldType.IMAGE_REFERENCE.ordinal ->
                    processImageReferenceField(formField, formResponse)
                FormFieldType.AUTO_DRIVER_NAME.ordinal -> {
                    //Don't send anything.
                    // https://confluence.trimble.tools/pages/viewpage.action?spaceKey=PNETTECH&title=Forms
                }
                FormFieldType.AUTO_DATE_TIME.ordinal -> {
                    //Don't send anything.
                    // https://confluence.trimble.tools/pages/viewpage.action?spaceKey=PNETTECH&title=Forms
                }
            }
        }
        logFormFieldList(formTemplate.formFieldsList)
        return formResponse
    }

    private fun logFormFieldList(formFieldsList: ArrayList<FormField>){
        val formFieldsListString = StringBuilder()
        formFieldsList.forEach {
            formFieldsListString.append("fieldId: ${it.fieldId} ")
                .append("qnum: ${it.qnum} ")
            if(it.qtype != FormFieldType.SIGNATURE_CAPTURE.ordinal && it.qtype != FormFieldType.IMAGE_REFERENCE.ordinal){
                formFieldsListString.append("uiData: ${it.uiData} | ")
            }
        }
        Log.d(tag,"formFieldsList: $formFieldsListString")
    }

    internal fun addRecipientToFormResponse(formTemplate: FormTemplate, formResponse: FormResponse): FormResponse {
        if (formTemplate.formDef.recipients.isEmpty()) return formResponse
        formTemplate.formDef.recipients.values.forEach { recipient ->
            formResponse.recipients.add(recipient.getRecipient())
        }
        return formResponse
    }

    fun processEditTextRelatedFields(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        with(HashMap<String, Any>()) {
            if (formField.errorMessage.isNotEmpty()) formField.uiData = ""
            this[FREETEXT_KEY] =
                gson.toJson(
                    FreeText(
                        formField.fieldId,
                        true,
                        formField.uiData
                    ).also { freeText ->
                        if (formField.qtype == FormFieldType.DISPLAY_TEXT.ordinal) freeText.qtype = formField.qtype
                    }
                )
            formResponse.fieldData.add(this)
            formResponse
        }

    internal suspend fun processAutoVehicleFuel(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        backboneUseCase.getFuelLevel().let { fuelLevel ->
            HashMap<String, Any>().let {
                it[FREETEXT_KEY] =
                    gson.toJson(
                        FreeText(
                            formField.fieldId,
                            true,
                            fuelLevel.toString()
                        )
                    )
                formResponse.fieldData.add(it)
            }
            formResponse
        }

    internal suspend fun processAutoVehicleLatLongField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        backboneUseCase.getCurrentLocation().let { latLngPair ->
            HashMap<String, Any>().let {
                it[LATLNG_KEY] = gson.toJson(
                    LatLong(
                        formField.fieldId,
                        true,
                        latLngPair.first,
                        latLngPair.second
                    )
                )
                formResponse.fieldData.add(it)
                formResponse
            }
        }

    internal suspend fun processAutoVehicleLocationField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        backboneUseCase.getCurrentLocation().let { latLngPair ->
            HashMap<String, Any>().let {
                it[LOCATION_KEY] = gson.toJson(
                    LatLong(
                        formField.fieldId,
                        true,
                        latLngPair.first,
                        latLngPair.second
                    )
                )
                formResponse.fieldData.add(it)
                formResponse
            }
        }

    private suspend fun processAutoVehicleOdometerField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        backboneUseCase.getOdometerReading(FeatureFlagGateKeeper().isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER, appModuleCommunicator.getFeatureFlags(), appModuleCommunicator.doGetCid())).let { odometerValue ->
            var odometerInMiles = -1
            if (odometerValue > -1.0) {
                odometerInMiles =
                    FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValue)
            }
            HashMap<String, Any>().let {
                it[ODOMETER_KEY] = gson.toJson(
                    Odometer(
                        formField.fieldId,
                        true,
                        odometerInMiles,
                        odometerInMiles
                    )
                )
                formResponse.fieldData.add(it)
                formResponse
            }
        }

    internal fun processDateField(formField: FormField, formResponse: FormResponse, context: Context): FormResponse =
        with(HashMap<String, Any>()) {
            this[FREETEXT_KEY] = gson.toJson(
                FreeText(
                    formField.fieldId,
                    true,
                    DateUtil.convertToServerDateFormat(formField.uiData, context)
                )
            )
            formResponse.fieldData.add(this)
            formResponse
        }

    internal fun processTimeField(formField: FormField, formResponse: FormResponse, context : Context): FormResponse =
        with(HashMap<String, Any>()) {
            this[FREETEXT_KEY] = gson.toJson(
                FreeText(
                    formField.fieldId,
                    true,
                    DateUtil.convertToServerTimeFormat(formField.uiData, context)
                )
            )
            formResponse.fieldData.add(this)
            formResponse
        }

    internal fun processDateTimeField(
        formField: FormField,
        formResponse: FormResponse,
        context: Context
    ): FormResponse =
        with(HashMap<String, Any>()) {
            this[FREETEXT_KEY] = gson.toJson(
                FreeText(
                    formField.fieldId,
                    true,
                    DateUtil.convertToServerDateTimeFormat(formField.uiData, context)
                )
            )
            formResponse.fieldData.add(this)
            formResponse
        }

    private fun processMultiChoiceField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse {
        if (formField.uiData.isEmpty()){
            val multipleChoiceHashMap = HashMap<String,Any>()
            multipleChoiceHashMap[MULTIPLECHOICE_KEY] = gson.toJson(
                MultipleChoice(
                    formField.fieldId,
                    false,
                    -1
                )
            )
            formResponse.fieldData.add(multipleChoiceHashMap)
            return formResponse
        }
        HashMap<String, Any>().let {
            formField.formChoiceList?.find { formChoice -> formChoice.value == formField.uiData }
                ?.let { matchedFormChoice ->
                    it[MULTIPLECHOICE_KEY] =
                        gson.toJson(
                            MultipleChoice(
                                formField.fieldId,
                                false,
                                matchedFormChoice.choicenum
                            )
                        )
                    formResponse.fieldData.add(it)
                }
        }
        return formResponse
    }

    private fun processBarcodeField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse {
        if (formField.uiData.isEmpty()) return formResponse
        val barCodeList = ArrayList<String>()
        if (formField.uiData.contains(",")) {
            barCodeList.addAll(formField.uiData.split(","))
        } else {
            barCodeList.add(formField.uiData)
        }

        barCodeList.forEach { barcodeString ->
            HashMap<String, Any>().let {
                it[BARCODE_KEY] = gson.toJson(
                    Barcode(
                        formField.fieldId,
                        true,
                        barcodeString.length,
                        0,
                        formField.bcBarCodeType,
                        barcodeString
                    )
                )
                formResponse.fieldData.add(it)
            }
        }
        return formResponse
    }

    private fun processSignatureField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse {
        if (formField.uiData.isEmpty()) return formResponse
        HashMap<String, Any>().let {
            it[SIGNATURE_KEY] = gson.toJson(
                Signature(
                    formField.fieldId,
                    false,
                    SIGNATURE_COMPRESSION_TYPE, //Gzip compression type
                    SIGNATURE_IMAGE_TYPE, //jpeg image type
                    formField.signViewHeight,
                    formField.signViewWidth,
                    formField.uiData
                )
            )
            formResponse.fieldData.add(it)
        }
        return formResponse
    }

    private fun processImageReferenceField(
        formField: FormField,
        formResponse: FormResponse
    ): FormResponse =
        with(HashMap<String, Any>()) {
            this[IMAGE_REFERENCE_KEY] = gson.toJson(
                ImageRef(
                    formField.fieldId,
                    true,
                    DateUtil.getUTCFormattedDate(Date()),
                    formField.qtext + formField.qnum,
                    "image/jpeg",
                    formField.uniqueIdentifier
                )
            )
            formResponse.fieldData.add(this)
            formResponse
        }

    fun isEditableFormField(formTemplate: FormTemplate, freeText: FreeText): Boolean {
        val formFieldQType = formTemplate.formFieldsList.find { it.fieldId == freeText.uniqueTag }?.qtype
        return (formFieldQType != FormFieldType.AUTO_DRIVER_NAME.ordinal
                && formFieldQType != FormFieldType.AUTO_VEHICLE_LOCATION.ordinal
                && formFieldQType != FormFieldType.AUTO_VEHICLE_FUEL.ordinal
                && formFieldQType != FormFieldType.AUTO_DATE_TIME.ordinal
                && formFieldQType != FormFieldType.AUTO_VEHICLE_LATLONG.ordinal
                && formFieldQType != FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal)
    }

    /**
     * Processes the data for a single form field based on its formfield type.
     *
     * This method takes a `FormTemplate` object, the raw field data, the corresponding form field attribute, and an empty array list as input.
     * It performs the following steps:
     * 1. Casts the raw data to a map and iterates through its key-value pairs.
     * 2. For each key, it performs specific processing based on the key value:
     *    - "FREETEXT_KEY": Extracts text from free-form text field data.
     *    - "MULTIPLE CHOICE_KEY": Extracts the selected choices from multiple-choice data.
     *    - "BARCODE_KEY": Appends barcode data with appropriate separators to a string in the list.
     *    - "SIGNATURE_KEY": Adds the signature data to the list.
     *    - "IMAGE_REFERENCE_KEY" or "IMG_REFERENCE_KEY": Extracts and adds the from the image reference.
     * 3. Returns the updated list containing the processed data.
     *
     * @param formTemplate The `FormTemplate` object containing form field information.
     * @param formFieldAttribute The attribute information for the current form field.
     * @param fieldDataArrayList An empty array list to store the processed data.
     * @adds The updated list containing the processed data to fieldDataArrayList.
     */
    internal suspend fun constructFormFieldData(
        formTemplate : FormTemplate,
        formFieldAttribute: FormFieldAttribute,
        fieldDataArrayList: ArrayList<String>,
        savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()
    ) {
            when (formFieldAttribute.fieldType) {
                FREETEXT_KEY ->
                    getTextFieldUIData(formFieldAttribute.formFieldData, formTemplate, fieldDataArrayList)

                MULTIPLECHOICE_KEY ->
                    getMultipleChoiceFieldData(
                        formFieldAttribute.formFieldData,
                        formTemplate,
                        formFieldAttribute,
                        fieldDataArrayList
                    )
                BARCODE_KEY -> {
                    barcode.clear()
                    prefix = EMPTY_STRING

                    // Parse JSON and add data with separators to the list
                    val data = gson.fromJson(formFieldAttribute.formFieldData, Barcode::class.java)
                    barcode.append(prefix)
                    prefix = ","
                    barcode.append(data.barcodeData)
                    fieldDataArrayList.add(barcode.toString())
                }
                SIGNATURE_KEY -> {
                    // Parse JSON and add signature data to the list
                    val data = gson.fromJson(formFieldAttribute.formFieldData, Signature::class.java)
                    fieldDataArrayList.add(data.signatureData)
                }
                IMAGE_REFERENCE_KEY, IMG_REFERENCE_KEY -> {
                    // Parse JSON and add reference data to the list
                    val imageRef = gson.fromJson(formFieldAttribute.formFieldData, ImageRef::class.java)
                    formTemplate.formFieldsList.find { it.fieldId == imageRef.uniqueTag }.let {
                        it?.uniqueIdentifier = imageRef.uniqueIdentifier
                        it?.needToSyncImage = savedImageUniqueIdentifierToValueMap[imageRef.uniqueIdentifier]?.second == true
                    }
                    if(savedImageUniqueIdentifierToValueMap.isNotEmpty() && savedImageUniqueIdentifierToValueMap.containsKey(imageRef.uniqueIdentifier)) {
                        fieldDataArrayList.add(savedImageUniqueIdentifierToValueMap[imageRef.uniqueIdentifier]!!.first)
                    } else {
                        fieldDataArrayList.add(getImageReferenceUiData(imageRef))
                    }
                }
            }
    }

    /**
     * Fetches and returns  data for a given image reference.
     *
     * This method takes an `ImageRef` object as input and performs the following:
     * 1. Checks if the `uniqueIdentifier` property is empty.
     *    - If empty, returns an empty string.
     * 2. Otherwise, calls the `fetchImageRef` to retrieve the image reference data.
     * 3. Returns the retrieved data (which is processed for UI display).
     *
     * @param data The `ImageRef` object containing the Image reference details containing uniqueIdentifier.
     * @return The data retrieved for the image reference, or an empty string if the identifier is empty.
     */
    suspend fun getImageReferenceUiData(data: ImageRef): String {
        if (data.uniqueIdentifier.isEmpty()) return EMPTY_STRING
        // Fetch the image reference data from Firestore
        return encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber(),
            data.uniqueIdentifier,
            TAG
        )
    }


    /**
     * Extracts the relevant data from a free-form text field in the form response.
     *
     * This method takes a JSON string representing the field data, a `FormTemplate` object, and an array list as input.
     * It performs the following steps:
     * 1. Parses the JSON string into a `FreeText` object using Gson.
     * 2. Retrieves the field type for the field with the matching unique tag from the `formTemplate`.
     * 3. Checks if the field type is not among a predefined list of excluded types (e.g., auto-driver names, vehicle locations).
     * 4. If the field type is not excluded, extracts the text value from the `FreeText` object and adds it to the provided list.
     * 5. Returns the updated list.
     *
     * @param value The JSON string representing the field data.
     * @param formTemplate The `FormTemplate` object containing field information.
     * @param fieldDataArrayList An array list to store the extracted data.
     * @return The updated list containing the extracted text data, or the original list if no data is extracted.
     */
    private fun getTextFieldUIData(
        value: Any,
        formTemplate: FormTemplate,
        fieldDataArrayList : ArrayList<String>
    ) {
        val data = gson.fromJson(value as String, FreeText::class.java)
        if (isEditableFormField(formTemplate, data)) {
            fieldDataArrayList.add(data.text)
        }
    }

    /**
     * Extracts the relevant data from a Multiple choice text field in the form response.
     *
     * This method working like as [getTextFieldUIData]
     *
     * @param value The JSON string representing the field data.
     * @param formTemplate The `FormTemplate` object containing field information.
     * @param fieldDataArrayList An array list to store the extracted data.
     * @adds The updated fieldDataArrayList containing the extracted text data, or the original list if no data is extracted.
     */
    private fun getMultipleChoiceFieldData(
        value: Any,
        formTemplate: FormTemplate,
        formFieldAttribute: FormFieldAttribute,
        fieldDataArrayList : ArrayList<String>
    ) {
        val data = gson.fromJson(value as String, MultipleChoice::class.java)
        formTemplate.formFieldsList.find {
            formFieldAttribute.uniqueTag.toLong() == it.fieldId
        }?.let{
            it.formChoiceList?.find {
                    formChoice -> formChoice.choicenum.toString() == data.choice.toString()
            }?.let { matchedFormChoice ->
                fieldDataArrayList.add(matchedFormChoice.value)
            }
        }
    }

    /**
     * Constructs a [Form] object from the provided data and based on the form type.
     *
     * @param result a list of objects representing various aspects of the form, typically including:
     *   -[FormTemplate]: Defines the structure and layout of the form.
     *   -[UIFormResponse]: Contains user responses to the form fields .
     *   -[HashMap]: Contains the Dispatch default values Key value of String and arraylist of String values.
     * @param isFreeForm a flag indicating whether the form is a free form (true) or not (false).
     * @param shouldFillUiResponse to show the default values only in the driver reply form. Form will be prefilled with previously entered data if true
     * @param isReplyWithSame to show the default values for only the replyWithSame Imessage form
     * @param isFormSaved to show the default values after the form saved.
     * @return a newly created [Form] object based on the provided data and type.
     */
    suspend fun createFormFromResult(
        result: List<Any>,
        isFreeForm: Boolean,
        shouldFillUiResponse: Boolean = false,
        isReplyWithSame: Boolean = false,
        isFormSaved: Boolean = false,
        savedImageUniqueIdentifierToValueMap: HashMap<String, Pair<String,Boolean>> = hashMapOf()
    ): Form = coroutineScope {
        val uiFormResponse = processUIFormResponse(result, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)
        val formResponse = result[UI_FORM_RESPONSE_INDEX] as? UIFormResponse ?: UIFormResponse()

        // Determine values based on isFreeForm flag
        val valuesMap = when (isFreeForm) {
            true -> uiFormResponse // Use processed values directly for free forms
            else -> {
                val defaultValues = extractDefaultValues(result)
                combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, shouldFillUiResponse, shouldDisplayDefaultValues(shouldFillUiResponse, isFormSaved.not() && formResponse.isSyncDataToQueue, isReplyWithSame))
            }
        }

        // Construct and return the Form object using the appropriate values
        val formTemplate = result[FORM_TEMPLATE_INDEX] as? FormTemplate ?: FormTemplate()
        formTemplate.asn = formResponse.formData.baseSerialNumber.takeIf { it > 0 } ?: 0 // Update ASN if valid
        Form(formTemplate, formResponse, valuesMap)
    }

    private fun extractDefaultValues(result: List<Any>): HashMap<String, ArrayList<String>> {
        // Extract default values from result if applicable
        return if (result.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE) {
            result[DEFAULT_VALUES_INDEX] as? HashMap<String, ArrayList<String>> ?: hashMapOf()
        } else {
            hashMapOf()
        }
    }

    /**
     * Combines processed form response values (`uiFormResponse`) with default values (`defaultValues`)
     * based on a flag (`shouldUseDefaultValues`).
     *
     * @param uiFormResponse A map containing processed form response values, where keys are field names
     *                       and values are lists of corresponding values for each field.
     * @param defaultValues A map containing default values for form fields, where keys are field names
     *                       and values are lists of corresponding default values for each field.
     * @param shouldUseDefaultValues A boolean flag indicating whether to use the default values. If true,
     *                               the combined map will prioritize default values if they exist, otherwise
     *                               it will use the processed values.
     * @return A new HashMap containing the combined form response and default values. The combined map
     *         prioritizes default values if `shouldUseDefaultValues` is true and processed values have
     *         already been provided for a field. Otherwise, it uses the processed values directly.
     */
    fun combineDefaultAndFormResponseValues(
        uiFormResponse: HashMap<String, ArrayList<String>>,
        defaultValues: HashMap<String, ArrayList<String>>,
        shouldFillUiResponse: Boolean,
        shouldUseDefaultValues: Boolean
    ): HashMap<String, ArrayList<String>> {
        // Combine processed and default values based on conditions
        val combinedValues = hashMapOf<String, ArrayList<String>>()
        if (shouldUseDefaultValues) {
            combinedValues.putAll(defaultValues)
        }
        // Fill the uiResponse when driver in ReplyForm or when replyActionType = 0 (no reply action) where same form has been opened in first and reply screen, here the UiData in first screen should be copied to reply screen
        if (shouldFillUiResponse){
            uiFormResponse.forEach { (qNum, uiResponseList) ->
                combinedValues[qNum]?.let { combinedValuesList->
                    if (combinedValuesList.isNotEmpty()) {
                        uiResponseList.forEachIndexed { index, innerValue ->
                            if(index in combinedValuesList.indices) {
                                combinedValuesList[index] = innerValue
                            }
                            else {
                                combinedValuesList.add(index, innerValue)
                            }
                        }
                    } else {
                        combinedValues[qNum] = uiResponseList
                    }
                }?: run {
                    combinedValues[qNum] = uiResponseList
                }
            }
        }
        return combinedValues
    }

    /**
     * Processes the form response data and returns it in a specific format for the UI.
     *
     * This method takes a list of any type as input, representing the raw form response data.
     * It then performs the following steps:
     * 1. Creates an empty map to store the processed form data for the UI.
     * 2. Builds a map of unique tags and their corresponding Qnums (question numbers) from the raw data.
     * 3. Processes each individual form field and adds them to the final map in the desired format for the UI.
     * 4. Returns the final map containing the processed form data.
     *
     * @param result The list of raw form response data.
     * @return A HashMap containing Qnum as keys and a list of associated Qnum values as values.
     */
    private suspend fun processUIFormResponse(result: List<Any>, savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()): HashMap<String, ArrayList<String>> {

        val uiFormResponseValuesMap = HashMap<String, ArrayList<String>>()

        // Build a map of unique tags and their corresponding Qnums
        val uniqueTagAndQnumMap = buildUniqueTagAndQnumMap(result)
        Log.d(FORM_DATA_RESPONSE, "Built uniqueTagAndQnumMap successfully $uniqueTagAndQnumMap")

        // Process each form field and populate the UI response map
        processFormFields(result, uniqueTagAndQnumMap, uiFormResponseValuesMap, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)
        return uiFormResponseValuesMap
    }

    /**
     * Processes individual form fields from the raw form response data and adds them to the final map for the UI.
     *
     * This method iterates through each field data item in the raw form response. For each field:
     * 1. Processes the individual field data based on the extracted attribute.
     * 2. Adds the processed data to the final map for the UI.
     *
     * @param result The list of raw form response data.
     * @param uniqueTagAndQnumMap The map of unique tags and their corresponding Qnums.
     * @param uiFormResponseValuesMap The final map to be populated with processed form data.
     */
    private suspend fun processFormFields(
        result: List<Any>,
        uniqueTagAndQnumMap: HashMap<Long, Int>,
        uiFormResponseValuesMap: HashMap<String, ArrayList<String>>,
        savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()
    ) {

        val uiFormResponseResult = result[UI_FORM_RESPONSE_INDEX] as? UIFormResponse
            ?: UIFormResponse()

        // Iterate through each field data item and process it
        uiFormResponseResult.formData.fieldData.forEach { fieldData ->
            val formFieldAttribute = getFormFieldAttributeFromFieldDatum("$fieldData")
            generateFormFieldData(result, formFieldAttribute, uniqueTagAndQnumMap, uiFormResponseValuesMap, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)
        }
    }

    /**
     * Processes individual form field data based on the provided attribute and adds it to the final UI response map.
     *
     * This method takes a form field attribute, its raw data, and the final map as input. Depending on the attribute type, it:
     * 1. Extracts specific values from the data (e.g., for multiple choice questions).
     * 2. Updates the appropriate entry in the UI response map with the processed data.
     *
     * @param result The list of raw form response data.
     * @param formFieldAttribute The attribute information for the current field.
     * @param uniqueTagAndQnumMap The map of unique tags and their corresponding Qnums.
     * @param uiFormResponseValuesMap The final map to be populated with processed form data.
     */
    private suspend fun generateFormFieldData(
        result: List<Any>,
        formFieldAttribute: FormFieldAttribute,
        uniqueTagAndQnumMap: HashMap<Long, Int>,
        uiFormResponseValuesMap: HashMap<String, ArrayList<String>>,
        savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()
    ) {

        val fieldDataArrayList = ArrayList<String>()

        // Extract the unique tag from the attribute
        val uniqueTag = formFieldAttribute.uniqueTag
        Log.d(FORM_DATA_RESPONSE, "Processing field with uniqueTag: $uniqueTag")

        // Process the field data based on its attribute type
        constructFormFieldData(result[FORM_TEMPLATE_INDEX] as FormTemplate, formFieldAttribute,fieldDataArrayList, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)

        // Update or add the processed data to the UI response map
        updateOrAddMapUiDataToFormField(uniqueTagAndQnumMap[uniqueTag.toLong()].toString(), fieldDataArrayList, uiFormResponseValuesMap)
    }

    /**
     * Builds a map of unique tags and their corresponding question numbers (Qnums) from the raw form response data.
     *
     * This method iterates through the list of form fields in the form template object and adds each field's ID (unique tag)
     * as the key and its Qnum as the value to the resulting map.
     *
     * @param result The list of raw form response data.
     * @return A map Qnum as keys and their corresponding Qnums values as values.
     */
    fun buildUniqueTagAndQnumMap(result: List<Any>): HashMap<Long, Int> {
        val uniqueTagAndQnumMap = HashMap<Long, Int>()
        val formTemplateResult = result[FORM_TEMPLATE_INDEX] as? FormTemplate ?: FormTemplate()

        // Iterate through each form field and add its fieldID and Qnum to the map
        formTemplateResult.formFieldsList.forEach { formField ->
            uniqueTagAndQnumMap[formField.fieldId] = formField.qnum
        }
        return uniqueTagAndQnumMap
    }

    /**
     * Updates an existing value in a map or adds a new key-value pair if the key doesn't exist.
     *
     * This method checks if a given key already exists in the map:
     * - If it exists, it appends the new values to the existing list associated with that key.
     * - If it doesn't exist, it creates a new entry in the map with the key and a new list containing the provided values.
     *
     * @param key The key to update or create.
     * @param newValue The new list of values to add.
     * @param uiFormResponseValuesMap The map to update.
     */
    fun updateOrAddMapUiDataToFormField(
        key: String,
        newValue: ArrayList<String>,
        uiFormResponseValuesMap: HashMap<String, ArrayList<String>>
    ) {
        Log.d(FORM_DATA_RESPONSE, "updateOrAddMapValue called for key: $key")

        val existingValues = uiFormResponseValuesMap[key]
        if (existingValues != null) {
            existingValues.addAll(newValue)
            uiFormResponseValuesMap[key] = existingValues
        } else {
            uiFormResponseValuesMap[key] = newValue
        }
    }
}