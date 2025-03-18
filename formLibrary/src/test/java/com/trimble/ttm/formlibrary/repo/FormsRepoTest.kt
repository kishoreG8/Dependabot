package com.trimble.ttm.formlibrary.repo

import android.util.Log
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.formlibrary.dataSource.CloudFunctionFormsDataSource
import com.trimble.ttm.formlibrary.dataSource.FORM_DEF_KEY
import com.trimble.ttm.formlibrary.dataSource.FORM_FIELDS_KEY
import com.trimble.ttm.formlibrary.dataSource.FirestoreFormsDataSource
import com.trimble.ttm.formlibrary.utils.isNotNull
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FormsRepoTest {

    private lateinit var formsRepoImpl: FormsRepoImpl
    private lateinit var firestoreFormDataSource : FirestoreFormsDataSource
    private lateinit var cloudFunctionFormDataSource : CloudFunctionFormsDataSource
    private val formTemplate= HashMap<String,Any>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        firestoreFormDataSource = mockk()
        cloudFunctionFormDataSource = spyk(CloudFunctionFormsDataSource())
        formsRepoImpl = FormsRepoImpl(firestoreFormDataSource, cloudFunctionFormDataSource)
    }

    @Test
    fun `check default form template`() {
        formTemplate[FORM_DEF_KEY]= FormDef()
        formTemplate[FORM_FIELDS_KEY]=ArrayList<FormField>()
        val defAndFields=cloudFunctionFormDataSource.parseAndGetFormDefAndFormFields(formTemplate)
        Assert.assertTrue(defAndFields.second.isEmpty())
        Assert.assertEquals(defAndFields.first, FormDef())
    }

    @Test
    fun `verify form template form def data`() {
        formTemplate[FORM_DEF_KEY]= FormDef(cid = 10119, name = "FormName")
        formTemplate[FORM_FIELDS_KEY]=ArrayList<FormField>()
        val defAndFields=cloudFunctionFormDataSource.parseAndGetFormDefAndFormFields(formTemplate)
        Assert.assertEquals(defAndFields.first.name,"FormName")
        Assert.assertNotEquals(defAndFields.first.cid,"")
    }

    @Test
    fun `verify form template form field data`() {
        val formField1= FormField(formid = 100, displayText = "FormField1")
        val formField2= FormField(formid = 200, displayText = "FormField2")
        formTemplate[FORM_FIELDS_KEY]= listOf(formField1,formField2)
        val defAndFields=cloudFunctionFormDataSource.parseAndGetFormDefAndFormFields(formTemplate)
        Assert.assertEquals(defAndFields.second[0].formid,100)
        Assert.assertEquals(defAndFields.second[1].displayText,"FormField2")
        Assert.assertTrue(defAndFields.second[1].formChoices.isNotNull())
    }

    @Test
    fun `verify form template form choice data`() {
        val formChoice1= FormChoice(
            qnum = 1,
            choicenum = 1,
            value = "Yes",
            formid = 100
        )
        val formChoice2= FormChoice(
            qnum = 2,
            choicenum = 2,
            value = "No",
            formid = 100
        )
        val formField= FormField(formid = 100, displayText = "FormField1")
        formField.formChoiceList= arrayListOf(formChoice1,formChoice2)
        formTemplate[FORM_FIELDS_KEY]= listOf(formField)
        val defAndFields=cloudFunctionFormDataSource.parseAndGetFormDefAndFormFields(formTemplate)
        Assert.assertTrue(defAndFields.second[0].formChoices.isNotNull())
        Assert.assertTrue(defAndFields.second[0].formChoiceList!!.isNotEmpty())
        Assert.assertEquals(defAndFields.second[0].formChoiceList!![0].qnum,1)
        Assert.assertEquals(defAndFields.second[0].formChoiceList!![1].value,"No")
    }

    @Test
    fun `verify formField actualLoopCount is populated if mandatory inspection is false`() = runTest {
        val formField = FormField(
            fieldId = 1,
            qnum = 1,
            qtext = "Title",
            loopcount = 1
        )
        val formFieldList = ArrayList<FormField>().apply {
            add(formField)
        }
        val formTemplate = FormTemplate(
            FormDef(cid = 10119, formid = 1), formFieldList
        )
        coEvery { firestoreFormDataSource.getForm(any(), any()) } returns formTemplate

        val processedFormTemplate = formsRepoImpl.getForm("10119", 1, false)
        assertEquals(processedFormTemplate.formFieldsList[0].actualLoopCount, formTemplate.formFieldsList[0].loopcount)
    }

    @Test
    fun `verify formField actualLoopCount is populated if mandatory inspection is true`() = runTest {
        val formField = FormField(
            fieldId = 1,
            qnum = 1,
            qtext = "Title",
            loopcount = 2
        )

        val formFieldList = ArrayList<FormField>().apply {
            add(formField)
        }
        val formTemplate = FormTemplate(
            FormDef(cid = 10119, formid = 1), formFieldList
        )

        coEvery { cloudFunctionFormDataSource.getForm(any(), any()) } returns formTemplate

        val processedFormTemplate = formsRepoImpl.getForm("10119", 1, true)
        assertEquals(processedFormTemplate.formFieldsList[0].actualLoopCount, 2)
    }
}