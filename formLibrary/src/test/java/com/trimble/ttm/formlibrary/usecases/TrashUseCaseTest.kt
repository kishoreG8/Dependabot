package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.repo.TrashRepo
import com.trimble.ttm.formlibrary.utils.TRASH_SCREEN_TIME
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

class TrashUseCaseTest {
    private lateinit var trashUseCase: TrashUseCase
    private lateinit var trashRepo: TrashRepo
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    @Before
    fun setup() {
        MockKAnnotations.init()
        firebaseAnalyticEventRecorder = mockk()
        trashRepo = mockk()
        trashUseCase = TrashUseCase(trashRepo, firebaseAnalyticEventRecorder)
    }

    @Test
    fun `verify getMessageListFlow call`() = runTest {    //NOSONAR
        val messages = mutableSetOf<Message>().also {
            it.add(Message(userName = "user1"))
            it.add(Message(userName = "user2"))
        }
        coEvery {
            trashRepo.getMessageListFlow()
        } returns flow { emit(messages) }

        trashUseCase.getMessageListFlow().safeCollect(this.javaClass.name) {
            assertEquals(messages, it)
        }
        coVerify {
            trashRepo.getMessageListFlow()
        }
    }

    @Test
    fun `verify getMessageOfVehicle call`() = runTest {    //NOSONAR
        coEvery {
            trashRepo.getMessages(any(), any(), any())
        } returns Unit

        trashUseCase.getMessageOfVehicle("123", "3242", true)
        coVerify(exactly = 1) {
            trashRepo.getMessages(any(), any(), any())
        }
    }

    @Test
    fun `verify resetPagination call`() {    //NOSONAR
        coEvery { trashRepo.resetPagination() } just runs
        trashUseCase.resetPagination()
        coVerify { trashRepo.resetPagination() }
    }

    @Test
    fun `verify clearRegistration`() = runTest {
        coEvery { trashRepo.detachListenerRegistration() } just runs
        trashUseCase.clearRegistration()

        coVerify { trashRepo.detachListenerRegistration() }
    }

    @Test
    fun `check logScreenViewEvent gets called`(){
        every { firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any()) } just runs
        trashUseCase.logScreenViewEvent(TRASH_SCREEN_TIME)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any())
        }
    }

    @Test
    fun `deleteAllMessage calls deleteAllTrashMessage in TrashRepo`() = runTest {
        val mockedResponse = CollectionDeleteResponse(true, "Success")
        coEvery { trashRepo.deleteAllMessages(any(), any(), any(), any()) } returns mockedResponse
        trashUseCase.deleteAllMessages("123", "324", "token", "appCheckToken")
        coVerify {
            trashRepo.deleteAllMessages("123","324", "token", "appCheckToken" )
        }
    }

    @Test
    fun `deleteMessage calls deleteTrashMessage in TrashRepo`() = runTest {
        coEvery { trashRepo.deleteMessage(any(), any(), any(), any()) } just runs
        trashUseCase.deleteMessage("123", "324", "asn", "caller")
        coVerify {
            trashRepo.deleteMessage("123", "324", "asn", "caller")
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}