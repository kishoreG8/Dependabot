package com.trimble.ttm.routemanifest.model

import junit.framework.Assert.assertTrue
import org.junit.Test

class AddressTests {

    @Test
    fun testAddressCachesCorrectValues() {
        val address = Address("name", "address", "city", "state", "country", "county", "zip")

        assertTrue(address.name == "name")
        assertTrue(address.address == "address")
        assertTrue(address.city == "city")
        assertTrue(address.state == "state")
        assertTrue(address.country == "country")
        assertTrue(address.county == "county")
        assertTrue(address.zip == "zip")
    }

    @Test
    fun testLegCachesCorrectValues() {
        val leg = Leg(12.0, 45.0)

        assertTrue(leg.time == 12.0)
        assertTrue(leg.distance == 45.0)
    }

}