package com.trimble.ttm.routemanifest.utils

import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils.getErrorFields
import com.trimble.ttm.formlibrary.utils.FormUtils.getNonAutoFields
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FormValidationTest {

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun testGetErrorFields_emptyForm() {
        val form = FormTemplate()
        val errorFields = getErrorFields(form)
        assertEquals(0, errorFields.size)
    }

    @Test
    fun testGetErrorFields_noErrors() {
        val form = FormTemplate()
        form.formFieldsList.add(FormField(required = 0).apply { uiData = "some data" })
        val errorFields = getErrorFields(form)
        assertEquals(0, errorFields.size)
    }

    @Test
    fun testGetErrorFields_requiredFieldEmpty() {
        val form = FormTemplate()
        form.formFieldsList.add(FormField(required = 1).apply { uiData = "" })
        val errorFields = getErrorFields(form)
        assertEquals(1, errorFields.size)
    }

    @Test
    fun testGetErrorFields_fieldWithErrorMessage() {
        val form = FormTemplate()
        form.formFieldsList.add(FormField().apply { errorMessage = "Error message" })
        val errorFields = getErrorFields(form)
        assertEquals(1, errorFields.size)
    }

    @Test
    fun testGetErrorFields_fieldWithErrorMessageAndUiDataEmpty() {
        val form = FormTemplate()
        form.formFieldsList.add(FormField(required = 1).apply { uiData = EMPTY_STRING; errorMessage = "Error message" })
        val errorFields = getErrorFields(form)
        assertEquals(1, errorFields.size)
    }

    @Test
    fun testGetErrorFields_nonAutoFields() {
        val form = FormTemplate()
        form.formFieldsList.add(FormField(required = 1, qtype = FormFieldType.AUTO_DATE_TIME.ordinal).apply { uiData = "" })
        form.formFieldsList.add(FormField(required = 1, qtype = FormFieldType.TEXT.ordinal).apply { uiData = "" })
        val errorFields = getErrorFields(form)
        assertEquals(1, errorFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, errorFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoVehicleFuel() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_VEHICLE_FUEL.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoVehicleLatLong() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LATLONG.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoVehicleLocation() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LOCATION.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoDateTime() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_DATE_TIME.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoOdometer() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @Test
    fun testGetNonAutoFields_excludesAutoDriverName() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_DRIVER_NAME.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal)
        )
        val nonAutoFields = formFields.getNonAutoFields()
        assertEquals(1, nonAutoFields.size)
        assertEquals(FormFieldType.TEXT.ordinal, nonAutoFields[0].qtype)
    }

    @After
    fun after() {
        unmockkAll()
    }

}