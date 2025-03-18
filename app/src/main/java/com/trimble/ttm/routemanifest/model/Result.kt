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
 *   Abstract: Used to return the state of its child operation
 * *
 */
package com.trimble.ttm.routemanifest.model

enum class STATE {
    SUCCESS,
    ERROR,
    IGNORE
}

open class Result {
    var state: STATE? = null
    var error: String = ""
}