package com.trimble.ttm.routemanifest.viewmodels

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.model.Barcode
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.EDITABLE
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldAttribute
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.FreeText
import com.trimble.ttm.commons.model.MultipleChoice
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.BARCODE_KEY
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.MULTIPLECHOICE_KEY
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.utils.ext.getDriverFormID
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.DraftingUseCase
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.repo.FormsRepositoryImpl
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.FormUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION
import com.trimble.ttm.routemanifest.viewmodel.FormViewModel
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class FormViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()
    private lateinit var formViewModel: FormViewModel
    private var formResponse: FormResponse =
        FormResponse()
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var draftingUseCase: DraftingUseCase
    @MockK
    private lateinit var draftUseCase: DraftUseCase
    private lateinit var formUseCase: FormUseCase

    @RelaxedMockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    private val testScope = TestScope()
    private val obcId="11018288"
    @MockK
    private lateinit var deepLinkUseCase: DeepLinkUseCase
    @MockK
    private lateinit var intent : Intent

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase

    @RelaxedMockK
    private lateinit var dispatcherFormValuesUseCase: DispatcherFormValuesUseCase

    @RelaxedMockK
    private lateinit var messageFormRepo: MessageFormRepo

    @RelaxedMockK
    private lateinit var dispatchBaseUseCase: DispatchBaseUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        mockkObject(WorkflowApplication)
        mockkObject(ApplicationContextProvider)
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        coEvery { appModuleCommunicator.doSetObcId(any()) }  just runs
        coEvery { appModuleCommunicator.doSetCid(any()) }  just runs
        coEvery { appModuleCommunicator.doSetTruckNumber(any()) }  just runs
        appModuleCommunicator.doSetObcId(obcId)
        formUseCase = spyk(FormUseCase(
            FormsRepositoryImpl(testScope),

            mockk(),
            mockk(),
            appModuleCommunicator,
            firebaseAnalyticEventRecorder,
            localDataSourceRepo,
            formFieldDataUseCase,
            fetchDispatchStopsAndActionsUseCase = mockk(),
            dispatcherFormValuesUseCase,
            messageFormRepo
        ))
        formViewModel = FormViewModel(
            application,
            formUseCase,
            appModuleCommunicator,
            tripPanelUseCase,
            draftingUseCase,
            draftUseCase,
            TestDispatcherProvider(),
            deepLinkUseCase,
            formFieldDataUseCase
        )
    }

    @Test
    fun cancelCanDisplay() {
        assertTrue(formViewModel.canShowCancel(isDriverInImessageReplyForm = true, canShow = false))
    }

    @Test
    fun cancelCanDisplayWhenTheFlagIsSetToTrue() {
        assertTrue(formViewModel.canShowCancel(isDriverInImessageReplyForm = true, canShow = true))
    }

    @Test
    fun cancelCannotDisplayWhenDriverIsNotInReplyFormAndFlagIsSetToFalse() {
        assertFalse(
            formViewModel.canShowCancel(
                isDriverInImessageReplyForm = false,
                canShow = false
            )
        )
    }

    @Test
    fun cancelCanDisplayWhenDriverIsNotInReplyFormAndFlagIsSetToTrue() {
        assertTrue(formViewModel.canShowCancel(isDriverInImessageReplyForm = false, canShow = true))
    }

    @Test
    fun getFormActivityLauncherAttributesWhenThereAreMorePendingForms() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "11111"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "Swift"
        coEvery {
            appModuleCommunicator.getCurrentWorkFlowId(any())
        } returns "33333"

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            listOf(
                DispatchFormPath("FORM_RESPONSE", 1, 2, 85212),
                DispatchFormPath("FORM_RESPONSE", 0, 2, 83212)
            )
        )

        val attributes = formViewModel.getFormActivityLaunchAttributes(dataStoreManager, 2)
        assertTrue(attributes.first)
        assertTrue(attributes.second.actionId == 2)
        assertTrue(attributes.second.formId == 85212)
    }

    @Test
    fun getFormActivityLauncherAttributesWhenThereIsOnePendingForms() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            listOf(DispatchFormPath("FORM_RESPONSE", 1, 2, 85212))
        )

        val attributes = formViewModel.getFormActivityLaunchAttributes(dataStoreManager, 2)
        assertFalse(attributes.first)
        assertEquals(attributes.second.actionId, -1)
        assertEquals(attributes.second.formId, -1)
    }

    @Test
    fun getFormActivityLauncherAttributesWhenThereIsNoPendingForms() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            emptyList<DispatchFormPath>()
        )

        val attributes = formViewModel.getFormActivityLaunchAttributes(dataStoreManager, 2)
        assertFalse(attributes.first)
        assertEquals(attributes.second.actionId, -1)
        assertEquals(attributes.second.formId, -1)
    }

    @Test
    fun getFormActivityLauncherAttributesWhenThereAreTwoPendingFormsButSameStopId() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            listOf(DispatchFormPath("FORM_RESPONSE", 1, 2, 85212),
                DispatchFormPath("FORM_RESPONSE", 1, 2, 85212))
        )

        val attributes = formViewModel.getFormActivityLaunchAttributes(dataStoreManager, 1)
        assertFalse(attributes.first)
        assertEquals(attributes.second.actionId, -1)
        assertEquals(attributes.second.formId, -1)
    }

    @Test
    fun getFormResponsePath() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "11111"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "22222"
        coEvery {
            appModuleCommunicator.getCurrentWorkFlowId(any())
        } returns "33333"

        assertTrue(formViewModel.getFormResponsePath(2,1) == "PFMFormResponses/11111/22222/33333/2/1")
    }

    @Test
    fun `validate text form data in FormResponse`() {    //NOSONAR
        val fieldValue = "John Henry"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Name",
                        FormFieldType.TEXT.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,"1101").let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[FREETEXT_KEY] as String
                    val freeText = Gson().fromJson(formDataString, FreeText::class.java)
                    assertEquals(formTemplate.formFieldsList[0].uiData, freeText.text)
                }
            }
        }
    }

    @Test
    fun `validate numeric form data in FormResponse`() {    //NOSONAR
        val fieldValue = "26"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Name",
                        FormFieldType.NUMERIC.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,"11018288").let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[FREETEXT_KEY] as String
                    val freeText = Gson().fromJson(formDataString, FreeText::class.java)
                    assertEquals(formTemplate.formFieldsList[0].uiData, freeText.text)
                }
            }
        }
    }

    @Test
    fun `validate enhancedNumeric form data in FormResponse`() {    //NOSONAR
        val fieldValue = "$256"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Amount",
                        FormFieldType.NUMERIC_ENHANCED.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,"11018288").let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[FREETEXT_KEY] as String
                    val freeText = Gson().fromJson(formDataString, FreeText::class.java)
                    assertEquals(formTemplate.formFieldsList[0].uiData, freeText.text)
                }
            }
        }
    }

    @Test
    fun `validate displayText form data in FormResponse`() {    //NOSONAR
        val fieldValue = "Happy Journey"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Message",
                        FormFieldType.DISPLAY_TEXT.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,obcId).let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[FREETEXT_KEY] as String
                    val freeText = Gson().fromJson(formDataString, FreeText::class.java)
                    assertEquals(formTemplate.formFieldsList[0].uiData, freeText.text)
                }
            }
        }
    }


    @Test
    fun `validate multipleChoice form data in FormResponse`() {    //NOSONAR
        val formChoice =
            FormChoice(1, 1, "20", 1, 1)
        val fieldValue = "20"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Name",
                        FormFieldType.MULTIPLE_CHOICE.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                        this.formChoiceList = arrayListOf(formChoice)
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,obcId).let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[MULTIPLECHOICE_KEY] as String
                    val multipleChoice = Gson().fromJson(formDataString, MultipleChoice::class.java)
                    formTemplate.formFieldsList.forEach { formField ->
                        formField.formChoiceList!!.find { formChoice -> formChoice.choicenum == multipleChoice.choice }
                            ?.let { formChoice ->
                                Assert.assertNotNull(formChoice)
                            }
                    }
                }
            }
        }
    }

    @Test
    fun `validate barcode form data in FormResponse`() {    //NOSONAR
        val fieldValue = "9876543456565767676756"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Barcode",
                        FormFieldType.BARCODE_SCAN.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,obcId).let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val formDataMap =
                        formResponse.fieldData[formTemplate.formFieldsList[0].qnum - 1] as HashMap<String, Any>
                    val formDataString = formDataMap[BARCODE_KEY] as String
                    val barcode = Gson().fromJson(formDataString, Barcode::class.java)
                    assertEquals(formTemplate.formFieldsList[0].uiData, barcode.barcodeData)
                }
            }
        }
    }

    @Test
    fun `validate multipleBarcode form data in FormResponse`() {    //NOSONAR
        val fieldValue = "9876543456565767676756,78657843456565767676756"
        val formTemplate =
            FormTemplate(
                FormDef(),
                arrayListOf(
                    FormField(
                        1,
                        "Barcode",
                        FormFieldType.BARCODE_SCAN.ordinal,
                        12,
                        ""
                    ).apply {
                        uiData = fieldValue
                    })
            )
        testScope.launch {
            formUseCase.addFieldDataInFormResponse(formTemplate, formResponse,obcId).let {
                it.safeCollect(this.javaClass.name) { formResponse ->
                    val barcodeList = fieldValue.split(",")
                    assertEquals(barcodeList.size, formResponse.fieldData.size)
                }
            }
        }
    }

    @Test
    fun `check the numeric form field type  is composable or not`() {
        assertTrue(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.NUMERIC.ordinal)))
    }

    @Test
    fun `check the text form field type is composable or not`() {
        assertTrue(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.TEXT.ordinal)))
    }

    @Test
    fun `check the display text form field type is composable or not`() {
        assertTrue(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.DISPLAY_TEXT.ordinal)))
    }

    @Test
    fun `check the barcode form field type is composable or not`() {
        assertTrue(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.BARCODE_SCAN.ordinal)))
    }

    @Test
    fun `check the image ref form field type is composable or not`() {
        assertFalse(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal)))
    }

    @Test
    fun `check the signature form field type is composable or not`() {
        assertTrue(formViewModel.isComposableView(formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)))
    }


    @Test
    fun `check the date view is of textInputLayout view type`() {
        assertTrue(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.DATE.ordinal)))
    }

    @Test
    fun `check the barcode view is of textInputLayout view type`() {
        assertTrue(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.BARCODE_SCAN.ordinal)))
    }

    @Test
    fun `check the date & time view is of textInputLayout view type`() {
        assertTrue(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.DATE_TIME.ordinal)))
    }

    @Test
    fun `check the numeric view is of textInputLayout view type`() {
        assertTrue(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.NUMERIC.ordinal)))
    }

    @Test
    fun `check the password view is of textInputLayout view type`() {
        assertTrue(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.PASSWORD.ordinal)))
    }

    @Test
    fun `check the signature view is of textInputLayout view type`() {
        assertFalse(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)))
    }

    @Test
    fun `check the image ref view is of textInputLayout view type`() {
        assertFalse(formViewModel.isOfTextInputLayoutViewType(formField = FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal)))
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form, reply form are different and valid, driver is in driver form and form is not saved `() {
        assertTrue(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 19245, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form, reply form are same and valid, driver is in driver form and form is not saved`() {
        assertTrue(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 18920, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is freeform, reply form is normal form, driver is in driver form and form is not saved`() {
        assertTrue(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 1
                ),
                replyFormDef = FormDef(formid = 19245, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is normal form, reply form is freeform, driver is in driver form and form is not saved`() {
        assertTrue(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 19245,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 18920, formClass = 1),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form id is 1, reply form id is 0, driver is in driver form and form is not saved`() {
        assertTrue(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 1,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 0, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is invalid, reply form is valid, driver is in driver form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = -1,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 18920, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is valid, reply form is invalid, driver is in driver form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 1,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = -1, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = false
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form class is invalid, reply form class is valid, driver is in driver form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 2,
                    formClass = -1
                ),
                replyFormDef = FormDef(formid = 18920, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form class is valid, reply form class is invalid, driver is in driver form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 1,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 2, formClass = -1),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = false
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form id & form class are invalid, reply form id & form class are invalid, driver is in driver form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = -1,
                    formClass = -1
                ),
                replyFormDef = FormDef(formid = -2, formClass = -1),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is valid, reply form is valid, driver is in reply form and form is not saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 19245, formClass = 0),
                isDriverInImessageReplyForm = true,
                isSyncToQueue = false,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is valid, reply form is valid, driver is in driver form and form is saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 19245, formClass = 0),
                isDriverInImessageReplyForm = false,
                isSyncToQueue = true,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is valid, reply form is valid, driver is in reply form and form is saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = 18920,
                    formClass = 0
                ),
                replyFormDef = FormDef(formid = 19245, formClass = 0),
                isDriverInImessageReplyForm = true,
                isSyncToQueue = true,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check the imessage forms are valid or not if the driver form is invalid, reply form is invalid, driver is in reply form and form is saved`() {
        assertFalse(
            formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = FormDef(
                    formid = -1,
                    formClass = -1
                ),
                replyFormDef = FormDef(formid = -2, formClass = -1),
                isDriverInImessageReplyForm = true,
                isSyncToQueue = true,
                isCannotSendAction = true
            )
        )
    }

    @Test
    fun `check send Button click event listener emit true when button is clicked`() = runTest{
        val result = mutableListOf<Boolean>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            formViewModel.sendButtonClickEventListener.toList(result)
        }
        formViewModel.setSendButtonClickEvent(true)
        assertTrue(result.first())
        collectJob.cancel()
    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndFormSubmissionEvent gets called when eventName is not empty`(){
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE,1)
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARRIVAL_TIME,
                EMPTY_STRING
            )
        } returns calendar.time.toString()
        every {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(any(),any())
        } just runs
        formViewModel.calculateAndRecordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
            TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION,dataStoreManager)
        verify(exactly = 1) {
            formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(any(), any())
        }
    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndFormSubmissionEvent gets called when eventName is empty`(){
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE,1)
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARRIVAL_TIME,
                EMPTY_STRING
            )
        } returns calendar.time.toString()
        every {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(any(),any())
        } just runs
        formViewModel.calculateAndRecordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
            EMPTY_STRING, dataStoreManager
        )
        verify(exactly = 0) {
            formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(any(), any())
        }
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is Editable`() {
        every { application.getString(any()) } returns "Enter your Message"
        val formField = FormField(driverEditable = EDITABLE)
        formField.uiData = "Hello"
        val expectedPair = Pair("Enter your Message","Hello")
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            EMPTY_STRING)
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is not editable shouldDisplayOriginalMessage is false`() {
        every { application.getString(any()) } returns "Enter your Message"
        val formField = FormField(driverEditable = 0)
        val expectedPair = Pair("Enter your Message", EMPTY_STRING)
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            EMPTY_STRING,false)
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is Editable and shouldDisplayOriginalMessage is false`() {
        every { application.getString(any()) } returns "Enter your Message"
        val formField = FormField(driverEditable = EDITABLE)
        formField.uiData = "Hello"
        val expectedPair = Pair("Enter your Message", "Hello")
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            EMPTY_STRING,false)
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is Editable, shouldDisplayOriginalMessage is false and formField ui data is empty`() {
        every { application.getString(any()) } returns "Enter your Message"
        val formField = FormField(driverEditable = EDITABLE)
        val expectedPair = Pair("Enter your Message", EMPTY_STRING)
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            EMPTY_STRING,false)
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is not editable and shouldDisplayOriginalMessage is true`() {
        every { application.getString(any()) } returns "Message"
        val formField = FormField(driverEditable = 0)
        formField.uiData = "Hello"
        val expectedPair = Pair("Message", "Hello")
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            EMPTY_STRING)
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun `check getFreeFormEditTextHintAndMessage when formField is not editable and shouldDisplayOriginalMessage is true and formField ui data is empty`() {
        every { application.getString(any()) } returns "Message"
        val formField = FormField(driverEditable = 0)
        val expectedPair = Pair("Message", "Hello")
        val actualPair = formViewModel.getFreeFormEditTextHintAndMessage(formField,
            "Hello")
        assertEquals(expectedPair,actualPair)
    }

    @Test
    fun verifyCheckForDeepLinkConfigurationCalled() {
        val formTemplateData = FormTemplate(
            formDef = FormDef(formid = 91234),
            formFieldsList = arrayListOf(
                FormField(qtext = "WEIGHT").apply {
                    uiData = "230"
                }
            )
        )
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        formViewModel.checkForDeepLinkConfiguration(context = context, formTemplateData = formTemplateData, caller = "")
        verify(exactly = 1) {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(
                context = context,
                formTemplateData = formTemplateData,
                caller = ""
            )
        }
    }

    @Test
    fun `verify form should be drafted if the form is not saved, not dtf, draft feature flag enabled and isDriverInSingleForm`() {
        assertTrue(formViewModel.shouldFormBeDrafted(isSyncToQueue = false, isDraftFeatureFlagEnabled = true, isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is not saved, not dtf,  draft feature flag enabled and IsDriverInImessageReplyForm`() {
        assertTrue(formViewModel.shouldFormBeDrafted(isSyncToQueue = false, isDraftFeatureFlagEnabled = true, isDriverInSingleForm = false, isDriverInImessageReplyForm = true, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is saved, not dtf and draft feature flag enabled`() {
        assertFalse(formViewModel.shouldFormBeDrafted(isSyncToQueue = true, isDraftFeatureFlagEnabled = true,  isDriverInSingleForm = false, isDriverInImessageReplyForm = false, cid = -1))
    }

    @Test
    fun `verify form should be drafted if the form is not saved, dtf and draft feature flag is enabled`() {
        assertTrue(formViewModel.shouldFormBeDrafted(isSyncToQueue = false, isDraftFeatureFlagEnabled = true,  isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is not saved,not dtf and draft feature flag is not enabled`() {
        assertFalse(formViewModel.shouldFormBeDrafted(isSyncToQueue = false, isDraftFeatureFlagEnabled = false,  isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is not saved, dtf and draft feature flag not enabled`() {
        assertFalse(formViewModel.shouldFormBeDrafted(isSyncToQueue = false, isDraftFeatureFlagEnabled = false,  isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is saved, dtf and draft feature flag is not enabled`() {
        assertFalse(formViewModel.shouldFormBeDrafted(isSyncToQueue = true, isDraftFeatureFlagEnabled = false,  isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `verify form should be drafted if the form is saved, dtf and draft feature flag is enabled`() {
        assertFalse(formViewModel.shouldFormBeDrafted(isSyncToQueue = true, isDraftFeatureFlagEnabled = true,  isDriverInSingleForm = true, isDriverInImessageReplyForm = false, cid = 1))
    }

    @Test
    fun `setDefaultValueForFreeFormMessage sets value when all conditions are met`() {
        val formField = FormField()
        formField.uiData = EMPTY_STRING
        val freeFormMessage = "Default Message"

        formViewModel.setDefaultValueForFreeFormMessage(true, formField, freeFormMessage, true, false)

        assertEquals(freeFormMessage, formField.uiData)
    }

    @Test
    fun `setDefaultValueForFreeFormMessage does not set value when isFormSaved is true`() {
        val formField = FormField()
        formField.uiData = EMPTY_STRING
        val freeFormMessage = "Default Message"

        formViewModel.setDefaultValueForFreeFormMessage(true, formField, freeFormMessage, true, true)

        assertEquals("", formField.uiData)
    }

    @Test
    fun `setDefaultValueForFreeFormMessage does not set value when isDriverInImessageReplyForm is false`() {
        val formField = FormField()
        formField.uiData = EMPTY_STRING
        val freeFormMessage = "Default Message"

        formViewModel.setDefaultValueForFreeFormMessage(true, formField, freeFormMessage, false, false)

        assertEquals("", formField.uiData)
    }

    @Test
    fun `setDefaultValueForFreeFormMessage does not set value when isReplyWithSame is false`() {
        val formField = FormField()
        formField.uiData = EMPTY_STRING
        val freeFormMessage = "Default Message"

        formViewModel.setDefaultValueForFreeFormMessage(false, formField, freeFormMessage, true, false)

        assertEquals("", formField.uiData)
    }

    @Test
    fun `setDefaultValueForFreeFormMessage does not set value when uiData is not empty`() {
        val formField = FormField()
        formField.uiData = "Existing Data"
        val freeFormMessage = "Default Message"

        formViewModel.setDefaultValueForFreeFormMessage(true, formField, freeFormMessage, true, false)

        assertEquals("Existing Data", formField.uiData)
    }

    @Test
    fun `changeDriverEditableValueForReplyFreeForm sets driverEditable when isDriverInImessageReplyForm is true`() {
        val formField = FormField(driverEditable = 0)

        formViewModel.changeDriverEditableValueForReplyFreeForm(true, formField)

        assertEquals(EDITABLE, formField.driverEditable)
    }

    @Test
    fun `changeDriverEditableValueForReplyFreeForm does not set driverEditable when isDriverInImessageReplyForm is false`() {
        val formField = FormField(driverEditable = 0)

        formViewModel.changeDriverEditableValueForReplyFreeForm(false, formField)

        assertEquals(0, formField.driverEditable)
    }

    @Test
    fun `getDraftedFormFieldMap returns empty map when formResponse is null`() {
        val result = formViewModel.getDraftedFormFieldMap(null)
        assertEquals(0, result.size)
    }

    @Test
    fun `getDraftedFormFieldMap returns empty map when formResponse has no fieldData`() {
        val formResponse = FormResponse()
        val result = formViewModel.getDraftedFormFieldMap(formResponse)
        assertEquals(0, result.size)
    }

    @Test
    fun `getDraftedFormFieldMap returns map with one entry when formResponse has one fieldData`() {
        val formFieldAttribute = FormFieldAttribute(uniqueTag = "uniqueTag1")
        every { formFieldDataUseCase.getFormFieldAttributeFromFieldDatum(any()) } returns formFieldAttribute

        val formResponse = FormResponse(fieldData = arrayListOf("fieldData1"))
        val result = formViewModel.getDraftedFormFieldMap(formResponse)

        assertEquals(1, result.size)
        assertEquals(formFieldAttribute, result["uniqueTag1"])
    }

    @Test
    fun `getDraftedFormFieldMap returns map with multiple entries when formResponse has multiple fieldData`() {
        val formFieldAttribute1 = FormFieldAttribute(uniqueTag = "uniqueTag1")
        val formFieldAttribute2 = FormFieldAttribute(uniqueTag = "uniqueTag2")
        every { formFieldDataUseCase.getFormFieldAttributeFromFieldDatum("fieldData1") } returns formFieldAttribute1
        every { formFieldDataUseCase.getFormFieldAttributeFromFieldDatum("fieldData2") } returns formFieldAttribute2

        val formResponse = FormResponse(fieldData = arrayListOf("fieldData1", "fieldData2"))
        val result = formViewModel.getDraftedFormFieldMap(formResponse)

        assertEquals(2, result.size)
        assertEquals(formFieldAttribute1, result["uniqueTag1"])
        assertEquals(formFieldAttribute2, result["uniqueTag2"])
    }

    @Test
    fun `getDispatchFormPath returns dispatchFormPath when isDriverInImessageReplyForm is false`() {
        val dispatchFormPath = DispatchFormPath()

        val result = formViewModel.getDispatchFormPath(false, intent, dispatchFormPath)

        assertEquals(dispatchFormPath, result)
    }

    @Test
    fun `getDispatchFormPath returns form path from intent when isDriverInImessageReplyForm is true`() {
        val dispatchFormPath = DispatchFormPath()
        val expectedFormPath = DispatchFormPath()
        every { intent.getDriverFormID() } returns expectedFormPath

        val result = formViewModel.getDispatchFormPath(true, intent, dispatchFormPath)

        assertEquals(expectedFormPath, result)
    }

    @Test
    fun `restoreSelectedDispatch calls executeAction after restoring dispatch`() {
        val executeAction = mockk<() -> Unit>(relaxed = true)
        coEvery { appModuleCommunicator.restoreSelectedDispatch() } just runs

        formViewModel.restoreSelectedDispatch(executeAction)

        coVerify(exactly = 1) {
            appModuleCommunicator.restoreSelectedDispatch()
            executeAction()
        }
    }

    @Test
    fun `setFormUiState emits the given screenContentState`() = runTest {
        val screenContentState = ScreenContentState.Loading()
        formViewModel.setFormUiState(screenContentState)
        assertEquals(screenContentState, formViewModel.formUiState.first())
    }

    @Test
    fun `saveDispatchFormDataToDraft saves draft data correctly`() = runTest {
        val formTemplateData = mockk<FormTemplate>()
        val stopActionFormKey = DispatchFormPath()
        val documentSavePath = "documentSavePath"
        val stopId = 1
        val isReplyWithSame = true
        val cid = "cid"
        val vehicleNumber = "vehicleNumber"
        val obcId = "obcId"
        val replyFormName = "replyFormName"

        every { formTemplateData.formDef.name } returns replyFormName
        every { formTemplateData.formDef.formid } returns 123
        every { formTemplateData.formDef.formClass } returns 456
        every { formTemplateData.formDef.recipients } returns mapOf()

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns vehicleNumber
        coEvery { appModuleCommunicator.doGetObcId() } returns obcId
        coEvery { formUseCase.removeFormFromPreferencesForStop(stopId) } just runs
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(documentSavePath, cid, vehicleNumber) } just runs
        coEvery { draftingUseCase.makeDraft(any(), any(), any()) } just runs

        formViewModel.saveDispatchFormDataToDraft(formTemplateData, stopActionFormKey, documentSavePath, stopId, isReplyWithSame)
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) {
            formUseCase.removeFormFromPreferencesForStop(stopId)
            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(documentSavePath, cid, vehicleNumber)
            draftingUseCase.makeDraft(any(), any(), any())
        }
    }

    @Test
    fun `saveFormData saves form data correctly with isSyncToQueue true`() = runTest {
        val formTemplate = mockk<FormTemplate>(relaxed = true)
        val path = "PFMFormResponses/5000/vid/10001/2/1"
        val actionId = 1
        val latestFormRecipients = arrayListOf<Recipients>(mockk(), mockk())
        val formResponse = FormResponse()
        val cid = "cid"
        val truckNumber = "truckNumber"
        val obcId = "obcId"

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNumber
        coEvery { appModuleCommunicator.doGetObcId() } returns obcId
        coEvery { formUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { formUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(formResponse) }
        coEvery { formUseCase.saveFormData(any(), any(), any(), any()) } returns true
        coEvery { draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any()) } just runs
        coEvery { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any()) } just runs
        every { application.applicationContext } returns mockk()

        formViewModel.saveFormData(formTemplate, path, true, latestFormRecipients, DispatchFormPath())
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) {
            formUseCase.mapImageUniqueIdentifier(any())
            formUseCase.addFieldDataInFormResponse(any(), any(), any())
            formUseCase.saveFormData(any(), any(), any(), any())
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(any(), any(), any())
            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(any(), any(), any())
        }
    }

    @Test
    fun `saveFormData saves form data correctly with isSyncToQueue false`() = runTest {
        val formTemplate = mockk<FormTemplate>(relaxed = true)
        val path = "path"
        val actionId = 1
        val latestFormRecipients = arrayListOf<Recipients>(mockk(), mockk())
        val formResponse = FormResponse()
        val cid = "cid"
        val truckNumber = "truckNumber"
        val obcId = "obcId"

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNumber
        coEvery { appModuleCommunicator.doGetObcId() } returns obcId
        coEvery { formUseCase.mapImageUniqueIdentifier(any()) } just runs
        coEvery { formUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(formResponse) }
        coEvery { formUseCase.saveFormData(any(), any(), any(), any()) } returns true
        every { application.applicationContext } returns mockk()

        formViewModel.saveFormData(formTemplate, path, false, latestFormRecipients, DispatchFormPath())
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) {
            formUseCase.mapImageUniqueIdentifier(any())
            formUseCase.addFieldDataInFormResponse(any(), any(), any())
            formUseCase.saveFormData(any(), any(), any(), any())
        }
    }

    @Test
    fun `getLatestFormRecipients returns the correct recipients`() = runTest {
        val customerId = 123
        val formID = 456
        val expectedRecipients = arrayListOf<Recipients>(mockk(), mockk())

        coEvery { formUseCase.getLatestFormRecipients(customerId, formID) } returns expectedRecipients

        val result = formViewModel.getLatestFormRecipients(customerId, formID)

        assertEquals(expectedRecipients, result)
        coVerify(exactly = 1) { formUseCase.getLatestFormRecipients(customerId, formID) }
    }

    @Test
    fun `removeFormFromStack removes form from preference`() = runTest {
        val stopId = 1
        coEvery { formUseCase.removeFormFromPreference(any(), any()) } returns  "Done"
        formViewModel.removeFormFromStack(stopId, dataStoreManager)

        coVerify(exactly = 1) { formUseCase.removeFormFromPreference(stopId, dataStoreManager) }
    }

    @Test
    fun `getFormData returns the correct UIFormResponse`() = runTest {
        val path = "path"
        val actionId = "actionId"
        val isActionResponseSentToServer = true
        val expectedUIFormResponse = mockk<UIFormResponse>()

        coEvery { formUseCase.getFormData(path, actionId, isActionResponseSentToServer) } returns expectedUIFormResponse

        val result = formViewModel.getFormData(path, actionId, isActionResponseSentToServer)

        assertEquals(expectedUIFormResponse, result)
        coVerify(exactly = 1) { formUseCase.getFormData(path, actionId, isActionResponseSentToServer) }
    }

    @Test
    fun `getStopAction returns the correct Action`() = runTest {
        val stopId = "stopId"
        val actionId = "actionId"
        val activeDispatch = "activeDispatch"
        val cid = "cid"
        val truckNumber = "truckNumber"
        val expectedAction = mockk<Action>()

        coEvery { appModuleCommunicator.getCurrentWorkFlowId("FVMGetStopAction") } returns activeDispatch
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNumber
        coEvery { formUseCase.getStopAction(truckNumber, cid, activeDispatch, stopId, actionId) } returns expectedAction

        val result = formViewModel.getStopAction(stopId, actionId)

        assertEquals(expectedAction, result)
        coVerify(exactly = 1) { formUseCase.getStopAction(truckNumber, cid, activeDispatch, stopId, actionId) }
    }

    @Test
    fun `getForm returns the correct Form`() = runTest {
        val isActionResponseSentToServer = true
        val isDriverInImessageReplyForm = false
        val isReplyWithSameForm = false
        val activeDispatch = "activeDispatch"
        val cid = "cid"
        val truckNumber = "truckNumber"
        val formData = FormData(customerId = cid, vehicleId = truckNumber, dispatchId = activeDispatch, formTemplatePath = "path", stopId = "1", actionId = "1", formId = "123", isFreeForm = false)
        val expectedForm = mockk<Form>()

        coEvery { appModuleCommunicator.getCurrentWorkFlowId("FVMGetForm") } returns activeDispatch
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNumber
        coEvery { formUseCase.getForm(any(), any(), any(), any(), any()) } returns expectedForm

        val result = formViewModel.getForm(formData, isActionResponseSentToServer, isDriverInImessageReplyForm, isReplyWithSameForm)

        assertEquals(expectedForm, result)
        coVerify(exactly = 1) { formUseCase.getForm(any(), isActionResponseSentToServer, isDriverInImessageReplyForm, isReplyWithSameForm, any()) }
    }

    @Test
    fun `getFreeForm returns the correct Form`() = runTest {
        val formTemplatePath = "formTemplatePath"
        val actionId = "actionId"
        val formId = 123
        val expectedForm = mockk<Form>()

        coEvery { formUseCase.getFreeForm(formTemplatePath, actionId, formId) } returns expectedForm

        val result = formViewModel.getFreeForm(formTemplatePath, actionId, formId)

        assertEquals(expectedForm, result)
        coVerify(exactly = 1) { formUseCase.getFreeForm(formTemplatePath, actionId, formId) }
    }

    @Test
    fun `checkForCompleteFormMessages calls tripPanelUseCase checkForCompleteFormMessages`() = runTest {
        coEvery { tripPanelUseCase.checkForCompleteFormMessages() } just runs

        formViewModel.checkForCompleteFormMessages()

        coVerify(exactly = 1) { tripPanelUseCase.checkForCompleteFormMessages() }
    }

    @Test
    fun `shouldSaveFormDataDuringConfigurationChange returns correct value`() {
        val isSyncToQueue = false
        val isDriverInSingleForm = true
        val isDriverInImessageReplyForm = false
        val cid = 1
        val isChangingConfigurations = false

        val result = formViewModel.shouldSaveFormDataDuringConfigurationChange(
            isSyncToQueue,
            isDriverInSingleForm,
            isDriverInImessageReplyForm,
            cid,
            isChangingConfigurations
        )

        assertTrue(result)
    }

    @Test
    fun `onInvalidForm calls formUseCase onInvalidForm with correct parameters`() = runTest {
        val caller = "caller"
        val stopId = 1
        val actionId = 2

        coEvery { formUseCase.onInvalidForm(caller, stopId, actionId) } just runs

        formViewModel.onInvalidForm(caller, stopId, actionId)

        coVerify(exactly = 1) { formUseCase.onInvalidForm(caller, stopId, actionId) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}