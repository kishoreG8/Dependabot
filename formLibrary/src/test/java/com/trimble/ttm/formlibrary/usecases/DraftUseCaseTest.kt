package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.repo.DraftRepo
import com.trimble.ttm.formlibrary.utils.DRAFT_SCREEN_TIME
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DraftUseCaseTest {
    private lateinit var draftUseCase: DraftUseCase
    private lateinit var draftRepo: DraftRepo
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var remoteConfigGatekeeper: FeatureGatekeeper
    private lateinit var firebaseAnalyticEventRecorder : FirebaseAnalyticEventRecorder

    @Before
    fun setup() {
        MockKAnnotations.init(relaxed = true)
        draftRepo = mockk()
        appModuleCommunicator = mockk()
        remoteConfigGatekeeper = mockk()
        firebaseAnalyticEventRecorder = mockk()
        draftUseCase = DraftUseCase(draftRepo, appModuleCommunicator, remoteConfigGatekeeper, firebaseAnalyticEventRecorder)
    }

    @Test
    fun `verify getMessageListFlow call`() = runTest {    //NOSONAR
        val messages = mutableSetOf<MessageFormResponse>().also {
            it.add(MessageFormResponse(formName = "form1"))
            it.add(MessageFormResponse(formName = "form2"))
        }
        coEvery {
            draftUseCase.getMessageListFlow()
        } returns flow { emit(messages) }
        draftUseCase.getMessageListFlow().safeCollect(this.javaClass.name) {
            assertEquals(messages, it)
        }
    }

    @Test
    fun `verify deleteAllMessage - token available`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            draftRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        draftUseCase.deleteAllMessage("122", "2131", "testtoken", "testAppCheckToken")

        coVerify {
            draftRepo.deleteAllMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify deleteAllMessage - token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            draftRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        draftUseCase.deleteAllMessage("122", "2131", null, "")

        coVerify(exactly = 0) {
            draftRepo.deleteAllMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify deleteAllMessage - app check token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            draftRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        draftUseCase.deleteAllMessage("122", "2131", "7857465476568", "")

        coVerify(exactly = 0) {
            draftRepo.deleteAllMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify clearRegistration`() = kotlin.run {
        coEvery { draftRepo.detachListenerRegistration() } just runs

        draftUseCase.clearRegistration()
        coVerify { draftRepo.detachListenerRegistration() }
    }

    @Test
    fun `verify getMessageOfVehicle call`() = runTest {    //NOSONAR
        coEvery {
            draftUseCase.getMessageOfVehicle(any(), any(), any())
        } returns Unit
        coVerify(exactly = 0) {
            draftUseCase.getMessageOfVehicle("1", "2", false)
        }
    }

    @Test
    fun `verify resetPagination call`() {    //NOSONAR
        verify(exactly = 0) {
            draftUseCase.resetPagination()
        }
    }

    @Test
    fun `verify deleteMessage call`() = runTest {    //NOSONAR
        coEvery {
            draftUseCase.deleteMessage(any(), any(), any())
        } returns Unit
        coVerify(exactly = 0) {
            draftUseCase.deleteMessage(
                "1", "2", 0
            )
        }
    }

    @Test
    fun `check logScreenViewEvent gets called`(){
        every { firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any()) } just runs
        draftUseCase.logScreenViewEvent(DRAFT_SCREEN_TIME)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}