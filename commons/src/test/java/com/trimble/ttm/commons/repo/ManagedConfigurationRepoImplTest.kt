package com.trimble.ttm.commons.repo

import android.os.Bundle
import com.trimble.ttm.commons.datasource.ManagedConfigurationDataSource
import com.trimble.ttm.commons.model.DeepLinkConfigurationData
import com.trimble.ttm.commons.model.FormRelatedConfigurationData
import com.trimble.ttm.commons.model.ManagedConfigurationData
import com.trimble.ttm.commons.utils.APP_NAME_KEY
import com.trimble.ttm.commons.utils.DEEP_LINK_CONFIGURATION
import com.trimble.ttm.commons.utils.DEEP_LINK_PARAMETER_ONE
import com.trimble.ttm.commons.utils.DEEP_LINK_URL
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FIELD_AND_VALUE_EQUALS
import com.trimble.ttm.commons.utils.FIELD_VALUE_EXISTS
import com.trimble.ttm.commons.utils.FIELD_VALUE_NOT_EXISTS
import com.trimble.ttm.commons.utils.FORM_ID
import com.trimble.ttm.commons.utils.FORM_NAME
import com.trimble.ttm.commons.utils.FORM_RELATED_CONFIGURATION
import com.trimble.ttm.commons.utils.FORM_RELATED_CONFIGURATION_LIST
import com.trimble.ttm.commons.utils.POLYGONAL_OPT_OUT_KEY
import com.trimble.ttm.commons.utils.TRIGGER_VALUE
import com.trimble.ttm.commons.utils.UUID
import com.trimble.ttm.commons.utils.WORKFLOW_EVENTS_COMMUNICATION
import com.trimble.ttm.commons.utils.getStringOrDefaultValue
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedConfigurationRepoImplTest {

    @RelaxedMockK
    private lateinit var managedConfigurationDataSource: ManagedConfigurationDataSource

    @RelaxedMockK
    private lateinit var bundle: Bundle

    private lateinit var managedConfigurationRepoImpl: ManagedConfigurationRepoImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        managedConfigurationRepoImpl = ManagedConfigurationRepoImpl(managedConfigurationDataSource)
    }

    @Test
    fun `verify app name is returned for communicating workflow events`() {
        managedConfigurationRepoImpl.managedConfigurationData = ManagedConfigurationData(
            appPackageNameForWorkflowEventsCommunication = "com.linde",
            deepLinkConfigurationData = DeepLinkConfigurationData()
        )
        val appNameForWorkflowEventsCommunication =
            managedConfigurationRepoImpl.getAppPackageForWorkflowEventsCommunicationFromManageConfiguration(
                ""
            )
        assertEquals("com.linde", appNameForWorkflowEventsCommunication)
    }

    @Test
    fun `verify  polygonal opt out is returned for true`() {
        managedConfigurationRepoImpl.managedConfigurationData = ManagedConfigurationData(
            polygonalOptOut = true,
            deepLinkConfigurationData = DeepLinkConfigurationData()
        )
        val polygonalOptOutData =
            managedConfigurationRepoImpl.getPolygonalOptOutFromManageConfiguration(
                ""
            )
        assertEquals(true, polygonalOptOutData)
    }

    @Test
    fun `verify polygonal opt out is returned for false`() {
        managedConfigurationRepoImpl.managedConfigurationData = ManagedConfigurationData(
            polygonalOptOut = false,
            deepLinkConfigurationData = DeepLinkConfigurationData()
        )
        val polygonalOptOutData =
            managedConfigurationRepoImpl.getPolygonalOptOutFromManageConfiguration(
                ""
            )
        assertEquals(false, polygonalOptOutData)
    }

    @Test
    fun `verify deep link config data`() {
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1233",
                uuid = "44455566",
                fieldValueExists = "Field1"
            )
        )
        managedConfigurationRepoImpl.managedConfigurationData = ManagedConfigurationData(
            appPackageNameForWorkflowEventsCommunication = "",
            deepLinkConfigurationData = DeepLinkConfigurationData(
                deepLinkTrigger = "Form Submission",
                appName = "Vector",
                formRelatedConfigurationDataList = formConfigList
            )
        )
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData(
            deepLinkTrigger = "Form Submission",
            appName = "Vector",
            formRelatedConfigurationDataList = formConfigList
        )
        val actualDeepLinkConfigurationData =
            managedConfigurationRepoImpl.getDeepLinkDataFromManagedConfiguration("")
        assertEquals(expectedDeepLinkConfigurationData, actualDeepLinkConfigurationData)
    }

    @Test
    fun `verify config is fetched from server when data is not available in local`() {
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1234",
                uuid = "44445555",
                fieldValueExists = "Field1"
            )
        )
        every { managedConfigurationDataSource.fetchManagedConfiguration("") } returns bundle
        every { bundle.getBundle(any()) } returns bundle
        every { bundle.getString(WORKFLOW_EVENTS_COMMUNICATION) } returns "com.linde"
        every { bundle.getString(TRIGGER_VALUE) } returns "Form Submission"
        every { bundle.getString(APP_NAME_KEY) } returns "Vector"
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 1) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 2) } returns false
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 3) } returns false
        every { bundle.getString(FORM_NAME + 1) } returns "Pickup Load"
        every { bundle.getString(FORM_ID + 1) } returns "1234"
        every { bundle.getString(UUID + 1) } returns "44445555"
        every { bundle.getString(FIELD_VALUE_EXISTS + 1) } returns "Field1"
        every { bundle.getString(DEEP_LINK_URL + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_VALUE_NOT_EXISTS + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_AND_VALUE_EQUALS + 1) } returns EMPTY_STRING
        val managedConfigurationData =
            managedConfigurationRepoImpl.fetchManagedConfigDataFromCache("")
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData(
            deepLinkTrigger = "Form Submission",
            appName = "Vector",
            formRelatedConfigurationDataList = formConfigList
        )
        verify(exactly = 1) { managedConfigurationDataSource.fetchManagedConfiguration("") }
        assertEquals(
            "com.linde",
            managedConfigurationData?.appPackageNameForWorkflowEventsCommunication
        )
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationData?.deepLinkConfigurationData
        )
    }

    @Test
    fun `verify config is fetched not from server when data is available in local`() {
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1233",
                uuid = "44455566",
                fieldValueExists = "Field1"
            )
        )
        managedConfigurationRepoImpl.managedConfigurationData = ManagedConfigurationData(
            appPackageNameForWorkflowEventsCommunication = "com.linde",
            deepLinkConfigurationData = DeepLinkConfigurationData(
                deepLinkTrigger = "Form Submission",
                formRelatedConfigurationDataList = formConfigList
            )
        )
        val managedConfigurationData =
            managedConfigurationRepoImpl.fetchManagedConfigDataFromCache("")
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData(
            deepLinkTrigger = "Form Submission",
            formRelatedConfigurationDataList = formConfigList
        )
        verify(exactly = 0) { managedConfigurationDataSource.fetchManagedConfiguration("") }
        assertEquals(
            "com.linde",
            managedConfigurationData?.appPackageNameForWorkflowEventsCommunication
        )
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationData?.deepLinkConfigurationData
        )
    }

    @Test
    fun `verify managedConfigurationData when appRestrictionsBundle is null`() {
        every { managedConfigurationDataSource.fetchManagedConfiguration(any()) } returns null
        assertNull(managedConfigurationRepoImpl.fetchManagedConfigDataFromCache(""))
    }

    @Test
    fun `check if the formConfigList present in the bundle`() {
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns true
        assertTrue(
            managedConfigurationRepoImpl.isFormRelatedConfigurationInTheBundle(
                bundle
            )
        )
    }

    @Test
    fun `check if the formConfigList not present in the bundle`() {
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns false
        assertFalse(
            managedConfigurationRepoImpl.isFormRelatedConfigurationInTheBundle(
                bundle
            )
        )
    }

    @Test
    fun `check if managedConfigurationDataFromBundle returns the expected ManagedConfigurationData`() {
        every { bundle.getBundle(any()) } returns bundle
        every { bundle.getString(WORKFLOW_EVENTS_COMMUNICATION) } returns "com.linde"
        every { bundle.getBoolean(POLYGONAL_OPT_OUT_KEY) } returns true
        every { bundle.getString(TRIGGER_VALUE) } returns "Form Submission"
        every { bundle.getString(APP_NAME_KEY) } returns "Vector"
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 1) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 2) } returns false
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 3) } returns false
        every { bundle.getString(FORM_NAME + 1) } returns "Pickup Load"
        every { bundle.getString(FORM_ID + 1) } returns "1234"
        every { bundle.getString(UUID + 1) } returns "44445555"
        every { bundle.getString(FIELD_VALUE_EXISTS + 1) } returns "Field1"
        every { bundle.getString(DEEP_LINK_URL + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_VALUE_NOT_EXISTS + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_AND_VALUE_EQUALS + 1) } returns EMPTY_STRING
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1234",
                uuid = "44445555",
                fieldValueExists = "Field1"
            )
        )
        val expectedManagedConfigurationData = ManagedConfigurationData(
            appPackageNameForWorkflowEventsCommunication = "com.linde",
            polygonalOptOut = true,
            deepLinkConfigurationData = DeepLinkConfigurationData(
                deepLinkTrigger = "Form Submission",
                appName = "Vector",
                formRelatedConfigurationDataList = formConfigList
            )
        )
        assertEquals(
            expectedManagedConfigurationData,
            managedConfigurationRepoImpl.fetchManagedConfigurationDataFromBundle(bundle)
        )
    }

    @Test
    fun `check if fetchDeepLinkDataFromBundle returns the expected DeepLinkConfigurationData`() {
        every { bundle.getBundle(any()) } returns bundle
        every { bundle.getString(TRIGGER_VALUE) } returns "Form Submission"
        every { bundle.getString(APP_NAME_KEY) } returns "Vector"
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 1) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 2) } returns false
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 3) } returns false
        every { bundle.getString(FORM_NAME + 1) } returns "Pickup Load"
        every { bundle.getString(FORM_ID + 1) } returns "1234"
        every { bundle.getString(UUID + 1) } returns "44445555"
        every { bundle.getString(FIELD_VALUE_EXISTS + 1) } returns "Field1"
        every { bundle.getString(DEEP_LINK_URL + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_VALUE_NOT_EXISTS + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_AND_VALUE_EQUALS + 1) } returns EMPTY_STRING
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1234",
                uuid = "44445555",
                fieldValueExists = "Field1"
            )
        )
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData(
            deepLinkTrigger = "Form Submission",
            appName = "Vector",
            formRelatedConfigurationDataList = formConfigList
        )
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationRepoImpl.fetchDeepLinkDataFromBundle(bundle)
        )
    }

    @Test
    fun `check if fetchDeepLinkDataFromBundle returns the expected DeepLinkConfigurationData when deepLinkBundle is null`() {
        every { bundle.getBundle(DEEP_LINK_CONFIGURATION) } returns null
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData()
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationRepoImpl.fetchDeepLinkDataFromBundle(bundle)
        )
    }

    @Test
    fun `check if fetchFormRelatedDataFromBundle returns the expected DeepLinkConfigurationData when formRelated key is present in the bundle`() {
        every { bundle.getBundle(any()) } returns bundle
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 1) } returns true
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 2) } returns false
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION + 3) } returns false
        every { bundle.getString(FORM_NAME + 1) } returns "Pickup Load"
        every { bundle.getString(FORM_ID + 1) } returns "1234"
        every { bundle.getString(UUID + 1) } returns "44445555"
        every { bundle.getString(FIELD_VALUE_EXISTS + 1) } returns "Field1"
        every { bundle.getString(DEEP_LINK_URL + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_VALUE_NOT_EXISTS + 1) } returns EMPTY_STRING
        every { bundle.getString(FIELD_AND_VALUE_EQUALS + 1) } returns EMPTY_STRING
        val formConfigList = listOf(
            FormRelatedConfigurationData(
                formName = "Pickup Load",
                formId = "1234",
                uuid = "44445555",
                fieldValueExists = "Field1"
            )
        )
        val expectedDeepLinkConfigurationData = DeepLinkConfigurationData(
            deepLinkTrigger = "Form Submission",
            appName = "Vector",
            formRelatedConfigurationDataList = formConfigList
        )
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationRepoImpl.fetchFormRelatedDataFromBundle(
                bundle,
                "Form Submission",
                "Vector"
            )
        )
    }

    @Test
    fun `check if fetchFormRelatedDataFromBundle returns the expected DeepLinkConfigurationData when formRelatedListBundle is null`() {
        every { bundle.getBundle(FORM_RELATED_CONFIGURATION_LIST) } returns null
        every { bundle.getBundle(FORM_RELATED_CONFIGURATION + 1) } returns bundle
        val expectedDeepLinkConfigurationData =
            DeepLinkConfigurationData(deepLinkTrigger = "Form Submission", appName = "Vector")
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationRepoImpl.fetchFormRelatedDataFromBundle(
                bundle,
                "Form Submission",
                "Vector"
            )
        )
    }


    @Test
    fun `check if fetchFormRelatedDataFromBundle returns the expected DeepLinkConfigurationData when formRelated key is not present in the bundle`() {
        every { bundle.getBundle(any()) } returns bundle
        every { bundle.containsKey(FORM_RELATED_CONFIGURATION_LIST) } returns false
        val expectedDeepLinkConfigurationData =
            DeepLinkConfigurationData(deepLinkTrigger = "Form Submission", appName = "Vector")
        assertEquals(
            expectedDeepLinkConfigurationData,
            managedConfigurationRepoImpl.fetchFormRelatedDataFromBundle(
                bundle,
                "Form Submission",
                "Vector"
            )
        )
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with valid data - 1 from static parameter, 2 from parcelableList `() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = listOf<Bundle>(
            mock(Bundle::class.java),
            mock(Bundle::class.java)
        )

        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            mock(Bundle::class.java)
        )

        parcelableList.forEachIndexed { index, bundle ->
            `when`(bundle.getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName$index")
            `when`(bundle.getStringOrDefaultValue(FORM_ID)).thenReturn("FormId$index")
            `when`(bundle.getStringOrDefaultValue(UUID)).thenReturn("UUID$index")
            `when`(bundle.getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals$index")
        }

        val staticBundle =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)
        `when`(staticBundle?.getStringOrDefaultValue(FORM_NAME + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FORM_ID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundle?.getStringOrDefaultValue(UUID + DEEP_LINK_PARAMETER_ONE)).thenReturn("StaticUUID")
        `when`(staticBundle?.getStringOrDefaultValue(DEEP_LINK_URL + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // 1 from static parameter, 2 from parcelableList
        assertEquals(3, result.size)
        result.forEachIndexed { index, formRelatedConfigurationData ->
            if (index == 0) {
                assertEquals("StaticFormName", formRelatedConfigurationData.formName)
                assertEquals("StaticFormId", formRelatedConfigurationData.formId)
                assertEquals("StaticUUID", formRelatedConfigurationData.uuid)
                assertEquals("StaticDeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
                assertEquals(
                    "StaticFieldValueExists",
                    formRelatedConfigurationData.fieldValueExists
                )
                assertEquals(
                    "StaticFieldValueNotExists",
                    formRelatedConfigurationData.fieldValueNotExists
                )
                assertEquals(
                    "StaticFieldAndValueEquals",
                    formRelatedConfigurationData.fieldAndValueEquals
                )
            } else {
                assertEquals("FormName${index - 1}", formRelatedConfigurationData.formName)
                assertEquals("FormId${index - 1}", formRelatedConfigurationData.formId)
                assertEquals("UUID${index - 1}", formRelatedConfigurationData.uuid)
                assertEquals("DeepLinkUrl${index - 1}", formRelatedConfigurationData.deepLinkUrl)
                assertEquals(
                    "FieldValueExists${index - 1}",
                    formRelatedConfigurationData.fieldValueExists
                )
                assertEquals(
                    "FieldValueNotExists${index - 1}",
                    formRelatedConfigurationData.fieldValueNotExists
                )
                assertEquals(
                    "FieldAndValueEquals${index - 1}",
                    formRelatedConfigurationData.fieldAndValueEquals
                )
            }
        }
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with empty data`() {
        val formRelatedConfigurationListBundle = bundle
        val parcelableList = emptyList<Bundle>()

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with duplicate data - One static parameter and two similar duplicate parameters`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = listOf<Bundle>(
            mock(Bundle::class.java),
            mock(Bundle::class.java)
        )
        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            mock(Bundle::class.java)
        )

        parcelableList.forEachIndexed { _, bundle ->
            `when`(bundle.getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName")
            `when`(bundle.getStringOrDefaultValue(FORM_ID)).thenReturn("FormId")
            `when`(bundle.getStringOrDefaultValue(UUID)).thenReturn("UUID")
            `when`(bundle.getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists")
            `when`(bundle.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals")
        }

        val staticBundle =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)
        `when`(staticBundle?.getStringOrDefaultValue(FORM_NAME + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FORM_ID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundle?.getStringOrDefaultValue(UUID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticUUID"
        )
        `when`(staticBundle?.getStringOrDefaultValue(DEEP_LINK_URL + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // Two Unique - one from static and 1 from parcelableList, with a duplicate eliminated
        assertEquals(2, result.size)
        result.forEachIndexed { index, formRelatedConfigurationData ->
            if (index == 0) {
                assertEquals("StaticFormName", formRelatedConfigurationData.formName)
                assertEquals("StaticFormId", formRelatedConfigurationData.formId)
                assertEquals("StaticUUID", formRelatedConfigurationData.uuid)
                assertEquals("StaticDeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
                assertEquals(
                    "StaticFieldValueExists",
                    formRelatedConfigurationData.fieldValueExists
                )
                assertEquals(
                    "StaticFieldValueNotExists",
                    formRelatedConfigurationData.fieldValueNotExists
                )
                assertEquals(
                    "StaticFieldAndValueEquals",
                    formRelatedConfigurationData.fieldAndValueEquals
                )
            } else {
                assertEquals("FormName", formRelatedConfigurationData.formName)
                assertEquals("FormId", formRelatedConfigurationData.formId)
                assertEquals("UUID", formRelatedConfigurationData.uuid)
                assertEquals("DeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
                assertEquals("FieldValueExists", formRelatedConfigurationData.fieldValueExists)
                assertEquals(
                    "FieldValueNotExists",
                    formRelatedConfigurationData.fieldValueNotExists
                )
                assertEquals(
                    "FieldAndValueEquals",
                    formRelatedConfigurationData.fieldAndValueEquals
                )
            }
        }
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with mixed unique and duplicate data - 1 from static parameters and 2 unique from parcelable list`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = listOf<Bundle>(
            mock(Bundle::class.java),
            mock(Bundle::class.java),
            mock(Bundle::class.java)
        )

        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            mock(Bundle::class.java)
        )

        `when`(parcelableList[0].getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName1")
        `when`(parcelableList[0].getStringOrDefaultValue(FORM_ID)).thenReturn("FormId1")
        `when`(parcelableList[0].getStringOrDefaultValue(UUID)).thenReturn("UUID1")
        `when`(parcelableList[0].getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl1")
        `when`(parcelableList[0].getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists1")
        `when`(parcelableList[0].getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists1")
        `when`(parcelableList[0].getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals1")
        `when`(parcelableList[1].getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName2")
        `when`(parcelableList[1].getStringOrDefaultValue(FORM_ID)).thenReturn("FormId2")
        `when`(parcelableList[1].getStringOrDefaultValue(UUID)).thenReturn("UUID2")
        `when`(parcelableList[1].getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl2")
        `when`(parcelableList[1].getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists2")
        `when`(parcelableList[1].getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists2")
        `when`(parcelableList[1].getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals2")

        `when`(parcelableList[2].getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName1")
        `when`(parcelableList[2].getStringOrDefaultValue(FORM_ID)).thenReturn("FormId1")
        `when`(parcelableList[2].getStringOrDefaultValue(UUID)).thenReturn("UUID1")
        `when`(parcelableList[2].getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl1")
        `when`(parcelableList[2].getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists1")
        `when`(parcelableList[2].getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists1")
        `when`(parcelableList[2].getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals1")

        val staticBundle =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)
        `when`(staticBundle?.getStringOrDefaultValue(FORM_NAME + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FORM_ID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundle?.getStringOrDefaultValue(UUID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticUUID"
        )
        `when`(staticBundle?.getStringOrDefaultValue(DEEP_LINK_URL + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // Expecting 3 unique entries: 1 from static parameters and 2 unique from parcelable list
        assertEquals(3, result.size)
        result.forEachIndexed { index, formRelatedConfigurationData ->
            when (index) {
                0 -> {
                    assertEquals("StaticFormName", formRelatedConfigurationData.formName)
                    assertEquals("StaticFormId", formRelatedConfigurationData.formId)
                    assertEquals("StaticUUID", formRelatedConfigurationData.uuid)
                    assertEquals("StaticDeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
                    assertEquals(
                        "StaticFieldValueExists",
                        formRelatedConfigurationData.fieldValueExists
                    )
                    assertEquals(
                        "StaticFieldValueNotExists",
                        formRelatedConfigurationData.fieldValueNotExists
                    )
                    assertEquals(
                        "StaticFieldAndValueEquals",
                        formRelatedConfigurationData.fieldAndValueEquals
                    )
                }

                1 -> {
                    assertEquals("FormName1", formRelatedConfigurationData.formName)
                    assertEquals("FormId1", formRelatedConfigurationData.formId)
                    assertEquals("UUID1", formRelatedConfigurationData.uuid)
                    assertEquals("DeepLinkUrl1", formRelatedConfigurationData.deepLinkUrl)
                    assertEquals(
                        "FieldValueExists1",
                        formRelatedConfigurationData.fieldValueExists
                    )
                    assertEquals(
                        "FieldValueNotExists1",
                        formRelatedConfigurationData.fieldValueNotExists
                    )
                    assertEquals(
                        "FieldAndValueEquals1",
                        formRelatedConfigurationData.fieldAndValueEquals
                    )
                }

                2 -> {
                    assertEquals("FormName2", formRelatedConfigurationData.formName)
                    assertEquals("FormId2", formRelatedConfigurationData.formId)
                    assertEquals("UUID2", formRelatedConfigurationData.uuid)
                    assertEquals("DeepLinkUrl2", formRelatedConfigurationData.deepLinkUrl)
                    assertEquals(
                        "FieldValueExists2",
                        formRelatedConfigurationData.fieldValueExists
                    )
                    assertEquals(
                        "FieldValueNotExists2",
                        formRelatedConfigurationData.fieldValueNotExists
                    )
                    assertEquals(
                        "FieldAndValueEquals2",
                        formRelatedConfigurationData.fieldAndValueEquals
                    )
                }
            }
        }
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with only static parameters`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = emptyList<Bundle>()

        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            mock(Bundle::class.java)
        )

        val staticBundle =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)
        `when`(staticBundle?.getStringOrDefaultValue(FORM_NAME + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FORM_ID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundle?.getStringOrDefaultValue(UUID + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticUUID"
        )
        `when`(staticBundle?.getStringOrDefaultValue(DEEP_LINK_URL + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundle?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // Expecting only 1 entry from static parameters
        assertEquals(1, result.size)
        val formRelatedConfigurationData = result[0]
        assertEquals("StaticFormName", formRelatedConfigurationData.formName)
        assertEquals("StaticFormId", formRelatedConfigurationData.formId)
        assertEquals("StaticUUID", formRelatedConfigurationData.uuid)
        assertEquals("StaticDeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
        assertEquals("StaticFieldValueExists", formRelatedConfigurationData.fieldValueExists)
        assertEquals(
            "StaticFieldValueNotExists",
            formRelatedConfigurationData.fieldValueNotExists
        )
        assertEquals(
            "StaticFieldAndValueEquals",
            formRelatedConfigurationData.fieldAndValueEquals
        )
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with duplicate static parameters`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = emptyList<Bundle>()

        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + 1)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + 1)).thenReturn(
            mock(Bundle::class.java)
        )
        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + 2)).thenReturn(
            true
        )
        `when`(formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + 2)).thenReturn(
            mock(Bundle::class.java)
        )

        val staticBundleOne =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)
        `when`(staticBundleOne?.getStringOrDefaultValue(FORM_NAME + 1)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(FORM_ID + 1)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(UUID + 1)).thenReturn(
            "StaticUUID"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(DEEP_LINK_URL + 1)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + 1)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + 1)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundleOne?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + 1)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val staticBundleTwo =
            formRelatedConfigurationListBundle.getBundle(FORM_RELATED_CONFIGURATION + 2)
        `when`(staticBundleTwo?.getStringOrDefaultValue(FORM_NAME + 2)).thenReturn(
            "StaticFormName"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(FORM_ID + 2)).thenReturn(
            "StaticFormId"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(UUID + 2)).thenReturn(
            "StaticUUID"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(DEEP_LINK_URL + 2)).thenReturn(
            "StaticDeepLinkUrl"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(FIELD_VALUE_EXISTS + 2)).thenReturn(
            "StaticFieldValueExists"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS + 2)).thenReturn(
            "StaticFieldValueNotExists"
        )
        `when`(staticBundleTwo?.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS + 2)).thenReturn(
            "StaticFieldAndValueEquals"
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // Expecting only 1 entry from static parameters, removing the duplicate static parameter
        assertEquals(1, result.size)
        val formRelatedConfigurationData = result[0]
        assertEquals("StaticFormName", formRelatedConfigurationData.formName)
        assertEquals("StaticFormId", formRelatedConfigurationData.formId)
        assertEquals("StaticUUID", formRelatedConfigurationData.uuid)
        assertEquals("StaticDeepLinkUrl", formRelatedConfigurationData.deepLinkUrl)
        assertEquals("StaticFieldValueExists", formRelatedConfigurationData.fieldValueExists)
        assertEquals(
            "StaticFieldValueNotExists",
            formRelatedConfigurationData.fieldValueNotExists
        )
        assertEquals(
            "StaticFieldAndValueEquals",
            formRelatedConfigurationData.fieldAndValueEquals
        )
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with only parcelable list`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = listOf<Bundle>(
            mock(Bundle::class.java),
            mock(Bundle::class.java)
        )

        parcelableList.forEachIndexed { index, bundle ->
            `when`(bundle.getStringOrDefaultValue(FORM_NAME)).thenReturn("FormName$index")
            `when`(bundle.getStringOrDefaultValue(FORM_ID)).thenReturn("FormId$index")
            `when`(bundle.getStringOrDefaultValue(UUID)).thenReturn("UUID$index")
            `when`(bundle.getStringOrDefaultValue(DEEP_LINK_URL)).thenReturn("DeepLinkUrl$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_EXISTS)).thenReturn("FieldValueExists$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_VALUE_NOT_EXISTS)).thenReturn("FieldValueNotExists$index")
            `when`(bundle.getStringOrDefaultValue(FIELD_AND_VALUE_EQUALS)).thenReturn("FieldAndValueEquals$index")
        }

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        // Expecting 2 entries from parcelable list
        assertEquals(2, result.size)
        result.forEachIndexed { index, formRelatedConfigurationData ->
            assertEquals("FormName$index", formRelatedConfigurationData.formName)
            assertEquals("FormId$index", formRelatedConfigurationData.formId)
            assertEquals("UUID$index", formRelatedConfigurationData.uuid)
            assertEquals("DeepLinkUrl$index", formRelatedConfigurationData.deepLinkUrl)
            assertEquals(
                "FieldValueExists$index",
                formRelatedConfigurationData.fieldValueExists
            )
            assertEquals(
                "FieldValueNotExists$index",
                formRelatedConfigurationData.fieldValueNotExists
            )
            assertEquals(
                "FieldAndValueEquals$index",
                formRelatedConfigurationData.fieldAndValueEquals
            )
        }
    }

    @Test
    fun `verify getFormRelatedDataFromBundle with no static parameters and empty parcelable list`() {
        val formRelatedConfigurationListBundle = mock(Bundle::class.java)
        val parcelableList = emptyList<Bundle>()
        `when`(formRelatedConfigurationListBundle.containsKey(FORM_RELATED_CONFIGURATION + DEEP_LINK_PARAMETER_ONE)).thenReturn(
            false
        )

        val result = managedConfigurationRepoImpl.getFormRelatedDataFromBundle(
            formRelatedConfigurationListBundle,
            parcelableList
        )

        assertEquals(0, result.size)
    }

    @After
    fun cleanUp() {
        unmockkAll()
    }
}