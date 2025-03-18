package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.gson.Gson
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.utils.IS_DELIVERED
import com.trimble.ttm.formlibrary.utils.MARK_DELIVERED
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class MessageFormRepoImplTest {

    private lateinit var SUT : MessageFormRepoImpl
    private val validCustomerId = "customerId"
    private val inValidCustomerId = ""
    private val validVehicleId = "vehicleId"
    private val validObcId = "obcId"
    private val validAsn = "asn"
    private val inValidAsn = "0"
    private val caller = "caller"
    private val validOperationType = "operationType"
    private val trashCollectionCallSource = "Trash"
    private val inboxCollectionCallSource = "Inbox"

    @RelaxedMockK
    private val mockCollectionReference = mockk<CollectionReference>()

    @RelaxedMockK
    private val mockDocumentReference = mockk<DocumentReference>()

    @Before
    fun setup(){
        SUT = spyk(MessageFormRepoImpl(mockk(), TestDispatcherProvider(), mockk()), recordPrivateCalls = true)
    }

    @Test
    fun `verify parseUser for new user addition`() {
        val oldUsers = mutableSetOf<User>().also {
            it.add(User(uID = 0))
        }
        val newUser = User(uID = 1)
        SUT.parseUser(Gson(), newUser, oldUsers)
        assertEquals(2, oldUsers.size)
    }

    @Test
    fun `verify parseUser for null user addition`() {
        val oldUsers = mutableSetOf<User>().also {
            it.add(User(uID = 0))
        }
        SUT.parseUser(Gson(), null, oldUsers)
        assertEquals(1, oldUsers.size)
    }

    @Test
    fun `verify markTrashMessageAsRead() is called when the callSource is Inbox`() = runTest {
            coEvery { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any()) } just runs
            coEvery { SUT.markTrashMessageAsRead(any(), any(), any(), any()) } just runs

            SUT.markMessageAsRead(caller, validCustomerId, validVehicleId, validObcId, validAsn, validOperationType, trashCollectionCallSource)

            coVerify(exactly = 1) { SUT.markTrashMessageAsRead(any(), any(), any(), any())  }
        }

    @Test
    fun `verify markInboxMessageAsRead() is called when the callSource is Trash`() = runTest {
        coEvery { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { SUT.markTrashMessageAsRead(any(), any(), any(), any()) }  returns Unit

        SUT.markMessageAsRead(caller, validCustomerId, validVehicleId, validObcId, validAsn, validOperationType, inboxCollectionCallSource)

        coVerify(exactly = 1) { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any())  }
    }

    @Test
    fun `verify markInboxMessageAsRead() or markTrashMessageAsRead() is not called when the customerId is empty`() = runTest {
        coEvery { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any()) } just runs
        coEvery { SUT.markTrashMessageAsRead(any(), any(), any(), any()) } just runs

        SUT.markMessageAsRead(caller, inValidCustomerId, validVehicleId, validObcId, validAsn, validOperationType, inboxCollectionCallSource)

        coVerify(exactly = 0) { SUT.markTrashMessageAsRead(any(), any(), any(), any())  }
        coVerify(exactly = 0) { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any())  }
    }

    @Test
    fun `verify markInboxMessageAsRead() or markTrashMessageAsRead() is not called when the asn is 0`() = runTest {
        coEvery { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { SUT.markTrashMessageAsRead(any(), any(), any(), any()) } returns Unit

        SUT.markMessageAsRead(caller, inValidCustomerId, validVehicleId, validObcId, inValidAsn, validOperationType, inboxCollectionCallSource)

        coVerify(exactly = 0) { SUT.markTrashMessageAsRead(any(), any(), any(), any())  }
        coVerify(exactly = 0) { SUT.markInboxMessageAsRead(any(), any(), any(), any(), any(), any())  }
    }

    @Test
    fun `verify markMessageAsReadAndSendInboxMessageConfirmation() calls sendInboxMessageConfirmation() and updateReadOrDelivered()`() = runTest {
        every { SUT["getInboxPathDocument"](validCustomerId, validVehicleId, validAsn) } returns mockDocumentReference
        every { SUT["getInboxPathCollection"](validCustomerId, validVehicleId) } returns mockCollectionReference
        every { mockDocumentReference.path } returns "mockDocumentPath"
        every { mockCollectionReference.path } returns "mockCollectionPath"
        every {
            SUT.sendInboxMessageConfirmation(any(), any(), any(), any(), any())
            SUT.updateReadOrDelivered(any(), any(), any(), any(), any(), any())
        }just runs

        SUT.markMessageAsReadAndSendInboxMessageConfirmation(caller, validCustomerId, validVehicleId, validObcId, validAsn, MARK_DELIVERED, hashMapOf(IS_DELIVERED to false))

        verify(exactly = 1) {
            SUT.sendInboxMessageConfirmation(any(), any(), any(), any(), any())
            SUT.updateReadOrDelivered(any(), any(), any(), any(), any(), any())
        }
    }

    @After
    fun after(){
        unmockkAll()
    }
}