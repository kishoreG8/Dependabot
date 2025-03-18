package com.trimble.ttm.formlibrary.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.JPG
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

@JacksonXmlRootElement(localName = "pnet_imessage_data")
data class CustomWorkFlowFormResponseIntent(
    @JacksonXmlProperty(localName = "imessage")
    var iMessage: IMessage? = null,
)

@JacksonXmlRootElement(localName = "imessage")
data class IMessage(
    @JacksonXmlProperty(
        localName = "vehicle_number", isAttribute = true
    ) var vehicleNumber: String ?= EMPTY_STRING,


    @JacksonXmlProperty(
        localName = "created_datetime", isAttribute = true
    ) var createdDatetime: String = EMPTY_STRING,

    @JacksonXmlProperty(
        localName = "received_datetime", isAttribute = true
    ) var receivedDatetime: String = EMPTY_STRING,

    @JacksonXmlProperty(localName = "recipient")
    var recipient: Recipient = Recipient(),

    @JacksonXmlProperty(localName = "msn") var msn: String = EMPTY_STRING,

    @JacksonXmlProperty(localName = "base_msn") var baseMsn: String = EMPTY_STRING,

    @JacksonXmlProperty(localName = "message_type") var messageType: String = EMPTY_STRING,

    @JacksonXmlProperty(localName = "formdata") var formData: FormData = FormData(),
)


data class Recipient(
    @JacksonXmlProperty(localName = "recip_uid") var recipUid: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "recip_name") var recipName: String = EMPTY_STRING,
)


data class FormData(
    @JacksonXmlProperty(localName = "form_id") var formId: String = EMPTY_STRING,

    @JacksonXmlProperty(localName = "im_field") @JacksonXmlElementWrapper(useWrapping = false) var imFields: List<IMField>? = listOf(),
)

@JsonDeserialize(using = IMField.Deserializer::class)
data class IMField(
    @JacksonXmlProperty(localName = "field_number") var fieldNumber: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "empty_at_start") var emptyAtStart: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "driver_modified") var driverModified: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "data") var data: Data = Data(),
    var qType: Int = -1,
) {
    class Deserializer : JsonDeserializer<IMField>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): IMField {
            val node = parser.codec.readTree<JsonNode>(parser)
            val fieldNumber = node["field_number"].asText()
            val emptyAtStart = node["empty_at_start"].asText()
            val driverModified = node["driver_modified"].asText()
            val data = parser.codec.treeToValue(node["data"], Data::class.java)

            /**
             * Custom workflow apps don't send qType, so we need to determine it based on the form field data type
             * This will be helpful in building form responses based on qtype.
             */
            var qType = -1
            when {
                data.dataText != null -> qType = FormFieldType.TEXT.ordinal
                data.dataMultipleChoice != null -> qType = FormFieldType.MULTIPLE_CHOICE.ordinal
                data.dataNumericEnhanced != null -> qType = FormFieldType.NUMERIC_ENHANCED.ordinal
                data.dataAutoLatLong != null -> qType = FormFieldType.AUTO_VEHICLE_LATLONG.ordinal
                data.dataAutoOdometer != null -> qType = FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal
                data.dataAutoLocation != null -> qType = FormFieldType.AUTO_VEHICLE_LOCATION.ordinal
                data.dataDate != null -> qType = FormFieldType.DATE.ordinal
                data.dataTime != null -> qType = FormFieldType.TIME.ordinal
                data.dataDateTime != null -> qType = FormFieldType.DATE_TIME.ordinal
                data.dataFuel != null -> qType = FormFieldType.AUTO_VEHICLE_FUEL.ordinal
                data.dataImage != null -> qType = FormFieldType.IMAGE_REFERENCE.ordinal
            }
            return IMField(fieldNumber, emptyAtStart, driverModified, data, qType)
        }
    }
}

