package com.trimble.ttm.formlibrary.usecases


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.commons.utils.IMAGE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.LATLNG_KEY
import com.trimble.ttm.commons.utils.LOCATION_KEY
import com.trimble.ttm.commons.utils.MULTIPLECHOICE_KEY
import com.trimble.ttm.commons.utils.ODOMETER_KEY
import com.trimble.ttm.commons.utils.Utils
import com.trimble.ttm.formlibrary.model.CustomWorkFlowFormResponse
import com.trimble.ttm.formlibrary.model.CustomWorkFlowFormResponseIntent
import com.trimble.ttm.formlibrary.model.CustomWorkFlowFreeText
import com.trimble.ttm.formlibrary.model.CustomWorkFlowImage
import com.trimble.ttm.formlibrary.model.CustomWorkFlowLocation
import com.trimble.ttm.formlibrary.model.CustomWorkFlowMultipleChoice
import com.trimble.ttm.formlibrary.model.CustomWorkFlowOdometer
import com.trimble.ttm.formlibrary.model.IMField
import com.trimble.ttm.formlibrary.repo.cawb.CustomWorkFlowFormResponseSaveRepo
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.toSafeInt
import java.util.Date

private const val tag = "CustomWorkflowFormHandleUseCase"
private const val GPS_QUALITY = 0 //Hardcoded value for GPS quality since custom workflow apps don't provide it

class CustomWorkflowFormHandleUseCase(private val customWorkFlowFormResponseSaveRepo: CustomWorkFlowFormResponseSaveRepo) {

