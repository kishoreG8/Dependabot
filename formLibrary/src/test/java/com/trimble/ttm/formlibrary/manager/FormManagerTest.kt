package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.FormUtils
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.util.Stack


class FormManagerTest {
    private lateinit var formManager: FormManager
    @RelaxedMockK
    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var formTemplate: FormTemplate
    private lateinit var formFieldStack: Stack<FormField>

    private val modulesToInject = listOf(module{
        single { formDataStoreManager }
    })

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startKoin {
            modules(modulesToInject)
        }
        Dispatchers.setMain(Dispatchers.Unconfined)
        every { formTemplate.formFieldsList } returns arrayListOf(
            FormField(qnum = 1),
            FormField(qnum = 2),
            FormField(qnum = 3)
        )
        formFieldStack = Stack()
        formManager = spyk(FormManager())
    }

    @Test
    fun `checkAndResetBranchTarget resets branchTo when it is greater than -1`() {
        // Arrange
        formManager.branchTo = 2

        // Action
        formManager.checkAndResetBranchTarget()

        // Assert
        val result = formManager.branchTo
        assertEquals(-1, result)
    }

    @Test
    fun `checkAndResetBranchTarget does not reset branchTo when it is not greater than -1`() {
        // Arrange
        formManager.branchTo = -1

        // Action
        formManager.checkAndResetBranchTarget()

        // Assert
        val result = formManager.branchTo
        assertEquals(-1, result)
    }

    @Test
    fun `checkIfFieldIsTextField returns true for numeric field`() {
        val formField = FormField(qtype = FormFieldType.NUMERIC.ordinal)
        assertTrue(formManager.checkIfFieldIsTextField(formField))
    }

    @Test
    fun `checkIfFieldIsTextField returns true for numeric enhanced field`() {
        val formField = FormField(qtype = FormFieldType.NUMERIC_ENHANCED.ordinal)
        assertTrue(formManager.checkIfFieldIsTextField(formField))
    }

    @Test
    fun `checkIfFieldIsTextField returns true for text field`() {
        val formField = FormField(qtype = FormFieldType.TEXT.ordinal)
        assertTrue(formManager.checkIfFieldIsTextField(formField))
    }

    @Test
    fun `checkIfFieldIsTextField returns true for password field`() {
        val formField = FormField(qtype = FormFieldType.PASSWORD.ordinal)
        assertTrue(formManager.checkIfFieldIsTextField(formField))
    }

    @Test
    fun `checkIfFieldIsTextField returns false for non-text field`() {
        val formField = FormField(qtype = FormFieldType.DATE.ordinal)
        assertFalse(formManager.checkIfFieldIsTextField(formField))
    }

    @Test
    fun `checkIfTextFieldIsEditable returns true for editable field`() {
        val formField = FormField(driverEditable = 1)
        assertTrue(formManager.checkIfTextFieldIsEditable(formField))
    }

    @Test
    fun `checkIfTextFieldIsEditable returns false for non-editable field`() {
        val formField = FormField(driverEditable = 0)
        assertFalse(formManager.checkIfTextFieldIsEditable(formField))
    }

    @Test
    fun `isFreeForm returns true when formClass is 1 and has a valid formid`() {
        val formDef = FormDef(formClass = 1, formid = 123)
        assertTrue(formManager.isFreeForm(formDef))
    }

    @Test
    fun `isFreeForm returns false when formClass is 1 and formid has default value`() {
        val formDef = FormDef(formClass = 1, formid = -1)
        assertFalse(formManager.isFreeForm(formDef))
    }

    @Test
    fun `isFreeForm returns false when formClass has default value and has a valid formid`() {
        val formDef = FormDef(formClass = -1, formid = 123)
        assertFalse(formManager.isFreeForm(formDef))
    }

    @Test
    fun `isFreeForm returns false when formClass is not 1`() {
        val formDef = FormDef(formClass = 2)
        assertFalse(formManager.isFreeForm(formDef))
    }

    @Test
    fun `removeKeyForImage does not remove keys when they do not exist`() = runTest {
        coEvery { formDataStoreManager.containsKey(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID) } returns false
        coEvery { formDataStoreManager.containsKey(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM) } returns false

        formManager.removeKeyForImage()

        coVerify(exactly = 0) { formDataStoreManager.removeItem(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID) }
        coVerify(exactly = 0) { formDataStoreManager.removeItem(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM) }
    }

    @Test
    fun `removePreviouslyRenderedField returns filtered map when fields exist`() {
        val parentMap = hashMapOf(1 to FormField(), 2 to FormField(), 3 to FormField())
        val result = formManager.removePreviouslyRenderedField(2, parentMap)

        assertEquals(1, result.size)
        assertEquals(true, result.containsKey(1))
    }

    @Test
    fun `removePreviouslyRenderedField returns original map when no fields to remove`() {
        val parentMap = hashMapOf(1 to FormField(), 2 to FormField(), 3 to FormField())
        val result = formManager.removePreviouslyRenderedField(4, parentMap)

        assertEquals(3, result.size)
        assertEquals(true, result.containsKey(1))
        assertEquals(true, result.containsKey(2))
        assertEquals(true, result.containsKey(3))
    }

    @Test
    fun `checkIfIterationFinished returns true when both iterations are finished`() {
        val result = formManager.checkIfIterationFinished(true, true)
        assertEquals(true, result)
    }

    @Test
    fun `checkIfIterationFinished returns false when left iteration is not finished`() {
        val result = formManager.checkIfIterationFinished(false, true)
        assertEquals(false, result)
    }

    @Test
    fun `checkIfIterationFinished returns false when right iteration is not finished`() {
        val result = formManager.checkIfIterationFinished(true, false)
        assertEquals(false, result)
    }

    @Test
    fun `checkIfIterationFinished returns false when both iterations are not finished`() {
        val result = formManager.checkIfIterationFinished(false, false)
        assertEquals(false, result)
    }

    @Test
    fun `renderLoopFieldsWithoutBranch with empty stack`() = runTest {
        val formField = FormField(qnum = 1).also {
            it.viewId = 1
        }
        val renderForm: suspend (Int, Int, Int, Boolean, Int, Int) -> Unit = mockk(relaxed = true)

        formManager.renderLoopFieldsWithoutBranch(formField, formTemplate, formFieldStack, false, renderForm)
        val branchTarget = FormUtils.getNextQNum(formField.qnum + 1, formTemplate)

        coVerify {
            renderForm(
                branchTarget,
                formField.viewId,
                -1,
                false,
                -1,
                -1
            )
        }
    }

    @Test
    fun `renderLoopFieldsWithoutBranch with stack having loop count greater than 1`() = runTest {
        val formField = FormField(qnum = 1).also {
            it.viewId = 1
        }
        val stackFormField = FormField(qnum = 2, loopcount = 2).also {
            it.actualLoopCount = 2
        }
        formFieldStack.push(stackFormField)
        val renderForm: suspend (Int, Int, Int, Boolean, Int, Int) -> Unit = mockk(relaxed = true)

        formManager.renderLoopFieldsWithoutBranch(formField, formTemplate, formFieldStack, false, renderForm)

        coVerify {
            renderForm(
                eq(3),
                eq(1),
                eq(1),
                eq(false),
                eq(2),
                eq(2)
            )
        }
    }

    @Test
    fun `renderLoopFieldsWithoutBranch with stack having loop count equal to 1`() = runTest {
        val formField = FormField(qnum = 1).also {
            it.viewId = 1
        }
        val stackFormField = FormField(qnum = 2, loopcount = 1).also {
            it.actualLoopCount = 1
        }
        formFieldStack.push(stackFormField)
        val renderForm: suspend (Int, Int, Int, Boolean, Int, Int) -> Unit = mockk(relaxed = true)
        val branchTarget = FormUtils.getNextQNum(formField.qnum + 1, formTemplate)

        formManager.renderLoopFieldsWithoutBranch(formField, formTemplate, formFieldStack, false, renderForm)

        coVerify {
            renderForm(
                branchTarget,
                formField.viewId,
                -1,
                false,
                -1,
                -1
            )
        }
    }

    @Test
    fun `renderLoopFieldsWithoutBranch with stack having more than one form field`() = runTest {
        val formField = FormField(qnum = 1)
        formField.viewId = 1
        val stackFormField1 = FormField(qnum = 0, loopcount = 1).also {
            it.actualLoopCount = 1
        }
        val stackFormField2 = FormField(qnum = 1, loopcount = 2).also {
            it.actualLoopCount = 2
        }
        formFieldStack.push(stackFormField1)
        formFieldStack.push(stackFormField2)
        val renderForm: suspend (Int, Int, Int, Boolean, Int, Int) -> Unit = mockk(relaxed = true)

        formManager.renderLoopFieldsWithoutBranch(formField, formTemplate, formFieldStack, false, renderForm)

        coVerify {
            renderForm(
                eq(2),
                eq(1),
                eq(1),
                eq(false),
                eq(2),
                eq(2)
            )
        }
    }

    @After
    fun tearDown() {
        unloadKoinModules(modulesToInject)
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
        clearAllMocks()
    }

}