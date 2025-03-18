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
 *   Abstract: The data class to get ETA and distance information(for each stop) from cpik instance of applauncher application
 * *
 */
package com.trimble.ttm.routemanifest.model

data class RouteCalculationResult(
    var stopDetailList: List<StopDetail> = mutableListOf(),
    var totalDistance: Double = 0.0,
    var totalHours: Double = 0.0
) : Result() {

    override fun toString(): String {
        return "RouteCalculationResult(stopDetailList=$stopDetailList, totalDistance=$totalDistance, totalHours=$totalHours)"
    }
}