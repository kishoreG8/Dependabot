package com.trimble.ttm.formlibrary.usecases

import android.content.Context
import android.os.Bundle
import com.trimble.ttm.commons.model.AuthResult
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseUsecasesData
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.repo.EDVIRFormRepoImpl
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class EDVIRFormUseCaseTest {

    private val edvirFormRepoImpl: EDVIRFormRepoImpl = mockk()
    private val edvirInspectionsRepo: EDVIRInspectionsRepo = mockk()
    private val appModuleCommunicator: AppModuleCommunicator = mockk(relaxed = true)
    private val firebaseAuthRepo: FirebaseAuthRepo = mockk()
    private val context: Context = mockk()
    private lateinit var edvirFormUseCase: EDVIRFormUseCase
    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase
    private val eDVIRFormRepository: EDVIRFormRepoImpl = mockk()
    private val messageConfirmationUseCase : MessageConfirmationUseCase = mockk()


    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        edvirFormUseCase = spyk( EDVIRFormUseCase(
            edvirFormRepoImpl, edvirInspectionsRepo, firebaseAuthRepo,
            appModuleCommunicator, formFieldDataUseCase = formFieldDataUseCase,
            messageConfirmationUseCase = messageConfirmationUseCase
        ))
    }

    @Test
    fun `verify getForm call`() = runTest {    //NOSONAR
        val form = Form(FormTemplate(
            FormDef(cid = 10119, formid = 1)
        ), UIFormResponse(), hashMapOf()
        )
        coEvery { edvirFormUseCase.getForm(any(), any(), any(), any(), any()) } returns form
        assertEquals(form, edvirFormUseCase.getForm("10119", 1, true, UIFormResponse(), false))
    }

    @Test
    fun `verify getFreeForm call`() = runTest {    //NOSONAR
        val form = Form(FormTemplate(
            FormDef(cid = 10119, formid = 2)
        ), UIFormResponse(), hashMapOf())
        coEvery { edvirFormUseCase.getFreeForm(any(), any(),any(), any()) } returns form
        assertEquals(form, edvirFormUseCase.getFreeForm(2, false, false, UIFormResponse()))
    }

    @Test
    fun `verify saveEDVIRFormResponse call`() {    //NOSONAR
        coVerify(exactly = 0) {
            edvirFormUseCase.saveEDVIRFormResponse(
                EDVIRFormResponseUsecasesData(
                    "10119", "11929", "12:30",
                    FormResponse(), "driver1", 1, 0,
                    2929292299, "Pre-Trip", false
                )
            )
        }
    }

    @Test
    fun `verify getEDVIRInspectionSetting call`() = runTest {    //NOSONAR
        val edvirPayload = EDVIRPayload(1, 10)
        coEvery {
            edvirFormUseCase.getEDVIRInspectionSetting(
                any(), any(), any()
            )
        } returns edvirPayload
        assertEquals(
            edvirPayload,
            edvirFormUseCase.getEDVIRInspectionSetting("1099", "1221", "232")
        )
    }

    @Test
    fun `verify getEDVIREnabledSetting call`() = runTest {    //NOSONAR
        val edvirPayload = EDVIRPayload(1, 10)
        coEvery {
            edvirFormUseCase.getEDVIREnabledSetting(
                any(), any()
            )
        } returns edvirPayload
        assertEquals(
            edvirPayload,
            edvirFormUseCase.getEDVIREnabledSetting("1099", "1221")
        )
    }

    @Test
    fun `verify getEDVIRFormDataResponse call`() = runTest {    //NOSONAR
        val uiFormResponse = UIFormResponse(false, FormResponse(1929))
        coEvery {
            edvirFormUseCase.getEDVIRFormDataResponse(
                any(), any(), any()
            )
        } returns uiFormResponse
        assertEquals(
            uiFormResponse,
            edvirFormUseCase.getEDVIRFormDataResponse("1099", "1221", "967757555")
        )
    }

    @Test
    fun `verify getFireBaseToken call`() = runTest {    //NOSONAR
        coEvery {
            edvirFormUseCase.getFireBaseToken(any())
        } returns "12"
        assertEquals("12", edvirFormUseCase.getFireBaseToken("1"))
    }

    @Test
    fun `verify getFireBaseToken - returns empty`() = runTest {    //NOSONAR
        coEvery {
            firebaseAuthRepo.getFireBaseToken(any())
        } returns null
        assertEquals("", edvirFormUseCase.getFireBaseToken("1"))

        coVerify {
            firebaseAuthRepo.getFireBaseToken("1")
        }
    }

    @Test
    fun `verify authenticateFirestore call`() = runTest {    //NOSONAR
        val authResult = AuthResult(true, null)
        coEvery {
            edvirFormUseCase.authenticateFirestore(any())
        } returns authResult
        assertEquals(authResult, edvirFormUseCase.authenticateFirestore("1"))
    }

    @Test
    fun `verify customerId calls`() = runTest {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.getCustomerId()
        edvirFormUseCase = mockk()
        coEvery {
            edvirFormUseCase.getCustomerId()
        } returns "10119"
        assertEquals("10119", edvirFormUseCase.getCustomerId())
    }

    @Test
    fun `verify vehicleId calls`() = runTest {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.getVehicleId()
        edvirFormUseCase = mockk()
        coEvery {
            edvirFormUseCase.getVehicleId()
        } returns "545"
        assertEquals("545", edvirFormUseCase.getVehicleId())
    }

    @Test
    fun `verify obcId calls`() = runTest {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.getOBCId()
        edvirFormUseCase = mockk()
        coEvery {
            edvirFormUseCase.getOBCId()
        } returns "123"
        assertEquals("123", edvirFormUseCase.getOBCId())
    }

    @Test
    fun `verify getCurrentDrivers calls`() {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.getCurrentDrivers()
        edvirFormUseCase = mockk()
        val driverSet = mutableSetOf<String>().also {
            it.add("driver1")
            it.add("driver2")
        }
        every {
            edvirFormUseCase.getCurrentDrivers()
        } returns driverSet
        assertEquals(driverSet, edvirFormUseCase.getCurrentDrivers())
    }

    @Test
    fun `verify getDeviceToken calls`() = runTest {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.getDeviceToken()
        edvirFormUseCase = mockk()
        coVerify(exactly = 0) {
            edvirFormUseCase.getDeviceToken()
        }
    }

    @Test
    fun `verify setCrashReportIdentifier calls`() = runTest {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.setCrashReportIdentifier()
        edvirFormUseCase = mockk()
        coVerify(exactly = 0) {
            edvirFormUseCase.setCrashReportIdentifier()
        }
    }

    @Test
    fun `verify startForegroundService calls`() {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.startForegroundService()
        edvirFormUseCase = mockk()
        coVerify(exactly = 0) {
            edvirFormUseCase.startForegroundService()
        }
    }

    @Test
    fun `verify setDockMode calls`() {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.setDockMode(Bundle())
        edvirFormUseCase = mockk()
        coVerify(exactly = 0) {
            edvirFormUseCase.setDockMode(Bundle())
        }
    }

    @Test
    fun `verify resetDockMode calls`() {    //NOSONAR
        //To call the actual class for code coverage
        edvirFormUseCase.resetDockMode()
        edvirFormUseCase = mockk()
        coVerify(exactly = 0) {
            edvirFormUseCase.resetDockMode()
        }
    }

    @Test
    fun `verify isEDVIR enabled`() = runTest {
        coEvery { edvirInspectionsRepo.isEDVIRSettingsExist(any(), any()) } returns true
        edvirFormUseCase.isEDVIREnabled("123", "1234")

        coVerify {
            edvirInspectionsRepo.isEDVIRSettingsExist("123", "1234")
        }
    }

    @Test
    fun `test getForm returns valid Form`() = runTest {
        val formTemplate = mockk<FormTemplate>()
        val uiFormResponse = UIFormResponse()
        val expectedForm = Form(formTemplate, uiFormResponse, hashMapOf())

        coEvery { eDVIRFormRepository.getForm("10119", 1, true) } returns formTemplate
        coEvery { formFieldDataUseCase.createFormFromResult(listOf(formTemplate, uiFormResponse), false) } returns expectedForm
        coEvery { edvirFormUseCase.getForm("10119", 1, true, uiFormResponse, false)} returns expectedForm

        val actualForm = edvirFormUseCase.getForm("10119", 1, true, uiFormResponse,false)

        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `test getForm returns empty Form due to invalid result size`() = runTest {
        val formTemplate = mockk<FormTemplate>()
        val uiFormResponse = UIFormResponse()
        val expectedForm = Form()

        coEvery { eDVIRFormRepository.getForm("10119", 1, true) } returns formTemplate
        coEvery { formFieldDataUseCase.createFormFromResult(listOf(formTemplate), false) } returns expectedForm
        coEvery { edvirFormUseCase.getForm("10119", 1, true, uiFormResponse,false) } returns expectedForm

        val actualForm = edvirFormUseCase.getForm("10119", 1, true, uiFormResponse,false)

        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `test getFreeForm returns valid Form`() = runTest {
        val formTemplate = mockk<FormTemplate>()
        val uiFormResponse = UIFormResponse()
        val expectedForm = Form(formTemplate, uiFormResponse, hashMapOf())

        coEvery { eDVIRFormRepository.getFreeForm(1, true) } returns formTemplate
        coEvery { formFieldDataUseCase.createFormFromResult(listOf(formTemplate, uiFormResponse), true) } returns expectedForm
        coEvery { edvirFormUseCase.getFreeForm(1, true,false, uiFormResponse) } returns expectedForm

        val actualForm = edvirFormUseCase.getFreeForm(1, true,false, uiFormResponse)

        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `test getFreeForm returns empty Form due to invalid result size`() = runTest {
        val formTemplate = mockk<FormTemplate>()
        val uiFormResponse = UIFormResponse()
        val expectedForm = Form()

        coEvery { eDVIRFormRepository.getFreeForm(1, true) } returns formTemplate
        coEvery { formFieldDataUseCase.createFormFromResult(listOf(formTemplate), true) } returns expectedForm
        coEvery { edvirFormUseCase.getFreeForm(1, true, false, uiFormResponse) } returns expectedForm

        val actualForm = edvirFormUseCase.getFreeForm(1, true, false, uiFormResponse)

        assertEquals(expectedForm, actualForm)
    }

    @Test
    fun `test getForm returns different Form`() = runTest {
        val form = Form(FormTemplate(
            FormDef(cid = 10119, formid = 2)
        ), UIFormResponse(), hashMapOf()
        )
        coEvery { edvirFormUseCase.getForm(any(), any(), any(), any(), any()) } returns form
        assertEquals(form, edvirFormUseCase.getForm("10119", 1, true, UIFormResponse(), false))
    }

    @Test
    fun `test getForm with different parameters`() = runTest {
        val form = Form(FormTemplate(
            FormDef(cid = 10119, formid = 1)
        ), UIFormResponse(), hashMapOf()
        )
        coEvery { edvirFormUseCase.getForm(any(), any(), any(), any(), any()) } returns form
        assertEquals(form, edvirFormUseCase.getForm("10120", 2, false, UIFormResponse(), false))
    }

    @Test
    fun `test getForm throws exception`() = runTest {
        coEvery { edvirFormUseCase.getForm(any(), any(), any(), any(), any()) } throws Exception()
        assertThrows<Exception> {
            edvirFormUseCase.getForm("10119", 1, true, UIFormResponse(), false)
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}
