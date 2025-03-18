package com.trimble.ttm.commons.usecases

import android.content.Context
import com.trimble.ttm.commons.model.DeepLinkConfigurationData
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormRelatedConfigurationData
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.repo.ManagedConfigurationRepoImpl
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.utils.ARRIVAL
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FORM_SUBMISSION
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import kotlin.test.assertEquals

class DeepLinkUseCaseTest {

    private val deepLinkUrl =
        "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''~UUID~''], ''order'':''~ORDER#~''}"
    private lateinit var deepLinkUseCase: DeepLinkUseCase
    private lateinit var context: Context
    private lateinit var managedConfigurationRepo: ManagedConfigurationRepoImpl

    @Before
    fun setup() {
        context = mockk()
        managedConfigurationRepo = mockk()
        deepLinkUseCase = spyk(DeepLinkUseCase(managedConfigurationRepo = managedConfigurationRepo))
    }

    @Test
    fun testGetDeepLinkDataFromManagedConfiguration() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl)
            )
        )
        every {managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(any())} returns deepLinkConfigurationData
        assertEquals(deepLinkConfigurationData, deepLinkUseCase.getDeepLinkDataFromManagedConfiguration(""))
    }

    @Test
    fun testCheckAndHandleDeepLinkConfigurationForArrival() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl))
        )
        every { deepLinkUseCase.getDeepLinkDataFromManagedConfiguration(any()) } returns deepLinkConfigurationData
        every { deepLinkUseCase.getDeepLinkOnArrival(any()) } returns deepLinkUrl
        every { context.startActivity(any()) } just runs

        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(context, "")

        verify(exactly = 1) {
            deepLinkUseCase.launchAppFromConfiguration(context, deepLinkUrl)
        }
    }

    @Test
    fun testCheckAndHandleDeepLinkConfigurationForFormSubmission() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    formName = "Delivery info test",
                    formId = "19663",
                    uuid = "1234",
                    fieldValueExists = "ORDER#",
                    deepLinkUrl = deepLinkUrl
                )
            )
        )
        every { deepLinkUseCase.getDeepLinkDataFromManagedConfiguration(any()) } returns deepLinkConfigurationData
        every { deepLinkUseCase.getDeepLinkOnArrival(any()) } returns deepLinkUrl
        every { context.startActivity(any()) } just runs
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 19663),
            formFieldsList = arrayListOf(
                FormField(qtext = "ORDER#").apply {
                    uiData = "8888"
                }
            )
        )
        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(context, formTemplateData, "")
        val expectedURL = "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''1234''], ''order'':''8888''}"
        verify(exactly = 1) {
            deepLinkUseCase.launchAppFromConfiguration(context, expectedURL)
        }
    }

    @Test
    fun testGetDeepLinkOnArrival() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl)
            )
        )
        assertEquals(
            deepLinkUrl, deepLinkUseCase.getDeepLinkOnArrival(deepLinkConfigurationData)
        )
    }

    @Test
    fun testGetDeepLinkOnArrivalIfTriggerIsNotArrival() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl)
            )
        )
        assertEquals(
            EMPTY_STRING, deepLinkUseCase.getDeepLinkOnArrival(deepLinkConfigurationData)
        )
    }

    @Test
    fun testGetDeepLinkOnArrivalIfConfigListIsNull() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf()
        )
        assertEquals(
            EMPTY_STRING, deepLinkUseCase.getDeepLinkOnArrival(deepLinkConfigurationData)
        )
    }

    @Test
    fun checkAndHandleDeepLinkConfigurationForArrival_deepLinkConfigurationDataIsNull() {
        val caller = "testCaller"
        every { managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(caller) } returns null

        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(context, caller)

        // Verify that no deep link is launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun checkAndHandleDeepLinkConfigurationForArrival_deepLinkTriggerIsFormSubmission() {
        val caller = "testCaller"
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = FORM_SUBMISSION,
            formRelatedConfigurationDataList = listOf()
        )
        every { managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(caller) } returns deepLinkConfigurationData

        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(context, caller)

        // Verify that no deep link is launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun checkAndHandleDeepLinkConfigurationForFormSubmission_deepLinkConfigurationDataIsNull() {
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 12345),
            formFieldsList = arrayListOf(FormField(qtext = "ORDER#").apply { uiData = "1234" })
        )
        every { managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(any()) } returns null

        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(context, formTemplateData, "testCaller")

        // Verify that no deep link is launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun checkAndHandleDeepLinkConfigurationForFormSubmission_deepLinkTriggerIsArrival() {
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 12345),
            formFieldsList = arrayListOf(FormField(qtext = "ORDER#").apply { uiData = "1234" })
        )
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = ARRIVAL,
            formRelatedConfigurationDataList = listOf()
        )
        every { managedConfigurationRepo.getDeepLinkDataFromManagedConfiguration(any()) } returns deepLinkConfigurationData

        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(context, formTemplateData, "testCaller")

        // Verify that no deep link is launched
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun testGetDeepLinkOnArrivalIfUrlIsEmpty() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = EMPTY_STRING)
            )
        )
        assertEquals(
            EMPTY_STRING, deepLinkUseCase.getDeepLinkOnArrival(deepLinkConfigurationData)
        )
    }

    @Test
    fun testParseDeepLinkUrlOnFormSubmission() {
        val data = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "91234",
                    uuid = "111222333",
                    fieldValueExists = "WEIGHT"
                ),
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "12349",
                    uuid = "888888"
                )
            )
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 91234),
            formFieldsList = arrayListOf(
                FormField(qtext = "WEIGHT").apply {
                    uiData = "230"
                },
                FormField(qtext = "ORDER#").apply {
                    uiData = "1234"
                }
            )
        )
        val processedUrl =
            "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''111222333''], ''order'':''1234''}"

        val actualData = deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(data, formTemplateData)
        assertEquals(processedUrl, actualData.encodedUrl)
    }

    @Test
    fun testParseDeepLinkUrlOnFormSubmissionIfFilteredListIsNull() {
        val deepLinkConfigData = DeepLinkConfigurationData(
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "91234",
                    uuid = "111222333",
                    fieldValueExists = "WEIGHT"
                )
            )
        )
        val formTemplateData = FormTemplate(
            FormDef(formid = 78907),
            arrayListOf(FormField(qtext = "WEIGHT"))
        )
        val actualData = deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(deepLinkConfigData, formTemplateData)
        assertEquals(EMPTY_STRING, actualData.encodedUrl)
    }

    @Test
    fun testParseDeepLinkUrlOnFormSubmissionIfUrlIsEmpty() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    formId = "78907",
                    deepLinkUrl = EMPTY_STRING
                )
            )
        )
        val formTemplateData = FormTemplate(
            FormDef(formid = 78907),
            arrayListOf(FormField(qtext = "WEIGHT"))
        )
        assertEquals(
            EMPTY_STRING,
            deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(deepLinkConfigurationData, formTemplateData).encodedUrl
        )
    }

    @Test
    fun testParseDeepLinkUrlOnFormSubmissionIfBothUrlAndFilteredListAreEmpty() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    formId = "56789",
                    deepLinkUrl = EMPTY_STRING
                )
            )
        )
        val formTemplateData = FormTemplate(
            FormDef(formid = 78907),
            arrayListOf(FormField(qtext = "WEIGHT"))
        )
        assertEquals(
            EMPTY_STRING,
            deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(deepLinkConfigurationData, formTemplateData).encodedUrl
        )
    }

    @Test
    fun testFilterConfigurationWithMatchingFormIdAndCriteria() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "91234",
                    uuid = "111222333",
                    fieldValueExists = "WEIGHT"
                ),
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "12349",
                    uuid = "888888"
                )
            )
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 91234),
            formFieldsList = arrayListOf(
                FormField(qtext = "WEIGHT").apply {
                    uiData = "150"
                },
                FormField(qtext = "ORDER#").apply {
                    uiData = "1234"
                }
            )
        )
        val expectedFormConfigData = FormRelatedConfigurationData(
            deepLinkUrl = deepLinkUrl,
            formId = "91234",
            uuid = "111222333",
            fieldValueExists = "WEIGHT"
        )
        val actualFormConfigData =
            deepLinkUseCase.filterConfigurationWithMatchingFormIdAndCriteria(
                deepLinkConfigurationData,
                formTemplateData
            )
        assertEquals(expectedFormConfigData, actualFormConfigData)
    }

    @Test
    fun testFilterConfigurationWithMatchingFormIdAndCriteriaIfNotMatching() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(
                    deepLinkUrl = deepLinkUrl,
                    formId = "12349",
                    uuid = "888888"
                )
            )
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 12345),
            formFieldsList = arrayListOf(
                FormField(qtext = "WEIGHT").apply {
                    uiData = "150"
                }
            )
        )
        val actualFormConfigData =
            deepLinkUseCase.filterConfigurationWithMatchingFormIdAndCriteria(
                deepLinkConfigurationData,
                formTemplateData
            )
        assertEquals(null, actualFormConfigData)
    }

    @Test
    fun testIsDriverFormMatchingWithConfig() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            formName = "DELIVERY INFO",
            formId = "98776",
            uuid = "111-000-988",
            deepLinkUrl = deepLinkUrl,
            fieldValueExists = "SEAL#"
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 98776),
            formFieldsList = arrayListOf(
                FormField(qtext = "SEAL#").apply {
                    uiData = "150"
                }
            )
        )
        assertEquals(true,
            deepLinkUseCase.isDriverFormMatchingWithConfig(formRelatedConfigurationData, formTemplateData)
        )
    }

    @Test
    fun testIsDriverFormMatchingWithConfigReturnsFalse() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            formName = "DELIVERY INFO",
            formId = "98776",
            uuid = "111-000-988",
            deepLinkUrl = deepLinkUrl,
            fieldValueExists = "SEAL#"
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 76898),
            formFieldsList = arrayListOf(
                FormField(qtext = "SEAL#")
            )
        )
        assertEquals(false,
            deepLinkUseCase.isDriverFormMatchingWithConfig(formRelatedConfigurationData, formTemplateData)
        )
    }

    @Test
    fun testCheckFieldValueExists() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = "BOL#"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "BOL#").apply { uiData = "123" }
            )
        )
        val result = deepLinkUseCase.checkFieldValueExists(formConfigData, formTemplate)
        assertEquals("123", result)
    }

    @Test
    fun testCheckFieldValueExistsIfNotMatching() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = "BOL#"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "PIECES").apply { uiData = "123" }
            )
        )
        val result = deepLinkUseCase.checkFieldValueExists(formConfigData, formTemplate)
        assertEquals(EMPTY_STRING, result)
    }

    @Test
    fun testCheckFieldValueNotExists() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueNotExists = "BOL#"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "BOL#").apply { uiData = "123" }
            )
        )
        val result = deepLinkUseCase.checkFieldValueNotExists(formConfigData, formTemplate)
        assertEquals("123", result)
    }

    @Test
    fun testCheckFieldValueNotExistsIfNotMatching() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueNotExists = "BOL#"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "LUMPER USED?")
            )
        )
        val result = deepLinkUseCase.checkFieldValueNotExists(formConfigData, formTemplate)
        assertEquals("-1", result)
    }

    @Test
    fun testCheckFieldAndValueEquals() {
        val formConfigData = FormRelatedConfigurationData(
            fieldAndValueEquals = "DRIVER UNLOAD?=NO"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "DRIVER UNLOAD?").apply { uiData = "NO" }
            )
        )
        val result = deepLinkUseCase.checkFieldAndValueEquals(formConfigData, formTemplate)
        assertEquals("NO", result)
    }

    @Test
    fun testCheckFieldAndValueEqualsIfNotMatching() {
        val formConfigData = FormRelatedConfigurationData(
            fieldAndValueEquals = "ARE YOU LATE?=NO"
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "DRIVER UNLOAD?").apply { uiData = "YES" }
            )
        )
        val result = deepLinkUseCase.checkFieldAndValueEquals(formConfigData, formTemplate)
        assertEquals(EMPTY_STRING, result)
    }

    @Test
    fun testCheckFieldAndValueEqualsIfSizeIsNotTwo() {
        val formConfigData = FormRelatedConfigurationData(
            fieldAndValueEquals = "DRIVER UNLOAD?="
        )
        val formTemplate = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "DRIVER UNLOAD?").apply { uiData = "NO" }
            )
        )
        val result = deepLinkUseCase.checkFieldAndValueEquals(formConfigData, formTemplate)
        assertEquals(EMPTY_STRING, result)
    }

    @Test
    fun testReplaceTheFieldsWithDataEnteredByDriver() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            deepLinkUrl = deepLinkUrl,
            formId = "91234",
            uuid = "111222333"
        )
        formRelatedConfigurationData.fieldsListToBeReplacedInUrl.addAll(arrayOf("UUID", "ORDER#"))
        val formTemplateData = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "ORDER#").apply {
                    uiData = "1234"
                }
            )
        )
        val expectedReplacedURL =
            "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''111222333''], ''order'':''1234''}"
        val actualReplacedURL = deepLinkUseCase.replaceTheFieldsWithDataEnteredByDriver(
            formRelatedConfigurationData,
            formTemplateData
        )
        assertEquals(expectedReplacedURL, actualReplacedURL)
    }

    @Test
    fun testReplaceTheFieldsWithDataEnteredByDriverIfNotMatching() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            deepLinkUrl = deepLinkUrl,
            formId = "91234",
            uuid = "111222333"
        )
        formRelatedConfigurationData.fieldsListToBeReplacedInUrl.addAll(arrayOf("UUID", "ORDER#"))
        val formTemplateData = FormTemplate(
            formDef = FormDef(),
            formFieldsList = arrayListOf(
                FormField(qtext = "WEIGHT").apply {
                    uiData = "1234"
                }
            )
        )
        val expectedReplacedURL =
            "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''111222333''], ''order'':''''}"
        val actualReplacedURL = deepLinkUseCase.replaceTheFieldsWithDataEnteredByDriver(
            formRelatedConfigurationData,
            formTemplateData
        )
        assertEquals(expectedReplacedURL, actualReplacedURL)
    }

    @Test
    fun testGetFieldNamesFromUrlToReplace() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            deepLinkUrl = deepLinkUrl
        )
        deepLinkUseCase.getFieldNamesFromUrlToReplace(formRelatedConfigurationData)
        val expectedFieldNames = mutableListOf("UUID", "ORDER#")
        assertEquals(expectedFieldNames, formRelatedConfigurationData.fieldsListToBeReplacedInUrl)
    }

    @Test
    fun testGetFieldNamesFromUrlToReplaceIfEmpty() {
        val formRelatedConfigurationData = FormRelatedConfigurationData(
            deepLinkUrl =
            "https://app.withvector.com/actions/entity/create?jsonProps={''docTypeIds'': [''''], ''order'':''''}"
        )
        deepLinkUseCase.getFieldNamesFromUrlToReplace(formRelatedConfigurationData)
        val expectedFieldNames = mutableListOf<String>()
        assertEquals(expectedFieldNames, formRelatedConfigurationData.fieldsListToBeReplacedInUrl)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldValueExists() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = "WEIGHT",
            fieldValueNotExists = EMPTY_STRING
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(qtext = "WEIGHT").apply { uiData = "230" },
                FormField(qtext = "LUMPER USED?").apply { uiData = "YES" }
            )
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(true, actualResult)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldValueExistsFails() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = "WEIGHT",
            fieldValueNotExists = EMPTY_STRING
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf( FormField(qtext = "TEMP").apply { uiData = "45" })
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(false, actualResult)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldValueNotExists() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueNotExists = "WEIGHT",
            fieldAndValueEquals = EMPTY_STRING
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf( FormField(qtext = "WEIGHT").apply { uiData = "" })
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(true, actualResult)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldValueNotExistsFails() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueNotExists = "WEIGHT",
            fieldAndValueEquals = EMPTY_STRING
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(qtext = "DRIVER UNLOAD?").apply { uiData = "NO" },
                FormField(qtext = "LUMPER USED?").apply { uiData = "YES" },
            )
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(false, actualResult)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldAndValueExists() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = EMPTY_STRING,
            fieldValueNotExists = EMPTY_STRING,
            fieldAndValueEquals = "LUMPER USED?=YES"
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf( FormField(qtext = "LUMPER USED?").apply { uiData = "YES" })
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(true, actualResult)
    }

    @Test
    fun testCheckFieldValuesBasedOnConditionForFieldAndValueExistsFails() {
        val formConfigData = FormRelatedConfigurationData(
            fieldValueExists = EMPTY_STRING,
            fieldValueNotExists = EMPTY_STRING,
            fieldAndValueEquals = "LUMPER USED?=YES"
        )
        val formTemplateData = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(qtext = "WEIGHT").apply { uiData = "230" },
                FormField(qtext = "LUMPER USED?").apply { uiData = "NO" }
            )
        )
        val actualResult = deepLinkUseCase.checkFieldValuesBasedOnCondition(formConfigData, formTemplateData)
        assertEquals(false, actualResult)
    }

    @Test
    fun testIsTriggeredOnArrivalReturnsTrue() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl)
            )
        )
        assertEquals(true, deepLinkUseCase.isTriggeredOnArrival(deepLinkConfigurationData))
    }

    @Test
    fun testIfTriggerArrivalButListIsEmpty() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Arrival",
            formRelatedConfigurationDataList = listOf()
        )
        assertEquals(false, deepLinkUseCase.isTriggeredOnArrival(deepLinkConfigurationData))
    }

    @Test
    fun testIfTriggerIsNotArrival() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(deepLinkUrl = deepLinkUrl)
            )
        )
        assertEquals(false, deepLinkUseCase.isTriggeredOnArrival(deepLinkConfigurationData))
    }

    @Test
    fun testIfTriggerIsNotArrivalAndListIsEmpty() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData()
            )
        )
        assertEquals(false, deepLinkUseCase.isTriggeredOnArrival(deepLinkConfigurationData))
    }

    @Test
    fun testIfConfigurationDataNull() {
        assertEquals(false, deepLinkUseCase.isTriggeredOnArrival(null))
    }


    @Test
    fun testIsDeepLinkNotEmpty() {
        val deepLinkUrl1 = deepLinkUrl
        val deepLinkUrl2 = EMPTY_STRING
        assertEquals(true, deepLinkUseCase.isDeepLinkNotEmpty(deepLinkUrl1))
        assertEquals(false, deepLinkUseCase.isDeepLinkNotEmpty(deepLinkUrl2))
    }

    @Test
    fun testLaunchAppFromConfiguration() {
        val url = "https://vector.com"
        every { context.startActivity(any()) } just runs
        deepLinkUseCase.launchAppFromConfiguration(context, url)

        verify(exactly = 1) {
            context.startActivity(any())
        }
    }

    @Test
    fun testLaunchAppFromConfigurationIfUrlIsNull() {
        val url = EMPTY_STRING
        every { context.startActivity(any()) } just runs
        deepLinkUseCase.launchAppFromConfiguration(context, url)

        verify(exactly = 0) {
            context.startActivity(any())
        }
    }

    @Test
    fun encodeURLBeforeLaunching_noQueryParams_returnsOriginalUrl() {
        val url = "https://app.withvector.com/actions/workflow/resume"
        val expectedUrl = "https://app.withvector.com/actions/workflow/resume"
        val actualUrl = deepLinkUseCase.encodeURLBeforeLaunching(url)
        assertEquals(expectedUrl, actualUrl)
    }


    @Test
    fun encodeURLBeforeLaunching_withQueryParams_returnsEncodedUrl() {
        val expectedURL = "https://app.withvector.com/actions/entity/create?jsonProps=%7B%22docTypeIds%22%3A%20%5B%22%7EUUID%7E%22%5D%2C%20%22order%22%3A%22%7EORDER%23%7E%22%7D"
        val actualURL = deepLinkUseCase.encodeURLBeforeLaunching(deepLinkUrl)
        assertEquals(expectedURL, actualURL)
    }

    @Test
    fun encodeURLBeforeLaunching_throwsUnsupportedEncodingException() {
        val expectedUrl = EMPTY_STRING

        mockkStatic(URLEncoder::class)
        every { URLEncoder.encode(any(),Charsets.UTF_8.name()) } throws UnsupportedEncodingException("Unsupported Encoding")

        val actualUrl = deepLinkUseCase.encodeURLBeforeLaunching(deepLinkUrl)
        assertEquals(expectedUrl, actualUrl)

        unmockkStatic(URLEncoder::class)
    }

    @Test
    fun parseDeepLinkUrlOnFormSubmission_returnsEmpty_if_deep_link_params_not_matching_form_id() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(formId = "222222")
            )
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 11111),
            formFieldsList = arrayListOf(
                FormField(qtext = "ORDER#", formid = 11111).apply {
                    uiData = "1234"
                }
        ))
        coEvery { deepLinkUseCase.filterConfigurationWithMatchingFormIdAndCriteria(any(),any()) } returns FormRelatedConfigurationData()

        val result = deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(deepLinkConfigurationData, formTemplateData)
        assertEquals(EMPTY_STRING, result.encodedUrl)
    }

    @Test
    fun parseDeepLinkUrlOnFormSubmission_if_deep_link_params_matching_form_id() {
        val deepLinkConfigurationData = DeepLinkConfigurationData(
            appName = "Vector",
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = listOf(
                FormRelatedConfigurationData(formId = "11111", deepLinkUrl = deepLinkUrl)
            )
        )
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 11111),
            formFieldsList = arrayListOf(
                FormField(qtext = "ORDER#", formid = 11111).apply {
                    uiData = "1234"
                }
            ))

        val result = deepLinkUseCase.parseDeepLinkUrlOnFormSubmission(deepLinkConfigurationData, formTemplateData)
        assertEquals(EMPTY_STRING, result.encodedUrl)
    }

    @After
    fun after() {
        unmockkAll()
    }

}