package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.routemanifest.utils.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class StopDetailKtTest {
    @Test
    fun `verify GetDefaultArrivedRadius() with Depart Action`() {
        val departAction = Action(actionType = ActionTypes.DEPARTED.ordinal, radius = 200)
        val stopDetail = StopDetail().apply {
            Actions = CopyOnWriteArrayList(listOf(departAction))
        }

        val arrivedRadius = stopDetail.getDefaultArrivedRadius()

        val expectedRadius =
            (departAction.radius * ARRIVED_RADIUS_RELATIVE_TO_DEPART_RADIUS_IN_PERCENTAGE).toInt()
        assertEquals(expectedRadius, arrivedRadius)
    }

    @Test
    fun `verify GetDefaultArrivedRadius() without Depart Action`() {
        val stopDetail = StopDetail()

        val arrivedRadius = stopDetail.getDefaultArrivedRadius()

        assertEquals(null, arrivedRadius)
    }
}