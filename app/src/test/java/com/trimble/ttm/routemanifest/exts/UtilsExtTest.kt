package com.trimble.ttm.routemanifest.exts

import android.content.Context
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.routemanifest.model.Leg
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.StopInfo
import com.trimble.ttm.routemanifest.utils.ext.areAllStopsComplete
import com.trimble.ttm.routemanifest.utils.ext.getDaysFromMinutes
import com.trimble.ttm.routemanifest.utils.ext.getNonDeletedAndUncompletedStopsBasedOnActions
import com.trimble.ttm.routemanifest.utils.ext.getStopDetailList
import com.trimble.ttm.routemanifest.utils.ext.getStopInfoList
import com.trimble.ttm.routemanifest.utils.ext.getTimeStringFromMinutes
import com.trimble.ttm.routemanifest.utils.ext.getUncompletedStopsBasedOnActions
import com.trimble.ttm.routemanifest.utils.ext.getWorkflowEventName
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isGreaterThan
import com.trimble.ttm.routemanifest.utils.ext.isGreaterThanAndEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isLessThan
import com.trimble.ttm.routemanifest.utils.ext.isLessThanAndEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isNotEqualTo
import com.trimble.ttm.routemanifest.utils.ext.toMilesText
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class UtilsExtTest {

    private val at02dhr02dmin = "%02d hr %02d min"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk()
    }

    @Test
    fun `should carry over minutes`() {    //NOSONAR
        every { context.getString(R.string.estimatedDriveTimeFormat) } returns at02dhr02dmin
        assertEquals("01 hr 01 min", 61.0.getTimeStringFromMinutes(context))
    }

    @Test
    fun `should handle less than an hour`() {    //NOSONAR
        every { context.getString(R.string.estimatedDriveTimeFormat) } returns at02dhr02dmin
        assertEquals("00 hr 01 min", 1.0.getTimeStringFromMinutes(context))
    }

    @Test
    fun `should handle 0`() {    //NOSONAR
        every { context.getString(R.string.estimatedDriveTimeFormat) } returns at02dhr02dmin
        assertEquals("00 hr 00 min", 0.0.getTimeStringFromMinutes(context))
    }

    @Test
    fun `should handle double digit hours`() {    //NOSONAR
        every { context.getString(R.string.estimatedDriveTimeFormat) } returns at02dhr02dmin
        assertEquals("10 hr 01 min", 601.0.getTimeStringFromMinutes(context))
    }

    @Test
    fun `should handle more than a day`() {    //NOSONAR
        every { context.getString(R.string.estimatedDriveTimeFormat) } returns at02dhr02dmin
        assertEquals("24 hr 05 min", 1445.0.getTimeStringFromMinutes(context))
    }

    @Test
    fun `should return minutes in days`() {    //NOSONAR
        assertEquals(2.0, 2880.0.getDaysFromMinutes())
    }

    @Test
    fun `verify return values of logical comparisons`() {    //NOSONAR
        assertEquals(true, 2.isEqualTo(2))
        assertEquals(false, 2.isEqualTo(3))

        assertEquals(true, 3.isNotEqualTo(4))
        assertEquals(false, 56.isNotEqualTo(56))

        assertEquals(true, 5.isGreaterThan(4))
        assertEquals(false, 5.isGreaterThan(6))

        assertEquals(true, 4.isLessThan(5))
        assertEquals(false, 4.isLessThan(3))

        assertEquals(true, 55.isGreaterThanAndEqualTo(55))
        assertEquals(false, 55.isGreaterThanAndEqualTo(57))

        assertEquals(true, 44.isLessThanAndEqualTo(55))
        assertEquals(false, 68.isLessThanAndEqualTo(67))
    }

    @Test
    fun `verify return values of toMilesText`() {    //NOSONAR
        assertEquals("0.6", 0.6f.toMilesText())
        assertEquals("0", 0.0f.toMilesText())
        assertEquals("0.1", 0.120f.toMilesText())
        assertEquals("1.1", 1.120f.toMilesText())
        assertEquals("2.2", 2.20f.toMilesText())
        assertEquals("1", 1.04f.toMilesText())
        assertEquals("1", 1.05f.toMilesText())
        assertEquals("1.1", 1.06f.toMilesText())
        assertEquals("1.9", 1.9f.toMilesText())
        assertEquals("234567.7", 234567.69f.toMilesText())
    }

    @Test
    fun verifyForCompletedTrip() {
        val stops = mutableListOf<StopDetail>().also {
            it.add(getStop(0,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
            it.add(getStop(1,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
            it.add(getStop(2,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
        }
        assertEquals(emptyList<StopDetail>(), stops.getUncompletedStopsBasedOnActions())
    }

    private fun getStop(stopId: Int, approachResponse: Boolean, arriveResponse: Boolean, departResponse: Boolean) = StopDetail(stopid = stopId).also { stopDetail ->
        stopDetail.Actions.add(Action(responseSent = approachResponse))
        stopDetail.Actions.add(Action(responseSent = arriveResponse))
        stopDetail.Actions.add(Action(responseSent = departResponse))
    }

    @Test
    fun verifyForUnCompletedStopInTrip() {
        val stops = mutableListOf<StopDetail>().also {
            it.add(getStop(0,
                approachResponse = true,
                arriveResponse = true,
                departResponse = false
            ))
            it.add(getStop(1,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
            it.add(getStop(2,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
        }
        assertEquals(mutableListOf<StopDetail>().also {
            it.add(stops[0])
        }, stops.getUncompletedStopsBasedOnActions())
    }

    @Test
    fun verifyForUnCompletedStopsInTrip() {
        val stops = mutableListOf<StopDetail>().also {
            it.add(getStop(0,
                approachResponse = true,
                arriveResponse = true,
                departResponse = false
            ))
            it.add(getStop(1,
                approachResponse = true,
                arriveResponse = true,
                departResponse = true
            ))
            it.add(getStop(2,
                approachResponse = true,
                arriveResponse = true,
                departResponse = false
            ))
        }

        assertEquals(mutableListOf<StopDetail>().also {
            it.add(stops[0])
            it.add(stops[2])
        }, stops.getUncompletedStopsBasedOnActions())
    }

    @Test
    fun `verify stopInfo list from stopDetail list`(){

        val actionListForFirstStop = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10))
        }

        val actionListForSecondStop = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.APPROACHING.ordinal, radius = 15))
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 4))
        }

        val stopList = ArrayList<StopDetail>()
            .also { list ->
                list.add(StopDetail(stopid = 0, name = "First Stop", latitude = 44.65656, longitude = -82.76564).also {
                    it.leg = Leg(1.0, 1.0)
                    it.Actions.addAll(actionListForFirstStop)
                })
                list.add(StopDetail(stopid = 1, name = "Second Stop", latitude = 44.354547, longitude = -82.676675).also {
                    it.isManualArrival = true
                    it.arrivalLatitude = 80.26
                    it.arrivalLongitude = 12.9876
                    it.leg = Leg(1.0, 1.2)
                    it.Actions.addAll(actionListForSecondStop)
                })
            }

        val stopInfoList = stopList.getStopInfoList(isPolygonalOptOut = false)

        Assert.assertEquals(stopList[0].stopid.toString(), stopInfoList[0].stopId)
        Assert.assertEquals(stopList[0].latitude, stopInfoList[0].latitude, 0.0)
        Assert.assertEquals(stopList[1].longitude, stopInfoList[1].longitude, 0.0)
        Assert.assertEquals(stopList[0].Actions[1].radius, stopInfoList[0].departRadius)
        Assert.assertEquals(stopList[1].Actions[1].radius, stopInfoList[1].arrivedRadius)
        Assert.assertEquals(stopList[0].isManualArrival, stopInfoList[0].isManualArrival)
        Assert.assertEquals(stopList[0].arrivalLatitude, stopInfoList[0].arrivalLatitude, 0.1)
        Assert.assertEquals(stopList[0].arrivalLongitude, stopInfoList[0].arrivalLongitude, 0.1)
        Assert.assertEquals(stopList[1].isManualArrival, stopInfoList[1].isManualArrival)
        Assert.assertEquals(stopList[1].arrivalLatitude, stopInfoList[1].arrivalLatitude, 0.1)
        Assert.assertEquals(stopList[1].arrivalLongitude, stopInfoList[1].arrivalLongitude,0.1)
    }

    @Test
    fun `verify stop detail from stop info`(){
        val address = Address(name = "First Address", address = "44, Baker Road")
        val stopInfoList = ArrayList<StopInfo>().also { stopList ->
            stopList.add(StopInfo(stopId = "1", latitude = 44.66565, longitude = -82.656465,).apply {
                this.Address = address
            })
            stopList.add(StopInfo(stopId = "2", latitude = 44.443355, longitude = -82.675442432,).apply {
                this.Address = address
            })
        }

        val stopDetailList = stopInfoList.getStopDetailList()

        Assert.assertEquals(stopInfoList[0].stopId, stopDetailList[0].stopid.toString())
        Assert.assertEquals(stopInfoList[0].latitude, stopDetailList[0].latitude, 0.0)
        Assert.assertEquals(stopInfoList[1].longitude, stopDetailList[1].longitude, 0.0)
        Assert.assertEquals(stopInfoList[0].Address.address, stopDetailList[0].Address?.address)
    }

    @Test
    fun `verify getWorkflowEventName func` () {
        Assert.assertEquals(ActionTypes.APPROACHING.ordinal.getWorkflowEventName(), WorkFlowEvents.APPROACH_EVENT)
        Assert.assertEquals(ActionTypes.ARRIVED.ordinal.getWorkflowEventName(), WorkFlowEvents.ARRIVE_EVENT)
        Assert.assertEquals(ActionTypes.DEPARTED.ordinal.getWorkflowEventName(), WorkFlowEvents.DEPART_EVENT)
    }

    @Test
    fun `verify areAllStopsComplete returns true when all stops have completed time `() { //NOSONAR
        val stopDetailList = ArrayList<StopDetail>()
        stopDetailList.add(
            StopDetail(
                stopid = 0,
                completedTime = "2024-03-16T13:19:54.765Z"
            )
        )
        stopDetailList.add(
            StopDetail(
                stopid = 1,
                completedTime = "2024-03-16T13:19:55.765Z"
            )
        )
        stopDetailList.add(
            StopDetail(
                stopid = 2,
                completedTime = "2024-03-16T13:19:56.765Z"
            )
        )
        Assert.assertEquals(true, stopDetailList.areAllStopsComplete())
    }

    @Test
    fun `all stop are complete when stop list is empty`() { //NOSONAR
        val stopDetailList = ArrayList<StopDetail>()
        assertEquals(true, stopDetailList.areAllStopsComplete())
    }

    @Test
    fun `verify areAllStopsComplete returns false when one stop has not completed time `() { // NOSONAR
        val stopDetailList = ArrayList<StopDetail>()
        stopDetailList.add(
            StopDetail(
                stopid = 0,
                completedTime = ""
            )
        )
        stopDetailList.add(
            StopDetail(
                stopid = 1,
                completedTime = "2024-03-16T13:19:54.765Z"
            )
        )
        stopDetailList.add(
            StopDetail(
                stopid = 2,
                completedTime = "2024-03-16T13:19:55.765Z"
            )
        )

        Assert.assertEquals(false,stopDetailList.areAllStopsComplete())
    }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions returns empty list when all the stops inside stopList are deleted`() =
        runTest {
            val actionList = CopyOnWriteArrayList<Action>().also { actionList ->
                actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5))
                actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10))
            }
            val stopDetailList = mutableListOf<StopDetail>().also {
                it.add(
                    StopDetail(
                        stopid = 0,
                        deleted = 1,
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
                it.add(
                    StopDetail(
                        stopid = 1,
                        deleted = 1
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
                it.add(
                    StopDetail(
                        stopid = 2,
                        deleted = 1
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
            }
            Assert.assertEquals(mutableListOf<StopDetail>(), stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
        }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions returns correct list when one the stops inside stopList is not deleted`() =
        runTest {
            val actionList = CopyOnWriteArrayList<Action>().also { actionList ->
                actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5))
                actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10))
            }
            val answerStopDetail = mutableListOf<StopDetail>().also {
                it.add(
                    StopDetail(
                        stopid = 2,
                        deleted = 0,
                    ).also { stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
            }
            val stopDetailList = mutableListOf<StopDetail>().also {
                it.add(
                    StopDetail(
                        stopid = 0,
                        deleted = 1,
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
                it.add(
                    StopDetail(
                        stopid = 1,
                        deleted = 1
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
                it.add(
                    StopDetail(
                        stopid = 2,
                        deleted = 0
                    ).also{ stopDetail ->
                        stopDetail.Actions.addAll(actionList)
                    }
                )
            }
            Assert.assertEquals(answerStopDetail, stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
        }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions() returns emptyList when there is no action document attached to the StopDetailList`() = runTest {
        val stopDetailList = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 0,
                    deleted = 1
                )
            )
            it.add(
                StopDetail(
                    stopid = 1,
                    deleted = 1
                )
            )
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 1
                )
            )
        }
        Assert.assertEquals(mutableListOf<StopDetail>(), stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
    }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions() filters the stops with completed actions`() = runTest {
        val completedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val inCompletedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.APPROACHING.ordinal, radius = 5, responseSent = false))
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = false))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val answerStopDetail = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 0,
                ).also { stopDetail ->
                    stopDetail.Actions.addAll(inCompletedStopActionList)
                }
            )
        }
        val stopDetailList = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 0,
                    deleted = 0,
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 1,
                    deleted = 0
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 0
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(inCompletedStopActionList)
                }
            )
        }
        Assert.assertEquals(answerStopDetail, stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
    }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions() for stop with all completed actions returns empty list`() = runTest {
        val completedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val stopDetailList = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 0,
                    deleted = 0,
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 1,
                    deleted = 0
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 1
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
        }
        Assert.assertEquals(mutableListOf<StopDetail>(), stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
    }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions() filters the stops with inCompleted Action, even if there is only one action incomplete`() = runTest {
        val completedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val inCompletedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.APPROACHING.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = false))
        }
        val stopDetailList = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 0,
                    deleted = 0,
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 1,
                    deleted = 0
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 1
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(inCompletedStopActionList)
                }
            )
        }
        Assert.assertEquals(mutableListOf<StopDetail>(), stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
    }

    @Test
    fun `verify getNonDeletedAndUncompletedStopsBasedOnActions() does not take the stops with inCompleted actions, if the stop is deleted`() = runTest {
        val completedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = true))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val inCompletedStopActionList = CopyOnWriteArrayList<Action>().also { actionList ->
            actionList.add(Action(ActionTypes.APPROACHING.ordinal, radius = 5, responseSent = false))
            actionList.add(Action(ActionTypes.ARRIVED.ordinal, radius = 5, responseSent = false))
            actionList.add(Action(ActionTypes.DEPARTED.ordinal, radius = 10, responseSent = true))
        }
        val stopDetailList = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(
                    stopid = 0,
                    deleted = 0,
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 1,
                    deleted = 0
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(completedStopActionList)
                }
            )
            it.add(
                StopDetail(
                    stopid = 2,
                    deleted = 1
                ).also{ stopDetail ->
                    stopDetail.Actions.addAll(inCompletedStopActionList)
                }
            )
        }
        Assert.assertEquals(mutableListOf<StopDetail>(), stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}