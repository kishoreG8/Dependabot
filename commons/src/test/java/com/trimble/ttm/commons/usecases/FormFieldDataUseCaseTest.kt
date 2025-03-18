package com.trimble.ttm.commons.usecases

import android.content.Context
import com.google.gson.Gson
import com.trimble.ttm.commons.model.Barcode
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
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
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.Signature
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.BARCODE_KEY
import com.trimble.ttm.commons.utils.DEFAULT_VALUES_INDEX
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FORM_TEMPLATE_INDEX
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.commons.utils.IMAGE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.LATLNG_KEY
import com.trimble.ttm.commons.utils.LOCATION_KEY
import com.trimble.ttm.commons.utils.MULTIPLECHOICE_KEY
import com.trimble.ttm.commons.utils.ODOMETER_KEY
import com.trimble.ttm.commons.utils.SIGNATURE_KEY
import com.trimble.ttm.commons.utils.UI_FORM_RESPONSE_INDEX
import com.trimble.ttm.commons.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FormFieldDataUseCaseTest {
    @RelaxedMockK
    private lateinit var context: Context

    private var formTemplate = FormTemplate()
    private var formResponse = FormResponse()


    private lateinit var formFieldDataUseCase: FormFieldDataUseCase

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @MockK
    private lateinit var encodedImageRefUseCase: EncodedImageRefUseCase
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase

    private lateinit var mockFormDataResult: List<Any>



    private val gson : Gson= Gson()

    @RelaxedMockK
    private lateinit var mockkGson : Gson

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        formFieldDataUseCase = FormFieldDataUseCase(encodedImageRefUseCase, appModuleCommunicator, backboneUseCase)
        mockkObject(Utils)
        mockkObject(FormUtils)
        mockkObject(DateUtil)
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123456"
        coEvery { appModuleCommunicator.doGetObcId() } returns "11018288"
        // Prepare mock data for the test
        mockFormDataResult = listOf(
            // Mock FormTemplate object
            FormTemplate(),
            // Mock UIFormResponse object
            UIFormResponse(),
            // Mock HashMap with some values
            hashMapOf("key1" to arrayListOf("value1", "value2"),
                "key2" to arrayListOf("value1", ),
                "key3" to arrayListOf("value1", "value2","value3")))

    }

    @Test
    fun `verify build data for freetext`() = runTest {
        val freeText = FreeText(
            1,
            true,
            "Test"
        )
        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[FREETEXT_KEY] = gson.toJson(freeText)
            expectedResponse.fieldData.add(it)
        }
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    12,
                    "",
                    1L
                ).apply {
                    uiData = "Test"
                }
            )
        )
        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for latLong`() = runTest {
        val latLong = LatLong(
            1,
            true,
            2.0,
            1.0
        )

        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[LATLNG_KEY] = gson.toJson(latLong)
            expectedResponse.fieldData.add(it)
        }

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.AUTO_VEHICLE_LATLONG.ordinal,
                    12,
                    "",
                    1L
                )
            )
        )
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(2.0, 1.0)
        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for vehicle location`() = runTest {
        val latLong = LatLong(
            1,
            true,
            2.0,
            1.0
        )

        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[LOCATION_KEY] = gson.toJson(latLong)
            expectedResponse.fieldData.add(it)
        }

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.AUTO_VEHICLE_LOCATION.ordinal,
                    12,
                    "",
                    1L
                )
            )
        )

        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(2.0, 1.0)
        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for odometer`() = runTest {
        val flagName = FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true))

        coEvery { appModuleCommunicator.getFeatureFlags() } returns featureMap
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        val odometer = Odometer(
            1,
            true,
            -1,
            -1
        )

        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[ODOMETER_KEY] = gson.toJson(odometer)
            expectedResponse.fieldData.add(it)
        }
        coEvery { backboneUseCase.getOdometerReading(any()) } returns -1.0

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal,
                    12,
                    "",
                    1L
                )
            )
        )

        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for image`() = runTest {
        val image = ImageRef(
            1,
            true,
            "date",
            "Field1",
            "image/jpeg",
            ""
        )

        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[IMAGE_REFERENCE_KEY] = gson.toJson(image)
            expectedResponse.fieldData.add(it)
        }

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.IMAGE_REFERENCE.ordinal,
                    12,
                    "",
                    1L
                ).apply { uiData = "image" }
            )
        )

        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertNotEquals(expectedResponse.fieldData[0], actualFormResponse.fieldData[0])
    }

    @Test
    fun `verify build data for signature`() = runTest {
        val signature = Signature(
            1,
            false,
            "gzip",
            "jpeg",
            100,
            100,
            "sign"
        )

        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[SIGNATURE_KEY] = gson.toJson(signature)
            expectedResponse.fieldData.add(it)
        }

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Field",
                    FormFieldType.SIGNATURE_CAPTURE.ordinal,
                    12,
                    "",
                    1L
                ).apply {
                    signViewHeight = 100
                    signViewWidth = 100
                    uiData = "sign"
                }
            )
        )

        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for multiplechoice`() = runTest {
        val data = MultipleChoice(
            1,
            false,
            0
        )
        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[MULTIPLECHOICE_KEY] =
                gson.toJson(data)
            expectedResponse.fieldData.add(it)
        }
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Multiple choice",
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    12,
                    "",
                    1L
                ).apply {
                    uiData = "multiple"
                    formChoiceList = arrayListOf(
                        FormChoice(
                            1,
                            0,
                            "multiple",
                            12,
                            1
                        )
                    )
                }
            )
        )

        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify build data for barcode`() = runTest {
        val data = Barcode(
            1L,
            true,
            7,
            0,
            1,
            "barcode"
        )
        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[BARCODE_KEY] = gson.toJson(data)
            expectedResponse.fieldData.add(it)
        }
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "Barcode",
                    FormFieldType.BARCODE_SCAN.ordinal,
                    12,
                    "",
                    1L,
                    bcBarCodeType = 1
                ).apply {
                    uiData = "barcode"
                }
            )
        )
        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun testRecipientAddition() {
        val recipients = mutableMapOf<String, Any>()
        val pfmUser = 10119L
        val emailUser = "test@example.com"
        recipients["1"] = pfmUser
        recipients["2"] = emailUser
        val formTemplate = FormTemplate(
            FormDef(
                recipients = recipients
            )
        )
        val expectedRecips = mutableListOf<Recipients>()
        expectedRecips.add(Recipients(recipientPfmUser = pfmUser))
        expectedRecips.add(Recipients(recipientEmailUser = emailUser))
        assertEquals(
            expectedRecips.size,
            formFieldDataUseCase.addRecipientToFormResponse(formTemplate, formResponse).recipients.size
        )
        assertEquals(
            pfmUser,
            formFieldDataUseCase.addRecipientToFormResponse(
                formTemplate,
                formResponse
            ).recipients[0].recipientPfmUser
        )
        assertEquals(
            emailUser,
            formFieldDataUseCase.addRecipientToFormResponse(
                formTemplate,
                formResponse
            ).recipients[1].recipientEmailUser
        )
    }

    @Test
    fun testAutoVehicleFuel() = runTest {
        val fuelLevel = 20
        coEvery {
            backboneUseCase.getFuelLevel()
        } returns fuelLevel
        val field = FormField(fieldId = 0)
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[FREETEXT_KEY] = Gson().toJson(FreeText(0, true, fuelLevel.toString()))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processAutoVehicleFuel(field, formResponse).fieldData
        )
    }

    @Test
    fun testAutoVehicleLatLongField() = runTest {
        val lat = 10.0
        val long = 20.0
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(lat, long)
        val field = FormField(fieldId = 0)
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[LATLNG_KEY] = Gson().toJson(LatLong(0, true, lat, long))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processAutoVehicleLatLongField(field, formResponse).fieldData
        )
    }

    @Test
    fun testAutoVehicleLocationField() = runTest {
        val lat = 10.0
        val long = 20.0
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(lat, long)
        val field = FormField(fieldId = 0)
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[LOCATION_KEY] = Gson().toJson(LatLong(0, true, lat, long))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processAutoVehicleLocationField(field, formResponse).fieldData
        )
    }

    @Test
    fun testDateField() {
        val date = "01/01/2022"
        every {
            DateUtil.convertToServerDateFormat(any(), context)
        } returns date
        val field = FormField(fieldId = 0)
        field.uiData = date
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[FREETEXT_KEY] = Gson().toJson(FreeText(0, true, date))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processDateField(field, formResponse, context).fieldData
        )
    }

    @Test
    fun testTimeField() {
        val time = "10:00:00"
        every {
            DateUtil.convertToServerTimeFormat(any(), context)
        } returns time
        val field = FormField(fieldId = 0)
        field.uiData = time
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[FREETEXT_KEY] = Gson().toJson(FreeText(0, true, time))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processTimeField(field, formResponse, context).fieldData
        )
    }

    @Test
    fun testDateTimeField() {
        val dateTime = "01/01/2022 10:00:00"
        every {
            DateUtil.convertToServerDateTimeFormat(any(), context)
        } returns dateTime
        val field = FormField(fieldId = 0)
        field.uiData = dateTime
        val expectedResponse = FormResponse(
            fieldData = arrayListOf<Any>().also {
                it.add(
                    mutableMapOf<String, Any>().also { map ->
                        map[FREETEXT_KEY] = Gson().toJson(FreeText(0, true, dateTime))
                    }
                )
            }
        )
        assertEquals(
            expectedResponse.fieldData,
            formFieldDataUseCase.processDateTimeField(field, formResponse, context).fieldData
        )
    }

    @Test
    fun testGetFormFieldAttributeFromFieldDatumWithFreeText() {
        // Assign
        val id: Long = 71913
        val expectedUniqueTag = "$id"
        val expectedFieldType = FormFieldType.TEXT.serializedName
        val expectedFieldData = "{\"uniqueTag\":71913,\"initiallyEmpty\":false,\"text\":\"textIsHere\",\"qtype\":1}"
        val freeTextField = FreeText(id, false, "textIsHere", FormFieldType.TEXT.ordinal)
        val fieldString = "{${FormFieldType.TEXT.serializedName}=${gson.toJson(freeTextField, FreeText::class.java)}}"

        // Act
        val actualResponse = formFieldDataUseCase.getFormFieldAttributeFromFieldDatum(fieldString)

        // Assert
        assertEquals(expectedUniqueTag, actualResponse.uniqueTag)
        assertEquals(expectedFieldType, actualResponse.fieldType)
        assertEquals(expectedFieldData, actualResponse.formFieldData)
    }

    @Test
    fun testGetFormFieldAttributeFromFieldDatumWithMultipleChoice() {
        // Assign
        val id: Long = 71914
        val choice = 4
        val expectedUniqueTag = "$id"
        val expectedFieldData = "{\"uniqueTag\":71914,\"initiallyEmpty\":false,\"choice\":4}"
        val expectedFieldType = FormFieldType.MULTIPLE_CHOICE.serializedName
        val freeTextField = MultipleChoice(id, false, choice)
        val fieldString = "{${FormFieldType.MULTIPLE_CHOICE.serializedName}=${gson.toJson(freeTextField, MultipleChoice::class.java)}}"

        // Act
        val actualResponse = formFieldDataUseCase.getFormFieldAttributeFromFieldDatum(fieldString)

        // Assert
        assertEquals(expectedUniqueTag, actualResponse.uniqueTag)
        assertEquals(expectedFieldType, actualResponse.fieldType)
        assertEquals(expectedFieldData, actualResponse.formFieldData)
    }

    @Test
    fun test_createFormFromResult_withValidData_returnsFormWithUIFormResponseValuesMap() = runTest{
        val form = formFieldDataUseCase.createFormFromResult(mockFormDataResult, false)

        // Assert that the form is created correctly with the expected data
        assertEquals(mockFormDataResult[FORM_TEMPLATE_INDEX] as FormTemplate, form.formTemplate)
        assertEquals(mockFormDataResult[UI_FORM_RESPONSE_INDEX] as UIFormResponse, form.uiFormResponse)
        assertEquals(mockFormDataResult[DEFAULT_VALUES_INDEX] as HashMap<String, ArrayList<String>>, form.formFieldValuesMap)
    }

    @Test
    fun test_createFormFromResult_withEmptyUIFormResponseValuesMap_returnsFormWithDefaultValues() = runTest{
        // Modify mock data to have an empty uiFormResponseValuesMap
        val modifiedResult = mockFormDataResult.toMutableList().apply {
            removeAt(DEFAULT_VALUES_INDEX)
            add(hashMapOf<String, ArrayList<String>>())
        }

        val form = formFieldDataUseCase.createFormFromResult(modifiedResult,false)

        // Assert that the form is created correctly with default values
        assertEquals(modifiedResult[FORM_TEMPLATE_INDEX] as FormTemplate, form.formTemplate)
        assertEquals(modifiedResult[UI_FORM_RESPONSE_INDEX] as UIFormResponse, form.uiFormResponse)
        assertEquals(emptyMap(), form.formFieldValuesMap)
    }

    @Test
    fun test_createFormFromResult_withDefaultValues() = runTest{
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    11,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    111,
                    "",
                    fieldId = 1
                ),
                FormField(
                    22,
                    "Field",
                    FormFieldType.SIGNATURE_CAPTURE.ordinal,
                    12,
                    "",
                    2
                ),
                FormField(
                    33,
                    "Field",
                    FormFieldType.IMAGE_REFERENCE.ordinal,
                    12,
                    "",
                    3
                )
            )
        )

        mockFormDataResult = listOf(
            formTemplate,
            UIFormResponse(),
            // Mock HashMap with some values
            hashMapOf("11" to arrayListOf("value1"),
                "22" to arrayListOf("value2" )))

        val form = formFieldDataUseCase.createFormFromResult(mockFormDataResult,false)
        assertEquals(3,form.formTemplate.formFieldsList.size)
        assertEquals(0,form.uiFormResponse.formData.fieldData.size)
        assertEquals(2,form.formFieldValuesMap.size)
        assertEquals(arrayListOf("value1"), form.formFieldValuesMap["11"])
        assertEquals(arrayListOf("value2"), form.formFieldValuesMap["22"])
    }

    @Test
    fun test_createFormFromResult_withDefaultAndFormResponseValues() = runTest{

        val fieldData = ArrayList<Any>().also {
            it.add(
                hashMapOf("freeText" to "{\"initiallyEmpty\":true,\"text\":\"1666\",\"uniqueTag\":1}")
            )
        }

        val formResponseTest = UIFormResponse(isSyncDataToQueue = false,  formData = FormResponse(fieldData = fieldData))

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    11,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    111,
                    "",
                    fieldId = 1
                ),
                FormField(
                    22,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    111,
                    "",
                    fieldId = 2
                ),
                FormField(
                    33,
                    "Field",
                    FormFieldType.IMAGE_REFERENCE.ordinal,
                    12,
                    "",
                    3
                )
            )
        )


