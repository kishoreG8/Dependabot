package com.trimble.ttm.routemanifest.repos

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.ext.getFromCache
import com.trimble.ttm.commons.utils.ext.getFromServer
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepoImpl
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertFalse


class TripMobileOriginatedEventsRepoImplTests {

    @RelaxedMockK
    private lateinit var firestore: FirebaseFirestore

    @RelaxedMockK
    private val documentReference = mockk<DocumentReference>()

    @RelaxedMockK
    private val collectionReference = mockk<CollectionReference>()

    @RelaxedMockK
    private val cacheDocumentSnapshot = mockk<DocumentSnapshot>()

    @RelaxedMockK
    private val serverDocumentSnapshot = mockk<DocumentSnapshot>()

    private lateinit var tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepoImpl

    val collectionName = "tripStart"
    val documentPath = "cust123/vehicles/truck456/dispatchEvent/42342789"

    private val testScope = TestScope()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        tripMobileOriginatedEventsRepo = TripMobileOriginatedEventsRepoImpl(firestore)
    }

    @Test
    fun `isTripActionResponseSaved should return true if document exists in cache`() =
        runTest {
            // Stub the necessary methods and instances
            coEvery { firestore.collection(collectionName) } returns collectionReference

            coEvery {
                firestore.collection(collectionName).document(documentPath)
            } returns documentReference

            coEvery { documentReference.getFromCache() } returns Tasks.forResult(
                cacheDocumentSnapshot
            )
            coEvery { cacheDocumentSnapshot.exists() } returns true

            // Act and Assert
            assertDoesNotThrow {
                tripMobileOriginatedEventsRepo.isTripActionResponseSaved(
                    collectionName,
                    documentPath
                )
            }

            // Verify that the relevant Firestore methods were called
            coVerify { documentReference.getFromCache() }
            coVerify(exactly = 0) { documentReference.getFromServer() }
        }

    @Test
    fun `isTripActionResponseSaved should return true if document exists in server`() =
        runTest {
            // Stub the necessary methods and instances
            coEvery { firestore.collection(collectionName) } returns collectionReference
            coEvery {
                firestore.collection(collectionName).document(documentPath)
            } returns documentReference

            coEvery { documentReference.getFromCache() } returns Tasks.forResult(
                cacheDocumentSnapshot
            )
            coEvery { cacheDocumentSnapshot.exists() } returns false

            coEvery { documentReference.getFromServer() } returns Tasks.forResult(
                serverDocumentSnapshot
            )
            coEvery { serverDocumentSnapshot.exists() } returns true

            // Act and Assert
            assertDoesNotThrow {
                tripMobileOriginatedEventsRepo.isTripActionResponseSaved(
                    collectionName,
                    documentPath
                )
            }

            // Verify that the relevant Firestore methods were called
            coVerify { documentReference.getFromCache() }
            coVerify { documentReference.getFromServer() }
        }


    // Late Notifications

    @Test
    fun testIsLateNotificationExistsWhenCacheIsEmpty() {
        lateNotificationHelper(false)
    }

    @Test
    fun testIsLateNotificationExistsWhenCacheIsNotEmpty() {
        lateNotificationHelper(true)
    }

    private fun lateNotificationHelper(isExistInCache: Boolean) = runTest {
        coEvery { firestore.collection("test") } returns collectionReference
        coEvery { firestore.document("1auto") } returns documentReference
        coEvery { firestore.collection(any()).document(any()) } returns documentReference

        coEvery { documentReference.getFromCache() } returns Tasks.forResult(
            cacheDocumentSnapshot
        )
        coEvery { cacheDocumentSnapshot.exists() } returns isExistInCache

        val result =
            tripMobileOriginatedEventsRepo.isLateNotificationExists(
                "test",
                "5097/vehicles/123456/stopEvents/1_0_timeout"
            )

        if (isExistInCache) {
            assertTrue(result)
        } else {
            assertFalse(result)
        }

    }

    @Test
    fun testSetLateNotificationDocumentData_Success() = runTest {
        val collectionName = "testCollection"
        val documentPath = "cust123/vehicles/truck456/stopEvents/42342789_0_timeout"
        val response = hashMapOf<String, Any>(
            "value" to "{\"reason\":\"timeout\",\"stopId\":2,\"dispId\":538131798}",
            "expireAt" to DateUtil.getExpireAtDateTimeForTTLInUTC().toString(),
            "dispatchId" to "123434"
        )
        val mockTask = mockk<Task<Void>>()
        coEvery { firestore.collection(collectionName).document(documentPath) } returns documentReference
        coEvery { documentReference.set(response) } returns mockTask
        coEvery { mockTask.addOnSuccessListener(any()) } returns mockTask
        coEvery { mockTask.addOnFailureListener(any()) } returns mockTask

        // Trigger the setDocumentData method with success listener
        tripMobileOriginatedEventsRepo.saveStopActionResponse(
            collectionName,
            documentPath,
            response, ArrivalReason(), "111111738822"
        )

        // Verify that the success listener was called
        coVerify { documentReference.set(response) }
    }


    @Test
    fun `saveStopActionResponse should verify reason, stopId, and dispatchId`() = runTest {
        // Arrange
        val collectionName = "testCollection"
        val documentPath = "cust123/vehicles/truck456/stopEvents/42342789"
        val response = hashMapOf<String, Any>(
            "value" to "{\"reason\":\"timeout\",\"stopId\":2,\"dispId\":538131798}"
        )

        val mockTask = mockk<Task<Void>>()
        coEvery { firestore.collection(collectionName).document(documentPath) } returns documentReference
        coEvery { documentReference.set(response) } returns mockTask
        coEvery { mockTask.addOnSuccessListener(any()) } returns mockTask
        coEvery { mockTask.addOnFailureListener(any()) } returns mockTask

        // Act
        tripMobileOriginatedEventsRepo.saveStopActionResponse(collectionName, documentPath, response,ArrivalReason(), "1336789927")

        // Assert
        coVerify { documentReference.set(response) }
        val extractedValues = tripMobileOriginatedEventsRepo.extractResponseValuesForLogging(response["value"] as String)
        assertEquals("timeout", extractedValues.first)
        assertEquals(2, extractedValues.third.toInt())
        assertEquals(538131798, extractedValues.second.toInt())
    }


    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }
}
