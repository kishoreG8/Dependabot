package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.model.ActiveEldDriver
import junit.framework.Assert.assertTrue
import org.junit.Test

class ActiveEldDriverTests {

    @Test
    fun testActiveEldDriverCachedCorrectValue() {
        val activeEldDriver = ActiveEldDriver("Test", "Me")

        assertTrue(activeEldDriver.userId == "Test")
        assertTrue(activeEldDriver.userName == "Me")
    }

}