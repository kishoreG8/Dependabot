/*
 * *
 *  * Copyright Trimble Inc., 2019 - 2020 All rights reserved.
 *  *
 *  * Licensed Software Confidential and Proprietary Information of Trimble Inc.,
 *   made available under Non-Disclosure Agreement OR License as applicable.
 *
 *   Product Name: TTM - Route Manifest
 *
 *   Author: Vignesh Elangovan
 *
 *   Created On: 12-08-2020
 *
 *   Abstract: The data class for getting location address from cpik instance in applauncher application
 * *
 */
package com.trimble.ttm.routemanifest.model


data class Address(
    var name: String = "",
    var address: String = "",
    var city: String = "",
    var state: String = "",
    var country: String = "",
    var county: String = "",
    var zip: String = ""
)

data class Leg(val time: Double, val distance: Double)