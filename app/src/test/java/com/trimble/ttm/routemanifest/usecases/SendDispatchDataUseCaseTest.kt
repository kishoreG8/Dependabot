package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.repo.SendDispatchDataRepo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class SendDispatchDataUseCaseTest {

    @RelaxedMockK
    private lateinit var sendDispatchDataRepo: SendDispatchDataRepo

    @RelaxedMockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    @RelaxedMockK
    private lateinit var sendWorkflowEventsToAppUseCase : SendWorkflowEventsToAppUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        sendDispatchDataUseCase =
            SendDispatchDataUseCase(fetchDispatchStopsAndActionsUseCase, sendDispatchDataRepo, localDataSourceRepo, sendWorkflowEventsToAppUseCase)
    }

    @Test
    fun `send Dispatch Data To Draw Only Route`() = runTest {
        val stopList = listOf(
            StopDetail(
                stopid = 1,
                sequenced = 0
            ), StopDetail(stopid = 2, sequenced = 0)
        )
        coEvery { sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), any()) } just runs
        coEvery { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) } just runs
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopList

        sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(stopList)

        coVerify { sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), any()) }
        coVerify { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) }
        coVerify { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) }

    }

    @Test
    fun `sendDispatchDataOnMapServiceBoundAfterHostAppUpdateOrDeviceRestart not executed when there is no active dispatch`() =
        runTest {
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns false
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "1234"
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(StopDetail(stopid = 1))

            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "testCaller")

            verify(exactly = 0) {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            }
        }

    @Test
    fun `sendDispatchDataOnMapServiceBoundAfterHostAppUpdateOrDeviceRestart not executed when there is no active dispatch id`() =
        runTest {

            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns ""
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(StopDetail(stopid = 1))

            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "testCaller")

            verify(exactly = 0) {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            }
        }

    @Test
    fun `sendDispatchDataOnMapServiceBoundAfterHostAppUpdateOrDeviceRestart not executed when there is no cached stops`() =
        runTest {

            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "1234"
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns emptyList()

            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "testCaller")

            verify(exactly = 0) {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            }
        }

    @Test
    fun `sendDispatchDataOnMapServiceBoundAfterHostAppUpdateOrDeviceRestart executed when there FF stops`() =
        runTest {
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "1234"
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ), StopDetail(stopid = 2, sequenced = 0)
            )

            coEvery {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            } just runs

            coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 1)
            coEvery {
                sendDispatchDataRepo.sendDispatchStopsForRoute(
                    any(), any(), any()
                )
            } just runs

            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "testCaller")

            verify(exactly = 1) {
                sendDispatchDataRepo.sendDispatchStopsForRoute(
                    any(), any(), any()
                )
            }

            verify(exactly = 1) {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            }
        }
    @Test
    fun `verify drawRouteForCurrentStop with current stop available in cached stop` () = runTest {
        val stopList = listOf(
            StopDetail(
                stopid = 1,
                sequenced = 1
            ), StopDetail(stopid = 2, sequenced = 1)
        )
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
        coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 2)
        sendDispatchDataUseCase.drawRouteForCurrentStop(false)
        verify (exactly = 1) {
            sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), false)
        }
    }

    @Test
    fun `verify drawRouteForCurrentStop with current stop not available in cached stop` () = runTest {
        val stopList = listOf(
            StopDetail(
                stopid = 1,
                sequenced = 1
            ), StopDetail(stopid = 3, sequenced = 1)
        )
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
        coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 2)
        sendDispatchDataUseCase.drawRouteForCurrentStop(false)
        verify (exactly = 0) {
            sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), false)
        }
    }

    @Test
    fun `sendDispatchDataOnMapServiceBoundAfterHostAppUpdateOrDeviceRestart executed when there are sequential stops`() =
        runTest {
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "1234"

            val stopList = listOf(
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ), StopDetail(stopid = 2, sequenced = 1),
                StopDetail(stopid = 3, sequenced = 1)
            )

            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 1)

            coEvery {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            } just runs

            coEvery {
                sendDispatchDataRepo.sendDispatchStopsForRoute(
                    any(), any(), any()
                )
            } just runs

            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "testCaller")

            verify(exactly = 1) {
                sendDispatchDataRepo.sendDispatchStopsForRoute(
                    any(), any(), any()
                )
            }

            verify (exactly = 1) {
                sendDispatchDataRepo.sendDispatchStopsForGeofence(
                    any(), any()
                )
            }

            coVerify(exactly = stopList.size) { localDataSourceRepo.getCurrentStop() }
        }

    @Test
    fun `send Dispatch Complete Event`() {
        sendDispatchDataUseCase.sendDispatchCompleteEvent()
        verify(exactly = 1) { sendDispatchDataRepo.sendDispatchEventDispatchComplete() }
    }

    private fun drawRouteTestHelper(stopId: Int, verifyCount: Int) = runTest {
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(
            StopDetail(stopid = 0),
            StopDetail(stopid = 1)
        )
        coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = stopId)
        coEvery { sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), any()) } just runs
        coEvery { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) } just runs
        sendDispatchDataUseCase.drawRouteForCurrentStop(shouldRedrawCopilotRoute = false)

        verify(exactly = verifyCount) {
            sendDispatchDataRepo.sendDispatchStopsForRoute(any(), any(), any())
        }
    }

    @Test
    fun `draw Route For Current Stop when there are stops from cache`() = runTest {
        drawRouteTestHelper(0, 1)
    }

    @Test
    fun `draw Route For Current Stop when there are no stops from cache`() = runTest {
        drawRouteTestHelper(5, 0)
    }

    @Test
    fun `verify remove geofence for Arrived Action`(){
        val arrivedAction = Action(ActionTypes.ARRIVED.ordinal,0).apply {
            stopid = 1
        }

        every { sendDispatchDataRepo.sendRemoveGeoFenceEvent(any()) } just runs

        sendDispatchDataUseCase.sendRemoveGeoFenceEvent(arrivedAction)

        verify(exactly = 1) { sendDispatchDataRepo.sendRemoveGeoFenceEvent("Arrived1") }
        verify(exactly = 1) { sendDispatchDataRepo.sendRemoveGeoFenceEvent("Approach1") }

    }

    @Test
    fun `verify remove geofence for Approach Action`(){
        val approachAction = Action(ActionTypes.APPROACHING.ordinal,0).apply {
            stopid = 0
        }

        every { sendDispatchDataRepo.sendRemoveGeoFenceEvent(any()) } just runs

        sendDispatchDataUseCase.sendRemoveGeoFenceEvent(approachAction)

        verify(exactly = 1) { sendDispatchDataRepo.sendRemoveGeoFenceEvent("Approach0") }

    }

    @Test
    fun `verify remove geofence for Depart Action`(){
        val departAction = Action(ActionTypes.DEPARTED.ordinal,0).apply {
            stopid = 1
        }

        every { sendDispatchDataRepo.sendRemoveGeoFenceEvent(any()) } just runs

        sendDispatchDataUseCase.sendRemoveGeoFenceEvent(departAction)

        verify(exactly = 1) { sendDispatchDataRepo.sendRemoveGeoFenceEvent("Depart1") }

    }

    @Test
    fun `sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved sends geofence data when dispatch state is active`() = runTest {
        val stopDetailList = listOf(StopDetail(stopid = 1))
        every { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) } just runs

        sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(stopDetailList, DispatchActiveState.ACTIVE, "testCaller")

        verify(exactly = 1) { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) }
    }

    @Test
    fun `sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved sends geofence data when dispatch state is no trip active`() = runTest {
        val stopDetailList = listOf(StopDetail(stopid = 1))
        every { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) } just runs

        sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(stopDetailList, DispatchActiveState.NO_TRIP_ACTIVE, "testCaller")

        verify(exactly = 0) { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) }
    }

    @Test
    fun `sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved does not send geofence data when dispatch state is previewing`() = runTest {
        val stopDetailList = listOf(StopDetail(stopid = 1))
        every { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) } just runs

        sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(stopDetailList, DispatchActiveState.PREVIEWING, "testCaller")

        verify(exactly = 0) { sendDispatchDataRepo.sendDispatchStopsForGeofence(any(), any()) }
    }

    @After
    fun after() {
        unmockkAll()
    }
}