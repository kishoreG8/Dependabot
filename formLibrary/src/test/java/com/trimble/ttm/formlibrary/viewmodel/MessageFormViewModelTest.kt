package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.model.DTFConditions
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.DispatchFormUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.getNotificationManager
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.MessageFormData
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.FormRenderUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.MARK_READ
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Stack
import kotlin.collections.set
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageFormViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @RelaxedMockK
    private lateinit var dispatchFormUseCase : DispatchFormUseCase
    @RelaxedMockK
    private lateinit var messageFormRepo : MessageFormRepo
    @RelaxedMockK
    private lateinit var deepLinkUseCase : DeepLinkUseCase
    @RelaxedMockK
    private lateinit var encodedImageRefUseCase : EncodedImageRefUseCase
    @RelaxedMockK
    private lateinit var draftUseCase: DraftUseCase
    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    private lateinit var messageFormViewModel: MessageFormViewModel
    private lateinit var formRenderUseCase: FormRenderUseCase
    @MockK
    private lateinit var appModuleCommunicator : AppModuleCommunicator
    private lateinit var messageFormUseCase: MessageFormUseCase
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase

    private val multipleChoice = "Multiple choice"

    private var formTemplate: FormTemplate =
        FormTemplate()
    private val testScope = TestScope()
    @RelaxedMockK
    private lateinit var notificationManager: NotificationManager
    @RelaxedMockK
    private lateinit var firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        firebaseAnalyticEventRecorder = mockk()
        messageFormUseCase = spyk(MessageFormUseCase(encodedImageRefUseCase, dispatchFormUseCase, messageFormRepo,mockk(), dispatcherFormValuesUseCase = mockk()))

        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetObcId() } returns "12345"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123442"
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        every { messageFormUseCase.getAppModuleCommunicator() } returns appModuleCommunicator

        formFieldDataUseCase = spyk(FormFieldDataUseCase(encodedImageRefUseCase = encodedImageRefUseCase, appModuleCommunicator = appModuleCommunicator, backboneUseCase = backboneUseCase))
        formRenderUseCase = FormRenderUseCase()
        messageFormViewModel = MessageFormViewModel(
            application = application,
            messageFormUseCase = messageFormUseCase,
            draftUseCase = draftUseCase,
            firebaseCurrentUserTokenFetchUseCase = firebaseCurrentUserTokenFetchUseCase,
            formRenderUseCase = formRenderUseCase,
            coroutineDispatcherProvider = TestDispatcherProvider(),
            deepLinkUseCase = deepLinkUseCase,
            formFieldDataUseCase = formFieldDataUseCase,
            formDataStoreManager = formDataStoreManager
        )
    }

    @Test
    fun `verify get form`() = runTest {    //NOSONAR
        coEvery { formFieldDataUseCase.createFormFromResult(any(),any()) } returns Form(
            FormTemplate(),
            UIFormResponse(),
            HashMap()
        )
        coEvery { messageFormRepo.getForm(any(), any()) } returns FormTemplate()
        coEvery { messageFormUseCase.getForm(any(), any(),any(),any()) } returns Form(
            FormTemplate(),
            UIFormResponse(),
            HashMap()
        )
        val form = messageFormViewModel.getForm("11233", false, UIFormResponse())
        assertEquals(-1, form.formTemplate.formDef.formid)
    }

    @Test
    @Ignore
    fun `verify get freeform`() = runTest {    //NOSONAR
        every { context.applicationContext } returns context
        coVerify(exactly = 0) { messageFormViewModel.getFreeForm(UIFormResponse()) }
    }

    @Test
    fun `verify getFreeFormEditTextHintAndMessage`() { //NOSONAR
        every { application.getString(any()) } returns "Message"
        val expectedPair = Pair("Message","abc")
        assertEquals(expectedPair, messageFormViewModel.getFreeFormEditTextHintAndMessage(FormField(), "abc"))
    }

    @Test
    fun `verify data consistency for orientation change`() {    //NOSONAR
        val savedFieldData = ArrayList<Any>()
        savedFieldData.add(
            LinkedHashMap<String, String>().let {
                it["edittext"] = "hello"
                it
            })
        val uiResponse = UIFormResponse(true, FormResponse(fieldData = savedFieldData))
        messageFormViewModel.saveEnteredUIDataDuringOrientationChange(uiResponse)
        assertEquals(
            uiResponse,
            messageFormViewModel.getSavedUIDataDuringOrientationChange()
        )
    }

    @Test
    fun `verify isEmptyForm with empty form and empty recipient list returns true`() {    //NOSONAR
        messageFormViewModel.selectedRecipients = mapOf()
        messageFormViewModel.savePreviousSelectedRecipients()
        val result = messageFormViewModel.isAnEmptyForm(FormTemplate())
        assert(result)
    }

    @Test
    fun `verify isEmptyForm with a non empty form and empty recipient list returns false`() {
        messageFormViewModel.savePreviousSelectedRecipients()
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.isAnEmptyForm(
            formTemplate,
            true
        )
        assert(!result)
    }

    @Test
    fun `verify isEmptyForm with a empty form and non empty recipient list returns false`() {
        messageFormViewModel.savePreviousSelectedRecipients()
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        val formTemplate = FormTemplate()
        val result = messageFormViewModel.isAnEmptyForm(
            formTemplate,
            true
        )
        assert(!result)
    }

    @Test
    fun `verify isEmptyForm with a non empty form and non empty recipient list returns false`() {
        messageFormViewModel.savePreviousSelectedRecipients()
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.isAnEmptyForm(
            formTemplate,
            true
        )
        assert(!result)
    }

    @Test
    fun `hasSomethingToDraft return true when the form content is different and the recipients the same`() {
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        messageFormViewModel.savePreviousSelectedRecipients()
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        messageFormViewModel.saveFormTemplateCopy(
            FormTemplate(
                formFieldsList = formFieldArray
            )
        )
        formFieldArray.add(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.hasSomethingToDraft(
            formTemplate,
            true
        )
        assert(result)
    }

    @Test
    fun `hasSomethingToDraft return true when the form content is the same and the recipients are different`() {
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        messageFormViewModel.savePreviousSelectedRecipients()
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100,
            "101" to "100"
        )
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        messageFormViewModel.saveFormTemplateCopy(
            FormTemplate(
                formFieldsList = formFieldArray
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.hasSomethingToDraft(
            formTemplate,
            true
        )
        assert(result)
    }

    @Test
    fun `hasSomethingToDraft return false when the form content is the same and the recipients are the same`() {
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        messageFormViewModel.savePreviousSelectedRecipients()
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        messageFormViewModel.saveFormTemplateCopy(
            FormTemplate(
                formFieldsList = formFieldArray
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.hasSomethingToDraft(
            formTemplate,
            true
        )
        assert(!result)
    }

    @Test
    fun `hasSomethingToDraft return true when the form content is different and don't check the recipients`() {
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        messageFormViewModel.savePreviousSelectedRecipients()
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        messageFormViewModel.saveFormTemplateCopy(
            FormTemplate(
                formFieldsList = formFieldArray
            )
        )
        formFieldArray.add(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.hasSomethingToDraft(
            formTemplate,
            false
        )
        assert(result)
    }

    @Test
    fun `hasSomethingToDraft return false when the form content is the same and don't check the recipients`() {
        messageFormViewModel.selectedRecipients = mapOf(
            "100" to 100
        )
        messageFormViewModel.savePreviousSelectedRecipients()
        val formFieldArray = arrayListOf(
            FormField(
                required = 0,
                qtext = "number"
            ).apply { uiData = "2344" }
        )
        messageFormViewModel.saveFormTemplateCopy(
            FormTemplate(
                formFieldsList = formFieldArray
            )
        )
        val formTemplate = FormTemplate(
            formFieldsList = formFieldArray
        )
        val result = messageFormViewModel.hasSomethingToDraft(
            formTemplate,
            false
        )
        assert(!result)
    }

    @Test
    fun `verify defaultValues is not empty when fieldList have preFilled values and formResponse is reply with same`() {
        val fieldList = arrayListOf(MessageFormField(text = "test"))
        val defaultValues = messageFormViewModel.processDispatcherFormValues(fieldList = fieldList)
        assert(defaultValues.isNotEmpty())
    }

    @Test
    fun `verify defaultValues is empty when fieldList doesn't have preFilled values and formResponse is reply with same`() {
        val fieldList = arrayListOf(MessageFormField())
        val defaultValues = messageFormViewModel.processDispatcherFormValues(fieldList = fieldList)
        assert(defaultValues.isEmpty())
    }

    @Test
    fun `verify defaultValues is empty when fieldList have preFilled values and formResponse is reply with new`() {
        val fieldList = arrayListOf(MessageFormField(text = "test"))
        val defaultValues = messageFormViewModel.processDispatcherFormValues(1, fieldList)
        assert(defaultValues.isEmpty())
    }


    @Test
    fun `verify values when branchTargetId is different than null`() = runTest {
        val formChoice = getFormChoice(branchTargetId = 3, viewId = 1)
        val result = mutableSetOf<DTFConditions>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(result)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            FormTemplate()
        )
        val actualResult = result.elementAt(1)
        val expectedResult = DTFConditions(
            branchTargetId = 3,
            selectedViewId = 1,
            loopEndId = -1,
            actualLoopCount = -1,
            currentLoopCount = -1
        )
        assert(
            expectedResult.branchTargetId == actualResult.branchTargetId
        )
        assert(
            expectedResult.selectedViewId == actualResult.selectedViewId
        )
        assert(
            expectedResult.loopEndId == actualResult.loopEndId
        )
        assert(
            expectedResult.actualLoopCount == actualResult.actualLoopCount
        )
        assert(
            expectedResult.currentLoopCount == actualResult.currentLoopCount
        )
        collectJob.cancel()
    }

    @Test
    fun `verify values when branchTargetId is null and loop count is grater than 1 and stack is not empty`() =
        runTest {
            val expectedResult = DTFConditions(
                branchTargetId = 2,
                selectedViewId = 3,
                loopEndId = 4,
                actualLoopCount = 3,
                currentLoopCount = 3
            )
            val viewId = 3
            val formTemplate = getFormTemplate()
            val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
            formField.viewId = 5
            formTemplate.formFieldsList.add(formField)
            val formFieldStack =
                getStackFieldWithLoopValue(loopValue = 3, viewId = 3, actualLoopCount = 3)
            val formChoice = getFormChoice(viewId = 3, qNum = 4)
            messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
            val results = mutableSetOf<DTFConditions>()
            val collectJob = launch(UnconfinedTestDispatcher()) {
                messageFormViewModel.renderValues.toSet(results)
            }
            Dispatchers.setMain(UnconfinedTestDispatcher())
            messageFormViewModel.processMultipleChoice(
                formChoice,
                formTemplate
            )
            delay(5000)
            val result = results.elementAt(1)
            assert(
                expectedResult.branchTargetId == result.branchTargetId
            )
            assert(
                expectedResult.selectedViewId == result.selectedViewId
            )
            assert(
                expectedResult.loopEndId == result.loopEndId
            )
            assert(
                expectedResult.actualLoopCount == result.actualLoopCount
            )
            assert(
                expectedResult.currentLoopCount == expectedResult.currentLoopCount
            )
            collectJob.cancel()
        }

    @Test
    fun `verify values when branchTargetId is null and loop count is equals to 1 and stack is not empty`() =
        runTest {
            val expectedResult = DTFConditions(
                branchTargetId = 5,
                selectedViewId = 3,
                loopEndId = -1,
                actualLoopCount = -1,
                currentLoopCount = -1
            )
            val viewId = 3
            val formTemplate = getFormTemplate()
            val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
            formField.viewId = 5
            formTemplate.formFieldsList.add(formField)
            val formFieldStack =
                getStackFieldWithLoopValue(loopValue = 1, viewId = 3, actualLoopCount = 1)
            val formChoice = getFormChoice(viewId = 3, qNum = 4)
            messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
            val results = mutableSetOf<DTFConditions>()
            val collectJob = launch(UnconfinedTestDispatcher()) {
                messageFormViewModel.renderValues.toSet(results)
            }
            Dispatchers.setMain(UnconfinedTestDispatcher())
            messageFormViewModel.processMultipleChoice(
                formChoice,
                formTemplate
            )
            delay(5000)
            val result = results.elementAt(1)
            assert(
                expectedResult.branchTargetId == result.branchTargetId
            )
            assert(
                expectedResult.selectedViewId == result.selectedViewId
            )
            assert(
                expectedResult.loopEndId == result.loopEndId
            )
            assert(
                expectedResult.actualLoopCount == result.actualLoopCount
            )
            assert(
                expectedResult.currentLoopCount == result.currentLoopCount
            )
            collectJob.cancel()
        }

    @Test
    fun `verify values when branchTargetId is null but stack is empty`() = runTest {
        val expectedResult = DTFConditions(
            branchTargetId = 2,
            selectedViewId = 1,
            loopEndId = -1,
            actualLoopCount = -1,
            currentLoopCount = -1
        )
        val viewId = 1
        val formTemplate = getFormTemplate()
        val formFieldStack = Stack<FormField>()
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val formChoice = getFormChoice(viewId = 1)
        val results = mutableSetOf<DTFConditions>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(results)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            formTemplate
        )
        delay(5000)
        val result = results.elementAt(1)
        assert(
            expectedResult.branchTargetId == result.branchTargetId
        )
        assert(
            expectedResult.selectedViewId == result.selectedViewId
        )
        assert(
            expectedResult.loopEndId == result.loopEndId
        )
        assert(
            expectedResult.actualLoopCount == result.actualLoopCount
        )
        assert(
            expectedResult.currentLoopCount == result.currentLoopCount
        )
        collectJob.cancel()
    }

    @Test
    fun `verify next field to be rendered when branch target is not null, stack last element iteration count is over and the form field stack size is greater than 1`() =  runTest {
        val result = mutableSetOf<DTFConditions>()
        val viewId = 3
        val formTemplate = getFormTemplate()
        val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
        formField.viewId = 5
        formTemplate.formFieldsList.add(formField)
        val formFieldStack =
            getStackFieldWithLoopValue(loopValue = 3, viewId = 3, actualLoopCount = 4).apply {
                push(FormField(qnum = 6, qtype = FormFieldType.LOOP_START.ordinal).apply {
                    actualLoopCount = 4
                    loopcount = 4
                })
            }
        val formChoice = getFormChoice(viewId = viewId, qNum = 2, branchTargetId = 3)
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(result)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            formTemplate
        )
        val actualResult = result.elementAt(1)
        val expectedResult = DTFConditions(
            branchTargetId = 3,
            selectedViewId = 3,
            loopEndId = 2,
            actualLoopCount = 4,
            currentLoopCount = 3
        )
        assertEquals(expectedResult.branchTargetId, actualResult.branchTargetId)
        assertEquals(expectedResult.selectedViewId, actualResult.selectedViewId)
        assertEquals(expectedResult.loopEndId, actualResult.loopEndId)
        assertEquals(expectedResult.actualLoopCount, actualResult.actualLoopCount)
        assertEquals(expectedResult.currentLoopCount, actualResult.currentLoopCount)
        collectJob.cancel()
    }

    @Test
    fun `verify next field to be rendered when branch target is not null and the form field stack size is 1`() = runTest {
        val result = mutableSetOf<DTFConditions>()
        val viewId = 3
        val formTemplate = getFormTemplate()
        val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
        formField.viewId = 5
        formTemplate.formFieldsList.add(formField)
        val formFieldStack = getStackFieldWithLoopValue(loopValue = 3, viewId = 3, actualLoopCount = 4)
        val formChoice = getFormChoice(viewId = viewId, qNum = 2, branchTargetId = 3)
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(result)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            formTemplate
        )
        val actualResult = result.elementAt(1)
        val expectedResult = DTFConditions(
            branchTargetId = 3,
            selectedViewId = 3,
            loopEndId = 2,
            actualLoopCount = 4,
            currentLoopCount = 3
        )
        assertEquals(expectedResult.branchTargetId, actualResult.branchTargetId)
        assertEquals(expectedResult.selectedViewId, actualResult.selectedViewId)
        assertEquals(expectedResult.loopEndId, actualResult.loopEndId)
        assertEquals(expectedResult.actualLoopCount, actualResult.actualLoopCount)
        assertEquals(expectedResult.currentLoopCount, actualResult.currentLoopCount)
        collectJob.cancel()
    }

    @Test
    fun `verify next field to be rendered when branch target is null , stack last element iteration count is over and stack size is greater than 1 `() = runTest {
        val result = mutableSetOf<DTFConditions>()
        val viewId = 4
        val formTemplate = getFormTemplate()
        val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
        formField.viewId = 5
        formTemplate.formFieldsList.add(formField)
        val formFieldStack =
            getStackFieldWithLoopValue(loopValue = 3, viewId = 3, actualLoopCount = 4).apply {
                push(FormField(qnum = 6, qtype = FormFieldType.LOOP_START.ordinal).apply {
                    actualLoopCount = 4
                    loopcount = 1
                })
            }
        val formChoice = getFormChoice(viewId = viewId, qNum = 4)
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(result)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            formTemplate
        )
        val actualResult = result.elementAt(1)
        val expectedResult = DTFConditions(
            branchTargetId = 5,
            selectedViewId = 4,
            loopEndId = 4,
            actualLoopCount = 4,
            currentLoopCount = 3
        )
        assertEquals(expectedResult.branchTargetId, actualResult.branchTargetId)
        assertEquals(expectedResult.selectedViewId, actualResult.selectedViewId)
        assertEquals(expectedResult.loopEndId, actualResult.loopEndId)
        assertEquals(expectedResult.actualLoopCount, actualResult.actualLoopCount)
        assertEquals(expectedResult.currentLoopCount, actualResult.currentLoopCount)
        collectJob.cancel()
    }

    @Test
    fun `verify next field to be rendered when branch target is null , stack last element iteration count is over and stack size is 1`() = runTest {
        val result = mutableSetOf<DTFConditions>()
        val viewId = 4
        val formTemplate = getFormTemplate()
        val formField = FormField(qnum = 5, qtype = FormFieldType.LOOP_END.ordinal)
        formField.viewId = 5
        formTemplate.formFieldsList.add(formField)
        val formFieldStack =
            getStackFieldWithLoopValue(loopValue = 1, viewId = 3, actualLoopCount = 4)
        val formChoice = getFormChoice(viewId = viewId, qNum = 4)
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val collectJob = launch(UnconfinedTestDispatcher()) {
            messageFormViewModel.renderValues.toSet(result)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageFormViewModel.processMultipleChoice(
            formChoice,
            formTemplate
        )
        val actualResult = result.elementAt(1)
        val expectedResult = DTFConditions(
            branchTargetId = 5,
            selectedViewId = 4,
            loopEndId = -1,
            actualLoopCount = -1,
            currentLoopCount = -1
        )
        assertEquals(expectedResult.branchTargetId, actualResult.branchTargetId)
        assertEquals(expectedResult.selectedViewId, actualResult.selectedViewId)
        assertEquals(expectedResult.loopEndId, actualResult.loopEndId)
        assertEquals(expectedResult.actualLoopCount, actualResult.actualLoopCount)
        assertEquals(expectedResult.currentLoopCount, actualResult.currentLoopCount)
        collectJob.cancel()
    }


    @Test
    fun `return true when the content is the same and the Qtype are equals but different than DATE enum`() {
        val oldValue = "old value"
        val newValue = "new value"
        val oldQtype = 1
        val newQtype = 1
        assert(
            messageFormViewModel.hasSomeDifferencesInContent(
                oldValue,
                newValue,
                oldQtype,
                newQtype
            )
        )
    }

    @Test
    fun `return false when the content is the same and the Qtype are equals but different than DATE enum`() {
        val oldValue = "old value"
        val newValue = "old value"
        val oldQtype = 1
        val newQtype = 1
        assert(
            !messageFormViewModel.hasSomeDifferencesInContent(
                oldValue,
                newValue,
                oldQtype,
                newQtype
            )
        )
    }

    @Test
    fun `check getFormTemplateForDTFForms returns the expected FormTemplate`(){
        val viewIdMap = HashMap<Int,FormField>()
        viewIdMap[1] = FormField(qtype = FormFieldType.TEXT.ordinal)
        viewIdMap[2] = FormField(qtype = FormFieldType.NUMERIC_ENHANCED.ordinal)
        val autoFieldList = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_DATE_TIME.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_FUEL.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LOCATION.ordinal),
            FormField(qtype = FormFieldType.AUTO_DRIVER_NAME.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LATLONG.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal)
        )
        val actual = messageFormViewModel.getFormTemplateForDTFForms(
            formTemplate = FormTemplate(
                formFieldsList = autoFieldList,
                formDef = FormDef()
            ), viewIdToFormFieldMap = viewIdMap
        )
        assertEquals(6, actual.formFieldsList.size)
    }

    @Test
    fun `verify when branchTargetId is not null`(){
        val formChoice = getFormChoice(branchTargetId = 2)
        formChoice.viewId = 1
        val expectedTriple = Triple(2, 1, -1)
        val actualTriple = messageFormViewModel.processComposeMultipleChoice(
            formChoice = formChoice,
            formTemplate = FormTemplate()
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and loop count is greater more than 1 and formFieldStack is not empty`(){
        val expectedTriple = Triple(2, 1, 1)
        val viewId = 1
        val formTemplate = getFormTemplate()
        val formFieldStack = getStackFieldWithLoopValue(3)
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = messageFormViewModel.processComposeMultipleChoice(
            formChoice = formChoice,
            formTemplate = formTemplate
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and loop count is 1 and formFieldStack is not empty`(){
        val expectedTriple = Triple(2, 1, -1)
        val viewId = 1
        val formTemplate = getFormTemplate()
        val formFieldStack = getStackFieldWithLoopValue()
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = messageFormViewModel.processComposeMultipleChoice(
            formChoice = formChoice,
            formTemplate = formTemplate
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == expectedTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and formFieldStack is empty`(){
        val expectedTriple = Triple(2,1,-1)
        val viewId = 1
        val formTemplate = getFormTemplate()
        val formFieldStack = Stack<FormField>()
        messageFormViewModel.getViewIdToFormFieldStackMap()[viewId] = formFieldStack
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = messageFormViewModel.processComposeMultipleChoice(
            formChoice = formChoice,
            formTemplate = formTemplate
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns true when formField is required,makeFieldsNonEditable is false and isFormSaved is false`() {
        assertTrue(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 1
                ), makeFieldsNonEditable = false, isFormSaved = false
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is not required,makeFieldsNonEditable is false and isFormSaved is false`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 0
                ), makeFieldsNonEditable = false, isFormSaved = false
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is required,makeFieldsNonEditable is true and isFormSaved is false`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 1
                ), makeFieldsNonEditable = true, isFormSaved = false
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is required,makeFieldsNonEditable is false and isFormSaved is true`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 1
                ), makeFieldsNonEditable = false, isFormSaved = true
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is not required,makeFieldsNonEditable is false and isFormSaved is true`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 0
                ), makeFieldsNonEditable = false, isFormSaved = true
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is not required,makeFieldsNonEditable is true and isFormSaved is false`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 0
                ), makeFieldsNonEditable = true, isFormSaved = false
            )
        )
    }

    @Test
    fun `check isFormRequiredAndReadOnlyView returns false when formField is not required,makeFieldsNonEditable is true and isFormSaved is true`() {
        assertFalse(
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = FormField(
                    required = 0
                ), makeFieldsNonEditable = true, isFormSaved = true
            )
        )
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns result as expected`() {
        assertTrue(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.TEXT.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns true for the formField type Numeric_Enhanced`() {
        assertTrue(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.NUMERIC_ENHANCED.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns true for the formField type Password`() {
        assertTrue(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.PASSWORD.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns true for the formField type Barcode`() {
        assertTrue(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.BARCODE_SCAN.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns true for the formField type Date_Time`() {
        assertTrue(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.DATE_TIME.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns false for the formField type Signature`() {
        assertFalse(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)))
    }

    @Test
    fun `check isOfTextInputLayoutViewType returns false for the formField type Image ref`() {
        assertFalse(messageFormViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal)))
    }

    @Test
    fun `check filterImageFormField returns only Image Form Fields`(){
        val formFieldList = arrayListOf(
            FormField(qtype = FormFieldType.PASSWORD.ordinal,qnum = 1),
            FormField(qtype = FormFieldType.DATE_TIME.ordinal,qnum = 2),
            FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal,qnum = 3),
            FormField(qtype = FormFieldType.TIME.ordinal,qnum = 4),
            FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal,qnum = 5),
        )
        assert(
            messageFormViewModel.filterImageFormField(formFieldList = formFieldList).size == 2
        )
    }


    private fun getFormTemplate() = FormTemplate(
        formFieldsList = arrayListOf(
            FormField(
                qnum = 1
            ),
            FormField(
                qnum = 2
            ),
            FormField(
                qnum = 3
            ),
            FormField(
                qnum = 4
            )
            )
        )

    private fun getStackFieldWithLoopValue(
        loopValue: Int = 1,
        viewId: Int = 1,
        qNum: Int = 1,
        actualLoopCount: Int = 1
    ): Stack<FormField> {
        val formFieldStack = Stack<FormField>()
        val formField = FormField(
            qnum = qNum,
            loopcount = loopValue
        )
        formField.viewId = viewId
        formField.actualLoopCount = actualLoopCount
        formFieldStack.push(
            formField
        )
        return formFieldStack
    }

    private fun getFormChoice(
        branchTargetId: Int? = null,
        viewId: Int = 0,
        qNum: Int = 1,
        choiceNum: Int = 1,
        formId: Int = 1
    ): FormChoice {
        val formChoice = FormChoice(
            qNum,
            choiceNum,
            "",
            formId,
            branchTargetId
        )
        formChoice.viewId = viewId
        return formChoice
    }


    @Test
    fun `do not process multipleChoice field default value if form is saved `() {
        val formField = FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal)

        val processMultipleChoice = mockk<(FormChoice?) -> Unit>(relaxed = true)

        //Act
        val isProcessingMultipleChoiceRequired = messageFormViewModel.isProcessingMultipleChoiceFieldRequired(
            formField,
            true,
            processMultipleChoice
        )

        //Assert
        verify(exactly = 0) {
            processMultipleChoice(any())
        }

        assertFalse(isProcessingMultipleChoiceRequired)
    }

    @Test
    fun `process multipleChoice field default value if form is not saved and default value is not empty and branch target is null`() {
        val formField = FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal, branchTargetId = null)
        formField.uiData = "defValue"

        val processMultipleChoice = mockk<(FormChoice?) -> Unit>(relaxed = true)
        val isProcessingMultipleChoiceRequired = messageFormViewModel.isProcessingMultipleChoiceFieldRequired(
            formField,
            false,
            processMultipleChoice
        )

        verify(exactly = 0) {
            processMultipleChoice(any())
        }

        assertFalse(isProcessingMultipleChoiceRequired)
    }

    @Test
    fun `do not process multipleChoice field default value if form default value is empty`() {
        val formField = FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal)
        formField.uiData = ""

        val processMultipleChoice = mockk<(FormChoice?) -> Unit>(relaxed = true)
        val isProcessingMultipleChoiceRequired = messageFormViewModel.isProcessingMultipleChoiceFieldRequired(
            formField,
            false,
            processMultipleChoice
        )

        verify(exactly = 0) {
            processMultipleChoice(any())
        }

        assertFalse(isProcessingMultipleChoiceRequired)
    }

    @Test
    fun `process multipleChoice field default value with expected value and branch target is not null`() {
        // Given
        val mockProcessMultipleChoice = mockk<(FormChoice?) -> Unit>(relaxed = true)
        val formField = FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal)
        val formChoice = FormChoice(
            qnum = 2,
            value = "dispatcherSent",
            choicenum = 2,
            formid = 1,
            branchTargetId = 3
        )
        formField.formChoiceList = arrayListOf(formChoice)
        formField.uiData = "dispatcherSent"
        formField.viewId = 2
        val isSyncToQueue = false

        //when
        val isProcessingMultipleChoiceRequired = messageFormViewModel.isProcessingMultipleChoiceFieldRequired(
            formField,
            isSyncToQueue,
            mockProcessMultipleChoice
        )

        //then
        val formChoiceCapturingSlot = slot<FormChoice>()
        verify {
            mockProcessMultipleChoice(capture(formChoiceCapturingSlot))
        }

        //assert the captured form choice value
        val capturedChoice = formChoiceCapturingSlot.captured
        assertNotNull(capturedChoice)
        assertEquals(2, capturedChoice.viewId)
        assertTrue(isProcessingMultipleChoiceRequired)
    }

    @Test
    fun `check processDispatcherDefaultValue when formClass is -1`(){
        val fieldList = arrayListOf(MessageFormField(qNum = "1", text = "Test1"),MessageFormField(qNum = "2", text = "Test2"),MessageFormField(qNum = "3", text = "Test3"),MessageFormField(qNum = "1", text = "Test12"),MessageFormField(qNum = "2", text = "Test22"))
        val actualResult = messageFormViewModel.processDispatcherFormValues(formClass = -1, fieldList = fieldList)
        assert(
            actualResult.size == 3 &&
                    actualResult["1"]?.size == 2
        )
    }

    @Test
    fun `check isDraftResponse emits true when the messaging form is saved`() {
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    multipleChoice,
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    12,
                    "",1L
                ).apply {
                    uiData = ""
                })
        )
        val formDataToSave = FormDataToSave(
            formTemplate = formTemplate,
            path = EMPTY_STRING,
            formId = EMPTY_STRING,
            typeOfResponse = EMPTY_STRING,
            formName = EMPTY_STRING,
            formClass = 0,
            cid = "123",
            obcId = EMPTY_STRING
        )
        val messageFormDataToSave = MessageFormData(path = EMPTY_STRING, formResponse = FormResponse(), formId = "123", typeOfResponse = EMPTY_STRING, formClass = 0, cid = 0, formName = EMPTY_STRING, hasPredefinedRecipients = false)
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.saveFormData(messageFormDataToSave) } returns true
        messageFormViewModel.saveFormData(formDataToSave)
        assertTrue(messageFormViewModel.isResponseDrafted.value == true)
    }

    @Test
    fun `when isDraftResponse emits true check check checkAndHandleDeepLinkConfigurationForFormSubmission is called`() {
        formTemplate = FormTemplate(
            FormDef(),
            arrayListOf(
                FormField(
                    1,
                    multipleChoice,
                    FormFieldType.MULTIPLE_CHOICE.ordinal,
                    12,
                    "",1L
                ).apply {
                    uiData = ""
                })
        )
        val formDataToSave = FormDataToSave(
            formTemplate = formTemplate,
            path = EMPTY_STRING,
            formId = EMPTY_STRING,
            typeOfResponse = EMPTY_STRING,
            formName = EMPTY_STRING,
            formClass = 0,
            cid = "123",
            obcId = EMPTY_STRING
        )
        val messageFormDataToSave = MessageFormData(path = EMPTY_STRING, formResponse = FormResponse(), formId = "123", typeOfResponse = EMPTY_STRING, formClass = 0, cid = 0, formName = EMPTY_STRING, hasPredefinedRecipients = false)
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.saveFormData(messageFormDataToSave) } returns true
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        messageFormViewModel.saveFormData(formDataToSave)
        verify(exactly = 1) {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any())
        }
    }

    @Test
    fun `check isDraftResponse emits true when saveDispatchFormResponse is saved`() {
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { messageFormUseCase.saveDispatchFormResponse(any(), any(), any(), any()) } returns true
        messageFormViewModel.saveDispatchFormResponse(
            path = "PFMFormResponses/5097/1234/4567/1/0",
            formTemplate = FormTemplate(),
            formDataToSave = constructFormDataToSave(),
            isSyncToQueue = true,
            caller = EMPTY_STRING
        )
        assertTrue(messageFormViewModel.isResponseDrafted.value == true)
    }

    @Test
    fun `check deepLink checkConfigurationForFormSubmission is called when saveDispatchFormResponse is saved`() {
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { messageFormUseCase.saveDispatchFormResponse(any(), any(), any(), any()) } returns true
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        messageFormViewModel.saveDispatchFormResponse(
            path = "PFMFormResponses/5097/1234/4567/1/0",
            formTemplate = FormTemplate(),
            formDataToSave = constructFormDataToSave(),
            isSyncToQueue = true,
            caller = EMPTY_STRING
        )
        verify(exactly = 1) {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any())
        }
    }

    @Test
    fun `check deepLink checkConfigurationForFormSubmission is called when isSyncQueue is false`() {
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { messageFormUseCase.saveDispatchFormResponse(any(), any(), any(), any()) } returns true
        messageFormViewModel.saveDispatchFormResponse(
            path = EMPTY_STRING,
            formTemplate = FormTemplate(),
            formDataToSave = constructFormDataToSave(),
            isSyncToQueue = false,
            caller = EMPTY_STRING
        )
        verify(exactly = 0) {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any())
        }
    }

    @Test
    fun `check deleteDraftMsgOfDispatchFormSavePath is called when isSyncQueue is true`() {
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { messageFormUseCase.saveDispatchFormResponse(any(), any(), any(), any()) } returns true
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        messageFormViewModel.saveDispatchFormResponse(
            path = "PFMFormResponses/5097/1234/4567/1/0",
            formTemplate = FormTemplate(),
            formDataToSave = constructFormDataToSave(),
            isSyncToQueue = true,
            caller = EMPTY_STRING
        )
        coVerify(exactly = 1) {
            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any())
        }
    }

    @Test
    fun `check deleteDraftMsgOfDispatchFormSavePath is called when isSyncQueue is false`() {
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        every { messageFormRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { messageFormUseCase.saveDispatchFormResponse(any(), any(), any(), any()) } returns true
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        messageFormViewModel.saveDispatchFormResponse(
            path = EMPTY_STRING,
            formTemplate = FormTemplate(),
            formDataToSave = constructFormDataToSave(),
            isSyncToQueue = false,
            caller = EMPTY_STRING
        )
        coVerify(exactly = 0) {
            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any())
        }
    }

    @Test
    fun `verify markMessageAsRead execution for message read`() {
        coEvery {
            messageFormUseCase.isMessageAlreadyRead(
                any(), any(), any(), any(), any()
            )
        } returns false
        every { getNotificationManager(application) } returns notificationManager

        messageFormViewModel.markMessageAsRead(caller = "testCaller", customerId = "2323", vehicleId = "vehicle", obcId = "2323433", asn = "1", operationType = MARK_READ, callSource = "test")

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            messageFormUseCase.markMessageAsRead(
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `verify markMessageAsRead for duplicate message read call, The message shouldn't be marked as read again`() {
        coEvery {
            messageFormUseCase.isMessageAlreadyRead(
                any(), any(), any(), any(), any()
            )
        } returns true

        messageFormViewModel.markMessageAsRead(caller = "testCaller", customerId = "2323", vehicleId = "vehicle", obcId = "2323433", asn = "1", operationType = MARK_READ, callSource = "test")

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageFormUseCase.markMessageAsRead(
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `verify message deletion execution`() {
        messageFormViewModel.markMessageAsDeleted("4545454", "test")
        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            messageFormUseCase.markMessageAsDeleted(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `test fetchLocallyCachedImageRefFields execution`() {
        val formFieldData = "base64string"
        val formFields = arrayListOf(
            FormField(
                1,
                "Image",
                FormFieldType.IMAGE_REFERENCE.ordinal,
                12,
                "",
                1L
            ).apply {
                uiData = formFieldData
            }
        )
        val formTemplate = FormTemplate(
            FormDef(),
            formFields
        )
        messageFormViewModel.saveEnteredUIDataDuringOrientationChange(UIFormResponse())
        assertEquals(formFieldData, formTemplate.formFieldsList[0].uiData)
    }

    @Test
    fun verifyResetIsDraftView() = runTest {
        coEvery {
            formDataStoreManager.setValue(
                FormDataStoreManager.IS_DRAFT_VIEW,
                any()
            )
        } just runs

        messageFormViewModel.resetIsDraftView()

        coVerify {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, any())
        }
    }

    @Test
    fun `verify showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection is getting executed`() = runTest {
        val tag = "Tag from EdvirFormActivity"
        every {
            messageFormViewModel.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)
        } just runs

        messageFormViewModel.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)
        verify(exactly = 1) {
            messageFormViewModel.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)
        }
    }

    @Test
    fun `verify deleteMessageForTrash calls deleteTrashMessage in messageFormUseCase`() {
        coEvery { messageFormUseCase.deleteSelectedTrashMessage(any(), any(), any(), any()) } just runs

        messageFormViewModel.deleteMessageForTrash("123", "456")

        coVerify {
            messageFormUseCase.deleteSelectedTrashMessage(any(), any(),"123", "456")
        }
    }

    @Test
    fun `verify getEncodedImage calls the usecase method`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "456"
        coEvery { messageFormUseCase.getEncodedImage(any(), any(), any(), any()) } returns "Image"
        messageFormViewModel.getEncodedImage("Image")
        coVerify {
            messageFormUseCase.getEncodedImage("123", "456", "Image", any())
        }
    }

    @Test
    fun `verify isReplayWithNewFormType calling the useCase method`() {
        assertTrue(messageFormViewModel.isReplayWithNewFormType(123, 456))
        assertFalse(messageFormViewModel.isReplayWithNewFormType(123, 123))
    }

    @Test
    fun `verify isReplayWithSameFormType calling the useCase Method`() {
        assertTrue(messageFormViewModel.isReplayWithSameFormType(123, 123))
        assertFalse(messageFormViewModel.isReplayWithSameFormType(123, 456))
    }

    @Test
    fun `verify areAllAutoFields calling usecase method`() {
        assertTrue(formRenderUseCase.areAllAutoFields(1,1))
        assertFalse(formRenderUseCase.areAllAutoFields(1,2))
    }

    @After
    fun after() {
        unmockkAll()
    }

    private fun constructFormDataToSave(): FormDataToSave {
        return FormDataToSave(
            formTemplate = FormTemplate(),
            path = EMPTY_STRING,
            formId = EMPTY_STRING,
            typeOfResponse = EMPTY_STRING,
            formName = EMPTY_STRING,
            formClass = 0,
            cid = "123",
            obcId = EMPTY_STRING
        )
    }

}