@JacksonXmlRootElement(localName = "data")
data class Data(
    @JacksonXmlProperty(localName = "data_text") var dataText: String? = null,

    @JacksonXmlProperty(localName = "data_multiple-choice") var dataMultipleChoice: DataMultipleChoice? = null,

    @JacksonXmlProperty(localName = "data_numeric-enhanced") var dataNumericEnhanced: DataNumericEnhanced? = null,

    @JacksonXmlProperty(localName = "data_auto_latlong") var dataAutoLatLong: DataAutoLatLong? = null,

    @JsonDeserialize(using = DataOdometerDeserializer::class)
    @JacksonXmlProperty(localName = "data_auto_odometer") var dataAutoOdometer: Any? = null,

    @JacksonXmlProperty(localName = "data_auto_location") var dataAutoLocation: DataAutoLocation? = null,

    @JacksonXmlProperty(localName = "data_date", isAttribute = true) var dataDate: String? = null,

    @JacksonXmlProperty(localName = "data_time", isAttribute = true) var dataTime: String? = null,

    @JacksonXmlProperty(
        localName = "data_date_time", isAttribute = true
    ) var dataDateTime: String? = null,

    @JacksonXmlProperty(
        localName = "data_auto_fuel", isAttribute = true
    ) var dataFuel: String? = null,

    /**
     * Both image and signature data will be coming as image ref as instructed by the custom workflow team
     */
    @JacksonXmlProperty(
        localName = "data_image_ref"
    ) var dataImage: DataImageRef? = null,
)

class DataOdometerDeserializer : JsonDeserializer<Any?>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Any {
        val node = parser.codec.readTree<JsonNode>(parser).asText()
        if (node != null && node.toDoubleOrNull() != null) {
            return node.toString().toDouble()
        }
        return -1
    }
}

@JacksonXmlRootElement(localName = "data_multiple_choice")
data class DataMultipleChoice(
    @JacksonXmlProperty(localName = "mc_choicenum") var mcChoiceNum: String = "-1",
    @JacksonXmlProperty(localName = "mc_choicetext") var mcChoiceText: String = EMPTY_STRING,
)

@JacksonXmlRootElement(localName = "data_auto_latlong")
data class DataAutoLatLong(
    @JacksonXmlProperty(localName = "latitude") var latitude: Double = 0.0,
    @JacksonXmlProperty(localName = "longitude") var longitude: Double = 0.0,
)

@JacksonXmlRootElement(localName = "data_auto_location")
data class DataAutoLocation(
    @JacksonXmlProperty(localName = "latitude") var latitude: Double = 0.0,
    @JacksonXmlProperty(localName = "longitude") var longitude: Double = 0.0,
)

@JsonDeserialize(using = DataNumericEnhanced.Deserializer::class)
data class DataNumericEnhanced(
    @JacksonXmlProperty(localName = "num_formatted") var numberFormatted: String = "-1",

    @JacksonXmlProperty(localName = "num_raw") var numberRaw: String = "-1",
) {
    class Deserializer : JsonDeserializer<DataNumericEnhanced>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): DataNumericEnhanced {
            val node = parser.codec.readTree<JsonNode>(parser)
            var numberFormatted = node["num_formatted"].asText()
            val numberRaw = node["num_raw"].asText()

            // Check if numberFormatted is numeric , sometimes it can be empty string or non numeric string from custom workflow apps
            if (numberFormatted.toDoubleOrNull() == null) {
                numberFormatted = ""
            }

            return DataNumericEnhanced(numberFormatted, numberRaw)
        }
    }
}

class DataImageRefDeserializer : JsonDeserializer<DataImageRef>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): DataImageRef {
        val node: JsonNode = parser.codec.readTree(parser)
        return if (node.isTextual) {
            // Handle the case where the node is a string / image ref comes as (skipped) string instead of object
            DataImageRef(EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING)
        } else {
            // Manually extract fields to avoid recursion and stack overflow errors
            val imageDate = node["data_image_date"]?.asText() ?: EMPTY_STRING
            val imageTransId =
                if (node["data_image_transid"] != null) STORAGE + stripExtensionFromImageRef(node["data_image_transid"].asText()) else EMPTY_STRING
            val imageName =
                if (node["data_image_name"] != null) STORAGE + stripExtensionFromImageRef(node["data_image_name"].asText()) else EMPTY_STRING
            val imageType = node["data_image_mimetype"]?.asText() ?: EMPTY_STRING
            DataImageRef(imageDate, imageTransId, imageName, imageType)
        }
    }
}

private fun stripExtensionFromImageRef(imageName: String): String {
    if (imageName.endsWith(JPG)) {
        return imageName.substringBeforeLast(".")
    }
    return imageName
}

@JsonDeserialize(using = DataImageRefDeserializer::class)
data class DataImageRef(
    @JacksonXmlProperty(localName = "data_image_date") var imageDate: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "data_image_transid") var imageTransId: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "data_image_name") var imageName: String = EMPTY_STRING,
    @JacksonXmlProperty(localName = "data_image_mimetype") var imageType: String = EMPTY_STRING
)