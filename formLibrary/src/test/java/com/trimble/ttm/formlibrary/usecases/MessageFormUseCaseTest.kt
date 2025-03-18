package com.trimble.ttm.formlibrary.usecases

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.DispatcherFormValuesPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.DriverMessageFormData
import com.trimble.ttm.formlibrary.model.MessageFormData
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.repo.MessageFormRepoImpl
import com.trimble.ttm.formlibrary.utils.CORPORATE_FREE_FORM_ID_DEV_AND_PROD
import com.trimble.ttm.formlibrary.utils.CORPORATE_FREE_FORM_ID_QA_AND_STAGE
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FLAVOR_DEV
import com.trimble.ttm.formlibrary.utils.FLAVOR_PROD
import com.trimble.ttm.formlibrary.utils.FLAVOR_QA
import com.trimble.ttm.formlibrary.utils.FLAVOR_STG
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import java.util.Stack
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MessageFormUseCaseTest: KoinTest {

    @RelaxedMockK
    private lateinit var application: Application

    private var formTemplate = FormTemplate()

    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var context: Context
    private val mailUsertrimble = "user1@trimble.com"

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var coroutineContext: CoroutineContext

    private lateinit var subjectUnderTest: MessageFormUseCase
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private lateinit var messageFormRepo: MessageFormRepoImpl
    private lateinit var firebaseAnalyticEventRecorder : FirebaseAnalyticEventRecorder
    @RelaxedMockK
    private lateinit var dispatcherFormValuesUseCase: DispatcherFormValuesUseCase
    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase


    private var koinModulesRequiredForTest = module {
        factory { MessageFormUseCase(get(), mockk(), get(), formFieldDataUseCase = formFieldDataUseCase, dispatcherFormValuesUseCase = dispatcherFormValuesUseCase) }
        factory { DraftUseCase(get(), get(), mockk(), firebaseAnalyticEventRecorder) }
        factory { FormRenderUseCase() }
        single<AppModuleCommunicator> { mockk() }
        single<MessageFormRepo> { MessageFormRepoImpl(get(), TestDispatcherProvider()) }
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        coroutineScope = mockk()
        coroutineContext = mockk()
        formDataStoreManager = spyk(FormDataStoreManager(context))
        firebaseAnalyticEventRecorder = mockk()
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(any()) } returns mockk()
        val mockFirebaseFirestore = mockk<FirebaseFirestore>()
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockFirebaseFirestore
        startKoin {
            androidContext(application)
            loadKoinModules(koinModulesRequiredForTest)
        }
        messageFormRepo = spyk(MessageFormRepoImpl(appModuleCommunicator, TestDispatcherProvider(), mockFirebaseFirestore))
        subjectUnderTest = spyk(MessageFormUseCase(mockk(), mockk(), messageFormRepo, formFieldDataUseCase = formFieldDataUseCase, dispatcherFormValuesUseCase = dispatcherFormValuesUseCase))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetObcId() } returns "11018288"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123456"
        formTemplate = FormTemplate(
            FormDef(
                cid = 10119,
                recipients = HashMap<String, String>().also {
                    it["emailUser"] = mailUsertrimble
                }
            )
        )
        mockkObject(Log)
    }

    @Test
    fun `verify getFreeForm - DEV flavor Form ID`() = runTest {
        `devProd getFreeForm helper for Test`(CORPORATE_FREE_FORM_ID_DEV_AND_PROD, FLAVOR_DEV)
    }

    @Test
    fun `verify getFreeForm - PROD flavor Form ID`() = runTest {
        `devProd getFreeForm helper for Test`(CORPORATE_FREE_FORM_ID_DEV_AND_PROD, FLAVOR_PROD)
    }

    @Test
    fun `verify getFreeForm - QA flavor Form ID`() = runTest {
        `devProd getFreeForm helper for Test`(CORPORATE_FREE_FORM_ID_QA_AND_STAGE, FLAVOR_QA)
    }

    @Test
    fun `verify getFreeForm - STG flavor Form ID`() = runTest {
        `devProd getFreeForm helper for Test`(CORPORATE_FREE_FORM_ID_QA_AND_STAGE, FLAVOR_STG)
    }

    private fun `devProd getFreeForm helper for Test`(formId: Int, flavor: String) = runTest {
        val formTemp = FormTemplate(FormDef(cid = 123, formid = formId), arrayListOf())
        coEvery { appModuleCommunicator.getAppFlavor() } returns flavor
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "2344"
        coEvery { messageFormRepo.getFreeForm(any(), any(), any()) } returns formTemp
        coEvery { subjectUnderTest.getFreeForm(any())} returns Form(formTemp, UIFormResponse(),
            hashMapOf()
        )

        val form = subjectUnderTest.getFreeForm(UIFormResponse())

        assertEquals(formTemp.formDef.cid,form.formTemplate.formDef.cid)

    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token available - dev flavor`() = runTest {
        markAllTheMessagesAsDeletedHelper(FLAVOR_DEV)
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token available - qa flavor`() = runTest {
        markAllTheMessagesAsDeletedHelper(FLAVOR_QA)
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token available - prod flavor`() = runTest {
        markAllTheMessagesAsDeletedHelper(FLAVOR_PROD)
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token available - stg flavor`() = runTest {
        markAllTheMessagesAsDeletedHelper(FLAVOR_STG)
    }

    private fun markAllTheMessagesAsDeletedHelper(flavor: String) = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns flavor
        coEvery {
            messageFormRepo.markAllTheMessagesAsDeleted(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        subjectUnderTest.markAllTheMessagesAsDeleted("122", "2131", "testtoken", "appchecktoken")

        coVerify {
            messageFormRepo.markAllTheMessagesAsDeleted("122", "2131", any(), "testtoken", "appchecktoken")
        }
    }

    @Test
    fun `verify isMessageAlreadyRead`() = runTest {
        coEvery {
            messageFormRepo.isMessageAlreadyRead(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns true
        subjectUnderTest.isMessageAlreadyRead("213", "123", "123", "rs12", "21312")

        coVerify { messageFormRepo.isMessageAlreadyRead(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token available`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            messageFormRepo.markAllTheMessagesAsDeleted(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        subjectUnderTest.markAllTheMessagesAsDeleted("122", "2131", "testtoken", "appchecktoken")

        coVerify {
            messageFormRepo.markAllTheMessagesAsDeleted(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        val collectedData = mutableListOf<CollectionDeleteResponse>()
        launch {
            val deleteAllResponseFlowObserver = messageFormRepo.getMessagesDeleteAllFlow()
            launch(Job()) {
                deleteAllResponseFlowObserver.collect {
                    collectedData.add(it)
                }
            }
        }.join()
        subjectUnderTest.markAllTheMessagesAsDeleted("122", "2131", null, "")
        assertEquals(false, collectedData.first().isDeleteSuccess)
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - app check token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        val collectedData = mutableListOf<CollectionDeleteResponse>()
        launch {
            val deleteAllResponseFlowObserver = messageFormRepo.getMessagesDeleteAllFlow()
            launch(Job()) {
                deleteAllResponseFlowObserver.collect {
                    collectedData.add(it)
                }
            }
        }.join()
        subjectUnderTest.markAllTheMessagesAsDeleted("122", "2131", "5656568587578", "")
        assertEquals(false, collectedData.first().isDeleteSuccess)
    }

    @Test
    fun `verify markAllTheMessagesAsDeleted - customer id Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        val collectedData = mutableListOf<CollectionDeleteResponse>()
        launch {
            val deleteAllResponseFlowObserver = messageFormRepo.getMessagesDeleteAllFlow()
            launch(Job()) {
                deleteAllResponseFlowObserver.collect {
                    collectedData.add(it)
                }
            }
        }.join()
        subjectUnderTest.markAllTheMessagesAsDeleted("", "2131", "token", "appchecktoken")
        assertEquals(false, collectedData.first().isDeleteSuccess)
    }

    @Test
    fun `verify cacheFormTemplate formid empty`() = runTest {
        coEvery { Log.i(any(), any()) } returns Unit
        coEvery { coroutineScope.coroutineContext } returns coroutineContext
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns coroutineScope
        subjectUnderTest.cacheFormTemplate("", true, "test")

        coVerify(exactly = 0) {
            Log.i(any(), any())
        }
    }

    @Test
    fun `verify getForm for freeform call`() = runTest {    //NOSONAR
        val form = Form(formTemplate, UIFormResponse(), hashMapOf())

        coEvery {
            messageFormRepo.getForm(any(), any())
        } returns formTemplate
        coEvery {
            messageFormRepo.getFreeForm(any(), any(), any())
        } returns formTemplate
        assertEquals(
            form.formTemplate,
            subjectUnderTest.getForm("10119", true).formTemplate
        )
    }

    @Test
    fun `verify getForm for normal form call`() = runTest {    //NOSONAR
        val form = Form(formTemplate, UIFormResponse(), hashMapOf())

        coEvery {
            messageFormRepo.getForm(any(), any())
        } returns formTemplate
        assertEquals(
            form.formTemplate,
            subjectUnderTest.getForm("10119", false).formTemplate
        )
    }

    @Test
    fun `verify saveFormData call`() = runTest {    //NOSONAR
        val users = mutableSetOf<User>().also {
            it.add(User(1))
        }
        coEvery {
            messageFormRepo.getRecipientUserName(
                any(),
                any()
            )
        } returns users
        val messageFormData = MessageFormData(
            "",
            FormResponse(),
            "",
            "",
            "",
            0,
            9,
            false
        )
        coEvery { subjectUnderTest.saveFormData(messageFormData) } returns true
        subjectUnderTest.saveFormData(messageFormData)
        coVerify {
            subjectUnderTest.saveFormData(
                messageFormData
            )
        }
    }

    @Test
    fun `verify getLatestFormRecipients call`() = runTest {    //NOSONAR
        val recipients = hashMapOf<String, Any>().also {
            it["user1"] = Recipients(null, mailUsertrimble)
            it["user2"] = Recipients(121212, null)
        }
        coEvery {
            subjectUnderTest.getLatestFormRecipients(any(), any())
        } returns recipients
        assertEquals(recipients, subjectUnderTest.getLatestFormRecipients(1, 2))
    }

    @Test
    fun `verify getFormFieldStackCopy call`() { // NOSONAR
        val formFieldStack = Stack<FormField>().also {
            it.push(FormField(1))
            it.push(FormField(2))
        }
        val result = subjectUnderTest.getFormFieldStackCopy(formFieldStack)
        assertEquals(formFieldStack, result)
    }

    @Test
    fun `verify getFormFieldCopy call`() { // NOSONAR
        val formField = FormField(1)
        val result = subjectUnderTest.getFormFieldCopy(formField)
        assertEquals(formField, result)
    }

    @Test
    fun `verify markMessageAsRead call`() = runTest { // NOSONAR
        coVerify(exactly = 0) {
            subjectUnderTest.markMessageAsRead(
                any(),
                "1",
                "2",
                "3",
                "0",
                "Read",
                INBOX_COLLECTION
            )
        }
    }

    @Test
    fun `verify markMessageAsDeleted call`() = runTest { // NOSONAR
        coVerify(exactly = 0) {
            subjectUnderTest.markMessageAsDeleted(
                "1",
                "2",
                "3",
                "0",
                "Delete"
            )
        }
    }

    @Test
    fun `verify getRecipientUserName call`() = runTest {    //NOSONAR
        val users = mutableSetOf<User>().also {
            it.add(User(1))
            it.add(User(2))
        }
        val recipients = mutableListOf<Recipients>().also {
            it.add(Recipients(22323))
            it.add(Recipients(mailUsertrimble))
        }
        coEvery {
            messageFormRepo.getRecipientUserName(
                any(),
                any()
            )
        } returns users
        assertEquals(
            users,
            subjectUnderTest.getRecipientUserName(
                recipients,
                1
            )
        )
    }

    @Test
    fun `verify form template deserialization`() { // NOSONAR
        assertEquals(
            formTemplate,
            subjectUnderTest.getFormTemplateCopy(
                "{\"FormDef\":{\"cid\":10119,\"formid\":-1,\"name\":\"\",\"description\":\"\",\"formHash\":0,\"formClass\":-1,\"driverOriginate\":0,\"recipients\":{\"emailUser\":\"user1@trimble.com\"}},\"FormFields\":[],\"error\":\"\"}"
            )
        )
    }

    @Test
    fun `verify form template deserialization empty formTemplateSerialisedString`() { // NOSONAR
        assertEquals(
            FormTemplate(),
            subjectUnderTest.getFormTemplateCopy("")
        )
    }

    @Test
    fun `verify save form data`() = runTest {
        val saveFormData = MessageFormData(
            "testPath",
            FormResponse(),
            "123",
            "testRes",
            "testForm",
            2,
            1234L,
            true
        )
        val users = mutableSetOf<User>()
        users.add(User(uID = 1))
        users.add(User(uID = 2))
        users.add(User(uID = 3))

        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns CoroutineScope(Job())
        coEvery {
            messageFormRepo.getRecipientUserName(any(), any())
        } returns users

        coEvery { messageFormRepo.saveFormResponse(any(), any(),any(), any()) } returns true

        safeLaunch {
            subjectUnderTest.saveFormData(
                saveFormData
            )

            coVerify {
                messageFormRepo.saveFormResponse(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `verify isProcessingMultipleChoiceFieldRequired returns true and processes for multiple choice field`() {
        var processMultipleChoiceRan = false
        val formChoice1 = FormChoice(qnum = 1, choicenum = 1, value = "Yes", formid = 100)
        val formField = FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal, formid = 100, displayText = "FormField1")
        formField.formChoiceList = arrayListOf(formChoice1)
        formField.uiData = "Yes"

        val isSyncToQueue = false
        val processMultipleChoice: (FormChoice?) -> Unit = {
            processMultipleChoiceRan = true
            assertEquals("qnum matches", 1, it?.qnum)
        }

        val result = subjectUnderTest.isProcessingMultipleChoiceFieldRequired(formField, isSyncToQueue, processMultipleChoice)

        assertTrue(processMultipleChoiceRan, "Process Multiple Choice")
        assertTrue(result, "did Process?")
    }

    @Test
    fun `verify isProcessingMultipleChoiceFieldRequired returns false and does not process for multiple text field`() {
        var processMultipleChoiceRan = false
        val formChoice1 = FormChoice(qnum = 1, choicenum = 1, value = "Yes", formid = 100)
        val formField = FormField(qtype = FormFieldType.TEXT.ordinal, formid = 100, displayText = "FormField1")
        formField.formChoiceList = arrayListOf(formChoice1)
        formField.uiData = "Yes"

        val isSyncToQueue = false
        val processMultipleChoice: (FormChoice?) -> Unit = {
            processMultipleChoiceRan = true
            assertEquals("qnum matches", 1, it?.qnum)
        }

        val result = subjectUnderTest.isProcessingMultipleChoiceFieldRequired(formField, isSyncToQueue, processMultipleChoice)

        assertFalse("Do not process", processMultipleChoiceRan)
        assertFalse("Wasn't multiple choice", result)
    }

    @Test
    fun `make a recipients copy add one more to original and have no modifications on copy`() {
        val recipients = mutableMapOf<String, Any>(
            "01" to 1,
            "02" to "email@value.com"
        )
        val recipientsCopy = subjectUnderTest.makeRecipientsCopy(recipients)
        recipients["03"] = 3
        assertNotEquals(recipients, recipientsCopy)
    }

    @Test
    fun `make a recipients copy modified original and have no modifications on copy`() {
        val recipients = mutableMapOf<String, Any>(
            "01" to 1,
            "02" to "email@value.com"
        )
        val recipientsCopy = subjectUnderTest.makeRecipientsCopy(recipients)
        recipients["02"] = "email@value.io"
        assertNotEquals(recipients, recipientsCopy)
    }





    @Test
    fun `verify setDataFromDefaultValueOrUiFormResponsesValue for correct uiData`() {
        val dispatcherFormValuesMap = hashMapOf("1" to arrayListOf<String>().also {
            it.add("1")
            it.add("2")
            it.add("3")
            it.add("4")
            it.add("5")
        })
        val formField = FormField(qnum = 1)
        for (index in 1 until 6) {
            subjectUnderTest.setDataFromDefaultValueOrUiFormResponsesValue(
                formFieldValuesMap = dispatcherFormValuesMap,
                formField = formField,
                actualLoopCount = 5,
                currentLoopCount = index,
                caller = EMPTY_STRING,
                )
            assertEquals((6 - index).toString(), formField.uiData)
        }
    }

    @Test
    fun `verify setDataFromDefaultValueOrUiFormResponsesValue for true response from setDataFromDefaultValueOrUiFormResponsesValue`() {
        val dispatcherFormValuesMap = hashMapOf("1" to arrayListOf<String>().also {
            it.add("1")
            it.add("2")
            it.add("3")
            it.add("4")
            it.add("5")
        })
        val formField = FormField(qnum = 1)
        for (index in 1 until 6) {
            assertTrue(
                subjectUnderTest.setDataFromDefaultValueOrUiFormResponsesValue(
                    formFieldValuesMap = dispatcherFormValuesMap,
                    formField = formField,
                    actualLoopCount = 5,
                    currentLoopCount = index,
                    caller = EMPTY_STRING,
                )
            )
        }
    }

    @Test
    fun `verify setDataFromDefaultValueOrUiFormResponsesValue for empty dispatcherFormValuesMap`() {
        val dispatcherFormValuesMap = hashMapOf<String, ArrayList<String>>()
        val formField = FormField(qnum = 1)
        assertFalse(
            subjectUnderTest.setDataFromDefaultValueOrUiFormResponsesValue(
                formFieldValuesMap = dispatcherFormValuesMap,
                formField = formField,
                actualLoopCount = 5,
                currentLoopCount = 0,
                caller = EMPTY_STRING,
            )
        )
    }

    @Test
    fun `getForm returns expected form`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val driverMessageFormData = DriverMessageFormData("123",false,
            UIFormResponse(), INBOX_FORM_RESPONSE_TYPE,"1233",
            DispatcherFormValuesPath("5688","test","12345","1","1")
            , HashMap<String, ArrayList<String>>()
        ) // replace with your test data
        coEvery { subjectUnderTest.getForm(driverMessageFormData, false, false, false) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getForm(driverMessageFormData, false, false, false)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getForm returns expected form when isDriverInMessageReplyForm is true`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val driverMessageFormData = DriverMessageFormData("123",false,
            UIFormResponse(), INBOX_FORM_RESPONSE_TYPE,"1233",
            DispatcherFormValuesPath("5688","test","12345","1","1")
            , HashMap<String, ArrayList<String>>()
        ) // replace with your test data
        coEvery { subjectUnderTest.getForm(driverMessageFormData, true, false, false) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getForm(driverMessageFormData, true, false, false)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getForm returns expected form when isFormSaved is true`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val driverMessageFormData = DriverMessageFormData("123",false,
            UIFormResponse(), INBOX_FORM_RESPONSE_TYPE,"1233",
            DispatcherFormValuesPath("5688","test","12345","1","1")
            , HashMap<String, ArrayList<String>>()
        ) // replace with your test data
        coEvery { subjectUnderTest.getForm(driverMessageFormData, false, true, false) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getForm(driverMessageFormData, false, true, false)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getForm returns expected form when isReplayWithSame is true`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val driverMessageFormData = DriverMessageFormData("123",false,
            UIFormResponse(), INBOX_FORM_RESPONSE_TYPE,"1233",
            DispatcherFormValuesPath("5688","test","12345","1","1")
            , HashMap<String, ArrayList<String>>()
        ) // replace with your test data
        coEvery { subjectUnderTest.getForm(driverMessageFormData, false, false, true) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getForm(driverMessageFormData, false, false, true)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getFreeForm returns expected form`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val uiFormResponse = UIFormResponse() // replace with your test data
        coEvery { subjectUnderTest.getFreeForm(uiFormResponse) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getFreeForm(uiFormResponse)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getFreeForm returns expected form when UIFormResponse is empty`() = runTest {
        // Arrange
        val expectedForm = Form() // replace with your expected form
        val uiFormResponse = UIFormResponse() // empty UIFormResponse
        coEvery { subjectUnderTest.getFreeForm(uiFormResponse) } returns expectedForm

        // Act
        val actualForm = subjectUnderTest.getFreeForm(uiFormResponse)

        // Assert
        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `getFreeForm returns null when exception occurs`() = runTest {
        // Arrange
        val uiFormResponse = UIFormResponse() // replace with your test data
        coEvery { subjectUnderTest.getFreeForm(uiFormResponse) } throws Exception()

        // Act
        val actualForm = runCatching { subjectUnderTest.getFreeForm(uiFormResponse) }.getOrNull()

        // Assert
        assertNull(actualForm)
    }

    @Test
    fun `splitDataFromDispatchFormSavePath returns DispatcherFormValuesPath with empty fields when dispatchFormSavePath cannot be split into 5 parts`() {
        val dispatchFormSavePath = "123/456"
        val unCompletedDispatchFormPath = DispatchFormPath("stopName", 1, 1, 1, 1)
        val expectedDispatcherFormValuesPath = DispatcherFormValuesPath("", "", "", "", "")
        val actualDispatcherFormValuesPath = subjectUnderTest.splitDataFromDispatchFormSavePath(
            dispatchFormSavePath,
            unCompletedDispatchFormPath
        )
        assertEquals(expectedDispatcherFormValuesPath, actualDispatcherFormValuesPath)
    }

    @Test
    fun `splitDataFromDispatchFormSavePath returns DispatcherFormValuesPath with correct fields when dispatchFormSavePath can be split into 6 parts`() {
        val dispatchFormSavePath = "/123/456/789/9/1"
        val unCompletedDispatchFormPath = DispatchFormPath("stopName", 9, 1, 123, 1)

        val actualDispatcherFormValuesPath = subjectUnderTest.splitDataFromDispatchFormSavePath(
            dispatchFormSavePath,
            unCompletedDispatchFormPath
        )

        assertEquals("123", actualDispatcherFormValuesPath.cid)
        assertEquals("456", actualDispatcherFormValuesPath.vehicleId)
        assertEquals("789", actualDispatcherFormValuesPath.dispatchId)
        assertEquals("9", actualDispatcherFormValuesPath.stopId)
        assertEquals("1", actualDispatcherFormValuesPath.actionId)
    }

    @Test
    fun `processDispatcherFormValues returns empty map when formClass is greater than 0`() =
        runTest {
            // Arrange
            val formClass = 1
            val formList = arrayListOf<MessageFormField>()

            // Act
            val actualDispatcherFormValues =
                subjectUnderTest.processDispatcherFormValues(formClass, formList)

            // Assert
            assertTrue(actualDispatcherFormValues.isEmpty())
        }

    @Test
    fun `processDispatcherFormValues returns map with non-empty text fields when formClass is 0`() =
        runTest {
            // Arrange
            val formClass = 0
            val formList = arrayListOf<MessageFormField>().apply {
                this.add(
                    MessageFormField(
                        text = "NotEmptyText",
                        qNum = "1",
                        fieldType = "freeText",
                        fieldID = "100"
                    )
                )
                this.add(
                    MessageFormField(
                        text = "",
                        qNum = "2",
                        fieldType = "freeText",
                        fieldID = "101"
                    )
                )
            }
            val expectedDispatcherFormValues = hashMapOf(
                "1" to arrayListOf("NotEmptyText")
            )

            // Act
            val actualDispatcherFormValues =
                subjectUnderTest.processDispatcherFormValues(formClass, formList)

            // Assert
            assertEquals(expectedDispatcherFormValues, actualDispatcherFormValues)
        }

    @Test
    fun `processDispatcherFormValues ignores fields with text containing SKIPPED when formClass is 0`() =
        runTest {
            // Arrange
            val formClass = 0
            val formList = arrayListOf<MessageFormField>().apply {
                this.add(
                    MessageFormField(
                        text = "SKIPPED",
                        qNum = "1",
                        fieldType = "freeText",
                        fieldID = "100"
                    )
                )
                this.add(
                    MessageFormField(
                        text = "NotEmptyText",
                        qNum = "2",
                        fieldType = "freeText",
                        fieldID = "101"
                    )
                )
            }
            val expectedDispatcherFormValues = hashMapOf(
                "2" to arrayListOf("NotEmptyText")
            )

            // Act
            val actualDispatcherFormValues =
                subjectUnderTest.processDispatcherFormValues(formClass, formList)

            // Assert
            assertEquals(expectedDispatcherFormValues, actualDispatcherFormValues)
        }

    @Test
    fun `processDispatcherFormValues returns empty map when formClass is not 0 or greater than 0`() =
        runTest {
            // Arrange
            val formClass = -2
            val formList = arrayListOf<MessageFormField>()

            // Act
            val actualDispatcherFormValues =
                subjectUnderTest.processDispatcherFormValues(formClass, formList)

            // Assert
            assertTrue(actualDispatcherFormValues.isEmpty())
        }

    @Test
    fun `getFreeForm returns expected form template from input ui form response default values`() = runTest {
        // Arrange
        val expectedFormTemplate = FormTemplate(
            FormDef(
                cid = 10119,
                recipients = HashMap<String, String>().also {
                    it["emailUser"] = mailUsertrimble
                },
                name = "testForm",
                formid = 123,
                formClass = 1,
                formHash = 100,
            ), formFieldsList = arrayListOf<FormField>().apply {
                add(FormField(qnum = 1, formid = 123, qtype = 1))
                add(FormField(qnum = 2, formid = 123, qtype = 3))
            }
        )
        val expectedForm = Form(expectedFormTemplate, UIFormResponse(), hashMapOf())
        coEvery { appModuleCommunicator.getAppFlavor() } returns FLAVOR_DEV
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "2344"
        coEvery { messageFormRepo.getFreeForm(any(), any(), any()) } returns expectedFormTemplate

        val uiFormResponse = UIFormResponse(
            isSyncDataToQueue = false,
            formData = FormResponse(
                fieldData = arrayListOf<Any>().apply {
                    add(MessageFormField(qNum = "1", fieldType = "freeText", text = "Yes"))
                    add(MessageFormField(qNum = "2", fieldType = "freeText", text = "No"))
                },
                uniqueTemplateTag = 123,
                uniqueTemplateHash = 100,
                recipients = mutableListOf(
                    Recipients(1, "user1@trimble.com")
                )
            ),
        )

        // Act
        val actualForm: Form = subjectUnderTest.getFreeForm(uiFormResponse)

        // Assert
        assertEquals(expectedForm.formFieldValuesMap, actualForm.formFieldValuesMap)
    }

    @Test
    fun `verify showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection is getting executed and the CoroutineScope is getting cancelled on job completion`() = runTest {
        val tag = "Tag from EdvirFormActivity"

        coEvery { appModuleCommunicator.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection() } returns Unit

        subjectUnderTest.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)

        coVerify { appModuleCommunicator.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection() }

        verify(exactly = 1){
            subjectUnderTest.cancelEnqueuedNotificationScope()
        }
    }

    @Test
    fun `delete All TrashMessage method success`() = runTest {
        //Flavor DEV
        every { appModuleCommunicator.getAppFlavor() } returns FLAVOR_DEV
        subjectUnderTest.deleteAllTrashMessages("122", "2131", "token", "appchecktoken")
        coVerify {
            messageFormRepo.deleteAllMessageInTrash("122", "2131", BuildEnvironment.Dev, "token", "appchecktoken")
        }

        //Flavor QA
        every { appModuleCommunicator.getAppFlavor() } returns FLAVOR_QA
        subjectUnderTest.deleteAllTrashMessages("122", "2131", "token", "appchecktoken")
        coVerify {
            messageFormRepo.deleteAllMessageInTrash("122", "2131", BuildEnvironment.Qa, "token", "appchecktoken")
        }

        //Flavor Prod
        every { appModuleCommunicator.getAppFlavor() } returns FLAVOR_PROD
        subjectUnderTest.deleteAllTrashMessages("122", "2131", "token", "appchecktoken")
        coVerify {
            messageFormRepo.deleteAllMessageInTrash("122", "2131", BuildEnvironment.Prod, "token", "appchecktoken")
        }

    }

    @Test
    fun `deleteAllTrashMessages with Empty Values`() = runTest {
        var token = ""
        var appCheckToken = ""
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"

        //Empty token and appCheckToken
        subjectUnderTest.deleteAllTrashMessages("123", "123", token, appCheckToken)
        coVerify(exactly = 0) {
            messageFormRepo.deleteAllMessageInTrash("123", "123", BuildEnvironment.Dev, token, appCheckToken)
        }

        //Empty appCheckToken
        token = "token"
        subjectUnderTest.deleteAllTrashMessages("123", "123", token, appCheckToken)
        coVerify(exactly = 0) {
            messageFormRepo.deleteAllMessageInTrash("123", "123", BuildEnvironment.Dev, token, appCheckToken)
        }

        //Empty token
        token = ""
        appCheckToken = "appCheckToken"
        subjectUnderTest.deleteAllTrashMessages("123", "123", token, appCheckToken)
        coVerify(exactly = 0) {
            messageFormRepo.deleteAllMessageInTrash("123", "123", BuildEnvironment.Dev, token, appCheckToken)
        }
    }

    @Test
    fun `deleteTrashMessage method success`() = runTest {
        subjectUnderTest.deleteSelectedTrashMessage("122", "2131", "testtoken", "appchecktoken")
        coVerify {
            messageFormRepo.deleteSelectedMessageInTrash("122", "2131", "testtoken", "appchecktoken")
        }
    }

    @After
    fun after() {
        unloadKoinModules(koinModulesRequiredForTest)
        stopKoin()
        unmockkAll()
    }
}