val freeTextData = FreeText(11,true,"1666",1)
        mockFormDataResult = listOf(
            formTemplate,
            formResponseTest,
            // Mock HashMap with some values
            hashMapOf("11" to arrayListOf("value1"),
                "22" to arrayListOf("value2" )))

        every {
            mockkGson.fromJson(any<String>(), FreeText::class.java)
        } returns freeTextData

        val form = formFieldDataUseCase.createFormFromResult(mockFormDataResult,false)
        assertEquals(3,form.formTemplate.formFieldsList.size)
        assertEquals(1,form.uiFormResponse.formData.fieldData.size)
        assertEquals(2,form.formFieldValuesMap.size)
    }

    @Test
    fun test_createFormFromResult_withDefaultAndFormResponseValuesForMultipleChoiceField() = runTest{

        val fieldData = ArrayList<Any>().also {
            it.add(
                hashMapOf(
                "multipleChoice" to "{\"choice\":4,\"initiallyEmpty\":false,\"uniqueTag\":1}"
            ))
        }

        val formResponseTest = UIFormResponse(isSyncDataToQueue = false,  formData = FormResponse(fieldData = fieldData))

        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    11,
                    "Field",
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    111,
                    "",
                    fieldId = 1
                ),
                FormField(
                    22,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    111,
                    "",
                    fieldId = 2
                ),
                FormField(
                    33,
                    "Field",
                    FormFieldType.IMAGE_REFERENCE.ordinal,
                    12,
                    "",
                    3
                )
            )
        )


        val multipleChoiceData = MultipleChoice(11,true,4)
        mockFormDataResult = listOf(
            formTemplate,
            formResponseTest,
            // Mock HashMap with some values
            hashMapOf("11" to arrayListOf("value1"),
                "22" to arrayListOf("value2" )))

        every {
            mockkGson.fromJson(any<String>(), MultipleChoice::class.java)
        } returns multipleChoiceData



        val form = formFieldDataUseCase.createFormFromResult(mockFormDataResult,false)
        assertEquals(3,form.formTemplate.formFieldsList.size)
        assertEquals(1,form.uiFormResponse.formData.fieldData.size)
        assertEquals(2,form.formFieldValuesMap.size)

    }


    @Test
    fun test_updateOrAddMapValue_with_existingKey() {
        val key = "1111"
        val newValue = arrayListOf("test2")
        val uiFormResponseValuesMap = hashMapOf("1111" to arrayListOf("test1"))
        formFieldDataUseCase.updateOrAddMapUiDataToFormField(key,newValue,uiFormResponseValuesMap)
        uiFormResponseValuesMap[key]?.let { assertEquals(2, it.size) }
        uiFormResponseValuesMap[key]?.let { assertEquals(arrayListOf("test1","test2"), it) }
    }

    @Test
    fun test_updateOrAddMapValue_with_newKey() {
        val key = "1111"
        val newValue = arrayListOf("test2")
        val uiFormResponseValuesMap = hashMapOf("2222" to arrayListOf("test1"))
        formFieldDataUseCase.updateOrAddMapUiDataToFormField(key,newValue,uiFormResponseValuesMap)
        uiFormResponseValuesMap[key]?.let { assertEquals(1, it.size) }
        uiFormResponseValuesMap[key]?.let { assertEquals(arrayListOf("test2"), it) }

    }

    @Test
    fun test_buildUniqueTagAndQnumMap_withNeededValues() {
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    11,
                    "Field",
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    111,
                    "",
                    fieldId = 1
                ),
                FormField(
                    22,
                    "Field",
                    FormFieldType.NUMERIC.ordinal,
                    111,
                    "",
                    fieldId = 2
                ),
                FormField(
                    33,
                    "Field",
                    FormFieldType.IMAGE_REFERENCE.ordinal,
                    12,
                    "",
                    3
                )
            )
        )
        mockFormDataResult = listOf(
            formTemplate,
            Any(),
            // Mock HashMap with some values
            hashMapOf("11" to arrayListOf("value1"),
                "22" to arrayListOf("value2" )))

        val getUniqueTagAndQnumMap = formFieldDataUseCase.buildUniqueTagAndQnumMap(mockFormDataResult)
        assertEquals(3,getUniqueTagAndQnumMap.size)
        assertEquals(22,getUniqueTagAndQnumMap[2])
        assertEquals(33,getUniqueTagAndQnumMap[3])
        assertEquals(11,getUniqueTagAndQnumMap[1])
    }

    @Test
    fun `verify empty ui data execution for the multiple choice`() = runTest {
        val data = MultipleChoice(
            1,
            false,
            -1
        )
        val expectedResponse = FormResponse()
        HashMap<String, Any>().let {
            it[MULTIPLECHOICE_KEY] = gson.toJson(data)
            expectedResponse.fieldData.add(it)
        }
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    "MC",
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    12,
                    "",
                    1L
                )
            )
        )
        val actualFormResponse = formFieldDataUseCase.buildFormData(
            formTemplate,
            formResponse,
            obcId = appModuleCommunicator.doGetObcId(),
            context = context
        )
        assertEquals(expectedResponse.fieldData, actualFormResponse.fieldData)
    }

    @Test
    fun `verify constructFormFieldData execution for barcode field`() = runTest {
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.BARCODE_SCAN.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"barcodeType\":1,\"barcode\":\"barcode\"}"
        )
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList)

        assertEquals(1, fieldDataList.size)
    }

    @Test
    fun `verify constructFormFieldData execution for signature field`() = runTest {
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.SIGNATURE_CAPTURE.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"signature\":\"signature\"}"
        )
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList)

        assertEquals(1, fieldDataList.size)
    }

    @Test
    fun `verify constructFormFieldData execution for image field with no unique id`() = runTest {
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.IMAGE_REFERENCE.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"image\":\"image\",\"uniqueIdentifier\":\"\"}"
        )
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList)

        assertEquals(1, fieldDataList.size)
        assertEquals("", fieldDataList[0])
    }

    @Test
    fun `verify constructFormFieldData execution for image field with unique id in uniqueIdentifiewToValueMap`() = runTest {
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.IMAGE_REFERENCE.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"image\":\"image\",\"uniqueIdentifier\":\"123456\"}"
        )
        val savedImageUniqueIdentifierToValueMap = hashMapOf("123456" to Pair("imgData", true))
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList, savedImageUniqueIdentifierToValueMap)

        assertEquals(1, fieldDataList.size)
        assertEquals("imgData", fieldDataList[0])
    }

    @Test
    fun `verify constructFormFieldData execution for image field with unique id`() = runTest {
        val imgData = "imgData3434fefef"
        coEvery { appModuleCommunicator.doGetCid() } returns "3434"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "R343"
        coEvery { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(any(), any(), any(), any()) } returns imgData
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.IMAGE_REFERENCE.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"image\":\"image\",\"uniqueIdentifier\":\"87\"}"
        )
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList)

        assertEquals(1, fieldDataList.size)
        assertEquals(imgData, fieldDataList[0])
        coEvery { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(any(), any(), any(), any()) }
    }

    @Test
    fun `verify constructFormFieldData execution for image field with unique id in firestore and different unique id in cache`() = runTest {
        val imgData = "imgData3434fefef"
        coEvery { appModuleCommunicator.doGetCid() } returns "3434"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "R343"
        coEvery { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(any(), any(), any(), any()) } returns imgData
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.IMAGE_REFERENCE.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"image\":\"image\",\"uniqueIdentifier\":\"ANNJBHG6785YUIP\"}"
        )
        val savedImageUniqueIdentifierToValueMap = hashMapOf("qwerty" to Pair(imgData, true))
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList, savedImageUniqueIdentifierToValueMap)

        assertEquals(1, fieldDataList.size)
        assertEquals(imgData, fieldDataList[0])
        coVerify { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(any(), any(), any(), any()) }
    }

    @Test
    fun `verify constructFormFieldData execution for invalid form field type`() = runTest {
        val formFieldAttribute = FormFieldAttribute(
            "1",
            FormFieldType.NUMERIC.serializedName,
            "{\"uniqueTag\":1,\"initiallyEmpty\":true,\"value\":\"123\"}"
        )
        val fieldDataList = ArrayList<String>()
        formFieldDataUseCase.constructFormFieldData(formTemplate, formFieldAttribute, fieldDataList)

        assertEquals(0, fieldDataList.size)
    }

    @Test
    fun `verify getImageReferenceUiData with empty string`() = runTest {
        val imageRef = ImageRef(12345,true, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, EMPTY_STRING)

        assertEquals(EMPTY_STRING, formFieldDataUseCase.getImageReferenceUiData(imageRef))
    }

    @Test
    fun `verify getImageReferenceUiData with valid imageData`() = runTest {
        val imgData = "imgDataTest3434fefef"
        val imageRef = ImageRef(12345,false, EMPTY_STRING, "formData.png", "sbjdbjsdb", "wttewerwe")
        coEvery { appModuleCommunicator.doGetCid() } returns "5688"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "testVehicle"
        coEvery { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(any(),any(),any(),any()) } returns imgData
        assertEquals(imgData, formFieldDataUseCase.getImageReferenceUiData(imageRef))
    }

    @Test
    fun `combineDefaultAndFormResponseValues when shouldUseDefaultValues is true`() {
        // Arrange
        val uiFormResponse = hashMapOf("key" to arrayListOf("value1", "value2"))
        val defaultValues = hashMapOf("key" to arrayListOf("default1", "default2"))

        // Act
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)

        // Assert
        assertEquals(defaultValues, result)
    }

    @Test
    fun `combineDefaultAndFormResponseValues when shouldUseDefaultValues is false`() {
        // Arrange
        val uiFormResponse = hashMapOf("key" to arrayListOf("value1", "value2"))
        val defaultValues = hashMapOf("key" to arrayListOf("default1", "default2"))

        // Act
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,false)

        // Assert
        assertEquals(uiFormResponse, result)
    }

    @Test
    fun `combineDefaultAndFormResponseValues when both uiFormResponse and defaultValues are empty`() {
        // Arrange
        val uiFormResponse = hashMapOf<String, ArrayList<String>>()
        val defaultValues = hashMapOf<String, ArrayList<String>>()

        // Act
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)

        // Assert
        assertEquals(uiFormResponse, result)
    }

    @Test
    fun `combineDefaultAndFormResponseValues when uiFormResponse is empty but defaultValues is not`() {
        // Arrange
        val uiFormResponse = hashMapOf<String, ArrayList<String>>()
        val defaultValues = hashMapOf("key" to arrayListOf("default1", "default2"))

        // Act
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)

        // Assert
        assertEquals(defaultValues, result)
    }

    @Test
    fun `combineDefaultAndFormResponseValues when uiFormResponse is not empty but defaultValues is`() {
        // Arrange
        val uiFormResponse = hashMapOf("key" to arrayListOf("value1", "value2"))
        val defaultValues = hashMapOf<String, ArrayList<String>>()

        // Act
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)

        // Assert
        assertEquals(uiFormResponse, result)
    }

    @Test
    fun `verify combineDefaultAndFormResponseValues when default value is empty and uiFormResponse is not empty`() {
        val uiFormResponse = hashMapOf("key" to arrayListOf("value1", "value2"))
        val defaultValues = hashMapOf("key1" to arrayListOf("value1", "value2"))
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)
        assertEquals(uiFormResponse["key"], result["key"])
        assertEquals(defaultValues["key1"], result["key1"])
    }

    @Test
    fun `verify combineDefaultAndFormResponseValues when default value size is 2 and uiFormResponse size is 4`() {
        val defaultValues = hashMapOf("key" to arrayListOf("value1", "value2"))
        val uiFormResponse = hashMapOf("key" to arrayListOf("value3", "value4", "value5","value6"))
        val result = formFieldDataUseCase.combineDefaultAndFormResponseValues(uiFormResponse, defaultValues, true,true)
        assertEquals(uiFormResponse, result)
    }


    @Test
    fun `isEditableFormField returns true for editable field types`() {
        val freeText = FreeText(1, false, "text", FormFieldType.TEXT.ordinal)
        assertTrue(formFieldDataUseCase.isEditableFormField(formTemplate, freeText))

        freeText.qtype = FormFieldType.MULTIPLE_CHOICE.ordinal
        assertTrue(formFieldDataUseCase.isEditableFormField(formTemplate, freeText))
    }
    @Test
    fun `test getFormFieldAttributeFromFieldDatum returns correct attribute`() = runTest {
        val field = "{${FormFieldType.TEXT.serializedName}=${gson.toJson(FreeText(1, false, "text", FormFieldType.TEXT.ordinal), FreeText::class.java)}}"
        val attribute = formFieldDataUseCase.getFormFieldAttributeFromFieldDatum(field)
        assertEquals("1", attribute.uniqueTag)
        assertEquals(FormFieldType.TEXT.serializedName, attribute.fieldType)
    }

    @Test
    fun `test addRecipientToFormResponse adds recipients correctly`() = runTest {
        val formTemplate = FormTemplate(FormDef(recipients = mapOf("1" to "recipient1", "2" to "recipient2")), arrayListOf())
        val formResponse = FormResponse()
        val updatedFormResponse = formFieldDataUseCase.addRecipientToFormResponse(formTemplate, formResponse)
        assertEquals(2, updatedFormResponse.recipients.size)
    }

    @Test
    fun `test processEditTextRelatedFields processes fields correctly`() = runTest {
        val formField = FormField(qtype = FormFieldType.TEXT.ordinal)
        formField.uiData = "text"
        val formResponse = FormResponse()
        val updatedFormResponse = formFieldDataUseCase.processEditTextRelatedFields(formField, formResponse)
        assertEquals(1, updatedFormResponse.fieldData.size)
    }
    @After
    fun after() {
        unmockkObject(FormUtils)
        unmockkObject(Utils)
        unmockkObject(DateUtil)
        unmockkAll()
    }
}
