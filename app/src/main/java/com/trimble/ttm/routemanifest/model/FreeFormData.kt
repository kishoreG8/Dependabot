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
 *   Abstract: Contains corporate freeform data. Which can be used to render a freeform with a single large text field.
 * *
 */
package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.routemanifest.utils.DRIVER_FORM_HASH

data class FreeFormData(val driverFormhash: Long, val freeFormMessage: String)

fun FreeFormData.isFreeForm(): Boolean = driverFormhash == DRIVER_FORM_HASH