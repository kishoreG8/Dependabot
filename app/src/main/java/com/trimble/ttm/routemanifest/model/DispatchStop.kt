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
 *   Abstract: Contains details to create a geofence in cpik instance of applauncher application.
 * *
 */
package com.trimble.ttm.routemanifest.model

data class DispatchStop(
    var latitude: Double,
    var longitude: Double,
    var stopId: Int,
    var isWayPoint: Boolean = false
){
    var approachRadius: Int?=null
    var arrivedRadius: Int?=null
    var departRadius: Int?=null
    var Address: Address = Address()
    var plannedDurationMinutes: Int = 0
}
