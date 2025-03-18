package com.trimble.ttm.routemanifest.model

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ActionTests {

    @Test
    fun testApproachingActionType() {
        val action = Action(0, 1)

        assert(action.thisAction == ActionTypes.APPROACHING)
    }

    @Test
    fun testArrivedActionType() {
        val action = Action(1, 1)

        assert(action.thisAction == ActionTypes.ARRIVED)
    }

    @Test
    fun testDepartedActionType() {
        val action = Action(2, 1)

        assert(action.thisAction == ActionTypes.DEPARTED)
    }

    @Test
    fun testDepartedActionCannotBeArrivedType() {
        val action = Action(2, 1)

        assert(action.thisAction != ActionTypes.ARRIVED)
    }

    @Test
    fun testDriverFormValid() {
        val action = Action(driverFormid = 123, driverFormClass = 432)
        assertTrue(action.isValidDriverForm())
    }

    @Test
    fun testDriverFormInvalidIfFormClassIsLessThanZero() {
        val action = Action(driverFormid = 123, driverFormClass = -1)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testDriverFormInvalidIfFormIdIsLessThanZero() {
        val action = Action(driverFormid = -1, driverFormClass = 7)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testDriverFormInvalidIfFormIdIsZero() {
        val action = Action(driverFormid = 0, driverFormClass = 7)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testReplyFormIsValid() {
        val action = Action(forcedFormId = "123", forcedFormClass = 432)
        assertTrue(action.isValidReplyForm())
    }

    @Test
    fun testReplyFormInvalidIfForcedFormIdIsLessThanZero() {
        val action = Action(forcedFormId = "-1", forcedFormClass = 432)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testReplyFormInvalidIfForcedFormIdIsZero() {
        val action = Action(forcedFormId = "0", forcedFormClass = 432)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testReplyFormInvalidIfForcedFormClassIsLessThanZero() {
        val action = Action(forcedFormId = "0", forcedFormClass = -1)
        assertFalse(action.isValidDriverForm())
    }

    @Test
    fun testActionIsValid() {
        val action = Action(actionid = 4)
        assertFalse(action.isInValid())
    }

    @Test
    fun testActionIsInvalid() {
        val action = Action(actionid = -1)
        assertTrue(action.isInValid())
    }

    @Test
    fun isEtaPassedReturnsTrueWhenActuallyElapsed() {
        assertTrue(Action(eta = "2022-02-08T14:30:00.000Z").isEtaMissed("isEtaPassedReturnsTrueWhenActuallyElapsed"))
    }

    @Test
    fun isEtaPassedReturnsFalseWhenActuallyNotElapsed() {
        assertFalse(Action(eta = "2040-02-08T14:30:00.000Z").isEtaMissed("isEtaPassedReturnsFalseWhenActuallyNotElapsed"))
    }

    @Test
    fun isEtaPassedReturnsFalseWhenEtaIsEmpty() {
        assertFalse(Action(eta = "").isEtaMissed("isEtaPassedReturnsFalseWhenActuallyNotElapsed"))
    }

    @Test
    fun checkForElapsedEtaDifferenceFromNow() {
        assertTrue(Action(eta = "2022-02-08T14:30:00.000Z").getEtaDifferenceFromNow("checkForElapsedEtaDifferenceFromNow") < 0L)
    }

    @Test
    fun checkForUnElapsedEtaDifferenceFromNow() {
        assertTrue(Action(eta = "2050-02-08T14:30:00.000Z").getEtaDifferenceFromNow("checkNegativeCaseForElapsedEtaDifferenceFromNow") > 0L)
    }

}