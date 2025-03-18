package com.trimble.ttm.routemanifest.model

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class FreeFormDataTests {

    // 5501L is a constant
    @Test
    fun testIfThisIsFreeFormData() {
        val freeFormData = FreeFormData(5501L, "Test")

        assertTrue(freeFormData.isFreeForm())
    }

    @Test
    fun testThisIsNotFreeFormData() {
        val freeFormData = FreeFormData(1111L, "Test")

        assertFalse(freeFormData.isFreeForm())
    }
}