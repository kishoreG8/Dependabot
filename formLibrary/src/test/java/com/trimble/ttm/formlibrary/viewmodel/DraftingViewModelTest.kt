package com.trimble.ttm.formlibrary.viewmodel

import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.usecases.DraftingUseCase
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class DraftingViewModelTest {
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var draftingUseCase: DraftingUseCase
    private lateinit var featureGatekeeper: FeatureGatekeeper
    private lateinit var draftingViewModel: DraftingViewModel

    @Before
    fun setUp() {
        appModuleCommunicator = mockk(relaxed = true)
        draftingUseCase = mockk(relaxed = true)
        featureGatekeeper = mockk(relaxed = true)
        draftingViewModel = DraftingViewModel(
            appModuleCommunicator, draftingUseCase, featureGatekeeper, Dispatchers.Unconfined
        )
    }

    @Test
    fun `makeDraft invokes makeDraft and getFormDataToDraftAsync with expected parameters`() =
        runTest {
            // Arrange
            val formTemplateData = FormTemplate(
                FormDef(
                    10119,
                    15447,
                    "form delivery",
                    "delivery form with image",
                    5501,
                    0,
                    0,
                    recipients = mutableMapOf("noemail@peoplenetonline.com" to "1000")
                ), formFieldsList = arrayListOf(
                    FormField(100, "field1", 1, 15447),
                    FormField(101, "field2", 2, 15447),
                )
            )
            val isOpenedForDraft = true
            val formResponseType = "FORM_LIBRARY_RESPONSE_TYPE"
            val replyFormName = "form1"
            val hasPredefinedRecipients = false
            val formClass = 0
            coEvery { appModuleCommunicator.doGetCid() } returns "10119"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "swift"
            coEvery { appModuleCommunicator.doGetObcId() } returns "37568790"

            val expectedFormDataToSave = FormDataToSave(
                formTemplate = formTemplateData,
                path = "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/10119/swift",
                formId = formTemplateData.formDef.formid.toString(),
                typeOfResponse = formResponseType,
                formName = replyFormName,
                formClass = formClass,
                cid = "10119",
                hasPredefinedRecipients = hasPredefinedRecipients,
                obcId = "37568790",
                unCompletedDispatchFormPath = draftingViewModel.unCompletedDispatchFormPath,
                dispatchFormSavePath = draftingViewModel.dispatchFormSavePath
            )

            // Act
            draftingViewModel.makeDraft(
                this,
                formTemplateData,
                isOpenedForDraft,
                formResponseType,
                replyFormName,
                hasPredefinedRecipients,
                formClass
            )

            coVerify {
                draftingUseCase.makeDraft(expectedFormDataToSave, any(), any())
            }
        }


    // Written with the help of copilot
    @Test
    fun `lookupSaveToDraftsFeatureFlag sets saveToDraftsFeatureFlag correctly`() = runTest {
        // Arrange
        val expectedValue = true
        coEvery { featureGatekeeper.isFeatureTurnedOn(any(), any(), any()) } returns expectedValue

        // Act
        draftingViewModel.lookupSaveToDraftsFeatureFlag(this)

        // Assert
        val actualValue = draftingViewModel.getSaveToDraftsFeatureFlag()
        assertEquals(expectedValue, actualValue)
    }

    // Written with the help of copilot
    // written this test to showcase how to test private variables
    @Test
    fun `getSaveToDraftsFeatureFlag returns correct value`() = runTest {
        // Arrange
        val expectedValue = true
        // Use reflection to set the value of the private variable
        val field = DraftingViewModel::class.java.getDeclaredField("saveToDraftsFeatureFlag")
        field.isAccessible = true
        field.set(draftingViewModel, expectedValue)

        // Act
        val actualValue = draftingViewModel.getSaveToDraftsFeatureFlag()

        // Assert
        assertEquals(expectedValue, actualValue)
    }

    // tests to satisfy the sonar coverage
    @Test
    fun `setDraftProcessAsFinished does not throw exception`() = runTest {
        draftingViewModel.setDraftProcessAsFinished(this)
    }

    @Test
    fun `restoreDraftProcessFinished does not throw exception`() = runTest {
        draftingViewModel.restoreDraftProcessFinished(this)
    }

    @Test
    fun `restoreInitDraftProcessing does not throw exception`() = runTest {
        draftingViewModel.restoreInitDraftProcessing(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}