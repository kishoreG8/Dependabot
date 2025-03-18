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
 *   Abstract: Contains Truck's detail.
 * 1. Cid - Customer Id
 * 2. Dsn - Device Serial Number
 * 3.Trucknum - Truck Number
 * 4.Vid - Vehicle Id
 * *
 */
package com.trimble.ttm.routemanifest.model
import com.google.gson.annotations.SerializedName

data class VUnit(
    @SerializedName("Cid") val cid: Long = 0L,
    @SerializedName("Dsn") val dsn: Long = 0L,
    @SerializedName("Trucknum") val trucknum: String = "",
    @SerializedName("Vid") val vid: Long = 0L
)
