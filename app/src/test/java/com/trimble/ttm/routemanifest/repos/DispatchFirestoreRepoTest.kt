package com.trimble.ttm.routemanifest.repos

import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.ARRIVAL_LATITUDE
import com.trimble.ttm.routemanifest.repo.ARRIVAL_LONGITUDE
import com.trimble.ttm.routemanifest.repo.COMPLETED_TIME_KEY
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepoImpl
import com.trimble.ttm.routemanifest.repo.IS_MANUAL_ARRIVAL
import com.trimble.ttm.routemanifest.viewmodel.PAYLOAD
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DispatchFirestoreRepoTest {
    private val dataDoc = mutableMapOf<String, Any>()
    private val stopid = 0
    private val completedTime = "2021-08-13T08:30:21.504Z"
    private val dispId = "301901"
    private val appModuleCommunicator = mockk<AppModuleCommunicator>()
    private lateinit var dispatchListRepo : DispatchFirestoreRepoImpl

    @Before
    fun setUp(){
        dispatchListRepo = spyk(DispatchFirestoreRepoImpl(appModuleCommunicator), recordPrivateCalls = true)
    }
    @Test
    fun testProcessStopDetailForCompletedStop() {
        dataDoc["Payload"] = hashMapOf<String, Any>().also {
            it["Dispid"] = dispId
            it[COMPLETED_TIME_KEY] = completedTime
            it["Stopid"] = stopid
        }
        val stopDetail = StopDetail(dispid = dispId, completedTime = completedTime, stopid = stopid)
        assertEquals(stopDetail, dispatchListRepo.processStopDetail(dataDoc))
    }

    @Test
    fun testProcessStopDetailForUnCompletedStop() {
        dataDoc["Payload"] = hashMapOf<String, Any>().also {
            it["Dispid"] = dispId
            it["Stopid"] = stopid
        }
        val stopDetail = StopDetail(dispid = dispId, stopid = stopid)
        assertEquals(stopDetail, dispatchListRepo.processStopDetail(dataDoc))
    }

    @Test
    fun testProcessStopDetailForEmptyData() {
        dataDoc["Payload"] = hashMapOf<String, Any>()
        assertEquals(StopDetail(), dispatchListRepo.processStopDetail(dataDoc))
    }

    @Test
    fun `test active dispatch id match with incoming data dispatch id`() {
        runTest {
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispId
            assertEquals(true, dispatchListRepo.isIncomingDataIsOfActiveDispatch(dispId))
        }
    }

    @Test
    fun `test active dispatch id not matches with incoming data dispatch id`() {
        val incomingDataDispatchId = "1011"
        runTest {
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispId
            assertEquals(
                false,
                dispatchListRepo.isIncomingDataIsOfActiveDispatch(incomingDataDispatchId)
            )
        }
    }

    @Test
    fun `test active dispatch id empty it not matches with incoming data dispatch id`() {
        val incomingDataDispatchId = "1011"
        runTest {
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns ""
            assertEquals(
                true,
                dispatchListRepo.isIncomingDataIsOfActiveDispatch(incomingDataDispatchId)
            )
        }
    }

    @Test
    fun `processDispatchBlob returns DispatchBlob when valid data is provided`() {
        val timeStamp = Timestamp(1716377404, 649093000)
        val docData = mutableMapOf<String, Any>("cid" to 10119, "vehicleNumber" to "vehicle22", "blob" to "blob message 1", "createDate" to timeStamp, "appId" to 101, "hostId" to 201, "vid" to 1998)
        val documentId = "123"

        val expectedDispatchBlob = DispatchBlob(cid = 10119, vehicleNumber = "vehicle22", blobMessage = "blob message 1", appId = 101, hostId = 201, vid = 1998, createDate = timeStamp.toDate().toInstant()).also {
            it.id = documentId
        }

        val result = dispatchListRepo.processDispatchBlob(docData, documentId)

        assertEquals(expectedDispatchBlob, result)
    }

    @Test
    fun `processDispatchBlob returns DispatchBlob with default value when some field data not provided`() {
        val timeStamp = Timestamp(1716377404, 649093000)
        val docData = mutableMapOf<String, Any>("vehicleNumber" to "vehicle22", "blob" to "blob message 1", "appId" to 101, "hostId" to 201)
        val documentId = "123"

        val expectedDispatchBlob = DispatchBlob(cid = 10119, vehicleNumber = "vehicle22", blobMessage = "blob message 1", appId = 101, hostId = 201, createDate = timeStamp.toDate().toInstant()).also {
            it.id = documentId
        }

        val result = dispatchListRepo.processDispatchBlob(docData, documentId)

        assertEquals(0, result.cid)
        assertEquals(true, result.createDate.toString().isNotEmpty())
    }

    @Test
    fun `processDispatchBlob returns empty DispatchBlob when exception occurs`() {
        val docData = mutableMapOf<String, Any>("key1" to Exception(), "key2" to "value2")
        val documentId = "123"

        val result = dispatchListRepo.processDispatchBlob(docData, documentId)

        assertNotNull(result)
        assertEquals(result.cid, 0)
        assertEquals(result.blobMessage, "")
        assertEquals(result.vehicleNumber, "")
    }

    @Test
    fun `processDispatchBlob returns empty DispatchBlob when empty data is provided`() {
        val docData = mutableMapOf<String, Any>()
        val documentId = "123"

        val result = dispatchListRepo.processDispatchBlob(docData, documentId)

        assertNotNull(result)
        assertEquals(result.cid, 0)
        assertEquals(result.blobMessage, "")
        assertEquals(result.vehicleNumber, "")
    }

    @Test
    fun `processAction returns correct Action`() {
        // Arrange
        val payload = mutableMapOf<String, Any>("ActionType" to 1, "Actionid" to 123)
        val docData = mutableMapOf<String, Any>(PAYLOAD to payload)

        // Act
        val action = dispatchListRepo.processAction(docData)

        // Assert
        val expectedAction = Gson().fromJson(Gson().toJson(payload), Action::class.java)
        assertEquals(expectedAction, action)
    }

    @Test
    fun `process stop detail with manual arrival information populates stopdetail object as expected`() {
        dataDoc["Payload"] = hashMapOf<String, Any>().also {
            it["Dispid"] = dispId
            it["Stopid"] = stopid
        }
        dataDoc.also {
            it[IS_MANUAL_ARRIVAL] = true
            it[ARRIVAL_LATITUDE] = 80.25
            it[ARRIVAL_LONGITUDE] = 12.9876
        }
        val stopDetail = StopDetail(dispid = dispId, stopid = stopid).also {
            it.isManualArrival = true
            it.arrivalLatitude = 80.25
            it.arrivalLongitude = 12.9876
        }
        assertEquals(stopDetail, dispatchListRepo.processStopDetail(dataDoc))
    }

    @Test
    fun `process stop detail without manual arrival information populates stopdetail object with default values for manual arrival related fields`() {
        dataDoc["Payload"] = hashMapOf<String, Any>().also {
            it["Dispid"] = dispId
            it["Stopid"] = stopid
        }
        val stopDetail = StopDetail(dispid = dispId, stopid = stopid).also {
            it.isManualArrival = false
            it.arrivalLatitude = 0.0
            it.arrivalLongitude = 0.0
        }
        assertEquals(stopDetail, dispatchListRepo.processStopDetail(dataDoc))
    }


}