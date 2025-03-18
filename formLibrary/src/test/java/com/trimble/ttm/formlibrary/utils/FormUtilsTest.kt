package com.trimble.ttm.formlibrary.utils

import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.utils.FormUtils.getRecipient
import com.trimble.ttm.formlibrary.utils.FormUtils.checkNumericFieldIsNotValid
import com.trimble.ttm.formlibrary.utils.FormUtils.checkValidityOfTheField
import com.trimble.ttm.formlibrary.utils.FormUtils.doesValueNotLieBetweenMinMaxRange
import com.trimble.ttm.formlibrary.utils.FormUtils.getNextFormField
import com.trimble.ttm.formlibrary.utils.FormUtils.isNumericFieldNotValidWithinMaxMinRange
import com.trimble.ttm.formlibrary.utils.FormUtils.validateNumericField
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class FormUtilsTest {

    private val userMail123trimble = "123@trimble.com"

    @Test
    fun `verify if recipient of type int is a pfm user`() =    //NOSONAR
        assertEquals(123.toLong(), 123.getRecipient().recipientPfmUser)

    @Test
    fun `verify if recipient of type double is a pfm user`() =    //NOSONAR
            assertEquals(123.toLong(), 123.0.getRecipient().recipientPfmUser)

    @Test
    fun `verify if recipient of type long is a pfm user`() =    //NOSONAR
            assertEquals(123.toLong(), 123L.getRecipient().recipientPfmUser)

    @Test
    fun `verify if recipient of type string is a pfm email user`() =    //NOSONAR
            assertEquals(userMail123trimble, userMail123trimble.getRecipient().recipientEmailUser)
    @Test
    fun `verify if recipient of type string is a pfm email user negative case`() =    //NOSONAR
            assertNotEquals(userMail123trimble, "12@trimble.com".getRecipient().recipientEmailUser)

    @Test
    fun `verify if recipient of type null is a pfm user`() =    //NOSONAR
            assertEquals(null, null?.getRecipient()?.recipientPfmUser)

    @Test
    fun `verify if recipient of type null is a pfm email user`() =    //NOSONAR
            assertEquals(null, null?.getRecipient()?.recipientEmailUser)

    @Test
    fun `verify if numeric field is valid with invalid input` (){
        val formField = FormField(
            qnum=2, qtext="Currency Field 2", qtype=0, formid=18985,
            description="field", fieldId=68379, required=0, dispatchEditable=0, driverEditable=1
        )
        formField.uiData ="-"
        assertTrue(formField.validateNumericField())
    }

    @Test
    fun `verify if numeric field is valid with valid input` (){
        val formField = FormField(
            qnum=2, qtext="Currency Field 2", qtype=0, formid=18985,
            description="field", fieldId=68379, required=0, dispatchEditable=0, driverEditable=1
        )
        formField.uiData ="1.5"
        assertFalse(formField.validateNumericField())
    }

    @Test
    fun `verify int value of odometer`(){
        var odometerValueInKm=2420.78
        assertEquals(15042,
            com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKm))

        odometerValueInKm=243333333.45
        assertEquals(1512002767,
            com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKm))

        odometerValueInKm=243333333.4555665565
        assertEquals(1512002767,
            com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKm))

        odometerValueInKm=249225.12827800002
        assertEquals(1548612,
            com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKm))

        odometerValueInKm=214748364.12827800002
        assertEquals(1334384057,
            com.trimble.ttm.commons.utils.FormUtils.convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKm))
    }

    @Test
    fun `verify if numeric field text is within max and min range`(){
        val formField = FormField(numspMax = BigDecimal(100.5) , numspMin = BigDecimal(-10.5) , numspPre = "" )
        assertTrue(doesValueNotLieBetweenMinMaxRange(formField,"10.4"))
    }

    @Test
    fun `verify if numeric field text is not within max and min range`(){
        val formField = FormField(numspMax = BigDecimal(100.5) , numspMin = BigDecimal(-10.5) , numspPre = "" )
        assertFalse(doesValueNotLieBetweenMinMaxRange(formField,"-105.4"))
    }

    @Test
    fun `verify if numeric field max and min when numpsmax and numpsmin are null`(){
        val formField = FormField(numspMax = null , numspMin = null )
        assertTrue(doesValueNotLieBetweenMinMaxRange(formField,"-105.4"))
    }

    @Test(expected = NumberFormatException::class)
    fun `verify if numeric field throws exception when the numpsmax is not decimal`() {
        val formField = FormField(numspMax = BigDecimal("123,0"), numspMin = null)
        assertFailsWith<NumberFormatException> {   doesValueNotLieBetweenMinMaxRange(formField, "-105.4")}
    }

    @Test
    fun `verify numeric field is valid failure case`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertFalse(checkValidityOfTheField("110.5",formField))
    }

    @Test
    fun `verify numeric field is valid success case`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertTrue(checkValidityOfTheField("0.1",formField))
    }

    @Test
    fun `verify numeric field is valid and not within max min range`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertTrue(isNumericFieldNotValidWithinMaxMinRange("100",formField))
    }
    @Test
    fun `verify numeric field is empty and not within max min range`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertFalse(isNumericFieldNotValidWithinMaxMinRange("",formField))
    }

    @Test
    fun `verify numeric field is not valid and not within max min range`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertFalse(isNumericFieldNotValidWithinMaxMinRange("0.",formField))
    }

    @Test
    fun `verify numeric field is valid and within max min range`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertFalse(isNumericFieldNotValidWithinMaxMinRange("0.5",formField))
    }

    @Test
    fun `verify numeric field is not valid`(){
        val formField = FormField(numspMin = BigDecimal(-10.5), numspMax = BigDecimal(10.5), numspPre = "")
        assertTrue(checkNumericFieldIsNotValid("0.",formField))
    }

    @Test
    fun `get a true value when an empty FormTemplate`(){
        val formTemplate = FormTemplate()
        assertTrue(FormUtils.areAllFieldsEmpty(formTemplate))
    }

    @Test
    fun `get a true value when all the fields are empty`(){
        val formFieldArray = arrayListOf(
            FormField()
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        assertTrue(FormUtils.areAllFieldsEmpty(formTemplate))
    }

    @Test
    fun `get a false value with one not empty field`(){
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        assertTrue(!FormUtils.areAllFieldsEmpty(formTemplate))
    }

    @Test
    fun `get a false value when one field is not empty`(){
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ),
            FormField(
                required = 0,
                qtext = "number"
            ),
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        assertTrue(!FormUtils.areAllFieldsEmpty(formTemplate))
    }

    @Test
    fun `get a false value when more than one field that is not empty`(){
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" },
            FormField(
                required = 0,
                qtext = "number"
            ),
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        assertTrue(!FormUtils.areAllFieldsEmpty(formTemplate))
    }

    @Test
    fun `view inflation is required if branch to and field number is same as next question number to inflate`(){
        val formField= FormField(qnum = 11)
        val branchTo=10
        val formFieldArray = arrayListOf(
            FormField(
                qnum = 10,
                qtext = "text"
            ),
            FormField(
                qnum = 11,
                required = 0,
                qtext = "number"
            ),
            FormField(
                qnum = 12,
                qtext = "number"
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )

        val toTest=FormUtils.isViewInflationRequired(branchTo,formField,formTemplate)

        assertTrue(toTest.second)

        assertEquals(branchTo+1,toTest.first)

    }

    @Test
    fun `view inflation is not required if branch to and field number is not same as next question number to inflate`(){
        val formField= FormField(qnum = 11)
        val branchTo=8
        val formFieldArray = arrayListOf(
            FormField(
                qnum = 10,
                qtext = "text"
            ),
            FormField(
                qnum = 11,
                required = 0,
                qtext = "number"
            ),
            FormField(
                qnum = 12,
                qtext = "number"
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )

        val toTest=FormUtils.isViewInflationRequired(branchTo,formField,formTemplate)

        assertFalse(toTest.second)

        assertNotSame(branchTo+1,toTest.first)

    }

    @Test
    fun `don't  check view inflation check if branch to is -1 `(){
        val formField= FormField(qnum = 11)
        val branchTo=-1
        val formFieldArray = arrayListOf(
            FormField(
                qnum = 10,
                qtext = "text"
            ),
            FormField(
                qnum = 11,
                required = 0,
                qtext = "number"
            ),
            FormField(
                qnum = 12,
                qtext = "number"
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )

        val toTest=FormUtils.isViewInflationRequired(branchTo,formField,formTemplate)

        assertTrue(toTest.second)

        assertNotSame(branchTo+1,toTest.first)

    }

    @Test
    fun `get Next FormField when selectedFormChoice doesn't have a branchTarget`(){
        val formField = FormField(qnum = 12, qtext = "number")
        val formFieldList = arrayListOf(
            FormField(
                qnum = 10,
                qtext = "text"
            ),
            FormField(
                qnum = 11,
                required = 0,
                qtext = "number"
            ),
            FormField(
                qnum = 12,
                qtext = "number"
            )
        )
        val formTemplate = FormTemplate(formFieldsList = formFieldList)
        assertEquals(formField, getNextFormField(qNum = 11, formTemplate = formTemplate))
    }

    @Test
    fun `verify getNextQNum with valid qnum`() {
        val formFieldList = arrayListOf<FormField>().also {
            it.add(FormField(qnum = 0))
            it.add(FormField(qnum = 1))
            it.add(FormField(qnum = 2))
        }
        val formTemplate = FormTemplate(formFieldsList = formFieldList)
        assertEquals(1, FormUtils.getNextQNum(1, formTemplate))
    }

    @Test
    fun `verify getNextQNum with invalid qnum`() {
        val formFieldList = arrayListOf<FormField>().also {
            it.add(FormField(qnum = 0))
            it.add(FormField(qnum = 1))
            it.add(FormField(qnum = 2))
        }
        val formTemplate = FormTemplate(formFieldsList = formFieldList)
        assertEquals(-1, FormUtils.getNextQNum(3, formTemplate))
    }
}