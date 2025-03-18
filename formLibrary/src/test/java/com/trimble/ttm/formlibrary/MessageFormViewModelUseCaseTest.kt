package com.trimble.ttm.formlibrary

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.repo.DraftRepoImpl
import com.trimble.ttm.formlibrary.repo.MessageFormRepoImpl
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MessageFormViewModelUseCaseTest {

    @RelaxedMockK
    private lateinit var application: Application
    private val appModuleCommunicator: AppModuleCommunicator = mockk(relaxed = true)
    private lateinit var formViewModel: MessageFormViewModel
    private var savedFieldData = ArrayList<Any>()
    private var uiResponse = UIFormResponse()
    private var formTemplate = FormTemplate()
    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase
    @RelaxedMockK
    private lateinit var dispatcherFormValuesUseCase: DispatcherFormValuesUseCase

    private lateinit var messageFormUseCase: MessageFormUseCase

    private val firebaseAnalyticEventRecorder : FirebaseAnalyticEventRecorder = mockk()
    private val draftUseCase = DraftUseCase(DraftRepoImpl(mockk()), appModuleCommunicator, mockk(), firebaseAnalyticEventRecorder)
    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var context: Context
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val deepLinkUseCase: DeepLinkUseCase = mockk()

    @ExperimentalCoroutinesApi
    @get:Rule
    val mainDispatcherRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = mockk()
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(any()) } returns mockk()
        val mockFirebaseFirestore = mockk<FirebaseFirestore>()
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockFirebaseFirestore
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        messageFormUseCase = MessageFormUseCase(mockk(), mockk(),
            MessageFormRepoImpl(appModuleCommunicator, TestDispatcherProvider(), mockFirebaseFirestore),mockk(),
            dispatcherFormValuesUseCase = mockk()
        )
        formViewModel = MessageFormViewModel(
            application,
            messageFormUseCase,
            draftUseCase,
            mockk(),
            mockk(),
            TestDispatcherProvider(),
            formDataStoreManager,
            deepLinkUseCase,
            formFieldDataUseCase
        )
        savedFieldData.add(
            LinkedHashMap<String, String>().let {
                it["edittext"] = "hello"
                it
            })
        uiResponse = UIFormResponse(true, FormResponse(fieldData = savedFieldData))
        formTemplate = FormTemplate(
            FormDef(cid = 10119, recipients = HashMap<String, String>().let {
                it["emailUser"] = "vignesh_elangovan@trimble.com"
                it
            })
        )
        appModuleCommunicator.doSetObcId( "12345")
    }

    @Test
    fun `verify data consistency for orientation change`() = runTest {    //NOSONAR
        formViewModel.saveEnteredUIDataDuringOrientationChange(uiResponse)
        Assert.assertEquals(uiResponse, formViewModel.getSavedUIDataDuringOrientationChange())
    }


    @Test
    fun `verify form template deserialization`()  {    //NOSONAR
        runTest {
            Assert.assertEquals(
                formTemplate,
                messageFormUseCase.getFormTemplateCopy("{\"FormDef\":{\"cid\":10119,\"formid\":-1,\"name\":\"\",\"description\":\"\",\"formHash\":0,\"formClass\":-1,\"driverOriginate\":0,\"recipients\":{\"emailUser\":\"vignesh_elangovan@trimble.com\"}},\"FormFields\":[],\"error\":\"\"}")
            )
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}