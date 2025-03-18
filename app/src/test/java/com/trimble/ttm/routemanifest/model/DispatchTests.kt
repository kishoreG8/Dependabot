package com.trimble.ttm.routemanifest.model

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class DispatchTests {

    @Test
    fun testDispatchIsValid() {
        val dispatch = Dispatch(dispid = "4647657")

        assertTrue(dispatch.isValid())
    }

    @Test
    fun testDispatchIsInvalid() {
        val dispatch = Dispatch(dispid = "")

        assertFalse(dispatch.isValid())
    }
}