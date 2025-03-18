package com.trimble.ttm.routemanifest.usecases

import androidx.datastore.preferences.core.intPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.EncodedImageRefRepo
import com.trimble.ttm.commons.usecase.DispatchFormUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.FormsRepository
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.utils.DRAFT_KEY
import com.trimble.ttm.commons.utils.SENT_KEY
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormUseCaseTests {

    @MockK
    private lateinit var formRepository: FormsRepository

    @MockK
    private lateinit var formUseCase: FormUseCase

    @RelaxedMockK
    private lateinit var formFieldDataUseCase: FormFieldDataUseCase

    @RelaxedMockK
    private lateinit var dispatcherFormValuesUseCase: DispatcherFormValuesUseCase

    private lateinit var encodedImageRefUseCase: EncodedImageRefUseCase

    @RelaxedMockK
    private lateinit var encodedImageRefRepo: EncodedImageRefRepo

    @MockK
    private lateinit var dispatchFormUseCase: DispatchFormUseCase

    @MockK
    private lateinit var formDataStoreManager: FormDataStoreManager

    @MockK
    private lateinit var dataStoreManager: DataStoreManager

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var messageFormRepo: MessageFormRepo

    @RelaxedMockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    private val testScope = TestScope()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(Log)
        formDataStoreManager = spyk(FormDataStoreManager(mockk(relaxed = true)))
        dataStoreManager = spyk(DataStoreManager(mockk(relaxed = true)))
        encodedImageRefUseCase = EncodedImageRefUseCase(encodedImageRefRepo)
        formUseCase = FormUseCase(
            formRepository,
            encodedImageRefUseCase,
            dispatchFormUseCase,
            appModuleCommunicator,
            firebaseAnalyticEventRecorder,
            localDataSourceRepo,
            formFieldDataUseCase,
            fetchDispatchStopsAndActionsUseCase,
            dispatcherFormValuesUseCase,
            messageFormRepo
            )
        coEvery { appModuleCommunicator.doGetCid() } returns "5097"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "Swift"
    }

    @Test
    fun `return Form for Free form`() = runTest {

        coEvery { formRepository.getFreeForm(any()) } returns FormTemplate(FormDef(cid = 123))
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(any(), any(), any()) } returns Pair(UIFormResponse(isSyncDataToQueue = true), true)
        coEvery { formFieldDataUseCase.createFormFromResult(any(),any())} returns Form(FormTemplate(FormDef(cid = 123)),UIFormResponse(),
            hashMapOf())

        val form =
            formUseCase.getForm(FormData("1", "", "", "", "", "", "1234", isFreeForm = true),
                isActionResponseSentToServer = true, shouldFillUiResponse = true, isReplyWithSameForm = true )
        assertTrue(form.formTemplate.formDef.cid == 123)
    }

    @Test
    @Ignore
    fun `return Form for normal form`() = runTest {

        val list = arrayListOf(FormField(0), FormField(1), FormField(2))

        coEvery { formRepository.getForm(any(), any()) } returns FormTemplate(
            FormDef(cid = 234),
            list
        )
        coEvery { formUseCase.getSavedFormResponse(any(), any(), true) } returns UIFormResponse()
        coEvery {
            dispatcherFormValuesUseCase.getDispatcherFormValues(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns hashMapOf()
        coEvery { formFieldDataUseCase.createFormFromResult(any(),any())} returns Form(FormTemplate(FormDef(cid = 234),list),UIFormResponse(),
            hashMapOf())
        val form =
            formUseCase.getForm(FormData("1", "", "", "", "", "", "1234", isFreeForm = false),
                isActionResponseSentToServer = true, shouldFillUiResponse = true, isReplyWithSameForm = true, UIFormResponse())
        assertTrue(form.formTemplate.formDef.cid == 234)
        assertTrue(form.formTemplate.formFieldsList.count() == 3)
    }

    @Test
    fun `get recipients`() = runTest {

        coEvery { formRepository.getLatestFormRecipients(any(), any()) } returns arrayListOf(
            Recipients("pfmuser", "emailuser")
        )
        assertTrue(formUseCase.getLatestFormRecipients(1234, 2345).count() == 1)
    }

    @Test
    fun `get recipients and validate users`() = runTest {

        coEvery { formRepository.getLatestFormRecipients(any(), any()) } returns arrayListOf(
            Recipients("pfmuser", "emailuser")
        )
        val recipients = formUseCase.getLatestFormRecipients(1234, 2345)
        assertTrue(recipients[0].recipientEmailUser == "emailuser")
        assertTrue(recipients[0].recipientPfmUser == "pfmuser")
    }

    @Test
    fun `verify get form data`() = runTest {

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(any(), any(), any()) } returns Pair(UIFormResponse(isSyncDataToQueue = true), true)

        assertTrue(formUseCase.getFormData("", "", true).isSyncDataToQueue)
    }

    @Test
    fun `verify save form data`() = runTest {

        coEvery {
            dispatchFormUseCase.saveDispatchFormResponse(
                any(),
                any(),
                any()
            )
        } returns true

        formUseCase.saveFormData(
            "asd",  FormResponse(), formDataToSave = constructFormDataToSave() , "test"
        )

        coVerify(exactly = 1) {
            dispatchFormUseCase.saveDispatchFormResponse(
                any(),
                any(),
                any()
            )
        }
    }

    private fun constructFormDataToSave() = FormDataToSave(
        formTemplate = FormTemplate(),
        path = com.trimble.ttm.commons.utils.EMPTY_STRING,
        formId = com.trimble.ttm.commons.utils.EMPTY_STRING,
        typeOfResponse = com.trimble.ttm.commons.utils.EMPTY_STRING,
        formName = com.trimble.ttm.commons.utils.EMPTY_STRING,
        formClass = 0,
        cid = "123",
        obcId = com.trimble.ttm.commons.utils.EMPTY_STRING
    )

    @Test
    fun `get stop action`() = runTest {

        coEvery {
            formRepository.getActionForStop(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Action(actionType = 1)

        assertTrue(formUseCase.getStopAction("123", "123", "12343", "2", "1").actionType == 1)
    }

    @Test
    fun `get free form`() = runTest {

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(any(), any(), any()) } returns Pair(UIFormResponse(isSyncDataToQueue = true), true)
        coEvery { formRepository.getFreeForm(any()) } returns FormTemplate(FormDef(cid = 423423))

        assertTrue(formUseCase.getFreeForm("", "1", 1234).formTemplate.formDef.cid == 423423)
    }

    @Test
    fun ` verify remove form from preference with more forms`() = runTest {
        val dfpath2 = DispatchFormPath("", 2, 1, 32423, 2)
        val dfpath3 = DispatchFormPath("", 3, 1, 32423, 2)

        mockkObject(UncompletedFormsUseCase)
        coEvery { UncompletedFormsUseCase.removeForm(any(), any()) } returns arrayListOf(
            dfpath2,
            dfpath3
        )
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                any()
            )
        } just runs

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns ""

        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = "formCountOfStops1"),
                ZERO
            )
        } returns 0

        coEvery {
            val stopId = 1
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                ZERO
            )
        } returns 0

        val formStack = formUseCase.removeFormFromPreference(1, dataStoreManager)

        coVerify(exactly = 1) {
                dataStoreManager.setValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    any()
                )
        }

        val formList: ArrayList<DispatchFormPath> = Gson().fromJson(
            formStack,
            object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
        )

        assertEquals(2, formList.size)
    }

    @Test
    fun `verify remove form from preference with one form`() = runTest {

        mockkObject(UncompletedFormsUseCase)
        coEvery { UncompletedFormsUseCase.removeForm(any(), any()) } returns arrayListOf()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                any()
            )
        } just runs

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns ""

        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = "formCountOfStops1"),
                ZERO
            )
        } returns 0

        coEvery {
            val stopId = 1
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                ZERO
            )
        } returns 0

        val formStack = formUseCase.removeFormFromPreference(1, dataStoreManager)

        coVerify(exactly = 1) {
            dataStoreManager.setValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        }

        assertEquals("", formStack)

    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndFormSubmissionEvent gets called when time duration is not empty and more than zero time duration`() {
        every {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        } just runs
        formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
            TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION, "0 hours 30 minutes 0 seconds"
        )
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        }
    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndFormSubmissionEvent gets called when time duration empty`() {
        every {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        } just runs
        formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
            TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION,
            EMPTY_STRING
        )
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        }
    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndFormSubmissionEvent gets called when time duration is zero time duration`() {
        every {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        } just runs
        formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
            TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION,
            "0 hours 0 minutes 0 seconds"
        )
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                any(),
                any()
            )
        }
    }

    @Test
    fun `Verify removeFormFromPreferencesForStop gets called when there is an invalid form`() =
        runTest {
            val stopId = 1
            val actionId = 1
            val stopDetail = listOf(StopDetail(stopid = stopId).also {
                it.Actions.add(Action(actionid = actionId))
            })

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns stopDetail

            coEvery {
                appModuleCommunicator.getCurrentWorkFlowId(any())
            } returns "123"

            coEvery {
                localDataSourceRepo.setToFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    any()
                )
            } just runs

            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    ZERO
                )
            } returns 1

            every {
                Log.d(any(), any())
            } returns Unit

            formUseCase.onInvalidForm("Test", stopId, actionId)

            coVerify(exactly = 1) {
                formUseCase.removeFormFromPreferencesForStop(stopId)
            }

            verify(exactly = 1) {
                Log.w("FormUseCase", any())
            }
        }

    @Test
    fun `Verify removeFormFromPreferencesForStop gets called when there is an invalid stopID`() =
        runTest {
            val stopId = 1
            val invalidStopId = 2
            val actionId = 1
            val stopDetail = listOf(StopDetail(stopid = stopId).also {
                it.Actions.add(Action(actionid = actionId))
            })

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns stopDetail

            coEvery {
                appModuleCommunicator.getCurrentWorkFlowId(any())
            } returns "123"

            coEvery {
                localDataSourceRepo.setToFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    any()
                )
            } just runs

            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    ZERO
                )
            } returns 1

            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$invalidStopId"),
                    ZERO
                )
            } returns 0

            formUseCase.onInvalidForm("Test", invalidStopId, actionId)

            coVerify(exactly = 1) {
                formUseCase.removeFormFromPreferencesForStop(invalidStopId)
            }

            verify(exactly = 1) {
                Log.w("FormUseCase", any())
            }
        }

    @Test
    fun `Verify removeFormFromPreferencesForStop gets called when there is an stopID with no arrive action`() =
        runTest {
            val stopId = 1
            val arrivingActionId = 0
            val arriveActionId = 1
            val stopDetail = listOf(StopDetail(stopid = stopId).also {
                it.Actions.add(Action(actionid = arrivingActionId))
            })

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns stopDetail

            coEvery {
                appModuleCommunicator.getCurrentWorkFlowId(any())
            } returns "123"

            coEvery {
                localDataSourceRepo.setToFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    any()
                )
            } just runs

            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                    ZERO
                )
            } returns 1

            formUseCase.onInvalidForm("Test", stopId, arriveActionId)

            coVerify(exactly = 1) {
                formUseCase.removeFormFromPreferencesForStop(stopId)
            }

            verify(exactly = 1) {
                Log.w(
                    "FormUseCase",
                    "Invalid form for 123 on stop: 1 with action 1 from caller: Test Current stop detail in datastore activeTripId:123, actionId:1 stopTripId:null, stopId: null, actionId: null, formID : null, replyFormId :null"
                )
            }
        }

    @Test
    fun `verify does stop has an uncompleted form`() = runTest {
        verifyUncompletedFormTestHelper(uncompletedFormCount = 1, expectedReturn = true, hasActiveDispatch = true)
    }

    @Test
    fun `verify does NOT stop has an uncompleted form`() = runTest {
        verifyUncompletedFormTestHelper(uncompletedFormCount = 0, expectedReturn = false, hasActiveDispatch = true)
    }

    @Test
    fun `verify does stop has an uncompleted form and does not have active Dispatch`() = runTest {
        verifyUncompletedFormTestHelper(uncompletedFormCount = 1, expectedReturn = false, hasActiveDispatch = false)
    }

    @Test
    fun `verify does NOT stop has an uncompleted form and does not have an active dispatch`() =
        runTest {
            verifyUncompletedFormTestHelper(uncompletedFormCount = 0, expectedReturn = false, hasActiveDispatch = false)
        }

    private fun verifyUncompletedFormTestHelper(
        uncompletedFormCount: Int,
        expectedReturn: Boolean,
        hasActiveDispatch: Boolean
    ) =
        runTest {
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$uncompletedFormCount"),
                    ZERO
                )
            } returns uncompletedFormCount

            coEvery {
                localDataSourceRepo.hasActiveDispatch()
            } returns hasActiveDispatch

            if (expectedReturn) {
                assertTrue(formUseCase.doesStopHasUncompletedForm(uncompletedFormCount))
            } else {
                assertFalse(formUseCase.doesStopHasUncompletedForm(uncompletedFormCount))
            }
        }

    @Test
    fun `getEncodedImage fetches image reference successfully`() = runTest {
        // Given
        val companyId = "10119"
        val vehicleId = "truck01"
        val imageID = "IMG_001"
        val caller = "TestCaller"
        val expectedImageRef = "sampleImageBase64String"
        coEvery { encodedImageRefRepo.fetchEncodedStringForReadOnlyThumbnailDisplay(companyId, vehicleId, imageID, caller, Dispatchers.IO) } returns expectedImageRef

        // When
        formUseCase.getEncodedImage(companyId, vehicleId, imageID, caller)

        // Then
        coVerify(exactly = 1) { encodedImageRefRepo.fetchEncodedStringForReadOnlyThumbnailDisplay(companyId, vehicleId, imageID, caller, Dispatchers.IO) }
    }

    @Test
    fun `getEncodedImage handles exception gracefully`() = runTest {
        // Given
        val companyId = "10119"
        val vehicleId = "truck02"
        val imageID = "IMG_002"
        val caller = "TestCaller"
        coEvery { encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(companyId, vehicleId, imageID, caller) } throws Exception()

        // When
        val result = runCatching { formUseCase.getEncodedImage(companyId, vehicleId, imageID, caller) }

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `isFormSaved returns true when form is present in sent responses`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(UIFormResponse(), true)
        val result = formUseCase.isFormSaved(path, actionId)

        assertTrue(result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
    }

    @Test
    fun `isFormSaved returns true when form is present in form responses and isSyncDataToQueue is true`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) } returns Pair(UIFormResponse(isSyncDataToQueue = true), true)
        val result = formUseCase.isFormSaved(path, actionId)

        assertTrue(result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
        coVerify(exactly = 1) { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) }
    }

    @Test
    fun `isFormSaved returns false when form is not present in sent or form responses`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) } returns Pair(UIFormResponse(), false)

        val result = formUseCase.isFormSaved(path, actionId)

        assertFalse(result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
        coVerify(exactly = 1) { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) }
    }

    @Test
    fun `getSavedFormResponse returns response from draft if present`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"
        val expectedResponse = UIFormResponse()

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) } returns Pair(expectedResponse, true)

        val result = formUseCase.getSavedFormResponse(path, actionId, true)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) }
    }

    @Test
    fun `getSavedFormResponse returns response from sent if present and not in draft`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"
        val expectedResponse = UIFormResponse()

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(expectedResponse, true)

        val result = formUseCase.getSavedFormResponse(path, actionId, true)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) }
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
    }

    @Test
    fun `getSavedFormResponse returns response from form responses if not in draft or sent`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"
        val expectedResponse = UIFormResponse()

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) } returns Pair(expectedResponse, true)

        val result = formUseCase.getSavedFormResponse(path, actionId, true)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) }
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
        coVerify(exactly = 1) { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) }
    }

    @Test
    fun `check getSavedFormResponse returns false if response not found in any source`() = runTest {
        val path = "testPath"
        val actionId = "testActionId"

        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) } returns Pair(UIFormResponse(), false)
        coEvery { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) } returns Pair(UIFormResponse(), false)

        val result = formUseCase.getSavedFormResponse(path, actionId, true)

        assertEquals(false, result.isSyncDataToQueue)

        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, DRAFT_KEY) }
        coVerify(exactly = 1) { formRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY) }
        coVerify(exactly = 1) { formRepository.getFromFormResponses(path, actionId, true, FORM_RESPONSES) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}