    fun deserializeFormXMLToDataClass(responseIntentData: String): CustomWorkFlowFormResponseIntent? {
        return try {
            val mapper = XmlMapper().registerModule(KotlinModule.Builder().build())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)

            val formXmlToDataClass = mapper.readValue(responseIntentData, CustomWorkFlowFormResponseIntent::class.java)
            Log.d(tag, "CWFResponseFromXMLParser $formXmlToDataClass")
            formXmlToDataClass ?: run {
                Log.e(
                    tag,
                    "CWFExceptionXMLParseNullValue",
                    throwable = null,
                    "intentData" to responseIntentData
                )
                null
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "CWFExceptionXMLParse $e.toString()",
                throwable = null,
                "intentData" to responseIntentData
            )
            null
        }
    }

    suspend fun sendCustomWorkflowResponseToPFM(
        customWorkFlowFormResponseIntent: CustomWorkFlowFormResponseIntent?
    ) {
        customWorkFlowFormResponseIntent?.iMessage?.let {
            val dataToSend = convertCWFResponseToInstinctFormResponseFormat(
                customWorkFlowFormResponseIntent
            )
            if (dataToSend.fieldData?.isNotEmpty() == true && dataToSend.recipients?.isNotEmpty() == true) {
                customWorkFlowFormResponseSaveRepo.addFormResponse(
                    dataToSend
                )
            }
        }
    }

    //Converting response to existing INSTINCT form response format. this method includes removing unnecessary fields given by custom workflow by creating new data class.
    fun convertCWFResponseToInstinctFormResponseFormat(
        customWorkFlowFormResponseIntent: CustomWorkFlowFormResponseIntent
    ): CustomWorkFlowFormResponse {
        val recipientList = createRecipientList(customWorkFlowFormResponseIntent)
        val fieldList = constructFormFieldsList(customWorkFlowFormResponseIntent)
        val formId = customWorkFlowFormResponseIntent.iMessage?.formData?.formId?.toLong() ?: run {
            0L
        }
        return CustomWorkFlowFormResponse(
            creationDateTime = DateUtil.getUTCFormattedDate(Date()),
            recipients = recipientList,
            uniqueTemplateTag = formId,
            fieldData = fieldList
        )
    }

    private fun createRecipientList(customWorkFlowFormResponseIntent: CustomWorkFlowFormResponseIntent): ArrayList<Recipients> {
        val recipientList: ArrayList<Recipients> = ArrayList()
        val recipientUid = if (customWorkFlowFormResponseIntent.iMessage?.recipient?.recipUid.isNotNull()&& customWorkFlowFormResponseIntent.iMessage?.recipient?.recipUid!!.isNotEmpty()){
            customWorkFlowFormResponseIntent.iMessage?.recipient?.recipUid?.toLong()
        } else {
            null
        }
        val recipient = Recipients(
            recipientUid,
            customWorkFlowFormResponseIntent.iMessage?.recipient?.recipName
        )
        recipientList.add(recipient)
        return recipientList
    }

    /**
     * Constructing the form fields list based on the field type
     * This is to convert the incoming response intent to PFM accepted format
     */
    private fun constructFormFieldsList(customWorkFlowFormResponseIntent: CustomWorkFlowFormResponseIntent): ArrayList<Any> {
        val formFieldList = ArrayList<Any>()
        customWorkFlowFormResponseIntent.iMessage?.formData?.imFields?.forEach { imField ->
            when (imField.qType) {
                FormFieldType.TEXT.ordinal -> handleTextBasedFields(
                    imField.data.dataText.toString(), imField.fieldNumber, formFieldList
                )

                FormFieldType.NUMERIC_ENHANCED.ordinal -> handleTextBasedFields(
                    imField.data.dataNumericEnhanced?.numberFormatted.toString(),
                    imField.fieldNumber,
                    formFieldList
                )

                FormFieldType.MULTIPLE_CHOICE.ordinal ->
                    handleMultipleChoice(
                        imField, formFieldList
                    )

                FormFieldType.AUTO_VEHICLE_LATLONG.ordinal -> handleAutoLatLong(
                    imField, formFieldList
                )

                FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal -> handleAutoOdometer(
                    imField, formFieldList
                )

                FormFieldType.AUTO_VEHICLE_LOCATION.ordinal -> handleAutoLocation(
                    imField, formFieldList
                )

                FormFieldType.DATE.ordinal -> handleTextBasedFields(
                    imField.data.dataDate.toString(), imField.fieldNumber, formFieldList
                )

                FormFieldType.TIME.ordinal -> handleTextBasedFields(
                    imField.data.dataTime.toString(), imField.fieldNumber, formFieldList
                )

                FormFieldType.DATE_TIME.ordinal -> handleTextBasedFields(
                    imField.data.dataDateTime.toString(), imField.fieldNumber, formFieldList
                )

                FormFieldType.AUTO_VEHICLE_FUEL.ordinal -> handleTextBasedFields(
                    imField.data.dataFuel.toString(), imField.fieldNumber, formFieldList
                )

                FormFieldType.IMAGE_REFERENCE.ordinal -> handleImageReference(
                    imField, formFieldList
                )
            }
        }
        return formFieldList
    }

    private fun handleTextBasedFields(
        fieldValue: String, fieldNumber: String, fieldList: ArrayList<Any>
    ) {
        Utils.toJsonString(
            CustomWorkFlowFreeText(
                fieldValue, fieldNumber
            )
        )?.let {
            fieldList.add(
                hashMapOf<String, Any>(
                    FREETEXT_KEY to it
                )
            )
        }
    }

    private fun handleMultipleChoice(imField: IMField, fieldList: ArrayList<Any>) {
        Utils.toJsonString(
            CustomWorkFlowMultipleChoice(
                imField.data.dataMultipleChoice?.mcChoiceNum?.toSafeInt()!!, imField.fieldNumber  //to do remove safe int
            )
        )?.let {
            fieldList.add(
                hashMapOf(
                    MULTIPLECHOICE_KEY to it
                )
            )
        }
    }


    private fun handleAutoLatLong(imField: IMField, fieldList: ArrayList<Any>) {
        Utils.toJsonString(
            CustomWorkFlowLocation(
                imField.data.dataAutoLatLong?.latitude!!.toDouble(),
                imField.data.dataAutoLatLong?.longitude!!.toDouble(),
                GPS_QUALITY,
                imField.fieldNumber
            )
        )?.let {
            fieldList.add(
                hashMapOf<String, Any>(
                    LATLNG_KEY to it
                )
            )
        }
    }

    private fun handleAutoOdometer(imField: IMField, fieldList: ArrayList<Any>) {
        val odometerInMiles = FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(
            imField.data.dataAutoOdometer!!.toString().toDouble()
        )
        Utils.toJsonString(
            CustomWorkFlowOdometer(
                odometerInMiles,odometerInMiles, imField.fieldNumber
            )
        )?.let {
            fieldList.add(
                hashMapOf<String, Any>(
                    ODOMETER_KEY to it
                )
            )
        }
    }

    private fun handleAutoLocation(imField: IMField, fieldList: ArrayList<Any>) {
        Utils.toJsonString(
            CustomWorkFlowLocation(
                imField.data.dataAutoLocation?.latitude!!.toDouble(),
                imField.data.dataAutoLocation?.longitude!!.toDouble(),
                GPS_QUALITY,
                imField.fieldNumber
            )
        )?.let {
            fieldList.add(
                hashMapOf<String, Any>(
                    LOCATION_KEY to it
                )
            )
        }
    }

    private fun handleImageReference(imField: IMField, fieldList: ArrayList<Any>) {
        Utils.toJsonString(
            CustomWorkFlowImage(
                DateUtil.convertToUTCFormattedDate(imField.data.dataImage?.imageDate!!),
                imField.data.dataImage?.imageName!!,
                imField.data.dataImage?.imageType!!,
                imField.fieldNumber,
                imField.data.dataImage?.imageName!!
            )
        )?.let {
            fieldList.add(
                hashMapOf<String, Any>(
                    IMAGE_REFERENCE_KEY to it
                )
            )
        }
    }
}
