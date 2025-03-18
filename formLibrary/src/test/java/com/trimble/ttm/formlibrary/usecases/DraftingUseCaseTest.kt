package com.trimble.ttm.formlibrary.usecases

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.usecase.DispatchFormUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.repo.DraftingRepoImpl
import com.trimble.ttm.formlibrary.repo.MessageFormRepoImpl
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftingUseCaseTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val _draftProcessFinished = MutableSharedFlow<Boolean>(0)

    private val draftProcessFinished: SharedFlow<Boolean> = _draftProcessFinished

    private val _initDraftProcessing = MutableSharedFlow<Boolean>(0)

    private val initDraftProcessing: SharedFlow<Boolean> = _initDraftProcessing
    @RelaxedMockK
    private lateinit var context : Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var SUT: DraftingUseCase
    private lateinit var draftingRepo: DraftingRepoImpl
    private lateinit var messageFormUseCase: MessageFormUseCase
    @MockK
    private lateinit var dispatchFormUseCase: DispatchFormUseCase
    private lateinit var messageFormRepoImpl: MessageFormRepoImpl
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private val testScope = TestScope()
    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var dispatcherFormValuesUseCase: DispatcherFormValuesUseCase
    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase
    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { context.filesDir } returns temporaryFolder.newFolder()
        formDataStoreManager = spyk(FormDataStoreManager(context))
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(any()) } returns mockk()
        val mockFirebaseFirestore = mockk<FirebaseFirestore>()
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockFirebaseFirestore
        messageFormRepoImpl= spyk(MessageFormRepoImpl(
            appModuleCommunicator, TestDispatcherProvider(), mockFirebaseFirestore
        ))
        messageFormUseCase = spyk(MessageFormUseCase(mockk(), dispatchFormUseCase, messageFormRepoImpl,
            formFieldDataUseCase = formFieldDataUseCase, dispatcherFormValuesUseCase = dispatcherFormValuesUseCase))
        every { context.applicationContext } returns context
        every { messageFormRepoImpl.getAppModuleCommunicator() } returns appModuleCommunicator
        draftingRepo = spyk(DraftingRepoImpl(formDataStoreManager, testScope))
        SUT= DraftingUseCase(
            messageFormUseCase,
            draftingRepo,
            appModuleCommunicator,
            DefaultDispatcherProvider(),
            dataStoreManager
        )
        coEvery {
            draftingRepo.draftProcessFinished
        } returns draftProcessFinished

        coEvery {
            draftingRepo.initDraftProcessing
        } returns initDraftProcessing

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
    }

    @Test
    fun `call makeDraft and verify setDraftProcessFinished is called with true value`() = runTest {
        coEvery {
            messageFormUseCase.saveFormData(any())
        } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns "5688"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12345"
        coEvery { dispatchFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(
            FormResponse()
        ) }
        coEvery { messageFormUseCase.mapImageUniqueIdentifier(any()) } just runs
        val formDataToSave = FormDataToSave(
            formTemplate = FormTemplate(),
            path = "",
            formId = "",
            typeOfResponse = "",
            formName = "",
            formClass = 0,
            cid = "",
            obcId = ""
        )
        SUT.makeDraft(
            formDataToSave, "test", true
        )
        advanceUntilIdle()
        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            draftingRepo.setDraftProcessFinished(true)
        }
    }

    @Test
    fun `call restoreDraftProcessFinished and verify restoreDraftProcessFinished is called`() = runTest {
        coEvery {
            draftingRepo.restoreDraftProcessFinished()
        } returns Unit
        SUT.restoreDraftProcessFinished()
        coVerify(exactly = 1) {
            draftingRepo.restoreDraftProcessFinished()
        }
    }

    @Test
    fun `call restoreInitDraftProcessing and verify restoreInitDraftProcessing is called`() = runTest {
        coEvery {
            draftingRepo.restoreInitDraftProcessing()
        } returns Unit
        SUT.restoreInitDraftProcessing()
        coVerify(exactly = 1) {
            draftingRepo.restoreInitDraftProcessing()
        }
    }

    @After
    fun after(){
        unmockkAll()
    }

}