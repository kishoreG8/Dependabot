package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.trimble.ttm.backbone.api.data.eld.CurrentUser
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.EDVIRFormData
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.model.InspectionType
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION_LOGIN
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION_LOGOUT
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.formlibrary.utils.callOnCleared
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date
import kotlin.test.assertEquals

class EDVIRFormViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val DRIVERID = "driverId"

    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var edvirFormUC: EDVIRFormUseCase
    @MockK
    private lateinit var messageFormUseCase: MessageFormUseCase
    @MockK
    private lateinit var authenticateUseCase: AuthenticateUseCase
    @MockK
    private lateinit var updateInspectionInformationUseCase: UpdateInspectionInformationUseCase
    private var dispatcherProvider: DispatcherProvider = TestDispatcherProvider()
    private val testDispatcher = TestCoroutineScheduler()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var edvirFormViewModel: EDVIRFormViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(DateUtil)
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { authenticateUseCase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { edvirFormUC.setCrashReportIdentifier() } just runs
        edvirFormViewModel = spyk( EDVIRFormViewModel(
            eDVIRFormUseCase = edvirFormUC,
            authenticateUseCase = authenticateUseCase,
            updateInspectionInformationUseCase = updateInspectionInformationUseCase,
            messageFormUseCase = messageFormUseCase,
            formDataStoreManager = formDataStoreManager,
            appModuleCommunicator = appModuleCommunicator,
            ioDispatcher = dispatcherProvider.io(),
            application = application
        ))
        mockkStatic(android.util.Log::class)
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123442"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
    }

    @Test
    fun `verify fetch form to be rendered - is free form`() = runTest(testDispatcher) {    //NOSONAR

        coEvery { edvirFormUC.getFreeForm(any(), any(),any(), any()) } returns Form()

        edvirFormViewModel.fetchFormToBeRendered("", FormDef(formid = 1213, formClass = 1), true,UIFormResponse(), true)

        coVerify(exactly = 1) {
            edvirFormUC.getFreeForm(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify fetch form to be rendered - is form`() = runTest(testDispatcher) {    //NOSONAR

        coEvery { edvirFormUC.getForm(any(), any(), any(), any(), any()) } returns Form()

        edvirFormViewModel.fetchFormToBeRendered("", FormDef(formid = 1213, formClass = 0), true, UIFormResponse(), true)

        coVerify(exactly = 1) {
            edvirFormUC.getForm(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify setMandatoryInspection`() = runTest(testDispatcher) {
        coEvery {
            formDataStoreManager.setValue(
                FormDataStoreManager.IS_MANDATORY_INSPECTION,
                any()
            )
        } just runs

        edvirFormViewModel.setMandatoryInspection(true)

        coVerify {
            formDataStoreManager.setValue(FormDataStoreManager.IS_MANDATORY_INSPECTION, any())
        }
    }

    @Test
    fun `verify saveEDVIRFormData - cid is empty`() = runTest(testDispatcher) {

        edvirFormViewModel.saveEDVIRFormData(
            EDVIRFormData(
                FormTemplate(),
                "", "321", "asdf", 1, 1, "Pre", true
            ), Date()
        )
        coVerify(exactly = 0) {
            edvirFormUC.saveEDVIRFormResponse(any())
        }
    }

    @Test
    fun `verify getEDVIRFormDataResponse - cid empty`() = runTest(testDispatcher) {

        assertTrue(
            edvirFormViewModel.getEDVIRFormDataResponse(
                "",
                "1234",
                "1231"
            ).formData.dsn == 0L
        )
    }

    @Test
    fun `verify getEDVIRFormDataResponse - obc id empty`() = runTest(testDispatcher) {
        assertTrue(
            edvirFormViewModel.getEDVIRFormDataResponse(
                "123",
                "",
                "1231"
            ).formData.dsn == 0L
        )
    }

    @Test
    fun `verify getEDVIRFormDataResponse - throws exception`() = runTest(testDispatcher) {
        coEvery {
            edvirFormUC.getEDVIRFormDataResponse(
                any(),
                any(),
                any()
            )
        } throws Exception()

        assertTrue(
            edvirFormViewModel.getEDVIRFormDataResponse(
                "123",
                "1321",
                "1231"
            ).formData.dsn == 0L
        )
    }

    @Test
    fun `verify getEDVIRFormDataResponse`() = runTest(testDispatcher) {

        coEvery {
            edvirFormUC.getEDVIRFormDataResponse(
                any(),
                any(),
                any()
            )
        } returns UIFormResponse(true, FormResponse(dsn = 1234))

        assertTrue(
            edvirFormViewModel.getEDVIRFormDataResponse(
                "123",
                "1321",
                "1231"
            ).formData.dsn == 1234L
        )

    }

    @Test
    fun `verify saveEDVIRFormData - obc id is empty`() = runTest(testDispatcher) {
        edvirFormViewModel.saveEDVIRFormData(
            EDVIRFormData(
                FormTemplate(),
                "123", "", "asdf", 1, 1, "Pre", true
            ), Date()
        )
        coVerify(exactly = 0) {
            edvirFormUC.saveEDVIRFormResponse(any())
        }
    }

    @Test
    fun `verify saveEDVIRFormData - current utc format time is empty`() = runTest(testDispatcher) {
        every { DateUtil.getUTCFormattedDate(any()) } returns ""
        edvirFormViewModel.saveEDVIRFormData(
            EDVIRFormData(
                FormTemplate(),
                "123", "12312", "asdf", 1, 1, "Pre", true
            ), Date()
        )
        coVerify(exactly = 0) {
            edvirFormUC.saveEDVIRFormResponse(any())
        }
    }

    @Test
    fun `verify saveEDVIRFormData`() = runTest(testDispatcher) {
        every { DateUtil.getUTCFormattedDate(any()) } returns "fasfdsa"
        coEvery { appModuleCommunicator.doGetObcId() } returns "123"
        coEvery { messageFormUseCase.addFieldDataInFormResponse(any(), any(), any()) } returns flow { emit(FormResponse()) }
        edvirFormViewModel.saveEDVIRFormData(
            EDVIRFormData(
                FormTemplate(),
                "123", "12312", "asdf", 1, 1, "Pre", true
            ), Date()
        )
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            edvirFormUC.saveEDVIRFormResponse(any())
        }
    }

    @Test
    fun `verify getFormIdForInspection - throws exception`() = runTest(testDispatcher) {
        coEvery {
            edvirFormUC.getEDVIREnabledSetting(
                any(),
                any()
            )
        } throws Exception()

        val form = edvirFormViewModel.getFormIdForInspection(
            "123",
            "1232",
            "pre",
            UIFormResponse()
        )
        assertTrue(
            form.formid == -1
        )

        coVerify(exactly = 0) {
            edvirFormUC.getEDVIRInspectionSetting(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getFormIdForInspection - cid is empty`() = runTest(testDispatcher) {
        getFormForInspectionHelper("", "123", "pre")
    }

    @Test
    fun `verify getFormIdForInspection - obc id is empty`() = runTest(testDispatcher) {
        getFormForInspectionHelper("123", "", "pre")
    }

    @Test
    fun `verify getFormIdForInspection - inspection type is empty`() = runTest(testDispatcher) {
        getFormForInspectionHelper("213", "123", "")
    }

    private fun getFormForInspectionHelper(cid: String, obcid: String, insType: String) =
        runTest(testDispatcher) {
            assertEquals(
                edvirFormViewModel.getFormIdForInspection(
                    cid,
                    obcid,
                    insType,
                    UIFormResponse()
                ).formid, -1
            )

        }


    @Test
    fun `verify getFormIdForInspection - PreInspection - Normal Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithNormalFormHelper("PreInspection")
    }

    @Test
    fun `verify getFormIdForInspection - PostInspection - Normal Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithNormalFormHelper("PostInspection")
    }

    @Test
    fun `verify getFormIdForInspection - InterInspection - Normal Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithNormalFormHelper("InterInspection")
    }

    @Test
    fun `verify getFormIdForInspection - DotInspection - Normal Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithNormalFormHelper("DotInspection")
    }

    private fun getFormIdForInspectionWithNormalFormHelper(insType: String) = runTest(testDispatcher) {
        coEvery {
            edvirFormUC.getEDVIREnabledSetting(
                any(),
                any()
            )
        } returns EDVIRPayload(intValue = 1)
        coEvery {
            edvirFormUC.getEDVIRInspectionSetting(
                any(),
                any(),
                any()
            )
        } returns EDVIRPayload(intValue = 1)

        val form = edvirFormViewModel.getFormIdForInspection(
            "123",
            "1232",
            insType,
            UIFormResponse()
        )
        assertTrue(
            form.formid == 1
        )

        coVerify(exactly = 1) {
            edvirFormUC.getEDVIREnabledSetting(
                any(),
                any()
            )
            edvirFormUC.getEDVIRInspectionSetting(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getFormIdForInspection - PreInspection - Free Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithFreeFormHelper("PreInspection")
    }

    @Test
    fun `verify getFormIdForInspection - PostInspection - Free Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithFreeFormHelper("PostInspection")
    }

    @Test
    fun `verify getFormIdForInspection - InterInspection - Free Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithFreeFormHelper("InterInspection")
    }

    @Test
    fun `verify getFormIdForInspection - DotInspection - Free Form Returned`() = runTest(testDispatcher) {
        getFormIdForInspectionWithFreeFormHelper("DotInspection")
    }

    private fun getFormIdForInspectionWithFreeFormHelper(insType: String) =
        runTest(testDispatcher) {
            coEvery {
                edvirFormUC.getEDVIREnabledSetting(
                    any(),
                    any()
                )
            } returns EDVIRPayload(intValue = 11)
            coEvery {
                messageFormUseCase.getFreeForm(any())
            } returns Form(FormTemplate(FormDef(formid = 1234)))

            coEvery {
                edvirFormViewModel.getFormIdForInspection(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns (FormDef(formid = 1234))

            val form = edvirFormViewModel.getFormIdForInspection(
                "123",
                "1232",
                insType,
                UIFormResponse()
            )
            assertTrue(
                form.formid == 1234
            )

        }

    @Test
    fun `verify invalid backbone customer id`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        edvirFormViewModel.hasValidBackboneData { hasData, customerId ->
            assertFalse(hasData)
            assertFalse(customerId.isNotEmpty())
        }

    }

    @Test
    fun `verify valid backbone customer id`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        edvirFormViewModel.hasValidBackboneData { hasData, customerId ->
            assertTrue(hasData)
            assertTrue(customerId.isNotEmpty())
        }
    }

    @Test
    fun `verify setDockMode`() {    //NOSONAR
        coVerify(exactly = 0) {
            edvirFormViewModel.setDockMode(Bundle())
        }
    }

    @Test
    fun `verify releaseDockMode`() {    //NOSONAR
        coVerify(exactly = 0) {
            edvirFormViewModel.releaseDockMode()
        }
    }

    @Test
    fun `verify fetchAndRegisterFcmDeviceSpecificToken`() {    //NOSONAR
        coVerify(exactly = 0) {
            edvirFormViewModel.fetchAndRegisterFcmDeviceSpecificToken()
        }
    }

    @Test
    fun `verify startForegroundService`() {    //NOSONAR
        coVerify(exactly = 0) {
            edvirFormViewModel.startForegroundService()
        }
    }

    @Test
    fun `verify updateInspectionInformation when inspectionType is PreInspection`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.updatePreTripInspectionRequire(any()) } just runs
        edvirFormViewModel.updateInspectionInformation(InspectionType.PreInspection.name)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updatePreTripInspectionRequire(any())
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.updatePostTripInspectionRequire(any())
        }
    }

    @Test
    fun `verify updateInspectionInformation when inspectionType is PostInspection`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.updatePostTripInspectionRequire(any()) } just runs
        edvirFormViewModel.updateInspectionInformation(InspectionType.PostInspection.name)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updatePostTripInspectionRequire(any())
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.updatePreTripInspectionRequire(any())
        }
    }

    @Test
    fun `verify updateInspectionInformation when inspectionType is InterInspection`() = runTest(testDispatcher) {
        edvirFormViewModel.updateInspectionInformation(InspectionType.InterInspection.name)
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.updatePreTripInspectionRequire(any())
            updateInspectionInformationUseCase.updatePostTripInspectionRequire(any())
        }
    }

    @Test
    fun `verify updateInspectionInformation when inspectionType is DotInspection`() = runTest(testDispatcher) {
        edvirFormViewModel.updateInspectionInformation(InspectionType.DotInspection.name)
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.updatePreTripInspectionRequire(any())
            updateInspectionInformationUseCase.updatePostTripInspectionRequire(any())
        }
    }

    @Test
    fun `verify updatePreviousAnnotationInformation when inspectionType is PreInspection`() =
        runTest(testDispatcher) {
            coEvery { updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(any()) } just runs
            edvirFormViewModel.updatePreviousAnnotationInformation(
                InspectionType.PreInspection.name,
                "Annotation"
            )
            coVerify(exactly = 1) {
                updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(any())
            }
            coVerify(exactly = 0) {
                updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(any())
            }
        }

    @Test
    fun `verify updatePreviousAnnotationInformation when inspectionType is PostInspection`() =
        runTest(testDispatcher) {
            coEvery { updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(any()) } just runs
            edvirFormViewModel.updatePreviousAnnotationInformation(
                InspectionType.PostInspection.name,
                "Annotation"
            )
            coVerify(exactly = 1) {
                updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(any())
            }
            coVerify(exactly = 0) {
                updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(any())
            }
        }

    @Test
    fun `verify updatePreviousAnnotationInformation when inspectionType is InterInspection`() =
        runTest(testDispatcher) {
            edvirFormViewModel.updatePreviousAnnotationInformation(
                InspectionType.InterInspection.name,
                "Annotation"
            )
            coVerify(exactly = 0) {
                updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(any())
                updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(any())
            }
        }

    @Test
    fun `verify updatePreviousAnnotationInformation when inspectionType is DotInspection`() =
        runTest(testDispatcher) {
            edvirFormViewModel.updatePreviousAnnotationInformation(
                InspectionType.DotInspection.name,
                "Annotation"
            )
            coVerify(exactly = 0) {
                updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(any())
                updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(any())
            }
        }

    @Test
    fun `verify isInspectionRequired when inspectionType is PreInspection and it is mandatory`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPreTripAnnotation() } returns ""
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.isPreTripInspectionRequired() } returns true
        assertTrue(edvirFormViewModel.isInspectionRequired(InspectionType.PreInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is PreInspection and it is not mandatory`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.isPreTripInspectionRequired() } returns false
        assertFalse(edvirFormViewModel.isInspectionRequired(InspectionType.PreInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is PreInspection and it is filled already`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPreTripAnnotation() } returns "Annotation"
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.isPreTripInspectionRequired() } returns true
        assertFalse(edvirFormViewModel.isInspectionRequired(InspectionType.PreInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is PostInspection and it is mandatory`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPostTripAnnotation() } returns ""
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.isPostTripInspectionRequired() } returns true
        assertTrue(edvirFormViewModel.isInspectionRequired(InspectionType.PostInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is PostInspection and it is not mandatory`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.isPostTripInspectionRequired() } returns false
        assertFalse(edvirFormViewModel.isInspectionRequired(InspectionType.PostInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is PostInspection and it is filled already`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPostTripAnnotation() } returns "Annotation"
        coEvery { edvirFormUC.getCurrentDrivers() } returns setOf("Driver1")
        coEvery { updateInspectionInformationUseCase.setLastSignedInDriversCount(any()) } just runs
        coEvery { updateInspectionInformationUseCase.isPostTripInspectionRequired() } returns true
        assertFalse(edvirFormViewModel.isInspectionRequired(InspectionType.PostInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is InterInspection`() = runTest(testDispatcher) {
        assertTrue(edvirFormViewModel.isInspectionRequired(InspectionType.InterInspection.name))
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify isInspectionRequired when inspectionType is DotInspection`() = runTest(testDispatcher) {
        assertTrue(edvirFormViewModel.isInspectionRequired(InspectionType.DotInspection.name))
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.setLastSignedInDriversCount(any())
            updateInspectionInformationUseCase.isPreTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
            updateInspectionInformationUseCase.isPostTripInspectionRequired()
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is PostInspection and LastLoggedInDriverCount is 1`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getLastSignedInDriversCount() } returns 1
        coEvery { updateInspectionInformationUseCase.updateInspectionRequire(any()) } just runs
        coEvery { updateInspectionInformationUseCase.getPreviousPostTripAnnotation() } returns "Annotation"
        coEvery { updateInspectionInformationUseCase.clearPreviousAnnotations() } just runs
        edvirFormViewModel.inspectionCompleted(InspectionType.PostInspection.name, true)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updateInspectionRequire(any())
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is PostInspection and LastLoggedInDriverCount is 3`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getLastSignedInDriversCount() } returns 3
        coEvery { updateInspectionInformationUseCase.updateInspectionRequire(any()) } just runs
        edvirFormViewModel.inspectionCompleted(InspectionType.PostInspection.name, true)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is PostInspection, LastLoggedInDriverCount is 1 and isFormMandatory false`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getLastSignedInDriversCount() } returns 1
        coEvery { updateInspectionInformationUseCase.updateInspectionRequire(any()) } just runs
        edvirFormViewModel.inspectionCompleted(InspectionType.PostInspection.name, false)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is PostInspection, LastLoggedInDriverCount is 1 and getPreviousAnnotation is empty`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getLastSignedInDriversCount() } returns 1
        coEvery { updateInspectionInformationUseCase.updateInspectionRequire(any()) } just runs
        coEvery { updateInspectionInformationUseCase.getPreviousPostTripAnnotation() } returns ""
        edvirFormViewModel.inspectionCompleted(InspectionType.PostInspection.name, true)
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is PreInspection`() = runTest(testDispatcher) {
        edvirFormViewModel.inspectionCompleted(InspectionType.PreInspection.name, true)
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getLastSignedInDriversCount()
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is InterInspection`() = runTest(testDispatcher) {
        edvirFormViewModel.inspectionCompleted(InspectionType.InterInspection.name, false)
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getLastSignedInDriversCount()
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
    }

    @Test
    fun `verify inspectionCompleted call when inspectionType is DotInspection`() = runTest(testDispatcher) {
        edvirFormViewModel.inspectionCompleted(InspectionType.DotInspection.name, false)
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getLastSignedInDriversCount()
            updateInspectionInformationUseCase.updateInspectionRequire(any())
        }
    }


    @Test
    fun `verify getPreviousAnnotation call when inspectionType is PreInspection`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPreTripAnnotation() } returns "Annotation"
        assertEquals("Annotation", edvirFormViewModel.getPreviousAnnotation(InspectionType.PreInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify getPreviousAnnotation call when inspectionType is PostInspection`() = runTest(testDispatcher) {
        coEvery { updateInspectionInformationUseCase.getPreviousPostTripAnnotation() } returns "Annotation"
        assertEquals("Annotation", edvirFormViewModel.getPreviousAnnotation(InspectionType.PostInspection.name))
        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
        }
        coVerify(exactly = 0) {
            updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
        }
    }

    @Test
    fun `verify getPreviousAnnotation call when inspectionType is InterInspection`() = runTest(testDispatcher) {
        assertEquals(EMPTY_STRING, edvirFormViewModel.getPreviousAnnotation(InspectionType.InterInspection.name))
    }

    @Test
    fun `verify getPreviousAnnotation call when inspectionType is DotInspection`() = runTest(testDispatcher) {
        assertEquals(EMPTY_STRING, edvirFormViewModel.getPreviousAnnotation(InspectionType.DotInspection.name))
    }


    @Test
    fun `verify onClear call`() =    //NOSONAR
        edvirFormViewModel.callOnCleared()

    @Test
    fun `get PreInspection String with a String Login Action in getProperTypeByDriverAction`() {
        val result = edvirFormViewModel.getProperTypeByDriverAction(DRIVER_ACTION_LOGIN)
        assertEquals(result, InspectionType.PreInspection.name)
    }

    @Test
    fun `get PostInspection String with a String Logout Action in getProperTypeByDriverAction`() {
        val result = edvirFormViewModel.getProperTypeByDriverAction(DRIVER_ACTION_LOGOUT)
        assertEquals(result, InspectionType.PostInspection.name)
    }

    @Test
    fun `get the same String with a String Random in getProperTypeByDriverAction`() {
        val result = edvirFormViewModel.getProperTypeByDriverAction("asdasdasda")
        assertEquals(result, "asdasdasda")
    }

    @Test
    fun `get driverId from getCurrentDriverId`() {
        every {
            appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[CurrentUser]?.data
        } returns DRIVERID
        val result = edvirFormViewModel.getCurrentDriverId()
        assertEquals(result, DRIVERID)
    }

    @Test
    fun `get driverId from getCurrentDriverId and got an empty string during nullPointerException`() {
        every {
            appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[CurrentUser]?.data
        } throws NullPointerException()
        val result = edvirFormViewModel.getCurrentDriverId()
        assert(result.isEmpty())
    }

    @Test
    fun `get driverName from getCurrentDriverName`() {
        //with this can avoid reflection problems during testing
        val userName = mockk<UserName> {
            every { firstName } returns "firstName"
            every { lastName } returns "lastName"
            every { middleName } returns "middleName"
            every { title } returns "title"
            every { userId } returns "userId"

        }
        every {
            appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[UserName]?.data
        } returns mapOf(DRIVERID to userName)
        val result = edvirFormViewModel.getCurrentDriverName(DRIVERID)
        assertEquals(result, "firstName lastName")
    }

    @Test
    fun `get driverName from getCurrentDriverName and got an empty string during nullPointerException`() {
        every {
            appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[UserName]!!.data
        } throws NullPointerException()
        val result = edvirFormViewModel.getCurrentDriverName(DRIVERID)
        assert(result.isEmpty())
    }


    @After
    fun after() {
        unmockkAll()
    }

}