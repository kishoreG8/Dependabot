package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNull

class StopDetailTests {

    @Test
    fun testThisStopTypeIsCompleted() {
        val stopDetail = StopDetail(completedTime = "24Jan2021")

        assertTrue(stopDetail.ThisStopType == StopType.STOP_COMPLETED)
    }

    @Test
    fun testThisStopTypeIsEntered() {
        val stopDetail = StopDetail(completedTime = "")

        assertTrue(stopDetail.ThisStopType == StopType.ENTER)
    }

    @Test
    fun testStopHasArrivingActionOnly() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertTrue(stopDetail.hasArrivingActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivingActionOnlyIfOnlyActionExistIsDeparted() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(2)))
            }
        }

        assertFalse(stopDetail.hasArrivingActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivingActionOnlyIfOnlyActionExistIsArrived() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(1)))
            }
        }

        assertFalse(stopDetail.hasArrivingActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivingActionOnlyIfMoreActionsExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(2), Action(1)))
            }
        }

        assertFalse(stopDetail.hasArrivingActionOnly())
    }

    @Test
    fun testStopHasDepartedActionOnly() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(2)))
            }
        }

        assertTrue(stopDetail.hasDepartActionOnly())
    }

    @Test
    fun testStopDoesNotHaveDepartActionOnlyIfOnlyActionExistIsArriving() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertFalse(stopDetail.hasDepartActionOnly())
    }

    @Test
    fun testStopDoesNotHaveDepartActionOnlyIfOnlyActionExistIsArrived() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(1)))
            }
        }

        assertFalse(stopDetail.hasDepartActionOnly())
    }

    @Test
    fun testStopDoesNotHaveDepartActionOnlyIfMoreActionsExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(1)))
            }
        }

        assertFalse(stopDetail.hasDepartActionOnly())
    }

    @Test
    fun testOnlyApproachDepartExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(2)))
            }
        }

        assertTrue(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testApproachArriveExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(1)))
            }
        }

        assertFalse(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testArriveDepartExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(1), Action(2)))
            }
        }

        assertFalse(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testArriveActionExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(2)))
            }
        }

        assertTrue(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testArriveActionNotExistIfOnlyDepartExists() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(2)))
            }
        }

        assertFalse(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testArriveActionNotExistIfOnlyArriveExists() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertFalse(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testArriveActionNotExistIfNoActionsExists() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf())
            }
        }

        assertFalse(stopDetail.hasArrivingAndDepartActionOnly())
    }

    @Test
    fun testNoDepartActionIfActionIsEmpty() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf())
            }
        }

        assertTrue(stopDetail.hasNoDepartedAction())
    }

    @Test
    fun testNoDepartActionIfArriveActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(1)))
            }
        }

        assertTrue(stopDetail.hasNoDepartedAction())
    }

    @Test
    fun testNoDepartActionIfApproachActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertTrue(stopDetail.hasNoDepartedAction())
    }

    @Test
    fun testNoDepartActionIfApproachAndArriveActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(1)))
            }
        }

        assertTrue(stopDetail.hasNoDepartedAction())
    }


    @Test
    fun testNoArriveActionIfActionIsEmpty() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf())
            }
        }

        assertTrue(stopDetail.hasNoArrivedAction())
    }

    @Test
    fun testNoArriveActionIfApproachActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertTrue(stopDetail.hasNoArrivedAction())
    }

    @Test
    fun testNoArriveActionIfDepartActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(2)))
            }
        }

        assertTrue(stopDetail.hasNoArrivedAction())
    }

    @Test
    fun testNoArriveActionIfApproachAndDepartActionOnlyExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0), Action(2)))
            }
        }

        assertTrue(stopDetail.hasNoArrivedAction())
    }

    @Test
    fun testGetApproachActionIfItExists() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(0)))
            }
        }

        assertTrue(stopDetail.getArrivingAction().isNotNull())
    }

    @Test
    fun testGetArriveActionIsNullIfItDoesNotExist() {
        val stopDetail = StopDetail().also {
            with(it.Actions) {
                clear()
                addAll(mutableListOf(Action(1)))
            }
        }

        assertTrue(stopDetail.getArrivingAction().isNull())
    }

    @Test
    fun testPendingActionExists() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(0, responseSent = true))
        stop.Actions.add(Action(1, responseSent = true))
        stop.Actions.add(Action(2, responseSent = false))

        assertFalse(stop.hasNoPendingAction())
    }

    @Test
    fun testPendingActionExistsIfAllAreYetToSendResponse() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(0, responseSent = false))
        stop.Actions.add(Action(1, responseSent = false))
        stop.Actions.add(Action(2, responseSent = false))

        assertFalse(stop.hasNoPendingAction())
    }

    @Test
    fun testPendingActionExistsIfFewAreYetToSendResponse() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(0, responseSent = true))
        stop.Actions.add(Action(1, responseSent = false))
        stop.Actions.add(Action(2, responseSent = false))

        assertFalse(stop.hasNoPendingAction())
    }

    @Test
    fun testPendingActionExistsIfOnlyActionYetToSendResponse() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(2, responseSent = false))

        assertFalse(stop.hasNoPendingAction())
    }

    @Test
    fun testPendingActionNotExists() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(0, responseSent = true))
        stop.Actions.add(Action(1, responseSent = true))
        stop.Actions.add(Action(2, responseSent = true))

        assertTrue(stop.hasNoPendingAction())
    }

    @Test
    fun testPendingActionNotExistsWhenOnlyCoupleActionsThere() {
        val stop = StopDetail(stopid = 1)
        stop.Actions.add(Action(1, responseSent = true))
        stop.Actions.add(Action(2, responseSent = true))

        assertTrue(stop.hasNoPendingAction())
    }

    @Test
    fun testSortedStopsBasedOnUniqueIds() {
        val unorderedStopList: List<StopDetail> = mutableListOf<StopDetail>().apply {
            add(StopDetail(stopid = 0))
            add(StopDetail(stopid = 2))
            add(StopDetail(stopid = 1))
            add(StopDetail(stopid = 3))
        }
        val orderedStopList: List<StopDetail> = mutableListOf<StopDetail>().apply {
            add(StopDetail(stopid = 0))
            add(StopDetail(stopid = 1))
            add(StopDetail(stopid = 2))
            add(StopDetail(stopid = 3))
        }
        assertEquals(orderedStopList, unorderedStopList.getSortedStops())
    }

    @Test
    fun testArriveOnlyExist() {
        val stop = StopDetail().also {
            it.Actions.add(Action(1))
        }
        assertTrue(stop.hasArrivedActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivedActionOnlyIfMoreActionsExist() {
        val stop = StopDetail().also {
            it.Actions.addAll(mutableListOf(Action(0), Action(1), Action(2)))
        }
        assertFalse(stop.hasArrivedActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivedActionOnlyIfOnlyActionExistIsDeparted() {
        val stop = StopDetail().also {
            it.Actions.add(Action(2))
        }
        assertFalse(stop.hasArrivedActionOnly())
    }

    @Test
    fun testStopDoesNotHaveArrivedActionOnlyIfOnlyActionExistIsArriving() {
        val stop = StopDetail().also {
            it.Actions.add(Action(0))
        }
        assertFalse(stop.hasArrivedActionOnly())
    }

    @Test
    fun testOnlyApproachAndArriveActionExist() {
        val stop = StopDetail().also {
            it.Actions.addAll(mutableListOf(Action(0), Action(1)))
        }
        assertTrue(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is approach and depart`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.addAll(mutableListOf(Action(0), Action(2)))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is arrive and depart`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.addAll(mutableListOf(Action(1), Action(2)))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is only approach`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.add(Action(0))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is only arrive`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.add(Action(1))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is only depart`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.add(Action(2))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `test approach and arrive action only if action exist is approach and arrive and depart`() {    //NOSONAR
        val stop = StopDetail().also {
            it.Actions.addAll(mutableListOf(Action(0), Action(1), Action(2)))
        }
        assertFalse(stop.hasArrivingAndArrivedActionOnly())
    }

    @Test
    fun `isStopDeparted - false`() {
        assertFalse(StopDetail(departedTime = "").isDeparted())
    }

    @Test
    fun `isStopDeparted - true`() {
        assertTrue(StopDetail(departedTime = "test").isDeparted())
    }

    @Test
    fun `verify stopDetail should display arrived data when arrival action exists`() {
        //Assign
        val objectUnderTest = StopDetail(completedTime = "07Feb2022", name = "test")
        val actionArrival = Action(actionType = ActionTypes.ARRIVED.ordinal)
        objectUnderTest.Actions.add(actionArrival)

        //Act
        val result = objectUnderTest.isArrivalAvailable()

        //Assert
        assertTrue("Arrival action is available", result)
    }

    @Test
    fun `verify stopDetail should hide any arrived data when there is no arrival action`() {
        //Assign
        val objectUnderTest = StopDetail(completedTime = "07Feb2022", name = "test")
        val actionDeparted = Action(actionType = ActionTypes.DEPARTED.ordinal)
        objectUnderTest.Actions.add(actionDeparted)

        //Act
        val result = objectUnderTest.isArrivalAvailable()

        //Assert
        assertFalse("Arrival action should be missing", result)
    }

    @Test
    fun `check if pre-planned arrival text is displayed in red color for the stop that is yet to be arrived and has past ETA`() {
        val stop = StopDetail().also {
            it.completedTime = EMPTY_STRING
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = "2020-03-03T08:00:00.000Z"
                )
            )
        }
        assertTrue(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color for the stop that is yet to be arrived and has future ETA`() {
        val stop = StopDetail().also {
            it.completedTime = EMPTY_STRING
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = "2050-03-03T08:00:00.000Z"
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color for the stop that is yet to be arrived and has no ETA`() {
        val stop = StopDetail().also {
            it.completedTime = EMPTY_STRING
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = EMPTY_STRING
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color when there is no arrive action in the stop`() {
        val stop = StopDetail().also {
            it.completedTime = EMPTY_STRING
            it.Actions.add(
                Action(
                    actionType = 0,
                    eta = "2020-03-03T08:00:00.000Z"
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in red color for the arrived stop and has past ETA`() {
        val stop = StopDetail().also {
            it.completedTime = "2023-10-26T08:00:00.000Z"
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = "2020-03-03T08:00:00.000Z"
                )
            )
        }
        assertTrue(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color for the arrived stop and has future ETA`() {
        val stop = StopDetail().also {
            it.completedTime = "2023-10-26T08:00:00.000Z"
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = "2050-03-03T08:00:00.000Z"
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color for the approached stop which has only the approach action and has future ETA`() {
        val stop = StopDetail().also {
            it.completedTime = EMPTY_STRING
            it.Actions.add(
                Action(
                    actionType = 0,
                    eta = "2050-03-03T08:00:00.000Z"
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun `check if pre-planned arrival text is displayed in white color for the arrived stop and has no ETA`() {
        val stop = StopDetail().also {
            it.completedTime = "2023-10-26T08:00:00.000Z"
            it.Actions.add(
                Action(
                    actionType = 1,
                    eta = EMPTY_STRING
                )
            )
        }
        assertFalse(stop.hasETACrossed())
    }

    @Test
    fun testGetArrivedAction_StopIdNotFound() {
        // Arrange
        val stopDetails: List<StopDetail> =
            listOf(StopDetail(stopid = 1).also { it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal)) },
                StopDetail(stopid = 2).also { it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal)) })

        // Act
        val result = stopDetails.getArrivedActionForGivenStop(3)

        // Assert
        assertNull(result, "Expected result to be null for stopId not found")
    }

    @Test
    fun testGetArrivedAction_ActionTypeNotFound() {
        // Arrange
        val stopDetails: List<StopDetail> =
            listOf(StopDetail(stopid = 1).also { it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal)) },
                StopDetail(stopid = 2).also { it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal)) })


        // Act
        val result = stopDetails.getArrivedActionForGivenStop(1)

        // Assert
        assertNull(result, "Expected result to be null for ActionType not found")
    }

    @Test
    fun testGetArrivedAction_Success() {
        // Arrange
        val stopDetails: List<StopDetail> =
            listOf(StopDetail(stopid = 1).also { it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal)) },
                StopDetail(stopid = 2).also { it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal)) })

        // Act
        val result = stopDetails.getArrivedActionForGivenStop(2)

        // Assert
        kotlin.test.assertEquals(
            Action(actionType = 1),
            result,
            "Expected correct Action for valid stopId and ActionType"
        )

    }

}