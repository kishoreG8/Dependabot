package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.repo.SentRepo
import com.trimble.ttm.formlibrary.utils.FLAVOR_DEV
import com.trimble.ttm.formlibrary.utils.FLAVOR_PROD
import com.trimble.ttm.formlibrary.utils.FLAVOR_QA
import com.trimble.ttm.formlibrary.utils.FLAVOR_STG
import com.trimble.ttm.formlibrary.utils.SENT_SCREEN_TIME
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

class SentUseCaseTest {
    private lateinit var sentUseCase: SentUseCase
    private lateinit var  sentRepo: SentRepo
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var firebaseAnalyticEventRecorder : FirebaseAnalyticEventRecorder

    @Before
    fun setup() {
        MockKAnnotations.init(relaxed = true)
        sentRepo = mockk()
        appModuleCommunicator = mockk()
        firebaseAnalyticEventRecorder = mockk()
        sentUseCase = SentUseCase(sentRepo, appModuleCommunicator, firebaseAnalyticEventRecorder)
    }

    @Test
    fun `verify getMessageListFlow call`() = runTest {    //NOSONAR
        val messages = mutableSetOf<MessageFormResponse>().also {
            it.add(MessageFormResponse(formName = "form1"))
            it.add(MessageFormResponse(formName = "form2"))
        }
        coEvery {
            sentRepo.getMessageListFlow()
        } returns flow { emit(messages) }
        sentUseCase.getMessageListFlow().safeCollect(this.javaClass.name) {
            assertEquals(messages, it)
        }

        coVerify {
            sentRepo.getMessageListFlow()
        }
    }

    @Test
    fun `verify getMessageOfVehicle call`() = runTest {    //NOSONAR
        coEvery {
            sentRepo.getMessages(any(), any(), any())
        } returns Unit

        sentUseCase.getMessageOfVehicle("123", "3242", true)
        coVerify(exactly = 1) {
            sentRepo.getMessages(any(), any(), any())
        }
    }

    @Test
    fun `verify resetPagination call`() {    //NOSONAR
        coEvery { sentRepo.resetPagination() } just runs
        sentUseCase.resetPagination()
        coVerify { sentRepo.resetPagination() }
    }

    @Test
    fun `verify deleteMessage call`() = runTest {    //NOSONAR
        coEvery {
            sentRepo.deleteMessage(any(), any(), any())
        } returns Unit

        sentUseCase.deleteMessage("12", "234", 2313123L)
        coVerify(exactly = 1) {
            sentRepo.deleteMessage(any(), any(), any())
        }
    }

    @Test
    fun `verify clearRegistration`() = runTest {
        coEvery { sentRepo.detachListenerRegistration() } just runs
        sentUseCase.clearRegistration()

        coVerify { sentRepo.detachListenerRegistration() }
    }

    @Test
    fun `verify deleteAllMessage - token available - dev flavor`() = runTest {
        deleteAllMessageHelper(FLAVOR_DEV)
    }

    @Test
    fun `verify deleteAllMessage - token available - qa flavor`() = runTest {
        deleteAllMessageHelper(FLAVOR_QA)
    }

    @Test
    fun `verify deleteAllMessage - token available - prod flavor`() = runTest {
        deleteAllMessageHelper(FLAVOR_PROD)
    }

    @Test
    fun `verify deleteAllMessage - token available - stg flavor`() = runTest {
        deleteAllMessageHelper(FLAVOR_STG)
    }

    private fun deleteAllMessageHelper(flavor: String) = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns flavor
        coEvery {
            sentRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        sentUseCase.deleteAllMessage("122", "2131", "testtoken", "appchecktoken")

        coVerify {
            sentRepo.deleteAllMessage("122", "2131", any(), "testtoken", "appchecktoken")
        }
    }

    @Test
    fun `verify deleteAllMessage - token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            sentRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        sentUseCase.deleteAllMessage("122", "2131", null, "")

        coVerify(exactly = 0) {
            sentRepo.deleteAllMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify deleteAllMessage - app check token Unavailable`() = runTest {
        every { appModuleCommunicator.getAppFlavor() } returns "Dev"
        coEvery {
            sentRepo.deleteAllMessage(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CollectionDeleteResponse(true, "test")
        sentUseCase.deleteAllMessage("122", "2131", "5475678687", "")

        coVerify(exactly = 0) {
            sentRepo.deleteAllMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify didLastMessageReached`() = runTest {

        coEvery { sentRepo.didLastItemReached() } returns flow { emit(true) }
        sentUseCase.didLastMessageReached()

        coVerify {
            sentRepo.didLastItemReached()
        }
    }

    @Test
    fun `check logScreenViewEvent gets called`(){
        every { firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any()) } just runs
        sentUseCase.logScreenViewEvent(SENT_SCREEN_TIME)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}