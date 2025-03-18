package com.trimble.ttm.commons.usecases

import android.content.Context
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.model.WorkflowEventData
import com.trimble.ttm.commons.model.WorkflowEventDataParameters
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class SendWorkflowEventsToAppUseCaseTests {

    @RelaxedMockK
    private lateinit var managedConfigurationRepo: ManagedConfigurationRepo

    private lateinit var context: Context
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()
        sendWorkflowEventsToAppUseCase = spyk(SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, TestScope()))
    }

    @Test
    fun `verify uniqueEventId for TRIP_START_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.TRIP_START_EVENT, "12345", "")
        assertEquals("TRIP_START_EVENT-12345", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for TRIP_END_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.TRIP_END_EVENT, "12345", "")
        assertEquals("TRIP_END_EVENT-12345", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for NEW_TRIP_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.NEW_TRIP_EVENT, "12345", "")
        assertEquals("NEW_TRIP_EVENT-12345", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for APPROACH_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.APPROACH_EVENT, "12345", "0")
        assertEquals("APPROACH_EVENT-12345-0", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for ARRIVE_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.ARRIVE_EVENT, "12345", "0")
        assertEquals("ARRIVE_EVENT-12345-0", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for DEPART_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.DEPART_EVENT, "12345", "0")
        assertEquals("DEPART_EVENT-12345-0", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for REMOVE_STOP_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.REMOVE_STOP_EVENT, "12345", "0")
        assertEquals("REMOVE_STOP_EVENT-12345-0", uniqueEventId)
    }

    @Test
    fun `verify uniqueEventId for ADD_STOP_EVENT`() {
        val uniqueEventId = sendWorkflowEventsToAppUseCase.generateUniqueEventId(WorkFlowEvents.ADD_STOP_EVENT, "12345", "0")
        assertEquals("ADD_STOP_EVENT-12345-0", uniqueEventId)
    }

    @Test
    fun `verify buildWorkflowEventData fun`() {
        System.currentTimeMillis().let {
            val actualWorkflowEventData = sendWorkflowEventsToAppUseCase.getWorkFlowEventDataToSend(WorkflowEventDataParameters("12345", "Trip1","0", "Stop1", WorkFlowEvents.REMOVE_STOP_EVENT, "AUTO", "", it))
            val expectedWorkflowEventData = WorkflowEventData("REMOVE_STOP_EVENT-12345-0", "12345", "Trip1","0", "Stop1", WorkFlowEvents.REMOVE_STOP_EVENT, "AUTO", "", it)
            assertEquals(expectedWorkflowEventData, actualWorkflowEventData)
        }
    }

    @Test
    fun `verify getAppPackageNamesForSendingWorkflowEvents fun returns package name of app for events communication`() {
        every { managedConfigurationRepo.getAppPackageForWorkflowEventsCommunicationFromManageConfiguration("") } returns "com.linde.mobile.obc.delivery.dev"
        val appPackageName = sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents("")
        assertEquals("com.linde.mobile.obc.delivery.dev", appPackageName)
    }

    @Test
    fun `verify sendWorkflowEvent fun when appName is null`() = runTest {
        sendWorkflowEventsToAppUseCase = spyk( SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, this))
        every { sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents("") } returns null
        sendWorkflowEventsToAppUseCase.sendWorkflowEvent(WorkflowEventDataParameters("", "", "","",WorkFlowEvents.TRIP_START_EVENT, "", "",0),"")
        verify(exactly = 0) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any())
        }
    }

    @Test
    fun `verify sendWorkflowEvent fun when dispatch id of workflowEventData is empty`() = runTest {
        sendWorkflowEventsToAppUseCase = spyk( SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, this))
        every { sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents("") } returns "com.linde.mobile.obc.delivery.dev"
        every { sendWorkflowEventsToAppUseCase.getWorkFlowEventDataToSend(any()) } returns WorkflowEventData("123456","", "Trip1","0", "Stop1", WorkFlowEvents.TRIP_START_EVENT,"MANUAL","",0)
        sendWorkflowEventsToAppUseCase.sendWorkflowEvent(WorkflowEventDataParameters("", "", "","",WorkFlowEvents.TRIP_START_EVENT, "","",0),"")
        verify(exactly = 0) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any())
        }
    }

    @Test
    fun `verify workflow event is sent to third party app when appName and dispatch id is present`() = runTest {
        val workflowEventData = WorkflowEventData("123456","12345", "Trip1","0", "Stop1", WorkFlowEvents.TRIP_START_EVENT,"MANUAL", "",0)
        sendWorkflowEventsToAppUseCase = spyk( SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, this))
        every { sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents("") } returns "com.linde.mobile.obc.delivery.dev"
        every { sendWorkflowEventsToAppUseCase.getWorkFlowEventDataToSend(any()) } returns workflowEventData
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any()) } just runs
        every { sendWorkflowEventsToAppUseCase.buildBundleForThirdPartyApp(workflowEventData) } returns mockk()
        sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
            WorkflowEventDataParameters("12345",
                "Trip1",
                "0",
                "Stop1",
                WorkFlowEvents.TRIP_START_EVENT,
                "MANUAL",
                "",
                0),
            ""
        )
        advanceUntilIdle()
        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.buildBundleForThirdPartyApp(any())
        }
        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any())
        }
    }

    @Test
    fun `verify diaptchBlob event is sent to third party app when blob message and event name is present`() = runTest {
        val dispatchBlob = DispatchBlob(cid = 10119, vehicleNumber = "10119", blobMessage = "blobMessage")
        val eventName = WorkFlowEvents.DISPATCH_BLOB_EVENT
        val timeStamp = System.currentTimeMillis()
        val caller = "testCaller"

        sendWorkflowEventsToAppUseCase = spyk( SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, this))

        every { sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents(caller) } returns "com.test.app"
        every { sendWorkflowEventsToAppUseCase.getWorkFlowEventDataToSend(any()) } returns WorkflowEventData("DISPATCH_BLOB_EVENT-${Random.nextInt()}", "", "", "", "",eventName,"", dispatchBlob.blobMessage, timeStamp)
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any()) } just runs
        every { sendWorkflowEventsToAppUseCase.buildBundleForThirdPartyApp(any()) } returns mockk()

        sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(dispatchBlob, eventName, timeStamp, caller)

        advanceUntilIdle()

        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.buildBundleForThirdPartyApp(any())
        }
        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any())
        }
    }
    @Test
    fun `verify diaptchBlob event is not sent to third party when appPackageName is null`() = runTest {
        val dispatchBlob = DispatchBlob(cid = 10119, vehicleNumber = "10119", blobMessage = "blobMessage")
        val eventName = WorkFlowEvents.DISPATCH_BLOB_EVENT
        val timeStamp = System.currentTimeMillis()
        val caller = "testCaller"

        sendWorkflowEventsToAppUseCase = spyk( SendWorkflowEventsToAppUseCase(context, managedConfigurationRepo, this))
        every { sendWorkflowEventsToAppUseCase.getAppPackageNamesForSendingWorkflowEvents(caller) } returns null

        sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(dispatchBlob, eventName, timeStamp, caller)

        advanceUntilIdle()

        verify(exactly = 0) {
            sendWorkflowEventsToAppUseCase.buildBundleForThirdPartyApp(any())
        }
        verify(exactly = 0) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEventDataToThirdPartyApp(any(), any(), any())
        }
    }

    @Test
    fun `verify getPolygonalOptOutFromManageConfiguration fun returns true`() {
        every { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration("") } returns true
        val appPackageName = sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig("")
        assertEquals(true, appPackageName)
    }

    @Test
    fun `verify getPolygonalOptOutFromManageConfiguration fun returns false`() {
        every { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration("") } returns false
        val appPackageName = sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig("")
        assertEquals(false, appPackageName)
    }

    @After
    fun after() {
        unmockkAll()
    }
}