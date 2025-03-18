package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class FetchDispatchStopsAndActionsUseCaseTest {

    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "testVehicle"
        coEvery { appModuleCommunicator.doGetCid() } returns "testCid"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "testWorkflowId"
        fetchDispatchStopsAndActionsUseCase = FetchDispatchStopsAndActionsUseCase(dispatchFirestoreRepo)
    }

    @Test
    fun `verify getAllActiveStopsAndActions execution`() = runTest {
        val dispatchId = "1"
        val stop1Actions = listOf(
            Action(actionid = 0, stopid = 0, dispid = dispatchId),
            Action(actionid = 1, stopid = 0, dispid = dispatchId)
        )
        val stop2Actions = listOf(
            Action(actionid = 0, stopid = 1, dispid = dispatchId),
            Action(actionid = 1, stopid = 1, dispid = dispatchId)
        )
        val stops = listOf(
            StopDetail(stopid = 0, dispid = dispatchId),
            StopDetail(stopid = 1, dispid = dispatchId)
        )

        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(any(), any(), any())
        } returns stops

        coEvery {
            dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())
        } returnsMany listOf(stop1Actions, stop2Actions)

        val actualStops = fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test")
        assertTrue { actualStops.size == 2 }
        assertTrue { actualStops[0].Actions.size == 2 }
        assertTrue { actualStops[1].Actions.size == 2 }
    }

    @Test
    fun `verify getStopData execution`() = runTest {
        val dispatchId = "1"
        val stop1Actions = listOf(
            Action(actionid = 0, stopid = 0, dispid = dispatchId),
            Action(actionid = 1, stopid = 0, dispid = dispatchId)
        )
        val stop2Actions = listOf(
            Action(actionid = 0, stopid = 1, dispid = dispatchId),
            Action(actionid = 1, stopid = 1, dispid = dispatchId)
        )
        val stops = listOf(
            StopDetail(stopid = 0, dispid = dispatchId),
            StopDetail(stopid = 1, dispid = dispatchId)
        )

        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(any(), any(), any())
        } returns stops

        coEvery {
            dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())
        } returnsMany listOf(stop1Actions, stop2Actions)

        val resultingStop = fetchDispatchStopsAndActionsUseCase.getStopData(1) ?: StopDetail()
        assertTrue { resultingStop.stopid == 1 }
        assertTrue { resultingStop.Actions.size == 2 }
        assertTrue { resultingStop.Actions[1].stopid == 1 }
    }

    @Test
    fun `verify getDispatch returns correct Dispatch`() = runTest {
        val expectedDispatch = Dispatch()
        coEvery { dispatchFirestoreRepo.getDispatchPayload(any(), any(), any(), any(), any()) } returns expectedDispatch

        val actualDispatch = fetchDispatchStopsAndActionsUseCase.getDispatch("testCid", "testVehicle", "testDispatchId")

        assertTrue { actualDispatch == expectedDispatch }
        coVerify { dispatchFirestoreRepo.getDispatchPayload(any(), "testCid", "testVehicle", "testDispatchId", true) }
    }

    @After
    fun clear() {
        unmockkAll()
    }
}